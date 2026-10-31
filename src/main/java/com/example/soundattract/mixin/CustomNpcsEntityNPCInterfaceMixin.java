package com.example.soundattract.mixin;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.ai.FollowerEdgeRelayGoal;
import com.example.soundattract.ai.LeaderAttractionGoal;
import com.example.soundattract.ai.FollowLeaderGoal;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.event.SoundAttractionEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Pseudo
@Mixin(targets = "noppes.npcs.entity.EntityNPCInterface", remap = false)
public abstract class CustomNpcsEntityNPCInterfaceMixin {

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
            SoundAttractMod.LOGGER.info("[CustomNPCs] updateTasks called for {}, checking goals", mob.getName().getString());
        }

        Set<EntityType<?>> blacklisted = SoundAttractionEvents.getCachedBlacklistedEntityTypes();
        if (blacklisted.contains(mob.getType())) {
            return;
        }

        if (!SoundAttractionEvents.isCustomNpcsMob(mob)) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[CustomNPCs] {} is not detected as CustomNPCs mob", mob.getName().getString());
            }
            return;
        }

        if (mob.goalSelector == null) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[CustomNPCs] {} has null goalSelector", mob.getName().getString());
            }
            return;
        }

        double moveSpeed = SoundAttractConfig.COMMON.mobMoveSpeed.get();

        boolean hasAttractionGoal = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrappedGoal -> wrappedGoal.getGoal() instanceof AttractionGoal);
        boolean hasLeaderAttractionGoal = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrappedGoal -> wrappedGoal.getGoal() instanceof LeaderAttractionGoal);
        boolean hasFollowerEdgeRelayGoal = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrappedGoal -> wrappedGoal.getGoal() instanceof FollowerEdgeRelayGoal);
        boolean hasFollowLeaderGoal = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrappedGoal -> wrappedGoal.getGoal() instanceof FollowLeaderGoal);

        boolean smartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        int attractionPriority = 0;

        if (smartEdge) {
            if (!hasFollowerEdgeRelayGoal) {
                mob.goalSelector.addGoal(attractionPriority, new FollowerEdgeRelayGoal(mob, moveSpeed));
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    com.example.soundattract.SoundAttractMod.LOGGER.info("[CustomNPCs] Re-added FollowerEdgeRelayGoal to {}", mob.getName().getString());
                }
            }
            if (!hasLeaderAttractionGoal) {
                mob.goalSelector.addGoal(attractionPriority + 1, new LeaderAttractionGoal(mob, moveSpeed));
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    com.example.soundattract.SoundAttractMod.LOGGER.info("[CustomNPCs] Re-added LeaderAttractionGoal to {}", mob.getName().getString());
                }
            }
        } else {
            if (!hasAttractionGoal) {
                mob.goalSelector.addGoal(attractionPriority, new AttractionGoal(mob, moveSpeed) {
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
                    com.example.soundattract.SoundAttractMod.LOGGER.info("[CustomNPCs] Re-added AttractionGoal to {}", mob.getName().getString());
                }
            }
        }

        if (!hasFollowLeaderGoal) {
            mob.goalSelector.addGoal(attractionPriority + 2, new FollowLeaderGoal(mob, moveSpeed));
        }
    }

    @Inject(method = "readSpawnData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void soundattract$onReadSpawnData(CallbackInfo ci) {
        if (SoundAttractConfig.COMMON == null || !SoundAttractConfig.COMMON.enableCustomNpcsIntegration.get()) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[CustomNPCs] Integration disabled, skipping readSpawnData");
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
            SoundAttractMod.LOGGER.info("[CustomNPCs] readSpawnData called for {}, checking if CustomNPCs", mob.getName().getString());
        }

        if (!SoundAttractionEvents.isCustomNpcsMob(mob)) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[CustomNPCs] {} is not detected as CustomNPCs mob", mob.getName().getString());
            }
            return;
        }

        SoundAttractionEvents.invalidateCachedEntityTypes();

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[CustomNPCs] Spawn data read for {}, forcing goal registration", mob.getName().getString());
        }

        soundattract$afterUpdateTasks(ci);
    }
}
