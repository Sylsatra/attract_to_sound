package com.example.soundattract.integration.enhancedai;

import com.example.soundattract.config.SoundAttractConfig;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

public class EnhancedAICompat {

    private static final boolean IS_ENHANCED_AI_LOADED = ModList.get().isLoaded("enhancedai");

    private static final int DEFAULT_MAX_Y = 256;
    private static final boolean DEFAULT_BLACKLIST_TILES = true;
    private static final boolean DEFAULT_BLACKLIST_AS_WHITELIST = false;

    private static final TagKey<Block> EMPTY_BLACKLIST = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("soundattract", "empty_block_tag"));

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

    public static double getTeleportToTargetChance(Level level) {
        if (IS_ENHANCED_AI_LOADED) {
            return TeleportProxy.getChance(level);
        }
        return SoundAttractConfig.COMMON.teleportChance.get();
    }

    public static int getTeleportCooldownTicks() {
        if (IS_ENHANCED_AI_LOADED) {
            return TeleportProxy.getCooldown();
        }
        return SoundAttractConfig.COMMON.teleportCooldownTicks.get();
    }

    public static double getPickUpAndThrowChance(Level level) {
        if (IS_ENHANCED_AI_LOADED) {
            return PickUpProxy.getChance(level);
        }
        return SoundAttractConfig.COMMON.pickUpChance.get();
    }

    public static int getPickUpMinDistanceToPickUp() {
        if (IS_ENHANCED_AI_LOADED) {
            return PickUpProxy.getMinDistanceToPickUp();
        }
        return SoundAttractConfig.COMMON.pickUpMinDistanceToPickUp.get();
    }

    public static int getPickUpMaxDistanceToThrow() {
        if (IS_ENHANCED_AI_LOADED) {
            return PickUpProxy.getMaxDistanceToThrow();
        }
        return SoundAttractConfig.COMMON.pickUpMaxDistanceToThrow.get();
    }

    public static double getPickUpSpeedModifier() {
        if (IS_ENHANCED_AI_LOADED) {
            return PickUpProxy.getSpeedModifier();
        }
        return SoundAttractConfig.COMMON.pickUpSpeedModifier.get();
    }

    public static int getPickUpCooldownTicks() {
        if (IS_ENHANCED_AI_LOADED) {
            return PickUpProxy.getCooldown();
        }
        return SoundAttractConfig.COMMON.pickUpCooldownTicks.get();
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
        static int getMaxY() { return insane96mcp.enhancedai.modules.mobs.miner.MinerMobs.maxY; }
        static boolean blacklistTileEntities() { return insane96mcp.enhancedai.modules.mobs.miner.MinerMobs.blacklistTileEntities; }
        static boolean isBlacklistAsWhitelist() { return insane96mcp.enhancedai.modules.mobs.miner.MinerMobs.blockBlacklistAsWhitelist; }
        static TagKey<Block> getBlockBlacklistTag() { return insane96mcp.enhancedai.modules.mobs.miner.MinerMobs.BLOCK_BLACKLIST; }
    }

    private static class TeleportProxy {
        static double getChance(Level level) {
            return readDifficultyScaledChance(
                "insane96mcp.enhancedai.modules.mobs.teleporttotarget.TeleportToTarget",
                "chance",
                level,
                SoundAttractConfig.COMMON.teleportChance.get()
            );
        }
        static int getCooldown() { return insane96mcp.enhancedai.modules.mobs.teleporttotarget.TeleportToTarget.cooldown; }
    }

    private static class PickUpProxy {
        static double getChance(Level level) {
            return readDifficultyScaledChance(
                "insane96mcp.enhancedai.modules.mobs.pickandthrow.PickUpAndThrow",
                "chance",
                level,
                SoundAttractConfig.COMMON.pickUpChance.get()
            );
        }
        static int getMinDistanceToPickUp() { return insane96mcp.enhancedai.modules.mobs.pickandthrow.PickUpAndThrow.minDistanceToPickUp; }
        static int getMaxDistanceToThrow() { return insane96mcp.enhancedai.modules.mobs.pickandthrow.PickUpAndThrow.maxDistanceToThrow; }
        static double getSpeedModifier() { return insane96mcp.enhancedai.modules.mobs.pickandthrow.PickUpAndThrow.speedModifierToPickUp; }
        static int getCooldown() { return insane96mcp.enhancedai.modules.mobs.pickandthrow.PickUpAndThrow.cooldown; }
    }

    private static class XrayProxy {
        static double getXrayFollowRange(Mob mob) {
            AttributeInstance inst = mob.getAttribute(insane96mcp.enhancedai.setup.EAIAttributes.XRAY_FOLLOW_RANGE.get());

            if (inst == null) {
                try {
                    Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.fromNamespaceAndPath("enhancedai", "xray_follow_range"));
                    if (attr == null) {
                        attr = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.fromNamespaceAndPath("enhancedai", "generic.xray_follow_range"));
                    }
                    if (attr != null) {
                        inst = mob.getAttribute(attr);
                    }
                } catch (Throwable ignored) {}
            }

            if (inst == null) return 0d;
            double v = inst.getBaseValue();
            if (v <= 0d) v = inst.getValue();
            return Math.max(0d, v);
        }
    }

    private static double readDifficultyScaledChance(String className, String fieldName, Level level, double fallback) {
        try {
            Class<?> cls = Class.forName(className);
            java.lang.reflect.Field field = cls.getField(fieldName);
            Object holder = field.get(null);
            if (holder == null) return fallback;
            java.lang.reflect.Method method = null;
            for (java.lang.reflect.Method m : holder.getClass().getMethods()) {
                if (!"getByDifficulty".equals(m.getName())) continue;
                if (m.getParameterCount() == 1) {
                    method = m;
                    break;
                }
            }
            if (method == null) return fallback;
            Object arg = null;
            Class<?> param = method.getParameterTypes()[0];
            if (param.isAssignableFrom(Level.class)) {
                arg = level;
            } else if ("insane96mcp.insanelib.base.config.Difficulty".equals(param.getName())) {
                try {
                    java.lang.reflect.Method fromLevel = param.getMethod("fromLevel", Level.class);
                    arg = fromLevel.invoke(null, level);
                } catch (Throwable ignored) {
                }
            }
            if (arg == null) return fallback;
            Object result = method.invoke(holder, arg);
            if (result instanceof Number num) {
                return num.doubleValue();
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }
}