package com.example.soundattract.mixin.taczhostiles;

import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.integration.taczhostiles.TaczHostilesDebug;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.corrinedev.tachostiles.common.entity.behavior.TaczShootAttack", remap = false)
public abstract class TaczShootAttackMixin {
    @Shadow private LivingEntity target;

    @Inject(
            method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lcom/corrinedev/tachostiles/common/entity/AbstractScavEntity;)Z",
            at = @At("RETURN"), cancellable = true, remap = false, require = 0
    )
    private void soundattract$gateStart(ServerLevel level, @Coerce Mob mob, CallbackInfoReturnable<Boolean> callback) {
        if (soundattract$suppressShot(mob, "checkExtraStartConditions")) {
            callback.setReturnValue(false);
        }
    }

    @Inject(
            method = "shouldKeepRunning(Lcom/corrinedev/tachostiles/common/entity/AbstractScavEntity;)Z",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0
    )
    private void soundattract$gateKeep(@Coerce Mob mob, CallbackInfoReturnable<Boolean> callback) {
        if (soundattract$suppressShot(mob, "shouldKeepRunning")) {
            callback.setReturnValue(false);
        }
    }

    @Inject(
            method = "tick(Lcom/corrinedev/tachostiles/common/entity/AbstractScavEntity;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0
    )
    private void soundattract$gateTick(@Coerce Mob mob, CallbackInfo callback) {
        if (soundattract$suppressShot(mob, "tick")) {
            callback.cancel();
        }
    }

    @Unique
    private boolean soundattract$suppressShot(Mob mob, String hook) {
        TaczHostilesDebug.logHookReached(mob);
        LivingEntity currentTarget = target;
        boolean suppressed = StealthDetectionEvents.shouldSuppressTargeting(mob, currentTarget);
        TaczHostilesDebug.logDecision(mob, currentTarget, hook, suppressed);
        return suppressed;
    }
}
