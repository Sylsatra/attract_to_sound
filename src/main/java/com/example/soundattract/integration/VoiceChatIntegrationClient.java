package com.example.soundattract.integration;

import java.util.Optional;
import java.util.UUID;
import com.example.soundattract.network.SoundMessage;
import com.example.soundattract.config.SoundAttractConfig;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

@OnlyIn(Dist.CLIENT)
public class VoiceChatIntegrationClient {

    public static void handleClientSound(ClientSoundEvent event) {
        if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enableVoiceChatIntegration.get()) {
            return;
        }

        Player clientPlayer = Minecraft.getInstance().player;
        Level clientWorld = Minecraft.getInstance().level;

        if (clientPlayer == null || clientWorld == null) {
            return;
        }

        short[] rawAudio = event.getRawAudio();
        if (rawAudio == null || rawAudio.length == 0) {
            return;
        }
        double db = computePeakDb(rawAudio);
        double normDb = db - (-127.0);
        float intensity01 = (float) Math.max(0.0, Math.min(1.0, normDb / 127.0));

        double factor = 0.0;
        List<? extends String> rawMap = SoundAttractConfig.SERVER.voiceChatDbThresholdMap.get();
        if (rawMap != null && !rawMap.isEmpty()) {
            List<double[]> pairs = new ArrayList<>();
            for (String entry : rawMap) {
                if (entry == null || entry.isEmpty()) continue;
                String[] parts = entry.split(":", 2);
                if (parts.length != 2) continue;
                try {
                    double th = Double.parseDouble(parts[0].trim());
                    double mul = Double.parseDouble(parts[1].trim());
                    pairs.add(new double[]{th, mul});
                } catch (Exception ignored) {
                }
            }

            Collections.sort(pairs, new Comparator<double[]>() {
                @Override
                public int compare(double[] a, double[] b) {
                    return Double.compare(b[0], a[0]);
                }
            });
            for (double[] p : pairs) {
                if (normDb >= p[0]) {
                    factor = p[1];
                    break;
                }
            }
        } else {
            if (normDb >= 50.0) {
                factor = 1.0;
            } else if (normDb >= 30.0) {
                factor = 0.7;
            } else if (normDb >= 10.0) {
                factor = 0.3;
            } else {
                factor = 0.0;
            }
        }

        boolean isWhispering = event.isWhispering();
        int baseRange = isWhispering ? SoundAttractConfig.SERVER.voiceChatWhisperRange.get()
                : SoundAttractConfig.SERVER.voiceChatNormalRange.get();
        int effectiveRange = (int) Math.round(baseRange * factor);
        if (effectiveRange <= 0) {
            return;
        }
        double weight = SoundAttractConfig.SERVER.voiceChatWeight.get();

        Vec3 pos = clientPlayer.position();
        Identifier dim = clientWorld.dimension().identifier();
        Optional<UUID> sourcePlayerUUID = Optional.of(clientPlayer.getUUID());

        SoundMessage msg = new SoundMessage(
            SoundMessage.VOICE_CHAT_SOUND_ID,
            pos,
            dim,
            sourcePlayerUUID,
            intensity01,
            isWhispering
        );
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(msg);
    }
    private static double computePeakDb(short[] samples) {
        short highest = 0;
        for (short s : samples) {
            int a = Math.abs(s);
            if (a > highest) highest = (short) a;
        }
        if (highest == 0) return -127.0;
        double norm = Math.abs(highest) / 32768.0;
        double db = 20.0 * Math.log10(norm);
        if (!Double.isFinite(db)) return -127.0;
        if (db > 0.0) db = 0.0;
        if (db < -127.0) db = -127.0;
        return db;
    }
}
