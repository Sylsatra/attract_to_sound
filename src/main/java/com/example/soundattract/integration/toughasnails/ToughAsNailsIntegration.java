package com.example.soundattract.integration.toughasnails;

import com.example.soundattract.ModChecker;
import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
    private static boolean registered;

    private ToughAsNailsIntegration() {
    }

    public static void register() {
        if (registered || !isEnabledAndLoaded()) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(ToughAsNailsIntegration::onServerTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                ToughAsNailsStateCache.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            cursor = 0;
            ToughAsNailsStateCache.clear();
        });
    }

    private static boolean isEnabledAndLoaded() {
        return ModChecker.isLoaded("toughasnails")
                && SoundAttractConfig.COMMON != null
                && SoundAttractConfig.COMMON.enableToughAsNailsIntegration.get();
    }

    private static void onServerTick(MinecraftServer server) {
        if (!isEnabledAndLoaded()) {
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        int interval = Math.max(1, SoundAttractConfig.COMMON.toughAsNailsPollIntervalTicks.get());
        int perTick = Math.max(1, (players.size() + interval - 1) / interval);
        for (int i = 0; i < perTick && !players.isEmpty(); i++) {
            if (cursor >= players.size()) {
                cursor = 0;
            }
            updatePlayer(players.get(cursor++));
        }
    }

    private static void updatePlayer(ServerPlayer player) {
        if (player == null || player.isRemoved()) {
            return;
        }
        long now = player.level().getGameTime();
        try {
            TemperatureLevel temperature = currentTemperature(player);
            int woolPieces = countArmorPieces(player, true);
            int leafPieces = countArmorPieces(player, false);
            double scent = temperatureScentMultiplier(temperature);
            double sound = 1.0;
            double thirst = thirstFactor(player);
            scent *= lerp(1.0, config(SoundAttractConfig.COMMON.toughAsNailsThirstScentMultiplierAtEmpty.get()), thirst);
            sound *= lerp(1.0, config(SoundAttractConfig.COMMON.toughAsNailsThirstSoundMultiplierAtEmpty.get()), thirst);
            if (isHyperthermic(player)) {
                scent *= config(SoundAttractConfig.COMMON.toughAsNailsHyperthermiaScentMultiplier.get());
                sound *= config(SoundAttractConfig.COMMON.toughAsNailsHyperthermiaSoundMultiplier.get());
            }
            sound *= woolSoundMultiplier(woolPieces);
            scent *= woolHeatScentMultiplier(temperature, woolPieces);
            scent *= leafScentMultiplier(leafPieces);
            ToughAsNailsStateCache.update(player.getUUID(), new ToughAsNailsStateCache.State(config(scent), config(sound), now));
        } catch (Throwable exception) {
            ToughAsNailsStateCache.update(player.getUUID(), new ToughAsNailsStateCache.State(1.0, 1.0, now));
            Soundattract.LOGGER.warn("Unable to update Tough As Nails sound and scent modifiers for {}", player.getName().getString(), exception);
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
            if (!ThirstHelper.isThirstEnabled()) {
                return 0.0;
            }
            IThirst thirst = ThirstHelper.getThirst(player);
            if (thirst == null) {
                return 0.0;
            }
            int value = Math.max(0, Math.min(20, thirst.getThirst()));
            return (20.0 - value) / 20.0;
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private static int countArmorPieces(ServerPlayer player, boolean wool) {
        int count = 0;
        for (ItemStack stack : com.example.soundattract.util.ArmorSlots.get(player)) {
            if (!stack.isEmpty() && (wool ? isWoolArmor(stack.getItem()) : isLeafArmor(stack.getItem()))) {
                count++;
            }
        }
        return count;
    }

    private static boolean isWoolArmor(Item item) {
        return item == TANItems.WOOL_HELMET || item == TANItems.WOOL_CHESTPLATE
                || item == TANItems.WOOL_LEGGINGS || item == TANItems.WOOL_BOOTS;
    }

    private static boolean isLeafArmor(Item item) {
        return item == TANItems.LEAF_HELMET || item == TANItems.LEAF_CHESTPLATE
                || item == TANItems.LEAF_LEGGINGS || item == TANItems.LEAF_BOOTS;
    }

    private static double temperatureScentMultiplier(TemperatureLevel level) {
        EnumMap<TemperatureLevel, Double> values = new EnumMap<>(TemperatureLevel.class);
        for (String raw : SoundAttractConfig.COMMON.toughAsNailsTemperatureScentMultipliers.get()) {
            String[] parts = raw.split(";", 2);
            if (parts.length != 2) {
                continue;
            }
            try {
                values.put(TemperatureLevel.valueOf(parts[0].trim().toUpperCase(Locale.ROOT)), config(Double.parseDouble(parts[1].trim())));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return values.getOrDefault(level == null ? TemperatureLevel.NEUTRAL : level, 1.0);
    }

    private static double woolSoundMultiplier(int pieces) {
        if (pieces <= 0) {
            return 1.0;
        }
        double perPiece = config(SoundAttractConfig.COMMON.toughAsNailsWoolArmorSoundMultiplierPerPiece.get());
        double minimum = config(SoundAttractConfig.COMMON.toughAsNailsWoolArmorMinimumSoundMultiplier.get());
        return Math.max(minimum, Math.pow(perPiece, pieces));
    }

    private static double woolHeatScentMultiplier(TemperatureLevel level, int pieces) {
        if (pieces <= 0 || (level != TemperatureLevel.HOT && level != TemperatureLevel.WARM)) {
            return 1.0;
        }
        double perPiece = level == TemperatureLevel.HOT
                ? config(SoundAttractConfig.COMMON.toughAsNailsWoolHotScentMultiplierPerPiece.get())
                : config(SoundAttractConfig.COMMON.toughAsNailsWoolWarmScentMultiplierPerPiece.get());
        double maximum = config(SoundAttractConfig.COMMON.toughAsNailsWoolHeatMaximumScentMultiplier.get());
        return Math.min(maximum, Math.pow(perPiece, pieces));
    }

    private static double leafScentMultiplier(int pieces) {
        if (pieces <= 0) {
            return 1.0;
        }
        double perPiece = config(SoundAttractConfig.COMMON.toughAsNailsLeafArmorScentMultiplierPerPiece.get());
        double minimum = config(SoundAttractConfig.COMMON.toughAsNailsLeafArmorMinimumScentMultiplier.get());
        return Math.max(minimum, Math.pow(perPiece, pieces));
    }

    private static double lerp(double from, double to, double amount) {
        double clamped = Math.max(0.0, Math.min(1.0, amount));
        return from + (to - from) * clamped;
    }

    private static double config(double value) {
        return Double.isFinite(value) && value >= 0.0 ? value : 1.0;
    }
}
