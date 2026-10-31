package com.example.soundattract.integration.hotbath;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HotBathScentRules {
    private static final Map<Identifier, HotBathConfigParser.FluidModifier> SCENT_MODIFIERS = new ConcurrentHashMap<>();
    private static final Map<Identifier, HotBathConfigParser.CamoWashRate> CAMO_WASH_RATES = new ConcurrentHashMap<>();
    private static final Map<Identifier, HotBathConfigParser.CamoWashRate> SPLASH_WASH_RATES = new ConcurrentHashMap<>();
    private static volatile List<HotBathConfigParser.BiomeModifier> biomeModifiers = List.of();

    private HotBathScentRules() {
    }

    public static void rebuildFromConfig() {
        SCENT_MODIFIERS.clear();
        CAMO_WASH_RATES.clear();
        SPLASH_WASH_RATES.clear();
        List<HotBathConfigParser.BiomeModifier> biome = new ArrayList<>();

        if (SoundAttractConfig.COMMON == null) {
            biomeModifiers = List.of();
            return;
        }

        parseFluidList(SoundAttractConfig.COMMON.hotBathFluidScentModifiers.get(), SCENT_MODIFIERS);
        parseFluidList(SoundAttractConfig.COMMON.hotBathCustomFluidScentModifiers.get(), SCENT_MODIFIERS);
        parseWashList(SoundAttractConfig.COMMON.hotBathCamoWashRates.get(), CAMO_WASH_RATES);
        parseWashList(SoundAttractConfig.COMMON.hotBathCustomFluidCamoWashRates.get(), CAMO_WASH_RATES);
        parseWashList(SoundAttractConfig.COMMON.hotBathSplashCamoWashRates.get(), SPLASH_WASH_RATES);

        for (String raw : SoundAttractConfig.COMMON.hotBathBiomeScentModifiers.get()) {
            HotBathConfigParser.parseBiomeModifier(raw).ifPresentOrElse(
                    biome::add,
                    () -> warnInvalid("biome scent modifier", raw));
        }
        biomeModifiers = List.copyOf(biome);
    }

    public static HotBathConfigParser.FluidModifier scentModifier(Identifier fluidId) {
        return fluidId == null ? null : SCENT_MODIFIERS.get(fluidId);
    }

    public static HotBathConfigParser.CamoWashRate camoWashRate(Identifier fluidId) {
        return fluidId == null ? null : CAMO_WASH_RATES.get(fluidId);
    }

    public static HotBathConfigParser.CamoWashRate splashWashRate(Identifier fluidId) {
        return fluidId == null ? null : SPLASH_WASH_RATES.get(fluidId);
    }

    public static double biomeMultiplier(Identifier fluidId, Level level, net.minecraft.core.BlockPos pos) {
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
        Identifier biomeId = level.registryAccess().lookupOrThrow(Registries.BIOME).getKey(holder.value());
        return matcher.id().equals(biomeId);
    }

    private static void parseFluidList(List<? extends String> rawList, Map<Identifier, HotBathConfigParser.FluidModifier> target) {
        for (String raw : rawList) {
            HotBathConfigParser.parseFluidModifier(raw).ifPresentOrElse(
                    modifier -> target.put(modifier.fluidId(), modifier),
                    () -> warnInvalid("fluid scent modifier", raw));
        }
    }

    private static void parseWashList(List<? extends String> rawList, Map<Identifier, HotBathConfigParser.CamoWashRate> target) {
        for (String raw : rawList) {
            HotBathConfigParser.parseCamoWashRate(raw).ifPresentOrElse(
                    rate -> target.put(rate.fluidId(), rate),
                    () -> warnInvalid("camo wash rate", raw));
        }
    }

    private static void warnInvalid(String kind, String raw) {
        if (SoundAttractConfig.COMMON != null && SoundAttractConfig.COMMON.debugLogging != null && SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.warn("[HotBathIntegration] Ignoring invalid {} entry '{}'", kind, raw);
        }
    }
}
