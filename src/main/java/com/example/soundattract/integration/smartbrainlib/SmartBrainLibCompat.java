package com.example.soundattract.integration.smartbrainlib;

import com.example.soundattract.runtime.DynamicScanCooldownManager;
import com.example.soundattract.Soundattract;

import net.minecraft.world.entity.Mob;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

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

            net.tslat.smartbrainlib.util.BrainUtil.removeBehaviour(pathfinder, net.minecraft.world.entity.schedule.Activity.IDLE,
                (behaviour, activity, priority, parent) -> behaviour != null && behaviour.getClass().getName().equals(OLD_BEHAVIOUR_CLASS));

            addSensor(brain, mob, new com.example.soundattract.integration.sbl.SoundAttractSensor<>());
            addSensor(brain, mob, new com.example.soundattract.integration.sbl.ScentSensor<>());


            com.example.soundattract.integration.sbl.MoveToSoundBehaviour<net.minecraft.world.entity.PathfinderMob> soundBehaviour = new com.example.soundattract.integration.sbl.MoveToSoundBehaviour<>();
            soundBehaviour.cooldownFor(e -> Math.max(1, DynamicScanCooldownManager.currentScanCooldownTicks));
            addBehaviour(brain, 3, net.minecraft.world.entity.schedule.Activity.IDLE, soundBehaviour);


            com.example.soundattract.integration.sbl.MoveToScentBehaviour<net.minecraft.world.entity.PathfinderMob> scentBehaviour = new com.example.soundattract.integration.sbl.MoveToScentBehaviour<>();
            addBehaviour(brain, 5, net.minecraft.world.entity.schedule.Activity.IDLE, scentBehaviour);

            return true;
        }

        private static boolean isSmartBrain(Object brain) {
            if (brain == null) return false;
            try {
                Class<?> smartBrain = Class.forName("net.tslat.smartbrainlib.api.internal.SmartBrain");
                return smartBrain.isInstance(brain);
            } catch (Throwable ignored) {
                return false;
            }
        }

        private static void addSensor(Object brain, Mob mob, Object sensor) {
            try {
                Class<?> smartBrain = Class.forName("net.tslat.smartbrainlib.api.internal.SmartBrain");
                Class<?> sensorContainer = Class.forName("net.tslat.smartbrainlib.api.internal.SmartBrain$Sensors");
                Class<?> sensorClass = Class.forName("net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor");
                Object sensors = smartBrain.getMethod("getSensors").invoke(brain);
                Method addSensor = sensorContainer.getMethod("addSensor", sensorClass,
                        net.minecraft.util.RandomSource.class, boolean.class);
                addSensor.invoke(sensors, sensor, mob.getRandom(), true);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not add a Sound Attract sensor to the SmartBrainLib brain", exception);
            }
        }

        private static void addBehaviour(Object brain, int priority, Object activity, Object behaviour) {
            try {
                Class<?> smartBrain = Class.forName("net.tslat.smartbrainlib.api.internal.SmartBrain");
                Class<?> behavioursContainer = Class.forName("net.tslat.smartbrainlib.api.internal.SmartBrain$Behaviours");
                Class<?> activityClass = Class.forName("net.minecraft.world.entity.schedule.Activity");
                Class<?> behaviourClass = Class.forName("net.minecraft.world.entity.ai.behavior.BehaviorControl");
                Object behaviours = smartBrain.getMethod("getBehaviours").invoke(brain);
                behavioursContainer.getMethod("addBehaviour", activityClass, int.class, behaviourClass, boolean.class)
                    .invoke(behaviours, activity, priority, behaviour, true);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not add a Sound Attract behaviour to the SmartBrainLib brain", exception);
            }
        }
    }
}
