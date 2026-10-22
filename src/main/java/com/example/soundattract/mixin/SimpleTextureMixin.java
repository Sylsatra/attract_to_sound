package com.example.soundattract.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.example.soundattract.camo.CamoTextureGenerator;

@Mixin(SimpleTexture.class)
public abstract class SimpleTextureMixin {

    @Unique
    private ResourceLocation soundattract$capturedLoc;
    @Inject(method = "<init>(Lnet/minecraft/resources/ResourceLocation;)V", at = @At("RETURN"))
    private void soundattract$onInit(ResourceLocation loc, CallbackInfo ci) {
        this.soundattract$capturedLoc = loc;
    }

    /**
     * Shared getter for subclasses like HttpTextureMixin.
     */
    protected ResourceLocation soundattract$getLocation() {
        return this.soundattract$capturedLoc;
    }

    /**
     * Captures the texture's NativeImage during upload/loading.
     * Targets Searge, MCP, and Mojang names to ensure compatibility.
     */
    @Inject(
        method = "doLoad(Lcom/mojang/blaze3d/platform/NativeImage;ZZ)V",
        at = @At("HEAD"), 
        require = 1
    )
    private void soundattract$onUpload(NativeImage img, boolean blur, boolean clamp, CallbackInfo ci) {
        ResourceLocation loc = this.soundattract$capturedLoc;
        if (loc != null) {
            String path = loc.getPath();

            if (path.contains("/textures/") || path.contains("armor/") || path.contains("skin")) {
                NativeImage copy = new NativeImage(img.getWidth(), img.getHeight(), false);
                copy.copyFrom(img);
                CamoTextureGenerator.recordCapturedMask(loc, copy);
            }
        }
    }

}
