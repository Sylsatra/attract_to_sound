package com.example.soundattract.camo;

import com.example.soundattract.config.separate.StealthConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import java.util.function.Function;

public class GenericMobCamoLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
    private final Function<S, Identifier> textureProvider;

    public GenericMobCamoLayer(RenderLayerParent<S, M> parent, Function<S, Identifier> textureProvider) {
        super(parent);
        this.textureProvider = textureProvider;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, S state, float yRot, float xRot) {
        if (state.isInvisible) return;
        LivingEntity entity = CamoRenderStateEntities.get(state);
        if (entity == null) return;
        CamoLODUtil.LODResult lod = CamoLODUtil.getLOD(entity);
        if (!lod.shouldRender()) return;
        CamouflageCapability camo = entity.getData(CamoAttachments.CAMOUFLAGE);
        if (camo.getLayers().isEmpty()) return;
        var color = camo.getBlendedColor();
        if (color.isEmpty()) return;
        Identifier entityTex = textureProvider.apply(state);
        if (entityTex == null) return;
        M model = this.getParentModel();
        float upperStrength = (float) Math.min(1.0D, camo.getLayers().stream().mapToDouble(CamoLayer::upperDurability).sum());
        float lowerStrength = (float) Math.min(1.0D, camo.getLayers().stream().mapToDouble(CamoLayer::lowerDurability).sum());
        if (upperStrength > 0.0F) {
            Identifier texture = CamoTextureGenerator.getOrCreateMaskedSmudge(camo.getDisplaySeed(), color.get(), upperStrength,
                    entityTex, lod.resolution(), camo.getDisplayErosion(), camo.getDisplayHumidity(), camo.getDisplayTemperature());
            if (texture != null) CamoModelOverlay.submitHumanoidUpper(model, poseStack, submitNodeCollector, packedLight, texture);
        }
        if (lowerStrength > 0.0F) {
            Identifier texture = CamoTextureGenerator.getOrCreateMaskedSmudge(camo.getDisplaySeed(), color.get(), lowerStrength,
                    entityTex, lod.resolution(), camo.getDisplayErosion(), camo.getDisplayHumidity(), camo.getDisplayTemperature());
            if (texture != null) CamoModelOverlay.submitHumanoidLower(model, poseStack, submitNodeCollector, packedLight, texture);
        }
    }

    public static class Illager extends RenderLayer<IllagerRenderState, IllagerModel<IllagerRenderState>> {
        private final Function<IllagerRenderState, Identifier> textureProvider;

        public Illager(RenderLayerParent<IllagerRenderState, IllagerModel<IllagerRenderState>> parent, Function<IllagerRenderState, Identifier> textureProvider) {
            super(parent);
            this.textureProvider = textureProvider;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, IllagerRenderState state, float yRot, float xRot) {
            if (state.isInvisible) return;
            LivingEntity entity = CamoRenderStateEntities.get(state);
            if (entity == null) return;
            CamoLODUtil.LODResult lod = CamoLODUtil.getLOD(entity);
            if (!lod.shouldRender()) return;
            CamouflageCapability camo = entity.getData(CamoAttachments.CAMOUFLAGE);
            if (camo.getLayers().isEmpty()) return;
            Identifier tex = getOrCreateSmudge(state, camo, lod.resolution());
            if (tex != null) {
                submitNodeCollector.submitModel(this.getParentModel(), state, poseStack,
                        CamoRenderTypes.camoOverlay(tex), packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, null);
            }
        }

        private Identifier getOrCreateSmudge(IllagerRenderState state, CamouflageCapability camo, int resolution) {
            Identifier entityTex = textureProvider.apply(state);
            if (entityTex == null) return null;
            return CamoTextureGenerator.getOrCreateMaskedSmudge(camo.getDisplaySeed(), camo.getBlendedColor().orElse(0),
                    camo.getVisualCamoStrength(), entityTex, resolution,
                    camo.getDisplayErosion(), camo.getDisplayHumidity(), camo.getDisplayTemperature());
        }
    }

    public static class VillagerLayer extends RenderLayer<VillagerRenderState, VillagerModel> {
        private final Function<VillagerRenderState, Identifier> textureProvider;

        public VillagerLayer(RenderLayerParent<VillagerRenderState, VillagerModel> parent, Function<VillagerRenderState, Identifier> textureProvider) {
            super(parent);
            this.textureProvider = textureProvider;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, VillagerRenderState state, float yRot, float xRot) {
            if (state.isInvisible) return;
            LivingEntity entity = CamoRenderStateEntities.get(state);
            if (entity == null) return;
            CamoLODUtil.LODResult lod = CamoLODUtil.getLOD(entity);
            if (!lod.shouldRender()) return;
            CamouflageCapability camo = entity.getData(CamoAttachments.CAMOUFLAGE);
            if (camo.getLayers().isEmpty()) return;
            Identifier entityTex = textureProvider.apply(state);
            if (entityTex == null) return;
            Identifier tex = CamoTextureGenerator.getOrCreateMaskedSmudge(camo.getDisplaySeed(), camo.getBlendedColor().orElse(0),
                    camo.getVisualCamoStrength(), entityTex, lod.resolution(), camo.getDisplayErosion(),
                    camo.getDisplayHumidity(), camo.getDisplayTemperature());
            if (tex != null) {
                submitNodeCollector.submitModel(this.getParentModel(), state, poseStack,
                        CamoRenderTypes.camoOverlay(tex), packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, null);
            }
        }
    }
}
