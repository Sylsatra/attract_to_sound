package com.example.soundattract.reload;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.packs.PackType;

import java.util.List;

/** Registers the datapack-backed configuration listeners on Fabric. */
public final class DataDrivenReloadRegistrar {
    private DataDrivenReloadRegistrar() {
    }

    public static void register() {
        ResourceManagerHelper helper = ResourceManagerHelper.get(PackType.SERVER_DATA);
        List<IdentifiedJsonReloadListener> listeners = List.of(
                new SoundDefinitionsReloadListener(),
                new ArmorColorsReloadListener(),
                new MobProfilesReloadListener(),
                new PlayerProfilesReloadListener(),
                new TaczGunsReloadListener(),
                new PointBlankGunsReloadListener());
        listeners.forEach(helper::registerReloadListener);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> listeners.forEach(listener -> listener.applyPending(server)));
    }
}
