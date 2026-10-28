package com.example.soundattract.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.example.soundattract.camo.CamoRenderStateEntities;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public class ItemInHandLayerMixin {

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V", at = @At("HEAD"), cancellable = true)
    private void soundattract$hideItemsWhenInvisible(PoseStack poseStack, SubmitNodeCollector collector,
                                                      int packedLight, ArmedEntityRenderState state,
                                                      float limbSwing, float limbSwingAmount, CallbackInfo ci) {
        LivingEntity entity = CamoRenderStateEntities.get(state);
        if (state.isInvisible && (entity == null
                        || !com.example.soundattract.client.FromTheFogVisualCompat.shouldKeepInvisibleEquipment(entity))
                && com.example.soundattract.config.SoundAttractConfig.shouldHideWornGearWhileInvisible()) {
            ci.cancel();
        }
    }
}
