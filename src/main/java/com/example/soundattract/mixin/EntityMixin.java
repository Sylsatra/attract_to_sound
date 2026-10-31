package com.example.soundattract.mixin;

import com.example.soundattract.camo.CamoAttachments;
import com.example.soundattract.camo.CamouflageCapability;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "doWaterSplashEffect", at = @At("HEAD"))
    private void soundattract$onWaterSplash(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player player) {
            CamouflageCapability camo = player.getData(CamoAttachments.CAMOUFLAGE);
            camo.onWaterSplashed(player, false);
        }
    }
}
