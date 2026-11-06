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
        private static final String BEHAVIOUR_CLASS = "com.example.soundattract.integration.sbl.MoveToSoundBehaviour";
        private static final String SCENT_BEHAVIOUR_CLASS = "com.example.soundattract.integration.sbl.MoveToScentBehaviour";

        private static boolean tryAttach(Mob mob) {
            if (!(mob instanceof net.minecraft.world.entity.PathfinderMob pathfinder)) {
                return false;
            }

            Object brain = pathfinder.getBrain();
            if (!isSmartBrainOwner(pathfinder)) {
                return false;
            }

            try {
                Class<?> BrainUtilClass = Class.forName("net.tslat.smartbrainlib.util.BrainUtil");
                Class<?> brainClass = Class.forName("net.minecraft.world.entity.ai.Brain");
                Object behaviours = BrainUtilClass.getMethod("getAllBehaviours", brainClass).invoke(null, brain);
                java.util.stream.Stream<?> stream = (java.util.stream.Stream<?>) behaviours;
                java.util.Set<String> existingBehaviours = new java.util.HashSet<>();
                stream.forEach(value -> collectSoundAttractBehaviours(value, existingBehaviours));

                if (existingBehaviours.contains(BEHAVIOUR_CLASS)
                        && existingBehaviours.contains(SCENT_BEHAVIOUR_CLASS)) {
                    return true;
                }

                BrainUtilClass.getMethod("addMemories", brainClass, net.minecraft.world.entity.ai.memory.MemoryModuleType[].class)
                        .invoke(null, brain, new net.minecraft.world.entity.ai.memory.MemoryModuleType[]{
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET,
                    com.example.soundattract.integration.sbl.SoundAttractSensor.SOUND_ATTRACT_TARGET,
                    com.example.soundattract.integration.sbl.ScentSensor.SCENT_TARGET
                });

                addSensor(brain, brainClass, com.example.soundattract.integration.sbl.SoundAttractSensor.TYPE,
                        new com.example.soundattract.integration.sbl.SoundAttractSensor<>(), pathfinder.level().getRandom());
                addSensor(brain, brainClass, com.example.soundattract.integration.sbl.ScentSensor.TYPE,
                        new com.example.soundattract.integration.sbl.ScentSensor<>(), pathfinder.level().getRandom());

                if (!existingBehaviours.contains(BEHAVIOUR_CLASS)) {
                    com.example.soundattract.integration.sbl.MoveToSoundBehaviour<net.minecraft.world.entity.PathfinderMob> soundBehaviour = new com.example.soundattract.integration.sbl.MoveToSoundBehaviour<>();
                    soundBehaviour.cooldownFor(e -> Math.max(1, DynamicScanCooldownManager.currentScanCooldownTicks));
                    addBehaviour(brain, 3, net.minecraft.world.entity.schedule.Activity.IDLE, soundBehaviour);
                }

                if (!existingBehaviours.contains(SCENT_BEHAVIOUR_CLASS)) {
                    com.example.soundattract.integration.sbl.MoveToScentBehaviour<net.minecraft.world.entity.PathfinderMob> scentBehaviour = new com.example.soundattract.integration.sbl.MoveToScentBehaviour<>();
                    addBehaviour(brain, 5, net.minecraft.world.entity.schedule.Activity.IDLE, scentBehaviour);
                }

                return true;
            } catch (Throwable t) {
                if (com.example.soundattract.config.SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.warn("[SmartBrainLibCompat] Failed to attach brain: {}", t.getMessage());
                }
                return false;
            }
        }

        private static boolean isSmartBrainOwner(Mob mob) {
            try {
                Class<?> smartBrainOwner = Class.forName("net.tslat.smartbrainlib.api.SmartBrainOwner");
                return smartBrainOwner.isInstance(mob);
            } catch (Throwable ignored) {
                return false;
            }
        }

        private static void collectSoundAttractBehaviours(Object value, java.util.Set<String> result) {
            if (value == null) {
                return;
            }
            if (value.getClass().getName().equals(BEHAVIOUR_CLASS)
                    || value.getClass().getName().equals(SCENT_BEHAVIOUR_CLASS)) {
                result.add(value.getClass().getName());
                return;
            }
            if (value instanceof Iterable<?> iterable) {
                for (Object element : iterable) {
                    collectSoundAttractBehaviours(element, result);
                }
            }
        }

        private static void addSensor(Object brain, Class<?> brainClass, Object sensorType, Object sensor, Object random) {
            try {
                Class<?> BrainUtilClass = Class.forName("net.tslat.smartbrainlib.util.BrainUtil");
                BrainUtilClass.getMethod("addSensor", brainClass,
                        net.minecraft.world.entity.ai.sensing.SensorType.class,
                        net.minecraft.world.entity.ai.sensing.Sensor.class,
                        net.minecraft.util.RandomSource.class).invoke(null, brain, sensorType, sensor, random);
            } catch (Throwable t) {
                if (com.example.soundattract.config.SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.warn("[SmartBrainLibCompat] Failed to add sensor: {}", t.getMessage());
                }
            }
        }

        private static void addBehaviour(Object brain, int priority, Object activity, Object behaviour) {
            try {
                Class<?> BrainUtilClass = Class.forName("net.tslat.smartbrainlib.util.BrainUtil");
                Class<?> brainClass = Class.forName("net.minecraft.world.entity.ai.Brain");
                BrainUtilClass.getMethod("addBehaviour", brainClass, net.minecraft.world.entity.schedule.Activity.class,
                        int.class, net.minecraft.world.entity.ai.behavior.BehaviorControl.class)
                    .invoke(null, brain, activity, priority, behaviour);
            } catch (Throwable t) {
                if (com.example.soundattract.config.SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.warn("[SmartBrainLibCompat] Failed to add behaviour: {}", t.getMessage());
                }
            }
        }
    }
}
