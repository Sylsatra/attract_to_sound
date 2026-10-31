package com.example.soundattract.camo;

import com.example.soundattract.config.separate.StealthConfig;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import com.mojang.blaze3d.systems.RenderSystem;

public class CamoTextureGenerator {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<Identifier, NativeImage> CAPTURED_MASKS = new ConcurrentHashMap<>();
    private static final int MAX_CAPTURED_MASKS = 512;
    private static final int MAX_GENERATED_TEXTURES = 256;
    private static final int MAX_RESOURCE_LOOKUP_ENTRIES = 512;
    private static final Set<Identifier> READBACK_FAILURES = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<Identifier, Identifier> REDIRECT_CACHE = new ConcurrentHashMap<>();
    private static final Identifier NULL_LOCATION = Identifier.tryBuild("soundattract", "null");

    public static void recordCapturedMask(Identifier loc, NativeImage img) {
        cacheCapturedMask(loc, img);
    }

    public static void recordCapturedMaskCopy(Identifier loc, NativeImage source) {
        if (loc == null || source == null) return;
        NativeImage copy = null;
        try {
            copy = new NativeImage(source.getWidth(), source.getHeight(), false);
            copy.copyFrom(source);
            cacheCapturedMask(loc, copy);
        } catch (RuntimeException e) {
            if (copy != null) copy.close();
            throw e;
        }
    }

    private static synchronized void cacheCapturedMask(Identifier loc, NativeImage image) {
        if (loc == null || image == null) return;
        NativeImage old = CAPTURED_MASKS.put(loc, image);
        if (old != null && old != image) old.close();


        READBACK_FAILURES.remove(loc);
        while (CAPTURED_MASKS.size() > MAX_CAPTURED_MASKS) {
            Map.Entry<Identifier, NativeImage> eldest = CAPTURED_MASKS.entrySet().iterator().next();
            if (CAPTURED_MASKS.remove(eldest.getKey(), eldest.getValue())) eldest.getValue().close();
        }
    }

    private static void recordReadbackFailure(Identifier loc) {
        READBACK_FAILURES.add(loc);
        while (READBACK_FAILURES.size() > MAX_RESOURCE_LOOKUP_ENTRIES) {
            Identifier evicted = READBACK_FAILURES.iterator().next();
            READBACK_FAILURES.remove(evicted);
        }
    }

    private static void cacheRedirect(Identifier source, Identifier target) {
        REDIRECT_CACHE.put(source, target);
        while (REDIRECT_CACHE.size() > MAX_RESOURCE_LOOKUP_ENTRIES) {
            Identifier evicted = REDIRECT_CACHE.keySet().iterator().next();
            REDIRECT_CACHE.remove(evicted);
        }
    }

    public static synchronized void releaseCapturedMask(Identifier loc) {
        NativeImage img = CAPTURED_MASKS.remove(loc);
        if (img != null) {
            img.close();
        }
    }

    private static final Map<TextureKey, Identifier> CACHE = new ConcurrentHashMap<>();
    private static final Map<MaskedTextureKey, Identifier> MASKED_CACHE = new ConcurrentHashMap<>();
    public static Identifier getOrCreateSmudge(long seed, int color, float strength, float erosion, float humidity, float temp) {
        return getOrCreateSmudge(seed, color, strength, StealthConfig.CAMO_TEXTURE_RESOLUTION.get(), erosion, humidity, temp);
    }

    public static Identifier getOrCreateSmudge(long seed, int color, float strength, int resolution, float erosion, float humidity, float temp) {
        float strengthTier = Math.round(strength * 50.0f) / 50.0f;
        TextureKey key = new TextureKey(seed, color, strengthTier, resolution, erosion, humidity, temp);

        Identifier existing = CACHE.get(key);
        if (existing != null) {
            return existing;
        }

        if (CACHE.size() >= MAX_GENERATED_TEXTURES) {
            clearCaches();
        }

        Identifier loc = generateTexture(key);
        CACHE.put(key, loc);
        return loc;
    }

