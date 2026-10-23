package com.example.soundattract.integration.enhancedai;

import com.example.soundattract.config.SoundAttractConfig;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.block.Block;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public class EnhancedAICompat {

    private static final boolean IS_ENHANCED_AI_LOADED = FabricLoader.getInstance().isModLoaded("enhancedai");

    private static final int DEFAULT_MAX_Y = 256;
    private static final boolean DEFAULT_BLACKLIST_TILES = true;
    private static final boolean DEFAULT_BLACKLIST_AS_WHITELIST = false;

    private static final TagKey<Block> EMPTY_BLACKLIST = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("soundattract", "empty_block_tag"));

    public static int getMaxY() {
        if (IS_ENHANCED_AI_LOADED) {
            return MinerMobsProxy.getMaxY();
        }
        return DEFAULT_MAX_Y;
    }

    public static boolean shouldBlacklistTileEntities() {
        if (IS_ENHANCED_AI_LOADED) {
            return MinerMobsProxy.blacklistTileEntities();
        }
        return DEFAULT_BLACKLIST_TILES;
    }

    public static boolean isBlacklistAsWhitelist() {
        if (IS_ENHANCED_AI_LOADED) {
            return MinerMobsProxy.isBlacklistAsWhitelist();
        }
        return DEFAULT_BLACKLIST_AS_WHITELIST;
    }

    public static TagKey<Block> getBlockBlacklistTag() {
        if (IS_ENHANCED_AI_LOADED) {
            return MinerMobsProxy.getBlockBlacklistTag();
        }
        return EMPTY_BLACKLIST;
    }

    public static boolean isEnhancedAiLoaded() {
        return IS_ENHANCED_AI_LOADED;
    }

    public static double getXrayAttributeValue(Mob mob) {
        if (!IS_ENHANCED_AI_LOADED || mob == null) return 0d;
        try {
            return XrayProxy.getXrayFollowRange(mob);
        } catch (Throwable t) {
            return 0d;
        }
    }
    private static class MinerMobsProxy {
        static int getMaxY() { return 64; }
        static boolean blacklistTileEntities() { return true; }
        static boolean isBlacklistAsWhitelist() { return false; }
        static TagKey<Block> getBlockBlacklistTag() { return net.minecraft.tags.BlockTags.DIAMOND_ORES; }
    }

    private static class XrayProxy {
        static double getXrayFollowRange(Mob mob) {
            AttributeInstance inst = null;
            try {
                Class<?> eaiAttributesClass = Class.forName("insane96mcp.enhancedai.setup.EAIAttributes");
                java.lang.reflect.Field xrayField = eaiAttributesClass.getField("XRAY_FOLLOW_RANGE");
                Object xrayAttrHolder = xrayField.get(null);
                if (xrayAttrHolder instanceof net.minecraft.world.entity.ai.attributes.Attribute attr) {
                    inst = mob.getAttribute(net.minecraft.core.Holder.direct(attr));
                }
            } catch (Throwable ignored) {}

            if (inst == null) {
                try {
                    Attribute attr = BuiltInRegistries.ATTRIBUTE.getValue(Identifier.fromNamespaceAndPath("enhancedai", "xray_follow_range"));
                    if (attr == null) {
                        attr = BuiltInRegistries.ATTRIBUTE.getValue(Identifier.fromNamespaceAndPath("enhancedai", "generic.xray_follow_range"));
                    }
                    if (attr != null) {
                        inst = mob.getAttribute(net.minecraft.core.Holder.direct(attr));
                    }
                } catch (Throwable ignored) {}
            }

            if (inst == null) return 0d;
            double v = inst.getBaseValue();
            if (v <= 0d) v = inst.getValue();
            return Math.max(0d, v);
        }
    }
}

