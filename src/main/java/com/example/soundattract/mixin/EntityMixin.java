package com.example.soundattract.mixin;

import com.example.soundattract.camo.CamouflageCapability;
import com.example.soundattract.ai.LivingBridgeMotionDiagnostics;
import com.example.soundattract.config.separate.LivingWallConfig;
import net.minecraft.world.entity.Entity;
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

    @Inject(method = "doWaterSplashEffect", at = @At("HEAD"))
    private void soundattract$onWaterSplash(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player player) {
            player.getCapability(CamouflageCapability.INSTANCE).ifPresent(camo -> {
                camo.onWaterSplashed(player, false);
            });
        }
    }

    @Inject(method = "push(DDD)V", at = @At("HEAD"), cancellable = true)
    private void soundattract$preventHazardPush(double x, double y, double z, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        boolean diagnostics = LivingWallConfig.ENABLE_MOTION_DIAGNOSTICS != null
                && LivingWallConfig.ENABLE_MOTION_DIAGNOSTICS.get()
                && !entity.level().isClientSide();
        if (x * x + z * z < 1.0e-5D) {
            if (diagnostics) LivingBridgeMotionDiagnostics.recordVector(entity, x, y, z, false);
            return;
        }
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            net.minecraft.world.level.Level level = mob.level();
            if (level != null && !level.isClientSide()) {
                net.minecraft.core.BlockPos feetPos = mob.blockPosition();
                if (com.example.soundattract.pathfinding.HazardClassifier.isSafeGround(level, feetPos)) {
                    if (diagnostics) LivingBridgeMotionDiagnostics.recordVector(entity, x, y, z, false);
                    return;
                }
                net.minecraft.world.phys.Vec3 currentPos = mob.position();
                net.minecraft.world.phys.Vec3 nextPos = currentPos.add(x * 5.0D, 0.0D, z * 5.0D);
                if (com.example.soundattract.pathfinding.HazardClassifier.isDangerous(level, mob, nextPos)
                        || com.example.soundattract.pathfinding.HazardClassifier.isDangerous(level, mob, currentPos.add(x * 10.0D, 0.0D, z * 10.0D))) {
                    if (diagnostics) LivingBridgeMotionDiagnostics.recordVector(entity, x, y, z, true);
                    ci.cancel();
                    return;
                }
            }
        }
        if (diagnostics) LivingBridgeMotionDiagnostics.recordVector(entity, x, y, z, false);
    }
}