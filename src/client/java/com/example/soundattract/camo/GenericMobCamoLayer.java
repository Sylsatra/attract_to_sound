package com.example.soundattract.camo;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class GenericMobCamoLayer<S extends net.minecraft.client.renderer.entity.state.HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
    public GenericMobCamoLayer(RenderLayerParent<S, M> parent) { super(parent); }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, S state, float limbSwing, float limbSwingAmount) {
        LivingEntity entity = CamoRenderStateEntities.get(state);
        if (entity == null || state.isInvisible) return;
        CamoLODUtil.LODResult lod = CamoLODUtil.getLOD(entity);
        if (!lod.shouldRender()) return;
        CamouflageCapability.getCapability(entity).ifPresent(camo -> {
            if (camo.getLayers().isEmpty()) return;
            int color = camo.getBlendedColor().orElse(0);
            Identifier base = ((net.minecraft.client.renderer.entity.LivingEntityRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity)).getTextureLocation(state);
            M model = getParentModel();
            float upper = (float) Math.min(1.0, camo.getLayers().stream().mapToDouble(CamoLayer::upperDurability).sum());
            float lower = (float) Math.min(1.0, camo.getLayers().stream().mapToDouble(CamoLayer::lowerDurability).sum());
            if (upper > 0) {
                Identifier texture = CamoTextureGenerator.getOrCreateMaskedSmudge(camo.getDisplaySeed(), color, upper, base, lod.resolution(), camo.getDisplayErosion(), camo.getDisplayHumidity(), camo.getDisplayTemperature());
                if (texture != null) CamoModelOverlay.submitHumanoidUpper(model, poseStack, collector, packedLight, texture);
            }
            if (lower > 0) {
                Identifier texture = CamoTextureGenerator.getOrCreateMaskedSmudge(camo.getDisplaySeed(), color, lower, base, lod.resolution(), camo.getDisplayErosion(), camo.getDisplayHumidity(), camo.getDisplayTemperature());
                if (texture != null) CamoModelOverlay.submitHumanoidLower(model, poseStack, collector, packedLight, texture);
            }
        });
    }

    @Environment(EnvType.CLIENT)
    public static class Illager extends RenderLayer<IllagerRenderState, IllagerModel<IllagerRenderState>> {
        public Illager(RenderLayerParent<IllagerRenderState, IllagerModel<IllagerRenderState>> parent) { super(parent); }
        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, IllagerRenderState state, float limbSwing, float limbSwingAmount) {
            submitSimple(this, poseStack, collector, packedLight, state);
        }
    }

    @Environment(EnvType.CLIENT)
    public static class VillagerLayer extends RenderLayer<VillagerRenderState, VillagerModel> {
        public VillagerLayer(RenderLayerParent<VillagerRenderState, VillagerModel> parent) { super(parent); }
        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, VillagerRenderState state, float limbSwing, float limbSwingAmount) {
            submitSimple(this, poseStack, collector, packedLight, state);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void submitSimple(RenderLayer layer, PoseStack poseStack, SubmitNodeCollector collector, int packedLight, LivingEntityRenderState state) {
        LivingEntity entity = CamoRenderStateEntities.get(state);
        if (entity == null || state.isInvisible) return;
        CamoLODUtil.LODResult lod = CamoLODUtil.getLOD(entity);
        if (!lod.shouldRender()) return;
        CamouflageCapability.getCapability(entity).ifPresent(camo -> {
            if (camo.getLayers().isEmpty()) return;
            Identifier base = ((net.minecraft.client.renderer.entity.LivingEntityRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity)).getTextureLocation(state);
            Identifier texture = CamoTextureGenerator.getOrCreateMaskedSmudge(camo.getDisplaySeed(), camo.getBlendedColor().orElse(0), camo.getVisualCamoStrength(), base, lod.resolution(), camo.getDisplayErosion(), camo.getDisplayHumidity(), camo.getDisplayTemperature());
            if (texture != null) collector.submitModel(layer.getParentModel(), state, poseStack, CamoRenderTypes.camoOverlay(texture), packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, null);
        });
    }
}
