package com.example.soundattract.integration;

import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import com.example.soundattract.config.SoundAttractConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLLoader;

@ForgeVoicechatPlugin
public class VoiceChatIntegration implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return com.example.soundattract.SoundAttractMod.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        if (true) {
            registration.registerEvent(ClientSoundEvent.class, ClientHandler::handle);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        static void handle(ClientSoundEvent event) {
            if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enableVoiceChatIntegration.get()) {
                return;
            }
            VoiceChatIntegrationClient.handleClientSound(event);
        }
    }
}
