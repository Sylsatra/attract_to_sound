package com.example.soundattract.mixin;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.camo.CamoLODUtil;
import com.example.soundattract.camo.CamoRenderTypes;
import com.example.soundattract.camo.CamoTextureGenerator;
import com.example.soundattract.util.CamoUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void soundattract$hideArmorWhenInvisible(PoseStack poseStack, MultiBufferSource buffer, int packedLight, LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float tickDelta, CallbackInfo ci) {
        if (entity.isInvisible()
                && !com.example.soundattract.client.FromTheFogVisualCompat.shouldKeepInvisibleEquipment(entity)
                && SoundAttractConfig.shouldHideWornGearWhileInvisible()) {
            boolean hasArmor = false;
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack stack = entity.getItemBySlot(slot);
                if (stack.getItem() instanceof ArmorItem) {
                    hasArmor = true;
                    break;
                }
            }

            if (hasArmor) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void soundattract$renderArmorCamo(PoseStack poseStack, MultiBufferSource buffer, int packedLight, LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float tickDelta, CallbackInfo ci) {
        CamoLODUtil.LODResult lod = CamoLODUtil.getLOD(entity);
        if (!lod.shouldRender()) {
            return;
        }
        if (entity.isInvisible()) {
            return;
        }

        HumanoidArmorLayer<?, ?, ?> self = (HumanoidArmorLayer<?, ?, ?>) (Object) this;

        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;

            if (!(stack.getItem() instanceof ArmorItem armorItem)) {
                continue;
            }

            if (armorItem.getEquipmentSlot() != slot) {
                continue;
            }

            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (tag == null || tag.isEmpty()) continue;

            List<ResourceLocation> armorTextures = getArmorTextures(stack, slot);
            if (armorTextures.isEmpty()) continue;

            if (tag.contains("soundattract:CamoLayers", Tag.TAG_LIST)) {
                ListTag list = tag.getList("soundattract:CamoLayers", Tag.TAG_COMPOUND);
                CamoUtil.BlendedCamoData blended = CamoUtil.getBlendedDataFromTag(list);

                if (blended.color().isPresent()) {
                    ResourceLocation smudgeTex = CamoTextureGenerator.getOrCreateMaskedSmudge(
                        blended.seed(),
                        blended.color().get(),
                        blended.strength() * 2.0f,
                        armorTextures,
                        lod.resolution(),
                        blended.erosion(),
                        blended.humidity(),
                        blended.temp()
                    );

                    if (smudgeTex != null) {
                        HumanoidModel<?> armorModel = getArmorModel(self, slot);
                        if (armorModel == null) continue;

                        try {
                            java.lang.reflect.Method copyMethod = net.minecraft.client.model.EntityModel.class.getDeclaredMethod("copyPropertiesTo", net.minecraft.client.model.EntityModel.class);
                            copyMethod.setAccessible(true);
                            copyMethod.invoke(self.getParentModel(), armorModel);
                        } catch (Exception e) {
                            continue;
                        }

                        setCamoPartVisibility(armorModel, slot);

                        VertexConsumer vertexConsumer = buffer.getBuffer(CamoRenderTypes.camoOverlay(smudgeTex));
                        armorModel.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);

                        armorModel.setAllVisible(true);
                    }
                }
            } else if (tag.contains("soundattract:CamoStrength")) {
                int color = tag.getInt("soundattract:CamoColor");
                float strength = tag.getFloat("soundattract:CamoStrength");
                long seed = tag.getLong("soundattract:CamoSeed");

                ResourceLocation smudgeTex = CamoTextureGenerator.getOrCreateMaskedSmudge(seed, color, strength * 2.0f, armorTextures, lod.resolution(), 0, 0, 0);

                if (smudgeTex != null) {
                    HumanoidModel<?> armorModel = getArmorModel(self, slot);
                    if (armorModel == null) continue;

                    try {
                        java.lang.reflect.Method copyMethod = net.minecraft.client.model.EntityModel.class.getDeclaredMethod("copyPropertiesTo", net.minecraft.client.model.EntityModel.class);
                        copyMethod.setAccessible(true);
                        copyMethod.invoke(self.getParentModel(), armorModel);
                    } catch (Exception e) {
                        continue;
                    }

                    setCamoPartVisibility(armorModel, slot);

                    VertexConsumer vertexConsumer = buffer.getBuffer(CamoRenderTypes.camoOverlay(smudgeTex));
                    armorModel.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);

                    armorModel.setAllVisible(true);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private HumanoidModel<?> getArmorModel(HumanoidArmorLayer<?, ?, ?> layer, EquipmentSlot slot) {
        try {
            java.lang.reflect.Field innerField = HumanoidArmorLayer.class.getDeclaredField("innerModel");
            innerField.setAccessible(true);
            java.lang.reflect.Field outerField = HumanoidArmorLayer.class.getDeclaredField("outerModel");
            outerField.setAccessible(true);

            boolean usesInnerModel = slot == EquipmentSlot.LEGS;
            return usesInnerModel ? (HumanoidModel<?>) innerField.get(layer) : (HumanoidModel<?>) outerField.get(layer);
        } catch (Exception e) {
            return null;
        }
    }

    private List<ResourceLocation> getArmorTextures(ItemStack stack, EquipmentSlot slot) {
        List<ResourceLocation> textures = new java.util.ArrayList<>();

        if (!(stack.getItem() instanceof ArmorItem armorItem)) {
            return textures;
        }

        if (armorItem.getEquipmentSlot() != slot) {
            return textures;
        }

        ArmorMaterial material = armorItem.getMaterial().value();
        boolean usesInnerModel = slot == EquipmentSlot.LEGS;

        for (ArmorMaterial.Layer layer : material.layers()) {
            ResourceLocation texture = layer.texture(usesInnerModel);
            if (texture != null) {
                textures.add(texture);
            }
        }

        return textures;
    }

    private void setCamoPartVisibility(HumanoidModel<?> model, EquipmentSlot slot) {
        model.setAllVisible(false);
        switch (slot) {
            case HEAD:
                model.head.visible = true;
                model.hat.visible = true;
                break;
            case CHEST:
                model.body.visible = true;
                model.rightArm.visible = true;
                model.leftArm.visible = true;
                break;
            case LEGS:
                model.body.visible = true;
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
                break;
            case FEET:
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
                break;
        }
    }
}
