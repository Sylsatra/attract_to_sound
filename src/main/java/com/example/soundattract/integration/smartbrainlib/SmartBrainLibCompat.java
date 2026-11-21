package com.example.soundattract.integration.smartbrainlib;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.runtime.DynamicScanCooldownManager;
import com.example.soundattract.SoundAttractMod;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraftforge.fml.ModList;

public final class SmartBrainLibCompat {
    private static final boolean IS_SBL_LOADED = ModList.get().isLoaded("smartbrainlib");

    private SmartBrainLibCompat() {}

    public static boolean tryAttachSoundAttractBrain(Mob mob) {
        if (!IS_SBL_LOADED || mob == null) return false;
        try {
            return Proxy.tryAttach(mob);
        } catch (Throwable t) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SMART_BRAIN_LIB)) {
                SoundAttractMod.LOGGER.warn("[SmartBrainLibCompat] Failed to attach brain integration for {}", mob.getUUID());
            }
            return false;
        }
    }

    /** Attach the fire flee behavior to both idle and combat activities for SBL mobs. */
    public static boolean tryAttachFireFleeBrain(Mob mob) {
        if (!IS_SBL_LOADED || !(mob instanceof PathfinderMob pathfinder)) return false;
        try {
            return Proxy.tryAttachFireFlee(pathfinder);
        } catch (Throwable t) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
                SoundAttractMod.LOGGER.warn("[CSGrenades] Failed to attach SmartBrainLib fire flee behavior for {}", mob.getUUID(), t);
            }
            return false;
        }
    }

    public static void stopSecondBrainMovement(Mob mob) {
        if (!IS_SBL_LOADED || !(mob instanceof PathfinderMob pathfinder)) return;
        try {
            Proxy.stopClueBehavior(pathfinder);
        } catch (Throwable ignored) {
        }
    }

    public static boolean managesSecondBrainMovement(Mob mob) {
        if (!IS_SBL_LOADED || !(mob instanceof PathfinderMob pathfinder)) return false;
        try {
            return Proxy.hasClueBehavior(pathfinder);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class Proxy {
        private static final String OLD_BEHAVIOUR_CLASS = "com.example.soundattract.integration.sbl.SoundAttractSblBehaviour";
        private static final String BEHAVIOUR_CLASS = "com.example.soundattract.integration.sbl.MoveToSoundBehaviour";
        private static final String FIRE_FLEE_BEHAVIOUR_CLASS = "com.example.soundattract.integration.csgrenades.FireFleeBehaviour";
        private static final Class<?> SMART_BRAIN_CLASS = resolveSmartBrainClass();

        private static boolean tryAttachFireFlee(PathfinderMob mob) {
            Object brain = mob.getBrain();
            if (!isSmartBrain(brain)) {
                return false;
            }

            if (net.tslat.smartbrainlib.util.BrainUtils.getAllBehaviours(mob.getBrain())
                    .anyMatch(behaviour -> behaviour != null && behaviour.getClass().getName().equals(FIRE_FLEE_BEHAVIOUR_CLASS))) {
                return true;
            }

            com.example.soundattract.integration.csgrenades.FireFleeBehaviour<PathfinderMob> idleBehaviour =
                    new com.example.soundattract.integration.csgrenades.FireFleeBehaviour<>();
            addBehaviour(brain, Integer.MAX_VALUE, net.minecraft.world.entity.schedule.Activity.CORE, idleBehaviour);

            boolean attached = net.tslat.smartbrainlib.util.BrainUtils.getAllBehaviours(mob.getBrain())
                    .anyMatch(behaviour -> behaviour != null && behaviour.getClass().getName().equals(FIRE_FLEE_BEHAVIOUR_CLASS));
            if (attached && DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
                SoundAttractMod.LOGGER.debug("[CSGrenades] Attached SmartBrainLib fire flee behavior to {}", mob.getUUID());
            }
            return attached;
        }

        private static boolean tryAttach(Mob mob) {
            if (!(mob instanceof net.minecraft.world.entity.PathfinderMob pathfinder)) {
                return false;
            }

            Object brain = pathfinder.getBrain();
            if (!isSmartBrain(brain)) {
                return false;
            }

            boolean hasBehaviour = net.tslat.smartbrainlib.util.BrainUtils.getAllBehaviours(pathfinder.getBrain())
                .anyMatch(b -> b != null && b.getClass().getName().equals(BEHAVIOUR_CLASS));

            if (hasBehaviour) {
                addClueBehavior(brain, pathfinder);
                return true;
            }

            net.tslat.smartbrainlib.util.BrainUtils.removeBehaviour(pathfinder, (priority, activity, behaviour, parent) -> behaviour != null && behaviour.getClass().getName().equals(OLD_BEHAVIOUR_CLASS));

            net.tslat.smartbrainlib.util.BrainUtils.addMemories(pathfinder.getBrain(),
                net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
                net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET,
                com.example.soundattract.integration.sbl.SoundAttractSensor.SOUND_ATTRACT_TARGET,
                com.example.soundattract.integration.sbl.ScentSensor.SCENT_TARGET);

            addSensor(brain, new com.example.soundattract.integration.sbl.SoundAttractSensor<>());
            addSensor(brain, new com.example.soundattract.integration.sbl.ScentSensor<>());

            com.example.soundattract.integration.sbl.MoveToSoundBehaviour<net.minecraft.world.entity.PathfinderMob> soundBehaviour = new com.example.soundattract.integration.sbl.MoveToSoundBehaviour<>();
            soundBehaviour.cooldownFor(e -> Math.max(1, DynamicScanCooldownManager.currentScanCooldownTicks));
            addBehaviour(brain, 3, net.minecraft.world.entity.schedule.Activity.IDLE, soundBehaviour);

            com.example.soundattract.integration.sbl.MoveToScentBehaviour<net.minecraft.world.entity.PathfinderMob> scentBehaviour = new com.example.soundattract.integration.sbl.MoveToScentBehaviour<>();
            addBehaviour(brain, 5, net.minecraft.world.entity.schedule.Activity.IDLE, scentBehaviour);
            addClueBehavior(brain, pathfinder);

            return true;
        }

        private static void addClueBehavior(Object brain, PathfinderMob mob) {
            boolean hasClueBehavior = net.tslat.smartbrainlib.util.BrainUtils.getAllBehaviours(mob.getBrain())
                    .anyMatch(behaviour -> behaviour != null && behaviour.getClass().getName().equals(ClueBehaviour.class.getName()));
            if (!hasClueBehavior) addBehaviour(brain, 6, net.minecraft.world.entity.schedule.Activity.IDLE, new ClueBehaviour<>());
        }

        private static boolean hasClueBehavior(PathfinderMob mob) {
            if (!isSmartBrain(mob.getBrain())) return false;
            return net.tslat.smartbrainlib.util.BrainUtils.getAllBehaviours(mob.getBrain())
                    .anyMatch(behaviour -> behaviour != null
                            && behaviour.getClass().getName().equals(ClueBehaviour.class.getName()));
        }

        private static void stopClueBehavior(PathfinderMob mob) {
            net.tslat.smartbrainlib.util.BrainUtils.getAllBehaviours(mob.getBrain())
                    .filter(ClueBehaviour.class::isInstance)
                    .map(ClueBehaviour.class::cast)
                    .forEach(behaviour -> behaviour.cancelForDisabled(mob));
        }

        private static final class ClueBehaviour<E extends PathfinderMob>
                extends net.tslat.smartbrainlib.api.core.behaviour.ExtendedBehaviour<E> {
            private static final java.util.List<com.mojang.datafixers.util.Pair<net.minecraft.world.entity.ai.memory.MemoryModuleType<?>, net.minecraft.world.entity.ai.memory.MemoryStatus>> MEMORY_REQUIREMENTS =
                    it.unimi.dsi.fastutil.objects.ObjectArrayList.of(
                            com.mojang.datafixers.util.Pair.of(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
                                    net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED),
                            com.mojang.datafixers.util.Pair.of(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET,
                                    net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED));

            private com.example.soundattract.ai.ClueDirector.Hint hint;
            private net.minecraft.world.entity.ai.memory.WalkTarget ownedWalkTarget;
            private net.minecraft.world.entity.ai.behavior.PositionTracker ownedLookTarget;

            private ClueBehaviour() {
                runFor(entity -> 100);
                startCondition(entity -> entity != null && entity.getTarget() == null
                        && !com.example.soundattract.ai.LivingWallCoordinator.isControlled(entity)
                        && com.example.soundattract.config.SoundAttractConfig.COMMON.enableSecondBrain.get());
            }

            @Override
            protected java.util.List<com.mojang.datafixers.util.Pair<net.minecraft.world.entity.ai.memory.MemoryModuleType<?>, net.minecraft.world.entity.ai.memory.MemoryStatus>> getMemoryRequirements() {
                return MEMORY_REQUIREMENTS;
            }

            @Override
            protected boolean checkExtraStartConditions(net.minecraft.server.level.ServerLevel level, E entity) {
                if (net.tslat.smartbrainlib.util.BrainUtils.getMemory(entity,
                        net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET) != null
                        || net.tslat.smartbrainlib.util.BrainUtils.getMemory(entity,
                        net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET) != null) return false;
                if (!com.example.soundattract.ai.ClueDirector.registerInterest(entity)) return false;
                hint = com.example.soundattract.ai.ClueDirector.takeHint(entity);
                return hint != null;
            }

            @Override
            protected void start(E entity) {
                if (hint == null) return;
                ownedLookTarget = new net.minecraft.world.entity.ai.behavior.BlockPosTracker(hint.position());
                ownedWalkTarget = new net.minecraft.world.entity.ai.memory.WalkTarget(
                        net.minecraft.world.phys.Vec3.atBottomCenterOf(hint.position()),
                        (float) com.example.soundattract.config.SoundAttractConfig.COMMON.mobMoveSpeed.get().doubleValue(), 1);
                net.tslat.smartbrainlib.util.BrainUtils.setMemory(entity,
                        net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET,
                        ownedLookTarget);
                net.tslat.smartbrainlib.util.BrainUtils.setMemory(entity,
                        net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET, ownedWalkTarget);
            }

            @Override
            protected boolean shouldKeepRunning(E entity) {
                return hint != null && ownedWalkTarget != null && ownedLookTarget != null
                        && net.tslat.smartbrainlib.util.BrainUtils.getMemory(entity,
                        net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET) == ownedWalkTarget
                        && net.tslat.smartbrainlib.util.BrainUtils.getMemory(entity,
                        net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET) == ownedLookTarget
                        && entity.getTarget() == null
                        && !com.example.soundattract.ai.LivingWallCoordinator.isControlled(entity)
                        && com.example.soundattract.ai.ClueDirector.isHintActive(entity, hint);
            }

            @Override
            protected void tick(E entity) {
                if (hint != null && entity.blockPosition().distSqr(hint.position()) <= 4.0) {
                    clearOwnedWalkTarget(entity);
                    com.example.soundattract.ai.ClueDirector.releaseHint(entity, hint,
                            com.example.soundattract.ai.ClueDirector.Resolution.ARRIVED);
                    hint = null;
                }
            }

            @Override
            protected void stop(E entity) {
                clearOwnedWalkTarget(entity);
                if (hint != null) {
                    com.example.soundattract.ai.ClueDirector.releaseHint(entity, hint,
                            com.example.soundattract.ai.ClueDirector.Resolution.PREEMPTED);
                    hint = null;
                }
                com.example.soundattract.ai.ClueDirector.cancelInterest(entity);
            }

            private void cancelForDisabled(E entity) {
                stop(entity);
            }

            private void clearOwnedWalkTarget(E entity) {
                if (ownedWalkTarget != null && net.tslat.smartbrainlib.util.BrainUtils.getMemory(
                        entity, net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET) == ownedWalkTarget) {
                    net.tslat.smartbrainlib.util.BrainUtils.clearMemory(entity,
                            net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
                }
                if (ownedLookTarget != null && net.tslat.smartbrainlib.util.BrainUtils.getMemory(
                        entity, net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET) == ownedLookTarget) {
                    net.tslat.smartbrainlib.util.BrainUtils.clearMemory(entity,
                            net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);
                }
                ownedWalkTarget = null;
                ownedLookTarget = null;
            }
        }

        private static boolean isSmartBrain(Object brain) {
            return brain != null && SMART_BRAIN_CLASS != null && SMART_BRAIN_CLASS.isInstance(brain);
        }

        private static Class<?> resolveSmartBrainClass() {
            try {
                return Class.forName("net.tslat.smartbrainlib.api.core.SmartBrain");
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static void addSensor(Object brain, Object sensor) {
            try {
                Class<?> smartBrain = Class.forName("net.tslat.smartbrainlib.api.core.SmartBrain");
                Class<?> sensorClass = Class.forName("net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor");
                smartBrain.getMethod("addSensor", sensorClass).invoke(brain, sensor);
            } catch (Throwable t) {
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SMART_BRAIN_LIB)) {
                    SoundAttractMod.LOGGER.warn("[SmartBrainLibCompat] Failed to add sensor: {}", t.getMessage());
                }
            }
        }

        private static void addBehaviour(Object brain, int priority, Object activity, Object behaviour) {
            try {
                Class<?> smartBrain = Class.forName("net.tslat.smartbrainlib.api.core.SmartBrain");
                Class<?> activityClass = Class.forName("net.minecraft.world.entity.schedule.Activity");
                Class<?> behaviourClass = Class.forName("net.minecraft.world.entity.ai.behavior.BehaviorControl");
                smartBrain.getMethod("addBehaviour", int.class, activityClass, behaviourClass)
                    .invoke(brain, priority, activity, behaviour);
            } catch (Throwable t) {
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SMART_BRAIN_LIB)) {
                    SoundAttractMod.LOGGER.warn("[SmartBrainLibCompat] Failed to add behaviour: {}", t.getMessage());
                }
            }
        }
    }
}
