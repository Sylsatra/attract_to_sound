package com.example.soundattract.mixin;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.event.StealthDetectionEvents;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.api.event.NpcEvent;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "noppes.npcs.EventHooks", remap = false)
public abstract class CustomNpcsEventHooksMixin {
    @Inject(method = "onNPCTarget", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void soundattract$cancelStealthBlockedTarget(EntityNPCInterface npc,
                                                               NpcEvent.TargetEvent event,
                                                               CallbackInfoReturnable<Boolean> cir) {
        if (npc == null
                || event == null
                || SoundAttractConfig.COMMON == null
                || !SoundAttractConfig.COMMON.enableCustomNpcsIntegration.get()
                || !SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return;
        }

        try {
            Object targetEntity = event.entity == null ? null : event.entity.getMCEntity();
            if (!(targetEntity instanceof LivingEntity target)) {
                return;
            }

            if (!StealthDetectionEvents.canMobDetectLivingEntity(npc, target)) {
                npc.setTarget(null);
                npc.getNavigation().stop();
                cir.setReturnValue(true);
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info(
                            "[CustomNPCs] Canceled target hook: {} cannot detect {} through stealth.",
                            npc.getName().getString(),
                            target.getName().getString());
                }
            }
        } catch (LinkageError | RuntimeException e) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.warn("[CustomNPCs] Failed to evaluate stealth target hook.", e);
            }
        }
    }
}