    public static Identifier getOrCreateMaskedSmudge(long seed, int color, float strength, Identifier maskLoc, int resolution, float erosion, float humidity, float temp) {
        return getOrCreateMaskedSmudge(seed, color, strength, Collections.singletonList(maskLoc), resolution, erosion, humidity, temp);
    }

    public static Identifier getOrCreateMaskedSmudge(long seed, int color, float strength, java.util.List<Identifier> maskLocs, int resolution, float erosion, float humidity, float temp) {
        float strengthTier = Math.round(strength * 50.0f) / 50.0f;
        MaskedTextureKey key = new MaskedTextureKey(seed, color, strengthTier, maskLocs, resolution, erosion, humidity, temp);

        Identifier existing = MASKED_CACHE.get(key);
        if (existing != null) {
            return existing == NULL_LOCATION ? null : existing;
        }

        if (MASKED_CACHE.size() >= MAX_GENERATED_TEXTURES) {
            clearCaches();
        }

        Identifier loc = generateMaskedTexture(key);
        MASKED_CACHE.put(key, loc == null ? NULL_LOCATION : loc);
        return loc;
    }

    public static void clearCaches() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        Runnable clear = () -> {
            for (Identifier loc : CACHE.values()) {
                mc.getTextureManager().release(loc);
            }
            CACHE.clear();

            for (Identifier loc : MASKED_CACHE.values()) {
                mc.getTextureManager().release(loc);
            }
            MASKED_CACHE.clear();
            synchronized (CamoTextureGenerator.class) {
                for (NativeImage image : CAPTURED_MASKS.values()) image.close();
                CAPTURED_MASKS.clear();
            }
            REDIRECT_CACHE.clear();
            READBACK_FAILURES.clear();
        };

