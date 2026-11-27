package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.separate.LivingWallConfig;
import com.example.soundattract.config.DebugLoggingConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("soundattract_bridge_e2e")
@PrefixGameTestTemplate(false)
public final class LivingBridgeEndToEndGameTests {
    private static final int MAX_ADMISSION_ATTEMPTS = 8;
    private static final int MAX_OBSERVATION_TICKS = 5000;
    private static boolean previousFormationDiagnostics;
    private static boolean previousConstructionDiagnostics;

    @GameTest(template = "livingwallcoordinatorgametests.empty", timeoutTicks = 5600)
    public void wallLedgeBridgeBuildsAndEntersTraversal(GameTestHelper helper) {
        LivingWallConfig.ENABLE_LIVING_WALL.set(true);
        previousFormationDiagnostics = setFormationDiagnostics(true);
        previousConstructionDiagnostics = setDebugFeature(DebugLoggingConfig.Feature.LIVING_WALL_CONSTRUCTION, true);
        ServerLevel level = helper.getLevel();
        forceFixtureChunks(helper, level);
        level.setDayTime(18000L);

        for (int x = 1; x <= 22; x++) {
            for (int z = 1; z <= 16; z++) {
                for (int y = 1; y <= 16; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
            }
        }
        for (int z = 1; z <= 14; z++) {
            helper.setBlock(new BlockPos(9, 1, z), Blocks.STONE.defaultBlockState());
        }
        for (int x = 10; x <= 11; x++) {
            for (int z = 1; z <= 14; z++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE.defaultBlockState());
            }
        }

        List<Mob> workers = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            Mob worker = helper.spawn(EntityType.ZOMBIE, 2.5F + (i % 4) * 1.35F, 1.0F,
                    2.5F + (i / 4) * 1.35F);
            worker.setPersistenceRequired();
            worker.setNoAi(true);
            worker.setOnGround(true);
            workers.add(worker);
        }
        BlockPos threat = helper.absolutePos(new BlockPos(10, 3, 11));
        helper.runAfterDelay(60, () -> admitAndObserve(helper, level, workers, threat, 0));
    }

    private static void forceFixtureChunks(GameTestHelper helper, ServerLevel level) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int minChunkX = Math.floorDiv(origin.getX(), 16);
        int maxChunkX = Math.floorDiv(origin.getX() + 22, 16);
        int minChunkZ = Math.floorDiv(origin.getZ(), 16);
        int maxChunkZ = Math.floorDiv(origin.getZ() + 16, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.setChunkForced(chunkX, chunkZ, true);
                level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
            }
        }
    }

    private static void admitAndObserve(GameTestHelper helper, ServerLevel level, List<Mob> workers,
                                        BlockPos threat, int attempt) {
        try {
            Method targetsMethod = LivingWallCoordinator.class.getDeclaredMethod("findClusterTargets",
                    ServerLevel.class, List.class, BlockPos.class);
            targetsMethod.setAccessible(true);
            List<?> targets = (List<?>) targetsMethod.invoke(null, level, workers, threat);

            Method requestMethod = LivingWallCoordinator.class.getDeclaredMethod("findConstructionRequest",
                    ServerLevel.class, List.class, BlockPos.class, BlockPos.class);
            requestMethod.setAccessible(true);
            Method prepareMethod = findPrepareFormationMethod();
            for (Object target : targets) {
                Object request = requestMethod.invoke(null, level, workers, threat, target);
                if (request == null) continue;

                workers.forEach(worker -> worker.setNoAi(false));
                boolean prepared = (boolean) prepareMethod.invoke(null, workers.get(0), target,
                        level.getGameTime(), workers, request);
                if (!prepared) {
                    workers.forEach(worker -> worker.setNoAi(true));
                    continue;
                }

                Object formation = findFormationForPlanner(level, workers.get(0));
                if (formation == null) {
                    restoreDiagnostics();
                    helper.fail("coordinator accepted the bridge request but did not retain its formation");
                    return;
                }
                helper.runAfterDelay(1, () -> observeBridge(helper, level, workers, formation, 0, 0));
                return;
            }

            if (attempt + 1 >= MAX_ADMISSION_ATTEMPTS) {
                restoreDiagnostics();
                helper.fail("no bridge formation admitted after " + MAX_ADMISSION_ATTEMPTS
                        + " attempts; targets=" + targets.size());
                return;
            }
            helper.runAfterDelay(61, () -> admitAndObserve(helper, level, workers, threat, attempt + 1));
        } catch (ReflectiveOperationException exception) {
            restoreDiagnostics();
            helper.fail("bridge admission failed: " + exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean setFormationDiagnostics(boolean enabled) {
        return setDebugFeature(DebugLoggingConfig.Feature.LIVING_WALL_FORMATION, enabled);
    }

    @SuppressWarnings("unchecked")
    private static boolean setDebugFeature(DebugLoggingConfig.Feature feature, boolean enabled) {
        boolean previous = DebugLoggingConfig.isEnabled(feature);
        try {
            Field featuresField = DebugLoggingConfig.class.getDeclaredField("FEATURES");
            featuresField.setAccessible(true);
            Map<DebugLoggingConfig.Feature, ModConfigSpec.BooleanValue> features =
                    (Map<DebugLoggingConfig.Feature, ModConfigSpec.BooleanValue>) featuresField.get(null);
            features.get(feature).set(enabled);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("unable to configure bridge test diagnostics", exception);
        }
        return previous;
    }

    private static void restoreDiagnostics() {
        setDebugFeature(DebugLoggingConfig.Feature.LIVING_WALL_FORMATION, previousFormationDiagnostics);
        setDebugFeature(DebugLoggingConfig.Feature.LIVING_WALL_CONSTRUCTION, previousConstructionDiagnostics);
    }

    private static Method findPrepareFormationMethod() throws NoSuchMethodException {
        for (Method method : LivingWallCoordinator.class.getDeclaredMethods()) {
            if (method.getName().equals("prepareFormation") && method.getParameterCount() == 5) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException("prepareFormation(ServerLevel, Mob, long, List, ConstructionRequest)");
    }

    private static Object findFormationForPlanner(ServerLevel level, Mob planner) throws ReflectiveOperationException {
        Method stateMethod = LivingWallCoordinator.class.getDeclaredMethod("state", ServerLevel.class);
        stateMethod.setAccessible(true);
        Object state = stateMethod.invoke(null, level);
        Object formations = field(state, "formations");
        for (Object formation : ((Map<?, ?>) formations).values()) {
            if (planner.getUUID().equals(field(formation, "plannerId"))) return formation;
        }
        return null;
    }

    private static void observeBridge(GameTestHelper helper, ServerLevel level, List<Mob> workers,
                                      Object formation, int tick, int peakPlacements) {
        try {
            Set<Object> activeWorkers = new java.util.HashSet<>(((Map<?, ?>) field(formation, "ticketGoals")).keySet());
            activeWorkers.addAll((List<?>) field(formation, "members"));
            activeWorkers.addAll((Set<?>) field(formation, "structuralBuilderIds"));
            activeWorkers.addAll(((Map<?, ?>) field(formation, "crosserTransit")).keySet());
            activeWorkers.add(field(formation, "plannerId"));
            boolean movementPhase = Set.of("ROTATE", "ANCHOR", "TRAVERSE").contains(field(formation, "phase").toString());
            Set<?> structuralMembers = Set.copyOf((List<?>) field(formation, "members"));
            for (Mob worker : workers) {
                if (worker.isAlive() && !worker.isRemoved()) {
                    worker.setNoAi(!activeWorkers.contains(worker.getUUID())
                            || movementPhase && structuralMembers.contains(worker.getUUID()));
                }
            }
            int placements = ((Map<?, ?>) field(formation, "successfulPlacementProvenance")).size();
            int updatedPeakPlacements = Math.max(peakPlacements, placements);
            if (tick % 100 == 0 || tick >= MAX_OBSERVATION_TICKS) {
                SoundAttractMod.LOGGER.info("[LivingBridgeEndToEndTest] tick={} phase={} placements={} assembled={}/{} members={} builders={} pending={} reservations={} tickets={} reports={} activeWorker={} activeSlot={} collapsed={} reason={} detail={}",
                        tick, field(formation, "phase"), placements,
                        field(formation, "assembledSlots"), field(formation, "requiredBodies"),
                        ((List<?>) field(formation, "members")).size(),
                        ((Set<?>) field(formation, "structuralBuilderIds")).size(),
                        ((List<?>) field(formation, "pendingBuilderIds")).size(),
                        ((Set<?>) field(formation, "builderReservations")).size(),
                        ((Map<?, ?>) field(formation, "ticketGoals")).size(),
                        ((Set<?>) field(formation, "recruitmentArrivalReports")).size(),
                        field(formation, "activeStructuralWorkerId"), field(formation, "activeStructuralSlot"),
                        field(formation, "collapsed"), field(formation, "collapseReason"),
                        field(formation, "collapseDetail"));
                logAssemblyLockPredicates(level, formation, tick);
            }
            if (placements >= ((Number) field(formation, "requiredBodies")).intValue()
                    && "TRAVERSE".equals(field(formation, "phase").toString())
                    && !(boolean) field(formation, "collapsed")) {
                restoreDiagnostics();
                helper.succeed();
                return;
            }
            if ((boolean) field(formation, "collapsed") || tick >= MAX_OBSERVATION_TICKS) {
                Object reason = field(formation, "collapseReason");
                Object detail = field(formation, "collapseDetail");
                if (reason != null && reason.toString().startsWith("tilt_obstructed_slot_")) {
                    logTiltObstruction(level, formation, Integer.parseInt(reason.toString()
                            .substring("tilt_obstructed_slot_".length())));
                }
                if ("pillar_contact_lost".equals(reason)) logPillarContact(level, formation);
                restoreDiagnostics();
                helper.fail("bridge did not place members and complete a crossing: phase="
                        + field(formation, "phase") + " collapsed=" + field(formation, "collapsed")
                        + " reason=" + reason + " detail=" + detail + " placements=" + placements
                        + " peakPlacements=" + updatedPeakPlacements + " required="
                        + field(formation, "requiredBodies"));
                return;
            }
            helper.runAfterDelay(1, () -> observeBridge(helper, level, workers, formation, tick + 1,
                    updatedPeakPlacements));
        } catch (ReflectiveOperationException exception) {
            restoreDiagnostics();
            helper.fail("bridge observation failed: " + exception);
        }
    }

    private static void logPillarContact(ServerLevel level, Object formation)
            throws ReflectiveOperationException {
        List<?> members = (List<?>) field(formation, "members");
        int columns = ((Number) field(formation, "columnCount")).intValue();
        for (int slot = 0; slot < members.size(); slot++) {
            Object member = members.get(slot);
            if (!(member instanceof java.util.UUID uuid) || !(level.getEntity(uuid) instanceof Mob mob)) continue;
            int below = slot - columns;
            boolean touchesBelow = below < 0 || !(level.getEntity((java.util.UUID) members.get(below)) instanceof Mob support)
                    || LivingWallCoordinator.physicalBodiesConnect(mob.getBoundingBox(), support.getBoundingBox());
            SoundAttractMod.LOGGER.info("[LivingBridgeEndToEndTest] pillar slot={} mob={} alive={} position={} box={} touchesBelow={}",
                    slot, uuid, mob.isAlive() && !mob.isRemoved(), mob.position(), mob.getBoundingBox(), touchesBelow);
        }
        if (!members.isEmpty() && members.get(0) instanceof java.util.UUID rootId
                && level.getEntity(rootId) instanceof Mob root) {
            boolean supported = (boolean) invokeCoordinator("hasFoundationTerrainSupport", 3,
                    level, root, root.position());
            SoundAttractMod.LOGGER.info("[LivingBridgeEndToEndTest] pillar root={} position={} terrainSupport={}",
                    rootId, root.position(), supported);
        }
    }

    private static void logTiltObstruction(ServerLevel level, Object formation, int slot)
            throws ReflectiveOperationException {
        List<?> members = (List<?>) field(formation, "members");
        if (slot < 0 || slot >= members.size() || !(members.get(slot) instanceof java.util.UUID uuid)
                || !(level.getEntity(uuid) instanceof Mob mob)) return;
        int shiftTick = ((Number) field(formation, "shiftTick")).intValue();
        int shiftDuration = ((Number) field(formation, "shiftDuration")).intValue();
        double progress = shiftDuration <= 0 ? 0.0D : shiftTick / (double) shiftDuration;
        Vec3 from = mob.position();
        Vec3 to = (Vec3) invokeCoordinator("formationSlotPosition", 3, formation, slot, progress);
        int samples = Math.max(1, (int) Math.ceil(from.distanceTo(to) / 0.2D));
        boolean safe = true;
        for (int sample = 0; sample <= samples; sample++) {
            Vec3 position = from.lerp(to, sample / (double) samples);
            AABB box = (AABB) invokeCoordinator("translatedBoundingBox", 2, mob, position);
            Object result = invokeCoordinator("structuralSweepResult", 5, level, mob, box, formation,
                    ((Number) field(formation, "assembledSlots")).doubleValue() + 1.0D);
            Method safeMethod = result.getClass().getDeclaredMethod("safe");
            safeMethod.setAccessible(true);
            if (!(boolean) safeMethod.invoke(result)) {
                safe = false;
                SoundAttractMod.LOGGER.info("[LivingBridgeEndToEndTest] tilt sweep slot={} sample={}/{} pos={} result={}",
                        slot, sample, samples, position, result);
            }
        }
        boolean entityClear = (boolean) invokeCoordinator("isEntityClearAt", 4, level, mob, from, to);
        AABB swept = mob.getBoundingBox().minmax(mob.getBoundingBox().move(to.subtract(from)));
        Set<?> memberIds = Set.copyOf(members);
        for (Entity entity : level.getEntities(mob, swept.inflate(0.1D), candidate ->
                candidate != null && candidate.isAlive() && !candidate.isRemoved()
                        && !memberIds.contains(candidate.getUUID()))) {
            SoundAttractMod.LOGGER.info("[LivingBridgeEndToEndTest] tilt nearby nonmember slot={} entity={} type={} box={}",
                    slot, entity.getUUID(), entity.getType(), entity.getBoundingBox());
        }
        SoundAttractMod.LOGGER.info("[LivingBridgeEndToEndTest] tilt obstruction slot={} from={} to={} progress={} structuralSweepSafe={} entityClear={} members={}",
                slot, from, to, progress, safe, entityClear, members.size());
    }

    private static void logAssemblyLockPredicates(ServerLevel level, Object formation, int tick)
            throws ReflectiveOperationException {
        Map<?, ?> tickets = (Map<?, ?>) field(formation, "mobTickets");
        for (Object ticket : tickets.values()) {
            if (!"STAGING_AT_ASSEMBLY".equals(field(ticket, "state").toString())) continue;
            Object mobId = field(ticket, "mobId");
            Object slotValue = field(ticket, "assemblySlot");
            if (!(mobId instanceof java.util.UUID uuid) || !(slotValue instanceof BlockPos slot)) continue;
            if (!(level.getEntity(uuid) instanceof Mob mob)) continue;
            Vec3 exactTarget = Vec3.atBottomCenterOf(slot);
            boolean owner = (boolean) invokeCoordinator("isCurrentAssemblyOwner", 4,
                    level, formation, mob, ticket);
            boolean atSlot = (boolean) invokeCoordinator("isAtAssemblySlot", 2, mob.position(), slot);
            int slotIndex = (int) invokeCoordinator("assemblySlotIndex", 2, formation, slot);
            int memberSlot = ((List<?>) field(formation, "members")).indexOf(uuid);
            boolean support = memberSlot >= 0
                    ? (boolean) invokeCoordinator("hasVerifiedSupport", 5,
                            level, formation, memberSlot, mob, exactTarget)
                    : (boolean) invokeCoordinator("hasFoundationTerrainSupport", 3,
                            level, mob, exactTarget);
            boolean lockValid = (boolean) invokeCoordinator("isExactAssemblyLockValid", 5,
                    level, formation, mob, ticket, exactTarget);
            Object clearance = invokeCoordinator("resolveClearance", 6,
                    level, mob, exactTarget, 0.0D, formation,
                    ((Number) field(formation, "assembledSlots")).doubleValue() + 1.0D);
            Method clearMethod = clearance.getClass().getDeclaredMethod("clear");
            clearMethod.setAccessible(true);
            boolean clear = (boolean) clearMethod.invoke(clearance);
            boolean entityClear = (boolean) invokeCoordinator("isEntityClearAt", 4,
                    level, mob, mob.position(), exactTarget);
            Object occupant = invokeCoordinator("findAssemblyOccupant", 6,
                    level, formation, mob, exactTarget, exactTarget, slot);
            SoundAttractMod.LOGGER.info("[LivingBridgeEndToEndTest] lock tick={} mob={} state={} pos={} slot={} owner={} atSlot={} slotIndex={} memberSlot={} support={} lockValid={} clearance={} entityClear={} occupied={}",
                    tick, uuid, field(ticket, "state"), mob.position(), slot, owner, atSlot, slotIndex,
                    memberSlot, support, lockValid, clear, entityClear, occupant != null);
        }
    }

    private static Object invokeCoordinator(String name, int parameterCount, Object... arguments)
            throws ReflectiveOperationException {
        Method method = java.util.Arrays.stream(LivingWallCoordinator.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name)
                        && candidate.getParameterCount() == parameterCount
                        && acceptsArguments(candidate.getParameterTypes(), arguments))
                .findFirst().orElseThrow(() -> new NoSuchMethodException(name + "/" + parameterCount));
        method.setAccessible(true);
        return method.invoke(null, arguments);
    }

    private static boolean acceptsArguments(Class<?>[] parameterTypes, Object[] arguments) {
        for (int index = 0; index < parameterTypes.length; index++) {
            Class<?> parameter = parameterTypes[index];
            Object argument = arguments[index];
            if (argument == null) {
                if (parameter.isPrimitive()) return false;
                continue;
            }
            if (parameter.isPrimitive()) {
                parameter = switch (parameter.getName()) {
                    case "boolean" -> Boolean.class;
                    case "byte" -> Byte.class;
                    case "short" -> Short.class;
                    case "int" -> Integer.class;
                    case "long" -> Long.class;
                    case "float" -> Float.class;
                    case "double" -> Double.class;
                    case "char" -> Character.class;
                    default -> parameter;
                };
            }
            if (!parameter.isInstance(argument)) return false;
        }
        return true;
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
}
