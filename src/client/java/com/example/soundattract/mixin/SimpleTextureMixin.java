package com.example.soundattract.mixin;

import com.example.soundattract.camo.CamoTextureGenerator;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReloadableTexture.class)
public abstract class SimpleTextureMixin {
    @Shadow public abstract Identifier resourceId();

    @Inject(method = "doLoad(Lcom/mojang/blaze3d/platform/NativeImage;)V", at = @At("HEAD"))
    private void soundattract$captureTexture(NativeImage image, CallbackInfo ci) {
        Identifier id = resourceId();
        String path = id.getPath();
        if (path.contains("/textures/") || path.contains("armor/") || path.contains("skin")) {
            NativeImage copy = new NativeImage(image.getWidth(), image.getHeight(), false);
            copy.copyFrom(image);
            CamoTextureGenerator.recordCapturedMask(id, copy);
        }
    }
}
