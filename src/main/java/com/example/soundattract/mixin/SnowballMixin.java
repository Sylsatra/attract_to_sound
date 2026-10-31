package com.example.soundattract.mixin;

import com.example.soundattract.camo.CamoAttachments;
import com.example.soundattract.camo.CamouflageCapability;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Snowball.class)
public abstract class SnowballMixin {

    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void soundattract$onSnowballHit(EntityHitResult result, CallbackInfo ci) {
        if (result.getEntity() instanceof net.minecraft.world.entity.LivingEntity entity) {
            CamouflageCapability camo = entity.getData(CamoAttachments.CAMOUFLAGE);
            camo.onSnowballed(entity);
        }
    }
}
