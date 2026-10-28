package com.example.soundattract.mixin;

import com.example.soundattract.event.ArrowInvestigationEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class ProjectileMixin {

    @Inject(method = "onHit", at = @At("HEAD"))
    private void soundattract$onProjectileHit(HitResult hitResult, CallbackInfo ci) {
        Projectile projectile = (Projectile) (Object) this;
        if (projectile.level() instanceof ServerLevel level) {
            ArrowInvestigationEvents.onProjectileImpact(projectile, hitResult, level);
        }
    }
}
