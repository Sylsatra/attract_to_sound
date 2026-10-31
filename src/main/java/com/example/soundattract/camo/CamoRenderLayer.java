package com.example.soundattract.camo;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import java.util.Optional;

public class CamoRenderLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public CamoRenderLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) { super(parent); }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, AvatarRenderState state, float limbSwing, float limbSwingAmount) {
        var level = Minecraft.getInstance().level;
        if (level == null || state.isInvisible) return;
        LivingEntity renderedEntity = CamoRenderStateEntities.get(state);
        if (renderedEntity == null) renderedEntity = level.getEntity(state.id) instanceof LivingEntity living ? living : null;
        if (!(renderedEntity instanceof LivingEntity player)) return;
        CamoLODUtil.LODResult lod = CamoLODUtil.getLOD(player);
        if (!lod.shouldRender()) return;
        CamouflageCapability camo = player.getData(CamoAttachments.CAMOUFLAGE);
        if (camo != null) {
            if (camo.getLayers().isEmpty()) return;
            Optional<Integer> color = camo.getBlendedColor();
            if (color.isEmpty()) return;
            PlayerModel model = getParentModel();
            float upper = (float) Math.min(1.0, camo.getLayers().stream().mapToDouble(CamoLayer::upperDurability).sum());
            float lower = (float) Math.min(1.0, camo.getLayers().stream().mapToDouble(CamoLayer::lowerDurability).sum());
            if (upper > 0) {


                Identifier texture = CamoTextureGenerator.getOrCreateSmudge(camo.getDisplaySeed(), color.get(), upper, lod.resolution(), camo.getDisplayErosion(), camo.getDisplayHumidity(), camo.getDisplayTemperature());
                if (texture != null) {
                    CamoModelOverlay.submitPlayerUpper(model, poseStack, collector, packedLight, texture);
                }
            }
            if (lower > 0) {
                Identifier texture = CamoTextureGenerator.getOrCreateSmudge(camo.getDisplaySeed(), color.get(), lower, lod.resolution(), camo.getDisplayErosion(), camo.getDisplayHumidity(), camo.getDisplayTemperature());
                if (texture != null) {
                    CamoModelOverlay.submitPlayerLower(model, poseStack, collector, packedLight, texture);
                }
            }
        }
    }

}
