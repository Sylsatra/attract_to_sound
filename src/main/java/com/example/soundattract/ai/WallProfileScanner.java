package com.example.soundattract.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.example.soundattract.config.separate.LivingWallConfig;

final class WallProfileScanner {
    private static final double EPSILON = 1.0E-8D;
    private static final int MIN_PROFILE_HEIGHT = 2;
    private static final int DIRECTIONAL_RAY_BUDGET = 48;
    private static final int FALLBACK_RAY_BUDGET = 48;
    static final int MAX_EVIDENCE_CELLS_PER_RAY = 8;
    static final int MAX_REFINE_RAYS_PER_TICK = 16;
    static final int MAX_LOGICAL_FALLBACK_RAYS = 48;
    private static final int MAX_SHAPE_CACHE_ENTRIES = 4096;
    private static final int SHAPE_CACHE_TTL_TICKS = 200;
    private static final int REFINE_CURSOR_TTL_TICKS = 200;
    private static final int REFINE_CURSOR_MAX_ENTRIES = 256;
    private static final double[] VERTICAL_PITCHES = {-1.0D, -0.5D, 0.0D, 0.5D, 1.0D};
    private static final double[] FALLBACK_PITCHES = {-1.0D, 0.0D, 1.0D};
    private static final Map<ResourceLocation, LinkedHashMap<ShapeCacheKey, ShapeCacheEntry>> SHAPE_CACHE = new LinkedHashMap<>();
    private static final Map<RefineKey, RefineCursorState> REFINE_CURSOR = new LinkedHashMap<>();

    enum CollisionKind {
        SOLID,
        PARTIAL,
        THIN,
        FOLIAGE,
        LAVA,
        OPEN_TRAPDOOR,
        VOID,
        PORTAL,
        FLUID,
        NONE,
        UNKNOWN,
        UNLOADED_BOUNDARY,
        VOID_EXPOSED
    }

    record RayEvidence(long rayId, Vec3 origin, Vec3 direction, BlockPos firstCollisionCell,
                       List<BlockPos> evidenceCells, List<CollisionKind> collisionKinds,
                       List<BlockPos> structuralCells,
                       boolean unloadedBoundary, double probeRadius, double verticalSample) {
        RayEvidence(long rayId, Vec3 origin, Vec3 direction, BlockPos firstCollisionCell,
                    List<BlockPos> evidenceCells, List<CollisionKind> collisionKinds,
                    boolean unloadedBoundary, double probeRadius, double verticalSample) {
            this(rayId, origin, direction, firstCollisionCell, evidenceCells, collisionKinds, List.of(),
                    unloadedBoundary, probeRadius, verticalSample);
        }

        RayEvidence {
            firstCollisionCell = firstCollisionCell == null ? null : firstCollisionCell.immutable();
            if (evidenceCells == null) {
                evidenceCells = List.of();
            } else {
                List<BlockPos> copy = new ArrayList<>(evidenceCells.size());
                for (BlockPos cell : evidenceCells) {
                    copy.add(cell == null ? null : cell.immutable());
                }
                evidenceCells = List.copyOf(copy);
            }
            collisionKinds = List.copyOf(collisionKinds);
            if (structuralCells == null) structuralCells = List.of();
            else structuralCells = List.copyOf(structuralCells);
        }
    }

    record TargetEvidence(BlockPos anchor, List<Long> sourceRayIds, List<BlockPos> hazardCells,
                          List<CollisionKind> hazardKinds, BlockPos wallFace, BlockPos supportCell, BlockPos sourceBucket,
                          BlockPos targetBucket, String scenario, double score, String rejectionCode,
                          List<BlockPos> structuralCells, BlockPos edgeCell, BlockPos sourceApproachSurface,
                          BlockPos landingSurface, BlockPos invasionSurface) {
        TargetEvidence(BlockPos anchor, List<Long> sourceRayIds, List<BlockPos> hazardCells,
                       BlockPos wallFace, BlockPos supportCell, BlockPos sourceBucket,
                       BlockPos targetBucket, String scenario, double score, String rejectionCode) {
            this(anchor, sourceRayIds, hazardCells, List.of(), wallFace, supportCell, sourceBucket, targetBucket,
                    scenario, score, rejectionCode);
        }

        TargetEvidence(BlockPos anchor, List<Long> sourceRayIds, List<BlockPos> hazardCells,
                       List<CollisionKind> hazardKinds, BlockPos wallFace, BlockPos supportCell, BlockPos sourceBucket,
                       BlockPos targetBucket, String scenario, double score, String rejectionCode) {
            this(anchor, sourceRayIds, hazardCells, hazardKinds, wallFace, supportCell, sourceBucket, targetBucket,
                    scenario, score, rejectionCode,
                    defaultStructuralCells(wallFace, supportCell), wallFace, sourceBucket, anchor, anchor);
        }

        TargetEvidence {
            anchor = anchor == null ? null : anchor.immutable();
            wallFace = wallFace == null ? null : wallFace.immutable();
            supportCell = supportCell == null ? null : supportCell.immutable();
            sourceBucket = sourceBucket == null ? null : sourceBucket.immutable();
            targetBucket = targetBucket == null ? null : targetBucket.immutable();
            edgeCell = edgeCell == null ? null : edgeCell.immutable();
            sourceApproachSurface = sourceApproachSurface == null ? null : sourceApproachSurface.immutable();
            landingSurface = landingSurface == null ? null : landingSurface.immutable();
            invasionSurface = invasionSurface == null ? null : invasionSurface.immutable();
            sourceRayIds = sourceRayIds == null ? List.of() : List.copyOf(sourceRayIds);
            hazardKinds = hazardKinds == null ? List.of() : List.copyOf(hazardKinds);
            if (hazardCells == null) {
                hazardCells = List.of();
            } else {
                List<BlockPos> copy = new ArrayList<>(hazardCells.size());
                for (BlockPos cell : hazardCells) {
                    copy.add(cell == null ? null : cell.immutable());
                }
                hazardCells = List.copyOf(copy);
            }
            if (structuralCells == null) {
                structuralCells = List.of();
            } else {
                List<BlockPos> copy = new ArrayList<>(structuralCells.size());
                for (BlockPos cell : structuralCells) {
                    copy.add(cell == null ? null : cell.immutable());
                }
                structuralCells = List.copyOf(copy);
            }
        }

        boolean matchesExactly(TargetEvidence other) {
            return other != null
                    && anchorEquals(anchor(), other.anchor())
                    && anchorEquals(wallFace(), other.wallFace())
                    && anchorEquals(supportCell(), other.supportCell())
                    && anchorEquals(sourceBucket(), other.sourceBucket())
                    && anchorEquals(targetBucket(), other.targetBucket())
                    && java.util.Objects.equals(scenario(), other.scenario())
                    && sourceRayIds().equals(other.sourceRayIds())
                    && hazardCells().equals(other.hazardCells())
                    && hazardKinds().equals(other.hazardKinds())
                    && structuralCells().equals(other.structuralCells())
                    && anchorEquals(edgeCell(), other.edgeCell())
                    && anchorEquals(sourceApproachSurface(), other.sourceApproachSurface())
                    && anchorEquals(landingSurface(), other.landingSurface())
                    && anchorEquals(invasionSurface(), other.invasionSurface());
        }

        String fingerprint() {
            return Objects.toString(anchor()) + '|' + Objects.toString(wallFace()) + '|' + Objects.toString(supportCell())
                    + '|' + Objects.toString(sourceBucket()) + '|' + Objects.toString(targetBucket())
                    + '|' + Objects.toString(scenario()) + '|' + sourceRayIds() + '|' + hazardCells()
                    + '|' + hazardKinds() + '|' + structuralCells()
                    + '|' + Objects.toString(edgeCell()) + '|' + Objects.toString(sourceApproachSurface())
                    + '|' + Objects.toString(landingSurface()) + '|' + Objects.toString(invasionSurface())
                    + '|' + Objects.toString(rejectionCode());
        }

        private static boolean anchorEquals(BlockPos first, BlockPos second) {
            return first == null ? second == null : first.equals(second);
        }

        private static List<BlockPos> defaultStructuralCells(BlockPos wallFace, BlockPos supportCell) {
            List<BlockPos> cells = new ArrayList<>(2);
            if (wallFace != null) cells.add(wallFace.immutable());
            if (supportCell != null) cells.add(supportCell.immutable());
            return List.copyOf(cells);
        }
    }

