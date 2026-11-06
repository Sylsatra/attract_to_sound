package com.example.soundattract.camo;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;


public final class CamoModelOverlay {
    private CamoModelOverlay() {}

    public static void submitPlayerUpper(PlayerModel model, PoseStack poseStack, SubmitNodeCollector collector,
                                         int packedLight, Identifier texture) {
        submitRootChildren(model.root(), poseStack, collector, packedLight, texture,
                model.head, model.body, model.leftArm, model.rightArm);
        submitGrandchild(model.root(), model.head, poseStack, collector, packedLight, texture, model.hat);
        submitGrandchild(model.root(), model.body, poseStack, collector, packedLight, texture, model.jacket);
        submitGrandchild(model.root(), model.leftArm, poseStack, collector, packedLight, texture, model.leftSleeve);
        submitGrandchild(model.root(), model.rightArm, poseStack, collector, packedLight, texture, model.rightSleeve);
    }

    public static void submitPlayerLower(PlayerModel model, PoseStack poseStack, SubmitNodeCollector collector,
                                         int packedLight, Identifier texture) {
        submitRootChildren(model.root(), poseStack, collector, packedLight, texture, model.leftLeg, model.rightLeg);
        submitGrandchild(model.root(), model.leftLeg, poseStack, collector, packedLight, texture, model.leftPants);
        submitGrandchild(model.root(), model.rightLeg, poseStack, collector, packedLight, texture, model.rightPants);
    }

    public static void submitHumanoidUpper(HumanoidModel<?> model, PoseStack poseStack, SubmitNodeCollector collector,
                                           int packedLight, Identifier texture) {
        submitRootChildren(model.root(), poseStack, collector, packedLight, texture,
                model.head, model.body, model.leftArm, model.rightArm);
        submitGrandchild(model.root(), model.head, poseStack, collector, packedLight, texture, model.hat);
    }

    public static void submitHumanoidLower(HumanoidModel<?> model, PoseStack poseStack, SubmitNodeCollector collector,
                                           int packedLight, Identifier texture) {
        submitRootChildren(model.root(), poseStack, collector, packedLight, texture, model.leftLeg, model.rightLeg);
    }

    private static void submitRootChildren(ModelPart root, PoseStack poseStack, SubmitNodeCollector collector,
                                           int packedLight, Identifier texture, ModelPart... parts) {
        var renderType = CamoRenderTypes.camoOverlay(texture);
        if (root == null || !root.visible) return;
        poseStack.pushPose();
        root.translateAndRotate(poseStack);
        for (ModelPart part : parts) {
            if (part.visible) {
                collector.submitModelPart(part, poseStack, renderType, packedLight, OverlayTexture.NO_OVERLAY, null);
            }
        }
        poseStack.popPose();
    }

    private static void submitGrandchild(ModelPart root, ModelPart parent, PoseStack poseStack,
                                         SubmitNodeCollector collector, int packedLight,
                                         Identifier texture, ModelPart child) {
        if (!root.visible || !parent.visible || !child.visible) return;
        poseStack.pushPose();
        root.translateAndRotate(poseStack);
        parent.translateAndRotate(poseStack);
        collector.submitModelPart(child, poseStack, CamoRenderTypes.camoOverlay(texture), packedLight,
                OverlayTexture.NO_OVERLAY, null);
        poseStack.popPose();
    }
}
