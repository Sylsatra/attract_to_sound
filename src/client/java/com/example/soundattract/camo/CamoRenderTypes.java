package com.example.soundattract.camo;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public final class CamoRenderTypes {
    private CamoRenderTypes() {}

    public static RenderType camoOverlay(Identifier texture) {
        return RenderTypes.entityTranslucent(texture == null
                ? Identifier.fromNamespaceAndPath("minecraft", "textures/block/stone.png") : texture);
    }
}
