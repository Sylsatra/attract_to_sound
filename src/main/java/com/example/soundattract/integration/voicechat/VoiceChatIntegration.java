package com.example.soundattract.integration.voicechat;

import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

public class VoiceChatIntegration implements VoicechatPlugin {

    private static final String VOICE_CHAT_PLUGIN_ID = "soundattract_vc_integration";

    @Override
    public String getPluginId() {
        return VOICE_CHAT_PLUGIN_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientSoundEvent.class, event -> {
            ClientHandler.handle(event);
        });
    }

    @Environment(EnvType.CLIENT)
    private static class ClientHandler {
        static void handle(ClientSoundEvent event) {
            VoiceChatClientDispatch.handle(event);
        }
    }
}
