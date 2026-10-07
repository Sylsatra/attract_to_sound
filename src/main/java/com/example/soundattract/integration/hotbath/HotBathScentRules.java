package com.example.soundattract.integration.hotbath;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HotBathScentRules {
    private static volatile Map<ResourceLocation, HotBathConfigParser.FluidModifier> scentModifiers = Map.of();
    private static volatile Map<ResourceLocation, HotBathConfigParser.CamoWashRate> camoWashRates = Map.of();
    private static volatile Map<ResourceLocation, HotBathConfigParser.CamoWashRate> splashWashRates = Map.of();
    private static volatile List<HotBathConfigParser.BiomeModifier> biomeModifiers = List.of();

    private HotBathScentRules() {}

    public static void rebuildFromConfig() {
        Map<ResourceLocation, HotBathConfigParser.FluidModifier> newScentModifiers = new HashMap<>();
        Map<ResourceLocation, HotBathConfigParser.CamoWashRate> newCamoWashRates = new HashMap<>();
        Map<ResourceLocation, HotBathConfigParser.CamoWashRate> newSplashWashRates = new HashMap<>();
        List<HotBathConfigParser.BiomeModifier> biome = new ArrayList<>();

        if (SoundAttractConfig.COMMON == null) {
            scentModifiers = Map.of();
            camoWashRates = Map.of();
            splashWashRates = Map.of();
            biomeModifiers = List.of();
            return;
        }

        parseFluidList(SoundAttractConfig.COMMON.hotBathFluidScentModifiers.get(), newScentModifiers);
        parseFluidList(SoundAttractConfig.COMMON.hotBathCustomFluidScentModifiers.get(), newScentModifiers);
        parseWashList(SoundAttractConfig.COMMON.hotBathCamoWashRates.get(), newCamoWashRates);
        parseWashList(SoundAttractConfig.COMMON.hotBathCustomFluidCamoWashRates.get(), newCamoWashRates);
        parseWashList(SoundAttractConfig.COMMON.hotBathSplashCamoWashRates.get(), newSplashWashRates);

        for (String raw : SoundAttractConfig.COMMON.hotBathBiomeScentModifiers.get()) {
            HotBathConfigParser.parseBiomeModifier(raw).ifPresentOrElse(
                    biome::add,
                    () -> warnInvalid("biome scent modifier", raw));
        }
        scentModifiers = Map.copyOf(newScentModifiers);
        camoWashRates = Map.copyOf(newCamoWashRates);
        splashWashRates = Map.copyOf(newSplashWashRates);
        biomeModifiers = List.copyOf(biome);
    }

    public static HotBathConfigParser.FluidModifier scentModifier(ResourceLocation fluidId) {
        return fluidId == null ? null : scentModifiers.get(fluidId);
    }

    public static HotBathConfigParser.CamoWashRate camoWashRate(ResourceLocation fluidId) {
        return fluidId == null ? null : camoWashRates.get(fluidId);
    }

    public static HotBathConfigParser.CamoWashRate splashWashRate(ResourceLocation fluidId) {
        return fluidId == null ? null : splashWashRates.get(fluidId);
    }

    public static double biomeMultiplier(ResourceLocation fluidId, Level level, net.minecraft.core.BlockPos pos) {
        if (fluidId == null || level == null || pos == null) return 1.0;
        double multiplier = 1.0;
        for (HotBathConfigParser.BiomeModifier modifier : biomeModifiers) {
            if (fluidId.equals(modifier.fluidId()) && matchesBiome(modifier.biomeMatcher(), level, pos)) {
                multiplier *= modifier.multiplier();
            }
        }
        return multiplier;
    }

    private static boolean matchesBiome(HotBathConfigParser.BiomeMatcher matcher, Level level, net.minecraft.core.BlockPos pos) {
        var holder = level.getBiome(pos);
        if (matcher.isTag()) {
            return holder.is(TagKey.create(Registries.BIOME, matcher.id()));
        }
        ResourceLocation biomeId = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(holder.value());
        return matcher.id().equals(biomeId);
    }

    private static void parseFluidList(List<? extends String> rawList, Map<ResourceLocation, HotBathConfigParser.FluidModifier> target) {
        for (String raw : rawList) {
            HotBathConfigParser.parseFluidModifier(raw).ifPresentOrElse(
                    modifier -> target.put(modifier.fluidId(), modifier),
                    () -> warnInvalid("fluid scent modifier", raw));
        }
    }

    private static void parseWashList(List<? extends String> rawList, Map<ResourceLocation, HotBathConfigParser.CamoWashRate> target) {
        for (String raw : rawList) {
            HotBathConfigParser.parseCamoWashRate(raw).ifPresentOrElse(
                    rate -> target.put(rate.fluidId(), rate),
                    () -> warnInvalid("camo wash rate", raw));
        }
    }

    private static void warnInvalid(String kind, String raw) {
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.HOT_BATH)) {
            SoundAttractMod.LOGGER.warn("[HotBathIntegration] Ignoring invalid {} entry '{}'", kind, raw);
        }
    }
}