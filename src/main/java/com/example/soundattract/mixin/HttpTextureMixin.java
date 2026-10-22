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

    /**
     * Captures the skin NativeImage after it finishes downloading.
     * Target names:
     * - onLoadingFinished / loadCallback (Mojang)
     * - onDownload (MCP/Mappings)
     * - m_118010_ (Searge/Runtime)
     */
    @Inject(
        method = "loadCallback(Lcom/mojang/blaze3d/platform/NativeImage;)V",
        at = @At("HEAD"), 
        require = 1
    )
    private void soundattract$onDownload(NativeImage img, CallbackInfo ci) {
        ResourceLocation loc = this.soundattract$getLocation();
        if (loc != null) {
            NativeImage copy = new NativeImage(img.getWidth(), img.getHeight(), false);
            copy.copyFrom(img);
            CamoTextureGenerator.recordCapturedMask(loc, copy);
        }
    }
}
