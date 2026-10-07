package com.example.soundattract.mixin;

import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimpleTexture.class)
public interface SimpleTextureAccessor {
    @Accessor(value = "location", remap = true)
    ResourceLocation soundattract$getLocation();
}