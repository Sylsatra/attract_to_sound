package com.example.soundattract.camo;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class CamoRenderLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public CamoRenderLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible) return;
        LivingEntity entity = CamoRenderStateEntities.get(state);
        if (entity == null) {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level != null && level.getEntity(state.id) instanceof LivingEntity living) entity = living;
        }
        if (!(entity instanceof Player player)) return;
        CamoLODUtil.LODResult lod = CamoLODUtil.getLOD(player);
        if (!lod.shouldRender()) return;
        CamouflageCapability camo = player.getData(CamoAttachments.CAMOUFLAGE);
        if (camo.getLayers().isEmpty()) return;
        var color = camo.getBlendedColor();
        if (color.isEmpty()) return;
        PlayerModel model = this.getParentModel();
        float upperStrength = (float) Math.min(1.0D, camo.getLayers().stream().mapToDouble(CamoLayer::upperDurability).sum());
        float lowerStrength = (float) Math.min(1.0D, camo.getLayers().stream().mapToDouble(CamoLayer::lowerDurability).sum());
        if (upperStrength > 0.0F) {
            Identifier texture = CamoTextureGenerator.getOrCreateSmudge(camo.getDisplaySeed(), color.get(), upperStrength,
                    lod.resolution(), camo.getDisplayErosion(), camo.getDisplayHumidity(), camo.getDisplayTemperature());
            if (texture != null) CamoModelOverlay.submitPlayerUpper(model, poseStack, submitNodeCollector, packedLight, texture);
        }
        if (lowerStrength > 0.0F) {
            Identifier texture = CamoTextureGenerator.getOrCreateSmudge(camo.getDisplaySeed(), color.get(), lowerStrength,
                    lod.resolution(), camo.getDisplayErosion(), camo.getDisplayHumidity(), camo.getDisplayTemperature());
            if (texture != null) CamoModelOverlay.submitPlayerLower(model, poseStack, submitNodeCollector, packedLight, texture);
        }
    }
}
