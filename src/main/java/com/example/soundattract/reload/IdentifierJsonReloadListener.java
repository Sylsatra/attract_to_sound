package com.example.soundattract.reload;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;

public abstract class IdentifierJsonReloadListener extends SimpleJsonResourceReloadListener<JsonElement> {
    private static final Codec<JsonElement> JSON_CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> dynamic.convert(JsonOps.INSTANCE).getValue(),
            json -> new Dynamic<>(JsonOps.INSTANCE, json));

    protected IdentifierJsonReloadListener(String directory) {
        super(JSON_CODEC, FileToIdConverter.json(directory));
    }
}
