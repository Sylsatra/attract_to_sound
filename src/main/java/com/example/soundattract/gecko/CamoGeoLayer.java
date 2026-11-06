package com.example.soundattract.gecko;

import com.example.soundattract.camo.CamoAttachments;
import com.example.soundattract.camo.CamoLODUtil;
import com.example.soundattract.camo.CamoRenderTypes;
import com.example.soundattract.camo.CamoTextureGenerator;
import com.example.soundattract.camo.CamouflageCapability;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

public class CamoGeoLayer<T extends GeoAnimatable, O, R extends GeoRenderState> extends GeoRenderLayer<T, O, R> {
    private static final DataTicket<LivingEntity> LIVING_ENTITY = DataTicket.create("soundattract_living_entity", LivingEntity.class);

    public CamoGeoLayer(GeoRenderer<T, O, R> entityRendererIn) {
        super(entityRendererIn);
    }

    @Override
    public void addRenderData(T animatable, O relatedObject, R renderState, float partialTick) {
        super.addRenderData(animatable, relatedObject, renderState, partialTick);
        if (animatable instanceof LivingEntity living) {
            renderState.addGeckolibData(LIVING_ENTITY, living);
        }
    }

    @Override
    public void submitRenderTask(RenderPassInfo<R> renderPassInfo, SubmitNodeCollector submitNodeCollector) {
        super.submitRenderTask(renderPassInfo, submitNodeCollector);
        LivingEntity living = renderPassInfo.renderState().getGeckolibData(LIVING_ENTITY);
        if (living == null) {
            return;
        }
        CamoLODUtil.LODResult lod = CamoLODUtil.getLOD(living);
        if (!lod.shouldRender() || living.isInvisible()) {
            return;
        }

        CamouflageCapability capability = living.getData(CamoAttachments.CAMOUFLAGE);
        if (capability == null || capability.getLayers().isEmpty()) return;

        Optional<Integer> colorOpt = capability.getBlendedColor();
        float strength = capability.getVisualCamoStrength();
        if (colorOpt.isEmpty() || strength <= 0.05f) return;

        Identifier maskLoc = getTextureResource(renderPassInfo.renderState());
        Identifier camoTex = CamoTextureGenerator.getOrCreateMaskedSmudge(
                capability.getDisplaySeed(),
                colorOpt.get(),
                strength,
                maskLoc,
                lod.resolution(),
                capability.getDisplayErosion(),
                capability.getDisplayHumidity(),
                capability.getDisplayTemperature());

        if (camoTex != null) {
            RenderType camoType = CamoRenderTypes.camoOverlay(camoTex);
            getRenderer().submitRenderTasks(renderPassInfo, submitNodeCollector, camoType);
        }
    }
}
