package com.example.soundattract.integration.tacz;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.tracking.SoundTracker;
import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.config.SoundAttractConfig;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.ModList;

public class TaczIntegration {

    private static final Identifier TACZ_SOUND_ID = Identifier.fromNamespaceAndPath("tacz", "gun");

    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        if (!ModList.get().isLoaded("tacz")) return;
        if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enableTaczIntegration.get()) return;

        if (event.getLogicalSide() != LogicalSide.SERVER) return;

        LivingEntity shooter = event.getShooter();
        if (!(shooter instanceof ServerPlayer player)) return;

        IGun iGun = IGun.getIGunOrNull(event.getGunItemStack());
        if (iGun != null) {
            double flashRange = SoundAttractConfig.COMMON.gunshotBaseDetectionRange.get();
            double reduction = 0.0;
            Identifier muzzleId = iGun.getAttachmentId(event.getGunItemStack(), AttachmentType.MUZZLE);
            if (muzzleId != null) {
                reduction = SoundAttractConfig.TACZ_MUZZLE_FLASH_REDUCTION_CACHE.getOrDefault(
                        muzzleId,
                        SoundAttractConfig.TACZ_ATTACHMENT_FLASH_REDUCTION_DEFAULT_CACHE
                );
            }
            double finalDetectionRange = Math.max(0, flashRange - reduction);
            StealthDetectionEvents.recordPlayerGunshot(player, finalDetectionRange);

            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                        "[TaczIntegration] Gunshot Flash: BaseRange={}, Muzzle='{}', Reduction={}, FinalRange={}",
                        String.format("%.2f", flashRange),
                        muzzleId != null ? muzzleId.toString() : "None",
                        String.format("%.2f", reduction),
                        String.format("%.2f", finalDetectionRange)
                );
            }
        }

        double[] rangeAndWeight = calculateShootRangeWeight(event.getGunItemStack());
        double range = rangeAndWeight[0];
        double weight = rangeAndWeight[1];

        String soundType = "shoot";

        int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
        net.minecraft.core.BlockPos pos = player.blockPosition();
        String dimString = player.level().dimension().identifier().toString();
        String meta = player.getUUID() + "/" + soundType;
        String soundIdToUse = SoundTracker.buildIntegrationSoundId(TACZ_SOUND_ID, meta);
        SoundTracker.addSound(null, pos, dimString, (int) range, weight, lifetime, soundIdToUse);
    }

    @SubscribeEvent
    public static void onGunReload(GunReloadEvent event) {
        if (!ModList.get().isLoaded("tacz")) return;
        if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enableTaczIntegration.get()) return;

        if (event.getLogicalSide() != LogicalSide.SERVER) return;

        LivingEntity reloader = event.getEntity();
        if (!(reloader instanceof ServerPlayer player)) return;

        double[] rangeAndWeight = calculateReloadRangeWeight(event.getGunItemStack());
        double range = rangeAndWeight[0];
        double weight = rangeAndWeight[1];

        String soundType = "reload";

        int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
        net.minecraft.core.BlockPos pos = player.blockPosition();
        String dimString = player.level().dimension().identifier().toString();
        String meta = player.getUUID() + "/" + soundType;
        String soundIdToUse = SoundTracker.buildIntegrationSoundId(TACZ_SOUND_ID, meta);
        SoundTracker.addSound(null, pos, dimString, (int) range, weight, lifetime, soundIdToUse);
    }

    private static double[] calculateShootRangeWeight(ItemStack gunStack) {
        IGun iGun = IGun.getIGunOrNull(gunStack);
        double finalRange = SoundAttractConfig.TACZ_SHOOT_RANGE_CACHE;

        if (iGun != null) {
            Identifier gunId = iGun.getGunId(gunStack);
            Identifier attId = iGun.getAttachmentId(gunStack, AttachmentType.MUZZLE);
            var gunStats = SoundAttractConfig.TACZ_GUN_SHOOT_DB_CACHE.get(gunId.toString());
            double gunRange = (gunStats != null) ? gunStats.getLeft() : SoundAttractConfig.TACZ_SHOOT_RANGE_CACHE;
            double reduction = 0.0;
            if (attId != null) {
                var attachmentStats = SoundAttractConfig.TACZ_ATTACHMENT_REDUCTION_DB_CACHE.get(attId.toString());
                if (attachmentStats != null) {
                    reduction = attachmentStats;
                }
            }
            finalRange = Math.max(0, gunRange - reduction);
        }
        double weight = finalRange / 10.0;
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[TaczIntegration] Shot: range={}, weight={}", String.format("%.2f", finalRange), String.format("%.2f", weight));
        }
        return new double[]{finalRange, weight};
    }

    private static double[] calculateReloadRangeWeight(ItemStack gunStack) {
        double range = SoundAttractConfig.TACZ_RELOAD_RANGE_CACHE;
        double weight = SoundAttractConfig.TACZ_RELOAD_WEIGHT_CACHE;
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[TaczIntegration] Reload: range={}, weight={}", String.format("%.2f", range), String.format("%.2f", weight));
        }
        return new double[]{range, weight};
    }
}