    private static boolean hasLandingAndEgress(ServerLevel level, BlockPos landing, BlockPos pivot, BlockPos anchor) {
        if (level == null || landing == null || pivot == null || anchor == null) return false;
        if (!isSafeAnchor(level, landing)) return false;
        int dx = Integer.compare(anchor.getX(), pivot.getX());
        int dz = Integer.compare(anchor.getZ(), pivot.getZ());
        if (dx == 0 && dz == 0) return false;
        BlockPos egress = landing.offset(dx, 0, dz);
        if (!level.isLoaded(egress) || !level.isLoaded(egress.above())) return false;
        BlockState feet = level.getBlockState(egress);
        BlockState head = level.getBlockState(egress.above());
        return feet.getCollisionShape(level, egress, CollisionContext.empty()).isEmpty()
                && head.getCollisionShape(level, egress.above(), CollisionContext.empty()).isEmpty()
                && feet.getFluidState().isEmpty()
                && head.getFluidState().isEmpty();
    }

    record Profile(BlockPos anchor, int wallHeight, int gapWidth) {
        Profile {
            anchor = anchor == null ? null : anchor.immutable();
        }
    }

    private record ShapeCacheKey(long blockStateKey, long cellKey, int probeKey) {
    }

    private record ShapeCacheEntry(CollisionKind kind, boolean empty, long expiresAt) {
    }

    private record RefineKey(ResourceLocation dimension, BlockPos pivot, BlockPos target) {
    }

    private record RefineCursorState(int cursor, long touchedAt) {
    }

    private WallProfileScanner() {
    }

    static List<RayEvidence> scanEvidence(ServerLevel level, BlockPos pivot, BlockPos target, int maxHeight, int maxGap) {
        return scanEvidence(level, pivot, target, maxHeight, maxGap, 0.31D);
    }

    private static List<RayEvidence> scanEvidence(ServerLevel level, BlockPos pivot, BlockPos target, int maxHeight,
                                          int maxGap, double probeRadius) {
        return scanEvidence(level, pivot, target, maxHeight, maxGap, probeRadius, DIRECTIONAL_RAY_BUDGET);
    }

    static List<RayEvidence> scanEvidence(ServerLevel level, BlockPos pivot, BlockPos target, int maxHeight,
                                          int maxGap, double probeRadius, int rayBudget) {
        if (level == null || pivot == null || maxHeight < MIN_PROFILE_HEIGHT || maxGap < 2) {
            return List.of();
        }
        Vec3 start = Vec3.atBottomCenterOf(pivot).add(0.0D, 0.9D, 0.0D);
        return scanEvidence(level, pivot, target, maxHeight, maxGap, start, directionsToward(start, target),
                VERTICAL_PITCHES, Math.min(DIRECTIONAL_RAY_BUDGET, Math.max(0, rayBudget)), probeRadius);
    }

    static List<RayEvidence> scanEvidenceFallback(ServerLevel level, BlockPos pivot, BlockPos target, int maxHeight, int maxGap) {
        return scanEvidenceFallback(level, pivot, target, maxHeight, maxGap, 0.31D);
    }

    static List<RayEvidence> scanEvidenceFallback(ServerLevel level, BlockPos pivot, BlockPos target, int maxHeight,
                                                  int maxGap, double probeRadius) {
        return scanEvidenceFallback(level, pivot, target, maxHeight, maxGap, probeRadius, FALLBACK_RAY_BUDGET);
    }

    static List<RayEvidence> scanEvidenceFallback(ServerLevel level, BlockPos pivot, BlockPos target, int maxHeight,
                                                  int maxGap, double probeRadius, int rayBudget) {
        return scanEvidenceFallback(level, pivot, target, maxHeight, maxGap, probeRadius, rayBudget, false);
    }

    private static List<RayEvidence> scanEvidenceFallback(ServerLevel level, BlockPos pivot, BlockPos target, int maxHeight,
                                                          int maxGap, double probeRadius, int rayBudget, boolean core) {
        if (level == null || pivot == null || maxHeight < MIN_PROFILE_HEIGHT || maxGap < 2) {
            return List.of();
        }
        Vec3 start = Vec3.atBottomCenterOf(pivot).add(0.0D, 0.9D, 0.0D);
        return scanEvidenceCore(level, pivot, target, maxHeight, maxGap, start, fallbackDirections(), FALLBACK_PITCHES,
                Math.min(FALLBACK_RAY_BUDGET, Math.max(0, rayBudget)), probeRadius);
    }

    private static Profile scan(ServerLevel level, BlockPos pivot, BlockPos target, int maxHeight, int maxGap) {
        return scan(level, pivot, target, maxHeight, maxGap, 0.31D);
    }

