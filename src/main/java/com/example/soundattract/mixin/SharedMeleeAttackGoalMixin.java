package com.example.soundattract.mixin;

import com.example.soundattract.ai.GroupTargetSharingManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MeleeAttackGoal.class)
public abstract class SharedMeleeAttackGoalMixin {
    @Accessor("mob")
    public abstract PathfinderMob soundattract$getMob();

    @Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
    private void soundattract$stopInvalidSharedLease(CallbackInfoReturnable<Boolean> cir) {
        PathfinderMob mob = soundattract$getMob();
        if (!GroupTargetSharingManager.allowSharedMelee(mob, mob.getTarget())) cir.setReturnValue(false);
    }

    @Redirect(method = "canUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;createPath(Lnet/minecraft/world/entity/Entity;I)Lnet/minecraft/world/level/pathfinder/Path;"))
    private Path soundattract$checkSharedPath(PathNavigation navigation, Entity target, int accuracy) {
        PathfinderMob mob = soundattract$getMob();
        if (target instanceof LivingEntity living && !GroupTargetSharingManager.allowSharedPath(mob, living)) return null;
        return navigation.createPath(target, accuracy);
    }

    @Inject(method = "start", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;moveTo(Lnet/minecraft/world/level/pathfinder/Path;D)Z"), cancellable = true)
    private void soundattract$checkSharedStart(CallbackInfo ci) {
        PathfinderMob mob = soundattract$getMob();
        if (!GroupTargetSharingManager.allowSharedMelee(mob, mob.getTarget())) ci.cancel();
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;moveTo(Lnet/minecraft/world/entity/Entity;D)Z"), require = 0)
    private boolean soundattract$checkSharedMove(PathNavigation navigation, Entity target, double speed) {
        PathfinderMob mob = soundattract$getMob();
        return (!(target instanceof LivingEntity living) || GroupTargetSharingManager.allowSharedPath(mob, living))
                && navigation.moveTo(target, speed);
    }

    @Inject(method = "checkAndPerformAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/PathfinderMob;swing(Lnet/minecraft/world/InteractionHand;)V"), cancellable = true)
    private void soundattract$checkSharedAttack(LivingEntity target, CallbackInfo ci) {
        PathfinderMob mob = soundattract$getMob();
        if (!GroupTargetSharingManager.allowSharedMelee(mob, target)) ci.cancel();
    }
}
