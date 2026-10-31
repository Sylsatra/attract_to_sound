package com.example.soundattract.integration.smartbrainlib;

import com.example.soundattract.runtime.DynamicScanCooldownManager;
import com.example.soundattract.SoundAttractMod;

import net.minecraft.world.entity.Mob;
import net.neoforged.fml.ModList;

public final class SmartBrainLibCompat {
    private static final boolean IS_SBL_LOADED = ModList.get().isLoaded("smartbrainlib");

    private SmartBrainLibCompat() {}

    public static boolean tryAttachSoundAttractBrain(Mob mob) {
        if (!IS_SBL_LOADED || mob == null) return false;
        try {
            return Proxy.tryAttach(mob);
        } catch (Throwable t) {
            if (com.example.soundattract.config.SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.warn("[SmartBrainLibCompat] Failed to attach brain integration for {}", mob.getUUID());
            }
            return false;
        }
    }

    private static final class Proxy {
        private static final String OLD_BEHAVIOUR_CLASS = "com.example.soundattract.integration.sbl.SoundAttractSblBehaviour";
        private static final String BEHAVIOUR_CLASS = "com.example.soundattract.integration.sbl.MoveToSoundBehaviour";

        private static boolean tryAttach(Mob mob) {
            if (!(mob instanceof net.minecraft.world.entity.PathfinderMob pathfinder)) {
                return false;
            }

            Object brain = pathfinder.getBrain();
            if (!isSmartBrain(brain)) {
                return false;
            }

            try {
                Class<?> brainUtilsClass = Class.forName("net.tslat.smartbrainlib.util.BrainUtil");
                Object behaviours = brainUtilsClass.getMethod("getAllBehaviours", Object.class).invoke(null, brain);
                java.util.stream.Stream<?> stream = (java.util.stream.Stream<?>) behaviours;
                boolean hasBehaviour = stream.anyMatch(b -> b != null && b.getClass().getName().equals(BEHAVIOUR_CLASS));

                if (hasBehaviour) {
                    return true;
                }

                brainUtilsClass.getMethod("removeBehaviour", net.minecraft.world.entity.LivingEntity.class, net.tslat.smartbrainlib.object.BrainBehaviourPredicate.class)
                    .invoke(null, pathfinder, (net.tslat.smartbrainlib.object.BrainBehaviourPredicate) (priority, activity, behaviour, parent) -> 
                        behaviour != null && behaviour.getClass().getName().equals(OLD_BEHAVIOUR_CLASS));

                brainUtilsClass.getMethod("addMemories", Object.class, Object[].class).invoke(null, brain, new Object[]{
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET,
                    com.example.soundattract.integration.sbl.SoundAttractSensor.SOUND_ATTRACT_TARGET,
                    com.example.soundattract.integration.sbl.ScentSensor.SCENT_TARGET
                });

                addSensor(brain, com.example.soundattract.integration.sbl.SoundAttractSensor.TYPE, new com.example.soundattract.integration.sbl.SoundAttractSensor<>());
                addSensor(brain, com.example.soundattract.integration.sbl.ScentSensor.TYPE, new com.example.soundattract.integration.sbl.ScentSensor<>());

                com.example.soundattract.integration.sbl.MoveToSoundBehaviour<net.minecraft.world.entity.PathfinderMob> soundBehaviour = new com.example.soundattract.integration.sbl.MoveToSoundBehaviour<>();
                soundBehaviour.cooldownFor(e -> Math.max(1, DynamicScanCooldownManager.currentScanCooldownTicks));
                addBehaviour(brain, 3, net.minecraft.world.entity.schedule.Activity.IDLE, soundBehaviour);

                com.example.soundattract.integration.sbl.MoveToScentBehaviour<net.minecraft.world.entity.PathfinderMob> scentBehaviour = new com.example.soundattract.integration.sbl.MoveToScentBehaviour<>();
                addBehaviour(brain, 5, net.minecraft.world.entity.schedule.Activity.IDLE, scentBehaviour);

                return true;
            } catch (Throwable t) {
                if (com.example.soundattract.config.SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.warn("[SmartBrainLibCompat] Failed to attach brain: {}", t.getMessage());
                }
                return false;
            }
        }

        private static boolean isSmartBrain(Object brain) {
            if (brain == null) return false;
            try {
                Class<?> smartBrain = Class.forName("net.tslat.smartbrainlib.api.core.SmartBrain");
                return smartBrain.isInstance(brain);
            } catch (Throwable ignored) {
                return false;
            }
        }

        private static void addSensor(Object brain, Object sensorType, Object sensor) {
            try {
                Class<?> brainUtilsClass = Class.forName("net.tslat.smartbrainlib.util.BrainUtil");
                brainUtilsClass.getMethod("addSensor", Object.class, Object.class, Object.class).invoke(null, brain, sensorType, sensor);
            } catch (Throwable t) {
                if (com.example.soundattract.config.SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.warn("[SmartBrainLibCompat] Failed to add sensor: {}", t.getMessage());
                }
            }
        }

        private static void addBehaviour(Object brain, int priority, Object activity, Object behaviour) {
            try {
                Class<?> brainUtilsClass = Class.forName("net.tslat.smartbrainlib.util.BrainUtil");
                brainUtilsClass.getMethod("addBehaviour", Object.class, int.class, Object.class, Object.class)
                    .invoke(brain, priority, activity, behaviour);
            } catch (Throwable t) {
                if (com.example.soundattract.config.SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.warn("[SmartBrainLibCompat] Failed to add behaviour: {}", t.getMessage());
                }
            }
        }
    }
}