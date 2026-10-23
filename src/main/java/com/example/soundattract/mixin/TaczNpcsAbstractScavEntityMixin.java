package com.example.soundattract.mixin;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.corrinedev.tacznpcs.common.entity.AbstractScavEntity", remap = false)
public abstract class TaczNpcsAbstractScavEntityMixin {

    @Inject(method = "lambda$getCoreTasks$15(Lnet/minecraft/world/entity/PathfinderMob;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void soundattract$nullSafeCoreTask15(PathfinderMob mob, CallbackInfoReturnable<Boolean> cir) {
        try {
            Mob self = (Mob) (Object) this;
            if (self.getTarget() == null) {
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {
        }
    }
}
