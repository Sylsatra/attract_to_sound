package com.example.soundattract.mixin;

import com.example.soundattract.camo.CamoTextureGenerator;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SkinTextureDownloader.class)
public abstract class HttpTextureMixin {
    @Inject(method = "registerTextureInManager", at = @At("HEAD"), require = 0)
    private void soundattract$captureSkin(ClientAsset.Texture textureId, NativeImage image,
                                          CallbackInfoReturnable<CompletableFuture<ClientAsset.Texture>> cir) {
        if (textureId == null || image == null) return;
        Identifier location = textureId.texturePath();
        CamoTextureGenerator.recordCapturedMaskCopy(location, image);
    }
}
