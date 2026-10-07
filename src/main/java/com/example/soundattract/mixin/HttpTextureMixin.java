package com.example.soundattract.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.example.soundattract.camo.CamoTextureGenerator;

@Mixin(HttpTexture.class)
public abstract class HttpTextureMixin extends SimpleTextureMixin {


    @Inject(
        method = {"onLoadingFinished", "loadCallback", "onDownload", "m_118010_"},
        at = @At("HEAD"),
        remap = false,
        require = 0
    )
    private void soundattract$onDownload(NativeImage img, CallbackInfo ci) {
        ResourceLocation loc = this.soundattract$getLocation();
        if (loc != null) {
            CamoTextureGenerator.recordCapturedMaskCopy(loc, img);
        }
    }
}