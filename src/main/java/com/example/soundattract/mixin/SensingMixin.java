package com.example.soundattract.mixin;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.event.FovEvents;
import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.sensing.Sensing;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Sensing.class)
public abstract class SensingMixin {

    @Accessor("mob")
    public abstract Mob soundattract$getMob();

    @Inject(method = "hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void soundattract$hasLineOfSight(Entity target, CallbackInfoReturnable<Boolean> cir) {
        Mob mob = soundattract$getMob();
        if (target instanceof Player player) {
            boolean visible = FovEvents.hasSmartLineOfSight(mob, player);
            cir.setReturnValue(visible);
            return;
        }

        if (SoundAttractConfig.COMMON.enableXrayTargeting.get()) {
            double xrayRange = StealthDetectionEvents.getEffectiveXrayRange(mob);
            if (xrayRange > 0d) {
                double distSq = mob.distanceToSqr(target);
                if (distSq <= xrayRange * xrayRange) {
                    if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.STEALTH_DETECTION)) {
                        com.example.soundattract.SoundAttractMod.LOGGER.info(
                            "[SensingMixin] {} can see {} through walls with X-ray (range: {}, distSq: {})",
                            mob.getName().getString(),
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

        boolean visible = FovEvents.hasSmartLineOfSight(mob, target);
        cir.setReturnValue(visible);
    }
}
