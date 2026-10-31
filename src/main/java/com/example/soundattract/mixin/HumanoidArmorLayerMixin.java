package com.example.soundattract.mixin;

import com.example.soundattract.camo.ArmorValidationTracker;
import com.example.soundattract.camo.CamoLODUtil;
import com.example.soundattract.camo.CamoRenderTypes;
import com.example.soundattract.camo.CamoTextureGenerator;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.util.CamoUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin<S extends HumanoidRenderState, M extends HumanoidModel<S>, A extends HumanoidModel<S>> {
    @Shadow private A getArmorModel(S state, EquipmentSlot slot) { throw new AssertionError(); }

    private static Identifier soundattract$buildArmorTexture(ItemStack stack, EquipmentSlot slot, String suffix) {
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null) return null;
        String asset = equippable.assetId().map(key -> key.identifier().toString()).orElse("minecraft:leather");
        return soundattract$buildArmorTexture(asset, slot, suffix);
    }

    private static Identifier soundattract$buildArmorTexture(String materialName, EquipmentSlot slot, String suffix) {
        Identifier material = Identifier.tryParse(materialName);
        if (material == null) return null;
        int layer = slot == EquipmentSlot.LEGS ? 2 : 1;
        return material.withPath("textures/models/armor/" + material.getPath() + "_layer_" + layer
                + (suffix == null ? "" : "_" + suffix) + ".png");
    }

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void soundattract$hideInvisibleArmor(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                                                  S state, float limbSwing, float limbSwingAmount, CallbackInfo ci) {
        LivingEntity entity = com.example.soundattract.camo.CamoRenderStateEntities.get(state);
        if (entity != null && entity.isInvisible()
                && SoundAttractConfig.shouldHideWornGearWhileInvisible()) ci.cancel();
    }

    @Inject(method = "renderArmorPiece", at = @At("TAIL"))
    private void soundattract$overlayCamouflage(PoseStack poseStack, SubmitNodeCollector collector, ItemStack stack,
                                                 EquipmentSlot slot, int packedLight, S state, CallbackInfo ci) {
        LivingEntity entity = com.example.soundattract.camo.CamoRenderStateEntities.get(state);
        if (entity == null || entity.isInvisible() && SoundAttractConfig.shouldHideWornGearWhileInvisible()) return;
        CamoLODUtil.LODResult lod = CamoLODUtil.getLOD(entity);
        if (!lod.shouldRender() || stack.isEmpty() || !stack.has(DataComponents.CUSTOM_DATA)) return;
        CompoundTag tag = CamoUtil.getCustomTagReadOnly(stack);
        if (tag == null) return;
        A model = getArmorModel(state, slot);
        if (entity instanceof Player) ArmorValidationTracker.markSlotValidated(entity, slot);
        Identifier main = soundattract$buildArmorTexture(stack, slot, null);
        Identifier overlay = soundattract$buildArmorTexture(stack, slot, "overlay");
        java.util.List<Identifier> masks = new java.util.ArrayList<>(2);
        if (main != null) masks.add(main);
        if (overlay != null) masks.add(overlay);
        if (masks.isEmpty()) return;
        Identifier texture = null;
        if (tag.get("soundattract:CamoLayers") instanceof ListTag layers) {
            CamoUtil.BlendedCamoData blended = CamoUtil.getBlendedDataFromTag(layers);
            if (blended.color().isPresent()) {
                texture = CamoTextureGenerator.getOrCreateMaskedSmudge(blended.seed(), blended.color().get(),
                        blended.strength() * 2.0f, masks, lod.resolution(), blended.erosion(), blended.humidity(), blended.temp());
            }
        } else if (tag.contains("soundattract:CamoStrength")) {
            texture = CamoTextureGenerator.getOrCreateMaskedSmudge(tag.getLongOr("soundattract:CamoSeed", 0L),
                    tag.getIntOr("soundattract:CamoColor", 0), tag.getFloatOr("soundattract:CamoStrength", 0.0f),
                    masks, lod.resolution(), 0, 0, 0);
        }
        if (texture != null) {
            collector.submitModel(model, state, poseStack, CamoRenderTypes.camoOverlay(texture), packedLight,
                    OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, null);
        }
    }
}
