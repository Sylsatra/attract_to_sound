package com.example.soundattract.integration.smartbrainlib;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.ai.ClueDirector;
import com.example.soundattract.ai.LivingWallCoordinator;
import com.example.soundattract.config.DebugLoggingConfig;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.integration.csgrenades.FireFleeBehaviour;
import com.example.soundattract.integration.sbl.MoveToScentBehaviour;
import com.example.soundattract.integration.sbl.MoveToSoundBehaviour;
import com.example.soundattract.integration.sbl.ScentSensor;
import com.example.soundattract.integration.sbl.SoundAttractSensor;
import com.example.soundattract.runtime.DynamicScanCooldownManager;
import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.tslat.smartbrainlib.api.core.SmartBrain;
import net.tslat.smartbrainlib.api.core.behaviour.ExtendedBehaviour;
import net.tslat.smartbrainlib.util.BrainUtils;

import java.util.List;

public final class SmartBrainLibCompat {
    private static final boolean IS_SBL_LOADED = ModList.get().isLoaded("smartbrainlib");

    private SmartBrainLibCompat() {}

    public static boolean tryAttachSoundAttractBrain(Mob mob) {
        if (!IS_SBL_LOADED || !(mob instanceof PathfinderMob pathfinder)) return false;
        try {
            return Proxy.tryAttach(pathfinder);
        } catch (LinkageError | RuntimeException failure) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SMART_BRAIN_LIB)) {
                SoundAttractMod.LOGGER.warn("[SmartBrainLibCompat] Failed to attach brain integration for {}",
                        mob.getUUID(), failure);
            }
            return false;
        }
    }

    public static void stopSecondBrainMovement(Mob mob) {
        if (!IS_SBL_LOADED || !(mob instanceof PathfinderMob pathfinder)) return;
        try {
            Proxy.stopClueBehavior(pathfinder);
        } catch (LinkageError | RuntimeException ignored) {
        }
    }

    public static boolean tryAttachFireFleeBrain(PathfinderMob mob) {
        if (!IS_SBL_LOADED) return false;
        try {
            return Proxy.tryAttachFireFlee(mob.getBrain());
        } catch (LinkageError | RuntimeException failure) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
                SoundAttractMod.LOGGER.warn("[CSGrenades] Failed to attach SmartBrainLib fire flee behavior for {}",
                        mob.getUUID(), failure);
            }
            return false;
        }
    }

    public static boolean managesSecondBrainMovement(Mob mob) {
        if (!IS_SBL_LOADED || !(mob instanceof PathfinderMob pathfinder)) return false;
        try {
            return Proxy.hasClueBehavior(pathfinder.getBrain());
        } catch (LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static final class Proxy {
        private static final String OLD_BEHAVIOUR_CLASS =
                "com.example.soundattract.integration.sbl.SoundAttractSblBehaviour";

        private static boolean tryAttachFireFlee(Brain<?> brain) {
            if (!(brain instanceof SmartBrain<?>)) return false;
            if (BrainUtils.getAllBehaviours(brain).anyMatch(FireFleeBehaviour.class::isInstance)) return true;
            BrainUtils.addBehaviour(brain, Integer.MAX_VALUE, Activity.CORE, new FireFleeBehaviour<PathfinderMob>());
            return BrainUtils.getAllBehaviours(brain).anyMatch(FireFleeBehaviour.class::isInstance);
        }

        private static boolean tryAttach(PathfinderMob mob) {
            Brain<?> brain = mob.getBrain();
            if (!(brain instanceof SmartBrain<?>)) return false;

            boolean hasSoundBehavior = BrainUtils.getAllBehaviours(brain).anyMatch(MoveToSoundBehaviour.class::isInstance);
            if (!hasSoundBehavior) {
                BrainUtils.removeBehaviour(mob, (priority, activity, behaviour, parent) ->
                        behaviour != null && behaviour.getClass().getName().equals(OLD_BEHAVIOUR_CLASS));
                BrainUtils.addMemories(brain, MemoryModuleType.WALK_TARGET, MemoryModuleType.LOOK_TARGET,
                        SoundAttractSensor.SOUND_ATTRACT_TARGET, ScentSensor.SCENT_TARGET);
                BrainUtils.addSensor(brain, SoundAttractSensor.TYPE, new SoundAttractSensor<>());
                BrainUtils.addSensor(brain, ScentSensor.TYPE, new ScentSensor<>());

                MoveToSoundBehaviour<PathfinderMob> soundBehavior = new MoveToSoundBehaviour<>();
                soundBehavior.cooldownFor(entity -> Math.max(1, DynamicScanCooldownManager.currentScanCooldownTicks));
                BrainUtils.addBehaviour(brain, 3, Activity.IDLE, soundBehavior);
                BrainUtils.addBehaviour(brain, 5, Activity.IDLE, new MoveToScentBehaviour<PathfinderMob>());
            }
            if (!hasClueBehavior(brain)) {
                BrainUtils.addBehaviour(brain, 6, Activity.IDLE, new ClueBehaviour());
            }
            return true;
        }

        private static boolean hasClueBehavior(Brain<?> brain) {
            return brain instanceof SmartBrain<?>
                    && BrainUtils.getAllBehaviours(brain).anyMatch(ClueBehaviour.class::isInstance);
        }

        private static void stopClueBehavior(PathfinderMob mob) {
            Brain<?> brain = mob.getBrain();
            if (!(brain instanceof SmartBrain<?>)) return;
            BrainUtils.getAllBehaviours(brain)
                    .filter(ClueBehaviour.class::isInstance)
                    .map(ClueBehaviour.class::cast)
                    .forEach(behavior -> behavior.cancelForDisabled(mob));
        }

        private static final class ClueBehaviour extends ExtendedBehaviour<PathfinderMob> {
            private static final List<Pair<MemoryModuleType<?>, MemoryStatus>> MEMORY_REQUIREMENTS = List.of(
                    Pair.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED),
                    Pair.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED));

            private ClueDirector.Hint hint;
            private WalkTarget ownedWalkTarget;
            private PositionTracker ownedLookTarget;

            private ClueBehaviour() {
                runFor(entity -> 100);
                startCondition(entity -> entity != null && entity.getTarget() == null
                        && !LivingWallCoordinator.isControlled(entity)
                        && SoundAttractConfig.COMMON.enableSecondBrain.get());
            }

            @Override
            protected List<Pair<MemoryModuleType<?>, MemoryStatus>> getMemoryRequirements() {
                return MEMORY_REQUIREMENTS;
            }

            @Override
            protected boolean checkExtraStartConditions(ServerLevel level, PathfinderMob entity) {
                if (BrainUtils.getMemory(entity, MemoryModuleType.WALK_TARGET) != null
                        || BrainUtils.getMemory(entity, MemoryModuleType.LOOK_TARGET) != null
                        || !ClueDirector.registerInterest(entity)) return false;
                hint = ClueDirector.takeHint(entity);
                return hint != null;
            }

            @Override
            protected void start(PathfinderMob entity) {
                if (hint == null) return;
                ownedLookTarget = new BlockPosTracker(hint.position());
                ownedWalkTarget = new WalkTarget(Vec3.atBottomCenterOf(hint.position()),
                        SoundAttractConfig.COMMON.mobMoveSpeed.get().floatValue(), 1);
                BrainUtils.setMemory(entity, MemoryModuleType.LOOK_TARGET, ownedLookTarget);
                BrainUtils.setMemory(entity, MemoryModuleType.WALK_TARGET, ownedWalkTarget);
            }

            @Override
            protected boolean shouldKeepRunning(PathfinderMob entity) {
                return hint != null && ownedWalkTarget != null && ownedLookTarget != null
                        && BrainUtils.getMemory(entity, MemoryModuleType.WALK_TARGET) == ownedWalkTarget
                        && BrainUtils.getMemory(entity, MemoryModuleType.LOOK_TARGET) == ownedLookTarget
                        && entity.getTarget() == null
                        && !LivingWallCoordinator.isControlled(entity)
                        && ClueDirector.isHintActive(entity, hint);
            }

            @Override
            protected void tick(PathfinderMob entity) {
                if (hint != null && entity.blockPosition().distSqr(hint.position()) <= 4.0) {
                    clearOwnedTargets(entity);
                    ClueDirector.releaseHint(entity, hint, ClueDirector.Resolution.ARRIVED);
                    hint = null;
                }
            }

            @Override
            protected void stop(PathfinderMob entity) {
                clearOwnedTargets(entity);
                if (hint != null) {
                    ClueDirector.releaseHint(entity, hint, ClueDirector.Resolution.PREEMPTED);
                    hint = null;
                }
                ClueDirector.cancelInterest(entity);
            }

            private void cancelForDisabled(PathfinderMob entity) {
                stop(entity);
            }

            private void clearOwnedTargets(PathfinderMob entity) {
                if (ownedWalkTarget != null && BrainUtils.getMemory(entity, MemoryModuleType.WALK_TARGET) == ownedWalkTarget) {
                    BrainUtils.clearMemory(entity, MemoryModuleType.WALK_TARGET);
                }
                if (ownedLookTarget != null && BrainUtils.getMemory(entity, MemoryModuleType.LOOK_TARGET) == ownedLookTarget) {
                    BrainUtils.clearMemory(entity, MemoryModuleType.LOOK_TARGET);
                }
                ownedWalkTarget = null;
                ownedLookTarget = null;
            }
        }
    }
}
