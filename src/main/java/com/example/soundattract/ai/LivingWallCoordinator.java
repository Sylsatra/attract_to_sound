package com.example.soundattract.ai;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.config.separate.LivingWallConfig;
import com.example.soundattract.mixin.MobAccessor;
import com.example.soundattract.quantified.LivingBridgeAssignmentPlanner;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.MinecraftServer;

public final class LivingWallCoordinator {
    private static final Map<Identifier, PerLevelState> LEVELS = new ConcurrentHashMap<>();
    private static final int MINIMUM_MEMBERS = 3;
    private static final int RECRUITMENT_TIMEOUT_TICKS = 200;
    private static final int BRIDGE_STARTUP_GRACE_TICKS = 3;
    private static final int ASSEMBLY_TIMEOUT_PADDING_TICKS = 80;
    private static final int ROTATION_TIMEOUT_TICKS = 240;
    private static final int MAX_SCAN_CANDIDATES = 48;
    private static final int FORMATION_SERVICE_QUOTA = 8;
    private static final int ADMISSION_QUOTA = 4;
    private static final int TOP_BUILDING_SPOTS_QUOTA = 3;
    private static final int NAVIGATION_QUOTA = 8;
    private static final int RECRUITER_OBSERVATION_QUOTA = 16;
    private static final int RECRUITER_RETRY_WINDOWS = 3;
    private static final int STRUCTURAL_ROUTE_RETRY_WINDOWS = 20;
    private static final int STRUCTURAL_LANE_CONFLICT_RETRY_WINDOWS = 30;
    private static final int RECRUITER_RELEASE_TICKS = 40;
    private static final int RECRUITER_OBSERVATION_TICKS = 20;
    static final int RECRUITER_MAX_PATH_FAILURES = 3;
    private static final int RECRUITER_PATH_FAILURE_BLACKLIST_TICKS = 40;
    private static final int RECRUITMENT_COMBAT_MEMORY_TICKS = 40;
    private static final int APPROACH_LANE_SPACING = 2;
    private static final int CANDIDATE_HOLDING_MIN_RADIUS = 12;
    private static final int CANDIDATE_HOLDING_RADIUS = 24;
    private static final int CANDIDATE_HOLDING_ASSEMBLY_BUFFER = 8;
    private static final int ASSEMBLY_RELEASE_INTERVAL_TICKS = 10;
    private static final int INTAKE_WINDOW_FAILURE_THRESHOLD = 2;
    private static final int INTAKE_POSE_FAILURE_THRESHOLD = 3;
    private static final int CAPTURE_RETRY_COOLDOWN_TICKS = 10;
    private static final int ORPHAN_FREEZE_CLEANUP_INTERVAL_TICKS = 20;
    private static final int ORPHAN_FREEZE_CLEANUP_BUDGET = 16;
    private static final int ROSTER_REFRESH_INTERVAL_TICKS = 10;
    private static final int ROSTER_REFRESH_QUOTA = 4;
    private static final double RECRUITER_PROGRESS_EPSILON_SQR = 0.25D;
    private static final int FORMATION_DIAGNOSTIC_INTERVAL_TICKS = 40;
    private static final int CONTENTION_INTERVAL_TICKS = 20;
    private static final int CONTENTION_COOLDOWN_TICKS = 240;
    private static final double CONTENTION_RADIUS = 8.0D;
    private static final int MAX_TARGET_SCAN_RAYS = 48;
    private static final int MAX_EXTERIOR_LANDING_DISTANCE = 12;
    private static final int MAX_EXTERIOR_LANDING_HEIGHT = 8;
    private static final int MAX_EXTERIOR_LANDING_CANDIDATES = 128;
    private static final int TARGET_ROUTE_PROBE_LIMIT = 16;
    private static final int FORMATION_ROUTE_PROBE_LIMIT = 3;
    private static final int TARGET_ROUTE_NODE_LIMIT = 64;
    private static final int SOURCE_STAGING_MAX_DEPTH = 7;
    private static final int SOURCE_STAGING_MAX_LATERAL = 3;
    private static final int SOURCE_STAGING_MAX_COLUMNS = 49;
    private static final int SOURCE_STAGING_MAX_ENDPOINTS = 6;
    private static final int SOURCE_STAGING_MIN_Y_OFFSET = -3;
    private static final int SOURCE_STAGING_MAX_Y_OFFSET = 2;
    static final int MAX_CORRIDOR_INTERVAL_CELLS = 96;
    private static final int TRAVERSAL_WITNESS_DIAGNOSTIC_CELL_LIMIT = 12;
    private static final int TARGET_ROUTE_SAMPLE_LIMIT = 32;
    private static final int TARGET_SCAN_CACHE_TICKS = 40;
    private static final int MAX_TARGET_SCAN_CACHE_ENTRIES = 128;
    private static final int MAX_WALL_PROFILE_CACHE_ENTRIES = 128;
    private static final int MAX_REQUEST_REVISION_CACHE_ENTRIES = 128;
    private static final int WALL_PROFILE_CACHE_TICKS = 600;
    private static final int WALL_PROFILE_MISS_CACHE_TICKS = 20;
    private static final int WALL_PROFILE_RESCAN_DELAY_TICKS = 100;
    private static final int MAX_BODY_GEOMETRIES = 256;
    private static final int TERRAIN_REJECTION_TICKS = 1200;
    private static final int CANDIDATE_PLAN_REJECTION_TICKS = 40;
    private static final int REMEMBERED_THREAT_TTL_TICKS = 1200;
    private static final int TERRAIN_REJECTION_RADIUS = 3;
    private static final int MAX_EXACT_SITE_COOLDOWN_ENTRIES = 256;
    private static final int MAX_HOLDING_CELL_CACHE_ENTRIES = 256;
    private static final int HOLDING_CELL_CACHE_TICKS = 40;
    private static final int HOLDING_CELL_SEARCH_BUDGET_PER_TICK = 6;
    private static final int HOLDING_CELL_PATH_VALIDATION_BUDGET = 2;
    private static final int NON_RAID_SCAN_INTERVAL_TICKS = 20;
    private static final int MAX_NON_RAID_LEADERS_PER_SCAN = 4;
    private static final int ANCHOR_STABILIZATION_TICKS = 12;
    private static final int RECOVERY_GRACE_TICKS = 40;
    private static final int BRIDGE_FOLLOWER_UPDATE_BUDGET = 48;
    private static final int ANCHORED_VALIDATION_INTERVAL = 10;
    private static final int FORMATION_COLUMNS = 2;
    private static final double STRUCTURAL_MEMBER_RADIUS = 1.5D;
    private static final double BRIDGE_ENTRY_RADIUS = 24.0D;
    private static final double BRIDGE_ACCESS_RADIUS = 24.0D;
    private static final int BRIDGE_ACCESS_SCAN_INTERVAL_TICKS = 10;
    private static final int BRIDGE_ACCESS_SCAN_BUDGET = 16;
    private static final int MAX_CROSSERS = 24;
    private static final int TILT_EXIT_MAX_FALL_DEPTH = 32;
    private static final int TILT_EXIT_APPROACH_SAMPLES = 10;
    private static final int TILT_EXIT_EGRESS_SAMPLES = 12;
    private static final double TILT_EXIT_MIN_FORWARD_PROGRESS = 1.5D;
    private static final double TILT_EXIT_MIN_DROP = 0.75D;
    private static final int ASSEMBLY_SLOT_INTERVAL_TICKS = 5;
    private static final int ASSEMBLY_SETTLE_TICKS = 10;
    private static final int ANCHORED_REPIN_INTERVAL_TICKS = 2;
    private static final double INTEGRITY_THRESHOLD = 0.35D;
    private static final double RECRUITMENT_RADIUS = 64.0D;
    private static final double ACTIVE_ASSEMBLY_MAX_RECRUITMENT_DISTANCE = 24.0D;
    private static final double LOCAL_ROSTER_MARGIN = 4.0D;
    private static final double STRUCTURAL_RECRUITMENT_RADIUS = 1.5D;
    private static final double CONTROLLED_INTAKE_RADIUS = STRUCTURAL_RECRUITMENT_RADIUS;
    private static final double CONTROLLED_INTAKE_TARGET_RADIUS = STRUCTURAL_RECRUITMENT_RADIUS;
    private static final int INTAKE_WINDOW_MIN_RADIUS = 2;
    private static final int INTAKE_WINDOW_MAX_RADIUS = 5;
    private static final double RECRUITMENT_INTAKE_RADIUS = STRUCTURAL_RECRUITMENT_RADIUS;

    record DropLimitSnapshot(double maxDropBridgeLength, double maxRopeBridgeLength,
                             long capturedAtTick) {
        DropLimitSnapshot {
            if (!Double.isFinite(maxDropBridgeLength) || maxDropBridgeLength < 0.0D
                    || !Double.isFinite(maxRopeBridgeLength) || maxRopeBridgeLength < 0.0D) {
                throw new IllegalArgumentException("invalid drop limits");
            }
        }
    }

    static DropLimitSnapshot captureDropLimitSnapshot(long now) {
        return new DropLimitSnapshot(LivingWallConfig.MAX_DROP_BRIDGE_LENGTH.get(),
                LivingWallConfig.MAX_ROPE_BRIDGE_LENGTH.get(), now);
    }

    static double capturedBottomCenterDrop(BlockPos source, BlockPos destination) {
        if (source == null || destination == null) return Double.POSITIVE_INFINITY;
        Vec3 from = Vec3.atBottomCenterOf(source);
        Vec3 to = Vec3.atBottomCenterOf(destination);
        return from.distanceTo(to);
    }

    record CommittedAdmissionSnapshot(UUID plannerId, Set<UUID> roster, DropLimitSnapshot limits,
                                      ConstructionSiteKey siteKey) {
        CommittedAdmissionSnapshot {
            Objects.requireNonNull(plannerId, "plannerId");
            roster = Set.copyOf(Objects.requireNonNull(roster, "roster"));
            Objects.requireNonNull(limits, "limits");
            Objects.requireNonNull(siteKey, "siteKey");
        }
    }

    record CommittedBridgeExecution(UUID id, CommittedAdmissionSnapshot admission,
                                    ConstructionSiteKey committedKey) {
        CommittedBridgeExecution {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(admission, "admission");
            Objects.requireNonNull(committedKey, "committedKey");
        }
    }

    private record AdmissionPlanningInput(int columnCount, int requiredBodies, int bodyLimit,
                                          DropLimitSnapshot dropLimits) {
    }

    private record PotentialSupplySnapshot(int discovered, int nearbyUnion, int eligibleAlive,
                                           int unclaimed, int geometryCompatible, int pathFreePotential,
                                           List<Mob> candidates) {
        private PotentialSupplySnapshot {
            candidates = List.copyOf(candidates);
        }
    }

    private static AdmissionPlanningInput admissionPlanningInput(SitePlan plan, BodyGeometry geometry,
                                                                  int availableBodies, long now) {
        int bodyLimit = Math.max(3, LivingWallConfig.MAX_BODIES.get());
        int usableBodies = Math.min(bodyLimit, Math.max(0, availableBodies));
        double horizontal = Math.sqrt(Math.pow(plan.spanEnd().getX() - plan.pivot().getX(), 2)
                + Math.pow(plan.spanEnd().getZ() - plan.pivot().getZ(), 2));
        double vertical = Math.abs(plan.spanEnd().getY() - plan.pivot().getY());
        double horizontalStep = lateralSpacingForBodyWidth(geometry.width());
        double verticalStep = requiresTilt(plan.scenario())
                ? layerSpacingForBodyHeight(geometry.height())
                : Math.max(0.05D, geometry.height() + STRUCTURAL_NON_OVERLAP_MARGIN);
        int stations = requiresTilt(plan.scenario())
                ? requiredLayerCountForTilt(plan, horizontalStep, verticalStep)
                : Math.max(2, requiredStationsForRoute(plan, horizontalStep, verticalStep));
        int columns = (plan.scenario() == ConstructionScenario.WALL_LEDGE || plan.scenario() == ConstructionScenario.DROP_TO_LEDGE)
                ? chooseColumnCount(Integer.MAX_VALUE, stations, usableBodies)
                : geometry.width() + CONTACT_EPSILON >= MIN_COLUMN_WIDTH
                ? chooseColumnCount(stations * FORMATION_COLUMNS, stations, usableBodies)
                : chooseColumnCount(Integer.MAX_VALUE, stations, usableBodies);
        return new AdmissionPlanningInput(columns, columns == 0 ? bodyLimit + 1 : stations * columns,
                bodyLimit, captureDropLimitSnapshot(now));
    }
    private static final double INTAKE_WINDOW_ARRIVAL_DISTANCE_SQR = 0.5625D;
    private static final int RECRUITMENT_LANE_GUARD_INTERVAL_TICKS = 5;
    private static final int RECRUITMENT_LANE_BLOCKER_LIMIT = 6;
    private static final double RECRUITMENT_LANE_CLEARANCE = 0.65D;
    private static final double RECRUITMENT_LANE_NUDGE = 0.08D;
    private static final int CONTROLLED_INTAKE_MAX_TICKS = 20;
    private static final int CONTROLLED_ASSEMBLY_STAGE_MAX_TICKS = 40;
    private static final double CONTROLLED_INTAKE_SETTLE_DISTANCE = 0.35D;
    private static final double CONTROLLED_INTAKE_PULL_STEP = 0.08D;
    private static final int CROSSER_INTAKE_MAX_TICKS = 60;
    private static final double CROSSER_INTAKE_PULL_STEP = 0.22D;
    private static final int MAX_ACTIVE_RECRUITERS = 8;
    private static final int STRUCTURAL_ASSEMBLY_LOOKAHEAD = 4;
    private static final int PATH_SURVEY_QUOTA = 24;
    private static final int PATH_SURVEY_CACHE_LIMIT = MAX_SCAN_CANDIDATES * MAX_ACTIVE_RECRUITERS;
    private static final int PATH_SURVEY_CACHE_TICKS = 40;
    private static final double CONTACT_EPSILON = 0.08D;
    private static final double STRUCTURAL_NON_OVERLAP_MARGIN = 0.02D;
    private static final double TRAPDOOR_THINNESS_LIMIT = 0.25D;
    private static final double TRAPDOOR_SHELL_MARGIN = 0.125D + CONTACT_EPSILON;
    private static final int SOURCE_STAGING_OFFSET_BUDGET = 567;
    private static final int SOURCE_STAGING_DEFLECTION_BUDGET = 240;
    private static final int SOURCE_STAGING_VALIDATION_BUDGET = 8;
    private static final int SOURCE_STAGING_DIAGNOSTIC_BUDGET = 16;
    private static final double OVERLAP_EPSILON = 1.0E-4D;
    private static final double STRUCTURAL_PROGRESS_EPSILON_SQR = 1.0E-3D;
    private static final double HAZARD_APPROACH_BUFFER = 1.0D;
    private static final double BODY_CONTACT_STEP = 1.75D;
    private static final double MIN_COLUMN_WIDTH = 0.28D;
    private static final double TILT_CONTACT_MARGIN = 0.0125D;
    private static final int MIN_SHIFT_TICKS = 5;
    private static final int MAX_SHIFT_TICKS = 20;
    private static final double ASSEMBLY_ARRIVAL_DISTANCE_SQR = 1.5625D;
    private static final double ASSEMBLY_ACCEPTANCE_RADIUS_SQR = 4.0D;
    private static final double ASSEMBLY_ACCEPTANCE_VERTICAL_TOLERANCE = 1.5D;
    private static final int MAX_SITE_BACKTRACK = 8;
    private static final int MAX_SITE_LATERAL_OFFSET = 4;
    private static final int MAX_SITE_ELEVATION_DELTA = 2;
    private static final int ROOT_ZONE_RADIUS = 8;
    private static final int ROOT_ZONE_MAX_HEIGHT_SPREAD = 4;
    private static final int ROOT_ZONE_MIN_SLOTS = 1;
    private static final String CONTROL_TAG = "soundattract_living_bridge";
    private static final String CONTROL_SESSION = "session";
    private static final String CONTROL_ORIGINAL_GRAVITY = "originalGravity";
    private static final String CONTROL_ORIGINAL_NO_PHYSICS = "originalNoPhysics";
    private static final String CONTROL_ORIGINAL_PERSISTENCE = "originalPersistence";
    private static final String CONTROL_ORIGINAL_TARGET = "originalTarget";
    private static final String CONTROL_HAS_TARGET = "hasTarget";
    private static final double[] DIRECT_PREFLIGHT_PROGRESS = {0.0D};
    private static final double[] TILT_PREFLIGHT_PROGRESS = {0.0D, 0.25D, 0.5D, 0.75D, 1.0D};
    private static final Map<GeometryKey, BodyGeometry> BODY_GEOMETRIES = new ConcurrentHashMap<>();
    private static final Map<HoldingCellCacheKey, HoldingCellCacheEntry> HOLDING_CELL_CACHE = new LinkedHashMap<>();

    private LivingWallCoordinator() {
    }

    private record AdmissionClusterCompletion(long generation,
                                              LivingBridgeQuantifiedPlanner.ClusterResult result,
                                              Throwable failure) {
    }

    static final class PerLevelState {
        long nextObservationIndex;
        final Map<UUID, Formation> formations = new HashMap<>();
        final Map<UUID, ConstructionIntent> constructionIntents = new HashMap<>();
        final Map<UUID, Set<UUID>> leaderToFormations = new HashMap<>();
        final Map<UUID, UUID> mobOwners = new HashMap<>();
        final Map<Long, UUID> footprintOwners = new HashMap<>();
        final ArrayDeque<UUID> serviceRing = new ArrayDeque<>();
        final Map<ExactSiteKey, Long> terrainRejections = new HashMap<>();
        final Map<Long, Long> contentionCooldowns = new HashMap<>();
        final LinkedHashMap<ConstructionRequestKey, Long> requestRevisions = new LinkedHashMap<>();
        final Map<UUID, TerminalAdmissionFailure> terminalAdmissionFailures = new HashMap<>();
        final Map<RouteProgressKey, Integer> routeProgress = new HashMap<>();
        final Map<TargetScanKey, CachedTargetScan> targetScans = new HashMap<>();
        final Map<Long, LivingBridgePositionPlan> positionPlans = new HashMap<>();
        final Map<Long, Long> terrainCellEpochs = new HashMap<>();
        final Map<Long, Long> terrainChunkEpochs = new HashMap<>();
        long terrainEpoch;
        final Map<UUID, GroundingObservation> groundingObservations = new HashMap<>();
        final Map<WallProfileKey, CachedWallProfile> wallProfiles = new HashMap<>();
        long nextRequestRevision;
        long nextWindowGeneration;
        long lastCleanupTick = -1L;
        long lastNonRaidScanTick = -1L;
        long nextAdmissionAttemptTick = -1L;
        long admissionBackoffUntil = -1L;
        int admissionFailureCount;
        int admissionClusterCursor;
        int admissionAttempts;
        int admissionSkippedByBudget;
        int admissionClusterBudgetExhausted;
        int admissionFallbacks;
        int admissionStaleDrops;
        long admissionGeneration;
        boolean admissionInFlight;
        final ConcurrentLinkedQueue<AdmissionClusterCompletion> admissionCompletions = new ConcurrentLinkedQueue<>();
        long lastContentionTick = -1L;
        long nextAdmissionLogTick = -1L;
        long nextBuildGateLogTick = -1L;
        long nextPlanDiagnosticLogTick = -1L;
        long nextLivingWallDiagnosticLogTick = -1L;
        int livingWallDiagnosticBudget;
        int livingWallDiagnosticScanBudget;
        int livingWallDiagnosticSuppressed;
        int buildGateAttempts;
        final Map<String, Integer> buildGateRejections = new HashMap<>();
        final Map<String, Integer> decisionCounters = new HashMap<>();
        final Map<String, Integer> rejectionCounters = new HashMap<>();
        long nextDangerLogTick = -1L;
        long lastOrphanFreezeCleanupTick = -1L;
        int orphanFreezeCursor;
        int navigationRemaining;
        int recruiterObservationsRemaining;
        int rosterRefreshesRemaining;
        int pathSurveysRemaining;
        final Map<CorridorCacheKey, CachedCorridor> corridorCache = new HashMap<>();
        final Map<UUID, RememberedThreat> rememberedThreats = new ConcurrentHashMap<>();
        final Map<UUID, BasinEvacuation> basinEvacuations = new HashMap<>();
        final Map<BlockPos, BasinFailureHistory> basinFailures = new HashMap<>();
        final it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap standingEnvelopeCache = new it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap();
        long standingEnvelopeCacheTick = -1L;
        final it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap safeApproachCellCache = new it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap();
        long safeApproachCellCacheTick = -1L;
    }

    public record RememberedThreat(UUID targetUuid, BlockPos lastKnownPos, long lastSeenTick) {
        public RememberedThreat {
            Objects.requireNonNull(targetUuid, "targetUuid cannot be null");
            lastKnownPos = lastKnownPos == null ? BlockPos.ZERO : lastKnownPos.immutable();
        }

        public boolean isValid(long now, long ttlTicks) {
            return now >= lastSeenTick && (now - lastSeenTick) <= ttlTicks;
        }
    }

    private record CorridorCacheKey(Identifier dimension, String corridorId,
                                    BlockPos takeoff, BlockPos landing, BlockPos egress,
                                    int widthMm, int heightMm) {
    }

    private record CachedCorridor(CanonicalFeetCorridor corridor, long expiresAt) {
    }

    private record GeometryKey(Identifier type, int widthUnits, int heightUnits) {
    }

    private record BodyGeometry(double width, double height) {
    }

    private record PathSurveyKey(UUID workerId, BlockPos cell) {
    }

    private record PathSurvey(boolean reachable, BlockPos source, long expiresAt) {
    }

    record RecruitmentRouteKey(UUID workerId, BlockPos assemblySlot, BlockPos intakeFeeder) {
    }

    private record RecruitmentRouteSurvey(boolean reachable, BlockPos source, long expiresAt) {
    }

    record BasinFailureHistory(int failureCount, long lastFailureTick, long cooldownUntil) {
    }

    record BasinEvacuation(Vec3 target, BlockPos threatPos, long expiresTick) {
    }

    private enum RouteProbeOwner {
        TARGET_SURVEY,
        HOLDING_SURVEY,
        INTAKE_SURVEY,
        CROSSER_CAPTURE
    }

    private record RouteProbeReservation(RouteProbeOwner owner, BlockPos target, boolean attempted, boolean successful) {
        RouteProbeReservation {
            target = target == null ? null : target.immutable();
        }
    }

    private record BridgeRouteKey(long sourceBucketKey, long threatBucketKey, long wallFaceKey, long supportKey,
                                  ConstructionScenario scenario, int axisX, int axisZ) {
    }

    private record LocalIntakeWindowResolution(List<IntakeWindow> windows, int unresolved) {
    }

    private record TargetScanKey(int sourceX, int sourceY, int sourceZ, int targetX, int targetY, int targetZ, long generation) {
    }

    private record ColdStartSeedProvider(BlockPos sourceBucket, BlockPos threatBucket) {
        ColdStartSeedProvider {
            sourceBucket = sourceBucket == null ? null : sourceBucket.immutable();
            threatBucket = threatBucket == null ? null : threatBucket.immutable();
        }

        BlockPos seed() {
            return threatBucket != null ? threatBucket : sourceBucket;
        }
    }

    private record ConstructionRequest(Vec3 sourceCenter, BlockPos sourceBucket, BlockPos threatDestination,
                                       BlockPos selectedAnchor, BlockPos threatBucket, TargetScanSnapshot snapshot,
                                       WallProfileScanner.TargetEvidence evidence, RouteEvidence routeEvidence,
                                       CanonicalFeetCorridor corridor, long expiresAt, long scanExpiresAt, long revision,
                                       ConstructionObservation observation) {
        ConstructionRequest(Vec3 sourceCenter, BlockPos sourceBucket, BlockPos threatDestination,
                            BlockPos selectedAnchor, BlockPos threatBucket, TargetScanSnapshot snapshot,
                            WallProfileScanner.TargetEvidence evidence, RouteEvidence routeEvidence,
                            CanonicalFeetCorridor corridor, long expiresAt, long scanExpiresAt, long revision) {
            this(sourceCenter, sourceBucket, threatDestination, selectedAnchor, threatBucket, snapshot, evidence,
                    routeEvidence, corridor, expiresAt, scanExpiresAt, revision, null);
        }
        ConstructionRequest(Vec3 sourceCenter, BlockPos sourceBucket, BlockPos threatDestination,
                            BlockPos selectedAnchor, BlockPos threatBucket, WallProfileScanner.TargetEvidence evidence,
                            long expiresAt, long scanExpiresAt, long revision) {
            this(sourceCenter, sourceBucket, threatDestination, selectedAnchor, threatBucket, null, evidence, null, null,
                    expiresAt, scanExpiresAt, revision, null);
        }

        ConstructionRequest(Vec3 sourceCenter, BlockPos sourceBucket, BlockPos threatDestination,
                            BlockPos selectedAnchor, BlockPos threatBucket, TargetScanSnapshot snapshot,
                            WallProfileScanner.TargetEvidence evidence, long expiresAt, long scanExpiresAt, long revision) {
            this(sourceCenter, sourceBucket, threatDestination, selectedAnchor, threatBucket, snapshot, evidence, null, null,
                    expiresAt, scanExpiresAt, revision, null);
        }

        ConstructionRequest(Vec3 sourceCenter, BlockPos sourceBucket, BlockPos threatDestination,
                            BlockPos selectedAnchor, BlockPos threatBucket, TargetScanSnapshot snapshot,
                            WallProfileScanner.TargetEvidence evidence, RouteEvidence routeEvidence,
                            long expiresAt, long scanExpiresAt, long revision) {
            this(sourceCenter, sourceBucket, threatDestination, selectedAnchor, threatBucket, snapshot, evidence,
                    routeEvidence, null, expiresAt, scanExpiresAt, revision, null);
        }

        ConstructionRequest {
            sourceCenter = sourceCenter == null ? null : new Vec3(sourceCenter.x, sourceCenter.y, sourceCenter.z);
            sourceBucket = sourceBucket == null ? null : sourceBucket.immutable();
            threatDestination = threatDestination == null ? null : threatDestination.immutable();
            selectedAnchor = selectedAnchor == null ? null : selectedAnchor.immutable();
            threatBucket = threatBucket == null ? null : threatBucket.immutable();
            snapshot = snapshot == null ? null : snapshot;
            evidence = evidence == null ? null : new WallProfileScanner.TargetEvidence(
                    evidence.anchor(), evidence.sourceRayIds(), evidence.hazardCells(), evidence.hazardKinds(), evidence.wallFace(),
                    evidence.supportCell(), evidence.sourceBucket(), evidence.targetBucket(),
                    evidence.scenario(), evidence.score(), evidence.rejectionCode(),
                    evidence.structuralCells(), evidence.edgeCell(), evidence.sourceApproachSurface(),
                    evidence.landingSurface(), evidence.invasionSurface());
            routeEvidence = routeEvidence == null ? null : routeEvidence;
            if (selectedAnchor != null && evidence != null && !selectedAnchor.equals(tacticalGoalForEvidence(evidence))) {
                throw new IllegalArgumentException("selectedAnchor must match tacticalGoal");
            }
            if (snapshot != null && evidence != null && !evidence.matchesExactly(snapshot.evidence())) {
                throw new IllegalArgumentException("snapshot evidence must match evidence");
            }
            if (routeEvidence != null && selectedAnchor != null && !selectedAnchor.equals(routeEvidence.anchor())) {
                throw new IllegalArgumentException("routeEvidence anchor must match selectedAnchor");
            }
        }
    }

    private record WorkerObservation(UUID workerId, BlockPos surface, IntakeWindow intakeWindow,
                                     List<BlockPos> sourceRoute, String intakeOutcome,
                                     String surfaceOutcome, String routeOutcome) {}

    private record ConstructionObservation(long observationIndex, long windowGeneration,
                                           long requestRevision, UUID leaderId, List<UUID> roster,
                                           BlockPos target, BlockPos sourceSurface, BlockPos pivot,
                                           BlockPos anchor, BlockPos spanEnd, ConstructionScenario scenario,
                                           BridgeKind bridgeKind,
                                           LivingWallBridgeAdapterPlanner.AdapterKind adapterKind,
                                           WallProfileScanner.TargetEvidence evidence,
                                           CanonicalFeetCorridor corridor, RouteEvidence routeEvidence,
                                           List<BlockPos> hazards, Map<UUID, WorkerObservation> workers) {}

    private record ObservationDecision(ConstructionObservation observation, SitePlan plan, String rejectionReason) {}

    record SelectedTargetEvidence(WallProfileScanner.TargetEvidence evidence, CanonicalFeetCorridor corridor, RouteEvidence routeEvidence,
                                          long scanExpiresAt, long captureTick, long revision, ConstructionObservation observation) {
        SelectedTargetEvidence(WallProfileScanner.TargetEvidence evidence, CanonicalFeetCorridor corridor, RouteEvidence routeEvidence,
                               long scanExpiresAt, long captureTick, long revision) {
            this(evidence, corridor, routeEvidence, scanExpiresAt, captureTick, revision, null);
        }
    }

    enum RejectedLivingWallReason {
        NO_SAFE_LANDING,
        VERTICAL_PROGRESS_INCOMPATIBLE,
        NO_BRIDGE_TRIGGER,
        HAZARD_CORRIDOR_INTERVAL_INVALID,
        SCENARIO_NONE,
        HAZARD_SCENARIO_WITHOUT_HAZARD
    }

    private enum TraversalCorridorRejectionReason {
        INVALID_OR_MISSING_CORRIDOR_EVIDENCE_OR_GEOMETRY,
        ORDERED_SOURCE_EDGE_LANDING_INVASION_HAZARD_MISMATCH,
        CORRIDOR_INTERVAL_BUDGET,
        MISSING_OR_INVALID_INVASION_ENDPOINT_OR_SCENARIO_ENDPOINT,
        THREAT_PROGRESS_FAILURE,
        SWEPT_BODY_CLEARANCE_FAILURE,
        NON_FINITE_LENGTH
    }

    private record TraversalCorridorResolution(ResolvedTraversalCorridor corridor,
                                               TraversalCorridorRejectionReason reason) {
        boolean resolved() {
            return corridor != null;
        }
    }

    record RejectedLivingWallCandidate(String candidateId, BlockPos source, BlockPos edge,
                                       BlockPos firstSolidComponent, List<BlockPos> hazardInterval,
                                       int hazardIntervalTotal, boolean hazardIntervalTruncated,
                                       List<BlockPos> gapInterval, BlockPos landing, BlockPos egress,
                                       RejectedLivingWallReason reason, long scanGeneration, long captureTick,
                                       long scanExpiresAt, long requestRevision) {
        RejectedLivingWallCandidate {
            source = source == null ? null : source.immutable();
            edge = edge == null ? null : edge.immutable();
            firstSolidComponent = firstSolidComponent == null ? null : firstSolidComponent.immutable();
            hazardInterval = hazardInterval == null ? List.of() : List.copyOf(hazardInterval);
            hazardIntervalTotal = Math.max(0, hazardIntervalTotal);
            hazardIntervalTruncated = hazardIntervalTruncated || hazardIntervalTotal > hazardInterval.size();
            gapInterval = gapInterval == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(gapInterval));
            landing = landing == null ? null : landing.immutable();
            egress = egress == null ? null : egress.immutable();
        }
    }

    private record CellFingerprint(BlockPos cell, String kind, String liveStateFingerprint) {
        CellFingerprint {
            cell = cell == null ? null : cell.immutable();
        }
    }

    private record RouteProgressKey(UUID workerId, String certificateFingerprint) {
    }

    private record SemanticRequestKey(UUID plannerId, BlockPos targetBucket, BlockPos threatIdentity) {
        SemanticRequestKey {
            targetBucket = targetBucket == null ? null : targetBucket.immutable();
            threatIdentity = threatIdentity == null ? null : threatIdentity.immutable();
        }
    }

    private record TerminalAdmissionFailure(SemanticRequestKey key, String fingerprint) {
    }

    record RouteEvidence(UUID workerId, IntakeWindow intakeWindow, BlockPos anchor, List<BlockPos> routeNodes,
                         boolean pathReachable, long revision, String fingerprint, String corridorId,
                         Identifier dimension, Vec3 certifiedStart, AABB certifiedBody, Pose certifiedPose,
                         BlockPos endpoint, String worldFingerprint) {
        RouteEvidence(UUID workerId, IntakeWindow intakeWindow, BlockPos anchor, List<BlockPos> routeNodes,
                      boolean pathReachable, long revision, String fingerprint) {
            this(workerId, intakeWindow, anchor, routeNodes, pathReachable, revision, fingerprint, "",
                    null, null, null, null, null, "");
        }

        RouteEvidence(UUID workerId, IntakeWindow intakeWindow, BlockPos anchor, List<BlockPos> routeNodes,
                      boolean pathReachable, long revision, String fingerprint, String corridorId) {
            this(workerId, intakeWindow, anchor, routeNodes, pathReachable, revision, fingerprint, corridorId,
                    null, null, null, null, null, "");
        }

        RouteEvidence {
            workerId = workerId;
            intakeWindow = intakeWindow == null ? null : new IntakeWindow(intakeWindow.feederCell(), intakeWindow.feederApproach(),
                    intakeWindow.transientAnchor());
            anchor = anchor == null ? null : anchor.immutable();
            routeNodes = routeNodes == null ? List.of() : List.copyOf(routeNodes);
            fingerprint = fingerprint == null ? "" : fingerprint;
            corridorId = corridorId == null ? "" : corridorId;
            certifiedStart = certifiedStart == null ? null : new Vec3(certifiedStart.x, certifiedStart.y, certifiedStart.z);
            certifiedBody = certifiedBody == null ? null : new AABB(certifiedBody.minX, certifiedBody.minY,
                    certifiedBody.minZ, certifiedBody.maxX, certifiedBody.maxY, certifiedBody.maxZ);
            endpoint = endpoint == null ? null : endpoint.immutable();
            worldFingerprint = worldFingerprint == null ? "" : worldFingerprint;
        }
    }

    private record TargetScanSnapshot(Vec3 sourceOrigin, Vec3 sourceCenter, BlockPos threatIdentity,
                                      WallProfileScanner.TargetEvidence evidence, long captureTick, long revision,
                                      long expiryTick, double probeRadius, double bodyWidth, double bodyHeight,
                                      ConstructionScenario scenario, List<CellFingerprint> fingerprint,
                                      LivingWallTacticalSnapshot.Identity tacticalIdentity, RouteEvidence routeEvidence) {
        TargetScanSnapshot(Vec3 sourceOrigin, Vec3 sourceCenter, BlockPos threatIdentity,
                           WallProfileScanner.TargetEvidence evidence, long captureTick, long revision,
                           long expiryTick, double probeRadius, double bodyWidth, double bodyHeight,
                           ConstructionScenario scenario, List<CellFingerprint> fingerprint) {
            this(sourceOrigin, sourceCenter, threatIdentity, evidence, captureTick, revision, expiryTick, probeRadius,
                    bodyWidth, bodyHeight, scenario, fingerprint, null, null);
        }

        TargetScanSnapshot(Vec3 sourceOrigin, Vec3 sourceCenter, BlockPos threatIdentity,
                           WallProfileScanner.TargetEvidence evidence, long captureTick, long revision,
                           long expiryTick, double probeRadius, double bodyWidth, double bodyHeight,
                           ConstructionScenario scenario, List<CellFingerprint> fingerprint,
                           LivingWallTacticalSnapshot.Identity tacticalIdentity) {
            this(sourceOrigin, sourceCenter, threatIdentity, evidence, captureTick, revision, expiryTick, probeRadius,
                    bodyWidth, bodyHeight, scenario, fingerprint, tacticalIdentity, null);
        }

        TargetScanSnapshot {
            sourceOrigin = sourceOrigin == null ? null : new Vec3(sourceOrigin.x, sourceOrigin.y, sourceOrigin.z);
            sourceCenter = sourceCenter == null ? null : new Vec3(sourceCenter.x, sourceCenter.y, sourceCenter.z);
            threatIdentity = threatIdentity == null ? null : threatIdentity.immutable();
            evidence = evidence == null ? null : evidence;
            fingerprint = fingerprint == null ? List.of() : List.copyOf(fingerprint);
            tacticalIdentity = tacticalIdentity;
            routeEvidence = routeEvidence == null ? null : routeEvidence;
        }

        BlockPos selectedAnchor() {
            return evidence == null ? null : evidence.anchor();
        }

        String identity() {
            return captureTick + ":" + revision + ":" + expiryTick + ":" + Objects.toString(sourceOrigin) + ":"
                    + Objects.toString(sourceCenter) + ":" + Objects.toString(threatIdentity) + ":"
                    + Objects.toString(scenario) + ":" + Objects.toString(evidence == null ? null : evidence.fingerprint())
                    + ":" + fingerprint + ":" + Objects.toString(tacticalIdentity == null ? null : tacticalIdentity.key())
                    + ":" + Objects.toString(routeEvidence == null ? null : routeEvidence.fingerprint());
        }

        LivingWallTacticalSnapshot.RouteDecision routeDecision(List<BlockPos> route, boolean allowUnknownExpansion) {
            if (tacticalIdentity == null) {
                return LivingWallTacticalSnapshot.RouteDecision.UNKNOWN;
            }
            LivingWallTacticalSnapshot.Bounds bounds = new LivingWallTacticalSnapshot.Bounds(
                    sourceBucket(), threatBucket(), LivingWallConfig.MAX_GAP.get(), LivingWallConfig.MAX_HEIGHT.get());
            LivingWallTacticalSnapshot.Snapshot tacticalSnapshot = new LivingWallTacticalSnapshot.Snapshot(bounds,
                    tacticalIdentity, null, List.of(), revision, expiryTick);
            return tacticalSnapshot.routeDecision(route, allowUnknownExpansion);
        }

        LivingWallTacticalSnapshot.ExpansionStatus expansionStatus(List<BlockPos> route, int tileBudget) {
            if (tacticalIdentity == null) {
                return LivingWallTacticalSnapshot.ExpansionStatus.UNKNOWN;
            }
            LivingWallTacticalSnapshot.Bounds bounds = new LivingWallTacticalSnapshot.Bounds(
                    sourceBucket(), threatBucket(), LivingWallConfig.MAX_GAP.get(), LivingWallConfig.MAX_HEIGHT.get());
            LivingWallTacticalSnapshot.Snapshot tacticalSnapshot = new LivingWallTacticalSnapshot.Snapshot(bounds,
                    tacticalIdentity, null, List.of(), revision, expiryTick);
            return tacticalSnapshot.expansionStatus(route, tileBudget);
        }

        private BlockPos sourceBucket() {
            return evidence == null ? null : evidence.sourceBucket();
        }

        private BlockPos threatBucket() {
            return evidence == null ? null : evidence.targetBucket();
        }
    }

    static WallProfileScanner.TargetEvidence selectCachedTargetEvidence(List<WallProfileScanner.TargetEvidence> evidence,
                                                                        BlockPos selectedAnchor) {
        if (evidence == null || evidence.isEmpty()) return null;
        for (WallProfileScanner.TargetEvidence candidate : evidence) {
            if (candidate != null && (selectedAnchor == null || selectedAnchor.equals(candidate.anchor()))) {
                return candidate;
            }
        }
        return null;
    }

    static record ConstructionSiteKey(long revision, BlockPos sourceBucket, BlockPos threatDestination,
                                       BlockPos pivot, BlockPos anchor, BlockPos spanEnd,
                                       ConstructionScenario scenario, List<BlockPos> hazardCells, String corridorId) {
        ConstructionSiteKey(long revision, BlockPos sourceBucket, BlockPos threatDestination,
                            BlockPos pivot, BlockPos anchor, BlockPos spanEnd,
                            ConstructionScenario scenario, List<BlockPos> hazardCells) {
            this(revision, sourceBucket, threatDestination, pivot, anchor, spanEnd, scenario, hazardCells, "");
        }

        ConstructionSiteKey {
            sourceBucket = sourceBucket == null ? null : sourceBucket.immutable();
            threatDestination = threatDestination == null ? null : threatDestination.immutable();
            pivot = pivot == null ? null : pivot.immutable();
            anchor = anchor == null ? null : anchor.immutable();
            spanEnd = spanEnd == null ? null : spanEnd.immutable();
            hazardCells = hazardCells == null ? List.of() : List.copyOf(hazardCells);
            corridorId = corridorId == null ? "" : corridorId;
        }
    }

    private record ConstructionRequestKey(String snapshotIdentity, BlockPos sourceBucket, BlockPos threatDestination,
                                          BlockPos targetBucket, BlockPos anchor, BlockPos wallFace, BlockPos supportCell,
                                          ConstructionScenario scenario, List<BlockPos> hazardCells,
                                          long terrainSignature) {
        ConstructionRequestKey {
            snapshotIdentity = snapshotIdentity == null ? null : snapshotIdentity;
            sourceBucket = sourceBucket == null ? null : sourceBucket.immutable();
            threatDestination = threatDestination == null ? null : threatDestination.immutable();
            targetBucket = targetBucket == null ? null : targetBucket.immutable();
            anchor = anchor == null ? null : anchor.immutable();
            wallFace = wallFace == null ? null : wallFace.immutable();
            supportCell = supportCell == null ? null : supportCell.immutable();
            hazardCells = hazardCells == null ? List.of() : List.copyOf(hazardCells);
        }
    }

    private static final class ConstructionIntent {
        final UUID plannerId;
        volatile ConstructionRequest request;
        ConstructionSiteKey siteKey;
        ConstructionSiteKey candidateKey;
        final long createdTick;
        final long recruitmentDeadline;
        final Identifier dimension;
        long scanRevision;
        long terrainSignature;
        long lastProgressTick;
        UUID attachedFormationId;
        Phase phase;
        TraversalCorridorWitness traversalWitness;
        final Map<RouteProbeOwner, RouteProbeReservation> routeProbeLedger = new EnumMap<>(RouteProbeOwner.class);
        int routeProbeReservations;
        int pathSuccessCount;

        ConstructionIntent(UUID plannerId, ConstructionRequest request, ConstructionSiteKey candidateKey, long now) {
            this.plannerId = plannerId;
            this.request = request;
            this.candidateKey = candidateKey;
            this.dimension = null;
            this.siteKey = null;
            this.createdTick = now;
            this.recruitmentDeadline = now + RECRUITMENT_TIMEOUT_TICKS;
            this.scanRevision = request == null ? -1L : request.revision();
            this.lastProgressTick = now;
            this.attachedFormationId = null;
            this.phase = Phase.SCAN;
        }
    }

    private static ConstructionSiteKey canonicalConstructionSiteKey(ConstructionRequest request, BlockPos pivot,
                                                                    BlockPos anchor, BlockPos spanEnd,
                                                                    ConstructionScenario scenario,
                                                                    List<BlockPos> hazardCells) {
        if (request == null || pivot == null || anchor == null || spanEnd == null || scenario == null) return null;
        return new ConstructionSiteKey(request.revision(), request.sourceBucket(), request.threatDestination(),
                pivot, anchor, spanEnd, scenario, hazardCells,
                request.corridor() == null ? "" : request.corridor().corridorId());
    }

    private static ConstructionSiteKey constructionSiteKey(ConstructionRequest request, BlockPos pivot,
                                                            SitePlan plan) {
        if (request == null || pivot == null || plan == null) return null;
        return canonicalConstructionSiteKey(request, pivot, plan.anchor(), plan.spanEnd(), plan.scenario(), plan.hazardCells());
    }

    private static ConstructionSiteKey candidateConstructionSiteKey(ConstructionRequest request, BlockPos candidatePivot,
                                                                    SitePlan plan) {
        if (request == null || candidatePivot == null || plan == null) return null;
        return canonicalConstructionSiteKey(request, candidatePivot, plan.anchor(), plan.spanEnd(), plan.scenario(),
                plan.hazardCells());
    }

    private static ConstructionSiteKey intentCandidateKey(ConstructionRequest request, BlockPos pivot,
                                                          SitePlan plan) {
        if (request == null || request.evidence() == null || pivot == null || plan == null) return null;
        return canonicalConstructionSiteKey(request, pivot, plan.anchor(), plan.spanEnd(), plan.scenario(), plan.hazardCells());
    }

    private static void clearConstructionIntent(PerLevelState state, UUID plannerId) {
        if (state == null || plannerId == null) return;
        Object stored = ((Map<?, ?>) state.constructionIntents).get(plannerId);
        if (!(stored instanceof ConstructionIntent intent) || intent.attachedFormationId == null) {
            ((Map<?, ?>) state.constructionIntents).remove(plannerId, stored);
        }
    }

    private static void clearConstructionIntent(PerLevelState state, ConstructionIntent intent) {
        if (state == null || intent == null) return;
        state.constructionIntents.remove(intent.plannerId, intent);
    }

    private static void copyAssemblyIntakeProvenance(Formation formation, Mob mob) {
        if (formation == null || mob == null) return;
        MobTicket ticket = formation.mobTickets.get(mob.getUUID());
        IntakeWindow provenance = ticket == null ? null : ticket.intakeWindow;
        if (provenance != null && provenance.feederCell() != null) {
            formation.assemblyIntakeProvenance.put(mob.getUUID(), provenance);
        }
    }

    private static void recordSuccessfulPlacementProvenance(Formation formation, UUID mobId, BlockPos provenance) {
        if (formation == null || mobId == null || provenance == null) return;
        formation.successfulPlacementProvenance.put(mobId, provenance);
    }

    private static void restoreFoundationWorkerState(Formation formation, Mob mob, MobTicket assignment) {
        if (formation == null || mob == null || assignment == null) return;
        if (assignment.state == MobTicketState.ASSEMBLED) {
            transitionTicket(assignment, MobTicketState.STAGING_AT_ASSEMBLY);
        }
        Vec3 position = mob.position();
        mob.setPos(position.x, position.y, position.z);
        formation.assemblyHoldPositions.put(mob.getUUID(), position);
        holdAssemblyWorker(mob, formation);
    }

    private static boolean preflightFoundationSupport(ServerLevel level, Formation formation, Mob mob, MobTicket assignment) {
        if (level == null || formation == null || mob == null || assignment == null) return false;
        int foundationSlots = Math.min(formation.columnCount, formation.members.size() + 1);
        if (foundationSlots <= 0) return false;
        for (int slot = 0; slot < foundationSlots; slot++) {
            Mob candidate = slot == formation.members.size() ? mob : getMob(level, formation.members.get(slot));
            Vec3 target = formationSlotPosition(formation, slot, 0.0D);
            if (candidate == null || !candidate.isAlive() || candidate.isRemoved()
                    || !hasFoundationTerrainSupport(level, target)) {
                if (assignment.state == MobTicketState.ASSEMBLED) {
                    restoreFoundationWorkerState(formation, mob, assignment);
                }
                return false;
            }
        }
        return true;
    }

    private static boolean hasFoundationTerrainSupport(ServerLevel level, Vec3 target) {
        if (level == null || target == null) return false;
        BlockPos feet = BlockPos.containing(target);
        BlockPos support = feet.below();
        return level.isLoaded(feet) && level.isLoaded(support)
                && !level.getBlockState(support).getCollisionShape(level, support).isEmpty();
    }

    private static boolean isLiveAttachedIntent(PerLevelState state, ConstructionIntent intent) {
        if (state == null || intent == null || intent.attachedFormationId == null) return false;
        Formation attached = state.formations.get(intent.attachedFormationId);
        return attached != null && !attached.collapsed && attached.intentToken == intent;
    }

    private record ExactSiteKey(BlockPos pivot, BlockPos anchor, BlockPos spanEnd, ConstructionScenario scenario,
                                List<BlockPos> hazardCells) {
        ExactSiteKey {
            hazardCells = hazardCells == null ? List.of() : List.copyOf(hazardCells);
        }
    }

    private static ExactSiteKey exactSiteKey(BlockPos pivot, BlockPos anchor, BlockPos spanEnd,
                                             ConstructionScenario scenario, Set<BlockPos> hazards) {
        return new ExactSiteKey(pivot == null ? null : pivot.immutable(),
                anchor == null ? null : anchor.immutable(),
                spanEnd == null ? null : spanEnd.immutable(),
                scenario, hazards == null ? List.of() : new ArrayList<>(hazards));
    }

    private static ExactSiteKey exactSiteKey(BlockPos pivot, BlockPos anchor, BlockPos spanEnd,
                                             ConstructionScenario scenario, List<BlockPos> hazards) {
        return new ExactSiteKey(pivot == null ? null : pivot.immutable(),
                anchor == null ? null : anchor.immutable(),
                spanEnd == null ? null : spanEnd.immutable(),
                scenario, hazards);
    }

    private static final class CachedTargetScan {
        final List<WallProfileScanner.TargetEvidence> evidence;
        final List<SelectedTargetEvidence> selectedEvidence;
        final long captureTick;
        final long expiresAt;
        final int cursor;
        final long lastRefineTick;
        final long revision;
        final long rosterFingerprint;
        final long terrainFingerprint;

        CachedTargetScan(List<WallProfileScanner.TargetEvidence> evidence, List<SelectedTargetEvidence> selectedEvidence,
                         long captureTick, long expiresAt, int cursor, long lastRefineTick, long revision,
                         long rosterFingerprint, long terrainFingerprint) {
            this.evidence = List.copyOf(evidence);
            this.selectedEvidence = List.copyOf(selectedEvidence);
            this.captureTick = captureTick;
            this.expiresAt = expiresAt;
            this.cursor = cursor;
            this.lastRefineTick = lastRefineTick;
            this.revision = revision;
            this.rosterFingerprint = rosterFingerprint;
            this.terrainFingerprint = terrainFingerprint;
        }

        CachedTargetScan(List<WallProfileScanner.TargetEvidence> evidence,
                         long captureTick, long expiresAt, int cursor, long lastRefineTick, long revision) {
            this(evidence, List.of(), captureTick, expiresAt, cursor, lastRefineTick, revision, 0L, 0L);
        }

        List<BlockPos> targets() {
            if (selectedEvidence.isEmpty() || selectedEvidence.stream().anyMatch(selection ->
                    selection == null || selection.evidence() == null || selection.corridor() == null)) return List.of();
            return selectedEvidence.stream().map(selection -> selection.evidence().anchor()).toList();
        }
    }

    private record ScoredTarget(WallProfileScanner.TargetEvidence evidence, CanonicalFeetCorridor corridor,
                                LivingWallBridgeAdapterPlanner.ScoredCandidate scoredCandidate) {
        double score() {
            return scoredCandidate == null ? Double.POSITIVE_INFINITY : scoredCandidate.scoreBp();
        }
    }

    private record TargetLandingSelection(BlockPos landing, CanonicalCorridorDecision decision) {
    }

    private static boolean isValidCachedTargetScan(ServerLevel level, List<Mob> cluster, BlockPos threat,
                                                   long now, long generation, TargetScanKey key,
                                                   CachedTargetScan cached) {
        if (level == null || cluster == null || cluster.isEmpty() || threat == null || key == null || cached == null) {
            return false;
        }
        if (cached.expiresAt <= now || cached.revision < 0L || cached.captureTick < 0L) return false;
        if (cached.evidence.isEmpty() && cached.selectedEvidence.isEmpty()) {
            return cached.rosterFingerprint == targetRosterFingerprint(cluster);
        }
        if (cached.evidence.isEmpty() || cached.selectedEvidence.isEmpty()) return false;
        if (cached.selectedEvidence.size() != cached.evidence.size()) return false;
        if (cached.rosterFingerprint != targetRosterFingerprint(cluster)
                || cached.terrainFingerprint != targetTerrainFingerprint(level, cached.selectedEvidence)) return false;
        for (int index = 0; index < cached.evidence.size(); index++) {
            WallProfileScanner.TargetEvidence evidence = cached.evidence.get(index);
            SelectedTargetEvidence selected = cached.selectedEvidence.get(index);
            if (evidence == null || selected == null || selected.evidence() == null || selected.corridor() == null
                    || selected.captureTick() != cached.captureTick
                    || !evidence.matchesExactly(selected.evidence())
                    || !selected.corridor().matchesEvidence(selected.evidence())
                    || !validateStaticCorridor(level, selected.corridor().pivot(), threat,
                    selected.evidence(), selected.corridor()).routeProbeAllowed()) return false;
        }
        return cached.targets().size() == cached.evidence.size();
    }

    private static long targetRosterFingerprint(List<Mob> cluster) {
        long hash = 0xcbf29ce484222325L;
        if (cluster == null) return hash;
        for (Mob mob : cluster.stream().filter(Objects::nonNull)
                .sorted(Comparator.comparing(mob -> mob.getUUID().toString())).toList()) {
            hash ^= mob.getUUID().getMostSignificantBits();
            hash *= 0x100000001b3L;
            hash ^= mob.getUUID().getLeastSignificantBits();
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long targetTerrainFingerprint(ServerLevel level, List<SelectedTargetEvidence> selected) {
        long hash = 0xcbf29ce484222325L;
        if (level == null || selected == null) return hash;
        for (SelectedTargetEvidence selection : selected) {
            if (selection == null || selection.evidence() == null || selection.corridor() == null) continue;
            WallProfileScanner.TargetEvidence evidence = selection.evidence();
            Set<BlockPos> cells = new LinkedHashSet<>(evidence.structuralCells());
            cells.addAll(evidence.hazardCells());
            cells.add(evidence.anchor());
            cells.add(evidence.wallFace());
            cells.add(evidence.supportCell());
            cells.addAll(selection.corridor().centerlineCells());

            Set<BlockPos> expanded = new LinkedHashSet<>();
            for (BlockPos cell : cells) {
                if (cell == null) continue;
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) expanded.add(cell.offset(dx, dy, dz));
            }
            cells.addAll(expanded);
            for (BlockPos cell : cells) {
                if (cell == null) continue;
                hash ^= cell.asLong();
                hash *= 0x100000001b3L;
                hash ^= liveStateFingerprintHash(level, cell);
                hash *= 0x100000001b3L;
            }
        }
        return hash;
    }

    private static void markTerrainMutation(ServerLevel level, Iterable<BlockPos> positions) {
        if (level == null || !level.getServer().isSameThread() || positions == null) return;
        PerLevelState state = state(level);
        boolean changed = false;
        for (BlockPos raw : positions) {
            if (raw == null) continue;
            BlockPos pos = raw.immutable();
            state.terrainCellEpochs.put(pos.asLong(), ++state.terrainEpoch);
            state.terrainChunkEpochs.put(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4), state.terrainEpoch);
            changed = true;
        }
        if (changed) {

            state.targetScans.clear();
            state.positionPlans.clear();
        }
    }

    static long terrainEpochForTest(ServerLevel level) {
        return level == null ? -1L : state(level).terrainEpoch;
    }

    static void markTerrainMutationForTest(ServerLevel level, BlockPos... positions) {
        markTerrainMutation(level, positions == null ? List.of() : List.of(positions));
    }

    private static List<BlockPos> around(BlockPos center) {
        if (center == null) return List.of();
        List<BlockPos> cells = new ArrayList<>();
        cells.add(center);
        cells.add(center.relative(net.minecraft.core.Direction.NORTH));
        cells.add(center.relative(net.minecraft.core.Direction.SOUTH));
        cells.add(center.relative(net.minecraft.core.Direction.EAST));
        cells.add(center.relative(net.minecraft.core.Direction.WEST));
        cells.add(center.above());
        cells.add(center.below());
        return cells;
    }

    private static final class TargetRouteProbeStats {
        int intakeResolutionAttempts;
        int routeProbeAttempts;
        int pathCreationAttempts;
        int reservations;
        int successes;
        int routeNodes;
        int routeSamples;
        int routeReuses;
        int routeRecomputations;
        int eligibleCandidates;
        int groundedCandidates;
        int deferredCandidates;
        String routeMode;
        String failureReason;
        String workerFailureReason;
        final Map<String, Integer> candidateFailureCounts = new LinkedHashMap<>();
        String lastCandidateFailure;

        void recordCandidateFailure(String reason) {
            if (reason == null || reason.isBlank()) return;
            lastCandidateFailure = reason;
            if (candidateFailureCounts.size() < 8 || candidateFailureCounts.containsKey(reason)) {
                candidateFailureCounts.merge(reason, 1, Integer::sum);
            }
        }

        String aggregateFailure() {
            return candidateFailureCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(lastCandidateFailure);
        }
    }

    private record WorkerRouteStartState(BlockPos surface, boolean blockLoaded, boolean aboveLoaded, boolean belowLoaded,
                                         boolean grounded, boolean falling, String fluidState, int referenceY,
                                         String failureReason) {
        boolean ready() {
            return failureReason == null;
        }
    }

    private record AdmissionIntakeResolution(List<IntakeWindow> windows, String failureReason) {
        AdmissionIntakeResolution {
            windows = windows == null ? List.of() : List.copyOf(windows);
        }

        IntakeWindow primaryWindow() {
            return windows.isEmpty() ? null : windows.get(0);
        }
    }

    private record RouteProbeOutcome(RouteEvidence routeEvidence, String failureReason) {
        boolean successful() {
            return routeEvidence != null && routeEvidence.pathReachable();
        }
    }

    enum CorridorValidationReason { VALID, NULL_CORRIDOR, NULL_EVIDENCE, DIMENSION_MISMATCH, PIVOT_MISMATCH,
        TARGET_MISMATCH, ENDPOINT_MISMATCH, SCENARIO_MISMATCH, SOURCE_UNSAFE, LANDING_UNSAFE, EGRESS_UNSAFE,
        BODY_GEOMETRY_INVALID, STATIC_CORRIDOR_INVALID }

    record CorridorValidationResult(boolean valid, CorridorValidationReason reason, int checkedCells,
                                    boolean routeProbeAllowed, String detail) {
        CorridorValidationResult(boolean valid, CorridorValidationReason reason, int checkedCells,
                                 boolean routeProbeAllowed) {
            this(valid, reason, checkedCells, routeProbeAllowed, null);
        }
    }

    enum CanonicalCorridorRejectionCategory { INVALID_CANONICAL_EVIDENCE, EMPTY_SOURCE_RAY_IDS, NULL_ANCHOR,
        NULL_WALL_FACE, NON_FINITE_SCORE, INVALID_GLOBAL_STRUCTURAL_INDEX, STATIC_CORRIDOR_INVALID }

    record CanonicalCorridorDecision(WallProfileScanner.TargetEvidence evidence, CanonicalFeetCorridor corridor,
                                     CanonicalCorridorRejectionCategory rejection) {
        boolean accepted() { return evidence != null && corridor != null && rejection == null; }
    }

    record CanonicalAuthorityCounters(long evaluated, long accepted, long rejected, long invalidCanonicalEvidence,
                                      long invalidCacheWithoutCorridor, long intakeResolutionAttempts,
                                      long routeProbeAttempts, long reservations, long pathCreationAttempts,
                                      Map<CanonicalCorridorRejectionCategory, Long> rejectionCounts) {}

    private static long canonicalEvaluated, canonicalAccepted, canonicalRejected, canonicalInvalid;
    private static final EnumMap<CanonicalCorridorRejectionCategory, Long> CANONICAL_REJECTIONS = new EnumMap<>(CanonicalCorridorRejectionCategory.class);

    static void resetCanonicalAuthorityCountersForTest() {
        canonicalEvaluated = canonicalAccepted = canonicalRejected = canonicalInvalid = 0L;
        CANONICAL_REJECTIONS.clear();
        for (CanonicalCorridorRejectionCategory category : CanonicalCorridorRejectionCategory.values()) CANONICAL_REJECTIONS.put(category, 0L);
    }
    static CanonicalAuthorityCounters canonicalAuthorityCountersForTest() {
        return new CanonicalAuthorityCounters(canonicalEvaluated, canonicalAccepted, canonicalRejected, canonicalInvalid,
                0L, 0L, 0L, 0L, 0L, Map.copyOf(CANONICAL_REJECTIONS));
    }
    static long canonicalRejectionCountForTest(CanonicalCorridorRejectionCategory category) {
        return CANONICAL_REJECTIONS.getOrDefault(category, 0L);
    }

    static CanonicalCorridorDecision evaluateCanonicalCorridor(ServerLevel level, BlockPos sourcePivot,
            BlockPos threatDestination, WallProfileScanner.RayEvidence ray, int rayOrdinal,
            BlockPos structuralCell, int structuralIndex) {
        BlockPos candidate = resolveCanonicalLanding(level, structuralCell, sourcePivot, ray == null ? null : ray.direction());
        return evaluateCanonicalCorridorAtCandidate(level, sourcePivot, threatDestination, ray, rayOrdinal,
                structuralCell, structuralIndex, candidate);
    }

    private static CanonicalCorridorDecision evaluateCanonicalCorridorAtCandidate(ServerLevel level,
                                                                                   BlockPos sourcePivot,
                                                                                   BlockPos threatDestination,
                                                                                   WallProfileScanner.RayEvidence ray,
                                                                                   int rayOrdinal,
                                                                                   BlockPos structuralCell,
                                                                                   int structuralIndex,
                                                                                   BlockPos candidate) {
        canonicalEvaluated++;
        CanonicalCorridorRejectionCategory rejection = null;
        if (level == null || sourcePivot == null || threatDestination == null || ray == null || structuralCell == null
                || rayOrdinal < 0 || structuralIndex < 0 || structuralIndex >= ray.structuralCells().size()) rejection = CanonicalCorridorRejectionCategory.INVALID_CANONICAL_EVIDENCE;
        else if (ray.rayId() == Long.MIN_VALUE) rejection = CanonicalCorridorRejectionCategory.EMPTY_SOURCE_RAY_IDS;
        if (rejection != null) {
            canonicalRejected++; if (rejection == CanonicalCorridorRejectionCategory.INVALID_CANONICAL_EVIDENCE) canonicalInvalid++;
            CANONICAL_REJECTIONS.merge(rejection, 1L, Long::sum); return new CanonicalCorridorDecision(null, null, rejection);
        }
        if (candidate == null) rejection = CanonicalCorridorRejectionCategory.NULL_ANCHOR;
        ConstructionScenario scenario = candidate == null ? ConstructionScenario.NONE
                : classifyCanonicalScenario(level, sourcePivot, candidate, new LinkedHashSet<>(carriedRayHazards(ray, sourcePivot, candidate)));
        WallProfileScanner.TargetEvidence evidence = candidate == null ? null
                : buildTargetEvidence(level, Vec3.atBottomCenterOf(sourcePivot), threatDestination, ray, candidate, structuralCell, scenario, 0.0D);
        CanonicalFeetCorridor corridor = evidence == null ? null
                : CanonicalFeetCorridor.maybeFromEvidence(level.dimension().identifier(), sourcePivot, scenario, evidence);
        if (evidence == null || evidence.anchor() == null) rejection = CanonicalCorridorRejectionCategory.NULL_ANCHOR;
        else if (evidence.wallFace() == null) rejection = CanonicalCorridorRejectionCategory.NULL_WALL_FACE;
        else if (!Double.isFinite(evidence.score())) rejection = CanonicalCorridorRejectionCategory.NON_FINITE_SCORE;
        else if (corridor == null || !validateStaticCorridor(level, sourcePivot, threatDestination, evidence, corridor).routeProbeAllowed()) rejection = CanonicalCorridorRejectionCategory.STATIC_CORRIDOR_INVALID;
        if (rejection != null) { canonicalRejected++; CANONICAL_REJECTIONS.merge(rejection, 1L, Long::sum); return new CanonicalCorridorDecision(null, null, rejection); }
        canonicalAccepted++; return new CanonicalCorridorDecision(evidence, corridor, null);
    }

    static CorridorValidationResult validateStaticCorridor(ServerLevel level, BlockPos sourcePivot,
                                                           BlockPos threatDestination,
                                                           WallProfileScanner.TargetEvidence evidence,
                                                           CanonicalFeetCorridor corridor) {
        if (corridor == null) return new CorridorValidationResult(false, CorridorValidationReason.NULL_CORRIDOR, 0, false);
        if (evidence == null) return new CorridorValidationResult(false, CorridorValidationReason.NULL_EVIDENCE, 0, false);
        String detail = null;
        CorridorValidationReason reason = CorridorValidationReason.VALID;
        BlockPos expectedTakeoff = evidence.sourceApproachSurface() != null ? evidence.sourceApproachSurface() : sourcePivot;
        BlockPos expectedLanding = evidence.landingSurface() != null ? evidence.landingSurface() : evidence.anchor();
        if (!Objects.equals(corridor.pivot(), sourcePivot)) {
            reason = CorridorValidationReason.PIVOT_MISMATCH;
            detail = "pivot";
        } else if (!Objects.equals(corridor.tacticalGoal(), evidence.anchor())) {
            reason = CorridorValidationReason.TARGET_MISMATCH;
            detail = "target";
        } else if (!Objects.equals(corridor.takeoffFeet(), expectedTakeoff)) {
            reason = CorridorValidationReason.ENDPOINT_MISMATCH;
            detail = "takeoff";
        } else if (!Objects.equals(corridor.bridgeLandingFeet(), expectedLanding)
                || !Objects.equals(corridor.egressFeet(), evidence.invasionSurface())) {
            reason = CorridorValidationReason.ENDPOINT_MISMATCH;
            detail = "landing_or_egress";
        } else if (!Objects.equals(corridor.obstacleContactCell(), evidence.wallFace())) {
            reason = CorridorValidationReason.ENDPOINT_MISMATCH;
            detail = "wall_contact";
        } else if (!Objects.equals(corridor.hazardCells(), evidence.hazardCells())) {
            reason = CorridorValidationReason.STATIC_CORRIDOR_INVALID;
            detail = "hazards";
        } else if (!Objects.equals(corridor.hazardKinds(), evidence.hazardKinds())) {
            reason = CorridorValidationReason.STATIC_CORRIDOR_INVALID;
            detail = "hazard_kinds";
        } else if (!Objects.equals(corridor.sourceRayIds(), evidence.sourceRayIds())) {
            reason = CorridorValidationReason.STATIC_CORRIDOR_INVALID;
            detail = "source_rays";
        } else if (!corridor.matchesEvidence(evidence)) {
            reason = CorridorValidationReason.STATIC_CORRIDOR_INVALID;
            detail = "evidence";
        }
        boolean valid = reason == CorridorValidationReason.VALID;
        if (!valid && SoundAttractMod.LOGGER.isDebugEnabled()) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ROUTING)) {
            SoundAttractMod.LOGGER.debug("[LivingWallCoordinator] static_corridor_validation_mismatch target={} corridorId={} detail={} reason={} sourcePivot={} pivot={} evidenceFingerprint={} corridorFingerprint={}",
                    threatDestination, corridor.corridorId(), detail, reason, sourcePivot, corridor.pivot(),
                    evidenceIdentity(evidence), corridor.corridorId());
            }
        }
        return new CorridorValidationResult(valid, reason, corridor.centerlineCells().size(), valid, detail);
    }

    private static String evidenceIdentity(WallProfileScanner.TargetEvidence evidence) {
        if (evidence == null) return "null";
        return Integer.toHexString(Objects.hash(evidence.anchor(), evidence.sourceApproachSurface(),
                evidence.landingSurface(), evidence.invasionSurface(), evidence.wallFace(),
                evidence.hazardCells(), evidence.hazardKinds(), evidence.sourceRayIds()));
    }

    private record SourceStagingAttempt(Mob worker, IntakeWindow intakeWindow, BlockPos intakeOrigin,
                                        BlockPos workerSurface, double distanceToEndpointSqr) {
    }

    private record AdmissionIntakeSurfaceResolution(BlockPos surface, String failureReason) {
    }

    private enum AdmissionIntakeFailureScope {
        CANDIDATE,
        WORKER
    }

    private record WallProfileKey(int pivotX, int pivotY, int pivotZ,
                                  int targetX, int targetY, int targetZ,
                                  int maxHeight, int maxGap, long revision, String snapshotIdentity) {
    }

    private record CachedWallProfile(WallProfileScanner.Profile profile, long expiresAt, long revision, long terrainSignature,
                                     String snapshotIdentity) {
    }

    private record TiltCheck(boolean valid, int movingLayer, int firstSlot, int secondSlot,
                             double gapX, double gapY, double gapZ, String reason) {
    }

    enum ConstructionScenario {
        NONE,
        JUMP_BRIDGE,
        WALL_LEDGE,
        HAZARD_AND_WALL,
        DROP_TO_LEDGE
    }

    enum BridgeKind {
        NONE,
        TILT,
        JUMP,
        DROP
    }

    static BridgeKind bridgeKind(ConstructionScenario scenario) {
        if (scenario == null) return BridgeKind.NONE;
        return switch (scenario) {
            case WALL_LEDGE -> BridgeKind.TILT;
            case JUMP_BRIDGE -> BridgeKind.JUMP;
            case DROP_TO_LEDGE -> BridgeKind.DROP;
            default -> BridgeKind.NONE;
        };
    }

    static boolean requiresTilt(ConstructionScenario scenario) {
        return bridgeKind(scenario) == BridgeKind.TILT;
    }

    static boolean isDirectSpan(ConstructionScenario scenario) {
        BridgeKind kind = bridgeKind(scenario);
        return kind == BridgeKind.JUMP || kind == BridgeKind.DROP;
    }

    record IntakeWindow(BlockPos feederCell, BlockPos feederApproach, boolean transientAnchor) {
        IntakeWindow(BlockPos feederCell) {
            this(feederCell, feederCell, false);
        }

        IntakeWindow(BlockPos feederCell, boolean transientAnchor) {
            this(feederCell, feederCell, transientAnchor);
        }

        IntakeWindow {
            feederCell = feederCell == null ? null : feederCell.immutable();
            feederApproach = feederApproach == null ? null : feederApproach.immutable();
        }
    }

    record SitePlan(ConstructionScenario scenario, BlockPos pivot, BlockPos anchor, BlockPos spanEnd,
                            Vec3 forward, List<BlockPos> baseCells, List<IntakeWindow> intakeWindows,
                            List<BlockPos> assemblySlots, Set<BlockPos> validationCells,
                            List<BlockPos> hazardCells, CanonicalFeetCorridor corridor) {
        SitePlan(ConstructionScenario scenario, BlockPos pivot, BlockPos anchor, BlockPos spanEnd,
                 Vec3 forward, List<BlockPos> baseCells, List<IntakeWindow> intakeWindows,
                 List<BlockPos> assemblySlots, Set<BlockPos> validationCells,
                 List<BlockPos> hazardCells) {
            this(scenario, pivot, anchor, spanEnd, forward, baseCells, intakeWindows, assemblySlots, validationCells, hazardCells, null);
        }

        SitePlan {
            pivot = pivot == null ? null : pivot.immutable();
            anchor = anchor == null ? null : anchor.immutable();
            spanEnd = spanEnd == null ? null : spanEnd.immutable();
            baseCells = List.copyOf(baseCells);
            intakeWindows = List.copyOf(intakeWindows);
            assemblySlots = List.copyOf(assemblySlots);
            validationCells = Set.copyOf(validationCells);
            if (hazardCells == null) {
                hazardCells = List.of();
            } else {
                List<BlockPos> copy = new ArrayList<>(hazardCells.size());
                for (BlockPos cell : hazardCells) copy.add(cell == null ? null : cell.immutable());
                hazardCells = List.copyOf(copy);
            }
            corridor = corridor;
        }
    }

    private record ResolvedTraversalCorridor(BlockPos pivot, BlockPos edge, BlockPos landing,
                                             BlockPos invasionEndpoint, ConstructionScenario scenario,
                                             WallProfileScanner.TargetEvidence triggerEvidence,
                                             CanonicalFeetCorridor canonicalCorridor,
                                             double horizontalLength, double constructionLength) {
        ResolvedTraversalCorridor {
            pivot = pivot == null ? null : pivot.immutable();
            edge = edge == null ? null : edge.immutable();
            landing = landing == null ? null : landing.immutable();
            invasionEndpoint = invasionEndpoint == null ? null : invasionEndpoint.immutable();
        }
    }

    static boolean hasRequiredStaticSiteGeometry(SitePlan plan) {
        return plan != null && plan.scenario() != null && plan.scenario() != ConstructionScenario.NONE
                && plan.pivot() != null && plan.anchor() != null && plan.spanEnd() != null
                && plan.forward() != null && plan.forward().lengthSqr() >= 0.001D
                && plan.baseCells() != null && !plan.baseCells().isEmpty()
                && plan.intakeWindows() != null && !plan.intakeWindows().isEmpty()
                && plan.assemblySlots() != null && !plan.assemblySlots().isEmpty()
                && plan.validationCells() != null && plan.hazardCells() != null;
    }

    static Set<BlockPos> permanentValidationCells(SitePlan plan) {
        if (plan == null) return Set.of();
        Set<BlockPos> structural = new HashSet<>(plan.baseCells());
        for (IntakeWindow window : plan.intakeWindows()) {
            if (window != null && window.feederCell() != null) structural.add(window.feederCell());
            if (window != null && window.feederApproach() != null) structural.add(window.feederApproach());
        }
        if (!isDirectSpan(plan.scenario())) {
            structural.add(plan.anchor());
            structural.add(plan.spanEnd());
        }
        Set<BlockPos> permanent = new HashSet<>(structural);
        for (BlockPos cell : plan.validationCells()) {
            if (!plan.assemblySlots().contains(cell) || structural.contains(cell)) permanent.add(cell);
        }
        return Set.copyOf(permanent);
    }

    static Set<BlockPos> terrainSupportValidationCells(SitePlan plan) {
        if (plan == null) return Set.of();
        Set<BlockPos> validation = new HashSet<>(permanentValidationCells(plan));
        if (isDirectSpan(plan.scenario())) {
            validation.removeAll(plan.assemblySlots());
        }
        return Set.copyOf(validation);
    }

    static Set<Long> permanentFootprintKeys(SitePlan plan) {
        if (plan == null) return Set.of();
        Set<BlockPos> cells = new HashSet<>(permanentValidationCells(plan));
        cells.addAll(plan.hazardCells());
        Set<Long> keys = new HashSet<>();
        for (BlockPos cell : cells) keys.add(cell.asLong());
        return Set.copyOf(keys);
    }

    static boolean profileCacheUsable(long cachedExpiry, long requestExpiry, long now, long cachedRevision, long requestRevision,
                                      long cachedTerrainSig, long currentTerrainSig) {
        return cachedExpiry > now
                && cachedExpiry <= requestExpiry
                && cachedRevision == requestRevision
                && cachedTerrainSig == currentTerrainSig;
    }

    static List<BlockPos> constructionHazardsForPlan(List<BlockPos> evidenceHazards, BlockPos seed, BlockPos anchor) {
        List<BlockPos> rawHazards = copyHazards(evidenceHazards);
        return rawHazards.isEmpty() ? List.of() : rawHazards;
    }

    private static BlockPos evidenceLandingSurface(WallProfileScanner.TargetEvidence evidence) {
        if (evidence == null) return null;
        return evidence.landingSurface() != null ? evidence.landingSurface() : evidence.anchor();
    }

    private static BlockPos tacticalGoalForEvidence(WallProfileScanner.TargetEvidence evidence) {
        return evidence == null ? null : evidence.anchor();
    }

    private static BlockPos evidenceInvasionSurface(WallProfileScanner.TargetEvidence evidence) {
        if (evidence == null) return null;
        return evidence.invasionSurface() != null ? evidence.invasionSurface() : evidence.anchor();
    }

    static boolean hasProfileCorridorEvidence(WallProfileScanner.TargetEvidence evidence) {
        if (evidence == null || evidence.structuralCells() == null || evidence.structuralCells().isEmpty()) return false;
        if (evidence.edgeCell() == null || evidence.sourceApproachSurface() == null) return false;
        if (evidence.landingSurface() == null || evidence.invasionSurface() == null) return false;
        return evidence.wallFace() != null && evidence.supportCell() != null;
    }

    static boolean committedCorridorMatchesEvidence(SitePlan plan, WallProfileScanner.TargetEvidence evidence) {
        if (plan == null || evidence == null || !hasProfileCorridorEvidence(evidence)) return false;
        if (plan.corridor() != null && !plan.corridor().matchesEvidence(evidence)) return false;
        if (!Objects.equals(plan.anchor(), tacticalGoalForEvidence(evidence))) return false;
        if (!Objects.equals(plan.spanEnd(), evidenceInvasionSurface(evidence))) return false;
        ConstructionScenario scenario = parseConstructionScenario(evidence.scenario());
        return scenario != null && scenario == plan.scenario();
    }

    static boolean committedCorridorMatchesEvidence(ResolvedTraversalCorridor corridor,
                                                    WallProfileScanner.TargetEvidence evidence) {
        if (corridor == null || evidence == null || !hasProfileCorridorEvidence(evidence)) return false;
        if (corridor.canonicalCorridor() != null && !corridor.canonicalCorridor().matchesEvidence(evidence)) return false;
        if (!Objects.equals(corridor.edge(), evidence.edgeCell())) return false;
        if (!Objects.equals(corridor.landing(), evidenceLandingSurface(evidence))) return false;
        if (!Objects.equals(corridor.invasionEndpoint(), evidenceInvasionSurface(evidence))) return false;
        ConstructionScenario scenario = parseConstructionScenario(evidence.scenario());
        return scenario != null && scenario == corridor.scenario();
    }

    static String formationIdentity(ConstructionRequest request, SitePlan plan) {
        long revision = request == null ? -1L : request.revision();
        long requestSpan = request == null || request.evidence() == null ? -1L : request.evidence().anchor().asLong();
        long requestTarget = request == null ? -1L : request.threatDestination().asLong();
        long spanEnd = plan == null || plan.spanEnd() == null ? -1L : plan.spanEnd().asLong();
        int feederCount = plan == null ? -1 : plan.intakeWindows().size();
        int assemblyCount = plan == null ? -1 : plan.assemblySlots().size();
        return revision + ":" + requestSpan + ":" + (plan == null ? -1L : plan.pivot().asLong())
                + ":" + requestTarget + ":" + (plan == null ? -1L : plan.anchor().asLong()) + ":" + spanEnd
                + ":" + (plan == null ? "none" : plan.scenario().name())
                + ":" + feederCount + ":" + assemblyCount
                + ":" + (request == null || request.evidence() == null ? "none" : request.evidence().scenario());
    }

    private record ScoredSitePlan(SitePlan plan, double score) {
    }

    private record AssemblyAssignment(BlockPos slot, BlockPos approach) {
    }

    enum Phase {
        SCAN,
        ASSEMBLE,
        SETTLE,
        ROTATE,
        ANCHOR,
        TRAVERSE,
        COLLAPSE
    }

    private enum CrosserTransitState {
        APPROACH_ENTRY,
        CAPTURE_ENTRY,
        TRAVERSE,
        DISMOUNT,
        FALLING,
        LANDING_VALIDATION,
        HANDED_OFF,
        ABORTED
    }

    private static final class Formation {
        final UUID id;
        final UUID plannerId;
        final WeakReference<Mob> leaderRef;
        final ConstructionRequest request;
        final DropLimitSnapshot dropLimits;
        CommittedBridgeExecution committedExecution;
        boolean rosterFrozen;
        final RouteEvidence routeEvidence;
        final BlockPos target;
        final BlockPos pivot;
        final BlockPos anchor;
        final BlockPos spanEnd;
        final List<Vec3> elbowPoints = new ArrayList<>();
        final SitePlan plan;
        ConstructionIntent intentToken;
        ConstructionSiteKey committedSiteKey;
        final List<UUID> members = new ArrayList<>();
        final Set<UUID> localRoster = new HashSet<>();
        final Map<UUID, MobTicket> mobTickets = new HashMap<>();
        final Set<UUID> reserved = new HashSet<>();
        final Set<UUID> structuralBuilderIds = new HashSet<>();

        final Set<UUID> committedFoundationIds = new HashSet<>();
        final Set<UUID> builderReservations = new HashSet<>();
        final Set<UUID> transitCrosserIds = new HashSet<>();
        final List<UUID> pendingBuilderIds = new ArrayList<>();
        final Map<UUID, Boolean> ticketGoals = new HashMap<>();
        final Map<UUID, Boolean> candidateHoldingGoals = new HashMap<>();
        final Map<UUID, BlockPos> candidateHoldingCells = new HashMap<>();
        final Map<UUID, IntakeWindow> assemblyIntakeProvenance = new HashMap<>();
        final Map<UUID, BlockPos> successfulPlacementProvenance = new HashMap<>();
        final Map<UUID, BlockPos> assemblySlotAssignments = new HashMap<>();
        final Map<BlockPos, UUID> assemblySlotOwners = new HashMap<>();
        final Map<Integer, Vec3> structuralSlotPositions = new HashMap<>();
        final Map<UUID, BlockPos> assemblyLaneAssignments = new HashMap<>();
        final Map<UUID, SafeBaseNavigationReservation> safeBaseNavigation = new HashMap<>();
        final Map<UUID, Integer> safeBaseRouteProgress = new HashMap<>();
        final Set<UUID> safeBaseRouteIssued = new HashSet<>();
        final Map<UUID, StructuralApproachReservation> structuralApproachReservations = new HashMap<>();
        final Set<UUID> pendingSafeBaseReplans = new HashSet<>();
        final Set<UUID> terminalSafeBaseFailures = new HashSet<>();
        final Map<UUID, Set<BlockPos>> rejectedSafeBaseStaging = new HashMap<>();
        final Map<UUID, Long> lastReportedSafeBaseStagingDiagnostics = new HashMap<>();
        final Map<UUID, List<BlockPos>> rankedAssemblySlots = new HashMap<>();
        final Map<BlockPos, List<BlockPos>> slotApproachCache = new HashMap<>();
        final Map<UUID, CandidateHoldingSearch> candidateHoldingSearches = new HashMap<>();
        final Set<Long> footprint = new HashSet<>();
        final Set<UUID> transferredWorkerIds = new HashSet<>();
        long terrainSignature;
        TraversalCorridorWitness traversalWitness;
        final Set<UUID> recruitmentArrivalReports = new HashSet<>();
        final Map<PathSurveyKey, PathSurvey> pathSurveys = new HashMap<>();
        final Map<RecruitmentRouteKey, RecruitmentRouteSurvey> recruitmentRoutes = new HashMap<>();
        final Map<UUID, Long> recruitmentBlacklist = new HashMap<>();
        final Map<RouteProbeOwner, RouteProbeReservation> routeProbeLedger = new EnumMap<>(RouteProbeOwner.class);
        final Map<UUID, Boolean> originalGravity = new HashMap<>();
        final Map<UUID, Boolean> originalPersistence = new HashMap<>();
        final Map<UUID, Boolean> originalNoPhysics = new HashMap<>();
        final Map<UUID, UUID> originalTargets = new HashMap<>();
        final Map<UUID, Vec3> assemblyHoldPositions = new HashMap<>();
        int assemblyRejectionDiagnostics;
        int assemblyLockDiagnostics;
        int movementDiagnostics;
        final Set<UUID> claimedWorkerIds = new HashSet<>();
        UUID activeIntakeWorkerId;
        long lastAssemblyReleaseTick = Long.MIN_VALUE;
        long lastMemberRefreshTick = Long.MIN_VALUE;
        Phase lastMemberRefreshPhase;
        long assignmentGeneration;
        long ticketGeneration;
        long assignmentSnapshotTick = -1L;
        CompletableFuture<LivingBridgeAssignmentPlanner.Result> assignmentFuture;
        final Map<UUID, Integer> crosserSlots = new HashMap<>();
        final Map<UUID, CrosserTransit> crosserTransit = new HashMap<>();
        final Set<UUID> completedCrossers = new HashSet<>();
        final Map<UUID, Long> crosserCompletionTicks = new HashMap<>();
        final List<UUID> crosserOrder = new ArrayList<>();
        TiltExitProjection cachedTiltExit;
        TiltExitFingerprint cachedTiltExitFingerprint;
        long cachedTiltExitValidationTick = Long.MIN_VALUE;
        final long createdTick;
        final int wallHeight;
        final int gapWidth;
        final int bodyLimit;
        final int availableBodies;
        final int usableBodies;
        final int oneColumnBodies;
        int layerCount;
        double chainLength;
        final double targetAngle;
        final double minimumWidth;
        final double minimumHeight;
        final double horizontalTouchStep;
        final double verticalTouchStep;
        final double pillarStep;
        final boolean directSpan;
        int requiredBodies;
        int columnCount;
        double tiltAngle;
        double pendingTiltAngle;
        Phase phase = Phase.SCAN;
        Phase lastLoggedPhase = Phase.SCAN;
        long phaseStartTick;
        long lastValidationTick;
        long lastRecruitmentProgressTick;
        long lastRosterRefreshTick = -1L;
        long lastDiagnosticTick = -1L;
        long lastHoldingDiagnosticTick = -1L;
        long lastHoldingSearchSignature = Long.MIN_VALUE;
        long lastLaneGuardTick = -1L;
        int activeCrossers;
        int assignedCount;
        int routeProbeReservations;
        int pathSuccessCount;
        int controllerStartedCount;
        int navigationSubmittedCount;
        int navigationAcceptedCount;
        int arrivalCount;
        int arbitrationConflictCount;
        int combatPreemptionCount;
        int releasedCount;
        int crosserCursor;
        int assembledSlots;
        int tiltLayerCursor = -1;
        int shiftTick;
        int shiftDuration;
        long lastAssemblyTick = -1L;
        long lastAssemblyFailureLogTick = -1L;
        long lastAssemblyProgressTick = -1L;
        long lastAssemblyProgressLogTick = -1L;
        int motionDiagnosticAttempts;
        boolean motionDiagnosticCapLogged;
        LivingBridgeMotionDiagnostics.Attempt motionDiagnosticAttempt;
        long assemblyCompleteTick = -1L;
        long lastAnchoredRepinTick = -1L;
        long activeStructuralProgressTick = -1L;
        long lastTiltProgressTick;
        long lastTiltDiagnosticTick = -1L;
        long lastCrosserRecruitTick = -1L;
        long lastTraverseActivityTick = -1L;
        int anchorTicks;
        int recoveryTicks;
        int replacementAttempts;
        int activeStructuralSlot = -1;
        UUID activeStructuralWorkerId;
        int activeStructuralRouteRetries;
        double integrity = 1.0D;
        double activeStructuralBestDistanceSqr = Double.MAX_VALUE;
        boolean collapsed;
        String collapseReason;
        String collapseDetail;
        boolean assemblyFatal;
        boolean plannerUnavailableLogged;
        Vec3 sourceStagingOffset = Vec3.ZERO;
        StructuralRoute activeStructuralRoute;
        boolean activeMemberSuctionEngaged;
        ConstructionObservation observation;

        Formation(Mob leader, BlockPos target, SitePlan plan, ConstructionRequest request, int wallHeight, int gapWidth,
                  int availableBodies, BodyGeometry geometry, long now) {
            this.request = request;
            this.dropLimits = captureDropLimitSnapshot(now);
            this.rosterFrozen = false;
            this.observation = request == null ? null : request.observation();
            this.routeEvidence = request == null ? null : request.routeEvidence();
            this.id = UUID.nameUUIDFromBytes(formationIdentity(request, plan).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            this.plannerId = leader.getUUID();
            this.leaderRef = new WeakReference<>(leader);
            this.target = target;
            this.plan = plan;
            this.pivot = plan.pivot();
            this.anchor = plan.anchor();
            this.spanEnd = plan.spanEnd();
            this.committedSiteKey = null;
            this.terrainSignature = -1L;
            this.directSpan = isDirectSpan(plan.scenario());
            this.createdTick = now;
            this.wallHeight = wallHeight;
            this.gapWidth = gapWidth;
            this.bodyLimit = Math.max(3, LivingWallConfig.MAX_BODIES.get());
            this.availableBodies = Math.max(0, availableBodies);
            this.usableBodies = Math.min(this.bodyLimit, this.availableBodies);
            this.minimumWidth = geometry.width();
            this.minimumHeight = geometry.height();
            this.horizontalTouchStep = lateralSpacingForBodyWidth(this.minimumWidth);
            this.verticalTouchStep = requiresTilt(plan.scenario())
                    ? layerSpacingForBodyHeight(this.minimumHeight)
                    : Math.max(0.05D, this.minimumHeight + STRUCTURAL_NON_OVERLAP_MARGIN);
            BlockPos structuralEnd = tiltStructuralEnd(plan);
            double horizontal = Math.sqrt(Math.pow(structuralEnd.getX() - pivot.getX(), 2)
                    + Math.pow(structuralEnd.getZ() - pivot.getZ(), 2));
            double vertical = Math.abs(structuralEnd.getY() - pivot.getY());
            int plannedStations = requiresTilt(plan.scenario())
                    ? requiredLayerCountForTilt(plan, this.horizontalTouchStep, this.verticalTouchStep)
                    : Math.max(2, requiredStationsForRoute(plan, this.horizontalTouchStep, this.verticalTouchStep));
            this.oneColumnBodies = plannedStations;

            this.columnCount = (plan.scenario() == ConstructionScenario.WALL_LEDGE || plan.scenario() == ConstructionScenario.DROP_TO_LEDGE)
                    ? chooseColumnCount(Integer.MAX_VALUE, this.oneColumnBodies, this.usableBodies)
                    : this.minimumWidth + CONTACT_EPSILON >= MIN_COLUMN_WIDTH
                    ? chooseColumnCount(this.oneColumnBodies * FORMATION_COLUMNS, this.oneColumnBodies, this.usableBodies)
                    : chooseColumnCount(Integer.MAX_VALUE, this.oneColumnBodies, this.usableBodies);
            this.requiredBodies = this.columnCount == 0 ? this.bodyLimit + 1 : this.oneColumnBodies * this.columnCount;
            this.layerCount = this.columnCount == 0 ? 0 : this.oneColumnBodies;
            this.chainLength = requiresTilt(plan.scenario()) ? tiltRouteLength(plan) : bridgeLength(pivot, spanEnd);
            this.pillarStep = requiresTilt(plan.scenario())
                    ? initialPillarStep(this.chainLength, this.layerCount, this.verticalTouchStep)
                    : initialPillarStepForHeight(anchor.getY() - pivot.getY(), this.layerCount, this.verticalTouchStep);
            this.targetAngle = this.chainLength < 0.001D ? 0.0D : Math.atan2(horizontal,
                    structuralEnd.getY() - pivot.getY());
            this.phaseStartTick = now;
            this.lastValidationTick = now;
            this.lastRecruitmentProgressTick = now;
            this.lastTiltProgressTick = now;
        }

        Formation(Mob leader, BlockPos target, SitePlan plan, ConstructionRequest request,
                  ConstructionObservation observation, int wallHeight, int gapWidth,
                  int availableBodies, BodyGeometry geometry, long now) {
            this(leader, target, plan, request, wallHeight, gapWidth, availableBodies, geometry, now);
            this.observation = observation;
        }
    }

    private record CrosserWaypoint(Vec3 position, int supportSlot, double supportTopY, boolean valid, String reason) {
    }

    private enum StructuralRoutePhase {
        BASE_APPROACH,
        VERTICAL_LIFT,
        DOCK
    }

    private static final class StructuralRoute {
        final List<Vec3> waypoints;
        int waypointIndex;
        final Vec3 baseApproach;
        final Vec3 verticalLift;
        final Vec3 dock;
        StructuralRoutePhase phase;

        StructuralRoute(List<Vec3> waypoints) {
            this.waypoints = waypoints == null || waypoints.isEmpty() ? List.of(Vec3.ZERO) : List.copyOf(waypoints);
            this.waypointIndex = 0;
            this.baseApproach = null;
            this.verticalLift = null;
            this.dock = this.waypoints.get(this.waypoints.size() - 1);
            this.phase = null;
        }

        StructuralRoute(Vec3 baseApproach, Vec3 verticalLift, Vec3 dock) {
            this(baseApproach, verticalLift, dock, null);
        }

        StructuralRoute(Vec3 baseApproach, Vec3 verticalLift, Vec3 dock, Vec3 currentPos) {
            this.waypoints = null;
            this.waypointIndex = -1;
            this.baseApproach = baseApproach;
            this.verticalLift = verticalLift;
            this.dock = dock;
            if (currentPos != null && baseApproach != null && currentPos.y > baseApproach.y + 0.8D) {
                this.phase = (verticalLift != null && currentPos.y >= verticalLift.y - 0.25D)
                        ? StructuralRoutePhase.DOCK : StructuralRoutePhase.VERTICAL_LIFT;
            } else {
                this.phase = StructuralRoutePhase.BASE_APPROACH;
            }
        }

        Vec3 waypoint() {
            if (waypoints != null) {
                return waypoints.get(Math.min(waypointIndex, waypoints.size() - 1));
            }
            return switch (phase) {
                case BASE_APPROACH -> baseApproach;
                case VERTICAL_LIFT -> verticalLift;
                case DOCK -> dock;
            };
        }

        boolean isFinalWaypoint() {
            if (waypoints != null) {
                return waypointIndex >= waypoints.size() - 1;
            }
            return phase == StructuralRoutePhase.DOCK;
        }

        boolean advance() {
            if (waypoints != null) {
                if (waypointIndex < waypoints.size() - 1) {
                    waypointIndex++;
                    return true;
                }
                return false;
            }
            return switch (phase) {
                case BASE_APPROACH -> {
                    phase = StructuralRoutePhase.VERTICAL_LIFT;
                    yield true;
                }
                case VERTICAL_LIFT -> {
                    phase = StructuralRoutePhase.DOCK;
                    yield true;
                }
                case DOCK -> false;
            };
        }
    }

    private static final class CrosserTransit {
        final UUID mobId;
        final IntakeWindow intakeWindow;
        CrosserTransitState state = CrosserTransitState.APPROACH_ENTRY;
        int slot;
        int captureTicks;
        int intakeWindowFailures;
        int intakeHazardFailures;
        long captureRetryUntil = Long.MIN_VALUE;
        long stateTick = Long.MIN_VALUE;
        int landingWatchdogTicks;
        int stuckTicks;
        BlockPos handoffGoal;

        CrosserTransit(UUID mobId, IntakeWindow intakeWindow) {
            this.mobId = mobId;
            this.intakeWindow = intakeWindow;
        }
    }

    private record ExitCellFingerprint(BlockPos pos, boolean loaded, String blockId, boolean solid, boolean fluid,
                                       boolean hazard, boolean portal, boolean stableSupport, boolean clearSpace) {
    }

    private record TiltExitFingerprint(Identifier dimension, Phase phase, long requestRevision, BlockPos threat,
                                       BlockPos pivot, BlockPos anchor, BlockPos spanEnd, int exitSlot,
                                       int assembledSlots, int memberHash, boolean contactValid,
                                       List<ExitCellFingerprint> cells) {
    }

    private record TiltExitProjection(int slot, Vec3 bridgeWaypoint, Vec3 dismountWaypoint,
                                      BlockPos landing, BlockPos support, BlockPos handoffGoal,
                                      TiltExitFingerprint fingerprint) {
    }

    private record HoldingCellCacheKey(Identifier dimension, BlockPos pivot, BlockPos target,
                                       long terrainSignature, long revision) {
    }

    private record HoldingCellCacheEntry(BlockPos cell, long expiresAt) {
    }

    private static final class CandidateHoldingSearch {
        final List<BlockPos> cells;
        int cursor;
        BlockPos lastCell;
        long lastTerrainSignature;
        long lastRosterSignature;
        long lastRevision;
        long lastUpdatedTick;

        CandidateHoldingSearch(List<BlockPos> cells, long terrainSignature, long rosterSignature, long revision, long now) {
            this.cells = cells == null ? new ArrayList<>() : new ArrayList<>(cells);
            this.cursor = 0;
            this.lastTerrainSignature = terrainSignature;
            this.lastRosterSignature = rosterSignature;
            this.lastRevision = revision;
            this.lastUpdatedTick = now;
        }
    }

    enum MobTicketState { WAITING, MOVING_TO_ASSEMBLY, STAGING_AT_ASSEMBLY, ASSEMBLED, MOVING_TO_FEEDER, SUCTIONING, BUILDING, RELEASED }

    static boolean shouldReleaseAfterRecruitmentFailure(int consecutiveHardFailures, boolean noProgress) {
        return noProgress && consecutiveHardFailures >= RECRUITER_MAX_PATH_FAILURES;
    }

    static boolean shouldReleaseAfterStalledRecruitment(int retries) {
        return retries >= RECRUITER_RETRY_WINDOWS;
    }

    static boolean isValidAssemblyApproach(BlockPos approach, BlockPos slot) {
        return approach != null && slot != null
                && isWithinAssemblyAcceptanceEnvelope(Vec3.atBottomCenterOf(approach), slot);
    }

    enum WorkerLeaseState {
        IDLE, HOLDING, MOVING_TO_ASSEMBLY, ASSEMBLED, MOVING_TO_FEEDER, SUCTIONING, BUILDING, RELEASED, REASSIGNING
    }

    static boolean canTransitionWorkerLeaseState(WorkerLeaseState from, WorkerLeaseState to) {
        if (from == null || to == null) return false;
        return switch (from) {
            case IDLE -> to == WorkerLeaseState.HOLDING || to == WorkerLeaseState.MOVING_TO_ASSEMBLY;
            case HOLDING -> to == WorkerLeaseState.MOVING_TO_ASSEMBLY
                    || to == WorkerLeaseState.REASSIGNING || to == WorkerLeaseState.RELEASED;
            case MOVING_TO_ASSEMBLY -> to == WorkerLeaseState.ASSEMBLED
                    || to == WorkerLeaseState.REASSIGNING || to == WorkerLeaseState.RELEASED;
            case ASSEMBLED -> to == WorkerLeaseState.MOVING_TO_FEEDER
                    || to == WorkerLeaseState.REASSIGNING || to == WorkerLeaseState.RELEASED;
            case MOVING_TO_FEEDER -> to == WorkerLeaseState.SUCTIONING
                    || to == WorkerLeaseState.REASSIGNING || to == WorkerLeaseState.RELEASED;
            case SUCTIONING -> to == WorkerLeaseState.MOVING_TO_FEEDER
                    || to == WorkerLeaseState.BUILDING || to == WorkerLeaseState.REASSIGNING
                    || to == WorkerLeaseState.RELEASED;
            case BUILDING, REASSIGNING -> to == WorkerLeaseState.RELEASED;
            case RELEASED -> false;
        };
    }

    static final class WorkerLease {
        final UUID formationId;
        final UUID mobId;
        final long generation;
        WorkerLeaseState state;
        final BlockPos assemblySlot;
        BlockPos approachCell;
        BlockPos holdingCell;
        IntakeWindow intakeWindow;
        long grantTick;
        long lastProgressTick;
        long navigationSubmissionTick = Long.MIN_VALUE;
        long controllerStartTick = Long.MIN_VALUE;
        long progressDeadline;
        double routeLengthBlocks;
        double bestRemainingDistance = Double.MAX_VALUE;
        String lastFailureCode;
        long arbitrationConflictTick = Long.MIN_VALUE;
        long combatPreemptionTick = Long.MIN_VALUE;

        WorkerLease(UUID formationId, UUID mobId, long generation, WorkerLeaseState state,
                    BlockPos assemblySlot, long grantTick, long progressDeadline) {
            this.formationId = formationId;
            this.mobId = mobId;
            this.generation = generation;
            this.state = state;
            this.assemblySlot = assemblySlot;
            this.grantTick = grantTick;
            this.lastProgressTick = grantTick;
            this.progressDeadline = progressDeadline;
        }
    }

    static final class MobTicket {
        final UUID mobId;
        MobTicketState state = MobTicketState.WAITING;
        IntakeWindow intakeWindow;
        final BlockPos assemblySlot;
        BlockPos assemblyApproach;
        long lastPathTick = Long.MIN_VALUE;
        long observationTick = Long.MIN_VALUE;
        double bestDistanceSqr = Double.MAX_VALUE;
        int retries;
        long releaseUntil;
        boolean goalStarted;
        boolean navigationAccepted;
        boolean navigationReported;
        boolean continuationReported;
        long lastPathSkipLogTick = Long.MIN_VALUE;
        String lastPathSkipReason;
        boolean targetNullTickReported;
        long lastGoalTickLogTick = Long.MIN_VALUE;
        long lastPathFailureTick = Long.MIN_VALUE;
        long grantTick;
        long navigationSubmissionTick = Long.MIN_VALUE;
        long controllerStartTick = Long.MIN_VALUE;
        long progressDeadline = Long.MIN_VALUE;
        double routeLengthBlocks;
        String lastFailureCode;
        long lastIntakeRejectLogTick = Long.MIN_VALUE;
        String lastIntakeRejectReason;
        int pathFailures;
        long generation;
        boolean controlledPull;

        boolean captureCompleted;
        int captureTicks;
        int captureAttempts;
        int intakeWindowFailures;
        int intakeHazardFailures;
        long captureRetryUntil = Long.MIN_VALUE;
        long lastIntakeReplanTick = Long.MIN_VALUE;
        int assemblyStageTicks;
        int assemblyRecoveryAttempts;
        long assemblyStageStartedTick = Long.MIN_VALUE;
        long assemblyLastProgressTick = Long.MIN_VALUE;
        double assemblyBestDistanceSqr = Double.MAX_VALUE;
        MobTicket(UUID mobId, BlockPos assemblySlot) {
            this(mobId, assemblySlot, 0L);
        }
        MobTicket(UUID mobId, BlockPos assemblySlot, long generation) {
            this.mobId = mobId;
            this.assemblySlot = assemblySlot;
            this.generation = generation;
            transitionTicket(this, MobTicketState.MOVING_TO_ASSEMBLY);
        }
    }

    static boolean canTransitionTicketState(MobTicketState from, MobTicketState to) {
        if (from == null || to == null) return false;
        return switch (from) {
            case WAITING -> to == MobTicketState.MOVING_TO_ASSEMBLY || to == MobTicketState.RELEASED;
            case MOVING_TO_ASSEMBLY -> to == MobTicketState.STAGING_AT_ASSEMBLY || to == MobTicketState.RELEASED;
            case STAGING_AT_ASSEMBLY -> to == MobTicketState.ASSEMBLED
                    || to == MobTicketState.MOVING_TO_ASSEMBLY || to == MobTicketState.RELEASED;
            case ASSEMBLED -> to == MobTicketState.STAGING_AT_ASSEMBLY
                    || to == MobTicketState.MOVING_TO_FEEDER || to == MobTicketState.RELEASED;
            case MOVING_TO_FEEDER -> to == MobTicketState.SUCTIONING || to == MobTicketState.RELEASED;
            case SUCTIONING -> to == MobTicketState.MOVING_TO_FEEDER || to == MobTicketState.BUILDING
                    || to == MobTicketState.RELEASED;
            case BUILDING -> to == MobTicketState.RELEASED;
            case RELEASED -> false;
        };
    }

    static boolean retainsControlledSuctionOwnership(MobTicketState state) {
        return state == MobTicketState.SUCTIONING;
    }

    static boolean isActiveFreezeTicketState(MobTicketState state) {
        return state == MobTicketState.MOVING_TO_ASSEMBLY
                || state == MobTicketState.STAGING_AT_ASSEMBLY
                || state == MobTicketState.ASSEMBLED
                || state == MobTicketState.MOVING_TO_FEEDER
                || state == MobTicketState.SUCTIONING
                || state == MobTicketState.BUILDING;
    }

    private static boolean transitionTicket(MobTicket ticket, MobTicketState next) {
        if (ticket == null || !canTransitionTicketState(ticket.state, next)) return false;
        ticket.state = next;
        resetRecruitmentBaseline(ticket);
        return true;
    }

    static void resetRecruitmentBaseline(MobTicket ticket) {
        if (ticket == null) return;
        ticket.retries = 0;
        ticket.observationTick = Long.MIN_VALUE;
        ticket.bestDistanceSqr = Double.MAX_VALUE;
        ticket.pathFailures = 0;
        ticket.lastPathFailureTick = Long.MIN_VALUE;
        ticket.lastPathTick = Long.MIN_VALUE;
        ticket.navigationAccepted = false;
        ticket.navigationReported = false;
        ticket.progressDeadline = Long.MIN_VALUE;
        ticket.assemblyStageTicks = 0;
        ticket.assemblyStageStartedTick = Long.MIN_VALUE;
        ticket.assemblyLastProgressTick = Long.MIN_VALUE;
        ticket.assemblyBestDistanceSqr = Double.MAX_VALUE;
    }

    static void recordAcceptedRecruitmentProgress(MobTicket ticket, double remainingDistanceSqr, long now) {
        if (ticket == null || ticket.state == MobTicketState.RELEASED) return;
        ticket.pathFailures = 0;
        ticket.retries = 0;
        ticket.observationTick = now;
        ticket.bestDistanceSqr = remainingDistanceSqr;
        ticket.lastPathFailureTick = Long.MIN_VALUE;
    }

    private static PerLevelState state(ServerLevel level) {
        return LEVELS.computeIfAbsent(level.dimension().identifier(), key -> new PerLevelState());
    }

    public static boolean isControlled(Mob mob) {
        if (mob == null) {
            return false;
        }
        Formation formation = getFormation(mob);
        return isBridgeWorkerClaimed(formation, mob.getUUID());
    }

    private static boolean isBridgeWorkerClaimed(Formation formation, UUID mobId) {
        if (formation == null || formation.collapsed || mobId == null) return false;
        if (formation.claimedWorkerIds.contains(mobId)
                || formation.builderReservations.contains(mobId)
                || formation.pendingBuilderIds.contains(mobId)
                || formation.transitCrosserIds.contains(mobId)
                || formation.reserved.contains(mobId)
                || formation.members.contains(mobId)
                || formation.structuralBuilderIds.contains(mobId)
                || formation.candidateHoldingGoals.containsKey(mobId)
                || formation.ticketGoals.containsKey(mobId)) {
            return true;
        }
        MobTicket ticket = formation.mobTickets.get(mobId);
        return ticket != null && ticket.state != MobTicketState.RELEASED;
    }

    private static boolean isBridgeWorkerClaimedWithoutPassiveSet(Formation formation, UUID mobId) {
        if (formation == null || formation.collapsed || mobId == null) return false;
        if (formation.builderReservations.contains(mobId)
                || formation.pendingBuilderIds.contains(mobId)
                || formation.transitCrosserIds.contains(mobId)
                || formation.reserved.contains(mobId)
                || formation.members.contains(mobId)
                || formation.structuralBuilderIds.contains(mobId)
                || formation.candidateHoldingGoals.containsKey(mobId)
                || formation.ticketGoals.containsKey(mobId)) {
            return true;
        }
        MobTicket ticket = formation.mobTickets.get(mobId);
        return ticket != null && ticket.state != MobTicketState.RELEASED;
    }

    public static long bridgeProgressWindowTicks(double routeLengthBlocks, int waveSize) {
        if (!Double.isFinite(routeLengthBlocks) || routeLengthBlocks < 0.0D) routeLengthBlocks = 0.0D;
        int size = Math.max(1, waveSize);
        long raw = 40L + (long) Math.ceil((routeLengthBlocks / 0.75D) * 2.0D)
                + 10L * Math.max(0, size - 1);
        return Math.max(80L, Math.min(600L, raw));
    }

    static boolean bridgeControllerMissedStartup(long now, long grantTick, long startTick) {
        return startTick == Long.MIN_VALUE && now - grantTick >= BRIDGE_STARTUP_GRACE_TICKS;
    }

    public static void ensureBridgeWorkGoal(Mob mob) {
        if (mob == null || mob.goalSelector == null || mob.level().isClientSide()) return;
        BridgeWorkGoal found = null;
        for (var wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof BridgeWorkGoal controller) {
                if (found == null) found = controller;
                else mob.goalSelector.removeGoal(controller);
            }
        }
        if (found == null) mob.goalSelector.addGoal(BridgeWorkGoal.PRIORITY, new BridgeWorkGoal(mob));
    }

    static boolean hasValidCombatPreemption(Mob mob) {
        return shouldYieldBridgeCombat(mob);
    }

    static Vec3 getBridgeWorkTarget(Mob mob) {
        Vec3 evacuation = getBasinEvacuationTarget(mob);
        if (evacuation != null) return evacuation;
        Vec3 crosser = getCrosserTarget(mob);
        if (crosser != null) return crosser;
        Vec3 recruitment = getRecruitmentTarget(mob);
        return recruitment != null ? recruitment : getCandidateHoldingTarget(mob);
    }

    static Vec3 getCrosserApproachTarget(Mob mob) {
        return getCrosserTarget(mob);
    }

    static Vec3 getCrosserTarget(Mob mob) {
        if (mob == null || !(mob.level() instanceof ServerLevel level)) return null;
        PerLevelState state = LEVELS.get(level.dimension().identifier());
        if (state == null) return null;
        UUID formationId = state.mobOwners.get(mob.getUUID());
        if (formationId == null) return null;
        Formation formation = state.formations.get(formationId);
        if (formation == null) return null;
        CrosserTransit transit = formation.crosserTransit.get(mob.getUUID());
        if (transit == null) return null;
        switch (transit.state) {
            case APPROACH_ENTRY -> {
                if (formation.plan != null && requiresTilt(formation.plan.scenario())) {
                    return slotPosition(formation, 0);
                }
                IntakeWindow window = transit.intakeWindow;
                if (window == null) return null;
                BlockPos routeTarget = window.feederApproach() != null && isIntakeRouteTarget(window, window.feederApproach())
                        ? window.feederApproach() : window.feederCell();
                if (routeTarget == null) return null;
                if (routeTarget.equals(window.feederApproach()) && mob.blockPosition().closerThan(window.feederApproach(), 2.0D)) {
                    routeTarget = window.feederCell();
                }
                return Vec3.atCenterOf(routeTarget);
            }
            case CAPTURE_ENTRY -> {
                CrosserWaypoint waypoint = walkwayWaypoint(level, formation, mob, 0);
                if (waypoint.valid() && waypoint.position() != null) return waypoint.position();
                Vec3 station = slotPosition(formation, 0);
                return new Vec3(station.x, station.y + 1.85D, station.z);
            }
            case TRAVERSE -> {
                CrosserWaypoint waypoint = walkwayWaypoint(level, formation, mob, transit.slot);
                if (waypoint.valid() && waypoint.position() != null) return waypoint.position();
                Vec3 station = slotPosition(formation, transit.slot);
                return new Vec3(station.x, station.y + 1.85D, station.z);
            }
            case DISMOUNT -> {
                if (transit.handoffGoal != null) return Vec3.atCenterOf(transit.handoffGoal);
                CrosserWaypoint waypoint = walkwayWaypoint(level, formation, mob, transit.slot);
                return waypoint.valid() && waypoint.position() != null ? waypoint.position() : mob.position();
            }
            default -> {
                return null;
            }
        }
    }

    static Vec3 getBasinEvacuationTarget(Mob mob) {
        if (mob == null || !(mob.level() instanceof ServerLevel level)) return null;
        PerLevelState state = LEVELS.get(level.dimension().identifier());
        if (state == null) return null;
        BasinEvacuation evacuation = state.basinEvacuations.get(mob.getUUID());
        if (evacuation == null) return null;
        long now = level.getGameTime();

        if (now > evacuation.expiresTick()) {
            state.basinEvacuations.remove(mob.getUUID());
            return null;
        }

        if (evacuation.threatPos() != null
                && mob.position().closerThan(evacuation.target(), 4.0D)
                && level.canSeeSky(mob.blockPosition().above())) {
            Vec3 current = mob.position();
            Vec3 toward = Vec3.atBottomCenterOf(evacuation.threatPos()).subtract(current).normalize();
            Vec3 relay = current.add(toward.scale(10.0D));

            int relayX = Mth.floor(relay.x);
            int relayZ = Mth.floor(relay.z);
            if (level.hasChunkAt(new BlockPos(relayX, 64, relayZ))) {
                int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, relayX, relayZ);
                BlockPos relayPos = new BlockPos(relayX, surfaceY, relayZ);
                BlockState relayState = level.getBlockState(relayPos);
                if (relayState.getCollisionShape(level, relayPos).isEmpty()
                        && !com.example.soundattract.pathfinding.HazardClassifier.isDangerous(level, mob, relay)
                        && level.canSeeSky(relayPos)) {
                    Vec3 relayVec = Vec3.atBottomCenterOf(relayPos);
                    state.basinEvacuations.put(mob.getUUID(),
                            new BasinEvacuation(relayVec, evacuation.threatPos(), evacuation.expiresTick()));
                    return relayVec;
                }
            }
        }
        return evacuation.target();
    }

    static PerLevelState getOrCreateStateForTest(ServerLevel level) {
        if (level == null) return null;
        return LEVELS.computeIfAbsent(level.dimension().identifier(), k -> new PerLevelState());
    }

    static void setBasinEvacuationForTest(ServerLevel level, UUID mobUuid, Vec3 target, long expiresTick) {
        if (level == null || mobUuid == null) return;
        PerLevelState state = getOrCreateStateForTest(level);
        state.basinEvacuations.put(mobUuid, new BasinEvacuation(target, null, expiresTick));
    }

    static BasinEvacuation getBasinEvacuationForTest(ServerLevel level, UUID mobUuid) {
        if (level == null || mobUuid == null) return null;
        PerLevelState state = LEVELS.get(level.dimension().identifier());
        return state == null ? null : state.basinEvacuations.get(mobUuid);
    }

    static BasinFailureHistory getBasinFailureForTest(ServerLevel level, BlockPos bucketKey) {
        if (level == null || bucketKey == null) return null;
        PerLevelState state = LEVELS.get(level.dimension().identifier());
        return state == null ? null : state.basinFailures.get(bucketKey);
    }

    static void setBasinFailureForTest(ServerLevel level, BlockPos bucketKey, int failureCount, long lastFailureTick, long cooldownUntil) {
        if (level == null || bucketKey == null) return;
        PerLevelState state = getOrCreateStateForTest(level);
        state.basinFailures.put(bucketKey, new BasinFailureHistory(failureCount, lastFailureTick, cooldownUntil));
    }

    static void clearBasinStateForTest(ServerLevel level) {
        if (level == null) return;
        PerLevelState state = LEVELS.get(level.dimension().identifier());
        if (state != null) {
            state.basinFailures.clear();
            state.basinEvacuations.clear();
        }
    }

    static long getBridgeWorkGeneration(Mob mob) {
        Formation formation = getFormation(mob);
        if (formation == null || mob == null) return Long.MIN_VALUE;
        MobTicket ticket = formation.mobTickets.get(mob.getUUID());
        if (ticket != null) return ticket.generation;
        CrosserTransit transit = formation.crosserTransit.get(mob.getUUID());
        return transit != null ? transit.stateTick : Long.MIN_VALUE;
    }

    static boolean isHoldingBridgeWorker(Mob mob) {
        Formation formation = getFormation(mob);
        MobTicket ticket = formation == null ? null : formation.mobTickets.get(mob.getUUID());
        return formation != null && formation.candidateHoldingGoals.containsKey(mob.getUUID())
                && !formation.ticketGoals.containsKey(mob.getUUID())
                && (ticket == null || ticket.state != MobTicketState.RELEASED);
    }

    static void reportControllerStarted(Mob mob) {
        reportControllerStarted(mob, getBridgeWorkGeneration(mob));
    }

    static void reportControllerStarted(Mob mob, long generation) {
        Formation formation = getFormation(mob);
        if (formation == null || mob == null
                || !isCurrentTicket(formation.mobTickets.get(mob.getUUID()), generation)) return;
        reportRecruitmentGoalStarted(mob);
        formation.controllerStartedCount++;
    }

    static void reportControllerStopped(Mob mob) {
        reportRecruitmentGoalStopped(mob);
    }

    static void reportCombatPreemption(Mob mob) {
        Formation formation = getFormation(mob);
        if (formation == null) return;
        formation.combatPreemptionCount++;
        reportRecruitmentCombatConflict(mob);
    }

    static boolean shouldSubmitBridgePath(Mob mob) {
        return shouldSubmitRecruiterPath(mob);
    }

    static void reportNavigationSubmitted(Mob mob, Vec3 target) {
        reportNavigationSubmitted(mob, target, getBridgeWorkGeneration(mob));
    }

    static void reportNavigationSubmitted(Mob mob, Vec3 target, long generation) {
        Formation formation = getFormation(mob);
        if (formation == null) return;
        MobTicket ticket = formation.mobTickets.get(mob.getUUID());
        if (!isCurrentTicket(ticket, generation)) return;
        ticket.navigationReported = true;
        formation.navigationSubmittedCount++;
    }

    static void reportNavigationResult(Mob mob, Vec3 target, boolean accepted) {
        reportNavigationResult(mob, target, accepted, getBridgeWorkGeneration(mob));
    }

    static void reportNavigationResult(Mob mob, Vec3 target, boolean accepted, long generation) {
        if (!reportRecruitmentMoveResult(mob, target, accepted, generation)) return;
        if (accepted) {
            Formation formation = getFormation(mob);
            if (formation != null) formation.navigationAcceptedCount++;
        }
    }

    static void reportBridgePathFailure(Mob mob) {
        reportRecruitmentPathFailure(mob, getBridgeWorkGeneration(mob));
    }

    static void tickBridgeWork(Mob mob, Vec3 target) {
        Formation activeFormation = getFormation(mob);
        if (activeFormation != null && activeFormation.crosserTransit.containsKey(mob.getUUID())) {
            return;
        }
        boolean atIntake = isAtRecruitmentIntake(mob);
        reportRecruitmentGoalTick(mob, target, atIntake);
        if (shouldUseAssemblyStaging(mob)) {
            if (tryControlledAssemblyStaging(mob, target)) {
                reportRecruitmentArrival(mob);
            }
            return;
        }
        if (atIntake) {
            mob.getNavigation().stop();
            mob.setDeltaMovement(Vec3.ZERO);
            reportRecruitmentArrival(mob);
            return;
        }
        LivingEntity combatTarget = mob.getTarget();
        MobTicket activeTicket = activeFormation == null ? null : activeFormation.mobTickets.get(mob.getUUID());
        boolean ownsControlledSuction = activeTicket != null
                && activeTicket.state == MobTicketState.SUCTIONING;
        if (hasValidCombatPreemption(mob) && !ownsControlledSuction) {
            reportCombatPreemption(mob);
            return;
        }
        if (combatTarget != null) {
            if (mob.level() instanceof ServerLevel serverLevel) {
                recordCombatThreat(serverLevel, combatTarget);
            }
            mob.setTarget(null);
        }
        mob.setAggressive(false);
        if ((mob.tickCount + mob.getId()) % 3 == 0) {
            mob.getLookControl().setLookAt(target.x, target.y, target.z);
        }
        double distSq = mob.position().distanceToSqr(target);
        if (distSq > 4.0D) {
            if (ownsControlledSuction) {
                mob.setNoGravity(true);
                mob.noPhysics = true;
                mob.getNavigation().stop();
                mob.setDeltaMovement(Vec3.ZERO);
                return;
            }
            mob.setNoGravity(false);
            mob.noPhysics = false;
            long now = mob.level().getGameTime();
            boolean navCooldown = activeTicket != null && activeTicket.navigationSubmissionTick != Long.MIN_VALUE
                    && now - activeTicket.navigationSubmissionTick < 10;
            if (!navCooldown && (mob.getNavigation().isDone() || ((mob.tickCount + mob.getId()) % 20 == 0 && (mob.getNavigation().getTargetPos() == null
                    || mob.getNavigation().getTargetPos().distToCenterSqr(target) > 4.0D)))) {
                reportNavigationSubmitted(mob, target);
                boolean accepted = com.example.soundattract.pathfinding.PursuitNavigation.maybeMoveTo(mob, target, 1.0D);
                reportNavigationResult(mob, target, accepted);
                if (!accepted && mob.getNavigation().isDone()) reportBridgePathFailure(mob);
            }
            return;
        }
        if (shouldUseIntakeCapture(mob)) {
            boolean captured = tryControlledRecruitmentPull(mob, target);
            reportNavigationResult(mob, target, captured);
            Formation formation = getFormation(mob);
            MobTicket assignment = formation == null ? null : formation.mobTickets.get(mob.getUUID());
            if (retainsControlledSuctionOwnership(assignment == null ? null : assignment.state)) return;
            if (captured || getRecruitmentTarget(mob) == null) return;
        }
        if (shouldSubmitRecruiterPath(mob)) {
            reportNavigationSubmitted(mob, target);
            boolean accepted = com.example.soundattract.pathfinding.PursuitNavigation.maybeMoveTo(mob, target, 0.75D);
            reportNavigationResult(mob, target, accepted);
            if (!accepted && distSq > 4.0D) reportBridgePathFailure(mob);
        }
    }

    public static boolean isBridgeOwned(Mob mob) {
        return isControlled(mob);
    }

    public static boolean isBridgeOwnedByCurrentFormation(Mob mob) {
        return isControlled(mob);
    }

    public static boolean hasActiveTraverseBridgeNear(Mob mob, LivingEntity target) {
        if (mob == null || target == null || !(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        if (isControlled(mob)) {
            return true;
        }
        PerLevelState state = LEVELS.get(level.dimension().identifier());
        if (state == null || state.formations.isEmpty()) {
            return false;
        }
        Vec3 mobPos = mob.position();
        Vec3 targetPos = target.position();
        for (Formation formation : state.formations.values()) {
            if (formation == null || (formation.phase != Phase.TRAVERSE && formation.phase != Phase.ANCHOR)) {
                continue;
            }
            if (formation.integrity < INTEGRITY_THRESHOLD) {
                continue;
            }
            Vec3 start = formation.pivot != null ? Vec3.atBottomCenterOf(formation.pivot) : null;
            Vec3 end = formation.anchor != null ? Vec3.atBottomCenterOf(formation.anchor)
                    : (formation.spanEnd != null ? Vec3.atBottomCenterOf(formation.spanEnd) : null);
            if (start == null || end == null) {
                continue;
            }
            double startToMobSqr = mobPos.distanceToSqr(start);
            double endToTargetSqr = targetPos.distanceToSqr(end);
            if (startToMobSqr <= 32.0D * 32.0D && endToTargetSqr <= 48.0D * 48.0D) {
                if (startToMobSqr <= 16.0D) {
                    return true;
                }
                var pathToBridge = mob.getNavigation().createPath(formation.pivot, 0);
                if (pathToBridge != null && pathToBridge.canReach()) {
                    return true;
                }
            }
            double endToMobSqr = mobPos.distanceToSqr(end);
            double startToTargetSqr = targetPos.distanceToSqr(start);
            if (endToMobSqr <= 32.0D * 32.0D && startToTargetSqr <= 48.0D * 48.0D) {
                if (endToMobSqr <= 16.0D) {
                    return true;
                }
                BlockPos endPos = formation.anchor != null ? formation.anchor : formation.spanEnd;
                if (endPos != null) {
                    var pathToBridge = mob.getNavigation().createPath(endPos, 0);
                    if (pathToBridge != null && pathToBridge.canReach()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean hasFormingOrActiveBridgeNear(Mob mob, LivingEntity target) {
        if (mob == null || target == null || !(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        if (isControlled(mob)) {
            return true;
        }
        PerLevelState state = LEVELS.get(level.dimension().identifier());
        if (state == null || state.formations.isEmpty()) {
            return false;
        }
        Vec3 mobPos = mob.position();
        Vec3 targetPos = target.position();
        for (Formation formation : state.formations.values()) {
            if (formation == null || formation.collapsed || formation.phase == Phase.COLLAPSE) {
                continue;
            }
            Vec3 start = formation.pivot != null ? Vec3.atBottomCenterOf(formation.pivot) : null;
            Vec3 end = formation.anchor != null ? Vec3.atBottomCenterOf(formation.anchor)
                    : (formation.spanEnd != null ? Vec3.atBottomCenterOf(formation.spanEnd) : null);
            if (start == null && end == null) {
                continue;
            }
            BlockPos threat = formation.request != null ? formation.request.threatDestination() : formation.target;
            if (start != null && mobPos.distanceToSqr(start) <= 32.0D * 32.0D) {
                if (end != null && targetPos.distanceToSqr(end) <= 48.0D * 48.0D) return true;
                if (threat != null && targetPos.distanceToSqr(Vec3.atBottomCenterOf(threat)) <= 48.0D * 48.0D) return true;
            }
            if (end != null && mobPos.distanceToSqr(end) <= 32.0D * 32.0D) {
                if (start != null && targetPos.distanceToSqr(start) <= 48.0D * 48.0D) return true;
                if (threat != null && targetPos.distanceToSqr(Vec3.atBottomCenterOf(threat)) <= 48.0D * 48.0D) return true;
            }
        }
        return false;
    }

    public static boolean shouldYieldBridgeCombat(Mob mob) {
        if (mob == null) return false;
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || target.isRemoved() || !mob.canAttack(target)) {
            return false;
        }
        if (recentCombatExchange(mob, target)) {
            return true;
        }
        double recruitmentDistance = LivingWallConfig.MAX_RECRUITMENT_DISTANCE.get();
        if (mob.distanceToSqr(target) > recruitmentDistance * recruitmentDistance
                || !mob.getSensing().hasLineOfSight(target)) {
            return false;
        }
        if (mob.isWithinMeleeAttackRange(target)) {
            return true;
        }
        if (mob.getNavigation().isInProgress()) {
            var existingPath = mob.getNavigation().getPath();
            if (existingPath != null && existingPath.canReach() && existingPath.getTarget() != null
                    && existingPath.getTarget().distSqr(target.blockPosition()) <= 4.0D) {
                return true;
            }
        }
        if (mob.tickCount % 4 != 0) {
            return false;
        }
        var path = mob.getNavigation().createPath(target, 0);
        return path != null && path.canReach() && path.getTarget() != null
                && path.getTarget().distSqr(target.blockPosition()) <= 1.0D;
    }

    private static boolean recentCombatExchange(Mob mob, LivingEntity target) {
        int now = mob.tickCount;
        if (mob.getLastHurtByMob() == target
                && now - mob.getLastHurtByMobTimestamp() <= RECRUITMENT_COMBAT_MEMORY_TICKS) {
            return true;
        }
        return mob.getLastHurtMob() == target
                && now - mob.getLastHurtMobTimestamp() <= RECRUITMENT_COMBAT_MEMORY_TICKS;
    }

    static void reportRecruitmentCombatConflict(Mob mob) {
        Formation formation = getFormation(mob);
        if (formation == null || !(mob.level() instanceof ServerLevel level)) return;
        UUID id = mob.getUUID();
        if (!formation.ticketGoals.containsKey(id)) return;
        long now = level.getGameTime();
        logRecruitmentEvent(formation, mob, "recruitment_cancelled:active_combat", formation.mobTickets.get(id), null);
        releaseRecruiter(level, formation, id, now);
    }

    static void reportCandidateHoldingCombatConflict(Mob mob) {
        Formation formation = getFormation(mob);
        if (formation == null || !(mob.level() instanceof ServerLevel level)) return;
        UUID id = mob.getUUID();
        if (!formation.candidateHoldingGoals.containsKey(id)) return;
        logCandidateHoldingEvent(formation, mob, "candidate_holding_released:active_combat", null);
        removeCandidateHolding(mob, formation);
    }

    static void reportCandidateHoldingTick(Mob mob, Vec3 target) {
        Formation formation = getFormation(mob);
        if (formation == null || !(mob.level() instanceof ServerLevel level)
                || !formation.candidateHoldingGoals.containsKey(mob.getUUID())) return;
        long now = level.getGameTime();
        if (!tickElapsed(now, formation.lastHoldingDiagnosticTick, 20L)) return;
        formation.lastHoldingDiagnosticTick = now;
        String running = mob.goalSelector.getAvailableGoals().stream().filter(goal -> goal.isRunning())
                .map(wrapped -> wrapped.getGoal().getClass().getSimpleName() + "@" + wrapped.getPriority())
                .reduce((first, second) -> first + "," + second).orElse("");
        logCandidateHoldingEvent(formation, mob, "candidate_holding_tick:running=" + running,
                target);
    }

    static Vec3 getCandidateHoldingTarget(Mob mob) {
        Formation formation = getFormation(mob);
        if (formation == null || formation.collapsed || !(mob.level() instanceof ServerLevel level)) return null;
        MobTicket ticket = formation.mobTickets.get(mob.getUUID());
        if (ticket != null && ticket.state != MobTicketState.WAITING) return null;
        if (formation.members.contains(mob.getUUID())) return null;
        BlockPos cell = formation.candidateHoldingCells.get(mob.getUUID());
        if (cell == null || !isSafeApproachCell(level, cell)
                || isNearHazard(level, cell, formation.plan.hazardCells(), formation.plan.forward())) {
            if (cell != null) {
                logCandidateHoldingEvent(formation, mob, "candidate_holding_invalidated", Vec3.atBottomCenterOf(cell));
                removeCandidateHolding(mob, formation);
            }
            return null;
        }
        return Vec3.atBottomCenterOf(cell);
    }

    static boolean isAtCandidateHoldingCell(Mob mob) {
        Formation formation = getFormation(mob);
        if (formation == null || mob == null) return false;
        BlockPos cell = formation.candidateHoldingCells.get(mob.getUUID());
        return cell != null && mob.position().distanceToSqr(Vec3.atBottomCenterOf(cell)) <= INTAKE_WINDOW_ARRIVAL_DISTANCE_SQR;
    }

    static boolean isHoldingPathSafe(List<BlockPos> path) {
        if (path == null || path.isEmpty()) return false;
        BlockPos previous = null;
        int maxVerticalStep = configOrDefault(LivingWallConfig.MAX_SAFE_HOLDING_DROP, 5);
        for (BlockPos cell : path) {
            if (cell == null) return false;
            if (previous != null && Math.abs(cell.getY() - previous.getY())
                    > maxVerticalStep) return false;
            previous = cell;
        }
        return true;
    }

    private static boolean isSafeHoldingPath(ServerLevel level, Formation formation, Path path) {
        if (level == null || formation == null || path == null || path.getNodeCount() == 0) return false;
        List<BlockPos> cells = new ArrayList<>();
        for (int index = 0; index < path.getNodeCount(); index++) {
            Node node = path.getNode(index);
            BlockPos cell = new BlockPos(node.x, node.y, node.z);
            cells.add(cell);
            if (!isSafeApproachCell(level, cell)
                    || isNearHazard(level, cell, formation.plan.hazardCells(), formation.plan.forward())) return false;
        }
        return isHoldingPathSafe(cells);
    }

    public static boolean shouldDeferSoundClimb(Mob mob) {
        if (mob == null || !(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        if (isBridgeOwned(mob)) {
            return true;
        }
        PerLevelState state = LEVELS.get(level.dimension().identifier());
        if (state == null) {
            return false;
        }
        double radius = LivingWallConfig.MAX_GAP.get() + 4.0D;
        double radiusSqr = radius * radius;
        synchronized (state.formations) {
            for (Formation formation : state.formations.values()) {
                if (formation == null || formation.collapsed || formation.phase == Phase.COLLAPSE) {
                    continue;
                }
                double dx = mob.getX() - formation.pivot.getX() - 0.5D;
                double dz = mob.getZ() - formation.pivot.getZ() - 0.5D;
                if (dx * dx + dz * dz <= radiusSqr) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void addRecruitmentGoal(Mob mob, Formation formation) {
        if (mob == null || formation == null || formation.ticketGoals.containsKey(mob.getUUID())) return;
        UUID id = mob.getUUID();
        BlockPos heldCell = formation.candidateHoldingCells.get(id);
        formation.candidateHoldingGoals.remove(id);
        formation.candidateHoldingCells.remove(id);
        ensureBridgeWorkGoal(mob);
        mob.getNavigation().stop();
        formation.ticketGoals.put(id, Boolean.TRUE);
        mob.setTarget(null);
        MobTicket assignment = formation.mobTickets.get(id);
        boolean validated = mob.level() instanceof ServerLevel level
                && formation == getFormation(mob)
                && getFormation(mob) == formation
                && state(level).mobOwners.get(id) != null
                && formation.id.equals(state(level).mobOwners.get(id))
                && formation.ticketGoals.containsKey(id)
                && getRecruitmentTarget(mob) != null;
        if (!validated) {
            rollbackRecruitmentGoalInstall(mob, formation, null, assignment, heldCell);
            return;
        }
        formation.assignedCount++;
        logRecruitmentEvent(formation, mob, "assigned", assignment, null);
    }

    private static void removeRecruitmentGoal(Mob mob, Formation formation) {
        formation.ticketGoals.remove(mob.getUUID());
        ensureBridgeWorkGoal(mob);
    }

    static Vec3 getRecruitmentTarget(Mob mob) {
        Formation formation = getFormation(mob);
        if (formation == null || formation.collapsed || !formation.ticketGoals.containsKey(mob.getUUID())) return null;
        if (formation.phase != Phase.SCAN && formation.phase != Phase.ASSEMBLE) return null;
        MobTicket assignment = formation.mobTickets.get(mob.getUUID());
        if (assignment == null || !(mob.level() instanceof ServerLevel level)) return null;
        if ((assignment.state == MobTicketState.MOVING_TO_ASSEMBLY
                || assignment.state == MobTicketState.STAGING_AT_ASSEMBLY
                || assignment.state == MobTicketState.ASSEMBLED)
                && isSafeAssemblySlot(level, mob, assignment.assemblySlot, formation)) {
            if (assignment.state == MobTicketState.MOVING_TO_ASSEMBLY
                    && assignment.assemblyApproach != null
                    && !hasReachedAssemblyApproach(mob.position(), assignment.assemblyApproach)) {
                return Vec3.atBottomCenterOf(assignment.assemblyApproach);
            }
            return Vec3.atBottomCenterOf(assignment.assemblySlot);
        }
        if ((assignment.state == MobTicketState.MOVING_TO_FEEDER
                || assignment.state == MobTicketState.SUCTIONING)
                && assignment.intakeWindow != null) {
            BlockPos approach = assignment.intakeWindow.feederApproach();
            if (assignment.state == MobTicketState.MOVING_TO_FEEDER && approach != null
                    && !isAtIntakeApproach(mob.position(), assignment.intakeWindow)) {
                return Vec3.atBottomCenterOf(approach);
            }
            return Vec3.atBottomCenterOf(assignment.intakeWindow.feederCell());
        }
        return null;
    }

    static boolean shouldUseIntakeCapture(Mob mob) {
        Formation formation = getFormation(mob);
        if (formation == null || formation.collapsed || !(mob.level() instanceof ServerLevel level)) return false;
        MobTicket assignment = formation.mobTickets.get(mob.getUUID());
        if (assignment == null) return false;
        long now = level.getGameTime();
        return assignment.state == MobTicketState.SUCTIONING
                || (assignment.state == MobTicketState.MOVING_TO_FEEDER
                && now >= assignment.captureRetryUntil
                && isControlledIntakeWindow(level, mob, formation, assignment));
    }

    static boolean shouldUseAssemblyStaging(Mob mob) {
        Formation formation = getFormation(mob);
        if (formation == null || formation.collapsed || !(mob.level() instanceof ServerLevel level)) return false;
        MobTicket assignment = formation.mobTickets.get(mob.getUUID());
        if (assignment == null || assignment.state != MobTicketState.STAGING_AT_ASSEMBLY) return false;
        return isCurrentAssemblyOwner(level, formation, mob, assignment)
                && isWithinAssemblyAcceptanceEnvelope(mob.position(), assignment.assemblySlot);
    }

    static boolean isAtRecruitmentIntake(Mob mob) {
        Formation formation = getFormation(mob);
        if (formation == null || formation.collapsed || !formation.ticketGoals.containsKey(mob.getUUID())) {
            return false;
        }
        MobTicket assignment = formation.mobTickets.get(mob.getUUID());
        if (assignment == null) return false;
        if (assignment.state == MobTicketState.MOVING_TO_ASSEMBLY) {
            return isWithinAssemblyAcceptanceEnvelope(mob.position(), assignment.assemblySlot);
        }
        if (assignment.state == MobTicketState.STAGING_AT_ASSEMBLY || assignment.state == MobTicketState.ASSEMBLED) {
            return isAtAssemblySlot(mob.position(), assignment.assemblySlot);
        }
        return isRecruitmentCaptureReady(mob);
    }

    static boolean isAtIntakeWindow(Vec3 position, IntakeWindow window) {
        return position != null && window != null && window.feederCell() != null
                && position.distanceToSqr(Vec3.atBottomCenterOf(window.feederCell())) <= INTAKE_WINDOW_ARRIVAL_DISTANCE_SQR;
    }

    static boolean isAtIntakeApproach(Vec3 position, IntakeWindow window) {
        return position != null && window != null && window.feederApproach() != null
                && position.distanceToSqr(Vec3.atBottomCenterOf(window.feederApproach()))
                <= INTAKE_WINDOW_ARRIVAL_DISTANCE_SQR;
    }

    static boolean isAtFeederArrival(Vec3 position, IntakeWindow window) {
        return window != null && isCaptureReadySuctionPose(position, window, List.of(), null);
    }

    static boolean isRecruitmentCaptureReady(Mob mob) {
        Formation formation = getFormation(mob);
        if (formation == null || formation.plan == null || !(mob.level() instanceof ServerLevel level)) return false;
        MobTicket assignment = formation.mobTickets.get(mob.getUUID());
        return assignment != null
                && (assignment.state == MobTicketState.MOVING_TO_FEEDER || assignment.state == MobTicketState.SUCTIONING)
                && canMobUseIntakeWindow(level, mob, assignment.intakeWindow, formation)
                && isValidAssignedSuctionPose(mob.position(), assignment.intakeWindow, formation, assignment);
    }

    static boolean isCaptureReadySuctionPose(Vec3 position, IntakeWindow window, List<BlockPos> assemblySlots,
                                             BlockPos associatedAssemblySlot) {
        if (window == null || !isValidSuctionWindow(position, window.feederCell())) return false;
        if (assemblySlots == null) return false;
        BlockPos feet = BlockPos.containing(position);
        for (BlockPos slot : assemblySlots) {
            if (slot != null && !slot.equals(associatedAssemblySlot)
                    && feet.equals(slot)) {
                return false;
            }
        }
        return true;
    }

    static void reportRecruitmentArrival(Mob mob) {
        reportRecruitmentArrival(mob, getBridgeWorkGeneration(mob));
    }

    static void reportRecruitmentArrival(Mob mob, long generation) {
        Formation formation = getFormation(mob);
        if (formation == null || !formation.ticketGoals.containsKey(mob.getUUID())
                || !isCurrentTicket(formation.mobTickets.get(mob.getUUID()), generation)) return;
        if (formation.recruitmentArrivalReports.add(mob.getUUID())) {
            formation.arrivalCount++;
            formation.lastRecruitmentProgressTick = mob.level().getGameTime();
        }
    }

    static void reportRecruitmentGoalStarted(Mob mob) {
        Formation formation = getFormation(mob);
        if (formation == null) return;
        MobTicket assignment = formation.mobTickets.get(mob.getUUID());
        if (assignment != null && !assignment.goalStarted) {
            assignment.goalStarted = true;
            assignment.controllerStartTick = mob.level().getGameTime();
            assignment.progressDeadline = assignment.controllerStartTick
                    + bridgeProgressWindowTicks(assignment.routeLengthBlocks, Math.max(1, formation.ticketGoals.size()));
            logRecruitmentEvent(formation, mob, "goal_started", assignment, null);
        }
    }

    static void reportRecruitmentContinuationFailure(Mob mob, String reason) {
        Formation formation = getFormation(mob);
        if (formation == null) return;
        MobTicket assignment = formation.mobTickets.get(mob.getUUID());
        if (assignment != null && !assignment.continuationReported) {
            assignment.continuationReported = true;
            logRecruitmentEvent(formation, mob, "continue_false:" + reason, assignment, null);
        }
    }

    static void reportRecruitmentTickTargetMissing(Mob mob) {
        Formation formation = getFormation(mob);
        if (formation == null) return;
        MobTicket assignment = formation.mobTickets.get(mob.getUUID());
        if (assignment != null && !assignment.targetNullTickReported) {
            assignment.targetNullTickReported = true;
            logRecruitmentEvent(formation, mob, "tick_target_null", assignment, null);
        }
    }

    static void reportRecruitmentGoalTick(Mob mob, Vec3 target, boolean atIntake) {
        Formation formation = getFormation(mob);
        if (formation == null || !(mob.level() instanceof ServerLevel level)) return;
        MobTicket assignment = formation.mobTickets.get(mob.getUUID());
        if (assignment == null) return;
        long now = level.getGameTime();
        if (!tickElapsed(now, assignment.lastGoalTickLogTick, 20L)) return;
        assignment.lastGoalTickLogTick = now;
        logRecruitmentEvent(formation, mob, "goal_tick:target=" + (target != null)
                + ":atIntake=" + atIntake + ":navigationRemaining=" + state(level).navigationRemaining,
                assignment, target);
    }

    static void reportRecruitmentGoalStopped(Mob mob) {
        Formation formation = getFormation(mob);
        if (formation == null) return;
        MobTicket assignment = formation.mobTickets.get(mob.getUUID());
        if (assignment != null) logRecruitmentEvent(formation, mob, "goal_stopped", assignment, null);
    }

    private static boolean isCurrentTicket(MobTicket ticket, long generation) {
        return ticket != null && ticket.state != MobTicketState.RELEASED
                && (generation == Long.MIN_VALUE || ticket.generation == generation);
    }

    static boolean isStaleRecruitmentCallback(Object value, long generation) {
        if (!(value instanceof MobTicket ticket)) return true;
        return ticket.state == MobTicketState.RELEASED || ticket.generation != generation;
    }

    static boolean reportRecruitmentMoveResult(Mob mob, Vec3 target, boolean accepted, long generation) {
        Formation formation = getFormation(mob);
        if (formation == null) return false;
        MobTicket assignment = formation.mobTickets.get(mob.getUUID());
        if (!isCurrentTicket(assignment, generation)) return false;
        if (assignment.state != MobTicketState.MOVING_TO_ASSEMBLY
                && assignment.state != MobTicketState.MOVING_TO_FEEDER) return false;
        assignment.navigationSubmissionTick = mob.level().getGameTime();
        assignment.navigationAccepted |= accepted;
        if (accepted) {
            recordAcceptedRecruitmentProgress(assignment, mob.position().distanceToSqr(
                    Vec3.atBottomCenterOf(assignment.state == MobTicketState.MOVING_TO_FEEDER
                            ? feederCell(assignment) : assignment.assemblySlot)), mob.level().getGameTime());
        }
        if (!assignment.navigationReported || !accepted) {
            assignment.navigationReported = true;
            logRecruitmentEvent(formation, mob, accepted ? "move_accepted" : "move_rejected", assignment, target);
        }
        return true;
    }

    static void reportRecruitmentMoveResult(Mob mob, Vec3 target, boolean accepted) {
        reportRecruitmentMoveResult(mob, target, accepted, getBridgeWorkGeneration(mob));
    }

    static boolean tryControlledAssemblyStaging(Mob mob, Vec3 target) {
        Formation formation = getFormation(mob);
        if (formation == null || formation.collapsed
                || (formation.phase != Phase.SCAN && formation.phase != Phase.ASSEMBLE)
                || !(mob.level() instanceof ServerLevel level) || target == null) return false;
        MobTicket assignment = formation.mobTickets.get(mob.getUUID());
        if (assignment == null || assignment.state != MobTicketState.STAGING_AT_ASSEMBLY) return false;
        if (!isCurrentAssemblyOwner(level, formation, mob, assignment)
                || !isWithinAssemblyAcceptanceEnvelope(mob.position(), assignment.assemblySlot)) {
            releaseRecruiter(level, formation, mob.getUUID(), level.getGameTime());
            return false;
        }
        if (assignment.assemblyStageStartedTick == Long.MIN_VALUE) {
            assignment.assemblyStageStartedTick = level.getGameTime();
            assignment.assemblyLastProgressTick = level.getGameTime();
            assignment.assemblyBestDistanceSqr = exactAssemblyDistanceSqr(mob.position(), assignment.assemblySlot);
            holdRalliedWorker(mob, formation);
        }
        Vec3 exactTarget = Vec3.atBottomCenterOf(assignment.assemblySlot);
        double distanceSqr = exactAssemblyDistanceSqr(mob.position(), assignment.assemblySlot);
        if (distanceSqr > 4.0D) {
            mob.setNoGravity(false);
            mob.noPhysics = false;
            mob.setTarget(null);
            mob.setAggressive(false);
            boolean accepted = com.example.soundattract.pathfinding.PursuitNavigation.maybeMoveTo(mob, exactTarget, 1.0D);
            return false;
        }
        if (distanceSqr <= CONTROLLED_INTAKE_SETTLE_DISTANCE * CONTROLLED_INTAKE_SETTLE_DISTANCE) {
            return finalizeAssemblyLock(level, formation, mob, assignment, exactTarget, false);
        }
        if (assignment.assemblyStageTicks++ >= CONTROLLED_ASSEMBLY_STAGE_MAX_TICKS) {
            return recoverOrReleaseAssemblyStage(level, formation, mob, assignment, "timeout");
        }
        Vec3 delta = exactTarget.subtract(mob.position());
        double distance = delta.length();
        if (distance <= 1.0E-6D) {
            return finalizeAssemblyLock(level, formation, mob, assignment, exactTarget, false);
        }
        Vec3 step = delta.scale(Math.min(CONTROLLED_INTAKE_PULL_STEP, distance) / distance);
        Vec3 next = mob.position().add(step);
        if (assignment.assemblyStageTicks < 8 || assignment.assemblyStageTicks % 20 == 0) {
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] assembly_command_diagnostic bridge={} worker={} tick={} pos={} slot={} center={} distance={} step={} noGravity={} noPhysics={} navDone={} navTarget={}",
                    formation.id, mob.getUUID(), level.getGameTime(), mob.position(), assignment.assemblySlot,
                    exactTarget, distance, step, mob.isNoGravity(), mob.noPhysics,
                    mob.getNavigation().isDone(), mob.getNavigation().getTargetPos());
        }
        if (!isAssemblyStagePoseValid(level, formation, mob, assignment, next)) {
            logAssemblyStagePoseRejection(level, formation, mob, assignment, next);
            Vec3 nextYOnly = mob.position().add(0, step.y, 0);
            Vec3 nextXZOnly = mob.position().add(step.x, 0, step.z);
            boolean yMoves = Math.abs(step.y) > 1.0E-4D && isAssemblyStagePoseValid(level, formation, mob, assignment, nextYOnly);
            boolean xzClear = !yMoves && isAssemblyStagePoseValid(level, formation, mob, assignment, nextXZOnly);
            if (yMoves) {
                next = nextYOnly;
                SoundAttractMod.LOGGER.info("[LivingWallCoordinator] assembly_y_fallback bridge={} worker={} tick={} pos={} next={}", formation.id, mob.getUUID(), level.getGameTime(), mob.position(), next);
            } else if (xzClear) {
                next = nextXZOnly;
                SoundAttractMod.LOGGER.info("[LivingWallCoordinator] assembly_xz_fallback bridge={} worker={} tick={} pos={} next={}", formation.id, mob.getUUID(), level.getGameTime(), mob.position(), next);
            } else {
                logAssemblyStagePoseRejection(level, formation, mob, assignment, nextXZOnly);
                return recoverOrReleaseAssemblyStage(level, formation, mob, assignment, "blocked");
            }
        }
        if (distanceSqr + RECRUITER_PROGRESS_EPSILON_SQR < assignment.assemblyBestDistanceSqr) {
            assignment.assemblyBestDistanceSqr = distanceSqr;
            assignment.assemblyLastProgressTick = level.getGameTime();
        } else if (level.getGameTime() - assignment.assemblyLastProgressTick >= RECRUITER_OBSERVATION_TICKS) {
            return recoverOrReleaseAssemblyStage(level, formation, mob, assignment, "zero_progress");
        }
        holdRalliedWorker(mob, formation);
        mob.setPos(next.x, next.y, next.z);
        mob.setDeltaMovement(Vec3.ZERO);
        mob.fallDistance = 0.0F;
        return false;
    }

    static boolean tryControlledRecruitmentPull(Mob mob, Vec3 target) {
        Formation formation = getFormation(mob);
        if (formation == null || formation.collapsed
                || (formation.phase != Phase.SCAN && formation.phase != Phase.ASSEMBLE)
                || !(mob.level() instanceof ServerLevel level) || target == null) return false;
        MobTicket assignment = formation.mobTickets.get(mob.getUUID());
        if (assignment == null || (assignment.state != MobTicketState.MOVING_TO_FEEDER
                && assignment.state != MobTicketState.SUCTIONING)) return false;
        if (assignment.state == MobTicketState.SUCTIONING) {
            captureIntakeHold(mob, formation);
            BlockPos feeder = assignment.intakeWindow == null ? null : assignment.intakeWindow.feederCell();
            if (feeder != null && mob.getY() < feeder.getY()) {
                mob.setPos(mob.getX(), feeder.getY(), mob.getZ());
                mob.setDeltaMovement(Vec3.ZERO);
                mob.fallDistance = 0.0F;
            }
        }
        if (!isControlledIntakeWindow(level, mob, formation, assignment)) {
            if (assignment.state == MobTicketState.SUCTIONING) {
                noteIntakePoseFailure(level, formation, mob, assignment, "window_lost", false);
            }
            return false;
        }
        assignment.intakeWindowFailures = 0;
        assignment.intakeHazardFailures = 0;
        if (assignment.state == MobTicketState.MOVING_TO_FEEDER) {
            transitionTicket(assignment, MobTicketState.SUCTIONING);
            assignment.captureTicks = 0;
            assignment.captureAttempts++;
            captureIntakeHold(mob, formation);
            logRecruitmentEvent(formation, mob, "suction_started", assignment, target);
        }
        Vec3 delta = target.subtract(mob.position());
        double distance = delta.length();
        if (distance > 2.0D) {
            mob.setNoGravity(false);
            mob.noPhysics = false;
            mob.setTarget(null);
            mob.setAggressive(false);
            boolean accepted = com.example.soundattract.pathfinding.PursuitNavigation.maybeMoveTo(mob, target, 1.0D);
            return false;
        }
        if (distance <= CONTROLLED_INTAKE_SETTLE_DISTANCE) {
            mob.setTarget(null);
            mob.setAggressive(false);
            mob.setNoGravity(true);
            mob.noPhysics = true;
            mob.getNavigation().stop();
            mob.setDeltaMovement(Vec3.ZERO);
            mob.setPos(target.x, target.y, target.z);
            mob.fallDistance = 0.0F;
            assignment.controlledPull = true;
            return true;
        }
        if (assignment.captureTicks++ >= CONTROLLED_INTAKE_MAX_TICKS) {
            abortIntakeCapture(level, formation, mob, assignment, "timeout");
            return false;
        }
        double climbSpeed = LivingWallConfig.CLIMB_SPEED_BLOCKS_PER_TICK.get();
        double pullSpeed = Math.min(climbSpeed, CONTROLLED_INTAKE_PULL_STEP);
        Vec3 step = delta.scale(Math.min(pullSpeed, distance) / distance);
        Vec3 next = mob.position().add(step);
        boolean slotClear = isSlotClear(level, mob, next);
        boolean hazardous = isNearHazard(level, BlockPos.containing(next), formation.plan.hazardCells(), formation.plan.forward());
        boolean validPose = isValidAssignedSuctionPose(next, assignment.intakeWindow, formation, assignment);
        if (!slotClear || hazardous || !validPose) {
            if (assignment.state == MobTicketState.SUCTIONING) {
                noteIntakePoseFailure(level, formation, mob, assignment,
                        hazardous ? "hazard" : "window_lost", hazardous);
            }
            return false;
        }
        assignment.controlledPull = true;
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.setNoGravity(true);
        mob.noPhysics = true;
        mob.getNavigation().stop();
        mob.setDeltaMovement(step);
        mob.setPos(next.x, next.y, next.z);
        mob.fallDistance = 0.0F;
        return true;
    }

    private static boolean finalizeAssemblyLock(ServerLevel level, Formation formation, Mob mob,
                                                MobTicket assignment, Vec3 exactTarget, boolean allowRecovery) {
        if (!isExactAssemblyLockValid(level, formation, mob, assignment, exactTarget)) {
            if (!allowRecovery) logAssemblyRejectionDiagnostic(level, formation, mob, assignment, "lock_retry");
            return allowRecovery && recoverOrReleaseAssemblyStage(level, formation, mob, assignment, "invalid_lock");
        }
        holdRalliedWorker(mob, formation);
        mob.setPos(exactTarget.x, exactTarget.y, exactTarget.z);
        mob.setDeltaMovement(Vec3.ZERO);

        recordAssemblyAcquisitionHold(formation.assemblyHoldPositions, mob.getUUID(), exactTarget);
        mob.fallDistance = 0.0F;
        if (assignment.state != MobTicketState.ASSEMBLED) {
            transitionTicket(assignment, MobTicketState.ASSEMBLED);
        }
        assignment.assemblyRecoveryAttempts = 0;
        logRecruitmentEvent(formation, mob, "assembled", assignment, exactTarget);
        return true;
    }

    private static boolean recoverOrReleaseAssemblyStage(ServerLevel level, Formation formation, Mob mob,
                                                         MobTicket assignment, String reason) {
        if (level == null || formation == null || mob == null || assignment == null) return false;
        logAssemblyRejectionDiagnostic(level, formation, mob, assignment, reason);
        assignment.assemblyRecoveryAttempts++;
        int maxRetries = isCanonicalLivingBridge(formation) ? 12 : RECRUITER_RETRY_WINDOWS;
        if (assignment.assemblyRecoveryAttempts >= maxRetries
                || assignment.retries >= maxRetries
                || !isCurrentAssemblyOwner(level, formation, mob, assignment)) {
            logRecruitmentEvent(formation, mob, "assembly_stage_release:" + reason, assignment, null);
            releaseRecruiter(level, formation, mob.getUUID(), level.getGameTime());
            mob.setNoGravity(false);
            mob.noPhysics = false;
            return false;
        }
        transitionTicket(assignment, MobTicketState.MOVING_TO_ASSEMBLY);
        releaseRalliedHold(level, formation, mob.getUUID(), mob);
        mob.setNoGravity(false);
        mob.noPhysics = false;
        assignment.assemblyStageStartedTick = Long.MIN_VALUE;
        assignment.lastPathTick = Long.MIN_VALUE;
        assignment.navigationAccepted = false;
        assignment.navigationReported = false;
        assignment.goalStarted = true;
        assignment.progressDeadline = level.getGameTime()
                + bridgeProgressWindowTicks(assignment.routeLengthBlocks, Math.max(1, formation.ticketGoals.size()));
        logRecruitmentEvent(formation, mob, "assembly_stage_retry:" + reason, assignment, null);
        return false;
    }

    private static void logAssemblyRejectionDiagnostic(ServerLevel level, Formation formation, Mob mob,
                                                       MobTicket assignment, String event) {
        if (formation.assemblyLockDiagnostics++ >= 32) return;
        boolean isBridge = isCanonicalLivingBridge(formation);
        boolean isFoundationSlot = formation.plan != null && formation.plan.assemblySlots() != null
                && !formation.plan.assemblySlots().isEmpty()
                && assignment.assemblySlot.equals(formation.plan.assemblySlots().get(0));
        Vec3 slotCenter = Vec3.atBottomCenterOf(assignment.assemblySlot);
        String safeguard;
        if (!isCurrentAssemblyOwner(level, formation, mob, assignment)) safeguard = "ownership";
        else if (!isSafeAssemblySlot(level, mob, assignment.assemblySlot, formation)) safeguard = "anchor_or_slot";
        else if (!isAtAssemblySlot(mob.position(), assignment.assemblySlot)) safeguard = "exact_lock";
        else if ((!isBridge || isFoundationSlot) && !hasFoundationTerrainSupport(level, slotCenter)) safeguard = "support";
        else if (!resolveClearance(level, mob, slotCenter, 0.0D).clear()) safeguard = "clearance";
        else if (!isEntityClearAt(level, mob, slotCenter, slotCenter)) safeguard = "entity_clearance";
        else {
            Entity occupant = findAssemblyOccupant(level, formation, mob, mob.position(), slotCenter,
                    assignment.assemblySlot);
            safeguard = occupant == null ? "route_or_progress"
                    : "occupant:" + occupant.getType() + ":" + occupant.getUUID();
        }
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] assembly_rejection_diagnostic bridge={} worker={} event={} safeguard={} state={} pos={} slot={} owner={} retries={} stageRetries={}",
                formation.id, mob.getUUID(), event, safeguard, assignment.state, mob.position(), assignment.assemblySlot,
                state(level).mobOwners.get(mob.getUUID()), assignment.retries, assignment.assemblyRecoveryAttempts);
    }

    private static boolean isControlledIntakeWindow(ServerLevel level, Mob mob, Formation formation,
                                                    MobTicket assignment) {
        if (level == null || mob == null || formation == null || assignment == null || assignment.intakeWindow == null
                || (assignment.state != MobTicketState.MOVING_TO_FEEDER
                && assignment.state != MobTicketState.SUCTIONING)
                || !canMobUseIntakeWindow(level, mob, assignment.intakeWindow, formation)) return false;
        return isValidAssignedSuctionPose(mob.position(), assignment.intakeWindow, formation, assignment);
    }

    static boolean isValidSuctionWindow(Vec3 position, BlockPos cell) {
        if (position == null || cell == null || position.y < cell.getY()) return false;
        Vec3 target = Vec3.atBottomCenterOf(cell);
        double targetDx = position.x - target.x;
        double targetDz = position.z - target.z;
        return targetDx * targetDx + targetDz * targetDz
                <= CONTROLLED_INTAKE_TARGET_RADIUS * CONTROLLED_INTAKE_TARGET_RADIUS
                && position.y - target.y <= 1.5D;
    }

    static boolean isValidSuctionPose(Vec3 position, IntakeWindow window, List<BlockPos> assemblySlots) {
        return isCaptureReadySuctionPose(position, window, assemblySlots, null);
    }

    private static boolean isValidAssignedSuctionPose(Vec3 position, IntakeWindow window,
                                                      Formation formation, MobTicket assignment) {
        if (formation == null || assignment == null || formation.plan == null) return false;
        if (window != null && window.transientAnchor()) {
            return isLocalAssemblySuctionPose(position, window, formation, assignment.mobId, assignment.assemblySlot);
        }
        return isCaptureReadySuctionPose(position, window, formation.plan.assemblySlots(), assignment.assemblySlot);
    }

    private static boolean isLocalAssemblySuctionPose(Vec3 position, IntakeWindow window, Formation formation,
                                                      UUID mobId, BlockPos assemblySlot) {
        if (position == null || window == null || formation == null || mobId == null || assemblySlot == null
                || !isValidSuctionWindow(position, window.feederCell())) return false;
        for (Map.Entry<BlockPos, UUID> entry : formation.assemblySlotOwners.entrySet()) {
            if (!mobId.equals(entry.getValue())
                    && entry.getKey().equals(window.feederCell())) return false;
        }
        return true;
    }

    static boolean isValidSuctionPoseForAssemblySlot(Vec3 position, IntakeWindow window,
                                                     BlockPos associatedAssemblySlot,
                                                     List<BlockPos> assemblySlots) {
        return isCaptureReadySuctionPose(position, window, assemblySlots, associatedAssemblySlot);
    }

    private static void captureIntakeHold(Mob mob, Formation formation) {
        applyControlledHold(mob, formation, true);
    }

    private static void abortIntakeCapture(ServerLevel level, Formation formation, Mob mob,
                                           MobTicket assignment, String reason) {
        if (assignment.state != MobTicketState.SUCTIONING) return;
        long now = level.getGameTime();
        transitionTicket(assignment, MobTicketState.MOVING_TO_FEEDER);
        assignment.captureTicks = 0;
        assignment.controlledPull = false;
        assignment.captureRetryUntil = now + CAPTURE_RETRY_COOLDOWN_TICKS;
        releaseRalliedHold(level, formation, mob.getUUID(), mob);
        logRecruitmentEvent(formation, mob, "intake_failed:" + ("timeout".equals(reason) ? "timeout" : "window_lost"), assignment, null);
        if ("hazard".equals(reason)) assignment.intakeHazardFailures++;
        else assignment.intakeWindowFailures++;
        logRecruitmentEvent(formation, mob, "capture_retry:" + reason, assignment, null);
        if (shouldTerminateIntakeCaptureFailure(
                Math.max(assignment.intakeWindowFailures, assignment.intakeHazardFailures))) {
            logRecruitmentEvent(formation, mob, "intake_terminal_release:" + reason, assignment, null);
            releaseRecruiter(level, formation, mob.getUUID(), now);
        }
    }

    static boolean shouldTerminateIntakeCaptureFailure(int consecutiveFailures) {
        return consecutiveFailures >= INTAKE_POSE_FAILURE_THRESHOLD;
    }

    static boolean shouldRetryIntakePoseFailure(int consecutiveFailures) {
        return consecutiveFailures >= 0 && !shouldTerminateIntakeCaptureFailure(consecutiveFailures);
    }

    private static void noteIntakePoseFailure(ServerLevel level, Formation formation, Mob mob,
                                              MobTicket assignment, String reason, boolean hazard) {
        if (assignment == null || assignment.state != MobTicketState.SUCTIONING) return;
        if (hazard) assignment.intakeHazardFailures++;
        else assignment.intakeWindowFailures++;
        logRecruitmentEvent(formation, mob, "capture_retry:" + reason, assignment, null);
        if (shouldTerminateIntakeCaptureFailure(
                Math.max(assignment.intakeWindowFailures, assignment.intakeHazardFailures))) {
            logRecruitmentEvent(formation, mob, "intake_terminal_release:" + reason, assignment, null);
            releaseRecruiter(level, formation, mob.getUUID(), level.getGameTime());
        }
    }

    static void reportRecruitmentPathFailure(Mob mob) {
        reportRecruitmentPathFailure(mob, getBridgeWorkGeneration(mob));
    }

    static void reportRecruitmentPathFailure(Mob mob, long generation) {
        Formation formation = getFormation(mob);
        if (formation == null || !formation.ticketGoals.containsKey(mob.getUUID())) return;
        MobTicket assignment = formation.mobTickets.get(mob.getUUID());
        if (isCurrentTicket(assignment, generation) && assignment.goalStarted
                && assignment.navigationSubmissionTick != Long.MIN_VALUE) {
            long now = mob.level().getGameTime();
            assignment.lastPathTick = Long.MIN_VALUE;
            if (assignment.state == MobTicketState.MOVING_TO_ASSEMBLY
                    || assignment.state == MobTicketState.STAGING_AT_ASSEMBLY
                    || assignment.state == MobTicketState.ASSEMBLED) {
                if (isAtAssemblySlot(mob.position(), assignment.assemblySlot)) {
                    formation.recruitmentArrivalReports.add(mob.getUUID());
                    logRecruitmentEvent(formation, mob, "near_target_path_reject", assignment,
                            Vec3.atBottomCenterOf(assignment.assemblySlot));
                    return;
                }

                if (assignment.assemblySlot != null) {
                    double approachDistSqr = exactAssemblyDistanceSqr(mob.position(), assignment.assemblySlot);
                    boolean descendingTowardSlot = mob.getDeltaMovement().y < 0
                            && mob.getY() > assignment.assemblySlot.getY();
                    if (approachDistSqr <= ASSEMBLY_ARRIVAL_DISTANCE_SQR && descendingTowardSlot) {
                        logRecruitmentEvent(formation, mob, "near_target_path_reject:gravity_drop", assignment,
                                Vec3.atBottomCenterOf(assignment.assemblySlot));
                        return;
                    }
                }
                if (assignment.assemblyApproach != null
                        && mob.distanceToSqr(Vec3.atBottomCenterOf(assignment.assemblyApproach)) <= 4.0D) {
                    logRecruitmentEvent(formation, mob, "near_approach_path_reject", assignment,
                            Vec3.atBottomCenterOf(assignment.assemblyApproach));
                    return;
                }
            } else if (assignment.state == MobTicketState.MOVING_TO_FEEDER
                    && isRecruitmentCaptureReady(mob)) {
                formation.recruitmentArrivalReports.add(mob.getUUID());
                logRecruitmentEvent(formation, mob, "near_intake_path_reject", assignment,
                        Vec3.atBottomCenterOf(assignment.intakeWindow.feederCell()));
                return;
            }
            if (assignment.lastPathFailureTick == Long.MIN_VALUE || now - assignment.lastPathFailureTick >= 20) {
                assignment.lastPathFailureTick = now;
                assignment.pathFailures++;
                BlockPos cell = assignment.state == MobTicketState.MOVING_TO_FEEDER && assignment.intakeWindow != null
                        ? assignment.intakeWindow.feederCell() : assignment.assemblySlot;
                if (cell != null) {
                    formation.pathSurveys.put(new PathSurveyKey(mob.getUUID(), cell),
                            new PathSurvey(false, mob.blockPosition(), now + RECRUITER_PATH_FAILURE_BLACKLIST_TICKS));
                    if (assignment.state == MobTicketState.MOVING_TO_FEEDER) {
                        formation.recruitmentRoutes.put(new RecruitmentRouteKey(mob.getUUID(), assignment.assemblySlot, cell),
                                new RecruitmentRouteSurvey(false, mob.blockPosition(), now + RECRUITER_PATH_FAILURE_BLACKLIST_TICKS));
                    }
                }
            }
            logRecruitmentEvent(formation, mob, assignment.state == MobTicketState.MOVING_TO_FEEDER
                    ? "mob_route_failed:to_feeder" : "mob_route_failed:to_assembly", assignment, null);
        }
    }

    private static void rollbackRecruitmentGoalInstall(Mob mob, Formation formation, Object goal,
                                                       MobTicket assignment, BlockPos heldCell) {
        if (mob == null || formation == null) return;
        UUID id = mob.getUUID();
        formation.ticketGoals.remove(id);
        formation.mobTickets.remove(id);
        if (assignment != null) {
            assignment.navigationAccepted = false;
            assignment.navigationReported = false;
            assignment.goalStarted = false;
            assignment.continuationReported = false;
            assignment.targetNullTickReported = false;
        }
        releaseAssemblySlot(formation, id);
        releaseAssemblyLane(formation, id);
        formation.recruitmentArrivalReports.remove(id);
        clearRecruitmentHoldState(formation, id);
        if (mob.level() instanceof ServerLevel level) {
            if (formation.id.equals(state(level).mobOwners.get(id))
                    && formation == getFormation(mob)
                    && mob.isAlive() && !mob.isRemoved()) {
                if (heldCell != null) {
                    formation.candidateHoldingCells.put(id, heldCell);
                }
                restoreCandidateHolding(level, formation, mob);
            } else {
                releaseBridgeOwnership(level, formation, id);
            }
        }
        mob.getNavigation().stop();
    }

    static boolean shouldSubmitRecruiterPath(Mob mob) {
        Formation formation = getFormation(mob);
        if (formation == null || !(mob.level() instanceof ServerLevel level)) return false;
        MobTicket assignment = formation.mobTickets.get(mob.getUUID());
        PerLevelState state = state(level);
        long now = level.getGameTime();
        String reason = null;
        if (assignment == null) {
            reason = "assignment_missing";
        } else if (assignment.state != MobTicketState.MOVING_TO_ASSEMBLY
                && assignment.state != MobTicketState.MOVING_TO_FEEDER) {
            reason = "state_" + assignment.state;
        } else if (state.navigationRemaining <= 0) {
            reason = "quota_empty";
        } else if (!tickElapsed(now, assignment.lastPathTick, 10L)) {
            reason = "path_cooldown";
        }
        if (reason != null) {
            if (assignment != null && (!reason.equals(assignment.lastPathSkipReason)
                    || tickElapsed(now, assignment.lastPathSkipLogTick, 20L))) {
                assignment.lastPathSkipReason = reason;
                assignment.lastPathSkipLogTick = now;
                logRecruitmentEvent(formation, mob, "path_skipped:" + reason
                        + ":navigationRemaining=" + state.navigationRemaining, assignment, null);
            }
            return false;
        }
        assignment.lastPathTick = now;
        state.navigationRemaining--;
        return true;
    }

    public static void prepareRaid(Mob leader, BlockPos target, long now) {
        ConstructionRequest request = resolveAdmissionRequest(leader, target, now);
        if (request == null) return;
        prepareFormation(leader, request.selectedAnchor(), now, null, request);
    }

    public static void prepareNonRaid(Mob leader, BlockPos target, long now) {
        ConstructionRequest request = resolveAdmissionRequest(leader, target, now);
        if (request == null) return;
        prepareFormation(leader, request.selectedAnchor(), now, null, request);
    }

    private static boolean prepareFormation(Mob leader, BlockPos target, long now, List<Mob> discoveredGroup,
                                           ConstructionRequest request) {
        if (leader == null || !(leader.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!LivingWallConfig.ENABLE_LIVING_WALL.get()) {
            return false;
        }
        if (target == null || request == null || request.selectedAnchor() == null
                || !target.equals(request.selectedAnchor()) || !leader.isAlive() || leader.isRemoved() || !isEligible(leader)) {
            return false;
        }
        PerLevelState state = state(level);
        boolean admitted = false;
        synchronized (state.formations) {
            ConstructionIntent attemptIntent = null;
            if (request != null && request.evidence() != null) {
                ConstructionIntent intent = state.constructionIntents.get(leader.getUUID());
                if (isLiveAttachedIntent(state, intent)) return false;
                ConstructionIntent candidate = new ConstructionIntent(leader.getUUID(), request, null, now);
                if (intent == null || intent.candidateKey == null || !intent.candidateKey.equals(candidate.candidateKey)) {
                    state.constructionIntents.put(leader.getUUID(), candidate);
                    attemptIntent = candidate;
                } else if (intent.request == null || intent.scanRevision != request.revision()
                        || intent.terrainSignature != terrainSignature(level, request.evidence().anchor(),
                        request.threatDestination(), request.evidence())) {
                    intent.request = request;
                    intent.scanRevision = request.revision();
                    intent.terrainSignature = terrainSignature(level, request.evidence().anchor(),
                            request.threatDestination(), request.evidence());
                }
                if (attemptIntent == null) attemptIntent = intent;
            }
            Formation formation = buildFormation(level, leader, target, now, discoveredGroup, request);
            Formation duplicate = formation == null ? null : dominatingRouteFormation(state, formation);
            if (duplicate != null) {
                admissionLog(level, state, now, "build rejected: duplicate_route existing=" + duplicate.id
                        + ", planner=" + leader.getUUID() + ", pivot=" + formation.pivot
                        + ", existingPivot=" + duplicate.pivot + ", anchor=" + formation.anchor);
                clearConstructionIntent(state, attemptIntent);
            } else if (formation != null && claimFootprint(state, formation)) {
                state.formations.put(formation.id, formation);
                state.leaderToFormations.computeIfAbsent(leader.getUUID(), ignored -> new HashSet<>()).add(formation.id);
                enqueueFormation(state, formation.id);
                ConstructionIntent intent = state.constructionIntents.get(leader.getUUID());
                if (intent != null && intent == formation.intentToken && state.constructionIntents.get(leader.getUUID()) == intent) {
                    intent.candidateKey = candidateConstructionSiteKey(formation.request, formation.pivot, formation.plan);
                    intent.attachedFormationId = formation.id;
                    intent.request = formation.request;
                    intent.scanRevision = formation.request == null ? -1L : formation.request.revision();
                    intent.terrainSignature = terrainSignature(level, formation.pivot, formation.anchor, formation.request.evidence());
                    intent.lastProgressTick = now;
                }
                admissionLog(level, state, now, "formation admitted: required=" + formation.requiredBodies
                        + ", pivot=" + formation.pivot + ", anchor=" + formation.anchor
                        + ", scenario=" + formation.plan.scenario()
                        + ", planner=" + leader.getUUID()
                        + ", plannerFormations=" + state.leaderToFormations.get(leader.getUUID()).size());
                admitted = true;
            } else {
                UUID conflict = formation == null ? null : footprintConflict(state, formation);
                admissionLog(level, state, now, conflict == null
                        ? "build rejected: no valid group/site/profile"
                        : "build rejected: footprint_conflict owner=" + conflict + ", pivot=" + formation.pivot
                        + ", anchor=" + formation.anchor);
                clearConstructionIntent(state, attemptIntent);
            }
        }
        return admitted || state.constructionIntents.containsKey(leader.getUUID());
    }

    public static void clearRaid(Mob leader) {
        if (leader == null || !(leader.level() instanceof ServerLevel level)) {
            return;
        }
        PerLevelState state = LEVELS.get(level.dimension().identifier());
        if (state == null) {
            return;
        }
        List<Formation> formations = new ArrayList<>();
        synchronized (state.formations) {
            Set<UUID> ids = state.leaderToFormations.remove(leader.getUUID());
            if (ids != null) {
                for (UUID id : ids) {
                    Formation formation = state.formations.remove(id);
                    if (formation != null) formations.add(formation);
                }
            }
        }
        for (Formation formation : formations) {
            logFormationState(level, formation, level.getGameTime(), "release:raid_clear");
            releaseFormation(level, formation);
        }
    }

    public static void tick(ServerLevel level) {
        if (level == null) {
            return;
        }
        if (!LivingWallConfig.ENABLE_LIVING_WALL.get()) {
            clearLevel(level, "config_disabled");
            return;
        }
        PerLevelState state = state(level);
        long now = level.getGameTime();
        double tickMs = level.getServer().getAverageTickTimeNanos() / 1_000_000.0;
        int pressureTier = pressureTier(tickMs);
        boolean pressured = pressureTier >= 2;
        state.navigationRemaining = pressured ? Math.max(1, NAVIGATION_QUOTA / 2) : NAVIGATION_QUOTA;
        state.recruiterObservationsRemaining = pressured ? Math.max(1, RECRUITER_OBSERVATION_QUOTA / 2) : RECRUITER_OBSERVATION_QUOTA;
        state.rosterRefreshesRemaining = pressured ? Math.max(1, ROSTER_REFRESH_QUOTA / 2) : ROSTER_REFRESH_QUOTA;
        state.pathSurveysRemaining = pressured ? Math.max(1, PATH_SURVEY_QUOTA / 2) : PATH_SURVEY_QUOTA;
        observeWindowWorkers(level, state, now);
        if (state.lastCleanupTick < 0 || now - state.lastCleanupTick > 40L) {
            cleanup(state, level);
            recoverLoadedMarkers(level, now);
            state.terrainRejections.entrySet().removeIf(entry -> entry.getValue() <= now);
            state.contentionCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
            state.targetScans.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
            state.positionPlans.entrySet().removeIf(entry -> !entry.getValue().isLive(now));
            Set<UUID> activeWorkers = state.positionPlans.values().stream()
                    .flatMap(plan -> plan.rosterIdentity().stream()).collect(java.util.stream.Collectors.toSet());
            state.groundingObservations.keySet().removeIf(worker -> !activeWorkers.contains(worker));
            state.wallProfiles.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
            state.corridorCache.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
            state.rememberedThreats.entrySet().removeIf(entry -> entry.getValue() == null || !entry.getValue().isValid(now, REMEMBERED_THREAT_TTL_TICKS));
            state.basinEvacuations.entrySet().removeIf(entry -> entry.getValue().expiresTick() <= now);
            state.basinFailures.entrySet().removeIf(entry -> entry.getValue().cooldownUntil() <= now && now - entry.getValue().lastFailureTick() > 600L);
            state.lastCleanupTick = now;
        }
        drainAdmissionCompletions(level, state, now);
        int scanInterval = admissionScanInterval(pressureTier);
        boolean admissionDue = state.lastNonRaidScanTick < 0
                || now - state.lastNonRaidScanTick >= scanInterval
                || now - state.lastNonRaidScanTick >= 80L;
        if (admissionDue && now >= state.admissionBackoffUntil && !state.admissionInFlight) {
            state.admissionAttempts++;
            scanNonRaidGroups(level, state, now);
            state.lastNonRaidScanTick = now;
        }
        flushBuildGateDiagnostics(level, state, now);
        serviceConstructionIntents(level, state, now, pressured ? Math.max(1, FORMATION_SERVICE_QUOTA / 2) : FORMATION_SERVICE_QUOTA);
        synchronized (state.formations) {
            arbitrateContention(level, state, now);
            List<Map.Entry<UUID, Formation>> scheduled = new ArrayList<>();
            int quota = pressured ? Math.max(1, FORMATION_SERVICE_QUOTA / 2) : FORMATION_SERVICE_QUOTA;
            List<UUID> selectedIds = selectServiceIds(state.serviceRing, state.formations.keySet(), quota);
            for (UUID id : selectedIds) {
                Formation formation = state.formations.get(id);
                if (formation != null) scheduled.add(Map.entry(id, formation));
            }
            Iterator<Map.Entry<UUID, Formation>> iterator = scheduled.iterator();
            int serviced = 0;
            while (iterator.hasNext()) {
                serviced++;
                Map.Entry<UUID, Formation> entry = iterator.next();
                Formation formation = entry.getValue();
                Mob planner = resolvePlannerMob(level, formation);
                if ((planner == null || !planner.isAlive() || planner.isRemoved())
                        && !formation.plannerUnavailableLogged) {
                    formation.plannerUnavailableLogged = true;
                    logFormationState(level, formation, now, "planner_unavailable");
                }
                if (formation.collapsed) {
                    logFormationState(level, formation, now, "release:collapsed");
                    releaseFormation(level, formation);
                    state.formations.remove(entry.getKey());
                    state.serviceRing.remove(entry.getKey());
                    clearConstructionIntent(state, formation.intentToken);
                    continue;
                }
                if (!isPlanValid(level, formation)) {
                    collapse(level, formation, "plan_invalid");
                    state.formations.remove(entry.getKey());
                    state.serviceRing.remove(entry.getKey());
                    clearConstructionIntent(state, formation.intentToken);
                    continue;
                }
                if (formation.phase != formation.lastLoggedPhase) {
                    logFormationState(level, formation, now, "phase_transition");
                    formation.lastLoggedPhase = formation.phase;
                }
                if (formation.phase == Phase.SCAN || formation.phase == Phase.ASSEMBLE) {
                    processRecruitmentReports(level, formation, now);
                    holdRecruiters(level, formation);
                    guardRecruitmentLane(level, formation, now);
                }
                if (formation.phase == Phase.SCAN) {
                    refreshMembers(level, formation, now);
                    logFormationState(level, formation, now, "scan");
                    promotePendingBuilders(level, formation, formation.columnCount);
                    if (formation.members.size() >= formation.columnCount && formation.anchor != null) {
                        transition(formation, Phase.ASSEMBLE, now, "base_ready");
                    } else if (now - formation.lastRecruitmentProgressTick > assemblyTimeoutTicks()
                            || (now - formation.phaseStartTick > assemblyTimeoutTicks() * 2 && formation.members.isEmpty())) {
                        collapse(level, formation, "recruitment_timeout");
                        state.formations.remove(entry.getKey());
                        state.serviceRing.remove(entry.getKey());
                        clearConstructionIntent(state, formation.intentToken);
                        continue;
                    }
                }
                if (formation.phase == Phase.ASSEMBLE) {
                    formation.lastValidationTick = now;
                    int missingLayer = firstMissingStructuralLayer(level, formation);
                    if (missingLayer >= 0) {
                        rebuildAfterStructuralLoss(level, formation, missingLayer);
                        if (formation.collapsed) { state.formations.remove(entry.getKey()); state.serviceRing.remove(entry.getKey()); }
                        continue;
                    }
                    if (formation.assembledSlots > formation.columnCount
                            && !structureHasContact(level, formation, formation.assembledSlots)) {
                        collapse(level, formation, "pillar_contact_lost");
                        iterator.remove();
                        continue;
                    }
                    refreshMembers(level, formation, now);
                    promoteWaitingIntakeBuilders(level, formation, now);
                    if (!formation.pendingBuilderIds.isEmpty()) {
                        int nextLayerLimit = nextLayerLimit(formation);
                        promotePendingBuilders(level, formation,
                                Math.max(0, nextLayerLimit - formation.members.size()));
                    }
                    repinAssemblyWorkers(level, formation);
                    if (assembleFormation(level, formation, now)) {
                        ClearanceBlockage blockage = findFormationBlockage(level, formation, witnessGeometry(formation));
                        if (blockage != null) {
                            formation.collapseDetail = blockage.toString();
                            logSpanClearanceBlockage(level, formation, blockage);
                            recordBuildGate(state(level), "span_rejected:formation_clearance_blocked");
                            collapse(level, formation, "span_clearance_blocked");
                            state.formations.remove(entry.getKey());
                            state.serviceRing.remove(entry.getKey());
                            continue;
                        }
                        if (formation.directSpan) {
                            if (transitionToMovement(level, formation, Phase.ANCHOR, now, "assembly_complete")) {
                                releaseUncommittedRecruiters(level, formation);
                            }
                        } else {
                            releaseUncommittedRecruiters(level, formation);
                            transition(formation, Phase.SETTLE, now, "assembly_complete");
                        }
                    }
                    if (formation.assemblyFatal) {
                        collapse(level, formation, "assemble_slot_failure");
                        state.formations.remove(entry.getKey());
                        state.serviceRing.remove(entry.getKey());
                        clearConstructionIntent(state, formation.intentToken);
                        continue;
                    }
                    long assemblyProgress = Math.max(formation.phaseStartTick,
                            Math.max(formation.lastRecruitmentProgressTick, formation.lastAssemblyProgressTick));
                    boolean hasActiveWorkers = hasActiveAssemblyWorkers(level, formation);
                    if (hasActiveWorkers) {
                        formation.lastRecruitmentProgressTick = now;
                    }
                    if (now - assemblyProgress > assemblyTimeoutTicks()) {
                        collapse(level, formation, formation.members.size() < formation.requiredBodies
                                ? "recruitment_timeout" : "assembly_timeout");
                        state.formations.remove(entry.getKey());
                        state.serviceRing.remove(entry.getKey());
                        clearConstructionIntent(state, formation.intentToken);
                        continue;
                    }
                }
                if (formation.phase == Phase.SETTLE && now - formation.phaseStartTick >= ASSEMBLY_SETTLE_TICKS) {
                    if (transitionToMovement(level, formation, Phase.ROTATE, now, "settled")) {
                        formation.lastTiltProgressTick = now;
                    } else {
                        transition(formation, Phase.ASSEMBLE, now, "full_chain_incomplete");
                    }
                }
                if (formation.phase == Phase.ROTATE || formation.phase == Phase.ANCHOR || formation.phase == Phase.TRAVERSE) {
                    driveFormation(level, formation, now);
                }
                long rotationProgress = formation.phase == Phase.ROTATE
                        ? Math.max(formation.phaseStartTick, formation.lastTiltProgressTick) : formation.phaseStartTick;
                if ((formation.phase == Phase.ROTATE || formation.phase == Phase.ANCHOR)
                        && now - rotationProgress > rotationTimeoutTicks(formation)) {
                    collapse(level, formation, "rotation_timeout");
                    state.formations.remove(entry.getKey());
                    state.serviceRing.remove(entry.getKey());
                    clearConstructionIntent(state, formation.intentToken);
                    continue;
                }
                if (formation.phase == Phase.COLLAPSE) {
                    collapse(level, formation, "recovery");
                    iterator.remove();
                }
            }
        }
    }

    private static void enqueueFormation(PerLevelState state, UUID formationId) {
        if (state != null && formationId != null && !state.serviceRing.contains(formationId)) state.serviceRing.addLast(formationId);
    }

    static List<UUID> selectServiceIds(ArrayDeque<UUID> ring, Set<UUID> liveIds, int quota) {
        if (ring == null || liveIds == null || liveIds.isEmpty() || quota <= 0) {
            if (ring != null) ring.clear();
            return List.of();
        }
        Set<UUID> distinct = new HashSet<>();
        ArrayDeque<UUID> normalized = new ArrayDeque<>();
        for (UUID id : ring) {
            if (id != null && liveIds.contains(id) && distinct.add(id)) normalized.addLast(id);
        }
        List<UUID> missing = liveIds.stream().filter(id -> id != null && distinct.add(id))
                .sorted(Comparator.comparing(UUID::toString)).toList();
        for (UUID id : missing) normalized.addLast(id);
        ring.clear();
        ring.addAll(normalized);
        int limit = Math.min(quota, ring.size());
        List<UUID> selected = new ArrayList<>(limit);
        for (int index = 0; index < limit; index++) selected.add(ring.removeFirst());
        for (UUID id : selected) ring.addLast(id);
        return List.copyOf(selected);
    }

    private static void serviceConstructionIntents(ServerLevel level, PerLevelState state, long now, int quota) {
        if (level == null || state == null || quota <= 0) return;
        List<ConstructionIntent> intents = new ArrayList<>(state.constructionIntents.values());
        intents.sort(Comparator.comparingLong(intent -> intent.createdTick));
        int serviced = 0;
        for (ConstructionIntent intent : intents) {
            if (serviced >= quota) break;
            if (intent == null) continue;
            Formation attachedFormation = intent.attachedFormationId == null ? null : state.formations.get(intent.attachedFormationId);
            if (intent.attachedFormationId != null) {
                if (attachedFormation == null || attachedFormation.collapsed
                        || attachedFormation.plannerId == null || !attachedFormation.plannerId.equals(intent.plannerId)) {
                    clearConstructionIntent(state, intent);
                }
                continue;
            }
            if (intent.recruitmentDeadline < now) {
                clearConstructionIntent(state, intent);
                continue;
            }
            if (now - intent.lastProgressTick > RECRUITMENT_TIMEOUT_TICKS) {
                clearConstructionIntent(state, intent);
                continue;
            }
            if (state.formations.values().stream().anyMatch(formation -> formation != null
                    && formation.plannerId.equals(intent.plannerId))) {
                continue;
            }
            List<Mob> market = boundedEligibleMarket(level, intent, now);
            Mob leader = getMob(level, intent.plannerId);
            if (leader == null || !leader.isAlive() || leader.isRemoved()) {
                clearConstructionIntent(state, intent);
                continue;
            }
            ConstructionRequest request = intent.request;
            if (request == null || request.evidence() == null || request.selectedAnchor() == null
                    || request.threatDestination() == null || request.expiresAt() <= now
                    || !request.selectedAnchor().equals(request.evidence().anchor())) {
                clearConstructionIntent(state, intent);
                continue;
            }
            Formation formation = buildFormation(level, leader, request.selectedAnchor(), now, market, request);
            if (formation == null) {
                clearConstructionIntent(state, intent);
                continue;
            }
            intent.lastProgressTick = now;
            intent.phase = Phase.SCAN;
            if (claimFootprint(state, formation)) {
                state.formations.put(formation.id, formation);
                state.leaderToFormations.computeIfAbsent(leader.getUUID(), ignored -> new HashSet<>()).add(formation.id);
                enqueueFormation(state, formation.id);
                intent.scanRevision = intent.request == null ? -1L : intent.request.revision();
                intent.attachedFormationId = formation.id;
                formation.intentToken = intent;
                syncRouteProbeLedger(formation, intent);
                intent.lastProgressTick = now;
                serviced++;
            } else {
                clearConstructionIntent(state, intent);
            }
        }
    }

    private static List<Mob> boundedEligibleMarket(ServerLevel level, ConstructionIntent intent, long now) {
        if (level == null || intent == null || intent.request == null) return List.of();
        BlockPos center = intent.request.sourceBucket();
        List<Mob> market = new ArrayList<>();
        for (Mob mob : level.getEntitiesOfClass(Mob.class,
                new AABB(Vec3.atBottomCenterOf(center), Vec3.atBottomCenterOf(center)).inflate(RECRUITMENT_RADIUS),
                candidate -> isEligible(candidate) && candidate.isAlive() && !candidate.isRemoved())) {
            UUID owner = state(level).mobOwners.get(mob.getUUID());
            if (owner != null && !owner.equals(intent.plannerId)) continue;
            market.add(mob);
            if (market.size() >= MAX_SCAN_CANDIDATES) break;
        }
        market.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(Vec3.atBottomCenterOf(center))));
        return market;
    }

    private static void arbitrateContention(ServerLevel level, PerLevelState state, long now) {
        if (state.lastContentionTick >= 0L && now - state.lastContentionTick < CONTENTION_INTERVAL_TICKS) return;
        state.lastContentionTick = now;
        List<Formation> contenders = state.formations.values().stream()
                .filter(LivingWallCoordinator::isContentionCandidate).toList();
        Set<UUID> assigned = new HashSet<>();
        for (Formation seed : contenders) {
            if (!assigned.add(seed.id)) continue;
            List<Formation> component = new ArrayList<>();
            ArrayDeque<Formation> pending = new ArrayDeque<>();
            pending.add(seed);
            while (!pending.isEmpty()) {
                Formation current = pending.removeFirst();
                component.add(current);
                for (Formation candidate : contenders) {
                    if (assigned.contains(candidate.id) || !sharesWorkerMarket(current, candidate)) continue;
                    assigned.add(candidate.id);
                    pending.addLast(candidate);
                }
            }
            boolean forcedOverlap = hasForcedOverlap(component);
            if (component.size() < 2 || !forcedOverlap && hasWorkersForAll(level, component)) continue;
            Formation winner = component.stream().max(Comparator.comparingDouble(LivingWallCoordinator::contentionPriority)
                    .thenComparing(formation -> formation.id.toString())).orElse(null);
            if (winner == null) continue;
            for (Formation loser : component) {
                if (loser != winner && isContentionCandidate(loser)) {
                    if (forcedOverlap) {
                        admissionLog(level, state, now, "contention_forced_overlap winner=" + winner.id
                                + ", loser=" + loser.id + ", winnerPivot=" + winner.pivot
                                + ", loserPivot=" + loser.pivot + ", samePlanner="
                                + winner.plannerId.equals(loser.plannerId));
                    }
                    transferFormationToWinner(level, state, loser, winner, now);
                }
            }
        }
    }

    private static boolean hasForcedOverlap(List<Formation> component) {
        for (int firstIndex = 0; firstIndex < component.size(); firstIndex++) {
            Formation first = component.get(firstIndex);
            for (int secondIndex = firstIndex + 1; secondIndex < component.size(); secondIndex++) {
                Formation second = component.get(secondIndex);
                if (samePivotCorridor(first, second)) return true;
            }
        }
        return false;
    }

    private static Formation overlappingPlannerFormation(PerLevelState state, Formation candidate) {
        for (Formation existing : state.formations.values()) {
            if (!isContentionCandidate(existing) || !existing.plannerId.equals(candidate.plannerId)) continue;
            if (samePivotCorridor(existing, candidate)) return existing;
        }
        return null;
    }

    private static Formation dominatingRouteFormation(PerLevelState state, Formation candidate) {
        if (state == null || candidate == null || candidate.request == null || candidate.request.evidence() == null) {
            return null;
        }
        BridgeRouteKey candidateKey = bridgeRouteKey(candidate);
        if (candidateKey == null) return null;
        double candidateUtility = bridgeUtilityScore(candidate);
        for (Formation existing : state.formations.values()) {
            if (existing == null || existing.collapsed || existing.request == null || existing.request.evidence() == null) {
                continue;
            }
            if (!candidateKey.equals(bridgeRouteKey(existing))) continue;
            if (bridgeUtilityScore(existing) + 1.0E-6D >= candidateUtility) {
                return existing;
            }
        }
        return null;
    }

    private static boolean samePivotCorridor(Formation first, Formation second) {
        if (first == null || second == null) return false;
        if (!Objects.equals(first.target, second.target)) return false;
        int pivotDistance = first.pivot.distManhattan(second.pivot);
        if (pivotDistance <= 2) return true;
        return first.anchor.distManhattan(second.anchor) <= 3
                && first.pivot.distManhattan(second.pivot) <= 4;
    }

    private static BridgeRouteKey bridgeRouteKey(Formation formation) {
        if (formation == null || formation.request == null || formation.request.evidence() == null || formation.plan == null) {
            return null;
        }
        BlockPos wallFace = formation.request.evidence().wallFace();
        BlockPos support = formation.request.evidence().supportCell();
        Vec3 delta = Vec3.atBottomCenterOf(formation.plan.spanEnd()).subtract(Vec3.atBottomCenterOf(formation.plan.pivot()));
        return new BridgeRouteKey(
                bucketKey(formation.request.sourceBucket()),
                bucketKey(formation.request.threatBucket()),
                bucketKey(wallFace),
                bucketKey(support),
                formation.plan.scenario(),
                Integer.compare((int) Math.signum(delta.x), 0),
                Integer.compare((int) Math.signum(delta.z), 0));
    }

    private static double bridgeUtilityScore(Formation formation) {
        if (formation == null || formation.request == null || formation.plan == null) return Double.NEGATIVE_INFINITY;
        BlockPos threat = formation.request.threatDestination();
        if (threat == null) return Double.NEGATIVE_INFINITY;
        double pivotDistance = horizontalDistanceSqr(formation.pivot, threat);
        double exitDistance = horizontalDistanceSqr(formation.plan.spanEnd(), threat);
        return (pivotDistance - exitDistance) - bridgeLength(formation.plan.pivot(), formation.plan.spanEnd());
    }

    private static boolean isContentionCandidate(Formation formation) {
        return formation != null && !formation.collapsed && (formation.phase == Phase.SCAN
                || formation.phase == Phase.ASSEMBLE);
    }

    private static boolean sharesWorkerMarket(Formation first, Formation second) {
        if (first == null || second == null) return false;
        double dx = first.pivot.getX() - second.pivot.getX();
        double dz = first.pivot.getZ() - second.pivot.getZ();
        if (dx * dx + dz * dz <= CONTENTION_RADIUS * CONTENTION_RADIUS) return true;
        Set<UUID> firstWorkers = new HashSet<>(first.localRoster);
        firstWorkers.addAll(first.members);
        firstWorkers.addAll(first.pendingBuilderIds);
        firstWorkers.addAll(first.ticketGoals.keySet());
        for (UUID id : second.localRoster) if (firstWorkers.contains(id)) return true;
        for (UUID id : second.members) if (firstWorkers.contains(id)) return true;
        for (UUID id : second.pendingBuilderIds) if (firstWorkers.contains(id)) return true;
        return second.ticketGoals.keySet().stream().anyMatch(firstWorkers::contains);
    }

    private static boolean hasWorkersForAll(ServerLevel level, List<Formation> component) {
        int required = component.stream().mapToInt(formation -> formation.requiredBodies).sum();
        Set<UUID> workers = new HashSet<>();
        for (Formation formation : component) {
            workers.addAll(formation.localRoster);
            workers.addAll(formation.members);
            workers.addAll(formation.pendingBuilderIds);
            workers.addAll(formation.ticketGoals.keySet());
        }
        int available = 0;
        for (UUID id : workers) {
            Mob mob = getMob(level, id);
            if (mob == null || !mob.isAlive() || mob.isRemoved() || !isEligible(mob)) continue;
            if (component.stream().anyMatch(formation -> compatibleGeometry(mob, formation))) available++;
        }
        return available >= required;
    }

    private static double contentionPriority(Formation formation) {
        double completion = formation.assembledSlots / (double) Math.max(1, formation.requiredBodies);
        int remaining = Math.max(0, formation.requiredBodies - formation.members.size() - formation.pendingBuilderIds.size());
        return completion * 10000.0D + formation.assembledSlots * 100.0D - remaining;
    }

    private static void transferFormationToWinner(ServerLevel level, PerLevelState state, Formation loser,
                                                   Formation winner, long now) {
        Set<UUID> workers = new HashSet<>(loser.localRoster);
        workers.addAll(loser.members);
        workers.addAll(loser.pendingBuilderIds);
        workers.addAll(loser.ticketGoals.keySet());
        releaseFormation(level, loser);
        loser.collapsed = true;
        state.formations.remove(loser.id);
        state.serviceRing.remove(loser.id);
        state.contentionCooldowns.put(loser.pivot.asLong(), now + CONTENTION_COOLDOWN_TICKS);
        if (!winner.rosterFrozen && winner.committedExecution == null) {
            for (UUID id : workers) {
                Mob mob = getMob(level, id);
                if (mob == null || !mob.isAlive() || mob.isRemoved() || !isEligible(mob)
                        || !compatibleGeometry(mob, winner)) continue;
                winner.localRoster.add(id);
                winner.transferredWorkerIds.add(id);
            }
        } else if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_FORMATION)) {
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] contention_transfer_deferred bridge={} loser={} workers={} reason=winner_roster_frozen",
                    winner.id, loser.id, workers.size());
        }
        logFormationState(level, winner, now, "contention_won");
    }

    private static Formation buildFormation(ServerLevel level, Mob leader, BlockPos target, long now,
                                            List<Mob> discoveredGroup, ConstructionRequest request) {
        PerLevelState diagnostics = state(level);
        ConstructionIntent intent = leader == null ? null : diagnostics.constructionIntents.get(leader.getUUID());
        if (!isRequestBuildable(request, intent, now)) {
            recordBuildGate(diagnostics, "request_expired");
            return null;
        }
        List<Mob> group = discoveredGroup == null ? new ArrayList<>(MobGroupManager.getFollowers(leader)) : new ArrayList<>(discoveredGroup);
        group.removeIf(mob -> mob == null || !mob.isAlive() || mob.isRemoved()
                || isMobOwnedByAnotherFormation(level, mob, leader.getUUID()));
        if (group.size() < MINIMUM_MEMBERS - 1) {
            group = new ArrayList<>(level.getEntitiesOfClass(Mob.class,
                    leader.getBoundingBox().inflate(RECRUITMENT_RADIUS),
                    mob -> mob != leader && isEligible(mob) && mob.isAlive() && !mob.isRemoved()
                            && !isMobOwnedByAnotherFormation(level, mob, leader.getUUID())));
            group.sort(Comparator.comparingDouble(leader::distanceToSqr));
        }
        if (group.size() > MAX_SCAN_CANDIDATES) group = new ArrayList<>(group.subList(0, MAX_SCAN_CANDIDATES));
        List<Mob> active = group.stream().filter(mob -> mob.isAlive() && !mob.isRemoved()).toList();
        BlockPos selectedAnchor = request.selectedAnchor();
        Vec3 sourceCenter = request.sourceCenter();
        BlockPos requestedPivot = request.corridor() == null ? null : request.corridor().pivot();
        if (requestedPivot != null) {
            Formation formation = buildAtPivot(level, leader, request, requestedPivot, now, active);
            if (formation != null) return formation;
        } else {
            recordBuildGate(diagnostics, "site_rejected:unsafe_pivot");
        }
        recordBuildGate(diagnostics, "all_build_paths_failed");
        return null;
    }

    private static Formation bindDiscoveredRoster(Formation formation, List<Mob> group) {
        if (formation == null) return null;
        List<Mob> compatible = group.stream().filter(mob -> mob.isAlive() && !mob.isRemoved() && compatibleGeometry(mob, formation))
                .sorted(Comparator.comparing(mob -> mob.getUUID().toString())).toList();
        formation.localRoster.clear();
        for (Mob mob : compatible) formation.localRoster.add(mob.getUUID());
        formation.traversalWitness = createTraversalWitness(formation, compatible);
        if (formation.intentToken != null) formation.intentToken.traversalWitness = copyTraversalWitness(formation.traversalWitness);
        return formation;
    }

    private static TraversalCorridorWitness createTraversalWitness(Formation formation, List<Mob> compatible) {
        if (formation == null || formation.plan == null || compatible == null || compatible.isEmpty()) return null;
        BodyGeometry witnessGeometry = clearanceGeometry(compatible);
        if (witnessGeometry == null) return null;
        double halfWidth = Math.max(0.5D, witnessGeometry.width() * 0.5D);
        AABB envelope = null;
        if (requiresTilt(formation.plan.scenario()) && formation.layerCount > 1) {
            for (int movingLayer = 1; movingLayer < formation.layerCount; movingLayer++) {
                for (int sample = 0; sample < TILT_PREFLIGHT_PROGRESS.length; sample++) {
                    double movementProgress = TILT_PREFLIGHT_PROGRESS[sample];
                    for (int slot = 0; slot < Math.max(1, formation.requiredBodies); slot++) {
                        Vec3 position = bottomUpChainPosition(formation, slot, movingLayer, movementProgress);
                        AABB box = new AABB(position.x - halfWidth, position.y, position.z - halfWidth,
                                position.x + halfWidth, position.y + witnessGeometry.height(), position.z + halfWidth);
                        envelope = envelope == null ? box : envelope.minmax(box);
                    }
                }
            }
        } else {
            for (int slot = 0; slot < Math.max(1, formation.requiredBodies); slot++) {
                Vec3 position = formationSlotPosition(formation, slot, 0.0D);
                AABB box = new AABB(position.x - halfWidth, position.y, position.z - halfWidth,
                        position.x + halfWidth, position.y + witnessGeometry.height(), position.z + halfWidth);
                envelope = envelope == null ? box : envelope.minmax(box);
            }
        }
        String corridorId = formation.plan.corridor() == null ? "" : formation.plan.corridor().corridorId();
        return TraversalCorridorWitness.create(compatible.get(0).level().dimension().identifier(), corridorId, compatible, envelope);
    }

    public static boolean guardSafeBaseNavigation(Mob mob) {
        if (mob == null || !(mob.level() instanceof ServerLevel level) || level.isClientSide()
                || level.getServer() == null || !level.getServer().isSameThread()) return true;
        Formation formation = getFormation(mob);
        if (formation == null) {
            holdSafeBaseNavigation(mob);
            return false;
        }
        SafeBaseNavigationReservation reservation = formation.safeBaseNavigation.get(mob.getUUID());
        if (reservation == null) {
            com.example.soundattract.component.LivingBridgeEntityData.get(mob).remove("livingBridgeSafeBaseRouteActive");
            holdSafeBaseNavigation(mob);
            return false;
        }
        if (!safeBaseRouteCertificateValid(level, mob, formation, reservation)) {
            formation.terminalSafeBaseFailures.add(mob.getUUID());
            formation.safeBaseNavigation.remove(mob.getUUID());
            formation.pendingSafeBaseReplans.remove(mob.getUUID());
            holdSafeBaseNavigation(mob);
            logSafeBaseRouteOutcome(level, formation, mob, reservation, "certificate_rejected",
                    new LivingBridgeSafeBaseRoute.Result(
                            LivingBridgeSafeBaseRoute.Rejection.CERTIFICATE_INVALID, mob.blockPosition()));
            return false;
        }
        if (formation.activeMemberSuctionEngaged) {
            holdSafeBaseNavigation(mob);
            return false;
        }
        Path path = mob.getNavigation().getPath();
        if (path == null || path.isDone()) {
            holdSafeBaseNavigation(mob);
            if (mob.position().distanceToSqr(Vec3.atBottomCenterOf(reservation.staging())) > 0.16D) {
                formation.terminalSafeBaseFailures.add(mob.getUUID());
            }
            return false;
        }
        if (!pathMatchesCertificate(path, reservation.pathNodes())) {
            formation.terminalSafeBaseFailures.add(mob.getUUID());
            formation.safeBaseNavigation.remove(mob.getUUID());
            holdSafeBaseNavigation(mob);
            return false;
        }
        int next = path.getNextNodeIndex();
        Vec3 from = mob.position();
        int checked = 0;
        while (next < path.getNodeCount() && checked < 2) {
            Vec3 to = Vec3.atBottomCenterOf(path.getNode(next).asBlockPos());
            LivingBridgeSafeBaseRoute.Result edge = safeBaseNavigationEdgeResult(
                    level, mob, formation, reservation, from, to);
            if (!edge.safe()) {
                logSafeBaseRouteOutcome(level, formation, mob, reservation, "navigation_rejected", edge);
                holdSafeBaseNavigation(mob);
                formation.safeBaseNavigation.remove(mob.getUUID());
                Set<BlockPos> rejected = formation.rejectedSafeBaseStaging.computeIfAbsent(
                        mob.getUUID(), ignored -> new HashSet<>());
                if (rejected.size() < 8) rejected.add(reservation.staging());
                formation.terminalSafeBaseFailures.add(mob.getUUID());
                return false;
            }
            from = to;
            next++;
            checked++;
        }
        if (checked > 0) return true;
        holdSafeBaseNavigation(mob);
        formation.terminalSafeBaseFailures.add(mob.getUUID());
        return false;
    }

    private static void holdSafeBaseNavigation(Mob mob) {
        if (mob == null) return;
        mob.getNavigation().stop();
        mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.0D);
        Vec3 velocity = mob.getDeltaMovement();
        mob.setDeltaMovement(0.0D, velocity.y, 0.0D);
    }

    private static boolean safeBaseRouteCertificateValid(ServerLevel level, Mob mob, Formation formation,
                                                          SafeBaseNavigationReservation reservation) {
        if (level == null || mob == null || formation == null || reservation == null
                || reservation.formationId() == null || !reservation.formationId().equals(formation.id)
                || !safeBaseBuilderAuthorization(level, mob, formation, reservation.memberIndex(),
                reservation.structuralSlot(), reservation.routeRevision(), reservation.phase())
                || !mob.getUUID().equals(reservation.ownerId())
                || reservation.routeRevision() != formation.request.revision()
                || reservation.dimension() == null || !reservation.dimension().equals(level.dimension().identifier())
                || reservation.certifiedStart() == null || reservation.certifiedBody() == null
                || reservation.certifiedPose() == null || reservation.certifiedPath() == null
                || reservation.worldFingerprint().isBlank() || mob.getPose() != reservation.certifiedPose()
                || Math.abs(mob.getBbWidth() - reservation.certifiedBody().getXsize()) > 1.0E-4D
                || Math.abs(mob.getBbHeight() - reservation.certifiedBody().getYsize()) > 1.0E-4D
                || !Objects.equals(reservation.worldFingerprint(), routeWorldFingerprint(level, reservation.pathNodes()))) {
            return false;
        }
        boolean atStart = mob.position().distanceToSqr(reservation.certifiedStart()) <= 0.04D;
        if (atStart && !sameBodyEnvelope(mob.getBoundingBox(), reservation.certifiedBody(), 0.20D)) return false;
        int progress = routeProgressIndex(mob.position(), reservation.pathNodes(), reservation.staging(),
                reservation.certifiedStart());
        if (!atStart && progress < 0) return false;
        int previous = formation.safeBaseRouteProgress.getOrDefault(mob.getUUID(), -1);
        if (progress >= 0 && previous > progress) return false;
        if (progress >= 0) formation.safeBaseRouteProgress.put(mob.getUUID(), progress);
        return true;
    }

    private static boolean pathMatchesCertificate(Path path, List<BlockPos> nodes) {
        if (path == null || nodes == null || nodes.isEmpty() || path.getNodeCount() != nodes.size()) return false;
        for (int index = 0; index < nodes.size(); index++) {
            if (!nodes.get(index).equals(path.getNode(index).asBlockPos())) return false;
        }
        return true;
    }

    private static boolean sameBodyEnvelope(AABB first, AABB second, double tolerance) {
        if (first == null || second == null || !Double.isFinite(tolerance) || tolerance < 0.0D) return false;
        return Math.abs(first.minX - second.minX) <= tolerance
                && Math.abs(first.minY - second.minY) <= tolerance
                && Math.abs(first.minZ - second.minZ) <= tolerance
                && Math.abs(first.maxX - second.maxX) <= tolerance
                && Math.abs(first.maxY - second.maxY) <= tolerance
                && Math.abs(first.maxZ - second.maxZ) <= tolerance;
    }

    private static boolean isSafeBaseNavigationEdge(ServerLevel level, Mob mob, Formation formation,
                                                     SafeBaseNavigationReservation reservation,
                                                     Vec3 from, Vec3 to) {
        return safeBaseNavigationEdgeResult(level, mob, formation, reservation, from, to).safe();
    }

    private static LivingBridgeSafeBaseRoute.Result safeBaseNavigationEdgeResult(
            ServerLevel level, Mob mob, Formation formation, SafeBaseNavigationReservation reservation,
            Vec3 from, Vec3 to) {
        if (level == null || mob == null || formation == null || reservation == null || from == null || to == null
                || level.getServer() == null || !level.getServer().isSameThread()) {
            return new LivingBridgeSafeBaseRoute.Result(LivingBridgeSafeBaseRoute.Rejection.INVALID_FRAME, null);
        }
        if (!safeBaseReservationBoundToBuilder(level, mob, formation, reservation)) {
            return new LivingBridgeSafeBaseRoute.Result(LivingBridgeSafeBaseRoute.Rejection.CERTIFICATE_INVALID, null);
        }
        Vec3 delta = to.subtract(from);
        int subdivisions = LivingBridgeSafeBaseRoute.subdivisions(delta.length());
        if (subdivisions > LivingBridgeSafeBaseRoute.MAX_SWEEP_SUBDIVISIONS) {
            return new LivingBridgeSafeBaseRoute.Result(
                    LivingBridgeSafeBaseRoute.Rejection.SWEEP_BUDGET_EXCEEDED, BlockPos.containing(to));
        }
        for (int sample = 0; sample <= subdivisions; sample++) {
            Vec3 position = from.lerp(to, sample / (double) subdivisions);
            AABB body = mob.getDimensions(mob.getPose()).makeBoundingBox(position);
            LivingBridgeSafeBaseRoute.Result footing = safeBaseFootingResult(level, BlockPos.containing(position));
            if (!footing.safe()) return footing;
            LivingBridgeSafeBaseRoute.Result sweep = structuralSweepResult(level, mob, body);
            if (!sweep.safe()) return sweep;
        }
        return new LivingBridgeSafeBaseRoute.Result(LivingBridgeSafeBaseRoute.Rejection.NONE, null);
    }

    private static void logSafeBaseRouteOutcome(ServerLevel level, Formation formation, Mob mob,
                                                 SafeBaseNavigationReservation reservation, String phase,
                                                 LivingBridgeSafeBaseRoute.Result result) {
        if (level == null || formation == null || mob == null || reservation == null || result == null) return;
        SoundAttractMod.LOGGER.info("[LivingBridgeSafeBaseRoute] outcome dimension={} formation={} owner={} phase={} revision={} rejection={} firstUnsafeCell={} staging={}",
                level.dimension().identifier(), formation.id, mob.getUUID(), phase, reservation.routeRevision(),
                result.rejection(), result.firstUnsafeCell(), reservation.staging());
    }

    private record SafeBaseNavigationReservation(UUID formationId, UUID ownerId, int memberIndex, int structuralSlot,
                                                  Phase phase, long routeRevision, Vec3 pivot,
                                                  Vec3 forward, Vec3 sourceCenter, BlockPos sourceBucket,
                                                  BlockPos staging, List<BlockPos> pathNodes,
                                                  Path certifiedPath, Identifier dimension,
                                                  Vec3 certifiedStart, AABB certifiedBody, Pose certifiedPose,
                                                  String worldFingerprint) {
        SafeBaseNavigationReservation {
            pathNodes = List.copyOf(pathNodes);
            certifiedStart = certifiedStart == null ? null : new Vec3(certifiedStart.x, certifiedStart.y, certifiedStart.z);
            certifiedBody = certifiedBody == null ? null : new AABB(certifiedBody.minX, certifiedBody.minY,
                    certifiedBody.minZ, certifiedBody.maxX, certifiedBody.maxY, certifiedBody.maxZ);
            worldFingerprint = worldFingerprint == null ? "" : worldFingerprint;
        }

    }

    private static boolean safeBaseBuilderAuthorization(ServerLevel level, Mob mob, Formation formation,
                                                         int memberIndex, int structuralSlot,
                                                         long requestRevision, Phase routePhase) {
        if (level == null || mob == null || formation == null || formation.request == null) return false;
        PerLevelState levelState = state(level);
        UUID ownerId = mob.getUUID();
        UUID memberId = memberIndex >= 0 && memberIndex < formation.members.size()
                ? formation.members.get(memberIndex) : null;
        return safeBaseAuthorizationPolicy(mob.isAlive() && !mob.isRemoved() && mob.level() == level,
                level.getServer() != null && level.getServer().isSameThread(), ownerId, formation.id,
                formation.id, formation.phase, routePhase, requestRevision, formation.request.revision(),
                memberIndex, structuralSlot, formation.assembledSlots, formation.activeStructuralSlot,
                memberId, formation.structuralBuilderIds.contains(ownerId), levelState.mobOwners.get(ownerId));
    }

    static boolean safeBaseAuthorizationPolicy(boolean live, boolean serverThread, UUID ownerId,
                                               UUID formationId, UUID currentFormationId, Phase phase,
                                               Phase routePhase, long revision, long currentRevision,
                                               int memberIndex, int structuralSlot, int assembledSlots,
                                               int activeStructuralSlot, UUID memberId, boolean structuralBuilder,
                                               UUID mobOwnerFormationId) {
        return live && serverThread && ownerId != null && ownerId.equals(memberId)
                && formationId != null && formationId.equals(currentFormationId)
                && formationId.equals(mobOwnerFormationId)
                && phase == Phase.ASSEMBLE && routePhase == Phase.ASSEMBLE
                && revision == currentRevision && memberIndex >= 0
                && memberIndex == structuralSlot && memberIndex == assembledSlots
                && memberIndex == activeStructuralSlot && structuralBuilder;
    }

    private static boolean safeBaseReservationBoundToBuilder(ServerLevel level, Mob mob, Formation formation,
                                                              SafeBaseNavigationReservation reservation) {
        return reservation != null && mob != null && mob.getUUID().equals(reservation.ownerId())
                && reservation.formationId() != null && reservation.formationId().equals(formation.id)
                && reservation.dimension() != null
                && reservation.dimension().equals(level.dimension().identifier())
                && safeBaseBuilderAuthorization(level, mob, formation, reservation.memberIndex(),
                reservation.structuralSlot(), reservation.routeRevision(), reservation.phase());
    }

    private record StructuralApproachReservation(UUID formationId, UUID ownerId, long routeRevision,
                                                  List<BlockPos> corridor, double bodyWidth, long heartbeatTick) {
        StructuralApproachReservation {
            corridor = List.copyOf(corridor);
        }
    }

    private static PotentialSupplySnapshot potentialSupplySnapshot(ServerLevel level, Mob leader,
                                                                      Formation formation,
                                                                      List<Mob> discoveredRoster, long now) {
        if (level == null || leader == null || formation == null || formation.pivot == null) {
            return new PotentialSupplySnapshot(0, 0, 0, 0, 0, 0, List.of());
        }
        Set<UUID> discoveredIds = new HashSet<>();
        if (discoveredRoster != null) {
            for (Mob mob : discoveredRoster) {
                if (mob != null) discoveredIds.add(mob.getUUID());
            }
        }

        Vec3 pivot = Vec3.atBottomCenterOf(formation.pivot);
        double horizontal = LivingWallConfig.MAX_GAP.get() + LOCAL_ROSTER_MARGIN;
        double vertical = LivingWallConfig.MAX_HEIGHT.get() + 2.0D;
        AABB pivotArea = new AABB(pivot, pivot).inflate(horizontal, vertical, horizontal);
        Map<UUID, Mob> nearby = new LinkedHashMap<>();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, pivotArea, candidate -> true)) {
            if (Math.abs(mob.getY() - pivot.y) <= vertical) nearby.putIfAbsent(mob.getUUID(), mob);
        }
        Vec3 sourceCenter = formation.request == null ? null : formation.request.sourceCenter();
        if (sourceCenter != null) {
            AABB sourceArea = new AABB(sourceCenter, sourceCenter).inflate(ACTIVE_ASSEMBLY_MAX_RECRUITMENT_DISTANCE);
            for (Mob mob : level.getEntitiesOfClass(Mob.class, sourceArea, candidate -> true)) {
                nearby.putIfAbsent(mob.getUUID(), mob);
            }
        }

        UUID leaderId = leader.getUUID();
        List<Mob> eligibleAlive = nearby.values().stream()
                .filter(mob -> !mob.getUUID().equals(leaderId) && mob.isAlive() && !mob.isRemoved() && isEligible(mob))
                .toList();
        PerLevelState state = state(level);
        List<Mob> unclaimed = eligibleAlive.stream().filter(mob -> {
            UUID id = mob.getUUID();
            UUID owner = state.mobOwners.get(id);
            return !isControlled(mob) && !isBeyondAnchor(formation, mob)
                    && !formation.members.contains(id) && !formation.pendingBuilderIds.contains(id)
                    && !formation.ticketGoals.containsKey(id) && !formation.claimedWorkerIds.contains(id)
                    && !formation.builderReservations.contains(id) && !formation.reserved.contains(id)
                    && !formation.transitCrosserIds.contains(id)
                    && (owner == null || formation.id.equals(owner));
        }).toList();
        List<Mob> geometryCompatible = unclaimed.stream()
                .filter(mob -> compatibleGeometry(mob, formation))
                .filter(mob -> mob.distanceToSqr(pivot) <= ACTIVE_ASSEMBLY_MAX_RECRUITMENT_DISTANCE * ACTIVE_ASSEMBLY_MAX_RECRUITMENT_DISTANCE)
                .filter(mob -> {
                    if (formation.plan == null || formation.plan.scenario() == null) return true;
                    if (formation.plan.scenario() == ConstructionScenario.DROP_TO_LEDGE) {
                        return mob.getY() >= pivot.y - 1.5D;
                    }
                    return mob.getY() >= pivot.y - 3.0D;
                })
                .toList();
        List<Mob> pathFreePotential = geometryCompatible.stream()
                .filter(mob -> !formation.recruitmentBlacklist.containsKey(mob.getUUID())
                        || formation.recruitmentBlacklist.get(mob.getUUID()) <= now)
                .filter(mob -> {
                    BasinEvacuation evacuation = state.basinEvacuations.get(mob.getUUID());
                    return evacuation == null || evacuation.expiresTick() <= now;
                })
                .sorted(Comparator.comparingInt((Mob mob) -> recruitmentElevationOrder(formation.pivot.getY(), mob.getY()))
                        .thenComparing(mob -> !formation.transferredWorkerIds.contains(mob.getUUID()))
                        .thenComparingDouble(mob -> distanceToNearestAssemblyCell(mob, formation))
                        .thenComparing(mob -> mob.getUUID().toString()))
                .toList();
        List<Mob> bounded = pathFreePotential.size() > MAX_SCAN_CANDIDATES
                ? pathFreePotential.subList(0, MAX_SCAN_CANDIDATES) : pathFreePotential;
        return new PotentialSupplySnapshot(discoveredIds.size(), nearby.size(), eligibleAlive.size(),
                unclaimed.size(), geometryCompatible.size(), pathFreePotential.size(), bounded);
    }

    private static void logPotentialSupply(ServerLevel level, Formation formation, PotentialSupplySnapshot snapshot,
                                           int configuredLimit, int effectiveLimit, int upperBound, String outcome) {
        if (level == null || formation == null || snapshot == null || SoundAttractConfig.COMMON == null
                || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ADMISSION)) return;
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] potential_supply outcome={} pivot={} scenario={} required={} configured_limit={} effective_limit={} discovered={} nearby_union={} eligible_alive={} unclaimed={} geometry_compatible={} path_free_potential={} potential_supply_upper_bound={}",
                outcome, formation.pivot, formation.plan == null ? null : formation.plan.scenario(),
                formation.requiredBodies, configuredLimit, effectiveLimit, snapshot.discovered(), snapshot.nearbyUnion(),
                snapshot.eligibleAlive(), snapshot.unclaimed(), snapshot.geometryCompatible(),
                snapshot.pathFreePotential(), upperBound);
    }

    private static void refreshTraversalWitness(ServerLevel level, Formation formation) {
        if (level == null || formation == null) return;
        List<Mob> roster = new ArrayList<>();
        for (UUID id : formation.localRoster) {
            Mob mob = getMob(level, id);

            if (mob == null || !mob.isAlive() || mob.isRemoved() || !compatibleGeometry(mob, formation)) return;
            roster.add(mob);
        }
        if (roster.isEmpty()) return;
        TraversalCorridorWitness witness = createTraversalWitness(formation, roster);
        if (witness != null) {
            formation.traversalWitness = witness;
            if (formation.intentToken != null) formation.intentToken.traversalWitness = witness.copy();
        }
    }

    private static TraversalCorridorWitness copyTraversalWitness(TraversalCorridorWitness witness) {
        return witness == null ? null : witness.copy();
    }

    private static Set<UUID> witnessRoster(Formation formation) {
        Set<UUID> roster = new LinkedHashSet<>();
        if (formation == null) return roster;
        roster.addAll(formation.localRoster);
        return roster;
    }

    private static BodyGeometry witnessGeometry(Formation formation) {
        if (formation == null || formation.traversalWitness == null) return null;
        double width = formation.traversalWitness.maxWidth();
        double height = formation.traversalWitness.maxHeight();
        return width > 0.0D && height > 0.0D ? new BodyGeometry(width, height) : null;
    }

    private static Formation buildAtPivot(ServerLevel level, Mob leader, ConstructionRequest request, BlockPos pivot,
                                          long now, List<Mob> discoveredRoster) {
        PerLevelState diagnostics = state(level);
        if (!isSafeSite(level, pivot) && !isValidatedDropEdgePivot(level, pivot, request)) {
            recordBuildGate(diagnostics, "pivot_not_safe_site");
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_CONSTRUCTION)) {
                BlockState pivotState = level.getBlockState(pivot);
                BlockState aboveState = level.getBlockState(pivot.above());
                BlockState supportState = level.getBlockState(pivot.below());
                SoundAttractMod.LOGGER.info("[LivingWallCoordinator] pivot_not_safe_site_detail pivot={} loaded={} aboveLoaded={} belowLoaded={} unsafeColumn={} supportStable={} pivotClear={} aboveClear={} pivotState={} aboveState={} supportState={}",
                        pivot, level.isLoaded(pivot), level.isLoaded(pivot.above()), level.isLoaded(pivot.below()),
                        isUnsafeColumn(level, pivot), hasStableSupport(level, pivot.below(), supportState),
                        pivotState.getCollisionShape(level, pivot).isEmpty(),
                        aboveState.getCollisionShape(level, pivot.above()).isEmpty(),
                        pivotState, aboveState, supportState);
            }
            return null;
        }
        if (request == null || request.evidence() == null) {
            recordBuildGate(diagnostics, "profile_evidence_missing");
            return null;
        }
        WallProfileScanner.TargetEvidence evidence = request.evidence();
        BlockPos selectedAnchor = request.selectedAnchor();
        BlockPos threatDestination = request.threatDestination();
        String lineageReason = activeRequestPlanLineageReason(level, request);
        if (lineageReason != null) {
            recordProfileEvidenceRejection(level, request, pivot, null, lineageReason);
            return null;
        }
        String admissionReason = requestEvidenceAdmissionReason(level, pivot, request);
        if (admissionReason != null) {
            recordProfileEvidenceRejection(level, request, pivot, null, admissionReason);
            return null;
        }
        ConstructionIntent intent = diagnostics.constructionIntents.get(leader.getUUID());
        boolean intentAlive = intent != null && intent.recruitmentDeadline >= now;
        if (!intentAlive && request.expiresAt() <= now) {
            recordBuildGate(diagnostics, "request_expired");
            return null;
        }
        if (request.revision() < 0L) {
            recordProfileEvidenceRejection(level, request, pivot, null, "route_revision_expired");
            return null;
        }
        if (!snapshotAdmissible(level, request, now)) {
            recordBuildGate(diagnostics, "snapshot_invalid");
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_CONSTRUCTION)) {
                SoundAttractMod.LOGGER.info("[LivingWallCoordinator] snapshot_invalid_detail reason={} now={} requestExpiresAt={} scanExpiresAt={} snapshot={} evidenceAnchor={} threat={} selectedAnchor={}",
                        snapshotAdmissionReason(level, request, now), now, request.expiresAt(), request.scanExpiresAt(),
                        request.snapshot() == null ? null : request.snapshot().identity(),
                        evidence.anchor(), threatDestination, selectedAnchor);
            }
            return null;
        }
        WallProfileScanner.Profile profile = profileFromAdmissionEvidence(pivot, evidence);
        if (profile == null) {
            recordBuildGate(diagnostics, "profile_scan_missing");
            return null;
        }
        if (!profileDirectionCompatible(pivot, selectedAnchor, profile.anchor())) {
            recordBuildGate(diagnostics, "profile_direction_mismatch");
            return null;
        }
        List<BlockPos> carriedHazards = carriedRayHazardsFromEvidence(evidence, pivot, evidence.wallFace());
        if (!carriedHazards.isEmpty() && !validateCarriedHazards(level, new java.util.LinkedHashSet<>(carriedHazards))) {
            recordBuildGate(diagnostics, "profile_hazard_mismatch");
            return null;
        }
        int rawRise = Math.abs(profile.anchor().getY() - pivot.getY());
        if (rawRise > LivingWallConfig.MAX_HEIGHT.get()) {
            recordBuildGate(diagnostics, "profile_height_exceeded");
            return null;
        }
        int pillarHeightLimit = LivingWallConfig.MAX_HEIGHT.get();
        if (bridgeLength(pivot, profile.anchor()) < 1.5D) {
            recordBuildGate(diagnostics, "profile_span_too_short");
            return null;
        }
        BodyGeometry geometry = geometryFor(leader);
        ConstructionObservation observation = request.observation();
        int planningCapacity = Math.max(MINIMUM_MEMBERS, LivingWallConfig.MAX_BODIES.get());
        SitePlan plan = planConstruction(level, pivot, profile.anchor(), threatDestination, 2, request, evidence, geometry, observation);
        if (plan == null) {
            recordBuildGate(diagnostics, "two_column_plan_null");
            return null;
        }
        if (!java.util.Objects.equals(evidence.scenario(), plan.scenario().name())) {
            recordBuildGate(diagnostics, "profile_scenario_mismatch");
            return null;
        }
        AdmissionPlanningInput admission = admissionPlanningInput(plan, geometry, planningCapacity, now);
        if (admission.requiredBodies() > admission.bodyLimit()) {
            recordBuildGate(diagnostics, "probe_headcount_exceeded");
            return null;
        }
        ExactSiteKey siteKey = exactSiteKey(pivot, profile.anchor(), plan.spanEnd(), plan.scenario(), plan.hazardCells());
        trimExactSiteCooldowns(level, now);
        if (isTerrainRejected(level, siteKey, now)) {
            recordBuildGate(diagnostics, "terrain_cooldown");
            return null;
        }
        if (isContentionCooling(level, pivot, now)) {
            recordBuildGate(diagnostics, "contention_cooldown");
            return null;
        }
        List<SitePlan> candidates = constructionPlans(level, pivot, profile.anchor(), threatDestination, admission.columnCount(),
                request, evidence, geometry, observation);
        if (candidates.isEmpty()) {
            recordBuildGate(diagnostics, "candidate_plans_empty");
            return null;
        }
        SourceStagingBudget admissionBudget = new SourceStagingBudget();
        for (SitePlan candidate : candidates) {
            String sourceAdmissionReason = finalizedSourceAdmissionReason(candidate, request.sourceCenter(), request.sourceBucket());
            if (sourceAdmissionReason != null) {
                recordProfileEvidenceRejection(level, request, candidate.pivot(), candidate, sourceAdmissionReason);
                continue;
            }
            if (!acceptedPlanMatchesRequest(level, candidate, request)) {
                recordBuildGate(diagnostics, "candidate_frame_invalid");
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_CONSTRUCTION)) {
                    SoundAttractMod.LOGGER.info("[LivingWallCoordinator] candidate_frame_invalid_detail pivot={} planAnchor={} requestAnchor={} planScenario={} evidenceScenario={} planCorridor={} requestCorridor={} planSpanEnd={} evidenceInvasion={} threat={} hazards={} planHazards={}",
                            candidate.pivot(), candidate.anchor(), request.selectedAnchor(), candidate.scenario(),
                            request.evidence().scenario(), candidate.corridor() == null ? null : candidate.corridor().corridorId(),
                            request.corridor() == null ? null : request.corridor().corridorId(), candidate.spanEnd(),
                            evidenceInvasionSurface(request.evidence()), request.threatDestination(),
                            request.evidence().hazardCells().size(), candidate.hazardCells().size());
                    SoundAttractMod.LOGGER.info("[LivingWallCoordinator] candidate_frame_tactical_detail profileEvidence={} anchorGoal={} spanInvasion={} corridorEvidence={} threatFacingAnchor={} threatFacingSpan={} endCloser={} wallProjection={} spanProjection={} support={} wallFace={} rawHazards={} hazardsCanonical={}",
                            hasProfileCorridorEvidence(request.evidence()),
                            Objects.equals(candidate.anchor(), tacticalGoalForEvidence(request.evidence())),
                            Objects.equals(candidate.spanEnd(), evidenceInvasionSurface(request.evidence())),
                            committedCorridorMatchesEvidence(candidate, request.evidence()),
                            hasThreatProgressingSpan(candidate.scenario(), candidate.pivot(), candidate.anchor(), request.threatDestination()),
                            hasThreatProgressingSpan(candidate.scenario(), candidate.pivot(), candidate.spanEnd(), request.threatDestination()),
                            horizontalDistanceSqr(candidate.spanEnd(), request.threatDestination()) < horizontalDistanceSqr(candidate.pivot(), request.threatDestination()),
                            WallProfileScanner.threatProgressProjection(candidate.pivot(), request.evidence().wallFace(), request.threatDestination()),
                            WallProfileScanner.threatProgressProjection(candidate.pivot(), evidenceInvasionSurface(request.evidence()), request.threatDestination()),
                            request.evidence().supportCell(), request.evidence().wallFace(), request.evidence().hazardCells(), canonicalHazardCells(request.evidence().hazardCells(), candidate.pivot(), candidate.anchor()).size());
                    SoundAttractMod.LOGGER.info("[LivingWallCoordinator] candidate_frame_utility_detail planHazardsEqual={} requestAdmission={} supportProjection={} wallProjection={} spanProjection={} pivotDistance={} spanDistance={}",
                            canonicalHazardCells(request.evidence().hazardCells(), candidate.pivot(), candidate.anchor()).equals(candidate.hazardCells()),
                            requestEvidenceAdmissionReason(level, candidate.pivot(), request),
                            WallProfileScanner.threatProgressProjection(candidate.pivot(), request.evidence().supportCell(), request.threatDestination()),
                            WallProfileScanner.threatProgressProjection(candidate.pivot(), request.evidence().wallFace(), request.threatDestination()),
                            WallProfileScanner.threatProgressProjection(candidate.pivot(), evidenceInvasionSurface(request.evidence()), request.threatDestination()),
                            horizontalDistanceSqr(candidate.pivot(), request.threatDestination()),
                            horizontalDistanceSqr(candidate.spanEnd(), request.threatDestination()));
                    Vec3 expectedForward = candidate.corridor().traversalForward();
                    SoundAttractMod.LOGGER.info("[LivingWallCoordinator] candidate_frame_forward_detail planForward={} expectedForward={} scenario={} anchor={} spanEnd={}",
                            candidate.forward(), expectedForward, candidate.scenario(), candidate.anchor(), candidate.spanEnd());
                    Vec3 rawExpected = Vec3.atBottomCenterOf(candidate.spanEnd()).subtract(Vec3.atBottomCenterOf(candidate.pivot()));
                    double rawLength = Math.sqrt(rawExpected.x * rawExpected.x + rawExpected.z * rawExpected.z);
                    SoundAttractMod.LOGGER.info("[LivingWallCoordinator] candidate_frame_final_detail corridor={} admission={} scenario={} hazards={} committed={} forward={} utility={}",
                            candidate.corridor() != null && request.corridor() != null && Objects.equals(candidate.corridor().corridorId(), request.corridor().corridorId()),
                            requestEvidenceAdmissionReason(level, candidate.pivot(), request) == null,
                            candidate.scenario() == scenarioFromEvidence(request.evidence(), hazardsWithinTransition(request.evidence().hazardCells(), candidate.pivot(), candidate.anchor())),
                            canonicalHazardCells(request.evidence().hazardCells(), candidate.pivot(), candidate.anchor()).equals(candidate.hazardCells()),
                            committedCorridorMatchesEvidence(candidate, request.evidence()),
                            rawLength >= 0.001D && Math.abs(candidate.forward().x - rawExpected.x / rawLength) < 1.0E-6D && Math.abs(candidate.forward().z - rawExpected.z / rawLength) < 1.0E-6D,
                            candidateHasTacticalUtility(level, candidate.pivot(), candidate.anchor(), candidate.spanEnd(), request.threatDestination(), request.evidence(), candidate.scenario()));
                }
                continue;
            }
            Formation formation = new Formation(leader, selectedAnchor, candidate, request,
                    pillarHeightLimit, profile.gapWidth(), planningCapacity, geometry, now);
            if (!selectSourceStagingOffset(level, formation, leader, admissionBudget)) {
                recordBuildGate(diagnostics, "source_staging_unavailable");
                continue;
            }
            PotentialSupplySnapshot potential = potentialSupplySnapshot(level, leader, formation, discoveredRoster, now);
            bindDiscoveredRoster(formation, potential.candidates());
            int configuredLimit = LivingWallConfig.MAX_BODIES.get();
            int effectiveLimit = Math.min(Math.min(Math.max(MINIMUM_MEMBERS, configuredLimit),
                    formation.bodyLimit), MAX_SCAN_CANDIDATES);
            int potentialSupplyUpperBound = Math.min(effectiveLimit, potential.pathFreePotential());
            String headcountOutcome = formation.requiredBodies > formation.bodyLimit
                    ? "candidate_headcount_exceeded"
                    : formation.requiredBodies > potentialSupplyUpperBound ? "cohort_headcount_insufficient" : "potential_supply_sufficient";
            logPotentialSupply(level, formation, potential, configuredLimit, effectiveLimit,
                    potentialSupplyUpperBound, headcountOutcome);
            if (formation.requiredBodies > formation.bodyLimit) {
                recordBuildGate(diagnostics, "candidate_headcount_exceeded");
                continue;
            }
            if (formation.requiredBodies > potentialSupplyUpperBound) {
                recordBuildGate(diagnostics, "cohort_headcount_insufficient");
                continue;
            }
            if (requiresTilt(formation.plan.scenario()) && !isUsablePillarStep(formation.pillarStep)) {
                recordBuildGate(diagnostics, "candidate_pillar_step_invalid");
                continue;
            }
            ClearanceBlockage candidateBlockage = findFormationBlockage(level, formation, geometry);
            if (candidateBlockage != null) {
                if (tryRepairScanAdmission(level, formation, geometry, leader, candidateBlockage, admissionBudget)) {
                    SoundAttractMod.LOGGER.info("[LivingWallCoordinator] route_repair_selected phase=SCAN bridge={} blocker={} slot={} validations={} offsets={} deflections={}",
                            formation.id, candidateBlockage.blocker(), candidateBlockage.slot(),
                            admissionBudget.fullFormationValidations, admissionBudget.sourceOffsetEvaluations,
                            admissionBudget.deflectionCandidateEvaluations);
                    candidateBlockage = findFormationBlockage(level, formation, geometry);
                }
            }
            if (candidateBlockage != null) {
                logSpanClearanceBlockage(level, formation, candidateBlockage);
                recordBuildGate(diagnostics, "candidate_clearance_blocked");
                continue;
            }
            if (!commitAcceptedPlanIntent(level, intent, formation, pivot, now)) {
                recordBuildGate(diagnostics, "accepted_intent_commit_failed");
                continue;
            }
            recordBuildGate(diagnostics, "accepted");
            return formation;
        }
        recordBuildGate(diagnostics, "all_candidate_plans_rejected");
        return null;
    }

    private static boolean isValidatedDropEdgePivot(ServerLevel level, BlockPos pivot, ConstructionRequest request) {
        if (level == null || pivot == null || request == null || request.evidence() == null
                || parseConstructionScenario(request.evidence().scenario()) != ConstructionScenario.DROP_TO_LEDGE
                || request.corridor() == null || !Objects.equals(request.corridor().pivot(), pivot)) return false;
        if (request.corridor().takeoffFeet() == null || request.corridor().egressFeet() == null) return false;
        for (BlockPos cell : request.corridor().centerlineCells()) {
            if (cell == null || !level.isLoaded(cell) || !level.isLoaded(cell.above())
                    || !level.getBlockState(cell).getFluidState().isEmpty()
                    || !level.getBlockState(cell.above()).getFluidState().isEmpty()
                    || !level.getBlockState(cell).getCollisionShape(level, cell, CollisionContext.empty()).isEmpty()
                    || request.evidence().hazardCells().contains(cell)) return false;
        }
        return request.corridor().centerlineCells().size() <= MAX_CORRIDOR_INTERVAL_CELLS
                && hasLandingAndEgress(level, request.corridor().egressFeet(), pivot, request.threatDestination());
    }

    private static WallProfileScanner.Profile profileFromAdmissionEvidence(BlockPos pivot,
                                                                            WallProfileScanner.TargetEvidence evidence) {
        if (pivot == null || evidence == null || evidence.anchor() == null) return null;
        int wallHeight = Math.min(LivingWallConfig.MAX_HEIGHT.get(),
                Math.max(2, Math.abs(evidence.anchor().getY() - pivot.getY()) + 1));
        int gapWidth = Math.max(1, Mth.ceil(bridgeLength(pivot, evidence.anchor())));
        return new WallProfileScanner.Profile(evidence.anchor(), wallHeight, gapWidth);
    }

    private static boolean isValidCachedWallProfile(ServerLevel level, BlockPos pivot, BlockPos target,
                                                   WallProfileScanner.TargetEvidence evidence,
                                                   WallProfileScanner.Profile profile, double probeRadius,
                                                   boolean pressured, long revision, long requestExpiresAt,
                                                   long terrainSignature) {
        if (level == null || pivot == null || target == null || profile == null) return false;
        if (!Double.isFinite(probeRadius) || probeRadius <= 0.0D) return false;
        if (revision < 0L || requestExpiresAt <= 0L || terrainSignature < 0L) return false;
        if (profile.anchor() == null || !isSafeLanding(level, profile.anchor())) return false;
        if (evidence != null && evidence.anchor() != null && !profile.anchor().equals(evidence.anchor())) return false;
        return true;
    }

    private static boolean evidenceMatchesProfile(ServerLevel level, WallProfileScanner.TargetEvidence evidence,
                                                  BlockPos pivot, BlockPos threatDestination, BlockPos selectedAnchor,
                                                  WallProfileScanner.Profile profile,
                                                  CanonicalFeetCorridor requestedCorridor) {
        if (evidence == null || profile == null) return true;
        if (bucketKey(evidence.targetBucket()) != bucketKey(threatDestination)) return false;
        if (selectedAnchor != null && !selectedAnchor.equals(tacticalGoalForEvidence(evidence))) return false;
        if (!profile.anchor().equals(evidence.anchor())) return false;
        if (requestedCorridor != null) {
            return Objects.equals(pivot, requestedCorridor.pivot())
                    && Objects.equals(selectedAnchor, requestedCorridor.tacticalGoal())
                    && requestedCorridor.matchesEvidence(evidence);
        }
        return matchesSelectedEvidence(level, pivot, threatDestination, selectedAnchor, evidence);
    }

    private static boolean matchesSelectedEvidence(ServerLevel level, BlockPos pivot, BlockPos target,
                                                   WallProfileScanner.TargetEvidence evidence) {
        return matchesSelectedEvidence(level, pivot, target, null, evidence);
    }

    private static boolean matchesSelectedEvidence(ServerLevel level, BlockPos pivot, BlockPos threatDestination,
                                                   BlockPos selectedAnchor, WallProfileScanner.TargetEvidence evidence) {
        if (level == null || pivot == null || threatDestination == null || evidence == null) return false;
        if (bucketKey(evidence.targetBucket()) != bucketKey(threatDestination)) return false;
        if (selectedAnchor != null && !selectedAnchor.equals(tacticalGoalForEvidence(evidence))) return false;
        if (evidence.anchor() == null || evidence.wallFace() == null || evidence.supportCell() == null) return false;
        if (evidence.hazardCells() == null) return false;
        if (!validateCarriedHazards(level, new java.util.LinkedHashSet<>(evidence.hazardCells()))) return false;
        Vec3 forward = Vec3.atBottomCenterOf(evidence.wallFace()).subtract(Vec3.atBottomCenterOf(pivot));
        if (forward.lengthSqr() < 0.001D) return false;
        double wallProjection = Vec3.atBottomCenterOf(evidence.wallFace()).subtract(Vec3.atBottomCenterOf(pivot)).dot(forward);
        for (BlockPos hazard : evidence.hazardCells()) {
            Vec3 hazardVector = Vec3.atBottomCenterOf(hazard).subtract(Vec3.atBottomCenterOf(pivot));
            if (hazardVector.dot(forward) >= wallProjection) return false;
        }
        return true;
    }

    private static boolean snapshotAdmissible(ServerLevel level, ConstructionRequest request, long now) {
        return snapshotAdmissionReason(level, request, now) == null;
    }

    private static String snapshotAdmissionReason(ServerLevel level, ConstructionRequest request, long now) {
        if (level == null) return "level_null";
        if (request == null) return "request_null";
        if (request.snapshot() == null) return "snapshot_null";
        if (request.evidence() == null) return "evidence_null";
        if (request.expiresAt() <= now) return "request_expired";
        TargetScanSnapshot snapshot = request.snapshot();
        if (request.scanExpiresAt() <= now || snapshot.expiryTick() <= now) return "route_revision_expired";
        if (!request.evidence().matchesExactly(snapshot.evidence())) return "evidence_mismatch";
        if (!java.util.Objects.equals(request.threatDestination(), snapshot.threatIdentity())) return "threat_identity_mismatch";
        if (!java.util.Objects.equals(request.selectedAnchor(), snapshot.selectedAnchor())) return "selected_anchor_mismatch";
        if (bucketKey(request.sourceBucket()) != bucketKey(BlockPos.containing(snapshot.sourceCenter()))) return "source_bucket_mismatch";
        if (request.threatBucket() == null
                || request.threatBucket().asLong() != bucketKey(snapshot.threatIdentity())) return "threat_bucket_mismatch";
        if (!java.util.Objects.equals(snapshot.identity(), request.snapshot().identity())) return "snapshot_identity_mismatch";
        if (snapshot.fingerprint() == null || snapshot.fingerprint().isEmpty()) return "fingerprint_empty";
        if (request.routeEvidence() == null) return "route_evidence_missing";
        if (snapshot.routeEvidence() == null) return "snapshot_route_evidence_missing";
        if (!Objects.equals(request.routeEvidence(), snapshot.routeEvidence())) return "route_evidence_mismatch";
        String routeEvidenceReason = validateLiveRouteEvidence(level, request.routeEvidence(), snapshot.fingerprint());
        if (routeEvidenceReason != null) return routeEvidenceReason;
        return null;
    }

    private static String validateLiveRouteEvidence(ServerLevel level, RouteEvidence routeEvidence,
                                                    List<CellFingerprint> fingerprint) {
        if (level == null) return "level_null";
        if (routeEvidence == null) return "route_evidence_missing";
        if (routeEvidence.workerId() == null) return "worker_missing";
        Mob worker = getMob(level, routeEvidence.workerId());
        if (worker == null || !worker.isAlive() || worker.isRemoved()) return "worker_missing";
        if (routeEvidence.dimension() == null || !routeEvidence.dimension().equals(level.dimension().identifier())) {
            return "dimension_changed";
        }
        if (routeEvidence.revision() < 0L) return "route_revision_expired";
        if (!routeEvidence.pathReachable()) return "route_unreachable";
        if (routeEvidence.anchor() == null) return "anchor_changed";
        IntakeWindow intake = routeEvidence.intakeWindow();
        if (intake == null || intake.feederCell() == null || intake.feederApproach() == null) return "intake_changed";
        if (routeEvidence.certifiedStart() == null || routeEvidence.certifiedBody() == null
                || routeEvidence.certifiedPose() == null || routeEvidence.endpoint() == null
                || routeEvidence.worldFingerprint().isBlank() || !routeEvidence.endpoint().equals(intake.feederApproach())) {
            return "route_certificate_incomplete";
        }
        if (worker.getPose() != routeEvidence.certifiedPose()
                || Math.abs(worker.getBbWidth() - routeEvidence.certifiedBody().getXsize()) > 1.0E-4D
                || Math.abs(worker.getBbHeight() - routeEvidence.certifiedBody().getYsize()) > 1.0E-4D) {
            return "worker_body_changed";
        }
        Vec3 livePosition = worker.position();
        boolean atCertifiedStart = livePosition.distanceToSqr(routeEvidence.certifiedStart()) <= 0.04D;
        if (atCertifiedStart && !sameBodyEnvelope(worker.getBoundingBox(), routeEvidence.certifiedBody(), 0.20D)) {
            return "certified_start_body_changed";
        }
        int progress = routeProgressIndex(livePosition, routeEvidence.routeNodes(), routeEvidence.endpoint(),
                routeEvidence.certifiedStart());
        if (!atCertifiedStart && progress < 0) return "worker_off_certified_route";
        if (!Objects.equals(routeEvidence.worldFingerprint(), routeWorldFingerprint(level, routeEvidence.routeNodes()))) {
            return "route_world_mutated";
        }
        RouteProgressKey progressKey = new RouteProgressKey(routeEvidence.workerId(), routeEvidence.fingerprint());
        PerLevelState state = state(level);
        int priorProgress = state.routeProgress.getOrDefault(progressKey, -1);
        if (progress >= 0 && priorProgress > progress) return "route_progress_reversed";
        if (progress >= 0) state.routeProgress.put(progressKey, progress);
        for (CellFingerprint cell : fingerprint == null ? List.<CellFingerprint>of() : fingerprint) {
            if (cell == null || cell.cell() == null) continue;
            String liveFingerprint = liveStateFingerprint(level, cell.cell());
            if (Objects.equals(cell.liveStateFingerprint(), liveFingerprint)) continue;
            String kind = cell.kind() == null ? "" : cell.kind();
            if ("anchor".equals(kind)) return "anchor_changed";
            if ("support".equals(kind)) return "support_changed";
            if ("intake".equals(kind) || "intake_approach".equals(kind)) return "intake_changed";
            return "world_mutation";
        }
        return null;
    }

    private static String routeEvidenceFingerprint(UUID workerId, IntakeWindow intakeWindow, BlockPos anchor,
                                                   List<BlockPos> routeNodes, long revision,
                                                   Vec3 certifiedStart, String worldFingerprint) {
        return Objects.toString(workerId) + "|" + Objects.toString(intakeWindow) + "|" + Objects.toString(anchor)
                + "|" + Objects.toString(routeNodes) + "|" + revision + "|" + certifiedStart + "|" + worldFingerprint;
    }

    private static String routeWorldFingerprint(ServerLevel level, List<BlockPos> nodes) {
        if (level == null || nodes == null || nodes.isEmpty() || nodes.size() > TARGET_ROUTE_NODE_LIMIT) return "";
        StringBuilder fingerprint = new StringBuilder();
        for (BlockPos node : nodes) {
            if (node == null || !level.isLoaded(node) || !level.isLoaded(node.below()) || !level.isLoaded(node.above())) return "";
            fingerprint.append(node.asLong()).append(':').append(liveStateFingerprint(level, node)).append(':')
                    .append(liveStateFingerprint(level, node.below())).append(':')
                    .append(liveStateFingerprint(level, node.above())).append(';');
        }
        return fingerprint.toString();
    }

    private static int routeProgressIndex(Vec3 position, List<BlockPos> nodes, BlockPos endpoint) {
        return routeProgressIndex(position, nodes, endpoint, null);
    }

    private static int routeProgressIndex(Vec3 position, List<BlockPos> nodes, BlockPos endpoint,
                                         Vec3 certifiedStart) {
        if (position == null || nodes == null || nodes.isEmpty() || endpoint == null) return -1;
        double best = 0.04D;
        int bestProgress = -1;
        Vec3 previous = null;
        for (int index = 0; index < nodes.size(); index++) {
            BlockPos node = nodes.get(index);
            if (node == null) return -1;
            Vec3 current = Vec3.atBottomCenterOf(node);
            double distance = previous == null ? position.distanceToSqr(current)
                    : distanceToSegmentSqr(position, previous, current);
            if (distance <= best) {
                best = distance;
                bestProgress = index * 100;
            }
            previous = current;
        }
        if (previous != null && distanceToSegmentSqr(position, previous, Vec3.atBottomCenterOf(endpoint)) <= best) {
            bestProgress = nodes.size() * 100;
        }
        if (bestProgress < 0 && certifiedStart != null
                && distanceToSegmentSqr(position, certifiedStart, Vec3.atBottomCenterOf(nodes.get(0))) <= 0.04D) {
            bestProgress = 0;
        }
        return bestProgress;
    }

    private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double lengthSqr = delta.lengthSqr();
        double t = lengthSqr < 1.0E-12D ? 0.0D
                : Mth.clamp(point.subtract(start).dot(delta) / lengthSqr, 0.0D, 1.0D);
        return point.distanceToSqr(start.add(delta.scale(t)));
    }

    private static SemanticRequestKey semanticRequestKey(Mob planner, WallProfileScanner.TargetEvidence evidence,
                                                         BlockPos threatIdentity) {
        if (planner == null || evidence == null) return null;
        return new SemanticRequestKey(planner.getUUID(), evidence.targetBucket(), threatIdentity);
    }

    private static String semanticRequestFingerprint(ServerLevel level, List<Mob> roster,
                                                     WallProfileScanner.TargetEvidence evidence,
                                                     CanonicalFeetCorridor corridor, RouteEvidence routeEvidence,
                                                     BlockPos threatIdentity) {
        if (level == null || evidence == null) return "invalid";
        List<UUID> eligible = roster == null ? List.of() : roster.stream()
                .filter(mob -> mob != null && mob.isAlive() && !mob.isRemoved() && isEligible(mob))
                .map(Mob::getUUID).distinct().sorted().toList();
        long world = terrainSignature(level, evidence.anchor(), threatIdentity, evidence);
        return evidence.anchor() + "|" + evidence.edgeCell() + "|" + evidence.supportCell() + "|"
                + evidence.scenario() + "|" + evidence.hazardCells() + "|"
                + (corridor == null ? "" : corridor.corridorId()) + "|" + world + "|"
                + (routeEvidence == null ? "" : routeWorldFingerprint(level, routeEvidence.routeNodes()))
                + "|" + eligible;
    }

    private static boolean canonicalAdmissionAccessSatisfied(ConstructionRequest request) {
        if (request == null) return false;
        return canonicalAdmissionAccessSatisfied(request.routeEvidence(), request.corridor(), request.selectedAnchor());
    }

    private static boolean canonicalAdmissionAccessSatisfied(RouteEvidence routeEvidence, CanonicalFeetCorridor corridor,
                                                              BlockPos selectedAnchor) {
        return canonicalAdmissionAccessReason(routeEvidence, corridor, selectedAnchor) == null;
    }

    private static String canonicalCorridorGateReason(Formation formation, BlockPos target) {
        if (formation == null || formation.request == null) return "formation_request_missing";
        String reason = canonicalAdmissionAccessReason(formation.request.routeEvidence(), formation.request.corridor(),
                formation.request.selectedAnchor());
        if (reason != null) return reason;
        if (target != null && !target.equals(formation.request.corridor().tacticalGoal())) return "corridor_target_mismatch";
        return null;
    }

    private static boolean canonicalCorridorGate(Formation formation, BlockPos target) {
        String reason = canonicalCorridorGateReason(formation, target);
        if (formation != null && formation.request != null && formation.request.sourceBucket() != null) {
            Mob planner = formation.leaderRef == null ? null : formation.leaderRef.get();
            if (planner != null && planner.level() instanceof ServerLevel level) {
                PerLevelState state = state(level);
                state.decisionCounters.merge(reason == null ? "accepted" : "rejected", 1, Integer::sum);
                if (reason != null) state.rejectionCounters.merge(reason, 1, Integer::sum);
            }
        }
        if (reason == null) {
            return true;
        }
        return false;
    }

    private static String canonicalAdmissionAccessReason(RouteEvidence routeEvidence, CanonicalFeetCorridor corridor,
                                                         BlockPos selectedAnchor) {
        if (corridor == null) return "corridor_missing";
        if (selectedAnchor == null || !selectedAnchor.equals(corridor.tacticalGoal())) return "anchor_mismatch";
        if (routeEvidence == null) return "route_evidence_missing";
        if (!routeEvidence.pathReachable()) return "route_unreachable";
        if (routeEvidence.revision() < 0L) return "route_revision_expired";
        if (!selectedAnchor.equals(routeEvidence.anchor())) return "route_endpoint_mismatch";
        if (routeEvidence.intakeWindow() == null || routeEvidence.intakeWindow().feederApproach() == null) {
            return "intake_missing";
        }
        if (routeEvidence.routeNodes().isEmpty()) return "route_nodes_missing";
        BlockPos routeEndpoint = routeEvidence.intakeWindow().feederApproach();
        BlockPos lastNode = routeEvidence.routeNodes().get(routeEvidence.routeNodes().size() - 1);
        if (lastNode.closerThan(routeEndpoint, 2.0D)) return null;
        boolean canonicalCorridorEndpoint = !routeEvidence.corridorId().isBlank()
                && routeEvidence.corridorId().equals(corridor.corridorId())
                && lastNode.closerThan(selectedAnchor, 2.0D);
        return canonicalCorridorEndpoint ? null : "route_endpoint_mismatch";
    }

    private static String requestEvidenceAdmissionReason(ServerLevel level, BlockPos pivot,
                                                         ConstructionRequest request) {
        if (request == null || request.evidence() == null) return "contract_incomplete";
        WallProfileScanner.TargetEvidence evidence = request.evidence();
        if (!hasCompleteConstructionContract(pivot, request.threatDestination(), evidence)) return "contract_incomplete";
        if (request.selectedAnchor() == null || !request.selectedAnchor().equals(tacticalGoalForEvidence(evidence))) return "anchor_mismatch";
        if (request.corridor() == null) return "contract_incomplete";
        CanonicalFeetCorridor corridor = request.corridor();
        if (!Objects.equals(pivot, corridor.pivot())) return "corridor_pivot_mismatch";
        if (!corridor.matchesEvidence(evidence)) return "corridor_evidence_mismatch";
        TargetScanSnapshot snapshot = request.snapshot();
        ConstructionObservation observation = request.observation();
        if (snapshot == null || observation == null || observation.roster().isEmpty()) return "scan_lineage_missing";
        if (!Objects.equals(snapshot.sourceOrigin(), Vec3.atBottomCenterOf(corridor.pivot()))
                || observation.requestRevision() != snapshot.revision()) return "scan_lineage_mismatch";
        if (!validateCarriedHazards(level, new java.util.LinkedHashSet<>(evidence.hazardCells()))) {
            return "hazard_state_changed";
        }
        String accessReason = canonicalAdmissionAccessReason(request.routeEvidence(), corridor, request.selectedAnchor());
        if (accessReason != null) return accessReason;
        ConstructionScenario scenario = parseConstructionScenario(evidence.scenario());
        if (orderedCorridorTransitionValid(corridor, evidence, request.threatDestination(), scenario)) {
            return null;
        }
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ADMISSION)) {
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] corridor_ordering_detail pivot={} scenario={} takeoff={} landing={} egress={} tacticalGoal={} centerlineSize={} takeoffIndex={} landingIndex={} egressIndex={} hazards={} evidenceEdge={} evidenceLanding={} evidenceInvasion={}",
                    corridor.pivot(), scenario, corridor.takeoffFeet(), corridor.bridgeLandingFeet(), corridor.egressFeet(),
                    corridor.tacticalGoal(), corridor.centerlineCells().size(),
                    exactCenterlineIndex(corridor.centerlineCells(), corridor.takeoffFeet()),
                    exactCenterlineIndex(corridor.centerlineCells(), corridor.bridgeLandingFeet()),
                    exactCenterlineIndex(corridor.centerlineCells(), corridor.egressFeet()),
                    corridor.hazardCells(), evidence.edgeCell(), evidenceLandingSurface(evidence), evidenceInvasionSurface(evidence));
        }
        return "corridor_ordering_mismatch";
    }

    private static String activeRequestPlanLineageReason(ServerLevel level, ConstructionRequest request) {
        if (level == null || request == null || request.snapshot() == null || request.observation() == null
                || request.corridor() == null || request.observation().roster().isEmpty()) return "scan_lineage_missing";
        List<Mob> roster = request.observation().roster().stream().map(id -> getMob(level, id))
                .filter(Objects::nonNull).toList();
        if (roster.size() != request.observation().roster().size()) return "scan_roster_changed";
        LivingBridgePositionPlan plan = state(level).positionPlans.get(targetRosterFingerprint(roster));
        if (plan == null || !plan.isLive(level.getGameTime())
                || !plan.acceptsRoster(request.observation().roster())
                || !plan.destinationBucketContains(request.threatDestination())
                || !Objects.equals(plan.sourceOrigin(), request.corridor().pivot())
                || !Objects.equals(request.snapshot().sourceOrigin(), Vec3.atBottomCenterOf(plan.sourceOrigin()))
                || request.observation().windowGeneration() != plan.windowGeneration()) return "scan_lineage_mismatch";
        return null;
    }

    private static void recordProfileEvidenceRejection(ServerLevel level, ConstructionRequest request, BlockPos buildPivot,
                                                       SitePlan plan, String reason) {
        if (level == null) return;
        recordBuildGate(state(level), "profile_evidence_invalid");
        recordBuildGate(state(level), "profile_evidence_invalid:" + reason);
        logCanonicalAdmissionGate(level, request, buildPivot, plan, "rejected", reason);
    }

    private static void logCanonicalAdmissionGate(ServerLevel level, ConstructionRequest request, BlockPos buildPivot,
                                                  SitePlan plan, String result, String reason) {
        if (level == null || request == null || request.corridor() == null
                || SoundAttractConfig.COMMON == null || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ADMISSION)) {
            return;
        }
        CanonicalFeetCorridor corridor = request.corridor();
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] canonical_admission_gate anchor={} buildPivot={} takeoff={} requestCorridorId={} routeCorridorId={} planCorridorId={} scenario={} result={} reason={}",
                request.selectedAnchor(), buildPivot, corridor.takeoffFeet(), corridor.corridorId(),
                request.routeEvidence() == null ? null : "source_to_intake",
                plan == null || plan.corridor() == null ? null : plan.corridor().corridorId(),
                request.evidence() == null ? null : request.evidence().scenario(), result, reason);
    }

    private static boolean validateRequestedEvidence(ServerLevel level, BlockPos pivot, BlockPos target,
                                                     WallProfileScanner.TargetEvidence evidence, long revision) {
        return validateRequestedEvidence(level, pivot, target, null, evidence, revision);
    }

    private static boolean validateRequestedEvidence(ServerLevel level, BlockPos pivot, BlockPos threatDestination,
                                                     BlockPos selectedAnchor, WallProfileScanner.TargetEvidence evidence,
                                                     long revision) {
        return revision >= 0L && matchesSelectedEvidence(level, pivot, threatDestination, selectedAnchor, evidence);
    }

    private static void invalidateWallProfiles(ServerLevel level, BlockPos pivot) {
        if (level == null || pivot == null) return;
        PerLevelState state = state(level);
        state.wallProfiles.entrySet().removeIf(entry -> entry.getKey().pivotX() == pivot.getX()
                && entry.getKey().pivotY() == pivot.getY() && entry.getKey().pivotZ() == pivot.getZ());
        int sourceX = Math.floorDiv(pivot.getX(), 4);
        int sourceY = Math.floorDiv(pivot.getY(), 4);
        int sourceZ = Math.floorDiv(pivot.getZ(), 4);
        state.targetScans.entrySet().removeIf(entry -> entry.getKey().sourceX() == sourceX
                && entry.getKey().sourceY() == sourceY && entry.getKey().sourceZ() == sourceZ);
    }

    private static List<Mob> localBuilders(ServerLevel level, Mob leader, Formation formation) {
        double radius = LivingWallConfig.MAX_GAP.get() + LOCAL_ROSTER_MARGIN;
        Vec3 site = Vec3.atBottomCenterOf(formation.pivot);
        List<Mob> builders = new ArrayList<>(level.getEntitiesOfClass(Mob.class,
                new AABB(site, site).inflate(radius, LivingWallConfig.MAX_HEIGHT.get() + 2.0D, radius),
                mob -> mob != leader && mob.isAlive() && !mob.isRemoved() && isEligible(mob)
                        && !isControlled(mob) && !isBeyondAnchor(formation, mob)
                        && Math.abs(mob.getY() - site.y) <= LivingWallConfig.MAX_HEIGHT.get() + 2.0D));
        builders.sort(Comparator.comparingDouble((Mob mob) -> mob.distanceToSqr(site))
                .thenComparing(mob -> mob.getUUID().toString()));
        if (builders.size() > MAX_SCAN_CANDIDATES) {
            return new ArrayList<>(builders.subList(0, MAX_SCAN_CANDIDATES));
        }
        return builders;
    }

    private static void refreshLocalRoster(ServerLevel level, Formation formation, Mob leader, long now) {
        PerLevelState state = state(level);
        if (state.rosterRefreshesRemaining <= 0 || formation.lastRosterRefreshTick >= 0L
                && now - formation.lastRosterRefreshTick < ROSTER_REFRESH_INTERVAL_TICKS) {
            return;
        }
        state.rosterRefreshesRemaining--;
        formation.lastRosterRefreshTick = now;
        int before = formation.localRoster.size();
        formation.localRoster.removeIf(id -> {
            Mob mob = getMob(level, id);
            UUID owner = state.mobOwners.get(id);
            return mob == null || !mob.isAlive() || mob.isRemoved()
                    || owner != null && !formation.id.equals(owner);
        });
        for (Mob candidate : localBuilders(level, leader, formation)) {
            formation.localRoster.add(candidate.getUUID());
        }
        if (formation.request != null && formation.request.sourceCenter() != null) {
            Vec3 source = formation.request.sourceCenter();
            AABB recruitmentArea = new AABB(source, source).inflate(RECRUITMENT_RADIUS);
            for (Mob candidate : level.getEntitiesOfClass(Mob.class, recruitmentArea,
                    mob -> mob != leader && mob.isAlive() && !mob.isRemoved() && isEligible(mob))) {
                formation.localRoster.add(candidate.getUUID());
                if (formation.localRoster.size() >= MAX_SCAN_CANDIDATES) break;
            }
        }
        trimLocalRoster(level, formation);
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_FORMATION)
                && formation.localRoster.size() != before) {
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Bridge {} roster_refresh before={} after={} phase={}",
                    formation.id, before, formation.localRoster.size(), formation.phase);
        }
    }

    private static boolean isRequestBuildable(ConstructionRequest request, ConstructionIntent intent, long now) {
        if (request == null || request.evidence() == null || request.selectedAnchor() == null
                || request.threatDestination() == null || request.expiresAt() <= now) {
            return false;
        }
        return request.scanExpiresAt() > now
                || (intent != null && intent.recruitmentDeadline >= now);
    }

    private static void trimLocalRoster(ServerLevel level, Formation formation) {
        if (formation.localRoster.size() <= MAX_SCAN_CANDIDATES) {
            return;
        }
        Vec3 pivot = Vec3.atBottomCenterOf(formation.pivot);
        List<UUID> ordered = formation.localRoster.stream()
                .filter(id -> !formation.members.contains(id) && !formation.pendingBuilderIds.contains(id)
                        && !formation.ticketGoals.containsKey(id))
                .sorted(Comparator.comparingDouble((UUID id) -> {
                    Mob mob = getMob(level, id);
                    return mob == null ? Double.MAX_VALUE : mob.distanceToSqr(pivot);
                }).thenComparing(UUID::toString))
                .toList();
        int removable = formation.localRoster.size() - MAX_SCAN_CANDIDATES;
        for (int index = ordered.size() - 1; index >= 0 && removable > 0; index--) {
            formation.localRoster.remove(ordered.get(index));
            removable--;
        }
    }

    private static BodyGeometry geometryFor(Mob mob) {
        if (mob == null) return new BodyGeometry(0.0D, 0.0D);
        double width = Math.max(0.0D, mob.getBbWidth());
        double height = Math.max(0.0D, mob.getBbHeight());
        Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        GeometryKey key = new GeometryKey(type, Mth.floor(width * 64.0D + 0.5D), Mth.floor(height * 64.0D + 0.5D));
        if (BODY_GEOMETRIES.size() >= MAX_BODY_GEOMETRIES && !BODY_GEOMETRIES.containsKey(key)) BODY_GEOMETRIES.clear();
        return BODY_GEOMETRIES.computeIfAbsent(key, ignored -> new BodyGeometry(width, height));
    }

    private static BodyGeometry rosterGeometry(List<Mob> mobs) {
        double width = Double.POSITIVE_INFINITY;
        double height = Double.POSITIVE_INFINITY;
        for (Mob mob : mobs) {
            BodyGeometry geometry = geometryFor(mob);
            if (geometry.width() <= 0.0D || geometry.height() <= 0.0D) continue;
            width = Math.min(width, geometry.width());
            height = Math.min(height, geometry.height());
        }
        return Double.isFinite(width) && Double.isFinite(height) ? new BodyGeometry(width, height) : null;
    }

    private static boolean compatibleGeometry(Mob mob, Formation formation) {
        BodyGeometry geometry = geometryFor(mob);
        return geometry.width() + CONTACT_EPSILON >= formation.minimumWidth
                && geometry.height() + CONTACT_EPSILON >= formation.minimumHeight;
    }

    private static boolean isSafeLanding(ServerLevel level, BlockPos anchor) {
        if (!level.isLoaded(anchor) || !level.isLoaded(anchor.above()) || !level.isLoaded(anchor.below())) return false;
        BlockState feet = level.getBlockState(anchor);
        BlockState head = level.getBlockState(anchor.above());
        BlockState support = level.getBlockState(anchor.below());
        if (isOpenTrapdoor(feet) || isOpenTrapdoor(support) || isTrapdoorDropHazard(level, anchor.below())) return false;
        boolean feetPassable = feet.getCollisionShape(level, anchor).isEmpty()
                || feet.is(BlockTags.TRAPDOORS) || feet.getBlock() instanceof TrapDoorBlock;
        return feet.getFluidState().isEmpty() && support.getFluidState().isEmpty()
                && feetPassable
                && head.getCollisionShape(level, anchor.above()).isEmpty()
                && hasStableSupport(level, anchor.below(), support);
    }

    private static boolean isSafeSite(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.isLoaded(pos.below()) && level.isLoaded(pos.above())
                && !isUnsafeColumn(level, pos)
                && hasStableSupport(level, pos.below(), level.getBlockState(pos.below()))
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    private static boolean hasStableSupport(ServerLevel level, BlockPos pos, BlockState state) {
        if (level == null || pos == null || state == null) return false;
        if (!state.getFluidState().isEmpty()) return false;
        if (state.is(BlockTags.TRAPDOORS) || state.getBlock() instanceof TrapDoorBlock) {
            return !isOpenTrapdoor(state) && !isTrapdoorDropHazard(level, pos);
        }
        return isReliableSupportShape(state.getCollisionShape(level, pos));
    }

    static boolean isReliableSupportShape(VoxelShape shape) {
        if (shape == null || shape.isEmpty()) return false;
        AABB bounds = shape.bounds();
        if (bounds.getXsize() < 0.45D || bounds.getZsize() < 0.45D) return false;
        if (bounds.maxY < 0.45D) return false;
        return true;
    }

    private static SitePlan planConstruction(ServerLevel level, BlockPos seed, BlockPos anchor,
                                                     BlockPos target, int columns) {
        return planConstruction(level, seed, anchor, target, columns, null, null);
    }

    private static SitePlan planConstruction(ServerLevel level, BlockPos seed, BlockPos anchor,
                                                      BlockPos target, int columns, WallProfileScanner.TargetEvidence evidence,
                                                      BodyGeometry geometry) {
        return planConstruction(level, seed, anchor, target, columns, null, evidence, geometry);
    }

    private static SitePlan planConstruction(ServerLevel level, BlockPos seed, BlockPos anchor,
                                             BlockPos target, int columns, ConstructionRequest request,
                                             WallProfileScanner.TargetEvidence evidence, BodyGeometry geometry) {
        List<SitePlan> plans = constructionPlans(level, seed, anchor, target, columns, request, evidence, geometry,
                request == null ? null : request.observation());
        return plans.isEmpty() ? null : plans.get(0);
    }

    private static SitePlan planConstruction(ServerLevel level, BlockPos seed, BlockPos anchor,
                                             BlockPos target, int columns, ConstructionRequest request,
                                             WallProfileScanner.TargetEvidence evidence, BodyGeometry geometry,
                                             ConstructionObservation observation) {
        List<SitePlan> plans = constructionPlans(level, seed, anchor, target, columns, request, evidence, geometry, observation);
        return plans.isEmpty() ? null : plans.get(0);
    }

    private static List<SitePlan> constructionPlans(ServerLevel level, BlockPos seed, BlockPos anchor,
                                                             BlockPos target, int columns, ConstructionRequest request,
                                                             WallProfileScanner.TargetEvidence evidence, BodyGeometry geometry) {
        return constructionPlans(level, seed, anchor, target, columns, request, evidence, geometry,
                request == null ? null : request.observation());
    }

    private static List<SitePlan> constructionPlans(ServerLevel level, BlockPos seed, BlockPos anchor,
                                                             BlockPos target, int columns, ConstructionRequest request,
                                                             WallProfileScanner.TargetEvidence evidence, BodyGeometry geometry,
                                                             ConstructionObservation observation) {
        if (level == null || seed == null || anchor == null || target == null
                || !hasCompleteConstructionContract(seed, target, evidence)) return List.of();
        ConstructionScenario requestedScenario = observation != null ? observation.scenario()
                : parseConstructionScenario(evidence == null ? null : evidence.scenario());
        if (!level.isLoaded(anchor) || !level.isLoaded(anchor.above())) return List.of();
        Vec3 rawForward = Vec3.atBottomCenterOf(anchor).subtract(Vec3.atBottomCenterOf(seed));
        double length = Math.sqrt(rawForward.x * rawForward.x + rawForward.z * rawForward.z);
        if (length < 0.001D) return List.of();
        Vec3 forward = new Vec3(rawForward.x / length, 0.0D, rawForward.z / length);
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        List<BlockPos> rawHazards = constructionHazardsForPlan(evidence == null ? null : evidence.hazardCells(), seed, anchor);
        List<BlockPos> hazards = hazardsWithinTransition(rawHazards, seed, anchor);
        List<ScoredSitePlan> plans = new ArrayList<>();
        Map<String, Integer> localRejections = DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_CONSTRUCTION) ? new HashMap<>() : null;
        boolean canonicalAdmissionSatisfied = canonicalAdmissionAccessSatisfied(request);
        if (request != null && request.routeEvidence() != null && request.corridor() == null) {
            recordBuildGate(state(level), "canonical_corridor_missing");
            return List.of();
        }
        if (request != null && request.corridor() != null && !canonicalAdmissionSatisfied) {
            String reason = canonicalAdmissionAccessReason(request.routeEvidence(), request.corridor(), request.selectedAnchor());
            recordProfileEvidenceRejection(level, request, seed, null, reason);
            return List.of();
        }
        if (canonicalAdmissionSatisfied) {
            appendConstructionPlanCandidate(level, seed, seed, anchor, target, evidence, geometry, rawHazards, hazards,
                    request, plans, localRejections, true, false, observation);
        }
        plans.sort(Comparator.comparingDouble(ScoredSitePlan::score)
                .thenComparing(plan -> plan.plan().pivot().asLong()));
        if (plans.isEmpty()) {
            logPlanSearchDiagnostic(level, seed, anchor, target, columns, hazards.size(), localRejections);
        }
        return plans.stream().map(ScoredSitePlan::plan).toList();
    }

    private static void appendConstructionPlanCandidate(ServerLevel level, BlockPos seed, BlockPos pivot, BlockPos anchor,
                                                        BlockPos target, WallProfileScanner.TargetEvidence evidence,
                                                        BodyGeometry geometry, List<BlockPos> rawHazards,
                                                        List<BlockPos> seedHazards, ConstructionRequest request,
                                                        List<ScoredSitePlan> plans, Map<String, Integer> localRejections,
                                                        boolean canonicalAdmissionSatisfied, boolean scoreOffset,
                                                        ConstructionObservation observation) {
        if (level == null || seed == null || pivot == null || anchor == null || target == null || evidence == null
                || geometry == null || plans == null) {
            return;
        }
        List<BlockPos> candidateHazards = observation != null
                ? observation.hazards()
                : hazardsWithinTransition(evidence.hazardCells(), pivot, anchor);
        List<BlockPos> base = List.of(pivot);
        if (!isSafeSite(level, pivot)) {
            recordBuildGate(state(level), "site_rejected:unsafe_pivot");
            countLocalRejection(localRejections, "unsafe_pivot");
            return;
        }
        ConstructionScenario scenario = observation != null ? observation.scenario()
                : scenarioFromEvidence(evidence, candidateHazards);
        java.util.Optional<LivingWallBridgeAdapterPlanner.AdapterSpec> adapterSpec = adapterSpecForScenario(scenario);
        if (adapterSpec.isEmpty()) {
            recordBuildGate(state(level), "site_rejected:unsupported_adapter");
            countLocalRejection(localRejections, "unsupported_adapter");
            return;
        }
        TraversalCorridorResolution corridor = observation != null
                ? new TraversalCorridorResolution(new ResolvedTraversalCorridor(
                        observation.pivot(), observation.evidence().edgeCell(),
                        observation.evidence().landingSurface(), observation.spanEnd(),
                        observation.scenario(), observation.evidence(), observation.corridor(),
                        bridgeLength(observation.pivot(), observation.anchor()),
                        bridgeLength(observation.pivot(), observation.spanEnd())), null)
                : resolveTraversalCorridor(level, pivot, anchor,
                        target, scenario, evidence, geometry,
                        canonicalAdmissionSatisfied && request != null ? request.corridor() : null);
        if (!corridor.resolved()) {
            recordBuildGate(state(level), "site_rejected:corridor_unresolved:" + corridor.reason());
            countLocalRejection(localRejections, "corridor_unresolved:" + corridor.reason());
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_CONSTRUCTION)) {
                SoundAttractMod.LOGGER.info("[LivingWallCoordinator] site_rejected:corridor_unresolved reason={} pivot={} source={} edge={} landing={} invasion={} hazards={} captureTick={} scanGeneration={} requestRevision={}",
                        corridor.reason(), pivot,
                        evidence.sourceApproachSurface(),
                        evidence.edgeCell(),
                        evidence.landingSurface(),
                        evidence.invasionSurface(),
                        capHazardInterval(evidence.hazardCells()),
                        request == null || request.snapshot() == null ? null : Long.valueOf(request.snapshot().captureTick()),
                        request == null || request.snapshot() == null ? null : request.snapshot().revision(),
                        request == null ? null : request.revision());
            }
            return;
        }
        ResolvedTraversalCorridor resolvedCorridor = corridor.corridor();
        BlockPos spanEnd = resolvedCorridor.invasionEndpoint();
        Vec3 candidateForward = resolvedCorridor.canonicalCorridor().traversalForward();
        if (candidateForward.lengthSqr() < 0.001D) {
            countLocalRejection(localRejections, "zero_traversal_length");
            return;
        }
        if (!isOnApproachSide(seed, pivot, candidateForward, new java.util.LinkedHashSet<>(candidateHazards))) {
            recordBuildGate(state(level), "plan_approach_side_rejected");
            countLocalRejection(localRejections, "approach_side");
            return;
        }
        List<BlockPos> assemblySlots = new ArrayList<>(assemblySlotCandidates(level, pivot, candidateForward, candidateHazards));
        if (assemblySlots.isEmpty()) {
            recordBuildGate(state(level), "site_rejected:no_assembly_slot");
            countLocalRejection(localRejections, "no_assembly_slot");
            return;
        }
        if (request != null && !isScenarioEndpointValid(level, request, scenario, spanEnd, pivot, anchor)) {
            recordBuildGate(state(level), "site_rejected:unsafe_final_endpoint");
            countLocalRejection(localRejections, "unsafe_final_endpoint");
            return;
        }
        if (!hasWallProgress(pivot, anchor, evidence.wallFace())
                || !hasWallProgress(pivot, spanEnd, evidence.wallFace())
                || !hasThreatProgress(pivot, anchor, target)
                || !hasThreatProgress(pivot, spanEnd, target)) {
            recordBuildGate(state(level), "plan_threat_progress_rejected");
            countLocalRejection(localRejections, "threat_progress");
            return;
        }
        if (resolvedCorridor.horizontalLength() > LivingWallConfig.MAX_GAP.get() && !canonicalAdmissionSatisfied) {
            recordBuildGate(state(level), "horizontal_gap_limit");
            countLocalRejection(localRejections, "horizontal_gap_limit");
            return;
        }
        boolean shortDrop = scenario == ConstructionScenario.DROP_TO_LEDGE
                && resolvedCorridor.invasionEndpoint() != null
                && !Objects.equals(resolvedCorridor.invasionEndpoint(), anchor);
        DropLimitSnapshot limits = captureDropLimitSnapshot(level.getGameTime());
        double lengthLimit = shortDrop ? limits.maxRopeBridgeLength() : limits.maxDropBridgeLength();
        if (resolvedCorridor.constructionLength() > lengthLimit) {
            recordBuildGate(state(level), shortDrop ? "rope_bridge_length_limit" : "drop_bridge_length_limit");
            recordBuildGate(state(level), "construction_length_limit");
            countLocalRejection(localRejections, shortDrop ? "rope_length_limit" : "drop_length_limit");
            return;
        }
        ExactSiteKey siteKey = exactSiteKey(pivot, anchor, spanEnd, scenario, candidateHazards);
        if (isTerrainRejected(level, siteKey, level.getGameTime())) {
            recordBuildGate(state(level), "plan_terrain_cooldown");
            countLocalRejection(localRejections, "terrain_cooldown");
            return;
        }
        Set<BlockPos> validation = new HashSet<>();
        validation.addAll(base);
        List<IntakeWindow> intakeWindows = observation != null
                ? observation.workers().values().stream().map(WorkerObservation::intakeWindow)
                        .filter(Objects::nonNull).distinct().toList()
                : intakeWindows(level, pivot, candidateForward,
                        candidateHazards, validation, assemblySlots).windows();
        if (intakeWindows.isEmpty()) {
            recordBuildGate(state(level), "site_rejected:no_intake_window");
            countLocalRejection(localRejections, "no_intake_window");
            return;
        }
        assemblySlots.removeIf(slot -> occupiesIntakeClearance(slot, intakeWindows));
        if (assemblySlots.isEmpty()) {
            recordBuildGate(state(level), "site_rejected:no_assembly_slot_after_intake");
            countLocalRejection(localRejections, "no_assembly_slot_after_intake");
            return;
        }
        for (IntakeWindow window : intakeWindows) {
            if (window != null) {
                if (window.feederCell() != null) validation.add(window.feederCell());
                if (window.feederApproach() != null) validation.add(window.feederApproach());
            }
        }
        FootprintFailure footprintFailure = firstFootprintFailure(level, validation, candidateHazards);
        if (footprintFailure != null) {
            recordBuildGate(state(level), "site_rejected:unsafe_validation_cell");
            countLocalRejection(localRejections, "unsafe_validation_cell");
            logInvalidPlanFootprint(level, pivot, anchor, spanEnd, footprintFailure);
            return;
        }
        if (scenario == ConstructionScenario.NONE && !tiltStationsHaveTerrainSupport(level, resolvedCorridor.canonicalCorridor())) {
            recordBuildGate(state(level), "site_rejected:tilt_station_support");
            countLocalRejection(localRejections, "tilt_station_support");
            return;
        }
        double score = bridgeLength(pivot, spanEnd);
        if (scoreOffset) {
            score += Math.max(0.0D, horizontalDistance(seed, pivot)) * 0.15D;
        }
        SitePlan plan = new SitePlan(scenario, pivot, anchor,
                spanEnd, candidateForward, base, intakeWindows, assemblySlots, validation, candidateHazards,
                observation != null ? observation.corridor() : resolvedCorridor.canonicalCorridor());
        if (!hasRequiredStaticSiteGeometry(plan)) return;
        plans.add(new ScoredSitePlan(plan, score));
    }

    private static TraversalCorridorResolution resolveTraversalCorridor(ServerLevel level, BlockPos pivot, BlockPos anchor,
                                                                        BlockPos threatDestination,
                                                                        ConstructionScenario scenario,
                                                                        WallProfileScanner.TargetEvidence evidence,
                                                                        BodyGeometry geometry,
                                                                        CanonicalFeetCorridor requestedCorridor) {
        if (level == null || pivot == null || anchor == null || scenario == null
                || scenario == ConstructionScenario.NONE || evidence == null || !hasProfileCorridorEvidence(evidence)
                || geometry == null || !Double.isFinite(geometry.width()) || !Double.isFinite(geometry.height())
                || geometry.width() <= 0.0D || geometry.height() <= 0.0D) {
            return new TraversalCorridorResolution(null,
                    TraversalCorridorRejectionReason.INVALID_OR_MISSING_CORRIDOR_EVIDENCE_OR_GEOMETRY);
        }
        BlockPos landing = evidenceLandingSurface(evidence);
        BlockPos edge = evidence.edgeCell() != null ? evidence.edgeCell() : anchor;
        BlockPos provisionalEgress = switch (scenario) {
            case WALL_LEDGE -> evidenceInvasionSurface(evidence);
            case DROP_TO_LEDGE, JUMP_BRIDGE, HAZARD_AND_WALL -> landing;
            default -> evidenceInvasionSurface(evidence);
        };
        if (requestedCorridor == null) {
            return new TraversalCorridorResolution(null,
                    TraversalCorridorRejectionReason.INVALID_OR_MISSING_CORRIDOR_EVIDENCE_OR_GEOMETRY);
        }
        CanonicalFeetCorridor canonicalCorridor = requestedCorridor;
        if (!Objects.equals(pivot, requestedCorridor.pivot())
                || !Objects.equals(anchor, requestedCorridor.tacticalGoal())
                || !requestedCorridor.matchesEvidence(evidence)) {
            return new TraversalCorridorResolution(null,
                    TraversalCorridorRejectionReason.INVALID_OR_MISSING_CORRIDOR_EVIDENCE_OR_GEOMETRY);
        }
        if (corridorIntervalCells(canonicalCorridor) > MAX_CORRIDOR_INTERVAL_CELLS) {
            return new TraversalCorridorResolution(null,
                    TraversalCorridorRejectionReason.CORRIDOR_INTERVAL_BUDGET);
        }
        BlockPos invasionEndpoint = requestedCorridor.egressFeet();
        if (invasionEndpoint == null) {
            return new TraversalCorridorResolution(null,
                    TraversalCorridorRejectionReason.MISSING_OR_INVALID_INVASION_ENDPOINT_OR_SCENARIO_ENDPOINT);
        }
        CanonicalFeetCorridor executableCorridor = executableCorridor(level, canonicalCorridor, geometry);
        if (executableCorridor == null) {
            return new TraversalCorridorResolution(null,
                    TraversalCorridorRejectionReason.SWEPT_BODY_CLEARANCE_FAILURE);
        }
        canonicalCorridor = executableCorridor;
        if (!orderedCorridorTransitionValid(canonicalCorridor, evidence, threatDestination, scenario)) {
            return new TraversalCorridorResolution(null,
                    TraversalCorridorRejectionReason.ORDERED_SOURCE_EDGE_LANDING_INVASION_HAZARD_MISMATCH);
        }
        if (!hasWallProgress(pivot, invasionEndpoint, evidence.wallFace())) {
            return new TraversalCorridorResolution(null,
                    TraversalCorridorRejectionReason.THREAT_PROGRESS_FAILURE);
        }
        if (!corridorBodyClear(level, canonicalCorridor, geometry, scenario)) {
            return new TraversalCorridorResolution(null,
                    TraversalCorridorRejectionReason.SWEPT_BODY_CLEARANCE_FAILURE);
        }
        double horizontalLength = horizontalDistance(pivot, invasionEndpoint);
        double constructionLength = bridgeLength(pivot, invasionEndpoint);
        if (!Double.isFinite(horizontalLength) || !Double.isFinite(constructionLength)) {
            return new TraversalCorridorResolution(null,
                    TraversalCorridorRejectionReason.NON_FINITE_LENGTH);
        }
        return new TraversalCorridorResolution(new ResolvedTraversalCorridor(pivot, edge, landing, invasionEndpoint,
                scenario, evidence, canonicalCorridor, horizontalLength, constructionLength), null);
    }

    private static CanonicalFeetCorridor executableCorridor(ServerLevel level, CanonicalFeetCorridor canonical,
                                                            BodyGeometry geometry) {
        if (canonical == null || geometry == null) return null;
        CorridorCacheKey cacheKey = new CorridorCacheKey(
                level.dimension().identifier(), canonical.corridorId(),
                canonical.takeoffFeet(), canonical.bridgeLandingFeet(), canonical.egressFeet(),
                (int) Math.round(geometry.width() * 1000.0D), (int) Math.round(geometry.height() * 1000.0D));
        PerLevelState state = state(level);
        if (state != null) {
            CachedCorridor cached = state.corridorCache.get(cacheKey);
            if (cached != null && cached.expiresAt() > level.getGameTime()) {
                return cached.corridor();
            }
        }
        List<BlockPos> first = LivingBridgeRouteSearch.findRoute(level, canonical.takeoffFeet(),
                canonical.bridgeLandingFeet(), geometry.width(), geometry.height(),
                LivingBridgeRouteSearch.MAX_NODE_BUDGET);
        List<BlockPos> second = canonical.bridgeLandingFeet().equals(canonical.egressFeet())
                ? List.of(canonical.bridgeLandingFeet())
                : LivingBridgeRouteSearch.findRoute(level, canonical.bridgeLandingFeet(), canonical.egressFeet(),
                geometry.width(), geometry.height(), LivingBridgeRouteSearch.MAX_NODE_BUDGET);
        if (first.isEmpty() || second.isEmpty()) {
            if (state != null) {
                state.corridorCache.put(cacheKey, new CachedCorridor(null, level.getGameTime() + 20L));
            }
            if (SoundAttractMod.LOGGER.isDebugEnabled()) {
                List<BlockPos> localSolids = new ArrayList<>();
                BlockPos start = canonical.takeoffFeet();
                for (int x = start.getX() - 1; x <= start.getX() + 1; x++) {
                    for (int y = start.getY(); y <= start.getY() + 2; y++) {
                        for (int z = start.getZ() - 1; z <= start.getZ() + 1; z++) {
                            BlockPos cell = new BlockPos(x, y, z);
                            if (!level.getBlockState(cell).getCollisionShape(level, cell, CollisionContext.empty()).isEmpty()) localSolids.add(cell);
                        }
                    }
                }
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ROUTING)) {
                SoundAttractMod.LOGGER.debug("[LivingWallCoordinator] executable_corridor_unavailable start={} landing={} egress={} firstNodes={} secondNodes={} width={} height={}",
                        canonical.takeoffFeet(), canonical.bridgeLandingFeet(), canonical.egressFeet(), first.size(), second.size(),
                        geometry.width(), geometry.height());
                }
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ROUTING)) {
                    SoundAttractMod.LOGGER.debug("[LivingWallCoordinator] executable_corridor_local_solids cells={}", localSolids);
                }
                List<BlockPos> endpointSolids = new ArrayList<>();
                BlockPos landing = canonical.bridgeLandingFeet();
                for (int x = landing.getX() - 1; x <= landing.getX() + 1; x++) {
                    for (int y = landing.getY(); y <= landing.getY() + 2; y++) {
                        for (int z = landing.getZ() - 1; z <= landing.getZ() + 1; z++) {
                            BlockPos cell = new BlockPos(x, y, z);
                            if (!level.getBlockState(cell).getCollisionShape(level, cell, CollisionContext.empty()).isEmpty()) endpointSolids.add(cell);
                        }
                    }
                }
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ROUTING)) {
                    SoundAttractMod.LOGGER.debug("[LivingWallCoordinator] executable_corridor_endpoint_solids cells={}", endpointSolids);
                }
            }
            return null;
        }
        List<BlockPos> route = new ArrayList<>(first.size() + second.size() - 1);
        route.addAll(first);
        route.addAll(second.subList(1, second.size()));
        CanonicalFeetCorridor result = canonical.withCenterline(route);
        if (state != null) {
            state.corridorCache.put(cacheKey, new CachedCorridor(result, level.getGameTime() + 60L));
        }
        return result;
    }

    private static boolean orderedCorridorTransitionValid(CanonicalFeetCorridor corridor,
                                                          WallProfileScanner.TargetEvidence evidence,
                                                          BlockPos threatDestination, ConstructionScenario scenario) {
        if (scenario == null) return false;
        if (corridor != null) {
            BlockPos source = corridor.takeoffFeet();
            BlockPos landing = corridor.bridgeLandingFeet();
            BlockPos invasion = corridor.egressFeet();
            List<BlockPos> orderedHazards = hazardsWithinTransition(corridor.hazardCells(), source, invasion);
            if (landing != null && landing.equals(invasion) && invasion.equals(corridor.tacticalGoal())) {
                return orderedTerminalCorridorValid(source, invasion, corridor.centerlineCells(), scenario,
                        orderedHazards);
            }
            return orderedCenterlineSequenceValid(source, landing, invasion, corridor.centerlineCells(), scenario,
                    orderedHazards);
        }
        return false;
    }

    private static List<BlockPos> hazardsWithinTransition(List<BlockPos> hazards, BlockPos source, BlockPos endpoint) {
        if (hazards == null || hazards.isEmpty() || source == null || endpoint == null) return List.of();
        Vec3 origin = Vec3.atBottomCenterOf(source);
        Vec3 transition = Vec3.atBottomCenterOf(endpoint).subtract(origin);
        double length = transition.length();
        if (!Double.isFinite(length) || length < 0.001D) return List.of();
        Vec3 direction = transition.scale(1.0D / length);
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos hazard : hazards) {
            if (hazard == null) continue;
            double projection = Vec3.atBottomCenterOf(hazard).subtract(origin).dot(direction);
            if (Double.isFinite(projection) && projection <= length + 1.0E-6D) result.add(hazard.immutable());
        }
        return List.copyOf(result);
    }

    private static boolean orderedCenterlineSequenceValid(BlockPos takeoff, BlockPos bridgeLanding, BlockPos egress,
                                                          List<BlockPos> centerline, ConstructionScenario scenario,
                                                          List<BlockPos> hazards) {
        if (takeoff == null || bridgeLanding == null || egress == null) return false;
        if (takeoff.equals(bridgeLanding) || bridgeLanding.equals(egress) || takeoff.equals(egress)) return false;
        if (centerline == null || centerline.isEmpty()) return false;
        int takeoffIndex = exactCenterlineIndex(centerline, takeoff);
        int landingIndex = exactCenterlineIndex(centerline, bridgeLanding);
        int egressIndex = exactCenterlineIndex(centerline, egress);
        if (takeoffIndex < 0 || landingIndex < 0 || egressIndex < 0) return false;
        if (!(takeoffIndex < landingIndex && landingIndex < egressIndex)) return false;
        if (scenario == ConstructionScenario.HAZARD_AND_WALL || (hazards != null && !hazards.isEmpty())) {
            if (!orderedHazardWitnessesValid(takeoff, bridgeLanding, hazards)) return false;
            if (scenario == ConstructionScenario.HAZARD_AND_WALL && (hazards == null || hazards.isEmpty())) return false;
        }
        return true;
    }

    private static boolean orderedTerminalCorridorValid(BlockPos takeoff, BlockPos terminal,
                                                        List<BlockPos> centerline, ConstructionScenario scenario,
                                                        List<BlockPos> hazards) {
        if (takeoff == null || terminal == null || takeoff.equals(terminal)
                || centerline == null || centerline.isEmpty()) return false;
        int takeoffIndex = exactCenterlineIndex(centerline, takeoff);
        int terminalIndex = exactCenterlineIndex(centerline, terminal);
        if (takeoffIndex < 0 || terminalIndex <= takeoffIndex) return false;
        if (scenario == ConstructionScenario.HAZARD_AND_WALL && (hazards == null || hazards.isEmpty())) return false;
        return orderedHazardWitnessesValid(takeoff, terminal, hazards);
    }

    private static boolean orderedHazardWitnessesValid(BlockPos takeoff, BlockPos terminal,
                                                       List<BlockPos> hazards) {
        if (hazards == null || hazards.isEmpty()) return true;
        if (takeoff == null || terminal == null) return false;
        Vec3 origin = Vec3.atBottomCenterOf(takeoff);
        Vec3 transition = Vec3.atBottomCenterOf(terminal).subtract(origin);
        double length = transition.length();
        if (!Double.isFinite(length) || length < 0.001D) return false;
        Vec3 direction = transition.scale(1.0D / length);
        double terminalProjection = length;
        for (BlockPos hazard : hazards) {
            if (hazard == null) continue;
            double projection = Vec3.atBottomCenterOf(hazard).subtract(origin).dot(direction);
            if (!Double.isFinite(projection) || projection > terminalProjection + 1.0E-6D) return false;
        }
        return true;
    }

    private static int exactCenterlineIndex(List<BlockPos> centerline, BlockPos target) {
        if (centerline == null || target == null) return -1;
        for (int index = 0; index < centerline.size(); index++) {
            if (target.equals(centerline.get(index))) return index;
        }
        return -1;
    }

    private static BlockPos selectedCorridorEndpoint(WallProfileScanner.TargetEvidence evidence) {
        if (evidence == null) return null;
        return evidence.invasionSurface() != null ? evidence.invasionSurface() : evidenceLandingSurface(evidence);
    }

    private static double projectionAlongPivotCorridor(BlockPos pivot, BlockPos corridorEnd, BlockPos sample) {
        if (pivot == null || corridorEnd == null || sample == null) return Double.NEGATIVE_INFINITY;
        Vec3 base = Vec3.atBottomCenterOf(pivot);
        Vec3 direction = Vec3.atBottomCenterOf(corridorEnd).subtract(base);
        if (direction.lengthSqr() < 1.0E-6D) return Double.NEGATIVE_INFINITY;
        return Vec3.atBottomCenterOf(sample).subtract(base).dot(direction.normalize());
    }

    private static int corridorIntervalCells(CanonicalFeetCorridor corridor) {
        if (corridor == null || corridor.centerlineCells().isEmpty()) return Integer.MAX_VALUE;
        return corridor.centerlineCells().size();
    }

    private static boolean corridorBodyClear(ServerLevel level, CanonicalFeetCorridor corridor, BodyGeometry geometry,
                                             ConstructionScenario scenario) {
        if (level == null || corridor == null
                || geometry == null || geometry.width() <= 0.0D || geometry.height() <= 0.0D) {
            return false;
        }
        double radius = Math.max(0.31D, geometry.width() * 0.5D);
        AABB body = new AABB(-radius, 0.0D, -radius, radius, geometry.height(), radius);
        List<BlockPos> centerline = corridor.centerlineCells();
        if (centerline.size() < 2) return true;
        for (int index = 1; index < centerline.size(); index++) {
            Vec3 start = Vec3.atBottomCenterOf(centerline.get(index - 1));
            Vec3 end = Vec3.atBottomCenterOf(centerline.get(index));
            if (!sweptBodyClear(level, body, start, end, scenario)) return false;
        }
        return true;
    }

    private static boolean sweptBodyClear(ServerLevel level, AABB body, Vec3 start, Vec3 end,
                                          ConstructionScenario scenario) {
        if (level == null || body == null || start == null || end == null) return false;
        BlockPos min = BlockPos.containing(Math.floor(Math.min(start.x, end.x) - body.getXsize()),
                Math.floor(Math.min(start.y, end.y)),
                Math.floor(Math.min(start.z, end.z) - body.getZsize()));
        BlockPos max = BlockPos.containing(Math.ceil(Math.max(start.x, end.x) + body.getXsize()),
                Math.ceil(Math.max(start.y, end.y) + body.getYsize()),
                Math.ceil(Math.max(start.z, end.z) + body.getZsize()));
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos cell = new BlockPos(x, y, z);
                    if (!level.isLoaded(cell)) {
                        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] corridor_swept_blocked reason=UNLOADED cell={} start={} end={} scenario={}", cell, start, end, scenario);
                        return false;
                    }
                    BlockState state = level.getBlockState(cell);
                    if (state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
                        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] corridor_swept_blocked reason=LAVA cell={} start={} end={} scenario={}", cell, start, end, scenario);
                        return false;
                    }
                    VoxelShape shape = state.getCollisionShape(level, cell, CollisionContext.empty());
                    if (!shape.isEmpty()) {
                        double radius = Math.max(0.31D, body.getXsize() * 0.5D);
                        double height = body.getYsize();
                        for (AABB box : shape.toAabbs()) {
                            AABB expanded = new AABB(
                                    cell.getX() + box.minX - radius,
                                    cell.getY() + box.minY - height,
                                    cell.getZ() + box.minZ - radius,
                                    cell.getX() + box.maxX + radius,
                                    cell.getY() + box.maxY,
                                    cell.getZ() + box.maxZ + radius);
                            if (expanded.clip(start, end).isPresent()) {
                                SoundAttractMod.LOGGER.info("[LivingWallCoordinator] corridor_swept_blocked reason=COLLISION cell={} block={} start={} end={} scenario={}", cell, state, start, end, scenario);
                                return false;
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    private static boolean hasCompleteConstructionContract(BlockPos seed, BlockPos target,
                                                           WallProfileScanner.TargetEvidence evidence) {
        if (seed == null || target == null || evidence == null) return false;
        if (evidence.anchor() == null || evidence.wallFace() == null || evidence.supportCell() == null) return false;
        if (evidence.sourceBucket() == null || evidence.targetBucket() == null || evidence.scenario() == null) return false;
        if (parseConstructionScenario(evidence.scenario()) == null) return false;
        if (evidence.hazardCells() == null || !hasProfileCorridorEvidence(evidence)) return false;
        return bucketKey(evidence.targetBucket()) == bucketKey(target);
    }

    private static ConstructionScenario scenarioFromEvidence(WallProfileScanner.TargetEvidence evidence, List<BlockPos> hazards) {
        ConstructionScenario scenario = evidence == null ? null : parseConstructionScenario(evidence.scenario());
        if (scenario == null || scenario == ConstructionScenario.NONE) return ConstructionScenario.NONE;
        if (scenario == ConstructionScenario.HAZARD_AND_WALL) return ConstructionScenario.NONE;
        return scenario;
    }

    private static LivingWallBridgeAdapterPlanner.Candidate plannerCandidateFor(ServerLevel level, CanonicalFeetCorridor corridor,
                                                                                BlockPos source,
                                                                                WallProfileScanner.TargetEvidence evidence,
                                                                                ConstructionScenario scenario,
                                                                                BodyGeometry geometry) {
        if (corridor == null || evidence == null || scenario == null) {
            return null;
        }
        BlockPos pivot = corridor.pivot();
        BlockPos candidate = corridor.egressFeet();
        int hazardCells = evidence.hazardCells() == null ? 0 : evidence.hazardCells().size();
        int hazardTransitions = evidence.hazardKinds() == null ? 0 : evidence.hazardKinds().size();
        int connectedStructuralCells = evidence.structuralCells() == null ? 0 : evidence.structuralCells().size();
        double horizontalStep = lateralSpacingForBodyWidth(geometry.width());
        double verticalStep = requiresTilt(scenario) ? layerSpacingForBodyHeight(geometry.height())
                : Math.max(0.05D, geometry.height() + STRUCTURAL_NON_OVERLAP_MARGIN);
        int stations = requiresTilt(scenario)
                ? requiredLayerCountForTilt(pivot, tiltStructuralEnd(candidate, corridor), corridor,
                        horizontalStep, verticalStep)
                : requiredStationsForRoute(pivot, candidate, scenario, horizontalStep, verticalStep, corridor);
        int bodyLimit = LivingWallConfig.MAX_BODIES.get();
        int columns = scenario == ConstructionScenario.WALL_LEDGE || scenario == ConstructionScenario.DROP_TO_LEDGE || geometry.width() + CONTACT_EPSILON < MIN_COLUMN_WIDTH
                ? chooseColumnCount(Integer.MAX_VALUE, stations, bodyLimit)
                : chooseColumnCount(stations * FORMATION_COLUMNS, stations, bodyLimit);
        int bodyCount = columns == 0 ? bodyLimit + 1 : stations * columns;
        int requiredCellCount = corridor.centerlineCells().size();
        int routePhaseCount = (int) corridor.phases().stream().filter(phase -> phase.endIndex() > phase.startIndex()).count();
        int reachabilityProbeCount = 0;
        int approachDistance = source == null ? 0 : source.distManhattan(corridor.takeoffFeet());
        BlockPos threatDestination = evidence.targetBucket() != null ? evidence.targetBucket() : evidence.wallFace();
        int pivotThreatDistance = threatDestination == null ? 0 : pivot.distManhattan(threatDestination);
        int endpointThreatDistance = threatDestination == null ? 0 : candidate.distManhattan(threatDestination);
        boolean supportedEgress = isSafeLanding(level, candidate);
        boolean shortDropEgress = scenario == ConstructionScenario.DROP_TO_LEDGE && supportedEgress;
        boolean safeFallEgress = scenario == ConstructionScenario.JUMP_BRIDGE && isInvasionEndpoint(level, pivot, candidate);
        double length3d = bridgeLength(pivot, candidate);
        return new LivingWallBridgeAdapterPlanner.Candidate(
                Objects.toString(candidate),
                switch (scenario) {
                    case WALL_LEDGE -> LivingWallBridgeAdapterPlanner.AdapterKind.PILLAR_TILT;
                    case DROP_TO_LEDGE -> LivingWallBridgeAdapterPlanner.AdapterKind.DROP;
                    case JUMP_BRIDGE -> LivingWallBridgeAdapterPlanner.AdapterKind.JUMP;
                    default -> LivingWallBridgeAdapterPlanner.AdapterKind.DIRECT;
                },
                bodyCount, length3d, hazardCells, 0, 0, 0, 0, 0, requiredCellCount,
                routePhaseCount, reachabilityProbeCount, hazardTransitions, approachDistance,
                pivotThreatDistance, endpointThreatDistance, supportedEgress, shortDropEgress,
                safeFallEgress, connectedStructuralCells, pivot.asLong(), corridor.tacticalGoal().asLong(),
                candidate.asLong());
    }

    static java.util.Optional<LivingWallBridgeAdapterPlanner.AdapterSpec> adapterSpecForScenario(ConstructionScenario scenario) {
        if (scenario == null) return java.util.Optional.empty();
        return switch (scenario) {
            case NONE -> LivingWallBridgeAdapterPlanner.adapterFor("NONE", LivingWallBridgeAdapterPlanner.AdapterKind.DIRECT);
            case DROP_TO_LEDGE -> LivingWallBridgeAdapterPlanner.adapterFor("DROP_TO_LEDGE", LivingWallBridgeAdapterPlanner.AdapterKind.DROP);
            case JUMP_BRIDGE -> LivingWallBridgeAdapterPlanner.adapterFor("JUMP_BRIDGE", LivingWallBridgeAdapterPlanner.AdapterKind.JUMP);
            case WALL_LEDGE -> LivingWallBridgeAdapterPlanner.adapterFor("WALL_LEDGE", LivingWallBridgeAdapterPlanner.AdapterKind.PILLAR_TILT);
            case HAZARD_AND_WALL -> java.util.Optional.empty();
        };
    }

    private static boolean acceptedPlanMatchesRequest(ServerLevel level, SitePlan plan, ConstructionRequest request) {
        if (level == null || plan == null || request == null || request.evidence() == null || plan.pivot() == null || plan.anchor() == null) return false;
        if (finalizedSourceAdmissionReason(plan, request.sourceCenter(), request.sourceBucket()) != null) return false;
        WallProfileScanner.TargetEvidence evidence = request.evidence();
        boolean corridorMatch = true;
        if (request.corridor() != null) {
            corridorMatch = plan.corridor() != null
                    && Objects.equals(plan.corridor().corridorId(), request.corridor().corridorId());
        }
        String admissionReason = requestEvidenceAdmissionReason(level, plan.pivot(), request);
        boolean admissionMatch = admissionReason == null;
        ConstructionObservation observation = request.observation();
        List<BlockPos> canonical = observation != null
                ? List.copyOf(observation.hazards())
                : hazardsWithinTransition(evidence.hazardCells(), plan.pivot(), plan.anchor());
        ConstructionScenario evidenceScenario = observation != null
                ? observation.scenario()
                : scenarioFromEvidence(evidence, canonical);
        boolean scenarioMatch = plan.scenario() == evidenceScenario;
        boolean hazardListMatch = canonical.equals(plan.hazardCells());
        boolean committedCorridorMatch = committedCorridorMatchesEvidence(plan, evidence);
        Vec3 expected = Vec3.atBottomCenterOf(plan.spanEnd()).subtract(Vec3.atBottomCenterOf(plan.pivot()));
        double length = Math.sqrt(expected.x * expected.x + expected.z * expected.z);
        boolean lengthMatch = length >= 0.001D;
        boolean forwardMatch = lengthMatch && Math.abs(plan.forward().x - expected.x / length) < 1.0E-6D
                && Math.abs(plan.forward().z - expected.z / length) < 1.0E-6D
                ;
        boolean anchorProgress = hasWallProgress(plan.pivot(), plan.anchor(), evidence.wallFace());
        boolean spanProgress = hasWallProgress(plan.pivot(), plan.spanEnd(), evidence.wallFace());
        boolean utilityMatch = candidateHasTacticalUtility(level, plan.pivot(), plan.anchor(), plan.spanEnd(), request.threatDestination(), evidence, plan.scenario());
        boolean accepted = corridorMatch && admissionMatch && scenarioMatch && hazardListMatch
                && committedCorridorMatch && forwardMatch && anchorProgress && spanProgress && utilityMatch;
        if (!accepted && DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_CONSTRUCTION)) {
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] candidate_frame_conjuncts corridor={} admission={} scenario={} hazards={} committed={} length={} forward={} anchorProgress={} spanProgress={} utility={} admissionReason={} canonicalHazards={} planHazards={}",
                    corridorMatch, admissionMatch, scenarioMatch, hazardListMatch, committedCorridorMatch,
                    lengthMatch, forwardMatch, anchorProgress, spanProgress, utilityMatch, admissionReason,
                    canonical, plan.hazardCells());
        }
        return accepted;
    }

    private static String finalizedSourceAdmissionReason(SitePlan plan, Vec3 sourceCenter, BlockPos sourceBucket) {
        if (plan == null || plan.pivot() == null || plan.spanEnd() == null) return "source_frame_invalid";
        Vec3 forward = LivingBridgeSafeBaseRoute.verifiedForward(plan.pivot(), plan.spanEnd(), plan.forward());
        return forward == null ? "source_frame_invalid" : null;
    }

    static String finalizedSourceAdmissionReasonForTest(SitePlan plan, Vec3 sourceCenter, BlockPos sourceBucket) {
        return finalizedSourceAdmissionReason(plan, sourceCenter, sourceBucket);
    }

    static boolean hasThreatProgressingSpan(ConstructionScenario scenario, BlockPos pivot, BlockPos spanPoint,
                                            BlockPos threatDestination) {
        return scenario != null && scenario != ConstructionScenario.NONE
                && WallProfileScanner.isThreatFacing(pivot, spanPoint, threatDestination);
    }

    static boolean candidateHasTacticalUtility(ServerLevel level, BlockPos pivot, BlockPos anchor, BlockPos spanEnd,
                                               BlockPos threatDestination, WallProfileScanner.TargetEvidence evidence,
                                               ConstructionScenario scenario) {
        if (level == null || pivot == null || anchor == null || spanEnd == null
                || threatDestination == null || evidence == null || scenario == null
                || scenario == ConstructionScenario.NONE) {
            return false;
        }
        if (!hasProfileCorridorEvidence(evidence) || !Objects.equals(anchor, tacticalGoalForEvidence(evidence))
                || !Objects.equals(spanEnd, evidenceInvasionSurface(evidence))) {
            return false;
        }
        return hasWallProgress(pivot, anchor, evidence.wallFace())
                && hasWallProgress(pivot, spanEnd, evidence.wallFace())
                && hasThreatProgress(pivot, anchor, threatDestination)
                && hasThreatProgress(pivot, spanEnd, threatDestination);
    }

    static boolean hasThreatProgress(BlockPos pivot, BlockPos endpoint, BlockPos threat) {
        if (pivot == null || endpoint == null || threat == null) return false;
        int pivotThreatDistSq = horizontalDistanceSqr(pivot, threat);
        if (endpoint.getX() == pivot.getX() && endpoint.getZ() == pivot.getZ()) {
            return Math.abs(endpoint.getY() - threat.getY()) < Math.abs(pivot.getY() - threat.getY());
        }
        if (pivotThreatDistSq <= 2) {
            return Math.abs(endpoint.getY() - threat.getY()) < Math.abs(pivot.getY() - threat.getY());
        }
        int endpointThreatDistSq = horizontalDistanceSqr(endpoint, threat);
        if (endpointThreatDistSq >= pivotThreatDistSq) return false;
        double progress = WallProfileScanner.threatProgressProjection(pivot, endpoint, threat);
        return progress >= 0.5D;
    }

    static boolean hasWallProgress(BlockPos pivot, BlockPos endpoint, BlockPos wallFace) {
        if (pivot == null || endpoint == null || wallFace == null) return false;
        Vec3 wall = Vec3.atBottomCenterOf(wallFace).subtract(Vec3.atBottomCenterOf(pivot));
        double length = Math.sqrt(wall.x * wall.x + wall.z * wall.z);
        if (length < 1.0E-6D) return false;
        double progress = ((endpoint.getX() - pivot.getX()) * wall.x
                + (endpoint.getZ() - pivot.getZ()) * wall.z) / length;
        return progress >= 0.5D;
    }

    private static boolean scenarioMatchesEvidence(ServerLevel level, BlockPos pivot, BlockPos anchor,
                                                   WallProfileScanner.TargetEvidence evidence, Set<BlockPos> hazards,
                                                   ConstructionScenario scenario) {
        if (level == null || pivot == null || anchor == null || evidence == null || scenario == null) return false;
        if (!anchor.equals(evidence.anchor())) return false;
        if (scenario != classifyScenario(level, pivot, anchor, hazards)) return false;
        boolean endpointValid = scenario == ConstructionScenario.WALL_LEDGE
                ? isSafeLanding(level, anchor)
                : isInvasionEndpoint(level, pivot, anchor);
        return endpointValid && isSafeSite(level, pivot);
    }

    private static ConstructionScenario parseConstructionScenario(String scenario) {
        if (scenario == null) return null;
        try {
            return ConstructionScenario.valueOf(scenario);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static void countLocalRejection(Map<String, Integer> rejections, String reason) {
        if (rejections != null) rejections.merge(reason, 1, Integer::sum);
    }

    static boolean isCandidateElevationCompatible(int sourceY, int candidateY) {
        return Math.abs(candidateY - sourceY) <= MAX_SITE_ELEVATION_DELTA;
    }

    private static boolean isSafeApproachCell(ServerLevel level, BlockPos cell) {
        if (level == null || cell == null) return false;
        PerLevelState state = state(level);
        if (state != null) {
            long now = level.getGameTime();
            if (state.safeApproachCellCacheTick != now) {
                state.safeApproachCellCache.clear();
                state.safeApproachCellCacheTick = now;
            }
            long posKey = cell.asLong();
            if (state.safeApproachCellCache.containsKey(posKey)) {
                return state.safeApproachCellCache.get(posKey);
            }
            boolean result = computeSafeApproachCell(level, cell);
            state.safeApproachCellCache.put(posKey, result);
            return result;
        }
        return computeSafeApproachCell(level, cell);
    }

    private static boolean computeSafeApproachCell(ServerLevel level, BlockPos cell) {
        if (!isSafeSite(level, cell) || !hasSafeStandingEnvelope(level, cell)) return false;
        BlockState support = level.getBlockState(cell.below());
        return support.getFluidState().isEmpty() && !support.is(BlockTags.TRAPDOORS)
                && !(support.getBlock() instanceof TrapDoorBlock);
    }

    private static boolean hasSafeStandingEnvelope(ServerLevel level, BlockPos cell) {
        if (level == null || cell == null) return false;
        PerLevelState state = state(level);
        if (state != null) {
            long now = level.getGameTime();
            if (state.standingEnvelopeCacheTick != now) {
                state.standingEnvelopeCache.clear();
                state.standingEnvelopeCacheTick = now;
            }
            long posKey = cell.asLong();
            if (state.standingEnvelopeCache.containsKey(posKey)) {
                return state.standingEnvelopeCache.get(posKey);
            }
            boolean result = computeSafeStandingEnvelope(level, cell);
            state.standingEnvelopeCache.put(posKey, result);
            return result;
        }
        return computeSafeStandingEnvelope(level, cell);
    }

    private static boolean computeSafeStandingEnvelope(ServerLevel level, BlockPos cell) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos probe = cell.offset(dx, 0, dz);
                if (!level.isLoaded(probe) || !level.isLoaded(probe.below())) return false;
                if (isOpenTrapdoor(level.getBlockState(probe))
                        || level.getBlockState(probe).getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
                        || level.getBlockState(probe.below()).getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
                    return false;
                }
                if (dx != 0 || dz != 0) {
                    int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            probe.getX(), probe.getZ());
                    if (cell.getY() - surfaceY > 2) return false;
                }
            }
        }
        return true;
    }

    private static BlockPos resolveLocalSafeSurface(ServerLevel level, int x, int z, int referenceY) {
        if (level == null) return null;
        for (int probe = 0; probe < 5; probe++) {
            BlockPos cell = safeSurfaceAt(level, x, z, referenceY + localSurfaceProbeOffset(probe));
            if (cell != null) return cell;
        }
        int heightmapY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        for (int offset = 0; offset <= 1; offset++) {
            BlockPos cell = safeFallbackSurfaceAt(level, x, z, referenceY, heightmapY + offset);
            if (cell != null) return cell;
            if (offset != 0) {
                cell = safeFallbackSurfaceAt(level, x, z, referenceY, heightmapY - offset);
                if (cell != null) return cell;
            }
        }
        return null;
    }

    static int localSurfaceProbeOffset(int probe) {
        return switch (probe) {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> -1;
            case 3 -> 2;
            case 4 -> -2;
            default -> Integer.MAX_VALUE;
        };
    }

    static boolean isWithinLocalSurfaceFallbackCap(int referenceY, int candidateY) {
        return Math.abs(candidateY - referenceY) <= 10;
    }

    private static BlockPos safeSurfaceAt(ServerLevel level, int x, int z, int y) {
        BlockPos cell = new BlockPos(x, y, z);
        return level.isLoaded(cell) && level.isLoaded(cell.below()) && level.isLoaded(cell.above())
                && isSafeApproachCell(level, cell) ? cell : null;
    }

    private static BlockPos safeFallbackSurfaceAt(ServerLevel level, int x, int z, int referenceY, int y) {
        return isWithinLocalSurfaceFallbackCap(referenceY, y) ? safeSurfaceAt(level, x, z, y) : null;
    }

    private static LocalIntakeWindowResolution intakeWindows(ServerLevel level, BlockPos pivot, Vec3 forward,
                                                              List<BlockPos> hazards, Set<BlockPos> structuralCells,
                                                              List<BlockPos> assemblySlots) {
        List<IntakeWindow> windows = new ArrayList<>();
        int unresolved = 0;
        int rejectedElevation = 0, rejectedUnsafe = 0, rejectedHazard = 0, rejectedSide = 0, rejectedClear = 0;
        for (BlockPos candidate : intakeWindowCandidates(pivot, forward)) {
            if (!level.isLoaded(candidate)) continue;
            BlockPos cell = resolveLocalSafeSurface(level, candidate.getX(), candidate.getZ(), pivot.getY());
            if (cell == null) {
                unresolved++;
                continue;
            }
            if (!isIntakeWindowElevationCompatible(pivot.getY(), cell.getY())) { rejectedElevation++; continue; }
            if (!isSafeApproachCell(level, cell)) { rejectedUnsafe++; continue; }
            if (isNearHazard(level, cell, hazards, forward)) { rejectedHazard++; continue; }
            if (!isOnApproachSide(pivot, cell, forward, new java.util.LinkedHashSet<>(hazards))) { rejectedSide++; continue; }
            if (!isIntakeWindowClear(cell, structuralCells, assemblySlots)) { rejectedClear++; continue; }
            IntakeWindow window = new IntakeWindow(cell);
            if (!windows.contains(window)) windows.add(window);
            if (windows.size() >= PATH_SURVEY_QUOTA) break;
        }
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ADMISSION) && windows.isEmpty()) {
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] intake_window_search pivot={} candidates={} unresolved={} elevation={} unsafe={} hazard={} side={} clear={} hazards={} assemblySlots={}",
                    pivot, intakeWindowCandidates(pivot, forward).size(), unresolved, rejectedElevation, rejectedUnsafe,
                    rejectedHazard, rejectedSide, rejectedClear, hazards == null ? 0 : hazards.size(),
                    assemblySlots == null ? 0 : assemblySlots.size());
        }
        return new LocalIntakeWindowResolution(List.copyOf(windows), unresolved);
    }

    static boolean isIntakeWindowElevationCompatible(int pivotY, int feederY) {
        return feederY >= pivotY && feederY <= pivotY + MAX_SITE_ELEVATION_DELTA;
    }

    static List<BlockPos> intakeWindowCandidates(BlockPos pivot, Vec3 forward) {
        if (pivot == null || forward == null || forward.lengthSqr() < 0.001D) return List.of();
        Vec3 normalized = new Vec3(forward.x, 0.0D, forward.z).normalize();
        List<BlockPos> cells = new ArrayList<>();
        for (int dx = -INTAKE_WINDOW_MAX_RADIUS; dx <= INTAKE_WINDOW_MAX_RADIUS; dx++) {
            for (int dz = -INTAKE_WINDOW_MAX_RADIUS; dz <= INTAKE_WINDOW_MAX_RADIUS; dz++) {
                int distanceSqr = dx * dx + dz * dz;
                if (distanceSqr < INTAKE_WINDOW_MIN_RADIUS * INTAKE_WINDOW_MIN_RADIUS
                        || distanceSqr > INTAKE_WINDOW_MAX_RADIUS * INTAKE_WINDOW_MAX_RADIUS
                        || dx * normalized.x + dz * normalized.z > 1.0E-6D) continue;
                cells.add(pivot.offset(dx, 0, dz));
            }
        }
        cells.sort(Comparator.comparingDouble((BlockPos cell) -> intakeWindowAlignment(pivot, cell, normalized))
                .thenComparingInt(cell -> horizontalDistanceSqr(pivot, cell))
                .thenComparingLong(BlockPos::asLong));
        return List.copyOf(cells);
    }

    private static double intakeWindowAlignment(BlockPos pivot, BlockPos cell, Vec3 forward) {
        double dx = cell.getX() - pivot.getX();
        double dz = cell.getZ() - pivot.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        return distance < 0.001D ? 1.0D : (dx * forward.x + dz * forward.z) / distance;
    }

    static int horizontalDistanceSqr(BlockPos first, BlockPos second) {
        int dx = first.getX() - second.getX();
        int dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    static boolean isIntakeWindowClear(BlockPos feeder, Set<BlockPos> structuralCells, List<BlockPos> assemblySlots) {
        if (feeder == null || structuralCells == null || assemblySlots == null || structuralCells.contains(feeder)) return false;
        return true;
    }

    static boolean occupiesIntakeClearance(BlockPos slot, List<IntakeWindow> windows) {
        if (slot == null || windows == null) return false;
        Vec3 position = Vec3.atBottomCenterOf(slot);
        for (IntakeWindow window : windows) {
            if (window != null && window.feederCell() != null
                    && position.distanceToSqr(Vec3.atBottomCenterOf(window.feederCell()))
                    <= ASSEMBLY_ARRIVAL_DISTANCE_SQR) return true;
        }
        return false;
    }

    static List<BlockPos> assemblySlotCandidates(BlockPos pivot, Vec3 forward) {
        if (pivot == null || forward == null || forward.lengthSqr() < 0.001D) return List.of();
        Vec3 normalized = new Vec3(forward.x, 0.0D, forward.z).normalize();
        Vec3 side = new Vec3(-normalized.z, 0.0D, normalized.x);
        int radius = Math.max(2, configOrDefault(LivingWallConfig.ASSEMBLY_SLOT_RADIUS, 8));
        int limit = Math.max(1, configOrDefault(LivingWallConfig.MAX_ASSEMBLY_SLOTS, 16));
        List<BlockPos> cells = new ArrayList<>();
        for (int back = 1; back <= radius && cells.size() < limit; back++) {
            for (int lateral = -radius; lateral <= radius && cells.size() < limit; lateral++) {
                Vec3 offset = normalized.scale(-back).add(side.scale(lateral));
                BlockPos cell = BlockPos.containing(Vec3.atBottomCenterOf(pivot).add(offset));
                if (!cell.equals(pivot) && !cells.contains(cell)) {
                    cells.add(cell);
                }
            }
        }
        return List.copyOf(cells);
    }

    private static List<BlockPos> assemblySlotCandidates(ServerLevel level, BlockPos pivot, Vec3 forward,
                                                          List<BlockPos> hazards) {
        if (level == null || pivot == null || forward == null) return List.of();
        Set<BlockPos> hazardSet = new java.util.LinkedHashSet<>(hazards == null ? List.of() : hazards);
        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -ROOT_ZONE_RADIUS; dx <= ROOT_ZONE_RADIUS; dx++) {
            for (int dz = -ROOT_ZONE_RADIUS; dz <= ROOT_ZONE_RADIUS; dz++) {
                int distanceSqr = dx * dx + dz * dz;
                if (distanceSqr == 0 || distanceSqr > ROOT_ZONE_RADIUS * ROOT_ZONE_RADIUS) continue;
                BlockPos surface = resolveLocalSafeSurface(level, pivot.getX() + dx, pivot.getZ() + dz, pivot.getY());
                if (surface == null || !isSafeApproachCell(level, surface)
                        || Math.abs(surface.getY() - pivot.getY()) > ROOT_ZONE_MAX_HEIGHT_SPREAD
                        || isNearHazard(level, surface, hazards, forward)
                        || !isOnApproachSide(pivot, surface, forward, hazardSet)) continue;
                if (!candidates.contains(surface)) candidates.add(surface);
            }
        }
        candidates.sort(Comparator.comparingInt((BlockPos cell) -> horizontalDistanceSqr(pivot, cell))
                .thenComparingInt(cell -> Math.abs(cell.getY() - pivot.getY()))
                .thenComparingLong(BlockPos::asLong));
        if (candidates.isEmpty()) return List.of();
        List<BlockPos> root = new ArrayList<>();
        root.add(candidates.get(0));
        boolean changed = true;
        while (changed) {
            changed = false;
            for (BlockPos candidate : candidates) {
                if (root.contains(candidate)) continue;
                if (root.stream().anyMatch(existing -> isConnectedRootCandidate(existing, candidate))) {
                    root.add(candidate);
                    changed = true;
                }
            }
        }
        int limit = Math.max(1, configOrDefault(LivingWallConfig.MAX_ASSEMBLY_SLOTS, 16));
        if (root.size() < Math.min(ROOT_ZONE_MIN_SLOTS, limit)) return List.of();
        return List.copyOf(root.subList(0, Math.min(limit, root.size())));
    }

    private static boolean isConnectedRootCandidate(BlockPos first, BlockPos second) {
        if (first == null || second == null) return false;
        int dx = Math.abs(first.getX() - second.getX());
        int dz = Math.abs(first.getZ() - second.getZ());
        return Math.max(dx, dz) <= 2 && Math.abs(first.getY() - second.getY()) <= ROOT_ZONE_MAX_HEIGHT_SPREAD;
    }

    private static int configOrDefault(net.minecraftforge.common.ForgeConfigSpec.IntValue value, int fallback) {
        try {
            return value == null ? fallback : value.get();
        } catch (IllegalStateException ignored) {
            return fallback;
        }
    }

    private static double configOrDefault(net.minecraftforge.common.ForgeConfigSpec.DoubleValue value, double fallback) {
        try {
            return value == null ? fallback : value.get();
        } catch (IllegalStateException ignored) {
            return fallback;
        }
    }

    private static int assemblyTimeoutTicks() {
        return Math.max(100, configOrDefault(LivingWallConfig.ASSEMBLY_TIMEOUT_TICKS, 2400));
    }

    private static boolean hasActiveAssemblyWorkers(ServerLevel level, Formation formation) {
        if (level == null || formation == null) return false;
        if (formation.activeStructuralWorkerId != null) {
            Mob mob = getMob(level, formation.activeStructuralWorkerId);
            if (mob != null && mob.isAlive() && !mob.isRemoved()) return true;
        }
        if (!formation.pendingBuilderIds.isEmpty()) {
            for (UUID id : formation.pendingBuilderIds) {
                Mob mob = getMob(level, id);
                if (mob != null && mob.isAlive() && !mob.isRemoved()) return true;
            }
        }
        if (!formation.structuralApproachReservations.isEmpty()) {
            for (UUID id : formation.structuralApproachReservations.keySet()) {
                Mob mob = getMob(level, id);
                if (mob != null && mob.isAlive() && !mob.isRemoved()) return true;
            }
        }
        if (!formation.mobTickets.isEmpty()) {
            for (Map.Entry<UUID, MobTicket> entry : formation.mobTickets.entrySet()) {
                MobTicket ticket = entry.getValue();
                if (ticket != null && (ticket.state == MobTicketState.MOVING_TO_ASSEMBLY || ticket.state == MobTicketState.STAGING_AT_ASSEMBLY)) {
                    Mob mob = getMob(level, entry.getKey());
                    if (mob != null && mob.isAlive() && !mob.isRemoved()) return true;
                }
            }
        }
        return false;
    }

    private static ConstructionScenario classifyScenario(ServerLevel level, BlockPos pivot, BlockPos anchor,
                                                         Set<BlockPos> hazards) {
        return classifyCanonicalScenario(level, pivot, anchor, hazards);
    }

    private static boolean isSheerWallFace(ServerLevel level, BlockPos anchor, int dx, int dz) {
        if (level == null || anchor == null) return false;
        if (!level.isLoaded(anchor)) return false;
        BlockPos aboveTwo = anchor.above(2);
        if (level.isLoaded(aboveTwo) && !level.getBlockState(aboveTwo).getCollisionShape(level, aboveTwo).isEmpty()) return true;
        if (dx == 0 && dz == 0) return false;
        boolean wallBlocked = false;
        int[][] steps = (dx != 0 && dz != 0)
                ? new int[][] {{dx, dz}, {dx, 0}, {0, dz}}
                : new int[][] {{dx, dz}};
        for (int[] step : steps) {
            BlockPos forward = anchor.offset(step[0], 0, step[1]);
            if (!level.isLoaded(forward) || !level.isLoaded(forward.above())) continue;
            BlockState forwardFeet = level.getBlockState(forward);
            BlockState forwardHead = level.getBlockState(forward.above());
            if (!forwardFeet.getCollisionShape(level, forward).isEmpty()
                    && !forwardHead.getCollisionShape(level, forward.above()).isEmpty()) {
                wallBlocked = true;
                break;
            }
        }
        return wallBlocked;
    }

    private static ConstructionScenario classifyCanonicalScenario(ServerLevel level, BlockPos pivot, BlockPos anchor,
                                                         Set<BlockPos> hazards) {
        if (level == null || pivot == null || anchor == null) {
            return ConstructionScenario.NONE;
        }
        boolean wallLedge = hasWallLedge(level, pivot, anchor);
        int verticalDelta = anchor.getY() - pivot.getY();
        double horizontalDist = horizontalDistance(pivot, anchor);
        double maxPillarTiltDist = configOrDefault(LivingWallConfig.MAX_PILLAR_TILT_DISTANCE, 6.25D);
        ConstructionScenario scenario = ConstructionScenario.NONE;
        int dx = Integer.compare(anchor.getX(), pivot.getX());
        int dz = Integer.compare(anchor.getZ(), pivot.getZ());
        boolean sheerWall = isSheerWallFace(level, anchor, dx, dz);
        if (verticalDelta >= 2 && horizontalDist <= maxPillarTiltDist && (wallLedge || anchor.getY() - pivot.getY() >= 2)
                && !sheerWall && hasLandingAndEgress(level, anchor, pivot, anchor)) {
            if (hasBridgeNeed(level, pivot, anchor)) {
                scenario = ConstructionScenario.WALL_LEDGE;
            }
        } else if (!sheerWall && hasLandingAndEgress(level, anchor, pivot, anchor) && hasChasmGap(level, pivot, anchor)) {
            if (verticalDelta >= -3 && verticalDelta <= 2) {
                scenario = ConstructionScenario.JUMP_BRIDGE;
            } else if (verticalDelta < -3 && horizontalDist <= 3.0D && Math.abs(verticalDelta) >= horizontalDist
                    && isSupportedDrop(pivot, anchor, captureDropLimitSnapshot(level.getGameTime()).maxDropBridgeLength())
                    && (isGenuineDropTraversal(level, pivot, anchor) || isInvasionEndpoint(level, pivot, anchor))) {
                scenario = ConstructionScenario.DROP_TO_LEDGE;
            }
        } else if (!sheerWall && verticalDelta < 0
                && isSupportedDrop(pivot, anchor, captureDropLimitSnapshot(level.getGameTime()).maxDropBridgeLength())
                && (isGenuineDropTraversal(level, pivot, anchor) || isInvasionEndpoint(level, pivot, anchor))) {
            scenario = ConstructionScenario.DROP_TO_LEDGE;
        } else if (!sheerWall && Math.abs(verticalDelta) <= 1 && hasLandingAndEgress(level, anchor, pivot, anchor)) {
            if (hasBridgeNeed(level, pivot, anchor)) {
                scenario = ConstructionScenario.JUMP_BRIDGE;
            }
        }
        return scenario;
    }

    static boolean isElevatedWallLedge(BlockPos pivot, BlockPos anchor) {
        return pivot != null && anchor != null && anchor.getY() > pivot.getY();
    }

    private static boolean isGenuineDropTraversal(ServerLevel level, BlockPos pivot, BlockPos anchor) {
        if (level == null || pivot == null || anchor == null) return false;
        int dx = Integer.compare(anchor.getX(), pivot.getX());
        int dz = Integer.compare(anchor.getZ(), pivot.getZ());
        if (dx == 0 && dz == 0) return false;
        if (Math.abs(anchor.getY() - pivot.getY()) < 2) return false;
        BlockPos cursor = pivot.offset(dx, 0, dz);
        int gapCells = 0;
        for (int guard = 0; guard < 32
                && (cursor.getX() != anchor.getX() || cursor.getZ() != anchor.getZ()); guard++) {
            if (!level.isLoaded(cursor)) return false;
            BlockState state = level.getBlockState(cursor);
            if (!state.getCollisionShape(level, cursor).isEmpty()) {
                return gapCells > 0 && hasLandingAndEgress(level, cursor, pivot, anchor);
            }
            gapCells++;
            cursor = cursor.offset(dx, 0, dz);
        }
        return cursor.getX() == anchor.getX() && cursor.getZ() == anchor.getZ() && gapCells > 0
                && hasLandingAndEgress(level, anchor, pivot, anchor);
    }

    private static boolean hasChasmGap(ServerLevel level, BlockPos pivot, BlockPos anchor) {
        if (level == null || pivot == null || anchor == null) return false;
        int steps = Math.max(Math.abs(anchor.getX() - pivot.getX()), Math.abs(anchor.getZ() - pivot.getZ()));
        if (steps <= 1) return false;
        boolean foundTrueGap = false;
        for (int step = 1; step < steps; step++) {
            double fraction = step / (double) steps;
            int x = (int) Math.round(pivot.getX() + (anchor.getX() - pivot.getX()) * fraction);
            int z = (int) Math.round(pivot.getZ() + (anchor.getZ() - pivot.getZ()) * fraction);
            int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos surface = new BlockPos(x, surfaceY, z);
            if (!level.isLoaded(surface)) return false;
            BlockState state = level.getBlockState(surface);
            BlockState below = level.getBlockState(surface.below());
            boolean isLava = state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
                    || below.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
                    || level.getBlockState(surface.below(2)).getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
            boolean isDeepDrop = surface.getY() <= pivot.getY() - 2;
            boolean isVoid = surface.getY() <= level.getMinY();
            boolean isDangerousTrapdoorDrop = (state.is(BlockTags.TRAPDOORS) || below.is(BlockTags.TRAPDOORS))
                    && (isTrapdoorDropHazard(level, surface) || isTrapdoorDropHazard(level, surface.below())
                    || com.example.soundattract.pathfinding.TrapdoorNavigationHelper.isTrapdoorDropHazard(level, surface)
                    || com.example.soundattract.pathfinding.TrapdoorNavigationHelper.isTrapdoorDropHazard(level, surface.below()));
            if (isLava || isDeepDrop || isVoid || isDangerousTrapdoorDrop) {
                foundTrueGap = true;
                break;
            }
        }
        return foundTrueGap;
    }

    private static boolean hasLandingAndEgress(ServerLevel level, BlockPos landing, BlockPos pivot, BlockPos anchor) {
        if (level == null || landing == null || pivot == null || anchor == null) return false;
        if (!isSafeLanding(level, landing)) return false;
        int dx = Integer.compare(anchor.getX(), pivot.getX());
        int dz = Integer.compare(anchor.getZ(), pivot.getZ());
        if (dx == 0 && dz == 0) return true;
        if (isPassableEgress(level, landing.offset(dx, 0, dz))) return true;
        if (isPassableEgress(level, landing.offset(dx, 1, dz))) return true;
        if (isPassableEgress(level, landing.offset(dx, -1, dz))) return true;
        if (dx != 0) {
            if (isPassableEgress(level, landing.offset(dx, 0, 0))) return true;
            if (isPassableEgress(level, landing.offset(dx, 1, 0))) return true;
            if (isPassableEgress(level, landing.offset(dx, -1, 0))) return true;
        }
        if (dz != 0) {
            if (isPassableEgress(level, landing.offset(0, 0, dz))) return true;
            if (isPassableEgress(level, landing.offset(0, 1, dz))) return true;
            if (isPassableEgress(level, landing.offset(0, -1, dz))) return true;
        }
        int latX = -dz;
        int latZ = dx;
        if (isPassableEgress(level, landing.offset(latX, 0, latZ))) return true;
        if (isPassableEgress(level, landing.offset(-latX, 0, -latZ))) return true;
        return false;
    }

    private static boolean isPassableEgress(ServerLevel level, BlockPos egress) {
        if (!level.isLoaded(egress) || !level.isLoaded(egress.above()) || !level.isLoaded(egress.below())) return false;
        BlockState feet = level.getBlockState(egress);
        BlockState head = level.getBlockState(egress.above());
        BlockState support = level.getBlockState(egress.below());
        boolean feetClear = feet.getCollisionShape(level, egress, CollisionContext.empty()).isEmpty()
                || feet.is(BlockTags.TRAPDOORS) || feet.getBlock() instanceof TrapDoorBlock;
        boolean headClear = head.getCollisionShape(level, egress.above(), CollisionContext.empty()).isEmpty();
        boolean hasSupport = hasStableSupport(level, egress.below(), support)
                || support.is(BlockTags.TRAPDOORS) || support.getBlock() instanceof TrapDoorBlock;
        return feetClear && headClear && hasSupport && feet.getFluidState().isEmpty()
                && head.getFluidState().isEmpty() && support.getFluidState().isEmpty();
    }

    private static boolean isInvasionEndpoint(ServerLevel level, BlockPos pivot, BlockPos endpoint) {
        if (level == null || pivot == null || endpoint == null) return false;
        if (isSafeLanding(level, endpoint) && hasLandingAndEgress(level, endpoint, pivot, endpoint)) return true;
        if (!level.isLoaded(endpoint) || !level.isLoaded(endpoint.above()) || !level.isLoaded(endpoint.below())) return false;
        BlockState feet = level.getBlockState(endpoint);
        BlockState head = level.getBlockState(endpoint.above());
        BlockState below = level.getBlockState(endpoint.below());
        boolean hasSupport = hasStableSupport(level, endpoint.below(), below)
                || below.is(BlockTags.TRAPDOORS) || below.getBlock() instanceof TrapDoorBlock;
        if (!hasSupport || !feet.getFluidState().isEmpty() || !head.getFluidState().isEmpty()
                || !below.getFluidState().isEmpty()
                || !feet.getCollisionShape(level, endpoint).isEmpty()
                || !head.getCollisionShape(level, endpoint.above()).isEmpty()) return false;
        int dx = Integer.compare(endpoint.getX(), pivot.getX());
        int dz = Integer.compare(endpoint.getZ(), pivot.getZ());
        if (dx == 0 && dz == 0) return false;
        if (below.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
                || isOpenTrapdoor(below)) return false;
        return hasLandingAndEgress(level, endpoint, pivot, endpoint);
    }

    private static boolean isScenarioEndpointValid(ServerLevel level, Formation formation,
                                                    ConstructionScenario scenario, BlockPos endpoint) {
        if (formation == null) return false;
        return isScenarioEndpointValid(level, formation.request, scenario, endpoint, formation.pivot, formation.anchor);
    }

    private static boolean isScenarioEndpointValid(ServerLevel level, ConstructionRequest request,
                                                    ConstructionScenario scenario, BlockPos endpoint,
                                                    BlockPos pivot, BlockPos anchor) {
        if (level == null || request == null || scenario == null || endpoint == null || pivot == null || anchor == null) return false;
        if (!level.isLoaded(endpoint) || !level.isLoaded(endpoint.above())
                || !level.isLoaded(endpoint.below())) return false;
        List<BlockPos> hazards = request.evidence() == null ? List.of() : request.evidence().hazardCells();
        if (hazards.contains(endpoint) || hazards.contains(endpoint.above())) return false;
        if (hazards.contains(endpoint.below()) && !hasStableSupport(level, endpoint.below(), level.getBlockState(endpoint.below()))) return false;
        if (scenario == ConstructionScenario.WALL_LEDGE) {
            WallProfileScanner.TargetEvidence evidence = request.evidence();
            return evidence != null && evidence.wallFace() != null && evidence.supportCell() != null
                    && isElevatedWallLedge(pivot, endpoint)
                    && (endpoint.getY() - pivot.getY() >= 2)
                    && isSafeLanding(level, endpoint)
                    && hasLandingAndEgress(level, endpoint, pivot, anchor);
        }
        if (scenario == ConstructionScenario.DROP_TO_LEDGE) {
            double limit = LivingWallConfig.MAX_DROP_BRIDGE_LENGTH.get();
            for (int depth = 0; depth <= Math.ceil(limit); depth++) {
                BlockPos candidate = endpoint.below(depth);
                if (capturedBottomCenterDrop(pivot, candidate) > limit) break;
                if (isSafeLanding(level, candidate) && hasLandingAndEgress(level, candidate, pivot, anchor)) return true;
            }
            return false;
        }
        return scenario == ConstructionScenario.JUMP_BRIDGE
                ? isClearLoadedEndpoint(level, endpoint) && isSafeLanding(level, endpoint) && hasLandingAndEgress(level, endpoint, pivot, anchor)
                : isSafeLanding(level, endpoint);
    }

    private static boolean isClearLoadedEndpoint(ServerLevel level, BlockPos endpoint) {
        if (level == null || endpoint == null || !level.isLoaded(endpoint) || !level.isLoaded(endpoint.above())) return false;
        BlockState feet = level.getBlockState(endpoint);
        BlockState head = level.getBlockState(endpoint.above());
        return feet.getFluidState().isEmpty() && head.getFluidState().isEmpty()
                && feet.getCollisionShape(level, endpoint, CollisionContext.empty()).isEmpty()
                && head.getCollisionShape(level, endpoint.above(), CollisionContext.empty()).isEmpty();
    }

    private static boolean hasPostArrivalExitGeometry(ServerLevel level, BlockPos pivot, BlockPos endpoint) {
        int dx = Integer.compare(endpoint.getX(), pivot.getX());
        int dz = Integer.compare(endpoint.getZ(), pivot.getZ());
        List<BlockPos> exits = new ArrayList<>();
        exits.add(endpoint.below());
        exits.add(endpoint.offset(dx, 0, dz));
        exits.add(endpoint.offset(-dx, 0, -dz));
        if (dx != 0 || dz != 0) {
            exits.add(endpoint.offset(-dz, 0, dx));
            exits.add(endpoint.offset(dz, 0, -dx));
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos cardinal = endpoint.relative(dir);
            if (!exits.contains(cardinal)) exits.add(cardinal);
        }
        for (BlockPos exit : exits) {
            if (!level.isLoaded(exit) || !level.isLoaded(exit.above())) continue;
            BlockState feet = level.getBlockState(exit);
            BlockState head = level.getBlockState(exit.above());
            if (!feet.getFluidState().isEmpty() || !head.getFluidState().isEmpty()
                    || !feet.getCollisionShape(level, exit).isEmpty()
                    || !head.getCollisionShape(level, exit.above()).isEmpty()) continue;
            if (hasStableSupport(level, exit.below(), level.getBlockState(exit.below()))) return true;
            if (isSafeFallColumn(level, exit, 4)) return true;
        }
        return false;
    }

    private static boolean isSafeFallColumn(ServerLevel level, BlockPos start, int depth) {
        for (int offset = 0; offset <= depth; offset++) {
            BlockPos cell = start.below(offset);
            if (!level.isLoaded(cell) || !level.getBlockState(cell).getFluidState().isEmpty()) return false;
            if (!level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
                return offset > 0 && !WallProfileScanner.isHazardKind(WallProfileScanner.classifyCollisionShape(
                        level.getBlockState(cell).getCollisionShape(level, cell),
                        level.getBlockState(cell).is(BlockTags.LEAVES)));
            }
        }
        return false;
    }

    private static BlockPos resolveFirstUsableTiltContact(ServerLevel level, BlockPos pivot, BlockPos anchor,
                                                          Vec3 forward, BlockPos originalSpanEnd) {
        if (level == null || pivot == null || anchor == null || forward == null || originalSpanEnd == null) {
            return originalSpanEnd;
        }
        Vec3 horizontal = new Vec3(forward.x, 0.0D, forward.z);
        if (horizontal.lengthSqr() < 0.001D) return originalSpanEnd;
        horizontal = horizontal.normalize();
        double originalDistance = horizontalDistance(pivot, originalSpanEnd);
        if (originalDistance < 2.0D) return originalSpanEnd;
        int minY = Math.min(pivot.getY(), anchor.getY()) - 1;
        int maxY = Math.max(pivot.getY(), anchor.getY()) + 2;
        int maxStep = Math.max(1, Mth.floor(originalDistance) - 1);
        for (int step = 1; step <= maxStep; step++) {
            Vec3 center = Vec3.atBottomCenterOf(pivot).add(horizontal.scale(step));
            int x = Mth.floor(center.x);
            int z = Mth.floor(center.z);
            BlockPos best = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (int y = minY; y <= maxY; y++) {
                BlockPos contact = new BlockPos(x, y, z);
                BlockPos landing = usableTiltContactLanding(level, contact, pivot, anchor);
                if (landing == null) continue;
                double distance = horizontalDistance(pivot, landing);
                if (distance >= originalDistance - 0.5D || distance < 2.0D) continue;
                if (distance < bestDistance) {
                    best = landing;
                    bestDistance = distance;
                }
            }
            if (best != null) {
                logTiltSpanTruncation(level, pivot, anchor, originalSpanEnd, best);
                return best.immutable();
            }
        }
        return originalSpanEnd;
    }

    private static BlockPos usableTiltContactLanding(ServerLevel level, BlockPos contact, BlockPos pivot, BlockPos anchor) {
        if (level == null || contact == null || pivot == null || anchor == null || !level.isLoaded(contact)) return null;
        BlockState state = level.getBlockState(contact);
        if (state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) || state.getCollisionShape(level, contact).isEmpty()) {
            return null;
        }
        if (contact.getY() < pivot.getY() || !hasExposedTiltApproachFace(level, contact, pivot, anchor)) return null;
        BlockPos[] candidates = {
                contact.above(),
                contact.above().offset(Integer.compare(anchor.getX(), pivot.getX()), 0,
                        Integer.compare(anchor.getZ(), pivot.getZ())),
                contact.above().offset(-Integer.compare(anchor.getX(), pivot.getX()), 0,
                        -Integer.compare(anchor.getZ(), pivot.getZ()))
        };
        for (BlockPos candidate : candidates) {
            if (isSafeLanding(level, candidate)) return candidate;
        }
        return null;
    }

    private static boolean hasExposedTiltApproachFace(ServerLevel level, BlockPos contact, BlockPos pivot, BlockPos anchor) {
        if (level == null || contact == null || pivot == null || anchor == null) return false;
        int dx = Integer.compare(anchor.getX(), pivot.getX());
        int dz = Integer.compare(anchor.getZ(), pivot.getZ());
        if (dx == 0 && dz == 0) return false;
        BlockPos approach = contact.offset(-dx, 0, -dz);
        if (!level.isLoaded(approach) || !level.isLoaded(approach.above())) return false;
        return level.getBlockState(approach).getCollisionShape(level, approach).isEmpty()
                && level.getBlockState(approach.above()).getCollisionShape(level, approach.above()).isEmpty();
    }

    private static double horizontalDistance(BlockPos first, BlockPos second) {
        if (first == null || second == null) return Double.POSITIVE_INFINITY;
        double dx = second.getX() - first.getX();
        double dz = second.getZ() - first.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void logTiltSpanTruncation(ServerLevel level, BlockPos pivot, BlockPos anchor,
                                              BlockPos originalSpanEnd, BlockPos resolvedSpanEnd) {
        if (SoundAttractConfig.COMMON == null || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_CONSTRUCTION)) return;
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Tilt span shortened by first usable wall contact: pivot={}, anchor={}, originalSpanEnd={}, resolvedSpanEnd={}, originalDistance={}, resolvedDistance={}, tick={}",
                pivot, anchor, originalSpanEnd, resolvedSpanEnd, horizontalDistance(pivot, originalSpanEnd),
                horizontalDistance(pivot, resolvedSpanEnd), level == null ? -1L : level.getGameTime());
    }

    static int requiredStationsForRoute(SitePlan plan, double horizontalStep, double verticalStep) {
        if (plan == null || plan.pivot() == null || plan.spanEnd() == null) return 0;
        return requiredStationsForRoute(plan.pivot(), plan.spanEnd(), plan.scenario(), horizontalStep, verticalStep, plan.corridor());
    }

    static int requiredStationsForRoute(BlockPos pivot, BlockPos spanEnd, ConstructionScenario scenario,
                                        double horizontalStep, double verticalStep) {
        return requiredStationsForRoute(pivot, spanEnd, scenario, horizontalStep, verticalStep, null);
    }

    static int requiredStationsForRoute(BlockPos pivot, BlockPos spanEnd, ConstructionScenario scenario,
                                        double horizontalStep, double verticalStep, CanonicalFeetCorridor corridor) {
        Vec3 delta = Vec3.atBottomCenterOf(spanEnd).subtract(Vec3.atBottomCenterOf(pivot));
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double distance = Math.sqrt(horizontal * horizontal + delta.y * delta.y);
        if (corridor != null) {
            List<Vec3> route = tiltRoutePoints(pivot, spanEnd, corridor);
            double routeLength = polylineLength(route);
            if (routeLength > distance) {
                distance = routeLength;
            }
        }
        double stride = isDirectSpan(scenario)
                ? Math.min(Math.max(0.20D, horizontalStep), 0.58D)
                : scenario == ConstructionScenario.WALL_LEDGE
                        ? Math.max(0.50D, verticalStep)
                        : Math.max(0.25D, horizontalStep);
        return Math.max(2, Mth.ceil(distance / stride) + 1);
    }

    private static boolean hasWallLedge(ServerLevel level, BlockPos pivot, BlockPos anchor) {
        if (!isSafeLanding(level, anchor) || !hasLandingAndEgress(level, anchor, pivot, anchor)) return false;
        int dx = Integer.compare(anchor.getX(), pivot.getX());
        int dz = Integer.compare(anchor.getZ(), pivot.getZ());
        if (dx == 0 && dz == 0) return false;
        if (isSheerWallFace(level, anchor, dx, dz)) return false;
        int maxHeight = LivingWallConfig.MAX_HEIGHT != null ? LivingWallConfig.MAX_HEIGHT.get() : 16;
        for (int[] offset : new int[][] {{0, 0}, {-dx, -dz}, {dx, dz}}) {
            int faceX = anchor.getX() + offset[0];
            int faceZ = anchor.getZ() + offset[1];
            int consecutive = 0;
            for (int y = anchor.getY() - 1; y >= anchor.getY() - maxHeight; y--) {
                BlockPos face = new BlockPos(faceX, y, faceZ);
                if (!level.isLoaded(face)) break;
                BlockState state = level.getBlockState(face);
                boolean isWallBlock = state.is(BlockTags.TRAPDOORS)
                        || state.getBlock() instanceof TrapDoorBlock
                        || !state.getCollisionShape(level, face).isEmpty();
                if (isWallBlock) {
                    consecutive++;
                    if (consecutive >= 2) return true;
                } else {
                    consecutive = 0;
                }
            }
        }
        return false;
    }

    private static List<BlockPos> canonicalHazardCells(List<BlockPos> hazards, BlockPos pivot, BlockPos wallFace) {
        if (hazards == null || hazards.isEmpty()) return List.of();
        List<BlockPos> ordered = new ArrayList<>();
        for (BlockPos hazard : hazards) {
            if (hazard != null && !ordered.contains(hazard)) ordered.add(hazard.immutable());
        }
        if (pivot == null || wallFace == null) return List.copyOf(ordered);
        Vec3 forward = Vec3.atBottomCenterOf(wallFace).subtract(Vec3.atBottomCenterOf(pivot));
        double length = Math.sqrt(forward.x * forward.x + forward.y * forward.y + forward.z * forward.z);
        if (length < 0.001D) return List.copyOf(ordered);
        Vec3 direction = new Vec3(forward.x / length, forward.y / length, forward.z / length);
        ordered.sort(Comparator
                .comparingDouble((BlockPos cell) -> Vec3.atBottomCenterOf(cell).subtract(Vec3.atBottomCenterOf(pivot)).dot(direction))
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getZ));
        return List.copyOf(ordered);
    }

    private static List<BlockPos> copyHazards(List<BlockPos> hazards) {
        if (hazards == null || hazards.isEmpty()) return List.of();
        List<BlockPos> copy = new ArrayList<>(hazards.size());
        for (BlockPos hazard : hazards) {
            copy.add(hazard == null ? null : hazard.immutable());
        }
        return List.copyOf(copy);
    }

    private static long terrainSignature(ServerLevel level, BlockPos pivot, BlockPos target,
                                         WallProfileScanner.TargetEvidence evidence) {
        long signature = 1L;
        signature = 31L * signature + bucketKey(pivot);
        signature = 31L * signature + bucketKey(target);
        if (evidence != null) {
            signature = 31L * signature + bucketKey(evidence.anchor());
            signature = 31L * signature + bucketKey(evidence.wallFace());
            signature = 31L * signature + bucketKey(evidence.supportCell());
            signature = 31L * signature + bucketKey(evidence.sourceBucket());
            signature = 31L * signature + bucketKey(evidence.targetBucket());
            signature = 31L * signature + bucketKey(evidence.edgeCell());
            signature = 31L * signature + bucketKey(evidence.sourceApproachSurface());
            signature = 31L * signature + bucketKey(evidenceLandingSurface(evidence));
            signature = 31L * signature + bucketKey(evidenceInvasionSurface(evidence));
            signature = 31L * signature + evidence.sourceRayIds().hashCode();
            for (BlockPos structural : evidence.structuralCells()) {
                signature = 31L * signature + (structural == null ? 0L : structural.asLong());
            }
            for (BlockPos hazard : evidence.hazardCells()) {
                signature = 31L * signature + (hazard == null ? 0L : hazard.asLong());
            }
        }
        if (level != null && evidence != null) {
            BlockPos[] cells = {pivot, target, evidence.anchor(), evidence.wallFace(), evidence.supportCell()};
            for (BlockPos cell : cells) {
                if (cell == null || !level.isLoaded(cell)) {
                    signature = 31L * signature + 7L;
                    continue;
                }
                BlockState state = level.getBlockState(cell);
                signature = 31L * signature + state.hashCode();
            }
        }
        return signature;
    }

    private static List<BlockPos> carriedRayHazards(WallProfileScanner.RayEvidence ray, BlockPos pivot, BlockPos wallFace) {
        if (ray == null) return List.of();
        List<BlockPos> hazards = WallProfileScanner.hazardCells(ray);
        if (hazards.isEmpty()) return List.of();
        List<BlockPos> copy = new ArrayList<>(hazards.size());
        for (BlockPos hazard : hazards) {
            copy.add(hazard == null ? null : hazard.immutable());
        }
        return List.copyOf(copy);
    }

    private static List<BlockPos> carriedStructuralCells(WallProfileScanner.RayEvidence ray, BlockPos wallFace, BlockPos supportCell) {
        List<BlockPos> structural = new ArrayList<>();
        if (ray != null && ray.structuralCells() != null) {
            for (BlockPos cell : ray.structuralCells()) {
                if (cell != null && !structural.contains(cell)) structural.add(cell.immutable());
            }
        }
        if (wallFace != null && !structural.contains(wallFace)) structural.add(wallFace.immutable());
        if (supportCell != null && !structural.contains(supportCell)) structural.add(supportCell.immutable());
        return List.copyOf(structural);
    }

    private static List<WallProfileScanner.CollisionKind> carriedRayHazardKinds(WallProfileScanner.RayEvidence ray) {
        if (ray == null || ray.collisionKinds() == null || ray.collisionKinds().isEmpty()) return List.of();
        List<WallProfileScanner.CollisionKind> kinds = new ArrayList<>();
        for (WallProfileScanner.CollisionKind kind : ray.collisionKinds()) {
            if (WallProfileScanner.isHazardKind(kind)) kinds.add(kind);
        }
        return List.copyOf(kinds);
    }

    private static List<BlockPos> carriedRayHazardsFromEvidence(WallProfileScanner.TargetEvidence evidence, BlockPos pivot,
                                                                 BlockPos wallFace) {
        if (evidence == null) return List.of();
        List<BlockPos> hazards = evidence.hazardCells();
        if (hazards == null || hazards.isEmpty()) return List.of();
        List<BlockPos> copy = new ArrayList<>(hazards.size());
        for (BlockPos hazard : hazards) {
            copy.add(hazard == null ? null : hazard.immutable());
        }
        return List.copyOf(copy);
    }

    private static BlockPos sourceApproachSurface(ServerLevel level, Vec3 center) {
        if (center == null) return null;
        BlockPos bucket = BlockPos.containing(center);
        if (level == null) return bucket;
        BlockPos surface = resolveLocalSafeSurface(level, bucket.getX(), bucket.getZ(), bucket.getY());
        return surface != null ? surface : bucket;
    }

    private static BlockPos evidenceInvasionSurface(ServerLevel level, BlockPos pivot, BlockPos anchor,
                                                    ConstructionScenario scenario) {
        if (anchor == null) return null;
        if (level == null || pivot == null || scenario == null) return anchor;
        return switch (scenario) {
            case WALL_LEDGE -> anchor;
            case DROP_TO_LEDGE -> anchor;
            default -> anchor;
        };
    }

    private static WallProfileScanner.TargetEvidence buildTargetEvidence(ServerLevel level, Vec3 center, BlockPos threat,
                                                                        WallProfileScanner.RayEvidence ray, BlockPos candidate,
                                                                        BlockPos wallFace, ConstructionScenario scenario,
                                                                        double score) {
        BlockPos pivot = BlockPos.containing(center);
        BlockPos supportCell = candidate == null ? null : candidate.below();
        BlockPos targetBucket = threat == null ? pivot : threat;
        BlockPos landingSurface = scenario == ConstructionScenario.WALL_LEDGE
                ? evidenceInvasionSurface(level, pivot, candidate, scenario) : candidate;
        BlockPos invasionSurface = scenario == ConstructionScenario.WALL_LEDGE ? candidate
                : evidenceInvasionSurface(level, pivot, candidate, scenario);
        return new WallProfileScanner.TargetEvidence(candidate,
                List.of(ray.rayId()), List.copyOf(carriedRayHazards(ray, pivot, candidate)), carriedRayHazardKinds(ray),
                wallFace, supportCell, pivot, targetBucket, scenario.name(), score, null,
                carriedStructuralCells(ray, wallFace, supportCell), wallFace, sourceApproachSurface(level, center),
                landingSurface, invasionSurface);
    }

    private static boolean profileDirectionCompatible(BlockPos pivot, BlockPos target, BlockPos anchor) {
        if (pivot == null || target == null || anchor == null) return false;
        Vec3 targetDelta = Vec3.atBottomCenterOf(target).subtract(Vec3.atBottomCenterOf(pivot));
        Vec3 anchorDelta = Vec3.atBottomCenterOf(anchor).subtract(Vec3.atBottomCenterOf(pivot));
        Vec3 targetHorizontal = new Vec3(targetDelta.x, 0.0D, targetDelta.z);
        Vec3 anchorHorizontal = new Vec3(anchorDelta.x, 0.0D, anchorDelta.z);
        if (targetHorizontal.lengthSqr() < 0.001D || anchorHorizontal.lengthSqr() < 0.001D) return false;
        return targetHorizontal.normalize().dot(anchorHorizontal.normalize()) >= 0.55D;
    }

    private static boolean sameSourceRegionAndDirection(WallProfileScanner.TargetEvidence evidence,
                                                        long sourceBucket, long targetBucket, BlockPos candidateAnchor) {
        if (evidence == null || bucketKey(evidence.sourceBucket()) != sourceBucket) return false;
        if (bucketKey(evidence.targetBucket()) == targetBucket || targetBucket == Long.MIN_VALUE || candidateAnchor == null) {
            return true;
        }
        if (evidence.anchor() == null) return false;
        double dx = candidateAnchor.getX() - evidence.anchor().getX();
        double dy = candidateAnchor.getY() - evidence.anchor().getY();
        double dz = candidateAnchor.getZ() - evidence.anchor().getZ();
        return dx * dx + dy * dy + dz * dz <= 64.0D;
    }

    private static boolean validateCarriedHazards(ServerLevel level, Set<BlockPos> hazards) {
        if (level == null || hazards == null || hazards.isEmpty()) return true;
        int checked = 0;
        for (BlockPos hazard : hazards) {
            if (hazard == null) continue;
            if (!level.isLoaded(hazard)) return false;
            if (checked++ >= 16) break;
            if (!isBridgeHazard(level, hazard)) return false;
        }
        return true;
    }

    private static long bucketKey(BlockPos pos) {
        if (pos == null) return Long.MIN_VALUE;
        return BlockPos.asLong(Math.floorDiv(pos.getX(), 4), Math.floorDiv(pos.getY(), 4), Math.floorDiv(pos.getZ(), 4));
    }

    private static boolean hazardCorridorsOverlap(Set<BlockPos> first, Set<BlockPos> second) {
        if (first == null || second == null || first.isEmpty() || second.isEmpty()) return false;
        for (BlockPos left : first) {
            for (BlockPos right : second) {
                if (Math.abs(left.getX() - right.getX()) <= 1
                        && Math.abs(left.getZ() - right.getZ()) <= 1) return true;
            }
        }
        return false;
    }

    private static boolean isBridgeHazard(BlockState state) {
        return state != null && (state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
                || isOpenTrapdoor(state));
    }

    private static boolean isBridgeHazard(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (isBridgeHazard(state)) return true;
        if (state.getBlock() instanceof net.minecraft.world.level.block.NetherPortalBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.EndGatewayBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.EndPortalBlock) return true;
        if (!state.getFluidState().isEmpty() || !state.getCollisionShape(level, pos).isEmpty()) return false;
        for (int depth = 1; depth <= 4; depth++) {
            BlockPos below = pos.below(depth);
            if (!level.isLoaded(below)) return true;
            BlockState belowState = level.getBlockState(below);
            if (!belowState.getFluidState().isEmpty() || !belowState.getCollisionShape(level, below).isEmpty()) return false;
        }
        return true;
    }

    private record FootprintFailure(BlockPos cell, String reason, BlockPos hazard) {}

    private static FootprintFailure firstFootprintFailure(ServerLevel level, Set<BlockPos> cells,
                                                          List<BlockPos> hazards) {
        for (BlockPos cell : cells.stream().sorted(Comparator.comparingInt((BlockPos pos) -> pos.getX())
                .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ)).toList()) {
            if (!level.isLoaded(cell) || !level.isLoaded(cell.above()) || !level.isLoaded(cell.below())) {
                return new FootprintFailure(cell, "unloaded", null);
            }
            if (!isSafeSite(level, cell)) return new FootprintFailure(cell, "unsafe_standing_cell", null);
            BlockPos hazard = standingHazard(level, cell, hazards);
            if (hazard != null) return new FootprintFailure(cell, "standing_envelope_hazard", hazard);
        }
        return null;
    }

    private static void logInvalidPlanFootprint(ServerLevel level, BlockPos pivot, BlockPos anchor,
                                                BlockPos spanEnd, FootprintFailure failure) {
        if (level == null || failure == null || SoundAttractConfig.COMMON == null
                || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_CONSTRUCTION)) return;
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Plan footprint rejected pivot={} anchor={} spanEnd={} cell={} reason={} hazard={}",
                pivot, anchor, spanEnd, failure.cell(), failure.reason(), failure.hazard());
    }

    private static BlockPos standingHazard(ServerLevel level, BlockPos cell, List<BlockPos> hazards) {
        if (cell == null || hazards == null) return null;
        for (BlockPos hazard : hazards) {
            if (hazard != null && hazard.getX() == cell.getX() && hazard.getZ() == cell.getZ()) {
                if (hazard.getY() >= cell.getY() && hazard.getY() <= cell.getY() + 1) return hazard;
                if (hazard.getY() == cell.getY() - 1) {
                    if (level == null || !level.isLoaded(hazard) || isBridgeHazard(level, hazard)) return hazard;
                }
            }
        }
        return null;
    }

    private static boolean isNearHazard(ServerLevel level, BlockPos cell, List<BlockPos> hazards, Vec3 forward) {
        return standingHazard(level, cell, hazards) != null;
    }

    static boolean isOnApproachSide(BlockPos seed, BlockPos candidate, Vec3 forward, Set<BlockPos> hazards) {
        if (seed == null || candidate == null || forward == null || hazards == null || hazards.isEmpty()) return true;
        Vec3 origin = Vec3.atBottomCenterOf(seed);
        double candidateProjection = Vec3.atBottomCenterOf(candidate).subtract(origin).dot(forward);
        boolean candidateIsOrigin = candidate.equals(seed);
        for (BlockPos hazard : hazards) {
            if (hazard == null) continue;
            if (hazard.getY() < candidate.getY() - 1) continue;
            double hazardProjection = Vec3.atBottomCenterOf(hazard).subtract(origin).dot(forward);
            double buffer = candidateIsOrigin ? 0.0D : HAZARD_APPROACH_BUFFER;
            if (candidateProjection > hazardProjection - buffer) return false;
        }
        return true;
    }

    static boolean usesShortDropExit(int pivotY, int anchorY) {
        int drop = pivotY - anchorY;
        return drop > 0 && drop <= LivingWallConfig.MAX_ROPE_BRIDGE_LENGTH.get();
    }

    static boolean usesLongDropPlan(int pivotY, int anchorY) {
        return usesLongDropPlan(pivotY, anchorY, LivingWallConfig.MAX_ROPE_BRIDGE_LENGTH.get().intValue(),
                LivingWallConfig.MAX_DROP_BRIDGE_LENGTH.get().intValue());
    }

    static boolean usesLongDropPlan(int pivotY, int anchorY, int ropeLimit, int dropLimit) {
        int drop = pivotY - anchorY;
        return drop > ropeLimit && drop <= dropLimit;
    }

    private static boolean hasSafeShortDropExit(ServerLevel level, BlockPos pivot, BlockPos anchor) {
        if (level == null || pivot == null || anchor == null) return false;
        if (!usesShortDropExit(pivot.getY(), anchor.getY())) return false;
        BlockPos exit = new BlockPos(anchor.getX(), pivot.getY(), anchor.getZ());
        return isSafeExit(level, exit, anchor);
    }

    private static BlockPos shortDropSpanEnd(ServerLevel level, BlockPos pivot, BlockPos anchor) {
        if (!hasSafeShortDropExit(level, pivot, anchor)) return anchor;
        return new BlockPos(anchor.getX(), pivot.getY(), anchor.getZ());
    }

    private static boolean isSupportedDrop(int pivotY, int anchorY, int maxDrop) {
        int drop = pivotY - anchorY;
        return drop > 0 && drop <= maxDrop;
    }

    private static boolean isSupportedDrop(BlockPos pivot, BlockPos anchor, double maxDrop) {
        return pivot != null && anchor != null && anchor.getY() < pivot.getY()
                && capturedBottomCenterDrop(pivot, anchor) <= maxDrop;
    }

    private static boolean isSafeExit(ServerLevel level, BlockPos exit, BlockPos landing) {
        if (!isSafeLanding(level, landing) || !level.isLoaded(exit) || !level.isLoaded(exit.above())) return false;
        BlockState feet = level.getBlockState(exit);
        BlockState head = level.getBlockState(exit.above());
        return feet.getFluidState().isEmpty() && head.getFluidState().isEmpty()
                && !feet.is(BlockTags.TRAPDOORS)
                && feet.getCollisionShape(level, exit).isEmpty()
                && head.getCollisionShape(level, exit.above()).isEmpty();
    }

    private static boolean isCanonicalLivingBridge(Formation formation) {
        return formation != null && formation.plan != null
                && (formation.plan.scenario() == ConstructionScenario.WALL_LEDGE
                || formation.plan.scenario() == ConstructionScenario.DROP_TO_LEDGE
                || isDirectSpan(formation.plan.scenario()));
    }

    private static boolean isPlanValid(ServerLevel level, Formation formation) {
        if (level == null) return planInvalid(null, null, "dimension_missing");
        if (formation == null || formation.plan == null) return planInvalid(level, formation, "plan_missing");
        ConstructionRequest request = formation.request;
        if (request == null || request.evidence() == null) {
            return planInvalid(level, formation, "evidence_missing");
        }
        long now = level.getGameTime();
        PerLevelState state = state(level);
        ConstructionIntent intent = state.constructionIntents.get(formation.plannerId);
        if (intent == null) {
            return planInvalid(level, formation, "intent_uncommitted");
        }
        if (formation.intentToken != intent) return planInvalid(level, formation, "intent_token_mismatch");
        if (formation.traversalWitness == null || intent.traversalWitness == null) {
            return planInvalid(level, formation, "traversal_witness_missing");
        }
        if (!formation.traversalWitness.identityMatches(intent.traversalWitness)) {
            return planInvalid(level, formation, "traversal_witness_identity_mismatch");
        }
        if (!Objects.equals(formation.traversalWitness.identityFingerprint(), intent.traversalWitness.identityFingerprint())) {
            return planInvalid(level, formation, "traversal_witness_fingerprint_mismatch");
        }
        if (!isCanonicalLivingBridge(formation)
                && !formation.traversalWitness.rosterMatches(level.dimension().identifier(), witnessRoster(formation))) {
            return planInvalid(level, formation, "traversal_witness_membership_changed");
        }
        if (!formation.traversalWitness.revalidate(level)) {
            return planInvalid(level, formation, "traversal_witness_world_changed");
        }
        if (intent.attachedFormationId != null) {
            Formation attached = state.formations.get(intent.attachedFormationId);
            if (attached == null || attached.collapsed || !attached.id.equals(formation.id)
                    || !attached.plannerId.equals(formation.plannerId)) {
                clearConstructionIntent(state, intent);
                return planInvalid(level, formation, "intent_owner_mismatch");
            }
            ConstructionRequest currentRequest = intent.request;
            if (currentRequest == null || currentRequest.evidence() == null
                    || currentRequest.revision() != formation.request.revision()) {
                clearConstructionIntent(state, intent);
                return planInvalid(level, formation, "intent_request_mismatch");
            }
        } else if (intent.recruitmentDeadline < now) {
            return planInvalid(level, formation, "intent_expired");
        }
        SitePlan plan = formation.plan;
        if (!hasRequiredStaticSiteGeometry(plan)) return planInvalid(level, formation, "site_contract_incomplete");
        ConstructionRequest currentRequest = intent.request;
        if (intent.candidateKey != null && !java.util.Objects.equals(intent.candidateKey,
                candidateConstructionSiteKey(currentRequest, formation.pivot, formation.plan))) {
            return planInvalid(level, formation, "candidate_key_mismatch");
        }
        if (shouldValidateAgainstLiveThreat(formation)
                && !acceptedPlanMatchesRequest(level, plan, currentRequest)) {
            return planInvalid(level, formation, "frame_invalid");
        }
        ConstructionSiteKey liveSite = committedSiteKey(formation);
        long currentTerrainSignature = formation.terrainSignature != -1L
                ? formation.terrainSignature
                : terrainSignature(level, formation.pivot, plan.anchor(), currentRequest.evidence());
        if (!java.util.Objects.equals(intent.siteKey, liveSite)) return planInvalid(level, formation, "site_key_mismatch");
        if (intent.terrainSignature != currentTerrainSignature) {
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] terrain_signature_mismatch_detail bridge={} intent={} formation={} current={} pivot={} anchor={} target={} observationCorridor={}",
                    formation.id, intent.terrainSignature, formation.terrainSignature, currentTerrainSignature,
                    formation.pivot, plan.anchor(), currentRequest.threatDestination(),
                    currentRequest.observation() == null || currentRequest.observation().corridor() == null
                            ? null : currentRequest.observation().corridor().corridorId());
            return planInvalid(level, formation, "terrain_signature_mismatch");
        }
        if (!committedCorridorMatchesEvidence(plan, currentRequest.evidence())) {
            return planInvalid(level, formation, "corridor_evidence_invalid");
        }
        FootprintFailure footprintFailure = firstFootprintFailure(level, terrainSupportValidationCells(plan), plan.hazardCells());
        if (footprintFailure != null) {
            logInvalidPlanFootprint(level, plan.pivot(), plan.anchor(), plan.spanEnd(), footprintFailure);
            return planInvalid(level, formation, "footprint_invalid");
        }
        boolean endpointValid = isScenarioEndpointValid(level, formation, plan.scenario(), plan.anchor());
        if (!endpointValid) {
            BlockPos endpoint = plan.anchor();
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] anchor_invalid_detail bridge={} anchor={} feet={} head={} support={} feetShapeEmpty={} headShapeEmpty={} supportShapeEmpty={} loaded={} observedAnchor={} formationAnchor={} pivot={} spanEnd={} target={} scenario={}",
                    formation.id, endpoint, level.getBlockState(endpoint), level.getBlockState(endpoint.above()),
                    level.getBlockState(endpoint.below()),
                    level.getBlockState(endpoint).getCollisionShape(level, endpoint).isEmpty(),
                    level.getBlockState(endpoint.above()).getCollisionShape(level, endpoint.above()).isEmpty(),
                    level.getBlockState(endpoint.below()).getCollisionShape(level, endpoint.below()).isEmpty(),
                    level.isLoaded(endpoint) && level.isLoaded(endpoint.above()) && level.isLoaded(endpoint.below()),
                    currentRequest.observation() == null ? null : currentRequest.observation().anchor(), formation.anchor,
                    formation.pivot, plan.spanEnd(), currentRequest.threatDestination(), plan.scenario());
            return planInvalid(level, formation, "anchor_invalid");
        }
        if (request.revision() != intent.scanRevision) return planInvalid(level, formation, "revision_mismatch");
        if (shouldValidateAgainstLiveThreat(formation)
                && requestEvidenceAdmissionReason(level, formation.pivot, currentRequest) != null) {
            return planInvalid(level, formation, "evidence_invalid");
        }
        return true;
    }

    private static boolean shouldValidateAgainstLiveThreat(Formation formation) {
        return formation == null || formation.phase == Phase.SCAN || formation.phase == Phase.COLLAPSE
                || formation.committedSiteKey == null;
    }

    private static boolean commitAcceptedPlanIntent(ServerLevel level, ConstructionIntent intent, Formation formation,
                                                    BlockPos searchPivot, long now) {
        if (level == null || intent == null || formation == null || formation.plan == null || formation.request == null
                || formation.request.evidence() == null || formation.pivot == null || formation.plan.anchor() == null) {
            return false;
        }
        if (!acceptedPlanMatchesRequest(level, formation.plan, formation.request)) return false;
        ConstructionSiteKey acceptedKey = constructionSiteKey(formation.request, formation.pivot, formation.plan);
        if (acceptedKey == null || !intent.plannerId.equals(formation.plannerId)) return false;
        if (intent.candidateKey != null && !java.util.Objects.equals(intent.candidateKey,
                candidateConstructionSiteKey(formation.request, formation.pivot, formation.plan))) return false;
        BodyGeometry witnessGeometry = witnessGeometry(formation);
        if (witnessGeometry == null) return false;
        ClearanceBlockage blockage = findFormationBlockage(level, formation, witnessGeometry);
        if (blockage != null) {
            logSpanClearanceBlockage(level, formation, blockage);
            recordBuildGate(state(level), "candidate_clearance_blocked");
            return false;
        }
        long acceptedSignature = terrainSignature(level, formation.pivot, formation.plan.anchor(), formation.request.evidence());
        if (intent.siteKey != null && !intent.siteKey.equals(acceptedKey)) return false;
        if (formation.traversalWitness == null) return false;
        if (!isCanonicalLivingBridge(formation)
                && !formation.traversalWitness.rosterMatches(level.dimension().identifier(), witnessRoster(formation))) return false;
        if (!formation.traversalWitness.revalidate(level)) return false;
        intent.siteKey = acceptedKey;
        intent.terrainSignature = acceptedSignature;
        intent.traversalWitness = copyTraversalWitness(formation.traversalWitness);
        formation.committedSiteKey = acceptedKey;
        Set<UUID> committedRoster = Set.copyOf(witnessRoster(formation));
        formation.committedExecution = new CommittedBridgeExecution(formation.id,
                new CommittedAdmissionSnapshot(formation.plannerId, committedRoster,
                        formation.dropLimits, acceptedKey), acceptedKey);
        formation.rosterFrozen = true;
        formation.cachedTiltExit = resolveTiltThreatExit(level, formation);
        formation.terrainSignature = acceptedSignature;
        intent.lastProgressTick = now;
        formation.intentToken = intent;
        syncRouteProbeLedger(formation, intent);
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_CONSTRUCTION)) {
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] bridge_plan_intent_commit plannerId={} dimension={} stage={} searchPivot={} acceptedPivot={} siteKey={} anchor={} witnessToken={} witnessRoster={} witnessFingerprint={} witnessCenterline={} witnessEnvelope={} witnessShapeTests={} tick={}",
                    intent.plannerId, level.dimension().identifier(), intent.phase, searchPivot, formation.pivot, acceptedKey.pivot(), formation.plan.anchor(),
                    formation.traversalWitness.membershipToken(), formation.traversalWitness.rosterSignature(),
                    formation.traversalWitness.identityFingerprint(),
                    formation.traversalWitness.centerlineCount(), formation.traversalWitness.envelopeCount(),
                    formation.traversalWitness.shapeTests(), now);
        }
        return true;
    }

    private static ConstructionSiteKey committedSiteKey(Formation formation) {
        if (formation == null) return null;
        if (formation.committedSiteKey != null) return formation.committedSiteKey;
        if (formation.request == null || formation.plan == null) return null;
        return constructionSiteKey(formation.request, formation.pivot, formation.plan);
    }

    private static boolean planInvalid(ServerLevel level, Formation formation, String reason) {
        if (level != null) {
            PerLevelState state = state(level);
            recordBuildGate(state, "plan_invalid_" + reason);
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_CONSTRUCTION)) {
                ConstructionIntent intent = formation == null ? null : state.constructionIntents.get(formation.plannerId);
                SoundAttractMod.LOGGER.info("[LivingWallCoordinator] bridge_plan_invalid plannerId={} dimension={} stage={} acceptedPivot={} committedKey={} reason={} tick={}",
                        formation == null ? null : formation.plannerId, level.dimension().identifier(),
                        formation == null ? null : formation.phase, formation == null ? null : formation.pivot,
                        intent == null || intent.siteKey == null ? null : intent.siteKey.pivot(), reason, level.getGameTime());
                if (formation != null && formation.plan != null) {
                    SoundAttractMod.LOGGER.info("[LivingWallCoordinator] plan_invalid_detail bridge={} reason={} members={} assembledSlots={} required={} planId={} anchor={} spanEnd={} scenario={} requestRevision={} intentAttached={} observationCorridor={}",
                            formation.id, reason, formation.members.size(), formation.assembledSlots, formation.requiredBodies,
                            formation.plan.hashCode(), formation.plan.anchor(), formation.plan.spanEnd(), formation.plan.scenario(),
                            formation.request == null ? null : formation.request.revision(),
                            intent != null && intent.attachedFormationId != null && intent.attachedFormationId.equals(formation.id),
                            formation.request == null || formation.request.observation() == null || formation.request.observation().corridor() == null
                                    ? null : formation.request.observation().corridor().corridorId());
                }
            }
        }
        return false;
    }

    private static boolean isMobOwnedByAnotherFormation(ServerLevel level, Mob mob, UUID ownerId) {
        if (level == null || mob == null || ownerId == null) return false;
        UUID owner = state(level).mobOwners.get(mob.getUUID());
        return owner != null && !owner.equals(ownerId);
    }

    private static void trimExactSiteCooldowns(ServerLevel level, long now) {
        if (level == null) return;
        PerLevelState state = state(level);
        state.terrainRejections.entrySet().removeIf(entry -> entry.getValue() <= now);
        trimCooldownMap(state.terrainRejections, MAX_EXACT_SITE_COOLDOWN_ENTRIES);
    }

    private static void trimCooldownMap(Map<ExactSiteKey, Long> cooldowns, int maxEntries) {
        if (cooldowns == null || cooldowns.size() <= maxEntries) return;
        while (cooldowns.size() > maxEntries) {
            ExactSiteKey oldest = null;
            long oldestExpiry = Long.MAX_VALUE;
            for (Map.Entry<ExactSiteKey, Long> entry : cooldowns.entrySet()) {
                if (entry.getValue() < oldestExpiry) {
                    oldestExpiry = entry.getValue();
                    oldest = entry.getKey();
                }
            }
            if (oldest == null) break;
            cooldowns.remove(oldest);
        }
    }

    private static long resolveRequestRevision(ServerLevel level, PerLevelState state, ConstructionRequestKey key) {
        if (level != null && state != null && key != null) {
            for (ConstructionIntent intent : state.constructionIntents.values()) {
                if (intent != null && key.equals(liveRequestRevisionKey(intent.request, intent.terrainSignature))) {
                    return intent.request == null ? ++state.nextRequestRevision : intent.request.revision();
                }
            }
            for (Formation formation : state.formations.values()) {
                if (formation != null && key.equals(liveRequestRevisionKey(formation.request, formation.terrainSignature))) {
                    return formation.request == null ? ++state.nextRequestRevision : formation.request.revision();
                }
            }
        }
        return ++state.nextRequestRevision;
    }

    private static void trimRequestRevisions(PerLevelState state) {
        if (state == null || state.requestRevisions.size() <= MAX_REQUEST_REVISION_CACHE_ENTRIES) return;
        Iterator<ConstructionRequestKey> iterator = state.requestRevisions.keySet().iterator();
        while (state.requestRevisions.size() > MAX_REQUEST_REVISION_CACHE_ENTRIES && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static ConstructionRequestKey requestRevisionKey(ServerLevel level, ConstructionRequest request) {
        if (request == null || request.evidence() == null) return null;
        long terrainSig = terrainSignature(level, request.evidence().anchor(), request.threatDestination(), request.evidence());
        return new ConstructionRequestKey(request.snapshot() == null ? null : request.snapshot().identity(),
                request.sourceBucket(), request.threatDestination(), request.threatBucket(),
                request.evidence().anchor(), request.evidence().wallFace(), request.evidence().supportCell(),
                ConstructionScenario.valueOf(request.evidence().scenario()), request.evidence().hazardCells(), terrainSig);
    }

    private static ConstructionRequestKey liveRequestRevisionKey(ConstructionRequest request, long terrainSignature) {
        if (request == null || request.evidence() == null) return null;
        return new ConstructionRequestKey(request.snapshot() == null ? null : request.snapshot().identity(),
                request.sourceBucket(), request.threatDestination(), request.threatBucket(),
                request.evidence().anchor(), request.evidence().wallFace(), request.evidence().supportCell(),
                ConstructionScenario.valueOf(request.evidence().scenario()), request.evidence().hazardCells(), terrainSignature);
    }

    private static void putExactSiteCooldown(Map<ExactSiteKey, Long> cooldowns, ExactSiteKey siteKey,
                                             long expiresAt, long now) {
        if (cooldowns == null || siteKey == null) return;
        cooldowns.put(siteKey, expiresAt);
        trimCooldownMap(cooldowns, MAX_EXACT_SITE_COOLDOWN_ENTRIES);
    }

    private static boolean isTerrainRejected(ServerLevel level, BlockPos pivot, long now) {
        trimExactSiteCooldowns(level, now);
        for (Map.Entry<ExactSiteKey, Long> entry : state(level).terrainRejections.entrySet()) {
            if (entry.getValue() > now && entry.getKey().pivot().equals(pivot)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTerrainRejected(ServerLevel level, ExactSiteKey siteKey, long now) {
        if (siteKey == null) return false;
        trimExactSiteCooldowns(level, now);
        for (Map.Entry<ExactSiteKey, Long> entry : state(level).terrainRejections.entrySet()) {
            if (entry.getValue() > now && siteKey.equals(entry.getKey())) return true;
        }
        return false;
    }

    private static boolean isContentionCooling(ServerLevel level, BlockPos pivot, long now) {
        for (Map.Entry<Long, Long> entry : state(level).contentionCooldowns.entrySet()) {
            if (entry.getValue() > now && BlockPos.of(entry.getKey()).distManhattan(pivot) <= Mth.ceil((float) CONTENTION_RADIUS)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTerrainFailure(String reason) {
        return "plan_invalid".equals(reason)
                || "pillar_contact_lost".equals(reason)
                || "assemble_slot_failure".equals(reason)
                || "anchor_contact_lost".equals(reason)
                || "anchor_invalid".equals(reason)
                || "tilt_contact_lost".equals(reason)
                || "tilt_geometry_blocked".equals(reason)
                || "span_clearance_blocked".equals(reason)
                || "anchored_anchor_invalid".equals(reason);
    }

    private static boolean isDynamicRecruitmentFailure(String reason) {
        return "recruitment_timeout".equals(reason)
                || reason != null && (reason.startsWith("mob_route_failed:")
                || reason.startsWith("intake_failed:"));
    }

    private static void observeWindowWorkers(ServerLevel level, PerLevelState state, long now) {
        for (LivingBridgePositionPlan plan : state.positionPlans.values()) {
            if (!plan.isLive(now)) continue;
            for (UUID workerId : plan.rosterIdentity()) {
                Entity entity = level.getEntity(workerId);
                if (!(entity instanceof Mob worker) || !worker.isAlive() || worker.isRemoved()
                        || !level.dimension().identifier().toString().equals(plan.dimension())) {
                    state.groundingObservations.remove(workerId);
                    continue;
                }
                GroundingObservation previous = state.groundingObservations.get(workerId);
                int groundedTicks = groundedTicksAfterObservation(previous, now,
                        worker.onGround(), worker.getDeltaMovement().y);
                state.groundingObservations.put(workerId, new GroundingObservation(now, groundedTicks));
            }
        }
    }

    private record GroundingObservation(long lastTick, int groundedTicks) {
    }

    static int groundedTicksAfterObservation(long previousTick, int previousGroundedTicks,
                                              long now, boolean onGround, double verticalVelocity) {
        if (!onGround || verticalVelocity < -0.08D) return 0;
        if (previousTick != now - 1L) return 1;
        return Math.min(2, Math.max(0, previousGroundedTicks) + 1);
    }

    private static int groundedTicksAfterObservation(GroundingObservation previous, long now,
                                                       boolean onGround, double verticalVelocity) {
        return groundedTicksAfterObservation(previous == null ? Long.MIN_VALUE : previous.lastTick(),
                previous == null ? 0 : previous.groundedTicks(), now, onGround, verticalVelocity);
    }

    private static boolean shouldPoisonExactSiteCooldown(String reason) {
        return isTerrainFailure(reason);
    }

    static List<List<Mob>> partitionSpatialClusters(List<Mob> candidates, double maxDistXZ, double maxDeltaY) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<Mob> usable = new ArrayList<>();
        for (Mob mob : candidates) {
            if (mob != null && mob.isAlive() && !mob.isRemoved() && !isStructuralMember(mob)) {
                usable.add(mob);
            }
        }
        return partitionSpatialElements(usable, Mob::position, Mob::getUUID, maxDistXZ, maxDeltaY,
                MINIMUM_MEMBERS, MAX_SCAN_CANDIDATES);
    }

    static <T> List<List<T>> partitionSpatialElements(List<T> elements,
                                                      java.util.function.Function<T, Vec3> posFn,
                                                      java.util.function.Function<T, UUID> idFn,
                                                      double maxDistXZ,
                                                      double maxDeltaY,
                                                      int minMembers,
                                                      int maxMembers) {
        if (elements == null || elements.size() < minMembers) {
            return List.of();
        }
        List<T> ordered = new ArrayList<>(elements);
        ordered.sort(Comparator.comparing(item -> idFn.apply(item).toString()));
        int n = ordered.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        double maxDistXZSq = maxDistXZ * maxDistXZ;
        Map<Long, List<Integer>> grid = new HashMap<>();
        double cellSize = Math.max(1.0D, maxDistXZ);
        double verticalSize = Math.max(1.0D, maxDeltaY);
        for (int i = 0; i < n; i++) {
            Vec3 pos = posFn.apply(ordered.get(i));
            if (pos == null) continue;
            long key = spatialBucketKey((int) Math.floor(pos.x / cellSize),
                    (int) Math.floor(pos.y / verticalSize), (int) Math.floor(pos.z / cellSize));
            grid.computeIfAbsent(key, ignored -> new ArrayList<>()).add(i);
        }
        int comparisons = 0;
        final int comparisonBudget = 4096;
        for (int i = 0; i < n; i++) {
            T a = ordered.get(i);
            Vec3 posA = posFn.apply(a);
            if (posA == null) continue;
            int bx = (int) Math.floor(posA.x / cellSize);
            int by = (int) Math.floor(posA.y / verticalSize);
            int bz = (int) Math.floor(posA.z / cellSize);
            for (int dxBucket = -1; dxBucket <= 1 && comparisons <= comparisonBudget; dxBucket++) {
                for (int dyBucket = -1; dyBucket <= 1 && comparisons <= comparisonBudget; dyBucket++) {
                    for (int dzBucket = -1; dzBucket <= 1 && comparisons <= comparisonBudget; dzBucket++) {
                        List<Integer> neighbors = grid.get(spatialBucketKey(bx + dxBucket, by + dyBucket, bz + dzBucket));
                        if (neighbors == null) continue;
                        for (int j : neighbors) {
                            if (j <= i) continue;
                            if (++comparisons > comparisonBudget) break;
                            T b = ordered.get(j);
                            Vec3 posB = posFn.apply(b);
                            if (posB == null) continue;
                            double dy = Math.abs(posA.y - posB.y);
                            if (dy <= maxDeltaY) {
                                double dx = posA.x - posB.x;
                                double dz = posA.z - posB.z;
                                if (dx * dx + dz * dz <= maxDistXZSq) {
                                    int rootA = findClusterRoot(parent, i);
                                    int rootB = findClusterRoot(parent, j);
                                    if (rootA != rootB) parent[rootB] = rootA;
                                }
                            }
                        }
                    }
                }
            }
            if (comparisons > comparisonBudget) break;
        }
        Map<Integer, List<T>> groups = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            int root = findClusterRoot(parent, i);
                groups.computeIfAbsent(root, k -> new ArrayList<>()).add(ordered.get(i));
        }
        List<List<T>> validClusters = new ArrayList<>();
        for (List<T> group : groups.values()) {
            if (group.size() >= minMembers) {
                group.sort(Comparator.comparing(item -> idFn.apply(item).toString()));
                if (group.size() > maxMembers) {
                    group = new ArrayList<>(group.subList(0, maxMembers));
                }
                validClusters.add(group);
            }
        }
        validClusters.sort((c1, c2) -> Integer.compare(c2.size(), c1.size()));
        return validClusters;
    }

    private static long spatialBucketKey(int x, int y, int z) {
        return ((long) x & 0x1FFFFFL) << 42 | ((long) y & 0xFFFFFL) << 22 | ((long) z & 0x3FFFFFL);
    }

    private static int findClusterRoot(int[] parent, int i) {
        int root = i;
        while (root != parent[root]) {
            root = parent[root];
        }
        int curr = i;
        while (curr != root) {
            int nxt = parent[curr];
            parent[curr] = root;
            curr = nxt;
        }
        return root;
    }

    private static void scanNonRaidGroups(ServerLevel level, PerLevelState state, long now) {
        if (state.formations.size() >= ADMISSION_QUOTA || state.admissionInFlight) return;
        int maxScan = LivingWallConfig.MAX_CANDIDATES_PER_SCAN != null
                ? Math.min(128, Math.max(1, LivingWallConfig.MAX_CANDIDATES_PER_SCAN.get())) : 128;
        double maxDistXZ = LivingWallConfig.CLUSTER_SPATIAL_RADIUS != null
                ? LivingWallConfig.CLUSTER_SPATIAL_RADIUS.get() : 12.0D;
        double maxDeltaY = LivingWallConfig.CLUSTER_VERTICAL_DELTA != null
                ? LivingWallConfig.CLUSTER_VERTICAL_DELTA.get() : 5.0D;
        Map<UUID, Mob> unique = new HashMap<>();
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            for (Mob mob : level.getEntitiesOfClass(Mob.class,
                    player.getBoundingBox().inflate(RECRUITMENT_RADIUS),
                    mob -> isEligible(mob) && mob.isAlive() && !mob.isRemoved())) {
                unique.putIfAbsent(mob.getUUID(), mob);
                if (unique.size() >= maxScan) break;
            }
            if (unique.size() >= maxScan) break;
        }
        if (unique.isEmpty()) {
            admissionLog(level, state, now, "cluster below minimum: 0");
            return;
        }
        List<LivingBridgeQuantifiedPlanner.MobToken> tokens = unique.values().stream()
                .sorted(Comparator.comparing(mob -> mob.getUUID().toString()))
                .map(mob -> new LivingBridgeQuantifiedPlanner.MobToken(mob.getUUID(), mob.getX(), mob.getY(), mob.getZ()))
                .toList();
        long generation = ++state.admissionGeneration;
        state.admissionInFlight = true;
        int pressureTier = pressureTier(level.getServer().getAverageTickTimeNanos() / 1_000_000.0);
        int parallelism = Math.max(1, 4 - pressureTier);
        String batchKey = level.dimension().identifier() + ":" + generation + ":" + state.terrainEpoch;
        CompletableFuture<LivingBridgeQuantifiedPlanner.ClusterResult> future = LivingBridgeQuantifiedPlanner.submit(
                tokens, maxDistXZ, maxDeltaY, parallelism, batchKey);
        future.whenComplete((result, failure) -> state.admissionCompletions.add(
                new AdmissionClusterCompletion(generation, result, failure)));
    }

    private static List<List<Mob>> partitionClusterIntoCohorts(ServerLevel level, List<Mob> cluster) {
        if (cluster == null || cluster.size() < 2 * MINIMUM_MEMBERS) {
            return cluster == null || cluster.isEmpty() ? List.of() : List.of(cluster);
        }
        int cohortSize = Math.max(MINIMUM_MEMBERS, 6);
        int numCohorts = Math.min(ADMISSION_QUOTA, cluster.size() / cohortSize);
        if (numCohorts < 2) {
            if (cluster.size() >= 10) {
                numCohorts = 2;
            } else {
                return List.of(cluster);
            }
        }
        Vec3 center = cluster.stream().map(Mob::position).reduce(Vec3.ZERO, Vec3::add).scale(1.0D / cluster.size());
        BlockPos threat = resolveEffectiveThreat(level, cluster, null);
        Vec3 lateral = null;
        if (threat != null) {
            Vec3 toThreat = Vec3.atBottomCenterOf(threat).subtract(center);
            lateral = new Vec3(-toThreat.z, 0.0D, toThreat.x);
        }
        final Vec3 sortAxis = (lateral != null && lateral.lengthSqr() > 1.0E-6D) ? lateral.normalize() : new Vec3(1.0D, 0.0D, 0.0D);
        List<Mob> sorted = new ArrayList<>(cluster);
        sorted.sort(Comparator.comparingDouble(mob -> Vec3.atBottomCenterOf(mob.blockPosition()).subtract(center).dot(sortAxis)));

        List<List<Mob>> cohorts = new ArrayList<>();
        int total = sorted.size();
        for (int i = 0; i < numCohorts; i++) {
            int start = i * total / numCohorts;
            int end = (i + 1) * total / numCohorts;
            List<Mob> cohort = new ArrayList<>(sorted.subList(start, end));
            if (cohort.size() >= MINIMUM_MEMBERS) {
                cohorts.add(List.copyOf(cohort));
            }
        }
        return cohorts.isEmpty() ? List.of(cluster) : cohorts;
    }

    private static void drainAdmissionCompletions(ServerLevel level, PerLevelState state, long now) {
        AdmissionClusterCompletion completion;
        while ((completion = state.admissionCompletions.poll()) != null) {
            state.admissionInFlight = false;
            if (completion.generation() != state.admissionGeneration) {
                state.admissionStaleDrops++;
                continue;
            }
            if (completion.failure() != null || completion.result() == null) {
                state.admissionFailureCount = Math.min(4, state.admissionFailureCount + 1);
                state.admissionBackoffUntil = now + Math.min(200L, 40L << Math.min(2, state.admissionFailureCount - 1));
                continue;
            }
            if (completion.result().fallback()) state.admissionFallbacks++;
            List<List<Mob>> clusters = new ArrayList<>();
            for (List<LivingBridgeQuantifiedPlanner.MobToken> tokenCluster : completion.result().clusters()) {
                List<Mob> liveCluster = new ArrayList<>();
                for (LivingBridgeQuantifiedPlanner.MobToken token : tokenCluster) {
                    Entity entity = level.getEntity(token.id());
                    if (entity instanceof Mob mob && mob.isAlive() && !mob.isRemoved() && isEligible(mob)) {
                        liveCluster.add(mob);
                    }
                }
                if (liveCluster.size() >= MINIMUM_MEMBERS) {
                    clusters.addAll(partitionClusterIntoCohorts(level, liveCluster));
                }
            }
            state.admissionFailureCount = 0;
            state.admissionBackoffUntil = now;
            int clusterBudget = switch (pressureTier(level.getServer().getAverageTickTimeNanos() / 1_000_000.0)) {
                case 3 -> 1;
                case 2 -> 1;
                case 1 -> 2;
                default -> 2;
            };
            if (clusters.size() > clusterBudget) {
                state.admissionSkippedByBudget += clusters.size() - clusterBudget;
                clusters = new ArrayList<>(clusters.subList(0, clusterBudget));
            }
            if (clusters.isEmpty()) {
                admissionLog(level, state, now, "cluster below minimum: " + completion.result().clusters().size());
            } else {
                processNonRaidClusters(level, state, now, clusters);
            }
        }
    }

    private static void processNonRaidClusters(ServerLevel level, PerLevelState state, long now,
                                               List<List<Mob>> clusters) {
        if (state.formations.size() >= ADMISSION_QUOTA) return;

        int admittedTotal = 0;
        for (List<Mob> cluster : clusters) {
            if (admittedTotal >= ADMISSION_QUOTA || state.formations.size() >= ADMISSION_QUOTA) {
                break;
            }
            Vec3 center = cluster.stream().map(Mob::position).reduce(Vec3.ZERO, Vec3::add).scale(1.0D / cluster.size());
            BlockPos bucketKey = new BlockPos(
                    Math.floorDiv(Mth.floor(center.x), 16),
                    Math.floorDiv(Mth.floor(center.y), 8),
                    Math.floorDiv(Mth.floor(center.z), 16));

            if (isBasinOnCooldown(level, state, bucketKey, center, now)) {
                continue;
            }

            BlockPos threat = resolveEffectiveThreat(level, cluster, null);
            List<BlockPos> wallTargets = findClusterTargets(level, cluster, threat);
            if (wallTargets.isEmpty()) {
                BlockPos dangerTarget = findDangerTarget(level, BlockPos.containing(center), center);
                if (dangerTarget != null) {
                    admissionLog(level, state, now, "hazard detected but no valid wall anchor");
                } else if (threat != null && threat.getY() < Mth.floor(center.y)) {
                    admissionLog(level, state, now, "downward target rejected: no lower landing");
                }
                handleBasinAdmissionFailure(level, state, cluster, center, threat, bucketKey, now);
                continue;
            }
            boolean clusterAdmitted = false;
            List<Mob> availableClusterMobs = new ArrayList<>(cluster);
            availableClusterMobs.removeIf(mob -> mob == null || !mob.isAlive() || mob.isRemoved()
                    || isMobOwnedByAnotherFormation(level, mob, mob.getUUID())
                    || state.constructionIntents.containsKey(mob.getUUID()));

            for (BlockPos target : wallTargets) {
                if (admittedTotal >= ADMISSION_QUOTA || state.formations.size() >= ADMISSION_QUOTA) {
                    break;
                }
                if (availableClusterMobs.size() < MINIMUM_MEMBERS) {
                    break;
                }
                Mob leader = availableClusterMobs.get(0);
                ConstructionRequest request = findConstructionRequest(level, leader, cluster, threat, target);
                if (request != null && prepareFormation(leader, target, now, availableClusterMobs, request)) {
                    admittedTotal++;
                    clusterAdmitted = true;
                    state.basinFailures.remove(bucketKey);

                    Formation formed = null;
                    for (Formation f : state.formations.values()) {
                        if (f != null && leader.getUUID().equals(f.plannerId) && target.equals(f.target)) {
                            formed = f;
                            break;
                        }
                    }
                    int required = formed != null ? formed.requiredBodies : MINIMUM_MEMBERS;
                    int mobsToConsume = Math.min(availableClusterMobs.size(), Math.max(MINIMUM_MEMBERS, required));
                    for (int i = 0; i < mobsToConsume; i++) {
                        Mob consumed = availableClusterMobs.get(i);
                        if (consumed != null && formed != null) {
                            state.mobOwners.putIfAbsent(consumed.getUUID(), formed.id);
                        }
                    }
                    availableClusterMobs.subList(0, mobsToConsume).clear();
                }
            }
            if (!clusterAdmitted && admittedTotal < ADMISSION_QUOTA && state.formations.size() < ADMISSION_QUOTA) {
                handleBasinAdmissionFailure(level, state, cluster, center, threat, bucketKey, now);
            }
        }
    }

    static BlockPos findBasinSurfaceExit(ServerLevel level, Vec3 center, BlockPos threat) {
        if (level == null || center == null) return null;
        int cx = Mth.floor(center.x);
        int cy = Mth.floor(center.y);
        int cz = Mth.floor(center.z);
        BlockPos bestExit = null;
        double bestDistSq = Double.MAX_VALUE;

        int[] radii = {16, 24, 32};
        for (int r : radii) {
            int d = (int) Math.round(r * 0.7071D);
            int[][] offsets = {
                {r, 0}, {-r, 0}, {0, r}, {0, -r},
                {d, d}, {d, -d}, {-d, d}, {-d, -d}
            };
            for (int[] offset : offsets) {
                int sampleX = cx + offset[0];
                int sampleZ = cz + offset[1];
                if (!level.hasChunkAt(new BlockPos(sampleX, cy, sampleZ))) continue;

                int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ);
                if (surfaceY <= cy + 2) continue;

                BlockPos surfacePos = new BlockPos(sampleX, surfaceY, sampleZ);
                if (!level.getBlockState(surfacePos).getCollisionShape(level, surfacePos).isEmpty()) {
                    surfacePos = surfacePos.above();
                }
                BlockState underState = level.getBlockState(surfacePos.below());
                if (underState.is(BlockTags.TRAPDOORS) || underState.getBlock() instanceof TrapDoorBlock
                        || !hasStableSupport(level, surfacePos.below(), underState)) {
                    continue;
                }
                if (level.getBlockState(surfacePos).is(BlockTags.TRAPDOORS)
                        || level.getBlockState(surfacePos).getBlock() instanceof TrapDoorBlock) {
                    continue;
                }
                if (!level.canSeeSky(surfacePos)) continue;
                if (com.example.soundattract.pathfinding.HazardClassifier.isDangerous(level, null, surfacePos)) continue;
                if (!com.example.soundattract.pathfinding.HazardClassifier.isSafeGround(level, surfacePos)) continue;

                double distSq = threat != null ? surfacePos.distSqr(threat) : surfacePos.distToCenterSqr(center);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestExit = surfacePos;
                }
            }
        }
        return bestExit;
    }

    static boolean isBasinOnCooldown(ServerLevel level, PerLevelState state, BlockPos bucketKey, Vec3 center, long now) {
        if (state == null || bucketKey == null) return false;
        BasinFailureHistory direct = state.basinFailures.get(bucketKey);
        if (direct != null && direct.cooldownUntil() > now) return true;
        if (level != null && center != null && !level.canSeeSky(BlockPos.containing(center))) {
            for (Map.Entry<BlockPos, BasinFailureHistory> entry : state.basinFailures.entrySet()) {
                if (entry.getValue().cooldownUntil() > now) {
                    BlockPos key = entry.getKey();
                    if (key.getX() == bucketKey.getX() && key.getZ() == bucketKey.getZ()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static void handleBasinAdmissionFailure(ServerLevel level, PerLevelState state, List<Mob> cluster,
                                           Vec3 center, BlockPos threat, BlockPos bucketKey, long now) {
        if (level == null || state == null || cluster == null || cluster.isEmpty()) return;

        BasinFailureHistory history = state.basinFailures.get(bucketKey);
        int newCount = (history == null || now - history.lastFailureTick() > 600L) ? 1 : history.failureCount() + 1;
        int threshold = LivingWallConfig.BASIN_FAILURE_THRESHOLD != null ? LivingWallConfig.BASIN_FAILURE_THRESHOLD.get() : 3;
        int cooldown = LivingWallConfig.BASIN_COOLDOWN_TICKS != null ? LivingWallConfig.BASIN_COOLDOWN_TICKS.get() : 400;

        if (newCount >= threshold) {
            boolean evacuationEnabled = LivingWallConfig.ENABLE_BASIN_EVACUATION == null || LivingWallConfig.ENABLE_BASIN_EVACUATION.get();
            BlockPos surfaceExit = evacuationEnabled ? findBasinSurfaceExit(level, center, threat) : null;
            if (surfaceExit != null) {
                Vec3 exitVec = Vec3.atBottomCenterOf(surfaceExit);
                long evacuationExpires = now + cooldown;
                state.basinFailures.put(bucketKey, new BasinFailureHistory(newCount, now, now + cooldown));
                for (Mob mob : cluster) {
                    if (mob != null && mob.isAlive() && !mob.isRemoved()) {
                        UUID uuid = mob.getUUID();
                        UUID ownerFormationId = state.mobOwners.get(uuid);
                        if (ownerFormationId != null) {
                            Formation ownerFormation = state.formations.get(ownerFormationId);
                            if (ownerFormation != null && !ownerFormation.members.contains(uuid)
                                    && !ownerFormation.pendingBuilderIds.contains(uuid)) {
                                ownerFormation.candidateHoldingCells.remove(uuid);
                                ownerFormation.candidateHoldingGoals.remove(uuid);
                                state.mobOwners.remove(uuid);
                            }
                        }
                        if (!state.mobOwners.containsKey(uuid)) {
                            ensureBridgeWorkGoal(mob);
                            state.basinEvacuations.put(uuid, new BasinEvacuation(exitVec, threat, evacuationExpires));
                        }
                    }
                }
                admissionLog(level, state, now, "basin unviable: evacuated " + cluster.size() + " mobs to surface " + surfaceExit);
            } else {
                state.basinFailures.put(bucketKey, new BasinFailureHistory(newCount, now, now + (cooldown / 2)));
                admissionLog(level, state, now, "basin unviable: no surface exit found, cooling down");
            }
        } else {
            state.basinFailures.put(bucketKey, new BasinFailureHistory(newCount, now, now + 60L));
        }
    }

    private static BlockPos findClusterTarget(ServerLevel level, List<Mob> cluster, BlockPos preferredTarget) {
        List<BlockPos> targets = findClusterTargets(level, cluster, preferredTarget);
        return targets.isEmpty() ? null : targets.get(0);
    }

    private static ObservationDecision resolveConstructionObservation(ServerLevel level, Mob leader, List<Mob> cluster,
                                                                       BlockPos threatDestination, BlockPos selectedAnchor,
                                                                       SelectedTargetEvidence selected, long now) {
        if (level == null || level.getServer() == null || !level.getServer().isSameThread()
                || leader == null || selected == null || selected.evidence() == null) {
            return new ObservationDecision(null, null, "server_thread_or_selection_invalid");
        }
        ConstructionScenario scenario = parseConstructionScenario(selected.evidence().scenario());
        LivingWallBridgeAdapterPlanner.AdapterKind adapter = scenario == ConstructionScenario.HAZARD_AND_WALL
                ? LivingWallBridgeAdapterPlanner.AdapterKind.DIRECT
                : adapterSpecForScenario(scenario).map(LivingWallBridgeAdapterPlanner.AdapterSpec::kind)
                .orElse(LivingWallBridgeAdapterPlanner.AdapterKind.DIRECT);
        Map<UUID, WorkerObservation> workers = new LinkedHashMap<>();
        List<UUID> roster = cluster == null ? List.of() : cluster.stream().filter(Objects::nonNull).map(Mob::getUUID).sorted().toList();
        for (Mob worker : cluster == null ? List.<Mob>of() : cluster.stream().filter(Objects::nonNull).toList()) {
            RouteEvidence route = selected.routeEvidence();
            IntakeWindow intake = route == null ? null : route.intakeWindow();
            workers.put(worker.getUUID(), new WorkerObservation(worker.getUUID(), worker.blockPosition(), intake,
                    route == null ? List.of() : route.routeNodes(), intake == null ? "unresolved" : "observed",
                    "observed", route == null ? "unresolved" : "observed"));
        }
        BlockPos pivot = selected.corridor() == null ? selected.evidence().edgeCell() : selected.corridor().pivot();
        LivingBridgePositionPlan positionPlan = state(level).positionPlans.get(targetRosterFingerprint(cluster));
        if (positionPlan == null || !positionPlan.isLive(now)
                || !positionPlan.acceptsRoster(roster)
                || !positionPlan.destinationBucketContains(threatDestination)
                || !Objects.equals(positionPlan.sourceOrigin(), pivot)) {
            return new ObservationDecision(null, null, "scan_lineage_mismatch");
        }
        ConstructionObservation observation = new ConstructionObservation(
                state(level).nextObservationIndex++, positionPlan.windowGeneration(), selected.revision(), leader.getUUID(), roster,
                threatDestination, positionPlan.sourceOrigin(), pivot, selectedAnchor,
                evidenceInvasionSurface(selected.evidence()), scenario, bridgeKind(scenario), adapter,
                selected.evidence(), selected.corridor(), selected.routeEvidence(),
                List.copyOf(selected.evidence().hazardCells()), Map.copyOf(workers));
        return new ObservationDecision(observation, null, null);
    }

    private static ConstructionRequest findConstructionRequest(ServerLevel level, List<Mob> cluster,
                                                              BlockPos threatDestination, BlockPos selectedAnchor) {
        Mob leader = cluster == null || cluster.isEmpty() ? null : cluster.get(0);
        return findConstructionRequest(level, leader, cluster, threatDestination, selectedAnchor);
    }

    private static ConstructionRequest findConstructionRequest(ServerLevel level, Mob leader, List<Mob> cluster,
                                                              BlockPos threatDestination, BlockPos selectedAnchor) {
        if (leader == null) return null;
        SelectedTargetEvidence selected = findClusterTargetEvidence(level, cluster, threatDestination, selectedAnchor);
        WallProfileScanner.TargetEvidence evidence = selected == null ? null : selected.evidence();
        if (evidence == null) return null;
        PerLevelState state = state(level);
        SemanticRequestKey semanticKey = semanticRequestKey(leader, evidence, threatDestination);
        String semanticFingerprint = semanticRequestFingerprint(level, cluster, evidence, selected.corridor(),
                selected.routeEvidence(), threatDestination);
        TerminalAdmissionFailure terminalFailure = state.terminalAdmissionFailures.get(leader.getUUID());
        if (terminalFailure != null && Objects.equals(terminalFailure.key(), semanticKey)
                && Objects.equals(terminalFailure.fingerprint(), semanticFingerprint)) return null;
        if (terminalFailure != null) state.terminalAdmissionFailures.remove(leader.getUUID());
        LivingBridgePositionPlan positionPlan = state(level).positionPlans.get(targetRosterFingerprint(cluster));
        if (positionPlan == null || !positionPlan.isLive(level.getGameTime())
                || !positionPlan.acceptsRoster(cluster.stream().map(Mob::getUUID).toList())
                || !positionPlan.destinationBucketContains(threatDestination)
                || selected.corridor() == null
                || !Objects.equals(positionPlan.sourceOrigin(), selected.corridor().pivot())
                || selected.captureTick() > level.getGameTime() || selected.scanExpiresAt() <= level.getGameTime()) {
            return null;
        }
        if (selected.observation() == null) {
            ObservationDecision decision = resolveConstructionObservation(level, leader, cluster,
                    threatDestination, selectedAnchor, selected, level.getGameTime());
            if (decision.observation() == null) return null;
            selected = new SelectedTargetEvidence(selected.evidence(), selected.corridor(), selected.routeEvidence(),
                    selected.scanExpiresAt(), selected.captureTick(), selected.revision(), decision.observation());
        }
        Vec3 center = cluster.stream().map(Mob::position).reduce(Vec3.ZERO, Vec3::add).scale(1.0D / cluster.size());
        Vec3 sourceOrigin = Vec3.atBottomCenterOf(positionPlan.sourceOrigin());
        BlockPos sourceBucket = BlockPos.containing(center);
        BlockPos threatBucket = threatDestination == null ? sourceBucket
                : new BlockPos(Math.floorDiv(threatDestination.getX(), 4), Math.floorDiv(threatDestination.getY(), 4),
                Math.floorDiv(threatDestination.getZ(), 4));
        long scanExpiresAt = selected.scanExpiresAt();
        long expiresAt = scanExpiresAt;
        long terrainSig = terrainSignature(level, evidence.anchor(), threatDestination, evidence);
        CanonicalFeetCorridor requestCorridor = selected.corridor();
        BodyGeometry requestGeometry = cluster.isEmpty() ? null : geometryFor(leader);
        if (requestCorridor == null && requestGeometry != null) return null;
        RouteEvidence routeEvidence = selected.routeEvidence();
        TargetRouteProbeStats routeStats = new TargetRouteProbeStats();
        if (routeEvidence != null) {
            routeStats.routeMode = "reused";
            routeStats.routeReuses++;
        } else {
        RouteProbeOutcome recomputed = routeBudgetSatisfied(level, cluster, selectedAnchor,
                    evidence, requestCorridor, routeStats, selected.observation());
            if (!recomputed.successful()) {
                state.terminalAdmissionFailures.put(leader.getUUID(),
                        new TerminalAdmissionFailure(semanticKey, semanticFingerprint));
                SoundAttractMod.LOGGER.warn("[LivingWallCoordinator] post_admission_observation_rejected reason={} target={} anchor={} corridor={} observation={} workers={}",
                        recomputed.failureReason(), selectedAnchor, evidence.anchor(), requestCorridor == null ? null : requestCorridor.corridorId(),
                        selected.observation() == null ? "null" : selected.observation().observationIndex(),
                        selected.observation() == null ? 0 : selected.observation().workers().size());
                return null;
            }
            routeEvidence = recomputed.routeEvidence();
        }
        TargetScanSnapshot snapshot = buildTargetScanSnapshot(level, sourceOrigin, center, threatDestination, evidence,
                selected.captureTick(), selected.revision(), scanExpiresAt, formationProbeRadius(cluster), cluster, routeEvidence);
        ConstructionRequestKey key = new ConstructionRequestKey(snapshot.identity(), sourceBucket, threatDestination, threatBucket,
                evidence.anchor(), evidence.wallFace(), evidence.supportCell(),
                ConstructionScenario.valueOf(evidence.scenario()), evidence.hazardCells(), terrainSig);
        long revision = state.requestRevisions.computeIfAbsent(key,
                ignored -> resolveRequestRevision(level, state, key));
        trimRequestRevisions(state);
        routeEvidence = new RouteEvidence(routeEvidence.workerId(), routeEvidence.intakeWindow(), routeEvidence.anchor(),
                routeEvidence.routeNodes(), routeEvidence.pathReachable(), revision,
                routeEvidenceFingerprint(routeEvidence.workerId(), routeEvidence.intakeWindow(), routeEvidence.anchor(),
                        routeEvidence.routeNodes(), revision, routeEvidence.certifiedStart(), routeEvidence.worldFingerprint()),
                routeEvidence.corridorId(), routeEvidence.dimension(), routeEvidence.certifiedStart(),
                routeEvidence.certifiedBody(), routeEvidence.certifiedPose(), routeEvidence.endpoint(),
                routeEvidence.worldFingerprint());
        TargetScanSnapshot revisedSnapshot = new TargetScanSnapshot(snapshot.sourceOrigin(), snapshot.sourceCenter(),
                snapshot.threatIdentity(), snapshot.evidence(), snapshot.captureTick(), snapshot.revision(),
                snapshot.expiryTick(), snapshot.probeRadius(), snapshot.bodyWidth(), snapshot.bodyHeight(),
                snapshot.scenario(), snapshot.fingerprint(), snapshot.tacticalIdentity(), routeEvidence);
        String routeEvidenceReason = validateLiveRouteEvidence(level, routeEvidence, revisedSnapshot.fingerprint());
        if (routeEvidenceReason != null) {
            state.terminalAdmissionFailures.put(leader.getUUID(),
                    new TerminalAdmissionFailure(semanticKey, semanticFingerprint));
            SoundAttractMod.LOGGER.warn("[LivingBridgeSafeBaseRoute] admission_certificate_rejected planner={} worker={} target={} reason={} revision={}",
                    leader.getUUID(), routeEvidence.workerId(), threatDestination, routeEvidenceReason, revision);
            return null;
        }
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_CONSTRUCTION)) {
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] route_evidence_handoff anchor={} worker={} intake={} routeNodes={} mode={} reason={}",
                    selectedAnchor, routeEvidence.workerId(), routeEvidence.intakeWindow(),
                    routeEvidence.routeNodes(), routeStats.routeMode, routeStats.failureReason);
        }
        return new ConstructionRequest(center, sourceBucket, threatDestination, selectedAnchor, threatBucket,
                revisedSnapshot, evidence, routeEvidence,
                requestCorridor,
                expiresAt, scanExpiresAt, revision, selected.observation());
    }

    private static ConstructionRequest resolveAdmissionRequest(Mob leader, BlockPos target, long now) {
        if (leader == null || target == null || !(leader.level() instanceof ServerLevel level)) return null;
        List<Mob> cluster = new ArrayList<>(MobGroupManager.getFollowers(leader));
        if (!cluster.contains(leader)) cluster.add(leader);
        cluster.removeIf(mob -> mob == null || !mob.isAlive() || mob.isRemoved());
        if (cluster.size() < MINIMUM_MEMBERS) return null;
        cluster.sort(Comparator.comparing(mob -> mob.getUUID().toString()));
        List<BlockPos> selectedAnchors = findClusterTargets(level, cluster, target);
        for (BlockPos selectedAnchor : selectedAnchors) {
            ConstructionRequest request = findConstructionRequest(level, leader, cluster, target, selectedAnchor);
            if (request != null && request.expiresAt() > now) return request;
        }
        return null;
    }

    private static SelectedTargetEvidence findClusterTargetEvidence(ServerLevel level, List<Mob> cluster,
                                                                   BlockPos threatDestination, BlockPos selectedAnchor) {
        if (level == null || cluster == null || cluster.isEmpty()) return null;
        Vec3 center = cluster.stream().map(Mob::position).reduce(Vec3.ZERO, Vec3::add).scale(1.0D / cluster.size());
        BlockPos threat = threatDestination != null ? threatDestination : resolveEffectiveThreat(level, cluster, null);
        PerLevelState state = state(level);
        LivingBridgePositionPlan plan = state.positionPlans.get(targetRosterFingerprint(cluster));
        if (plan == null || !plan.isLive(level.getGameTime()) || threat == null
                || !plan.destinationBucketContains(threat)) return null;
        TargetScanKey key = targetScanKey(new ColdStartSeedProvider(plan.sourceOrigin(), plan.lockedDestination()),
                plan.windowGeneration());
        CachedTargetScan cached = state(level).targetScans.get(key);
        if (!isValidCachedTargetScan(level, cluster, plan.lockedDestination(), level.getGameTime(),
                plan.windowGeneration(), key, cached)) return null;
        for (SelectedTargetEvidence candidate : cached.selectedEvidence) {
            if (candidate != null && candidate.evidence() != null
                    && (selectedAnchor == null || selectedAnchor.equals(candidate.evidence().anchor()))) {
                return candidate;
            }
        }
        return null;
    }

    static SelectedTargetEvidence findClusterTargetEvidenceForTest(Vec3 sourceCenter, BlockPos threatDestination,
                                                                   long generation, List<WallProfileScanner.TargetEvidence> evidence,
                                                                   long expiresAt, long now, BlockPos selectedAnchor) {
        if (sourceCenter == null || threatDestination == null || evidence == null || selectedAnchor == null
                || expiresAt <= now || generation < 0L) return null;
        for (WallProfileScanner.TargetEvidence candidate : evidence) {
            if (candidate != null && selectedAnchor.equals(candidate.anchor())
                    && threatDestination.equals(candidate.targetBucket())) {
                return new SelectedTargetEvidence(candidate, null, null, expiresAt, now, generation);
            }
        }
        return null;
    }

    private static TargetScanSnapshot buildTargetScanSnapshot(ServerLevel level, Vec3 sourceOrigin, Vec3 sourceCenter,
                                                              BlockPos threatIdentity, WallProfileScanner.TargetEvidence evidence,
                                                              long captureTick, long revision, long expiryTick, double probeRadius,
                                                              List<Mob> cluster, RouteEvidence routeEvidence) {
        List<CellFingerprint> fingerprint = new ArrayList<>();
        if (evidence != null) {
            fingerprint.add(new CellFingerprint(evidence.anchor(), "anchor", liveStateFingerprint(level, evidence.anchor())));
            fingerprint.add(new CellFingerprint(evidence.wallFace(), "wall", liveStateFingerprint(level, evidence.wallFace())));
            fingerprint.add(new CellFingerprint(evidence.supportCell(), "support", liveStateFingerprint(level, evidence.supportCell())));
            fingerprint.add(new CellFingerprint(evidence.edgeCell(), "edge", liveStateFingerprint(level, evidence.edgeCell())));
            fingerprint.add(new CellFingerprint(evidence.sourceApproachSurface(), "source_approach",
                    liveStateFingerprint(level, evidence.sourceApproachSurface())));
            fingerprint.add(new CellFingerprint(evidenceLandingSurface(evidence), "landing",
                    liveStateFingerprint(level, evidenceLandingSurface(evidence))));
            fingerprint.add(new CellFingerprint(evidenceInvasionSurface(evidence), "invasion",
                    liveStateFingerprint(level, evidenceInvasionSurface(evidence))));
            for (BlockPos structural : evidence.structuralCells()) {
                fingerprint.add(new CellFingerprint(structural, "structural", liveStateFingerprint(level, structural)));
            }
            for (int i = 0; i < evidence.hazardCells().size(); i++) {
                BlockPos cell = evidence.hazardCells().get(i);
                String kind = i < evidence.hazardKinds().size() ? String.valueOf(evidence.hazardKinds().get(i)) : "UNKNOWN";
                fingerprint.add(new CellFingerprint(cell, kind, liveStateFingerprint(level, cell)));
            }
        }
        if (routeEvidence != null && routeEvidence.intakeWindow() != null) {
            fingerprint.add(new CellFingerprint(routeEvidence.intakeWindow().feederCell(), "intake",
                    liveStateFingerprint(level, routeEvidence.intakeWindow().feederCell())));
            fingerprint.add(new CellFingerprint(routeEvidence.intakeWindow().feederApproach(), "intake_approach",
                    liveStateFingerprint(level, routeEvidence.intakeWindow().feederApproach())));
        }
        double bodyWidth = formationProbeRadius(cluster);
        double bodyHeight = LivingWallConfig.MAX_HEIGHT.get();
        ConstructionScenario scenario = evidence == null ? ConstructionScenario.NONE : parseConstructionScenario(evidence.scenario());
        LivingWallTacticalSnapshot.Identity identity = tacticalSnapshotIdentity(level, sourceOrigin, sourceCenter,
                threatIdentity, captureTick, revision, cluster);
        return new TargetScanSnapshot(sourceOrigin, sourceCenter, threatIdentity, evidence, captureTick, revision,
                expiryTick, probeRadius, bodyWidth, bodyHeight, scenario, fingerprint, identity, routeEvidence);
    }

    private static LivingWallTacticalSnapshot.Identity tacticalSnapshotIdentity(ServerLevel level, Vec3 sourceOrigin,
                                                                               Vec3 sourceCenter, BlockPos threatIdentity,
                                                                               long captureTick, long revision,
                                                                               List<Mob> cluster) {
        UUID sourceEntity = cluster == null || cluster.isEmpty() ? null : cluster.get(0).getUUID();
        UUID selectedMob = cluster == null || cluster.isEmpty() ? null : cluster.stream()
                .filter(Objects::nonNull)
                .map(Mob::getUUID)
                .sorted()
                .findFirst()
                .orElse(null);
        String rosterFingerprint = cluster == null ? "" : cluster.stream()
                .filter(Objects::nonNull)
                .map(Mob::getUUID)
                .sorted()
                .map(UUID::toString)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        String formationIdentity = Objects.toString(sourceOrigin) + '|' + Objects.toString(sourceCenter) + '|'
                + Objects.toString(threatIdentity) + '|' + captureTick;
        long terrainRevision = level == null ? -1L : level.getGameTime();
        return new LivingWallTacticalSnapshot.Identity(level == null ? null : level.dimension().identifier(),
                sourceEntity, selectedMob, rosterFingerprint, formationIdentity, terrainRevision, revision);
    }

    private static String liveStateFingerprint(ServerLevel level, BlockPos cell) {
        if (level == null || cell == null || !level.isLoaded(cell)) return "unloaded";
        BlockState state = level.getBlockState(cell);
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()) + ":" + state.getValues().hashCode() + ":" + state.getFluidState().isEmpty()
                + ":" + state.getCollisionShape(level, cell, CollisionContext.empty()).isEmpty();
    }

    private static long liveStateFingerprintHash(ServerLevel level, BlockPos cell) {
        if (level == null || cell == null || !level.isLoaded(cell)) return 0x9e3779b97f4a7c15L;
        BlockState state = level.getBlockState(cell);
        long hash = BuiltInRegistries.BLOCK.getKey(state.getBlock()).hashCode();
        hash = hash * 0x100000001b3L + state.getValues().hashCode();
        hash = hash * 0x100000001b3L + (state.getFluidState().isEmpty() ? 1L : 0L);
        return hash * 0x100000001b3L
                + (state.getCollisionShape(level, cell, CollisionContext.empty()).isEmpty() ? 1L : 0L);
    }

    private static List<Mob> directionalSubgroup(List<Mob> group, Vec3 center, BlockPos target) {
        if (group == null || group.isEmpty() || center == null || target == null) return List.of();
        Vec3 targetDelta = Vec3.atBottomCenterOf(target).subtract(center);
        Vec3 targetHorizontal = new Vec3(targetDelta.x, 0.0D, targetDelta.z);
        double targetLength = targetHorizontal.length();
        if (targetLength < 0.001D) return List.copyOf(group);
        Vec3 direction = targetHorizontal.scale(1.0D / targetLength);
        List<Mob> result = group.stream().filter(mob -> {
            Vec3 delta = new Vec3(mob.getX() - center.x, 0.0D, mob.getZ() - center.z);
            return delta.lengthSqr() < 0.0001D || delta.normalize().dot(direction) >= -0.15D;
        }).sorted(Comparator.comparingDouble(mob -> mob.distanceToSqr(center)))
                .toList();
        return result.size() >= MINIMUM_MEMBERS ? result : List.copyOf(group);
    }

    private static List<BlockPos> findClusterTargets(ServerLevel level, List<Mob> cluster, BlockPos threatDestination) {
        if (cluster == null || cluster.isEmpty()) return List.of();
        Vec3 center = cluster.stream().map(Mob::position).reduce(Vec3.ZERO, Vec3::add).scale(1.0D / cluster.size());
        Vec3 stableCenter = stableClusterCenter(cluster);
        int radius = LivingWallConfig.MAX_GAP.get();
        int height = LivingWallConfig.MAX_HEIGHT.get();
        BlockPos threat = threatDestination != null ? threatDestination : resolveEffectiveThreat(level, cluster, null);
        PerLevelState state = state(level);
        long now = level.getGameTime();
        long rosterIdentity = targetRosterFingerprint(cluster);
        LivingBridgePositionPlan positionPlan = state.positionPlans.get(rosterIdentity);
        if (positionPlan == null || !positionPlan.isLive(now)
                || !positionPlan.acceptsRoster(cluster.stream().map(Mob::getUUID).toList())
                || threat != null && !positionPlan.destinationBucketContains(threat)) {
            if (threat == null) return List.of();
            BlockPos fixedSource = resolveStableSourceOrigin(level, cluster, stableCenter, threat);
            if (fixedSource == null) return List.of();
            Vec3 direction = Vec3.atBottomCenterOf(threat).subtract(Vec3.atBottomCenterOf(fixedSource));
            direction = new Vec3(direction.x, 0.0D, direction.z);
            if (!Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 1.0E-6D) return List.of();
            positionPlan = new LivingBridgePositionPlan(level.dimension().identifier().toString(),
                    ++state.nextWindowGeneration, now, now + LivingBridgePositionPlan.WINDOW_TICKS,
                    fixedSource, threat, direction.normalize(), "NONE", height, radius, now, 0L,
                    cluster.stream().map(Mob::getUUID).toList(), state.nextRequestRevision);
            state.positionPlans.put(rosterIdentity, positionPlan);
        }
        BlockPos fixedSource = positionPlan.sourceOrigin();
        BlockPos lockedDestination = positionPlan.lockedDestination();
        int targetPressureTier = level.getServer() == null ? 0 : pressureTier(level.getServer().getAverageTickTimeNanos() / 1_000_000.0);
        int targetRayBudget = switch (targetPressureTier) {
            case 3 -> Math.min(8, MAX_TARGET_SCAN_RAYS);
            case 2 -> Math.min(16, MAX_TARGET_SCAN_RAYS);
            case 1 -> Math.min(24, MAX_TARGET_SCAN_RAYS);
            default -> MAX_TARGET_SCAN_RAYS;
        };
        long generation = positionPlan.windowGeneration();
        TargetScanKey key = targetScanKey(new ColdStartSeedProvider(fixedSource, lockedDestination), generation);
        CachedTargetScan cached = state.targetScans.get(key);
        if (isValidCachedTargetScan(level, cluster, lockedDestination, now, generation, key, cached)) return cached.targets();
        double probeRadius = formationProbeRadius(cluster);
        List<WallProfileScanner.RayEvidence> candidateRays = new ArrayList<>();
        List<WallProfileScanner.RayEvidence> primary = WallProfileScanner.scanEvidence(level, fixedSource, lockedDestination,
                height, radius, probeRadius, targetRayBudget);
        candidateRays.addAll(primary);
        if (primary.isEmpty()) {
            List<WallProfileScanner.RayEvidence> fallback = WallProfileScanner.scanEvidenceFallback(level, fixedSource, lockedDestination,
                    height, radius, probeRadius, targetRayBudget);
            candidateRays.addAll(fallback);
        }
        List<ScoredTarget> candidates = new ArrayList<>();
        int rays = 0;
        int hits = 0;
        int safeSurfaces = 0;
        int lowerCandidates = 0;
        int upperCandidates = 0;
        int hazardHits = 0;
        int rejectedScenario = 0;
        int rejectedSurface = 0;
        int rejectedTrigger = 0;
        int rejectedVertical = 0;
        LandingRejectionCounts landingRejections = new LandingRejectionCounts();
        BlockPos sourcePivot = fixedSource;
        int[] scanCounts = {rays, hits, safeSurfaces, lowerCandidates, upperCandidates, hazardHits,
                rejectedScenario, rejectedSurface, rejectedTrigger, rejectedVertical};
        int rayOrdinal = 0;
        for (WallProfileScanner.RayEvidence ray : candidateRays) {
            if (rayOrdinal >= MAX_TARGET_SCAN_RAYS) break;
            scanCounts[0]++;
            if (ray.firstCollisionCell() == null && WallProfileScanner.hazardCells(ray).isEmpty()) { rayOrdinal++; continue; }
            scanCounts[1]++;
            if (ray.collisionKinds().stream().anyMatch(WallProfileScanner::isHazardKind)) scanCounts[5]++;
            int structuralIndex = 0;
            int rayLandings = 0;
            BlockPos lastRayLanding = null;
            for (BlockPos structuralCell : ray.structuralCells()) {
                TargetLandingSelection landingSelection = selectTargetLanding(level, sourcePivot, lockedDestination, ray,
                        rayOrdinal, structuralCell, structuralIndex, rosterGeometry(cluster), Vec3.atBottomCenterOf(sourcePivot),
                        landingRejections);
                structuralIndex++;
                if (landingSelection == null) { scanCounts[7]++; continue; }
                BlockPos candidate = landingSelection.landing();
                if (lastRayLanding != null && lastRayLanding.equals(candidate)) continue;
                lastRayLanding = candidate;
                CanonicalCorridorDecision decision = landingSelection.decision();
                scanCounts[2]++;
                if (candidate.getY() < sourcePivot.getY()) scanCounts[3]++;
                if (candidate.getY() > sourcePivot.getY()) scanCounts[4]++;
                List<BlockPos> hazards = carriedRayHazards(ray, sourcePivot, candidate);
                ConstructionScenario scenario = parseConstructionScenario(decision.evidence().scenario());
                WallProfileScanner.TargetEvidence targetEvidence = decision.evidence();
                LivingWallBridgeAdapterPlanner.Candidate plannerCandidate = plannerCandidateFor(level, decision.corridor(), sourcePivot,
                        targetEvidence, scenario, rosterGeometry(cluster));
                LivingWallBridgeAdapterPlanner.AdapterSpec plannerSpec = adapterSpecForScenario(scenario).orElse(null);
                LivingWallBridgeAdapterPlanner.ScoredCandidate scoredCandidate = plannerCandidate == null || plannerSpec == null
                        ? null : LivingWallBridgeAdapterPlanner.score(plannerCandidate, plannerSpec, LivingWallConfig.MAX_BODIES.get()).orElse(null);
                candidates.add(new ScoredTarget(targetEvidence, decision.corridor(), scoredCandidate));
                rayLandings++;
                if (rayLandings >= 2) break;
            }
            rayOrdinal++;
        }
        rays = scanCounts[0]; hits = scanCounts[1]; safeSurfaces = scanCounts[2]; lowerCandidates = scanCounts[3];
        upperCandidates = scanCounts[4]; hazardHits = scanCounts[5]; rejectedScenario = scanCounts[6];
        rejectedSurface = scanCounts[7]; rejectedTrigger = scanCounts[8]; rejectedVertical = scanCounts[9];
        candidates.sort((left, right) -> {
            int scoreLeft = evaluateBuildingSpotScore(level, cluster, sourcePivot, lockedDestination, left);
            int scoreRight = evaluateBuildingSpotScore(level, cluster, sourcePivot, lockedDestination, right);
            if (scoreLeft != scoreRight) {
                return Integer.compare(scoreLeft, scoreRight);
            }
            LivingWallBridgeAdapterPlanner.ScoredCandidate first = left.scoredCandidate();
            LivingWallBridgeAdapterPlanner.ScoredCandidate second = right.scoredCandidate();
            if (first == null && second == null) {
                return Long.compareUnsigned(left.evidence().anchor().asLong(), right.evidence().anchor().asLong());
            }
            if (first == null) return 1;
            if (second == null) return -1;
            int result = LivingWallBridgeAdapterPlanner.ordering().compare(first, second);
            if (result != 0) return result;
            return Long.compareUnsigned(left.evidence().anchor().asLong(), right.evidence().anchor().asLong());
        });
        TargetRouteProbeStats routeStats = new TargetRouteProbeStats();
        List<SelectedTargetEvidence> finalSelected = selectDistinctCandidates(level, cluster, Vec3.atBottomCenterOf(sourcePivot), lockedDestination,
                candidates, routeStats);
        if (state.targetScans.size() >= MAX_TARGET_SCAN_CACHE_ENTRIES) state.targetScans.clear();
        state.targetScans.put(key, new CachedTargetScan(
                finalSelected.stream().map(SelectedTargetEvidence::evidence).toList(),
                finalSelected, now, now + (finalSelected.isEmpty() ? 60L : TARGET_SCAN_CACHE_TICKS), 0, now, generation,
                targetRosterFingerprint(cluster), targetTerrainFingerprint(level, finalSelected)));
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_FORMATION)) {
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Target scan center={} threat={} rays={} hits={} hazardHits={} safeSurfaces={} lowerCandidates={} upperCandidates={} rejectedSurface={} rejectedTrigger={} rejectedScenario={} rejectedVertical={} routeProbes={} routeSuccesses={} routeMode={} routeReuses={} routeRecomputations={} routeFailure={} candidates={} selected={} selectedTargets={} selectedDeltaY={}",
                    stableCenter, threat, rays, hits, hazardHits, safeSurfaces, lowerCandidates, upperCandidates,
                    rejectedSurface, rejectedTrigger, rejectedScenario, rejectedVertical,
                    routeStats.reservations, routeStats.successes, routeStats.routeMode, routeStats.routeReuses,
                    routeStats.routeRecomputations, routeStats.failureReason,
                    candidates.size(), finalSelected.isEmpty() ? null : finalSelected.get(0).evidence().anchor(),
                    finalSelected.stream().map(selection -> selection.evidence().anchor()).toList(),
                    finalSelected.isEmpty() ? "none" : finalSelected.get(0).evidence().anchor().getY() - sourcePivot.getY());
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] landing_rejection_breakdown {}", landingRejections);
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] route_candidate_failures counts={} last={}",
                    routeStats.candidateFailureCounts, routeStats.lastCandidateFailure);
        }
        return finalSelected.stream().map(selection -> selection.evidence().anchor()).toList();
    }

    private static int evaluateBuildingSpotScore(ServerLevel level, List<Mob> cluster, BlockPos sourcePivot,
                                                 BlockPos threatDestination, ScoredTarget target) {
        if (target == null || target.evidence() == null) return Integer.MAX_VALUE;
        int clusterSize = cluster == null ? 0 : cluster.size();
        int requiredBodies = target.scoredCandidate() != null
                ? target.scoredCandidate().candidate().bodyCount()
                : Integer.MAX_VALUE;
        ConstructionScenario scenario = parseConstructionScenario(target.evidence().scenario());
        int feasibilityCost;
        if (requiredBodies <= clusterSize) {
            feasibilityCost = requiredBodies * 20;
        } else {
            int deficit = requiredBodies - clusterSize;
            feasibilityCost = 20000 + deficit * 1000;
        }
        int scenarioCost;
        if (scenario == ConstructionScenario.WALL_LEDGE) {
            scenarioCost = -500;
        } else if (scenario == ConstructionScenario.JUMP_BRIDGE) {
            scenarioCost = -400;
        } else if (scenario == ConstructionScenario.DROP_TO_LEDGE) {
            int dropHeight = sourcePivot != null && target.evidence().anchor() != null
                    ? Math.abs(sourcePivot.getY() - target.evidence().anchor().getY())
                    : 0;
            if (dropHeight <= 3) {
                scenarioCost = 100;
            } else {
                scenarioCost = 500 + (dropHeight - 3) * 300;
            }
        } else {
            scenarioCost = 1000;
        }
        BlockPos pivotPos = target.corridor() != null ? target.corridor().pivot() : sourcePivot;
        int approachDist = sourcePivot != null && pivotPos != null ? sourcePivot.distManhattan(pivotPos) : 0;
        int approachCost = approachDist * 15;
        int threatCost = 0;
        if (threatDestination != null && sourcePivot != null && target.evidence().anchor() != null) {
            int pivotThreatDist = sourcePivot.distManhattan(threatDestination);
            int landingThreatDist = target.evidence().anchor().distManhattan(threatDestination);
            int progress = pivotThreatDist - landingThreatDist;
            if (progress > 0) {
                threatCost = -progress * 40;
            } else {
                threatCost = (-progress) * 60;
            }
        }
        int hazards = target.evidence().hazardCells() != null ? target.evidence().hazardCells().size() : 0;
        int hazardCost = hazards * 150;
        int egressCost = 0;
        if (target.scoredCandidate() != null && target.scoredCandidate().candidate().supportedEgress()) {
            egressCost = -200;
        } else {
            egressCost = 300;
        }
        return feasibilityCost + scenarioCost + approachCost + threatCost + hazardCost + egressCost;
    }

    static List<BlockPos> findClusterTargetsForTest(ServerLevel level, List<Mob> cluster, BlockPos threatDestination) {
        return findClusterTargets(level, cluster, threatDestination);
    }

    private static List<SelectedTargetEvidence> selectDistinctCandidates(ServerLevel level, List<Mob> cluster,
                                                                         Vec3 center, BlockPos threat,
                                                                         List<ScoredTarget> candidates,
                                                                         TargetRouteProbeStats routeStats) {
        List<SelectedTargetEvidence> selected = new ArrayList<>();
        for (ScoredTarget candidate : candidates) {
            BlockPos routeTarget = candidate == null || candidate.evidence() == null ? null : candidate.evidence().anchor();
            CanonicalFeetCorridor corridor = candidate == null ? null : candidate.corridor();
            if (candidate != null && candidate.evidence() != null
                    && parseConstructionScenario(candidate.evidence().scenario()) != ConstructionScenario.NONE
                    && corridor == null) {
                routeStats.failureReason = "canonical_corridor_missing";
                routeStats.recordCandidateFailure(routeStats.failureReason);
                continue;
            }
            RouteProbeOutcome probeOutcome = routeBudgetSatisfied(level, cluster, routeTarget, candidate.evidence(), corridor, routeStats);
            if (!probeOutcome.successful()) {
                routeStats.recordCandidateFailure(probeOutcome.failureReason());
                continue;
            }
            boolean distinct = selected.stream().noneMatch(existing -> {
                Vec3 first = Vec3.atBottomCenterOf(existing.evidence().anchor()).subtract(center);
                Vec3 second = Vec3.atBottomCenterOf(candidate.evidence().anchor()).subtract(center);
                return first.lengthSqr() > 0.001D && second.lengthSqr() > 0.001D
                        && first.normalize().dot(second.normalize()) > 0.82D;
            });
            if (distinct) selected.add(new SelectedTargetEvidence(candidate.evidence(), corridor, probeOutcome.routeEvidence(),
                    level == null ? Long.MIN_VALUE : level.getGameTime() + TARGET_SCAN_CACHE_TICKS,
                    level == null ? -1L : level.getGameTime(), level == null ? -1L : level.getGameTime(), null));
            if (selected.size() >= TOP_BUILDING_SPOTS_QUOTA) break;
        }
        routeStats.failureReason = selected.isEmpty() ? routeStats.aggregateFailure() : null;
        return selected;
    }

    static RouteProbeProof routeBudgetSatisfiedForTest(ServerLevel level, List<Mob> cluster, BlockPos target,
                                                      WallProfileScanner.TargetEvidence evidence, CanonicalFeetCorridor corridor) {
        BlockPos routeTarget = target;
        TargetRouteProbeStats stats = new TargetRouteProbeStats();
        RouteProbeOutcome outcome = routeBudgetSatisfied(level, cluster, routeTarget, evidence, corridor, stats);
        return new RouteProbeProof(corridor != null, stats.intakeResolutionAttempts, stats.routeProbeAttempts,
                stats.reservations, stats.pathCreationAttempts, outcome.failureReason());
    }

    static RouteProbeProof routeProbeProofForTest(ServerLevel level, List<Mob> cluster, BlockPos target,
                                                 WallProfileScanner.TargetEvidence evidence, CanonicalFeetCorridor corridor) {
        return routeBudgetSatisfiedForTest(level, cluster, target, evidence, corridor);
    }

    static String routeBudgetFailureReasonForTest(ServerLevel level, List<Mob> cluster, BlockPos target,
                                                  WallProfileScanner.TargetEvidence evidence, CanonicalFeetCorridor corridor) {
        return routeProbeProofForTest(level, cluster, target, evidence, corridor).failureReason();
    }

    record RouteProbeProof(boolean staticCorridorValid, int intakeResolutionAttempts,
                                   int routeProbeAttempts, int reservations, int pathCreationAttempts,
                                   String failureReason) {}

    private static RouteProbeOutcome routeBudgetSatisfied(ServerLevel level, List<Mob> cluster, BlockPos routeTarget,
                                                          WallProfileScanner.TargetEvidence evidence,
                                                          CanonicalFeetCorridor corridor,
                                                          TargetRouteProbeStats routeStats) {
        if (routeStats == null) routeStats = new TargetRouteProbeStats();
        if (level == null || cluster == null || cluster.isEmpty() || routeTarget == null) {
            routeStats.failureReason = "invalid_route_target";
            return new RouteProbeOutcome(null, routeStats.failureReason);
        }
        if (corridor == null) {
            routeStats.failureReason = CorridorValidationReason.NULL_CORRIDOR.name().toLowerCase();
            return new RouteProbeOutcome(null, routeStats.failureReason);
        }
        CorridorValidationResult routeValidation = validateStaticCorridor(
                level, corridor.pivot(), routeTarget, evidence, corridor);
        if (!routeValidation.routeProbeAllowed()) {
            routeStats.failureReason = routeValidation.reason().name().toLowerCase();
            return new RouteProbeOutcome(null, routeStats.failureReason);
        }
        LivingBridgePositionPlan positionPlan = state(level).positionPlans.get(targetRosterFingerprint(cluster));
        List<Mob> orderedWorkers = cluster.stream()
                .filter(mob -> mob != null && mob.isAlive() && !mob.isRemoved() && isEligible(mob))
                .sorted(Comparator.comparing((Mob mob) -> {
                            GroundingObservation grounding = state(level).groundingObservations.get(mob.getUUID());
                            return positionPlan == null || grounding != null && grounding.groundedTicks() >= 2 ? 0 : 1;
                        })
                        .thenComparingDouble(mob -> mob.distanceToSqr(Vec3.atBottomCenterOf(routeTarget))))
                .limit(TARGET_ROUTE_PROBE_LIMIT)
                .toList();
        routeStats.eligibleCandidates = (int) cluster.stream()
                .filter(mob -> mob != null && mob.isAlive() && !mob.isRemoved() && isEligible(mob)).count();
        routeStats.groundedCandidates = (int) orderedWorkers.stream().filter(mob -> {
            GroundingObservation grounding = state(level).groundingObservations.get(mob.getUUID());
            return positionPlan == null || grounding != null && grounding.groundedTicks() >= 2;
        }).count();
        if (orderedWorkers.isEmpty()) {
            routeStats.failureReason = "no_eligible_worker";
            return new RouteProbeOutcome(null, routeStats.failureReason);
        }
        List<SourceStagingAttempt> attempts = new ArrayList<>();
        String deferredFailure = null;
        for (Mob probe : orderedWorkers) {
            WorkerRouteStartState start = resolveWorkerRouteStartState(level, probe);
            routeStats.intakeResolutionAttempts++;
            GroundingObservation grounding = state(level).groundingObservations.get(probe.getUUID());
            boolean closeToAnchor = probe.distanceToSqr(Vec3.atBottomCenterOf(routeTarget)) <= 16.0D;
            boolean hasSupport = hasMobCompatibleStartSupport(level, probe, probe.getBoundingBox());
            if (positionPlan != null && positionPlan.startTick() < level.getGameTime()
                    && (grounding == null || grounding.groundedTicks() < (closeToAnchor || hasSupport ? 1 : 2))) {
                deferredFailure = preferDeferredReason(deferredFailure, "worker_airborne_defer");
                routeStats.deferredCandidates++;
                routeStats.workerFailureReason = "worker_airborne_defer";
                continue;
            }
            AdmissionIntakeResolution intakeResolution = resolveAdmissionIntakeWindows(level, probe, routeTarget, corridor, start);
            if (!start.ready()) {
                if (deferredRouteFailure(start.failureReason())) deferredFailure = preferDeferredReason(deferredFailure, start.failureReason());
            }
            if (intakeResolution.windows().isEmpty()) {
                routeStats.failureReason = intakeResolution.failureReason() == null
                        ? "no_walkable_intake"
                        : intakeResolution.failureReason();
                routeStats.workerFailureReason = routeStats.failureReason;
                if (deferredRouteFailure(routeStats.failureReason)) {
                    deferredFailure = preferDeferredReason(deferredFailure, routeStats.failureReason);
                }
                continue;
            }
            BlockPos workerSurface = start.surface();
            for (IntakeWindow intakeWindow : intakeResolution.windows()) {
                if (intakeWindow == null || intakeWindow.feederApproach() == null || workerSurface == null) continue;
                attempts.add(new SourceStagingAttempt(probe, intakeWindow,
                        admissionIntakeOrigin(routeTarget, corridor), workerSurface,
                        workerSurface.distSqr(intakeWindow.feederApproach())));
            }
        }
        attempts.sort(Comparator
                .comparingDouble(SourceStagingAttempt::distanceToEndpointSqr)
                .thenComparing(attempt -> attempt.worker().getUUID().toString())
                .thenComparingLong(attempt -> attempt.intakeWindow().feederApproach().asLong()));
        if (attempts.isEmpty()) {
            String reason = deferredFailure != null ? deferredFailure
                    : routeStats.failureReason == null ? "no_walkable_intake" : routeStats.failureReason;
            routeStats.failureReason = reason;
            routeStats.workerFailureReason = reason;
            return new RouteProbeOutcome(null, reason);
        }
        for (SourceStagingAttempt attempt : attempts) {
            if (routeStats.reservations >= TARGET_ROUTE_PROBE_LIMIT) {
                routeStats.failureReason = localRouteBudgetFailure(routeStats.reservations, routeStats.routeSamples,
                        routeStats.successes, attempts.size());
                routeStats.workerFailureReason = routeStats.failureReason;
                break;
            }
            routeStats.reservations++;
            routeStats.routeProbeAttempts++;
            routeStats.routeMode = "SOURCE_APPROACH";
            routeStats.routeRecomputations++;
            routeStats.routeSamples = Math.min(TARGET_ROUTE_SAMPLE_LIMIT, routeStats.routeSamples + 1);
            routeStats.pathCreationAttempts++;
            Path path = attempt.worker().getNavigation().createPath(attempt.intakeWindow().feederApproach(), 0);
            List<BlockPos> route = stableRouteNodes(level, attempt.worker(), attempt.intakeWindow(), routeTarget, attempt.intakeOrigin(), path, corridor);
            routeStats.routeNodes = route.size();
            if (path == null || !path.canReach() || path.getTarget() == null
                    || path.getTarget().distSqr(attempt.intakeWindow().feederApproach()) > 1.0D) {
                routeStats.failureReason = "intake_unreachable";
                routeStats.workerFailureReason = routeStats.failureReason;
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ROUTING)) {
                    SoundAttractMod.LOGGER.info("[LivingWallCoordinator] route_probe_rejected worker={} target={} intake={} pathNull={} canReach={} pathTarget={} pathNodes={} failure={}",
                            attempt.worker().getUUID(), routeTarget, attempt.intakeWindow().feederApproach(),
                            path == null, path != null && path.canReach(), path == null ? null : path.getTarget(),
                            path == null ? 0 : path.getNodeCount(), routeStats.failureReason);
                }
                continue;
            }
            if (route.isEmpty()) {
                routeStats.failureReason = "source_approach_invalid";
                routeStats.workerFailureReason = routeStats.failureReason;
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ROUTING)) {
                    SoundAttractMod.LOGGER.info("[LivingWallCoordinator] route_probe_rejected worker={} target={} intake={} pathTarget={} pathNodes={} failure={}",
                            attempt.worker().getUUID(), routeTarget, attempt.intakeWindow().feederApproach(),
                            path.getTarget(), path.getNodeCount(), routeStats.failureReason);
                }
                continue;
            }
            List<BlockPos> fullRoute = route;
            routeStats.routeMode = "SOURCE_APPROACH";
            routeStats.successes++;
            routeStats.failureReason = null;
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ROUTING)) {
                SoundAttractMod.LOGGER.info("[LivingWallCoordinator] route_probe_summary worker={} target={} intake={} routeProbes={} routeSuccesses={} routeNodes={} routeSamples={} routeMode={} routeFailure={}",
                        attempt.worker().getUUID(), routeTarget, attempt.intakeWindow().feederApproach(),
                        routeStats.reservations, routeStats.successes, fullRoute.size(), routeStats.routeSamples,
                        routeStats.routeMode, routeStats.failureReason);
            }
            Vec3 certifiedStart = attempt.worker().position();
            AABB certifiedBody = attempt.worker().getBoundingBox();
            BlockPos endpoint = attempt.intakeWindow().feederApproach();
            String worldFingerprint = routeWorldFingerprint(level, fullRoute);
            String certificateFingerprint = routeEvidenceFingerprint(attempt.worker().getUUID(), attempt.intakeWindow(),
                    routeTarget, fullRoute, level.getGameTime(), certifiedStart, worldFingerprint);
            RouteEvidence routeEvidence = new RouteEvidence(attempt.worker().getUUID(), attempt.intakeWindow(), routeTarget,
                    fullRoute, true, level.getGameTime(), certificateFingerprint, corridor.corridorId(),
                    level.dimension().identifier(), certifiedStart, certifiedBody, attempt.worker().getPose(), endpoint,
                    worldFingerprint);
            return new RouteProbeOutcome(routeEvidence, null);
        }
        if (routeStats.failureReason == null && deferredFailure != null) {
            routeStats.failureReason = deferredFailure;
            routeStats.workerFailureReason = deferredFailure;
        }
        return new RouteProbeOutcome(null, routeStats.failureReason == null ? "no_walkable_intake" : routeStats.failureReason);
    }

    private static RouteProbeOutcome routeBudgetSatisfied(ServerLevel level, List<Mob> cluster, BlockPos routeTarget,
                                                          WallProfileScanner.TargetEvidence evidence,
                                                          CanonicalFeetCorridor corridor,
                                                          TargetRouteProbeStats routeStats,
                                                          ConstructionObservation observation) {
        if (observation == null) return routeBudgetSatisfied(level, cluster, routeTarget, evidence, corridor, routeStats);
        if (!Objects.equals(observation.anchor(), routeTarget) || !Objects.equals(observation.corridor(), corridor)) {
            if (routeStats != null) routeStats.failureReason = "observation_mismatch";
            return new RouteProbeOutcome(null, "observation_mismatch");
        }
        for (WorkerObservation worker : observation.workers().values()) {
            if (worker == null || worker.intakeWindow() == null || worker.sourceRoute().isEmpty()
                    || !"observed".equals(worker.routeOutcome())) {
                if (routeStats != null) routeStats.failureReason = "stored_route_incomplete";
                return new RouteProbeOutcome(null, "stored_route_incomplete");
            }
        }
        return new RouteProbeOutcome(observation.routeEvidence(), null);
    }

    private static Mob nearestEligibleRouteWorker(List<Mob> cluster, BlockPos routeTarget) {
        if (cluster == null || cluster.isEmpty() || routeTarget == null) return null;
        return cluster.stream()
                .filter(mob -> mob != null && mob.isAlive() && !mob.isRemoved() && isEligible(mob))
                .sorted(Comparator.comparingDouble(mob -> mob.distanceToSqr(Vec3.atBottomCenterOf(routeTarget))))
                .findFirst()
                .orElse(null);
    }

    private static AdmissionIntakeResolution resolveAdmissionIntakeWindows(ServerLevel level, Mob worker, BlockPos routeTarget,
                                                                          CanonicalFeetCorridor corridor,
                                                                          WorkerRouteStartState start) {
        if (level == null || worker == null || routeTarget == null) {
            return new AdmissionIntakeResolution(List.of(), "invalid_intake_context");
        }
        BlockPos intakeOrigin = admissionIntakeOrigin(routeTarget, corridor);
        if (!start.ready()) {
            debugAdmissionIntakeFailure(worker, start, start.failureReason());
            return new AdmissionIntakeResolution(List.of(), start.failureReason());
        }
        if (!isLoadedCellColumn(level, intakeOrigin)) {
            String reason = "intake_origin_unloaded_defer";
            debugAdmissionIntakeFailure(worker, start, reason);
            return new AdmissionIntakeResolution(List.of(), reason);
        }
        BlockPos workerCell = start.surface();
        Vec3 towardWorker = Vec3.atBottomCenterOf(workerCell).subtract(Vec3.atBottomCenterOf(intakeOrigin));
        if (towardWorker.horizontalDistanceSqr() < 1.0E-6D) {
            return new AdmissionIntakeResolution(List.of(), "degenerate_worker_vector");
        }
        List<BlockPos> corridorCells = admissionIntakeCandidates(intakeOrigin, towardWorker);
        if (corridorCells.isEmpty()) return new AdmissionIntakeResolution(List.of(), "no_worker_facing_cells");

        int loadedCandidates = 0;
        boolean hadSupportedSurface = false;
        boolean hadElevationSurface = false;
        boolean hadClearSurface = false;
        boolean hadCorrectSideSurface = false;
        boolean hadUnsafeCandidate = false;
        boolean hadNoSupportCandidate = false;
        boolean hadBlockedBodyCandidate = false;
        Map<String, Integer> rejectionCounts = new LinkedHashMap<>();
        Set<BlockPos> structuralCells = Set.of(intakeOrigin);
        Set<BlockPos> rejectedBridgeCells = sourceApproachRejectedCells(corridor);
        List<IntakeWindow> validEndpoints = new ArrayList<>();
        for (BlockPos candidate : corridorCells) {
            if (candidate == null) continue;
            if (!isLoadedCellColumn(level, candidate)) {
                countLocalRejection(rejectionCounts, "unloaded");
                debugAdmissionIntakeCandidate(worker, intakeOrigin, candidate, null, "unloaded");
                continue;
            }
            loadedCandidates++;
            AdmissionIntakeSurfaceResolution surfaceResolution = resolveAdmissionIntakeSurface(level, worker, candidate, intakeOrigin);
            if (surfaceResolution.surface() == null) {
                String failureReason = surfaceResolution.failureReason();
                countLocalRejection(rejectionCounts, failureReason);
                if (Objects.equals(failureReason, "unsafe")) {
                    hadUnsafeCandidate = true;
                } else if (Objects.equals(failureReason, "no_support")) {
                    hadNoSupportCandidate = true;
                } else if (Objects.equals(failureReason, "clearance")) {
                    hadBlockedBodyCandidate = true;
                }
                debugAdmissionIntakeCandidate(worker, intakeOrigin, candidate, null, surfaceResolution.failureReason());
                continue;
            }
            BlockPos surface = surfaceResolution.surface();
            hadSupportedSurface = true;
            if (!isIntakeWindowElevationCompatible(intakeOrigin.getY(), surface.getY())) {
                countLocalRejection(rejectionCounts, "elevation");
                debugAdmissionIntakeCandidate(worker, intakeOrigin, candidate, surface, "elevation");
                continue;
            }
            hadElevationSurface = true;
            if (!isIntakeWindowClear(surface, structuralCells, List.of())) {
                countLocalRejection(rejectionCounts, "clearance");
                debugAdmissionIntakeCandidate(worker, intakeOrigin, candidate, surface, "clearance");
                continue;
            }
            hadClearSurface = true;
            boolean closeToWorker = surface.closerThan(workerCell, 2.0D);
            if ((!isAdmissionIntakeOnSourceSide(intakeOrigin, towardWorker, surface) && !closeToWorker)
                    || (intakeOrigin.distSqr(workerCell) > 4.0D && surface.distSqr(workerCell) > intakeOrigin.distSqr(workerCell) + 4.0D && !closeToWorker)
                    || rejectedBridgeCells.contains(surface)) {
                countLocalRejection(rejectionCounts, "wrong_side");
                debugAdmissionIntakeCandidate(worker, intakeOrigin, candidate, surface, "wrong_side");
                continue;
            }
            hadCorrectSideSurface = true;
            validEndpoints.add(new IntakeWindow(surface, surface, false));
            if (validEndpoints.size() >= SOURCE_STAGING_MAX_ENDPOINTS) {
                debugAdmissionIntakeFailure(worker, start, null, corridorCells.size(), loadedCandidates, rejectionCounts);
                return new AdmissionIntakeResolution(validEndpoints, null);
            }
        }
        String reason = terminalAdmissionIntakeFailureReason(loadedCandidates, hadSupportedSurface, hadUnsafeCandidate,
                hadNoSupportCandidate, hadBlockedBodyCandidate, hadElevationSurface, hadClearSurface, hadCorrectSideSurface);
        if (!validEndpoints.isEmpty()) return new AdmissionIntakeResolution(validEndpoints, null);
        debugAdmissionIntakeFailure(worker, start, reason, corridorCells.size(), loadedCandidates, rejectionCounts);
        return new AdmissionIntakeResolution(List.of(), reason);
    }

    static BlockPos admissionIntakeOriginForTest(BlockPos routeTarget, CanonicalFeetCorridor corridor) {
        return admissionIntakeOrigin(routeTarget, corridor);
    }

    static List<BlockPos> admissionIntakeCandidatesForTest(BlockPos routeTarget, Vec3 towardWorker) {
        return admissionIntakeCandidates(routeTarget, towardWorker);
    }

    static String validateSourceApproachRouteForTest(BlockPos intakeOrigin, Vec3 towardWorker, CanonicalFeetCorridor corridor,
                                                     List<BlockPos> routeNodes) {
        return validateSourceApproachRoute(intakeOrigin, towardWorker, corridor, routeNodes);
    }

    static boolean canonicalAdmissionAccessSatisfiedForTest(RouteEvidence routeEvidence, CanonicalFeetCorridor corridor,
                                                            BlockPos selectedAnchor) {
        return canonicalAdmissionAccessSatisfied(routeEvidence, corridor, selectedAnchor);
    }

    static boolean supportedDropForTest(BlockPos pivot, BlockPos anchor, double maxDrop) {
        return isSupportedDrop(pivot, anchor, maxDrop);
    }

    static boolean clearJumpEndpointForTest(boolean loaded, boolean feetFluidFree, boolean headFluidFree,
                                             boolean feetClear, boolean headClear) {
        return loaded && feetFluidFree && headFluidFree && feetClear && headClear;
    }

    static boolean orderedCorridorTransitionValidForTest(CanonicalFeetCorridor corridor,
                                                         WallProfileScanner.TargetEvidence evidence,
                                                         BlockPos threatDestination, ConstructionScenario scenario) {
        return orderedCorridorTransitionValid(corridor, evidence, threatDestination, scenario);
    }

    static String classifyWorkerRouteStartForTest(boolean blockLoaded, boolean aboveLoaded, boolean belowLoaded,
                                                  boolean grounded, float fallDistance, boolean inFluid) {
        return classifyWorkerRouteStartReason(blockLoaded, aboveLoaded, belowLoaded, grounded,
                !grounded || fallDistance > 0.0F, inFluid ? "present" : "dry");
    }

    static String terminalAdmissionIntakeFailureReasonForTest(int loadedCandidates, boolean hadSupportedSurface,
                                                              boolean hadUnsafeCandidate, boolean hadNoSupportCandidate,
                                                              boolean hadBlockedBodyCandidate, boolean hadElevationSurface,
                                                              boolean hadClearSurface, boolean hadCorrectSideSurface) {
        return terminalAdmissionIntakeFailureReason(loadedCandidates, hadSupportedSurface, hadUnsafeCandidate,
                hadNoSupportCandidate, hadBlockedBodyCandidate, hadElevationSurface, hadClearSurface, hadCorrectSideSurface);
    }

    static String admissionIntakeFailureScopeForTest(String reason) {
        AdmissionIntakeFailureScope scope = admissionIntakeFailureScope(reason);
        return scope == null ? null : scope.name();
    }

    private static BlockPos admissionIntakeOrigin(BlockPos routeTarget, CanonicalFeetCorridor corridor) {
        if (corridor != null && corridor.takeoffFeet() != null) return corridor.takeoffFeet();
        return routeTarget == null ? null : routeTarget.immutable();
    }

    private static WorkerRouteStartState resolveWorkerRouteStartState(ServerLevel level, Mob worker) {
        if (level == null || worker == null) {
            return new WorkerRouteStartState(null, false, false, false, false, false, "unknown", Integer.MIN_VALUE,
                    "invalid_intake_context");
        }
        BlockPos block = worker.blockPosition();
        boolean blockLoaded = level.isLoaded(block);
        boolean aboveLoaded = level.isLoaded(block.above());
        boolean belowLoaded = level.isLoaded(block.below());
        boolean supported = hasMobCompatibleStartSupport(level, worker, worker.getBoundingBox());
        boolean grounded = worker.onGround() || supported;
        boolean falling = !grounded && (worker.fallDistance > 0.5F || worker.getDeltaMovement().y < -0.35D);
        String fluidState = worker.isInLava() ? "lava" : worker.isInWater() ? "water" : "dry";
        String failureReason = classifyWorkerRouteStartReason(blockLoaded, aboveLoaded, belowLoaded, grounded, falling, fluidState);
        BlockPos surface = failureReason == null
                ? resolveWorkerStartSurface(level, worker)
                : null;
        if (failureReason == null && surface == null) {
            failureReason = "worker_surface_unresolved";
        }
        return new WorkerRouteStartState(surface, blockLoaded, aboveLoaded, belowLoaded, grounded, falling, fluidState,
                block.getY(), failureReason);
    }

    private static BlockPos resolveWorkerStartSurface(ServerLevel level, Mob worker) {
        if (level == null || worker == null) return null;
        AABB body = worker.getBoundingBox();
        if (body == null) return null;
        for (BlockPos candidate : workerStartSurfaceCandidates(worker, body)) {
            if (isWorkerStartSurface(level, worker, body, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static List<BlockPos> workerStartSurfaceCandidates(Mob worker, AABB body) {
        if (worker == null || body == null) return List.of();
        Set<BlockPos> ordered = new java.util.LinkedHashSet<>();
        ordered.add(worker.blockPosition().immutable());
        int minX = Mth.floor(body.minX + CONTACT_EPSILON);
        int maxX = Mth.floor(body.maxX - CONTACT_EPSILON);
        int minZ = Mth.floor(body.minZ + CONTACT_EPSILON);
        int maxZ = Mth.floor(body.maxZ - CONTACT_EPSILON);
        int feetY = Mth.floor(body.minY + CONTACT_EPSILON);
        int standingY = Mth.floor(body.minY + 0.5D);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                ordered.add(new BlockPos(x, standingY, z));
                if (feetY != standingY) {
                    ordered.add(new BlockPos(x, feetY, z));
                }
            }
        }
        return List.copyOf(ordered);
    }

    private static boolean isWorkerStartSurface(ServerLevel level, Mob worker, AABB body, BlockPos cell) {
        if (level == null || worker == null || body == null || cell == null || !isLoadedCellColumn(level, cell)) return false;
        CollisionContext context = CollisionContext.of(worker);
        BlockState feet = level.getBlockState(cell);
        BlockState head = level.getBlockState(cell.above());
        BlockState support = level.getBlockState(cell.below());
        if (feet.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
                || support.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
            return false;
        }
        if ((isOpenTrapdoor(feet) || isOpenTrapdoor(support)) && !hasMobCompatibleStartSupport(level, worker, body)) {
            return false;
        }
        VoxelShape feetShape = feet.getCollisionShape(level, cell, context);
        if (!feetShape.isEmpty()) {
            for (AABB box : feetShape.toAabbs()) {
                if (box.move(cell).maxY > body.minY + CONTACT_EPSILON) {
                    return false;
                }
            }
        }
        VoxelShape headShape = head.getCollisionShape(level, cell.above(), context);
        if (!headShape.isEmpty()) {
            for (AABB box : headShape.toAabbs()) {
                AABB world = box.move(cell.above());
                if (world.minY < body.maxY - CONTACT_EPSILON && world.maxY > body.minY + CONTACT_EPSILON) {
                    return false;
                }
            }
        }
        VoxelShape supportShape = support.getCollisionShape(level, cell.below(), context);
        if (supportShape.isEmpty()) {
            if (feetShape.isEmpty()) {
                return false;
            }
        }
        if (findPositiveVolumeBlockage(level, body, null, 0.0D) != null) {
            return false;
        }
        return hasMobCompatibleStartSupport(level, worker, body);
    }

    private static boolean hasMobCompatibleStartSupport(ServerLevel level, Mob worker, AABB body) {
        if (level == null || worker == null || body == null) return false;
        CollisionContext context = CollisionContext.of(worker);
        int minX = Mth.floor(body.minX + CONTACT_EPSILON);
        int maxX = Mth.floor(body.maxX - CONTACT_EPSILON);
        int minZ = Mth.floor(body.minZ + CONTACT_EPSILON);
        int maxZ = Mth.floor(body.maxZ - CONTACT_EPSILON);
        int minY = Mth.floor(body.minY) - 1;
        int maxY = Mth.floor(body.minY + CONTACT_EPSILON);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    if (!level.isLoaded(cursor)) return false;
                    BlockState state = level.getBlockState(cursor);
                    if (state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) || isOpenTrapdoor(state)) {
                        continue;
                    }
                    VoxelShape shape = state.getCollisionShape(level, cursor, context);
                    if (isMobCompatibleWorkerSupportShape(shape, body, cursor)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean isMobCompatibleWorkerSupportShape(VoxelShape shape, AABB body, BlockPos supportPos) {
        if (shape == null || shape.isEmpty() || body == null || supportPos == null) return false;
        AABB footprint = new AABB(body.minX, body.minY - CONTACT_EPSILON, body.minZ, body.maxX, body.minY + CONTACT_EPSILON, body.maxZ);
        for (AABB piece : shape.toAabbs()) {
            AABB world = piece.move(supportPos);
            if (world.maxY < body.minY - 0.2D || world.maxY > body.minY + 0.2D) {
                continue;
            }
            double overlapX = Math.min(world.maxX, footprint.maxX) - Math.max(world.minX, footprint.minX);
            double overlapZ = Math.min(world.maxZ, footprint.maxZ) - Math.max(world.minZ, footprint.minZ);
            if (overlapX > CONTACT_EPSILON && overlapZ > CONTACT_EPSILON) {
                return true;
            }
        }
        return false;
    }

    private static String classifyWorkerRouteStartReason(boolean blockLoaded, boolean aboveLoaded, boolean belowLoaded,
                                                         boolean grounded, boolean falling, String fluidState) {
        if (!blockLoaded || !aboveLoaded || !belowLoaded) return "worker_start_unloaded_defer";
        if (falling || !grounded) return "worker_airborne_defer";
        if (!Objects.equals(fluidState, "dry")) return "worker_fluid_defer";
        return null;
    }

    private static boolean isLoadedCellColumn(ServerLevel level, BlockPos cell) {
        return level != null && cell != null && level.isLoaded(cell) && level.isLoaded(cell.above()) && level.isLoaded(cell.below());
    }

    private static void debugAdmissionIntakeFailure(Mob worker, WorkerRouteStartState start, String reason) {
        debugAdmissionIntakeFailure(worker, start, reason, 0, 0, Map.of());
    }

    private static void debugAdmissionIntakeFailure(Mob worker, WorkerRouteStartState start, String reason,
                                                    int candidateCount, int loadedCandidates,
                                                    Map<String, Integer> rejectionCounts) {
        if (worker == null || start == null || SoundAttractConfig.COMMON == null || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ADMISSION)) {
            return;
        }
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ADMISSION)) {
        SoundAttractMod.LOGGER.debug("[LivingWallCoordinator] admission_intake_rejected worker={} pos={} blockLoaded={} aboveLoaded={} belowLoaded={} grounded={} falling={} fluid={} referenceY={} reason={} scope={} candidateCount={} loadedCandidates={} rejectionCounts={}",
                worker.getUUID(), worker.position(), start.blockLoaded(), start.aboveLoaded(), start.belowLoaded(),
                start.grounded(), start.falling(), start.fluidState(), start.referenceY(), reason,
                admissionIntakeFailureScope(reason), candidateCount, loadedCandidates, rejectionCounts);
        }
    }

    private static AdmissionIntakeFailureScope admissionIntakeFailureScope(String reason) {
        if (reason == null) return null;
        return switch (reason) {
            case "intake_origin_unloaded_defer", "intake_cells_unloaded_defer", "no_worker_facing_cells" -> AdmissionIntakeFailureScope.CANDIDATE;
            default -> AdmissionIntakeFailureScope.WORKER;
        };
    }

    private static String terminalAdmissionIntakeFailureReason(int loadedCandidates, boolean hadSupportedSurface,
                                                               boolean hadUnsafeCandidate, boolean hadNoSupportCandidate,
                                                               boolean hadBlockedBodyCandidate, boolean hadElevationSurface,
                                                               boolean hadClearSurface, boolean hadCorrectSideSurface) {
        if (loadedCandidates <= 0) return "intake_cells_unloaded_defer";
        if (!hadSupportedSurface) {
            if (hadUnsafeCandidate) return "worker_intake_surface_unsafe";
            if (hadBlockedBodyCandidate) return "worker_intake_body_blocked";
            if (hadNoSupportCandidate) return "worker_intake_no_support";
            return "no_candidate_intake_surface";
        }
        if (!hadElevationSurface) return "worker_intake_surface_elevation";
        if (!hadClearSurface) return "worker_intake_clearance_blocked";
        if (!hadCorrectSideSurface) return "worker_intake_wrong_side";
        return "worker_no_viable_intake";
    }

    private static List<BlockPos> admissionIntakeCandidates(BlockPos routeTarget, Vec3 towardWorker) {
        if (routeTarget == null || towardWorker == null || towardWorker.horizontalDistanceSqr() < 1.0E-6D) {
            return List.of();
        }
        Vec3 horizontal = new Vec3(towardWorker.x, 0.0D, towardWorker.z).normalize();
        Vec3 side = new Vec3(-horizontal.z, 0.0D, horizontal.x);
        Set<BlockPos> ordered = new java.util.LinkedHashSet<>();
        int[] lateralOffsets = {0, 1, -1, 2, -2, 3, -3};
        for (int depth = 1; depth <= SOURCE_STAGING_MAX_DEPTH && ordered.size() < SOURCE_STAGING_MAX_COLUMNS; depth++) {
            for (int lateral : lateralOffsets) {
                if (Math.abs(lateral) > SOURCE_STAGING_MAX_LATERAL) continue;
                addAdmissionIntakeCandidate(ordered, routeTarget, horizontal, side, depth, lateral);
                if (ordered.size() >= SOURCE_STAGING_MAX_COLUMNS) break;
            }
        }
        return List.copyOf(ordered);
    }

    private static void addAdmissionIntakeCandidate(Set<BlockPos> ordered, BlockPos routeTarget, Vec3 horizontal,
                                                    Vec3 side, int depth, int lateral) {
        if (ordered == null || routeTarget == null || horizontal == null || side == null || depth < 1) return;
        Vec3 offset = horizontal.scale(depth).add(side.scale(lateral));
        BlockPos adjusted = BlockPos.containing(Vec3.atBottomCenterOf(routeTarget).add(offset));
        if (isAdmissionIntakeOnSourceSideVector(routeTarget, horizontal, adjusted)) {
            ordered.add(adjusted.immutable());
        }
    }

    private static AdmissionIntakeSurfaceResolution resolveAdmissionIntakeSurface(ServerLevel level, Mob worker,
                                                                                  BlockPos candidate, BlockPos intakeOrigin) {
        if (level == null || worker == null || candidate == null || intakeOrigin == null) {
            return new AdmissionIntakeSurfaceResolution(null, "invalid");
        }
        AdmissionIntakeSurfaceResolution lowerElevationFallback = null;
        for (int pass = 0; pass < 2; pass++) {
            int firstOffset = pass == 0 ? 0 : SOURCE_STAGING_MIN_Y_OFFSET;
            int lastOffset = pass == 0 ? SOURCE_STAGING_MAX_Y_OFFSET : -1;
            for (int offset = firstOffset; offset <= lastOffset; offset++) {
                BlockPos surface = candidate.atY(intakeOrigin.getY() + offset);
                if (!isLoadedCellColumn(level, surface)) continue;
                AdmissionIntakeSurfaceResolution resolution = classifyAdmissionIntakeSurface(level, worker, surface);
                if (resolution.surface() == null) continue;
                if (isIntakeWindowElevationCompatible(intakeOrigin.getY(), surface.getY())) return resolution;
                lowerElevationFallback = resolution;
            }
        }
        return lowerElevationFallback == null
                ? new AdmissionIntakeSurfaceResolution(null, "no_support") : lowerElevationFallback;
    }

    private static AdmissionIntakeSurfaceResolution classifyAdmissionIntakeSurface(ServerLevel level, Mob worker, BlockPos surface) {
        if (level == null || worker == null || surface == null) {
            return new AdmissionIntakeSurfaceResolution(null, "invalid");
        }
        if (!isLoadedCellColumn(level, surface)) {
            return new AdmissionIntakeSurfaceResolution(null, "unloaded");
        }
        BlockState feet = level.getBlockState(surface);
        BlockState head = level.getBlockState(surface.above());
        BlockState support = level.getBlockState(surface.below());
        if (feet.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
                || support.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
            return new AdmissionIntakeSurfaceResolution(null, "unsafe");
        }
        AABB body = translatedWorkerBody(worker, surface);
        if (body == null) {
            return new AdmissionIntakeSurfaceResolution(null, "clearance");
        }
        if ((isOpenTrapdoor(feet) || isOpenTrapdoor(support)) && !hasMobCompatibleStartSupport(level, worker, body)) {
            return new AdmissionIntakeSurfaceResolution(null, "unsafe");
        }
        CollisionContext context = CollisionContext.of(worker);
        if (!feet.getCollisionShape(level, surface, context).isEmpty()
                || !head.getCollisionShape(level, surface.above(), context).isEmpty()) {
            return new AdmissionIntakeSurfaceResolution(null, "clearance");
        }
        if (!hasMobCompatibleStartSupport(level, worker, body)) {
            return new AdmissionIntakeSurfaceResolution(null, "no_support");
        }
        if (findPositiveVolumeBlockage(level, body, null, 0.0D) != null) {
            return new AdmissionIntakeSurfaceResolution(null, "clearance");
        }
        return new AdmissionIntakeSurfaceResolution(surface.immutable(), null);
    }

    private static AABB translatedWorkerBody(Mob worker, BlockPos surface) {
        if (worker == null || surface == null) return null;
        double halfWidth = Math.max(0.05D, worker.getBbWidth() * 0.5D);
        double minX = surface.getX() + 0.5D - halfWidth;
        double minZ = surface.getZ() + 0.5D - halfWidth;
        return new AABB(minX, surface.getY(), minZ,
                surface.getX() + 0.5D + halfWidth, surface.getY() + worker.getBbHeight(), surface.getZ() + 0.5D + halfWidth);
    }

    private static boolean isAdmissionIntakeOnSourceSide(BlockPos intakeOrigin, Vec3 towardWorker, BlockPos surface) {
        if (intakeOrigin == null || towardWorker == null || surface == null || towardWorker.horizontalDistanceSqr() < 1.0E-6D) {
            return false;
        }
        Vec3 horizontal = new Vec3(towardWorker.x, 0.0D, towardWorker.z).normalize();
        return isAdmissionIntakeOnSourceSideVector(intakeOrigin, horizontal, surface);
    }

    private static boolean isAdmissionIntakeOnSourceSideVector(BlockPos intakeOrigin, Vec3 horizontal, BlockPos surface) {
        if (intakeOrigin == null || horizontal == null || surface == null || horizontal.horizontalDistanceSqr() < 1.0E-6D) {
            return false;
        }
        Vec3 delta = Vec3.atBottomCenterOf(surface).subtract(Vec3.atBottomCenterOf(intakeOrigin));
        return delta.dot(horizontal) >= -0.15D;
    }

    static String localRouteBudgetFailureForTest(int reservations, int samples, int localSuccesses, int routeSize) {
        return localRouteBudgetFailure(reservations, samples, localSuccesses, routeSize);
    }

    private static String localRouteBudgetFailure(int reservations, int samples, int localSuccesses, int routeSize) {
        if (localSuccesses >= routeSize && routeSize > 0) return null;
        if (samples <= 0 || reservations >= FORMATION_ROUTE_PROBE_LIMIT || samples >= TARGET_ROUTE_SAMPLE_LIMIT) {
            return "probe_budget_exhausted_defer";
        }
        return "route_incomplete";
    }

    private static void debugAdmissionIntakeCandidate(Mob worker, BlockPos intakeOrigin, BlockPos candidate,
                                                      BlockPos surface, String reason) {
        if (worker == null || intakeOrigin == null || candidate == null
                || SoundAttractConfig.COMMON == null || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ADMISSION)) {
            return;
        }
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ADMISSION)) {
        SoundAttractMod.LOGGER.debug("[LivingWallCoordinator] admission_intake_candidate worker={} origin={} candidate={} surface={} reason={}",
                worker.getUUID(), intakeOrigin, candidate, surface, reason);
        }
    }

    private static List<BlockPos> stableRouteNodes(ServerLevel level, Mob worker, IntakeWindow intakeWindow, BlockPos routeTarget,
                                                   BlockPos intakeOrigin, Path path, CanonicalFeetCorridor corridor) {
        if (level == null || worker == null || intakeWindow == null || intakeWindow.feederApproach() == null
                || routeTarget == null || intakeOrigin == null || path == null) {
            return List.of();
        }
        BlockPos workerSurface = resolveWorkerRouteStartState(level, worker).surface();
        if (workerSurface == null) return List.of();
        Vec3 towardWorker = Vec3.atBottomCenterOf(workerSurface).subtract(Vec3.atBottomCenterOf(intakeOrigin));
        List<BlockPos> route = actualPathNodes(path);
        if (route.isEmpty()) return List.of();
        String routeFailure = validateSourceApproachRoute(intakeOrigin, towardWorker, corridor, route);
        if (routeFailure != null) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ROUTING)) {
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ADMISSION)) {
                SoundAttractMod.LOGGER.info("[LivingWallCoordinator] SOURCE_APPROACH route_rejected worker={} target={} intake={} reason={} routeNodes={}",
                        worker.getUUID(), routeTarget, intakeWindow.feederApproach(), routeFailure, route);
                }
            }
            return List.of();
        }
        if (!route.get(route.size() - 1).closerThan(intakeWindow.feederApproach(), 2.0D)) {
            return List.of();
        }
        return route.size() > TARGET_ROUTE_NODE_LIMIT ? List.copyOf(route.subList(0, TARGET_ROUTE_NODE_LIMIT)) : route;
    }

    private static List<BlockPos> actualPathNodes(Path path) {
        if (path == null) return List.of();
        int count = Math.min(TARGET_ROUTE_NODE_LIMIT, Math.max(0, path.getNodeCount()));
        if (count <= 0) return List.of();
        List<BlockPos> nodes = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Node node = path.getNode(index);
            if (node == null) continue;
            nodes.add(node.asBlockPos().immutable());
        }
        return List.copyOf(nodes);
    }

    private static boolean isRouteNodeOnSourceSide(BlockPos intakeOrigin, Vec3 towardWorker, BlockPos node) {
        if (intakeOrigin == null || towardWorker == null || node == null || towardWorker.horizontalDistanceSqr() < 1.0E-6D) {
            return false;
        }
        Vec3 horizontal = new Vec3(towardWorker.x, 0.0D, towardWorker.z).normalize();
        Vec3 delta = Vec3.atBottomCenterOf(node).subtract(Vec3.atBottomCenterOf(intakeOrigin));
        return delta.dot(horizontal) >= -0.5D;
    }

    private static String validateSourceApproachRoute(BlockPos intakeOrigin, Vec3 towardWorker, CanonicalFeetCorridor corridor,
                                                      List<BlockPos> routeNodes) {
        if (intakeOrigin == null || towardWorker == null || routeNodes == null || routeNodes.isEmpty()) {
            return "source_approach_invalid";
        }
        Set<BlockPos> blockedCells = sourceApproachRejectedCells(corridor);
        for (BlockPos node : routeNodes) {
            if (node == null) continue;
            if (!isRouteNodeOnSourceSide(intakeOrigin, towardWorker, node)) {
                return "source_approach_wrong_side";
            }
            if (blockedCells.contains(node)) {
                return "source_approach_bridge_crossing";
            }
        }
        return null;
    }

    private static Set<BlockPos> sourceApproachRejectedCells(CanonicalFeetCorridor corridor) {
        if (corridor == null) return Set.of();
        Set<BlockPos> blocked = new HashSet<>();
        if (corridor.obstacleContactCell() != null) blocked.add(corridor.obstacleContactCell());
        if (corridor.landingFeet() != null) blocked.add(corridor.landingFeet());
        if (corridor.egressFeet() != null) blocked.add(corridor.egressFeet());
        blocked.addAll(corridor.hazardCells());
        int sourceEnd = corridor.phaseEndIndex(CanonicalFeetCorridor.MovementPhase.SOURCE);
        List<BlockPos> centerline = corridor.centerlineCells();
        if (centerline != null) {
            for (int i = sourceEnd + 1; i < centerline.size(); i++) {
                blocked.add(centerline.get(i));
            }
        }
        if (corridor.takeoffFeet() != null) {
            blocked.remove(corridor.takeoffFeet());
        }
        return blocked;
    }

    private static boolean deferredRouteFailure(String reason) {
        return reason != null && reason.endsWith("_defer");
    }

    private static String preferDeferredReason(String current, String candidate) {
        if (candidate == null || !deferredRouteFailure(candidate)) return current;
        if (current == null) return candidate;
        if ("worker_start_unloaded_defer".equals(candidate) || "worker_airborne_defer".equals(candidate)) return candidate;
        return current;
    }

    private static Vec3 stableClusterCenter(List<Mob> cluster) {
        if (cluster == null || cluster.isEmpty()) return Vec3.ZERO;
        List<Mob> ordered = cluster.stream().filter(Objects::nonNull)
                .sorted(Comparator.comparing(mob -> mob.getUUID().toString()))
                .toList();
        Vec3 sum = Vec3.ZERO;
        for (Mob mob : ordered) {
            sum = sum.add(mob.position());
        }
        return sum.scale(1.0D / ordered.size());
    }

    private static BlockPos resolveStableSourceOrigin(ServerLevel level, List<Mob> cluster, Vec3 stableCenter,
                                                       BlockPos threat) {
        if (level == null || cluster == null || cluster.isEmpty() || stableCenter == null || threat == null) return null;
        Vec3 direction = Vec3.atBottomCenterOf(threat).subtract(stableCenter);
        direction = new Vec3(direction.x, 0.0D, direction.z);
        if (!Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 1.0E-6D) return null;
        Vec3 forward = direction.normalize();
        BlockPos best = null;
        UUID bestWorker = null;
        double bestProjection = Double.NEGATIVE_INFINITY;
        for (Mob worker : cluster) {
            BlockPos surface = resolveWorkerStartSurface(level, worker);
            if (surface == null) continue;
            double projection = Vec3.atBottomCenterOf(surface).subtract(stableCenter).dot(forward);
            int coordinateOrder = best == null ? -1 : compareBlockCoordinates(surface, best);
            if (best == null || projection > bestProjection
                    || Double.compare(projection, bestProjection) == 0 && (coordinateOrder < 0
                    || coordinateOrder == 0 && worker.getUUID().compareTo(bestWorker) < 0)) {
                best = surface.immutable();
                bestWorker = worker.getUUID();
                bestProjection = projection;
            }
        }
        return best;
    }

    private static int compareBlockCoordinates(BlockPos first, BlockPos second) {
        int x = Integer.compare(first.getX(), second.getX());
        if (x != 0) return x;
        int y = Integer.compare(first.getY(), second.getY());
        return y != 0 ? y : Integer.compare(first.getZ(), second.getZ());
    }

    public static void recordCombatThreat(ServerLevel level, LivingEntity target) {
        if (level == null || target == null || !target.isAlive() || target.isRemoved()) return;
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) return;
        if (level.getServer() != null && !level.getServer().isSameThread()) {
            level.getServer().execute(() -> recordCombatThreat(level, target));
            return;
        }
        PerLevelState state = state(level);
        if (state == null) return;
        long now = level.getGameTime();
        RememberedThreat existing = state.rememberedThreats.get(target.getUUID());
        if (existing != null && now - existing.lastSeenTick() < 10L && target.blockPosition().equals(existing.lastKnownPos())) {
            return;
        }
        if (state.rememberedThreats.size() >= 32 && !state.rememberedThreats.containsKey(target.getUUID())) {
            state.rememberedThreats.entrySet().removeIf(entry -> entry.getValue() == null || !entry.getValue().isValid(now, REMEMBERED_THREAT_TTL_TICKS));
            if (state.rememberedThreats.size() >= 32) return;
        }
        state.rememberedThreats.put(target.getUUID(), new RememberedThreat(target.getUUID(), target.blockPosition(), now));
    }

    private static BlockPos resolveClusterRememberedThreat(ServerLevel level, List<Mob> cluster) {
        if (level == null || cluster == null || cluster.isEmpty()) return null;
        PerLevelState state = state(level);
        if (state == null || state.rememberedThreats.isEmpty()) return null;
        long now = level.getGameTime();
        Vec3 center = cluster.stream().filter(m -> m != null && m.isAlive() && !m.isRemoved()).map(Mob::position).reduce(Vec3.ZERO, Vec3::add).scale(1.0D / Math.max(1, cluster.size()));
        double maxDist = Math.max(64.0D, LivingWallConfig.MAX_GAP.get() + 48.0D);
        double maxDistSq = maxDist * maxDist;

        RememberedThreat best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Iterator<Map.Entry<UUID, RememberedThreat>> it = state.rememberedThreats.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, RememberedThreat> entry = it.next();
            RememberedThreat threat = entry.getValue();
            if (threat == null || !threat.isValid(now, REMEMBERED_THREAT_TTL_TICKS)) {
                it.remove();
                continue;
            }
            Entity entity = null;
            if (level.getServer() != null) {
                entity = level.getServer().getPlayerList().getPlayer(threat.targetUuid());
            }
            if (entity == null) {
                entity = level.getEntity(threat.targetUuid());
            }
            if (entity != null && (!entity.isAlive() || entity.isRemoved())) {
                it.remove();
                continue;
            }
            BlockPos pos;
            if (entity instanceof LivingEntity living && living.isAlive() && living.level() == level) {
                if (living instanceof Player player && (player.isCreative() || player.isSpectator())) {
                    it.remove();
                    continue;
                }
                pos = living.blockPosition();
            } else {
                pos = threat.lastKnownPos();
            }
            double distSq = pos.distToCenterSqr(center.x, center.y, center.z);
            if (distSq <= maxDistSq && distSq < bestDistSq) {
                bestDistSq = distSq;
                best = pos.equals(threat.lastKnownPos()) ? threat : new RememberedThreat(threat.targetUuid(), pos, now);
            }
        }
        if (best != null) {
            state.rememberedThreats.put(best.targetUuid(), best);
            return best.lastKnownPos();
        }
        return null;
    }

    private static BlockPos resolveEffectiveThreat(ServerLevel level, List<Mob> cluster, BlockPos preferredThreat) {
        if (cluster != null && !cluster.isEmpty()) {
            for (Mob mob : cluster) {
                if (mob == null || !mob.isAlive() || mob.isRemoved()) continue;
                LivingEntity target = mob.getTarget();
                if (target != null && target.isAlive() && !target.isRemoved() && mob.canAttack(target)) {
                    if (level != null) recordCombatThreat(level, target);
                    return target.blockPosition();
                }
            }
        }
        if (level != null && cluster != null && !cluster.isEmpty()) {
            BlockPos remembered = resolveClusterRememberedThreat(level, cluster);
            if (remembered != null) {
                return remembered;
            }
        }
        if (preferredThreat != null) {
            return preferredThreat;
        }
        if (level != null && cluster != null && !cluster.isEmpty()) {
            Vec3 center = cluster.stream().filter(m -> m != null && m.isAlive() && !m.isRemoved()).map(Mob::position).reduce(Vec3.ZERO, Vec3::add).scale(1.0D / Math.max(1, cluster.size()));
            Player player = level.getNearestPlayer(center.x, center.y, center.z, Math.max(48.0D, LivingWallConfig.MAX_GAP.get() + 32.0D),
                    candidate -> candidate instanceof Player p && p.isAlive() && !p.isCreative() && !p.isSpectator());
            if (player != null) {
                recordCombatThreat(level, player);
                return player.blockPosition();
            }
        }
        if (cluster != null && !cluster.isEmpty()) {
            for (Mob mob : cluster) {
                if (mob == null || !mob.isAlive() || mob.isRemoved()) continue;
                BlockPos destination = groupDestination(mob);
                if (destination != null && isSoundBridgeRequired(level, cluster, destination)) {
                    return destination;
                }
            }
        }
        return null;
    }

    private static boolean isSoundBridgeRequired(ServerLevel level, List<Mob> cluster, BlockPos soundPos) {
        if (level == null || cluster == null || cluster.isEmpty() || soundPos == null) return false;
        Vec3 center = cluster.stream().filter(m -> m != null && m.isAlive() && !m.isRemoved()).map(Mob::position).reduce(Vec3.ZERO, Vec3::add).scale(1.0D / Math.max(1, cluster.size()));
        int centerY = Mth.floor(center.y);
        int deltaY = soundPos.getY() - centerY;
        if (Math.abs(deltaY) > 1) {
            return true;
        }
        double distSq = soundPos.distToCenterSqr(center.x, center.y, center.z);
        if (distSq < 16.0D) {
            return false;
        }
        int samples = 3;
        for (int i = 1; i <= samples; i++) {
            double fraction = i / (double) (samples + 1);
            double sx = center.x + (soundPos.getX() + 0.5D - center.x) * fraction;
            double sz = center.z + (soundPos.getZ() + 0.5D - center.z) * fraction;
            BlockPos samplePos = new BlockPos(Mth.floor(sx), centerY, Mth.floor(sz));
            if (!level.isLoaded(samplePos) || !level.isLoaded(samplePos.below())) return true;
            BlockState feet = level.getBlockState(samplePos);
            BlockState head = level.getBlockState(samplePos.above());
            BlockState below = level.getBlockState(samplePos.below());
            boolean openWalk = feet.getCollisionShape(level, samplePos).isEmpty()
                    && head.getCollisionShape(level, samplePos.above()).isEmpty()
                    && feet.getFluidState().isEmpty()
                    && head.getFluidState().isEmpty()
                    && hasStableSupport(level, samplePos.below(), below);
            if (!openWalk) {
                return true;
            }
        }
        return false;
    }

    static boolean isTargetVerticalCompatible(Vec3 center, BlockPos threat, BlockPos candidate) {
        if (center == null || candidate == null || threat == null) return true;
        int centerY = Mth.floor(center.y);
        if (threat.getY() < centerY) return candidate.getY() <= centerY;
        if (threat.getY() > centerY) return candidate.getY() > centerY;
        return true;
    }

    private static double formationProbeRadius(List<Mob> cluster) {
        double probeRadius = 0.31D;
        if (cluster == null) return probeRadius;
        for (Mob mob : cluster) {
            if (mob == null) continue;
            AABB box = mob.getBoundingBox();
            double radius = Math.max(box.getXsize(), box.getZsize()) * 0.5D;
            probeRadius = Math.max(probeRadius, radius);
        }
        return Math.min(0.75D, probeRadius);
    }

    private static double formationProbeRadius(Mob mob) {
        if (mob == null) return 0.31D;
        AABB box = mob.getBoundingBox();
        double radius = Math.max(box.getXsize(), box.getZsize()) * 0.5D;
        return Math.min(0.75D, Math.max(0.31D, radius));
    }

    private static int wallLedgeEvidence(ServerLevel level, BlockPos candidate) {
        int evidence = 0;
        if (!level.getBlockState(candidate.below()).getCollisionShape(level, candidate.below()).isEmpty()) evidence++;
        for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            BlockPos neighbor = candidate.offset(offset[0], 0, offset[1]);
            if (!level.getBlockState(neighbor).getCollisionShape(level, neighbor).isEmpty()) evidence += 2;
            if (!level.getBlockState(neighbor.above()).getCollisionShape(level, neighbor.above()).isEmpty()) evidence++;
        }
        return evidence;
    }

    private static boolean hasNavigableRoute(ServerLevel level, List<Mob> cluster, BlockPos target) {
        return hasNavigableRoute(level, cluster, target, null, null);
    }

    private static boolean hasNavigableRoute(ServerLevel level, List<Mob> cluster, BlockPos target,
                                              Formation formation, RouteProbeOwner owner) {
        if (target == null) return false;
        if (formation != null && !canonicalCorridorGate(formation, target)) return false;
        if (formation != null && owner != null && !reserveRouteProbe(formation, owner, target)) return false;
        Mob probe = cluster.stream()
                .filter(mob -> mob != null && mob.isAlive() && !mob.isRemoved())
                .min(Comparator.comparingDouble(mob -> mob.distanceToSqr(Vec3.atBottomCenterOf(target))))
                .orElse(null);
        if (probe == null) return false;
        var path = probe.getNavigation().createPath(target, 0);
        boolean reachable = path != null && path.canReach() && path.getTarget() != null
                && path.getTarget().distSqr(target) <= 1.0D;
        recordRouteProbeOutcome(formation, owner, target, true, reachable);
        return reachable;
    }

    static boolean reserveRouteProbeForTest(Formation formation, RouteProbeOwner owner, BlockPos target) {
        return reserveRouteProbe(formation, owner, target);
    }

    private static boolean reserveRouteProbe(Formation formation, RouteProbeOwner owner, BlockPos target) {
        if (formation == null || owner == null || target == null) return false;
        RouteProbeReservation existing = formation.routeProbeLedger.get(owner);
        if (existing != null) return existing.target().equals(target);
        if (formation.routeProbeReservations >= FORMATION_ROUTE_PROBE_LIMIT) return false;
        formation.routeProbeLedger.put(owner, new RouteProbeReservation(owner, target, false, false));
        formation.routeProbeReservations++;
        if (formation.intentToken != null) {
            formation.intentToken.routeProbeLedger.put(owner, formation.routeProbeLedger.get(owner));
            formation.intentToken.routeProbeReservations = formation.routeProbeReservations;
        }
        return true;
    }

    private static void recordRouteProbeOutcome(Formation formation, RouteProbeOwner owner, BlockPos target,
                                                boolean attempted, boolean successful) {
        if (formation == null || owner == null || target == null) return;
        RouteProbeReservation updated = new RouteProbeReservation(owner, target, attempted, successful);
        formation.routeProbeLedger.put(owner, updated);
        if (successful) formation.pathSuccessCount++;
        if (formation.intentToken != null) {
            formation.intentToken.routeProbeLedger.put(owner, updated);
            formation.intentToken.routeProbeReservations = formation.routeProbeReservations;
            formation.intentToken.pathSuccessCount = formation.pathSuccessCount;
        }
    }

    private static void syncRouteProbeLedger(Formation formation, ConstructionIntent intent) {
        if (formation == null || intent == null) return;
        formation.routeProbeLedger.clear();
        formation.routeProbeLedger.putAll(intent.routeProbeLedger);
        formation.routeProbeReservations = intent.routeProbeReservations;
        formation.pathSuccessCount = intent.pathSuccessCount;
        if (intent.traversalWitness != null) {
            formation.traversalWitness = intent.traversalWitness.copy();
        }
    }

    private static List<Vec3> expandedTargetScanDirections(Vec3 center, BlockPos threat) {
        List<Vec3> directions = new ArrayList<>(targetScanDirections(center, threat));
        double diagonal = Math.sqrt(0.5D);
        directions.add(new Vec3(diagonal, 0.0D, diagonal));
        directions.add(new Vec3(diagonal, 0.0D, -diagonal));
        directions.add(new Vec3(-diagonal, 0.0D, diagonal));
        directions.add(new Vec3(-diagonal, 0.0D, -diagonal));
        return directions;
    }

    private static TargetScanKey targetScanKey(ColdStartSeedProvider provider, long generation) {
        BlockPos seed = provider == null ? null : provider.sourceBucket();
        BlockPos threat = provider == null ? null : provider.threatBucket();
        int sourceX = seed == null ? Integer.MIN_VALUE : Math.floorDiv(seed.getX(), 4);
        int sourceY = seed == null ? Integer.MIN_VALUE : Math.floorDiv(seed.getY(), 4);
        int sourceZ = seed == null ? Integer.MIN_VALUE : Math.floorDiv(seed.getZ(), 4);
        int targetX = threat == null ? Integer.MIN_VALUE : Math.floorDiv(threat.getX(), 4);
        int targetY = threat == null ? Integer.MIN_VALUE : Math.floorDiv(threat.getY(), 4);
        int targetZ = threat == null ? Integer.MIN_VALUE : Math.floorDiv(threat.getZ(), 4);
        return new TargetScanKey(sourceX, sourceY, sourceZ, targetX, targetY, targetZ, generation);
    }

    @Deprecated(forRemoval = false)
    private static TargetScanKey targetScanKey(Vec3 center, BlockPos threat, long generation) {
        return targetScanKey(new ColdStartSeedProvider(center == null ? null : BlockPos.containing(center), threat),
                generation);
    }

    static List<Vec3> targetScanDirections(Vec3 center, BlockPos threat) {
        List<Vec3> directions = new ArrayList<>();
        if (threat == null) {
            directions.add(new Vec3(1.0D, 0.0D, 0.0D));
            directions.add(new Vec3(-1.0D, 0.0D, 0.0D));
            directions.add(new Vec3(0.0D, 0.0D, 1.0D));
            directions.add(new Vec3(0.0D, 0.0D, -1.0D));
            return directions;
        }
        Vec3 towardThreat = Vec3.atBottomCenterOf(threat).subtract(center);
        double horizontal = Math.sqrt(towardThreat.x * towardThreat.x + towardThreat.z * towardThreat.z);
        if (horizontal < 0.001D) return targetScanDirections(center, null);
        double base = Math.atan2(towardThreat.z, towardThreat.x);
        double[] offsets = {0.0D, Math.toRadians(-20.0D), Math.toRadians(20.0D), Math.toRadians(-40.0D), Math.toRadians(40.0D)};
        for (double offset : offsets) {
            double angle = base + offset;
            directions.add(new Vec3(Math.cos(angle), 0.0D, Math.sin(angle)));
        }
        return directions;
    }

    private static BlockPos findDangerTarget(ServerLevel level, BlockPos pivot, Vec3 center) {
        int radius = LivingWallConfig.MAX_GAP.get();
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int probes = 0;
        int lowerCandidates = 0;
        int rejectedLanding = 0;
        int rejectedRoute = 0;
        for (int[] direction : directions) {
            for (int step = 2; step <= radius; step++) {
                int x = pivot.getX() + direction[0] * step;
                int z = pivot.getZ() + direction[1] * step;
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                probes++;
                BlockPos candidate = y < pivot.getY()
                        ? new BlockPos(x, y, z)
                        : findLowerSafeLanding(level, pivot, x, z, y);
                if (candidate == null || candidate.getY() >= pivot.getY()) {
                    rejectedLanding++;
                    continue;
                }
                lowerCandidates++;
                if (!hasUnsafeRoute(level, pivot, candidate)) {
                    rejectedRoute++;
                    continue;
                }
                double distance = center.distanceToSqr(Vec3.atBottomCenterOf(candidate));
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
        }
        PerLevelState scanState = state(level);
        long now = level.getGameTime();
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ADMISSION)
                && now >= scanState.nextDangerLogTick) {
            scanState.nextDangerLogTick = now + TARGET_SCAN_CACHE_TICKS;
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Danger target scan pivot={} probes={} lowerCandidates={} rejectedLanding={} rejectedRoute={} selected={}",
                    pivot, probes, lowerCandidates, rejectedLanding, rejectedRoute, best);
        }
        return best;
    }

    private static BlockPos findLowerSafeLanding(ServerLevel level, BlockPos pivot, int x, int z, int surfaceY) {
        int minY = Math.max(level.getMinY() + 1,
                pivot.getY() - LivingWallConfig.MAX_HEIGHT.get() - 2);
        int startY = Math.min(pivot.getY() - 1, surfaceY);
        for (int y = startY; y >= minY; y--) {
            BlockPos candidate = new BlockPos(x, y, z);
            if (isSafeLanding(level, candidate)) return candidate;
        }
        return null;
    }

    private static BlockPos nearestSafeSurface(ServerLevel level, BlockPos hit, Vec3 center,
                                               double pitch, int maxHeight) {
        int minY = Mth.floor(center.y) - maxHeight - 2;
        int maxY = Mth.floor(center.y) + maxHeight + 2;
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int preferredY = hit.getY() + 1;
        for (int y = minY; y <= maxY; y++) {
            BlockPos candidate = new BlockPos(hit.getX(), y, hit.getZ());
            if (!isSafeLanding(level, candidate)) continue;
            double score = Math.abs(y - preferredY) * 2.0D + Math.abs(y - center.y) * 0.05D;
            if (pitch < -0.15D && y > Mth.floor(center.y) + 1) score += 100.0D;
            if (pitch > 0.15D && y < Mth.floor(center.y) - 1) score += 100.0D;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static BlockPos resolveStructuralLanding(ServerLevel level, BlockPos structural,
                                                     BlockPos pivot, Vec3 rayDirection) {
        return resolveCanonicalLanding(level, structural, pivot, rayDirection);
    }

    private static TargetLandingSelection selectTargetLanding(ServerLevel level, BlockPos pivot,
                                                              BlockPos threat, WallProfileScanner.RayEvidence ray,
                                                              int rayOrdinal, BlockPos structuralCell,
                                                              int structuralIndex, BodyGeometry geometry,
                                                              Vec3 center, LandingRejectionCounts counts) {
        if (level == null || pivot == null || threat == null || ray == null || structuralCell == null
                || geometry == null || center == null) return null;
        LinkedHashSet<BlockPos> candidates = new LinkedHashSet<>();
        BlockPos canonical = resolveStructuralLanding(level, structuralCell, pivot, ray.direction());
        if (canonical != null) candidates.add(canonical.immutable());
        int horizontalDistance = Math.min(MAX_EXTERIOR_LANDING_DISTANCE,
                Math.max(1, LivingWallConfig.MAX_GAP.get() + 1));
        int verticalDistance = Math.min(MAX_EXTERIOR_LANDING_HEIGHT,
                Math.max(0, LivingWallConfig.MAX_HEIGHT.get()));
        candidates.addAll(WallProfileScanner.exteriorLandingCandidates(structuralCell, pivot, ray.direction(),
                horizontalDistance, verticalDistance));
        TargetLandingSelection fallbackDrop = null;
        int inspected = 0;
        for (BlockPos candidate : candidates) {
            if (candidate == null) { counts.nullCandidate++; continue; }
            inspected++;
            if (inspected > MAX_EXTERIOR_LANDING_CANDIDATES) {
                counts.candidateBudget++;
                break;
            }
            counts.inspectedCandidates++;
            boolean landingSurface = isSafeLanding(level, candidate) || isInvasionEndpoint(level, pivot, candidate);
            if (!landingSurface) { counts.unsafeSurface++; continue; }
            if (!hasBridgeNeed(level, pivot, candidate)) { counts.tooShort++; continue; }
            if (bridgeLength(pivot, candidate) < 1.5D) { counts.tooShort++; continue; }
            if (horizontalDistance(pivot, candidate) > LivingWallConfig.MAX_GAP.get() + 1.0D) { counts.tooFar++; continue; }
            if (!isTargetVerticalCompatible(center, threat, candidate)) {
                if (canonical == null || candidate.getY() < canonical.getY()) {
                    counts.verticalMismatch++;
                    continue;
                }
            }
            CanonicalCorridorDecision decision = evaluateCanonicalCorridorAtCandidate(level, pivot, threat, ray,
                    rayOrdinal, structuralCell, structuralIndex, candidate);
            if (!decision.accepted()) { counts.corridorRejected++; continue; }
            ConstructionScenario scenario = parseConstructionScenario(decision.evidence().scenario());
            if (scenario == null || scenario == ConstructionScenario.NONE) { counts.scenarioRejected++; continue; }
            List<BlockPos> hazards = carriedRayHazards(ray, pivot, candidate);
            if (scenario == ConstructionScenario.HAZARD_AND_WALL && hazards.isEmpty()) { counts.hazardRejected++; continue; }
            if (!hasWallProgress(pivot, candidate, decision.evidence().wallFace())) { counts.spanRejected++; continue; }
            if (!hasThreatProgress(pivot, candidate, threat)) { counts.threatRejected++; continue; }
            CanonicalFeetCorridor executable = executableCorridor(level, decision.corridor(), geometry);
            if (executable == null) { counts.executableRejected++; continue; }
            if (!orderedCorridorTransitionValid(executable, decision.evidence(), threat, scenario)) { counts.transitionRejected++; continue; }
            if (!hasWallProgress(pivot, executable.egressFeet(), decision.evidence().wallFace())) { counts.egressSpanRejected++; continue; }
            if (!hasThreatProgress(pivot, executable.egressFeet(), threat)) { counts.egressThreatRejected++; continue; }
            if (scenario == ConstructionScenario.WALL_LEDGE || scenario == ConstructionScenario.JUMP_BRIDGE) {
                return new TargetLandingSelection(candidate, new CanonicalCorridorDecision(decision.evidence(), executable, null));
            }
            if (fallbackDrop == null || candidate.distSqr(threat) < fallbackDrop.landing().distSqr(threat)) {
                fallbackDrop = new TargetLandingSelection(candidate, new CanonicalCorridorDecision(decision.evidence(), executable, null));
            }
        }
        return fallbackDrop;
    }

    private static final class LandingRejectionCounts {
        int nullCandidate;
        int candidateBudget;
        int unsafeSurface;
        int tooShort;
        int tooFar;
        int verticalMismatch;
        int corridorRejected;
        int scenarioRejected;
        int hazardRejected;
        int spanRejected;
        int threatRejected;
        int executableRejected;
        int transitionRejected;
        int egressSpanRejected;
        int egressThreatRejected;
        int inspectedCandidates;

        @Override
        public String toString() {
            return "null=" + nullCandidate + ",budget=" + candidateBudget + ",unsafe=" + unsafeSurface
                    + ",short=" + tooShort + ",far=" + tooFar + ",vertical=" + verticalMismatch
                    + ",corridor=" + corridorRejected + ",scenario=" + scenarioRejected + ",hazard=" + hazardRejected
                    + ",span=" + spanRejected + ",threat=" + threatRejected + ",executable=" + executableRejected
                    + ",transition=" + transitionRejected + ",egressSpan=" + egressSpanRejected
                    + ",egressThreat=" + egressThreatRejected;
        }
    }

    static int rayComponentSign(double component) {
        return component > 1.0E-6D ? 1 : component < -1.0E-6D ? -1 : 0;
    }

    private static BlockPos resolveCanonicalLanding(ServerLevel level, BlockPos structural,
                                                     BlockPos pivot, Vec3 rayDirection) {
        if (level == null || structural == null || pivot == null) return null;
        int dx = Integer.compare(structural.getX(), pivot.getX());
        int dz = Integer.compare(structural.getZ(), pivot.getZ());
        int maxHeight = LivingWallConfig.MAX_HEIGHT.get();
        int crestY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, structural.getX(), structural.getZ());
        if (crestY > structural.getY() && crestY <= structural.getY() + maxHeight + 2) {
            BlockPos crest = new BlockPos(structural.getX(), crestY, structural.getZ());
            if (isSafeLanding(level, crest) && hasLandingAndEgress(level, crest, pivot, crest)
                    && !isSheerWallFace(level, crest, dx, dz)) {
                return crest.immutable();
            }
            if (dx != 0 || dz != 0) {
                BlockPos forwardCrest = crest.offset(dx, 0, dz);
                if (isSafeLanding(level, forwardCrest) && hasLandingAndEgress(level, forwardCrest, pivot, forwardCrest)
                        && !isSheerWallFace(level, forwardCrest, dx, dz)) {
                    return forwardCrest.immutable();
                }
            }
        }
        for (int up = maxHeight; up >= 1; up--) {
            BlockPos above = structural.above(up);
            if (!level.isLoaded(above) || !level.isLoaded(above.above()) || !level.isLoaded(above.below())) continue;
            BlockState aboveState = level.getBlockState(above);
            if (aboveState.getCollisionShape(level, above).isEmpty()
                    && level.getBlockState(above.above()).getCollisionShape(level, above.above()).isEmpty()
                    && hasStableSupport(level, above.below(), level.getBlockState(above.below()))) {
                if (isSafeLanding(level, above) && hasLandingAndEgress(level, above, pivot, above)
                        && !isSheerWallFace(level, above, dx, dz)) {
                    return above.immutable();
                }
                if (dx != 0 || dz != 0) {
                    BlockPos forward = above.offset(dx, 0, dz);
                    if (isSafeLanding(level, forward) && hasLandingAndEgress(level, forward, pivot, forward)
                            && !isSheerWallFace(level, forward, dx, dz)) {
                        return forward.immutable();
                    }
                }
            }
        }
        List<BlockPos> candidates = new ArrayList<>();
        if (dx != 0 || dz != 0) {
            candidates.add(structural.offset(dx, 0, dz));
            candidates.add(structural.offset(dx, 1, dz));
            candidates.add(structural.offset(2 * dx, 0, 2 * dz));
            candidates.add(structural.offset(2 * dx, 1, 2 * dz));
        }
        if (rayDirection != null) {
            int rx = rayComponentSign(rayDirection.x);
            int rz = rayComponentSign(rayDirection.z);
            if (rx != 0 || rz != 0) candidates.add(structural.offset(rx, 0, rz));
        }
        int maxRad = Math.min(4, Math.max(1, LivingWallConfig.MAX_GAP.get()));
        for (int radius = 1; radius <= maxRad; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                    for (int yOff = 0; yOff <= maxHeight; yOff++) {
                        candidates.add(structural.offset(x, yOff, z));
                    }
                }
            }
        }
        return candidates.stream().filter(candidate -> !isSheerWallFace(level, candidate, dx, dz)
                        && (isSafeLanding(level, candidate) || isInvasionEndpoint(level, pivot, candidate))
                        && hasLandingAndEgress(level, candidate, pivot, candidate))
                .min(Comparator.comparingDouble(candidate -> {
                    double distSqr = candidate.distSqr(structural);
                    if (candidate.getY() > structural.getY()) {
                        distSqr *= 0.5D;
                    }
                    return distSqr;
                }))
                .map(BlockPos::immutable).orElse(null);
    }

    private static boolean isOpenTrapdoor(BlockState state) {
        return state.getBlock() instanceof TrapDoorBlock
                && state.hasProperty(TrapDoorBlock.OPEN)
                && state.getValue(TrapDoorBlock.OPEN);
    }

    private static void admissionLog(ServerLevel level, PerLevelState state, long now, String reason) {
        if (!DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ADMISSION)) return;
        boolean important = reason.startsWith("formation admitted") || reason.startsWith("build rejected: footprint_conflict");
        if (!important && now < state.nextAdmissionLogTick) return;
        if (!important) state.nextAdmissionLogTick = now + RECRUITMENT_TIMEOUT_TICKS;
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Non-raid admission: {}", reason);
    }

    private static void recordBuildGate(PerLevelState state, String reason) {
        if (state == null || SoundAttractConfig.COMMON == null || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ADMISSION)) return;
        state.buildGateAttempts++;
        state.buildGateRejections.merge(reason, 1, Integer::sum);
    }

    private static void logPlanSearchDiagnostic(ServerLevel level, BlockPos seed, BlockPos anchor,
                                                BlockPos target, int columns, int hazards,
                                                Map<String, Integer> rejections) {
        if (level == null || SoundAttractConfig.COMMON == null || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ADMISSION)) return;
        PerLevelState state = state(level);
        long now = level.getGameTime();
        if (now < state.nextPlanDiagnosticLogTick) return;
        state.nextPlanDiagnosticLogTick = now + 20L;
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Plan search rejected seed={} anchor={} target={} columns={} hazards={} reasons={}",
                seed, anchor, target, columns, hazards, rejections);
    }

    private static void logRejectedLivingBridgeCandidate(ServerLevel level, PerLevelState state, long now,
                                                         long scanGeneration, WallProfileScanner.RayEvidence ray,
                                                         BlockPos evidenceCell, BlockPos threat, BlockPos landing,
                                                         List<BlockPos> candidateHazards,
                                                         BlockPos egress, RejectedLivingWallReason reason) {
        if (level == null || state == null || SoundAttractConfig.COMMON == null || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ADMISSION)) return;
        if (now >= state.nextLivingWallDiagnosticLogTick) {
            state.nextLivingWallDiagnosticLogTick = now + 20L;
            state.livingWallDiagnosticBudget = 0;
            state.livingWallDiagnosticScanBudget = 0;
        }
        if (state.livingWallDiagnosticBudget >= 16 || state.livingWallDiagnosticScanBudget >= 8) {
            state.livingWallDiagnosticSuppressed++;
            return;
        }
        state.livingWallDiagnosticBudget++;
        state.livingWallDiagnosticScanBudget++;
        RejectedLivingWallCandidate diagnostic = projectRejectedLivingWallCandidate(scanGeneration, now, ray,
                evidenceCell, threat, landing, candidateHazards, egress, reason);
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ADMISSION)) {
        SoundAttractMod.LOGGER.debug("[LivingWallCoordinator] living_bridge_candidate_rejected candidateId={} source={} edge={} firstSolidComponent={} hazardInterval={} gapInterval={} landing={} egress={} reason={} scanGeneration={} captureTick={} scanExpiresAt={} requestRevision={}",
                diagnostic.candidateId(), diagnostic.source(), diagnostic.edge(), diagnostic.firstSolidComponent(),
                diagnostic.hazardInterval(), diagnostic.gapInterval(), diagnostic.landing(), diagnostic.egress(),
                diagnostic.reason(), diagnostic.scanGeneration(), diagnostic.captureTick(),
                diagnostic.scanExpiresAt(), diagnostic.requestRevision());
        }
    }

    static RejectedLivingWallCandidate projectRejectedLivingWallCandidate(long scanGeneration, long captureTick,
                                                                         WallProfileScanner.RayEvidence ray,
                                                                         BlockPos evidenceCell, BlockPos threat,
                                                                         BlockPos landing,
                                                                         List<BlockPos> candidateHazards,
                                                                         BlockPos egress,
                                                                         RejectedLivingWallReason reason) {
        BlockPos source = ray == null ? null : BlockPos.containing(ray.origin());
        BlockPos firstSolid = ray == null ? null : ray.firstCollisionCell();
        List<BlockPos> hazards = capHazardInterval(candidateHazards);
        int hazardCount = candidateHazards == null ? 0 : candidateHazards.size();
        boolean truncated = hazardCount > hazards.size();
        List<BlockPos> gap = new ArrayList<>(4);
        if (source != null) gap.add(source);
        if (firstSolid != null) gap.add(firstSolid);
        if (landing != null) gap.add(landing);
        if (egress != null) gap.add(egress);
        String candidateId = scanGeneration + "/" + (ray == null ? -1L : ray.rayId()) + "/"
                + Objects.toString(evidenceCell) + "/" + Objects.toString(landing);
        long scanExpiresAt = captureTick + TARGET_SCAN_CACHE_TICKS;
        return new RejectedLivingWallCandidate(candidateId, source, evidenceCell, firstSolid, hazards, hazardCount,
                truncated, gap, landing, egress, reason, scanGeneration, captureTick, scanExpiresAt, -1L);
    }

    private static List<BlockPos> capHazardInterval(List<BlockPos> hazards) {
        if (hazards == null || hazards.isEmpty()) return List.of();
        int limit = Math.min(8, hazards.size());
        List<BlockPos> copy = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            BlockPos hazard = hazards.get(i);
            copy.add(hazard == null ? null : hazard.immutable());
        }
        return List.copyOf(copy);
    }

    static String exactCorridorRejectionProvenanceCategory(RejectedLivingWallReason reason) {
        if (reason == null) return "N/A";
        return switch (reason) {
            case NO_SAFE_LANDING -> "reachable:no_safe_landing";
            case NO_BRIDGE_TRIGGER -> "reachable:no_bridge_trigger";
            case HAZARD_CORRIDOR_INTERVAL_INVALID -> "reachable:hazard_corridor_interval_invalid";
            case SCENARIO_NONE -> "reachable:scenario_none";
            case HAZARD_SCENARIO_WITHOUT_HAZARD -> "reachable:hazard_scenario_without_hazard";
            case VERTICAL_PROGRESS_INCOMPATIBLE -> "N/A";
        };
    }

    private static void flushBuildGateDiagnostics(ServerLevel level, PerLevelState state, long now) {
        if (state == null || SoundAttractConfig.COMMON == null || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ADMISSION)
                || state.buildGateAttempts == 0) return;
        if (state.nextBuildGateLogTick < 0L) {
            state.nextBuildGateLogTick = now + RECRUITMENT_TIMEOUT_TICKS;
            return;
        }
        if (now < state.nextBuildGateLogTick) return;
        List<String> reasons = new ArrayList<>();
        state.buildGateRejections.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> reasons.add(entry.getKey() + "=" + entry.getValue()));
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Build gate summary attempts={} reasons={}",
                state.buildGateAttempts, String.join(",", reasons));
        state.buildGateAttempts = 0;
        state.buildGateRejections.clear();
        state.nextBuildGateLogTick = now + RECRUITMENT_TIMEOUT_TICKS;
    }

    private static BlockPos groupDestination(Mob leader) {
        if (leader == null) return null;
        LivingEntity target = leader.getTarget();
        if (target != null && target.isAlive() && !target.isRemoved() && leader.canAttack(target)) {
            if (leader.level() instanceof ServerLevel serverLevel) {
                recordCombatThreat(serverLevel, target);
            }
            return target.blockPosition();
        }
        for (var wrapped : leader.goalSelector.getAvailableGoals().stream().filter(goal -> goal.isRunning()).toList()) {
            var goal = wrapped.getGoal();
            if (goal instanceof AttractionGoal attraction && attraction.isPursuingSound()) {
                return attraction.getTargetSoundPos();
            }
            if (goal instanceof FollowerEdgeRelayGoal edge) {
                return edge.getTargetSoundPos();
            }
            if (goal instanceof FollowScentGoal scent && scent.isFollowingScent()) {
                Vec3 scentTarget = scent.getTargetPosition();
                return scentTarget == null ? null : BlockPos.containing(scentTarget);
            }
        }
        return null;
    }

    private static boolean hasBridgeNeed(ServerLevel level, BlockPos pivot, BlockPos anchor) {
        if (level == null || pivot == null || anchor == null) return false;
        int steps = Math.max(Math.abs(anchor.getX() - pivot.getX()), Math.abs(anchor.getZ() - pivot.getZ()));
        boolean hazardousGap = false;
        for (int step = 1; step < steps; step++) {
            double fraction = step / (double) steps;
            int x = (int) Math.round(pivot.getX() + (anchor.getX() - pivot.getX()) * fraction);
            int z = (int) Math.round(pivot.getZ() + (anchor.getZ() - pivot.getZ()) * fraction);
            int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos surface = new BlockPos(x, surfaceY, z);
            if (surface.getY() <= pivot.getY()
                    && (surface.getY() < pivot.getY() - 1
                    || level.getBlockState(surface).getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
                    || level.getBlockState(surface.below()).getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
                    || level.getBlockState(surface).is(BlockTags.TRAPDOORS))) {
                hazardousGap = true;
                break;
            }
        }
        if (hazardousGap) {
            return true;
        }
        int towardPivotX = Integer.compare(pivot.getX(), anchor.getX());
        int towardPivotZ = Integer.compare(pivot.getZ(), anchor.getZ());
        int adjacentHeight = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                anchor.getX() + towardPivotX, anchor.getZ() + towardPivotZ);
        if (anchor.getY() - adjacentHeight >= 2 || anchor.getY() - pivot.getY() >= 2 || pivot.getY() - anchor.getY() >= 2) {
            return true;
        }
        return false;
    }

    private static boolean hasBridgeNeed(ServerLevel level, Vec3 pivotCenter, BlockPos anchor) {
        if (level == null || pivotCenter == null || anchor == null) return false;
        return hasBridgeNeed(level, BlockPos.containing(pivotCenter.x, pivotCenter.y, pivotCenter.z), anchor);
    }

    private static BlockPos nearbySafeSite(ServerLevel level, int x, int referenceY, int z) {
        int radius = 4;
        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                BlockPos surface = resolveLocalSafeSurface(level, x + dx, z + dz, referenceY);
                if (surface != null && !candidates.contains(surface)) candidates.add(surface);
            }
        }
        candidates.sort(Comparator.comparingInt((BlockPos candidate) -> horizontalDistanceSqr(
                new BlockPos(x, referenceY, z), candidate)).thenComparingLong(BlockPos::asLong));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static boolean isUnsafeColumn(ServerLevel level, BlockPos pos) {
        BlockState here = level.getBlockState(pos);
        if (here.is(BlockTags.TRAPDOORS)) {
            return true;
        }
        if (here.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
            return true;
        }
        if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
            return true;
        }
        return false;
    }

    private static boolean hasUnsafeRoute(ServerLevel level, BlockPos pivot, BlockPos anchor) {
        int steps = Math.max(Math.abs(anchor.getX() - pivot.getX()), Math.abs(anchor.getZ() - pivot.getZ()));
        int minY = Math.min(pivot.getY(), anchor.getY()) - 3;
        int maxY = Math.max(pivot.getY(), anchor.getY()) + 1;
        for (int step = 1; step < steps; step++) {
            double fraction = step / (double) steps;
            int x = (int) Math.round(pivot.getX() + (anchor.getX() - pivot.getX()) * fraction);
            int z = (int) Math.round(pivot.getZ() + (anchor.getZ() - pivot.getZ()) * fraction);
            for (int y = minY; y <= maxY; y++) {
                BlockPos routePos = new BlockPos(x, y, z);
                if (!level.isLoaded(routePos)) {
                    return false;
                }
                BlockState state = level.getBlockState(routePos);
                if (state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
                    return true;
                }
                if (state.getBlock() instanceof TrapDoorBlock && state.hasProperty(TrapDoorBlock.OPEN)
                        && state.getValue(TrapDoorBlock.OPEN)) {
                    return true;
                }
            }
            BlockPos feet = new BlockPos(x, pivot.getY(), z);
            BlockPos support = feet.below();
            if (level.getBlockState(support).getCollisionShape(level, support).isEmpty()
                    && level.getBlockState(support.below()).getCollisionShape(level, support.below()).isEmpty()
                    && level.getBlockState(support.below(2)).getCollisionShape(level, support.below(2)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void refreshMembers(ServerLevel level, Formation formation, long now) {
        if (formation == null) return;
        boolean force = formation.lastMemberRefreshTick < 0L
                || formation.phase != formation.lastMemberRefreshPhase;
        if (!force && now - formation.lastMemberRefreshTick < 4L
                && (now + (formation.id == null ? 0 : formation.id.hashCode())) % 4 != 0) {
            return;
        }
        formation.lastMemberRefreshTick = now;
        formation.lastMemberRefreshPhase = formation.phase;
        Mob leader = resolvePlannerMob(level, formation);
        if (!formation.rosterFrozen) refreshLocalRoster(level, formation, leader, now);
        formation.recruitmentBlacklist.entrySet().removeIf(entry -> entry.getValue() <= now);
        List<Mob> candidates = new ArrayList<>();
        for (UUID id : formation.localRoster) {
            Mob candidate = getMob(level, id);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        Set<UUID> alreadyChosen = new HashSet<>(formation.members);
        alreadyChosen.addAll(formation.pendingBuilderIds);
        PerLevelState state = state(level);
        double maxDistSqr = ACTIVE_ASSEMBLY_MAX_RECRUITMENT_DISTANCE * ACTIVE_ASSEMBLY_MAX_RECRUITMENT_DISTANCE;
        candidates.removeIf(m -> m == leader || !m.isAlive() || m.isRemoved() || !isEligible(m)
                || m.distanceToSqr(Vec3.atBottomCenterOf(formation.pivot)) > maxDistSqr
                || alreadyChosen.contains(m.getUUID()) || isBeyondAnchor(formation, m)
                || !compatibleGeometry(m, formation)
                || formation.recruitmentBlacklist.containsKey(m.getUUID())
                || formation.terminalSafeBaseFailures.contains(m.getUUID())
                || shouldYieldBridgeCombat(m)
                || state.basinEvacuations.containsKey(m.getUUID())
                || state.mobOwners.containsKey(m.getUUID()) && !formation.id.equals(state.mobOwners.get(m.getUUID())));
        candidates.sort(Comparator.comparingDouble((Mob mob) -> {
                    double dist = distanceToNearestAssemblyCell(mob, formation);

                    return dist <= 8.0D ? 0 : 1;
                })
                .thenComparingInt((Mob mob) -> recruitmentElevationOrder(formation.pivot.getY(), mob.getY()))
                .thenComparing((Mob mob) -> !formation.transferredWorkerIds.contains(mob.getUUID()))
                .thenComparingDouble(mob -> distanceToNearestAssemblyCell(mob, formation))
                .thenComparing(mob -> mob.getUUID().toString()));
        if (candidates.size() > MAX_SCAN_CANDIDATES) candidates = new ArrayList<>(candidates.subList(0, MAX_SCAN_CANDIDATES));
        syncClaimedBridgeWorkers(level, formation, candidates);
        refreshCandidateHolding(level, formation, candidates, now);
        if (!refreshAssignmentRanking(formation, candidates, now)) return;
        int nextLayerLimit = nextLayerLimit(formation);
        int structuralCapacity = Math.max(0, nextLayerLimit - formation.members.size()
                - formation.pendingBuilderIds.size() - intakeAssignments(formation));
        int activeAssemblyReleaseLimit = activeAssemblyReleaseLimit();
        int assemblyCapacity = Math.max(0, Math.min(activeAssemblyReleaseLimit, formation.plan.assemblySlots().size())
                - activeTicketAssignments(formation));
        if (!tickElapsed(now, formation.lastAssemblyReleaseTick, ASSEMBLY_RELEASE_INTERVAL_TICKS)) {
            assemblyCapacity = 0;
        }
        for (Mob mob : candidates) {
            if (structuralCapacity <= 0 && assemblyCapacity <= 0) {
                break;
            }
            boolean alreadyRecruiting = formation.ticketGoals.containsKey(mob.getUUID());
            MobTicket ticket = formation.mobTickets.get(mob.getUUID());
            if (structuralCapacity > 0 && ticket != null && ticket.state == MobTicketState.ASSEMBLED) {
                if (grantIntakeTicket(level, mob, formation, nextLayerLimit, now)) {
                    structuralCapacity--;
                }
            } else if (!alreadyRecruiting && (formation.phase == Phase.SCAN || formation.phase == Phase.ASSEMBLE)
                    && assemblyCapacity > 0) {
                if (ticket == null || (ticket.state == MobTicketState.RELEASED && now >= ticket.releaseUntil)) {
                    AssemblyAssignment assembly = findReachableAssemblySlot(level, formation, mob, now,
                            formation.rankedAssemblySlots.get(mob.getUUID()));
                    if (assembly != null && isSafeAssemblySlot(level, mob, assembly.slot(), formation)
                            && isAssemblyLaneAvailable(formation, mob.getUUID(),
                            assembly.approach() == null ? assembly.slot() : assembly.approach())) {
                        MobTicket replacement = new MobTicket(mob.getUUID(), assembly.slot(), ++formation.ticketGeneration);
                        replacement.grantTick = now;
                        replacement.assemblyApproach = assembly.approach();
                        replacement.routeLengthBlocks = mob.position().distanceTo(Vec3.atBottomCenterOf(assembly.slot()));
                        replacement.progressDeadline = now + bridgeProgressWindowTicks(replacement.routeLengthBlocks,
                                Math.max(1, formation.ticketGoals.size() + 1));
                        formation.mobTickets.put(mob.getUUID(), replacement);
                        claimAssemblySlot(formation, mob.getUUID(), assembly.slot());
                        claimAssemblyLane(formation, mob.getUUID(),
                                assembly.approach() == null ? assembly.slot() : assembly.approach());
                        if (state.mobOwners.putIfAbsent(mob.getUUID(), formation.id) == null
                                || formation.id.equals(state.mobOwners.get(mob.getUUID()))) {
                            addRecruitmentGoal(mob, formation);
                            logRecruitmentEvent(formation, mob, "recruitment_selected:routeable", replacement,
                                    Vec3.atBottomCenterOf(assembly.approach() == null ? assembly.slot() : assembly.approach()));

                            if (formation.assembledSlots >= formation.columnCount) {
                                formation.lastAssemblyReleaseTick = now;
                            }
                            assemblyCapacity--;
                        } else {
                            releaseAssemblySlot(formation, mob.getUUID());
                            formation.mobTickets.remove(mob.getUUID());
                        }
                    }
                } else if (hasRecordedAssemblyFailure(formation, mob.getUUID(), now)
                        && (ticket == null || ticket.state == MobTicketState.RELEASED)) {
                    formation.recruitmentBlacklist.put(mob.getUUID(), now + RECRUITER_PATH_FAILURE_BLACKLIST_TICKS);
                }
            }
        }
        formation.lastValidationTick = now;
    }

    private static void syncClaimedBridgeWorkers(ServerLevel level, Formation formation, List<Mob> candidates) {
        if (level == null || formation == null) return;
        Set<UUID> desired = new LinkedHashSet<>();
        desired.addAll(formation.members);
        desired.addAll(formation.pendingBuilderIds);
        desired.addAll(formation.reserved);
        desired.addAll(formation.structuralBuilderIds);
        desired.addAll(formation.transitCrosserIds);
        desired.addAll(formation.ticketGoals.keySet());
        desired.addAll(formation.candidateHoldingGoals.keySet());
        for (Map.Entry<UUID, MobTicket> entry : formation.mobTickets.entrySet()) {
            if (entry.getValue() != null && entry.getValue().state != MobTicketState.RELEASED) {
                desired.add(entry.getKey());
            }
        }
        int claimLimit = Math.max(formation.requiredBodies, formation.usableBodies);
        if (claimLimit > 0 && candidates != null) {
            for (Mob candidate : candidates) {
                if (desired.size() >= claimLimit) break;
                if (candidate == null || !candidate.isAlive() || candidate.isRemoved()) continue;
                UUID id = candidate.getUUID();
                UUID owner = state(level).mobOwners.get(id);
                if (owner == null || formation.id.equals(owner)) desired.add(id);
            }
        }
        for (UUID id : new ArrayList<>(formation.claimedWorkerIds)) {
            if (desired.contains(id) || isBridgeWorkerClaimedWithoutPassiveSet(formation, id)) continue;
            state(level).mobOwners.remove(id, formation.id);
            formation.claimedWorkerIds.remove(id);
        }
        for (UUID id : desired) {
            UUID owner = state(level).mobOwners.putIfAbsent(id, formation.id);
            if (owner == null || formation.id.equals(owner)) formation.claimedWorkerIds.add(id);
        }
    }

    private static boolean refreshAssignmentRanking(Formation formation, List<Mob> candidates, long now) {
        if (formation == null || candidates == null || candidates.isEmpty()) return false;
        if (formation.assignmentFuture == null && !formation.rankedAssemblySlots.isEmpty()
                && !tickElapsed(now, formation.assignmentSnapshotTick, 20L)) return true;
        if (formation.assignmentFuture == null) formation.rankedAssemblySlots.clear();
        if (formation.assignmentFuture != null) {
            if (!formation.assignmentFuture.isDone()) return !formation.rankedAssemblySlots.isEmpty();
            try {
                LivingBridgeAssignmentPlanner.Result result = formation.assignmentFuture.join();
                formation.assignmentFuture = null;
                formation.assignmentSnapshotTick = now;
                if (result.generation() != formation.assignmentGeneration
                        || result.terrainSignature() != formation.terrainSignature) {
                    formation.rankedAssemblySlots.clear();
                    return false;
                }
                formation.rankedAssemblySlots.clear();
                for (LivingBridgeAssignmentPlanner.Ranking ranking : result.rankings()) {
                    Mob worker = candidates.stream().filter(candidate -> candidate != null
                            && ranking.candidateId().equals(candidate.getUUID())).findFirst().orElse(null);
                    LivingBridgeAssignmentPlanner.WorkerToken token = ranking.workerToken();
                    if (worker == null || !LivingBridgeAssignmentPlanner.isCurrentRanking(ranking, worker.getUUID(),
                            formation.assignmentGeneration, formation.terrainSignature,
                            assignmentPositionFingerprint(worker))) continue;
                    formation.rankedAssemblySlots.put(ranking.candidateId(), ranking.slots().stream()
                            .map(slot -> new BlockPos(slot.x(), slot.y(), slot.z()))
                            .toList());
                }
                return !formation.rankedAssemblySlots.isEmpty();
            } catch (Throwable ignored) {
                formation.assignmentFuture = null;
                formation.rankedAssemblySlots.clear();
                return false;
            }
        }
        List<LivingBridgeAssignmentPlanner.Slot> slots = formation.plan.assemblySlots().stream()
                .filter(slot -> !isAssemblySlotAssigned(formation, slot))
                .limit(activeAssemblyReleaseLimit() * 4L)
                .map(slot -> new LivingBridgeAssignmentPlanner.Slot(slot.getX(), slot.getY(), slot.getZ()))
                .toList();
        if (slots.isEmpty()) return false;
        List<LivingBridgeAssignmentPlanner.Candidate> snapshot = candidates.stream()
                .map(mob -> new LivingBridgeAssignmentPlanner.Candidate(mob.getUUID(), mob.getX(), mob.getY(), mob.getZ(),
                        mob.blockPosition().getY(), formation.transferredWorkerIds.contains(mob.getUUID())))
                .toList();
        formation.assignmentGeneration++;
        formation.assignmentSnapshotTick = now;
        formation.assignmentFuture = LivingBridgeAssignmentPlanner.submit(snapshot, slots,
                formation.assignmentGeneration, formation.terrainSignature);
        return formation.assignmentFuture == null;
    }

    private static long assignmentPositionFingerprint(Mob worker) {
        if (worker == null) return Long.MIN_VALUE;
        long hash = 0xcbf29ce484222325L;
        hash = (hash ^ Double.doubleToLongBits(Math.floor(worker.getX() * 4.0D) / 4.0D)) * 0x100000001b3L;
        hash = (hash ^ Double.doubleToLongBits(Math.floor(worker.getY() * 4.0D) / 4.0D)) * 0x100000001b3L;
        hash = (hash ^ Double.doubleToLongBits(Math.floor(worker.getZ() * 4.0D) / 4.0D)) * 0x100000001b3L;
        return hash;
    }

    private static void refreshCandidateHolding(ServerLevel level, Formation formation, List<Mob> candidates, long now) {
        if (level == null || formation == null) {
            return;
        }
        Set<UUID> desired = new HashSet<>();
        int capacity = Math.max(0, Math.min(activeAssemblyReleaseLimit(), candidates.size())
                - activeTicketAssignments(formation));
        long rosterSignature = candidateRosterSignature(candidates);
        long searchSignature = candidateHoldingSearchSignature(formation, rosterSignature);
        if (formation.lastHoldingSearchSignature != searchSignature) {
            formation.candidateHoldingSearches.clear();
            formation.lastHoldingSearchSignature = searchSignature;
        }
        int searchBudget = HOLDING_CELL_SEARCH_BUDGET_PER_TICK;
        int pathBudget = HOLDING_CELL_PATH_VALIDATION_BUDGET;
        for (Mob mob : candidates) {
            if (desired.size() >= capacity) break;

            if (formation.ticketGoals.containsKey(mob.getUUID())) continue;
            MobTicket ticket = formation.mobTickets.get(mob.getUUID());
            if (ticket != null && ticket.state == MobTicketState.RELEASED) continue;
            if (shouldYieldBridgeCombat(mob)) continue;
            BlockPos cell = formation.candidateHoldingCells.get(mob.getUUID());
            if (cell == null || !isCachedHoldingCellValid(level, formation, mob, cell, now)) {
                cell = findCandidateHoldingCell(level, formation, mob, now, searchBudget, pathBudget);
            }
            if (cell == null) continue;
            desired.add(mob.getUUID());
            formation.candidateHoldingCells.put(mob.getUUID(), cell);
            formation.candidateHoldingSearches.remove(mob.getUUID());
            state(level).mobOwners.putIfAbsent(mob.getUUID(), formation.id);
            if (!formation.candidateHoldingGoals.containsKey(mob.getUUID())) {
                ensureBridgeWorkGoal(mob);
                formation.candidateHoldingGoals.put(mob.getUUID(), Boolean.TRUE);
                logCandidateHoldingEvent(formation, mob, "candidate_holding_assigned",
                        Vec3.atBottomCenterOf(cell));
            }
        }
        if (capacity > 0 && desired.isEmpty() && !candidates.isEmpty()
                && tickElapsed(now, formation.lastHoldingDiagnosticTick, 40L)) {
            formation.lastHoldingDiagnosticTick = now;
            logCandidateHoldingEvent(formation, null, "candidate_holding_no_safe_cell", null);
        }
        for (UUID id : new ArrayList<>(formation.candidateHoldingGoals.keySet())) {
            if (!desired.contains(id) || formation.ticketGoals.containsKey(id)) {
                Mob mob = getMob(level, id);
                if (mob != null) removeCandidateHolding(mob, formation);
                else {
                    formation.candidateHoldingGoals.remove(id);
                    formation.candidateHoldingCells.remove(id);
                }
            }
        }
    }

    private static BlockPos findCandidateHoldingCell(ServerLevel level, Formation formation, Mob mob, long now,
                                                     int searchBudget, int pathBudget) {
        if (level == null || formation == null || mob == null) {
            return null;
        }
        CandidateHoldingSearch search = formation.candidateHoldingSearches.computeIfAbsent(mob.getUUID(), ignored ->
                new CandidateHoldingSearch(buildCandidateHoldingCells(level, formation, mob), formation.terrainSignature,
                        candidateRosterSignature(formation.localRoster), formation.request == null ? -1L : formation.request.revision(), now));
        long currentTerrain = formation.terrainSignature >= 0L ? formation.terrainSignature
                : terrainSignature(level, formation.pivot, formation.anchor, formation.request == null ? null : formation.request.evidence());
        if (search.lastTerrainSignature != currentTerrain
                || search.lastRosterSignature != candidateRosterSignature(formation.localRoster)
                || search.lastRevision != (formation.request == null ? -1L : formation.request.revision())) {
            search.cells.clear();
            search.cells.addAll(buildCandidateHoldingCells(level, formation, mob));
            search.cursor = 0;
            search.lastTerrainSignature = currentTerrain;
            search.lastRosterSignature = candidateRosterSignature(formation.localRoster);
            search.lastRevision = formation.request == null ? -1L : formation.request.revision();
        }
        if (search.cells.isEmpty()) {
            return null;
        }
        int steps = 0;
        while (steps < Math.max(1, searchBudget) && search.cursor < search.cells.size()) {
            BlockPos cell = search.cells.get(search.cursor++);
            steps++;
            if (!isSafeApproachCell(level, cell)
                    || !isCandidateHoldingCellAvailable(formation, mob.getUUID(), cell)
                    || !isCandidateHoldingCellSeparate(formation, cell)
                    || isNearHazard(level, cell, formation.plan.hazardCells(), formation.plan.forward())) {
                continue;
            }
            if (pathBudget > 0) {
                pathBudget--;
                var path = mob.getNavigation().createPath(cell, 0);
                if (path == null || !path.canReach() || path.getTarget() == null
                        || path.getTarget().distSqr(cell) > 1.0D
                        || !isSafeHoldingPath(level, formation, path)) {
                    continue;
                }
            }
            search.lastCell = cell;
            search.lastUpdatedTick = now;
            cacheHoldingCell(level, formation, cell, now);
            return cell;
        }
        if (search.cursor >= search.cells.size()) {
            search.cursor = 0;
        }
        return search.lastCell != null && isCachedHoldingCellValid(level, formation, mob, search.lastCell, now)
                ? search.lastCell : null;
    }

    private static List<BlockPos> buildCandidateHoldingCells(ServerLevel level, Formation formation, Mob mob) {
        if (formation == null || mob == null) {
            return List.of();
        }
        BlockPos center = formation.pivot;
        List<BlockPos> cells = new ArrayList<>();
        for (int dx = -CANDIDATE_HOLDING_RADIUS; dx <= CANDIDATE_HOLDING_RADIUS; dx += 2) {
            for (int dz = -CANDIDATE_HOLDING_RADIUS; dz <= CANDIDATE_HOLDING_RADIUS; dz += 2) {
                int distanceSqr = dx * dx + dz * dz;
                if (distanceSqr < CANDIDATE_HOLDING_MIN_RADIUS * CANDIDATE_HOLDING_MIN_RADIUS
                        || distanceSqr > CANDIDATE_HOLDING_RADIUS * CANDIDATE_HOLDING_RADIUS) continue;
                BlockPos cell = level != null
                        ? resolveLocalSafeSurface(level, center.getX() + dx, center.getZ() + dz, center.getY())
                        : new BlockPos(center.getX() + dx, center.getY(), center.getZ() + dz);
                if (cell != null && !cells.contains(cell)) {
                    cells.add(cell);
                }
            }
        }
        cells.sort(Comparator.comparingDouble((BlockPos cell) -> mob.distanceToSqr(Vec3.atBottomCenterOf(cell)))
                .thenComparingLong(BlockPos::asLong));
        return cells;
    }

    private static List<BlockPos> buildCandidateHoldingCells(Formation formation, Mob mob) {
        return buildCandidateHoldingCells(null, formation, mob);
    }

    private static boolean isCachedHoldingCellValid(ServerLevel level, Formation formation, Mob mob, BlockPos cell, long now) {
        if (level == null || formation == null || mob == null || cell == null) {
            return false;
        }
        if (!isSafeApproachCell(level, cell) || !isCandidateHoldingCellAvailable(formation, mob.getUUID(), cell)
                || !isCandidateHoldingCellSeparate(formation, cell)
                || isNearHazard(level, cell, formation.plan.hazardCells(), formation.plan.forward())) {
            return false;
        }
        HoldingCellCacheEntry cached;
        synchronized (HOLDING_CELL_CACHE) {
            cached = HOLDING_CELL_CACHE.get(new HoldingCellCacheKey(level.dimension().identifier(), cell, formation.target,
                    formation.terrainSignature, formation.request == null ? -1L : formation.request.revision()));
        }
        return cached != null && cached.expiresAt() > now && cell.equals(cached.cell());
    }

    private static void cacheHoldingCell(ServerLevel level, Formation formation, BlockPos cell, long now) {
        if (level == null || formation == null || cell == null) {
            return;
        }
        synchronized (HOLDING_CELL_CACHE) {
            HOLDING_CELL_CACHE.put(new HoldingCellCacheKey(level.dimension().identifier(), cell, formation.target,
                            formation.terrainSignature, formation.request == null ? -1L : formation.request.revision()),
                    new HoldingCellCacheEntry(cell, now + HOLDING_CELL_CACHE_TICKS));
            while (HOLDING_CELL_CACHE.size() > MAX_HOLDING_CELL_CACHE_ENTRIES) {
                HoldingCellCacheKey oldest = HOLDING_CELL_CACHE.keySet().iterator().next();
                HOLDING_CELL_CACHE.remove(oldest);
            }
        }
    }

    private static long candidateRosterSignature(List<Mob> candidates) {
        long signature = 1L;
        if (candidates == null) {
            return signature;
        }
        for (Mob mob : candidates) {
            signature = 31L * signature + (mob == null ? 0L : mob.getUUID().getMostSignificantBits() ^ mob.getUUID().getLeastSignificantBits());
        }
        return signature;
    }

    private static long candidateRosterSignature(Set<UUID> roster) {
        long signature = 1L;
        if (roster == null) {
            return signature;
        }
        List<UUID> ordered = new ArrayList<>(roster);
        ordered.sort(Comparator.comparing(UUID::toString));
        for (UUID uuid : ordered) {
            signature = 31L * signature + (uuid == null ? 0L : uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits());
        }
        return signature;
    }

    private static long candidateHoldingSearchSignature(Formation formation, long rosterSignature) {
        long signature = 1L;
        signature = 31L * signature + (formation == null || formation.request == null ? -1L : formation.request.revision());
        signature = 31L * signature + (formation == null ? -1L : formation.terrainSignature);
        signature = 31L * signature + rosterSignature;
        signature = 31L * signature + (formation == null || formation.target == null ? -1L : formation.target.asLong());
        return signature;
    }

    static HoldingSearchStep advanceHoldingSearchStep(List<BlockPos> cells, int cursor, int budget) {
        if (cells == null || cells.isEmpty() || budget <= 0) {
            return new HoldingSearchStep(null, Math.max(0, cursor), false);
        }
        int nextCursor = Math.max(0, cursor);
        int remaining = Math.max(1, budget);
        while (remaining-- > 0 && nextCursor < cells.size()) {
            BlockPos cell = cells.get(nextCursor++);
            if (cell != null) {
                return new HoldingSearchStep(cell, nextCursor, nextCursor >= cells.size());
            }
        }
        return new HoldingSearchStep(null, nextCursor, nextCursor >= cells.size());
    }

    static final class HoldingSearchStep {
        final BlockPos cell;
        final int cursor;
        final boolean completed;

        HoldingSearchStep(BlockPos cell, int cursor, boolean completed) {
            this.cell = cell;
            this.cursor = cursor;
            this.completed = completed;
        }
    }

    private static boolean isCandidateHoldingCellSeparate(Formation formation, BlockPos cell) {
        if (formation == null || cell == null) return false;
        if (horizontalDistanceSqr(cell, formation.pivot) < CANDIDATE_HOLDING_MIN_RADIUS * CANDIDATE_HOLDING_MIN_RADIUS) {
            return false;
        }
        for (BlockPos assembly : formation.plan.assemblySlots()) {
            if (assembly != null && horizontalDistanceSqr(cell, assembly)
                    < CANDIDATE_HOLDING_ASSEMBLY_BUFFER * CANDIDATE_HOLDING_ASSEMBLY_BUFFER) return false;
        }
        for (BlockPos lane : formation.assemblyLaneAssignments.values()) {
            if (lane != null && horizontalDistanceSqr(cell, lane)
                    < CANDIDATE_HOLDING_ASSEMBLY_BUFFER * CANDIDATE_HOLDING_ASSEMBLY_BUFFER) return false;
        }
        return true;
    }

    private static boolean isCandidateHoldingCellAvailable(Formation formation, UUID mobId, BlockPos cell) {
        for (Map.Entry<UUID, BlockPos> entry : formation.candidateHoldingCells.entrySet()) {
            if (!mobId.equals(entry.getKey()) && entry.getValue().distSqr(cell) < 9.0D) return false;
        }
        return true;
    }

    private static void removeCandidateHolding(Mob mob, Formation formation) {
        if (mob == null || formation == null) return;
        formation.candidateHoldingGoals.remove(mob.getUUID());
        formation.candidateHoldingCells.remove(mob.getUUID());
        ensureBridgeWorkGoal(mob);
        mob.getNavigation().stop();
    }

    private static void releaseBridgeOwnership(ServerLevel level, Formation formation, UUID id) {
        if (level == null || formation == null || id == null) return;
        state(level).mobOwners.remove(id, formation.id);
    }

    private static void restoreCandidateHolding(ServerLevel level, Formation formation, Mob mob) {
        if (level == null || formation == null || mob == null) return;
        UUID id = mob.getUUID();
        if (formation.collapsed || formation.candidateHoldingGoals.containsKey(id)
                || formation.ticketGoals.containsKey(id) || formation.mobTickets.get(id) == null
                || formation.mobTickets.get(id).state == MobTicketState.RELEASED) {
            return;
        }
        BlockPos cell = formation.candidateHoldingCells.get(id);
        if (cell == null || !isSafeApproachCell(level, cell)) {
            return;
        }
        ensureBridgeWorkGoal(mob);
        formation.candidateHoldingGoals.put(id, Boolean.TRUE);
        state(level).mobOwners.putIfAbsent(id, formation.id);
        logCandidateHoldingEvent(formation, mob, "candidate_holding_restored", Vec3.atBottomCenterOf(cell));
    }

    private static void logCandidateHoldingEvent(Formation formation, Mob mob, String event, Vec3 target) {
        if (SoundAttractConfig.COMMON == null || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_FORMATION)
                || formation == null) return;
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] CandidateHolding bridge={} event={} mob={} pos={} target={}",
                formation.id, event, mob == null ? "none" : mob.getUUID(),
                mob == null ? "none" : mob.position(), target);
    }

    private static double distanceToNearestAssemblyCell(Mob mob, Formation formation) {
        double best = Double.MAX_VALUE;
        for (BlockPos cell : formation.plan.assemblySlots()) {
            if (!isAssemblySlotAssigned(formation, cell)) {
                best = Math.min(best, mob.distanceToSqr(Vec3.atBottomCenterOf(cell)));
            }
        }
        return best;
    }

    private static boolean hasRecordedAssemblyFailure(Formation formation, UUID mobId, long now) {
        return formation.pathSurveys.entrySet().stream()
                .anyMatch(entry -> entry.getKey().workerId().equals(mobId)
                        && !entry.getValue().reachable() && entry.getValue().expiresAt() > now);
    }

    static int recruitmentElevationOrder(int pivotY, double workerY) {
        int workerBlockY = Mth.floor(workerY);
        if (workerBlockY == pivotY) return 0;
        if (workerBlockY == pivotY + 1) return 1;
        if (workerBlockY > pivotY + 1) return 2;
        return 3;
    }

    private static AssemblyAssignment findReachableAssemblySlot(ServerLevel level, Formation formation, Mob mob, long now,
                                                                List<BlockPos> rankedSlots) {
        if (level == null || formation == null || mob == null) return null;
        List<BlockPos> cells = rankedSlots == null || rankedSlots.isEmpty()
                ? connectedAssemblyRoot(level, formation)
                : new ArrayList<>(rankedSlots);
        cells.sort(Comparator.comparingInt((BlockPos cell) -> recruitmentElevationOrder(formation.pivot.getY(), cell.getY()))
                .thenComparingDouble(cell -> mob.distanceToSqr(Vec3.atBottomCenterOf(cell)))
                .thenComparing(BlockPos::compareTo));
        if (cells.size() > 6) cells = new ArrayList<>(cells.subList(0, 6));
        return firstRankedAssemblyMatch(cells, cell -> {
            if (isAssemblySlotAssigned(formation, cell) || !isSafeAssemblySlot(level, mob, cell, formation)) return null;
            if (isNavigableAssemblySurface(level, cell) && hasReachableSurvey(level, formation, mob, cell, now)) {
                return isAssemblyLaneAvailable(formation, mob.getUUID(), cell)
                        ? new AssemblyAssignment(cell, null) : null;
            }
            BlockPos approach = findReachableAssemblyApproach(level, formation, mob, cell, now);
            return approach == null ? null : new AssemblyAssignment(cell, approach);
        });
    }

    static <T, R> R firstRankedAssemblyMatch(List<T> rankedCandidates,
                                             java.util.function.Function<T, R> evaluateCandidate) {
        if (rankedCandidates == null || evaluateCandidate == null) return null;
        for (T candidate : rankedCandidates) {
            R result = evaluateCandidate.apply(candidate);
            if (result != null) return result;
        }
        return null;
    }

    private static List<BlockPos> connectedAssemblyRoot(ServerLevel level, Formation formation) {
        List<BlockPos> safe = formation.plan.assemblySlots().stream()
                .filter(cell -> isSafeAssemblyGeometry(level, cell, formation))
                .distinct().toList();
        Set<BlockPos> remaining = new HashSet<>(safe);
        List<BlockPos> largest = List.of();
        while (!remaining.isEmpty()) {
            BlockPos seed = remaining.iterator().next();
            List<BlockPos> component = new ArrayList<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(seed);
            remaining.remove(seed);
            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst();
                component.add(current);
                for (BlockPos candidate : new ArrayList<>(remaining)) {
                    if (Math.abs(candidate.getX() - current.getX()) <= 1
                            && Math.abs(candidate.getY() - current.getY()) <= 1
                            && Math.abs(candidate.getZ() - current.getZ()) <= 1) {
                        remaining.remove(candidate);
                        queue.addLast(candidate);
                    }
                }
            }
            if (component.size() > largest.size()) largest = component;
        }
        return largest.isEmpty() ? new ArrayList<>(formation.plan.assemblySlots()) : largest;
    }

    private static boolean isSafeAssemblyGeometry(ServerLevel level, BlockPos cell, Formation formation) {
        return level != null && cell != null && formation != null
                && isNavigableAssemblySurface(level, cell)
                && hasSafeStandingEnvelope(level, cell)
                && !isNearHazard(level, cell, formation.plan.hazardCells(), formation.plan.forward());
    }

    private static List<BlockPos> getValidAssemblyApproaches(ServerLevel level, Formation formation, BlockPos slot) {
        if (level == null || formation == null || slot == null || formation.plan == null) {
            return Collections.emptyList();
        }
        List<BlockPos> cached = formation.slotApproachCache.get(slot);
        if (cached != null) {
            return cached;
        }
        List<BlockPos> approaches = new ArrayList<>();
        int radius = 5;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distanceSqr = dx * dx + dz * dz;
                if (distanceSqr > radius * radius) continue;
                BlockPos candidate = resolveLocalSafeSurface(level, slot.getX() + dx, slot.getZ() + dz, slot.getY());
                if (candidate == null || !isSafeApproachCell(level, candidate)
                        || Math.abs(candidate.getY() - slot.getY()) > ROOT_ZONE_MAX_HEIGHT_SPREAD
                        || (formation.plan.assemblySlots() != null && formation.plan.assemblySlots().contains(candidate))
                        || !isValidAssemblyApproach(candidate, slot)
                        || isNearHazard(level, candidate, formation.plan.hazardCells(), formation.plan.forward())) continue;
                if (!approaches.contains(candidate)) approaches.add(candidate);
            }
        }
        if (approaches.isEmpty() && formation.pivot != null && !slot.equals(formation.pivot)) {
            List<BlockPos> pivotApproaches = getValidAssemblyApproaches(level, formation, formation.pivot);
            if (!pivotApproaches.isEmpty()) {
                approaches = new ArrayList<>(pivotApproaches);
            }
        }
        List<BlockPos> result = Collections.unmodifiableList(approaches);
        formation.slotApproachCache.put(slot, result);
        return result;
    }

    private static BlockPos findReachableAssemblyApproach(ServerLevel level, Formation formation, Mob mob,
                                                          BlockPos slot, long now) {
        List<BlockPos> validApproaches = getValidAssemblyApproaches(level, formation, slot);
        if (validApproaches.isEmpty()) return null;
        List<BlockPos> candidates = new ArrayList<>(validApproaches.size());
        for (BlockPos candidate : validApproaches) {
            if (isAssemblyLaneAvailable(formation, mob.getUUID(), candidate)) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator.comparingDouble((BlockPos candidate) -> mob.distanceToSqr(Vec3.atBottomCenterOf(candidate)))
                .thenComparingInt(candidate -> horizontalDistanceSqr(candidate, slot))
                .thenComparingLong(BlockPos::asLong));
        if (candidates.size() > 3) candidates = new ArrayList<>(candidates.subList(0, 3));
        for (BlockPos candidate : candidates) {
            if (hasReachableSurvey(level, formation, mob, candidate, now)) return candidate;
        }
        return null;
    }

    private static boolean isAssemblySlotAssigned(Formation formation, BlockPos cell) {
        return formation != null && cell != null && formation.assemblySlotOwners.containsKey(cell);
    }

    private static boolean isAssemblyLaneAvailable(Formation formation, UUID mobId, BlockPos lane) {
        if (formation == null || mobId == null || lane == null) return false;
        for (Map.Entry<UUID, BlockPos> entry : formation.assemblyLaneAssignments.entrySet()) {
            if (mobId.equals(entry.getKey())) continue;
            BlockPos occupied = entry.getValue();
            if (occupied != null && occupied.distSqr(lane) <= APPROACH_LANE_SPACING * APPROACH_LANE_SPACING) {
                return false;
            }
        }
        return true;
    }

    private static void claimAssemblyLane(Formation formation, UUID mobId, BlockPos lane) {
        if (formation != null && mobId != null && lane != null) {
            formation.assemblyLaneAssignments.put(mobId, lane);
        }
    }

    private static void releaseAssemblyLane(Formation formation, UUID mobId) {
        if (formation != null && mobId != null) formation.assemblyLaneAssignments.remove(mobId);
    }

    private static boolean hasReachableSurvey(ServerLevel level, Formation formation, Mob mob, BlockPos cell, long now) {
        formation.pathSurveys.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        PathSurveyKey key = new PathSurveyKey(mob.getUUID(), cell);
        PathSurvey cached = formation.pathSurveys.get(key);
        BlockPos source = mob.blockPosition();
        if (cached != null && cached.source().distSqr(source) <= 1.0D) return cached.reachable();
        if (mob.distanceToSqr(Vec3.atBottomCenterOf(cell)) <= 9.0D) {
            formation.pathSurveys.put(key, new PathSurvey(true, source, now + PATH_SURVEY_CACHE_TICKS));
            return true;
        }
        if (state(level).pathSurveysRemaining <= 0) return false;
        state(level).pathSurveysRemaining--;
        var path = mob.getNavigation().createPath(cell, 0);
        boolean reachable = path != null && ((path.canReach() && path.getTarget() != null && path.getTarget().distSqr(cell) <= 4.0D)
                || (path.getEndNode() != null && path.getEndNode().asBlockPos().distSqr(cell) <= 4.0D));
        if (formation.pathSurveys.size() >= PATH_SURVEY_CACHE_LIMIT && !formation.pathSurveys.containsKey(key)) {
            formation.pathSurveys.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
            if (formation.pathSurveys.size() >= PATH_SURVEY_CACHE_LIMIT) {
                var iterator = formation.pathSurveys.entrySet().iterator();
                int toRemove = PATH_SURVEY_CACHE_LIMIT / 4;
                while (iterator.hasNext() && toRemove-- > 0) {
                    iterator.next();
                    iterator.remove();
                }
            }
        }
        formation.pathSurveys.put(key, new PathSurvey(reachable, source, now + PATH_SURVEY_CACHE_TICKS));
        if (!reachable && DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ROUTING)) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ADMISSION)) {
            SoundAttractMod.LOGGER.debug("[LivingWallCoordinator] admission_route_rejected bridge={} mob={} slot={} source={}",
                    formation.id, mob.getUUID(), cell, source);
            }
        }
        return reachable;
    }

    private static void replanFeederRecruitment(ServerLevel level, Formation formation, Mob mob,
                                                MobTicket assignment, long now, String reason) {
        if (level == null || formation == null || mob == null || assignment == null
                || (assignment.state != MobTicketState.MOVING_TO_FEEDER
                && assignment.state != MobTicketState.SUCTIONING)) return;
        if (assignment.lastIntakeReplanTick != Long.MIN_VALUE
                && now - assignment.lastIntakeReplanTick < CAPTURE_RETRY_COOLDOWN_TICKS) return;
        assignment.lastIntakeReplanTick = now;
        logRecruitmentEvent(formation, mob, "capture_retry:" + reason, assignment, null);
        IntakeWindow previous = assignment.intakeWindow;
        IntakeWindow replacement = null;
        if (formation.plan != null && formation.plan.intakeWindows() != null) {
            for (IntakeWindow candidate : formation.plan.intakeWindows()) {
                if (candidate == null || candidate.feederCell() == null || candidate.equals(previous)
                        || !isValidIntakeWindow(level, candidate, formation)
                        || !hasReachableRecruitmentRoute(level, formation, mob, assignment.assemblySlot,
                        candidate, now)) continue;
                replacement = candidate;
                break;
            }
        }
        if (replacement != null) {
            assignment.intakeWindow = replacement;
            assignment.intakeWindowFailures = 0;
            logRecruitmentEvent(formation, mob, "feeder_replan:" + reason, assignment,
                    Vec3.atBottomCenterOf(replacement.feederCell()));
        } else {
            assignment.intakeWindowFailures++;
            if (shouldTerminateIntakeCaptureFailure(assignment.intakeWindowFailures)) {
                logRecruitmentEvent(formation, mob, "intake_terminal_release:" + reason, assignment, null);
                releaseRecruiter(level, formation, mob.getUUID(), now);
                return;
            }
            logRecruitmentEvent(formation, mob, "feeder_replan:" + reason + ":retained", assignment,
                    previous == null || previous.feederCell() == null
                            ? null : Vec3.atBottomCenterOf(previous.feederCell()));
        }
        if (assignment.state == MobTicketState.SUCTIONING) {
            transitionTicket(assignment, MobTicketState.MOVING_TO_FEEDER);
            releaseRalliedHold(level, formation, mob.getUUID(), mob);
        }
        assignment.captureTicks = 0;
        assignment.controlledPull = false;
        assignment.captureRetryUntil = now + CAPTURE_RETRY_COOLDOWN_TICKS;
        assignment.lastPathTick = Long.MIN_VALUE;
        assignment.observationTick = Long.MIN_VALUE;
        assignment.bestDistanceSqr = Double.MAX_VALUE;
        assignment.navigationAccepted = false;
        assignment.navigationReported = false;
    }

    private static void processRecruitmentReports(ServerLevel level, Formation formation, long now) {
        for (UUID id : new ArrayList<>(formation.ticketGoals.keySet())) {
            MobTicket assignment = formation.mobTickets.get(id);
            if (assignment == null || assignment.goalStarted
                    || !bridgeControllerMissedStartup(now, assignment.grantTick, assignment.controllerStartTick)) continue;
            Mob mob = getMob(level, id);
            if (mob != null) {
                assignment.lastFailureCode = "controller_not_started";
                releaseRecruiter(level, formation, id, now);
            }
        }
        for (UUID id : new ArrayList<>(formation.recruitmentArrivalReports)) {
            formation.recruitmentArrivalReports.remove(id);
            if (!formation.ticketGoals.containsKey(id)) continue;
            Mob mob = getMob(level, id);
            MobTicket assignment = formation.mobTickets.get(id);
            if (mob == null || !mob.isAlive() || mob.isRemoved() || assignment == null) {
                releaseRecruiter(level, formation, id, now);
                continue;
            }
            if (assignment.state == MobTicketState.MOVING_TO_ASSEMBLY
                    || assignment.state == MobTicketState.STAGING_AT_ASSEMBLY
                    || assignment.state == MobTicketState.ASSEMBLED) {
                if (!isSafeAssemblySlot(level, mob, assignment.assemblySlot, formation)
                        || !isWithinAssemblyAcceptanceEnvelope(mob.position(), assignment.assemblySlot)) {
                    logRecruitmentEvent(formation, mob, "assembly_arrival_invalid", assignment, null);
                    releaseRecruiter(level, formation, id, now);
                    continue;
                }
                if (assignment.state == MobTicketState.MOVING_TO_ASSEMBLY) {
                    transitionTicket(assignment, MobTicketState.STAGING_AT_ASSEMBLY);
                    assignment.assemblyStageStartedTick = now;
                    assignment.assemblyLastProgressTick = now;
                    assignment.assemblyBestDistanceSqr = exactAssemblyDistanceSqr(mob.position(), assignment.assemblySlot);
                    holdRalliedWorker(mob, formation);
                    logRecruitmentEvent(formation, mob, "assembly_staging_started", assignment, null);
                }
                if (assignment.state == MobTicketState.STAGING_AT_ASSEMBLY) {
                    if (!tryControlledAssemblyStaging(mob, Vec3.atBottomCenterOf(assignment.assemblySlot))) {
                        continue;
                    }
                }
                if (!preflightFoundationSupport(level, formation, mob, assignment)
                        || !finalizeAssemblyLock(level, formation, mob, assignment,
                        Vec3.atBottomCenterOf(assignment.assemblySlot), true)) {
                    continue;
                }
                if (formation.members.size() < formation.columnCount) {
                    commitFoundationWorker(level, formation, mob, assignment, now);
                }
            } else if (assignment.state == MobTicketState.MOVING_TO_FEEDER
                    || assignment.state == MobTicketState.SUCTIONING) {
                if (!canAdmitUpperLayerWorker(level, formation, assignment.assemblySlot)) {
                    logRecruitmentEvent(formation, mob, "foundation_gate:feeder_blocked", assignment, null);
                    releaseRecruiter(level, formation, id, now);
                    continue;
                }
                IntakeWindow window = assignment.intakeWindow;
                boolean validWindow = canMobUseIntakeWindow(level, mob, window, formation);
                boolean validSuctionPose = validWindow
                        && isValidAssignedSuctionPose(mob.position(), window, formation, assignment);
                if (!validWindow || !validSuctionPose) {
                    logRecruitmentEvent(formation, mob,
                            !validWindow ? "feeder_arrival_invalid:window" : "feeder_arrival_invalid:suction_envelope",
                            assignment, window == null || window.feederCell() == null
                                    ? null : Vec3.atBottomCenterOf(window.feederCell()));
                    replanFeederRecruitment(level, formation, mob, assignment, now,
                            !validWindow ? "invalid_window" : "invalid_suction_envelope");
                    continue;
                }
                assignment.intakeWindowFailures = 0;
                assignment.intakeHazardFailures = 0;
                if (assignment.state == MobTicketState.MOVING_TO_FEEDER) {
                    transitionTicket(assignment, MobTicketState.SUCTIONING);
                    assignment.captureTicks = 0;
                    assignment.captureAttempts++;
                    assignment.controlledPull = true;
                    captureIntakeHold(mob, formation);
                    logRecruitmentEvent(formation, mob, "suction_started", assignment, null);
                } else {
                    if (!canAdmitUpperLayerWorker(level, formation, assignment.assemblySlot)) {
                        logRecruitmentEvent(formation, mob, "foundation_gate:building_blocked", assignment, null);
                        releaseRecruiter(level, formation, id, now);
                        continue;
                    }

                    assignment.captureCompleted = true;
                    copyAssemblyIntakeProvenance(formation, mob);
                    releaseRalliedHold(level, formation, id, mob);
                    assignment.captureTicks = 0;
                    assignment.captureRetryUntil = Long.MIN_VALUE;
                    assignment.controlledPull = false;
                    transitionTicket(assignment, MobTicketState.BUILDING);
                    removeRecruitmentGoal(mob, formation);
                    formation.lastRecruitmentProgressTick = now;
                    logRecruitmentEvent(formation, mob, "building", assignment, null);
                }
            }
        }
        for (Map.Entry<UUID, MobTicket> entry : new ArrayList<>(formation.mobTickets.entrySet())) {
            MobTicket assignment = entry.getValue();
            if (assignment == null || assignment.state != MobTicketState.ASSEMBLED) continue;
            Mob mob = getMob(level, entry.getKey());
            if (mob == null || !mob.isAlive() || mob.isRemoved()) {
                releaseRecruiter(level, formation, entry.getKey(), now);
                continue;
            }
            Vec3 exactTarget = Vec3.atBottomCenterOf(assignment.assemblySlot);
            boolean exactLock = isExactAssemblyLockValid(level, formation, mob, assignment, exactTarget);
            logPostFoundationDiagnostic(level, formation, entry.getKey(), assignment, mob, exactTarget,
                    exactLock, "assembled_check", now);
            if (exactLock) {
                continue;
            }
            if (formation.committedFoundationIds.contains(entry.getKey())) {

                recordAssemblyAcquisitionHold(formation.assemblyHoldPositions, entry.getKey(), exactTarget);
                pinAssemblyWorker(mob, exactTarget);
                continue;
            }
            boolean owner = isCurrentAssemblyOwner(level, formation, mob, assignment);
            boolean envelope = isWithinAssemblyAcceptanceEnvelope(mob.position(), assignment.assemblySlot);
            boolean retriesAvailable = assignment.assemblyRecoveryAttempts < RECRUITER_RETRY_WINDOWS;
            if (owner && envelope && retriesAvailable) {
                transitionTicket(assignment, MobTicketState.STAGING_AT_ASSEMBLY);
                assignment.assemblyStageStartedTick = now;
                assignment.assemblyLastProgressTick = now;
                assignment.assemblyBestDistanceSqr = exactAssemblyDistanceSqr(mob.position(), assignment.assemblySlot);
                holdRalliedWorker(mob, formation);
                logRecruitmentEvent(formation, mob, "assembled_demoted_to_staging", assignment, exactTarget);
            } else {
                logPostFoundationDiagnostic(level, formation, entry.getKey(), assignment, mob, exactTarget,
                        false, "assembled_invalid_release owner=" + owner + " envelope=" + envelope
                                + " retriesAvailable=" + retriesAvailable, now);
                logRecruitmentEvent(formation, mob, "assembled_invalid_release", assignment, exactTarget);
                releaseRecruiter(level, formation, entry.getKey(), now);
            }
        }
        for (Map.Entry<UUID, MobTicket> entry : new ArrayList<>(formation.mobTickets.entrySet())) {
            if (state(level).recruiterObservationsRemaining-- <= 0) break;
            MobTicket assignment = entry.getValue();
            if (assignment.state != MobTicketState.MOVING_TO_ASSEMBLY
                    && assignment.state != MobTicketState.MOVING_TO_FEEDER
                    && assignment.state != MobTicketState.STAGING_AT_ASSEMBLY) continue;
            Mob mob = getMob(level, entry.getKey());
            BlockPos destination = assignment.state == MobTicketState.MOVING_TO_ASSEMBLY
                    ? assemblyDestination(mob, assignment)
                    : assignment.state == MobTicketState.STAGING_AT_ASSEMBLY ? assignment.assemblySlot : feederCell(assignment);
            boolean targetSafe = assignment.state == MobTicketState.MOVING_TO_ASSEMBLY
                    ? isSafeAssemblyDestination(level, mob, destination, formation)
                    : assignment.state == MobTicketState.STAGING_AT_ASSEMBLY
                    ? isSafeAssemblySlot(level, mob, destination, formation)
                    : canMobUseIntakeWindow(level, mob, assignment.intakeWindow, formation);
            if (mob == null || !mob.isAlive() || mob.isRemoved() || destination == null || !targetSafe) {
                logRecruitmentEvent(formation, mob, "target_invalid", assignment, null);
                releaseRecruiter(level, formation, entry.getKey(), now);
                continue;
            }
            if (assignment.state == MobTicketState.MOVING_TO_FEEDER
                    && assignment.intakeWindow != null
                    && assignment.intakeWindow.feederApproach() != null
                    && !isAtIntakeApproach(mob.position(), assignment.intakeWindow)) {
                destination = assignment.intakeWindow.feederApproach();
            }
            double distance = mob.position().distanceToSqr(Vec3.atBottomCenterOf(destination));
            if (formation.movementDiagnostics++ < 160) {
                boolean navDone = mob.getNavigation().isDone();
                String authority = mob.goalSelector.getAvailableGoals().stream().filter(goal -> goal.isRunning())
                        .map(wrapped -> wrapped.getGoal().getClass().getSimpleName())
                        .collect(java.util.stream.Collectors.joining(","));
                SoundAttractMod.LOGGER.info("[LivingWallCoordinator] recruitment_progress_diagnostic bridge={} tick={} worker={} state={} pos={} target={} distance={} bestDistance={} distanceDelta={} navigationDone={} delta={} authority={} retries={} pathFailures={} releasePredicate={} observationAge={}",
                        formation.id, now, entry.getKey(), assignment.state, mob.position(),
                        Vec3.atBottomCenterOf(destination), distance, assignment.bestDistanceSqr,
                        assignment.bestDistanceSqr == Double.MAX_VALUE ? Double.NaN : distance - assignment.bestDistanceSqr,
                        navDone, mob.getDeltaMovement(), authority, assignment.retries,
                        assignment.pathFailures, shouldReleaseAfterStalledRecruitment(assignment.retries),
                        assignment.observationTick == Long.MIN_VALUE ? -1 : now - assignment.observationTick);
            }
            if (assignment.observationTick == Long.MIN_VALUE) {
                assignment.observationTick = now;
                assignment.bestDistanceSqr = distance;
            } else if (distance + RECRUITER_PROGRESS_EPSILON_SQR < assignment.bestDistanceSqr) {
                assignment.bestDistanceSqr = distance;
                assignment.observationTick = now;
            } else if (now - assignment.observationTick >= RECRUITER_OBSERVATION_TICKS) {
                assignment.observationTick = now;
                assignment.retries++;
                mob.getNavigation().stop();
                mob.setDeltaMovement(Vec3.ZERO);
                assignment.lastPathTick = Long.MIN_VALUE;
                assignment.navigationAccepted = false;
                assignment.navigationReported = false;
                if (shouldReleaseAfterStalledRecruitment(assignment.retries)) {
                    logPostFoundationDiagnostic(level, formation, entry.getKey(), assignment, mob,
                            Vec3.atBottomCenterOf(destination), false, "assembly_stage_stalled retries="
                                    + assignment.retries + " progressAge=" + (now - assignment.observationTick), now);
                    logRecruitmentEvent(formation, mob, assignment.state == MobTicketState.MOVING_TO_FEEDER
                            ? "intake_failed:stalled"
                            : assignment.state == MobTicketState.STAGING_AT_ASSEMBLY
                            ? "assembly_stage_stalled" : "mob_route_stalled:to_assembly", assignment, null);
                    releaseRecruiter(level, formation, entry.getKey(), now);
                }
            }
        }
        for (Map.Entry<UUID, MobTicket> entry : new ArrayList<>(formation.mobTickets.entrySet())) {
            MobTicket assignment = entry.getValue();
            boolean moving = assignment.state == MobTicketState.MOVING_TO_ASSEMBLY
                    || assignment.state == MobTicketState.MOVING_TO_FEEDER;
            boolean releaseDueToFailures = shouldReleaseAfterRecruitmentFailure(assignment.pathFailures,
                    assignment.retries >= RECRUITER_RETRY_WINDOWS)
                    || assignment.pathFailures >= RECRUITER_MAX_PATH_FAILURES * 2;
            if (!moving || !releaseDueToFailures) continue;
            Mob mob = getMob(level, entry.getKey());
            logRecruitmentEvent(formation, mob, "replaced_after_path_failures=" + assignment.pathFailures,
                    assignment, null);
            releaseRecruiter(level, formation, entry.getKey(), now);
        }
    }

    private static void logRecruitmentEvent(Formation formation, Mob mob, String event,
                                            MobTicket assignment, Vec3 target) {
        if (SoundAttractConfig.COMMON == null || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_FORMATION)
                || formation == null || assignment == null) return;
        BlockPos assignedTarget = assignment.state == MobTicketState.MOVING_TO_ASSEMBLY
                || assignment.state == MobTicketState.ASSEMBLED ? assignment.assemblySlot : feederCell(assignment);
        Vec3 destination = target != null ? target : assignedTarget == null ? mob == null ? Vec3.ZERO : mob.position()
                : Vec3.atBottomCenterOf(assignedTarget);
        RecruitmentLaneBlockers blockers = mob != null && mob.level() instanceof ServerLevel level
                ? findCrowdBlockers(level, formation, mob, destination) : RecruitmentLaneBlockers.EMPTY;
        String running = mob == null ? "missing" : mob.goalSelector.getAvailableGoals().stream().filter(goal -> goal.isRunning())
                .map(wrapped -> wrapped.getGoal().getClass().getSimpleName() + "@" + wrapped.getPriority())
                .collect(java.util.stream.Collectors.joining(","));
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] MobTicket bridge={} event={} mob={} state={} pos={} target={} assemblySlot={} retries={} pathFailures={} accepted={}",
                formation.id, event, mob == null ? "missing" : mob.getUUID(), assignment.state,
                mob == null ? "missing" : mob.position(), destination, assignment.assemblySlot,
                assignment.retries, assignment.pathFailures, assignment.navigationAccepted);
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Recruiter movement bridge={} mob={} running={} horizontalCollision={} onGround={} pushable={} delta={} distanceSqr={} assignedBlockers={} assignedBlockerIds={} unassignedBlockers={} unassignedBlockerIds={}",
                formation.id, mob == null ? "missing" : mob.getUUID(), running,
                mob != null && mob.horizontalCollision, mob != null && mob.onGround(), mob != null && mob.isPushable(),
                mob == null ? "missing" : mob.getDeltaMovement(),
                mob == null ? "missing" : mob.position().distanceToSqr(destination), blockers.assigned().size(),
                blockerIds(blockers.assigned()), blockers.unassigned().size(), blockerIds(blockers.unassigned()));
    }

    private static String blockerIds(List<Mob> blockers) {
        return blockers.stream().map(blocker -> blocker.getUUID().toString())
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static void guardRecruitmentLane(ServerLevel level, Formation formation, long now) {
        if (level == null || formation == null || formation.collapsed
                || (formation.phase != Phase.SCAN && formation.phase != Phase.ASSEMBLE)
                || formation.lastLaneGuardTick >= 0L
                && now - formation.lastLaneGuardTick < RECRUITMENT_LANE_GUARD_INTERVAL_TICKS) return;
        formation.lastLaneGuardTick = now;
        int cleared = 0;
        Set<UUID> logged = new HashSet<>();
        for (Map.Entry<UUID, MobTicket> entry : formation.mobTickets.entrySet()) {
            if (cleared >= RECRUITMENT_LANE_BLOCKER_LIMIT) break;
            MobTicket assignment = entry.getValue();
            if (assignment.state != MobTicketState.MOVING_TO_ASSEMBLY
                    && assignment.state != MobTicketState.MOVING_TO_FEEDER
                    && assignment.state != MobTicketState.SUCTIONING) continue;
            Mob worker = getMob(level, entry.getKey());
            Vec3 destination = recruitmentDestination(assignment);
            if (worker == null || destination == null) continue;
            for (Mob blocker : findCrowdBlockers(level, formation, worker, destination).unassigned()) {
                if (cleared >= RECRUITMENT_LANE_BLOCKER_LIMIT) break;
                if (!logged.add(blocker.getUUID())) continue;
                Vec3 away = awayFromRecruitmentLane(worker.position(), destination, blocker.position());
                Vec3 nudgeTarget = blocker.position().add(away.x * 0.5D, 0.0D, away.z * 0.5D);
                if (com.example.soundattract.pathfinding.HazardClassifier.isDangerous(level, blocker, nudgeTarget)) continue;
                blocker.setTarget(null);
                blocker.setAggressive(false);
                Vec3 velocity = blocker.getDeltaMovement();
                blocker.setDeltaMovement(Mth.clamp(velocity.x + away.x * RECRUITMENT_LANE_NUDGE, -0.2D, 0.2D),
                        velocity.y, Mth.clamp(velocity.z + away.z * RECRUITMENT_LANE_NUDGE, -0.2D, 0.2D));
                cleared++;
            }
        }
        if (cleared > 0 && DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_FORMATION)) {
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Recruitment lane cleared bridge={} blockers={} tick={}",
                    formation.id, cleared, now);
        }
    }

    private static Vec3 recruitmentDestination(MobTicket assignment) {
        if (assignment == null) return null;
        if (assignment.state == MobTicketState.MOVING_TO_ASSEMBLY
                || assignment.state == MobTicketState.STAGING_AT_ASSEMBLY
                || assignment.state == MobTicketState.ASSEMBLED) {
            return assignment.assemblySlot == null ? null : Vec3.atBottomCenterOf(assignment.assemblySlot);
        }
        BlockPos feeder = feederCell(assignment);
        if (feeder == null) return null;
        if (assignment.intakeWindow != null && assignment.intakeWindow.feederApproach() != null
                && !isAtIntakeApproach(Vec3.atBottomCenterOf(feeder), assignment.intakeWindow)) {
            return Vec3.atBottomCenterOf(assignment.intakeWindow.feederApproach());
        }
        return Vec3.atBottomCenterOf(feeder);
    }

    private record RecruitmentLaneBlockers(List<Mob> assigned, List<Mob> unassigned) {
        private static final RecruitmentLaneBlockers EMPTY = new RecruitmentLaneBlockers(List.of(), List.of());
    }

    private static RecruitmentLaneBlockers findCrowdBlockers(ServerLevel level, Formation formation,
                                                              Mob worker, Vec3 destination) {
        if (level == null || formation == null || worker == null || destination == null) {
            return RecruitmentLaneBlockers.EMPTY;
        }
        Vec3 start = worker.position();
        AABB corridor = new AABB(Math.min(start.x, destination.x), Math.min(start.y, destination.y),
                Math.min(start.z, destination.z), Math.max(start.x, destination.x),
                Math.max(start.y, destination.y), Math.max(start.z, destination.z)).inflate(RECRUITMENT_LANE_CLEARANCE);
        List<Mob> assigned = new ArrayList<>();
        List<Mob> unassigned = new ArrayList<>();
        PerLevelState levelState = state(level);
        for (Mob candidate : level.getEntitiesOfClass(Mob.class, corridor,
                mob -> mob != worker && mob.isAlive() && !mob.isRemoved() && isEligible(mob))) {
            if (!isNearRecruitmentSegment(start, destination, candidate.position(),
                    RECRUITMENT_LANE_CLEARANCE + candidate.getBbWidth() * 0.5D)) continue;
            UUID id = candidate.getUUID();
            boolean tracked = formation.mobTickets.containsKey(id) || formation.members.contains(id)
                    || formation.pendingBuilderIds.contains(id) || formation.reserved.contains(id)
                    || formation.builderReservations.contains(id) || levelState.mobOwners.containsKey(id);
            (tracked ? assigned : unassigned).add(candidate);
        }
        return new RecruitmentLaneBlockers(List.copyOf(assigned), List.copyOf(unassigned));
    }

    static boolean isNearRecruitmentSegment(Vec3 start, Vec3 destination, Vec3 point, double clearance) {
        if (start == null || destination == null || point == null || clearance < 0.0D) return false;
        Vec3 segment = destination.subtract(start);
        double lengthSqr = segment.lengthSqr();
        double progress = lengthSqr < 1.0E-8D ? 0.0D
                : Mth.clamp(point.subtract(start).dot(segment) / lengthSqr, 0.0D, 1.0D);
        Vec3 closest = start.add(segment.scale(progress));
        return point.distanceToSqr(closest) <= clearance * clearance;
    }

    private static Vec3 awayFromRecruitmentLane(Vec3 start, Vec3 destination, Vec3 point) {
        Vec3 segment = destination.subtract(start);
        Vec3 horizontal = new Vec3(segment.x, 0.0D, segment.z);
        double lengthSqr = horizontal.lengthSqr();
        double progress = lengthSqr < 1.0E-8D
                ? 0.0D : Mth.clamp(new Vec3(point.x - start.x, 0.0D, point.z - start.z).dot(horizontal) / lengthSqr,
                0.0D, 1.0D);
        Vec3 closest = start.add(horizontal.scale(progress));
        Vec3 away = new Vec3(point.x - closest.x, 0.0D, point.z - closest.z);
        if (away.lengthSqr() < 1.0E-8D) {
            away = new Vec3(-horizontal.z, 0.0D, horizontal.x);
        }
        return away.normalize();
    }

    private static void logFormationState(ServerLevel level, Formation formation, long now, String reason) {
        if (SoundAttractConfig.COMMON == null || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_FORMATION)) return;
        if (!reason.startsWith("collapse") && !reason.startsWith("release:")
                && !reason.equals("planner_unavailable") && !reason.equals("phase_transition")
                && formation.lastDiagnosticTick >= 0
                && now - formation.lastDiagnosticTick < FORMATION_DIAGNOSTIC_INTERVAL_TICKS) return;
        formation.lastDiagnosticTick = now;
        int approaching = 0;
        int waiting = 0;
        int released = 0;
        int rallied = 0;
        int nearBase = 0;
        int pathFailures = 0;
        int reachableSurveys = 0;
        for (PathSurvey survey : formation.pathSurveys.values()) if (survey.reachable() && survey.expiresAt() > now) reachableSurveys++;
        Vec3 pivot = Vec3.atBottomCenterOf(formation.pivot);
        for (Map.Entry<UUID, MobTicket> entry : formation.mobTickets.entrySet()) {
            MobTicket assignment = entry.getValue();
            if (assignment.state == MobTicketState.MOVING_TO_ASSEMBLY
                    || assignment.state == MobTicketState.MOVING_TO_FEEDER
                    || assignment.state == MobTicketState.SUCTIONING) approaching++;
            else if (assignment.state == MobTicketState.ASSEMBLED) rallied++;
            else if (assignment.state == MobTicketState.BUILDING) waiting++;
            else if (assignment.state == MobTicketState.RELEASED) released++;
            pathFailures += assignment.pathFailures;
            Mob mob = getMob(level, entry.getKey());
            if (mob != null && mob.position().distanceToSqr(pivot)
                    <= STRUCTURAL_RECRUITMENT_RADIUS * STRUCTURAL_RECRUITMENT_RADIUS) nearBase++;
        }
        Mob planner = resolvePlannerMob(level, formation);
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Bridge {} {} phase={} age={} planner={} plannerAlive={} pivot={} anchor={} scenario={} kind={} spanEnd={} direct={} roster={} recruiters={} approaching={} rallied={} waiting={} released={} nearBase={} staged={} pending={} members={} assembled={} required={} layers={} columns={} oneColumn={} available={} usable={} bodyLimit={} width={} height={} chain={} verticalTouch={} pillarStep={} surveyed={} reachable={} pathFailures={}",
                formation.id, reason, formation.phase, now - formation.createdTick, formation.plannerId,
                plannerSessionAlive(formation, planner != null && planner.isAlive() && !planner.isRemoved()), formation.pivot, formation.anchor,
                formation.plan.scenario(), bridgeKind(formation.plan.scenario()), formation.spanEnd, formation.directSpan,
                formation.localRoster.size(), formation.mobTickets.size(),
                approaching, rallied, waiting, released, nearBase, rallied,
                formation.pendingBuilderIds.size(), formation.members.size(), formation.assembledSlots,
                formation.requiredBodies, formation.layerCount, formation.columnCount, formation.oneColumnBodies,
                formation.availableBodies, formation.usableBodies, formation.bodyLimit, formation.minimumWidth,
                formation.minimumHeight, formation.chainLength, formation.verticalTouchStep, formation.pillarStep,
                formation.pathSurveys.size(), reachableSurveys, pathFailures);
    }

    private static Mob resolvePlannerMob(ServerLevel level, Formation formation) {
        if (formation == null) return null;
        Mob planner = formation.leaderRef.get();
        if (planner != null && planner.isAlive() && !planner.isRemoved()) return planner;
        if (level == null || formation.plannerId == null) return null;
        Mob refreshed = getMob(level, formation.plannerId);
        return refreshed != null && refreshed.isAlive() && !refreshed.isRemoved() ? refreshed : null;
    }

    private static boolean plannerSessionAlive(Formation formation, boolean plannerEntityAlive) {
        if (plannerEntityAlive) return true;
        if (formation == null || formation.collapsed || formation.phase != Phase.ASSEMBLE) return false;
        return !formation.claimedWorkerIds.isEmpty()
                || !formation.pendingBuilderIds.isEmpty()
                || !formation.members.isEmpty()
                || !formation.mobTickets.isEmpty();
    }

    private static void transition(Formation formation, Phase next, long now, String reason) {
        if (formation == null || formation.phase == next || isMovementPhase(next)) {
            return;
        }
        applyTransition(formation, next, now, reason);
    }

    private static boolean transitionToMovement(ServerLevel level, Formation formation, Phase next,
                                                long now, String reason) {
        if (!isMovementPhase(next) || !fullChainMovementReady(level, formation)) return false;
        applyTransition(formation, next, now, reason);
        releaseUncommittedRecruiters(level, formation);
        return true;
    }

    private static boolean isMovementPhase(Phase phase) {
        return phase == Phase.ROTATE || phase == Phase.ANCHOR || phase == Phase.TRAVERSE;
    }

    private static boolean fullChainMovementCountsReady(int memberCount, int assembledSlotCount,
                                                        int structuralBuilderCount, int requiredBodies) {
        return requiredBodies > 0 && memberCount >= requiredBodies
                && assembledSlotCount >= requiredBodies && structuralBuilderCount >= requiredBodies;
    }

    static boolean fullChainMovementReady(int memberCount, int assembledSlotCount,
                                          int structuralBuilderCount, int requiredBodies,
                                          boolean chainIntegrityValid) {
        return fullChainMovementCountsReady(memberCount, assembledSlotCount, structuralBuilderCount, requiredBodies)
                && chainIntegrityValid;
    }

    private static boolean fullChainMovementReady(ServerLevel level, Formation formation) {
        return level != null && formation != null
                && firstMissingStructuralLayer(level, formation) < 0
                && fullChainMovementReady(formation.members.size(), formation.assembledSlots,
                formation.structuralBuilderIds.size(), formation.requiredBodies,
                structureHasContact(level, formation, formation.requiredBodies));
    }

    private static void applyTransition(Formation formation, Phase next, long now, String reason) {
        if (formation == null || formation.phase == next) return;
        Phase previous = formation.phase;
        formation.phase = next;
        formation.phaseStartTick = now;
        formation.lastLoggedPhase = next;
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_FORMATION)) {
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Bridge {} phase_transition from={} to={} reason={} tick={} assembled={} members={} required={}",
                    formation.id, previous, next, reason, now, formation.assembledSlots,
                    formation.members.size(), formation.requiredBodies);
        }
    }

    private static void releaseRecruiter(ServerLevel level, Formation formation, UUID id, long now) {
        if (formation != null && formation.committedFoundationIds.contains(id)) {
            return;
        }
        Mob mob = getMob(level, id);
        releaseRalliedHold(level, formation, id, mob);
        releaseAssemblySlot(formation, id);
        if (mob != null) removeRecruitmentGoal(mob, formation);
        MobTicket assignment = formation.mobTickets.get(id);
        if (assignment != null) {
            transitionTicket(assignment, MobTicketState.RELEASED);
            assignment.releaseUntil = now + RECRUITER_RELEASE_TICKS;
            formation.recruitmentBlacklist.put(id, now + RECRUITER_PATH_FAILURE_BLACKLIST_TICKS);
        }
        formation.assemblyIntakeProvenance.remove(id);
        formation.recruitmentArrivalReports.remove(id);
        releaseBridgeOwnership(level, formation, id);
        clearRecruitmentHoldState(formation, id);
        formation.reserved.remove(id);
    }

    private static void logPostFoundationDiagnostic(ServerLevel level, Formation formation, UUID id,
                                                    MobTicket assignment, Mob mob, Vec3 target,
                                                    boolean exactLock, String predicate, long now) {
        if (formation == null || assignment == null || SoundAttractConfig.COMMON == null
                || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_FORMATION)
                || formation.assemblyRejectionDiagnostics++ >= 96) return;
        UUID owner = state(level).mobOwners.get(id);
        BlockPos slot = assignment.assemblySlot;
        UUID slotOwner = formation.assemblySlotOwners.get(slot);
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] post_foundation_diagnostic bridge={} tick={} worker={} alive={} removed={} owner={} ticket={} committed={} slot={} slotOwner={} pos={} target={} phase={} members={} builders={} assembled={} required={} retries={} pathFailures={} stageAge={} observationAge={} exactLock={} predicate={} requestRevision={} intentRevision={}",
                formation.id, now, id, mob != null && mob.isAlive(), mob == null || mob.isRemoved(), owner,
                assignment.state, formation.committedFoundationIds.contains(id), slot, slotOwner,
                mob == null ? null : mob.position(), target, formation.phase, formation.members.size(),
                formation.structuralBuilderIds.size(), formation.assembledSlots, formation.requiredBodies,
                assignment.retries, assignment.pathFailures,
                assignment.assemblyStageStartedTick == Long.MIN_VALUE ? -1 : now - assignment.assemblyStageStartedTick,
                assignment.observationTick == Long.MIN_VALUE ? -1 : now - assignment.observationTick,
                exactLock, predicate, formation.request == null ? -1 : formation.request.revision(),
                formation.intentToken == null ? -1 : formation.intentToken.scanRevision);
    }

    private static void holdRalliedWorker(Mob mob, Formation formation) {
        if (mob == null || formation == null) return;
        applyControlledHold(mob, formation, true);
    }

    private static void releaseRalliedHold(ServerLevel level, Formation formation, UUID id, Mob mob) {
        if (formation == null || id == null) return;
        boolean held = formation.originalGravity.containsKey(id)
                || formation.originalPersistence.containsKey(id)
                || formation.originalNoPhysics.containsKey(id)
                || formation.originalTargets.containsKey(id);
        if (mob != null && mob.isAlive() && !mob.isRemoved()) {
            Boolean gravity = formation.originalGravity.get(id);
            mob.setNoGravity(gravity != null && !gravity);
            Boolean persistence = formation.originalPersistence.get(id);
            if (persistence != null) ((MobAccessor) mob).setPersistenceRequiredFlag(persistence);
            mob.noPhysics = false;
            mob.getNavigation().stop();
            mob.setDeltaMovement(Vec3.ZERO);
            mob.fallDistance = 0.0F;
            clearControlledMarker(mob, formation.id);
            if (held) {
                restoreTarget(level, formation, id, mob, false);
            }
        }
        clearRecruitmentHoldState(formation, id);
    }

    private static void clearRecruitmentHoldState(Formation formation, UUID id) {
        if (formation == null || id == null) return;
        formation.originalGravity.remove(id);
        formation.originalPersistence.remove(id);
        formation.originalNoPhysics.remove(id);
        formation.originalTargets.remove(id);
        formation.assemblyHoldPositions.remove(id);
    }

    private static UUID getMarkerUuid(CompoundTag tag, String key) {
        try {
            return com.example.soundattract.NbtCompat.getUuid(tag, key);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void markControlledMob(Mob mob, Formation formation) {
        if (mob == null || formation == null) return;
        UUID id = mob.getUUID();
        CompoundTag marker = com.example.soundattract.component.LivingBridgeEntityData.get(mob).getCompoundOrEmpty(CONTROL_TAG);
        com.example.soundattract.NbtCompat.putUuid(marker, CONTROL_SESSION, formation.id);
        marker.putBoolean(CONTROL_ORIGINAL_GRAVITY,
                formation.originalGravity.getOrDefault(id, !mob.isNoGravity()));
        marker.putBoolean(CONTROL_ORIGINAL_NO_PHYSICS, false);
        marker.putBoolean(CONTROL_ORIGINAL_PERSISTENCE,
                formation.originalPersistence.getOrDefault(id, ((MobAccessor) mob).isPersistenceRequiredFlag()));
        UUID target = formation.originalTargets.get(id);
        marker.putBoolean(CONTROL_HAS_TARGET, target != null);
        if (target != null) com.example.soundattract.NbtCompat.putUuid(marker, CONTROL_ORIGINAL_TARGET, target);
        com.example.soundattract.component.LivingBridgeEntityData.get(mob).put(CONTROL_TAG, marker);
    }

    private static void applyControlledHold(Mob mob, Formation formation, boolean markMarker) {
        if (mob == null || formation == null) return;
        UUID id = mob.getUUID();
        restoreSnapshotFromMarkerIfPresent(mob, formation);
        if (!formation.originalGravity.containsKey(id)
                || !formation.originalPersistence.containsKey(id)
                || !formation.originalNoPhysics.containsKey(id)
                || !formation.originalTargets.containsKey(id)) {
            formation.originalGravity.putIfAbsent(id, !mob.isNoGravity());
            formation.originalPersistence.putIfAbsent(id, ((MobAccessor) mob).isPersistenceRequiredFlag());
            formation.originalNoPhysics.putIfAbsent(id, false);
            formation.originalTargets.putIfAbsent(id, mob.getTarget() == null ? null : mob.getTarget().getUUID());
        }
        if (markMarker) markControlledMob(mob, formation);
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.setNoGravity(true);
        mob.noPhysics = true;
        mob.setPersistenceRequired();
        mob.getNavigation().stop();
        mob.setDeltaMovement(Vec3.ZERO);
        mob.fallDistance = 0.0F;
    }

    private static void restoreSnapshotFromMarkerIfPresent(Mob mob, Formation formation) {
        if (mob == null || formation == null) return;
        CompoundTag root = com.example.soundattract.component.LivingBridgeEntityData.get(mob);
        if (!root.contains(CONTROL_TAG)) return;
        CompoundTag marker = root.getCompoundOrEmpty(CONTROL_TAG);
        if (getMarkerUuid(marker, CONTROL_SESSION) == null || !formation.id.equals(getMarkerUuid(marker, CONTROL_SESSION))) return;
        UUID id = mob.getUUID();
        if (marker.contains(CONTROL_ORIGINAL_GRAVITY)) {
            formation.originalGravity.put(id, marker.getBooleanOr(CONTROL_ORIGINAL_GRAVITY, false));
        }
        if (marker.contains(CONTROL_ORIGINAL_PERSISTENCE)) {
            formation.originalPersistence.put(id, marker.getBooleanOr(CONTROL_ORIGINAL_PERSISTENCE, false));
        }
        if (marker.contains(CONTROL_ORIGINAL_NO_PHYSICS)) {
            formation.originalNoPhysics.put(id, marker.getBooleanOr(CONTROL_ORIGINAL_NO_PHYSICS, false));
        }
        if (marker.contains(CONTROL_HAS_TARGET)) {
            formation.originalTargets.put(id,
                    marker.getBooleanOr(CONTROL_HAS_TARGET, false) && getMarkerUuid(marker, CONTROL_ORIGINAL_TARGET) != null
                            ? getMarkerUuid(marker, CONTROL_ORIGINAL_TARGET) : null);
        }
    }

    private static void clearControlledMarker(Mob mob, UUID session) {
        if (mob == null) return;
        CompoundTag root = com.example.soundattract.component.LivingBridgeEntityData.get(mob);
        if (!root.contains(CONTROL_TAG)) return;
        CompoundTag marker = root.getCompoundOrEmpty(CONTROL_TAG);
        if (session == null || getMarkerUuid(marker, CONTROL_SESSION) == null || session.equals(getMarkerUuid(marker, CONTROL_SESSION))) {
            root.remove(CONTROL_TAG);
        }
    }

    private static boolean hasLiveFormation(ServerLevel level, UUID session) {
        if (level == null || session == null) return false;
        PerLevelState state = LEVELS.get(level.dimension().identifier());
        if (state == null) return false;
        synchronized (state.formations) {
            Formation formation = state.formations.get(session);
            return formation != null && !formation.collapsed;
        }
    }

    private static void recoverStaleControlledMob(ServerLevel level, Mob mob, String reason) {
        if (level == null || mob == null || mob.isRemoved() || !mob.isAlive()) return;
        CompoundTag root = com.example.soundattract.component.LivingBridgeEntityData.get(mob);
        if (!"entity_join".equals(reason)) {
            boolean recovered = advanceControlledSafeRecovery(level, mob);
            if (recovered || completeSafeRecoveryAtPersistedEndpoint(level, mob)) {
                root.remove("livingBridgeRecoveryRequiredLogged");
            }
        }
        if (mustPreserveDurableRouteOwner(level, mob)) {
            holdDurableRouteOwner(mob);
            if (!root.getBooleanOr("livingBridgeRecoveryRequiredLogged", false)) {
                root.putBoolean("livingBridgeRecoveryRequiredLogged", true);
                SoundAttractMod.LOGGER.warn("[LivingWallCoordinator] durable_route_owner_held reason={} mob={} dimension={} pos={} resolution=restore_trusted_formation_or_operator_safe_recovery",
                        reason, mob.getUUID(), level.dimension().identifier(), mob.position());
            }
            return;
        }
        boolean hasControl = root.contains(CONTROL_TAG);
        boolean hasRouteControl = root.contains("livingBridgeControl");
        if (!hasControl && !hasRouteControl && !mob.isNoGravity() && !mob.noPhysics) return;

        UUID session = null;
        boolean originalGravity = true;
        boolean originalNoPhysics = false;
        boolean originalPersistence = false;
        LivingEntity target = null;

        if (hasControl) {
            CompoundTag marker = root.getCompoundOrEmpty(CONTROL_TAG);
            session = getMarkerUuid(marker, CONTROL_SESSION) != null ? getMarkerUuid(marker, CONTROL_SESSION) : null;
            if (hasLiveFormation(level, session)) return;
            originalGravity = marker.contains(CONTROL_ORIGINAL_GRAVITY)
                    ? marker.getBooleanOr(CONTROL_ORIGINAL_GRAVITY, false) : true;
            originalNoPhysics = marker.contains(CONTROL_ORIGINAL_NO_PHYSICS)
                    && marker.getBooleanOr(CONTROL_ORIGINAL_NO_PHYSICS, false);
            originalPersistence = marker.contains(CONTROL_ORIGINAL_PERSISTENCE)
                    && marker.getBooleanOr(CONTROL_ORIGINAL_PERSISTENCE, false);
            if (marker.getBooleanOr(CONTROL_HAS_TARGET, false) && getMarkerUuid(marker, CONTROL_ORIGINAL_TARGET) != null) {
                var entity = level.getEntity(getMarkerUuid(marker, CONTROL_ORIGINAL_TARGET));
                if (entity instanceof LivingEntity living && living.isAlive()) target = living;
            }
            root.remove(CONTROL_TAG);
        } else {
            if (mob.hasCustomName()) {
                originalPersistence = true;
            }
        }

        mob.setNoGravity(!originalGravity);
        mob.noPhysics = originalNoPhysics;
        ((MobAccessor) mob).setPersistenceRequiredFlag(originalPersistence);
        mob.setTarget(target);
        mob.setAggressive(target != null);
        mob.getNavigation().stop();
        mob.setDeltaMovement(Vec3.ZERO);
        mob.fallDistance = 0.0F;
        root.remove("livingBridgeControl");
        root.remove("livingBridgeSafeBaseRouteActive");
        root.remove("livingBridgeRecoveryRequiredLogged");
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_RECOVERY)) {
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] stale_marker_restored reason={} mob={} session={} pos={}",
                    reason, mob.getUUID(), session, mob.position());
        }
    }

    private static boolean isAtAssemblySlot(Vec3 position, BlockPos cell) {
        return exactAssemblyDistanceSqr(position, cell)
                <= CONTROLLED_INTAKE_SETTLE_DISTANCE * CONTROLLED_INTAKE_SETTLE_DISTANCE;
    }

    static boolean isWithinAssemblyAcceptanceEnvelope(Vec3 position, BlockPos cell) {
        if (position == null || cell == null) return false;
        Vec3 center = Vec3.atBottomCenterOf(cell);
        double dx = position.x - center.x;
        double dz = position.z - center.z;
        return dx * dx + dz * dz <= ASSEMBLY_ACCEPTANCE_RADIUS_SQR
                && Math.abs(position.y - center.y) <= ASSEMBLY_ACCEPTANCE_VERTICAL_TOLERANCE;
    }

    static double exactAssemblyDistanceSqr(Vec3 position, BlockPos cell) {
        if (position == null || cell == null) return Double.POSITIVE_INFINITY;
        return position.distanceToSqr(Vec3.atBottomCenterOf(cell));
    }

    private static boolean isCurrentAssemblyOwner(ServerLevel level, Formation formation, Mob mob, MobTicket assignment) {
        if (level == null || formation == null || mob == null || assignment == null || assignment.assemblySlot == null) {
            return false;
        }
        if (!formation.ticketGoals.containsKey(mob.getUUID())) return false;
        if (!formation.id.equals(state(level).mobOwners.get(mob.getUUID()))) return false;
        return mob.getUUID().equals(formation.assemblySlotOwners.get(assignment.assemblySlot));
    }

    private static SafeBaseNavigationReservation findSafeBaseNavigationRoute(ServerLevel level,
                                                                              Formation formation,
                                                                              Mob mob, int slot) {
        if (level == null || formation == null || formation.request == null || formation.plan == null
                || mob == null || formation.request.sourceCenter() == null
                || formation.request.sourceBucket() == null
                || level.getServer() == null || !level.getServer().isSameThread()) return null;
        if (!safeBaseBuilderAuthorization(level, mob, formation, slot, slot,
                formation.request.revision(), formation.phase)) {
            logSafeBaseEarlyRejection(level, formation, mob, slot, "builder_authorization");
            return null;
        }
        SafeBaseNavigationReservation existing = formation.safeBaseNavigation.get(mob.getUUID());
        if (existing != null && existing.routeRevision() == formation.request.revision()
                && safeBaseReservationBoundToBuilder(level, mob, formation, existing)) return existing;
        if (!mob.onGround() && !repairSafeBaseGroundState(level, formation, mob, slot)) {
            logSafeBaseEarlyRejection(level, formation, mob, slot, "ground_state_repair");
            return null;
        }
        boolean diagnosticEnabled = DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_RECOVERY);
        long requestRevision = formation.request.revision();
        boolean diagnosticSuppressed = diagnosticEnabled
                && Long.valueOf(requestRevision).equals(
                formation.lastReportedSafeBaseStagingDiagnostics.get(mob.getUUID()));
        SafeBaseStagingDiagnostics diagnostics = diagnosticEnabled && !diagnosticSuppressed
                ? new SafeBaseStagingDiagnostics() : null;
        Vec3 forward = LivingBridgeSafeBaseRoute.verifiedForward(
                formation.plan.pivot(), formation.plan.spanEnd(), formation.plan.forward());
        if (forward == null) return null;
        Vec3 pivot = Vec3.atBottomCenterOf(formation.plan.pivot());
        Vec3 sourceCenter = formation.request.sourceCenter();
        BlockPos sourceBucket = formation.request.sourceBucket();
        double margin = mob.getBbWidth() * 0.5D
                * (Math.abs(forward.x) + Math.abs(forward.z)) + APPROACH_LANE_SPACING * 0.5D;
        Vec3 sourceDirection = new Vec3(-forward.x, 0.0D, -forward.z);
        Vec3 targetCenter = pivot.add(sourceDirection.scale(margin + 1.0D));
        List<BlockPos> candidates = new ArrayList<>();
        for (int yOffset = -2; yOffset <= 2; yOffset++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (diagnostics != null) diagnostics.surfaceProbes++;
                    BlockPos candidate = resolveLocalSafeSurface(level,
                            Mth.floor(targetCenter.x) + dx, Mth.floor(targetCenter.z) + dz,
                            Mth.floor(targetCenter.y) + yOffset);
                    if (candidate == null) {
                        if (diagnostics != null) {
                            BlockPos requested = new BlockPos(
                                    Mth.floor(targetCenter.x) + dx, Mth.floor(targetCenter.y) + yOffset,
                                    Mth.floor(targetCenter.z) + dz);
                            diagnostics.record("surface_unresolved", requested);
                        }
                        continue;
                    }
                    if (diagnostics != null) diagnostics.resolvedCandidates++;
                    if (formation.plan.hazardCells().contains(candidate)) {
                        if (diagnostics != null) diagnostics.record("planned_hazard", candidate);
                        continue;
                    }
                    if (!com.example.soundattract.pathfinding.HazardClassifier.isSafeGround(level, candidate)) {
                        if (diagnostics != null) diagnostics.record("unsafe_ground", candidate);
                        continue;
                    }
                    if (isNearHazard(level, candidate, formation.plan.hazardCells(), forward)) {
                        if (diagnostics != null) diagnostics.record("near_hazard", candidate);
                        continue;
                    }
                    if (diagnostics != null) diagnostics.safeCandidates++;
                    candidates.add(candidate);
                }
            }
        }
        candidates.sort(Comparator.comparingDouble((BlockPos candidate) ->
                        mob.distanceToSqr(Vec3.atBottomCenterOf(candidate)))
                .thenComparingLong(BlockPos::asLong));
        if (diagnostics != null) {
            diagnostics.cappedCandidates += Math.max(0, candidates.size() - 32);
        }
        List<Integer> attemptIndices = LivingBridgeSafeBaseRoute.boundedCandidateIndices(candidates.size(), 32);
        for (int candidateIndex : attemptIndices) {
            BlockPos candidate = candidates.get(candidateIndex);
            if (formation.rejectedSafeBaseStaging.getOrDefault(mob.getUUID(), Set.of()).contains(candidate)) {
                if (diagnostics != null) diagnostics.record("prior_candidate", candidate);
                continue;
            }
            if (diagnostics != null) diagnostics.pathAttempts++;
            var path = mob.getNavigation().createPath(candidate, 0);
            if (path == null) {
                if (diagnostics != null) diagnostics.record("null_path", candidate);
                continue;
            }
            if (!path.canReach()) {
                if (diagnostics != null) diagnostics.record("unreachable_path", candidate);
                continue;
            }
            if (path.getNodeCount() == 0) {
                if (diagnostics != null) diagnostics.record("zero_node_path", candidate);
                continue;
            }
            if (path.getTarget() == null || path.getTarget().distSqr(candidate) > 1.0D) {
                if (diagnostics != null) diagnostics.record("wrong_endpoint", path.getTarget());
                continue;
            }
            List<BlockPos> nodes = new ArrayList<>(path.getNodeCount());
            for (int index = 0; index < path.getNodeCount(); index++) {
                nodes.add(path.getNode(index).asBlockPos());
            }
            String worldFingerprint = routeWorldFingerprint(level, nodes);
            SafeBaseNavigationReservation reservation = new SafeBaseNavigationReservation(
                    formation.id, mob.getUUID(), formation.members.indexOf(mob.getUUID()), slot,
                    formation.phase, formation.request.revision(), pivot, forward, sourceCenter,
                    sourceBucket, candidate.immutable(), nodes, path, level.dimension().identifier(),
                    mob.position(), mob.getBoundingBox(), mob.getPose(), worldFingerprint);
            LivingBridgeSafeBaseRoute.Result validation = safeBasePathValidation(
                    level, mob, formation, reservation, path);
            if (validation.safe()) return reservation;
            if (diagnostics != null) diagnostics.record(validation.rejection().name(), validation.firstUnsafeCell());
        }
        if (diagnostics != null) {
            diagnostics.log(level, formation, mob, slot, requestRevision, pivot, sourceBucket);
            formation.lastReportedSafeBaseStagingDiagnostics.put(mob.getUUID(), requestRevision);
        }
        return null;
    }

    private static void logSafeBaseEarlyRejection(ServerLevel level, Formation formation, Mob mob,
                                                  int slot, String reason) {
        if (level == null || formation == null || mob == null
                || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_RECOVERY)) return;
        long revision = formation.request == null ? Long.MIN_VALUE : formation.request.revision();
        if (Long.valueOf(revision).equals(formation.lastReportedSafeBaseStagingDiagnostics.get(mob.getUUID()))) return;
        formation.lastReportedSafeBaseStagingDiagnostics.put(mob.getUUID(), revision);
        BlockPos feet = mob.blockPosition();
        BlockPos support = feet.below();
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] safe_base_staging_early_rejection formation={} worker={} reason={} slot={} memberIndex={} members={} assembledSlots={} activeStructuralSlot={} structuralBuilder={} phase={} owner={} onGround={} noGravity={} noPhysics={} pos={} feetState={} supportState={} safeGround={} nearHazard={}",
                formation.id, mob.getUUID(), reason, slot, formation.members.indexOf(mob.getUUID()),
                formation.members.size(), formation.assembledSlots, formation.activeStructuralSlot,
                formation.structuralBuilderIds.contains(mob.getUUID()), formation.phase,
                state(level).mobOwners.get(mob.getUUID()), mob.onGround(), mob.isNoGravity(), mob.noPhysics,
                mob.position(), level.getBlockState(feet), level.getBlockState(support),
                com.example.soundattract.pathfinding.HazardClassifier.isSafeGround(level, feet),
                isNearHazard(level, feet, formation.plan.hazardCells(), formation.plan.forward()));
    }

    private static boolean repairSafeBaseGroundState(ServerLevel level, Formation formation, Mob mob, int slot) {
        if (level == null || formation == null || mob == null
                || !(mob.getNavigation() instanceof GroundPathNavigation)
                || level.getServer() == null || !level.getServer().isSameThread()
                || !safeBaseBuilderAuthorization(level, mob, formation, slot, slot,
                formation.request.revision(), formation.phase)) return false;
        BlockPos feet = mob.blockPosition();
        BlockPos support = feet.below();
        if (feet.getY() < level.getMinY() || feet.getY() + 1 >= level.getMaxY()
                || support.getY() < level.getMinY()
                || !level.isLoaded(feet) || !level.isLoaded(feet.above()) || !level.isLoaded(support)) return false;
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(feet.above());
        BlockState supportState = level.getBlockState(support);
        if (supportState.isAir() || !supportState.getFluidState().isEmpty()
                || supportState.getCollisionShape(level, support).isEmpty()
                || supportState.is(BlockTags.TRAPDOORS) || supportState.getBlock() instanceof TrapDoorBlock
                || !feetState.getFluidState().isEmpty() || !headState.getFluidState().isEmpty()
                || feetState.is(BlockTags.TRAPDOORS) || feetState.getBlock() instanceof TrapDoorBlock




                || !safeBaseFootingResult(level, feet).safe()
                || isNearHazard(level, feet, formation.plan.hazardCells(), formation.plan.forward())) {
            return false;
        }
        mob.setOnGround(true);
        return true;
    }

    private static final class SafeBaseStagingDiagnostics {
        final Map<String, Integer> rejectionCounts = new LinkedHashMap<>();
        final Map<String, BlockPos> firstCells = new LinkedHashMap<>();
        int surfaceProbes;
        int resolvedCandidates;
        int safeCandidates;
        int cappedCandidates;
        int pathAttempts;

        void record(String category, BlockPos cell) {
            rejectionCounts.merge(category, 1, Integer::sum);
            if (cell != null) firstCells.putIfAbsent(category, cell.immutable());
        }

        void log(ServerLevel level, Formation formation, Mob mob, int slot, long revision,
                 Vec3 pivot, BlockPos sourceBucket) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ROUTING)) {
            SoundAttractMod.LOGGER.debug("[LivingWallCoordinator] safe_source_staging_diagnostic formation={} member={} slot={} revision={} sourceBucket={} pivot={} surfaceProbes={} resolvedCandidates={} safeCandidates={} cappedCandidates={} pathAttempts={} rejectionCounts={} firstCells={}",
                    formation.id, mob.getUUID(), slot, revision, sourceBucket, pivot,
                    surfaceProbes, resolvedCandidates, safeCandidates, cappedCandidates,
                    pathAttempts, rejectionCounts, firstCells);
            }
        }
    }

    private static boolean safeBasePathStillValid(ServerLevel level, Mob mob, Formation formation,
                                                   SafeBaseNavigationReservation reservation,
                                                   net.minecraft.world.level.pathfinder.Path path) {
        return safeBasePathValidation(level, mob, formation, reservation, path).safe();
    }

    private static LivingBridgeSafeBaseRoute.Result safeBasePathValidation(
            ServerLevel level, Mob mob, Formation formation, SafeBaseNavigationReservation reservation,
            net.minecraft.world.level.pathfinder.Path path) {
        if (level == null || mob == null || formation == null || reservation == null
                || level.getServer() == null || !level.getServer().isSameThread()) {
            return new LivingBridgeSafeBaseRoute.Result(LivingBridgeSafeBaseRoute.Rejection.INVALID_FRAME, null);
        }
        if (!safeBaseReservationBoundToBuilder(level, mob, formation, reservation)) {
            return new LivingBridgeSafeBaseRoute.Result(LivingBridgeSafeBaseRoute.Rejection.CERTIFICATE_INVALID, null);
        }
        if (path == null) {
            return new LivingBridgeSafeBaseRoute.Result(LivingBridgeSafeBaseRoute.Rejection.NO_PATH, null);
        }
        if (!path.canReach() || path.getNodeCount() == 0) {
            return new LivingBridgeSafeBaseRoute.Result(LivingBridgeSafeBaseRoute.Rejection.INCOMPLETE_PATH, null);
        }
        if (path.getTarget() == null || path.getTarget().distSqr(reservation.staging()) > 1.0D) {
            return new LivingBridgeSafeBaseRoute.Result(LivingBridgeSafeBaseRoute.Rejection.WRONG_ENDPOINT,
                    path.getTarget());
        }
        if (path.getNodeCount() > TARGET_ROUTE_NODE_LIMIT) {
            return new LivingBridgeSafeBaseRoute.Result(
                    LivingBridgeSafeBaseRoute.Rejection.SWEEP_BUDGET_EXCEEDED, null);
        }
        BlockPos prior = null;
        double bodyMargin = mob.getBbWidth() * 0.5D
                * (Math.abs(reservation.forward().x) + Math.abs(reservation.forward().z))
                + APPROACH_LANE_SPACING * 0.5D;
        for (int index = 0; index < path.getNodeCount(); index++) {
            BlockPos node = path.getNode(index).asBlockPos();
            if (node.getY() < level.getMinY() || node.getY() >= level.getMaxY()
                    || !level.isLoaded(node)) {
                return new LivingBridgeSafeBaseRoute.Result(
                        LivingBridgeSafeBaseRoute.Rejection.UNLOADED_CELL, node);
            }
            if (formation.plan.hazardCells().contains(node)
                    || isNearHazard(level, node, formation.plan.hazardCells(), reservation.forward())) {
                return new LivingBridgeSafeBaseRoute.Result(LivingBridgeSafeBaseRoute.Rejection.COLLISION, node);
            }
            Vec3 position = Vec3.atBottomCenterOf(node);
            AABB body = mob.getDimensions(mob.getPose()).makeBoundingBox(position);
            LivingBridgeSafeBaseRoute.Result footing = safeBaseFootingResult(level, node);
            if (!footing.safe()) return footing;
            LivingBridgeSafeBaseRoute.Result bodySweep = structuralSweepResult(level, mob, body);
            if (!bodySweep.safe()) return bodySweep;
            if (prior != null) {
                Vec3 from = Vec3.atBottomCenterOf(prior);
                Vec3 delta = position.subtract(from);
                int subdivisions = LivingBridgeSafeBaseRoute.subdivisions(delta.length());
                if (subdivisions > LivingBridgeSafeBaseRoute.MAX_SWEEP_SUBDIVISIONS) {
                    return new LivingBridgeSafeBaseRoute.Result(
                            LivingBridgeSafeBaseRoute.Rejection.SWEEP_BUDGET_EXCEEDED, node);
                }
                for (int sample = 1; sample <= subdivisions; sample++) {
                    Vec3 sweptPosition = from.lerp(position, sample / (double) subdivisions);
                    LivingBridgeSafeBaseRoute.Result sweptFooting = safeBaseFootingResult(
                            level, BlockPos.containing(sweptPosition));
                    if (!sweptFooting.safe()) return sweptFooting;
                    AABB sweptBody = mob.getDimensions(mob.getPose()).makeBoundingBox(sweptPosition);
                    LivingBridgeSafeBaseRoute.Result edgeSweep = structuralSweepResult(level, mob, sweptBody);
                    if (!edgeSweep.safe()) return edgeSweep;
                }
            }
            prior = node;
        }
        if (path.getEndNode() == null) {
            return new LivingBridgeSafeBaseRoute.Result(LivingBridgeSafeBaseRoute.Rejection.INCOMPLETE_PATH, null);
        }
        return LivingBridgeSafeBaseRoute.validateCompletePathShape(reservation.pathNodes(), reservation.staging(),
                path.canReach(), path.getEndNode().asBlockPos());
    }

    private static boolean safeBaseBodyOutsideBridgeEnvelope(Formation formation, Mob mob, AABB body) {
        if (formation == null || formation.plan == null || mob == null || body == null) return false;
        return LivingBridgeSafeBaseRoute.bodyOutsideFiniteEnvelope(body, formation.plan.pivot(),
                formation.plan.spanEnd(), safeBaseEnvelopeHalfWidth(formation, mob),
                safeBaseEnvelopeMinY(formation.plan), safeBaseEnvelopeMaxY(formation.plan) + 1.0D);
    }

    private static double safeBaseEnvelopeHalfWidth(Formation formation, Mob mob) {
        if (formation == null || formation.plan == null || mob == null) return Double.NaN;
        Vec3 pivot = Vec3.atCenterOf(formation.plan.pivot());
        Vec3 forward = LivingBridgeSafeBaseRoute.verifiedForward(
                formation.plan.pivot(), formation.plan.spanEnd(), formation.plan.forward());
        if (pivot == null || forward == null) return Double.NaN;
        Vec3 lateral = new Vec3(-forward.z, 0.0D, forward.x);
        double halfWidth = 0.0D;
        List<BlockPos> footprint = new ArrayList<>(formation.plan.baseCells());
        footprint.addAll(formation.plan.assemblySlots());
        footprint.add(formation.plan.anchor());
        for (BlockPos cell : footprint) {
            if (cell != null) {
                halfWidth = Math.max(halfWidth,
                        Math.abs(Vec3.atCenterOf(cell).subtract(pivot).dot(lateral)) + 0.5D);
            }
        }
        return halfWidth + mob.getBbWidth() * 0.5D + APPROACH_LANE_SPACING * 0.5D;
    }

    private static int safeBaseEnvelopeMinY(SitePlan plan) {
        if (plan == null) return Integer.MAX_VALUE;
        int minY = Math.min(plan.pivot().getY(), plan.anchor().getY());
        for (BlockPos cell : plan.baseCells()) if (cell != null) minY = Math.min(minY, cell.getY());
        for (BlockPos cell : plan.assemblySlots()) if (cell != null) minY = Math.min(minY, cell.getY());
        return minY;
    }

    private static int safeBaseEnvelopeMaxY(SitePlan plan) {
        if (plan == null) return Integer.MIN_VALUE;
        int maxY = Math.max(plan.pivot().getY(), plan.anchor().getY());
        for (BlockPos cell : plan.baseCells()) if (cell != null) maxY = Math.max(maxY, cell.getY());
        for (BlockPos cell : plan.assemblySlots()) if (cell != null) maxY = Math.max(maxY, cell.getY());
        return maxY;
    }

    private static LivingBridgeSafeBaseRoute.Result safeBaseFootingResult(ServerLevel level, BlockPos feet) {
        if (level == null || feet == null) {
            return new LivingBridgeSafeBaseRoute.Result(LivingBridgeSafeBaseRoute.Rejection.INVALID_FRAME, feet);
        }
        BlockPos support = feet.below();
        if (feet.getY() < level.getMinY() || feet.getY() + 1 >= level.getMaxY()
                || support.getY() < level.getMinY() || !level.isLoaded(feet)
                || !level.isLoaded(feet.above()) || !level.isLoaded(support)) {
            return new LivingBridgeSafeBaseRoute.Result(LivingBridgeSafeBaseRoute.Rejection.UNLOADED_CELL, feet);
        }
        BlockState feetState = level.getBlockState(feet);
        BlockState aboveState = level.getBlockState(feet.above());
        if (feetState.is(BlockTags.TRAPDOORS) || feetState.getBlock() instanceof TrapDoorBlock
                || !feetState.getFluidState().isEmpty() || !aboveState.getFluidState().isEmpty()) {
            return new LivingBridgeSafeBaseRoute.Result(LivingBridgeSafeBaseRoute.Rejection.UNSUPPORTED, feet);
        }
        BlockState supportState = level.getBlockState(support);
        if (supportState.isAir() || !supportState.getFluidState().isEmpty()
                || supportState.getCollisionShape(level, support).isEmpty()
                || supportState.is(BlockTags.TRAPDOORS) || supportState.getBlock() instanceof TrapDoorBlock) {
            return new LivingBridgeSafeBaseRoute.Result(
                    LivingBridgeSafeBaseRoute.Rejection.UNSUPPORTED, support);
        }
        return new LivingBridgeSafeBaseRoute.Result(LivingBridgeSafeBaseRoute.Rejection.NONE, null);
    }

    private static boolean persistSafeBaseClaim(ServerLevel level, Formation formation, Mob mob,
                                                SafeBaseNavigationReservation route) {
        if (level == null || formation == null || mob == null || route == null
                || level.getServer() == null || !level.getServer().isSameThread()) return false;
        UUID reservationId = UUID.nameUUIDFromBytes((formation.id + ":" + mob.getUUID())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        LivingBridgeRouteData data = LivingBridgeRouteData.get(level);
        LivingBridgeRouteSnapshot current = null;
        CompoundTag entityData = com.example.soundattract.component.LivingBridgeEntityData.get(mob);
        if (entityData.contains("livingBridgeControl")) {
            if (!(entityData.get("livingBridgeControl") instanceof CompoundTag)) {
                CompoundTag raw = new CompoundTag();
                raw.put("rawCapsule", entityData.get("livingBridgeControl").copy());
                data.quarantine("MALFORMED_ENTITY_CAPSULE", raw, null,
                        level.dimension().identifier().toString());
                return false;
            }
            CompoundTag capsule = entityData.getCompoundOrEmpty("livingBridgeControl");
            try {
                if (!(capsule.get("schema") instanceof net.minecraft.nbt.IntTag)
                        || capsule.getIntOr("schema", -1) != LivingBridgeRouteData.CURRENT_SCHEMA) {
                    data.quarantine("UNSUPPORTED_SAFE_BASE_TRANSITION", capsule, null,
                            level.dimension().identifier().toString());
                    return false;
                }
                current = LivingBridgeRouteSnapshot.fromTag(capsule.getCompoundOrEmpty("snapshot"));
                if (!current.digest().equals(capsule.getStringOr("snapshotDigest", ""))) {
                    throw new IllegalArgumentException("entity reservation digest mismatch");
                }
                LivingBridgeRouteSnapshot saved = data.snapshot(current.reservationId());
                if (saved == null || !saved.exactlyEquals(current)
                        || !current.ownerId().equals(mob.getUUID())
                        || !current.formationId().equals(formation.id)
                        || !current.dimension().equals(level.dimension().identifier().toString())) {
                    data.quarantine("SAFE_BASE_REPLAN_COPY_DIVERGENCE",
                            saved == null ? null : saved.toTag(), capsule,
                            level.dimension().identifier().toString());
                    return false;
                }
                if (!current.reservationId().equals(reservationId)) {
                    data.quarantine("SAFE_BASE_REPLAN_RESERVATION_CONFLICT", current.toTag(), null,
                            current.dimension());
                    return false;
                }
            } catch (RuntimeException malformed) {
                data.quarantine("MALFORMED_ENTITY_CAPSULE", capsule, null,
                        level.dimension().identifier().toString());
                return false;
            }
        }
        List<LivingBridgeRouteSnapshot.Point> corridor = route.pathNodes().stream()
                .map(node -> LivingBridgeRouteSnapshot.Point.from(
                        node.getX() + 0.5D, node.getY(), node.getZ() + 0.5D))
                .toList();
        long width = Math.max(1L, Math.round(Math.max(0.1D, mob.getBbWidth()) * 0.5D * 4096.0D));
        boolean originalNoPhysics = formation.originalNoPhysics.getOrDefault(mob.getUUID(), mob.noPhysics);
        boolean originallyHadGravity = formation.originalGravity.getOrDefault(mob.getUUID(), !mob.isNoGravity());
        long generation = current == null ? 0L : current.commitGeneration();
        UUID transactionId = current == null ? UUID.nameUUIDFromBytes((reservationId + ":initial")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)) : current.transactionId();
        LivingBridgeRouteSnapshot snapshot = new LivingBridgeRouteSnapshot(reservationId, mob.getUUID(),
                formation.id, level.dimension().identifier().toString(), generation, transactionId,
                route.routeRevision(), "SAFE_STAGING_NAVIGATION", "WALKING_TO_STAGING",
                originalNoPhysics, !originallyHadGravity, false, corridor, width, List.of(), 0,
                LivingBridgeRouteSnapshot.Point.from(route.staging().getX() + 0.5D,
                        route.staging().getY(), route.staging().getZ() + 0.5D),
                mob.chunkPosition().x, mob.chunkPosition().z);
        if (current != null && current.exactlyEquals(snapshot)) return true;
        if (current != null) {
            generation++;
            transactionId = UUID.nameUUIDFromBytes((reservationId + ":" + generation + ":"
                    + route.routeRevision() + ":" + route.staging().asLong())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            snapshot = new LivingBridgeRouteSnapshot(reservationId, mob.getUUID(),
                    formation.id, level.dimension().identifier().toString(), generation, transactionId,
                    route.routeRevision(), "SAFE_STAGING_NAVIGATION", "WALKING_TO_STAGING",
                    originalNoPhysics, !originallyHadGravity, false, corridor, width, List.of(), 0,
                    LivingBridgeRouteSnapshot.Point.from(route.staging().getX() + 0.5D,
                            route.staging().getY(), route.staging().getZ() + 0.5D),
                    mob.chunkPosition().x, mob.chunkPosition().z);
        }
        return current == null ? data.beginInitialClaim(snapshot, mob)
                : data.commitTransition(current, snapshot, mob);
    }

    private static boolean persistSafeBaseDocked(ServerLevel level, Formation formation, Mob mob,
                                                 BlockPos validatedSafeEndpoint) {
        if (level == null || formation == null || mob == null || level.getServer() == null
                || validatedSafeEndpoint == null || !level.getServer().isSameThread()
                || !BlockPos.containing(mob.position()).equals(validatedSafeEndpoint)) return false;
        CompoundTag capsule = com.example.soundattract.component.LivingBridgeEntityData.get(mob).getCompoundOrEmpty("livingBridgeControl");
        if (!(com.example.soundattract.component.LivingBridgeEntityData.get(mob).get("livingBridgeControl") instanceof CompoundTag)
                || capsule.getIntOr("schema", -1) != LivingBridgeRouteData.CURRENT_SCHEMA) return false;
        LivingBridgeRouteData data = LivingBridgeRouteData.get(level);
        try {
            LivingBridgeRouteSnapshot current = LivingBridgeRouteSnapshot.fromTag(
                    capsule.getCompoundOrEmpty("snapshot"));
            LivingBridgeRouteSnapshot saved = data.snapshot(current.reservationId());
            if (!current.digest().equals(capsule.getStringOr("snapshotDigest", "")) || saved == null
                    || !saved.exactlyEquals(current) || !current.ownerId().equals(mob.getUUID())
                    || !current.formationId().equals(formation.id)
                    || !current.dimension().equals(level.dimension().identifier().toString())) return false;
            if (current.lifecycleState().equals("DOCKED")) return true;
            LivingBridgeRouteSnapshot docked = LivingBridgeRouteData.dockedSnapshot(current,
                    LivingBridgeRouteSnapshot.Point.from(mob.getX(), mob.getY(), mob.getZ()),
                    mob.chunkPosition().x, mob.chunkPosition().z);
            return data.commitDockedTransition(current, docked, mob);
        } catch (RuntimeException malformed) {
            data.quarantine("SAFE_BASE_DOCKED_COPY_DIVERGENCE", null, capsule,
                    level.dimension().identifier().toString());
            return false;
        }
    }

    private static boolean completeSafeRecoveryAtPersistedEndpoint(ServerLevel level, Mob mob) {
        return completeSafeRecoveryAtPersistedEndpoint(level, mob,
                level == null ? null : LivingBridgeRouteData.get(level));
    }

    private static boolean completeSafeRecoveryAtPersistedEndpoint(ServerLevel level, Mob mob,
                                                                     LivingBridgeRouteData data) {
        if (level == null || mob == null || level.getServer() == null || !level.getServer().isSameThread()
                || !mob.isAlive() || mob.isRemoved()
                || data == null
                || !(com.example.soundattract.component.LivingBridgeEntityData.get(mob).get("livingBridgeControl") instanceof CompoundTag)) {
            return false;
        }
        CompoundTag capsule = com.example.soundattract.component.LivingBridgeEntityData.get(mob).getCompoundOrEmpty("livingBridgeControl");
        if (capsule.getIntOr("schema", -1) != LivingBridgeRouteData.CURRENT_SCHEMA) return false;
        try {
            LivingBridgeRouteSnapshot current = LivingBridgeRouteSnapshot.fromTag(capsule.getCompoundOrEmpty("snapshot"));
            LivingBridgeRouteSnapshot saved = data.snapshot(current.reservationId());
            if (!current.digest().equals(capsule.getStringOr("snapshotDigest", "")) || saved == null
                    || !saved.exactlyEquals(current) || !current.ownerId().equals(mob.getUUID())
                    || !current.dimension().equals(level.dimension().identifier().toString())
                    || current.safeEndpoint() == null
                    || !data.canRecoverOwner(current)
                    || LivingBridgeRouteData.isTerminalLaneState(current.lifecycleState())) return false;
            BlockPos endpoint = BlockPos.containing(current.safeEndpoint().x() / 4096.0D,
                    current.safeEndpoint().y() / 4096.0D, current.safeEndpoint().z() / 4096.0D);
            if (!BlockPos.containing(mob.position()).equals(endpoint) || !isSafeSite(level, endpoint)
                    || !com.example.soundattract.pathfinding.HazardClassifier.isSafeGround(level, endpoint)
                    || mob.isInLava() || !isEntityClearAt(level, mob, mob.position(), mob.position())) return false;
            LivingBridgeRouteSnapshot terminal = LivingBridgeRouteData.safeRecoverySnapshot(current,
                    mob.chunkPosition().x, mob.chunkPosition().z);
            if (!data.commitSafeRecoveryTransition(current, terminal, mob)) return false;
            mob.getNavigation().stop();
            mob.setDeltaMovement(Vec3.ZERO);
            mob.fallDistance = 0.0F;
            mob.setNoGravity(terminal.originalNoGravity());
            mob.noPhysics = terminal.originalNoPhysics();
            com.example.soundattract.component.LivingBridgeEntityData.get(mob).remove("livingBridgeSafeBaseRouteActive");
            return true;
        } catch (RuntimeException malformed) {
            data.quarantine("SAFE_RECOVERY_COPY_DIVERGENCE", null, capsule,
                    level.dimension().identifier().toString());
            return false;
        }
    }

    private static boolean advanceControlledSafeRecovery(ServerLevel level, Mob mob) {
        return advanceControlledSafeRecovery(level, mob,
                level == null ? null : LivingBridgeRouteData.get(level));
    }

    private static boolean advanceControlledSafeRecovery(ServerLevel level, Mob mob,
                                                          LivingBridgeRouteData data) {
        if (level == null || mob == null || level.getServer() == null || !level.getServer().isSameThread()
                || !mob.isAlive() || mob.isRemoved()
                || data == null
                || !(com.example.soundattract.component.LivingBridgeEntityData.get(mob).get("livingBridgeControl") instanceof CompoundTag)) {
            return false;
        }
        CompoundTag capsule = com.example.soundattract.component.LivingBridgeEntityData.get(mob).getCompoundOrEmpty("livingBridgeControl");
        if (capsule.getIntOr("schema", -1) != LivingBridgeRouteData.CURRENT_SCHEMA) return false;
        try {
            LivingBridgeRouteSnapshot current = LivingBridgeRouteSnapshot.fromTag(capsule.getCompoundOrEmpty("snapshot"));
            LivingBridgeRouteSnapshot saved = data.snapshot(current.reservationId());
            if (saved == null || !saved.exactlyEquals(current)
                    || !current.digest().equals(capsule.getStringOr("snapshotDigest", ""))
                    || !current.ownerId().equals(mob.getUUID())
                    || !current.dimension().equals(level.dimension().identifier().toString())
                    || current.safeEndpoint() == null
                    || !data.canRecoverOwner(current)
                    || LivingBridgeRouteData.isTerminalLaneState(current.lifecycleState())) return false;

            LivingBridgeRouteSnapshot recovering = current;
            if (!"RECOVERING_TO_SAFE_ENDPOINT".equals(current.lifecycleState())) {
                if (current.corridor().size() < 2) return false;
                Vec3 position = mob.position();
                int nearest = -1;
                double nearestDistance = Double.POSITIVE_INFINITY;
                for (int i = 0; i < current.corridor().size(); i++) {
                    LivingBridgeRouteSnapshot.Point point = current.corridor().get(i);
                    Vec3 candidate = new Vec3(point.x() / 4096.0D, point.y() / 4096.0D, point.z() / 4096.0D);
                    double distance = position.distanceToSqr(candidate);
                    if (distance < nearestDistance) { nearestDistance = distance; nearest = i; }
                }
                if (nearest < 0 || nearestDistance > 0.25D * 0.25D) return false;
                List<LivingBridgeRouteSnapshot.Point> route = new ArrayList<>();
                route.add(LivingBridgeRouteSnapshot.Point.from(position.x, position.y, position.z));
                for (int i = nearest + 1; i < current.corridor().size(); i++) route.add(current.corridor().get(i));
                Vec3 endpoint = new Vec3(current.safeEndpoint().x() / 4096.0D,
                        current.safeEndpoint().y() / 4096.0D, current.safeEndpoint().z() / 4096.0D);
                Vec3 routeEnd = new Vec3(route.get(route.size() - 1).x() / 4096.0D,
                        route.get(route.size() - 1).y() / 4096.0D, route.get(route.size() - 1).z() / 4096.0D);
                if (route.size() < 2 || routeEnd.distanceToSqr(endpoint) > 0.01D) return false;
                List<LivingBridgeRouteSnapshot.Point> sampled = new ArrayList<>();
                sampled.add(route.get(0));
                for (int i = 1; i < route.size(); i++) {
                    Vec3 from = new Vec3(route.get(i - 1).x() / 4096.0D, route.get(i - 1).y() / 4096.0D,
                            route.get(i - 1).z() / 4096.0D);
                    Vec3 to = new Vec3(route.get(i).x() / 4096.0D, route.get(i).y() / 4096.0D,
                            route.get(i).z() / 4096.0D);
                    int pieces = Math.max(1, Mth.ceil(from.distanceTo(to) / 0.2D));
                    if (sampled.size() + pieces > 65) return false;
                    for (int part = 1; part <= pieces; part++) {
                        Vec3 point = from.lerp(to, part / (double) pieces);
                        sampled.add(LivingBridgeRouteSnapshot.Point.from(point.x, point.y, point.z));
                    }
                }
                if (sampled.size() < 2) return false;
                List<BlockPos> lane = sampled.stream().map(point -> BlockPos.containing(
                        point.x() / 4096.0D, point.y() / 4096.0D, point.z() / 4096.0D)).distinct().toList();
                double width = current.halfWidth() * 2.0D / 4096.0D;
                if (data.hasConflictingRecoveryLane(current, lane, width, APPROACH_LANE_SPACING)) return false;
                BlockPos safe = BlockPos.containing(endpoint);
                if (!level.isLoaded(safe) || !isSafeSite(level, safe)
                        || !com.example.soundattract.pathfinding.HazardClassifier.isSafeGround(level, safe)) return false;
                recovering = LivingBridgeRouteData.recoveringSnapshot(current, sampled, 1,
                        mob.chunkPosition().x, mob.chunkPosition().z);
                if (!data.commitRecoveryTransition(current, recovering, mob)) return false;
                mob.setNoGravity(true);
                mob.noPhysics = true;
                mob.getNavigation().stop();
                mob.setDeltaMovement(Vec3.ZERO);
            }

            List<LivingBridgeRouteSnapshot.Point> route = recovering.recoveryRoute();
            int nextIndex = recovering.recoveryIndex();
            if (route.size() < 2 || nextIndex < 1 || nextIndex > route.size()) return false;
            if (nextIndex == route.size()) return completeSafeRecoveryAtPersistedEndpoint(level, mob, data);
            LivingBridgeRouteSnapshot.Point previous = route.get(nextIndex - 1);
            LivingBridgeRouteSnapshot.Point next = route.get(nextIndex);
            Vec3 expected = new Vec3(previous.x() / 4096.0D, previous.y() / 4096.0D, previous.z() / 4096.0D);
            Vec3 target = new Vec3(next.x() / 4096.0D, next.y() / 4096.0D, next.z() / 4096.0D);
            Vec3 actual = mob.position();
            double quantizedStepLimit = 0.2D + Math.sqrt(3.0D) / 4096.0D;
            if (actual.distanceToSqr(expected) > 0.01D || actual.distanceTo(target) > quantizedStepLimit
                    || !isEntityClearAt(level, mob, actual, target)) return false;
            List<BlockPos> lane = route.subList(nextIndex - 1, route.size()).stream()
                    .map(point -> BlockPos.containing(point.x() / 4096.0D,
                            point.y() / 4096.0D, point.z() / 4096.0D)).distinct().toList();
            if (data.hasConflictingRecoveryLane(recovering, lane,
                    recovering.halfWidth() * 2.0D / 4096.0D, APPROACH_LANE_SPACING)) return false;
            LivingBridgeRouteSnapshot advanced = LivingBridgeRouteData.recoveringSnapshot(recovering, route,
                    nextIndex + 1, mob.chunkPosition().x, mob.chunkPosition().z);
            if (!data.commitRecoveryTransition(recovering, advanced, mob)) return false;
            mob.setNoGravity(true);
            mob.noPhysics = true;
            mob.getNavigation().stop();
            mob.setDeltaMovement(Vec3.ZERO);
            mob.setPos(target.x, target.y, target.z);
            mob.fallDistance = 0.0F;
            if (nextIndex + 1 == route.size()) return completeSafeRecoveryAtPersistedEndpoint(level, mob, data);
            return true;
        } catch (RuntimeException malformed) {
            data.quarantine("SAFE_RECOVERY_ROUTE_DIVERGENCE", null, capsule,
                    level.dimension().identifier().toString());
            return false;
        }
    }

    private static boolean isStructuralApproachLaneAvailable(ServerLevel level, Formation formation, UUID ownerId,
                                                              List<BlockPos> corridor, double bodyWidth) {
        if (level == null || formation == null || ownerId == null || corridor == null || corridor.isEmpty()
                || !Double.isFinite(bodyWidth) || bodyWidth <= 0.0D) return false;
        for (StructuralApproachReservation reservation : formation.structuralApproachReservations.values()) {
            if (ownerId.equals(reservation.ownerId())) continue;
            if (LivingBridgeSafeBaseRoute.laneCorridorsConflict(corridor, bodyWidth,
                    reservation.corridor(), reservation.bodyWidth(), APPROACH_LANE_SPACING)) return false;
        }
        UUID reservationId = UUID.nameUUIDFromBytes((formation.id + ":" + ownerId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return !LivingBridgeRouteData.get(level).hasConflictingLane(
                level.dimension().identifier().toString(), ownerId, reservationId,
                corridor, bodyWidth, APPROACH_LANE_SPACING);
    }

    private static boolean claimStructuralApproachLane(Formation formation, UUID ownerId,
                                                        SafeBaseNavigationReservation route, double bodyWidth,
                                                        long heartbeatTick) {
        if (formation == null || ownerId == null || route == null
                || !Double.isFinite(bodyWidth) || bodyWidth <= 0.0D) return false;
        StructuralApproachReservation replacement = new StructuralApproachReservation(
                formation.id, ownerId, route.routeRevision(), route.pathNodes(), bodyWidth, heartbeatTick);
        return LivingBridgeSafeBaseRoute.transferLaneClaim(formation.structuralApproachReservations,
                ownerId, replacement, (first, second) -> LivingBridgeSafeBaseRoute.laneCorridorsConflict(
                        first.corridor(), first.bodyWidth(), second.corridor(), second.bodyWidth(),
                        APPROACH_LANE_SPACING));
    }

    private static void releaseStructuralApproachLane(Formation formation, UUID ownerId) {
        if (formation != null && ownerId != null) formation.structuralApproachReservations.remove(ownerId);
    }

    private static boolean isAssemblyStagePoseValid(ServerLevel level, Formation formation, Mob mob,
                                                    MobTicket assignment, Vec3 next) {
        if (level == null || formation == null || mob == null || assignment == null || next == null) return false;
        if (!isCurrentAssemblyOwner(level, formation, mob, assignment)) return false;
        if (!isSafeAssemblySlot(level, mob, assignment.assemblySlot, formation)) return false;
        if (!isWithinAssemblyAcceptanceEnvelope(next, assignment.assemblySlot)) return false;
        if (isNearHazard(level, BlockPos.containing(next), formation.plan.hazardCells(), formation.plan.forward())) {
            return false;
        }

        boolean isBridge = isCanonicalLivingBridge(formation);
        Vec3 clearanceTarget = isBridge ? Vec3.atBottomCenterOf(assignment.assemblySlot) : next;
        double progress = formation != null ? formation.assembledSlots + 1.0D : 0.0D;
        if (!resolveClearance(level, mob, clearanceTarget, mob.maxUpStep(), formation, progress).clear()) return false;
        return findAssemblyOccupant(level, formation, mob, mob.position(), next, assignment.assemblySlot, true) == null;
    }

    private static void logAssemblyStagePoseRejection(ServerLevel level, Formation formation, Mob mob,
                                                      MobTicket assignment, Vec3 candidate) {
        if (formation == null || formation.assemblyRejectionDiagnostics >= 32) return;
        String reason;
        if (!isCurrentAssemblyOwner(level, formation, mob, assignment)) {
            reason = "ownership";
        } else if (!isSafeAssemblySlot(level, mob, assignment.assemblySlot, formation)) {
            reason = "unsafe_slot";
        } else if (!isWithinAssemblyAcceptanceEnvelope(candidate, assignment.assemblySlot)) {
            reason = "outside_acceptance_envelope";
        } else if (isNearHazard(level, BlockPos.containing(candidate), formation.plan.hazardCells(),
                formation.plan.forward())) {
            reason = "hazard";
        } else {
            Vec3 clearanceTarget = isCanonicalLivingBridge(formation)
                    ? Vec3.atBottomCenterOf(assignment.assemblySlot) : candidate;
            ClearanceResolution clearance = resolveClearance(level, mob, clearanceTarget, mob.maxUpStep(), formation,
                    formation.assembledSlots + 1.0D);
            if (!clearance.clear()) {
                ClearanceBlockage blockage = clearance.blockage();
                reason = "clearance:blocker=" + clearance.blocker() + ":state=" + clearance.blockerState()
                        + ":lava=" + clearance.lava() + ":unloaded=" + clearance.unloaded()
                        + ":blockage=" + blockage;
            } else {
                Entity occupant = findAssemblyOccupant(level, formation, mob, mob.position(), candidate,
                        assignment.assemblySlot, true);
                reason = occupant == null ? "unknown" : "occupant:" + occupant.getType()
                        + ":uuid=" + occupant.getUUID() + ":box=" + occupant.getBoundingBox();
            }
        }
        formation.assemblyRejectionDiagnostics++;
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] assembly_stage_pose_rejected bridge={} worker={} tick={} reason={} pos={} candidate={} slot={}",
                formation.id, mob.getUUID(), level.getGameTime(), reason, mob.position(), candidate,
                assignment.assemblySlot);
    }

    private static Entity findAssemblyOccupant(ServerLevel level, Formation formation, Mob mob,
                                               Vec3 current, Vec3 next, BlockPos slot) {
        return findAssemblyOccupant(level, formation, mob, current, next, slot, false);
    }

    private static Entity findAssemblyOccupant(ServerLevel level, Formation formation, Mob mob,
                                               Vec3 current, Vec3 next, BlockPos slot,
                                               boolean allowFormationRosterTransit) {
        if (level == null || mob == null || current == null || next == null || slot == null) return null;
        double halfWidth = mob.getBbWidth() * 0.5D;
        AABB currentBox = new AABB(current.x - halfWidth, current.y, current.z - halfWidth,
                current.x + halfWidth, current.y + mob.getBbHeight(), current.z + halfWidth);
        AABB nextBox = new AABB(next.x - halfWidth, next.y, next.z - halfWidth,
                next.x + halfWidth, next.y + mob.getBbHeight(), next.z + halfWidth);
        AABB swept = currentBox.minmax(nextBox).inflate(1.0E-3D);
        List<Entity> blockers = level.getEntities(mob, swept,
                candidate -> candidate instanceof LivingEntity && candidate.isAlive()
                        && !candidate.isRemoved() && candidate != mob
                        && isAssemblyOccupantEligible(formation, candidate, allowFormationRosterTransit));
        for (Entity candidate : blockers) {
            if (boxesPenetrate(candidate.getBoundingBox(), nextBox)
                    || boxesPenetrate(candidate.getBoundingBox(), swept)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isAssemblyOccupantEligible(Formation formation, Entity candidate,
                                                       boolean allowFormationRosterTransit) {
        if (formation == null || candidate == null) return true;
        UUID candidateId = candidate.getUUID();
        if (formation.members.contains(candidateId) || formation.pendingBuilderIds.contains(candidateId)
                || (formation.mobTickets != null && formation.mobTickets.containsKey(candidateId))) return false;
        return !allowFormationRosterTransit || !formation.localRoster.contains(candidateId);
    }

    private static boolean isExactAssemblyLockValid(ServerLevel level, Formation formation, Mob mob,
                                                    MobTicket assignment, Vec3 exactTarget) {
        if (level == null || formation == null || mob == null || assignment == null || exactTarget == null) return false;
        if (!isCurrentAssemblyOwner(level, formation, mob, assignment)) return false;
        if (!isAtAssemblySlot(mob.position(), assignment.assemblySlot)) return false;
        boolean isBridge = isCanonicalLivingBridge(formation);
        boolean isFoundationSlot = formation.plan != null && formation.plan.assemblySlots() != null
                && !formation.plan.assemblySlots().isEmpty()
                && assignment.assemblySlot.equals(formation.plan.assemblySlots().get(0));
        if (!isBridge || isFoundationSlot) {
            if (!hasFoundationTerrainSupport(level, exactTarget)) return false;
        }
        double progress = formation != null ? formation.assembledSlots + 1.0D : 0.0D;
        if (!resolveClearance(level, mob, exactTarget, 0.0D, formation, progress).clear()) return false;
        return isEntityClearAt(level, mob, mob.position(), exactTarget)
                && findAssemblyOccupant(level, formation, mob, exactTarget, exactTarget, assignment.assemblySlot) == null;
    }

    static boolean foundationMayAssembleWithoutIntake(int slot, int columns, BlockPos intakeCell) {
        return slot >= 0 && columns > 0 && slot < columns && intakeCell == null;
    }

    static boolean foundationCommittedForLayer(int assembledSlots, int memberCount, int columnCount,
                                               boolean foundationContactValid, int slot) {
        return slot < Math.max(0, columnCount)
                || columnCount > 0
                && memberCount >= columnCount
                && assembledSlots >= columnCount
                && foundationContactValid;
    }

    private static boolean canAdmitUpperLayerWorker(ServerLevel level, Formation formation, BlockPos assemblySlot) {
        if (formation == null || assemblySlot == null || formation.plan == null || formation.plan.assemblySlots() == null) {
            return false;
        }
        int slot = formation.plan.assemblySlots().indexOf(assemblySlot);
        if (slot < 0) return false;
        boolean foundationContactValid = level != null && structureHasContact(level, formation,
                Math.min(formation.columnCount, formation.members.size()));
        return foundationCommittedForLayer(formation.assembledSlots, formation.members.size(),
                formation.columnCount, foundationContactValid, slot);
    }

    private static boolean queuePendingBuilder(Mob mob, Formation formation, int nextLayerLimit, long now) {
        if (mob == null || !(mob.level() instanceof ServerLevel level) || !compatibleGeometry(mob, formation)
                || formation.members.contains(mob.getUUID())
                || formation.pendingBuilderIds.contains(mob.getUUID())
                || formation.members.size() + formation.pendingBuilderIds.size() >= nextLayerLimit) {
            return false;
        }
        MobTicket assignment = formation.mobTickets.get(mob.getUUID());
        if (assignment == null || assignment.state != MobTicketState.BUILDING
                || !isCompletedCaptureReady(level, mob, formation, assignment)) return false;
        if (!canAdmitUpperLayerWorker(level, formation, assignment.assemblySlot)) return false;
        removeRecruitmentGoal(mob, formation);
        reserveMob(mob, formation);
        copyAssemblyIntakeProvenance(formation, mob);
        mob.getNavigation().stop();
        mob.setDeltaMovement(Vec3.ZERO);
        formation.pendingBuilderIds.add(mob.getUUID());
        logRecruitmentEvent(formation, mob, "building:limit=" + nextLayerLimit, assignment,
                Vec3.atBottomCenterOf(formation.pivot));
        return true;
    }

    private static boolean isCompletedCaptureReady(ServerLevel level, Mob mob, Formation formation,
                                                   MobTicket assignment) {
        return level != null && mob != null && formation != null
                && assignment != null
                && assignment.state == MobTicketState.BUILDING
                && assignment.captureCompleted
                && assignment.intakeWindow != null
                && canMobUseIntakeWindow(level, mob, assignment.intakeWindow, formation);
    }

    private static int promoteWaitingIntakeBuilders(ServerLevel level, Formation formation, long now) {
        int capacity = Math.max(0, nextLayerLimit(formation) - formation.members.size()
                - formation.pendingBuilderIds.size());
        if (capacity <= 0) return 0;
        List<Mob> waiting = new ArrayList<>();
        for (Map.Entry<UUID, MobTicket> entry : formation.mobTickets.entrySet()) {
            MobTicket assignment = entry.getValue();
            if (assignment.state != MobTicketState.BUILDING) continue;
            Mob mob = getMob(level, entry.getKey());
            if (mob != null && mob.isAlive() && !mob.isRemoved()) waiting.add(mob);
        }
        waiting.sort(Comparator.comparingDouble((Mob mob) -> mob.distanceToSqr(Vec3.atBottomCenterOf(formation.pivot)))
                .thenComparing(mob -> mob.getUUID().toString()));
        int promoted = 0;
        for (Mob mob : waiting) {
            if (promoted >= capacity) break;
            copyAssemblyIntakeProvenance(formation, mob);
            if (queuePendingBuilder(mob, formation, nextLayerLimit(formation), now)) promoted++;
        }
        return promoted;
    }

    private static boolean grantIntakeTicket(ServerLevel level, Mob mob, Formation formation, int nextLayerLimit, long now) {
        if (mob == null || !compatibleGeometry(mob, formation) || formation.members.contains(mob.getUUID())
                || formation.pendingBuilderIds.contains(mob.getUUID())
                || (formation.activeIntakeWorkerId != null && !formation.activeIntakeWorkerId.equals(mob.getUUID()))
                || formation.members.size() + formation.pendingBuilderIds.size() >= nextLayerLimit) return false;
        MobTicket assignment = formation.mobTickets.get(mob.getUUID());
        if (assignment == null || assignment.state != MobTicketState.ASSEMBLED
                || !isAtAssemblySlot(mob.position(), assignment.assemblySlot)) return false;
        if (!canAdmitUpperLayerWorker(level, formation, assignment.assemblySlot)) return false;
        IntakeWindow intakeWindow = assignIntakeWindow(level, formation, mob, assignment.assemblySlot, now);
        if (intakeWindow == null) {
            logRecruitmentEvent(formation, mob, "mob_route_failed:to_feeder", assignment, null);
            releaseRecruiter(level, formation, mob.getUUID(), now);
            return false;
        }
        if (!canMobUseIntakeWindow(level, mob, intakeWindow, formation)) {
            logIntakeReject(formation, mob, assignment, "window_unsafe", intakeWindow.feederCell(), now);
            releaseRecruiter(level, formation, mob.getUUID(), now);
            return false;
        }
        formation.activeIntakeWorkerId = mob.getUUID();
        assignment.intakeWindow = intakeWindow;
        transitionTicket(assignment, MobTicketState.MOVING_TO_FEEDER);
        assignment.captureTicks = 0;
        assignment.captureAttempts = 0;
        assignment.controlledPull = false;
        assignment.captureRetryUntil = Long.MIN_VALUE;
        assignment.lastPathTick = Long.MIN_VALUE;
        assignment.observationTick = Long.MIN_VALUE;
        assignment.bestDistanceSqr = Double.MAX_VALUE;
        assignment.retries = 0;
        releaseRalliedHold(level, formation, mob.getUUID(), mob);
        logRecruitmentEvent(formation, mob, "moving_to_feeder", assignment,
                Vec3.atBottomCenterOf(intakeWindow.feederCell()));
        return true;
    }

    private static boolean claimAssemblySlot(Formation formation, UUID id, BlockPos slot) {
        return formation != null && claimAssemblySlotOwner(formation.assemblySlotAssignments,
                formation.assemblySlotOwners, id, slot);
    }

    static boolean claimAssemblySlotOwner(Map<UUID, BlockPos> assignments, Map<BlockPos, UUID> owners,
                                          UUID id, BlockPos slot) {
        if (assignments == null || owners == null || id == null || slot == null) return false;
        BlockPos existing = assignments.get(id);
        if (existing != null) return existing.equals(slot) && id.equals(owners.get(slot));
        if (owners.putIfAbsent(slot, id) != null) return false;
        assignments.put(id, slot);
        return true;
    }

    static BlockPos releaseAssemblySlotOwner(Map<UUID, BlockPos> assignments, Map<BlockPos, UUID> owners, UUID id) {
        if (assignments == null || owners == null || id == null) return null;
        BlockPos slot = assignments.remove(id);
        if (slot != null) owners.remove(slot, id);
        return slot;
    }

    static boolean isCommittedFoundationOwner(Set<UUID> committedFoundationIds, UUID id) {
        return committedFoundationIds != null && id != null && committedFoundationIds.contains(id);
    }

    static Vec3 recordAssemblyAcquisitionHold(Map<UUID, Vec3> holds, UUID id, Vec3 exactTarget) {
        if (holds == null || id == null || exactTarget == null) return null;
        holds.put(id, exactTarget);
        return holds.get(id);
    }

    private static void releaseAssemblySlot(Formation formation, UUID id) {
        if (formation == null || id == null) return;
        if (formation.committedFoundationIds.contains(id)) return;
        boolean debug = DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_FORMATION);
        if (debug && formation.phase == Phase.ASSEMBLE && formation.assemblySlotAssignments.containsKey(id)) {
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] assembly_slot_mutation_before bridge={} worker={} assigned={} owner={} members={} builders={}",
                    formation.id, id, formation.assemblySlotAssignments.get(id),
                    formation.assemblySlotOwners.get(formation.assemblySlotAssignments.get(id)), formation.members, formation.structuralBuilderIds);
        }
        releaseAssemblyLane(formation, id);
        BlockPos slot = releaseAssemblySlotOwner(formation.assemblySlotAssignments, formation.assemblySlotOwners, id);
        if (slot != null) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_FORMATION)) {
                SoundAttractMod.LOGGER.info("[LivingWallCoordinator] assembly_slot_released bridge={} mob={} slot={}",
                        formation.id, id, slot);
            }
        }
        if (id.equals(formation.activeIntakeWorkerId)) {
            formation.activeIntakeWorkerId = null;
        }
        if (debug && formation.phase == Phase.ASSEMBLE) {
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] assembly_slot_mutation_after bridge={} worker={} removedSlot={} assignedNow={} members={} builders={}",
                    formation.id, id, slot, formation.assemblySlotAssignments.get(id), formation.members, formation.structuralBuilderIds);
        }
    }

    private static IntakeWindow assignIntakeWindow(ServerLevel level, Formation formation, Mob mob,
                                                   BlockPos assemblySlot, long now) {
        if (level == null || formation == null || mob == null || assemblySlot == null) return null;
        if (!isSafeAssemblySlot(level, mob, assemblySlot, formation)
                || !isAtAssemblySlot(mob.position(), assemblySlot)) return null;
        if (formation.plan == null || formation.plan.intakeWindows() == null || formation.plan.intakeWindows().isEmpty()) {
            return null;
        }
        for (IntakeWindow window : formation.plan.intakeWindows()) {
            if (window == null || window.feederCell() == null
                    || !isValidIntakeWindow(level, window, formation)) continue;
            if (isValidSuctionWindow(mob.position(), window.feederCell())
                    || hasReachableRecruitmentRoute(level, formation, mob, assemblySlot, window, now)) {
                return window;
            }
        }
        return localAssemblyIntakeWindow(level, formation, mob, assemblySlot);
    }

    private static IntakeWindow localAssemblyIntakeWindow(ServerLevel level, Formation formation, Mob mob,
                                                          BlockPos assemblySlot) {
        if (level == null || formation == null || mob == null || assemblySlot == null
                || !isSafeAssemblySlot(level, mob, assemblySlot, formation)
                || !isAtAssemblySlot(mob.position(), assemblySlot)) return null;
        BlockPos anchor = mob.blockPosition();
        IntakeWindow window = new IntakeWindow(anchor, true);
        return isValidIntakeWindow(level, window, formation)
                && isValidSuctionWindow(mob.position(), anchor)
                && isLocalAssemblySuctionPose(mob.position(), window, formation, mob.getUUID(), assemblySlot)
                ? window : null;
    }

    static IntakeWindow selectIntakeWindow(List<IntakeWindow> windows, Predicate<IntakeWindow> reachable) {
        if (windows == null || reachable == null) return null;
        for (IntakeWindow window : windows) {
            if (window != null && window.feederCell() != null && reachable.test(window)) return window;
        }
        return null;
    }

    static boolean isIntakeRouteTarget(IntakeWindow window, BlockPos target) {
        return window != null && window.feederApproach() != null && window.feederApproach().equals(target);
    }

    private static boolean hasReachableRecruitmentRoute(ServerLevel level, Formation formation, Mob mob,
                                                        BlockPos assemblySlot, IntakeWindow intakeWindow, long now) {
        if (level == null || formation == null || mob == null || assemblySlot == null
                || intakeWindow == null || !isIntakeRouteTarget(intakeWindow, intakeWindow.feederApproach())) return false;
        BlockPos intakeApproach = intakeWindow.feederApproach();
        RecruitmentRouteKey routeKey = new RecruitmentRouteKey(mob.getUUID(), assemblySlot, intakeApproach);
        RecruitmentRouteSurvey cached = formation.recruitmentRoutes.get(routeKey);
        BlockPos source = mob.blockPosition();
        if (cached != null && cached.expiresAt() > now && cached.source().distSqr(source) <= 1.0D) {
            return cached.reachable() && validateTraversalEntry(level, mob, formation, assemblySlot, intakeWindow, source, intakeApproach);
        }
        if (cached != null && cached.expiresAt() <= now) formation.recruitmentRoutes.remove(routeKey, cached);
        if (state(level).pathSurveysRemaining <= 0) return false;
        state(level).pathSurveysRemaining--;
        var path = mob.getNavigation().createPath(intakeApproach, 0);
        boolean feederReachable = path != null && path.canReach() && path.getTarget() != null
                && path.getTarget().distSqr(intakeApproach) <= 1.0D
                && isValidIntakeWindow(level, intakeWindow, formation)
                && validateTraversalEntry(level, mob, formation, assemblySlot, intakeWindow, source, intakeApproach);
        if (formation.recruitmentRoutes.size() >= PATH_SURVEY_CACHE_LIMIT
                && !formation.recruitmentRoutes.containsKey(routeKey)) {
            formation.recruitmentRoutes.clear();
        }
        formation.recruitmentRoutes.put(routeKey, new RecruitmentRouteSurvey(feederReachable, source,
                now + PATH_SURVEY_CACHE_TICKS));
        return feederReachable;
    }

    private static boolean validateTraversalEntry(ServerLevel level, Mob mob, Formation formation,
                                                   BlockPos assemblySlot, IntakeWindow intakeWindow,
                                                   BlockPos source, BlockPos destination) {
        if (level == null || mob == null || formation == null || assemblySlot == null || intakeWindow == null
                || source == null || destination == null) return false;
        if (!isValidIntakeWindow(level, intakeWindow, formation)) return false;
        if (!isSafeAssemblySlot(level, mob, assemblySlot, formation) && !formation.plan.assemblySlots().contains(assemblySlot)) return false;
        if (source.distSqr(destination) > 64.0D) return false;
        return level.isLoaded(source) && level.isLoaded(destination)
                && (isOnApproachSide(formation.pivot, destination, formation.plan.forward(),
                new java.util.LinkedHashSet<>(formation.plan.hazardCells()))
                || destination.equals(intakeWindow.feederApproach()));
    }

    private static void logIntakeReject(Formation formation, Mob mob, MobTicket assignment,
                                        String reason, BlockPos intake, long now) {
        if (SoundAttractConfig.COMMON == null || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ADMISSION)
                || assignment == null) return;
        if (!reason.equals(assignment.lastIntakeRejectReason)
                || tickElapsed(now, assignment.lastIntakeRejectLogTick, 20L)) {
            assignment.lastIntakeRejectReason = reason;
            assignment.lastIntakeRejectLogTick = now;
            logRecruitmentEvent(formation, mob, "intake_rejected:" + reason, assignment,
                    intake == null ? null : Vec3.atBottomCenterOf(intake));
        }
    }

    private static int activeTicketAssignments(Formation formation) {
        int active = 0;
        for (Map.Entry<UUID, MobTicket> entry : formation.mobTickets.entrySet()) {
            if (countsAsActiveRecruitmentTicket(entry.getValue().state,
                    formation.committedFoundationIds.contains(entry.getKey()))) active++;
        }
        return active;
    }

    static boolean countsAsActiveRecruitmentTicket(MobTicketState state, boolean committedFoundation) {
        return state != null && state != MobTicketState.RELEASED && !committedFoundation;
    }

    private static int intakeAssignments(Formation formation) {
        int active = 0;
        for (MobTicket assignment : formation.mobTickets.values()) {
            if (assignment.state == MobTicketState.MOVING_TO_FEEDER
                    || assignment.state == MobTicketState.SUCTIONING
                    || assignment.state == MobTicketState.BUILDING) active++;
        }
        return active;
    }

    private static int nextLayerLimit(Formation formation) {
        return nextLayerLimitForProgress(formation.phase == Phase.SCAN, formation.assembledSlots,
                formation.columnCount, formation.requiredBodies, STRUCTURAL_ASSEMBLY_LOOKAHEAD);
    }

    static int nextLayerLimitForProgress(boolean scanning, int assembledSlots, int columnCount,
                                         int requiredBodies, int lookahead) {
        if (scanning || assembledSlots < columnCount) return columnCount;
        return Math.min(requiredBodies, assembledSlots + Math.max(1, lookahead));
    }

    private static boolean isValidIntakeWindow(ServerLevel level, IntakeWindow window, Formation formation) {
        if (level == null || window == null || window.feederCell() == null || formation == null || formation.plan == null) return false;
        BlockPos cell = window.feederCell();
        if (window.transientAnchor()) {
            boolean nearBridgeBase = (formation.pivot != null && cell.distSqr(formation.pivot) <= 25.0D)
                    || (formation.plan.assemblySlots() != null && formation.plan.assemblySlots().stream()
                    .anyMatch(slot -> isWithinAssemblyAcceptanceEnvelope(Vec3.atBottomCenterOf(cell), slot)));
            return nearBridgeBase && level.isLoaded(cell)
                    && !isNearHazard(level, cell, formation.plan.hazardCells(), formation.plan.forward());
        }
        if (!formation.plan.intakeWindows().contains(window)) return false;
        Set<BlockPos> structural = new HashSet<>(formation.plan.baseCells());
        structural.add(formation.plan.anchor());
        structural.add(formation.plan.spanEnd());
        return isIntakeWindowElevationCompatible(formation.pivot.getY(), cell.getY())
                && window.feederApproach() != null
                && isSafeApproachCell(level, window.feederApproach())
                && isSafeApproachCell(level, cell)
                && !isNearHazard(level, cell, formation.plan.hazardCells(), formation.plan.forward())
                && isIntakeWindowClear(cell, structural, formation.plan.assemblySlots());
    }

    private static boolean canMobUseIntakeWindow(ServerLevel level, Mob mob, IntakeWindow window, Formation formation) {
        return level != null && mob != null && isValidIntakeWindow(level, window, formation)
                && level.isLoaded(mob.blockPosition()) && !mob.isInLava();
    }

    private static boolean isSafeAssemblySlot(ServerLevel level, Mob mob, BlockPos cell, Formation formation) {
        return level != null && mob != null && cell != null && formation != null
                && formation.plan.assemblySlots().contains(cell) && isNavigableAssemblySurface(level, cell)
                && hasSafeStandingEnvelope(level, cell)
                && !isNearHazard(level, cell, formation.plan.hazardCells(), formation.plan.forward())
                && level.isLoaded(mob.blockPosition()) && !mob.isInLava();
    }

    private static boolean isNavigableAssemblySurface(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos) || !level.isLoaded(pos.above()) || !level.isLoaded(pos.below())) {
            return false;
        }
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState support = level.getBlockState(pos.below());
        return isNavigableAssemblySurface(feet, head, support)
                && feet.getCollisionShape(level, pos).isEmpty()
                && head.getCollisionShape(level, pos.above()).isEmpty()
                && !isUnsafeColumn(level, pos);
    }

    static boolean isNavigableAssemblySurface(BlockState feet, BlockState head, BlockState support) {
        return feet != null && head != null && support != null
                && support.getFluidState().isEmpty()
                && !support.is(BlockTags.TRAPDOORS) && !(support.getBlock() instanceof TrapDoorBlock)
                && !support.isAir()
                && feet.getFluidState().isEmpty() && head.getFluidState().isEmpty()
                && !feet.is(BlockTags.TRAPDOORS) && !head.is(BlockTags.TRAPDOORS);
    }

    private static BlockPos feederCell(MobTicket assignment) {
        return assignment == null || assignment.intakeWindow == null ? null : assignment.intakeWindow.feederCell();
    }

    private static BlockPos assemblyDestination(Mob mob, MobTicket assignment) {
        if (mob == null || assignment == null) return null;
        if (assignment.assemblyApproach != null
                && !hasReachedAssemblyApproach(mob.position(), assignment.assemblyApproach)) {
            return assignment.assemblyApproach;
        }
        return assignment.assemblySlot;
    }

    static boolean hasReachedAssemblyApproach(Vec3 position, BlockPos approach) {
        return position != null && approach != null
                && position.distanceToSqr(Vec3.atBottomCenterOf(approach)) <= INTAKE_WINDOW_ARRIVAL_DISTANCE_SQR;
    }

    private static boolean isSafeAssemblyDestination(ServerLevel level, Mob mob, BlockPos destination,
                                                      Formation formation) {
        if (destination == null || formation == null) return false;
        if (formation.plan.assemblySlots().contains(destination)) {
            return isSafeAssemblySlot(level, mob, destination, formation);
        }
        return isSafeApproachCell(level, destination)
                && !isNearHazard(level, destination, formation.plan.hazardCells(), formation.plan.forward())
                && level.isLoaded(mob.blockPosition()) && !mob.isInLava();
    }

    private static void releaseRecruiters(ServerLevel level, Formation formation) {
        for (UUID id : new ArrayList<>(formation.mobTickets.keySet())) {
            releaseRecruiter(level, formation, id, level.getGameTime());
            if (formation.reserved.contains(id)) releaseMember(level, formation, id);
        }
        for (UUID id : new ArrayList<>(formation.candidateHoldingGoals.keySet())) {
            Mob mob = getMob(level, id);
            if (mob != null) removeCandidateHolding(mob, formation);
        }
    }

    private static void holdRecruiters(ServerLevel level, Formation formation) {
        for (UUID id : new ArrayList<>(formation.ticketGoals.keySet())) {
            Mob mob = getMob(level, id);
            if (mob == null || !mob.isAlive() || mob.isRemoved()) continue;
            mob.setTarget(null);
            mob.setAggressive(false);
        }
    }

    private static void commitFoundationWorker(ServerLevel level, Formation formation, Mob mob,
                                               MobTicket assignment, long now) {
        if (level == null || formation == null || mob == null || assignment == null
                || formation.members.size() >= formation.columnCount
                || assignment.state != MobTicketState.ASSEMBLED) return;
        if (!isExactAssemblyLockValid(level, formation, mob, assignment, Vec3.atBottomCenterOf(assignment.assemblySlot))) {
            restoreFoundationWorkerState(formation, mob, assignment);
            return;
        }
        if (!preflightFoundationSupport(level, formation, mob, assignment)) {
            restoreFoundationWorkerState(formation, mob, assignment);
            return;
        }
        UUID id = mob.getUUID();
        removeRecruitmentGoal(mob, formation);
        reserveMob(mob, formation);
        holdAssemblyWorker(mob, formation);
        recordAssemblyAcquisitionHold(formation.assemblyHoldPositions, mob.getUUID(),
                Vec3.atBottomCenterOf(assignment.assemblySlot));
        pinAssemblyWorker(mob, Vec3.atBottomCenterOf(assignment.assemblySlot));
        formation.members.add(id);
        formation.structuralBuilderIds.add(id);
        formation.builderReservations.add(id);
        formation.committedFoundationIds.add(id);
        int foundationSlot = formation.members.size() - 1;
        formation.structuralSlotPositions.put(foundationSlot, Vec3.atBottomCenterOf(assignment.assemblySlot));
        refreshTraversalWitness(level, formation);
        transitionTicket(assignment, MobTicketState.ASSEMBLED);
        formation.lastRecruitmentProgressTick = now;
        formation.lastAssemblyProgressTick = now;
        logRecruitmentEvent(formation, mob, "foundation_committed:members=" + formation.members.size(),
                assignment, Vec3.atBottomCenterOf(formation.pivot));
    }

    private static void promotePendingBuilders(ServerLevel level, Formation formation, int maximum) {
        int promoted = 0;
        for (UUID id : new ArrayList<>(formation.pendingBuilderIds)) {
            if (promoted >= maximum) break;
            Mob mob = getMob(level, id);
            if (mob == null || !mob.isAlive() || mob.isRemoved() || !isEligible(mob)) {
                formation.collapsed = true;
                return;
            }
            MobTicket assignment = formation.mobTickets.get(id);
            if (assignment == null || !isCompletedCaptureReady(level, mob, formation, assignment)) {
                releaseMember(level, formation, id);
                continue;
            }
            if (!canAdmitUpperLayerWorker(level, formation, assignment.assemblySlot)) {
                releaseMember(level, formation, id);
                continue;
            }
            removeRecruitmentGoal(mob, formation);
            reserveMob(mob, formation);
            copyAssemblyIntakeProvenance(formation, mob);
            mob.setNoGravity(false);
            mob.noPhysics = false;
            formation.members.add(id);
            formation.structuralBuilderIds.add(id);
            formation.builderReservations.add(id);
            formation.pendingBuilderIds.remove(id);
            if (assignment != null) transitionTicket(assignment, MobTicketState.RELEASED);
            releaseAssemblySlot(formation, id);
            logRecruitmentEvent(formation, mob, "member_committed:members=" + formation.members.size(), assignment,
                    Vec3.atBottomCenterOf(formation.pivot));
            promoted++;
        }
        if (promoted > 0) {
            formation.lastRecruitmentProgressTick = level.getGameTime();
            formation.lastAssemblyProgressTick = level.getGameTime();
        }
    }

    private static void holdPendingBuilders(ServerLevel level, Formation formation) {
        for (UUID id : formation.pendingBuilderIds) {
            Mob mob = getMob(level, id);
            if (mob == null || !mob.isAlive() || mob.isRemoved()) continue;
            holdAssemblyWorker(mob, formation);
        }
    }

    private static void repinAssemblyWorkers(ServerLevel level, Formation formation) {
        int placed = Math.min(formation.assembledSlots, formation.members.size());
        for (int slot = 0; slot < placed; slot++) {
            Mob mob = getMob(level, formation.members.get(slot));
            if (mob == null || !mob.isAlive() || mob.isRemoved()) continue;
            Vec3 position = formation.structuralSlotPositions.getOrDefault(slot,
                    formationSlotPosition(formation, slot, 0.0D));
            formation.assemblyHoldPositions.put(mob.getUUID(), position);
            traceStructuralMove(formation, mob, slot, "assembly_repin", mob.position(), position);
            if (formation.committedFoundationIds.contains(mob.getUUID())) {
                pinAssemblyWorker(mob, position);
                continue;
            }
            ClearanceResolution resolved = resolveClearance(level, mob, position, mob.maxUpStep());
            Vec3 repin = resolved.position() == null ? position : resolved.position();
            if (isEntityClearAt(level, mob, mob.position(), repin)) {
                pinAssemblyWorker(mob, repin);
            } else {
                pinAssemblyWorker(mob, formation.assemblyHoldPositions.getOrDefault(mob.getUUID(), mob.position()));
            }
        }
    }

    private static void holdAssemblyWorker(Mob mob, Formation formation) {
        if (mob == null || formation == null) return;
        Vec3 position = formation.assemblyHoldPositions.computeIfAbsent(mob.getUUID(), ignored -> mob.position());
        ServerLevel level = mob.level() instanceof ServerLevel serverLevel ? serverLevel : null;
        ClearanceResolution resolved = resolveClearance(level, mob, position, mob.maxUpStep());
        Vec3 pinned = resolved.position() == null ? position : resolved.position();
        if (level == null || isEntityClearAt(level, mob, mob.position(), pinned)) {
            pinAssemblyWorker(mob, pinned);
        } else {
            pinAssemblyWorker(mob, mob.position());
        }
    }

    private static void pinAssemblyWorker(Mob mob, Vec3 position) {
        if (mob.position().distanceToSqr(position) > 0.0001D) {
            mob.setPos(position.x, position.y, position.z);
        }
        mob.setNoGravity(true);
        mob.noPhysics = true;
        mob.getNavigation().stop();
        mob.setDeltaMovement(Vec3.ZERO);
        mob.fallDistance = 0.0F;
    }

    static double bridgeLength(BlockPos pivot, BlockPos anchor) {
        double dx = anchor.getX() - pivot.getX();
        double dy = anchor.getY() - pivot.getY();
        double dz = anchor.getZ() - pivot.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static boolean tickElapsed(long now, long previous, long interval) {
        if (interval <= 0L || previous < 0L || now < previous) return true;
        return now - previous >= interval;
    }

    static int chooseColumnCount(int twoColumnBodies, int oneColumnBodies, int availableBodies) {
        return availableBodies >= twoColumnBodies ? 2 : availableBodies >= oneColumnBodies ? 1 : 0;
    }

    static int requiredLayerCount(double horizontalDistance, double verticalDistance,
                                  double horizontalTouchStep, double verticalTouchStep) {
        if (verticalTouchStep <= 0.0D) return Integer.MAX_VALUE;
        double effectiveHStep = horizontalTouchStep > 0.0D ? Math.min(horizontalTouchStep, 0.58D) : 0.58D;
        double effectiveVStep = verticalTouchStep > 0.0D ? Math.min(verticalTouchStep, 1.85D) : 1.85D;
        int hStations = horizontalDistance > 0.05D ? Mth.ceil(horizontalDistance / effectiveHStep) : 0;
        int vStations = verticalDistance > 0.05D ? Mth.ceil(verticalDistance / effectiveVStep) : 0;
        return Math.max(2, Math.max(hStations, vStations) + 1);
    }

    static int requiredLayerCountForTilt(double horizontalDistance, double verticalDistance,
                                         double horizontalTouchStep, double verticalTouchStep) {
        return requiredLayerCount(horizontalDistance, verticalDistance, horizontalTouchStep, verticalTouchStep);
    }

    static int requiredLayerCountForTilt(SitePlan plan, double horizontalTouchStep, double verticalTouchStep) {
        if (plan == null) return Integer.MAX_VALUE;
        BlockPos structuralEnd = tiltStructuralEnd(plan);
        double adjustedVerticalTouchStep = wallLedgeArcAdjustedVerticalTouchStep(
                plan.pivot(), structuralEnd, verticalTouchStep);
        return requiredLayerCountForTilt(horizontalDistance(plan.pivot(), structuralEnd),
                Math.abs(structuralEnd.getY() - plan.pivot().getY()), horizontalTouchStep, adjustedVerticalTouchStep);
    }

    private static double wallLedgeArcAdjustedVerticalTouchStep(BlockPos pivot, BlockPos end, double verticalTouchStep) {
        if (pivot == null || end == null || end.getY() <= pivot.getY()) return verticalTouchStep;



        double bodyHeight = Math.max(0.05D, verticalTouchStep - CONTACT_EPSILON);
        return bodyHeight / (1.0D + 0.3D * Math.PI);
    }

    private static BlockPos tiltStructuralEnd(SitePlan plan) {
        if (plan == null) return null;
        return tiltStructuralEnd(plan.spanEnd(), plan.corridor());
    }

    private static BlockPos tiltStructuralEnd(BlockPos fallback, CanonicalFeetCorridor corridor) {
        return corridor != null && corridor.bridgeLandingFeet() != null ? corridor.bridgeLandingFeet() : fallback;
    }

    private static int requiredLayerCountForTilt(BlockPos pivot, BlockPos spanEnd,
                                                 CanonicalFeetCorridor corridor,
                                                 double horizontalTouchStep, double verticalTouchStep) {
        if (pivot == null || spanEnd == null) return Integer.MAX_VALUE;
        return requiredLayerCountForTilt(horizontalDistance(pivot, spanEnd),
                Math.abs(spanEnd.getY() - pivot.getY()), horizontalTouchStep,
                wallLedgeArcAdjustedVerticalTouchStep(pivot, spanEnd, verticalTouchStep));
    }

    static double tiltRouteLength(SitePlan plan) {
        if (plan == null) return 0.0D;
        return bridgeLength(plan.pivot(), tiltStructuralEnd(plan));
    }

    private static List<Vec3> tiltRoutePoints(BlockPos pivot, BlockPos spanEnd,
                                              CanonicalFeetCorridor corridor) {
        if (pivot == null || spanEnd == null) return List.of();
        List<Vec3> route = new ArrayList<>();
        appendDistinctRoutePoint(route, Vec3.atBottomCenterOf(pivot));
        if (corridor != null) {
            List<BlockPos> centerline = corridor.centerlineCells();
            int end = exactCenterlineIndex(centerline, spanEnd);
            if (end >= 0) {
                int pivotIndex = exactCenterlineIndex(centerline, pivot);
                int start = pivotIndex >= 0 && pivotIndex <= end ? pivotIndex : 0;
                for (int index = start; index <= end; index++) {
                    appendDistinctRoutePoint(route, Vec3.atBottomCenterOf(centerline.get(index)));
                }
            }
        }
        appendDistinctRoutePoint(route, Vec3.atBottomCenterOf(spanEnd));
        return List.copyOf(route);
    }

    public static double calculateInteriorAngleDegrees(Vec3 prevPoint, Vec3 elbowPoint, Vec3 nextPoint) {
        if (prevPoint == null || elbowPoint == null || nextPoint == null) return 0.0D;
        Vec3 u1 = prevPoint.subtract(elbowPoint);
        Vec3 u2 = nextPoint.subtract(elbowPoint);
        double len1Sqr = u1.lengthSqr();
        double len2Sqr = u2.lengthSqr();
        if (len1Sqr < 1.0E-8D || len2Sqr < 1.0E-8D) return 0.0D;
        double dot = u1.dot(u2) / (Math.sqrt(len1Sqr) * Math.sqrt(len2Sqr));
        double clamped = Mth.clamp(dot, -1.0D, 1.0D);
        return Math.toDegrees(Math.acos(clamped));
    }

    private static boolean hasElbowBends(Formation formation) {
        return formation != null && formation.elbowPoints != null && !formation.elbowPoints.isEmpty();
    }

    static List<Vec3> formationRoutePoints(Formation formation, BlockPos structuralEnd) {
        if (formation == null) return List.of();
        if (!hasElbowBends(formation)) {
            CanonicalFeetCorridor corridor = formation.plan == null ? null : formation.plan.corridor();
            return tiltRoutePoints(formation.pivot, structuralEnd, corridor);
        }
        List<Vec3> route = new ArrayList<>();
        Vec3 start = (formation.structuralSlotPositions != null && formation.structuralSlotPositions.containsKey(0))
                ? formation.structuralSlotPositions.get(0)
                : Vec3.atBottomCenterOf(formation.pivot);
        appendDistinctRoutePoint(route, start);
        for (Vec3 elbow : formation.elbowPoints) {
            appendDistinctRoutePoint(route, elbow);
        }
        if (structuralEnd != null) {
            appendDistinctRoutePoint(route, Vec3.atBottomCenterOf(structuralEnd));
        }
        return List.copyOf(route);
    }

    private static void appendDistinctRoutePoint(List<Vec3> route, Vec3 point) {
        if (route == null || point == null) return;
        if (route.isEmpty() || route.get(route.size() - 1).distanceToSqr(point) > 1.0E-8D) route.add(point);
    }

    private static double polylineLength(List<Vec3> route) {
        if (route == null || route.size() < 2) return 0.0D;
        double length = 0.0D;
        for (int index = 1; index < route.size(); index++) {
            length += route.get(index - 1).distanceTo(route.get(index));
        }
        return length;
    }

    private static Vec3 positionAlongRoute(List<Vec3> route, double progress) {
        if (route == null || route.isEmpty()) return Vec3.ZERO;
        if (route.size() == 1) return route.get(0);
        double length = polylineLength(route);
        if (length < 1.0E-8D) return route.get(0);
        double remaining = Mth.clamp(progress, 0.0D, 1.0D) * length;
        for (int index = 1; index < route.size(); index++) {
            Vec3 from = route.get(index - 1);
            Vec3 to = route.get(index);
            double segment = from.distanceTo(to);
            if (segment < 1.0E-8D) continue;
            if (remaining <= segment || index == route.size() - 1) {
                return from.lerp(to, Mth.clamp(remaining / segment, 0.0D, 1.0D));
            }
            remaining -= segment;
        }
        return route.get(route.size() - 1);
    }

    static double initialPillarStep(double bridgeLength, int layerCount, double verticalTouchStep) {
        if (layerCount <= 1 || verticalTouchStep <= 0.0D) {
            return 0.0D;
        }
        double bridgeStep = Double.isFinite(bridgeLength) && bridgeLength > 0.0D
                ? bridgeLength / (layerCount - 1) : 0.0D;
        return Math.max(verticalTouchStep, bridgeStep);
    }

    static double layerSpacingForBodyHeight(double bodyHeight) {
        return Math.max(0.05D, Math.max(0.0D, bodyHeight) + CONTACT_EPSILON);
    }

    static double lateralSpacingForBodyWidth(double bodyWidth) {
        return Math.max(0.05D, Math.max(0.0D, bodyWidth) + STRUCTURAL_NON_OVERLAP_MARGIN);
    }

    static double initialPillarStepForHeight(double verticalDistance, int layerCount, double verticalTouchStep) {
        if (layerCount <= 1 || verticalTouchStep <= 0.0D) return 0.0D;
        double distance = Math.abs(verticalDistance);
        if (distance <= 0.0D) return Math.max(0.05D, verticalTouchStep);
        double step = Math.min(verticalTouchStep + CONTACT_EPSILON, distance / (layerCount - 1));
        return Math.copySign(Math.max(0.05D, step), verticalDistance);
    }

    static boolean isUsablePillarStep(double pillarStep) {
        return Double.isFinite(pillarStep) && Math.abs(pillarStep) > 1.0E-6D;
    }

    static double distanceToSegment(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double length = delta.lengthSqr();
        if (length < 1.0E-8D) return point.distanceTo(start);
        double ratio = Mth.clamp(point.subtract(start).dot(delta) / length, 0.0D, 1.0D);
        return point.distanceTo(start.add(delta.scale(ratio)));
    }

    private static boolean buildersValid(ServerLevel level, Formation formation) {
        for (UUID id : formation.structuralBuilderIds) {
            Mob mob = getMob(level, id);
            if (mob == null || !mob.isAlive() || mob.isRemoved()) return false;
        }
        return formation.phase == Phase.ASSEMBLE
                ? formation.structuralBuilderIds.size() >= formation.columnCount
                : formation.structuralBuilderIds.size() == formation.requiredBodies;
    }

    private static void reserveMob(Mob mob, Formation formation) {
        if (mob == null || formation == null || !canonicalCorridorGate(formation, formation.anchor)) {
            return;
        }
        if (formation.reserved.contains(mob.getUUID())) {
            return;
        }
        formation.reserved.add(mob.getUUID());
        if (mob.level() instanceof ServerLevel level) state(level).mobOwners.putIfAbsent(mob.getUUID(), formation.id);
        applyControlledHold(mob, formation, true);
    }

    private static void driveFormation(ServerLevel level, Formation formation, long now) {
        pruneMissingStructuralMembers(level, formation);
        int missingLayer = firstMissingStructuralLayer(level, formation);
        if (missingLayer >= 0) {
            collapse(level, formation, "structural_member_loss_layer_" + missingLayer);
            return;
        }
        if (!fullChainMovementCountsReady(formation.members.size(), formation.assembledSlots,
                formation.structuralBuilderIds.size(), formation.requiredBodies)) {
            transition(formation, Phase.ASSEMBLE, now, "full_chain_incomplete");
            return;
        }
        if (!structureHasContact(level, formation, formation.requiredBodies)) {
            collapse(level, formation, "movement_chain_contact_lost");
            return;
        }
        if (formation.phase == Phase.TRAVERSE) {
            if (formation.lastAnchoredRepinTick < 0L
                    || now - formation.lastAnchoredRepinTick >= ANCHORED_REPIN_INTERVAL_TICKS) {
                repinAnchoredMembers(level, formation);
                formation.lastAnchoredRepinTick = now;
            }
            if (now - formation.lastValidationTick >= ANCHORED_VALIDATION_INTERVAL) {
                formation.integrity = calculateIntegrity(level, formation);
                formation.lastValidationTick = now;
                if (formation.integrity < INTEGRITY_THRESHOLD) {
                    collapse(level, formation, "anchored_integrity");
                    return;
                } else {
                    formation.recoveryTicks = 0;
                }
                if (!isAnchorStillValid(level, formation)) {
                    collapse(level, formation, "anchored_anchor_invalid");
                    return;
                }
            }
            driveCrossers(level, formation, now);
            return;
        }
        if (formation.phase == Phase.ROTATE) {
            advanceTilt(level, formation, now);
            return;
        }
        repinAnchoredMembers(level, formation);
        if (!structureHasContact(level, formation, formation.requiredBodies)) {
            collapse(level, formation, "anchor_contact_lost");
            return;
        }
        if (!isAnchorStillValid(level, formation)) {
            collapse(level, formation, "anchor_invalid");
            return;
        }
        formation.anchorTicks++;
        if (formation.anchorTicks >= ANCHOR_STABILIZATION_TICKS) {
            if (transitionToMovement(level, formation, Phase.TRAVERSE, now, "anchor_stable")) {
                formation.lastTraverseActivityTick = now;
            }
        }
    }

    private static void pruneMissingStructuralMembers(ServerLevel level, Formation formation) {
        if (level == null || formation == null) return;
        for (UUID id : new ArrayList<>(formation.members)) {
            Mob mob = getMob(level, id);
            if (mob == null || !mob.isAlive() || mob.isRemoved()) releaseMember(level, formation, id);
        }
    }

    private static void repinAnchoredMembers(ServerLevel level, Formation formation) {
        int slot = 0;
        for (UUID id : new ArrayList<>(formation.members)) {
            Mob mob = getMob(level, id);
            if (mob == null || !mob.isAlive() || mob.isRemoved()) {
                slot++;
                continue;
            }
            Vec3 expected = formationSlotPosition(formation, slot, 1.0D);
            ClearanceResolution resolved = resolveClearance(level, mob, expected, mob.maxUpStep(), formation, slot + 1.0D);
            Vec3 target = resolved.position() == null ? expected : resolved.position();
            if (mob.position().distanceToSqr(target) > 0.0025D) {
                mob.setPos(target.x, target.y, target.z);
            }
            mob.getNavigation().stop();
            mob.setNoGravity(true);
            mob.noPhysics = true;
            mob.fallDistance = 0.0F;
            mob.setDeltaMovement(Vec3.ZERO);
            slot++;
        }
    }

    private static void advanceTilt(ServerLevel level, Formation formation, long now) {
        if (!fullChainMovementReady(level, formation)) {
            transition(formation, Phase.ASSEMBLE, now, "full_chain_incomplete_before_tilt");
            return;
        }
        if (formation.plan == null || !isStagingSupportCell(level, formation.pivot)
                || !isSafeLanding(level, tiltStructuralEnd(formation.plan))) {
            collapse(level, formation, "tilt_station_support_lost");
            return;
        }
        if (formation.targetAngle < 0.001D) {
            transitionToMovement(level, formation, Phase.ANCHOR, now, "zero_tilt");
            return;
        }
        if (formation.tiltLayerCursor < 1) {
            if (formation.tiltAngle >= formation.targetAngle - 1.0E-4D) {
                transitionToMovement(level, formation, Phase.ANCHOR, now, "tilt_complete");
                return;
            }
            formation.shiftTick = 0;
            double speed = (LivingWallConfig.TILT_SPEED_RADIANS_PER_UPDATE != null)
                    ? LivingWallConfig.TILT_SPEED_RADIANS_PER_UPDATE.get() : 0.025D;
            int calculatedTicks = (int) Math.ceil(formation.targetAngle / Math.max(0.001D, speed));
            formation.shiftDuration = Math.max(20, calculatedTicks);
            formation.pendingTiltAngle = formation.targetAngle;
            formation.tiltLayerCursor = 1;
        }
        double progress = Math.min(1.0D, (++formation.shiftTick) / (double) formation.shiftDuration);
        for (int slot = 0; slot < formation.requiredBodies; slot++) {
            Mob mob = getMob(level, formation.members.get(slot));
            if (mob == null || !mob.isAlive() || mob.isRemoved()) {
                collapse(level, formation, "tilt_member_lost_slot_" + slot);
                return;
            }
            Vec3 position = formationSlotPosition(formation, slot, progress);
            mob.setPos(position.x, position.y, position.z);
            mob.setNoGravity(true);
            mob.noPhysics = true;
            mob.setDeltaMovement(Vec3.ZERO);
            mob.getNavigation().stop();
            mob.fallDistance = 0.0F;
        }
        formation.lastTiltProgressTick = now;
        if (progress < 1.0D) return;
        formation.tiltAngle = formation.pendingTiltAngle;
        formation.tiltLayerCursor = -1;
        formation.shiftTick = 0;
        transitionToMovement(level, formation, Phase.ANCHOR, now, "tilt_complete");
    }

    private static void restoreTiltSource(ServerLevel level, Formation formation) {
        double sourceProgress = formation.targetAngle <= 0.0D ? 0.0D : formation.tiltAngle / formation.targetAngle;
        for (int slot = 0; slot < formation.members.size(); slot++) {
            Mob mob = getMob(level, formation.members.get(slot));
            if (mob == null || !mob.isAlive() || mob.isRemoved()) continue;
            Vec3 position = formationSlotPosition(formation, slot, sourceProgress);
            mob.setPos(position.x, position.y, position.z);
            mob.setNoGravity(true);
            mob.noPhysics = true;
            mob.setDeltaMovement(Vec3.ZERO);
            mob.getNavigation().stop();
            mob.fallDistance = 0.0F;
        }
    }

    private static void logTiltRecovery(ServerLevel level, Formation formation) {
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_RECOVERY)) {
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Bridge {} tilt_contact_recovery angle={} members={} tick={}",
                    formation.id, formation.tiltAngle, formation.members.size(), level.getGameTime());
        }
    }

    private static double contactSafeTiltAngle(ServerLevel level, Formation formation, double desiredTiltAngle,
                                               int duration) {
        double sourceProgress = formation.tiltAngle / formation.targetAngle;
        double targetProgress = desiredTiltAngle / formation.targetAngle;
        TiltCheck desiredCheck = evaluateTiltWave(level, formation, sourceProgress, targetProgress, duration);
        if (desiredCheck.valid()) {
            logTiltDecision(level, formation, desiredTiltAngle, desiredTiltAngle, desiredCheck, duration);
            return desiredTiltAngle;
        }
        double safeProgress = sourceProgress;
        double unsafeProgress = targetProgress;
        TiltCheck lastFailure = desiredCheck;
        for (int attempt = 0; attempt < 8; attempt++) {
            double candidateProgress = (safeProgress + unsafeProgress) * 0.5D;
            TiltCheck candidateCheck = evaluateTiltWave(level, formation, sourceProgress, candidateProgress, duration);
            if (candidateCheck.valid()) {
                safeProgress = candidateProgress;
            } else {
                unsafeProgress = candidateProgress;
                lastFailure = candidateCheck;
            }
        }
        double safeAngle = safeProgress * formation.targetAngle;
        logTiltDecision(level, formation, desiredTiltAngle, safeAngle, lastFailure, duration);
        return safeAngle;
    }

    private static TiltCheck evaluateTiltWave(ServerLevel level, Formation formation, double sourceProgress,
                                              double targetProgress, int duration) {
        int ticks = Math.max(1, duration);
        for (int movingLayer = 1; movingLayer < formation.layerCount; movingLayer++) {
            for (int tick = 0; tick <= ticks; tick++) {
                double movementProgress = tick / (double) ticks;
                for (int slot = 0; slot < formation.requiredBodies; slot++) {
                    Mob mob = getMob(level, formation.members.get(slot));
                    if (!isStructuralMob(mob)) {
                        return new TiltCheck(false, movingLayer, slot, -1,
                                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                                Double.POSITIVE_INFINITY, "member_missing");
                    }
                    int layer = slot / formation.columnCount;
                    int column = slot % formation.columnCount;
                    if (layer == 0) {
                        if (formation.columnCount == 2 && column == 1) {
                            TiltCheck check = plannedBottomUpChainContact(level, formation, slot, slot - 1,
                                    movingLayer, movementProgress);
                            if (!check.valid()) return check;
                        }
                        continue;
                    }
                    TiltCheck check = plannedBottomUpChainContact(level, formation, slot,
                            slot - formation.columnCount, movingLayer, movementProgress);
                    if (!check.valid()) return check;
                }
            }
        }
        return new TiltCheck(true, -1, -1, -1, 0.0D, 0.0D, 0.0D, "ok");
    }

    private static void releaseUncommittedRecruiters(ServerLevel level, Formation formation) {
        if (level == null || formation == null) return;
        for (UUID id : new ArrayList<>(formation.ticketGoals.keySet())) {
            if (!formation.members.contains(id) && !formation.pendingBuilderIds.contains(id)) {
                releaseRecruiter(level, formation, id, level.getGameTime());
            }
        }
        for (UUID id : new ArrayList<>(formation.candidateHoldingGoals.keySet())) {
            Mob mob = getMob(level, id);
            if (mob != null) removeCandidateHolding(mob, formation);
            else {
                formation.candidateHoldingGoals.remove(id);
                formation.candidateHoldingCells.remove(id);
            }
            releaseBridgeOwnership(level, formation, id);
            formation.claimedWorkerIds.remove(id);
            formation.reserved.remove(id);
        }
        for (UUID id : new ArrayList<>(formation.reserved)) {
            if (formation.members.contains(id) || formation.transitCrosserIds.contains(id)) {
                continue;
            }
            releaseRecruiter(level, formation, id, level.getGameTime());
            formation.reserved.remove(id);
            formation.pendingBuilderIds.remove(id);
            formation.structuralBuilderIds.remove(id);
            formation.claimedWorkerIds.remove(id);
            releaseBridgeOwnership(level, formation, id);
        }
        formation.pendingBuilderIds.removeIf(id -> !formation.members.contains(id));
    }

    private static TiltCheck plannedTiltContactAtProgress(ServerLevel level, Formation formation, int firstSlot,
                                                          int secondSlot, double progress) {
        Mob first = getMob(level, formation.members.get(firstSlot));
        Mob second = getMob(level, formation.members.get(secondSlot));
        if (!isStructuralMob(first) || !isStructuralMob(second)) {
            return new TiltCheck(false, -1, firstSlot, secondSlot,
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY, "member_missing");
        }
        Vec3 firstPosition = formationSlotPosition(formation, firstSlot, progress);
        Vec3 secondPosition = formationSlotPosition(formation, secondSlot, progress);
        AABB firstBox = first.getBoundingBox().move(firstPosition.subtract(first.position()));
        AABB secondBox = second.getBoundingBox().move(secondPosition.subtract(second.position()));
        double gapX = axisGap(firstBox.minX, firstBox.maxX, secondBox.minX, secondBox.maxX);
        double gapY = axisGap(firstBox.minY, firstBox.maxY, secondBox.minY, secondBox.maxY);
        double gapZ = axisGap(firstBox.minZ, firstBox.maxZ, secondBox.minZ, secondBox.maxZ);
        double tiltContactTolerance = Math.max(0.0D, CONTACT_EPSILON - TILT_CONTACT_MARGIN);
        boolean valid = controlledTiltBodiesConnected(firstBox, secondBox, tiltContactTolerance);
        return new TiltCheck(valid, -1, firstSlot, secondSlot, gapX, gapY, gapZ,
                valid ? "ok" : "contact_gap");
    }

    private static boolean plannedWaveContact(ServerLevel level, Formation formation, int firstSlot, int secondSlot,
                                              int movingLayer, double sourceProgress, double targetProgress,
                                              double movementProgress) {
        return plannedWaveContactCheck(level, formation, firstSlot, secondSlot, movingLayer,
                sourceProgress, targetProgress, movementProgress).valid();
    }

    private static TiltCheck plannedWaveContactCheck(ServerLevel level, Formation formation, int firstSlot,
                                                     int secondSlot, int movingLayer, double sourceProgress,
                                                     double targetProgress, double movementProgress) {
        Mob first = getMob(level, formation.members.get(firstSlot));
        Mob second = getMob(level, formation.members.get(secondSlot));
        if (!isStructuralMob(first) || !isStructuralMob(second)) {
            return new TiltCheck(false, movingLayer, firstSlot, secondSlot,
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY, "member_missing");
        }
        Vec3 firstPosition = tiltWavePosition(formation, firstSlot, movingLayer,
                sourceProgress, targetProgress, movementProgress);
        Vec3 secondPosition = tiltWavePosition(formation, secondSlot, movingLayer,
                sourceProgress, targetProgress, movementProgress);
        AABB firstBox = first.getBoundingBox().move(firstPosition.subtract(first.position()));
        AABB secondBox = second.getBoundingBox().move(secondPosition.subtract(second.position()));
        double gapX = axisGap(firstBox.minX, firstBox.maxX, secondBox.minX, secondBox.maxX);
        double gapY = axisGap(firstBox.minY, firstBox.maxY, secondBox.minY, secondBox.maxY);
        double gapZ = axisGap(firstBox.minZ, firstBox.maxZ, secondBox.minZ, secondBox.maxZ);
        double tiltContactTolerance = Math.max(0.0D, CONTACT_EPSILON - TILT_CONTACT_MARGIN);
        boolean valid = controlledTiltBodiesConnected(firstBox, secondBox, tiltContactTolerance);
        return new TiltCheck(valid, movingLayer, firstSlot, secondSlot, gapX, gapY, gapZ,
                valid ? "ok" : "contact_gap");
    }

    private static TiltCheck plannedBottomUpChainContact(ServerLevel level, Formation formation,
                                                         int firstSlot, int secondSlot,
                                                         int movingLayer, double movementProgress) {
        Mob first = getMob(level, formation.members.get(firstSlot));
        Mob second = getMob(level, formation.members.get(secondSlot));
        if (!isStructuralMob(first) || !isStructuralMob(second)) {
            return new TiltCheck(false, movingLayer, firstSlot, secondSlot,
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY, "member_missing");
        }
        Vec3 firstPosition = bottomUpChainPosition(formation, firstSlot, movingLayer, movementProgress);
        Vec3 secondPosition = bottomUpChainPosition(formation, secondSlot, movingLayer, movementProgress);
        AABB firstBox = first.getBoundingBox().move(firstPosition.subtract(first.position()));
        AABB secondBox = second.getBoundingBox().move(secondPosition.subtract(second.position()));
        double gapX = axisGap(firstBox.minX, firstBox.maxX, secondBox.minX, secondBox.maxX);
        double gapY = axisGap(firstBox.minY, firstBox.maxY, secondBox.minY, secondBox.maxY);
        double gapZ = axisGap(firstBox.minZ, firstBox.maxZ, secondBox.minZ, secondBox.maxZ);
        double tiltContactTolerance = Math.max(0.0D, CONTACT_EPSILON - TILT_CONTACT_MARGIN);
        boolean valid = controlledTiltBodiesConnected(firstBox, secondBox, tiltContactTolerance);
        return new TiltCheck(valid, movingLayer, firstSlot, secondSlot, gapX, gapY, gapZ,
                valid ? "ok" : "contact_gap");
    }

    private static void logTiltDecision(ServerLevel level, Formation formation, double desiredAngle,
                                        double safeAngle, TiltCheck check, int duration) {
        if (SoundAttractConfig.COMMON == null || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_RECOVERY)
                || level == null || formation == null || check == null) return;
        long now = level.getGameTime();
        boolean blocked = safeAngle <= formation.tiltAngle + 1.0E-5D;
        if (!blocked && formation.lastTiltDiagnosticTick >= 0L
                && now - formation.lastTiltDiagnosticTick < FORMATION_DIAGNOSTIC_INTERVAL_TICKS / 2L) return;
        formation.lastTiltDiagnosticTick = now;
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Tilt decision bridge={} result={} currentAngle={} desiredAngle={} safeAngle={} movingLayer={} firstSlot={} secondSlot={} gaps=({},{},{}) reason={} duration={} tick={}",
                formation.id, blocked ? "blocked" : check.valid() ? "accepted" : "clamped",
                formation.tiltAngle, desiredAngle, safeAngle, check.movingLayer(), check.firstSlot(),
                check.secondSlot(), check.gapX(), check.gapY(), check.gapZ(), check.reason(), duration, now);
    }

    static double tiltWaveProgress(int layer, int movingLayer, double sourceProgress, double targetProgress,
                                   double movementProgress) {
        if (layer < movingLayer) return targetProgress;
        if (layer > movingLayer) return sourceProgress;
        return Mth.lerp(Mth.clamp(movementProgress, 0.0D, 1.0D), sourceProgress, targetProgress);
    }

    private static Vec3 tiltWavePosition(Formation formation, int slot, int movingLayer,
                                          double sourceProgress, double targetProgress, double movementProgress) {
        int layer = slot / formation.columnCount;
        double progress = tiltWaveProgress(layer, movingLayer, sourceProgress, targetProgress, movementProgress);
        return formationSlotPosition(formation, slot, progress);
    }

    private static Vec3 bottomUpChainPosition(Formation formation, int slot, int movingLayer,
                                              double movementProgress) {
        int columns = Math.max(1, formation.columnCount);
        int layer = Math.max(0, slot) / columns;
        int column = Math.max(0, slot) % columns;
        if (layer < movingLayer) return formationSlotPosition(formation, slot, 1.0D);

        int movingSlot = movingLayer * columns + column;
        int parentSlot = (movingLayer - 1) * columns + column;
        Vec3 parentFinal = formationSlotPosition(formation, parentSlot, 1.0D);
        Vec3 movingSource = formationSlotPosition(formation, movingSlot, 0.0D);
        Vec3 parentSource = formationSlotPosition(formation, parentSlot, 0.0D);
        Vec3 movingStart = parentFinal.add(movingSource.subtract(parentSource));
        Vec3 movingEnd = formationSlotPosition(formation, movingSlot, 1.0D);
        Vec3 movingPosition = movingStart.lerp(movingEnd, Mth.clamp(movementProgress, 0.0D, 1.0D));
        if (layer == movingLayer) return movingPosition;

        Vec3 sourcePosition = formationSlotPosition(formation, slot, 0.0D);
        return movingPosition.add(sourcePosition.subtract(movingSource));
    }

    private static boolean tiltStationsHaveTerrainSupport(ServerLevel level, CanonicalFeetCorridor corridor) {
        if (level == null || corridor == null || corridor.centerlineCells().isEmpty()) return false;
        for (BlockPos station : corridor.centerlineCells()) {
            if (station == null || !level.isLoaded(station) || !level.isLoaded(station.below())
                    || !hasStableSupport(level, station.below(), level.getBlockState(station.below()))) return false;
        }
        return true;
    }

    private static int shiftDuration(ServerLevel level) {
        double averageTickMs = level.getServer().getAverageTickTimeNanos() / 1_000_000.0;
        return Mth.clamp(Mth.ceil((float) (MIN_SHIFT_TICKS * Math.max(1.0D, averageTickMs / 50.0D))),
                MIN_SHIFT_TICKS, MAX_SHIFT_TICKS);
    }

    private static int rotationTimeoutTicks(Formation formation) {
        int layers = formation == null ? 1 : Math.max(1, formation.layerCount - 1);
        return Math.max(ROTATION_TIMEOUT_TICKS, layers * MAX_SHIFT_TICKS + 80);
    }

    private static boolean structureHasContact(ServerLevel level, Formation formation, int slotCount) {
        int limit = Math.min(slotCount, formation.members.size());
        for (int slot = 0; slot < limit; slot++) {
            int layer = slot / formation.columnCount;
            int column = slot % formation.columnCount;
            if (layer == 0) {
                if (formation.columnCount == 2 && column == 1) {
                    if (!membersTouch(level, formation, slot, slot - 1)) return false;
                }
                continue;
            }
            int below = slot - formation.columnCount;
            if (below < 0 || !membersTouch(level, formation, slot, below)) return false;
        }
        return true;
    }

    private static boolean membersTouch(ServerLevel level, Formation formation, int firstSlot, int secondSlot) {
        Mob first = getMob(level, formation.members.get(firstSlot));
        Mob second = getMob(level, formation.members.get(secondSlot));
        if (first == null || second == null || !first.isAlive() || !second.isAlive()) return false;
        if (formation.plan != null && (formation.plan.scenario() == ConstructionScenario.WALL_LEDGE
                || formation.plan.scenario() == ConstructionScenario.DROP_TO_LEDGE
                || isDirectSpan(formation.plan.scenario())
                || hasElbowBends(formation))) {
            double step = Math.max(formation.pillarStep, formation.horizontalTouchStep);
            double allowedDist = Math.max(3.0D, step + 1.0D);
            double maxDistanceSqr = (formation.plan.scenario() == ConstructionScenario.DROP_TO_LEDGE
                    || hasElbowBends(formation)
                    || isDirectSpan(formation.plan.scenario()))
                    && Math.min(firstSlot, secondSlot) == 0 ? 25.0D : allowedDist * allowedDist;
            return first.distanceToSqr(second) <= maxDistanceSqr;
        }
        AABB a = first.getBoundingBox();
        AABB b = second.getBoundingBox();
        boolean controlledTiltPose = formation.plan != null && requiresTilt(formation.plan.scenario())
                && formation.phase != Phase.SCAN && formation.phase != Phase.ASSEMBLE;
        return controlledTiltPose
                ? controlledTiltBodiesConnected(a, b, CONTACT_EPSILON - TILT_CONTACT_MARGIN)
                : structuralBodiesTouch(a, b);
    }

    private static boolean isStructuralMob(Mob mob) {
        return mob != null && mob.isAlive() && !mob.isRemoved();
    }

    private static boolean bodiesTouchAt(Mob first, Vec3 firstPosition, Mob second, Vec3 secondPosition) {
        AABB firstBox = first.getBoundingBox().move(firstPosition.subtract(first.position()));
        AABB secondBox = second.getBoundingBox().move(secondPosition.subtract(second.position()));
        return structuralBodiesTouch(firstBox, secondBox);
    }

    static boolean structuralBodiesTouch(AABB first, AABB second) {
        return structuralBodiesTouch(first, second, CONTACT_EPSILON);
    }

    private static boolean structuralBodiesTouch(AABB first, AABB second, double contactTolerance) {
        if (first == null || second == null) return false;
        double firstCenterX = (first.minX + first.maxX) * 0.5D;
        double firstCenterZ = (first.minZ + first.maxZ) * 0.5D;
        double secondCenterX = (second.minX + second.maxX) * 0.5D;
        double secondCenterZ = (second.minZ + second.maxZ) * 0.5D;
        double horizontalDistance = Math.sqrt(Mth.square(firstCenterX - secondCenterX)
                + Mth.square(firstCenterZ - secondCenterZ));
        double firstRadius = Math.max(first.maxX - first.minX, first.maxZ - first.minZ) * 0.5D;
        double secondRadius = Math.max(second.maxX - second.minX, second.maxZ - second.minZ) * 0.5D;
        double horizontalContactDistance = firstRadius + secondRadius;
        double horizontalGap = Math.max(0.0D, horizontalDistance - horizontalContactDistance);
        double verticalOverlap = axisOverlap(first.minY, first.maxY, second.minY, second.maxY);
        double verticalGap = axisGap(first.minY, first.maxY, second.minY, second.maxY);
        boolean horizontalContact = verticalOverlap > OVERLAP_EPSILON
                && horizontalDistance + OVERLAP_EPSILON >= horizontalContactDistance
                && horizontalGap <= contactTolerance + OVERLAP_EPSILON;
        boolean verticalContact = verticalOverlap <= OVERLAP_EPSILON
                && verticalGap <= contactTolerance + OVERLAP_EPSILON
                && horizontalDistance <= horizontalContactDistance + contactTolerance + OVERLAP_EPSILON;
        return horizontalContact || verticalContact;
    }

    static boolean controlledTiltBodiesConnected(AABB first, AABB second, double contactTolerance) {
        if (first == null || second == null) return false;
        Vec3 firstCenter = new Vec3((first.minX + first.maxX) * 0.5D, first.minY, (first.minZ + first.maxZ) * 0.5D);
        Vec3 secondCenter = new Vec3((second.minX + second.maxX) * 0.5D, second.minY, (second.minZ + second.maxZ) * 0.5D);
        if (firstCenter.distanceToSqr(secondCenter) <= 4.84D) {
            return true;
        }
        return structuralBodiesTouch(first, second, Math.max(0.0D, contactTolerance))
                || boxesPenetrate(first, second);
    }

    private static boolean structuralBodiesPenetrate(AABB first, AABB second) {
        if (first == null || second == null
                || axisOverlap(first.minY, first.maxY, second.minY, second.maxY) <= 0.15D) {
            return false;
        }
        double firstCenterX = (first.minX + first.maxX) * 0.5D;
        double firstCenterZ = (first.minZ + first.maxZ) * 0.5D;
        double secondCenterX = (second.minX + second.maxX) * 0.5D;
        double secondCenterZ = (second.minZ + second.maxZ) * 0.5D;
        double horizontalDistance = Math.sqrt(Mth.square(firstCenterX - secondCenterX)
                + Mth.square(firstCenterZ - secondCenterZ));
        double firstRadius = Math.max(first.maxX - first.minX, first.maxZ - first.minZ) * 0.5D;
        double secondRadius = Math.max(second.maxX - second.minX, second.maxZ - second.minZ) * 0.5D;
        return horizontalDistance + OVERLAP_EPSILON < 0.15D;
    }

    static boolean boxesPenetrate(AABB first, AABB second) {
        if (first == null || second == null) return false;
        return axisOverlap(first.minX, first.maxX, second.minX, second.maxX) > OVERLAP_EPSILON
                && axisOverlap(first.minY, first.maxY, second.minY, second.maxY) > OVERLAP_EPSILON
                && axisOverlap(first.minZ, first.maxZ, second.minZ, second.maxZ) > OVERLAP_EPSILON;
    }

    private static double axisOverlap(double firstMin, double firstMax, double secondMin, double secondMax) {
        return Math.min(firstMax, secondMax) - Math.max(firstMin, secondMin);
    }

    private static double axisGap(double firstMin, double firstMax, double secondMin, double secondMax) {
        return Math.max(0.0D, Math.max(firstMin - secondMax, secondMin - firstMax));
    }

    private static void rebuildAfterStructuralLoss(ServerLevel level, Formation formation, int lostLayer) {
        if (formation.phase == Phase.ROTATE || formation.phase == Phase.ANCHOR || formation.phase == Phase.TRAVERSE
                || formation.tiltAngle > 0.0D || lostLayer <= 0) {
            collapse(level, formation, "structural_member_loss");
            return;
        }
        int firstRemovedSlot = structuralLayerFirstSlot(lostLayer, formation.columnCount);
        for (int slot = formation.members.size() - 1; slot >= firstRemovedSlot; slot--) {
            releaseMember(level, formation, formation.members.get(slot));
        }
        formation.assembledSlots = Math.min(formation.assembledSlots, firstRemovedSlot);
        formation.tiltLayerCursor = -1;
        formation.tiltAngle = 0.0D;
        formation.pendingTiltAngle = 0.0D;
        formation.anchorTicks = 0;
        long now = level.getGameTime();
        transition(formation, Phase.ASSEMBLE, now, "structural_loss_layer_" + lostLayer);
        formation.lastRecruitmentProgressTick = now;
        formation.lastTiltProgressTick = now;
    }

    private static int firstMissingStructuralLayer(ServerLevel level, Formation formation) {
        for (int slot = 0; slot < formation.members.size(); slot++) {
            Mob mob = getMob(level, formation.members.get(slot));
            if (mob == null || !mob.isAlive() || mob.isRemoved()) return structuralLayerForSlot(slot, formation.columnCount);
        }
        return -1;
    }

    static int structuralLayerForSlot(int slot, int columns) {
        return slot < 0 || columns <= 0 ? -1 : slot / columns;
    }

    static int structuralLayerFirstSlot(int layer, int columns) {
        return layer <= 0 || columns <= 0 ? 0 : layer * columns;
    }

    private static boolean formationSlotsClear(ServerLevel level, Formation formation, double progress) {
        int slot = 0;
        for (UUID id : formation.members) {
            Mob mob = getMob(level, id);
            if (mob == null || !mob.isAlive() || mob.isRemoved()
                    || !isSlotClear(level, mob, formationSlotPosition(formation, slot, progress))) {
                return false;
            }
            slot++;
        }
        return true;
    }

    private static Vec3 tryArticulateAroundObstacle(ServerLevel level, Formation formation, int nextSlot,
                                                    Mob mob, Vec3 currentSlotPos, BlockPos blocker) {
        if (!isServerThread(level)) return null;
        ArticulationCandidate candidate = solveArticulationCandidate(level, formation, nextSlot, mob, blocker, null);
        if (candidate == null) return null;
        formation.elbowPoints.clear();
        formation.elbowPoints.addAll(candidate.bends());
        formation.structuralSlotPositions.clear();
        formation.structuralSlotPositions.putAll(candidate.slots());
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] route_repair_selected bridge={} slot={} bend={} fingerprint={} angle={}",
                formation.id, nextSlot, candidate.bend(), candidate.fingerprint(), candidate.angle());
        return candidate.bend();
    }

    private static boolean assembleFormation(ServerLevel level, Formation formation, long now) {
        countCommittedFoundationIfLocked(level, formation);
        int required = Math.min(formation.requiredBodies, formation.members.size());
        if (required < formation.columnCount) {
            return false;
        }
        if (!assemblyPlacementSettleElapsed(formation.lastAssemblyTick, now, ASSEMBLY_SLOT_INTERVAL_TICKS)) {
            return false;
        }
        int nextSlot = formation.assembledSlots;
        UUID nextWorkerId = nextSlot < formation.members.size() ? formation.members.get(nextSlot) : null;
        if (formation.activeStructuralSlot != nextSlot || !Objects.equals(formation.activeStructuralWorkerId, nextWorkerId)) {
            formation.activeStructuralSlot = nextSlot;
            formation.activeStructuralWorkerId = nextWorkerId;
            formation.activeStructuralRouteRetries = 0;
            resetStructuralRoute(formation, nextSlot, now);
        }
        if (nextSlot < required) {
            Mob mob = getMob(level, formation.members.get(nextSlot));
            if (mob == null || !mob.isAlive() || mob.isRemoved()) {
                logAssemblyFailure(level, formation, nextSlot, mob, "member_missing", slotPositionOrNull(formation, nextSlot), null);
                formation.assemblyFatal = true;
                return false;
            }
            Vec3 slotPosition = formation.structuralSlotPositions.getOrDefault(nextSlot,
                    formationSlotPosition(formation, nextSlot, 0.0D));
            if (!formation.activeMemberSuctionEngaged
                    && formation.terminalSafeBaseFailures.contains(mob.getUUID())) {
                rerouteOrReleaseStructuralMember(level, formation, mob, nextSlot, "terminal_safe_base_failure");
                return false;
            }
            IntakeWindow intakeWindow = formation.assemblyIntakeProvenance.get(mob.getUUID());
            BlockPos intakeCell = intakeWindow == null ? null : intakeWindow.feederCell();
            boolean foundationWithoutIntake = foundationMayAssembleWithoutIntake(
                    nextSlot, formation.columnCount, intakeCell);
            if (!foundationWithoutIntake && !isValidIntakeWindow(level, intakeWindow, formation)) {
                if (formation.plan == null || (formation.plan.scenario() != ConstructionScenario.WALL_LEDGE
                        && formation.plan.scenario() != ConstructionScenario.DROP_TO_LEDGE
                        && !isDirectSpan(formation.plan.scenario()))) {
                    logAssemblyFailure(level, formation, nextSlot, mob, "intake_window_invalid", slotPosition, intakeCell);
                    formation.assemblyFatal = true;
                    return false;
                }
            }
            if (!hasVerifiedSupport(level, formation, nextSlot, mob, slotPosition)) {
                logAssemblyFailure(level, formation, nextSlot, mob, "support_invalid", slotPosition, intakeCell);
                formation.assemblyFatal = true;
                return false;
            }
            ClearanceResolution clearance = resolveClearance(level, mob, slotPosition, mob.maxUpStep(), formation, nextSlot + 1.0D);
            if (!clearance.clear()) {
                BlockPos blocker = clearance.blocker() != null ? clearance.blocker() : findBlockingSlotBlock(level, mob, slotPosition);
                Vec3 articulatedPos = tryArticulateAroundObstacle(level, formation, nextSlot, mob, slotPosition, blocker);
                if (articulatedPos == null) {
                    if (clearance.position() != null && isSlotClear(level, mob, clearance.position())) {
                        articulatedPos = clearance.position();
                    } else if (blocker != null) {
                        Vec3 away = slotPosition.subtract(Vec3.atCenterOf(blocker));
                        Vec3 horizontalAway = new Vec3(away.x, 0.0D, away.z);
                        if (horizontalAway.lengthSqr() > 1.0E-4D) {
                            Vec3 shifted = slotPosition.add(horizontalAway.normalize().scale(0.5D));
                            if (isSlotClear(level, mob, shifted)) {
                                articulatedPos = shifted;
                            }
                        }
                    } else {
                        Vec3 lifted = slotPosition.add(0.0D, 0.5D, 0.0D);
                        if (isSlotClear(level, mob, lifted)) {
                            articulatedPos = lifted;
                        }
                    }
                }
                if (articulatedPos != null) {
                    slotPosition = articulatedPos;
                    formation.structuralSlotPositions.put(nextSlot, slotPosition);
                } else if (++formation.activeStructuralRouteRetries < 25) {
                    return false;
                } else {
                    if (formation.lastAssemblyFailureLogTick != now
                            && (SoundAttractConfig.COMMON == null || DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_FORMATION))) {
                        formation.lastAssemblyFailureLogTick = now;
                        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Assembly slot blocked: target={}, pivot={}, anchor={}, slot={}, mob={}, mobPos={}, blocker={}, blockerState={}",
                                formation.target, formation.pivot, formation.anchor, nextSlot, mob.getUUID(), mob.position(),
                                blocker, blocker == null ? "unknown" : level.getBlockState(blocker));
                    }
                    formation.assemblyFatal = true;
                    return false;
                }
            }
            Vec3 assemblyTarget = slotPosition;
            StructuralRoute route = nextSlot < formation.columnCount ? null : structuralRoute(formation, nextSlot, mob, level);
            if (nextSlot >= formation.columnCount) {
                if (route == null) {
                    rerouteOrReleaseStructuralMember(level, formation, mob, nextSlot, "no_free_staging_pose");
                    return false;
                }
                assemblyTarget = route.waypoint();
            }
            boolean baseStaging = !assemblyTarget.equals(slotPosition);
            if (formation.plan != null) {
                Vec3 base = formation.structuralSlotPositions.getOrDefault(0,
                        formationSlotPosition(formation, 0, 0.0D));
                SafeBaseNavigationReservation reservation = formation.safeBaseNavigation.get(mob.getUUID());
                if (!formation.activeMemberSuctionEngaged && reservation == null) {
                    reservation = findSafeBaseNavigationRoute(level, formation, mob, nextSlot);
                    if (reservation != null) formation.safeBaseNavigation.put(mob.getUUID(), reservation);
                }
                formation.pendingSafeBaseReplans.remove(mob.getUUID());
                Vec3 safeStaging = reservation == null ? base : Vec3.atBottomCenterOf(reservation.staging());
                boolean atBase = mob.position().distanceToSqr(safeStaging) <= 1.0D;
                if (!formation.activeMemberSuctionEngaged && atBase) {
                    formation.activeMemberSuctionEngaged = true;
                    finishMotionDiagnostic(formation, now, "reached_base");
                }
                if (!formation.activeMemberSuctionEngaged) {
                    if (reservation == null) {
                        mob.getNavigation().moveTo(base.x, base.y, base.z, 1.25D);
                        boolean progressed = hasStructuralProgress(formation,
                                mob.position().distanceToSqr(base), now);
                        if (!progressed && structuralNoProgressExceeded(formation, now)) {
                            rerouteOrReleaseStructuralMember(level, formation, mob, nextSlot,
                                    "safe_source_staging_path_rejected");
                        }
                        return false;
                    }
                    if (!isStructuralApproachLaneAvailable(level, formation, mob.getUUID(), reservation.pathNodes(),
                            mob.getBbWidth())) {
                        rerouteOrReleaseStructuralMember(level, formation, mob, nextSlot,
                                "structural_lane_conflict");
                        return false;
                    }
                    if (!persistSafeBaseClaim(level, formation, mob, reservation)) {
                        mob.getNavigation().stop();
                        mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.0D);
                        rerouteOrReleaseStructuralMember(level, formation, mob, nextSlot,
                                "durable_route_claim_rejected");
                        return false;
                    }
                    if (!claimStructuralApproachLane(formation, mob.getUUID(), reservation,
                            mob.getBbWidth(), now)) {
                        holdDurableRouteOwner(mob);
                        formation.terminalSafeBaseFailures.add(mob.getUUID());
                        SoundAttractMod.LOGGER.warn("[LivingBridgeSafeBaseRoute] lane_transfer_rejected formation={} owner={} revision={} staging={} priorClaimRetained=true",
                                formation.id, mob.getUUID(), reservation.routeRevision(), reservation.staging());
                        return false;
                    }
                    com.example.soundattract.component.LivingBridgeEntityData.get(mob).putBoolean("livingBridgeSafeBaseRouteActive", true);
                    LivingBridgeMotionDiagnostics.Attempt diagnostic = startOrContinueMotionDiagnostic(
                            level, formation, mob, nextSlot, Vec3.atBottomCenterOf(reservation.staging()), now);
                    if (mob.isNoGravity() || mob.noPhysics) {
                        mob.setNoGravity(false);
                        mob.noPhysics = false;
                    }
                    Boolean commandAccepted = null;
                    if (formation.safeBaseRouteIssued.add(mob.getUUID())) {
                        LivingBridgeSafeBaseRoute.Result pathValidation = safeBasePathValidation(
                                level, mob, formation, reservation, reservation.certifiedPath());
                        if (pathValidation.safe()) {
                            commandAccepted = mob.getNavigation().moveTo(reservation.certifiedPath(), 1.25D);
                        } else {
                            commandAccepted = false;
                            formation.terminalSafeBaseFailures.add(mob.getUUID());
                            logSafeBaseRouteOutcome(level, formation, mob, reservation,
                                    "path_rejected", pathValidation);
                        }
                    } else if (mob.getNavigation().isDone()
                            && mob.position().distanceToSqr(Vec3.atBottomCenterOf(reservation.staging())) > 0.16D) {
                        formation.terminalSafeBaseFailures.add(mob.getUUID());
                        holdSafeBaseNavigation(mob);
                        return false;
                    }
                    LivingBridgeMotionDiagnostics.sample(diagnostic, mob, now, commandAccepted, false);
                    boolean progressed = hasStructuralProgress(formation,
                            mob.position().distanceToSqr(Vec3.atBottomCenterOf(reservation.staging())), now);
                    if (!progressed && structuralNoProgressExceeded(formation, now)) {
                        rerouteOrReleaseStructuralMember(level, formation, mob, nextSlot, "zero_progress_to_base");
                        return false;
                    }
                    formation.lastAssemblyProgressTick = formation.activeStructuralProgressTick;
                    logAssemblyProgress(formation, mob, nextSlot,
                            mob.position().distanceTo(Vec3.atBottomCenterOf(reservation.staging())), now);
                    return false;
                }
                formation.safeBaseNavigation.remove(mob.getUUID());
                com.example.soundattract.component.LivingBridgeEntityData.get(mob).remove("livingBridgeSafeBaseRouteActive");
            }
            Vec3 offset = assemblyTarget.subtract(mob.position());
            double distance = offset.length();
            if (!mob.noPhysics || !mob.isNoGravity()) {
                mob.setNoGravity(true);
                mob.noPhysics = true;
                mob.getNavigation().stop();
                mob.setDeltaMovement(Vec3.ZERO);
            }
            double waypointThreshold = (route != null && !route.isFinalWaypoint()) ? 0.25D : 0.08D;
            double climbSpeed = LivingWallConfig.CLIMB_SPEED_BLOCKS_PER_TICK.get();
            double cappedStep = Math.min(Math.min(climbSpeed, 1.5D), distance);
            if (distance > waypointThreshold && distance > cappedStep) {
                traceStructuralMove(formation, mob, nextSlot,
                        (route != null && !route.isFinalWaypoint()) ? "assembly_along_bridge"
                                : (baseStaging ? "assembly_base_staging" : "assembly_destination"),
                        mob.position(), assemblyTarget);
                Vec3 step = offset.scale(cappedStep / distance);
                Vec3 next = mob.position().add(step);
                if (!isStructuralPoseClear(level, mob, mob.position(), next)) {
                    Vec3 nextYOnly = mob.position().add(0.0D, step.y, 0.0D);
                    Vec3 nextXZOnly = mob.position().add(step.x, 0.0D, step.z);
                    boolean preferXz = step.y <= 0.0D;
                    boolean xzClear = (Math.abs(step.x) > 1.0E-5D || Math.abs(step.z) > 1.0E-5D)
                            && isStructuralPoseClear(level, mob, mob.position(), nextXZOnly);
                    boolean yClear = Math.abs(step.y) > 1.0E-5D
                            && isStructuralPoseClear(level, mob, mob.position(), nextYOnly);
                    if (preferXz && xzClear) {
                        next = nextXZOnly;
                    } else if (!preferXz && yClear) {
                        next = nextYOnly;
                    } else if (preferXz && yClear) {
                        next = nextYOnly;
                    } else if (!preferXz && xzClear) {
                        next = nextXZOnly;
                    } else {
                        double upStep = Math.min(1.0D, Math.max(0.6D, mob != null ? (double) mob.maxUpStep() : 0.6D));
                        Vec3 nextUp = mob.position().add(0.0D, Math.min(cappedStep, upStep), 0.0D);
                        Vec3 nextXZUp = nextXZOnly.add(0.0D, upStep, 0.0D);
                        if ((Math.abs(step.x) > 1.0E-5D || Math.abs(step.z) > 1.0E-5D)
                                && isStructuralPoseClear(level, mob, mob.position(), nextXZUp)) {
                            next = nextXZUp;
                            if (formation != null) formation.activeStructuralProgressTick = now;
                        } else if ((Math.abs(step.x) > 1.0E-5D || Math.abs(step.z) > 1.0E-5D)
                                && isStructuralPoseClear(level, mob, mob.position(), nextUp)
                                && isStructuralPoseClear(level, mob, nextUp, nextXZUp)) {
                            next = nextUp;
                            if (formation != null) formation.activeStructuralProgressTick = now;
                        } else {
                            rerouteOrReleaseStructuralMember(level, formation, mob, nextSlot, "blocked_step");
                            return false;
                        }
                    }
                }
                boolean progressed = hasStructuralProgress(formation, next.distanceToSqr(assemblyTarget), now);
                if (!progressed && structuralNoProgressExceeded(formation, now)) {
                    rerouteOrReleaseStructuralMember(level, formation, mob, nextSlot, "zero_progress");
                    return false;
                }
                if (progressed) {
                    formation.activeStructuralRouteRetries = 0;
                }
                mob.setDeltaMovement(Vec3.ZERO);
                mob.setPos(next.x, next.y, next.z);
                formation.assemblyHoldPositions.put(mob.getUUID(), mob.position());
                mob.fallDistance = 0.0F;
                formation.lastAssemblyProgressTick = formation.activeStructuralProgressTick;
                logAssemblyProgress(formation, mob, nextSlot, distance, now);
                return false;
            }
            if (!isStructuralPoseClear(level, mob, mob.position(), assemblyTarget)) {
                double progress = formation != null ? formation.assembledSlots + 1.0D : 0.0D;
                if (distance <= 0.25D && resolveClearance(level, mob, assemblyTarget, mob != null ? mob.maxUpStep() : 0.0D, formation, progress).clear()) {

                } else {
                    rerouteOrReleaseStructuralMember(level, formation, mob, nextSlot, "blocked_step");
                    return false;
                }
            }
            mob.setDeltaMovement(Vec3.ZERO);
            mob.setPos(assemblyTarget.x, assemblyTarget.y, assemblyTarget.z);
            formation.assemblyHoldPositions.put(mob.getUUID(), assemblyTarget);
            mob.fallDistance = 0.0F;
            if (route != null && route.advance()) {
                formation.activeStructuralBestDistanceSqr = Double.MAX_VALUE;
                formation.activeStructuralProgressTick = now;
                return false;
            }
            if (!isStructuralPoseClear(level, mob, mob.position(), slotPosition)) {
                rerouteOrReleaseStructuralMember(level, formation, mob, nextSlot, "dock_blocked");
                return false;
            }
            mob.setPos(slotPosition.x, slotPosition.y, slotPosition.z);
            formation.assemblyHoldPositions.put(mob.getUUID(), slotPosition);
            mob.setNoGravity(true);
            mob.noPhysics = true;
            mob.setDeltaMovement(Vec3.ZERO);
            mob.getNavigation().stop();
            formation.assembledSlots++;
            formation.lastAssemblyTick = now;
            formation.lastAssemblyProgressTick = now;
            formation.activeStructuralRoute = null;
            formation.activeMemberSuctionEngaged = false;
            formation.activeStructuralSlot = formation.assembledSlots;
            formation.activeStructuralWorkerId = null;
            formation.activeStructuralRouteRetries = 0;
            formation.activeStructuralBestDistanceSqr = Double.MAX_VALUE;
            boolean durableRouteClaim = formation.structuralApproachReservations.containsKey(mob.getUUID())
                    || com.example.soundattract.component.LivingBridgeEntityData.get(mob).contains("livingBridgeControl");
            BlockPos dockPos = BlockPos.containing(slotPosition);
            if (!structureHasContact(level, formation, formation.assembledSlots)) {
                logAssemblyFailure(level, formation, nextSlot, mob, "contact_invalid_after_placement", slotPosition, intakeCell);
                if (isCanonicalLivingBridge(formation) && hasVerifiedSupport(level, formation, nextSlot, mob, slotPosition)) {
                    SoundAttractMod.LOGGER.warn("[LivingWallCoordinator] Suppressed fatal assembly collapse on slot {} for formation {} due to verified support", nextSlot, formation.id);
                    if (durableRouteClaim) {
                        persistSafeBaseDocked(level, formation, mob, dockPos);
                    }
                    releaseStructuralApproachLane(formation, mob.getUUID());
                    formation.safeBaseNavigation.remove(mob.getUUID());
                    releaseAssemblySlot(formation, mob.getUUID());
                    recordSuccessfulPlacementProvenance(formation, mob.getUUID(), dockPos);
                } else {
                    formation.assemblyFatal = true;
                }
            } else {
                if (durableRouteClaim) {
                    persistSafeBaseDocked(level, formation, mob, dockPos);
                }
                releaseStructuralApproachLane(formation, mob.getUUID());
                formation.safeBaseNavigation.remove(mob.getUUID());
                releaseAssemblySlot(formation, mob.getUUID());
                recordSuccessfulPlacementProvenance(formation, mob.getUUID(), dockPos);
            }
            formation.assemblyCompleteTick = -1L;
            return false;
        }
        if (formation.members.size() < formation.requiredBodies) {
            formation.assemblyCompleteTick = -1L;
            return false;
        }

        if (formation.assemblyCompleteTick < 0L) {
            formation.assemblyCompleteTick = now;
            return false;
        }
        return now - formation.assemblyCompleteTick >= ASSEMBLY_SETTLE_TICKS;
    }

    static boolean assemblyPlacementSettleElapsed(long lastPlacementTick, long now, int intervalTicks) {
        return lastPlacementTick < 0L || now - lastPlacementTick >= Math.max(0, intervalTicks);
    }

    private static void logAssemblyProgress(Formation formation, Mob mob, int slot, double distance, long now) {
        if (SoundAttractConfig.COMMON == null || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_FORMATION)
                || formation.lastAssemblyProgressLogTick >= 0L
                && now - formation.lastAssemblyProgressLogTick < FORMATION_DIAGNOSTIC_INTERVAL_TICKS) return;
        formation.lastAssemblyProgressLogTick = now;
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Assembly moving: bridge={}, slot={}, mob={}, distance={}, assembled={}, members={}",
                formation.id, slot, mob.getUUID(), distance, formation.assembledSlots, formation.members.size());
    }

    private static LivingBridgeMotionDiagnostics.Attempt startOrContinueMotionDiagnostic(
            ServerLevel level, Formation formation, Mob mob, int slot, Vec3 target, long now) {
        boolean enabled = LivingWallConfig.ENABLE_MOTION_DIAGNOSTICS != null
                && LivingWallConfig.ENABLE_MOTION_DIAGNOSTICS.get();
        LivingBridgeMotionDiagnostics.Attempt current = formation.motionDiagnosticAttempt;
        if (!enabled) {
            finishMotionDiagnostic(formation, now, "disabled");
            return null;
        }
        if (current != null && current.member.equals(mob.getUUID()) && current.slot == slot
                && now - current.startTick < LivingBridgeMotionDiagnostics.MAX_WINDOW_TICKS) return current;
        if (current != null) {
            boolean windowExpired = now - current.startTick >= LivingBridgeMotionDiagnostics.MAX_WINDOW_TICKS;
            finishMotionDiagnostic(formation, now, "window_complete");
            if (windowExpired) return null;
        }
        if (formation.motionDiagnosticAttempts >= 32) {
            if (!formation.motionDiagnosticCapLogged) {
                formation.motionDiagnosticCapLogged = true;
                SoundAttractMod.LOGGER.info("[LivingBridgeMotion] motion_diag_attempt_cap bridge={} tick={} cap=32",
                        formation.id, now);
            }
            return null;
        }
        int attemptNumber = ++formation.motionDiagnosticAttempts;
        current = LivingBridgeMotionDiagnostics.begin(formation.id, mob.getUUID(), slot, attemptNumber,
                level.dimension().identifier(), now, target);
        formation.motionDiagnosticAttempt = current;
        return current;
    }

    private static void finishMotionDiagnostic(Formation formation, long now, String reason) {
        if (formation == null || formation.motionDiagnosticAttempt == null) return;
        LivingBridgeMotionDiagnostics.finish(formation.motionDiagnosticAttempt, now, reason);
        formation.motionDiagnosticAttempt = null;
    }

    private static void traceStructuralMove(Formation formation, Mob mob, int slot, String action, Vec3 from, Vec3 to) {
        if (SoundAttractConfig.COMMON == null || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_FORMATION)
                || formation == null || mob == null || from == null || to == null) return;
        double distanceSqr = from.distanceToSqr(to);
        if (!"collapse_release".equals(action) && distanceSqr < 1.0D) return;
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Structural trace: bridge={}, phase={}, action={}, slot={}, mob={}, from={}, to={}, distance={}, noGravity={}, noPhysics={}",
                formation.id, formation.phase, action, slot, mob.getUUID(), from, to, Math.sqrt(distanceSqr),
                mob.isNoGravity(), mob.noPhysics);
    }

    private static Vec3 slotPositionOrNull(Formation formation, int slot) {
        return formation == null ? null : formationSlotPosition(formation, slot, 0.0D);
    }

    private static Vec3 assemblyTargetPosition(Formation formation, int slot, Vec3 current, Vec3 slotPosition) {
        if (formation == null || current == null || slotPosition == null || slot < formation.columnCount) {
            return slotPosition;
        }
        Vec3 base = formationSlotBasePosition(formation, slot);
        double dx = current.x - base.x;
        double dz = current.z - base.z;
        double horizontalSqr = dx * dx + dz * dz;
        if (horizontalSqr > 0.18D * 0.18D) {
            return base;
        }
        return slotPosition;
    }

    private static Vec3 formationSlotBasePosition(Formation formation, int slot) {
        Vec3 pivot = Vec3.atBottomCenterOf(formation.pivot);
        Vec3 anchor = Vec3.atBottomCenterOf(requiresTilt(formation.plan.scenario())
                ? tiltStructuralEnd(formation.plan) : formation.spanEnd);
        Vec3 delta = anchor.subtract(pivot);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 0.001D) return pivot;
        double normX = delta.x / horizontal;
        double normZ = delta.z / horizontal;
        Vec3 lateral = formationSlotLateralOffset(formation, slot, normX, normZ);
        return pivot.add(lateral.x, 0.0D, lateral.z);
    }

    private static Vec3 formationSlotLateralOffset(Formation formation, int slot, double normX, double normZ) {
        int columns = Math.max(1, formation.columnCount);
        int column = Math.max(0, slot) % columns;
        double lateral = (column - (columns - 1) * 0.5D) * formationLateralSpacing(formation);
        return new Vec3(-normZ * lateral, 0.0D, normX * lateral);
    }

    private static double formationLateralSpacing(Formation formation) {
        return Math.max(lateralSpacingForBodyWidth(formation.minimumWidth),
                Math.max(0.05D, formation.horizontalTouchStep));
    }

    private static double formationLayerSpacing(Formation formation) {
        if (formation != null && formation.plan != null
                && formation.plan.scenario() == ConstructionScenario.WALL_LEDGE) {
            return 1.90D;
        }
        double pillarStep = Math.abs(formation.pillarStep);
        if (!Double.isFinite(pillarStep) || pillarStep <= 0.0D) {
            pillarStep = Math.abs(formation.verticalTouchStep);
        }
        return Math.max(layerSpacingForBodyHeight(formation.minimumHeight), Math.max(0.05D, pillarStep));
    }

    private static void resetStructuralRoute(Formation formation, int slot, long now) {
        if (formation == null) return;
        formation.activeStructuralSlot = slot;
        formation.activeStructuralRoute = null;
        formation.activeMemberSuctionEngaged = false;
        formation.activeStructuralBestDistanceSqr = Double.MAX_VALUE;
        formation.activeStructuralProgressTick = now;
        formation.activeStructuralRouteRetries = 0;
    }

    private static StructuralRoute structuralRoute(Formation formation, int slot, Mob mob, ServerLevel level) {
        if (formation == null || mob == null || level == null) return null;
        if (formation.activeStructuralSlot != slot) {
            resetStructuralRoute(formation, slot, level.getGameTime());
        }
        if (formation.activeStructuralRoute != null) {
            return formation.activeStructuralRoute;
        }
        Vec3 dock = formation.structuralSlotPositions.getOrDefault(slot,
                formationSlotPosition(formation, slot, 0.0D));
        if (formation.plan != null && (isDirectSpan(formation.plan.scenario()) || formation.plan.scenario() == ConstructionScenario.WALL_LEDGE)) {
            List<Vec3> waypoints = new ArrayList<>();
            for (int s = 0; s <= slot; s++) {
                Vec3 p = formation.structuralSlotPositions.getOrDefault(s, formationSlotPosition(formation, s, 0.0D));
                if (waypoints.isEmpty() || waypoints.get(waypoints.size() - 1).distanceToSqr(p) > 0.01D) {
                    waypoints.add(p);
                }
            }
            if (waypoints.isEmpty()) {
                waypoints.add(dock);
            }
            formation.activeStructuralRoute = new StructuralRoute(waypoints);
            formation.activeStructuralBestDistanceSqr = mob.position().distanceToSqr(waypoints.get(0));
            formation.activeStructuralProgressTick = level.getGameTime();
            return formation.activeStructuralRoute;
        }
        Vec3 lift = new Vec3(dock.x, dock.y, dock.z);
        List<Vec3> candidates = new ArrayList<>(structuralStagingCandidates(formation, slot, mob, dock));
        Vec3 current = mob.position();
        candidates.sort(Comparator
                .comparingDouble((Vec3 candidate) -> candidate.distanceToSqr(current))
                .thenComparingDouble(candidate -> candidate.distanceToSqr(dock))
                .thenComparingDouble(candidate -> candidate.x)
                .thenComparingDouble(candidate -> candidate.z));
        for (Vec3 candidate : candidates) {
            Vec3 elevated = new Vec3(candidate.x, dock.y, candidate.z);
            if (!hasFoundationTerrainSupport(level, candidate)) continue;
            boolean alreadyElevated = current.y > candidate.y + 0.8D;
            if (!alreadyElevated && !isStructuralPoseClear(level, mob, current, candidate)) continue;
            if (alreadyElevated && !isStructuralPoseClear(level, mob, current, elevated)) continue;
            if (!isEntityClearAt(level, mob, candidate, elevated)) continue;
            if (!isEntityClearAt(level, mob, elevated, dock)) continue;
            formation.activeStructuralRoute = new StructuralRoute(candidate, elevated, dock, current);
            formation.activeStructuralBestDistanceSqr = current.distanceToSqr(formation.activeStructuralRoute.waypoint());
            formation.activeStructuralProgressTick = level.getGameTime();
            return formation.activeStructuralRoute;
        }
        return null;
    }

    private static List<Vec3> structuralStagingCandidates(Formation formation, int slot, Mob mob, Vec3 dock) {
        if (formation == null || mob == null || dock == null) return List.of();
        Vec3 base = formationSlotBasePosition(formation, slot);
        Vec3 center = structuralStagingCenter(formation.plan.scenario(), base, dock);
        double outside = Math.min(0.22D, mob.getBbWidth() * 0.35D);
        List<Vec3> candidates = new ArrayList<>();
        if (formation.plan.scenario() == ConstructionScenario.WALL_LEDGE) {
            Vec3 forward = formation.plan.forward();
            double horizontal = Math.sqrt(forward.x * forward.x + forward.z * forward.z);
            if (horizontal >= 0.001D) {
                double normalX = -forward.z / horizontal;
                double normalZ = forward.x / horizontal;
                candidates.add(center.add(normalX * outside, 0.0D, normalZ * outside));
                candidates.add(center.add(-normalX * outside, 0.0D, -normalZ * outside));
            }
        }
        candidates.add(new Vec3(center.x + outside, center.y, center.z));
        candidates.add(new Vec3(center.x - outside, center.y, center.z));
        candidates.add(new Vec3(center.x, center.y, center.z + outside));
        candidates.add(new Vec3(center.x, center.y, center.z - outside));
        candidates.add(new Vec3(center.x + outside, center.y, center.z + outside));
        candidates.add(new Vec3(center.x + outside, center.y, center.z - outside));
        candidates.add(new Vec3(center.x - outside, center.y, center.z + outside));
        candidates.add(new Vec3(center.x - outside, center.y, center.z - outside));
        candidates.add(center);
        return candidates;
    }

    static Vec3 structuralStagingCenter(ConstructionScenario scenario, Vec3 base, Vec3 dock) {
        return scenario == ConstructionScenario.WALL_LEDGE
                ? new Vec3(dock.x, base.y, dock.z)
                : base;
    }

    private static boolean hasStructuralProgress(Formation formation, double distanceSqr, long now) {
        if (formation == null) return false;
        if (distanceSqr + STRUCTURAL_PROGRESS_EPSILON_SQR < formation.activeStructuralBestDistanceSqr) {
            formation.activeStructuralBestDistanceSqr = distanceSqr;
            formation.activeStructuralProgressTick = now;
            return true;
        }
        return false;
    }

    private static void countCommittedFoundationIfLocked(ServerLevel level, Formation formation) {
        if (level == null || formation == null || formation.assembledSlots > 0
                || formation.committedFoundationIds.isEmpty()) return;
        UUID id = formation.committedFoundationIds.iterator().next();
        Mob mob = getMob(level, id);
        MobTicket ticket = formation.mobTickets.get(id);
        boolean alive = mob != null && mob.isAlive() && !mob.isRemoved();
        boolean owned = alive && formation.members.contains(id) && formation.structuralBuilderIds.contains(id)
                && formation.id.equals(state(level).mobOwners.get(id));
        boolean slotOwned = ticket != null && id.equals(formation.assemblySlotOwners.get(ticket.assemblySlot));
        boolean exactLocked = alive && ticket != null && isExactAssemblyLockValid(level, formation, mob, ticket,
                Vec3.atBottomCenterOf(ticket.assemblySlot));
        if (!committedFoundationCountsAsAssembled(alive, owned, slotOwned, exactLocked)) return;
        if (mob == null || !mob.isAlive() || mob.isRemoved() || ticket == null
                || !formation.members.contains(id) || !formation.structuralBuilderIds.contains(id)
                || !formation.id.equals(state(level).mobOwners.get(id))
                || !id.equals(formation.assemblySlotOwners.get(ticket.assemblySlot))
                || !isExactAssemblyLockValid(level, formation, mob, ticket,
                Vec3.atBottomCenterOf(ticket.assemblySlot))) return;
        formation.assembledSlots = 1;
        formation.activeStructuralSlot = 1;
        formation.lastAssemblyProgressTick = level.getGameTime();
    }

    static boolean committedFoundationCountsAsAssembled(boolean alive, boolean owned,
                                                         boolean slotOwned, boolean exactLocked) {
        return alive && owned && slotOwned && exactLocked;
    }

    static Vec3 structuralSlotPositionForTest(Map<Integer, Vec3> mappedSlots, int slot, Vec3 fallback) {
        return mappedSlots != null && mappedSlots.containsKey(slot) ? mappedSlots.get(slot) : fallback;
    }

    private static boolean structuralNoProgressExceeded(Formation formation, long now) {
        return formation != null
                && formation.activeStructuralProgressTick >= 0L
                && now - formation.activeStructuralProgressTick >= RECRUITER_OBSERVATION_TICKS;
    }

    private static boolean rerouteOrReleaseStructuralMember(ServerLevel level, Formation formation, Mob mob, int slot, String reason) {
        if (level == null || formation == null || mob == null || slot < 0 || slot >= formation.members.size()) return false;
        finishMotionDiagnostic(formation, level.getGameTime(), reason);
        formation.activeStructuralRoute = null;
        formation.activeStructuralBestDistanceSqr = Double.MAX_VALUE;
        formation.activeStructuralProgressTick = level.getGameTime();
        formation.activeStructuralRouteRetries++;
        int maxRetries = "structural_lane_conflict".equals(reason)
                ? STRUCTURAL_LANE_CONFLICT_RETRY_WINDOWS
                : STRUCTURAL_ROUTE_RETRY_WINDOWS;
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Structural member step retry: bridge={}, slot={}, mob={}, reason={}, retries={}/{}",
                formation.id, slot, mob.getUUID(), reason, formation.activeStructuralRouteRetries, maxRetries);
        if (formation.activeStructuralRouteRetries > maxRetries) {
            UUID workerId = formation.members.get(slot);
            formation.activeStructuralRouteRetries = 0;
            formation.activeStructuralWorkerId = null;
            formation.activeMemberSuctionEngaged = false;
            formation.activeStructuralRoute = null;
            releaseMember(level, formation, workerId);
            formation.recruitmentBlacklist.put(workerId, level.getGameTime() + RECRUITER_PATH_FAILURE_BLACKLIST_TICKS);
            formation.lastAssemblyProgressTick = level.getGameTime();
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Structural member released: bridge={}, slot={}, mob={}, reason={}",
                    formation.id, slot, workerId, reason);
            return false;
        }
        return true;
    }

    private static void logAssemblyFailure(ServerLevel level, Formation formation, int slot, Mob mob,
                                           String reason, Vec3 target, BlockPos intakeCell) {
        if (SoundAttractConfig.COMMON == null || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_FORMATION)) return;
        BlockPos feet = target == null ? null : BlockPos.containing(target);
        BlockPos support = feet == null ? null : feet.below();
        Mob sibling = formation != null && slot > 0 && slot - 1 < formation.members.size()
                ? getMob(level, formation.members.get(slot - 1)) : null;
        boolean intakeSafe = intakeCell != null && isValidIntakeWindow(level, new IntakeWindow(intakeCell), formation);
        boolean supportSolid = support != null && level.isLoaded(support)
                && !level.getBlockState(support).getCollisionShape(level, support).isEmpty();
        boolean plannedTouch = mob != null && sibling != null && target != null && plannedBodiesTouch(mob, target, sibling);
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Assembly guard failed: reason={}, bridge={}, slot={}, mob={}, mobPos={}, target={}, intakeWindow={}, intakeSafe={}, inLava={}, support={}, supportSolid={}, sibling={}, siblingFrozen={}, plannedTouch={}",
                reason, formation == null ? "unknown" : formation.id, slot, mob == null ? "missing" : mob.getUUID(),
                mob == null ? "n/a" : mob.position(), target, intakeCell, intakeSafe,
                mob != null && mob.isInLava(), support, supportSolid,
                sibling == null ? "missing" : sibling.getUUID(),
                sibling != null && sibling.noPhysics && sibling.isNoGravity(), plannedTouch);
    }

    private static boolean hasVerifiedSupport(ServerLevel level, Formation formation, int slot, Mob mob, Vec3 target) {
        if (slot < formation.columnCount) {
            BlockPos feet = BlockPos.containing(target);
            BlockPos support = feet.below();
            if (!level.isLoaded(feet) || !level.isLoaded(support)
                    || level.getBlockState(support).getCollisionShape(level, support).isEmpty()) return false;
            if (slot == 0) return true;
            Mob sibling = getMob(level, formation.members.get(slot - 1));
            return sibling != null && sibling.noPhysics && sibling.isNoGravity()
                    && plannedBodiesTouch(mob, target, sibling);
        }
        int lowerSlot = slot - formation.columnCount;
        if (lowerSlot < 0 || lowerSlot >= formation.assembledSlots) return false;
        Mob lower = getMob(level, formation.members.get(lowerSlot));
        if (lower == null || !lower.isAlive() || lower.isRemoved() || !lower.noPhysics || !lower.isNoGravity()) return false;
        if (formation.plan != null && (formation.plan.scenario() == ConstructionScenario.WALL_LEDGE
                || formation.plan.scenario() == ConstructionScenario.DROP_TO_LEDGE
                || isDirectSpan(formation.plan.scenario())
                || hasElbowBends(formation))) {
            double step = Math.max(formation.pillarStep, formation.horizontalTouchStep);
            double allowedDist = Math.max(3.0D, step + 1.0D);
            double maxDistanceSqr = (formation.plan.scenario() == ConstructionScenario.DROP_TO_LEDGE
                    || hasElbowBends(formation)
                    || isDirectSpan(formation.plan.scenario())) && lowerSlot == 0
                    ? 25.0D : allowedDist * allowedDist;
            return target.distanceToSqr(lower.position()) <= maxDistanceSqr;
        }
        if (!structureHasContact(level, formation, formation.assembledSlots)) return false;
        Vec3 expected = formationSlotPosition(formation, lowerSlot, 0.0D);
        return lower.position().distanceToSqr(expected) <= 0.01D && plannedBodiesTouch(mob, target, lower);
    }

    private static boolean plannedBodiesTouch(Mob moving, Vec3 target, Mob support) {
        if (target != null && support != null && target.distanceToSqr(support.position()) <= 25.0D) {
            return true;
        }
        AABB proposed = translatedBoundingBox(moving, target);
        AABB actual = support.getBoundingBox();
        return structuralBodiesTouch(proposed, actual);
    }

    private static Vec3 formationSlotPosition(Formation formation, int slot, double progress) {
        Vec3 pivot = Vec3.atBottomCenterOf(formation.pivot);
        Vec3 anchor = Vec3.atBottomCenterOf(tiltStructuralEnd(formation.plan));
        int layerCount = Math.max(1, formation.layerCount);
        int columns = Math.max(1, formation.columnCount);
        int layer = Math.max(0, slot) / columns;

        Vec3 sourcePosition = formationSourcePosition(formation, slot, layer, columns, pivot);
        Vec3 baseOrigin = formationSourcePosition(formation, slot, 0, columns, pivot);

        Vec3 spanVector = anchor.subtract(pivot);
        double horizontal = Math.sqrt(spanVector.x * spanVector.x + spanVector.z * spanVector.z);
        double normX = horizontal < 0.001D ? 0.0D : spanVector.x / horizontal;
        double normZ = horizontal < 0.001D ? 0.0D : spanVector.z / horizontal;
        Vec3 lateral = formationSlotLateralOffset(formation, slot, normX, normZ);
        Vec3 targetAnchor = anchor.add(lateral.x, 0.0D, lateral.z);

        double layerProgress = layerCount <= 1 ? 0.0D : layer / (double) (layerCount - 1);
        if (requiresTilt(formation.plan.scenario()) && !hasElbowBends(formation)) {
            Vec3 delta = targetAnchor.subtract(baseOrigin);
            double arc = (formation.plan.scenario() == ConstructionScenario.WALL_LEDGE && delta.y > 0.0D)
                    ? Math.sin(layerProgress * Math.PI) * (0.3D * delta.y)
                    : 0.0D;
            Vec3 finalPosition = baseOrigin.add(delta.scale(layerProgress)).add(0.0D, arc, 0.0D);
            return sourcePosition.lerp(finalPosition, Mth.clamp(progress, 0.0D, 1.0D));
        }

        Vec3 delta = anchor.subtract(pivot);
        Vec3 finalPosition = routeFormationPosition(formation, layerProgress, pivot, anchor, delta);
        Vec3 base = formationSlotBasePosition(formation, slot);
        finalPosition = finalPosition.add(base.x - pivot.x, 0.0D, base.z - pivot.z);
        return finalPosition;
    }

    static Vec3 sphericalDirection(Vec3 source, Vec3 target, double progress) {
        if (source == null || target == null || source.lengthSqr() < 1.0E-8D || target.lengthSqr() < 1.0E-8D) {
            return Vec3.ZERO;
        }
        Vec3 from = source.normalize();
        Vec3 to = target.normalize();
        double bounded = Mth.clamp(progress, 0.0D, 1.0D);
        double dot = Mth.clamp(from.dot(to), -1.0D, 1.0D);
        double angle = Math.acos(dot);
        double sine = Math.sin(angle);
        if (Math.abs(sine) < 1.0E-6D) {
            Vec3 linear = from.lerp(to, bounded);
            return linear.lengthSqr() < 1.0E-8D ? from : linear.normalize();
        }
        return from.scale(Math.sin((1.0D - bounded) * angle) / sine)
                .add(to.scale(Math.sin(bounded * angle) / sine)).normalize();
    }

    private static Vec3 formationSourcePosition(Formation formation, int slot, int layer, int columns, Vec3 pivot) {
        int foundationSlot = Math.max(0, slot) % columns;
        Vec3 foundation = formation.structuralSlotPositions == null
                ? null : formation.structuralSlotPositions.get(foundationSlot);
        if (foundation == null) {
            foundation = formationSlotBasePosition(formation, foundationSlot);
        }
        Vec3 sourceOffset = formation.sourceStagingOffset == null ? Vec3.ZERO : formation.sourceStagingOffset;
        foundation = foundation.add(sourceOffset);
        return foundation.add(0.0D, layer * formationLayerSpacing(formation), 0.0D);
    }

    private static boolean selectSourceStagingOffset(ServerLevel level, Formation formation, Mob leader,
                                                     SourceStagingBudget budget) {
        if (!isServerThread(level) || formation == null || leader == null) {
            if (formation != null) SoundAttractMod.LOGGER.info("[LivingWallCoordinator] admission_articulation_rejected reason=wrong_server_thread");
            return false;
        }
        Vec3 originalOffset = formation.sourceStagingOffset;
        List<Vec3> offsets = new ArrayList<>();
        int[] dyOrder = {0, 1, -1, 2, -2, 3, 4};
        for (int dy : dyOrder) {
            for (int radius = 0; radius <= 4; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                        offsets.add(new Vec3(dx, dy, dz));
                    }
                }
            }
        }
        for (Vec3 offset : offsets) {
            if (!budget.takeOffset()) break;
            boolean clear = true;
            ClearanceBlockage firstBlocker = null;
            int blockedSlot = -1;
            for (int slot = 0; slot < formation.requiredBodies; slot++) {
                Vec3 sourcePose = sourcePositionFromGeometry(formation, slot,
                        formation.structuralSlotPositions, offset);
                ClearanceResolution sourceClearance = resolveClearance(level, leader, sourcePose, 0.0D,
                        true);
                boolean stagingSupport = slot >= formation.columnCount
                        || isStagingSupportCell(level, BlockPos.containing(sourcePose));
                if (!sourceClearance.clear() || !stagingSupport) {
                    firstBlocker = sourceClearance.blockage();
                    blockedSlot = slot;
                    String key = offset + "|" + slot + "|" + sourceClearance.blocker() + "|"
                            + sourceClearance.blockerState();
                    if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ROUTING)
                            && budget.shouldDiagnose(key)) {
                        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] source_staging_pose_failed offset={} slot={} pose={} clear={} blocker={} blockerState={} safeApproach={}",
                                offset, slot, sourcePose, sourceClearance.clear(), sourceClearance.blocker(),
                                sourceClearance.blockerState(), stagingSupport);
                    }
                    clear = false;
                    break;
                }
            }
            if (clear) {
                formation.sourceStagingOffset = offset;
                return true;
            }
            if (firstBlocker != null && blockedSlot > 0 && !firstBlocker.lava() && !firstBlocker.unloaded()
                    && isArticulationEnabled(formation) && budget.fullFormationValidations < SOURCE_STAGING_VALIDATION_BUDGET) {
                ArticulationCandidate candidate = solveArticulationCandidate(level, formation, blockedSlot, leader,
                        firstBlocker.blocker(), budget);
                if (candidate != null && budget.takeValidation()) {
                    boolean provisionalClear;
                    TraversalCorridorWitness provisionalWitness = copyTraversalWitness(formation.traversalWitness);
                    provisionalClear = true;
                    for (int slot = 0; slot < formation.requiredBodies; slot++) {
                        Vec3 pose = sourcePositionFromGeometry(formation, slot, candidate.slots(), offset);
                        ClearanceResolution resolution = resolveClearance(level, leader, pose, 0.0D, true);
                        boolean support = slot >= formation.columnCount
                                || isStagingSupportCell(level, BlockPos.containing(pose));
                        if (!resolution.clear() || !support) {
                            provisionalClear = false;
                            break;
                        }
                    }
                    if (provisionalClear) {
                        formation.elbowPoints.clear();
                        formation.elbowPoints.addAll(candidate.bends());
                        formation.structuralSlotPositions.clear();
                        formation.structuralSlotPositions.putAll(candidate.slots());
                        formation.sourceStagingOffset = offset;
                        if (provisionalWitness != null) formation.traversalWitness = provisionalWitness;
                        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Source staging deflected blocker {} at {} around slot {} with bend {} evaluations={} validations={}",
                                firstBlocker.blocker(), firstBlocker.blockerState(), blockedSlot, candidate.bend(),
                                budget.deflectionCandidateEvaluations, budget.fullFormationValidations);
                        return true;
                    }
                }
            }
        }
        formation.sourceStagingOffset = originalOffset;
        return false;
    }

    private static boolean tryRepairScanAdmission(ServerLevel level, Formation formation, BodyGeometry geometry,
                                                  Mob leader, ClearanceBlockage blockage,
                                                  SourceStagingBudget budget) {
        if (!isServerThread(level) || formation == null || geometry == null || leader == null
                || blockage == null || blockage.blocker() == null || budget == null
                || !budget.takeValidation()) return false;
        List<Vec3> savedElbows = new ArrayList<>(formation.elbowPoints);
        Map<Integer, Vec3> savedSlots = new HashMap<>(formation.structuralSlotPositions);
        Vec3 savedOffset = formation.sourceStagingOffset;
        TraversalCorridorWitness savedWitness = formation.traversalWitness;
        ArticulationCandidate candidate = solveArticulationCandidate(level, formation,
                Math.max(1, blockage.slot()), leader, blockage.blocker(), budget);
        if (candidate == null) return false;
        boolean valid = false;
        try {
            formation.elbowPoints.clear();
            formation.elbowPoints.addAll(candidate.bends());
            formation.structuralSlotPositions.clear();
            formation.structuralSlotPositions.putAll(candidate.slots());
            formation.traversalWitness = copyTraversalWitness(savedWitness);
            valid = findFormationBlockage(level, formation, geometry) == null;
            return valid;
        } finally {
            if (!valid) {
                formation.elbowPoints.clear();
                formation.elbowPoints.addAll(savedElbows);
                formation.structuralSlotPositions.clear();
                formation.structuralSlotPositions.putAll(savedSlots);
                formation.sourceStagingOffset = savedOffset;
                formation.traversalWitness = savedWitness;
            }
        }
    }

    private static Vec3 sourcePositionFromGeometry(Formation formation, int slot,
                                                    Map<Integer, Vec3> structuralSlots, Vec3 offset) {
        int columns = Math.max(1, formation.columnCount);
        int layer = Math.max(0, slot) / columns;
        int foundationSlot = foundationColumnForSlot(slot, columns);
        Vec3 foundation = structuralSlots.getOrDefault(foundationSlot,
                formationSlotBasePosition(formation, foundationSlot));
        return foundation.add(offset == null ? Vec3.ZERO : offset)
                .add(0.0D, layer * formationLayerSpacing(formation), 0.0D);
    }

    static int foundationColumnForSlot(int slot, int columns) {
        return Math.max(0, slot) % Math.max(1, columns);
    }

    private static boolean isStagingSupportCell(ServerLevel level, BlockPos cell) {
        if (level == null || cell == null || !level.isLoaded(cell) || !level.isLoaded(cell.below())) return false;
        BlockState feet = level.getBlockState(cell);
        BlockState head = level.getBlockState(cell.above());
        BlockState support = level.getBlockState(cell.below());
        return feet.getFluidState().isEmpty() && head.getFluidState().isEmpty()
                && support.getFluidState().isEmpty()
                && feet.getCollisionShape(level, cell).isEmpty()
                && head.getCollisionShape(level, cell.above()).isEmpty()
                && !support.is(BlockTags.TRAPDOORS)
                && hasStableSupport(level, cell.below(), support);
    }

    private static Vec3 routeFormationPosition(Formation formation, double progress,
                                                Vec3 pivot, Vec3 anchor, Vec3 fallbackDelta) {
        if (formation == null || formation.plan == null || (!hasElbowBends(formation) && formation.plan.corridor() == null)) {
            return pivot.add(fallbackDelta.scale(progress));
        }
        BlockPos structuralEnd = tiltStructuralEnd(formation.plan);
        List<Vec3> route = formationRoutePoints(formation, structuralEnd);
        if (formation.structuralSlotPositions != null && formation.structuralSlotPositions.containsKey(0)
                && isDirectSpan(formation.plan.scenario()) && !hasElbowBends(formation)) {
            Vec3 foundation = formation.structuralSlotPositions.get(0);
            if (foundation != null && !route.isEmpty() && foundation.distanceToSqr(route.get(0)) > 1.0E-6D) {
                List<Vec3> adjusted = new ArrayList<>();
                adjusted.add(foundation);
                adjusted.addAll(route);
                route = adjusted;
            }
        }
        return route.size() < 2 ? pivot.add(fallbackDelta.scale(progress)) : positionAlongRoute(route, progress);
    }

    static Vec3 rotatedSlot(Vec3 pivot, Vec3 anchor, double layerProgress, double lateral, double progress) {
        Vec3 delta = anchor.subtract(pivot);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double length = Math.sqrt(horizontal * horizontal + delta.y * delta.y);
        if (length < 0.001D) return pivot;
        double angle = Math.atan2(horizontal, delta.y) * Mth.clamp(progress, 0.0D, 1.0D);
        double distance = length * layerProgress;
        double normX = horizontal < 0.001D ? 0.0D : delta.x / horizontal;
        double normZ = horizontal < 0.001D ? 0.0D : delta.z / horizontal;
        return new Vec3(pivot.x + normX * Math.sin(angle) * distance - normZ * lateral,
                pivot.y + Math.cos(angle) * distance,
                pivot.z + normZ * Math.sin(angle) * distance + normX * lateral);
    }

    private static boolean isSlotClear(ServerLevel level, Mob mob, Vec3 position) {
        return resolveClearance(level, mob, position, 0.0D).clear();
    }

    private static AABB translatedBoundingBox(Mob mob, Vec3 feetPosition) {
        return mob.getBoundingBox().move(feetPosition.subtract(mob.position()));
    }

    private static boolean isEntityClearAt(ServerLevel level, Mob mob, Vec3 from, Vec3 to) {
        if (level == null || mob == null || from == null || to == null) return false;
        Formation formation = getFormation(mob);
        AABB nextBox = translatedBoundingBox(mob, to);
        AABB swept = translatedBoundingBox(mob, from).minmax(nextBox);
        int samples = Math.max(1, Mth.ceil(from.distanceTo(to) / 0.2D));
        if (samples > 16) return false;
        for (int sample = 0; sample <= samples; sample++) {
            Vec3 position = from.lerp(to, sample / (double) samples);
            AABB body = translatedBoundingBox(mob, position);
            if (!isStructuralSweepVolumeClear(level, mob, body,
                    formation,
                    formation == null ? 0.0D : formation.assembledSlots + 1.0D)) return false;
        }
        boolean isBridge = formation != null && (isCanonicalLivingBridge(formation)
                || (formation.plan != null && (isDirectSpan(formation.plan.scenario())
                || formation.plan.scenario() == ConstructionScenario.WALL_LEDGE)));
        List<Entity> blockers = level.getEntities(mob, swept, candidate -> {
            if (!(candidate instanceof LivingEntity) || !candidate.isAlive()
                    || candidate.isRemoved() || candidate == mob) {
                return false;
            }
            if (formation == null) {
                return true;
            }
            UUID id = candidate.getUUID();
            boolean isMember = formation.members != null && formation.members.contains(id);
            if (!isMember) {
                if (isBridge && (candidate instanceof net.minecraft.world.entity.monster.Monster
                        || (formation.localRoster != null && formation.localRoster.contains(id)))) {
                    return false;
                }
                return true;
            }
            if (isBridge) {
                return false;
            }
            return isPlacedStructuralMember(formation.members, formation.assembledSlots, id);
        });
        for (Entity candidate : blockers) {
            AABB candidateBox = candidate.getBoundingBox();
            for (int sample = 1; sample <= samples; sample++) {
                Vec3 position = from.lerp(to, sample / (double) samples);
                if (structuralBodiesPenetrate(translatedBoundingBox(mob, position), candidateBox)) {
                    if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ROUTING)) {
                        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Entity clearance blocked: mob={}, candidate={}, from={}, to={}",
                                mob.getUUID(), candidate.getUUID(), from, to);
                    }
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isStructuralSweepVolumeClear(ServerLevel level, Mob mob, AABB body,
                                                         Formation formation, double progress) {
        return structuralSweepResult(level, mob, body, formation, progress).safe();
    }

    private static LivingBridgeSafeBaseRoute.Result structuralSweepResult(ServerLevel level, Mob mob, AABB body) {
        return structuralSweepResult(level, mob, body, null, 0.0D);
    }

    private static LivingBridgeSafeBaseRoute.Result structuralSweepResult(ServerLevel level, Mob mob, AABB body,
                                                                           Formation formation, double progress) {
        if (level == null || mob == null || body == null || level.getServer() == null
                || !level.getServer().isSameThread()) {
            return new LivingBridgeSafeBaseRoute.Result(LivingBridgeSafeBaseRoute.Rejection.INVALID_FRAME, null);
        }
        int minX = Mth.floor(body.minX - 1.0E-7D);
        int minY = Mth.floor(body.minY - 1.0E-7D);
        int minZ = Mth.floor(body.minZ - 1.0E-7D);
        int maxX = Mth.floor(body.maxX + 1.0E-7D);
        int maxY = Mth.floor(body.maxY + 1.0E-7D);
        int maxZ = Mth.floor(body.maxZ + 1.0E-7D);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos cell = new BlockPos(x, y, z);
                    if (y < level.getMinY() || y >= level.getMaxY()
                            || !level.isLoaded(cell)) {
                        return new LivingBridgeSafeBaseRoute.Result(
                                LivingBridgeSafeBaseRoute.Rejection.UNLOADED_CELL, cell);
                    }
                    BlockState state = level.getBlockState(cell);
                    if (state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
                            && !mob.fireImmune()) {
                        return new LivingBridgeSafeBaseRoute.Result(
                                LivingBridgeSafeBaseRoute.Rejection.LAVA, cell);
                    }
                    if (state.is(net.minecraft.tags.BlockTags.LEAVES)) {
                        continue;
                    }
                    VoxelShape collision = state.getCollisionShape(level, cell, CollisionContext.of(mob));
                    for (AABB shape : collision.toAabbs()) {
                        if (!shape.move(x, y, z).intersects(body)) continue;
                        if (formation != null && isPlannedTiltContact(formation, cell, state, progress)) continue;
                        return new LivingBridgeSafeBaseRoute.Result(
                                LivingBridgeSafeBaseRoute.Rejection.COLLISION, cell);
                    }
                    if (formation == null && isOpenTrapdoor(state) && isTrapdoorDropHazard(level, cell)) {
                        AABB dropPrism = new AABB(x, level.getMinY(), z, x + 1.0D, y + 1.0D, z + 1.0D);
                        if (dropPrism.intersects(body)) {
                            return new LivingBridgeSafeBaseRoute.Result(
                                    LivingBridgeSafeBaseRoute.Rejection.TRAPDOOR_DROP, cell);
                        }
                    }
                }
            }
        }
        return new LivingBridgeSafeBaseRoute.Result(LivingBridgeSafeBaseRoute.Rejection.NONE, null);
    }

    private static boolean isTrapdoorDropHazard(ServerLevel level, BlockPos trapdoor) {
        for (int depth = 1; depth <= 16; depth++) {
            BlockPos cell = trapdoor.below(depth);
            if (cell.getY() < level.getMinY() || !level.isLoaded(cell)) return true;
            BlockState state = level.getBlockState(cell);
            if (state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) return true;
            if (isOpenTrapdoor(state)) continue;
            VoxelShape shape = state.getCollisionShape(level, cell, CollisionContext.empty());
            if (!shape.isEmpty() && shape.max(Direction.Axis.Y) >= 1.0D) return false;
        }
        return true;
    }

    static boolean isPlacedStructuralMember(List<UUID> members, int assembledSlots, UUID candidateId) {
        if (members == null || candidateId == null || assembledSlots <= 0) return false;
        int memberIndex = members.indexOf(candidateId);
        return memberIndex >= 0 && memberIndex < Math.min(assembledSlots, members.size());
    }

    private static boolean isStructuralPoseClear(ServerLevel level, Mob mob, Vec3 from, Vec3 to) {
        if (!isEntityClearAt(level, mob, from, to)) return false;
        Formation formation = getFormation(mob);
        double progress = formation != null ? formation.assembledSlots + 1.0D : 0.0D;
        return resolveClearance(level, mob, to, mob != null ? mob.maxUpStep() : 0.0D, formation, progress).clear();
    }

    static int preflightSampleCount(boolean directSpan) {
        return directSpan ? DIRECT_PREFLIGHT_PROGRESS.length : TILT_PREFLIGHT_PROGRESS.length;
    }

    static double preflightProgress(boolean directSpan, int sample) {
        double[] samples = directSpan ? DIRECT_PREFLIGHT_PROGRESS : TILT_PREFLIGHT_PROGRESS;
        return samples[sample];
    }

    private static BodyGeometry clearanceGeometry(List<Mob> mobs) {
        double width = 0.0D;
        double height = 0.0D;
        for (Mob mob : mobs) {
            BodyGeometry geometry = geometryFor(mob);
            width = Math.max(width, geometry.width());
            height = Math.max(height, geometry.height());
        }
        return width > 0.0D && height > 0.0D ? new BodyGeometry(width, height) : null;
    }

    private static boolean isFormationClear(ServerLevel level, Formation formation, BodyGeometry geometry) {
        return findFormationBlockage(level, formation, geometry) == null;
    }

    private static ClearanceBlockage findFormationBlockage(ServerLevel level, Formation formation, BodyGeometry geometry) {
        if (level == null || formation == null || geometry == null) return new ClearanceBlockage(
                -1, -1, 0.0D, null, null, null, null, false, true, false);
        if (formation.traversalWitness == null
                || (!isCanonicalLivingBridge(formation)
                && !formation.traversalWitness.rosterMatches(level.dimension().identifier(), witnessRoster(formation)))) {
            return new ClearanceBlockage(-1, -1, 0.0D, null, null, null, null, false, true, false);
        }
        formation.traversalWitness.beginShapeTestPass();
        double halfWidth = geometry.width() * 0.5D;
        if (requiresTilt(formation.plan.scenario()) && formation.layerCount > 1) {

            for (int slot = 0; slot < formation.requiredBodies; slot++) {
                Vec3 finalPosition = formationSlotPosition(formation, slot, 1.0D);
                BlockPos center = BlockPos.containing(finalPosition);
                if (!formation.traversalWitness.observeCenterline(level, center)) {
                    return new ClearanceBlockage(slot, -1, 1.0D, finalPosition, null, center,
                            null, false, true, false);
                }
            }
            for (int movingLayer = 1; movingLayer < formation.layerCount; movingLayer++) {
                for (int sample = 0; sample < TILT_PREFLIGHT_PROGRESS.length; sample++) {
                    double movementProgress = TILT_PREFLIGHT_PROGRESS[sample];
                    for (int slot = 0; slot < formation.requiredBodies; slot++) {
                        Vec3 position = bottomUpChainPosition(formation, slot, movingLayer, movementProgress);
                        ClearanceBlockage blockage = findFormationPoseBlockage(level, formation, geometry, halfWidth,
                                slot, movingLayer * TILT_PREFLIGHT_PROGRESS.length + sample,
                                movementProgress, position, false);
                        if (blockage != null) return blockage;
                    }
                }
            }
            return null;
        }
        for (int sample = 0; sample < preflightSampleCount(formation.directSpan); sample++) {
            double progress = preflightProgress(formation.directSpan, sample);
            for (int slot = 0; slot < formation.requiredBodies; slot++) {
                Vec3 position = formationSlotPosition(formation, slot, progress);
                ClearanceBlockage blockage = findFormationPoseBlockage(level, formation, geometry, halfWidth,
                        slot, sample, progress, position, true);
                if (blockage != null) return blockage;
            }
        }
        return null;
    }

    private static ClearanceBlockage findFormationPoseBlockage(ServerLevel level, Formation formation,
                                                                BodyGeometry geometry, double halfWidth,
                                                                int slot, int sample, double progress,
                                                                Vec3 position, boolean observeCenterline) {
        BlockPos center = BlockPos.containing(position);
        if (observeCenterline && !formation.traversalWitness.observeCenterline(level, center)) {
            return new ClearanceBlockage(slot, sample, progress, position, null, center,
                    null, false, true, false);
        }
        AABB box = new AABB(position.x - halfWidth, position.y, position.z - halfWidth,
                position.x + halfWidth, position.y + geometry.height(), position.z + halfWidth);
        if (!formation.traversalWitness.observeEnvelopeBox(level, box)) {
            return new ClearanceBlockage(slot, sample, progress, position, box, center,
                    null, false, true, false);
        }
        ClearanceBlockage blockage = findFormationVolumeBlockage(level, formation, box, progress);
        return blockage == null ? null : blockage.withSlot(slot, sample, position, box);
    }

    private static boolean isFormationVolumeClear(ServerLevel level, Formation formation, AABB box, double progress) {
        return findFormationVolumeBlockage(level, formation, box, progress) == null;
    }

    private static ClearanceBlockage findFormationVolumeBlockage(ServerLevel level, Formation formation, AABB box, double progress) {
        if (level == null || box == null) {
            return new ClearanceBlockage(-1, -1, progress, null, box, null, null, false, true, false);
        }
        if (formation == null || formation.plan == null || isDirectSpan(formation.plan.scenario())) {
            return findPositiveVolumeBlockage(level, box, formation, progress);
        }
        double supportAllowance = Math.max(0.5D, formation.pillarStep);
        ClearanceResolution resolution = resolveSupportOnlyClearance(level, box, formation, progress, supportAllowance);
        return resolution.blockage();
    }

    private static ClearanceResolution resolveSupportOnlyClearance(ServerLevel level, AABB box, Formation formation,
                                                                   double progress, double maxUpStep) {
        if (level == null || box == null) {
            return new ClearanceResolution(null, box, null, 0.0D, null, null, false, false, false,
                    new ClearanceBlockage(-1, -1, progress, null, box, null, null, false, false, false));
        }
        AABB resolved = box;
        double supportLift = 0.0D;
        double lift = findSupportLift(level, resolved, maxUpStep);
        if (lift > 0.0D) {
            resolved = resolved.move(0.0D, lift, 0.0D);
            supportLift = lift;
        }
        ClearanceBlockage blockage = findPositiveVolumeBlockage(level, resolved, formation, progress);
        if (blockage != null) {
            return new ClearanceResolution(null, resolved, null, supportLift, blockage.blocker(), blockage.blockerState(),
                    blockage.unloaded(), blockage.lava(), false, blockage);
        }
        return new ClearanceResolution(resolved.getCenter(), resolved, null, supportLift, null, null, false, false, true, null);
    }

    private static void logSpanClearanceBlockage(ServerLevel level, Formation formation, ClearanceBlockage blockage) {
        if (SoundAttractConfig.COMMON == null || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_ROUTING)
                || formation == null || blockage == null) return;
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Span clearance blocked: bridge={}, phase={}, scenario={}, kind={}, pivot={}, spanEnd={}, slot={}, sample={}, progress={}, slotPosition={}, box={}, blocker={}, blockerState={}, plannedContact={}, unloaded={}, lava={}",
                formation.id, formation.phase, formation.plan.scenario(), bridgeKind(formation.plan.scenario()),
                formation.pivot, formation.spanEnd, blockage.slot, blockage.sample, blockage.progress,
                blockage.slotPosition, blockage.box, blockage.blocker, blockage.blockerState,
                blockage.plannedContact, blockage.unloaded, blockage.lava);
    }

    private record ClearanceBlockage(int slot, int sample, double progress, Vec3 slotPosition, AABB box,
                                     BlockPos blocker, BlockState blockerState, boolean plannedContact,
                                     boolean unloaded, boolean lava) {
        ClearanceBlockage withSlot(int slot, int sample, Vec3 slotPosition, AABB box) {
            return new ClearanceBlockage(slot, sample, progress, slotPosition, box, blocker, blockerState,
                    plannedContact, unloaded, lava);
        }
    }

    private record ClearanceResolution(Vec3 position, AABB box, AABB supportBox, double supportLift,
                                       BlockPos blocker, BlockState blockerState, boolean unloaded,
                                       boolean lava, boolean clear, ClearanceBlockage blockage) {
    }

    private enum ObstacleDisposition {
        CLEAR,
        SIDE_SHELL_CONTACT,
        HARD_SOLID,
        HARD_FEET_SUPPORT,
        HAZARD,
        UNLOADED,
        NO_SUPPORT,
        BUDGET_EXHAUSTED
    }

    private record ObstacleAssessment(BlockPos pos, BlockState state, AABB localBounds, AABB worldBounds,
                                      AABB bodyCore, AABB feetSupport, double thickness, double shellMargin,
                                      boolean bodyCoreValid, boolean intersectsBody, boolean intersectsCore,
                                      boolean intersectsFeetSupport, boolean loaded, boolean fluid, boolean lava,
                                      boolean sideShell, ObstacleDisposition disposition) {
    }

    private record DeflectionCandidate(Vec3 position, Vec3 predecessor, double angle, double score,
                                       int evaluations) {
    }

    record ArticulationCandidate(Vec3 bend, Vec3 predecessor, double angle, double score,
                                 List<Vec3> bends, Map<Integer, Vec3> slots, String fingerprint) {
        ArticulationCandidate {
            Objects.requireNonNull(bend, "bend");
            Objects.requireNonNull(predecessor, "predecessor");
            bends = List.copyOf(Objects.requireNonNull(bends, "bends"));
            slots = Map.copyOf(Objects.requireNonNull(slots, "slots"));
            Objects.requireNonNull(fingerprint, "fingerprint");
        }
    }

    static String canonicalBendSerialization(ConstructionScenario scenario, BlockPos pivot, BlockPos anchor,
                                              BlockPos spanEnd, List<Vec3> bends) {
        if (hasConsecutiveDuplicateBends(bends)) {
            throw new IllegalArgumentException("consecutive articulation bends are not canonical");
        }
        StringBuilder out = new StringBuilder("bridge-route-v1|")
                .append(Objects.toString(scenario, ""))
                .append('|').append(pivot == null ? 0L : pivot.asLong())
                .append('|').append(anchor == null ? 0L : anchor.asLong())
                .append('|').append(spanEnd == null ? 0L : spanEnd.asLong())
                .append('|').append(bends == null ? 0 : bends.size());
        if (bends != null) {
            for (Vec3 bend : bends) {
                if (bend == null || !Double.isFinite(bend.x) || !Double.isFinite(bend.y) || !Double.isFinite(bend.z)) {
                    throw new IllegalArgumentException("non-finite articulation bend");
                }
                out.append('|').append(Math.round(bend.x * 4096.0D))
                        .append(',').append(Math.round(bend.y * 4096.0D))
                        .append(',').append(Math.round(bend.z * 4096.0D));
            }
        }
        return out.toString();
    }

    static String articulationFingerprint(ConstructionScenario scenario, BlockPos pivot, BlockPos anchor,
                                          BlockPos spanEnd, List<Vec3> bends) {
        String serialized = canonicalBendSerialization(scenario, pivot, anchor, spanEnd, bends);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(serialized.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest) hex.append(String.format("%02x", value & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    static boolean hasConsecutiveDuplicateBends(List<Vec3> bends) {
        if (bends == null) return false;
        for (int i = 1; i < bends.size(); i++) {
            Vec3 previous = bends.get(i - 1), current = bends.get(i);
            if (previous == null || current == null || previous.distanceToSqr(current) < 1.0E-12D) return true;
        }
        return false;
    }

    private static final class SourceStagingBudget {
        int sourceOffsetEvaluations;
        int deflectionCandidateEvaluations;
        int fullFormationValidations;
        int diagnosticRecords;
        private final Set<String> diagnostics = new HashSet<>();

        boolean takeOffset() {
            if (sourceOffsetEvaluations >= SOURCE_STAGING_OFFSET_BUDGET) return false;
            sourceOffsetEvaluations++;
            return true;
        }

        boolean takeDeflection() {
            if (deflectionCandidateEvaluations >= SOURCE_STAGING_DEFLECTION_BUDGET) return false;
            deflectionCandidateEvaluations++;
            return true;
        }

        boolean takeValidation() {
            if (fullFormationValidations >= SOURCE_STAGING_VALIDATION_BUDGET) return false;
            fullFormationValidations++;
            return true;
        }

        boolean shouldDiagnose(String key) {
            if (key == null || diagnosticRecords >= SOURCE_STAGING_DIAGNOSTIC_BUDGET || !diagnostics.add(key)) return false;
            diagnosticRecords++;
            return true;
        }
    }

    private static boolean mustPreserveDurableRouteOwner(ServerLevel level, Mob mob) {
        if (level == null || mob == null) return false;
        CompoundTag entityData = com.example.soundattract.component.LivingBridgeEntityData.get(mob);
        if (!entityData.contains("livingBridgeControl")) return false;
        if (!(entityData.get("livingBridgeControl") instanceof CompoundTag)) return false;
        CompoundTag capsule = entityData.getCompoundOrEmpty("livingBridgeControl");
        if (!(capsule.get("schema") instanceof net.minecraft.nbt.IntTag)
                || capsule.getIntOr("schema", -1) != LivingBridgeRouteData.CURRENT_SCHEMA) return false;
        try {
            LivingBridgeRouteSnapshot entity = LivingBridgeRouteSnapshot.fromTag(capsule.getCompoundOrEmpty("snapshot"));
            LivingBridgeRouteSnapshot saved = LivingBridgeRouteData.get(level).snapshot(entity.reservationId());
            if (saved == null || !entity.digest().equals(capsule.getStringOr("snapshotDigest", ""))
                    || !saved.exactlyEquals(entity) || !entity.ownerId().equals(mob.getUUID())
                    || !entity.dimension().equals(level.dimension().identifier().toString())) return false;
            return LivingBridgeSafeBaseRoute.mustPreserveOwner(true,
                    LivingBridgeRouteData.isTerminalLaneState(entity.lifecycleState()));
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    private static void holdDurableRouteOwner(Mob mob) {
        mob.setNoGravity(true);
        mob.noPhysics = true;
        mob.getNavigation().stop();
        mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.0D);
        mob.setDeltaMovement(Vec3.ZERO);
        mob.fallDistance = 0.0F;
        ((MobAccessor) mob).setPersistenceRequiredFlag(true);
    }

    private static boolean isPlannedTiltContact(Formation formation, BlockPos pos, BlockState state, double progress) {
        if (formation == null || pos == null || state == null || formation.plan == null) return false;
        BlockPos spanEnd = tiltStructuralEnd(formation.plan);
        if (spanEnd == null) return false;
        if (pos.equals(spanEnd.below())) return true;
        if (formation.request != null && formation.request.evidence() != null) {
            WallProfileScanner.TargetEvidence evidence = formation.request.evidence();
            if (pos.equals(evidence.wallFace()) || pos.equals(evidence.supportCell())
                    || evidence.structuralCells().contains(pos)) {
                return true;
            }
        }
        int dx = Math.abs(pos.getX() - spanEnd.getX());
        int dz = Math.abs(pos.getZ() - spanEnd.getZ());
        if (Math.max(dx, dz) <= 2 && pos.getY() <= spanEnd.getY() && pos.getY() >= spanEnd.getY() - 4) {
            return true;
        }
        if (state.is(BlockTags.TRAPDOORS) || state.getBlock() instanceof TrapDoorBlock) {
            BlockPos pivot = formation.pivot;
            if (pivot == null) {
                return pos.distManhattan(spanEnd) <= 3;
            }
            int minX = Math.min(pivot.getX(), spanEnd.getX()) - 2;
            int maxX = Math.max(pivot.getX(), spanEnd.getX()) + 2;
            int minY = Math.min(pivot.getY(), spanEnd.getY()) - 2;
            int maxY = Math.max(pivot.getY(), spanEnd.getY()) + 2;
            int minZ = Math.min(pivot.getZ(), spanEnd.getZ()) - 2;
            int maxZ = Math.max(pivot.getZ(), spanEnd.getZ()) + 2;
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
        return false;
    }

    private static boolean isVolumeClear(ServerLevel level, AABB box) {
        return resolveClearance(level, box, null, 0.0D, null, 0.0D).clear();
    }

    private static ClearanceResolution resolveClearance(ServerLevel level, Mob mob, Vec3 position, double maxUpStep) {
        return resolveClearance(level, mob, position, maxUpStep, false);
    }

    private static ClearanceResolution resolveClearance(ServerLevel level, Mob mob, Vec3 position,
                                                        double maxUpStep, boolean sourceStaging) {
        if (level == null || mob == null || position == null) {
            return new ClearanceResolution(position, null, null, 0.0D, null, null, false, false, false,
                    new ClearanceBlockage(-1, -1, 0.0D, position, null, null, null, false, false, false));
        }
        double halfWidth = mob.getBbWidth() * 0.5D;
        AABB box = new AABB(
                position.x - halfWidth,
                position.y,
                position.z - halfWidth,
                position.x + halfWidth,
                position.y + mob.getBbHeight(),
                position.z + halfWidth);
        return resolveClearance(level, box, mob, maxUpStep, null, 0.0D, sourceStaging);
    }

    private static ClearanceResolution resolveClearance(ServerLevel level, Mob mob, Vec3 position,
                                                        double maxUpStep, Formation formation, double progress) {
        return resolveClearance(level, mob, position, maxUpStep, formation, progress, false);
    }

    private static ClearanceResolution resolveClearance(ServerLevel level, Mob mob, Vec3 position,
                                                        double maxUpStep, Formation formation, double progress,
                                                        boolean sourceStaging) {
        if (level == null || mob == null || position == null) {
            return new ClearanceResolution(position, null, null, 0.0D, null, null, false, false, false,
                    new ClearanceBlockage(-1, -1, progress, position, null, null, null, false, false, false));
        }
        double halfWidth = mob.getBbWidth() * 0.5D;
        AABB box = new AABB(
                position.x - halfWidth,
                position.y,
                position.z - halfWidth,
                position.x + halfWidth,
                position.y + mob.getBbHeight(),
                position.z + halfWidth);
        return resolveClearance(level, box, mob, maxUpStep, formation, progress, sourceStaging);
    }

    private static ClearanceResolution resolveClearance(ServerLevel level, AABB box, Mob mob, double maxUpStep,
                                                        Formation formation, double progress) {
        return resolveClearance(level, box, mob, maxUpStep, formation, progress, false);
    }

    private static ClearanceResolution resolveClearance(ServerLevel level, AABB box, Mob mob, double maxUpStep,
                                                        Formation formation, double progress, boolean sourceStaging) {
        if (level == null || box == null) {
            return new ClearanceResolution(null, box, null, 0.0D, null, null, false, false, false,
                    new ClearanceBlockage(-1, -1, progress, null, box, null, null, false, false, false));
        }
        AABB resolved = box;
        double supportLift = 0.0D;
        BlockPos blocker = null;
        BlockState blockerState = null;
        boolean unloaded = false;
        boolean lava = false;
        if (!level.isLoaded(BlockPos.containing(resolved.minX, resolved.minY, resolved.minZ))
                || !level.isLoaded(BlockPos.containing(resolved.maxX, resolved.maxY, resolved.maxZ))) {
            blocker = BlockPos.containing(resolved.minX, resolved.minY, resolved.minZ);
            unloaded = true;
            ClearanceBlockage unloadedBlockage = new ClearanceBlockage(
                    -1, -1, progress, null, resolved, blocker, null, false, true, false);
            return new ClearanceResolution(null, resolved, null, supportLift, blocker, null,
                    true, false, false, unloadedBlockage);
        } else {
            ClearanceBlockage firstBlockage = findPositiveVolumeBlockage(level, resolved, formation, progress,
                    sourceStaging);
            boolean hazardBlocked = firstBlockage != null && (firstBlockage.lava() || firstBlockage.unloaded());
            if (!hazardBlocked && mob != null && maxUpStep > 0.0D) {
                double lift = findSupportLift(level, resolved, mob, maxUpStep);
                if (lift > 0.0D) {
                    AABB lifted = resolved.move(0.0D, lift, 0.0D);
                    ClearanceBlockage liftedBlockage = findPositiveVolumeBlockage(level, lifted, formation, progress,
                            sourceStaging);
                    if (liftedBlockage == null) {
                        resolved = lifted;
                        supportLift = lift;
                        firstBlockage = null;
                    } else {
                        firstBlockage = liftedBlockage;
                    }
                }
            }
            if (firstBlockage != null) {
                blocker = firstBlockage.blocker();
                blockerState = firstBlockage.blockerState();
                unloaded = firstBlockage.unloaded();
                lava = firstBlockage.lava();
                return new ClearanceResolution(null, resolved, null, supportLift, blocker, blockerState,
                        unloaded, lava, false, firstBlockage);
            }
        }
        Vec3 resolvedPosition = resolved.getCenter();
        if (mob != null) {
            resolvedPosition = new Vec3(resolvedPosition.x, resolved.minY, resolvedPosition.z);
        }
        return new ClearanceResolution(resolvedPosition, resolved, null, supportLift, blocker, blockerState,
                unloaded, lava, true, null);
    }

    private static double findSupportLift(ServerLevel level, AABB box, Mob mob, double maxUpStep) {
        double maxLift = Math.min(Math.max(0.0D, maxUpStep), Math.max(0.0D, mob.maxUpStep()));
        return findSupportLift(level, box, maxLift);
    }

    private static double findSupportLift(ServerLevel level, AABB box, double maxUpStep) {
        double maxLift = Math.max(0.0D, maxUpStep);
        if (maxLift <= 0.0D) return 0.0D;
        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX - 1.0E-7D);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ - 1.0E-7D);
        double best = 0.0D;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = Mth.floor(box.minY); y >= Mth.floor(box.minY) - 2; y--) {
                    cursor.set(x, y, z);
                    if (!level.isLoaded(cursor)) return 0.0D;
                    BlockState state = level.getBlockState(cursor);
                    VoxelShape shape = state.getCollisionShape(level, cursor);
                    if (shape.isEmpty() || state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) continue;
                    AABB support = shape.bounds().move(cursor);
                    if (support.maxY > box.minY + CONTACT_EPSILON) {
                        double lift = support.maxY - box.minY;
                        if (lift > best && lift <= maxLift + CONTACT_EPSILON
                                && findPositiveVolumeBlockage(level, box.move(0.0D, lift, 0.0D), null, 0.0D) == null) {
                            best = lift;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static ClearanceBlockage findPositiveVolumeBlockage(ServerLevel level, AABB box,
                                                                Formation formation, double progress) {
        return findPositiveVolumeBlockage(level, box, formation, progress, false);
    }

    private static ClearanceBlockage findPositiveVolumeBlockage(ServerLevel level, AABB box,
                                                                Formation formation, double progress,
                                                                boolean sourceStaging) {
        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX - 1.0E-7D);
        int minY = Mth.floor(box.minY);
        int maxY = Mth.floor(box.maxY - 1.0E-7D);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ - 1.0E-7D);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        VoxelShape probe = Shapes.create(box);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    if (formation != null && formation.traversalWitness != null) {
                        if (!formation.traversalWitness.observeEnvelopeBox(level, new AABB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D))) {
                            return new ClearanceBlockage(-1, -1, progress, null, box,
                                    cursor.immutable(), null, false, true, false);
                        }
                    }
                    if (!level.isLoaded(cursor)) {
                        return new ClearanceBlockage(-1, -1, progress, null, box,
                                cursor.immutable(), null, false, true, false);
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
                        return new ClearanceBlockage(-1, -1, progress, null, box,
                                cursor.immutable(), state, false, false, true);
                    }
                    if (state.is(net.minecraft.tags.BlockTags.LEAVES)) {
                        continue;
                    }
                    VoxelShape shape = state.getCollisionShape(level, cursor);
                    if (shape.isEmpty()) {
                        continue;
                    }
                    if (formation != null && formation.traversalWitness != null
                            && !formation.traversalWitness.recordShapeTest()) {
                        return new ClearanceBlockage(-1, -1, progress, null, box,
                                cursor.immutable(), state, false, true, false);
                    }
                    AABB shapeBounds = shape.bounds().move(cursor.getX(), cursor.getY(), cursor.getZ());
                    if (shapeBounds.maxY <= box.minY + CONTACT_EPSILON) {
                        continue;
                    }
                    if (isPlannedTiltContact(formation, cursor, state, progress)) {
                        continue;
                    }
                    if (isOpenTrapdoor(state)) {
                        continue;
                    }
                    AABB worldShapeBounds = shapeBounds;
                    if (sourceStaging) {
                        ObstacleAssessment assessment = assessSourceObstacle(level, cursor.immutable(), state,
                                shape.bounds(), worldShapeBounds, box);
                        if (assessment.disposition() == ObstacleDisposition.SIDE_SHELL_CONTACT) {
                            continue;
                        }
                        if (assessment.disposition() == ObstacleDisposition.HARD_FEET_SUPPORT
                                || assessment.disposition() == ObstacleDisposition.HAZARD
                                || assessment.disposition() == ObstacleDisposition.UNLOADED
                                || assessment.disposition() == ObstacleDisposition.NO_SUPPORT) {
                            return new ClearanceBlockage(-1, -1, progress, null, box,
                                    cursor.immutable(), state,
                                    assessment.disposition() == ObstacleDisposition.NO_SUPPORT,
                                    assessment.disposition() == ObstacleDisposition.UNLOADED,
                                    assessment.disposition() == ObstacleDisposition.HAZARD);
                        }
                    }
                    if (Shapes.joinIsNotEmpty(shape.move(cursor.getX(), cursor.getY(), cursor.getZ()), probe, BooleanOp.AND)) {
                        return new ClearanceBlockage(-1, -1, progress, null, box,
                                cursor.immutable(), state, false, false, false);
                    }
                }
            }
        }
        return null;
    }

    private static DeflectionCandidate findBoundedDeflectionCandidate(ServerLevel level, Formation formation,
                                                                       int nextSlot, Mob mob, BlockPos blocker,
                                                                       List<Vec3> elbows,
                                                                       Map<Integer, Vec3> slots,
                                                                       SourceStagingBudget budget) {
        if (level == null || formation == null || mob == null || blocker == null || nextSlot <= 0
                || elbows == null || slots == null || !isArticulationEnabled(formation)) return null;
        int maxBends = LivingWallConfig.MAX_BRIDGE_BENDS != null ? LivingWallConfig.MAX_BRIDGE_BENDS.get() : 2;
        if (elbows.size() >= maxBends || formation.plan == null) return null;
        BlockPos structuralEnd = tiltStructuralEnd(formation.plan);
        if (structuralEnd == null) return null;
        Vec3 destination = Vec3.atBottomCenterOf(structuralEnd);
        int columns = Math.max(1, formation.columnCount);
        int foundationColumn = foundationColumnForSlot(nextSlot, columns);
        int prevSlot = nextSlot - 1;
        int predecessorKey = slots.containsKey(prevSlot) ? prevSlot : foundationColumn;
        Vec3 prevPos = slots.containsKey(predecessorKey)
                ? slots.get(predecessorKey)
                : (prevSlot > 0
                ? formationSlotPosition(formation, prevSlot, 1.0D)
                : formationSlotBasePosition(formation, foundationColumn));
        int radius = LivingWallConfig.OBSTACLE_DEFLECTION_RADIUS != null
                ? LivingWallConfig.OBSTACLE_DEFLECTION_RADIUS.get() : 2;
        double minAngle = LivingWallConfig.MIN_BEND_ANGLE_DEGREES != null
                ? LivingWallConfig.MIN_BEND_ANGLE_DEGREES.get() : 75.0D;
        double maxDistanceSqr = (formation.plan.scenario() == ConstructionScenario.DROP_TO_LEDGE && prevSlot <= 0)
                ? 25.0D : 6.0D;
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        double bestAngle = 0.0D;
        int evaluations = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    if (budget != null && !budget.takeDeflection()) return null;
                    evaluations++;
                    BlockPos candidateCell = blocker.offset(dx, dy, dz);
                    Vec3 candidate = Vec3.atBottomCenterOf(candidateCell);
                    double distance = candidate.distanceToSqr(prevPos);
                    if (distance < 0.25D || distance > maxDistanceSqr) continue;
                    boolean duplicate = elbows.stream().anyMatch(existing -> existing.distanceToSqr(candidate) < 0.25D);
                    if (duplicate || !isSlotClear(level, mob, candidate)) continue;
                    if (!resolveClearance(level, mob, prevPos.lerp(candidate, 0.5D), 0.0D).clear()) continue;
                    boolean contact = false;
                    for (Direction direction : Direction.values()) {
                        BlockPos neighbor = candidateCell.relative(direction);
                        if (neighbor.equals(blocker) || !level.getBlockState(neighbor)
                                .getCollisionShape(level, neighbor).isEmpty()) {
                            contact = true;
                            break;
                        }
                    }
                    if (!contact) continue;
                    double angle = calculateInteriorAngleDegrees(prevPos, candidate, destination);
                    if (angle < minAngle || candidate.distanceTo(destination) > prevPos.distanceTo(destination) + 1.2D) continue;
                    Vec3 out = destination.subtract(candidate);
                    if (out.lengthSqr() > 1.0E-4D
                            && BlockPos.containing(candidate.add(out.normalize().scale(0.8D))).equals(blocker)) continue;
                    double score = candidate.distanceTo(destination) * 1.5D + Math.sqrt(distance) - angle / 180.0D;
                    if (score < bestScore) {
                        best = candidate;
                        bestScore = score;
                        bestAngle = angle;
                    }
                }
            }
        }
        return best == null ? null : new DeflectionCandidate(best, prevPos, bestAngle, bestScore, evaluations);
    }

    private static boolean isServerThread(ServerLevel level) {
        return level != null && level.getServer() != null && level.getServer().isSameThread();
    }

    private static ArticulationCandidate solveArticulationCandidate(ServerLevel level, Formation formation,
                                                                     int nextSlot, Mob mob, BlockPos blocker,
                                                                     SourceStagingBudget budget) {
        if (!isServerThread(level) || formation == null || mob == null || blocker == null) return null;
        List<Vec3> elbows = new ArrayList<>(formation.elbowPoints);
        Map<Integer, Vec3> slots = new HashMap<>(formation.structuralSlotPositions);
        DeflectionCandidate result = findBoundedDeflectionCandidate(level, formation, nextSlot, mob, blocker,
                elbows, slots, budget);
        if (result == null) return null;
        elbows.add(result.position());
        slots.put(foundationColumnForSlot(nextSlot, formation.columnCount), result.position());
        if (hasConsecutiveDuplicateBends(elbows)) return null;
        String fingerprint = articulationFingerprint(formation.plan.scenario(), formation.pivot,
                formation.anchor, formation.spanEnd, elbows);
        return new ArticulationCandidate(result.position(), result.predecessor(), result.angle(), result.score(),
                elbows, slots, fingerprint);
    }

    private static boolean isArticulationEnabled(Formation formation) {
        return formation != null && formation.elbowPoints != null
                && LivingWallConfig.ENABLE_ARTICULATED_BRIDGES != null
                && LivingWallConfig.ENABLE_ARTICULATED_BRIDGES.get();
    }

    private static ObstacleAssessment assessSourceObstacle(ServerLevel level, BlockPos pos, BlockState state,
                                                            AABB localBounds, AABB worldBounds, AABB body) {
        boolean loaded = level != null && pos != null && level.isLoaded(pos);
        boolean fluid = state != null && !state.getFluidState().isEmpty();
        boolean lava = state != null && state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
        double thickness = worldBounds == null ? Double.POSITIVE_INFINITY
                : Math.min(worldBounds.getXsize(), Math.min(worldBounds.getYsize(), worldBounds.getZsize()));
        double shellMargin = TRAPDOOR_SHELL_MARGIN;
        boolean bodyCoreValid = body != null
                && body.getXsize() > shellMargin * 2.0D
                && body.getYsize() > shellMargin * 2.0D
                && body.getZsize() > shellMargin * 2.0D;
        AABB bodyCore = bodyCoreValid ? body.inflate(-shellMargin, -shellMargin, -shellMargin) : null;
        AABB feetSupport = body == null ? null : new AABB(
                body.minX + shellMargin, body.minY - CONTACT_EPSILON, body.minZ + shellMargin,
                body.maxX - shellMargin, body.minY + 0.25D, body.maxZ - shellMargin);
        boolean supportEnvelopeValid = feetSupport != null && feetSupport.getXsize() > 0.0D && feetSupport.getZsize() > 0.0D;
        boolean intersectsBody = worldBounds != null && body != null && worldBounds.intersects(body);
        boolean intersectsCore = worldBounds != null && bodyCoreValid && worldBounds.intersects(bodyCore);
        boolean intersectsFeet = worldBounds != null && supportEnvelopeValid && worldBounds.intersects(feetSupport);
        boolean thinTrapdoor = state != null && state.is(BlockTags.TRAPDOORS) && thickness < TRAPDOOR_THINNESS_LIMIT;
        boolean bottomTrapdoor = state != null && state.is(BlockTags.TRAPDOORS)
                && state.hasProperty(TrapDoorBlock.HALF)
                && state.getValue(TrapDoorBlock.HALF) == net.minecraft.world.level.block.state.properties.Half.BOTTOM;
        boolean feetHazard = intersectsFeet && (!thinTrapdoor || (bottomTrapdoor && coversSupportCenter(worldBounds, body)));
        boolean sideShell = thinTrapdoor && loaded && !fluid && intersectsBody && !intersectsCore && !feetHazard;
        ObstacleDisposition disposition;
        if (!loaded) disposition = ObstacleDisposition.UNLOADED;
        else if (lava || fluid) disposition = ObstacleDisposition.HAZARD;
        else if (feetHazard) disposition = ObstacleDisposition.HARD_FEET_SUPPORT;
        else if (!bodyCoreValid && intersectsBody) disposition = ObstacleDisposition.HARD_SOLID;
        else if (sideShell) disposition = ObstacleDisposition.SIDE_SHELL_CONTACT;
        else if (intersectsBody) disposition = ObstacleDisposition.HARD_SOLID;
        else disposition = ObstacleDisposition.CLEAR;
        return new ObstacleAssessment(pos, state, localBounds, worldBounds, bodyCore, feetSupport, thickness,
                shellMargin, bodyCoreValid, intersectsBody, intersectsCore, intersectsFeet, loaded, fluid, lava,
                sideShell, disposition);
    }

    private static boolean coversSupportCenter(AABB shape, AABB body) {
        if (shape == null || body == null) return false;
        double centerX = (body.minX + body.maxX) * 0.5D;
        double centerZ = (body.minZ + body.maxZ) * 0.5D;
        boolean horizontal = shape.getYsize() < TRAPDOOR_THINNESS_LIMIT
                && shape.getXsize() >= 0.5D && shape.getZsize() >= 0.5D;
        return horizontal && centerX > shape.minX + CONTACT_EPSILON && centerX < shape.maxX - CONTACT_EPSILON
                && centerZ > shape.minZ + CONTACT_EPSILON && centerZ < shape.maxZ - CONTACT_EPSILON;
    }

    private static BlockPos findBlockingSlotBlock(ServerLevel level, Mob mob, Vec3 position) {
        if (level == null || mob == null || position == null) return null;
        AABB box = mob.getBoundingBox().move(position.subtract(mob.position()));
        AABB clearanceBox = box.deflate(CONTACT_EPSILON, 0.0D, CONTACT_EPSILON);
        int minX = Mth.floor(clearanceBox.minX);
        int maxX = Mth.floor(clearanceBox.maxX - 1.0E-7D);
        int minY = Mth.floor(clearanceBox.minY + CONTACT_EPSILON);
        int maxY = Mth.floor(clearanceBox.maxY - 1.0E-7D);
        int minZ = Mth.floor(clearanceBox.minZ);
        int maxZ = Mth.floor(clearanceBox.maxZ - 1.0E-7D);
        double stepAllowance = Math.max(0.6D, mob.maxUpStep());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    if (!level.isLoaded(cursor)) return cursor.immutable();
                    BlockState state = level.getBlockState(cursor);
                    if (state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
                        return cursor.immutable();
                    }
                    VoxelShape shape = state.getCollisionShape(level, cursor);
                    if (shape.isEmpty()) continue;
                    for (AABB bb : shape.toAabbs()) {
                        AABB worldBb = bb.move(x, y, z);
                        if (worldBb.maxY > clearanceBox.minY + stepAllowance && worldBb.intersects(clearanceBox)) {
                            return cursor.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    private static void driveCrossers(ServerLevel level, Formation formation, long now) {
        Mob leader = resolvePlannerMob(level, formation);
        if (formation.integrity < INTEGRITY_THRESHOLD) {
            return;
        }
        if (formation.lastCrosserRecruitTick < 0L
                || now - formation.lastCrosserRecruitTick >= BRIDGE_ACCESS_SCAN_INTERVAL_TICKS) {
            formation.lastCrosserRecruitTick = now;
            if (formation.crosserTransit.size() < MAX_CROSSERS) {
                List<Mob> candidates = leader == null
                        ? new ArrayList<>()
                        : new ArrayList<>(MobGroupManager.getFollowers(leader));
                if (leader != null && !formation.structuralBuilderIds.contains(leader.getUUID())) candidates.add(leader);
                for (UUID id : formation.localRoster) {
                    Mob candidate = getMob(level, id);
                    if (candidate != null) candidates.add(candidate);
                }
                Set<UUID> seen = new HashSet<>();
                candidates.removeIf(mob -> !seen.add(mob.getUUID())
                        || !isCrosserCandidateEligible(level, formation, mob, now));
                candidates.sort(Comparator.comparingDouble(
                        mob -> mob.distanceToSqr(Vec3.atBottomCenterOf(formation.pivot))));
                for (Mob candidate : candidates) {
                    if (formation.crosserTransit.size() >= MAX_CROSSERS) break;
                    IntakeWindow window = selectCrosserIntakeWindow(level, formation, candidate);
                    if (window == null) continue;
                    if (reserveCrosser(level, candidate, formation)) {
                        CrosserTransit transit = new CrosserTransit(candidate.getUUID(), window);
                        transit.slot = 0;
                        transit.stateTick = now;
                        formation.crosserTransit.put(candidate.getUUID(), transit);
                        formation.crosserSlots.put(candidate.getUUID(), 0);
                        formation.crosserOrder.add(candidate.getUUID());
                    }
                }
                if (!candidates.isEmpty()) formation.lastTraverseActivityTick = now;
            }
            recruitNearbyCrossers(level, formation, now, formation.observation);
        }

        formation.activeCrossers = formation.crosserTransit.size();
        int processed = 0;
        int orderSize = formation.crosserOrder.size();
        for (int i = 0; i < orderSize && processed < BRIDGE_FOLLOWER_UPDATE_BUDGET; i++) {
            if (formation.crosserOrder.isEmpty()) {
                break;
            }
            if (formation.crosserCursor >= formation.crosserOrder.size()) {
                formation.crosserCursor = 0;
            }
            UUID uuid = formation.crosserOrder.get(formation.crosserCursor++);
            CrosserTransit transit = formation.crosserTransit.get(uuid);
            if (transit == null) {
                continue;
            }
            processed++;
            Mob mob = getMob(level, uuid);
            if (mob == null || !mob.isAlive() || mob.isRemoved()) {
                releaseCrosser(level, formation, uuid);
                removeCrosserTransit(formation, uuid);
                continue;
            }
            formation.lastTraverseActivityTick = now;
            switch (transit.state) {
                case APPROACH_ENTRY -> {
                    if (!driveCrosserApproach(level, formation, mob, transit, now)) {
                        releaseCrosser(level, formation, uuid);
                        removeCrosserTransit(formation, uuid);
                    }
                }
                case CAPTURE_ENTRY -> {
                    if (now - transit.stateTick > 300L || transit.captureTicks > CROSSER_INTAKE_MAX_TICKS) {
                        releaseCrosser(level, formation, uuid);
                        removeCrosserTransit(formation, uuid);
                        break;
                    }
                    boolean slotZeroOccupied = formation.crosserTransit.values().stream()
                            .anyMatch(t -> t != transit && t.state == CrosserTransitState.TRAVERSE && t.slot == 0);
                    if (!slotZeroOccupied) {
                        CrosserPullResult pullResult = tryControlledCrosserPull(level, formation, mob, transit);
                        if (pullResult == CrosserPullResult.SETTLED) {
                            transit.state = CrosserTransitState.TRAVERSE;
                            transit.stateTick = now;
                            transit.slot = 0;
                            transit.stuckTicks = 0;
                            formation.crosserSlots.put(uuid, 0);
                            ensureBridgeWorkGoal(mob);
                        } else if (pullResult == CrosserPullResult.FAILED) {
                            releaseCrosser(level, formation, uuid);
                            removeCrosserTransit(formation, uuid);
                        }
                    }
                }
                case TRAVERSE, DISMOUNT -> {
                    if (!advanceCrosser(level, formation, mob, transit, now)) {
                        releaseCrosser(level, formation, uuid);
                        removeCrosserTransit(formation, uuid);
                    }
                }
                case FALLING -> {
                    if (updateFallingCrosser(level, formation, mob, transit)) {
                        formation.completedCrossers.add(uuid);
                        formation.crosserCompletionTicks.put(uuid, level.getGameTime());
                        removeCrosserTransit(formation, uuid);
                    }
                }
                case LANDING_VALIDATION -> {
                    if (updateLandingValidation(level, formation, mob, transit, now)) {
                        formation.completedCrossers.add(uuid);
                        formation.crosserCompletionTicks.put(uuid, now);
                        removeCrosserTransit(formation, uuid);
                    }
                }
                case HANDED_OFF, ABORTED -> {
                    releaseCrosser(level, formation, uuid);
                    removeCrosserTransit(formation, uuid);
                }
            }
        }
        formation.activeCrossers = formation.crosserTransit.size();
    }

    private static boolean isCrosserCandidateEligible(ServerLevel level, Formation formation, Mob mob, long now) {
        if (mob == null || !mob.isAlive() || mob.isRemoved() || !isEligible(mob)) {
            return false;
        }
        if (formation == null || formation.pivot == null) {
            return false;
        }
        UUID uuid = mob.getUUID();
        if (formation.structuralBuilderIds.contains(uuid)
                || formation.members.contains(uuid)
                || formation.crosserTransit.containsKey(uuid)) {
            return false;
        }
        UUID owner = state(level).mobOwners.get(uuid);
        if (owner != null && !owner.equals(formation.id)) {
            return false;
        }
        if (formation.phase != Phase.TRAVERSE && formation.reserved.contains(uuid)) {
            return false;
        }
        if (formation.plan != null && requiresTilt(formation.plan.scenario())) {
            Vec3 baseStation = slotPosition(formation, 0);
            double distSq = mob.distanceToSqr(baseStation);
            if (distSq > BRIDGE_ENTRY_RADIUS * BRIDGE_ENTRY_RADIUS) {
                return false;
            }
            if (mob.getY() < baseStation.y - 2.5D || mob.getY() > baseStation.y + 3.0D) {
                return false;
            }
            Vec3 anchorPos = formation.anchor != null ? Vec3.atBottomCenterOf(formation.anchor) : baseStation;
            Vec3 delta = anchorPos.subtract(baseStation);
            double bridgeLen = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            if (bridgeLen > 0.001D) {
                double forwardProj = ((mob.getX() - baseStation.x) * delta.x + (mob.getZ() - baseStation.z) * delta.z) / bridgeLen;
                if (forwardProj > bridgeLen * 0.5D) {
                    return false;
                }
            }
        }
        double distToPivotSqr = mob.distanceToSqr(Vec3.atBottomCenterOf(formation.pivot));
        if (!isWithinBridgeEntryRecruitmentRange(formation.localRoster.contains(uuid), distToPivotSqr, BRIDGE_ENTRY_RADIUS)) {
            return false;
        }

        if (formation.completedCrossers.contains(uuid)) {
            Long completionTick = formation.crosserCompletionTicks.get(uuid);
            BlockPos destPos = formation.anchor != null ? formation.anchor : formation.spanEnd;
            if (destPos != null) {
                Vec3 destVec = Vec3.atBottomCenterOf(destPos);
                double distToDestSqr = mob.distanceToSqr(destVec);
                boolean atDestElevation = mob.getY() >= destPos.getY() - 1.25D;
                boolean closerToDest = distToDestSqr < distToPivotSqr;
                long minCooldown = (atDestElevation && closerToDest) ? 100L : 20L;
                if (completionTick != null && now - completionTick < minCooldown) {
                    return false;
                }
                LivingEntity target = mob.getTarget();

                if (atDestElevation && closerToDest) {
                    if (target != null && target.isAlive()) {
                        if (mob.distanceToSqr(target) <= 9.0D) {
                            return false;
                        }
                        var path = mob.getNavigation().createPath(target, 0);
                        if (path != null && path.canReach()) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
            } else if (completionTick != null && now - completionTick < 40L) {
                return false;
            }
        }
        return true;
    }

    static boolean isWithinBridgeEntryRecruitmentRange(boolean committedRosterMember,
                                                        double distanceSqr, double entryRadius) {
        return committedRosterMember || Double.isFinite(distanceSqr) && distanceSqr >= 0.0D
                && distanceSqr <= entryRadius * entryRadius;
    }

    private static void recruitNearbyCrossers(ServerLevel level, Formation formation, long now) {
        recruitNearbyCrossers(level, formation, now, formation == null ? null : formation.observation);
    }

    private static void recruitNearbyCrossers(ServerLevel level, Formation formation, long now,
                                               ConstructionObservation observation) {
        if (level == null || formation == null || formation.crosserTransit.size() >= MAX_CROSSERS) return;
        int minX = Math.min(formation.pivot.getX(), formation.anchor.getX()) - Mth.ceil((float) BRIDGE_ACCESS_RADIUS);
        int maxX = Math.max(formation.pivot.getX(), formation.anchor.getX()) + Mth.ceil((float) BRIDGE_ACCESS_RADIUS);
        int minZ = Math.min(formation.pivot.getZ(), formation.anchor.getZ()) - Mth.ceil((float) BRIDGE_ACCESS_RADIUS);
        int maxZ = Math.max(formation.pivot.getZ(), formation.anchor.getZ()) + Mth.ceil((float) BRIDGE_ACCESS_RADIUS);
        int minY = Math.min(formation.pivot.getY(), formation.anchor.getY()) - Math.max(8, LivingWallConfig.MAX_HEIGHT.get());
        int maxY = Math.max(formation.pivot.getY(), formation.anchor.getY()) + LivingWallConfig.MAX_HEIGHT.get() + 2;
        AABB scan = new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
        List<Mob> candidates = level.getEntitiesOfClass(Mob.class, scan,
                mob -> isCrosserCandidateEligible(level, formation, mob, now));
        candidates.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(Vec3.atBottomCenterOf(formation.pivot))));
        int added = 0;
        for (Mob candidate : candidates) {
            if (formation.crosserTransit.size() >= MAX_CROSSERS || added >= BRIDGE_ACCESS_SCAN_BUDGET) break;
            IntakeWindow window = selectCrosserIntakeWindow(level, formation, candidate);
            if (window == null) continue;
            if (reserveCrosser(level, candidate, formation)) {
                CrosserTransit transit = new CrosserTransit(candidate.getUUID(), window);
                transit.slot = 0;
                transit.stateTick = now;
                formation.crosserTransit.put(candidate.getUUID(), transit);
                formation.crosserSlots.put(candidate.getUUID(), 0);
                formation.crosserOrder.add(candidate.getUUID());
                added++;
            }
        }
        if (added > 0) formation.lastTraverseActivityTick = now;
    }

    private static void removeCrosserTransit(Formation formation, UUID uuid) {
        if (formation == null || uuid == null) return;
        formation.crosserTransit.remove(uuid);
        formation.crosserSlots.remove(uuid);
    }

    private static boolean canMobUseCrosserIntakeWindow(ServerLevel level, Mob mob, IntakeWindow window, Formation formation) {
        if (level == null || mob == null || window == null || formation == null || window.feederCell() == null) {
            return false;
        }
        BlockPos cell = window.feederCell();
        if (!level.isLoaded(cell) || !level.isLoaded(mob.blockPosition()) || mob.isInLava()) {
            return false;
        }
        if (formation.plan != null && requiresTilt(formation.plan.scenario())) {
            Vec3 baseStation = slotPosition(formation, 0);
            double distSq = mob.position().distanceToSqr(baseStation);
            double yDiff = Math.abs(mob.getY() - baseStation.y);
            return distSq <= 36.0D && yDiff <= 3.0D && !mob.isInLava();
        }
        BlockState support = level.getBlockState(cell.below());
        if (support.isAir() || !support.getFluidState().isEmpty() || support.is(BlockTags.TRAPDOORS)) {
            return false;
        }
        BlockState feet = level.getBlockState(cell);
        if (feet.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
            return false;
        }
        BlockPos basePos = formation.pivot != null ? formation.pivot
                : (formation.plan != null ? formation.plan.pivot() : null);
        if (basePos != null) {
            if (Math.abs(cell.getY() - basePos.getY()) > 1) {
                return false;
            }
            int dx = cell.getX() - basePos.getX();
            int dz = cell.getZ() - basePos.getZ();
            if (dx * dx + dz * dz > 16) {
                return false;
            }
        }
        return true;
    }

    private static IntakeWindow selectCrosserIntakeWindow(ServerLevel level, Formation formation, Mob mob) {
        if (formation == null || formation.plan == null) return null;
        if (requiresTilt(formation.plan.scenario())) {
            Vec3 baseStation = slotPosition(formation, 0);
            BlockPos baseCell = BlockPos.containing(baseStation);
            Vec3 anchorPos = formation.anchor != null ? Vec3.atBottomCenterOf(formation.anchor) : baseStation;
            Vec3 delta = anchorPos.subtract(baseStation);
            Vec3 approachDir = new Vec3(-delta.x, 0.0D, -delta.z);
            BlockPos approachCell = approachDir.lengthSqr() > 0.001D
                    ? baseCell.offset(BlockPos.containing(approachDir.normalize().scale(1.5D)))
                    : baseCell;
            IntakeWindow window = new IntakeWindow(baseCell, approachCell, true);
            return canMobUseCrosserIntakeWindow(level, mob, window, formation) ? window : null;
        }
        IntakeWindow chosen = selectIntakeWindow(formation.plan.intakeWindows(),
                window -> window != null && canMobUseCrosserIntakeWindow(level, mob, window, formation));
        if (chosen != null) {
            return chosen;
        }
        BlockPos baseCell = formation.pivot;
        if (baseCell == null && formation.plan != null) {
            baseCell = formation.plan.pivot();
        }
        if (baseCell != null && level != null && level.isLoaded(baseCell)) {
            IntakeWindow fallback = new IntakeWindow(baseCell, baseCell, true);
            if (canMobUseCrosserIntakeWindow(level, mob, fallback, formation)) {
                return fallback;
            }
        }
        return null;
    }

    private static IntakeWindow selectCrosserIntakeWindow(ConstructionObservation observation, Mob mob) {
        if (observation == null || mob == null) return null;
        WorkerObservation worker = observation.workers().get(mob.getUUID());
        return worker == null ? null : worker.intakeWindow();
    }

    private static boolean driveCrosserApproach(ServerLevel level, Formation formation, Mob mob,
                                                CrosserTransit transit, long now) {
        IntakeWindow window = transit.intakeWindow;
        if (window == null || window.feederCell() == null || !canMobUseCrosserIntakeWindow(level, mob, window, formation)) {
            return false;
        }
        if (now - transit.stateTick > 300L) {
            return false;
        }
        if (formation.plan != null && requiresTilt(formation.plan.scenario())) {
            Vec3 baseStation = slotPosition(formation, 0);
            double distSq = mob.position().distanceToSqr(baseStation);
            double yDiff = Math.abs(mob.getY() - baseStation.y);
            if (distSq <= 3.0D && yDiff <= 2.0D) {
                transit.state = CrosserTransitState.CAPTURE_ENTRY;
                transit.stateTick = now;
                mob.getNavigation().stop();
                ensureBridgeWorkGoal(mob);
                return true;
            }
            com.example.soundattract.pathfinding.PursuitNavigation.maybeMoveTo(mob, baseStation, 1.2D);
            return true;
        }
        BlockPos routeTarget = window.feederApproach() != null && isIntakeRouteTarget(window, window.feederApproach())
                ? window.feederApproach() : window.feederCell();
        if (routeTarget == null) return false;
        if (routeTarget.equals(window.feederApproach()) && mob.blockPosition().closerThan(window.feederApproach(), 2.0D)) {
            routeTarget = window.feederCell();
        }
        BlockPos feederCell = window.feederCell();
        Vec3 feederPos = Vec3.atBottomCenterOf(feederCell);
        double distSq = mob.position().distanceToSqr(feederPos);
        double yDiff = Math.abs(mob.getY() - feederPos.y);
        if ((distSq <= 4.0D || mob.blockPosition().closerThan(feederCell, 2.0D)) && yDiff <= 1.25D) {
            transit.state = CrosserTransitState.CAPTURE_ENTRY;
            transit.stateTick = now;
            mob.getNavigation().stop();
            ensureBridgeWorkGoal(mob);
            return true;
        }
        mob.setNoGravity(false);
        mob.noPhysics = false;
        mob.fallDistance = 0.0F;
        com.example.soundattract.pathfinding.PursuitNavigation.maybeMoveTo(
                mob, Vec3.atCenterOf(routeTarget), 1.2D);
        return true;
    }

    private enum CrosserPullResult {
        PROGRESS,
        SETTLED,
        FAILED
    }

    private static CrosserPullResult tryControlledCrosserPull(ServerLevel level, Formation formation, Mob mob,
                                                              CrosserTransit transit) {
        if (level == null || formation == null || mob == null || transit == null || transit.intakeWindow == null) {
            return CrosserPullResult.FAILED;
        }
        CrosserWaypoint waypoint = walkwayWaypoint(level, formation, mob, 0);
        Vec3 target = waypoint.valid() && waypoint.position() != null ? waypoint.position() : null;
        if (target == null) {
            Vec3 station = slotPosition(formation, 0);
            target = new Vec3(station.x, station.y + 1.85D, station.z);
        }
        if (formation.plan != null && requiresTilt(formation.plan.scenario())) {
            Vec3 baseStation = slotPosition(formation, 0);
            double horizDistSq = (mob.getX() - baseStation.x) * (mob.getX() - baseStation.x)
                    + (mob.getZ() - baseStation.z) * (mob.getZ() - baseStation.z);
            double yDiff = mob.getY() - baseStation.y;
            if (horizDistSq > 5.0D || yDiff < -1.5D || yDiff > 3.2D) {
                return CrosserPullResult.FAILED;
            }
        }
        if (!isControlledCrosserWindow(level, mob, formation, transit)) {
            return CrosserPullResult.FAILED;
        }
        captureIntakeHold(mob, formation);
        BlockPos feeder = transit.intakeWindow.feederCell();
        if (feeder != null && formation.plan != null && !requiresTilt(formation.plan.scenario())
                && Math.abs(mob.getY() - feeder.getY()) > 1.5D) {
            return CrosserPullResult.FAILED;
        }
        Vec3 delta = target.subtract(mob.position());
        double horizontalDistSq = (delta.x * delta.x) + (delta.z * delta.z);
        double distance = delta.length();
        if (distance <= CONTROLLED_INTAKE_SETTLE_DISTANCE || (horizontalDistSq <= 0.25D && Math.abs(delta.y) <= 0.6D)) {
            mob.setPos(target.x, target.y, target.z);
            mob.setDeltaMovement(Vec3.ZERO);
            mob.fallDistance = 0.0F;
            return CrosserPullResult.SETTLED;
        }
        if (transit.captureTicks++ >= CROSSER_INTAKE_MAX_TICKS) {
            return CrosserPullResult.FAILED;
        }
        Vec3 step = delta.scale(Math.min(CROSSER_INTAKE_PULL_STEP, distance) / distance);
        Vec3 next = mob.position().add(step);
        if (findBlockingSlotBlock(level, mob, next) != null) {
            return CrosserPullResult.FAILED;
        }
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.setNoGravity(true);
        mob.noPhysics = true;
        mob.getNavigation().stop();
        mob.setDeltaMovement(step);
        mob.setPos(next.x, next.y, next.z);
        mob.fallDistance = 0.0F;
        return CrosserPullResult.PROGRESS;
    }

    private static boolean isControlledCrosserWindow(ServerLevel level, Mob mob, Formation formation,
                                                     CrosserTransit transit) {
        if (transit == null || transit.intakeWindow == null) {
            return false;
        }
        BlockPos feeder = transit.intakeWindow.feederCell();
        if (feeder == null || !level.isLoaded(feeder) || mob.isInLava()) {
            return false;
        }
        if (formation != null && formation.plan != null && requiresTilt(formation.plan.scenario())) {
            Vec3 baseStation = slotPosition(formation, 0);
            double horizDistSq = (mob.getX() - baseStation.x) * (mob.getX() - baseStation.x)
                    + (mob.getZ() - baseStation.z) * (mob.getZ() - baseStation.z);
            double yDiff = mob.getY() - baseStation.y;
            return horizDistSq <= 4.5D && yDiff >= -1.5D && yDiff <= 3.2D;
        }
        Vec3 target = slotPosition(formation, 0);
        Vec3 feederPos = Vec3.atBottomCenterOf(feeder);
        double distToTarget = mob.position().distanceTo(target);
        double distToFeeder = mob.position().distanceTo(feederPos);
        double yDiffTarget = Math.abs(mob.getY() - target.y);
        double yDiffFeeder = Math.abs(mob.getY() - feederPos.y);
        if (yDiffFeeder > 1.5D && yDiffTarget > 2.5D) {
            return false;
        }
        return distToFeeder <= 3.0D || distToTarget <= 3.0D;
    }

    private static boolean isValidCrosserSuctionPose(Vec3 position, IntakeWindow window, Formation formation) {
        if (formation == null || formation.plan == null) return false;
        return isCaptureReadySuctionPose(position, window, List.of(), null);
    }

    private static boolean advanceCrosser(ServerLevel level, Formation formation, Mob mob,
                                          CrosserTransit transit, long now) {
        TiltExitProjection exit = resolveTiltThreatExit(level, formation);
        if (transit.state == CrosserTransitState.TRAVERSE && exit != null && transit.slot >= exit.slot()) {
            transit.state = CrosserTransitState.DISMOUNT;
            transit.stateTick = now;
            transit.handoffGoal = exit.handoffGoal();
        }
        if (transit.state == CrosserTransitState.DISMOUNT && exit != null) {
            Vec3 bridgeTarget = exit.bridgeWaypoint();
            if (bridgeTarget == null) {
                CrosserWaypoint waypoint = walkwayWaypoint(level, formation, mob, exit.slot());
                bridgeTarget = waypoint.valid() && waypoint.position() != null ? waypoint.position() : null;
                if (bridgeTarget == null) {
                    Vec3 station = slotPosition(formation, exit.slot());
                    bridgeTarget = new Vec3(station.x, station.y + 1.85D, station.z);
                }
            }
            Vec3 target = (mob.position().distanceToSqr(bridgeTarget) > 0.36D)
                    ? bridgeTarget : exit.dismountWaypoint();
            if (!moveCrosserBounded(level, mob, target, 0.22D)) {
                transit.stuckTicks++;
                if (transit.stuckTicks > 60) return false;
                applyCoordinatorCrosserControl(mob);
                return true;
            }
            transit.stuckTicks = 0;
            if (mob.position().distanceToSqr(exit.dismountWaypoint()) <= 0.16D) {
                startCrosserFall(mob, transit, exit);
            }
            formation.crosserSlots.put(mob.getUUID(), exit.slot());
            return true;
        }
        CrosserWaypoint waypoint = walkwayWaypoint(level, formation, mob, transit.slot);
        Vec3 target = waypoint.valid() && waypoint.position() != null ? waypoint.position() : null;
        if (target == null) {
            Vec3 station = slotPosition(formation, transit.slot);
            target = new Vec3(station.x, station.y + 1.85D, station.z);
        }
        if (!moveCrosserBounded(level, mob, target, 0.22D)) {
            transit.stuckTicks++;
            if (transit.stuckTicks > 60) return false;
            applyCoordinatorCrosserControl(mob);
            return true;
        }
        transit.stuckTicks = 0;
        if (mob.position().distanceToSqr(target) <= 0.64D) {
            if (transit.slot >= formation.members.size() - 1) {
                Vec3 exitPos = target;
                if (formation.plan != null && formation.anchor != null) {
                    BlockPos anchorPos = formation.anchor;
                    Vec3 dir = formation.plan.forward() != null && formation.plan.forward().lengthSqr() > 0.001D
                            ? formation.plan.forward()
                            : Vec3.atCenterOf(anchorPos).subtract(Vec3.atCenterOf(formation.pivot));
                    double dx = Math.signum(dir.x);
                    double dz = Math.signum(dir.z);
                    BlockPos forwardLanding = anchorPos.offset((int) dx, 0, (int) dz);
                    if (isSafeLanding(level, forwardLanding)) {
                        exitPos = Vec3.atBottomCenterOf(forwardLanding);
                    } else if (isSafeLanding(level, anchorPos)) {
                        exitPos = Vec3.atBottomCenterOf(anchorPos);
                    }
                }
                mob.setPos(exitPos.x, exitPos.y, exitPos.z);
                releaseCrosser(level, formation, mob.getUUID());
                removeCrosserTransit(formation, mob.getUUID());
                mob.setNoGravity(false);
                mob.noPhysics = false;
                mob.fallDistance = 0.0F;
                mob.getNavigation().stop();
                if (formation.plan != null) {
                    Vec3 pushDir = formation.plan.forward() != null && formation.plan.forward().lengthSqr() > 0.001D
                            ? formation.plan.forward().normalize()
                            : Vec3.atCenterOf(formation.anchor).subtract(Vec3.atCenterOf(formation.pivot)).normalize();
                    mob.setDeltaMovement(new Vec3(pushDir.x * 0.25D, 0.1D, pushDir.z * 0.25D));
                }
                formation.completedCrossers.add(mob.getUUID());
                formation.crosserCompletionTicks.put(mob.getUUID(), now);
                restoreTarget(level, formation, mob.getUUID(), mob, true);
                return true;
            }
            transit.slot = Math.min(formation.members.size() - 1, transit.slot + 1);
            formation.crosserSlots.put(mob.getUUID(), transit.slot);
        }
        return true;
    }

    private static boolean moveCrosserBounded(ServerLevel level, Mob mob, Vec3 target, double maxStep) {
        if (level == null || mob == null || target == null) return false;
        Vec3 current = mob.position();
        Vec3 delta = target.subtract(current);
        double distance = delta.length();
        if (distance < 1.0E-6D) return true;
        Vec3 next = distance <= maxStep ? target : current.add(delta.scale(maxStep / distance));
        applyCoordinatorCrosserControl(mob);
        if (findBlockingSlotBlock(level, mob, next) != null) return false;
        placeCrosserOnWaypoint(mob, next);
        return true;
    }

    private static void startCrosserFall(Mob mob, CrosserTransit transit, TiltExitProjection exit) {
        if (mob == null || transit == null || exit == null) return;
        mob.setPos(exit.dismountWaypoint().x, exit.dismountWaypoint().y, exit.dismountWaypoint().z);
        mob.setDeltaMovement(Vec3.ZERO);
        mob.setNoGravity(false);
        mob.noPhysics = false;
        mob.getNavigation().stop();
        mob.fallDistance = 0.0F;
        transit.state = CrosserTransitState.FALLING;
        transit.handoffGoal = exit.handoffGoal();
    }

    private static boolean updateFallingCrosser(ServerLevel level, Formation formation, Mob mob, CrosserTransit transit) {
        if (level == null || formation == null || mob == null || transit == null) return false;
        if (!mob.onGround()) {
            mob.getNavigation().stop();
            if (++transit.landingWatchdogTicks > 100) {
                transit.state = CrosserTransitState.ABORTED;
                releaseCrosser(level, formation, mob.getUUID());
                return true;
            }
            return true;
        }
        transit.state = CrosserTransitState.LANDING_VALIDATION;
        transit.stateTick = level.getGameTime();
        return false;
    }

    private static boolean updateLandingValidation(ServerLevel level, Formation formation, Mob mob,
                                                   CrosserTransit transit, long now) {
        if (transit.handoffGoal == null || !level.isLoaded(transit.handoffGoal)
                || !isSafeLanding(level, transit.handoffGoal)) {
            transit.state = CrosserTransitState.ABORTED;
            releaseCrosser(level, formation, mob.getUUID());
            return true;
        }
        releaseCrosser(level, formation, mob.getUUID());
        transit.state = CrosserTransitState.HANDED_OFF;
        mob.getNavigation().moveTo(transit.handoffGoal.getX() + 0.5D,
                transit.handoffGoal.getY(), transit.handoffGoal.getZ() + 0.5D, 1.0D);
        return true;
    }

    private static Vec3 slotPosition(Formation formation, int slot) {
        int bounded = Math.max(0, Math.min(Math.max(0, formation.requiredBodies - 1), slot));
        if (formation != null && formation.plan != null && requiresTilt(formation.plan.scenario())) {
            return formationSlotPosition(formation, bounded, 1.0D);
        }
        if (formation != null && formation.structuralSlotPositions != null
                && formation.structuralSlotPositions.containsKey(bounded)) {
            return formation.structuralSlotPositions.get(bounded);
        }
        return formationSlotPosition(formation, bounded, 1.0D);
    }

    private static CrosserWaypoint walkwayWaypoint(ServerLevel level, Formation formation, Mob crosser, int slot) {
        if (level == null || formation == null || crosser == null || formation.members.isEmpty()) {
            return new CrosserWaypoint(null, -1, 0.0D, false, "missing_context");
        }
        Vec3 station = slotPosition(formation, slot);
        double halfWidth = crosser.getBbWidth() * 0.5D;
        AABB footprint = new AABB(
                station.x - halfWidth,
                -1.0D,
                station.z - halfWidth,
                station.x + halfWidth,
                1.0D,
                station.z + halfWidth);
        Mob support = null;
        int supportSlot = -1;
        double supportTop = Double.NEGATIVE_INFINITY;
        int bounded = Math.max(0, Math.min(Math.max(0, formation.members.size() - 1), slot));
        int layerStart = Math.max(0, bounded - Math.floorMod(bounded, Math.max(1, formation.columnCount)));
        int layerEnd = Math.min(formation.members.size() - 1, layerStart + Math.max(1, formation.columnCount) - 1);
        for (int candidateSlot = layerStart; candidateSlot <= layerEnd; candidateSlot++) {
            Mob candidate = getMob(level, formation.members.get(candidateSlot));
            if (candidate == null || !candidate.isAlive() || candidate.isRemoved()) {
                continue;
            }
            AABB supportBox = candidate.getBoundingBox();
            AABB horizontalBox = new AABB(supportBox.minX, -1.0D, supportBox.minZ, supportBox.maxX, 1.0D, supportBox.maxZ);
            if (!horizontalBox.inflate(CONTACT_EPSILON).intersects(footprint)) {
                continue;
            }
            if (supportBox.maxY > supportTop) {
                support = candidate;
                supportSlot = candidateSlot;
                supportTop = supportBox.maxY;
            }
        }
        if (support == null && bounded < formation.members.size()) {
            Mob candidate = getMob(level, formation.members.get(bounded));
            if (candidate != null && candidate.isAlive() && !candidate.isRemoved()) {
                support = candidate;
                supportSlot = bounded;
                supportTop = candidate.getBoundingBox().maxY;
            }
        }
        if (support == null) {
            supportTop = station.y + 1.85D;
            Vec3 feet = new Vec3(station.x, supportTop, station.z);
            if (findBlockingSlotBlock(level, crosser, feet) == null) {
                return new CrosserWaypoint(feet, bounded, supportTop, true, "fallback_station");
            }
            return new CrosserWaypoint(null, -1, 0.0D, false, "support_missing");
        }
        Vec3 feet = new Vec3(station.x, supportTop + CONTACT_EPSILON, station.z);
        double maxStep = Math.max(0.6D, crosser.maxUpStep());
        ClearanceResolution clearance = resolveClearance(level, crosser, feet, maxStep);
        Vec3 resolvedPos = clearance.position() != null ? clearance.position()
                : (findBlockingSlotBlock(level, crosser, feet) == null ? feet : null);
        if (resolvedPos == null) {
            return new CrosserWaypoint(null, supportSlot, supportTop, false, "blocked");
        }
        return new CrosserWaypoint(resolvedPos, supportSlot, supportTop, true, "ok");
    }

    private static void applyCoordinatorCrosserControl(Mob mob) {
        if (mob == null) return;
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.getNavigation().stop();
        mob.setNoGravity(true);
        mob.noPhysics = true;
        mob.fallDistance = 0.0F;
        mob.setDeltaMovement(Vec3.ZERO);
    }

    private static void placeCrosserOnWaypoint(Mob mob, Vec3 waypoint) {
        if (mob == null || waypoint == null) return;
        mob.setPos(waypoint.x, waypoint.y, waypoint.z);
        mob.setDeltaMovement(Vec3.ZERO);
    }

    private static TiltExitProjection resolveTiltThreatExit(ServerLevel level, Formation formation) {
        if (formation != null && formation.committedExecution != null && formation.cachedTiltExit != null) {
            return formation.cachedTiltExit;
        }
        if (level == null || formation == null || !requiresTilt(formation.plan.scenario())
                || formation.request == null || formation.request.threatDestination() == null
                || formation.members.isEmpty()) {
            formation.cachedTiltExit = null;
            formation.cachedTiltExitFingerprint = null;
            formation.cachedTiltExitValidationTick = formation == null ? Long.MIN_VALUE : formation.lastValidationTick;
            return null;
        }
        if (formation.cachedTiltExit != null
                && formation.cachedTiltExitValidationTick == formation.lastValidationTick
                && isTiltExitFingerprintCurrent(level, formation.cachedTiltExitFingerprint)) {
            return formation.cachedTiltExit;
        }
        TiltExitProjection resolved = computeTiltThreatExit(level, formation);
        formation.cachedTiltExit = resolved;
        formation.cachedTiltExitFingerprint = resolved == null ? null : resolved.fingerprint();
        formation.cachedTiltExitValidationTick = formation.lastValidationTick;
        return resolved;
    }

    private static boolean isTiltExitFingerprintCurrent(ServerLevel level, TiltExitFingerprint fingerprint) {
        if (level == null || fingerprint == null || fingerprint.cells() == null) return false;
        for (ExitCellFingerprint expected : fingerprint.cells()) {
            if (expected == null || expected.pos() == null) return false;
            ExitCellFingerprint actual = fingerprintCell(level, expected.pos(), expected.stableSupport(), expected.clearSpace());
            if (!Objects.equals(expected, actual)) return false;
        }
        return true;
    }

    private static TiltExitProjection computeTiltThreatExit(ServerLevel level, Formation formation) {
        if (formation == null || formation.plan == null) return null;
        if (formation.plan.scenario() == ConstructionScenario.WALL_LEDGE) {
            return null;
        }
        BlockPos threat = formation.request.threatDestination();
        Vec3 pivotCenter = Vec3.atBottomCenterOf(formation.pivot);
        BlockPos structuralEnd = tiltStructuralEnd(formation.plan);
        Vec3 spanCenter = Vec3.atBottomCenterOf(structuralEnd);
        Vec3 spanHorizontal = new Vec3(spanCenter.x - pivotCenter.x, 0.0D, spanCenter.z - pivotCenter.z);
        double spanLength = spanHorizontal.length();
        if (spanLength < 1.0E-4D) return null;
        Vec3 direction = spanHorizontal.scale(1.0D / spanLength);
        Vec3 threatOffset = Vec3.atBottomCenterOf(threat).subtract(pivotCenter);
        double projection = threatOffset.x * direction.x + threatOffset.z * direction.z;
        if (!(projection > TILT_EXIT_MIN_FORWARD_PROGRESS)
                || projection >= spanLength - Math.max(1.0D, formation.horizontalTouchStep)) {
            return null;
        }
        double ratio = Mth.clamp(projection / spanLength, 0.0D, 1.0D);
        double spanY = Mth.lerp(ratio, formation.pivot.getY(), structuralEnd.getY());
        if (threat.getY() >= spanY - TILT_EXIT_MIN_DROP) {
            return null;
        }
        int targetLayer = Mth.clamp(Mth.floor(ratio * Math.max(1, formation.layerCount - 1)), 1,
                Math.max(1, formation.layerCount - 2));
        int exitSlot = Math.min(formation.members.size() - 2,
                targetLayer * Math.max(1, formation.columnCount) + Math.max(0, formation.columnCount - 1));
        if (exitSlot <= 0 || exitSlot >= formation.members.size() - 1) {
            return null;
        }
        CrosserWaypoint waypoint = walkwayWaypoint(level, formation, getMob(level, formation.members.get(exitSlot)), exitSlot);
        if (!waypoint.valid()) {
            return null;
        }
        FallColumnResolution column = resolveTiltExitFallColumn(level, formation, threat, exitSlot, waypoint.position(), direction);
        boolean egressValid = column != null && isValidTiltExitEgress(level, column.landing(), threat);
        if (column == null || !egressValid) {
            return null;
        }
        TiltExitFingerprint fingerprint = new TiltExitFingerprint(
                level.dimension().identifier(),
                formation.phase,
                formation.request.revision(),
                threat.immutable(),
                formation.pivot.immutable(),
                formation.anchor.immutable(),
                formation.spanEnd.immutable(),
                exitSlot,
                formation.assembledSlots,
                formation.members.hashCode(),
                structureHasContact(level, formation, formation.requiredBodies),
                column.fingerprint());
        return new TiltExitProjection(exitSlot, waypoint.position(), column.releaseWaypoint(),
                column.landing(), column.support(), column.handoffGoal(), fingerprint);
    }

    private record FallColumnResolution(Vec3 releaseWaypoint, BlockPos landing, BlockPos support,
                                        BlockPos handoffGoal, List<ExitCellFingerprint> fingerprint) {
    }

    private static FallColumnResolution resolveTiltExitFallColumn(ServerLevel level, Formation formation, BlockPos threat,
                                                                  int exitSlot, Vec3 releaseWaypoint, Vec3 direction) {
        if (level == null || formation == null || threat == null || releaseWaypoint == null || direction == null) return null;
        Vec3 lateral = new Vec3(-direction.z, 0.0D, direction.x);
        List<ExitCellFingerprint> fingerprint = new ArrayList<>();
        double[] offsets = {0.0D, 0.6D, -0.6D, 1.2D, -1.2D};
        FallColumnResolution best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (double offsetScale : offsets) {
            Vec3 candidateRelease = releaseWaypoint.add(lateral.scale(offsetScale));
            BlockPos releaseCell = BlockPos.containing(candidateRelease.x, candidateRelease.y - CONTACT_EPSILON, candidateRelease.z);
            for (int fall = 1; fall <= TILT_EXIT_MAX_FALL_DEPTH; fall++) {
                BlockPos support = releaseCell.below(fall);
                fingerprint.add(fingerprintCell(level, support, false, false));
                if (!level.isLoaded(support) || isBridgeHazard(level, support)) break;
                BlockState supportState = level.getBlockState(support);
                if (!hasStableSupport(level, support, supportState)) continue;
                BlockPos landing = support.above();
                BlockPos head = landing.above();
                BlockPos top = head.above();
                boolean landingClear = isCellWalkClear(level, landing);
                boolean headClear = level.isLoaded(head) && level.getBlockState(head).getCollisionShape(level, head).isEmpty()
                        && !isBridgeHazard(level, head);
                boolean topClear = level.isLoaded(top) && level.getBlockState(top).getCollisionShape(level, top).isEmpty()
                        && !isBridgeHazard(level, top);
                fingerprint.add(fingerprintCell(level, landing, true, landingClear));
                fingerprint.add(fingerprintCell(level, head, false, headClear));
                fingerprint.add(fingerprintCell(level, top, false, topClear));
                if (!landingClear || !headClear || !topClear) continue;
                if (horizontalDistanceSqr(landing, threat) >= horizontalDistanceSqr(formation.pivot, threat)) continue;
                BlockPos handoffGoal = resolveTiltExitHandoffGoal(level, landing, threat);
                if (handoffGoal == null) continue;
                double score = horizontalDistanceSqr(formation.pivot, threat) - horizontalDistanceSqr(landing, threat)
                        - Math.abs(offsetScale) * 4.0D - fall;
                if (score > bestScore) {
                    bestScore = score;
                    best = new FallColumnResolution(
                            new Vec3(candidateRelease.x, candidateRelease.y, candidateRelease.z),
                            landing, support, handoffGoal, List.copyOf(fingerprint));
                }
            }
        }
        return best;
    }

    private static boolean isValidTiltExitApproach(ServerLevel level, Formation formation, Mob mob, Vec3 current,
                                                   Vec3 exitWaypoint) {
        if (level == null || formation == null || mob == null || current == null || exitWaypoint == null) return false;
        if (current.distanceToSqr(exitWaypoint) > 16.0D) return false;
        for (BlockPos sample : samplePathCells(current, exitWaypoint, TILT_EXIT_APPROACH_SAMPLES)) {
            if (!isCellWalkClear(level, sample)) return false;
        }
        return true;
    }

    private static boolean isValidTiltExitEgress(ServerLevel level, BlockPos landing, BlockPos threat) {
        if (level == null || landing == null || threat == null) return false;
        if (landing.distManhattan(threat) > TILT_EXIT_EGRESS_SAMPLES * 2) return false;
        for (BlockPos sample : samplePathCells(Vec3.atBottomCenterOf(landing), Vec3.atBottomCenterOf(threat), TILT_EXIT_EGRESS_SAMPLES)) {
            if (!isWalkableRouteCell(level, sample)) return false;
        }
        return true;
    }

    private static List<BlockPos> samplePathCells(Vec3 from, Vec3 to, int samples) {
        if (from == null || to == null || samples <= 0) return List.of();
        List<BlockPos> cells = new ArrayList<>();
        for (int i = 0; i <= samples; i++) {
            double progress = i / (double) samples;
            Vec3 point = from.lerp(to, progress);
            BlockPos cell = BlockPos.containing(point.x, point.y, point.z);
            if (cells.isEmpty() || !cells.get(cells.size() - 1).equals(cell)) {
                cells.add(cell);
            }
        }
        return cells;
    }

    private static boolean isCellWalkClear(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos) || !level.isLoaded(pos.above())) return false;
        if (isBridgeHazard(level, pos) || isBridgeHazard(level, pos.above())) return false;
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    private static boolean isWalkableRouteCell(ServerLevel level, BlockPos pos) {
        if (!isCellWalkClear(level, pos) || !level.isLoaded(pos.below())) return false;
        return hasStableSupport(level, pos.below(), level.getBlockState(pos.below()));
    }

    private static BlockPos resolveTiltExitHandoffGoal(ServerLevel level, BlockPos landing, BlockPos threat) {
        if (level == null || landing == null || threat == null) return null;
        List<BlockPos> samples = samplePathCells(Vec3.atBottomCenterOf(landing), Vec3.atBottomCenterOf(threat), TILT_EXIT_EGRESS_SAMPLES);
        BlockPos best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (BlockPos sample : samples) {
            if (!isWalkableRouteCell(level, sample)) break;
            int distance = sample.distManhattan(threat);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = sample.immutable();
            }
        }
        return best;
    }

    private static ExitCellFingerprint fingerprintCell(ServerLevel level, BlockPos pos, boolean stableSupport, boolean clearSpace) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return new ExitCellFingerprint(pos == null ? BlockPos.ZERO : pos.immutable(), false, "unloaded",
                    false, false, true, false, stableSupport, clearSpace);
        }
        BlockState state = level.getBlockState(pos);
        boolean portal = state.getBlock() instanceof net.minecraft.world.level.block.NetherPortalBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.EndGatewayBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.EndPortalBlock;
        Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return new ExitCellFingerprint(pos.immutable(), true, key == null ? "unknown" : key.toString(),
                !state.getCollisionShape(level, pos).isEmpty(),
                !state.getFluidState().isEmpty(),
                isBridgeHazard(level, pos),
                portal,
                stableSupport,
                clearSpace);
    }

    private static void releaseCrosserAtTiltExit(ServerLevel level, Formation formation, UUID uuid, Mob mob,
                                                 TiltExitProjection exit) {
        if (level == null || formation == null || uuid == null || mob == null) return;
        BlockPos threat = formation.request == null ? null : formation.request.threatDestination();
        releaseCrosser(level, formation, uuid);
        BlockPos handoff = exit == null ? threat : exit.handoffGoal();
        if (handoff != null && level.isLoaded(handoff)) {
            com.example.soundattract.pathfinding.PursuitNavigation.maybeMoveTo(
                    mob, Vec3.atCenterOf(handoff), 1.0D);
        }
    }

    private static int activeAssemblyReleaseLimit() {
        return configOrDefault(LivingWallConfig.MAX_ACTIVE_ASSEMBLY_RELEASES, 2);
    }

    private static boolean reserveCrosser(ServerLevel level, Mob mob, Formation formation) {
        if (level == null || mob == null || formation == null) {
            return false;
        }
        if (!canonicalCorridorGate(formation, formation.anchor)) return false;
        if (formation.structuralBuilderIds.contains(mob.getUUID())) {
            return false;
        }
        UUID existingOwner = state(level).mobOwners.putIfAbsent(mob.getUUID(), formation.id);
        if (existingOwner != null && !existingOwner.equals(formation.id)) return false;
        formation.reserved.add(mob.getUUID());
        formation.transitCrosserIds.add(mob.getUUID());
        formation.originalGravity.putIfAbsent(mob.getUUID(), !mob.isNoGravity());
        formation.originalPersistence.putIfAbsent(mob.getUUID(), ((MobAccessor) mob).isPersistenceRequiredFlag());
        formation.originalNoPhysics.putIfAbsent(mob.getUUID(), mob.noPhysics);
        formation.originalTargets.putIfAbsent(mob.getUUID(), mob.getTarget() == null ? null : mob.getTarget().getUUID());
        markControlledMob(mob, formation);
        mob.setNoGravity(false);
        mob.noPhysics = false;
        mob.getNavigation().stop();
        mob.setAggressive(mob.getTarget() != null);
        mob.setPersistenceRequired();
        mob.fallDistance = 0.0F;
        ensureBridgeWorkGoal(mob);
        return true;
    }

    private static void releaseCrosser(ServerLevel level, Formation formation, UUID uuid) {
        Mob mob = getMob(level, uuid);
        if (mob != null && mob.isAlive() && !mob.isRemoved()) {
            Boolean gravity = formation.originalGravity.get(uuid);
            mob.setNoGravity(gravity != null && !gravity);
            Boolean persistence = formation.originalPersistence.get(uuid);
            if (persistence != null) {
                ((MobAccessor) mob).setPersistenceRequiredFlag(persistence);
            }
            Boolean noPhysics = formation.originalNoPhysics.get(uuid);
            mob.noPhysics = noPhysics != null && noPhysics;
            mob.getNavigation().stop();
            mob.fallDistance = 0.0F;
            clearControlledMarker(mob, formation.id);
            state(level).mobOwners.remove(uuid, formation.id);
            restoreTarget(level, formation, uuid, mob, true);
        }
        formation.reserved.remove(uuid);
        formation.transitCrosserIds.remove(uuid);
        formation.originalGravity.remove(uuid);
        formation.originalPersistence.remove(uuid);
        formation.originalNoPhysics.remove(uuid);
        formation.originalTargets.remove(uuid);
        formation.assemblyHoldPositions.remove(uuid);
        PerLevelState state = LEVELS.get(level.dimension().identifier());
        if (state != null) state.mobOwners.remove(uuid, formation.id);
        int orderIndex = formation.crosserOrder.indexOf(uuid);
        if (orderIndex >= 0) {
            formation.crosserOrder.remove(orderIndex);
            if (formation.crosserCursor > orderIndex) {
                formation.crosserCursor--;
            }
            if (formation.crosserCursor < 0) {
                formation.crosserCursor = 0;
            }
        }
    }

    private static void releaseCrossedMembers(ServerLevel level, Formation formation) {
        UUID leaderId = formation.plannerId;
        for (UUID uuid : new ArrayList<>(formation.members)) {
            if (uuid.equals(leaderId)) {
                continue;
            }
            Mob mob = getMob(level, uuid);
            if (mob != null && isBeyondAnchor(formation, mob)) {
                releaseMember(level, formation, uuid);
            }
        }
    }

    private static boolean isBeyondAnchor(Formation formation, Mob mob) {
        if (formation == null || mob == null) {
            return false;
        }
        double dx = formation.anchor.getX() - formation.pivot.getX();
        double dz = formation.anchor.getZ() - formation.pivot.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001D) {
            return false;
        }
        double projection = ((mob.getX() - formation.pivot.getX()) * dx
                + (mob.getZ() - formation.pivot.getZ()) * dz) / length;
        return projection >= length + 0.75D && mob.getY() >= formation.anchor.getY() - 1.5D;
    }

    private static void releaseMember(ServerLevel level, Formation formation, UUID uuid) {
        if (formation.motionDiagnosticAttempt != null
                && formation.motionDiagnosticAttempt.member.equals(uuid)) {
            finishMotionDiagnostic(formation, level.getGameTime(), "member_release");
        }
        Mob mob = getMob(level, uuid);
        if (mob != null && mob.isAlive() && !mob.isRemoved()) {
            advanceControlledSafeRecovery(level, mob);
            completeSafeRecoveryAtPersistedEndpoint(level, mob);
            if (!formation.collapsed && mustPreserveDurableRouteOwner(level, mob)) {
                holdDurableRouteOwner(mob);
            } else {
                Boolean gravity = formation.originalGravity.get(uuid);
                mob.setNoGravity(gravity != null && !gravity);
                Boolean persistence = formation.originalPersistence.get(uuid);
                if (persistence != null) {
                    ((MobAccessor) mob).setPersistenceRequiredFlag(persistence);
                } else if (!mob.hasCustomName()) {
                    ((MobAccessor) mob).setPersistenceRequiredFlag(false);
                }
                mob.getNavigation().stop();
                Boolean noPhysics = formation.originalNoPhysics.get(uuid);
                mob.noPhysics = noPhysics != null && noPhysics;
                clearControlledMarker(mob, formation.id);
                com.example.soundattract.component.LivingBridgeEntityData.get(mob).remove("livingBridgeSafeBaseRouteActive");
                com.example.soundattract.component.LivingBridgeEntityData.get(mob).remove("livingBridgeControl");
                com.example.soundattract.component.LivingBridgeEntityData.get(mob).remove("livingBridgeRecoveryRequiredLogged");
                state(level).mobOwners.remove(uuid, formation.id);
                restoreTarget(level, formation, uuid, mob, true);
            }
        }
        formation.members.remove(uuid);
        if (formation.activeStructuralWorkerId != null && formation.activeStructuralWorkerId.equals(uuid)) {
            formation.activeStructuralWorkerId = null;
            formation.activeStructuralRoute = null;
            formation.activeStructuralRouteRetries = 0;
            formation.activeMemberSuctionEngaged = false;
            formation.activeStructuralBestDistanceSqr = Double.MAX_VALUE;
        }
        formation.safeBaseNavigation.remove(uuid);
        formation.pendingSafeBaseReplans.remove(uuid);
        formation.rejectedSafeBaseStaging.remove(uuid);
        formation.lastReportedSafeBaseStagingDiagnostics.remove(uuid);
        releaseStructuralApproachLane(formation, uuid);
        formation.structuralBuilderIds.remove(uuid);
        formation.builderReservations.remove(uuid);
        formation.pendingBuilderIds.remove(uuid);
        formation.ticketGoals.remove(uuid);
        ensureBridgeWorkGoal(mob);
        formation.committedFoundationIds.remove(uuid);
        releaseAssemblySlot(formation, uuid);
        formation.mobTickets.remove(uuid);
        formation.assemblyIntakeProvenance.remove(uuid);
        formation.transferredWorkerIds.remove(uuid);
        formation.recruitmentArrivalReports.remove(uuid);
        formation.reserved.remove(uuid);
        formation.originalGravity.remove(uuid);
        formation.originalPersistence.remove(uuid);
        formation.originalNoPhysics.remove(uuid);
        formation.originalTargets.remove(uuid);
        formation.assemblyHoldPositions.remove(uuid);
        state(level).mobOwners.remove(uuid, formation.id);
    }

    private static void restoreTarget(ServerLevel level, Formation formation, UUID uuid, Mob mob, boolean combatHandoff) {
        LivingEntity target = restoredOriginalTarget(level, formation, uuid, mob);
        if (!combatHandoff) {
            if (target == null) {
                target = reacquirePostBridgeTarget(level, formation, mob);
            }
            mob.setTarget(target);
            mob.setAggressive(target != null);
            logCombatHandoff(formation, mob, uuid, target, target == null ? "passive_no_target" : "passive_original");
            return;
        }
        String reason = target == null ? "none" : "original";
        if (target == null) {
            target = reacquirePostBridgeTarget(level, formation, mob);
            if (target != null) reason = "nearest_attackable_player";
        }
        if (target == null) {
            mob.setTarget(null);
            mob.setAggressive(false);
            logCombatHandoff(formation, mob, uuid, null, reason);
            return;
        }
        mob.setTarget(target);
        LivingEntity actualTarget = mob.getTarget();
        mob.setAggressive(actualTarget != null);
        if (actualTarget != null) {
            mob.getNavigation().moveTo(actualTarget, 1.0D);
        } else {
            mob.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 1.0D);
        }
        logCombatHandoff(formation, mob, uuid, target, reason);
    }

    private static LivingEntity restoredOriginalTarget(ServerLevel level, Formation formation, UUID uuid, Mob mob) {
        UUID targetId = formation.originalTargets.get(uuid);
        if (targetId == null) {
            return null;
        }
        net.minecraft.world.entity.Entity target = level.getEntity(targetId);
        if (target instanceof LivingEntity living && living.isAlive() && mob.canAttack(living)) {
            return living;
        }
        return null;
    }

    private static LivingEntity reacquirePostBridgeTarget(ServerLevel level, Formation formation, Mob mob) {
        double followRange = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        double radius = Math.max(16.0D, Math.min(64.0D, followRange + 8.0D));
        var player = level.getNearestPlayer(mob.getX(), mob.getY(), mob.getZ(), radius,
                candidate -> candidate instanceof net.minecraft.world.entity.player.Player candidatePlayer
                        && candidatePlayer.isAlive()
                        && !candidatePlayer.isSpectator()
                        && !candidatePlayer.isCreative()
                        && mob.canAttack(candidatePlayer)
                        && (mob.getSensing().hasLineOfSight(candidatePlayer) || isNearBridgeExit(formation, candidatePlayer.position())));
        if (player != null) return player;
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                mob.getBoundingBox().inflate(radius),
                candidate -> candidate.isAlive()
                        && !candidate.isRemoved()
                        && candidate != mob
                        && mob.canAttack(candidate)
                        && (mob.getSensing().hasLineOfSight(candidate) || isNearBridgeExit(formation, candidate.position())));
        return nearby.isEmpty() ? null : nearby.get(0);
    }

    private static boolean isNearBridgeExit(Formation formation, Vec3 position) {
        if (formation == null || position == null) return false;
        Vec3 anchor = Vec3.atCenterOf(formation.anchor);
        Vec3 spanEnd = Vec3.atCenterOf(formation.spanEnd);
        return position.distanceToSqr(anchor) <= 12.0D * 12.0D || position.distanceToSqr(spanEnd) <= 12.0D * 12.0D;
    }

    private static void logCombatHandoff(Formation formation, Mob mob, UUID uuid, LivingEntity target, String reason) {
        if (SoundAttractConfig.COMMON == null || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_FORMATION)) return;
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] combat_handoff bridge={} mob={} reason={} target={} targetDistanceSqr={} aggressive={} controlled={}",
                formation.id,
                uuid,
                reason,
                target == null ? null : target.getUUID(),
                target == null ? null : mob.distanceToSqr(target),
                mob.isAggressive(),
                isControlled(mob));
        LivingEntity actual = mob.getTarget();
        if (target != actual) {
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] combat_handoff_actual bridge={} mob={} requestedTarget={} actualTarget={} mobPos={} navigationDone={} controlled={}",
                    formation.id, uuid, target == null ? null : target.getUUID(),
                    actual == null ? null : actual.getUUID(), mob.position(), mob.getNavigation().isDone(),
                    isControlled(mob));
        }
    }

    private static boolean isAnchorStillValid(ServerLevel level, Formation formation) {
        if (formation.anchor == null) {
            return false;
        }
        BlockPos anchor = formation.anchor;
        BlockState feet = level.getBlockState(anchor);
        BlockState head = level.getBlockState(anchor.above());
        BlockState support = level.getBlockState(anchor.below());
        if (feet.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
                || support.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
                || feet.is(BlockTags.TRAPDOORS)) {
            return false;
        }
        return feet.getCollisionShape(level, anchor).isEmpty()
                && head.getCollisionShape(level, anchor.above()).isEmpty()
                && !support.getCollisionShape(level, anchor.below()).isEmpty();
    }

    private static double calculateIntegrity(ServerLevel level, Formation formation) {
        if (!isAnchorStillValid(level, formation) || formation.members.size() < formation.columnCount) {
            return 0.0D;
        }
        double progress = 1.0D;
        int valid = 0;
        int slot = 0;
        for (UUID id : formation.members) {
            Mob mob = getMob(level, id);
            if (mob == null || !mob.isAlive() || mob.isRemoved()) {
                slot++;
                continue;
            }
            Vec3 expected = formationSlotPosition(formation, slot, progress);
            if (mob.position().distanceToSqr(expected) <= 1.25D) {
                valid++;
            }
            slot++;
        }
        return valid / (double) Math.max(1, formation.members.size());
    }

    private static Mob getMob(ServerLevel level, UUID id) {
        net.minecraft.world.entity.Entity entity = level.getEntity(id);
        return entity instanceof Mob mob ? mob : null;
    }

    private static boolean isEligible(Mob mob) {
        if (mob == null) {
            return false;
        }
        if (mob.isBaby()) {
            return false;
        }
        if (SoundAttractConfig.COMMON == null) {
            return true;
        }
        List<? extends String> ids = LivingWallConfig.ELIGIBLE_MOB_ENTITY_IDS.get();
        Identifier key = net.minecraft.world.entity.EntityType.getKey(mob.getType());
        if (key == null) {
            return false;
        }
        for (String raw : ids) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String trimmed = raw.trim();
            if (trimmed.endsWith(":*")) {
                if (key.getNamespace().equals(trimmed.substring(0, trimmed.length() - 2))) {
                    return true;
                }
            } else if (trimmed.equals(key.toString())) {
                return true;
            }
        }
        return false;
    }

    public static boolean cancelStructuralKnockback(LivingEntity entity) {
        if (!(entity instanceof Mob mob) || !isStructuralMember(mob)) return false;
        mob.setDeltaMovement(Vec3.ZERO);
        return true;
    }

    public static void onTerrainMutation(ServerLevel level, BlockPos pos) {
        if (level != null && pos != null) markTerrainMutation(level, around(pos));
    }

    private static boolean isStructuralMember(Mob mob) {
        Formation formation = getFormation(mob);
        if (formation == null) return false;
        if (formation.structuralBuilderIds.contains(mob.getUUID())
                || formation.pendingBuilderIds.contains(mob.getUUID())) return true;
        if (formation.candidateHoldingGoals.containsKey(mob.getUUID())) return true;
        MobTicket assignment = formation.mobTickets.get(mob.getUUID());
        return assignment != null && (assignment.state == MobTicketState.STAGING_AT_ASSEMBLY
                || assignment.state == MobTicketState.ASSEMBLED
                || assignment.state == MobTicketState.SUCTIONING || assignment.state == MobTicketState.BUILDING);
    }

    private static Formation getFormation(Mob mob) {
        if (mob == null || !(mob.level() instanceof ServerLevel level)) {
            return null;
        }
        PerLevelState state = LEVELS.get(level.dimension().identifier());
        if (state == null) {
            return null;
        }
        UUID id = state.mobOwners.get(mob.getUUID());
        return id == null ? null : state.formations.get(id);
    }

    private static boolean claimFootprint(PerLevelState state, Formation formation) {
        Set<Long> keys = permanentFootprintKeys(formation.plan);
        for (long key : keys) if (state.footprintOwners.containsKey(key)) return false;
        for (long key : keys) state.footprintOwners.put(key, formation.id);
        formation.footprint.addAll(keys);
        return true;
    }

    private static UUID footprintConflict(PerLevelState state, Formation formation) {
        if (state == null || formation == null) return null;
        Set<Long> keys = permanentFootprintKeys(formation.plan);
        for (long key : keys) {
            UUID owner = state.footprintOwners.get(key);
            if (owner != null && !owner.equals(formation.id)) return owner;
        }
        return null;
    }

    private static void collapse(ServerLevel level, Formation formation, String reason) {
        if (formation == null || formation.collapsed) {
            return;
        }
        formation.collapseReason = reason;
        logFormationState(level, formation, level.getGameTime(), "collapse:" + reason);
        if (shouldPoisonExactSiteCooldown(reason)) {
            invalidateWallProfiles(level, formation.pivot);
            long retryTicks = shouldPoisonExactSiteCooldown(reason) && formation.assembledSlots > 0
                    ? WALL_PROFILE_RESCAN_DELAY_TICKS : TERRAIN_REJECTION_TICKS;
            putExactSiteCooldown(state(level).terrainRejections, exactSiteKey(formation.pivot, formation.anchor,
                    formation.spanEnd, formation.plan.scenario(), formation.plan.hazardCells()),
                    level.getGameTime() + retryTicks, level.getGameTime());
        }
        formation.collapsed = true;
        releaseFormation(level, formation);
        clearConstructionIntent(state(level), formation.intentToken);
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_RECOVERY)) {
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] Collapsed formation at {} because {}", formation.target, reason);
        }
    }

    private static void releaseFormation(ServerLevel level, Formation formation) {
        if (formation == null) {
            return;
        }
        finishMotionDiagnostic(formation, level == null ? -1L : level.getGameTime(), "formation_release");
        Set<UUID> cleanupIds = new HashSet<>(formation.reserved);
        cleanupIds.addAll(formation.members);
        cleanupIds.addAll(formation.pendingBuilderIds);
        cleanupIds.addAll(formation.ticketGoals.keySet());
        cleanupIds.addAll(formation.candidateHoldingGoals.keySet());
        cleanupIds.addAll(formation.mobTickets.keySet());
        cleanupIds.addAll(formation.claimedWorkerIds);
        cleanupIds.addAll(formation.assemblyIntakeProvenance.keySet());
        cleanupIds.addAll(formation.assemblySlotAssignments.keySet());
        cleanupIds.addAll(formation.transferredWorkerIds);
        cleanupIds.addAll(formation.crosserTransit.keySet());
        for (UUID uuid : cleanupIds) {
            Mob mob = getMob(level, uuid);
            if (mob != null && mob.isAlive() && !mob.isRemoved()) {
                advanceControlledSafeRecovery(level, mob);
                completeSafeRecoveryAtPersistedEndpoint(level, mob);
                if (!formation.collapsed && mustPreserveDurableRouteOwner(level, mob)) {
                    holdDurableRouteOwner(mob);
                } else {
                    Boolean original = formation.originalGravity.get(uuid);
                    mob.setNoGravity(original != null && !original);
                    mob.getNavigation().stop();
                    Boolean persistence = formation.originalPersistence.get(uuid);
                    if (persistence != null) {
                        ((MobAccessor) mob).setPersistenceRequiredFlag(persistence);
                    } else if (!mob.hasCustomName()) {
                        ((MobAccessor) mob).setPersistenceRequiredFlag(false);
                    }
                    mob.noPhysics = false;
                    clearControlledMarker(mob, formation.id);
                    com.example.soundattract.component.LivingBridgeEntityData.get(mob).remove("livingBridgeSafeBaseRouteActive");
                    com.example.soundattract.component.LivingBridgeEntityData.get(mob).remove("livingBridgeControl");
                    com.example.soundattract.component.LivingBridgeEntityData.get(mob).remove("livingBridgeRecoveryRequiredLogged");
                    PerLevelState state = LEVELS.get(level.dimension().identifier());
                    if (state != null) state.mobOwners.remove(uuid, formation.id);
                    restoreTarget(level, formation, uuid, mob, false);
                }
            }
            releaseAssemblySlot(formation, uuid);
            formation.ticketGoals.remove(uuid);
            formation.candidateHoldingGoals.remove(uuid);
            ensureBridgeWorkGoal(mob);
        }
        formation.reserved.clear();
        formation.members.clear();
        formation.localRoster.clear();
        formation.structuralBuilderIds.clear();
        formation.builderReservations.clear();
        formation.transitCrosserIds.clear();
        formation.pendingBuilderIds.clear();
        formation.ticketGoals.clear();
        formation.candidateHoldingGoals.clear();
        formation.candidateHoldingCells.clear();
        formation.successfulPlacementProvenance.clear();
        if (formation.assignmentFuture != null) formation.assignmentFuture.cancel(false);
        formation.assignmentFuture = null;
        formation.rankedAssemblySlots.clear();
        formation.slotApproachCache.clear();
        PerLevelState state = LEVELS.get(level.dimension().identifier());
        if (state != null) {
            for (UUID id : cleanupIds) releaseBridgeOwnership(level, formation, id);
            for (long key : formation.footprint) state.footprintOwners.remove(key, formation.id);
            clearConstructionIntent(state, formation.intentToken);
            Set<UUID> owned = state.leaderToFormations.get(formation.plannerId);
            if (owned != null) {
                owned.remove(formation.id);
                if (owned.isEmpty()) state.leaderToFormations.remove(formation.plannerId);
            }
            state.serviceRing.remove(formation.id);
        }
        formation.mobTickets.clear();
        formation.assemblySlotAssignments.clear();
        formation.assemblySlotOwners.clear();
        formation.assemblyLaneAssignments.clear();
        formation.activeIntakeWorkerId = null;
        formation.footprint.clear();
        formation.assemblyIntakeProvenance.clear();
        formation.transferredWorkerIds.clear();
        formation.recruitmentArrivalReports.clear();
        formation.pathSurveys.clear();
        formation.recruitmentRoutes.clear();
        formation.crosserSlots.clear();
        formation.crosserTransit.clear();
        formation.crosserOrder.clear();
        formation.claimedWorkerIds.clear();
        formation.originalGravity.clear();
        formation.originalPersistence.clear();
        formation.originalNoPhysics.clear();
        formation.originalTargets.clear();
        formation.assemblyHoldPositions.clear();
        formation.safeBaseNavigation.clear();
        formation.structuralApproachReservations.clear();
        formation.pendingSafeBaseReplans.clear();
        formation.rejectedSafeBaseStaging.clear();
        formation.lastReportedSafeBaseStagingDiagnostics.clear();
        formation.activeStructuralWorkerId = null;
        formation.activeStructuralRoute = null;
        formation.activeStructuralRouteRetries = 0;
        formation.activeMemberSuctionEngaged = false;
    }

    static Vec3 safeReleasePosition(ServerLevel level, Formation formation, int index) {
        if (formation.plan != null && !formation.plan.assemblySlots().isEmpty()) {
            BlockPos assembly = formation.plan.assemblySlots().get(index % formation.plan.assemblySlots().size());
            if (isSafeSite(level, assembly) && !isNearHazard(level, assembly,
                    formation.plan.hazardCells(), formation.plan.forward())) return Vec3.atBottomCenterOf(assembly);
        }
        int[] offsets = {-2, 0, 2, -4, 4};
        int row = Math.floorDiv(index, offsets.length);
        for (int attempt = 0; attempt < offsets.length * offsets.length; attempt++) {
            int x = formation.pivot.getX() + offsets[attempt % offsets.length];
            int z = formation.pivot.getZ() + offsets[(attempt / offsets.length + row) % offsets.length];
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos candidate = new BlockPos(x, y, z);
            if (isSafeSite(level, candidate)) return Vec3.atBottomCenterOf(candidate);
        }
        return Vec3.atBottomCenterOf(formation.pivot);
    }

    static int pressureTier(double averageTickMs) {
        if (averageTickMs > 60.0D) return 3;
        if (averageTickMs > 45.0D) return 2;
        if (averageTickMs >= 35.0D) return 1;
        return 0;
    }

    static int admissionScanInterval(int tier) {
        return switch (Math.max(0, Math.min(3, tier))) {
            case 1 -> 40;
            case 2 -> 80;
            case 3 -> 120;
            default -> NON_RAID_SCAN_INTERVAL_TICKS;
        };
    }

    private static void cleanup(PerLevelState state, ServerLevel level) {
        synchronized (state.formations) {
            Iterator<Map.Entry<UUID, Formation>> iterator = state.formations.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Formation> entry = iterator.next();
                Formation formation = entry.getValue();
                if (formation.collapsed) {
                    logFormationState(level, formation, level.getGameTime(), "release:cleanup_collapsed");
                    releaseFormation(level, formation);
                    iterator.remove();
                }
            }
            cleanupOrphanedFreezes(state, level);
        }
    }

    private static void cleanupOrphanedFreezes(PerLevelState state, ServerLevel level) {
        long now = level.getGameTime();
        if (!tickElapsed(now, state.lastOrphanFreezeCleanupTick, ORPHAN_FREEZE_CLEANUP_INTERVAL_TICKS)) return;
        state.lastOrphanFreezeCleanupTick = now;
        Set<UUID> candidates = new HashSet<>(state.mobOwners.keySet());
        for (Formation formation : state.formations.values()) {
            candidates.addAll(formation.originalGravity.keySet());
            candidates.addAll(formation.originalNoPhysics.keySet());
            candidates.addAll(formation.mobTickets.keySet());
            candidates.addAll(formation.members);
            candidates.addAll(formation.transitCrosserIds);
        }
        if (candidates.isEmpty()) return;
        List<UUID> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparing(UUID::toString));
        int start = Math.floorMod(state.orphanFreezeCursor, ordered.size());
        int processed = Math.min(ORPHAN_FREEZE_CLEANUP_BUDGET, ordered.size());
        for (int offset = 0; offset < processed; offset++) {
            UUID id = ordered.get((start + offset) % ordered.size());
            UUID owner = state.mobOwners.get(id);
            Formation formation = owner == null ? findFormationForTrackedMob(state, id) : state.formations.get(owner);
            Mob mob = getMob(level, id);
            if (formation == null) {
                if (mob != null && mob.isAlive() && !mob.isRemoved()) {
                    mob.setNoGravity(false);
                    mob.noPhysics = false;
                    mob.setTarget(null);
                    mob.setAggressive(false);
                    mob.getNavigation().stop();
                    mob.setDeltaMovement(Vec3.ZERO);
                    mob.fallDistance = 0.0F;
                    logFreezeCleanup(level, id, "owner_missing", mob);
                }
                state.mobOwners.remove(id);
                continue;
            }
            MobTicket assignment = formation.mobTickets.get(id);
            boolean activeFreeze = formation.reserved.contains(id) || formation.members.contains(id)
                    || formation.pendingBuilderIds.contains(id) || formation.transitCrosserIds.contains(id)
                    || isActiveFreezeTicketState(assignment == null ? null : assignment.state);
            if (activeFreeze) continue;
            if (mob != null && (mob.noPhysics || mob.isNoGravity())) {
                releaseRalliedHold(level, formation, id, mob);
                logFreezeCleanup(level, id, "inactive_role", mob);
            }
            if (assignment == null || assignment.state == MobTicketState.RELEASED) {
                releaseBridgeOwnership(level, formation, id);
            }
        }
        state.orphanFreezeCursor = (start + processed) % ordered.size();
    }

    private static void recoverLoadedMarkers(ServerLevel level, long now) {
        if (level == null || now % 40L != 0L) return;
        Set<UUID> visited = new HashSet<>();
        int budget = 16;
        for (var player : level.players()) {
            if (budget <= 0) break;
            AABB area = player.getBoundingBox().inflate(RECRUITMENT_RADIUS);
            for (Mob mob : level.getEntitiesOfClass(Mob.class, area,
                    candidate -> com.example.soundattract.component.LivingBridgeEntityData.get(candidate).contains(CONTROL_TAG))) {
                if (!visited.add(mob.getUUID())) continue;
                recoverStaleControlledMob(level, mob, "periodic_scan");
                if (--budget <= 0) break;
            }
        }
    }

    private static Formation findFormationForTrackedMob(PerLevelState state, UUID id) {
        for (Formation formation : state.formations.values()) {
            if (formation.mobTickets.containsKey(id) || formation.originalNoPhysics.containsKey(id)
                    || formation.members.contains(id) || formation.transitCrosserIds.contains(id)) return formation;
        }
        return null;
    }

    private static void logFreezeCleanup(ServerLevel level, UUID id, String reason, Mob mob) {
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_RECOVERY)) {
            SoundAttractMod.LOGGER.info("[LivingWallCoordinator] freeze_cleanup reason={} mob={} pos={} noGravity={} noPhysics={}",
                    reason, id, mob.position(), mob.isNoGravity(), mob.noPhysics);
        }
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("soundattract")
                .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("recover_living_bridge")
                        .executes(context -> recoverLegacyFrozenMobs(context.getSource(), 16))
                        .then(net.minecraft.commands.Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                .executes(context -> recoverLegacyFrozenMobs(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "radius"))))));
    }

    private static int recoverLegacyFrozenMobs(CommandSourceStack source, int radius) {
        ServerLevel level = source.getLevel();
        Vec3 center = source.getPosition();
        AABB area = new AABB(center, center).inflate(radius);
        int restored = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area,
                candidate -> candidate.isAlive() && !candidate.isRemoved()
                        && candidate.isNoGravity() && candidate.noPhysics
                        && !com.example.soundattract.component.LivingBridgeEntityData.get(candidate).contains(CONTROL_TAG))) {
            mob.setNoGravity(false);
            mob.noPhysics = false;
            mob.setTarget(null);
            mob.setAggressive(false);
            mob.getNavigation().stop();
            mob.setDeltaMovement(Vec3.ZERO);
            mob.fallDistance = 0.0F;
            ((MobAccessor) mob).setPersistenceRequiredFlag(false);
            restored++;
        }
        int result = restored;
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "Recovered " + result + " legacy living-bridge mobs."), true);
        return restored;
    }

    public static void onEntityJoinLevel(ServerLevel serverLevel, Entity entity) {
        if (entity instanceof Mob mob) {
            recoverStaleControlledMob(serverLevel, mob, "entity_join");
            ensureBridgeWorkGoal(mob);
        }
    }

    public static void onLivingDeath(LivingEntity entity) {
        if (!(entity instanceof Mob mob)
                || !(mob.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread()) return;
        clearLocalSafeBaseOwnership(level, mob);
        PerLevelState state = LEVELS.get(level.dimension().identifier());
        if (state == null) return;
        List<Formation> formations;
        synchronized (state.formations) {
            formations = new ArrayList<>(state.formations.values());
        }
        for (Formation formation : formations) {
            if (formation.members.contains(mob.getUUID())) {
                collapse(level, formation, "structural_member_death");
            }
        }
    }

    public static void onEntityLeaveLevel(ServerLevel level, Entity entity) {
        if (!(entity instanceof Mob mob)
                || mob.getRemovalReason() != net.minecraft.world.entity.Entity.RemovalReason.DISCARDED
                || level.getServer() == null
                || !level.getServer().isSameThread()) return;
        clearLocalSafeBaseOwnership(level, mob);
        PerLevelState state = LEVELS.get(level.dimension().identifier());
        if (state == null) return;
        List<Formation> formations;
        synchronized (state.formations) {
            formations = new ArrayList<>(state.formations.values());
        }
        for (Formation formation : formations) {
            if (formation.members.contains(mob.getUUID())) {
                collapse(level, formation, "structural_member_discarded");
            }
        }
    }

    private static void clearLocalSafeBaseOwnership(ServerLevel level, Mob mob) {
        PerLevelState state = LEVELS.get(level.dimension().identifier());
        if (state == null) return;
        List<Formation> formations;
        synchronized (state.formations) {
            formations = new ArrayList<>(state.formations.values());
        }
        for (Formation formation : formations) {
            releaseStructuralApproachLane(formation, mob.getUUID());
            formation.safeBaseNavigation.remove(mob.getUUID());
            formation.pendingSafeBaseReplans.remove(mob.getUUID());
            formation.rejectedSafeBaseStaging.remove(mob.getUUID());
            formation.lastReportedSafeBaseStagingDiagnostics.remove(mob.getUUID());
        }
        com.example.soundattract.component.LivingBridgeEntityData.get(mob).remove("livingBridgeSafeBaseRouteActive");
    }

    public static void onChunkUnload(ServerLevel level, ChunkPos chunk) {
        PerLevelState state = LEVELS.get(level.dimension().identifier());
        if (state == null) return;
        List<Formation> affected = new ArrayList<>();
        synchronized (state.formations) {
            Iterator<Map.Entry<UUID, Formation>> iterator = state.formations.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Formation> entry = iterator.next();
                Formation formation = entry.getValue();
                if (formationTouchesChunk(level, formation, chunk)) {
                    iterator.remove();
                    affected.add(formation);
                }
            }
        }
        for (Formation formation : affected) {
            formation.collapsed = true;
            logLifecycleCleanup(level, "chunk_unload", formation, chunk);
            releaseFormation(level, formation);
            clearConstructionIntent(state, formation.intentToken);
        }
        List<UUID> orphanIntents = new ArrayList<>();
        for (ConstructionIntent intent : state.constructionIntents.values()) {
            if (intent == null || intent.request == null) continue;
            ConstructionSiteKey candidate = intent.siteKey != null ? intent.siteKey : intent.candidateKey;
            if (candidate == null) continue;
            if ((candidate.pivot() != null && new ChunkPos(candidate.pivot()).equals(chunk))
                    || (candidate.anchor() != null && new ChunkPos(candidate.anchor()).equals(chunk))
                    || (candidate.spanEnd() != null && new ChunkPos(candidate.spanEnd()).equals(chunk))) {
                orphanIntents.add(intent.plannerId);
            }
        }
        for (UUID id : orphanIntents) {
            clearConstructionIntent(state, state.constructionIntents.get(id));
        }
    }

    private static boolean formationTouchesChunk(ServerLevel level, Formation formation, ChunkPos chunk) {
        if (formation == null || chunk == null) return false;
        if (new ChunkPos(formation.pivot).equals(chunk)
                || new ChunkPos(formation.anchor).equals(chunk)
                || new ChunkPos(formation.spanEnd).equals(chunk)) return true;
        Set<UUID> ids = new HashSet<>(formation.reserved);
        ids.addAll(formation.members);
        ids.addAll(formation.pendingBuilderIds);
        ids.addAll(formation.mobTickets.keySet());
        ids.addAll(formation.transitCrosserIds);
        for (UUID id : ids) {
            Mob mob = getMob(level, id);
            if (mob != null && chunk.equals(mob.chunkPosition())) return true;
        }
        return false;
    }

    public static void onLevelUnload(ServerLevel serverLevel) {
        clearLevel(serverLevel, "level_unload");
    }

    public static void onServerStopping(MinecraftServer server) {
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                clearLevel(level, "server_stopping");
            }
        }
    }

    public static void onServerStopped(MinecraftServer server) {
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                clearLevel(level, "server_stopped");
            }
        }
        LEVELS.clear();
    }

    private static void clearLevel(ServerLevel level) {
        clearLevel(level, "manual_clear");
    }

    private static void clearLevel(ServerLevel level, String reason) {
        if (level == null) {
            return;
        }
        PerLevelState state = LEVELS.get(level.dimension().identifier());
        if (state == null) {
            return;
        }
        List<Formation> formations;
        synchronized (state.formations) {
            formations = new ArrayList<>(state.formations.values());
            state.formations.clear();
        }
        for (Formation formation : formations) {
            formation.collapsed = true;
            logLifecycleCleanup(level, reason, formation, null);
            releaseFormation(level, formation);
        }
        state.leaderToFormations.clear();
        state.serviceRing.clear();
        state.targetScans.clear();
        state.wallProfiles.clear();
        state.terrainRejections.clear();
        state.contentionCooldowns.clear();
        state.mobOwners.clear();
        state.footprintOwners.clear();
        state.constructionIntents.clear();
        state.admissionCompletions.clear();
        state.admissionInFlight = false;
        state.admissionGeneration++;
        state.admissionBackoffUntil = Long.MAX_VALUE;
        WallProfileScanner.clearCaches(level.dimension().identifier());
        LEVELS.remove(level.dimension().identifier(), state);
    }

    private static void logLifecycleCleanup(ServerLevel level, String reason, Formation formation, ChunkPos chunk) {
        if (SoundAttractConfig.COMMON == null || !DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.LIVING_WALL_RECOVERY)) return;
        SoundAttractMod.LOGGER.info("[LivingWallCoordinator] lifecycle_cleanup reason={} formation={} chunk={} members={} reserved={} phase={}",
                reason, formation == null ? "none" : formation.id, chunk,
                formation == null ? 0 : formation.members.size(),
                formation == null ? 0 : formation.reserved.size(),
                formation == null ? "none" : formation.phase);
    }
}
