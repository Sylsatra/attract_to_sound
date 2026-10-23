package com.example.soundattract.integration.smartbrainlib;

import com.example.soundattract.runtime.DynamicScanCooldownManager;
import com.example.soundattract.Soundattract;

import net.minecraft.world.entity.Mob;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

public final class SmartBrainLibCompat {
    private static final boolean IS_SBL_LOADED = FabricLoader.getInstance().isModLoaded("smartbrainlib");

    private SmartBrainLibCompat() {}

    public static boolean tryAttachSoundAttractBrain(Mob mob) {
        if (!IS_SBL_LOADED || mob == null) return false;
        try {
            return Proxy.tryAttach(mob);
        } catch (Throwable t) {
            if (com.example.soundattract.config.SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.warn("[SmartBrainLibCompat] Failed to attach brain integration for {}", mob.getUUID());
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

            boolean hasBehaviour = net.tslat.smartbrainlib.util.BrainUtil.getAllBehaviours(pathfinder.getBrain())
                .anyMatch(b -> b != null && b.getClass().getName().equals(BEHAVIOUR_CLASS));

            if (hasBehaviour) {
                return true;
            }

            net.tslat.smartbrainlib.util.BrainUtil.removeBehaviour(pathfinder, (priority, activity, behaviour, parent) -> behaviour != null && behaviour.getClass().getName().equals(OLD_BEHAVIOUR_CLASS));

            registerBrainMemories(brain,
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

            return true;
        }

        /**
         * SmartBrainLib's BrainUtil.addMemories only probes the vanilla brain's
         * existing memory entries; it does not add missing entries. Sensors
         * attached after a mob's brain has been created therefore need their
         * custom memory modules registered explicitly before they can write to
         * them.
         */
        @SuppressWarnings("unchecked")
        private static void registerBrainMemories(Object brain, net.minecraft.world.entity.ai.memory.MemoryModuleType<?>... modules) {
            try {
                Class<?> brainClass = Class.forName("net.minecraft.world.entity.ai.Brain");
                Field memoriesField = brainClass.getDeclaredField("memories");
                memoriesField.setAccessible(true);
                Map<Object, Optional<?>> memories = (Map<Object, Optional<?>>) memoriesField.get(brain);
                for (net.minecraft.world.entity.ai.memory.MemoryModuleType<?> module : modules) {
                    memories.putIfAbsent(module, Optional.empty());
                }
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not register Sound Attract memories on SmartBrainLib brain", exception);
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

        private static void addSensor(Object brain, Object sensor) {
            try {
                Class<?> smartBrain = Class.forName("net.tslat.smartbrainlib.api.core.SmartBrain");
                Class<?> sensorClass = Class.forName("net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor");
                smartBrain.getMethod("addSensor", sensorClass).invoke(brain, sensor);
            } catch (Throwable t) {
                if (com.example.soundattract.config.SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.warn("[SmartBrainLibCompat] Failed to add sensor: {}", t.getMessage());
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
                if (com.example.soundattract.config.SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.warn("[SmartBrainLibCompat] Failed to add behaviour: {}", t.getMessage());
                }
            }
        }
    }
}
