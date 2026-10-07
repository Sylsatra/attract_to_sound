package com.example.soundattract.mixin.hotbath;

import com.crabmod.hotbath.items.SplashBathWaterBottleItem;
import com.example.soundattract.integration.hotbath.HotBathIntegration;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.crabmod.hotbath.items.ThrownBathWater", remap = false)
public abstract class ThrownBathWaterMixin {
    @Inject(method = "applySplash", at = @At("TAIL"), remap = false)
    private void soundattract$washCamoFromHotBathSplash(SplashBathWaterBottleItem splashItem, CallbackInfo ci) {
        HotBathIntegration.applySplashWash((ThrowableItemProjectile) (Object) this, null);
    }
}