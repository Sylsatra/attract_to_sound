package com.example.soundattract.mixin;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "noppes.npcs.client.layer.LayerBackItem", remap = false)
public abstract class CustomNpcsLayerBackItemMixin {
    @org.spongepowered.asm.mixin.Shadow protected EntityCustomNpc npc;
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IFFFFFF)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void soundattract$hideWhenInvisible(PoseStack poseStack, MultiBufferSource buffer, int packedLight, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float partialTicks, CallbackInfo ci) {
        if (npc != null && npc.isInvisible() && SoundAttractConfig.shouldHideWornGearWhileInvisible()) ci.cancel();
    }
}
