package com.example.soundattract.mixin;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.event.FovEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityLosMixin {

    @Inject(method = "hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void soundattract$overrideHasLineOfSight(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (!SoundAttractConfig.COMMON.enableLivingEntityLosOverride.get()) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Mob mob) || target == null) {
            return;
        }
        cir.setReturnValue(FovEvents.hasSmartLineOfSight(mob, target));
    }

}