    private static Profile scan(ServerLevel level, BlockPos pivot, BlockPos target, int maxHeight, int maxGap, double probeRadius) {
        return scan(level, pivot, target, maxHeight, maxGap, probeRadius, false);
    }

    private static Profile scan(ServerLevel level, BlockPos pivot, BlockPos target, int maxHeight, int maxGap,
                        double probeRadius, boolean pressured) {
        if (level == null || pivot == null || maxHeight < MIN_PROFILE_HEIGHT || maxGap < 2) {
            return null;
        }
        Vec3 start = Vec3.atBottomCenterOf(pivot).add(0.0D, 0.9D, 0.0D);
        long now = level.getGameTime();
        List<RayEvidence> evidence = scanEvidenceCore(level, pivot, target, maxHeight, maxGap,
                start, directionsToward(start, target), VERTICAL_PITCHES,
                pressured ? Math.max(16, DIRECTIONAL_RAY_BUDGET / 2) : DIRECTIONAL_RAY_BUDGET, probeRadius);
        Profile profile = scanDirections(level, pivot, target, start, evidence, now,
                VERTICAL_PITCHES, pressured ? Math.max(8, DIRECTIONAL_RAY_BUDGET / 2) : DIRECTIONAL_RAY_BUDGET,
                maxHeight, maxGap, probeRadius, pressured);
        if (profile != null) return profile;
        List<RayEvidence> fallbackEvidence = scanEvidenceCore(level, pivot, target, maxHeight, maxGap, start,
                fallbackDirections(), FALLBACK_PITCHES,
                pressured ? Math.max(16, FALLBACK_RAY_BUDGET / 2) : FALLBACK_RAY_BUDGET, probeRadius);
        return scanDirections(level, pivot, target, start, fallbackEvidence, now,
                FALLBACK_PITCHES, pressured ? Math.max(8, FALLBACK_RAY_BUDGET / 2) : FALLBACK_RAY_BUDGET,
                maxHeight, maxGap, probeRadius, pressured);
    }

    private static Profile profileFromEvidence(ServerLevel level, BlockPos pivot, BlockPos target, TargetEvidence evidence,
                                       double probeRadius, boolean pressured) {
        if (level == null || pivot == null || target == null || evidence == null) {
            return null;
        }
        if (bucketKey(evidence.targetBucket()) != bucketKey(target)) {
            return null;
        }
        if (evidence.anchor() == null || !isSafeAnchor(level, evidence.anchor())) {
            return null;
        }
        if (evidence.wallFace() == null || evidence.supportCell() == null) {
            return null;
        }
        if (!level.isLoaded(evidence.wallFace()) || !level.isLoaded(evidence.supportCell())) {
            return null;
        }
        if (level.getBlockState(evidence.wallFace()).getCollisionShape(level, evidence.wallFace(), CollisionContext.empty()).isEmpty()) {
            return null;
        }
        if (level.getBlockState(evidence.supportCell()).getCollisionShape(level, evidence.supportCell(), CollisionContext.empty()).isEmpty()) {
            return null;
        }
        if (!validateHazardCells(level, evidence.hazardCells(), evidence.hazardKinds(), pivot, evidence.anchor())) {
            return null;
        }
        if (!validateTypedHazards(level, evidence.hazardCells(), evidence.hazardKinds())) {
            return null;
        }
        int wallHeight = Math.min(LivingWallConfig.MAX_HEIGHT.get(),
                Math.max(MIN_PROFILE_HEIGHT, Math.abs(evidence.anchor().getY() - pivot.getY()) + 1));
        int gapWidth = Math.max(1, Mth.ceil(bridgeLength(pivot, evidence.anchor())));
        return new Profile(evidence.anchor(), wallHeight, gapWidth);
    }

