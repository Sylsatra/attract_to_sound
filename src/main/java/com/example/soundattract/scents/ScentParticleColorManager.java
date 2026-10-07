package com.example.soundattract.scents;

import com.example.soundattract.config.PlayerProfile2;
import com.example.soundattract.config.ScentEmissionConfig;
import com.example.soundattract.config.SoundAttractConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ScentParticleColorManager {

    private static final Cache<UUID, Vector3f> colorCache = Caffeine.newBuilder()
            .maximumSize(4096)
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .build();


    public static Vector3f getColorForPlayer(UUID playerUUID) {
        if (playerUUID == null) {
            return new Vector3f(1.0f, 1.0f, 1.0f);
        }
        return colorCache.get(playerUUID, ScentParticleColorManager::computeColor);
    }


    public static Vector3f getColorForPlayer(UUID playerUUID, Player player) {

        PlayerProfile2 profile = player == null ? null : SoundAttractConfig.getMatchingPlayerProfile(player);
        if (profile != null && profile.scentEmission().isPresent()) {
            ScentEmissionConfig scentConfig = profile.scentEmission().get();
            String hexColor = scentConfig.scentParticleColor();
            if (hexColor != null && !hexColor.isEmpty()) {
                return parseHexColor(hexColor);
            }
        }
        return getColorForPlayer(playerUUID);
    }


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


    public static void clearCache() {
        colorCache.invalidateAll();
    }
}