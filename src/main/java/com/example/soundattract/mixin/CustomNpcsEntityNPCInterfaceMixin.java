package com.example.soundattract.mixin;

import com.example.soundattract.Soundattract;
import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.ai.FollowerEdgeRelayGoal;
import com.example.soundattract.ai.LeaderAttractionGoal;
import com.example.soundattract.ai.FollowLeaderGoal;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.event.SoundAttractionEvents;
import com.example.soundattract.integration.customnpcs.GoalWrappingHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.effect.MobEffects;

import java.util.Set;

@Pseudo
@Mixin(targets = "noppes.npcs.entity.EntityNPCInterface", remap = false)
public abstract class CustomNpcsEntityNPCInterfaceMixin {

    @Inject(method = "isInvisible()Z", at = @At("RETURN"), cancellable = true, remap = false)
    private void soundattract$includeVanillaInvisibility(CallbackInfoReturnable<Boolean> cir) {
        if (((net.minecraft.world.entity.LivingEntity) (Object) this).hasEffect(MobEffects.INVISIBILITY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "updateTasks()V", at = @At("TAIL"))
    private void soundattract$afterUpdateTasks(CallbackInfo ci) {
        if (SoundAttractConfig.COMMON == null || !SoundAttractConfig.COMMON.enableCustomNpcsIntegration.get()) {
            return;
        }

        Mob mob;
        try {
            mob = (Mob) (Object) this;
        } catch (Throwable t) {
            return;
        }

        if (mob.level() == null || mob.level().isClientSide()) {
            return;
        }

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info("[CustomNPCs] updateTasks called for {}, checking goals", mob.getName().getString());
        }

        Set<EntityType<?>> blacklisted = SoundAttractionEvents.getCachedBlacklistedEntityTypes();
        if (blacklisted.contains(mob.getType())) {
            return;
        }

        if (!SoundAttractionEvents.isCustomNpcsMob(mob)) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info("[CustomNPCs] {} is not detected as CustomNPCs mob", mob.getName().getString());
            }
            return;
        }

        GoalSelector goalSelector = ((MobAccessor) mob).getGoalSelector();
        if (goalSelector == null) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info("[CustomNPCs] {} has null goalSelector (MobAccessor failed)", mob.getName().getString());
            }
            return;
        }

        double moveSpeed = SoundAttractConfig.COMMON.mobMoveSpeed.get();

        boolean hasAttractionGoal = goalSelector.getAvailableGoals().stream()
                .anyMatch(wrappedGoal -> wrappedGoal.getGoal() instanceof AttractionGoal);
        boolean hasLeaderAttractionGoal = goalSelector.getAvailableGoals().stream()
                .anyMatch(wrappedGoal -> wrappedGoal.getGoal() instanceof LeaderAttractionGoal);
        boolean hasFollowerEdgeRelayGoal = goalSelector.getAvailableGoals().stream()
                .anyMatch(wrappedGoal -> wrappedGoal.getGoal() instanceof FollowerEdgeRelayGoal);
        boolean hasFollowLeaderGoal = goalSelector.getAvailableGoals().stream()
                .anyMatch(wrappedGoal -> wrappedGoal.getGoal() instanceof FollowLeaderGoal);

        boolean smartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        int attractionPriority = 0;

        if (smartEdge) {
            if (!hasFollowerEdgeRelayGoal) {
                goalSelector.addGoal(attractionPriority, new FollowerEdgeRelayGoal(mob, moveSpeed));
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    com.example.soundattract.Soundattract.LOGGER.info("[CustomNPCs] Re-added FollowerEdgeRelayGoal to {}", mob.getName().getString());
                }
            }
            if (!hasLeaderAttractionGoal) {
                goalSelector.addGoal(attractionPriority + 1, new LeaderAttractionGoal(mob, moveSpeed));
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    com.example.soundattract.Soundattract.LOGGER.info("[CustomNPCs] Re-added LeaderAttractionGoal to {}", mob.getName().getString());
                }
            }
        } else {
            if (!hasAttractionGoal) {
                goalSelector.addGoal(attractionPriority, new AttractionGoal(mob, moveSpeed) {
                    @Override
                    public boolean canUse() {
                        return (mob.getTarget() == null || !mob.getTarget().isAlive()) && super.canUse();
                    }

                    @Override
                    public boolean canContinueToUse() {
                        return (mob.getTarget() == null || !mob.getTarget().isAlive()) && super.canContinueToUse();
                    }
                });
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    com.example.soundattract.Soundattract.LOGGER.info("[CustomNPCs] Re-added AttractionGoal to {}", mob.getName().getString());
                }
            }
        }

        if (!hasFollowLeaderGoal) {
            goalSelector.addGoal(attractionPriority + 2, new FollowLeaderGoal(mob, moveSpeed));
        }

        GoalWrappingHelper.wrapCustomNpcsGoals(mob, goalSelector);
    }

    @Inject(method = "readSpawnData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void soundattract$onReadSpawnData(CallbackInfo ci) {
        if (SoundAttractConfig.COMMON == null || !SoundAttractConfig.COMMON.enableCustomNpcsIntegration.get()) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info("[CustomNPCs] Integration disabled, skipping readSpawnData");
            }
            return;
        }

        Mob mob;
        try {
            mob = (Mob) (Object) this;
        } catch (Throwable t) {
            return;
        }

        if (mob.level() == null || mob.level().isClientSide()) {
            return;
        }

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info("[CustomNPCs] readSpawnData called for {}, checking if CustomNPCs", mob.getName().getString());
        }

        if (!SoundAttractionEvents.isCustomNpcsMob(mob)) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info("[CustomNPCs] {} is not detected as CustomNPCs mob", mob.getName().getString());
            }
            return;
        }

        SoundAttractionEvents.invalidateCachedEntityTypes();

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info("[CustomNPCs] Spawn data read for {}, forcing goal registration", mob.getName().getString());
        }

        soundattract$afterUpdateTasks(ci);
    }
}
