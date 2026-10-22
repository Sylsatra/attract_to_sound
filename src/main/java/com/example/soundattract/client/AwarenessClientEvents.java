package com.example.soundattract.client;

import com.example.soundattract.config.ClientConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class AwarenessClientEvents {
    private static boolean registered;

    private AwarenessClientEvents() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        AwarenessKeybinds.register();
        registered = true;
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) ->
                FromTheFogVisualCompat.applyMarker(entity.getUUID(), false));
        AwarenessHud.register();
        MobAlertRenderer.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (AwarenessKeybinds.consumeToggleHudClick()) {
                ClientConfig.AWARENESS_HUD_ENABLED.set(!ClientConfig.AWARENESS_HUD_ENABLED.get());
                ClientConfig.AWARENESS_HUD_ENABLED.save();
            }
        });
        registerDisconnectCleanup();
    }

    static void registerDisconnectCleanup() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            AwarenessClientState.clear();
            AwarenessHud.reset();
            FromTheFogVisualCompat.clear();
        });
    }
}
