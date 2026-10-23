package com.example.soundattract.mixin;

import com.example.soundattract.event.SoundAttractionEvents;
import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.ai.FollowerEdgeRelayGoal;
import com.example.soundattract.ai.FollowLeaderGoal;
import com.example.soundattract.ai.LeaderAttractionGoal;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.Soundattract;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "noppes.npcs.entity.EntityNPCInterface", remap = false)
public abstract class CustomNpcsUpdateTasksMixin {

    @Inject(method = "updateTasks()V", at = @At("TAIL"))
    private void soundattract$afterUpdateTasks(CallbackInfo ci) {
        if (SoundAttractConfig.COMMON == null || !SoundAttractConfig.COMMON.enableCustomNpcsIntegration.get()) {
            return;
        }

        try {
            Mob mob = (Mob) (Object) this;
            if (mob.level() == null || mob.level().isClientSide()) {
                return;
            }

            if (!SoundAttractionEvents.isCustomNpcsMob(mob)) {
                return;
            }

            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info("[CustomNPCs] updateTasks() completed, re-adding sound attraction goals for {}", mob.getName().getString());
            }


            addCustomNpcsGoalsAfterUpdate(mob);

        } catch (Throwable t) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.error("[CustomNPCs] Error in updateTasks mixin", t);
            }
        }
    }

    private void addCustomNpcsGoalsAfterUpdate(Mob mob) {
        GoalSelector goalSelector = ((MobAccessor) mob).getGoalSelector();
        if (goalSelector == null) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info("[CustomNPCs] {} has null goalSelector (MobAccessor failed)", mob.getName().getString());
            }
            return;
        }

        double moveSpeed = SoundAttractConfig.COMMON.mobMoveSpeed.get();
        int attractionPriority = 0;

        boolean hasAttractionGoal = goalSelector.getAvailableGoals().stream()
                .anyMatch(wrappedGoal -> wrappedGoal.getGoal() instanceof AttractionGoal);
        boolean hasLeaderAttractionGoal = goalSelector.getAvailableGoals().stream()
                .anyMatch(wrappedGoal -> wrappedGoal.getGoal() instanceof LeaderAttractionGoal);
        boolean hasFollowerEdgeRelayGoal = goalSelector.getAvailableGoals().stream()
                .anyMatch(wrappedGoal -> wrappedGoal.getGoal() instanceof FollowerEdgeRelayGoal);
        boolean hasFollowLeaderGoal = goalSelector.getAvailableGoals().stream()
                .anyMatch(wrappedGoal -> wrappedGoal.getGoal() instanceof FollowLeaderGoal);

        boolean smartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();

        if (smartEdge) {
            if (!hasFollowerEdgeRelayGoal) {
                goalSelector.addGoal(attractionPriority, new FollowerEdgeRelayGoal(mob, moveSpeed));
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[CustomNPCs] Re-added FollowerEdgeRelayGoal to {}", mob.getName().getString());
                }
            }
            if (!hasLeaderAttractionGoal) {
                goalSelector.addGoal(attractionPriority + 1, new LeaderAttractionGoal(mob, moveSpeed));
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[CustomNPCs] Re-added LeaderAttractionGoal to {}", mob.getName().getString());
                }
            }
        } else {
            if (!hasAttractionGoal) {
                goalSelector.addGoal(attractionPriority, new AttractionGoal(mob, moveSpeed) {
                    @Override
                    public boolean canUse() {
                        boolean hasTarget = (mob.getTarget() != null && mob.getTarget().isAlive());
                        boolean result = (!hasTarget || isHighWeightOverrideActive()) && super.canUse();
                        if (SoundAttractConfig.COMMON.debugLogging.get() && result) {
                            Soundattract.LOGGER.info("[CustomNPCs] AttractionGoal.canUse() returning true for {}", mob.getName().getString());
                        }
                        return result;
                    }

                    @Override
                    public boolean canContinueToUse() {
                        boolean hasTarget = (mob.getTarget() != null && mob.getTarget().isAlive());
                        boolean result = (!hasTarget || isHighWeightOverrideActive()) && super.canContinueToUse();
                        if (SoundAttractConfig.COMMON.debugLogging.get() && result) {
                            Soundattract.LOGGER.info("[CustomNPCs] AttractionGoal.canContinueToUse() returning true for {}", mob.getName().getString());
                        }
                        return result;
                    }
                });
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[CustomNPCs] Re-added AttractionGoal to {}", mob.getName().getString());
                }
            }
        }

        if (!hasFollowLeaderGoal) {
            goalSelector.addGoal(attractionPriority + 2, new FollowLeaderGoal(mob, moveSpeed));
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info("[CustomNPCs] Re-added FollowLeaderGoal to {}", mob.getName().getString());
            }
        }
    }
}
