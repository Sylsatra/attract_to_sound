package com.example.soundattract.mixin;

import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SimpleTexture.class)
public interface SimpleTextureAccessor {
    @Invoker("resourceId")
    Identifier soundattract$getLocation();
}
