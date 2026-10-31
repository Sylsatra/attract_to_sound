package com.example.soundattract.integration.immersive_melodies;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.config.separate.IntegrationConfig;
import com.example.soundattract.tracking.SoundTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public class ImmersiveMelodiesEvents {

    private static boolean initialized = false;
    private static DataComponentType<Boolean> PLAYING_COMPONENT = null;
    private static DataComponentType<Identifier> MELODY_COMPONENT = null;

    public static void register() {
    }

    private static void initializeReflection() {
        if (initialized) return;
        try {
            Class<?> instrumentItemClass = Class.forName("immersive_melodies.item.InstrumentItem");
            Field playingField = instrumentItemClass.getField("PLAYING");
            Field melodyField = instrumentItemClass.getField("MELODY");
            PLAYING_COMPONENT = (DataComponentType<Boolean>) playingField.get(null);
            MELODY_COMPONENT = (DataComponentType<Identifier>) melodyField.get(null);
            initialized = true;
        } catch (Exception e) {
            SoundAttractMod.LOGGER.warn("[ImmersiveMelodies] Failed to initialize reflection: {}", e.getMessage());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ModList.get().isLoaded("immersive_melodies")) return;
        if (!IntegrationConfig.ENABLE_IMMERSIVE_MELODIES_INTEGRATION.get()) return;

        initializeReflection();
        if (PLAYING_COMPONENT == null || MELODY_COMPONENT == null) return;

        int interval = IntegrationConfig.IMMERSIVE_MELODIES_POLL_INTERVAL.get();
        long gameTime = event.getServer().overworld().getGameTime();

        if (gameTime % interval != 0) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                checkAndAddMusicSound(player);
            }
        }
    }

    private static void checkAndAddMusicSound(ServerPlayer player) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);

        if (isInstrumentPlaying(main)) {
            addMusicSound(player, main);
        } else if (isInstrumentPlaying(off)) {
            addMusicSound(player, off);
        }
    }

    private static boolean isInstrumentPlaying(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (PLAYING_COMPONENT == null) return false;
        
        try {
            Class<?> instrumentItemClass = Class.forName("immersive_melodies.item.InstrumentItem");
            if (instrumentItemClass.isInstance(stack.getItem())) {
                return stack.getOrDefault(PLAYING_COMPONENT, false);
            }
        } catch (Exception e) {
        }
        return false;
    }

    private static void addMusicSound(ServerPlayer player, ItemStack stack) {
        if (MELODY_COMPONENT == null) return;
        
        try {
            Class<?> instrumentItemClass = Class.forName("immersive_melodies.item.InstrumentItem");
            if (!instrumentItemClass.isInstance(stack.getItem())) return;

            Identifier melodyId = stack.getOrDefault(MELODY_COMPONENT, null);
            if (melodyId == null) return;

            Identifier instrumentId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (instrumentId == null) return;

            double range = IntegrationConfig.IMMERSIVE_MELODIES_DEFAULT_RANGE.get();
            double weight = IntegrationConfig.IMMERSIVE_MELODIES_DEFAULT_WEIGHT.get();

            double[] overrides = getMelodyOverride(melodyId.toString());
            if (overrides != null) {
                range = overrides[0];
                weight = overrides[1];
            } else {
                weight *= getInstrumentMultiplier(instrumentId.toString());
            }

            BlockPos pos = player.blockPosition();
            String dimString = player.level().dimension().identifier().toString();
            int lifetime = IntegrationConfig.IMMERSIVE_MELODIES_POLL_INTERVAL.get() + 10;

            Identifier baseId = Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "virtual");
            String meta = player.getUUID().toString() + "/ImmersiveMelody";
            String soundIdToUse = SoundTracker.buildIntegrationSoundId(baseId, meta);

            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[ImmersiveMelodies] addSound: player={} | melody={} | range={} | weight={} | pos={}", 
                    player.getName().getString(), melodyId, range, weight, pos);
            }

            SoundTracker.addSound(null, pos, dimString, range, weight, lifetime, soundIdToUse);
        } catch (Exception e) {
        }
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
