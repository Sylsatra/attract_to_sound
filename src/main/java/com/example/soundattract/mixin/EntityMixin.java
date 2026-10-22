package com.example.soundattract.mixin;

import com.example.soundattract.camo.CamouflageCapability;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
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
            CamouflageCapability.getCapability(player).ifPresent(camo -> {
                camo.onWaterSplashed(player, false);
            });
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void soundattract$refreshSpatialIndex(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Mob mob && !mob.level().isClientSide && mob.tickCount % 5 == 0) {
            com.example.soundattract.spatial.SpatialLifecycleEvents.refreshMob(mob);
        }
    }
}
