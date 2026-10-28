package com.example.soundattract.mixin;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.integration.pointblank.PointBlankIntegration;
import com.vicmatskiv.pointblank.item.FireModeInstance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(targets = "com.vicmatskiv.pointblank.item.GunItem", priority = 1100, remap = false)
public abstract class PointBlankGunItemMixin {

    @Inject(method = "handleClientHitScanFireRequest", at = @At("RETURN"), remap = false, require = 1)
    private void soundattract$onHitScanFire(ServerPlayer player,
                                            FireModeInstance fireModeInstance,
                                            UUID stateId,
                                            int slotIndex,
                                            int correlationId,
                                            boolean isAiming,
                                            boolean requestFlag,
                                            long requestSeed,
                                            CallbackInfo ci) {
        if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enablePointBlankIntegration.get()) return;
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[PBMixin] handleClientHitScanFireRequest hooked for {} slot {}", player, slotIndex);
        }
        PointBlankIntegration.onGunShoot(player, player.getInventory().getItem(slotIndex));
    }

    @Inject(method = "handleClientProjectileFireRequest", at = @At("RETURN"), remap = false, require = 1)
    private void soundattract$onProjectileFire(ServerPlayer player,
                                               FireModeInstance fireModeInstance,
                                               UUID stateId,
                                               int slotIndex,
                                               int correlationId,
                                               boolean isAiming,
                                               double spawnPositionX,
                                               double spawnPositionY,
                                               double spawnPositionZ,
                                               double spawnDirectionX,
                                               double spawnDirectionY,
                                               double spawnDirectionZ,
                                               int targetEntityId,
                                               long requestSeed,
                                               CallbackInfo ci) {
        if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enablePointBlankIntegration.get()) return;
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[PBMixin] handleClientProjectileFireRequest hooked for {} slot {}", player, slotIndex);
        }
        PointBlankIntegration.onGunShoot(player, player.getInventory().getItem(slotIndex));
    }

    @Inject(method = "handleClientReloadRequest", at = @At("RETURN"), remap = false, require = 1)
    private void soundattract$onReload(ServerPlayer player,
                                      ItemStack itemStack,
                                      UUID clientStateId,
                                      int slotIndex,
                                      FireModeInstance fireModeInstance,
                                      CallbackInfo ci) {
        if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enablePointBlankIntegration.get()) return;
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[PBMixin] handleClientReloadRequest hooked for {} with item {}", player, itemStack);
        }
        PointBlankIntegration.onGunReload(player, itemStack);
    }
}