        if (RenderSystem.isOnRenderThread()) {
            clear.run();
        } else {
            mc.execute(clear);
        }
    }

    private static NativeImage extractAlphaMask(Identifier loc) {
        if (loc == null) return null;
        try {
            synchronized (CamoTextureGenerator.class) {
                NativeImage captured = CAPTURED_MASKS.get(loc);
                if (captured != null) {
                    NativeImage copy = new NativeImage(captured.getWidth(), captured.getHeight(), false);
                    copy.copyFrom(captured);
                    return copy;
                }
            }
            if (READBACK_FAILURES.contains(loc)) return null;
            var resource = Minecraft.getInstance().getResourceManager().getResource(loc);
            if (resource.isPresent()) {
                try (var input = resource.get().open()) {
                    return NativeImage.read(input);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Camo] Could not load mask {}", loc, e);
        }
        recordReadbackFailure(loc);
        return null;
    }

    private static Identifier generateMaskedTexture(MaskedTextureKey key) {
        if (key.maskLocs == null || key.maskLocs.isEmpty()) return null;

        java.util.List<NativeImage> masks = new java.util.ArrayList<>();
        try {
            for (Identifier loc : key.maskLocs) {
                NativeImage mask = extractAlphaMask(loc);
                if (mask != null) masks.add(mask);
            }
            if (masks.isEmpty()) return null;
            return generateMaskedTexture(key, masks);
        } finally {
            for (NativeImage mask : masks) mask.close();
        }
    }

    private static Identifier generateMaskedTexture(MaskedTextureKey key, java.util.List<NativeImage> masks) {
        int maxWidth = 0;
        int maxHeight = 0;
        for (NativeImage mask : masks) {
            maxWidth = Math.max(maxWidth, mask.getWidth());
            maxHeight = Math.max(maxHeight, mask.getHeight());
        }

        NativeImage masterMask = null;
        NativeImage image = null;
        DynamicTexture texture = null;
        try {
        if (masks.size() == 1) {
            masterMask = masks.get(0);
        } else {
            masterMask = new NativeImage(maxWidth, maxHeight, true);
            for (NativeImage m : masks) {
                for (int mx = 0; mx < maxWidth; mx++) {
                    for (int my = 0; my < maxHeight; my++) {
                        int srcX = (int) ((mx / (float) maxWidth) * m.getWidth());
                        int srcY = (int) ((my / (float) maxHeight) * m.getHeight());
                        
                        int srcPixel = m.getPixel(srcX, srcY);
                        int srcAlpha = (srcPixel >> 24) & 0xFF;
                        
                        if (srcAlpha > 0) {
                            int existingPixel = masterMask.getPixel(mx, my);
                            int existingAlpha = (existingPixel >> 24) & 0xFF;
                            
                            if (srcAlpha > existingAlpha) {
                                masterMask.setPixelABGR(mx, my, (srcAlpha << 24) | (srcPixel & 0xFFFFFF));
                            }
                        }
                    }
                }
            }
        }

        int width = key.resolution;
        int height = key.resolution;
        
        width = Math.max(16, Math.min(width, 4096));
        height = Math.max(16, Math.min(height, 4096));

        image = new NativeImage(width, height, true);
        Random random = new Random(key.seed);


        float[][] noise = new float[width][height];
        float min = 1.0f, max = 0.0f;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                noise[x][y] = random.nextFloat();
            }
        }


        float[][] nextNoise = new float[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                float sum = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        sum += noise[(x + dx + width) % width][(y + dy + height) % height];
                    }
                }
                nextNoise[x][y] = sum / 9.0f;
                min = Math.min(min, nextNoise[x][y]);
                max = Math.max(max, nextNoise[x][y]);
            }
        }
        noise = nextNoise;


        boolean jagged = key.erosion < 0;
        boolean humid = key.humidity > 0;
        
        float threshold = 0.95f - (key.strength * 0.8f);
        int r = (key.color >> 16) & 0xFF;
        int g = (key.color >> 8) & 0xFF;
        int b = key.color & 0xFF;
        float range = (max - min) + 0.0001f;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int maskX = (int) ((x / (float) width) * masterMask.getWidth());
                int maskY = (int) ((y / (float) height) * masterMask.getHeight());
                int maskPixel = masterMask.getPixel(maskX, maskY);
                int maskAlpha = (maskPixel >> 24) & 0xFF;
                if (maskAlpha == 0) {
                    image.setPixelABGR(x, y, 0);
                    continue;
                }

                float rawNorm = (noise[x][y] - min) / range;
                float finalVal;

                if (jagged) {
                    if (humid) {
                        finalVal = (float) Math.pow(rawNorm, 3.0);
                    } else {
                        finalVal = Math.round(rawNorm * 4.0f) / 4.0f;
                    }
                } else {
                    if (humid) {
                        finalVal = (float) Math.pow(rawNorm, 1.5);
                    } else {
                        float ripple = (float) Math.sin(x * 0.5 + y * 0.2);
                        finalVal = (rawNorm * 0.7f) + (ripple * 0.3f);
                    }
                }

                if (finalVal >= threshold) {
                    float alphaStep = (finalVal - threshold) / (1.0f - threshold + 0.001f);
                    int alpha = (int) (Math.min(0.75f, key.strength * 0.8f) * 255 * (0.5f + alphaStep * 0.5f));
                    alpha = (int) (alpha * (maskAlpha / 255.0f));
                    int abgr = (alpha << 24) | (b << 16) | (g << 8) | r;
                    image.setPixelABGR(x, y, abgr);
                } else {
                    image.setPixelABGR(x, y, 0);
                }
            }
        }

        texture = new DynamicTexture(() -> "soundattract camo texture", image);
        image = null;
        int strengthCode = (int)(key.strength * 10);
        String maskHash = String.valueOf(key.maskLocs.hashCode());
        String name = "generated/camo_masked/" + Math.abs(key.seed) + "_" + strengthCode + "_" + key.color + "_" + maskHash + "_" + key.resolution;
        Identifier loc = Identifier.tryBuild("soundattract", name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_/.]", "_"));
        Minecraft.getInstance().getTextureManager().register(loc, texture);
        texture = null;
        return loc;
        } finally {
            if (image != null) image.close();
            if (masterMask != null && !masks.contains(masterMask)) masterMask.close();
            if (texture != null) texture.close();
        }
    }

    private static Identifier generateTexture(TextureKey key) {
        int res = key.resolution;
        NativeImage image = new NativeImage(res, res, true);
        DynamicTexture texture = null;
        try {
        Random random = new Random(key.seed);


        float[][] noise = new float[res][res];
        float min = 1.0f;
        float max = 0.0f;
        for (int x = 0; x < res; x++) {
            for (int y = 0; y < res; y++) {
                noise[x][y] = random.nextFloat();
            }
        }


        for (int pass = 0; pass < 2; pass++) {
            float[][] nextNoise = new float[res][res];
            for (int x = 0; x < res; x++) {
                for (int y = 0; y < res; y++) {
                    float sum = 0;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            sum += noise[(x + dx + res) % res][(y + dy + res) % res];
                        }
                    }
                    nextNoise[x][y] = sum / 9.0f;
                }
            }
            noise = nextNoise;
        }


        for (int x = 0; x < res; x++) {
            for (int y = 0; y < res; y++) {
                min = Math.min(min, noise[x][y]);
                max = Math.max(max, noise[x][y]);
            }
        }



        boolean jagged = key.erosion < 0;
        boolean humid = key.humidity > 0;
        
        float threshold = 0.95f - (key.strength * 0.8f);
        int r = (key.color >> 16) & 0xFF;
        int g = (key.color >> 8) & 0xFF;
        int b = key.color & 0xFF;
        float range = (max - min) + 0.0001f;

        for (int x = 0; x < res; x++) {
            for (int y = 0; y < res; y++) {
                float rawNorm = (noise[x][y] - min) / range;
                float finalVal;

                if (jagged) {
                    if (humid) finalVal = (float) Math.pow(rawNorm, 3.0);
                    else finalVal = Math.round(rawNorm * 4.0f) / 4.0f;
                } else {
                    if (humid) finalVal = (float) Math.pow(rawNorm, 1.5);
                    else { 
                        float ripple = (float) Math.sin(x * 0.5 + y * 0.2);
                        finalVal = (rawNorm * 0.7f) + (ripple * 0.3f);
                    }
                }

                if (finalVal >= threshold) {
                    float alphaStep = (finalVal - threshold) / (1.0f - threshold + 0.001f);
                    int alpha = (int) (Math.min(0.75f, key.strength * 0.8f) * 255 * (0.5f + alphaStep * 0.5f));
                    int abgr = (alpha << 24) | (b << 16) | (g << 8) | r;
                    image.setPixelABGR(x, y, abgr);
                } else {
                    image.setPixelABGR(x, y, 0);
                }
            }
        }

        texture = new DynamicTexture(() -> "soundattract camo texture", image);
        image = null;

        int strengthCode = (int)(key.strength * 10);
        String name = "generated/camo/" + Math.abs(key.seed) + "_" + strengthCode + "_" + key.color;
        Identifier loc = Identifier.tryBuild("soundattract", name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_/.]", "_"));
        Minecraft.getInstance().getTextureManager().register(loc, texture);
        texture = null;
        return loc;
        } finally {
            if (image != null) image.close();
            if (texture != null) texture.close();
        }
    }

    private record TextureKey(long seed, int color, float strength, int resolution, float erosion, float humidity, float temp) {}
    private record MaskedTextureKey(long seed, int color, float strength, java.util.List<Identifier> maskLocs, int resolution, float erosion, float humidity, float temp) {}

}
