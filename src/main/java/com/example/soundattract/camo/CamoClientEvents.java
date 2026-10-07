package com.example.soundattract.camo;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import com.example.soundattract.integration.tacz.TaczCamoDebug;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CamoClientEvents {

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skinType : new String[]{"default", "slim"}) {
            PlayerRenderer renderer = event.getSkin(skinType);
            if (renderer != null) {
                renderer.addLayer(new CamoRenderLayer((RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) (Object) renderer));
            }
        }

        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            tryAddLayer(event, type);
        }

        if (ModList.get().isLoaded("geckolib")) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.example.soundattract.gecko.GeckoCamoLayerRegistration.addLayers(event));
        }
    }

    private static void tryAddLayer(EntityRenderersEvent.AddLayers event, EntityType<?> type) {
        boolean traceTacz = TaczCamoDebug.isTracked(type);
        EntityRenderer<?> renderer = CamoEntityRendererLookup.getRenderer(event, type);

        if (renderer == null) {
            if (traceTacz) {
                TaczCamoDebug.logLayerStatus(type, "generic_living_renderer", "renderer_missing", null);
            }
            return;
        }

        if (renderer instanceof LivingEntityRenderer livingRenderer) {
            var model = livingRenderer.getModel();

            if (model instanceof HumanoidModel humanoid) {

                if (type != EntityType.PLAYER) {
                    livingRenderer.addLayer(new GenericMobCamoLayer(livingRenderer));
                    if (traceTacz) {
                        TaczCamoDebug.logLayerStatus(type, "generic_humanoid", "added", renderer.getClass().getName());
                    }
                }
            } else if (model instanceof IllagerModel illager) {
                livingRenderer.addLayer(new GenericMobCamoLayer.Illager((RenderLayerParent<AbstractIllager, IllagerModel<AbstractIllager>>) livingRenderer));
                if (traceTacz) {
                    TaczCamoDebug.logLayerStatus(type, "generic_illager", "added", renderer.getClass().getName());
                }
            } else if (model instanceof VillagerModel villager) {
                livingRenderer.addLayer(new GenericMobCamoLayer.VillagerLayer(livingRenderer));
                if (traceTacz) {
                    TaczCamoDebug.logLayerStatus(type, "generic_villager", "added", renderer.getClass().getName());
                }
            } else if (traceTacz) {
                TaczCamoDebug.logLayerStatus(type, "generic_living_renderer", "unsupported_model:" + model.getClass().getName(), renderer.getClass().getName());
            }
        }
    }
}
