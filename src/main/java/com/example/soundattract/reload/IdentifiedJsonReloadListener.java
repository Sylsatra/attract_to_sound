package com.example.soundattract.reload;

import com.example.soundattract.Soundattract;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;

import java.util.Map;

/** Shared Fabric identity for datapack-backed JSON reload listeners. */
public abstract class IdentifiedJsonReloadListener extends SimpleJsonResourceReloadListener
        implements IdentifiableResourceReloadListener {
    private final ResourceLocation fabricId;
    private final boolean requiresServer;
    private volatile Map<ResourceLocation, JsonElement> pendingElements;

    protected IdentifiedJsonReloadListener(String directory, String listenerName, boolean requiresServer) {
        super(new Gson(), directory);
        this.fabricId = new ResourceLocation(Soundattract.MOD_ID, listenerName);
        this.requiresServer = requiresServer;
    }

    @Override
    public final ResourceLocation getFabricId() {
        return fabricId;
    }

    @Override
    protected final void apply(Map<ResourceLocation, JsonElement> elements, ResourceManager resourceManager,
                               ProfilerFiller profiler) {
        if (requiresServer && Soundattract.getServer() == null) {
            pendingElements = Map.copyOf(elements);
            return;
        }
        applyElements(elements, resourceManager, profiler);
    }

    /** Applies initial datapack data that arrived before SERVER_STARTED exposed registry access. */
    public final void applyPending(MinecraftServer server) {
        Map<ResourceLocation, JsonElement> pending = pendingElements;
        if (pending == null || server == null) return;
        pendingElements = null;
        applyElements(pending, server.getResourceManager(), server.getProfiler());
    }

    protected abstract void applyElements(Map<ResourceLocation, JsonElement> elements,
                                          ResourceManager resourceManager, ProfilerFiller profiler);
}
