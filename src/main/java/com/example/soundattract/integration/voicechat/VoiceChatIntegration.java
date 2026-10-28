package com.example.soundattract.integration.voicechat;

import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;

@ForgeVoicechatPlugin
public class VoiceChatIntegration implements VoicechatPlugin {

    private static final String VOICE_CHAT_PLUGIN_ID = "soundattract_vc_integration";

    @Override
    public String getPluginId() {
        return VOICE_CHAT_PLUGIN_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            registration.registerEvent(ClientSoundEvent.class, event -> {
                ClientHandler.handle(event);
            });
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        static void handle(ClientSoundEvent event) {
            com.example.soundattract.integration.voicechat.VoiceChatIntegrationClient.handleClientSound(event);
        }
    }
}
