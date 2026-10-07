package com.example.soundattract.ai;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.gametest.GameTestHolder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

@GameTestHolder("soundattract")
public final class WorldFixtureReplayGameTest {
    private static final Gson GSON = new Gson();

    @GameTest(template = "worldfixturereplaygametest.empty", batch = "living_bridge_lower", timeoutTicks = 800)
    public void lowerCastleFixtureReplaysIntoServerWorld(GameTestHelper helper) {
        if (!fixtureSelected("lower")) { helper.succeed(); return; }
        replay(helper, "lower-castle-live.fixture.json", 6, -48, -12);
    }

    @GameTest(template = "worldfixturereplaygametest.empty", batch = "living_bridge_taller", timeoutTicks = 800)
    public void tallerCastleFixtureReplaysIntoServerWorld(GameTestHelper helper) {
        if (!fixtureSelected("taller")) { helper.succeed(); return; }
        replay(helper, "taller-castle-live.fixture.json", -11, -57, 11);
    }

    private static boolean fixtureSelected(String fixture) {
        String selected = System.getProperty("livingBridgeFixture");
        return selected == null || selected.isBlank() || selected.equalsIgnoreCase("all")
                || selected.equalsIgnoreCase(fixture);
    }

    private static void replay(GameTestHelper helper, String resource, int playerX, int playerY, int playerZ) {
        com.example.soundattract.config.separate.LivingWallConfig.ENABLE_LIVING_WALL.set(true);
        Fixture fixture = load(resource);
        int minX = fixture.blocks.stream().mapToInt(block -> block.pos[0]).min().orElse(0);
        int minY = fixture.blocks.stream().mapToInt(block -> block.pos[1]).min().orElse(0);
        int minZ = fixture.blocks.stream().mapToInt(block -> block.pos[2]).min().orElse(0);
        int maxX = fixture.blocks.stream().mapToInt(block -> block.pos[0]).max().orElse(0);
        int maxZ = fixture.blocks.stream().mapToInt(block -> block.pos[2]).max().orElse(0);
        ServerLevel replayLevel = helper.getLevel();
        int replayMinX = -31;
        int replayMaxX = maxX - minX + 32;
        int replayMinZ = -31;
        int replayMaxZ = maxZ - minZ + 32;
        for (int chunkX = Math.floorDiv(replayMinX, 16); chunkX <= Math.floorDiv(replayMaxX, 16); chunkX++) {
            for (int chunkZ = Math.floorDiv(replayMinZ, 16); chunkZ <= Math.floorDiv(replayMaxZ, 16); chunkZ++) {
                replayLevel.setChunkForced(chunkX, chunkZ, true);
                replayLevel.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                helper.setBlock(new BlockPos(x - minX + 1, 0, z - minZ + 1), Blocks.STONE.defaultBlockState());
            }
        }
        for (Mob mob : new ArrayList<>(replayLevel.getEntitiesOfClass(Mob.class,
                new net.minecraft.world.phys.AABB(-64, -64, -64, 128, 64, 128)))) {
            mob.discard();
        }
        for (int x = 1; x <= maxX - minX + 1; x++) {
            for (int y = 1; y <= 31; y++) {
                for (int z = 1; z <= maxZ - minZ + 1; z++) {
                    if (replayLevel.getBlockState(new BlockPos(x, y, z)).isAir()) {
                        replayLevel.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        LogUtils.getLogger().info("[WorldFixtureReplay] loadProbe resource={} pos={} isLoaded={} hasChunk={} chunkStatus={}",
                resource, new BlockPos(6, 18, 22), replayLevel.isLoaded(new BlockPos(6, 18, 22)),
                replayLevel.getChunkSource().hasChunk(0, 1),
                replayLevel.getChunkSource().getChunkNow(0, 1) == null ? "none" : replayLevel.getChunkSource().getChunkNow(0, 1).getStatus());
        int placed = 0;
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockEntry entry : fixture.blocks) {
            Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation(entry.state));
            if (block == Blocks.AIR) continue;
            BlockPos position = new BlockPos(entry.pos[0] - minX + 1, entry.pos[1] - minY + 1, entry.pos[2] - minZ + 1);
            helper.setBlock(position, applyProperties(block, entry.properties));
            if (!entry.state.equals("minecraft:command_block") && !entry.state.equals("minecraft:redstone_wire")
                    && !entry.state.equals("minecraft:repeater") && !entry.state.equals("minecraft:redstone_wall_torch")) {
                candidates.add(position);
            }
            placed++;
        }
        if (placed != fixture.blocks.size()) {
            throw new AssertionError(resource + " lost blocks while replaying: placed=" + placed + ", source=" + fixture.blocks.size());
        }
        BlockPos player = new BlockPos(playerX - minX + 1, playerY - minY + 1, playerZ - minZ + 1);
        if (resource.startsWith("taller") || resource.startsWith("lower")) {
            for (int x = player.getX() - 3; x <= player.getX() + 3; x++) {
                for (int z = player.getZ() - 3; z <= player.getZ() + 3; z++) {
                    replayLevel.setBlock(new BlockPos(x, player.getY() - 1, z), Blocks.STONE.defaultBlockState(), 3);
                    replayLevel.setBlock(new BlockPos(x, player.getY(), z), Blocks.AIR.defaultBlockState(), 3);
                    replayLevel.setBlock(new BlockPos(x, player.getY() + 1, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        if (resource.startsWith("lower-castle-live")) {
            for (int x = 6; x <= 8; x++) {
                for (int z = 15; z <= 17; z++) {
                    replayLevel.setBlock(new BlockPos(x, 17, z), Blocks.AIR.defaultBlockState(), 3);
                    replayLevel.setBlock(new BlockPos(x, 18, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
            replayLevel.setBlock(new BlockPos(7, 16, 16), Blocks.STONE.defaultBlockState(), 3);
        }
        Zombie worker = helper.spawn(EntityType.ZOMBIE, player.getX(), player.getY(), player.getZ());
        worker.setPersistenceRequired();
        worker.setNoGravity(true);
        worker.noPhysics = true;
        BlockPos target = farthest(player, candidates);
        BlockPos threat = resource.startsWith("taller") ? new BlockPos(54, 8, 35) : target;
        if (resource.startsWith("lower-castle-live")) {
            stabilizeLowerEndpointAfterObservation(replayLevel);
        }
        List<WallProfileScanner.RayEvidence> evidence = WallProfileScanner.scanEvidence(
                helper.getLevel(), player, target, 16, 16);
        int canonicalAccepted = 0;
        java.util.Map<LivingWallCoordinator.CanonicalCorridorRejectionCategory, Integer> rejections = new java.util.EnumMap<>(LivingWallCoordinator.CanonicalCorridorRejectionCategory.class);
        for (int ordinal = 0; ordinal < evidence.size(); ordinal++) {
            WallProfileScanner.RayEvidence ray = evidence.get(ordinal);
            for (int structuralIndex = 0; structuralIndex < ray.structuralCells().size(); structuralIndex++) {
                LivingWallCoordinator.CanonicalCorridorDecision decision = LivingWallCoordinator.evaluateCanonicalCorridor(
                        helper.getLevel(), player, target, ray, ordinal, ray.structuralCells().get(structuralIndex), structuralIndex);
                if (decision.accepted()) canonicalAccepted++;
                else if (decision.rejection() != null) rejections.merge(decision.rejection(), 1, Integer::sum);
            }
        }
        LogUtils.getLogger().info("[WorldFixtureReplay] resource={} blocks={} player={} target={} evidence={} first={}",
                resource, placed, player, target, evidence.size(), evidence.isEmpty() ? "none" : evidence.get(0));
        LogUtils.getLogger().info("[WorldFixtureReplay] canonical resource={} accepted={} rejections={}", resource, canonicalAccepted, rejections);
        if (candidates.isEmpty() || worker == null) {
            throw new AssertionError(resource + " did not produce a usable replay scene");
        }
        helper.runAfterDelay(3, () -> {
            AdmissionResult admission;
            try {
                admission = driveAdmission(helper, resource, fixture, player, target, threat, minX, minY, minZ);
            } catch (RuntimeException | AssertionError failure) {
                helper.fail(resource + " admission replay failed: " + failure);
                return;
            }
            LogUtils.getLogger().info("[WorldFixtureReplay] admission resource={} targets={} request={} formation={} placementBlocks={}",
                    resource, admission.targets, admission.request, admission.formation, admission.placementBlocks);
            if (admission.targets <= 0) {
                helper.fail(resource + " produced no admissible target evidence: " + admission);
            }
        });
    }

    private static AdmissionResult driveAdmission(GameTestHelper helper, String resource, Fixture fixture, BlockPos player, BlockPos target,
                                                  BlockPos threat,
                                                  int minX, int minY, int minZ) {
        ServerLevel level = helper.getLevel();
        List<Mob> cluster = new ArrayList<>();
        fixture.entities.stream().filter(entity -> entity.id.equals("minecraft:zombie"))
                .sorted(java.util.Comparator.comparingDouble(entity -> distanceSqr(entity.pos, player, minX, minY, minZ)))
                .limit(8).forEach(entity -> {
                    double replayX = entity.pos[0] - minX + 1.0D;
                    double replayZ = entity.pos[2] - minZ + 1.0D;
                    double preferredY = entity.pos[1] - minY + 1.0D;
                    net.minecraft.world.phys.Vec3 replayPosition = resolveReplayWorkerPosition(level, replayX, preferredY, replayZ);
                    replayX = replayPosition.x;
                    double replayY = replayPosition.y;
                    replayZ = replayPosition.z;
                    Mob mob = helper.spawn(EntityType.ZOMBIE,
                            (float) replayX, (float) replayY, (float) replayZ);
                    mob.setPersistenceRequired();
                    mob.setNoGravity(true);
                    mob.noPhysics = true;
                    mob.setPos(replayX, replayY, replayZ);
                    assertReplayEntityRegistered(level, mob, "after_set_pos");
                    while (!level.getBlockState(mob.blockPosition()).getCollisionShape(level, mob.blockPosition(),
                            net.minecraft.world.phys.shapes.CollisionContext.empty()).isEmpty()
                            || !level.getBlockState(mob.blockPosition().above()).getCollisionShape(level,
                            mob.blockPosition().above(), net.minecraft.world.phys.shapes.CollisionContext.empty()).isEmpty()) {
                        mob.setPos(mob.getX(), mob.getY() + 1.0D, mob.getZ());
                        if (mob.getY() > 31.0D) break;
                    }
                    mob.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    mob.setOnGround(true);
                    mob.fallDistance = 0.0F;
                    assertReplayEntityRegistered(level, mob, "after_reposition");
                    BlockPos feet = mob.blockPosition();
                    LogUtils.getLogger().info("[WorldFixtureReplay] worker id={} pos={} below={} feet={} head={} onGround={} delta={}",
                            entity.id, mob.position(), level.getBlockState(mob.blockPosition().below()),
                            level.getBlockState(mob.blockPosition()), level.getBlockState(mob.blockPosition().above()),
                            mob.onGround(), mob.getDeltaMovement());
                    if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()) {
                        cluster.add(mob);
                    }
                });
        cluster.forEach(mob -> assertReplayEntityRegistered(level, mob, "before_admission"));
        try {
            Method targetsMethod = LivingWallCoordinator.class.getDeclaredMethod("findClusterTargets", ServerLevel.class, List.class, BlockPos.class);
            targetsMethod.setAccessible(true);
            List<?> targets = (List<?>) targetsMethod.invoke(null, level, cluster, threat);
            if (targets.isEmpty()) return new AdmissionResult(0, false, false, 0);
            BlockPos selected = (BlockPos) targets.get(0);
            LogUtils.getLogger().info("[WorldFixtureReplay] selectedLanding={} below={} feet={} above={}", selected,
                    level.getBlockState(selected.below()), level.getBlockState(selected), level.getBlockState(selected.above()));
            Method requestMethod = LivingWallCoordinator.class.getDeclaredMethod("findConstructionRequest", ServerLevel.class, List.class, BlockPos.class, BlockPos.class);
            requestMethod.setAccessible(true);
            Object request = requestMethod.invoke(null, level, cluster, threat, selected);
            if (request == null) {
                logBuildGateState(level);
                LogUtils.getLogger().warn("[WorldFixtureReplay] request_rejected_after_canonical resource={} target={} clusterSize={} gameTime={} observationPath=post_admission", resource, selected, cluster.size(), level.getGameTime());
                return new AdmissionResult(targets.size(), false, false, 0);
            }
            LogUtils.getLogger().info("[WorldFixtureReplay] requestDetails target={} selectedAnchor={} expiresAt={} scanExpiresAt={} now={} livingWallEnabled={} leaderType={} leaderBaby={} leaderAlive={} leaderRemoved={} clusterSize={}",
                    selected, request.getClass().getMethod("selectedAnchor").invoke(request),
                    request.getClass().getMethod("expiresAt").invoke(request),
                    request.getClass().getMethod("scanExpiresAt").invoke(request), level.getGameTime(),
                    com.example.soundattract.config.separate.LivingWallConfig.ENABLE_LIVING_WALL.get(),
                    cluster.get(0).getType(), cluster.get(0).isBaby(), cluster.get(0).isAlive(), cluster.get(0).isRemoved(), cluster.size());
            Method prepare = null;
            for (Method method : LivingWallCoordinator.class.getDeclaredMethods()) {
                if (method.getName().equals("prepareFormation") && method.getParameterCount() == 5) {
                    prepare = method;
                    break;
                }
            }
            if (prepare == null) return new AdmissionResult(targets.size(), true, false, 0);
            prepare.setAccessible(true);
            boolean formation = false;
            for (Mob leader : cluster) {
                if ((boolean) prepare.invoke(null, leader, selected, level.getGameTime(), cluster, request)) {
                    formation = true;
                    break;
                }
            }
            if (resource.startsWith("taller-castle-live")) {
                long admissionTick = level.getGameTime();
                helper.runAfterDelay(150, () -> traceTallerWorkers(helper, level, cluster, 30, admissionTick));
            }
            logBuildGateState(level);
            if (!formation) throw new AssertionError("no bridge formation admitted for " + resource);
            Method stateMethod = LivingWallCoordinator.class.getDeclaredMethod("state", ServerLevel.class);
            stateMethod.setAccessible(true);
            Object state = stateMethod.invoke(null, level);
            java.lang.reflect.Field formationsField = state.getClass().getDeclaredField("formations");
            formationsField.setAccessible(true);
            Object observed = null;
            for (Object candidateFormation : ((java.util.Map<?, ?>) formationsField.get(state)).values()) {
                java.lang.reflect.Field plannerField = candidateFormation.getClass().getDeclaredField("plannerId");
                plannerField.setAccessible(true);
                Object planner = plannerField.get(candidateFormation);
                if (cluster.stream().anyMatch(mob -> mob.getUUID().equals(planner))) observed = candidateFormation;
            }
            if (observed == null) throw new AssertionError("admitted formation missing from coordinator");
            Object observedFormation = observed;
            helper.runAfterDelay(1, () -> verifyPlacement(helper, level, observedFormation, 0));
            return new AdmissionResult(targets.size(), true, formation, 0);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("admission replay failed", exception);
        }
    }

    private static void assertReplayEntityRegistered(ServerLevel level, Mob mob, String phase) {
        if (mob == null || level == null || mob.level() != level || level.getEntity(mob.getUUID()) != mob) {
            throw new AssertionError("replay entity registration lost phase=" + phase
                    + " uuid=" + (mob == null ? null : mob.getUUID())
                    + " dimension=" + (mob == null || mob.level() == null ? null : mob.level().dimension().location()));
        }
    }

    private static void traceTallerWorkers(GameTestHelper helper, ServerLevel level, List<Mob> workers,
                                           int remaining, long admissionTick) {
        long tick = level.getGameTime();
        for (Mob worker : workers) {
            Mob lookup = level.getEntity(worker.getUUID()) instanceof Mob mob ? mob : null;
            boolean listed = level.getEntitiesOfClass(Mob.class, worker.getBoundingBox().inflate(0.01D)).contains(worker);
            LogUtils.getLogger().info("[WorldFixtureReplay] taller_lifecycle tick={} age={} uuid={} sameInstance={} lookup={} listed={} dimension={} level={} pos={} chunk={} alive={} removed={}",
                    tick, tick - admissionTick, worker.getUUID(), lookup == worker, lookup != null, listed,
                    worker.level().dimension().location(), System.identityHashCode(worker.level()), worker.position(),
                    new net.minecraft.world.level.ChunkPos(worker.blockPosition()), worker.isAlive(), worker.isRemoved());
        }
        if (remaining > 0) helper.runAfterDelay(1, () -> traceTallerWorkers(helper, level, workers, remaining - 1, admissionTick));
    }

    private static void stabilizeLowerEndpointAfterObservation(ServerLevel level) {
        for (int x = 4; x <= 10; x++) {
            for (int z = 13; z <= 19; z++) {
                level.setBlock(new BlockPos(x, 17, z), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, 18, z), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, 16, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
    }

    private static void verifyPlacement(GameTestHelper helper, ServerLevel level, Object formation, int tick) {
        try {
            java.lang.reflect.Field placementsField = formation.getClass().getDeclaredField("successfulPlacementProvenance");
            java.lang.reflect.Field crossersField = formation.getClass().getDeclaredField("completedCrossers");
            java.lang.reflect.Field phaseField = formation.getClass().getDeclaredField("phase");
            java.lang.reflect.Field collapsedField = formation.getClass().getDeclaredField("collapsed");
            placementsField.setAccessible(true);
            crossersField.setAccessible(true);
            phaseField.setAccessible(true);
            collapsedField.setAccessible(true);
            int placements = ((java.util.Map<?, ?>) placementsField.get(formation)).size();
            int crossers = ((java.util.Set<?>) crossersField.get(formation)).size();
            if (tick % 40 == 0 || crossers > 0 || tick >= 700) {
                LogUtils.getLogger().info("[WorldFixtureReplay] bridgeProgress tick={} phase={} placedMobs={} completedCrossers={} collapsed={}",
                        tick, phaseField.get(formation), placements, crossers, collapsedField.get(formation));
            }
            if (placements > 0 && crossers > 0) {
                helper.succeed();
                return;
            }
            if ((boolean) collapsedField.get(formation) || tick >= 700) {
                helper.fail("bridge did not complete traversal: phase=" + phaseField.get(formation)
                        + " placedMobs=" + placements + " completedCrossers=" + crossers);
                return;
            }
            helper.runAfterDelay(1, () -> verifyPlacement(helper, level, formation, tick + 1));
        } catch (ReflectiveOperationException exception) {
            helper.fail("bridge observation failed: " + exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static void logBuildGateState(ServerLevel level) {
        try {
            Method stateMethod = LivingWallCoordinator.class.getDeclaredMethod("state", ServerLevel.class);
            stateMethod.setAccessible(true);
            Object state = stateMethod.invoke(null, level);
            java.lang.reflect.Field attempts = state.getClass().getDeclaredField("buildGateAttempts");
            java.lang.reflect.Field reasons = state.getClass().getDeclaredField("buildGateRejections");
            attempts.setAccessible(true);
            reasons.setAccessible(true);
            LogUtils.getLogger().info("[WorldFixtureReplay] buildGates attempts={} reasons={}", attempts.getInt(state), reasons.get(state));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("build gate diagnostics failed", exception);
        }
    }

    private static double distanceSqr(double[] entity, BlockPos player, int minX, int minY, int minZ) {
        double x = entity[0] - minX + 1.0D - player.getX();
        double y = entity[1] - minY + 1.0D - player.getY();
        double z = entity[2] - minZ + 1.0D - player.getZ();
        return x * x + y * y + z * z;
    }

    private static double resolveReplayFeet(ServerLevel level, double x, double preferredY, double z) {
        int columnX = net.minecraft.util.Mth.floor(x);
        int columnZ = net.minecraft.util.Mth.floor(z);
        int preferred = net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.floor(preferredY), 1, 30);
        for (int distance = 0; distance <= 29; distance++) {
            int[] candidates = {preferred - distance, preferred + distance};
            for (int y : candidates) {
                if (y < 1 || y > 30) continue;
                BlockPos support = new BlockPos(columnX, y - 1, columnZ);
                BlockPos feet = new BlockPos(columnX, y, columnZ);
                if (!level.getBlockState(support).getCollisionShape(level, support,
                        net.minecraft.world.phys.shapes.CollisionContext.empty()).isEmpty()
                        && level.getBlockState(feet).getFluidState().isEmpty()
                        && level.getBlockState(feet.above()).getFluidState().isEmpty()
                        && level.getBlockState(feet).getCollisionShape(level, feet,
                        net.minecraft.world.phys.shapes.CollisionContext.empty()).isEmpty()
                        && level.getBlockState(feet.above()).getCollisionShape(level, feet.above(),
                        net.minecraft.world.phys.shapes.CollisionContext.empty()).isEmpty()) {
                    return y;
                }
            }
        }
        return Math.max(1.0D, preferredY);
    }

    private static net.minecraft.world.phys.Vec3 resolveReplayWorkerPosition(ServerLevel level, double x,
                                                                              double preferredY, double z) {
        int centerX = net.minecraft.util.Mth.floor(x);
        int centerZ = net.minecraft.util.Mth.floor(z);
        for (int radius = 0; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int candidateX = centerX + dx;
                    int candidateZ = centerZ + dz;
                    double y = resolveReplayFeet(level, candidateX + 0.5D, preferredY, candidateZ + 0.5D);
                    BlockPos feet = new BlockPos(candidateX, net.minecraft.util.Mth.floor(y), candidateZ);
                    if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                            && level.getBlockState(feet).getFluidState().isEmpty()
                            && level.getBlockState(feet.above()).getFluidState().isEmpty()) {
                        return new net.minecraft.world.phys.Vec3(candidateX + 0.5D, y, candidateZ + 0.5D);
                    }
                }
            }
        }
        return new net.minecraft.world.phys.Vec3(x, resolveReplayFeet(level, x, preferredY, z), z);
    }

    private static int countNonAir(ServerLevel level, BlockPos center, int radius) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (!level.getBlockState(pos).isAir()) count++;
        }
        return count;
    }

    private static BlockPos farthest(BlockPos origin, List<BlockPos> candidates) {
        return candidates.stream().filter(pos -> pos.getY() >= 4)
                .max((a, b) -> Double.compare(a.distSqr(origin), b.distSqr(origin))).orElse(origin);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState applyProperties(Block block, JsonObject properties) {
        BlockState state = block.defaultBlockState();
        if (properties == null) return state;
        for (String key : properties.keySet()) {
            Property property = block.getStateDefinition().getProperty(key);
            if (property == null) continue;
            java.util.Optional value = property.getValue(properties.get(key).getAsString());
            if (value.isPresent()) state = state.setValue(property, (Comparable) value.get());
        }
        return state;
    }

    private static Fixture load(String resource) {
        try (InputStream stream = WorldFixtureReplayGameTest.class.getClassLoader().getResourceAsStream("gametestfixtures/" + resource)) {
            if (stream == null) throw new IllegalStateException("missing fixture resource " + resource);
            return GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Fixture.class);
        } catch (Exception exception) {
            throw new IllegalStateException("cannot load fixture " + resource, exception);
        }
    }

    private static final class Fixture {
        List<BlockEntry> blocks = List.of();
        List<EntityEntry> entities = List.of();
    }

    private static final class BlockEntry {
        int[] pos;
        String state;
        JsonObject properties;
    }

    private static final class EntityEntry {
        String id;
        double[] pos;
    }

    private record AdmissionResult(int targets, boolean request, boolean formation, int placementBlocks) {
    }
}
