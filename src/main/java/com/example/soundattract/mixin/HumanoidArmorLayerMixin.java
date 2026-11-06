package com.example.soundattract.mixin;

import com.example.soundattract.camo.CamoLODUtil;
import com.example.soundattract.camo.CamoRenderStateEntities;
import com.example.soundattract.camo.CamoRenderTypes;
import com.example.soundattract.camo.CamoTextureGenerator;
import com.example.soundattract.camo.ArmorValidationTracker;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.util.CamoUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin<S extends HumanoidRenderState, M extends HumanoidModel<S>, A extends HumanoidModel<S>> {
    @Shadow private A getArmorModel(S state, EquipmentSlot slot) { throw new AssertionError(); }

    private static Identifier soundattract$buildArmorTexture(ItemStack stack, EquipmentSlot slot, String suffix) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
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
    private void soundattract$hideArmorWhenInvisible(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, S state, float yRot, float xRot, CallbackInfo ci) {
        if (!state.isInvisible || !SoundAttractConfig.shouldHideWornGearWhileInvisible()) {
            return;
        }

        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = switch (slot) {
                case HEAD -> state.headEquipment;
                case CHEST -> state.chestEquipment;
                case LEGS -> state.legsEquipment;
                case FEET -> state.feetEquipment;
                default -> ItemStack.EMPTY;
            };
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            if (equippable != null && equippable.slot() == slot) {
                ci.cancel();
                return;
            }
        }
    }

    @Inject(method = "renderArmorPiece", at = @At("TAIL"))
    private void soundattract$overlayCamouflage(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, ItemStack stack,
                                                 EquipmentSlot slot, int packedLight, S state, CallbackInfo ci) {
        LivingEntity entity = CamoRenderStateEntities.get(state);
        if (entity == null || entity.isInvisible() && SoundAttractConfig.shouldHideWornGearWhileInvisible()
                || stack == null || stack.isEmpty() || !CamoUtil.hasCustomTag(stack)) return;
        CamoLODUtil.LODResult lod = CamoLODUtil.getLOD(entity);
        if (!lod.shouldRender()) return;
        CompoundTag tag = CamoUtil.getCustomTag(stack);
        if (entity instanceof Player) ArmorValidationTracker.markSlotValidated(entity, slot);
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot() != slot || equippable.assetId().isEmpty()) return;
        Identifier main = soundattract$buildArmorTexture(stack, slot, null);
        Identifier overlay = soundattract$buildArmorTexture(stack, slot, "overlay");
        List<Identifier> masks = new ArrayList<>(2);
        if (main != null) masks.add(main);
        if (overlay != null) masks.add(overlay);
        if (masks.isEmpty()) return;
        Identifier texture = null;
        if (tag.contains("soundattract:CamoLayers")) {
            CamoUtil.BlendedCamoData blended = CamoUtil.getBlendedDataFromTag(tag.getListOrEmpty("soundattract:CamoLayers"));
            if (blended.color().isPresent()) {
                texture = CamoTextureGenerator.getOrCreateMaskedSmudge(blended.seed(), blended.color().get(),
                        blended.strength() * 2.0F, masks, lod.resolution(), blended.erosion(), blended.humidity(), blended.temp());
            }
        } else if (tag.contains("soundattract:CamoStrength")) {
            texture = CamoTextureGenerator.getOrCreateMaskedSmudge(tag.getLongOr("soundattract:CamoSeed", 0L),
                    tag.getIntOr("soundattract:CamoColor", 0), tag.getFloatOr("soundattract:CamoStrength", 0.0F),
                    masks, lod.resolution(), 0.0F, 0.0F, 0.0F);
        }
        if (texture != null) {
            A model = getArmorModel(state, slot);
            submitNodeCollector.submitModel(model, state, poseStack, CamoRenderTypes.camoOverlay(texture), packedLight,
                    OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, null);
        }
    }
}
