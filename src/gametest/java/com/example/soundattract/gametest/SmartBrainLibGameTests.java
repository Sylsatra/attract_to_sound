package com.example.soundattract.gametest;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.tracking.SoundTracker;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.tslat.smartbrainlib.api.SmartBrainOwner;
import net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public final class SmartBrainLibGameTests {
    private static final int TEST_TIMEOUT_TICKS = 100;

    private static String describeThrowable(Throwable throwable) {
        StringBuilder description = new StringBuilder();
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (!description.isEmpty()) description.append(" <- ");
            description.append(cause.getClass().getName());
            if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
                description.append(": ").append(cause.getMessage());
            }
        }
        return description.toString();
    }

    private static java.lang.reflect.Field findBrainField(Class<?> entityClass) throws ReflectiveOperationException {
        Class<?> brainType = Class.forName("net.minecraft.world.entity.ai.Brain");
        for (Class<?> current = entityClass; current != null; current = current.getSuperclass()) {
            for (java.lang.reflect.Field field : current.getDeclaredFields()) {
                if (brainType.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        throw new NoSuchFieldException("No Brain field on " + entityClass.getName());
    }

    private static List<String> readSblBehaviourNames(Object smartBrain) throws ReflectiveOperationException {
        Object stream = smartBrain.getClass().getMethod("getBehaviours").invoke(smartBrain);
        java.lang.reflect.Field prioritiesField = stream.getClass().getDeclaredField("behaviours");
        prioritiesField.setAccessible(true);
        List<String> names = new ArrayList<>();
        for (Object priority : (List<?>) prioritiesField.get(stream)) {
            Object configured = priority.getClass().getMethod("behaviours").invoke(priority);
            for (Object behaviour : (Iterable<?>) configured) names.add(behaviour.getClass().getName());
        }
        return List.copyOf(names);
    }

    private static List<String> readSblSensorNames(Object smartBrain) throws ReflectiveOperationException {
        for (Class<?> current = smartBrain.getClass(); current != null; current = current.getSuperclass()) {
            try {
                java.lang.reflect.Field field = current.getDeclaredField("sensors");
                field.setAccessible(true);
                List<String> sensorNames = new ArrayList<>();
                for (Object sensor : (Iterable<?>) field.get(smartBrain)) {
                    sensorNames.add(sensor.getClass().getName());
                }
                return List.copyOf(sensorNames);
            } catch (NoSuchFieldException ignored) {
                // Continue through the class hierarchy in case the SBL API relocates its sensor storage.
            }
        }
        throw new NoSuchFieldException("No sensors list on " + smartBrain.getClass().getName());
    }

    private static Object findSblSensor(Object smartBrain, String simpleName) throws ReflectiveOperationException {
        for (Class<?> current = smartBrain.getClass(); current != null; current = current.getSuperclass()) {
            try {
                java.lang.reflect.Field field = current.getDeclaredField("sensors");
                field.setAccessible(true);
                for (Object sensor : (Iterable<?>) field.get(smartBrain)) {
                    if (sensor.getClass().getSimpleName().equals(simpleName)) return sensor;
                }
            } catch (NoSuchFieldException ignored) {
                // Continue through the class hierarchy in case SBL relocates its sensor storage.
            }
        }
        throw new NoSuchFieldException("No " + simpleName + " instance on " + smartBrain.getClass().getName());
    }

    private static void prepareFloor(GameTestHelper helper, int minX, int minZ, int maxX, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
                helper.setBlock(x, 1, z, Blocks.AIR);
                helper.setBlock(x, 2, z, Blocks.AIR);
            }
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = TEST_TIMEOUT_TICKS)
    public void smartBrainLibIntegrationAttachesProductionSensorsAndBehaviours(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 16, 3);
        Zombie vanillaBrainMob = helper.spawn(com.example.soundattract.gametest.GameTestEntityTypes.ZOMBIE, 1, 1, 1);
        boolean smartBrainLibLoaded = FabricLoader.getInstance().isModLoaded("smartbrainlib");
        boolean vanillaBrainRejected = !com.example.soundattract.integration.smartbrainlib.SmartBrainLibCompat
                .tryAttachSoundAttractBrain(vanillaBrainMob);

        if (!smartBrainLibLoaded) {
            if (!vanillaBrainRejected) {
                helper.fail("SmartBrainLib integration accepted a vanilla brain while the optional mod was absent");
                return;
            }
            helper.succeed();
            return;
        }

        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        SmartBrainTestZombie sblBrainMob = new SmartBrainTestZombie(level);
        BlockPos mobPos = helper.absolutePos(new BlockPos(1, 1, 1));
        sblBrainMob.setPos(mobPos.getX() + 0.5, mobPos.getY(), mobPos.getZ() + 0.5);
        sblBrainMob.setYRot(0.0f);
        // GameTests run without a nearby real player; keep the fixture alive so
        // vanilla's normal distance-despawn rule does not remove it after one tick.
        sblBrainMob.setPersistenceRequired();
        // Isolate SBL behavior from vanilla zombie target goals, which can lock
        // onto the GameTest server's mock player and suppress sound sensing.
        ((com.example.soundattract.mixin.MobAccessor) (Object) sblBrainMob)
                .getTargetSelector().removeAllGoals(goal -> true);
        Object smartBrain;
        try {
            // SBL's 26.2 builder copies memories from the packed vanilla brain;
            // null is no longer accepted by its makeBrain implementation.
            smartBrain = sblBrainMob.getBrainBuilder().makeBrain(
                    sblBrainMob, net.minecraft.world.entity.ai.Brain.Packed.EMPTY);
            findBrainField(sblBrainMob.getClass()).set(sblBrainMob, smartBrain);
        } catch (Throwable throwable) {
            helper.fail("Could not install the actual SmartBrainLib brain type on a GameTest mob: "
                    + describeThrowable(throwable));
            return;
        }

        try {
            BlockPos soundPos = mobPos.offset(10, 0, 0);
            sblBrainMob.expectedSound = soundPos;
            String dimension = level.dimension().identifier().toString();
            int previousCooldown = com.example.soundattract.runtime.DynamicScanCooldownManager.currentScanCooldownTicks;
            double previousArrivalDistance = SoundAttractConfig.COMMON.arrivalDistance.get();
            boolean previousScentSystem = SoundAttractConfig.COMMON.enableScentSystem.get();
            Set<Identifier> previousWhitelist = Set.copyOf(SoundAttractConfig.SOUND_ID_WHITELIST_CACHE);
            com.example.soundattract.runtime.DynamicScanCooldownManager.currentScanCooldownTicks = 1;
            SoundAttractConfig.COMMON.arrivalDistance.set(1.0);
            SoundAttractConfig.COMMON.enableScentSystem.set(false);
            SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.clear();
            SoundTracker.addVirtualSound(soundPos, dimension, 64.0, 10_000.0, 100, UUID.randomUUID(),
                    "minecraft:entity.zombie.ambient");
            if (!level.addFreshEntity(sblBrainMob)) {
                SoundTracker.removeSoundAt(soundPos, dimension);
                com.example.soundattract.runtime.DynamicScanCooldownManager.currentScanCooldownTicks = previousCooldown;
                SoundAttractConfig.COMMON.arrivalDistance.set(previousArrivalDistance);
                SoundAttractConfig.COMMON.enableScentSystem.set(previousScentSystem);
                SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.addAll(previousWhitelist);
                helper.fail("Could not add the SmartBrainOwner mob to the GameTest level");
                return;
            }

            // The production entity-load callback has run. Remove vanilla goal
            // selector work now so it cannot overwrite the SBL memory targets.
            com.example.soundattract.mixin.MobAccessor mobAccessor =
                    (com.example.soundattract.mixin.MobAccessor) (Object) sblBrainMob;
            mobAccessor.getGoalSelector().removeAllGoals(goal -> true);
            mobAccessor.getTargetSelector().removeAllGoals(goal -> true);

            // Adding an eligible mob to the real GameTest level invokes the same
            // ServerEntityEvents.ENTITY_LOAD callback used for production entities.
            boolean attachedByEntityLoad = readSblBehaviourNames(smartBrain).stream()
                    .anyMatch(name -> name.endsWith("MoveToSoundBehaviour"))
                    && readSblBehaviourNames(smartBrain).stream()
                    .anyMatch(name -> name.endsWith("MoveToScentBehaviour"))
                    && readSblSensorNames(smartBrain).stream()
                    .anyMatch(name -> name.endsWith("SoundAttractSensor"))
                    && readSblSensorNames(smartBrain).stream()
                    .anyMatch(name -> name.endsWith("ScentSensor"));
            List<String> behavioursAfterEntityLoad = readSblBehaviourNames(smartBrain);
            List<String> sensorsAfterEntityLoad = readSblSensorNames(smartBrain);
            boolean attachedAgain = com.example.soundattract.integration.smartbrainlib.SmartBrainLibCompat
                    .tryAttachSoundAttractBrain(sblBrainMob);
            boolean idempotent = readSblBehaviourNames(smartBrain).equals(behavioursAfterEntityLoad)
                    && readSblSensorNames(smartBrain).equals(sensorsAfterEntityLoad);
            if (!vanillaBrainRejected || !attachedByEntityLoad || !attachedAgain || !idempotent) {
                SoundTracker.removeSoundAt(soundPos, dimension);
                com.example.soundattract.runtime.DynamicScanCooldownManager.currentScanCooldownTicks = previousCooldown;
                SoundAttractConfig.COMMON.arrivalDistance.set(previousArrivalDistance);
                SoundAttractConfig.COMMON.enableScentSystem.set(previousScentSystem);
                SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.clear();
                SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.addAll(previousWhitelist);
                helper.fail("SmartBrainLib entity-load attachment mismatch: vanillaRejected=" + vanillaBrainRejected
                        + ", attachedByEntityLoad=" + attachedByEntityLoad + ", behaviours=" + behavioursAfterEntityLoad
                        + ", sensors=" + sensorsAfterEntityLoad + ", attachedAgain=" + attachedAgain
                        + ", idempotent=" + idempotent);
                return;
            }

            helper.runAfterDelay(30, () -> {
                try {
                    net.minecraft.core.BlockPos rememberedSound = net.tslat.smartbrainlib.util.BrainUtil.getMemory(
                            sblBrainMob, com.example.soundattract.integration.sbl.SoundAttractSensor.SOUND_ATTRACT_TARGET);
                    net.minecraft.world.entity.ai.memory.WalkTarget walkTarget = net.tslat.smartbrainlib.util.BrainUtil.getMemory(
                            sblBrainMob, net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
                    Object lookTarget = net.tslat.smartbrainlib.util.BrainUtil.getMemory(
                            sblBrainMob, net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);
                    boolean soundAcquired = soundPos.equals(rememberedSound);
                    boolean pursuitStarted = walkTarget != null && lookTarget != null
                            && sblBrainMob.soundWalkTargetObservations > 0;
                    if (sblBrainMob.brainTicks < 1 || !soundAcquired || !pursuitStarted) {
                        SoundTracker.SoundRecord nearest = SoundTracker.findNearestSound(sblBrainMob, level,
                                sblBrainMob.blockPosition(), sblBrainMob.getEyePosition());
                        helper.fail("Automatic world ticks did not run the attached SBL brain and acquire/pursue Sound Attract's sound: brainTicks="
                                + sblBrainMob.brainTicks + ", remembered=" + rememberedSound + ", expected=" + soundPos
                                + ", walkTarget=" + walkTarget + ", walkPosition=" + (walkTarget == null ? "null"
                                        : walkTarget.getTarget().currentPosition()) + ", target=" + sblBrainMob.getTarget()
                                + ", soundMemoryTicks=" + sblBrainMob.soundMemoryObservations
                                + ", soundWalkTargetTicks=" + sblBrainMob.soundWalkTargetObservations
                                + ", lookTarget=" + lookTarget + ", nearbySounds="
                                + SoundTracker.getNearbySounds(level, soundPos, 64.0).size() + ", entityTickCount="
                                + sblBrainMob.tickCount + ", noAi=" + sblBrainMob.isNoAi() + ", removed="
                                + sblBrainMob.isRemoved() + ", alive=" + sblBrainMob.isAlive() + ", gameTime="
                                + level.getGameTime() + ", nearest="
                                + (nearest == null ? "null" : nearest.pos));
                        return;
                    }
                    helper.succeed();
                } catch (Throwable throwable) {
                    helper.fail("Could not tick the attached SmartBrainLib sensor and behaviour path: "
                            + describeThrowable(throwable));
                } finally {
                    SoundTracker.removeSoundAt(soundPos, dimension);
                    com.example.soundattract.runtime.DynamicScanCooldownManager.currentScanCooldownTicks = previousCooldown;
                    SoundAttractConfig.COMMON.arrivalDistance.set(previousArrivalDistance);
                    SoundAttractConfig.COMMON.enableScentSystem.set(previousScentSystem);
                    SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.clear();
                    SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.addAll(previousWhitelist);
                    sblBrainMob.discard();
                }
            });
        } catch (Throwable throwable) {
            helper.fail("Could not inspect SmartBrainLib attachment results: " + describeThrowable(throwable));
        }
    }

    private static final class SmartBrainTestZombie extends Zombie implements SmartBrainOwner<SmartBrainTestZombie> {
        private int brainTicks;
        private BlockPos expectedSound;
        private int soundMemoryObservations;
        private int soundWalkTargetObservations;

        private SmartBrainTestZombie(ServerLevel level) {
            super(com.example.soundattract.gametest.GameTestEntityTypes.ZOMBIE, level);
        }

        @Override
        public List<? extends ExtendedSensor<?>> getSensors(SmartBrainTestZombie entity) {
            return List.of();
        }

        @Override
        public net.tslat.smartbrainlib.api.core.ActivityBuilder<SmartBrainTestZombie> getIdleBehaviourGroup(
                SmartBrainTestZombie entity) {
            return net.tslat.smartbrainlib.api.core.ActivityBuilder.create(
                    net.minecraft.world.entity.schedule.Activity.IDLE);
        }

        @Override
        protected void customServerAiStep(ServerLevel level) {
            brainTicks++;
            // Other GameTests update the shared adaptive cooldown using their
            // mobs. Pin this fixture to one scan tick while exercising SBL.
            com.example.soundattract.runtime.DynamicScanCooldownManager.currentScanCooldownTicks = 1;
            @SuppressWarnings("unchecked")
            net.tslat.smartbrainlib.api.internal.SmartBrain<SmartBrainTestZombie> smartBrain =
                    (net.tslat.smartbrainlib.api.internal.SmartBrain<SmartBrainTestZombie>) (Object) getBrain();
            smartBrain.tick(level, this);
            if (expectedSound != null) {
                BlockPos soundMemory = net.tslat.smartbrainlib.util.BrainUtil.getMemory(
                        this, com.example.soundattract.integration.sbl.SoundAttractSensor.SOUND_ATTRACT_TARGET);
                if (expectedSound.equals(soundMemory)) soundMemoryObservations++;
                net.minecraft.world.entity.ai.memory.WalkTarget walkTarget = net.tslat.smartbrainlib.util.BrainUtil.getMemory(
                        this, net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
                if (walkTarget != null && walkTarget.getTarget().currentPosition().distanceTo(
                        net.minecraft.world.phys.Vec3.atCenterOf(expectedSound)) < 0.01) {
                    soundWalkTargetObservations++;
                }
            }
        }
    }
}