    private static List<Vec3> directionsToward(Vec3 start, BlockPos target) {
        List<Vec3> directions = new ArrayList<>();
        if (target == null) {
            for (int index = 0; index < 8; index++) {
                double angle = (Math.PI * 2.0D * index) / 8.0D;
                directions.add(new Vec3(Math.cos(angle), 0.0D, Math.sin(angle)));
            }
            return directions;
        }
        Vec3 delta = Vec3.atCenterOf(target).subtract(start);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 1.0D) return directionsToward(start, null);
        double base = Math.atan2(delta.z, delta.x);
        double[] offsets = {0.0D, Math.toRadians(20.0D), Math.toRadians(-20.0D), Math.toRadians(40.0D), Math.toRadians(-40.0D)};
        for (double offset : offsets) {
            double angle = base + offset;
            directions.add(new Vec3(Math.cos(angle), 0.0D, Math.sin(angle)));
        }
        return directions;
    }

    private static List<Vec3> fallbackDirections() {
        List<Vec3> fallback = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            double angle = (Math.PI * 2.0D * index) / 16.0D;
            fallback.add(new Vec3(Math.cos(angle), 0.0D, Math.sin(angle)));
        }
        return fallback;
    }

    private static List<RayEvidence> scanEvidence(ServerLevel level, BlockPos pivot, BlockPos target, int maxHeight,
                                                   int maxGap, Vec3 start, List<Vec3> directions, double[] pitches,
                                                   int rayBudget, double probeRadius) {
        return scanEvidenceCore(level, pivot, target, maxHeight, maxGap, start, directions, pitches, rayBudget, probeRadius);
    }

    private static List<RayEvidence> scanEvidenceCore(ServerLevel level, BlockPos pivot, BlockPos target, int maxHeight,
                                                   int maxGap, Vec3 start, List<Vec3> directions, double[] pitches,
                                                   int rayBudget, double probeRadius) {
        List<RayEvidence> rays = new ArrayList<>();
        double horizontalRange = maxGap + 0.75D;
        double verticalRange = maxHeight + 1.0D;
        for (Vec3 direction : directions) {
            for (double pitch : pitches) {
                if (rays.size() >= rayBudget) {
                    return rays;
                }
                Vec3 end = start.add(direction.scale(horizontalRange)).add(0.0D, pitch * verticalRange, 0.0D);
                rays.add(scanRay(level, start, end, direction, rays.size(), pitch, probeRadius));
            }
        }
        return rays;
    }

    private static Profile scanDirections(ServerLevel level, BlockPos pivot, BlockPos target, Vec3 start,
                                          List<RayEvidence> evidence, long now, double[] pitches, int rayBudget,
                                          int maxHeight, int maxGap, double probeRadius, boolean pressured) {
        Set<BlockPos> seenHits = new HashSet<>();
        Set<BlockPos> observedStructure = new HashSet<>();
        for (RayEvidence ray : evidence) {
            if (ray != null) observedStructure.addAll(ray.structuralCells());
        }
        List<ProfileCandidate> candidates = new ArrayList<>();
        int rays = 0;
        for (RayEvidence ray : evidence) {
            if (rays++ >= rayBudget) return chooseCandidate(level, pivot, target, candidates, maxHeight, maxGap);
            BlockPos hit = ray.firstCollisionCell();
            if (hit == null || !seenHits.add(hit)) continue;
            addCandidates(level, pivot, target, hit, candidates, maxHeight, maxGap, observedStructure);
        }
        if (candidates.isEmpty()) {
            List<RayEvidence> refined = refineEvidence(level, pivot, target, evidence, now, rayBudget, probeRadius, pressured);
            for (RayEvidence ray : refined) {
                BlockPos hit = ray.firstCollisionCell();
                if (hit == null || !seenHits.add(hit)) continue;
                addCandidates(level, pivot, target, hit, candidates, maxHeight, maxGap, observedStructure);
            }
        }
        return chooseCandidate(level, pivot, target, candidates, maxHeight, maxGap);
    }

    private static List<RayEvidence> refineEvidence(ServerLevel level, BlockPos pivot, BlockPos target,
                                                    List<RayEvidence> base, long now, int rayBudget, double probeRadius,
                                                    boolean pressured) {
        List<RayEvidence> refined = new ArrayList<>();
        if (base.isEmpty()) return refined;
        RefineKey key = new RefineKey(level.dimension().location(), pivot, target);
        int budget = Math.min(MAX_REFINE_RAYS_PER_TICK, Math.max(0, rayBudget));
        if (pressured) {
            budget = Math.max(1, budget / 2);
        }
        int startIndex;
        synchronized (REFINE_CURSOR) {
            RefineCursorState state = REFINE_CURSOR.get(key);
            if (state == null || now - state.touchedAt() > REFINE_CURSOR_TTL_TICKS) {
                REFINE_CURSOR.put(key, new RefineCursorState(0, now));
                startIndex = 0;
            } else {
                startIndex = state.cursor();
            }
        }
        int inspected = 0;
        for (int offset = 0; offset < base.size() && refined.size() < budget; offset++) {
            int index = (startIndex + offset) % base.size();
            RayEvidence ray = base.get(index);
            inspected++;
            if (ray == null || ray.unloadedBoundary()) continue;
            Vec3 direction = ray.direction();
            Vec3 left = new Vec3(-direction.z, 0.0D, direction.x);
            Vec3 right = new Vec3(direction.z, 0.0D, -direction.x);
            List<RayEvidence> rerays = scanEvidenceCore(level, pivot, target, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pivot.getX(), pivot.getZ()),
                    LivingWallConfig.MAX_GAP.get(), ray.origin(),
                    List.of(direction.add(left.scale(0.25D)).normalize(),
                            direction.add(right.scale(0.25D)).normalize()), new double[] {ray.verticalSample()},
                    Math.min(2, budget - refined.size()), probeRadius);
            for (RayEvidence reray : rerays) {
                if (refined.size() >= budget) break;
                refined.add(reray);
            }
        }
        synchronized (REFINE_CURSOR) {
            REFINE_CURSOR.put(key, new RefineCursorState((startIndex + inspected) % base.size(), now));
            expireRefineCursor(now);
        }
        return refined;
    }

    private static long bucketKey(BlockPos pos) {
        if (pos == null) return Long.MIN_VALUE;
        return BlockPos.asLong(Math.floorDiv(pos.getX(), 4), Math.floorDiv(pos.getY(), 4), Math.floorDiv(pos.getZ(), 4));
    }

    private static boolean validateHazardCells(ServerLevel level, List<BlockPos> hazardCells,
                                               List<CollisionKind> hazardKinds, BlockPos pivot,
                                               BlockPos anchor) {
        if (hazardCells == null || hazardCells.isEmpty()) return true;
        int checked = 0;
        for (int index = 0; index < hazardCells.size(); index++) {
            BlockPos cell = hazardCells.get(index);
            if (cell == null) continue;
            if (!level.isLoaded(cell)) return false;
            CollisionKind actual = classifyCollision(level, cell);
            if (!isHazardKind(actual)) return false;
            if (hazardKinds != null && index < hazardKinds.size()) {
                CollisionKind expected = hazardKinds.get(index);
                if (expected != null && expected != actual) return false;
            }
            if (pivot != null && anchor != null && checked++ >= 16) break;
        }
        return true;
    }

    private static boolean validateTypedHazards(ServerLevel level, List<BlockPos> hazardCells, List<CollisionKind> hazardKinds) {
        if (hazardCells == null || hazardCells.isEmpty()) return true;
        for (int index = 0; index < hazardCells.size(); index++) {
            BlockPos cell = hazardCells.get(index);
            if (cell == null || !level.isLoaded(cell)) return false;
            CollisionKind actual = classifyCollision(level, cell);
            if (!isHazardKind(actual)) return false;
            if (hazardKinds != null && index < hazardKinds.size()) {
                CollisionKind expected = hazardKinds.get(index);
                if (expected != null && expected != actual) return false;
            }
        }
        return true;
    }

    private static void addCandidates(ServerLevel level, BlockPos pivot, BlockPos target, BlockPos hit,
                                      List<ProfileCandidate> candidates, int maxHeight, int maxGap,
                                      Set<BlockPos> observedStructure) {
        if (isHazard(level.getBlockState(hit))) return;
        if (!hasConnectedBoundaryEvidence(hit, observedStructure)) return;
        BlockPos[] anchors = {hit.above(), hit.north(), hit.south(), hit.east(), hit.west()};
        for (BlockPos anchor : anchors) {
            if (!isSafeAnchor(level, anchor)) continue;
            int vertical = anchor.getY() - pivot.getY();
            double horizontal = horizontalDistance(pivot, anchor);
            if (Math.abs(vertical) > maxHeight || horizontal < 1.5D || horizontal > maxGap + 1.0D) continue;
            int evidence = wallEvidence(level, hit);
            if (target != null && !isThreatFacing(pivot, anchor, target)) continue;
            double score = horizontal + Math.abs(vertical) * 0.35D - evidence * 0.4D;
            if (target != null) {
                score += Math.sqrt(Vec3.atBottomCenterOf(anchor).distanceToSqr(Vec3.atCenterOf(target))) * 0.75D;
            }
            candidates.add(new ProfileCandidate(anchor, score));
        }
    }

    private static boolean hasConnectedBoundaryEvidence(BlockPos hit, Set<BlockPos> observedStructure) {
        if (hit == null || observedStructure == null || observedStructure.isEmpty()) return false;
        int connected = 0;
        boolean hasNonSupportNeighbor = false;
        for (BlockPos cell : observedStructure) {
            if (cell == null || cell.distManhattan(hit) > 2) continue;
            int distance = Math.abs(cell.getX() - hit.getX()) + Math.abs(cell.getY() - hit.getY())
                    + Math.abs(cell.getZ() - hit.getZ());
            if (distance == 0 || distance == 1) {
                connected++;
                if (cell.getY() >= hit.getY()) hasNonSupportNeighbor = true;
            }
        }
        return connected >= 2 && hasNonSupportNeighbor;
    }

    static boolean isThreatFacing(BlockPos pivot, BlockPos anchor, BlockPos target) {
        return threatProgressProjection(pivot, anchor, target) >= 1.0D;
    }

    static List<BlockPos> exteriorLandingCandidates(BlockPos structural, BlockPos pivot,
                                                     Vec3 rayDirection, int maxHorizontalDistance,
                                                     int maxVerticalDistance) {
        if (structural == null || pivot == null || rayDirection == null
                || maxHorizontalDistance <= 0 || maxVerticalDistance < 0) return List.of();
        int normalX = directionSign(rayDirection.x);
        int normalZ = directionSign(rayDirection.z);
        if (normalX == 0 && normalZ == 0) {
            normalX = Integer.compare(structural.getX(), pivot.getX());
            normalZ = Integer.compare(structural.getZ(), pivot.getZ());
        }
        if (normalX == 0 && normalZ == 0) return List.of();
        int lateralX = -normalZ;
        int lateralZ = normalX;
        Set<BlockPos> candidates = new LinkedHashSet<>();
        for (int distance = 1; distance <= maxHorizontalDistance; distance++) {
            for (int vertical = 0; vertical <= maxVerticalDistance; vertical++) {
                addExteriorCandidate(candidates, structural, normalX, normalZ, lateralX, lateralZ,
                        distance, vertical);
                if (vertical > 0) addExteriorCandidate(candidates, structural, normalX, normalZ, lateralX, lateralZ,
                        distance, -vertical);
            }
        }
        return List.copyOf(candidates);
    }

    private static void addExteriorCandidate(Set<BlockPos> candidates, BlockPos structural,
                                             int normalX, int normalZ, int lateralX, int lateralZ,
                                             int distance, int vertical) {
        BlockPos direct = structural.offset(normalX * distance, vertical, normalZ * distance);
        addCandidate(candidates, direct);
        addCandidate(candidates, direct.offset(lateralX, 0, lateralZ));
        addCandidate(candidates, direct.offset(-lateralX, 0, -lateralZ));
        addCandidate(candidates, structural.offset(lateralX * distance, vertical, lateralZ * distance));
        addCandidate(candidates, structural.offset(-lateralX * distance, vertical, -lateralZ * distance));
        if (normalX != 0 && normalZ != 0) {
            addCandidate(candidates, structural.offset(normalX * distance, vertical, 0));
            addCandidate(candidates, structural.offset(0, vertical, normalZ * distance));
        }
    }

    private static void addCandidate(Set<BlockPos> candidates, BlockPos candidate) {
        candidates.add(candidate.immutable());
    }

    private static int directionSign(double value) {
        return value < -EPSILON ? -1 : value > EPSILON ? 1 : 0;
    }

    static double threatProgressProjection(BlockPos pivot, BlockPos anchor, BlockPos target) {
        if (pivot == null || anchor == null || target == null) return Double.NEGATIVE_INFINITY;
        double anchorX = anchor.getX() - pivot.getX();
        double anchorZ = anchor.getZ() - pivot.getZ();
        double targetX = target.getX() - pivot.getX();
        double targetZ = target.getZ() - pivot.getZ();
        double anchorLength = Math.sqrt(anchorX * anchorX + anchorZ * anchorZ);
        double targetLength = Math.sqrt(targetX * targetX + targetZ * targetZ);
        if (anchorLength < 1.0E-6D || targetLength < 1.0E-6D) return Double.NEGATIVE_INFINITY;
        double unitTargetX = targetX / targetLength;
        double unitTargetZ = targetZ / targetLength;
        return anchorX * unitTargetX + anchorZ * unitTargetZ;
    }

    private static Profile chooseCandidate(ServerLevel level, BlockPos pivot, BlockPos target,
                                           List<ProfileCandidate> candidates, int maxHeight, int maxGap) {
        candidates.sort(java.util.Comparator.comparingDouble(ProfileCandidate::score));
        int checked = 0;
        for (ProfileCandidate candidate : candidates) {
            if (checked++ >= 12) break;
            if (!clearCorridor(level, pivot, candidate.anchor(), maxHeight, maxGap)) continue;
            int wallHeight = Math.min(maxHeight,
                    Math.max(MIN_PROFILE_HEIGHT, Math.abs(candidate.anchor().getY() - pivot.getY()) + 1));
            int gapWidth = Math.max(1, Mth.ceil(bridgeLength(pivot, candidate.anchor())));
            return new Profile(candidate.anchor(), wallHeight, gapWidth);
        }
        return null;
    }

    private static boolean clearCorridor(ServerLevel level, BlockPos pivot, BlockPos anchor,
                                         int maxHeight, int maxGap) {
        int steps = Math.max(Math.max(Math.abs(anchor.getX() - pivot.getX()),
                Math.abs(anchor.getZ() - pivot.getZ())), Math.abs(anchor.getY() - pivot.getY()));
        for (int step = 1; step < steps; step++) {
            double fraction = step / (double) steps;
            Vec3 point = Vec3.atBottomCenterOf(pivot).lerp(Vec3.atBottomCenterOf(anchor), fraction);
            AABB box = new AABB(point.x - 0.31D, point.y, point.z - 0.31D,
                    point.x + 0.31D, point.y + 1.8D, point.z + 0.31D);
            int minX = Mth.floor(box.minX);
            int maxX = Mth.floor(box.maxX - 1.0E-7D);
            int minY = Mth.floor(box.minY);
            int maxY = Mth.floor(box.maxY - 1.0E-7D);
            int minZ = Mth.floor(box.minZ);
            int maxZ = Mth.floor(box.maxZ - 1.0E-7D);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos cell = new BlockPos(x, y, z);
                        BlockState state = level.getBlockState(cell);
                        if (isHazard(state)
                                || !state.getCollisionShape(level, cell, CollisionContext.empty()).isEmpty()) return false;
                    }
                }
            }
        }
        return true;
    }

    private static int wallEvidence(ServerLevel level, BlockPos hit) {
        int evidence = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos cell = hit.offset(dx, dy, dz);
                    if (!level.getBlockState(cell).getCollisionShape(level, cell, CollisionContext.empty()).isEmpty()) {
                        evidence++;
                    }
                }
            }
        }
        return evidence;
    }

    private static boolean isHazard(BlockState state) {
        return state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
                || state.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock
                && state.hasProperty(net.minecraft.world.level.block.TrapDoorBlock.OPEN)
                && state.getValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN);
    }

    static boolean isHazardKind(CollisionKind kind) {
        return kind == CollisionKind.LAVA || kind == CollisionKind.OPEN_TRAPDOOR
                || kind == CollisionKind.VOID || kind == CollisionKind.VOID_EXPOSED || kind == CollisionKind.PORTAL;
    }

    private static boolean isPortal(BlockState state) {
        return state != null && (state.getBlock() instanceof net.minecraft.world.level.block.NetherPortalBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.EndGatewayBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.EndPortalBlock);
    }

    private static boolean isVoidExposure(ServerLevel level, BlockPos cell, BlockState state) {
        if (level == null || state == null || !state.getFluidState().isEmpty()
                || !state.getCollisionShape(level, cell, CollisionContext.empty()).isEmpty()) return false;
        for (int depth = 1; depth <= 4; depth++) {
            BlockPos below = cell.below(depth);
            if (!level.isLoaded(below)) return false;
            BlockState belowState = level.getBlockState(below);
            if (!belowState.getFluidState().isEmpty()
                    || !belowState.getCollisionShape(level, below, CollisionContext.empty()).isEmpty()) return false;
        }
        return true;
    }

    static CollisionKind projectVoidExposure(ServerLevel level, BlockPos cell) {
        if (level == null || cell == null) {
            return CollisionKind.UNKNOWN;
        }
        return projectVoidExposure(cell, level::isLoaded, level::getBlockState,
                (probeCell, state) -> state != null
                        && state.getFluidState().isEmpty()
                        && state.getCollisionShape(level, probeCell, CollisionContext.empty()).isEmpty());
    }

    static CollisionKind projectVoidExposure(BlockPos cell, Predicate<BlockPos> loaded,
                                             Function<BlockPos, BlockState> stateProvider,
                                             BiPredicate<BlockPos, BlockState> emptyProbe) {
        if (cell == null || loaded == null || stateProvider == null || emptyProbe == null) {
            return CollisionKind.UNKNOWN;
        }
        if (!loaded.test(cell)) {
            return CollisionKind.UNLOADED_BOUNDARY;
        }
        BlockState state = stateProvider.apply(cell);
        if (!emptyProbe.test(cell, state)) {
            return CollisionKind.NONE;
        }
        for (int depth = 1; depth <= 4; depth++) {
            BlockPos below = cell.below(depth);
            if (!loaded.test(below)) {
                return CollisionKind.UNKNOWN;
            }
            BlockState belowState = stateProvider.apply(below);
            if (!emptyProbe.test(below, belowState)) {
                return CollisionKind.NONE;
            }
        }
        return CollisionKind.VOID_EXPOSED;
    }

    private static double bridgeLength(BlockPos first, BlockPos second) {
        double dx = second.getX() - first.getX();
        double dy = second.getY() - first.getY();
        double dz = second.getZ() - first.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double horizontalDistance(BlockPos a, BlockPos b) {
        double dx = b.getX() - a.getX();
        double dz = b.getZ() - a.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static boolean isSafeAnchor(ServerLevel level, BlockPos anchor) {
        if (!level.isLoaded(anchor) || !level.isLoaded(anchor.below()) || !level.isLoaded(anchor.above())) {
            return false;
        }
        BlockState feet = level.getBlockState(anchor);
        BlockState head = level.getBlockState(anchor.above());
        BlockState support = level.getBlockState(anchor.below());
        if (feet.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
                || support.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
                || feet.is(net.minecraft.tags.BlockTags.TRAPDOORS)) {
            return false;
        }
        return feet.getCollisionShape(level, anchor, CollisionContext.empty()).isEmpty()
                && head.getCollisionShape(level, anchor.above(), CollisionContext.empty()).isEmpty()
                && LivingWallCoordinator.isReliableSupportShape(support.getCollisionShape(level, anchor.below(), CollisionContext.empty()));
    }

    private record Hit(BlockPos block, double distance) {
    }

    private record ProfileCandidate(BlockPos anchor, double score) {
    }

    private static Hit firstCollision(ServerLevel level, Vec3 start, Vec3 end, CollisionContext context) {
        return firstCollision(level, start, end, context, false);
    }

    private static Hit firstCollision(ServerLevel level, Vec3 start, Vec3 end, CollisionContext context,
                                      boolean skipLeaves) {
        return firstCollision(level, start, end, context, skipLeaves, false);
    }

    private static Hit firstCollision(ServerLevel level, Vec3 start, Vec3 end, CollisionContext context,
                                      boolean skipLeaves, boolean skipHazards) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        int x = Mth.floor(start.x);
        int y = Mth.floor(start.y);
        int z = Mth.floor(start.z);
        int endX = Mth.floor(end.x);
        int endY = Mth.floor(end.y);
        int endZ = Mth.floor(end.z);
        int stepX = Integer.compare(endX, x);
        int stepY = Integer.compare(endY, y);
        int stepZ = Integer.compare(endZ, z);
        double invX = stepX == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(dx);
        double invY = stepY == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(dy);
        double invZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(dz);
        double nextX = stepX == 0 ? Double.POSITIVE_INFINITY : ((stepX > 0 ? x + 1.0D - start.x : start.x - x) * invX);
        double nextY = stepY == 0 ? Double.POSITIVE_INFINITY : ((stepY > 0 ? y + 1.0D - start.y : start.y - y) * invY);
        double nextZ = stepZ == 0 ? Double.POSITIVE_INFINITY : ((stepZ > 0 ? z + 1.0D - start.z : start.z - z) * invZ);
        double deltaX = invX;
        double deltaY = invY;
        double deltaZ = invZ;
        int maxSteps = 4 + Math.abs(endX - x) + Math.abs(endY - y) + Math.abs(endZ - z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int step = 0; step < maxSteps; step++) {
            pos.set(x, y, z);
            if (!level.isLoaded(pos)) {
                return null;
            }
            BlockState state = level.getBlockState(pos);
            if (skipHazards && isHazard(state)) {
                if (x == endX && y == endY && z == endZ) break;
                double min = Math.min(nextX, Math.min(nextY, nextZ));
                if (nextX - min <= EPSILON) {
                    x += stepX;
                    nextX += deltaX;
                }
                if (nextY - min <= EPSILON) {
                    y += stepY;
                    nextY += deltaY;
                }
                if (nextZ - min <= EPSILON) {
                    z += stepZ;
                    nextZ += deltaZ;
                }
                continue;
            }
            VoxelShape shape = state.getCollisionShape(level, pos, context);
            if (!shape.isEmpty() && (!skipLeaves || !state.is(net.minecraft.tags.BlockTags.LEAVES))) {
                for (AABB part : shape.toAabbs()) {
                    AABB worldBox = part.move(pos);
                    if (worldBox.inflate(0.31D).clip(start, end).isPresent()) {
                        return new Hit(pos.immutable(), start.distanceTo(Vec3.atCenterOf(pos)));
                    }
                }
            }
            if (x == endX && y == endY && z == endZ) {
                break;
            }
            double min = Math.min(nextX, Math.min(nextY, nextZ));
            if (nextX - min <= EPSILON) {
                x += stepX;
                nextX += deltaX;
            }
            if (nextY - min <= EPSILON) {
                y += stepY;
                nextY += deltaY;
            }
            if (nextZ - min <= EPSILON) {
                z += stepZ;
                nextZ += deltaZ;
            }
        }
        return null;
    }

    private static RayEvidence scanRay(ServerLevel level, Vec3 start, Vec3 end, Vec3 direction, long rayId,
                                       double verticalSample, double probeRadius) {
        List<BlockPos> evidence = new ArrayList<>();
        List<CollisionKind> kinds = new ArrayList<>();
        List<BlockPos> structural = new ArrayList<>();
        BlockPos firstCollision = null;
        boolean unloadedBoundary = false;
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        int x = Mth.floor(start.x);
        int y = Mth.floor(start.y);
        int z = Mth.floor(start.z);
        int endX = Mth.floor(end.x);
        int endY = Mth.floor(end.y);
        int endZ = Mth.floor(end.z);
        int stepX = Integer.compare(endX, x);
        int stepY = Integer.compare(endY, y);
        int stepZ = Integer.compare(endZ, z);
        double invX = stepX == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(dx);
        double invY = stepY == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(dy);
        double invZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(dz);
        double nextX = stepX == 0 ? Double.POSITIVE_INFINITY : ((stepX > 0 ? x + 1.0D - start.x : start.x - x) * invX);
        double nextY = stepY == 0 ? Double.POSITIVE_INFINITY : ((stepY > 0 ? y + 1.0D - start.y : start.y - y) * invY);
        double nextZ = stepZ == 0 ? Double.POSITIVE_INFINITY : ((stepZ > 0 ? z + 1.0D - start.z : start.z - z) * invZ);
        double deltaX = invX;
        double deltaY = invY;
        double deltaZ = invZ;
        int maxSteps = 4 + Math.abs(endX - x) + Math.abs(endY - y) + Math.abs(endZ - z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        CollisionContext context = CollisionContext.empty();
        int maxStructuralCells = MAX_EVIDENCE_CELLS_PER_RAY;
        int maxHazardCells = MAX_EVIDENCE_CELLS_PER_RAY * 4;
        int hazardCount = 0;
        for (int step = 0; step < maxSteps
                && (structural.size() < maxStructuralCells || hazardCount < maxHazardCells); step++) {
            pos.set(x, y, z);
            if (!level.isLoaded(pos)) {
                unloadedBoundary = true;
                break;
            }
            BlockState state = level.getBlockState(pos);
            CollisionKind kind = classifyCollision(level, pos);
            if (isHazardKind(kind)) {
                if (hazardCount++ < maxHazardCells && evidence.size() < maxStructuralCells + maxHazardCells) {
                    appendPairedEvidence(evidence, kinds, pos.immutable(), kind);
                }
            }
            VoxelShape shape = state.getCollisionShape(level, pos, context);
            if (!shape.isEmpty() && !isHazardKind(kind)) {
                for (AABB part : shape.toAabbs()) {
                    if (part.move(pos).inflate(probeRadius).clip(start, end).isPresent()) {
                        if (firstCollision == null && kind != CollisionKind.THIN) {
                            firstCollision = pos.immutable();
                        }
                        BlockPos structuralCell = pos.immutable();
                        if (structural.isEmpty() || !structural.get(structural.size() - 1).equals(structuralCell)) {
                            structural.add(structuralCell);
                        }
                        if (structural.size() <= maxStructuralCells
                                && evidence.size() < maxStructuralCells + maxHazardCells) {
                            appendPairedEvidence(evidence, kinds, structuralCell,
                                    kind == CollisionKind.NONE ? CollisionKind.SOLID : kind);
                        }
                    }
                }
            }
            if (x == endX && y == endY && z == endZ) break;
            double min = Math.min(nextX, Math.min(nextY, nextZ));
            if (nextX - min <= EPSILON) {
                x += stepX;
                nextX += deltaX;
            }
            if (nextY - min <= EPSILON) {
                y += stepY;
                nextY += deltaY;
            }
            if (nextZ - min <= EPSILON) {
                z += stepZ;
                nextZ += deltaZ;
            }
        }
        if (firstCollision == null && !structural.isEmpty()) {
            firstCollision = structural.get(0);
        }
        return new RayEvidence(rayId, start, direction, firstCollision, evidence, kinds, structural,
                unloadedBoundary, probeRadius, verticalSample);
    }

    private static void appendPairedEvidence(List<BlockPos> cells, List<CollisionKind> kinds,
                                              BlockPos cell, CollisionKind kind) {
        if (cell == null || kind == null) return;
        cells.add(cell.immutable());
        kinds.add(kind);
    }

    private static CollisionKind classifyCollision(ServerLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        if (isPortal(state)) return CollisionKind.PORTAL;
        if (isHazard(state)) {
            return state.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock ? CollisionKind.OPEN_TRAPDOOR : CollisionKind.LAVA;
        }
        CollisionKind projection = projectVoidExposure(level, cell);
        if (projection == CollisionKind.VOID_EXPOSED) return projection;
        return classifyCollisionCached(level.dimension().location(), level.getGameTime(), state, cell,
                state.getCollisionShape(level, cell, CollisionContext.empty()), state.is(net.minecraft.tags.BlockTags.LEAVES));
    }

    static CollisionKind classifyCollisionCached(ResourceLocation dimension, long now, BlockState state, BlockPos cell,
                                                 VoxelShape shape, boolean foliage) {
        if (shape == null || shape.isEmpty()) return CollisionKind.NONE;
        long blockStateKey = BuiltInRegistries.BLOCK.getId(state.getBlock()) * 31L + state.getValues().hashCode();
        long cellKey = cell.asLong();
        int probeKey = foliage ? 1 : 0;
        ShapeCacheKey key = new ShapeCacheKey(blockStateKey, cellKey, probeKey);
        synchronized (SHAPE_CACHE) {
            LinkedHashMap<ShapeCacheKey, ShapeCacheEntry> cache = SHAPE_CACHE.computeIfAbsent(dimension,
                    ignored -> new LinkedHashMap<>(16, 0.75F, true));
            ShapeCacheEntry cached = cache.get(key);
            if (cached != null && cached.expiresAt() > now) {
                return cached.kind();
            }
            CollisionKind kind = classifyCollisionShape(shape, foliage);
            expireShapeCache(cache, now);
            cache.put(key, new ShapeCacheEntry(kind, shape.isEmpty(), now + SHAPE_CACHE_TTL_TICKS));
            if (cache.size() > MAX_SHAPE_CACHE_ENTRIES) {
                trimShapeCache(cache);
            }
            if (cache.isEmpty()) {
                SHAPE_CACHE.remove(dimension);
            }
            return kind;
        }
    }

    static CollisionKind classifyCollisionShape(VoxelShape shape, boolean foliage) {
        if (shape == null || shape.isEmpty()) return CollisionKind.NONE;
        if (foliage) return CollisionKind.FOLIAGE;
        return shape.toAabbs().stream().anyMatch(box -> box.getXsize() < 0.2D || box.getZsize() < 0.2D)
                ? CollisionKind.THIN : CollisionKind.PARTIAL;
    }

    static boolean sweptIntersects(AABB part, Vec3 start, Vec3 end, double probeRadius) {
        return part.inflate(probeRadius).clip(start, end).isPresent();
    }

    static List<BlockPos> hazardCells(RayEvidence evidence) {
        if (evidence == null) return List.of();
        List<BlockPos> hazards = new ArrayList<>();
        for (int index = 0; index < Math.min(evidence.evidenceCells().size(), evidence.collisionKinds().size()); index++) {
            CollisionKind kind = evidence.collisionKinds().get(index);
            if (isHazardKind(kind)) {
                hazards.add(evidence.evidenceCells().get(index));
            }
        }
        return List.copyOf(hazards);
    }

    static String stateFingerprint(ServerLevel level, BlockPos cell) {
        if (level == null || cell == null || !level.isLoaded(cell)) return "unloaded";
        BlockState state = level.getBlockState(cell);
        CollisionKind kind = classifyCollision(level, cell);
        VoxelShape shape = state.getCollisionShape(level, cell, CollisionContext.empty());
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()) + ":" + kind + ":"
                + state.getFluidState().getType() + ":" + shape.isEmpty();
    }

    static int shapeCacheSize(ResourceLocation dimension) {
        synchronized (SHAPE_CACHE) {
            LinkedHashMap<ShapeCacheKey, ShapeCacheEntry> cache = SHAPE_CACHE.get(dimension);
            return cache == null ? 0 : cache.size();
        }
    }

    static void recordShapeCacheSample(ResourceLocation dimension, long now, long blockStateKey, long cellKey,
                                       int probeKey, CollisionKind kind) {
        synchronized (SHAPE_CACHE) {
            LinkedHashMap<ShapeCacheKey, ShapeCacheEntry> cache = SHAPE_CACHE.computeIfAbsent(dimension,
                    ignored -> new LinkedHashMap<>(16, 0.75F, true));
            expireShapeCache(cache, now);
            cache.put(new ShapeCacheKey(blockStateKey, cellKey, probeKey), new ShapeCacheEntry(kind, false, now + SHAPE_CACHE_TTL_TICKS));
            if (cache.size() > MAX_SHAPE_CACHE_ENTRIES) {
                trimShapeCache(cache);
            }
            if (cache.isEmpty()) {
                SHAPE_CACHE.remove(dimension);
            }
        }
    }

    private static void expireShapeCache(LinkedHashMap<ShapeCacheKey, ShapeCacheEntry> cache, long now) {
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private static void trimShapeCache(LinkedHashMap<ShapeCacheKey, ShapeCacheEntry> cache) {
        while (cache.size() > MAX_SHAPE_CACHE_ENTRIES) {
            ShapeCacheKey oldest = cache.keySet().iterator().next();
            cache.remove(oldest);
        }
    }

    static void clearCaches(ResourceLocation dimension) {
        synchronized (SHAPE_CACHE) {
            if (dimension == null) {
                SHAPE_CACHE.clear();
            } else {
                SHAPE_CACHE.remove(dimension);
            }
        }
        synchronized (REFINE_CURSOR) {
            if (dimension == null) {
                REFINE_CURSOR.clear();
            } else {
                REFINE_CURSOR.keySet().removeIf(key -> dimension.equals(key.dimension()));
            }
        }
    }

    private static void expireRefineCursor(long now) {
        REFINE_CURSOR.entrySet().removeIf(entry -> now - entry.getValue().touchedAt() > REFINE_CURSOR_TTL_TICKS);
        while (REFINE_CURSOR.size() > REFINE_CURSOR_MAX_ENTRIES) {
            RefineKey oldest = REFINE_CURSOR.keySet().iterator().next();
            REFINE_CURSOR.remove(oldest);
        }
    }

    static BlockPos firstCollisionBlock(ServerLevel level, Vec3 start, Vec3 end) {
        Hit hit = firstCollision(level, start, end, CollisionContext.empty());
        return hit == null ? null : hit.block();
    }

    static BlockPos firstNonFoliageCollisionBlock(ServerLevel level, Vec3 start, Vec3 end) {
        Hit hit = firstCollision(level, start, end, CollisionContext.empty(), true);
        return hit == null ? null : hit.block();
    }

    static BlockPos firstNonHazardCollisionBlock(ServerLevel level, Vec3 start, Vec3 end) {
        Hit hit = firstCollision(level, start, end, CollisionContext.empty(), true, true);
        return hit == null ? null : hit.block();
    }

}