package com.example.soundattract.scents;

import com.example.soundattract.config.PlayerProfile2;
import com.example.soundattract.config.ScentEmissionConfig;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player scent particle colors.
 * By default, hashes UUID → unique HSB hue. Profile overrides take priority.
 */
public class ScentParticleColorManager {

    private static final Map<UUID, Vector3f> colorCache = new ConcurrentHashMap<>();

    /**
     * Get the RGB color for a player's scent particles.
     * Priority: profile hex override → deterministic UUID hash.
     */
    public static Vector3f getColorForPlayer(UUID playerUUID) {
        return colorCache.computeIfAbsent(playerUUID, ScentParticleColorManager::computeColor);
    }

    /**
     * Get the color for a player, checking their profile override first.
     */
    public static Vector3f getColorForPlayer(UUID playerUUID, Player player) {

        PlayerProfile2 profile = SoundAttractConfig.getMatchingPlayerProfile(player);
        if (profile != null && profile.scentEmission().isPresent()) {
            ScentEmissionConfig scentConfig = profile.scentEmission().get();
            String hexColor = scentConfig.scentParticleColor();
            if (hexColor != null && !hexColor.isEmpty()) {
                return parseHexColor(hexColor);
            }
        }
        return getColorForPlayer(playerUUID);
    }

    /**
     * Deterministic color from UUID. Uses the UUID hash to pick a hue,
     * with fixed saturation and brightness for visibility.
     */
    private static Vector3f computeColor(UUID uuid) {

        float hue = ((uuid.hashCode() & 0x7FFFFFFF) % 360) / 360.0f;
        float saturation = 0.7f;
        float brightness = 0.9f;

        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;

        return new Vector3f(r, g, b);
    }

    /**
     * Parses a hex color string like "#FF0000" or "FF0000" into an RGB Vector3f.
     */
    private static Vector3f parseHexColor(String hex) {
        try {
            hex = hex.trim();
            if (hex.startsWith("#")) hex = hex.substring(1);
            int color = Integer.parseInt(hex, 16);
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            return new Vector3f(r, g, b);
        } catch (NumberFormatException e) {

            return new Vector3f(1.0f, 1.0f, 1.0f);
        }
    }

    /**
     * Clear the color cache (e.g., on config reload).
     */
    public static void clearCache() {
        colorCache.clear();
    }
}
