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


    protected ResourceLocation soundattract$getLocation() {
        return this.soundattract$capturedLoc;
    }


    @Inject(
        method = {"upload", "doLoad", "m_118136_"},
        at = @At("HEAD"),
        remap = false,
        require = 0
    )
    private void soundattract$onUpload(NativeImage img, boolean blur, boolean clamp, CallbackInfo ci) {
        ResourceLocation loc = this.soundattract$capturedLoc;
        if (loc != null) {
            String path = loc.getPath();

            if (path.contains("/textures/") || path.contains("armor/") || path.contains("skin")) {
                CamoTextureGenerator.recordCapturedMaskCopy(loc, img);
            }
        }
    }

}