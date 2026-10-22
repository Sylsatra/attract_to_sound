package com.example.soundattract.mixin;

import com.example.soundattract.camo.CamouflageCapability;
import com.example.soundattract.camo.CamoTextureGenerator;
import com.example.soundattract.camo.CamoRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.world.entity.player.Player;
import com.example.soundattract.camo.CamoLODUtil;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final ThreadLocal<java.util.List<ResourceLocation>> s_CapturedTextures = ThreadLocal.withInitial(java.util.ArrayList::new);
    private static final ThreadLocal<Boolean> s_IsRecording = ThreadLocal.withInitial(() -> false);

    private static ResourceLocation soundattract$buildArmorTexture(ItemStack stack, EquipmentSlot slot, String suffix) {
        if (!(stack.getItem() instanceof ArmorItem armorItem)) return null;
        return soundattract$buildArmorTexture(armorItem.getMaterial().unwrapKey().map(key -> key.location().toString()).orElse("minecraft:leather"), slot, suffix);
    }

    private static ResourceLocation soundattract$buildArmorTexture(String materialName, EquipmentSlot slot, String suffix) {
        boolean secondLayer = slot == EquipmentSlot.LEGS;
        ResourceLocation material = ResourceLocation.tryParse(materialName);
        if (material == null) return null;
        String path = "textures/models/armor/" + material.getPath()
            + "_layer_" + (secondLayer ? 2 : 1)
            + (suffix == null ? "" : "_" + suffix) + ".png";
        return material.withPath(path);
    }

    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void soundattract$startRecording(PoseStack poseStack, MultiBufferSource buffer, LivingEntity entity, EquipmentSlot slot, int packedLight, HumanoidModel<?> armorModel, CallbackInfo ci) {
        if (entity.isInvisible() && !com.example.soundattract.client.FromTheFogVisualCompat.shouldKeepInvisibleEquipment(entity)
                && SoundAttractConfig.shouldHideWornGearWhileInvisible()) {
            ci.cancel();
            return;
        }
        s_CapturedTextures.get().clear();
        s_IsRecording.set(true);
    }

    @Inject(method = "renderArmorPiece", at = @At("TAIL"))
    private void soundattract$tintArmorWithCamo(
            PoseStack poseStack, MultiBufferSource buffer,
            LivingEntity entity, EquipmentSlot slot,
            int packedLight, HumanoidModel<?> armorModel,
            CallbackInfo ci) {

        if (!s_IsRecording.get()) return;
        try {
            ItemStack stack = entity.getItemBySlot(slot);
            ResourceLocation main = soundattract$buildArmorTexture(stack, slot, null);
            if (main != null) s_CapturedTextures.get().add(main);
            ResourceLocation overlay = soundattract$buildArmorTexture(stack, slot, "overlay");
            if (overlay != null) s_CapturedTextures.get().add(overlay);

            if (entity.isInvisible() && SoundAttractConfig.shouldHideWornGearWhileInvisible()) return;
        CamoLODUtil.LODResult lod = CamoLODUtil.getLOD(entity);
        if (!lod.shouldRender()) return;

        java.util.List<ResourceLocation> captured = new java.util.ArrayList<>(s_CapturedTextures.get());

        if (entity == Minecraft.getInstance().player && entity.level().getGameTime() % 200 == 0) {
            if (captured.isEmpty()) {
            } else {
                com.example.soundattract.camo.ArmorValidationTracker.markSlotValidated(entity, slot);
            }
        } else if (!captured.isEmpty()) {
             com.example.soundattract.camo.ArmorValidationTracker.markSlotValidated(entity, slot);
        }

        if (captured.isEmpty()) return;

        if (isGeckoAnimatable(stack)) {
            return;
        }

        if (stack.isEmpty() || !stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) return;

        net.minecraft.nbt.CompoundTag tag = com.example.soundattract.util.CamoUtil.getCustomTagReadOnly(stack);

            if (tag.contains("soundattract:CamoLayers", net.minecraft.nbt.Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag list = tag.getList("soundattract:CamoLayers", net.minecraft.nbt.Tag.TAG_COMPOUND);
            com.example.soundattract.util.CamoUtil.BlendedCamoData blended = com.example.soundattract.util.CamoUtil.getBlendedDataFromTag(list);

            if (blended.color().isPresent()) {
                ResourceLocation smudgeTex = com.example.soundattract.camo.CamoTextureGenerator.getOrCreateMaskedSmudge(
                    blended.seed(),
                    blended.color().get(),
                    blended.strength() * 2.0f,
                    captured,
                    lod.resolution(),
                    blended.erosion(),
                    blended.humidity(),
                    blended.temp()
                );

                if (smudgeTex != null) {
                    VertexConsumer vertexConsumer = buffer.getBuffer(CamoRenderTypes.camoOverlay(smudgeTex));
                    armorModel.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
                }
            }
            } else if (tag.contains("soundattract:CamoStrength")) {
            int color = tag.getInt("soundattract:CamoColor");
            float strength = tag.getFloat("soundattract:CamoStrength");
            long seed = tag.getLong("soundattract:CamoSeed");

            ResourceLocation smudgeTex = CamoTextureGenerator.getOrCreateMaskedSmudge(seed, color, strength, captured, lod.resolution(), 0, 0, 0);

            if (smudgeTex != null) {
                VertexConsumer vertexConsumer = buffer.getBuffer(CamoRenderTypes.camoOverlay(smudgeTex));
                armorModel.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            }
            }
        } finally {
            s_IsRecording.set(false);
            s_CapturedTextures.get().clear();
        }
    }

    private boolean isGeckoAnimatable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        try {
            if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("geckolib")) return false;
            Class<?> geoAnimatableClass = Class.forName("software.bernie.geckolib.animatable.GeoAnimatable");
            return geoAnimatableClass.isInstance(stack.getItem());
        } catch (Throwable e) {
            return false;
        }
    }
}

