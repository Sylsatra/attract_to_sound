package com.example.soundattract.mixin;

import com.example.soundattract.event.FovEvents;
import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.sensing.Sensing;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Sensing.class)
public abstract class SensingMixin {

    @Shadow @Final private Mob mob;

    @Inject(method = "hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void soundattract$hasLineOfSight(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof Player player) {
            boolean visible = FovEvents.hasSmartLineOfSight(this.mob, player);
            cir.setReturnValue(visible);
            return;
        }

        if (SoundAttractConfig.COMMON.enableXrayTargeting.get()) {
            double xrayRange = StealthDetectionEvents.getEffectiveXrayRange(this.mob);
            if (xrayRange > 0d) {
                double distSq = this.mob.distanceToSqr(target);
                if (distSq <= xrayRange * xrayRange) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        com.example.soundattract.Soundattract.LOGGER.info(
                            "[SensingMixin] {} can see {} through walls with X-ray (range: {}, distSq: {})",
                            this.mob.getName().getString(),
                            target.getName().getString(),
                            String.format("%.2f", xrayRange),
                            String.format("%.2f", distSq)
                        );
                    }
                    cir.setReturnValue(true);
                    return;
                }
            }
        }

        boolean visible = FovEvents.hasSmartLineOfSight(this.mob, target);
        cir.setReturnValue(visible);
    }
}
