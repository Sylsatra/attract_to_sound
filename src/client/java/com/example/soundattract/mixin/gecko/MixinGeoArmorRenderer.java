package com.example.soundattract.mixin.gecko;

import com.example.soundattract.gecko.CamoGeoLayer;
import com.example.soundattract.gecko.GeckoCamoData;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.GeoRenderer;

@SuppressWarnings({"rawtypes", "unchecked"})
@Mixin(GeoArmorRenderer.class)
public abstract class MixinGeoArmorRenderer {
    @Unique
    private boolean soundattract$camoLayerAdded;

    @Inject(method = "<init>*", at = @At("RETURN"), remap = false)
    private void soundattract$addCamoLayer(CallbackInfo ci) {
        if (this.soundattract$camoLayerAdded) {
            return;
        }

        this.soundattract$camoLayerAdded = true;
        GeoArmorRenderer renderer = (GeoArmorRenderer) (Object) this;
        renderer.withRenderLayer(new CamoGeoLayer((GeoRenderer) renderer));
    }

    @Inject(method = "captureDefaultRenderState", at = @At("RETURN"), remap = false)
    private void soundattract$captureArmorCamo(@Coerce Object animatable, GeoArmorRenderer.RenderData renderData,
                                               @Coerce Object renderState, float partialTick, CallbackInfo ci) {
        if (!(renderState instanceof GeoRenderState geoRenderState)) {
            return;
        }

        LivingEntity entity = renderData.entity();
        GeckoCamoData.put(geoRenderState, GeckoCamoData.fromArmor(entity, renderData.itemStack()));
    }
}
