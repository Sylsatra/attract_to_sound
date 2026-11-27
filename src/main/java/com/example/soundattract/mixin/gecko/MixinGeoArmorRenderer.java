package com.example.soundattract.mixin.gecko;

import com.example.soundattract.camo.ArmorValidationTracker;
import com.example.soundattract.camo.CamoLODUtil;
import com.example.soundattract.camo.CamoRenderTypes;
import com.example.soundattract.camo.CamoTextureGenerator;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.util.CamoUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;

@Mixin(GeoArmorRenderer.class)
public abstract class MixinGeoArmorRenderer<T extends Item & GeoItem> implements GeoRenderer<T> {

    @Shadow(remap = false) public abstract ItemStack getCurrentStack();
    @Shadow(remap = false) public abstract Entity getCurrentEntity();
    @Shadow(remap = false) public abstract EquipmentSlot getCurrentSlot();

    private boolean soundattract$renderingCamo;

    @Inject(method = "actuallyRender", at = @At("RETURN"), remap = false)
    private void soundattract$renderCamoOverlay(PoseStack poseStack,
                                                T animatable,
                                                BakedGeoModel model,
                                                RenderType renderType,
                                                MultiBufferSource bufferSource,
                                                VertexConsumer buffer,
                                                boolean isReRender,
                                                float partialTick,
                                                int packedLight,
                                                int packedOverlay,
                                                int renderColor,
                                                CallbackInfo ci) {
        if (soundattract$renderingCamo) return;

        Entity entity = getCurrentEntity();
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        if (living.isInvisible()
                && !com.example.soundattract.client.FromTheFogVisualCompat.shouldKeepInvisibleEquipment(living)
                && SoundAttractConfig.shouldHideWornGearWhileInvisible()) return;

        ItemStack stack = getCurrentStack();
        if (stack.isEmpty() || !CamoUtil.hasCustomTag(stack)) {
            return;
        }

        CompoundTag tag = CamoUtil.getCustomTag(stack);
        if (!tag.contains("soundattract:CamoLayers", 9)) {
            return;
        }

        ListTag list = tag.getList("soundattract:CamoLayers", 10);
        CamoUtil.BlendedCamoData blended = CamoUtil.getBlendedDataFromTag(list);
        if (blended.color().isEmpty()) {
            return;
        }

        ArmorValidationTracker.markSlotValidated(living, getCurrentSlot());
        float strength = blended.strength() * 2.0f;
        if (strength <= 0.05f) {
            return;
        }

        CamoLODUtil.LODResult lod = CamoLODUtil.getLOD(living);
        if (!lod.shouldRender()) {
            return;
        }

        ResourceLocation smudgeTex = CamoTextureGenerator.getOrCreateSmudge(
                blended.seed(),
                blended.color().get(),
                strength,
                lod.resolution(),
                blended.erosion(),
                blended.humidity(),
                blended.temp());
        if (smudgeTex == null) {
            return;
        }

        RenderType camoType = CamoRenderTypes.camoOverlay(smudgeTex);
        VertexConsumer camoBuffer = bufferSource.getBuffer(camoType);

        soundattract$renderingCamo = true;
        try {
            this.updateAnimatedTextureFrame(animatable);

            poseStack.pushPose();
            poseStack.translate(0, 24 / 16f, 0);
            poseStack.scale(-1, -1, 1);

            for (software.bernie.geckolib.cache.object.GeoBone group : model.topLevelBones()) {
                this.renderRecursively(poseStack, animatable, group, camoType, bufferSource, camoBuffer, true, partialTick, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            }

            poseStack.popPose();
        } finally {
            soundattract$renderingCamo = false;
        }
    }
}
