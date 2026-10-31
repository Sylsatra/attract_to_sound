package com.example.soundattract.integration.toughasnails;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import toughasnails.api.item.TANItems;
import toughasnails.api.temperature.TemperatureHelper;
import toughasnails.api.temperature.TemperatureLevel;
import toughasnails.api.thirst.IThirst;
import toughasnails.api.thirst.ThirstHelper;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

public final class ToughAsNailsIntegration {
    private static int cursor;

    private ToughAsNailsIntegration() {}

    public static boolean isEnabledAndLoaded() {
        return ModList.get().isLoaded("toughasnails")
                && SoundAttractConfig.COMMON != null
                && SoundAttractConfig.COMMON.enableToughAsNailsIntegration.get();
    }

    public static void registerIfPresent(IEventBus eventBus) {
        if (isEnabledAndLoaded()) {
            eventBus.register(ToughAsNailsIntegration.class);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!isEnabledAndLoaded()) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;

        int interval = Math.max(1, SoundAttractConfig.COMMON.toughAsNailsPollIntervalTicks.get());
        int perTick = Math.max(1, (players.size() + interval - 1) / interval);
        for (int i = 0; i < perTick && !players.isEmpty(); i++) {
            if (cursor >= players.size()) cursor = 0;
            updatePlayer(players.get(cursor++));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ToughAsNailsStateCache.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        cursor = 0;
        ToughAsNailsStateCache.clear();
    }

    private static void updatePlayer(ServerPlayer player) {
        if (player == null || player.isRemoved()) return;
        long now = player.level().getGameTime();
        try {
            TemperatureLevel temperature = currentTemperature(player);
            int woolPieces = countArmorPieces(player, true);
            int leafPieces = countArmorPieces(player, false);
            double scent = temperatureScentMultiplier(temperature);
            double sound = 1.0;

            double thirstFactor = thirstFactor(player);
            scent *= lerp(1.0, config(SoundAttractConfig.COMMON.toughAsNailsThirstScentMultiplierAtEmpty.get()), thirstFactor);
            sound *= lerp(1.0, config(SoundAttractConfig.COMMON.toughAsNailsThirstSoundMultiplierAtEmpty.get()), thirstFactor);

            if (isHyperthermic(player)) {
                scent *= config(SoundAttractConfig.COMMON.toughAsNailsHyperthermiaScentMultiplier.get());
                sound *= config(SoundAttractConfig.COMMON.toughAsNailsHyperthermiaSoundMultiplier.get());
            }

            sound *= woolSoundMultiplier(woolPieces);
            scent *= woolHeatScentMultiplier(temperature, woolPieces);
            scent *= leafScentMultiplier(leafPieces);

            ToughAsNailsStateCache.update(player.getUUID(), new ToughAsNailsStateCache.State(config(scent), config(sound), now));
        } catch (Throwable t) {
            ToughAsNailsStateCache.update(player.getUUID(), new ToughAsNailsStateCache.State(1.0, 1.0, now));
            if (SoundAttractConfig.COMMON != null && SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.warn("[ToughAsNailsIntegration] Failed to update state for {}", player.getName().getString(), t);
            }
        }
    }

    private static TemperatureLevel currentTemperature(ServerPlayer player) {
        try {
            if (TemperatureHelper.isTemperatureEnabled()) {
                TemperatureLevel level = TemperatureHelper.getTemperatureForPlayer(player);
                return level == null ? TemperatureLevel.NEUTRAL : level;
            }
        } catch (Throwable ignored) {
        }
        return TemperatureLevel.NEUTRAL;
    }

    private static boolean isHyperthermic(ServerPlayer player) {
        try {
            return TemperatureHelper.isTemperatureEnabled() && TemperatureHelper.getTicksHyperthermic(player) > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static double thirstFactor(ServerPlayer player) {
        try {
            if (!ThirstHelper.isThirstEnabled()) return 0.0;
            IThirst thirst = ThirstHelper.getThirst(player);
            if (thirst == null) return 0.0;
            int value = Math.max(0, Math.min(20, thirst.getThirst()));
            return (20.0 - value) / 20.0;
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private static int countArmorPieces(ServerPlayer player, boolean wool) {
        int count = 0;
        for (ItemStack stack : com.example.soundattract.util.ArmorSlots.get(player)) {
            if (stack == null || stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (wool ? isWoolArmor(item) : isLeafArmor(item)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isWoolArmor(Item item) {
        return item == TANItems.WOOL_HELMET
                || item == TANItems.WOOL_CHESTPLATE
                || item == TANItems.WOOL_LEGGINGS
                || item == TANItems.WOOL_BOOTS;
    }

    private static boolean isLeafArmor(Item item) {
        return item == TANItems.LEAF_HELMET
                || item == TANItems.LEAF_CHESTPLATE
                || item == TANItems.LEAF_LEGGINGS
                || item == TANItems.LEAF_BOOTS;
    }

    private static double temperatureScentMultiplier(TemperatureLevel level) {
        EnumMap<TemperatureLevel, Double> values = new EnumMap<>(TemperatureLevel.class);
        for (String raw : SoundAttractConfig.COMMON.toughAsNailsTemperatureScentMultipliers.get()) {
            String[] parts = raw.split(";", 2);
            if (parts.length != 2) continue;
            try {
                TemperatureLevel parsedLevel = TemperatureLevel.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
                values.put(parsedLevel, config(Double.parseDouble(parts[1].trim())));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return values.getOrDefault(level == null ? TemperatureLevel.NEUTRAL : level, 1.0);
    }

    private static double woolSoundMultiplier(int woolPieces) {
        if (woolPieces <= 0) return 1.0;
        double perPiece = config(SoundAttractConfig.COMMON.toughAsNailsWoolArmorSoundMultiplierPerPiece.get());
        double minimum = config(SoundAttractConfig.COMMON.toughAsNailsWoolArmorMinimumSoundMultiplier.get());
        return Math.max(minimum, Math.pow(perPiece, woolPieces));
    }

    private static double woolHeatScentMultiplier(TemperatureLevel level, int woolPieces) {
        if (woolPieces <= 0) return 1.0;
        double perPiece;
        if (level == TemperatureLevel.HOT) {
            perPiece = config(SoundAttractConfig.COMMON.toughAsNailsWoolHotScentMultiplierPerPiece.get());
        } else if (level == TemperatureLevel.WARM) {
            perPiece = config(SoundAttractConfig.COMMON.toughAsNailsWoolWarmScentMultiplierPerPiece.get());
        } else {
            return 1.0;
        }
        double maximum = config(SoundAttractConfig.COMMON.toughAsNailsWoolHeatMaximumScentMultiplier.get());
        return Math.min(maximum, Math.pow(perPiece, woolPieces));
    }

    private static double leafScentMultiplier(int leafPieces) {
        if (leafPieces <= 0) return 1.0;
        double perPiece = config(SoundAttractConfig.COMMON.toughAsNailsLeafArmorScentMultiplierPerPiece.get());
        double minimum = config(SoundAttractConfig.COMMON.toughAsNailsLeafArmorMinimumScentMultiplier.get());
        return Math.max(minimum, Math.pow(perPiece, leafPieces));
    }

    private static double lerp(double from, double to, double amount) {
        double clamped = Math.max(0.0, Math.min(1.0, amount));
        return from + (to - from) * clamped;
    }

    private static double config(double value) {
        return Double.isFinite(value) && value >= 0.0 ? value : 1.0;
    }
}
