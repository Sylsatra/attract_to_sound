package com.example.soundattract.mixin;

import com.example.soundattract.camo.CamoRenderLayer;
import com.example.soundattract.camo.GenericMobCamoLayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererCamoMixin {
    @Shadow protected abstract boolean addLayer(RenderLayer layer);

    @Inject(method = "<init>", at = @At("RETURN"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void soundattract$addCamoLayer(EntityRendererProvider.Context context, EntityModel model, float shadowRadius, CallbackInfo ci) {
        if ((Object) this instanceof AvatarRenderer) {
            addLayer(new CamoRenderLayer((RenderLayerParent) this));
        } else if (model instanceof IllagerModel) {
            addLayer(new GenericMobCamoLayer.Illager((RenderLayerParent) this));
        } else if (model instanceof VillagerModel) {
            addLayer(new GenericMobCamoLayer.VillagerLayer((RenderLayerParent) this));
        } else if (model instanceof HumanoidModel) {
            addLayer(new GenericMobCamoLayer((RenderLayerParent) this));
        }
    }
}
