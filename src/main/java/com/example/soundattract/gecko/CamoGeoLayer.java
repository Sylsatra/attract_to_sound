package com.example.soundattract.gecko;

import com.example.soundattract.camo.CamoRenderTypes;
import com.example.soundattract.camo.CamoTextureGenerator;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderer;
import software.bernie.geckolib.renderer.base.RenderPassInfo;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Applies camouflage as a second deferred render pass over a GeckoLib 5 model. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class CamoGeoLayer extends GeoRenderLayer<GeoAnimatable, Object, GeoRenderState> {
    public CamoGeoLayer(GeoRenderer renderer) {
        super(renderer);
    }

    @Override
    public void submitRenderTask(RenderPassInfo<GeoRenderState> renderPassInfo, SubmitNodeCollector renderTasks) {
        if (!renderPassInfo.renderState().hasGeckolibData(GeckoCamoData.TICKET)) {
            return;
        }

        GeckoCamoData data = renderPassInfo.getGeckolibData(GeckoCamoData.TICKET);
        if (data.strength() <= 0.05f) {
            return;
        }

        GeoRenderer renderer = renderPassInfo.renderer();
        Identifier texture = data.masked()
                ? CamoTextureGenerator.getOrCreateMaskedSmudge(
                        data.seed(), data.color(), data.strength(), renderer.getTextureLocation(renderPassInfo.renderState()),
                        data.resolution(), data.erosion(), data.humidity(), data.temperature())
                : CamoTextureGenerator.getOrCreateSmudge(
                        data.seed(), data.color(), data.strength(), data.resolution(),
                        data.erosion(), data.humidity(), data.temperature());
        if (texture == null) {
            return;
        }

        RenderType camoType = CamoRenderTypes.camoOverlay(texture);

        renderer.submitRenderTasks(renderPassInfo, (OrderedSubmitNodeCollector) renderTasks, camoType);
    }
}
