package com.example.soundattract.mixin;

import com.example.soundattract.camo.GenericMobCamoLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import com.mojang.blaze3d.vertex.PoseStack;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "noppes.npcs.client.renderer.RenderCustomNpc", remap = false)
public abstract class CustomNpcsRenderCustomNpcMixin {
    @Shadow public List<RenderLayer> npclayers;

    @Inject(method = "render(Lnoppes/npcs/entity/EntityNPCInterface;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), remap = false)
    private void soundattract$ensureCamoLayer(EntityNPCInterface npc, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (!(npc instanceof EntityCustomNpc)) return;
        if (!(this instanceof RenderLayerParent<?, ?> parent) || npclayers == null) return;
        if (npclayers.stream().noneMatch(layer -> layer instanceof GenericMobCamoLayer)) {
            npclayers.add(new GenericMobCamoLayer((RenderLayerParent) parent));
        }
    }
}
