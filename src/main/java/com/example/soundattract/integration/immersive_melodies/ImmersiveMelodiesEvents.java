package com.example.soundattract.integration.immersive_melodies;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.config.separate.IntegrationConfig;
import com.example.soundattract.tracking.SoundTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ImmersiveMelodiesEvents {

    public static void onServerTick(MinecraftServer server) {
        if (!IntegrationConfig.ENABLE_IMMERSIVE_MELODIES_INTEGRATION.get()) return;

        int interval = IntegrationConfig.IMMERSIVE_MELODIES_POLL_INTERVAL.get();
        long gameTime = server.overworld().getGameTime();

        if (gameTime % interval != 0) return;

        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                checkAndAddMusicSound(player);
            }
        }
    }

    static void checkAndAddMusicSound(ServerPlayer player) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);

        if (isInstrumentPlaying(main)) {
            addMusicSound(player, main);
        } else if (isInstrumentPlaying(off)) {
            addMusicSound(player, off);
        }
    }

    static boolean isInstrumentPlaying(ItemStack stack) {
        return !stack.isEmpty() && ImmersiveMelodiesComponents.isPlaying(stack);
    }

    private static void addMusicSound(ServerPlayer player, ItemStack stack) {
        String melodyId = ImmersiveMelodiesComponents.melodyId(stack);
        Identifier instrumentId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (instrumentId == null) return;

        double range = IntegrationConfig.IMMERSIVE_MELODIES_DEFAULT_RANGE.get();
        double weight = IntegrationConfig.IMMERSIVE_MELODIES_DEFAULT_WEIGHT.get();

        double[] overrides = getMelodyOverride(melodyId);
        if (overrides != null) {
            range = overrides[0];
            weight = overrides[1];
        } else {
            weight *= getInstrumentMultiplier(instrumentId.toString());
        }

        BlockPos pos = player.blockPosition();
        String dimString = player.level().dimension().identifier().toString();
        int lifetime = IntegrationConfig.IMMERSIVE_MELODIES_POLL_INTERVAL.get() + 10;

        Identifier baseId = Identifier.fromNamespaceAndPath(Soundattract.MOD_ID, "virtual");
        String meta = player.getUUID().toString() + "/ImmersiveMelody";
        String soundIdToUse = SoundTracker.buildIntegrationSoundId(baseId, meta);

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info("[ImmersiveMelodies] addSound: player={} | melody={} | range={} | weight={} | pos={}", 
                player.getName().getString(), melodyId, range, weight, pos);
        }

        SoundTracker.addSound(null, pos, dimString, range, weight, lifetime, soundIdToUse);
    }

    private static double getInstrumentMultiplier(String itemId) {
        List<? extends String> list = IntegrationConfig.IMMERSIVE_MELODIES_INSTRUMENT_MULTIPLIERS.get();
        for (String entry : list) {
            String[] parts = entry.split(":");
            if (parts.length >= 3) {
                String id = parts[0] + ":" + parts[1];
                if (id.equals(itemId)) {
                    try {
                        return Double.parseDouble(parts[2]);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return 1.0;
    }

    private static double[] getMelodyOverride(String melodyId) {
        if (melodyId == null || melodyId.isEmpty()) return null;
        
        List<? extends String> list = IntegrationConfig.IMMERSIVE_MELODIES_MELODY_OVERRIDES.get();
        for (String entry : list) {
            String[] parts = entry.split(":");
            if (parts.length >= 4) {
                String id = parts[0] + ":" + parts[1];
                if (id.equals(melodyId)) {
                    try {
                        return new double[]{Double.parseDouble(parts[2]), Double.parseDouble(parts[3])};
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return null;
    }
}
