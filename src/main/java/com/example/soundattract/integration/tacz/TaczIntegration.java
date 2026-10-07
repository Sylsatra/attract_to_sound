package com.example.soundattract.integration.tacz;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.tracking.SoundTracker;
import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.config.SoundAttractConfig;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import org.apache.commons.lang3.tuple.Pair;
import java.util.Optional;

public class TaczIntegration {
    private static final ResourceLocation TACZ_SOUND_ID = ResourceLocation.fromNamespaceAndPath("tacz", "gun");

    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) {
            return;
        }
        if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enableTaczIntegration.get()) {
            return;
        }

        LivingEntity shooter = event.getShooter();
        if (!(shooter instanceof Player player)) {
            return;
        }
        IGun iGun = IGun.getIGunOrNull(event.getGunItemStack());
        if (iGun != null) {
            double flashRange = SoundAttractConfig.COMMON.gunshotBaseDetectionRange.get();
            double reduction = 0.0;
            ResourceLocation muzzleId = iGun.getAttachmentId(event.getGunItemStack(), AttachmentType.MUZZLE);
            if (muzzleId != null) {
                reduction = SoundAttractConfig.TACZ_MUZZLE_FLASH_REDUCTION_CACHE.getOrDefault(
                        muzzleId,
                        SoundAttractConfig.TACZ_ATTACHMENT_FLASH_REDUCTION_DEFAULT_CACHE
                );
            }
            double finalDetectionRange = Math.max(0, flashRange - reduction);
            StealthDetectionEvents.recordPlayerGunshot(player, finalDetectionRange);
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.TACZ)) {
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
        String dimString = player.level().dimension().location().toString();
        String meta = player.getUUID() + "/" + soundType;
        String soundIdToUse = SoundTracker.buildIntegrationSoundId(TACZ_SOUND_ID, meta);
        SoundTracker.addSound(null, pos, dimString, (int) range, weight, lifetime, soundIdToUse);
    }

    @SubscribeEvent
    public static void onGunReload(GunReloadEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) {
            return;
        }
        if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enableTaczIntegration.get()) {
            return;
        }

        LivingEntity reloader = event.getEntity();
        if (!(reloader instanceof Player player)) {
            return;
        }

        double[] rangeAndWeight = calculateReloadRangeWeight(event.getGunItemStack());
        double range = rangeAndWeight[0];
        double weight = rangeAndWeight[1];

        String soundType = "reload";

        int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
        net.minecraft.core.BlockPos pos = player.blockPosition();
        String dimString = player.level().dimension().location().toString();
        String meta = player.getUUID() + "/" + soundType;
        String soundIdToUse = SoundTracker.buildIntegrationSoundId(TACZ_SOUND_ID, meta);
        SoundTracker.addSound(null, pos, dimString, (int) range, weight, lifetime, soundIdToUse);
    }

    private static double[] calculateShootRangeWeight(ItemStack gunStack) {
        IGun iGun = IGun.getIGunOrNull(gunStack);

        double finalRange = SoundAttractConfig.TACZ_SHOOT_RANGE_CACHE;

        if (iGun != null) {
            ResourceLocation gunId = iGun.getGunId(gunStack);
            ResourceLocation attId = iGun.getAttachmentId(gunStack, AttachmentType.MUZZLE);

            Pair<Double, Double> gunStats = SoundAttractConfig.TACZ_GUN_SHOOT_DB_CACHE.get(gunId);

            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.TACZ)) {
                SoundAttractMod.LOGGER.info("[TaczIntegration][DEBUG] gunId={}, statsInCache={}", gunId, gunStats);
            }

            double gunRange;
            if (gunStats != null) {
                gunRange = gunStats.getLeft();
            } else {
                gunRange = SoundAttractConfig.TACZ_SHOOT_RANGE_CACHE;
            }

            double reduction = 0.0;
            if (attId != null) {
                String attKey = attId.toString();
                Double configured = SoundAttractConfig.TACZ_ATTACHMENT_REDUCTION_DB_CACHE.get(attKey);
                if (configured != null) {
                    reduction = configured;
                }
            }

            finalRange = Math.max(0, gunRange - reduction);
        }

        double weight = finalRange / 10.0;

        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.TACZ)) {
            SoundAttractMod.LOGGER.info("[TaczIntegration] Shot: range={}, weight={}", finalRange, weight);
        }

        return new double[]{finalRange, weight};
    }

    private static double[] calculateReloadRangeWeight(ItemStack gunStack) {
        double range = SoundAttractConfig.TACZ_RELOAD_RANGE_CACHE;
        double weight = SoundAttractConfig.TACZ_RELOAD_WEIGHT_CACHE;

        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.TACZ)) {
            SoundAttractMod.LOGGER.info("[TaczIntegration] Reload: range={}, weight={}", range, weight);
        }

        return new double[]{range, weight};
    }
}