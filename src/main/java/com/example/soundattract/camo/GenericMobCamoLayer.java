package com.example.soundattract.camo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import com.example.soundattract.camo.CamoLODUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;

import java.util.Optional;

/**
 * Generic render layer for Humanoid-style mobs (Zombies, Skeletons, etc.)
 */
@Environment(EnvType.CLIENT)
public class GenericMobCamoLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    public GenericMobCamoLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        CamoLODUtil.LODResult lod = CamoLODUtil.getLOD(entity);
        if (!lod.shouldRender()) return;
        if (entity.isInvisible()) return;

        CamouflageCapability.getCapability(entity).ifPresent(camo -> {
            if (camo.getLayers().isEmpty()) return;

            long seed = camo.getDisplaySeed();
            Optional<Integer> colorOpt = camo.getBlendedColor();
            if (colorOpt.isEmpty()) return;
            int color = colorOpt.get();
            float erosion = camo.getDisplayErosion();
            float humidity = camo.getDisplayHumidity();
            float temp = camo.getDisplayTemperature();

            ResourceLocation entityTex = this.getTextureLocation(entity);
            M model = this.getParentModel();


            float upperStrength = 0;
            for (CamoLayer layer : camo.getLayers()) upperStrength += layer.upperDurability();
            upperStrength = Math.min(1.0f, upperStrength);

            if (upperStrength > 0) {
                ResourceLocation upperSmudge = CamoTextureGenerator.getOrCreateMaskedSmudge(seed, color, upperStrength, entityTex, lod.resolution(), erosion, humidity, temp);
                if (upperSmudge != null) {
                    VertexConsumer upperConsumer = buffer.getBuffer(CamoRenderTypes.camoOverlay(upperSmudge));
                    

                    boolean lLegVis = model.leftLeg != null && model.leftLeg.visible;
                    boolean rLegVis = model.rightLeg != null && model.rightLeg.visible;
                    
                    if (model.leftLeg != null) model.leftLeg.visible = false;
                    if (model.rightLeg != null) model.rightLeg.visible = false;
    
                    model.renderToBuffer(poseStack, upperConsumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
                    
                    if (model.leftLeg != null) model.leftLeg.visible = lLegVis;
                    if (model.rightLeg != null) model.rightLeg.visible = rLegVis;
                }
            }


            float lowerStrength = 0;
            for (CamoLayer layer : camo.getLayers()) lowerStrength += layer.lowerDurability();
            lowerStrength = Math.min(1.0f, lowerStrength);

            if (lowerStrength > 0) {
                ResourceLocation lowerSmudge = CamoTextureGenerator.getOrCreateMaskedSmudge(seed, color, lowerStrength, entityTex, lod.resolution(), erosion, humidity, temp);
                if (lowerSmudge != null) {
                    VertexConsumer lowerConsumer = buffer.getBuffer(CamoRenderTypes.camoOverlay(lowerSmudge));
    

                    boolean headVis = model.head != null && model.head.visible;
                    boolean hatVis = model.hat != null && model.hat.visible;
                    boolean bodyVis = model.body != null && model.body.visible;
                    boolean lArmVis = model.leftArm != null && model.leftArm.visible;
                    boolean rArmVis = model.rightArm != null && model.rightArm.visible;
    
                    if (model.head != null) model.head.visible = false;
                    if (model.hat != null) model.hat.visible = false;
                    if (model.body != null) model.body.visible = false;
                    if (model.leftArm != null) model.leftArm.visible = false;
                    if (model.rightArm != null) model.rightArm.visible = false;
    
                    model.renderToBuffer(poseStack, lowerConsumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
    
                    if (model.head != null) model.head.visible = headVis;
                    if (model.hat != null) model.hat.visible = hatVis;
                    if (model.body != null) model.body.visible = bodyVis;
                    if (model.leftArm != null) model.leftArm.visible = lArmVis;
                    if (model.rightArm != null) model.rightArm.visible = rArmVis;
                }
            }
        });
    }

    /**
     * Specialized layer for Illager-style mobs (Pillager, Evoker, etc.)
     */
    @Environment(EnvType.CLIENT)
    public static class Illager<T extends AbstractIllager> extends RenderLayer<T, IllagerModel<T>> {
        public Illager(RenderLayerParent<T, IllagerModel<T>> parent) { super(parent); }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            CamoLODUtil.LODResult lod = CamoLODUtil.getLOD(entity);
            if (!lod.shouldRender()) return;
            if (entity.isInvisible()) return;

            CamouflageCapability.getCapability(entity).ifPresent(camo -> {
                if (camo.getLayers().isEmpty()) return;
                
                int color = camo.getBlendedColor().orElse(0xFFFFFF);
                float strength = camo.getVisualCamoStrength();
                ResourceLocation tex = getOrCreateSmudge(camo, entity, lod.resolution());
                if (tex != null) {
                    VertexConsumer consumer = buffer.getBuffer(CamoRenderTypes.camoOverlay(tex));
                    this.getParentModel().renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
                }
            });
        }

        private ResourceLocation getOrCreateSmudge(CamouflageCapability camo, T entity, int resolution) {
            return CamoTextureGenerator.getOrCreateMaskedSmudge(camo.getDisplaySeed(), camo.getBlendedColor().orElse(0), camo.getVisualCamoStrength(), this.getTextureLocation(entity), resolution, camo.getDisplayErosion(), camo.getDisplayHumidity(), camo.getDisplayTemperature());
        }
    }

    /**
     * Specialized layer for Villagers and Wandering Traders
     */
    @Environment(EnvType.CLIENT)
    public static class VillagerLayer<T extends LivingEntity> extends RenderLayer<T, VillagerModel<T>> {
        public VillagerLayer(RenderLayerParent<T, VillagerModel<T>> parent) { super(parent); }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            CamoLODUtil.LODResult lod = CamoLODUtil.getLOD(entity);
            if (!lod.shouldRender()) return;
            if (entity.isInvisible()) return;

            CamouflageCapability.getCapability(entity).ifPresent(camo -> {
                if (camo.getLayers().isEmpty()) return;
                
                ResourceLocation tex = CamoTextureGenerator.getOrCreateMaskedSmudge(camo.getDisplaySeed(), camo.getBlendedColor().orElse(0), camo.getVisualCamoStrength(), this.getTextureLocation(entity), lod.resolution(), camo.getDisplayErosion(), camo.getDisplayHumidity(), camo.getDisplayTemperature());
                if (tex != null) {
                    VertexConsumer consumer = buffer.getBuffer(CamoRenderTypes.camoOverlay(tex));
                    this.getParentModel().renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
                }
            });
        }
    }
}

