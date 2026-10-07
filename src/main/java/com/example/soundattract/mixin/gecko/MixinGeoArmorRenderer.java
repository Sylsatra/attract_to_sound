package com.example.soundattract.mixin.gecko;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;

import java.util.Set;

@Mixin(GeoArmorRenderer.class)
public abstract class MixinGeoArmorRenderer<T extends net.minecraft.world.item.Item & GeoAnimatable> implements GeoRenderer<T> {

    @Shadow(remap = false) public abstract ItemStack getCurrentStack();
    @Shadow(remap = false) public abstract Entity getCurrentEntity();
    @Shadow(remap = false) public abstract EquipmentSlot getCurrentSlot();

    private boolean isRenderingCamo = false;

    @Inject(method = "actuallyRender", at = @At("RETURN"), remap = false)
    public void onActuallyRenderReturn(PoseStack poseStack, T animatable, BakedGeoModel model, RenderType renderType,
                                       MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                                       int packedLight, int packedOverlay, float red, float green, float blue, float alpha, CallbackInfo ci) {


        if (isRenderingCamo) return;

        Entity entity = getCurrentEntity();
        if (!(entity instanceof LivingEntity living)) {
             return;
        }
        if (living.isInvisible() && com.example.soundattract.config.SoundAttractConfig.shouldHideWornGearWhileInvisible()) return;

        ItemStack stack = getCurrentStack();
        if (stack.isEmpty() || !stack.hasTag()) {
             return;
        }

        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (!tag.contains("soundattract:CamoLayers", 9)) {
             return;
        }

        net.minecraft.nbt.ListTag list = tag.getList("soundattract:CamoLayers", 10);
        com.example.soundattract.util.CamoUtil.BlendedCamoData blended = com.example.soundattract.util.CamoUtil.getBlendedDataFromTag(list);

        if (blended.color().isPresent()) {
            com.example.soundattract.camo.ArmorValidationTracker.markSlotValidated(living, getCurrentSlot());

            float strength = blended.strength() * 2.0f;
            if (strength > 0.05f) {
                int color = blended.color().get();

                com.example.soundattract.camo.CamoLODUtil.LODResult lod = com.example.soundattract.camo.CamoLODUtil.getLOD(living);
                ResourceLocation smudgeTex = com.example.soundattract.camo.CamoTextureGenerator.getOrCreateSmudge(
                    blended.seed(),
                    color,
                    strength,
                    lod.resolution(),
                    blended.erosion(),
                    blended.humidity(),
                    blended.temp()
                );

                if (smudgeTex != null) {
                    RenderType camoType = com.example.soundattract.camo.CamoRenderTypes.camoOverlay(smudgeTex);
                    VertexConsumer camoBuffer = bufferSource.getBuffer(camoType);

                    isRenderingCamo = true;
                    try {
                        this.updateAnimatedTextureFrame(animatable);

                        poseStack.pushPose();
                        poseStack.translate(0, 24 / 16f, 0);
                        poseStack.scale(-1, -1, 1);

                        java.util.List<software.bernie.geckolib.cache.object.GeoBone> bones = model.topLevelBones();

                        for (software.bernie.geckolib.cache.object.GeoBone group : bones) {
                            this.renderRecursively(poseStack, animatable, group, camoType, bufferSource, camoBuffer, true, partialTick, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
                        }

                        poseStack.popPose();
                    } finally {
                        isRenderingCamo = false;
                    }
                }
            }
        }
    }
}