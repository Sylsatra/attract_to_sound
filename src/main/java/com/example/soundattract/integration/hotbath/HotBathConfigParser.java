package com.example.soundattract.integration.hotbath;

import net.minecraft.resources.Identifier;

import java.util.Optional;

public final class HotBathConfigParser {
    private HotBathConfigParser() {
    }

    public record FluidModifier(Identifier fluidId, double scentMultiplier, int durationTicks) {
    }

    public record BiomeModifier(Identifier fluidId, BiomeMatcher biomeMatcher, double multiplier) {
    }

    public record CamoWashRate(Identifier fluidId, float skinRate, float armorRate) {
    }

    public record BiomeMatcher(Identifier id, boolean isTag) {
        public static Optional<BiomeMatcher> parse(String raw) {
            if (raw == null || raw.isBlank()) return Optional.empty();
            String trimmed = raw.trim();
            boolean tag = trimmed.startsWith("#");
            Identifier id = Identifier.tryParse(tag ? trimmed.substring(1) : trimmed);
            if (id == null) return Optional.empty();
            return Optional.of(new BiomeMatcher(id, tag));
        }
    }

    public static Optional<FluidModifier> parseFluidModifier(String raw) {
        String[] parts = split(raw, 3);
        if (parts == null) return Optional.empty();
        Identifier fluidId = Identifier.tryParse(parts[0]);
        if (fluidId == null) return Optional.empty();
        try {
            return Optional.of(new FluidModifier(
                    fluidId,
                    clampNonNegative(Double.parseDouble(parts[1])),
                    Math.max(0, Integer.parseInt(parts[2]))));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<BiomeModifier> parseBiomeModifier(String raw) {
        String[] parts = split(raw, 3);
        if (parts == null) return Optional.empty();
        Identifier fluidId = Identifier.tryParse(parts[0]);
        if (fluidId == null) return Optional.empty();
        Optional<BiomeMatcher> matcher = BiomeMatcher.parse(parts[1]);
        if (matcher.isEmpty()) return Optional.empty();
        try {
            return Optional.of(new BiomeModifier(fluidId, matcher.get(), clampNonNegative(Double.parseDouble(parts[2]))));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<CamoWashRate> parseCamoWashRate(String raw) {
        String[] parts = split(raw, 3);
        if (parts == null) return Optional.empty();
        Identifier fluidId = Identifier.tryParse(parts[0]);
        if (fluidId == null) return Optional.empty();
        try {
            return Optional.of(new CamoWashRate(
                    fluidId,
                    clamp01(Float.parseFloat(parts[1])),
                    clamp01(Float.parseFloat(parts[2]))));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static String[] split(String raw, int expectedParts) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.trim().split(";", -1);
        if (parts.length != expectedParts) return null;
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
            if (parts[i].isEmpty()) return null;
        }
        return parts;
    }

    private static double clampNonNegative(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, value);
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
