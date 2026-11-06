package com.example.soundattract.gecko;

import com.example.soundattract.camo.CamoEntityRendererLookup;
import net.minecraft.world.entity.EntityType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import com.geckolib.renderer.GeoEntityRenderer;

public final class GeckoCamoLayerRegistration {
    private GeckoCamoLayerRegistration() {
    }

    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            tryAddLayer(event, type);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void tryAddLayer(EntityRenderersEvent.AddLayers event, EntityType<?> type) {
        EntityRenderer<?, ?> renderer = CamoEntityRendererLookup.getRenderer(event, type);

        if (renderer == null) {
            return;
        }

        if (!(renderer instanceof GeoEntityRenderer geoRenderer)) {
            return;
        }
        boolean alreadyRegistered = geoRenderer.getRenderLayers().stream()
                .anyMatch(layer -> layer instanceof CamoGeoLayer<?, ?, ?>);
        if (!alreadyRegistered) {
            geoRenderer.withRenderLayer(new CamoGeoLayer(geoRenderer));
        }
    }
}
