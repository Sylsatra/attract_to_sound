package com.example.soundattract.mixin;

import com.example.soundattract.camo.CamoTextureGenerator;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Releases captured source masks when Minecraft closes a reloadable texture. */
@Mixin(AbstractTexture.class)
public abstract class SimpleTextureTextureImageMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void soundattract$releaseCapturedTextureMask(CallbackInfo ci) {
        if ((Object) this instanceof ReloadableTexture texture) {
            CamoTextureGenerator.releaseCapturedMask(texture.resourceId());
        }
    }
}
