package com.example.soundattract.mixin.taczhostiles;

import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.integration.taczhostiles.TaczHostilesDebug;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.corrinedev.tachostiles.common.entity.behavior.TaczShootAttack", remap = false)
public abstract class TaczShootAttackMixin {
    @Shadow private LivingEntity target;
    @Inject(method="checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lcom/corrinedev/tachostiles/common/entity/AbstractScavEntity;)Z", at=@At("RETURN"), cancellable=true, remap=false)
    private void soundattract$gateStart(ServerLevel level, net.minecraft.world.entity.Mob mob, CallbackInfoReturnable<Boolean> ci) {
        TaczHostilesDebug.logHookReached(mob);
        boolean suppressed = StealthDetectionEvents.shouldSuppressTargeting(mob, target);
        TaczHostilesDebug.logDecision(mob, target, "checkExtraStartConditions", suppressed);
        if (suppressed) {
            ci.setReturnValue(false);
        }
    }
    @Inject(method="shouldKeepRunning(Lcom/corrinedev/tachostiles/common/entity/AbstractScavEntity;)Z", at=@At("HEAD"), cancellable=true, remap=false)
    private void soundattract$gateKeep(net.minecraft.world.entity.Mob mob, CallbackInfoReturnable<Boolean> ci) {
        TaczHostilesDebug.logHookReached(mob);
        boolean suppressed = StealthDetectionEvents.shouldSuppressTargeting(mob, target);
        TaczHostilesDebug.logDecision(mob, target, "shouldKeepRunning", suppressed);
        if (suppressed) {
            ci.setReturnValue(false);
        }
    }
    @Inject(method="tick(Lcom/corrinedev/tachostiles/common/entity/AbstractScavEntity;)V", at=@At("HEAD"), cancellable=true, remap=false)
    private void soundattract$gateTick(net.minecraft.world.entity.Mob mob, CallbackInfo ci) {
        TaczHostilesDebug.logHookReached(mob);
        boolean suppressed = StealthDetectionEvents.shouldSuppressTargeting(mob, target);
        TaczHostilesDebug.logDecision(mob, target, "tick", suppressed);
        if (suppressed) {
            ci.cancel();
        }
    }
}
