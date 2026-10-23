package com.example.soundattract.mixin;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.event.SoundAttractionEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric equivalent of the Forge CustomNPCs target-event listener. */
@Mixin(Mob.class)
public abstract class CustomNpcStealthTargetMixin {
    @Inject(method = "setTarget(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
    private void soundattract$cancelUndetectedCustomNpcTarget(LivingEntity target, CallbackInfo ci) {
        Mob npc = (Mob) (Object) this;
        if (target != null && SoundAttractConfig.COMMON != null
                && SoundAttractConfig.COMMON.enableCustomNpcsIntegration.get()
                && SoundAttractConfig.COMMON.enableStealthMechanics.get()
                && SoundAttractionEvents.isCustomNpcsMob(npc)
                && !StealthDetectionEvents.canMobDetectLivingEntity(npc, target)) {
            ci.cancel();
        }
    }
}
