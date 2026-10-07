package com.example.soundattract.mixin.hotbath;

import com.crabmod.hotbath.custom_fluid.CustomFluidDefinition;
import com.crabmod.hotbath.custom_fluid.SplashCustomFluidBottleItem;
import com.example.soundattract.integration.hotbath.HotBathIntegration;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.crabmod.hotbath.custom_fluid.ThrownCustomFluidBottle", remap = false)
public abstract class ThrownCustomFluidBottleMixin {
    @Inject(method = "applySplash", at = @At("TAIL"), remap = false)
    private void soundattract$washCamoFromHotBathCustomSplash(SplashCustomFluidBottleItem splashItem, CustomFluidDefinition definition, CallbackInfo ci) {
        HotBathIntegration.applySplashWash((ThrowableItemProjectile) (Object) this, definition == null ? null : definition.id());
    }
}