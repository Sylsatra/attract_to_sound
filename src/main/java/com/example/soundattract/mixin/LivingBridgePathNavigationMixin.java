package com.example.soundattract.mixin;

import com.example.soundattract.ai.LivingWallCoordinator;
import com.example.soundattract.ai.LivingBridgeNavigationGuardProbe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PathNavigation.class)
public abstract class LivingBridgePathNavigationMixin {
    @Shadow
    protected Mob mob;

    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true, require = 1, expect = 1, allow = 1)
    private void soundattract$guardSafeBaseRoute(CallbackInfo callback) {
        if (mob == null || mob.level().isClientSide
                || !mob.getPersistentData().getBoolean("livingBridgeSafeBaseRouteActive")) return;
        LivingBridgeNavigationGuardProbe.recordInvocation();
        if (!(mob.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread()) {
            holdAndCancel(callback);
            return;
        }
        if (!LivingWallCoordinator.guardSafeBaseNavigation(mob)) callback.cancel();
    }

    private void holdAndCancel(CallbackInfo callback) {
        mob.getNavigation().stop();
        mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.0D);
        Vec3 velocity = mob.getDeltaMovement();
        mob.setDeltaMovement(0.0D, velocity.y, 0.0D);
        callback.cancel();
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void soundattract$assistTrapdoorJump(CallbackInfo callback) {
        if (mob != null && !mob.level().isClientSide && mob.isAlive()
                && mob.horizontalCollision && mob.onGround()) {
            com.example.soundattract.pathfinding.TrapdoorNavigationHelper.tryJumpOverTrapdoor(mob);
        }
    }
}