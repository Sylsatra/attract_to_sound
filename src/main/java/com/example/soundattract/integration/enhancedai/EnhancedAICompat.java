package com.example.soundattract.integration.enhancedai;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;

public class EnhancedAICompat {

    private static final boolean IS_ENHANCED_AI_LOADED = ModList.get().isLoaded("enhancedai");

    private static final int DEFAULT_MAX_Y = 320;
    private static final boolean DEFAULT_BLACKLIST_TILES = true;
    private static final boolean DEFAULT_BLACKLIST_AS_WHITELIST = false;

    private static final TagKey<Block> EMPTY_BLACKLIST = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("soundattract", "empty_enhancedai_blacklist"));

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

    private static class MinerMobsProxy {
        static int getMaxY() {
            try {
                Class<?> cls = Class.forName("insane96mcp.enhancedai.modules.mobs.miner.MinerMobs");
                return (Integer) cls.getField("maxY").get(null);
            } catch (Exception e) {
                return DEFAULT_MAX_Y;
            }
        }
        static boolean blacklistTileEntities() {
            try {
                Class<?> cls = Class.forName("insane96mcp.enhancedai.modules.mobs.miner.MinerMobs");
                return (Boolean) cls.getField("blacklistTileEntities").get(null);
            } catch (Exception e) {
                return DEFAULT_BLACKLIST_TILES;
            }
        }
        static boolean isBlacklistAsWhitelist() {
            try {
                Class<?> cls = Class.forName("insane96mcp.enhancedai.modules.mobs.miner.MinerMobs");
                return (Boolean) cls.getField("blockBlacklistAsWhitelist").get(null);
            } catch (Exception e) {
                return DEFAULT_BLACKLIST_AS_WHITELIST;
            }
        }
        static TagKey<Block> getBlockBlacklistTag() {
            try {
                Class<?> cls = Class.forName("insane96mcp.enhancedai.modules.mobs.miner.MinerMobs");
                return (TagKey<Block>) cls.getField("BLOCK_BLACKLIST").get(null);
            } catch (Exception e) {
                return EMPTY_BLACKLIST;
            }
        }
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
        static int getCooldown() {
            try {
                Class<?> cls = Class.forName("insane96mcp.enhancedai.modules.mobs.teleporttotarget.TeleportToTarget");
                return (Integer) cls.getField("cooldown").get(null);
            } catch (Exception e) {
                return SoundAttractConfig.COMMON.teleportCooldownTicks.get();
            }
        }
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
        static int getMinDistanceToPickUp() {
            try {
                Class<?> cls = Class.forName("insane96mcp.enhancedai.modules.mobs.pickandthrow.PickUpAndThrow");
                return (Integer) cls.getField("minDistanceToPickUp").get(null);
            } catch (Exception e) {
                return SoundAttractConfig.COMMON.pickUpMinDistanceToPickUp.get();
            }
        }
        static int getMaxDistanceToThrow() {
            try {
                Class<?> cls = Class.forName("insane96mcp.enhancedai.modules.mobs.pickandthrow.PickUpAndThrow");
                return (Integer) cls.getField("maxDistanceToThrow").get(null);
            } catch (Exception e) {
                return SoundAttractConfig.COMMON.pickUpMaxDistanceToThrow.get();
            }
        }
        static double getSpeedModifier() {
            try {
                Class<?> cls = Class.forName("insane96mcp.enhancedai.modules.mobs.pickandthrow.PickUpAndThrow");
                return (Double) cls.getField("speedModifierToPickUp").get(null);
            } catch (Exception e) {
                return SoundAttractConfig.COMMON.pickUpSpeedModifier.get();
            }
        }
        static int getCooldown() {
            try {
                Class<?> cls = Class.forName("insane96mcp.enhancedai.modules.mobs.pickandthrow.PickUpAndThrow");
                return (Integer) cls.getField("cooldown").get(null);
            } catch (Exception e) {
                return SoundAttractConfig.COMMON.pickUpCooldownTicks.get();
            }
        }
    }

    private static class XrayProxy {
        static double getXrayFollowRange(Mob mob) {
            AttributeInstance inst = null;
            try {
                Class<?> eaiAttributesClass = Class.forName("insane96mcp.enhancedai.setup.EAIAttributes");
                Object xrayFollowRangeHolder = eaiAttributesClass.getField("XRAY_FOLLOW_RANGE").get(null);
                java.lang.reflect.Method getMethod = xrayFollowRangeHolder.getClass().getMethod("get");
                Object attrHolder = getMethod.invoke(xrayFollowRangeHolder);
                
                if (attrHolder instanceof net.minecraft.core.Holder<?> holder) {
                    inst = mob.getAttribute((net.minecraft.core.Holder<Attribute>) holder);
                }
            } catch (Exception ignored) {}

            if (inst == null) {
                try {
                    net.minecraft.core.Holder<Attribute> attr = BuiltInRegistries.ATTRIBUTE.get(Identifier.fromNamespaceAndPath("enhancedai", "xray_follow_range")).orElse(null);
                    if (attr == null) {
                        attr = BuiltInRegistries.ATTRIBUTE.get(Identifier.fromNamespaceAndPath("enhancedai", "generic.xray_follow_range")).orElse(null);
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