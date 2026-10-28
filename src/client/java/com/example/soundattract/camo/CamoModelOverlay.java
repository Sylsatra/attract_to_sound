package com.example.soundattract.camo;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Submits camouflage per model part so deferred rendering never observes mutated visibility flags. */
public final class CamoModelOverlay {
    private CamoModelOverlay() {}

    public static void submitPlayerUpper(PlayerModel model, PoseStack poseStack, SubmitNodeCollector collector,
                                         int packedLight, Identifier texture) {
        submit(poseStack, collector, packedLight, texture,
                model.head, model.hat, model.body, model.jacket,
                model.leftArm, model.rightArm, model.leftSleeve, model.rightSleeve);
    }

    public static void submitPlayerLower(PlayerModel model, PoseStack poseStack, SubmitNodeCollector collector,
                                         int packedLight, Identifier texture) {
        submit(poseStack, collector, packedLight, texture,
                model.leftLeg, model.rightLeg, model.leftPants, model.rightPants);
    }

    public static void submitHumanoidUpper(HumanoidModel<?> model, PoseStack poseStack, SubmitNodeCollector collector,
                                           int packedLight, Identifier texture) {
        submit(poseStack, collector, packedLight, texture,
                model.head, model.hat, model.body, model.leftArm, model.rightArm);
    }

    public static void submitHumanoidLower(HumanoidModel<?> model, PoseStack poseStack, SubmitNodeCollector collector,
                                           int packedLight, Identifier texture) {
        submit(poseStack, collector, packedLight, texture, model.leftLeg, model.rightLeg);
    }

    private static void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                               Identifier texture, ModelPart... parts) {
        var renderType = CamoRenderTypes.camoOverlay(texture);
        for (ModelPart part : parts) {
            if (part.visible) {
                collector.submitModelPart(part, poseStack, renderType, packedLight, OverlayTexture.NO_OVERLAY, null);
            }
        }
    }
}
