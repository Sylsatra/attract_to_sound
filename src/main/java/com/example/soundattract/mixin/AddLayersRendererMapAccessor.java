package com.example.soundattract.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.client.event.EntityRenderersEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = EntityRenderersEvent.AddLayers.class, remap = false)
public interface AddLayersRendererMapAccessor {
    @Accessor(value = "renderers", remap = false)
    Map<EntityType<?>, EntityRenderer<?>> soundattract$getRenderers();
}
