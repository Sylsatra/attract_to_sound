package com.example.soundattract.integration.pointblank;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.network.SoundMessage;
import com.example.soundattract.tracking.SoundTracker;
import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.config.SoundAttractConfig;
import com.vicmatskiv.pointblank.attachment.Attachment;
import com.vicmatskiv.pointblank.attachment.AttachmentCategory;
import com.vicmatskiv.pointblank.attachment.Attachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Optional;

public class PointBlankIntegration {

    public static void onGunShoot(ServerPlayer player, ItemStack gunStack) {
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.POINT_BLANK)) {
            SoundAttractMod.LOGGER.info("[PointBlankIntegration] onGunShoot called for player: {}", player.getName().getString());
        }

        double flashRange = SoundAttractConfig.COMMON.gunshotBaseDetectionRange.get();
        double reduction = 0.0;

        for (ItemStack attachmentStack : Attachments.getAttachments(gunStack)) {
            if (attachmentStack.getItem() instanceof Attachment attachment && attachment.getCategory() == AttachmentCategory.MUZZLE) {
                ResourceLocation muzzleId = ForgeRegistries.ITEMS.getKey(attachmentStack.getItem());
                if (muzzleId != null) {
                    reduction += SoundAttractConfig.POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE.getOrDefault(muzzleId, 0.0);
                }
            }
        }
        double finalDetectionRange = Math.max(0, flashRange - reduction);
        StealthDetectionEvents.recordPlayerGunshot(player, finalDetectionRange);

        double[] rangeAndWeight = calculateShootRangeWeight(gunStack);

        int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
        net.minecraft.core.BlockPos pos = player.blockPosition();
        String dimString = player.level().dimension().location().toString();
        String meta = player.getUUID() + "/shoot";
        String soundIdToUse = SoundTracker.buildIntegrationSoundId(SoundMessage.POINT_BLANK_SOUND_ID, meta);
        SoundTracker.addSound(null, pos, dimString, (int) rangeAndWeight[0], rangeAndWeight[1], lifetime, soundIdToUse);
    }

    public static void onGunReload(ServerPlayer player, ItemStack gunStack) {

        double[] rangeAndWeight = calculateReloadRangeWeight(gunStack);

        int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
        net.minecraft.core.BlockPos pos = player.blockPosition();
        String dimString = player.level().dimension().location().toString();
        String meta = player.getUUID() + "/reload";
        String soundIdToUse = SoundTracker.buildIntegrationSoundId(SoundMessage.POINT_BLANK_SOUND_ID, meta);
        SoundTracker.addSound(null, pos, dimString, (int) rangeAndWeight[0], rangeAndWeight[1], lifetime, soundIdToUse);
    }

    private static double[] calculateShootRangeWeight(ItemStack gunStack) {
        ResourceLocation gunId = ForgeRegistries.ITEMS.getKey(gunStack.getItem());

        double finalRange = SoundAttractConfig.POINT_BLANK_GUN_RANGE_CACHE.getOrDefault(gunId, SoundAttractConfig.POINT_BLANK_SHOOT_RANGE_CACHE);

        double soundReduction = 0.0;
        for (ItemStack attachmentStack : Attachments.getAttachments(gunStack)) {
            ResourceLocation attachmentId = ForgeRegistries.ITEMS.getKey(attachmentStack.getItem());
            soundReduction += SoundAttractConfig.POINT_BLANK_ATTACHMENT_REDUCTION_CACHE.getOrDefault(attachmentId, SoundAttractConfig.POINT_BLANK_ATTACHMENT_REDUCTION_DEFAULT_CACHE);
        }
        finalRange = Math.max(0, finalRange - soundReduction);

        double weight = finalRange / 10.0;

        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.POINT_BLANK)) {
            SoundAttractMod.LOGGER.info("[PointBlankIntegration] Shoot: Gun='{}', BaseRange={}, Reduction={}, FinalRange={}, Weight={}",
                    gunId, String.format("%.2f", finalRange + soundReduction), String.format("%.2f", soundReduction), String.format("%.2f", finalRange), String.format("%.2f", weight));
        }

        return new double[]{finalRange, weight};
    }

    private static double[] calculateReloadRangeWeight(ItemStack gunStack) {
        double range = SoundAttractConfig.POINT_BLANK_RELOAD_RANGE_CACHE;
        double weight = SoundAttractConfig.POINT_BLANK_RELOAD_WEIGHT_CACHE;

        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.POINT_BLANK)) {
            SoundAttractMod.LOGGER.info("[PointBlankIntegration] Reload: range={}, weight={}", range, weight);
        }
        return new double[]{range, weight};
    }
}