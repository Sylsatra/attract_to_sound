package com.example.soundattract.mixin.gecko;

import com.example.soundattract.gecko.CamoGeoLayer;
import com.example.soundattract.gecko.GeckoCamoData;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderer;

@SuppressWarnings({"rawtypes", "unchecked"})
@Mixin(GeoEntityRenderer.class)
public abstract class MixinGeoEntityRenderer {
    @Unique
    private boolean soundattract$camoLayerAdded;

    @Inject(method = "<init>*", at = @At("RETURN"), remap = false)
    private void soundattract$addCamoLayer(CallbackInfo ci) {
        if (this.soundattract$camoLayerAdded) {
            return;
        }

        this.soundattract$camoLayerAdded = true;
        GeoEntityRenderer renderer = (GeoEntityRenderer) (Object) this;
        renderer.withRenderLayer(new CamoGeoLayer((GeoRenderer) renderer));
    }

    @Inject(method = "captureDefaultRenderState", at = @At("RETURN"), remap = false)
    private void soundattract$captureCamo(Entity entity, Object renderData, GeoRenderState renderState,
                                          float partialTick, CallbackInfo ci) {
        GeckoCamoData.put(renderState, entity instanceof net.minecraft.world.entity.LivingEntity living
                ? GeckoCamoData.fromEntity(living)
                : null);
    }
}
