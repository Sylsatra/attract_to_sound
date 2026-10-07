package com.example.soundattract.camo;

import com.example.soundattract.mixin.AddLayersRendererMapAccessor;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;

@OnlyIn(Dist.CLIENT)
public final class CamoEntityRendererLookup {
    private CamoEntityRendererLookup() {
    }

    public static EntityRenderer<?> getRenderer(EntityRenderersEvent.AddLayers event, EntityType<?> type) {
        Object target = event;
        if (target instanceof AddLayersRendererMapAccessor accessor) {
            return accessor.soundattract$getRenderers().get(type);
        }
        return null;
    }
}
