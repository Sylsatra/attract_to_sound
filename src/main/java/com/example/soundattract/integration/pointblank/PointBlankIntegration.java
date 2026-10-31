package com.example.soundattract.integration.pointblank;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.network.SoundMessage;
import com.example.soundattract.tracking.SoundTracker;
import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import java.util.Optional;

public class PointBlankIntegration {

    private static boolean initialized = false;
    private static Class<?> attachmentsClass = null;
    private static Class<?> attachmentClass = null;
    private static Class<?> attachmentCategoryClass = null;
    private static java.lang.reflect.Method getAttachmentsRecursiveMethod = null;
    private static java.lang.reflect.Method getCategoryMethod = null;
    private static java.lang.reflect.Method getCategoryNameMethod = null;
    private static java.lang.reflect.Field muzzleCategoryField = null;

    private static void initializeReflection() {
        if (initialized) return;
        try {
            attachmentsClass = Class.forName("com.vicmatskiv.pointblank.attachment.Attachments");
            attachmentClass = Class.forName("com.vicmatskiv.pointblank.attachment.Attachment");
            attachmentCategoryClass = Class.forName("com.vicmatskiv.pointblank.attachment.AttachmentCategory");
            getAttachmentsRecursiveMethod = attachmentsClass.getMethod("getAttachments", ItemStack.class, boolean.class);
            getCategoryMethod = attachmentClass.getMethod("getCategory");
            getCategoryNameMethod = attachmentCategoryClass.getMethod("getName");
            muzzleCategoryField = attachmentCategoryClass.getField("MUZZLE");
            initialized = true;
        } catch (Exception e) {
            SoundAttractMod.LOGGER.warn("[PointBlankIntegration] Failed to initialize reflection: {}", e.getMessage());
        }
    }

    private static java.util.Collection<ItemStack> getAttachmentStacks(ItemStack gunStack) {
        try {
            if (getAttachmentsRecursiveMethod == null) return java.util.List.of();
            java.util.NavigableMap<?, ItemStack> map = (java.util.NavigableMap<?, ItemStack>) getAttachmentsRecursiveMethod.invoke(null, gunStack, true);
            if (map == null) return java.util.List.of();
            return map.values();
        } catch (Exception e) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.warn("[PointBlankIntegration] Error getting attachment stacks: {}", e.getMessage());
            }
            return java.util.List.of();
        }
    }

    private static String getCategoryName(Object category) {
        try {
            if (getCategoryNameMethod == null || category == null) return null;
            return (String) getCategoryNameMethod.invoke(category);
        } catch (Exception e) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.warn("[PointBlankIntegration] Error getting category name: {}", e.getMessage());
            }
            return null;
        }
    }

    public static void onGunShoot(ServerPlayer player, ItemStack gunStack) {
        if (!ModList.get().isLoaded("pointblank")) return;
        if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enablePointBlankIntegration.get()) return;

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[PointBlankIntegration] onGunShoot called for player: {}", player.getName().getString());
        }

        double flashRange = SoundAttractConfig.COMMON.gunshotBaseDetectionRange.get();
        double reduction = 0.0;

        try {
            initializeReflection();
            if (muzzleCategoryField != null) {
                Object muzzleCategory = muzzleCategoryField.get(null);
                String muzzleCategoryName = getCategoryName(muzzleCategory);

                java.util.Collection<ItemStack> attachmentStacks = getAttachmentStacks(gunStack);
                for (ItemStack attachmentStack : attachmentStacks) {
                    if (attachmentClass.isInstance(attachmentStack.getItem())) {
                        Object attachment = attachmentClass.cast(attachmentStack.getItem());
                        Object category = getCategoryMethod.invoke(attachment);
                        String categoryName = getCategoryName(category);
                        if (java.util.Objects.equals(categoryName, muzzleCategoryName)) {
                            Identifier muzzleId = BuiltInRegistries.ITEM.getKey(attachmentStack.getItem());
                            if (muzzleId != null) {
                                reduction += SoundAttractConfig.POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE.getOrDefault(muzzleId, 0.0);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.warn("[PointBlankIntegration] Error in onGunShoot: {}", e.getMessage());
            }
        }

        double finalDetectionRange = Math.max(0, flashRange - reduction);
        StealthDetectionEvents.recordPlayerGunshot(player, finalDetectionRange);

        double[] rangeAndWeight = calculateShootRangeWeight(gunStack);

        int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
        net.minecraft.core.BlockPos pos = player.blockPosition();
        String dimString = player.level().dimension().identifier().toString();
        String meta = player.getUUID() + "/shoot";
        String soundIdToUse = SoundTracker.buildIntegrationSoundId(SoundMessage.POINT_BLANK_SOUND_ID, meta);
        SoundTracker.addSound(null, pos, dimString, (int) rangeAndWeight[0], rangeAndWeight[1], lifetime, soundIdToUse);
    }

    public static void onGunReload(ServerPlayer player, ItemStack gunStack) {
        if (!ModList.get().isLoaded("pointblank")) return;
        if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enablePointBlankIntegration.get()) return;

        double[] rangeAndWeight = calculateReloadRangeWeight(gunStack);

        int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
        net.minecraft.core.BlockPos pos = player.blockPosition();
        String dimString = player.level().dimension().identifier().toString();
        String meta = player.getUUID() + "/reload";
        String soundIdToUse = SoundTracker.buildIntegrationSoundId(SoundMessage.POINT_BLANK_SOUND_ID, meta);
        SoundTracker.addSound(null, pos, dimString, (int) rangeAndWeight[0], rangeAndWeight[1], lifetime, soundIdToUse);
    }

    private static double[] calculateShootRangeWeight(ItemStack gunStack) {
        Identifier gunId = BuiltInRegistries.ITEM.getKey(gunStack.getItem());

        double finalRange = SoundAttractConfig.POINT_BLANK_GUN_RANGE_CACHE.getOrDefault(gunId, SoundAttractConfig.POINT_BLANK_SHOOT_RANGE_CACHE);

        double soundReduction = 0.0;
        try {
            initializeReflection();
            java.util.Collection<ItemStack> attachmentStacks = getAttachmentStacks(gunStack);
            for (ItemStack attachmentStack : attachmentStacks) {
                Identifier attachmentId = BuiltInRegistries.ITEM.getKey(attachmentStack.getItem());
                soundReduction += SoundAttractConfig.POINT_BLANK_ATTACHMENT_REDUCTION_CACHE.getOrDefault(attachmentId, SoundAttractConfig.POINT_BLANK_ATTACHMENT_REDUCTION_DEFAULT_CACHE);
            }
        } catch (Exception e) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.warn("[PointBlankIntegration] Error in calculateShootRangeWeight: {}", e.getMessage());
            }
        }
        finalRange = Math.max(0, finalRange - soundReduction);

        double weight = finalRange / 10.0;

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[PointBlankIntegration] Shoot: Gun='{}', BaseRange={}, Reduction={}, FinalRange={}, Weight={}",
                    gunId, String.format("%.2f", finalRange + soundReduction), String.format("%.2f", soundReduction), String.format("%.2f", finalRange), String.format("%.2f", weight));
        }

        return new double[]{finalRange, weight};
    }
    
    private static double[] calculateReloadRangeWeight(ItemStack gunStack) {
        double range = SoundAttractConfig.POINT_BLANK_RELOAD_RANGE_CACHE;
        double weight = SoundAttractConfig.POINT_BLANK_RELOAD_WEIGHT_CACHE;

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[PointBlankIntegration] Reload: range={}, weight={}", range, weight);
        }
        return new double[]{range, weight};
    }
}
