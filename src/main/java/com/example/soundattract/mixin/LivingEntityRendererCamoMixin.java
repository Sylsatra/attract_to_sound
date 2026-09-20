package com.example.soundattract.mixin;

import com.example.soundattract.camo.CamoRenderLayer;
import com.example.soundattract.camo.GenericMobCamoLayer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractIllager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererCamoMixin<T extends LivingEntity, M extends EntityModel<T>> {

    @Shadow
    protected abstract boolean addLayer(RenderLayer<T, M> layer);

    @Inject(method = "<init>", at = @At("RETURN"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void soundattract$addCamoLayer(EntityRendererProvider.Context context, EntityModel model, float shadowRadius, CallbackInfo ci) {
        try {
            if ((Object) this instanceof PlayerRenderer) {
                addLayer((RenderLayer) new CamoRenderLayer((RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) this));
                return;
            }

            if (getClass().getName().equals("noppes.npcs.client.renderer.RenderCustomNpc")) {
                return;
            } else if (model instanceof HumanoidModel) {
                addLayer(new GenericMobCamoLayer((RenderLayerParent) this));
            } else if (model instanceof IllagerModel) {
                addLayer((RenderLayer) new GenericMobCamoLayer.Illager((RenderLayerParent<AbstractIllager, IllagerModel<AbstractIllager>>) this));
            } else if (model instanceof VillagerModel) {
                addLayer((RenderLayer) new GenericMobCamoLayer.VillagerLayer((RenderLayerParent) this));
            }
        } catch (Throwable t) {
            com.example.soundattract.Soundattract.LOGGER.warn("[CamoLayer] Failed to add camo layer to {}: {}", getClass().getSimpleName(), t.getMessage());
        }
    }
}
