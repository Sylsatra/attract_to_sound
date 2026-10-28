package com.example.soundattract.integration.voicechat;

import com.example.soundattract.config.DebugLoggingConfig;

import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;
import java.util.UUID;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.network.SoundMessage;
import com.example.soundattract.network.SoundAttractNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class VoiceChatIntegrationClient {
    public static void handleClientSound(ClientSoundEvent event) {
        Player clientPlayer = Minecraft.getInstance().player;
        Level clientWorld = Minecraft.getInstance().level;

        if (clientPlayer == null || clientWorld == null
                || !SoundAttractConfig.serverReady()
                || !SoundAttractConfig.SERVER.enableVoiceChatIntegration.get()) {
            return;
        }

        short[] rawAudio = event.getRawAudio();
        if (rawAudio == null || rawAudio.length == 0) {
            return;
        }

        double db = computePeakDb(rawAudio);
        double normDb = db - (-127.0);
        float intensity01 = (float) Math.max(0.0, Math.min(1.0, normDb / 127.0));

        if (intensity01 <= 0.0f) {
            return;
        }

        boolean isWhispering = event.isWhispering();

        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.VOICE_CHAT)) {
            SoundAttractMod.LOGGER.info(
                "[SVC Client] dbFS={} normDb={} intensity01={} whispering={}",
                db, normDb, intensity01, isWhispering
            );
        }

        double x = clientPlayer.getX();
        double y = clientPlayer.getY();
        double z = clientPlayer.getZ();
        ResourceLocation dim = clientWorld.dimension().location();
        Optional<UUID> sourcePlayerUUID = Optional.of(clientPlayer.getUUID());

        SoundMessage msg = new SoundMessage(
            SoundMessage.VOICE_CHAT_SOUND_ID,
            x, y, z,
            dim,
            sourcePlayerUUID,
            intensity01,
            isWhispering
        );
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(msg);
    }

    private static double computePeakDb(short[] samples) {
        int highest = 0;
        for (short s : samples) {
            int a = s == Short.MIN_VALUE ? 32768 : Math.abs(s);
            if (a > highest) highest = a;
        }
        if (highest == 0) return -127.0;
        double norm = highest / 32768.0;
        double db = 20.0 * Math.log10(norm);
        if (!Double.isFinite(db)) return -127.0;
        if (db > 0.0) db = 0.0;
        if (db < -127.0) db = -127.0;
        return db;
    }
}
