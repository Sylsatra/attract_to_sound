package com.example.soundattract.mixin;

import com.example.soundattract.camo.CamouflageCapability;
import com.example.soundattract.ai.LivingBridgeMotionDiagnostics;
import com.example.soundattract.config.separate.LivingWallConfig;
import com.example.soundattract.pathfinding.HazardClassifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void soundattract$logBridgeCollisionPair(Entity other, CallbackInfo ci) {
        Entity receiver = (Entity) (Object) this;
        if (LivingWallConfig.ENABLE_MOTION_DIAGNOSTICS == null
                || !LivingWallConfig.ENABLE_MOTION_DIAGNOSTICS.get()
                || receiver.level().isClientSide()) return;
        LivingBridgeMotionDiagnostics.recordPair(receiver, other);
    }

    @Inject(method = "push(DDD)V", at = @At("HEAD"), cancellable = true)
    private void soundattract$preventHazardPush(double x, double y, double z, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        boolean diagnostics = LivingWallConfig.ENABLE_MOTION_DIAGNOSTICS != null
                && LivingWallConfig.ENABLE_MOTION_DIAGNOSTICS.get()
                && !entity.level().isClientSide();
        if (x * x + z * z < 1.0E-5D) {
            if (diagnostics) LivingBridgeMotionDiagnostics.recordVector(entity, x, y, z, false);
            return;
        }
        if (entity instanceof Mob mob && !mob.level().isClientSide()) {
            var level = mob.level();
            if (HazardClassifier.isSafeGround(level, mob.blockPosition())) {
                if (diagnostics) LivingBridgeMotionDiagnostics.recordVector(entity, x, y, z, false);
                return;
            }
            var current = mob.position();
            if (HazardClassifier.isDangerous(level, mob, current.add(x * 5.0D, 0.0D, z * 5.0D))
                    || HazardClassifier.isDangerous(level, mob, current.add(x * 10.0D, 0.0D, z * 10.0D))) {
                if (diagnostics) LivingBridgeMotionDiagnostics.recordVector(entity, x, y, z, true);
                ci.cancel();
                return;
            }
        }
        if (diagnostics) LivingBridgeMotionDiagnostics.recordVector(entity, x, y, z, false);
    }

    @Inject(method = "doWaterSplashEffect", at = @At("HEAD"))
    private void soundattract$onWaterSplash(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player player) {
            CamouflageCapability.getCapability(player).ifPresent(camo -> {
                camo.onWaterSplashed(player, false);
            });
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void soundattract$refreshSpatialIndex(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Mob mob && !mob.level().isClientSide() && mob.tickCount % 5 == 0) {
            com.example.soundattract.spatial.SpatialLifecycleEvents.refreshMob(mob);
        }
    }
}
