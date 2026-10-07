package com.example.soundattract.gecko;

import com.example.soundattract.integration.tacz.TaczCamoDebug;
import com.example.soundattract.camo.CamoEntityRendererLookup;
import net.minecraft.world.entity.EntityType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@OnlyIn(Dist.CLIENT)
public final class GeckoCamoLayerRegistration {
    private GeckoCamoLayerRegistration() {
    }

    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            tryAddLayer(event, type);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void tryAddLayer(EntityRenderersEvent.AddLayers event, EntityType<?> type) {
        boolean traceTacz = TaczCamoDebug.isTracked(type);
        EntityRenderer<?> renderer = CamoEntityRendererLookup.getRenderer(event, type);

        if (renderer == null) {
            if (traceTacz) {
                TaczCamoDebug.logLayerStatus(type, "geckolib", "renderer_missing", null);
            }
            return;
        }

        if (!(renderer instanceof GeoEntityRenderer geoRenderer)) {
            return;
        }
        boolean alreadyRegistered = geoRenderer.getRenderLayers().stream()
                .anyMatch(layer -> layer instanceof CamoGeoLayer<?>);
        if (!alreadyRegistered) {
            geoRenderer.addRenderLayer(new CamoGeoLayer(geoRenderer));
        }
        if (traceTacz) {
            TaczCamoDebug.logLayerStatus(type, "geckolib", alreadyRegistered ? "already_present" : "added", renderer.getClass().getName());
        }
    }
}
