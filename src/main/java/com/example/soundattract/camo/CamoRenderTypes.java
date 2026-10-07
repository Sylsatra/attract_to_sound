package com.example.soundattract.camo;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;

@OnlyIn(Dist.CLIENT)
public class CamoRenderTypes extends RenderType {

    public CamoRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    private static final RenderStateShard.LayeringStateShard CAMO_POLYGON_OFFSET = new RenderStateShard.LayeringStateShard(
            "camo_polygon_offset", () -> {
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(-1.0f, -100.0f);
    }, () -> {
        GL11.glPolygonOffset(0.0f, 0.0f);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    });

    public static RenderType camoOverlay(ResourceLocation texture) {
        if (texture == null) return RenderType.crumbling(ResourceLocation.tryBuild("minecraft", "textures/block/stone.png"));

        return create("camo_overlay",
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setLayeringState(CAMO_POLYGON_OFFSET)
                        .createCompositeState(false));
    }
}