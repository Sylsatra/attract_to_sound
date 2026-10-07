package com.example.soundattract.camo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public class CamoRenderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public CamoRenderLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        CamoLODUtil.LODResult lod = CamoLODUtil.getLOD(player);
        if (!lod.shouldRender()) return;
        if (player.isInvisible()) return;

        player.getCapability(CamouflageCapability.INSTANCE).ifPresent(camo -> {
            if (camo.getLayers().isEmpty()) return;

            long seed = camo.getDisplaySeed();
            Optional<Integer> colorOpt = camo.getBlendedColor();
            if (colorOpt.isEmpty()) return;
            int color = colorOpt.get();
            float erosion = camo.getDisplayErosion();
            float humidity = camo.getDisplayHumidity();
            float temp = camo.getDisplayTemperature();

            ResourceLocation skinTex = player.getSkinTextureLocation();
            PlayerModel<AbstractClientPlayer> model = this.getParentModel();

            float upperStrength = 0;
            for (CamoLayer layer : camo.getLayers()) {
                upperStrength += layer.upperDurability();
            }
            upperStrength = Math.min(1.0f, upperStrength);

            if (upperStrength > 0) {
                ResourceLocation upperSmudge = CamoTextureGenerator.getOrCreateMaskedSmudge(seed, color, upperStrength, skinTex, lod.resolution(), erosion, humidity, temp);
                if (upperSmudge != null) {
                    VertexConsumer upperConsumer = buffer.getBuffer(CamoRenderTypes.camoOverlay(upperSmudge));

                    boolean lLegVis = model.leftLeg.visible;
                    boolean rLegVis = model.rightLeg.visible;
                    boolean lPantsVis = model.leftPants.visible;
                    boolean rPantsVis = model.rightPants.visible;

                    model.leftLeg.visible = false;
                    model.rightLeg.visible = false;
                    model.leftPants.visible = false;
                    model.rightPants.visible = false;

                    model.renderToBuffer(poseStack, upperConsumer, packedLight, OverlayTexture.pack(0, 10), 1.0f, 1.0f, 1.0f, 1.0f);

                    model.leftLeg.visible = lLegVis;
                    model.rightLeg.visible = rLegVis;
                    model.leftPants.visible = lPantsVis;
                    model.rightPants.visible = rPantsVis;
                }
            }

            float lowerStrength = 0;
            for (CamoLayer layer : camo.getLayers()) {
                lowerStrength += layer.lowerDurability();
            }
            lowerStrength = Math.min(1.0f, lowerStrength);

            if (lowerStrength > 0) {
                ResourceLocation lowerSmudge = CamoTextureGenerator.getOrCreateMaskedSmudge(seed, color, lowerStrength, skinTex, lod.resolution(), erosion, humidity, temp);
                if (lowerSmudge != null) {
                    VertexConsumer lowerConsumer = buffer.getBuffer(CamoRenderTypes.camoOverlay(lowerSmudge));


                    boolean headVisible = model.head.visible;
                    boolean hatVisible = model.hat.visible;
                    boolean bodyVisible = model.body.visible;
                    boolean jacketVisible = model.jacket.visible;
                    boolean leftArmVisible = model.leftArm.visible;
                    boolean rightArmVisible = model.rightArm.visible;
                    boolean leftSleeveVisible = model.leftSleeve.visible;
                    boolean rightSleeveVisible = model.rightSleeve.visible;

                    model.head.visible = false;
                    model.hat.visible = false;
                    model.body.visible = false;
                    model.jacket.visible = false;
                    model.leftArm.visible = false;
                    model.rightArm.visible = false;
                    model.leftSleeve.visible = false;
                    model.rightSleeve.visible = false;

                    model.renderToBuffer(poseStack, lowerConsumer, packedLight, OverlayTexture.pack(0, 10), 1.0f, 1.0f, 1.0f, 1.0f);


                    model.head.visible = headVisible;
                    model.hat.visible = hatVisible;
                    model.body.visible = bodyVisible;
                    model.jacket.visible = jacketVisible;
                    model.leftArm.visible = leftArmVisible;
                    model.rightArm.visible = rightArmVisible;
                    model.leftSleeve.visible = leftSleeveVisible;
                    model.rightSleeve.visible = rightSleeveVisible;
                }
            }
        });
    }
}