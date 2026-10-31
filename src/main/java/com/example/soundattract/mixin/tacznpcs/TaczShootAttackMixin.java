package com.example.soundattract.mixin.tacznpcs;

import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.integration.tacznpcs.TaczNpcDebug;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.corrinedev.tacznpcs.common.entity.behavior.TaczShootAttack", remap = false)
public abstract class TaczShootAttackMixin {
    @Shadow protected LivingEntity target;

    @Inject(
            method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lcom/corrinedev/tacznpcs/common/entity/AbstractScavEntity;)Z",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void soundattract$gateStart(ServerLevel level, @Coerce Mob mob, CallbackInfoReturnable<Boolean> callback) {
        if (!TaczNpcDebug.isIntegrationEnabled()) {
            return;
        }
        TaczNpcDebug.logAiHookReached(mob, "start-condition check");
        if (callback.getReturnValueZ() && shouldSuppress(mob, "checkExtraStartConditions")) {
            callback.setReturnValue(false);
        }
    }

    @Inject(
            method = "start(Lcom/corrinedev/tacznpcs/common/entity/AbstractScavEntity;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void soundattract$gateShotStart(@Coerce Mob mob, CallbackInfo callback) {
        if (!TaczNpcDebug.isIntegrationEnabled()) {
            return;
        }
        TaczNpcDebug.logAiHookReached(mob, "shot start");
        if (shouldSuppress(mob, "start")) {
            callback.cancel();
        }
    }

    private boolean shouldSuppress(Mob mob, String hook) {
        LivingEntity currentTarget = this.target;
        boolean suppressed = StealthDetectionEvents.shouldSuppressTargeting(mob, currentTarget);
        TaczNpcDebug.logDecision(mob, currentTarget, hook, suppressed);
        return suppressed;
    }
}
