package com.example.soundattract.reload;

import com.example.soundattract.Soundattract;
import com.google.gson.JsonElement;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.FileToIdConverter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;

import java.util.Map;

/** Shared Fabric identity for datapack-backed JSON reload listeners. */
public abstract class IdentifiedJsonReloadListener extends SimpleJsonResourceReloadListener<JsonElement>
        implements IdentifiableResourceReloadListener {
    private static final Codec<JsonElement> JSON_ELEMENT_CODEC = Codec.PASSTHROUGH.xmap(
            value -> value.convert(JsonOps.INSTANCE).getValue(),
            value -> new Dynamic<>(JsonOps.INSTANCE, value));
    private final Identifier fabricId;
    private final boolean requiresServer;
    private volatile Map<Identifier, JsonElement> pendingElements;

    protected IdentifiedJsonReloadListener(String directory, String listenerName, boolean requiresServer) {
        super(JSON_ELEMENT_CODEC, FileToIdConverter.json(directory));
        this.fabricId = Identifier.fromNamespaceAndPath(Soundattract.MOD_ID, listenerName);
        this.requiresServer = requiresServer;
    }

    @Override
    public final Identifier getFabricId() {
        return fabricId;
    }

    @Override
    protected final void apply(Map<Identifier, JsonElement> elements, ResourceManager resourceManager,
                               ProfilerFiller profiler) {
        if (requiresServer && Soundattract.getServer() == null) {
            pendingElements = Map.copyOf(elements);
            return;
        }
        applyElements(elements, resourceManager, profiler);
    }

    /** Applies initial datapack data that arrived before SERVER_STARTED exposed registry access. */
    public final void applyPending(MinecraftServer server) {
        Map<Identifier, JsonElement> pending = pendingElements;
        if (pending == null || server == null) return;
        pendingElements = null;
        applyElements(pending, server.getResourceManager(), net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
    }

    protected abstract void applyElements(Map<Identifier, JsonElement> elements,
                                          ResourceManager resourceManager, ProfilerFiller profiler);
}
