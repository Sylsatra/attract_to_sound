package com.example.soundattract.mixin;

import com.example.soundattract.camo.CamoAttachments;
import com.example.soundattract.camo.CamouflageCapability;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(AbstractThrownPotion.class)
public abstract class ThrownPotionMixin {

    @Inject(method = "onHitAsWater", at = @At("HEAD"))
    private void soundattract$onWaterSplash(net.minecraft.server.level.ServerLevel level, CallbackInfo ci) {
        AbstractThrownPotion potion = (AbstractThrownPotion) (Object) this;
        AABB aabb = potion.getBoundingBox().inflate(4.0D, 2.0D, 4.0D);
        List<LivingEntity> nearbyEntities = potion.level().getEntitiesOfClass(LivingEntity.class, aabb);
        
        for (LivingEntity entity : nearbyEntities) {
            CamouflageCapability camo = entity.getData(CamoAttachments.CAMOUFLAGE);
            camo.onWaterSplashed(entity, true);
        }
    }
}
