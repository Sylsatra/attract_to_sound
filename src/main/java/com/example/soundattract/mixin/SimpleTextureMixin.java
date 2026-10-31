package com.example.soundattract.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.example.soundattract.camo.CamoTextureGenerator;

@Mixin(SimpleTexture.class)
public abstract class SimpleTextureMixin {

    @Unique
    private Identifier soundattract$capturedLoc;
    @Inject(method = "<init>(Lnet/minecraft/resources/Identifier;)V", at = @At("RETURN"))
    private void soundattract$onInit(Identifier loc, CallbackInfo ci) {
        this.soundattract$capturedLoc = loc;
    }

    /**
     * Shared getter for subclasses like HttpTextureMixin.
     */
    protected Identifier soundattract$getLocation() {
        return this.soundattract$capturedLoc;
    }

    /**
     * Captures the texture's NativeImage during upload/loading.
     * Targets Searge, MCP, and Mojang names to ensure compatibility.
     */
    @Inject(
        method = {"upload", "doLoad", "m_118136_"}, 
        at = @At("HEAD"), 
        remap = false, 
        require = 0
    )
    private void soundattract$onUpload(NativeImage img, boolean blur, boolean clamp, CallbackInfo ci) {
        Identifier loc = this.soundattract$capturedLoc;
        if (loc != null) {
            String path = loc.getPath();

            if (path.contains("/textures/") || path.contains("armor/") || path.contains("skin")) {
                NativeImage copy = new NativeImage(img.getWidth(), img.getHeight(), false);
                copy.copyFrom(img);
                CamoTextureGenerator.recordCapturedMask(loc, copy);
            }
        }
    }

    /**
     * Cleans up the captured mask ONLY when the texture is actually closed by the game.
     */
    @Inject(method = "close", at = @At("HEAD"), remap = false, require = 0)
    private void soundattract$onClose(CallbackInfo ci) {
        if (this.soundattract$capturedLoc != null) {
            CamoTextureGenerator.releaseCapturedMask(this.soundattract$capturedLoc);
        }
    }
}
