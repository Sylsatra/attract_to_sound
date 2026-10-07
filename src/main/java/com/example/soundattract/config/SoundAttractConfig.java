package com.example.soundattract.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.tuple.Pair;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.quantified.QuantifiedCacheCompat;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeConfigSpec;
import com.example.soundattract.config.separate.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.config.ModConfig;

public class SoundAttractConfig {

    public record SoundDefaultEntry(double range, double weight) {

    }
    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        final org.apache.commons.lang3.tuple.Pair<Common, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = specPair.getLeft();
        COMMON_SPEC = specPair.getRight();
    }

    public static class Server {
        public final ForgeConfigSpec.BooleanValue hideWornGearWhileInvisible;

        public final ForgeConfigSpec.BooleanValue enablePlayerActionSounds;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> playerActionRanges;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> playerActionWeights;
        public final ForgeConfigSpec.DoubleValue playerActionCheckRadius;

        public final ForgeConfigSpec.BooleanValue enablePointBlankIntegration;
        public final ForgeConfigSpec.BooleanValue enableTaczIntegration;
        public final ForgeConfigSpec.BooleanValue enableVoiceChatIntegration;

        public final ForgeConfigSpec.DoubleValue maxClientSoundRange;

        public final ForgeConfigSpec.IntValue voiceChatWhisperRange;
        public final ForgeConfigSpec.IntValue voiceChatNormalRange;
        public final ForgeConfigSpec.DoubleValue voiceChatWeight;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> voiceChatDbThresholdMap;

        Server(ForgeConfigSpec.Builder b) {
            b.push("rendering");
            hideWornGearWhileInvisible = b
                    .comment("If true, mob/player armor, held items, and Sound Attract camo layers are hidden while the entity has invisibility.",
                            "If false, those layers render normally through invisibility.",
                            "Server-authoritative: connected clients use this value regardless of their local setting.")
                    .define("hideWornGearWhileInvisible", true);
            b.pop();

            b.push("player_action_sounds");
            enablePlayerActionSounds = b
                    .comment("Enable/disable virtual sound generation from player movements.",
                            "Read on the client before sending footstep messages; server-authoritative so admins control it.")
                    .define("enablePlayerActionSounds", true);
            playerActionRanges = b
                    .comment("Base detection ranges for player actions. Format: 'ACTION;range'")
                    .defineList("playerActionRanges", Arrays.asList(
                            "CRAWLING;3", "SNEAKING;5", "WALKING;10", "SPRINTING;16", "SPRINT_JUMPING;20"
                    ), obj -> obj instanceof String && ((String) obj).split(";").length == 2);
            playerActionWeights = b
                    .comment("Weights for player action sounds. Format: 'ACTION;weight'")
                    .defineList("playerActionWeights", Arrays.asList(
                            "CRAWLING;1.0", "SNEAKING;1.0", "WALKING;1.0", "SPRINTING;1.0", "SPRINT_JUMPING;1.0"
                    ), obj -> obj instanceof String && ((String) obj).split(";").length == 2);
            playerActionCheckRadius = b
                    .comment("Radius within which to check for players to generate sounds.")
                    .defineInRange("playerActionCheckRadius", 24.0, 0.0, 512.0);
            b.pop();

            b.push("integrations");
            enablePointBlankIntegration = b
                    .comment("Enable Point Blank gun integration.")
                    .define("enablePointBlankIntegration", true);
            enableTaczIntegration = b
                    .comment("Enable Tacz gun integration.")
                    .define("enableTaczIntegration", true);
            enableVoiceChatIntegration = b
                    .comment("Enable Simple Voice Chat integration.")
                    .define("enableVoiceChatIntegration", true);
            b.pop();

            b.push("safety");
            maxClientSoundRange = b
                    .comment("Caps the final resolved range (blocks) for any wire-received sound.",
                            "Applies after server-side resolution; server-local gun/vanilla sounds are unaffected.")
                    .defineInRange("maxClientSoundRange", 256.0, 1.0, 256.0);
            b.pop();

            b.push("voice_chat");
            voiceChatWhisperRange = b
                    .comment("Base range used when the speaker is whispering.")
                    .defineInRange("voiceChatWhisperRange", 16, 1, 64);
            voiceChatNormalRange = b
                    .comment("Base range used for normal speaking.")
                    .defineInRange("voiceChatNormalRange", 32, 1, 128);
            voiceChatWeight = b
                    .comment("Weight assigned to the generated voice chat sound event.")
                    .defineInRange("voiceChatWeight", 9.0, 0.0, 10.0);
            voiceChatDbThresholdMap = b
                    .comment("Normalized dB thresholds to range multipliers. Format: 'threshold:multiplier'.",
                            "Threshold is based on current peak volume level (usually 0-127).")
                    .defineList("voiceChatDbThresholdMap", Arrays.asList(
                            "110:2.0", "90:1.8", "75:1.5", "50:1.0", "30:0.7", "10:0.3", "0:0.05"
                    ), obj -> obj instanceof String && ((String) obj).contains(":"));
            b.pop();
        }
    }

    public static final Server SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;

    static {
        final org.apache.commons.lang3.tuple.Pair<Server, ForgeConfigSpec> srvPair =
                new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = srvPair.getLeft();
        SERVER_SPEC = srvPair.getRight();
    }

    public static boolean serverReady() {
        return SERVER_SPEC != null && SERVER_SPEC.isLoaded();
    }

    public static boolean commonConfigsReady() {
        return isLoaded(GeneralConfig.SPEC)
                && isLoaded(StealthConfig.SPEC)
                && isLoaded(GunsConfig.SPEC)
                && isLoaded(VoiceConfig.SPEC)
                && isLoaded(IntegrationConfig.SPEC)
                && isLoaded(ScentConfig.SPEC)
                && isLoaded(PerformanceConfig.SPEC)
                && isLoaded(RaidConfig.SPEC)
                && isLoaded(LivingWallConfig.SPEC)
                && isLoaded(PathfindingConfig.SPEC);
    }

    private static boolean isLoaded(ForgeConfigSpec spec) {
        return spec != null && spec.isLoaded();
    }

    public static boolean shouldHideWornGearWhileInvisible() {
        try {
            if (serverReady()) {
                return SERVER.hideWornGearWhileInvisible.get();
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    private static volatile boolean serverMigrationDone = false;

    public static void migrateCommonToServerIfNeeded() {
        if (serverMigrationDone) return;
        if (!serverReady()) return;
        if (!commonConfigsReady()) return;

        int migrated = 0;
        migrated += migrateBool("enablePlayerActionSounds",
                COMMON.enablePlayerActionSounds, SERVER.enablePlayerActionSounds, true);
        migrated += migrateDouble("playerActionCheckRadius",
                COMMON.playerActionCheckRadius, SERVER.playerActionCheckRadius, 24.0);
        migrated += migrateStringList("playerActionRanges",
                COMMON.playerActionRanges, SERVER.playerActionRanges,
                Arrays.asList("CRAWLING;3", "SNEAKING;5", "WALKING;10", "SPRINTING;16", "SPRINT_JUMPING;20"));
        migrated += migrateStringList("playerActionWeights",
                COMMON.playerActionWeights, SERVER.playerActionWeights,
                Arrays.asList("CRAWLING;1.0", "SNEAKING;1.0", "WALKING;1.0", "SPRINTING;1.0", "SPRINT_JUMPING;1.0"));
        migrated += migrateBool("enablePointBlankIntegration",
                COMMON.enablePointBlankIntegration, SERVER.enablePointBlankIntegration, true);
        migrated += migrateBool("enableTaczIntegration",
                COMMON.enableTaczIntegration, SERVER.enableTaczIntegration, true);
        migrated += migrateBool("enableVoiceChatIntegration",
                COMMON.enableVoiceChatIntegration, SERVER.enableVoiceChatIntegration, true);
        migrated += migrateInt("voiceChatWhisperRange",
                COMMON.voiceChatWhisperRange, SERVER.voiceChatWhisperRange, 16);
        migrated += migrateInt("voiceChatNormalRange",
                COMMON.voiceChatNormalRange, SERVER.voiceChatNormalRange, 32);
        migrated += migrateDouble("voiceChatWeight",
                COMMON.voiceChatWeight, SERVER.voiceChatWeight, 9.0);
        migrated += migrateStringList("voiceChatDbThresholdMap",
                COMMON.voiceChatDbThresholdMap, SERVER.voiceChatDbThresholdMap,
                Arrays.asList("110:2.0", "90:1.8", "75:1.5", "50:1.0", "30:0.7", "10:0.3", "0:0.05"));

        if (migrated > 0) {
            SoundAttractMod.LOGGER.info(
                "[SoundAttract] Migrated {} config keys from COMMON (deprecated) to SERVER spec.",
                migrated);
            try {
                SERVER_SPEC.save();
            } catch (Throwable t) {
                SoundAttractMod.LOGGER.warn("[SoundAttract] SERVER_SPEC.save() failed after migration: {}", t.toString());
            }
        }
        serverMigrationDone = true;
    }

    private static int migrateBool(String name,
                                   ForgeConfigSpec.BooleanValue src,
                                   ForgeConfigSpec.BooleanValue dst,
                                   boolean dstDefault) {
        if (dst.get() != dstDefault) return 0;
        boolean comCurr = src.get();
        if (comCurr == dstDefault) return 0;
        dst.set(comCurr);
        SoundAttractMod.LOGGER.info("[SoundAttract] migrate {}: {} -> SERVER", name, comCurr);
        return 1;
    }

    private static int migrateDouble(String name,
                                     ForgeConfigSpec.DoubleValue src,
                                     ForgeConfigSpec.DoubleValue dst,
                                     double dstDefault) {
        if (Math.abs(dst.get() - dstDefault) > 1.0e-9) return 0;
        double comCurr = src.get();
        if (Math.abs(comCurr - dstDefault) < 1.0e-9) return 0;
        dst.set(comCurr);
        SoundAttractMod.LOGGER.info("[SoundAttract] migrate {}: {} -> SERVER", name, comCurr);
        return 1;
    }

    private static int migrateInt(String name,
                                  ForgeConfigSpec.IntValue src,
                                  ForgeConfigSpec.IntValue dst,
                                  int dstDefault) {
        if (dst.get() != dstDefault) return 0;
        int comCurr = src.get();
        if (comCurr == dstDefault) return 0;
        dst.set(comCurr);
        SoundAttractMod.LOGGER.info("[SoundAttract] migrate {}: {} -> SERVER", name, comCurr);
        return 1;
    }

    private static int migrateStringList(String name,
                                         ForgeConfigSpec.ConfigValue<List<? extends String>> src,
                                         ForgeConfigSpec.ConfigValue<List<? extends String>> dst,
                                         List<String> dstDefault) {
        List<? extends String> dstCurr = dst.get();
        if (!listsEqual(dstCurr, dstDefault)) return 0;
        List<? extends String> comCurr = src.get();
        if (listsEqual(comCurr, dstDefault)) return 0;
        dst.set(new ArrayList<>(comCurr));
        SoundAttractMod.LOGGER.info("[SoundAttract] migrate {}: {} -> SERVER", name, comCurr);
        return 1;
    }

    private static boolean listsEqual(List<? extends String> a, List<? extends String> b) {
        if (a == null || b == null) return a == b;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!java.util.Objects.equals(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    public static Set<ResourceLocation> SOUND_ID_WHITELIST_CACHE = ConcurrentHashMap.newKeySet();
    public static Map<ResourceLocation, SoundDefaultEntry> SOUND_DEFAULT_ENTRIES_CACHE = new ConcurrentHashMap<>();
    public static final Set<ResourceLocation> DP_SOUND_WHITELIST_CACHE = ConcurrentHashMap.newKeySet();
    public static final Map<ResourceLocation, SoundDefaultEntry> DP_SOUND_DEFAULTS_CACHE = new ConcurrentHashMap<>();
    public static Set<ResourceLocation> CUSTOM_LIQUID_BLOCKS_CACHE = ConcurrentHashMap.newKeySet();
    public static final Set<net.minecraft.world.entity.EntityType<?>> INVESTIGATE_PROJECTILE_TYPES_CACHE = ConcurrentHashMap.newKeySet();
    public static Set<ResourceLocation> CUSTOM_WOOL_BLOCKS_CACHE = ConcurrentHashMap.newKeySet();
    public static Set<ResourceLocation> CUSTOM_SOLID_BLOCKS_CACHE = ConcurrentHashMap.newKeySet();
    public static Set<ResourceLocation> CUSTOM_NON_SOLID_BLOCKS_CACHE = ConcurrentHashMap.newKeySet();
    public static Set<ResourceLocation> CUSTOM_THIN_BLOCKS_CACHE = ConcurrentHashMap.newKeySet();
    public static Set<ResourceLocation> CUSTOM_AIR_BLOCKS_CACHE = ConcurrentHashMap.newKeySet();
    public static Set<ResourceLocation> NON_BLOCKING_VISION_ALLOW_CACHE = ConcurrentHashMap.newKeySet();
    public static double TACZ_RELOAD_RANGE_CACHE = 10.0;
    public static double TACZ_RELOAD_WEIGHT_CACHE = 1.0;
    public static double TACZ_SHOOT_RANGE_CACHE = 140.0;
    public static double TACZ_SHOOT_WEIGHT_CACHE = 15;
    public static boolean TACZ_ENABLED_CACHE = false;
    public static final Map<ResourceLocation, Pair<Double, Double>> TACZ_GUN_SHOOT_DB_CACHE = new ConcurrentHashMap<>();
    public static final Map<String, Double> TACZ_ATTACHMENT_REDUCTION_DB_CACHE = new ConcurrentHashMap<>();
    public static final Map<ResourceLocation, Double> TACZ_MUZZLE_FLASH_REDUCTION_CACHE = new ConcurrentHashMap<>();
    public static final Map<ResourceLocation, Pair<Double, Double>> DP_TACZ_GUN_SHOOT_DB_CACHE = new ConcurrentHashMap<>();
    public static final Map<String, Double> DP_TACZ_ATTACHMENT_REDUCTION_DB_CACHE = new ConcurrentHashMap<>();
    public static final Map<ResourceLocation, Double> DP_TACZ_MUZZLE_FLASH_REDUCTION_CACHE = new ConcurrentHashMap<>();
    public static double TACZ_ATTACHMENT_REDUCTION_DEFAULT_CACHE = 20.0;
    public static double TACZ_ATTACHMENT_FLASH_REDUCTION_DEFAULT_CACHE = 0.0;
    public static boolean POINT_BLANK_ENABLED_CACHE = false;
    public static double POINT_BLANK_RELOAD_RANGE_CACHE = 9.0;
    public static double POINT_BLANK_RELOAD_WEIGHT_CACHE = 1.0;
    public static double POINT_BLANK_SHOOT_RANGE_CACHE = 140.0;
    public static double POINT_BLANK_SHOOT_WEIGHT_CACHE = 15.0;
    public static final Map<ResourceLocation, Double> POINT_BLANK_GUN_RANGE_CACHE = new ConcurrentHashMap<>();
    public static final Map<ResourceLocation, Double> POINT_BLANK_ATTACHMENT_REDUCTION_CACHE = new ConcurrentHashMap<>();
    public static final Map<ResourceLocation, Double> POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE = new ConcurrentHashMap<>();
    public static double POINT_BLANK_ATTACHMENT_REDUCTION_DEFAULT_CACHE = 20.0;
    public static final Map<ResourceLocation, Double> DP_POINT_BLANK_GUN_RANGE_CACHE = new ConcurrentHashMap<>();
    public static final Map<ResourceLocation, Double> DP_POINT_BLANK_ATTACHMENT_REDUCTION_CACHE = new ConcurrentHashMap<>();
    public static final Map<ResourceLocation, Double> DP_POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE = new ConcurrentHashMap<>();

    public static volatile List<com.example.soundattract.config.MobProfile2> SPECIAL_MOB_PROFILES_CACHE = List.of();
    public static volatile List<com.example.soundattract.config.PlayerProfile2> SPECIAL_PLAYER_PROFILES_CACHE = List.of();
    public static volatile List<com.example.soundattract.config.MobProfile2> DP_MOB_PROFILES_CACHE = List.of();
    public static volatile List<com.example.soundattract.config.PlayerProfile2> DP_PLAYER_PROFILES_CACHE = List.of();
    public static final Map<ResourceLocation, Integer> customArmorColors = new ConcurrentHashMap<>();
    public static final Map<ResourceLocation, Integer> DP_CUSTOM_ARMOR_COLORS = new ConcurrentHashMap<>();

    public static final Set<String> ATTRACTED_ENTITY_TYPES_CACHE = ConcurrentHashMap.newKeySet();
    public static final Set<ResourceLocation> STEALTH_BYPASS_ENTITY_TYPES_CACHE = ConcurrentHashMap.newKeySet();
    public static final Set<String> STEALTH_BYPASS_MOD_NAMESPACES_CACHE = ConcurrentHashMap.newKeySet();
    public static final Set<String> FLEE_FROM_FIRE_ELIGIBLE_SET = ConcurrentHashMap.newKeySet();

    public static volatile boolean PLAYER_ACTION_SOUNDS_ENABLED_CACHE = true;
    public static volatile Map<String, Integer> PLAYER_ACTION_RANGES_CACHE = Map.of();
    public static volatile Map<String, Double> PLAYER_ACTION_WEIGHTS_CACHE = Map.of();

    public static void parseAndCachePlayerActionConfig() {
        if (!serverReady()) return;
        PLAYER_ACTION_SOUNDS_ENABLED_CACHE = SERVER.enablePlayerActionSounds.get();
        Map<String, Integer> rangesSnapshot = new HashMap<>();
        Map<String, Double> weightsSnapshot = new HashMap<>();

        List<? extends String> ranges = SERVER.playerActionRanges.get();
        if (ranges != null) {
            for (String entry : ranges) {
                String[] parts = entry.split(";");
                if (parts.length == 2) {
                    try {
                        rangesSnapshot.put(parts[0].trim(), Integer.parseInt(parts[1].trim()));
                    } catch (Exception e) {}
                }
            }
        }

        List<? extends String> weights = SERVER.playerActionWeights.get();
        if (weights != null) {
            for (String entry : weights) {
                String[] parts = entry.split(";");
                if (parts.length == 2) {
                    try {
                        weightsSnapshot.put(parts[0].trim(), Double.parseDouble(parts[1].trim()));
                    } catch (Exception e) {}
                }
            }
        }

        PLAYER_ACTION_RANGES_CACHE = Map.copyOf(rangesSnapshot);
        PLAYER_ACTION_WEIGHTS_CACHE = Map.copyOf(weightsSnapshot);


        SOUND_ID_WHITELIST_CACHE.add(ResourceLocation.tryParse("soundattract:player_action.crawling"));
        SOUND_ID_WHITELIST_CACHE.add(ResourceLocation.tryParse("soundattract:player_action.sneaking"));
        SOUND_ID_WHITELIST_CACHE.add(ResourceLocation.tryParse("soundattract:player_action.walking"));
        SOUND_ID_WHITELIST_CACHE.add(ResourceLocation.tryParse("soundattract:player_action.sprinting"));
        SOUND_ID_WHITELIST_CACHE.add(ResourceLocation.tryParse("soundattract:player_action.sprint_jumping"));
        SOUND_ID_WHITELIST_CACHE.add(ResourceLocation.tryParse("soundattract:virtual"));
    }

    public static void parseAndCacheCustomArmorColors() {
        customArmorColors.clear();
        if (COMMON == null || COMMON.customArmorColors == null) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CAMOUFLAGE)) {
                SoundAttractMod.LOGGER.info("SoundAttractConfig: COMMON or customArmorColors is null, skipping custom armor color parsing.");
            }
            return;
        }

        List<? extends String> configList = COMMON.customArmorColors.get();
        if (configList == null || configList.isEmpty()) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CAMOUFLAGE)) {
                SoundAttractMod.LOGGER.info("SoundAttractConfig: No custom armor colors defined in config.");
            }
            return;
        }

        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CAMOUFLAGE)) {
            SoundAttractMod.LOGGER.info("SoundAttractConfig: Parsing {} custom armor color entries...", configList.size());
        }

        for (String entry : configList) {
            if (entry == null || entry.trim().isEmpty()) {
                continue;
            }
            String[] parts = entry.split(";");
            if (parts.length == 2) {
                String itemId = parts[0].trim();
                String colorHex = parts[1].trim();
                try {
                    ResourceLocation loc = ResourceLocation.tryParse(itemId);
                    if (loc == null) {
                        SoundAttractMod.LOGGER.warn("SoundAttractConfig: Invalid ResourceLocation for custom armor color: {}", itemId);
                        continue;
                    }
                    if (!colorHex.startsWith("#") || colorHex.length() != 7) {
                        SoundAttractMod.LOGGER.warn("SoundAttractConfig: Invalid hex color format '{}' for item {}. Expected #RRGGBB.", colorHex, itemId);
                        continue;
                    }
                    int color = Integer.parseInt(colorHex.substring(1), 16);
                    customArmorColors.put(loc, color);
                    if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CAMOUFLAGE)) {
                        SoundAttractMod.LOGGER.info("SoundAttractConfig: Added custom armor color: {} -> #{}", itemId, Integer.toHexString(color).toUpperCase(java.util.Locale.ROOT));
                    }
                } catch (NumberFormatException e) {
                    SoundAttractMod.LOGGER.warn("SoundAttractConfig: Could not parse color hex '{}' for item {}: {}", colorHex, itemId, e.getMessage());
                } catch (Exception e) {
                    SoundAttractMod.LOGGER.error("SoundAttractConfig: Unexpected error parsing custom armor color entry '{}': {}", entry, e.getMessage());
                }
            } else {
                SoundAttractMod.LOGGER.warn("SoundAttractConfig: Malformed custom armor color entry: '{}'. Expected format: 'modid:item_id;#RRGGBB'", entry);
            }
        }

        boolean enableDataDriven = COMMON != null && COMMON.enableDataDriven != null && COMMON.enableDataDriven.get();
        String priority = COMMON != null && COMMON.datapackPriority != null ? COMMON.datapackPriority.get() : "datapack_over_config";

        boolean datapackOverConfig = "datapack_over_config".equalsIgnoreCase(priority);


        if (enableDataDriven && !DP_CUSTOM_ARMOR_COLORS.isEmpty()) {
            if (datapackOverConfig) {
                customArmorColors.clear();
                customArmorColors.putAll(DP_CUSTOM_ARMOR_COLORS);
            } else {
                for (Map.Entry<ResourceLocation, Integer> e : DP_CUSTOM_ARMOR_COLORS.entrySet()) {
                    customArmorColors.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }

        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CAMOUFLAGE)) {
            SoundAttractMod.LOGGER.info("SoundAttractConfig: Loaded {} entries into customArmorColors (after datapack merge).", customArmorColors.size());
        }
    }

    public static void parseAndCacheNonBlockingVisionAllowList() {
        NON_BLOCKING_VISION_ALLOW_CACHE.clear();
        if (COMMON == null || COMMON.nonBlockingVisionAllowList == null) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CAMOUFLAGE)) {
                SoundAttractMod.LOGGER.info("SoundAttractConfig: COMMON or nonBlockingVisionAllowList is null, skipping parsing.");
            }
            return;
        }

        List<? extends String> cfg = COMMON.nonBlockingVisionAllowList.get();
        if (cfg == null || cfg.isEmpty()) {
            return;
        }

        for (String entry : cfg) {
            if (entry == null || entry.trim().isEmpty()) {
                continue;
            }
            try {
                ResourceLocation loc = ResourceLocation.tryParse(entry.trim());
                if (loc != null) {
                    NON_BLOCKING_VISION_ALLOW_CACHE.add(loc);
                } else {
                    SoundAttractMod.LOGGER.warn("SoundAttractConfig: Invalid ResourceLocation in nonBlockingVisionAllowList: {}", entry);
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("SoundAttractConfig: Error parsing nonBlockingVisionAllowList entry '{}': {}", entry, e.getMessage());
            }
        }

        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CAMOUFLAGE)) {
            SoundAttractMod.LOGGER.info("SoundAttractConfig: Loaded {} entries into NON_BLOCKING_VISION_ALLOW_CACHE.", NON_BLOCKING_VISION_ALLOW_CACHE.size());
        }
    }

    public static boolean isStealthBypassed(Entity entity) {
        if (entity == null) {
            return false;
        }
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (typeId == null) {
            return false;
        }
        if (STEALTH_BYPASS_ENTITY_TYPES_CACHE.contains(typeId)) {
            return true;
        }
        String namespace = typeId.getNamespace();
        return namespace != null && STEALTH_BYPASS_MOD_NAMESPACES_CACHE.contains(namespace.toLowerCase(Locale.ROOT));
    }

    public static class Common {

        public final ForgeConfigSpec.BooleanValue enableDataDriven = GeneralConfig.ENABLE_DATA_DRIVEN;
        public final ForgeConfigSpec.ConfigValue<String> datapackPriority = GeneralConfig.DATAPACK_PRIORITY;
        public final ForgeConfigSpec.BooleanValue enablePlayerActionSounds = GeneralConfig.ENABLE_PLAYER_ACTION_SOUNDS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> playerActionRanges = GeneralConfig.PLAYER_ACTION_RANGES;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> playerActionWeights = GeneralConfig.PLAYER_ACTION_WEIGHTS;
        public final ForgeConfigSpec.DoubleValue playerActionCheckRadius = GeneralConfig.PLAYER_ACTION_CHECK_RADIUS;

        public final ForgeConfigSpec.BooleanValue edgeMobSmartBehavior = RaidConfig.EDGE_MOB_SMART_BEHAVIOR;
        public final ForgeConfigSpec.BooleanValue enableFleeFromUnseenAttackerGoal = GeneralConfig.ENABLE_FLEE_FROM_UNSEEN_ATTACKER_GOAL;
        public final ForgeConfigSpec.IntValue soundLifetimeTicks = GeneralConfig.SOUND_LIFETIME_TICKS;
        public final ForgeConfigSpec.DoubleValue arrivalDistance = GeneralConfig.ARRIVAL_DISTANCE;
        public final ForgeConfigSpec.DoubleValue followLeaderSpreadOutDistance = RaidConfig.FOLLOW_LEADER_SPREAD_OUT_DISTANCE;
        public final ForgeConfigSpec.DoubleValue followLeaderMinSoundWeightToSpreadOut = GeneralConfig.FOLLOW_LEADER_MIN_SOUND_WEIGHT_TO_SPREAD_OUT;
        public final ForgeConfigSpec.DoubleValue highSoundWeightTargetOverride = GeneralConfig.HIGH_SOUND_WEIGHT_TARGET_OVERRIDE;
        public final ForgeConfigSpec.DoubleValue mobMoveSpeed = GeneralConfig.MOB_MOVE_SPEED;
        public final ForgeConfigSpec.IntValue maxSoundsTracked = PerformanceConfig.MAX_SOUNDS_TRACKED;
        public final ForgeConfigSpec.DoubleValue soundSwitchRatio = GeneralConfig.SOUND_SWITCH_RATIO;
        public final ForgeConfigSpec.DoubleValue soundNoveltyBonusWeight = GeneralConfig.SOUND_NOVELTY_BONUS_WEIGHT;
        public final ForgeConfigSpec.IntValue soundNoveltyTimeTicks = GeneralConfig.SOUND_NOVELTY_TIME_TICKS;

        public final ForgeConfigSpec.BooleanValue enableRaycastCache = PerformanceConfig.ENABLE_RAYCAST_CACHE;
        public final ForgeConfigSpec.IntValue raycastCacheTtlTicks = PerformanceConfig.RAYCAST_CACHE_TTL_TICKS;
        public final ForgeConfigSpec.IntValue raycastCacheMaxEntries = PerformanceConfig.RAYCAST_CACHE_MAX_ENTRIES;
        public final ForgeConfigSpec.IntValue scanCooldownTicks = PerformanceConfig.SCAN_COOLDOWN_TICKS;
        public final ForgeConfigSpec.DoubleValue cooldownTicksPerMob = PerformanceConfig.COOLDOWN_TICKS_PER_MOB;
        public final ForgeConfigSpec.DoubleValue minTpsForScanCooldown = PerformanceConfig.MIN_TPS_FOR_SCAN_COOLDOWN;
        public final ForgeConfigSpec.DoubleValue maxTpsForScanCooldown = PerformanceConfig.MAX_TPS_FOR_SCAN_COOLDOWN;
        public final ForgeConfigSpec.BooleanValue enableTieredStealthPerformance = PerformanceConfig.ENABLE_TIERED_STEALTH_PERFORMANCE;
        public final ForgeConfigSpec.DoubleValue stealthTierSkipExpensiveChecksTps = PerformanceConfig.STEALTH_TIER_SKIP_EXPENSIVE_CHECKS_TPS;
        public final ForgeConfigSpec.DoubleValue stealthTierCurrentTargetsOnlyTps = PerformanceConfig.STEALTH_TIER_CURRENT_TARGETS_ONLY_TPS;
        public final ForgeConfigSpec.DoubleValue stealthTierSharedRangeTps = PerformanceConfig.STEALTH_TIER_SHARED_RANGE_TPS;
        public final ForgeConfigSpec.DoubleValue stealthTierVanillaTps = PerformanceConfig.STEALTH_TIER_VANILLA_TPS;
        public final ForgeConfigSpec.DoubleValue stealthShareTargetToNearbyMobsRadius = PerformanceConfig.STEALTH_SHARE_TARGET_TO_NEARBY_MOBS_RADIUS;
        public final ForgeConfigSpec.IntValue soundScoringSubmitCooldownTicks = PerformanceConfig.SOUND_SCORING_SUBMIT_COOLDOWN_TICKS;
        public final ForgeConfigSpec.IntValue asyncResultTtlTicks = PerformanceConfig.ASYNC_RESULT_TTL_TICKS;

        public final ForgeConfigSpec.IntValue maxGroupSize = GeneralConfig.MAX_GROUP_SIZE;
        public final ForgeConfigSpec.DoubleValue leaderGroupRadius = GeneralConfig.LEADER_GROUP_RADIUS;
        public final ForgeConfigSpec.DoubleValue groupDistance = GeneralConfig.GROUP_DISTANCE;
        public final ForgeConfigSpec.DoubleValue leaderSpacingMultiplier = GeneralConfig.LEADER_SPACING_MULTIPLIER;
        public final ForgeConfigSpec.IntValue numEdgeSectors = GeneralConfig.NUM_EDGE_SECTORS;
        public final ForgeConfigSpec.IntValue groupUpdateInterval = GeneralConfig.GROUP_UPDATE_INTERVAL;
        public final ForgeConfigSpec.IntValue maxLeaders = GeneralConfig.MAX_LEADERS;
        public final ForgeConfigSpec.IntValue edgeMobsPerSector = RaidConfig.EDGE_MOBS_PER_SECTOR;
        public final ForgeConfigSpec.DoubleValue groupSprintMultiplier = RaidConfig.GROUP_SPRINT_MULTIPLIER;
        public final ForgeConfigSpec.DoubleValue leaderReturnArrivalDistance = RaidConfig.LEADER_RETURN_ARRIVAL_DISTANCE;
        public final ForgeConfigSpec.IntValue raidCountdownTicks = RaidConfig.RAID_COUNTDOWN_TICKS;
        public final ForgeConfigSpec.BooleanValue skipSoundScanWhenHasTarget = GeneralConfig.SKIP_SOUND_SCAN_WHEN_HAS_TARGET;
        public final ForgeConfigSpec.IntValue maxMufflingRaycastsPerTick = GeneralConfig.MAX_MUFFLING_RAYCASTS_PER_TICK;
        public final ForgeConfigSpec.BooleanValue raidLeaderOnlySoundScan = RaidConfig.RAID_LEADER_ONLY_SOUND_SCAN;
        public final ForgeConfigSpec.BooleanValue enableFlowField = PathfindingConfig.ENABLE_FLOW_FIELD;
        public final ForgeConfigSpec.IntValue flowFieldMobThreshold = PathfindingConfig.FLOW_FIELD_MOB_THRESHOLD;
        public final ForgeConfigSpec.IntValue moveToCooldownTicks = PathfindingConfig.MOVE_TO_COOLDOWN_TICKS;
        public final ForgeConfigSpec.DoubleValue moveToMinDelta = PathfindingConfig.MOVE_TO_MIN_DELTA;
        public final ForgeConfigSpec.IntValue moveToTeamBudgetPerTick = PathfindingConfig.MOVE_TO_TEAM_BUDGET_PER_TICK;
        public final ForgeConfigSpec.IntValue maxPathAttemptsPerTick = PathfindingConfig.MAX_PATH_ATTEMPTS_PER_TICK;
        public final ForgeConfigSpec.BooleanValue enableNodeRouter = PathfindingConfig.ENABLE_NODE_ROUTER;
        public final ForgeConfigSpec.BooleanValue enableSoundRaid = RaidConfig.ENABLE_SOUND_RAID;
        public final ForgeConfigSpec.BooleanValue enableScentRaid = RaidConfig.ENABLE_SCENT_RAID;
        public final ForgeConfigSpec.BooleanValue enableRaidReinforcements = RaidConfig.ENABLE_RAID_REINFORCEMENTS;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> raidReinforcementCaps = RaidConfig.RAID_REINFORCEMENT_CAPS;
        public final ForgeConfigSpec.IntValue raidMaxTotalMobs = RaidConfig.RAID_MAX_TOTAL_MOBS;
        public final ForgeConfigSpec.DoubleValue raidReinforcementDifficultyMultiplier = RaidConfig.RAID_REINFORCEMENT_DIFFICULTY_MULTIPLIER;
        public final ForgeConfigSpec.IntValue raidReinforcementInterval = RaidConfig.RAID_REINFORCEMENT_INTERVAL;
        public final ForgeConfigSpec.ConfigValue<String> raidEdgeDetectionAction = RaidConfig.RAID_EDGE_DETECTION_ACTION;
        public final ForgeConfigSpec.BooleanValue raidFollowerInheritTarget = RaidConfig.RAID_FOLLOWER_INHERIT_TARGET;
        public final ForgeConfigSpec.IntValue initialGroupComputationDelay = PerformanceConfig.INITIAL_GROUP_COMPUTATION_DELAY;

        public final ForgeConfigSpec.IntValue workerThreads = PerformanceConfig.WORKER_THREADS;
        public final ForgeConfigSpec.IntValue workerTaskBudgetMs = PerformanceConfig.WORKER_TASK_BUDGET_MS;

        public final ForgeConfigSpec.BooleanValue enableQuantifiedIntegration = IntegrationConfig.ENABLE_QUANTIFIED_INTEGRATION;
        public final ForgeConfigSpec.BooleanValue enableSmartBrainLibIntegration = IntegrationConfig.ENABLE_SMART_BRAIN_LIB_INTEGRATION;
        public final ForgeConfigSpec.BooleanValue enableCustomNpcsIntegration = IntegrationConfig.ENABLE_CUSTOM_NPCS_INTEGRATION;
        public final ForgeConfigSpec.BooleanValue enableQuantifiedCacheIntegration = IntegrationConfig.ENABLE_QUANTIFIED_CACHE_INTEGRATION;
        public final ForgeConfigSpec.IntValue quantifiedCacheMemoryLimitMB = IntegrationConfig.QUANTIFIED_CACHE_MEMORY_LIMIT_MB;
        public final ForgeConfigSpec.BooleanValue disableQuantifiedCacheOnMemoryPressure = IntegrationConfig.DISABLE_QUANTIFIED_CACHE_ON_MEMORY_PRESSURE;
        public final ForgeConfigSpec.BooleanValue triggerQuantifiedCacheCleanupOnMemoryPressure = IntegrationConfig.TRIGGER_QUANTIFIED_CACHE_CLEANUP_ON_MEMORY_PRESSURE;
        public final ForgeConfigSpec.BooleanValue enableQuantifiedSoundScoreSliceCache = IntegrationConfig.ENABLE_QUANTIFIED_SOUND_SCORE_SLICE_CACHE;
        public final ForgeConfigSpec.BooleanValue quantifiedSoundScoreSliceCachePersistent = IntegrationConfig.QUANTIFIED_SOUND_SCORE_SLICE_CACHE_PERSISTENT;
        public final ForgeConfigSpec.IntValue quantifiedSoundScoreSliceCacheTtlTicks = IntegrationConfig.QUANTIFIED_SOUND_SCORE_SLICE_CACHE_TTL_TICKS;
        public final ForgeConfigSpec.IntValue quantifiedSoundScoreSliceCacheMaxEntries = IntegrationConfig.QUANTIFIED_SOUND_SCORE_SLICE_CACHE_MAX_ENTRIES;
        public final ForgeConfigSpec.IntValue playerOdorScanIntervalTicks = ScentConfig.PLAYER_ODOR_SCAN_INTERVAL_TICKS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> playerOdorItemValues = ScentConfig.PLAYER_ODOR_ITEM_VALUES;
        public final ForgeConfigSpec.BooleanValue enableWindScent = ScentConfig.ENABLE_WIND_SCENT;
        public final ForgeConfigSpec.DoubleValue windScentActivationThreshold = ScentConfig.WIND_SCENT_ACTIVATION_THRESHOLD;
        public final ForgeConfigSpec.DoubleValue windScentDeactivationThreshold = ScentConfig.WIND_SCENT_DEACTIVATION_THRESHOLD;
        public final ForgeConfigSpec.IntValue windScentUpdateIntervalTicks = ScentConfig.WIND_SCENT_UPDATE_INTERVAL_TICKS;
        public final ForgeConfigSpec.IntValue windScentMaximumDistance = ScentConfig.WIND_SCENT_MAXIMUM_DISTANCE;
        public final ForgeConfigSpec.IntValue windScentMaximumParcels = ScentConfig.WIND_SCENT_MAXIMUM_PARCELS;
        public final ForgeConfigSpec.DoubleValue windshieldScentReduction = ScentConfig.WINDSHIELD_SCENT_REDUCTION;
        public final ForgeConfigSpec.DoubleValue windScentSpeedScale = ScentConfig.WIND_SCENT_SPEED_SCALE;
        public final ForgeConfigSpec.BooleanValue windScentRainDecay = ScentConfig.WIND_SCENT_RAIN_DECAY;
        public final ForgeConfigSpec.DoubleValue windScentSpreadFactor = ScentConfig.WIND_SCENT_SPREAD_FACTOR;
        public final ForgeConfigSpec.DoubleValue windScentBarrierLeakage = ScentConfig.WIND_SCENT_BARRIER_LEAKAGE;
        public final ForgeConfigSpec.IntValue windshieldScanDistance = ScentConfig.WINDSHIELD_SCAN_DISTANCE;
        public final ForgeConfigSpec.DoubleValue windshieldMinimumWindSpeed = ScentConfig.WINDSHIELD_MINIMUM_WIND_SPEED;
        public final ForgeConfigSpec.DoubleValue windshieldActivationThreshold = ScentConfig.WINDSHIELD_ACTIVATION_THRESHOLD;
        public final ForgeConfigSpec.DoubleValue windshieldDeactivationThreshold = ScentConfig.WINDSHIELD_DEACTIVATION_THRESHOLD;
        public final ForgeConfigSpec.BooleanValue enableWindScentParticles = ScentConfig.ENABLE_WIND_SCENT_PARTICLES;
        public final ForgeConfigSpec.IntValue windScentParticleIntervalTicks = ScentConfig.WIND_SCENT_PARTICLE_INTERVAL_TICKS;
        public final ForgeConfigSpec.IntValue windScentParticleMaxPerViewer = ScentConfig.WIND_SCENT_PARTICLE_MAX_PER_VIEWER;
        public final ForgeConfigSpec.BooleanValue enableWeather2WindIntegration = IntegrationConfig.ENABLE_WEATHER2_WIND_INTEGRATION;

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> attractedEntities = GeneralConfig.ATTRACTED_ENTITIES;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> stealthBypassMobIds = GeneralConfig.STEALTH_BYPASS_MOB_IDS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> stealthBypassModNamespaces = GeneralConfig.STEALTH_BYPASS_MOD_NAMESPACES;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> mobBlacklist = GeneralConfig.MOB_BLACKLIST;

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> specialMobProfilesRaw = GeneralConfig.SPECIAL_MOB_PROFILES;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> specialPlayerProfilesRaw = GeneralConfig.SPECIAL_PLAYER_PROFILES;

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> soundIdWhitelist = GeneralConfig.SOUND_ID_WHITELIST;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> rawSoundDefaults = GeneralConfig.SOUND_DEFAULTS;

        public final ForgeConfigSpec.BooleanValue enableBlockMuffling = GeneralConfig.ENABLE_BLOCK_MUFFLING;
        public final ForgeConfigSpec.IntValue maxMufflingBlocksToCheck = PerformanceConfig.MAX_MUFFLING_BLOCKS_TO_CHECK;
        public final ForgeConfigSpec.DoubleValue mufflingFactorWool = GeneralConfig.MUFFLING_FACTOR_WOOL;
        public final ForgeConfigSpec.DoubleValue mufflingFactorSolid = GeneralConfig.MUFFLING_FACTOR_SOLID;
        public final ForgeConfigSpec.DoubleValue mufflingFactorNonSolid = GeneralConfig.MUFFLING_FACTOR_NON_SOLID;
        public final ForgeConfigSpec.DoubleValue mufflingFactorThin = GeneralConfig.MUFFLING_FACTOR_THIN;
        public final ForgeConfigSpec.DoubleValue mufflingFactorLiquid = GeneralConfig.MUFFLING_FACTOR_LIQUID;
        public final ForgeConfigSpec.DoubleValue mufflingFactorAir = GeneralConfig.MUFFLING_FACTOR_AIR;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customWoolBlocks = GeneralConfig.CUSTOM_WOOL_BLOCKS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customSolidBlocks = GeneralConfig.CUSTOM_SOLID_BLOCKS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customNonSolidBlocks = GeneralConfig.CUSTOM_NON_SOLID_BLOCKS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customThinBlocks = GeneralConfig.CUSTOM_THIN_BLOCKS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customLiquidBlocks = GeneralConfig.CUSTOM_LIQUID_BLOCKS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customAirBlocks = GeneralConfig.CUSTOM_AIR_BLOCKS;

        public final ForgeConfigSpec.BooleanValue enableStealthMechanics = StealthConfig.ENABLE_STEALTH_MECHANICS;
        public final ForgeConfigSpec.IntValue stealthCheckInterval = StealthConfig.STEALTH_CHECK_INTERVAL;
        public final ForgeConfigSpec.IntValue stealthGracePeriodTicks = StealthConfig.STEALTH_GRACE_PERIOD_TICKS;
        public final ForgeConfigSpec.DoubleValue minStealthDetectionRange = StealthConfig.MIN_STEALTH_DETECTION_RANGE;
        public final ForgeConfigSpec.DoubleValue maxStealthDetectionRange = StealthConfig.MAX_STEALTH_DETECTION_RANGE;

        public final ForgeConfigSpec.DoubleValue standingDetectionRangePlayer = StealthConfig.STANDING_DETECTION_RANGE_PLAYER;
        public final ForgeConfigSpec.DoubleValue sneakingDetectionRangePlayer = StealthConfig.SNEAKING_DETECTION_RANGE_PLAYER;
        public final ForgeConfigSpec.DoubleValue crawlingDetectionRangePlayer = StealthConfig.CRAWLING_DETECTION_RANGE_PLAYER;

        public final ForgeConfigSpec.IntValue neutralLightLevel = StealthConfig.NEUTRAL_LIGHT_LEVEL;
        public final ForgeConfigSpec.DoubleValue lightLevelSensitivity = StealthConfig.LIGHT_LEVEL_SENSITIVITY;
        public final ForgeConfigSpec.DoubleValue minLightFactor = StealthConfig.MIN_LIGHT_FACTOR;
        public final ForgeConfigSpec.DoubleValue maxLightFactor = StealthConfig.MAX_LIGHT_FACTOR;
        public final ForgeConfigSpec.IntValue lightSampleRadiusHorizontal = StealthConfig.LIGHT_SAMPLE_RADIUS_HORIZONTAL;
        public final ForgeConfigSpec.IntValue lightSampleRadiusVertical = StealthConfig.LIGHT_SAMPLE_RADIUS_VERTICAL;
        public final ForgeConfigSpec.DoubleValue rainStealthFactor = StealthConfig.RAIN_STEALTH_FACTOR;
        public final ForgeConfigSpec.DoubleValue thunderStealthFactor = StealthConfig.THUNDER_STEALTH_FACTOR;

        public final ForgeConfigSpec.DoubleValue movementStealthPenalty = StealthConfig.MOVEMENT_STEALTH_PENALTY;
        public final ForgeConfigSpec.DoubleValue stationaryStealthBonusFactor = StealthConfig.STATIONARY_STEALTH_BONUS_FACTOR;
        public final ForgeConfigSpec.DoubleValue movementThreshold = StealthConfig.MOVEMENT_THRESHOLD;
        public final ForgeConfigSpec.DoubleValue invisibilityStealthFactor = StealthConfig.INVISIBILITY_STEALTH_FACTOR;

        public final ForgeConfigSpec.BooleanValue enableCamouflage = StealthConfig.ENABLE_CAMOUFLAGE;
        public final ForgeConfigSpec.BooleanValue enableHeldItemPenalty = StealthConfig.ENABLE_HELD_ITEM_PENALTY;
        public final ForgeConfigSpec.DoubleValue heldItemPenaltyFactor = StealthConfig.HELD_ITEM_PENALTY_FACTOR;
        public final ForgeConfigSpec.BooleanValue enableEnchantmentPenalty = StealthConfig.ENABLE_ENCHANTMENT_PENALTY;
        public final ForgeConfigSpec.DoubleValue armorEnchantmentPenaltyFactor = StealthConfig.ARMOR_ENCHANTMENT_PENALTY_FACTOR;
        public final ForgeConfigSpec.DoubleValue heldItemEnchantmentPenaltyFactor = StealthConfig.HELD_ITEM_ENCHANTMENT_PENALTY_FACTOR;

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> camouflageArmorItems = StealthConfig.CAMOUFLAGE_ARMOR_ITEMS;
        public final ForgeConfigSpec.BooleanValue requireFullSetForCamouflageBonus = StealthConfig.REQUIRE_FULL_SET_FOR_CAMOUFLAGE_BONUS;
        public final ForgeConfigSpec.DoubleValue fullArmorStealthBonus = StealthConfig.FULL_ARMOR_STEALTH_BONUS;
        public final ForgeConfigSpec.DoubleValue helmetCamouflageEffectiveness = StealthConfig.HELMET_CAMOUFLAGE_EFFECTIVENESS;
        public final ForgeConfigSpec.DoubleValue chestplateCamouflageEffectiveness = StealthConfig.CHESTPLATE_CAMOUFLAGE_EFFECTIVENESS;
        public final ForgeConfigSpec.DoubleValue leggingsCamouflageEffectiveness = StealthConfig.LEGGINGS_CAMOUFLAGE_EFFECTIVENESS;
        public final ForgeConfigSpec.DoubleValue bootsCamouflageEffectiveness = StealthConfig.BOOTS_CAMOUFLAGE_EFFECTIVENESS;
        public final ForgeConfigSpec.DoubleValue maxCamouflageEffectivenessCap = StealthConfig.MAX_CAMOUFLAGE_EFFECTIVENESS_CAP;
        public final ForgeConfigSpec.BooleanValue allowPartialBonusIfFullSetRequired = StealthConfig.ALLOW_PARTIAL_BONUS_IF_FULL_SET_REQUIRED;

        public final ForgeConfigSpec.BooleanValue enableEnvironmentalCamouflage = StealthConfig.ENABLE_ENVIRONMENTAL_CAMOUFLAGE;
        public final ForgeConfigSpec.DoubleValue environmentalCamouflageMaxEffectiveness = StealthConfig.ENVIRONMENTAL_CAMOUFLAGE_MAX_EFFECTIVENESS;
        public final ForgeConfigSpec.IntValue environmentalCamouflageColorMatchThreshold = StealthConfig.ENVIRONMENTAL_CAMOUFLAGE_COLOR_MATCH_THRESHOLD;
        public final ForgeConfigSpec.BooleanValue environmentalCamouflageOnlyDyedLeather = StealthConfig.ENVIRONMENTAL_CAMOUFLAGE_ONLY_DYED_LEATHER;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customArmorColors = StealthConfig.CUSTOM_ARMOR_COLORS;
        public final ForgeConfigSpec.IntValue envColorSampleRadius = StealthConfig.ENV_COLOR_SAMPLE_RADIUS;
        public final ForgeConfigSpec.IntValue envColorSampleYOffsetStart = StealthConfig.ENV_COLOR_SAMPLE_Y_OFFSET_START;
        public final ForgeConfigSpec.IntValue envColorSampleYOffsetEnd = StealthConfig.ENV_COLOR_SAMPLE_Y_OFFSET_END;
        public final ForgeConfigSpec.ConfigValue<String> environmentalCamouflageSamplingMode = StealthConfig.ENVIRONMENTAL_CAMOUFLAGE_SAMPLING_MODE;
        public final ForgeConfigSpec.IntValue envBackdropMaxDistance = StealthConfig.ENV_BACKDROP_MAX_DISTANCE;
        public final ForgeConfigSpec.IntValue envBackdropSampleRays = StealthConfig.ENV_BACKDROP_SAMPLE_RAYS;
        public final ForgeConfigSpec.IntValue envBackdropMinSamples = StealthConfig.ENV_BACKDROP_MIN_SAMPLES;
        public final ForgeConfigSpec.IntValue envBackdropViewerCellSize = StealthConfig.ENV_BACKDROP_VIEWER_CELL_SIZE;
        public final ForgeConfigSpec.IntValue envBackdropCacheTtlTicks = StealthConfig.ENV_BACKDROP_CACHE_TTL_TICKS;
        public final ForgeConfigSpec.DoubleValue envBackdropMinTps = StealthConfig.ENV_BACKDROP_MIN_TPS;
        public final ForgeConfigSpec.DoubleValue envBackdropRecoveryTps = StealthConfig.ENV_BACKDROP_RECOVERY_TPS;
        public final ForgeConfigSpec.BooleanValue envBackdropUseSkyColor = StealthConfig.ENV_BACKDROP_USE_SKY_COLOR;
        public final ForgeConfigSpec.ConfigValue<String> envBackdropDaySkyColor = StealthConfig.ENV_BACKDROP_DAY_SKY_COLOR;
        public final ForgeConfigSpec.ConfigValue<String> envBackdropNightSkyColor = StealthConfig.ENV_BACKDROP_NIGHT_SKY_COLOR;
        public final ForgeConfigSpec.ConfigValue<String> envBackdropRainSkyColor = StealthConfig.ENV_BACKDROP_RAIN_SKY_COLOR;
        public final ForgeConfigSpec.BooleanValue enableEnvironmentalMismatchPenalty = StealthConfig.ENABLE_ENVIRONMENTAL_MISMATCH_PENALTY;
        public final ForgeConfigSpec.DoubleValue environmentalMismatchPenaltyFactor = StealthConfig.ENVIRONMENTAL_MISMATCH_PENALTY_FACTOR;
        public final ForgeConfigSpec.IntValue environmentalMismatchThreshold = StealthConfig.ENVIRONMENTAL_MISMATCH_THRESHOLD;

        public final ForgeConfigSpec.BooleanValue enableTaczIntegration = GunsConfig.ENABLE_TACZ_INTEGRATION;
        public final ForgeConfigSpec.DoubleValue taczReloadRange = GunsConfig.TACZ_RELOAD_RANGE;
        public final ForgeConfigSpec.DoubleValue taczReloadWeight = GunsConfig.TACZ_RELOAD_WEIGHT;
        public final ForgeConfigSpec.DoubleValue taczShootRange = GunsConfig.TACZ_SHOOT_RANGE;
        public final ForgeConfigSpec.DoubleValue taczShootWeight = GunsConfig.TACZ_SHOOT_WEIGHT;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> taczGunShootDecibels = GunsConfig.TACZ_GUN_SHOOT_DECIBELS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> taczAttachmentReductions = GunsConfig.TACZ_ATTACHMENT_REDUCTIONS;
        public final ForgeConfigSpec.DoubleValue taczAttachmentReductionDefault = GunsConfig.TACZ_ATTACHMENT_REDUCTION_DEFAULT;
        public final ForgeConfigSpec.DoubleValue gunshotBaseDetectionRange = GunsConfig.GUNSHOT_BASE_DETECTION_RANGE;
        public final ForgeConfigSpec.IntValue gunshotDetectionDurationTicks = GunsConfig.GUNSHOT_DETECTION_DURATION_TICKS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> taczMuzzleFlashReductions = GunsConfig.TACZ_MUZZLE_FLASH_REDUCTIONS;
        public final ForgeConfigSpec.DoubleValue taczAttachmentFlashReductionDefault = GunsConfig.TACZ_ATTACHMENT_FLASH_REDUCTION_DEFAULT;

        public final ForgeConfigSpec.BooleanValue enablePointBlankIntegration = GunsConfig.ENABLE_POINT_BLANK_INTEGRATION;
        public final ForgeConfigSpec.DoubleValue pointBlankReloadRange = GunsConfig.POINT_BLANK_RELOAD_RANGE;
        public final ForgeConfigSpec.DoubleValue pointBlankReloadWeight = GunsConfig.POINT_BLANK_RELOAD_WEIGHT;
        public final ForgeConfigSpec.DoubleValue pointBlankShootRange = GunsConfig.POINT_BLANK_SHOOT_RANGE;
        public final ForgeConfigSpec.DoubleValue pointBlankShootWeight = GunsConfig.POINT_BLANK_SHOOT_WEIGHT;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> pointBlankGunShootRanges = GunsConfig.POINT_BLANK_GUN_SHOOT_RANGES;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> pointBlankAttachmentSoundReductions = GunsConfig.POINT_BLANK_ATTACHMENT_SOUND_REDUCTIONS;
        public final ForgeConfigSpec.DoubleValue pointBlankAttachmentReductionDefault = GunsConfig.POINT_BLANK_ATTACHMENT_REDUCTION_DEFAULT;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> pointBlankMuzzleFlashReductions = GunsConfig.POINT_BLANK_MUZZLE_FLASH_REDUCTIONS;

        public final ForgeConfigSpec.BooleanValue enableVoiceChatIntegration = VoiceConfig.ENABLE_VOICE_CHAT_INTEGRATION;
        public final ForgeConfigSpec.IntValue voiceChatWhisperRange = VoiceConfig.VOICE_CHAT_WHISPER_RANGE;
        public final ForgeConfigSpec.IntValue voiceChatNormalRange = VoiceConfig.VOICE_CHAT_NORMAL_RANGE;
        public final ForgeConfigSpec.DoubleValue voiceChatWeight = VoiceConfig.VOICE_CHAT_WEIGHT;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> voiceChatDbThresholdMap = VoiceConfig.VOICE_CHAT_DB_THRESHOLD_MAP;

        public final ForgeConfigSpec.DoubleValue defaultHorizontalFov = StealthConfig.DEFAULT_HORIZONTAL_FOV;
        public final ForgeConfigSpec.DoubleValue defaultVerticalFov = StealthConfig.DEFAULT_VERTICAL_FOV;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> fovOverrides = StealthConfig.CUSTOM_FOV_OVERRIDES;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> fovExclusionList = StealthConfig.FOV_EXCLUSION_LIST;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> nonBlockingVisionAllowList = StealthConfig.NON_BLOCKING_VISION_ALLOW_LIST;

        public final ForgeConfigSpec.BooleanValue enableBlockBreaking = IntegrationConfig.ENABLE_BLOCK_BREAKING;
        public final ForgeConfigSpec.DoubleValue blockBreakingTimeMultiplier = IntegrationConfig.BLOCK_BREAKING_TIME_MULTIPLIER;
        public final ForgeConfigSpec.BooleanValue blockBreakingToolOnly = IntegrationConfig.BLOCK_BREAKING_TOOL_ONLY;
        public final ForgeConfigSpec.BooleanValue blockBreakingProperToolOnly = IntegrationConfig.BLOCK_BREAKING_PROPER_TOOL_ONLY;

        public final ForgeConfigSpec.BooleanValue enableTeleportToSound = IntegrationConfig.ENABLE_TELEPORT_TO_SOUND;
        public final ForgeConfigSpec.DoubleValue teleportChance = IntegrationConfig.TELEPORT_CHANCE;
        public final ForgeConfigSpec.IntValue teleportCooldownTicks = IntegrationConfig.TELEPORT_COOLDOWN_TICKS;
        public final ForgeConfigSpec.ConfigValue<String> teleportCanTeleportTag = IntegrationConfig.TELEPORT_CAN_TELEPORT_TAG;
        public final ForgeConfigSpec.ConfigValue<String> teleportCanBeTeleportedTag = IntegrationConfig.TELEPORT_CAN_BE_TELEPORTED_TAG;

        public final ForgeConfigSpec.BooleanValue enablePickUpAndThrowToSound = IntegrationConfig.ENABLE_PICK_UP_AND_THROW_TO_SOUND;
        public final ForgeConfigSpec.DoubleValue pickUpChance = IntegrationConfig.PICK_UP_CHANCE;
        public final ForgeConfigSpec.IntValue pickUpCooldownTicks = IntegrationConfig.PICK_UP_COOLDOWN_TICKS;
        public final ForgeConfigSpec.IntValue pickUpMinDistanceToPickUp = IntegrationConfig.PICK_UP_MIN_DISTANCE_TO_PICK_UP;
        public final ForgeConfigSpec.IntValue pickUpMaxDistanceToThrow = IntegrationConfig.PICK_UP_MAX_DISTANCE_TO_THROW;
        public final ForgeConfigSpec.DoubleValue pickUpSpeedModifier = IntegrationConfig.PICK_UP_SPEED_MODIFIER;
        public final ForgeConfigSpec.ConfigValue<String> pickUpCanPickUpTag = IntegrationConfig.PICK_UP_CAN_PICK_UP_TAG;
        public final ForgeConfigSpec.ConfigValue<String> pickUpCanBePickedUpTag = IntegrationConfig.PICK_UP_CAN_BE_PICKED_UP_TAG;

        public final ForgeConfigSpec.BooleanValue enableXrayTargeting = IntegrationConfig.ENABLE_XRAY_TARGETING;
        public final ForgeConfigSpec.ConfigValue<String> xrayApplyTag = IntegrationConfig.XRAY_APPLY_TAG;
        public final ForgeConfigSpec.BooleanValue xrayRequireBetterNearby = IntegrationConfig.XRAY_REQUIRE_BETTER_NEARBY;
        public final ForgeConfigSpec.ConfigValue<String> xrayBetterNearbyTag = IntegrationConfig.XRAY_BETTER_NEARBY_TAG;
        public final ForgeConfigSpec.IntValue xrayMinRange = IntegrationConfig.XRAY_MIN_RANGE;
        public final ForgeConfigSpec.IntValue xrayMaxRange = IntegrationConfig.XRAY_MAX_RANGE;
        public final ForgeConfigSpec.DoubleValue xrayChance = IntegrationConfig.XRAY_CHANCE;

        public final ForgeConfigSpec.BooleanValue enableCsgrenadesIntegration = IntegrationConfig.ENABLE_CSGRENADES_INTEGRATION;
        public final ForgeConfigSpec.BooleanValue enableTaczNpcIntegration = IntegrationConfig.ENABLE_TACZ_NPC_INTEGRATION;
        public final ForgeConfigSpec.BooleanValue enableFlashbangBlinding = IntegrationConfig.ENABLE_FLASHBANG_BLINDING;
        public final ForgeConfigSpec.DoubleValue flashbangBlindRadius = IntegrationConfig.FLASHBANG_BLIND_RADIUS;
        public final ForgeConfigSpec.IntValue flashbangBlindDurationTicks = IntegrationConfig.FLASHBANG_BLIND_DURATION_TICKS;
        public final ForgeConfigSpec.BooleanValue flashbangRequireLineOfSight = IntegrationConfig.FLASHBANG_REQUIRE_LINE_OF_SIGHT;
        public final ForgeConfigSpec.BooleanValue flashbangRequireFov = IntegrationConfig.FLASHBANG_REQUIRE_FOV;
        public final ForgeConfigSpec.IntValue flashbangMobScanBudget = IntegrationConfig.FLASHBANG_MOB_SCAN_BUDGET;
        public final ForgeConfigSpec.BooleanValue enableSmokeLosBlocking = IntegrationConfig.ENABLE_SMOKE_LOS_BLOCKING;
        public final ForgeConfigSpec.DoubleValue smokeCloudRadius = IntegrationConfig.SMOKE_CLOUD_RADIUS;
        public final ForgeConfigSpec.IntValue smokeLifetimeMaxTicks = IntegrationConfig.SMOKE_LIFETIME_MAX_TICKS;
        public final ForgeConfigSpec.BooleanValue enableFireFlee = IntegrationConfig.ENABLE_FIRE_FLEE;
        public final ForgeConfigSpec.DoubleValue fireFleeDangerRadius = IntegrationConfig.FIRE_FLEE_DANGER_RADIUS;
        public final ForgeConfigSpec.DoubleValue fireFleeAwayDistance = IntegrationConfig.FIRE_FLEE_AWAY_DISTANCE;
        public final ForgeConfigSpec.DoubleValue fireFleeSpeedModifier = IntegrationConfig.FIRE_FLEE_SPEED_MODIFIER;
        public final ForgeConfigSpec.IntValue fireFleeCooldownTicks = IntegrationConfig.FIRE_FLEE_COOLDOWN_TICKS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> fleeFromFireEligibleMobs = IntegrationConfig.FLEE_FROM_FIRE_ELIGIBLE_MOBS;
        public final ForgeConfigSpec.IntValue csgrenadesTrackerScanIntervalTicks = IntegrationConfig.CSGRENADES_TRACKER_SCAN_INTERVAL_TICKS;
        public final ForgeConfigSpec.BooleanValue enableHotBathIntegration = IntegrationConfig.ENABLE_HOTBATH_INTEGRATION;
        public final ForgeConfigSpec.IntValue hotBathPollIntervalTicks = IntegrationConfig.HOTBATH_POLL_INTERVAL_TICKS;
        public final ForgeConfigSpec.DoubleValue hotBathMaxDirtyScentMultiplier = IntegrationConfig.HOTBATH_MAX_DIRTY_SCENT_MULTIPLIER;
        public final ForgeConfigSpec.IntValue hotBathDefaultBathAromaDurationTicks = IntegrationConfig.HOTBATH_DEFAULT_BATH_AROMA_DURATION_TICKS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> hotBathFluidScentModifiers = IntegrationConfig.HOTBATH_FLUID_SCENT_MODIFIERS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> hotBathBiomeScentModifiers = IntegrationConfig.HOTBATH_BIOME_SCENT_MODIFIERS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> hotBathCamoWashRates = IntegrationConfig.HOTBATH_CAMO_WASH_RATES;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> hotBathSplashCamoWashRates = IntegrationConfig.HOTBATH_SPLASH_CAMO_WASH_RATES;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> hotBathCustomFluidScentModifiers = IntegrationConfig.HOTBATH_CUSTOM_FLUID_SCENT_MODIFIERS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> hotBathCustomFluidCamoWashRates = IntegrationConfig.HOTBATH_CUSTOM_FLUID_CAMO_WASH_RATES;
        public final ForgeConfigSpec.BooleanValue enableSpoiledFoodScent = IntegrationConfig.ENABLE_SPOILED_FOOD_SCENT;
        public final ForgeConfigSpec.DoubleValue spoiledFoodOdorPerItem = IntegrationConfig.SPOILED_FOOD_ODOR_PER_ITEM;
        public final ForgeConfigSpec.BooleanValue enableToughAsNailsIntegration = IntegrationConfig.ENABLE_TOUGH_AS_NAILS_INTEGRATION;
        public final ForgeConfigSpec.IntValue toughAsNailsPollIntervalTicks = IntegrationConfig.TOUGH_AS_NAILS_POLL_INTERVAL_TICKS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> toughAsNailsTemperatureScentMultipliers = IntegrationConfig.TOUGH_AS_NAILS_TEMPERATURE_SCENT_MULTIPLIERS;
        public final ForgeConfigSpec.DoubleValue toughAsNailsHyperthermiaScentMultiplier = IntegrationConfig.TOUGH_AS_NAILS_HYPERTHERMIA_SCENT_MULTIPLIER;
        public final ForgeConfigSpec.DoubleValue toughAsNailsHyperthermiaSoundMultiplier = IntegrationConfig.TOUGH_AS_NAILS_HYPERTHERMIA_SOUND_MULTIPLIER;
        public final ForgeConfigSpec.DoubleValue toughAsNailsThirstScentMultiplierAtEmpty = IntegrationConfig.TOUGH_AS_NAILS_THIRST_SCENT_MULTIPLIER_AT_EMPTY;
        public final ForgeConfigSpec.DoubleValue toughAsNailsThirstSoundMultiplierAtEmpty = IntegrationConfig.TOUGH_AS_NAILS_THIRST_SOUND_MULTIPLIER_AT_EMPTY;
        public final ForgeConfigSpec.DoubleValue toughAsNailsWoolArmorSoundMultiplierPerPiece = IntegrationConfig.TOUGH_AS_NAILS_WOOL_ARMOR_SOUND_MULTIPLIER_PER_PIECE;
        public final ForgeConfigSpec.DoubleValue toughAsNailsWoolArmorMinimumSoundMultiplier = IntegrationConfig.TOUGH_AS_NAILS_WOOL_ARMOR_MINIMUM_SOUND_MULTIPLIER;
        public final ForgeConfigSpec.DoubleValue toughAsNailsWoolWarmScentMultiplierPerPiece = IntegrationConfig.TOUGH_AS_NAILS_WOOL_WARM_SCENT_MULTIPLIER_PER_PIECE;
        public final ForgeConfigSpec.DoubleValue toughAsNailsWoolHotScentMultiplierPerPiece = IntegrationConfig.TOUGH_AS_NAILS_WOOL_HOT_SCENT_MULTIPLIER_PER_PIECE;
        public final ForgeConfigSpec.DoubleValue toughAsNailsWoolHeatMaximumScentMultiplier = IntegrationConfig.TOUGH_AS_NAILS_WOOL_HEAT_MAXIMUM_SCENT_MULTIPLIER;
        public final ForgeConfigSpec.DoubleValue toughAsNailsLeafArmorScentMultiplierPerPiece = IntegrationConfig.TOUGH_AS_NAILS_LEAF_ARMOR_SCENT_MULTIPLIER_PER_PIECE;
        public final ForgeConfigSpec.DoubleValue toughAsNailsLeafArmorMinimumScentMultiplier = IntegrationConfig.TOUGH_AS_NAILS_LEAF_ARMOR_MINIMUM_SCENT_MULTIPLIER;

        public final ForgeConfigSpec.IntValue configSchemaVersion = GeneralConfig.CONFIG_SCHEMA_VERSION;

        public final ForgeConfigSpec.BooleanValue enableFloorCreek = GeneralConfig.ENABLE_FLOOR_CREEK;
        public final ForgeConfigSpec.DoubleValue floorCreekProbSwimmingCrawling = GeneralConfig.FLOOR_CREEK_PROB_SWIMMING_CRAWLING;
        public final ForgeConfigSpec.DoubleValue floorCreekProbSneaking = GeneralConfig.FLOOR_CREEK_PROB_SNEAKING;
        public final ForgeConfigSpec.DoubleValue floorCreekProbWalking = GeneralConfig.FLOOR_CREEK_PROB_WALKING;
        public final ForgeConfigSpec.DoubleValue floorCreekProbSprinting = GeneralConfig.FLOOR_CREEK_PROB_SPRINTING;
        public final ForgeConfigSpec.DoubleValue floorCreekProbJump = GeneralConfig.FLOOR_CREEK_PROB_JUMP;
        public final ForgeConfigSpec.DoubleValue floorCreekDistanceStep = GeneralConfig.FLOOR_CREEK_DISTANCE_STEP;

        public final ForgeConfigSpec.BooleanValue enableArrowInvestigation = GeneralConfig.ENABLE_ARROW_INVESTIGATION;
        public final ForgeConfigSpec.DoubleValue arrowNearMissRadius = GeneralConfig.ARROW_NEAR_MISS_RADIUS;
        public final ForgeConfigSpec.DoubleValue arrowImpactRadius = GeneralConfig.ARROW_IMPACT_RADIUS;
        public final ForgeConfigSpec.IntValue arrowInvestigationOffset = GeneralConfig.ARROW_INVESTIGATION_OFFSET;
        public final ForgeConfigSpec.IntValue arrowInvestigationCooldownTicks = GeneralConfig.ARROW_INVESTIGATION_COOLDOWN_TICKS;
        public final ForgeConfigSpec.DoubleValue arrowReverseExtrapolateMaxDistance = GeneralConfig.ARROW_REVERSE_EXTRAPOLATE_MAX_DISTANCE;
        public final ForgeConfigSpec.BooleanValue arrowInvestigationRespectCurrentTarget = GeneralConfig.ARROW_INVESTIGATION_RESPECT_CURRENT_TARGET;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> investigateProjectileIds = GeneralConfig.INVESTIGATE_PROJECTILE_IDS;

        public final ForgeConfigSpec.BooleanValue enableOptimizedLos = PerformanceConfig.ENABLE_OPTIMIZED_LOS;
        public final ForgeConfigSpec.BooleanValue enableOptimizedLosPairCache = PerformanceConfig.ENABLE_OPTIMIZED_LOS_PAIR_CACHE;
        public final ForgeConfigSpec.IntValue optimizedLosPairCacheMaxEntries = PerformanceConfig.OPTIMIZED_LOS_PAIR_CACHE_MAX_ENTRIES;
        public final ForgeConfigSpec.BooleanValue enableOptimizedLosVanillaFallback = PerformanceConfig.ENABLE_OPTIMIZED_LOS_VANILLA_FALLBACK;

        public final ForgeConfigSpec.BooleanValue enableLosBatching = PerformanceConfig.ENABLE_LOS_BATCHING;
        public final ForgeConfigSpec.IntValue losBatchBudgetPerTick = PerformanceConfig.LOS_BATCH_BUDGET_PER_TICK;
        public final ForgeConfigSpec.IntValue losBatchQueueMaxSize = PerformanceConfig.LOS_BATCH_QUEUE_MAX_SIZE;

        public final ForgeConfigSpec.BooleanValue enableLivingEntityLosOverride = PerformanceConfig.ENABLE_LIVING_ENTITY_LOS_OVERRIDE;

        public final ForgeConfigSpec.IntValue globalCacheMaxSize = PerformanceConfig.GLOBAL_CACHE_MAX_SIZE;
        public final ForgeConfigSpec.IntValue globalCacheExpireMins = PerformanceConfig.GLOBAL_CACHE_EXPIRE_MINS;

        public final ForgeConfigSpec.BooleanValue enableRelentlessClimbing = IntegrationConfig.ENABLE_RELENTLESS_CLIMBING;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> relentlessEligibleMobs = IntegrationConfig.RELENTLESS_ELIGIBLE_MOBS;
        public final ForgeConfigSpec.BooleanValue zombiesIgnoreHeight = IntegrationConfig.ZOMBIES_IGNORE_HEIGHT;
        public final ForgeConfigSpec.BooleanValue zombiesCanStack = IntegrationConfig.ZOMBIES_CAN_STACK;
        public final ForgeConfigSpec.DoubleValue zombieFallDamageMultiplier = IntegrationConfig.ZOMBIE_FALL_DAMAGE_MULTIPLIER;

        public final ForgeConfigSpec.BooleanValue enableImmersiveMelodiesIntegration = IntegrationConfig.ENABLE_IMMERSIVE_MELODIES_INTEGRATION;
        public final ForgeConfigSpec.IntValue immersiveMelodiesPollInterval = IntegrationConfig.IMMERSIVE_MELODIES_POLL_INTERVAL;
        public final ForgeConfigSpec.DoubleValue immersiveMelodiesDefaultRange = IntegrationConfig.IMMERSIVE_MELODIES_DEFAULT_RANGE;
        public final ForgeConfigSpec.DoubleValue immersiveMelodiesDefaultWeight = IntegrationConfig.IMMERSIVE_MELODIES_DEFAULT_WEIGHT;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> immersiveMelodiesInstrumentMultipliers = IntegrationConfig.IMMERSIVE_MELODIES_INSTRUMENT_MULTIPLIERS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> immersiveMelodiesMelodyOverrides = IntegrationConfig.IMMERSIVE_MELODIES_MELODY_OVERRIDES;

        public final ForgeConfigSpec.BooleanValue enableSporeIntegration = IntegrationConfig.ENABLE_SPORE_INTEGRATION;
        public final ForgeConfigSpec.BooleanValue enableSporeScentTrail = IntegrationConfig.ENABLE_SPORE_SCENT_TRAIL;
        public final ForgeConfigSpec.BooleanValue enableStealthMarkerBridge = IntegrationConfig.ENABLE_STEALTH_MARKER_BRIDGE;
        public final ForgeConfigSpec.BooleanValue enableScentBlockSuppression = IntegrationConfig.ENABLE_SCENT_BLOCK_SUPPRESSION;
        public final ForgeConfigSpec.BooleanValue enableProtoSoundDeployment = IntegrationConfig.ENABLE_PROTO_SOUND_DEPLOYMENT;
        public final ForgeConfigSpec.BooleanValue enableSoundRelay = IntegrationConfig.ENABLE_SOUND_RELAY;
        public final ForgeConfigSpec.BooleanValue smartSpreadToSound = IntegrationConfig.SMART_SPREAD_TO_SOUND;
        public final ForgeConfigSpec.DoubleValue stealthMarkerCamoThreshold = IntegrationConfig.STEALTH_MARKER_CAMO_THRESHOLD;
        public final ForgeConfigSpec.IntValue stealthBridgeCheckIntervalTicks = IntegrationConfig.STEALTH_BRIDGE_CHECK_INTERVAL_TICKS;
        public final ForgeConfigSpec.IntValue scentEntityDissipationAccel = IntegrationConfig.SCENT_ENTITY_DISSIPATION_ACCEL;
        public final ForgeConfigSpec.DoubleValue protoSoundWeightThreshold = IntegrationConfig.PROTO_SOUND_WEIGHT_THRESHOLD;
        public final ForgeConfigSpec.IntValue protoSoundDeploymentCooldownTicks = IntegrationConfig.PROTO_SOUND_DEPLOYMENT_COOLDOWN_TICKS;
        public final ForgeConfigSpec.IntValue protoSoundBiomassThreshold = IntegrationConfig.PROTO_SOUND_BIOMASS_THRESHOLD;
        public final ForgeConfigSpec.DoubleValue protoSpreadMaxDistance = IntegrationConfig.PROTO_SPREAD_MAX_DISTANCE;
        public final ForgeConfigSpec.DoubleValue protoSpreadLerpFactor = IntegrationConfig.PROTO_SPREAD_LERP_FACTOR;
        public final ForgeConfigSpec.DoubleValue soundRelayRange = IntegrationConfig.SOUND_RELAY_RANGE;

        public final ForgeConfigSpec.BooleanValue enableScentSystem = ScentConfig.ENABLE_SCENT_SYSTEM;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> scentEligibleMobs = ScentConfig.SCENT_ELIGIBLE_MOBS;
        public final ForgeConfigSpec.BooleanValue enableScentParticles = ScentConfig.ENABLE_SCENT_PARTICLES;
        public final ForgeConfigSpec.BooleanValue enableGameplayScentParticles = ScentConfig.ENABLE_GAMEPLAY_SCENT_PARTICLES;
        public final ForgeConfigSpec.IntValue scentParticleSpawnInterval = ScentConfig.SCENT_PARTICLE_SPAWN_INTERVAL;
        public final ForgeConfigSpec.DoubleValue scentParticleRenderDistance = ScentConfig.SCENT_PARTICLE_RENDER_DISTANCE;
        public final ForgeConfigSpec.IntValue scentNodeDurationTicks = ScentConfig.SCENT_NODE_DURATION_TICKS;
        public final ForgeConfigSpec.DoubleValue scentCreationIntervalBlocks = ScentConfig.SCENT_CREATION_INTERVAL_BLOCKS;
        public final ForgeConfigSpec.DoubleValue tempHeavyDecayThreshold = ScentConfig.TEMP_HEAVY_DECAY_THRESHOLD;
        public final ForgeConfigSpec.DoubleValue tempFreezeThreshold = ScentConfig.TEMP_FREEZE_THRESHOLD;
        public final ForgeConfigSpec.DoubleValue humidityPreserveThreshold = ScentConfig.HUMIDITY_PRESERVE_THRESHOLD;
        public final ForgeConfigSpec.DoubleValue rainDecayMultiplier = ScentConfig.RAIN_DECAY_MULTIPLIER;
        public final ForgeConfigSpec.BooleanValue waterStopsScent = ScentConfig.WATER_STOPS_SCENT;
        public final ForgeConfigSpec.IntValue scentAmbushDurationTicks = ScentConfig.SCENT_AMBUSH_DURATION_TICKS;
        public final ForgeConfigSpec.IntValue scentScanBudgetPerTick = ScentConfig.SCENT_SCAN_BUDGET_PER_TICK;
        public final ForgeConfigSpec.DoubleValue scentDetectionRadius = ScentConfig.SCENT_DETECTION_RADIUS;
        public final ForgeConfigSpec.BooleanValue enableArrowScentTrail = ScentConfig.ENABLE_ARROW_SCENT_TRAIL;
        public final ForgeConfigSpec.DoubleValue arrowScentEmissionIntervalBlocks = ScentConfig.ARROW_SCENT_EMISSION_INTERVAL_BLOCKS;
        public final ForgeConfigSpec.DoubleValue arrowScentStrength = ScentConfig.ARROW_SCENT_STRENGTH;
        public final ForgeConfigSpec.IntValue arrowScentNodeDurationTicks = ScentConfig.ARROW_SCENT_NODE_DURATION_TICKS;
        public final ForgeConfigSpec.BooleanValue scentOverrideSoundPriority = ScentConfig.SCENT_OVERRIDE_SOUND_PRIORITY;
        public final ForgeConfigSpec.BooleanValue enableScentParticlesForArrows = ScentConfig.ENABLE_SCENT_PARTICLES_FOR_ARROWS;
        public final ForgeConfigSpec.IntValue arrowScentRateLimitPerShooterPerMinute = ScentConfig.ARROW_SCENT_RATE_LIMIT_PER_SHOOTER_PER_MINUTE;
        public final ForgeConfigSpec.DoubleValue scentOverrideSoundWeightThreshold = ScentConfig.SCENT_OVERRIDE_SOUND_WEIGHT_THRESHOLD;
        public final ForgeConfigSpec.DoubleValue scentVsSoundHybridMultiplier = ScentConfig.SCENT_VS_SOUND_HYBRID_MULTIPLIER;
        public final ForgeConfigSpec.IntValue scentQueryCacheTtlTicks = ScentConfig.SCENT_QUERY_CACHE_TTL_TICKS;
        public final ForgeConfigSpec.IntValue scentQueryCacheMaxEntries = ScentConfig.SCENT_QUERY_CACHE_MAX_ENTRIES;

        public Common(ForgeConfigSpec.Builder builder) {

        }

    }

    @SuppressWarnings("unchecked")
    private static <T> void moveConfigValue(com.electronwill.nightconfig.core.UnmodifiableConfig config, String oldPath, net.minecraftforge.common.ForgeConfigSpec.ConfigValue<T> newValue) {
        if (config.contains(oldPath)) {
            Object oldValue = config.get(oldPath);

            newValue.set((T) oldValue);
            SoundAttractMod.LOGGER.info("Migrated config value '{}' -> '{}'", oldPath, String.join(".", newValue.getPath()));
        }
    }

    private static void saveConfigSpec(ForgeConfigSpec spec, String name) {
        if (spec == null || !spec.isLoaded()) {
            return;
        }
        try {
            spec.save();
        } catch (Throwable t) {
            SoundAttractMod.LOGGER.warn("[SoundAttract] {} config save failed after migration: {}", name, t.toString());
        }
    }

    private static int ensureToughAsNailsArmorColorConfigEntries() {
        if (COMMON == null || COMMON.customArmorColors == null) {
            return 0;
        }

        List<String> armorColors = new ArrayList<>(COMMON.customArmorColors.get());
        List<String> tanArmorColors = List.of(
                "toughasnails:leaf_helmet;#48B518",
                "toughasnails:leaf_chestplate;#48B518",
                "toughasnails:leaf_leggings;#48B518",
                "toughasnails:leaf_boots;#48B518",
                "toughasnails:wool_helmet;#FFFFFF",
                "toughasnails:wool_chestplate;#FFFFFF",
                "toughasnails:wool_leggings;#FFFFFF",
                "toughasnails:wool_boots;#FFFFFF");
        int addedArmorColors = 0;
        for (String entry : tanArmorColors) {
            String itemId = entry.split(";")[0];
            boolean present = armorColors.stream().anyMatch(existing -> existing != null && existing.trim().startsWith(itemId + ";"));
            if (!present) {
                armorColors.add(entry);
                addedArmorColors++;
            }
        }

        if (addedArmorColors > 0) {
            COMMON.customArmorColors.set(armorColors);
            SoundAttractMod.LOGGER.info("Added {} ToughAsNails armor color defaults.", addedArmorColors);
            saveConfigSpec(StealthConfig.SPEC, "stealth");
        }
        return addedArmorColors;
    }

    public static void bakeConfig() {
        if (!commonConfigsReady()) {
            return;
        }
        if (COMMON.configSchemaVersion.get() < 12) {
            SoundAttractMod.LOGGER.info("Config migration: Sound muffling factors updated to new defaults (Schema v12).");
            COMMON.mufflingFactorWool.set(0.75);
            COMMON.mufflingFactorSolid.set(0.85);
            COMMON.mufflingFactorNonSolid.set(0.93);
            COMMON.mufflingFactorThin.set(0.95);
            COMMON.configSchemaVersion.set(12);
        }

        if (COMMON.configSchemaVersion.get() < 13) {
            SoundAttractMod.LOGGER.info("Config migration: Updating attracted entities to include new Spore support (Schema v13).");

            List<String> currentEntities = new ArrayList<>(COMMON.attractedEntities.get());
            List<? extends String> defaultEntities = GeneralConfig.ATTRACTED_ENTITIES.getDefault();

            int addedCount = 0;
            for (String entityId : defaultEntities) {
                if (!currentEntities.contains(entityId)) {
                    currentEntities.add(entityId);
                    addedCount++;
                }
            }

            if (addedCount > 0) {
                COMMON.attractedEntities.set(currentEntities);
                SoundAttractMod.LOGGER.info("Added {} new default entities to the attracted list.", addedCount);
            }

            COMMON.configSchemaVersion.set(13);
        }

        if (COMMON.configSchemaVersion.get() < 14) {
            SoundAttractMod.LOGGER.info("Config migration: Updating sound whitelist to include new Spore sounds (Schema v14).");

            List<String> currentWhitelist = new ArrayList<>(COMMON.soundIdWhitelist.get());
            List<? extends String> defaultWhitelist = GeneralConfig.SOUND_ID_WHITELIST.getDefault();

            int addedCount = 0;
            for (String soundId : defaultWhitelist) {
                if (!currentWhitelist.contains(soundId)) {
                    currentWhitelist.add(soundId);
                    addedCount++;
                }
            }

            if (addedCount > 0) {
                COMMON.soundIdWhitelist.set(currentWhitelist);
                SoundAttractMod.LOGGER.info("Added {} new default sounds to the whitelist.", addedCount);
            }

            COMMON.configSchemaVersion.set(14);
        }

        if (COMMON.configSchemaVersion.get() < 15) {
            SoundAttractMod.LOGGER.info("Config migration: Adding Quantified sound score slice cache settings (Schema v15).");
            COMMON.configSchemaVersion.set(15);
        }

        if (COMMON.configSchemaVersion.get() < 16) {
            SoundAttractMod.LOGGER.info("Config migration: Adding floor creek + arrow investigation defaults (Schema v16).");

            List<String> whitelist = new ArrayList<>(COMMON.soundIdWhitelist.get());
            List<String> toAddWhitelist = List.of(
                    "soundattract:wooden_floor_creek",
                    "soundattract:arrow_miss",
                    "soundattract:arrow_impact");
            int addedWhitelist = 0;
            for (String id : toAddWhitelist) {
                if (!whitelist.contains(id)) { whitelist.add(id); addedWhitelist++; }
            }
            if (addedWhitelist > 0) {
                COMMON.soundIdWhitelist.set(whitelist);
                SoundAttractMod.LOGGER.info("Added {} sound ids to whitelist (v16).", addedWhitelist);
            }

            List<String> defaults = new ArrayList<>(COMMON.rawSoundDefaults.get());
            List<String> toAddDefaults = List.of(
                    "soundattract:wooden_floor_creek;20;20",
                    "soundattract:arrow_miss;16;10",
                    "soundattract:arrow_impact;24;15");
            int addedDefaults = 0;
            for (String entry : toAddDefaults) {
                String soundId = entry.split(";")[0];
                boolean present = defaults.stream().anyMatch(e -> e.startsWith(soundId + ";"));
                if (!present) { defaults.add(entry); addedDefaults++; }
            }
            if (addedDefaults > 0) {
                COMMON.rawSoundDefaults.set(defaults);
                SoundAttractMod.LOGGER.info("Added {} sound defaults (v16).", addedDefaults);
            }

            COMMON.configSchemaVersion.set(16);
        }

        if (COMMON.configSchemaVersion.get() < 17) {
            SoundAttractMod.LOGGER.info("Config migration: Adding CS Grenades sound defaults (Schema v17).");

            List<String> whitelist = new ArrayList<>(COMMON.soundIdWhitelist.get());
            List<String> toAddWhitelist = List.of(
                    "csgrenades:he_explode",
                    "csgrenades:flash_bang_explode",
                    "csgrenades:smoke_explode",
                    "csgrenades:incendiary_explode",
                    "csgrenades:molotov_explode");
            int addedWhitelist = 0;
            for (String id : toAddWhitelist) {
                if (!whitelist.contains(id)) { whitelist.add(id); addedWhitelist++; }
            }
            if (addedWhitelist > 0) {
                COMMON.soundIdWhitelist.set(whitelist);
                SoundAttractMod.LOGGER.info("Added {} CS Grenades sound ids to whitelist (v17).", addedWhitelist);
            }

            List<String> defaults = new ArrayList<>(COMMON.rawSoundDefaults.get());
            List<String> toAddDefaults = List.of(
                    "csgrenades:he_explode;64;20",
                    "csgrenades:flash_bang_explode;32;8",
                    "csgrenades:smoke_explode;12;3",
                    "csgrenades:incendiary_explode;24;6",
                    "csgrenades:molotov_explode;24;6");
            int addedDefaults = 0;
            for (String entry : toAddDefaults) {
                String soundId = entry.split(";")[0];
                boolean present = defaults.stream().anyMatch(e -> e.startsWith(soundId + ";"));
                if (!present) { defaults.add(entry); addedDefaults++; }
            }
            if (addedDefaults > 0) {
                COMMON.rawSoundDefaults.set(defaults);
                SoundAttractMod.LOGGER.info("Added {} CS Grenades sound defaults (v17).", addedDefaults);
            }

            COMMON.configSchemaVersion.set(17);
        }

        if (COMMON.configSchemaVersion.get() < 18) {
            SoundAttractMod.LOGGER.info("Config migration: Adding ToughAsNails armor color defaults (Schema v18).");

            ensureToughAsNailsArmorColorConfigEntries();
            COMMON.configSchemaVersion.set(18);
            saveConfigSpec(GeneralConfig.SPEC, "general");
        }

        if (COMMON.configSchemaVersion.get() < 19) {
            List<String> previousOdorDefaults = List.of(
                    "minecraft:rotten_flesh=1.0",
                    "minecraft:beef=1.0",
                    "minecraft:porkchop=1.0",
                    "minecraft:chicken=1.0",
                    "minecraft:mutton=1.0",
                    "minecraft:rabbit=1.0",
                    "minecraft:cod=0.5",
                    "minecraft:salmon=0.5");
            List<String> configuredOdorValues = new ArrayList<>(COMMON.playerOdorItemValues.get());
            if (configuredOdorValues.equals(previousOdorDefaults)) {
                COMMON.playerOdorItemValues.set(new ArrayList<>(COMMON.playerOdorItemValues.getDefault()));
                saveConfigSpec(ScentConfig.SPEC, "scent");
                SoundAttractMod.LOGGER.info("Config migration: Reduced default food odor values (Schema v19).");
            }
            COMMON.configSchemaVersion.set(19);
            saveConfigSpec(GeneralConfig.SPEC, "general");
        }

        ensureToughAsNailsArmorColorConfigEntries();

        SOUND_ID_WHITELIST_CACHE.clear();
        COMMON.soundIdWhitelist.get().forEach(idStr -> {
            ResourceLocation loc = ResourceLocation.tryParse(idStr);
            if (loc != null) {
                SOUND_ID_WHITELIST_CACHE.add(loc);
            } else {
                SoundAttractMod.LOGGER.warn("Invalid ResourceLocation in soundIdWhitelist: {}", idStr);
            }
        });
        ATTRACTED_ENTITY_TYPES_CACHE.clear();
        if (COMMON.attractedEntities != null) {
            COMMON.attractedEntities.get().forEach(id -> ATTRACTED_ENTITY_TYPES_CACHE.add(id.toString()));
        }

        STEALTH_BYPASS_ENTITY_TYPES_CACHE.clear();
        if (COMMON.stealthBypassMobIds != null) {
            for (String entry : COMMON.stealthBypassMobIds.get()) {
                if (entry == null || entry.trim().isEmpty()) continue;
                ResourceLocation loc = ResourceLocation.tryParse(entry.trim());
                if (loc != null) {
                    STEALTH_BYPASS_ENTITY_TYPES_CACHE.add(loc);
                } else {
                    SoundAttractMod.LOGGER.warn("Invalid ResourceLocation in stealthBypassMobIds: {}", entry);
                }
            }
        }
        STEALTH_BYPASS_MOD_NAMESPACES_CACHE.clear();
        if (COMMON.stealthBypassModNamespaces != null) {
            for (String entry : COMMON.stealthBypassModNamespaces.get()) {
                if (entry == null) continue;
                String cleaned = entry.trim().toLowerCase(Locale.ROOT);
                if (cleaned.isEmpty()) continue;
                STEALTH_BYPASS_MOD_NAMESPACES_CACHE.add(cleaned);
            }
        }

        INVESTIGATE_PROJECTILE_TYPES_CACHE.clear();
        if (COMMON.investigateProjectileIds != null) {
            for (String idStr : COMMON.investigateProjectileIds.get()) {
                ResourceLocation loc = ResourceLocation.tryParse(idStr);
                if (loc == null) {
                    SoundAttractMod.LOGGER.warn("investigateProjectileIds: invalid id '{}'", idStr);
                    continue;
                }
                net.minecraft.world.entity.EntityType<?> type =
                        net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(loc);
                if (type == null) {
                    if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.ARROW_INVESTIGATION)) {
                        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.ARROW_INVESTIGATION)) {
                            SoundAttractMod.LOGGER.debug("investigateProjectileIds: unknown entity type '{}'", idStr);
                        }
                    }
                    continue;
                }
                INVESTIGATE_PROJECTILE_TYPES_CACHE.add(type);
            }
        }

        SOUND_DEFAULT_ENTRIES_CACHE.clear();
        if (COMMON.rawSoundDefaults != null) {
            COMMON.rawSoundDefaults.get().forEach(entry -> {
                String[] parts = entry.split(";");
                if (parts.length == 3) {
                    ResourceLocation soundId = ResourceLocation.tryParse(parts[0]);
                    try {
                        double range = Double.parseDouble(parts[1]);
                        double weight = Double.parseDouble(parts[2]);
                        if (soundId != null) {
                            SOUND_DEFAULT_ENTRIES_CACHE.put(soundId, new SoundDefaultEntry(range, weight));
                        }
                    } catch (NumberFormatException e) {
                        SoundAttractMod.LOGGER.warn("Could not parse range/weight for sound default entry: {}", entry, e);
                    }
                }
            });
        }

        boolean enableDataDriven = COMMON.enableDataDriven.get();
        String priority = COMMON.datapackPriority.get();
        boolean datapackOverConfig = "datapack_over_config".equalsIgnoreCase(priority);

        if (enableDataDriven && !DP_SOUND_WHITELIST_CACHE.isEmpty()) {
            if (datapackOverConfig) {
                SOUND_ID_WHITELIST_CACHE.clear();
                SOUND_ID_WHITELIST_CACHE.addAll(DP_SOUND_WHITELIST_CACHE);
            } else {
                for (ResourceLocation loc : DP_SOUND_WHITELIST_CACHE) {
                    SOUND_ID_WHITELIST_CACHE.add(loc);
                }
            }
        }

        if (enableDataDriven && !DP_SOUND_DEFAULTS_CACHE.isEmpty()) {
            if (datapackOverConfig) {
                for (Map.Entry<ResourceLocation, SoundDefaultEntry> e : DP_SOUND_DEFAULTS_CACHE.entrySet()) {
                    SOUND_DEFAULT_ENTRIES_CACHE.put(e.getKey(), e.getValue());
                }
            } else {
                for (Map.Entry<ResourceLocation, SoundDefaultEntry> e : DP_SOUND_DEFAULTS_CACHE.entrySet()) {
                    SOUND_DEFAULT_ENTRIES_CACHE.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }

        CUSTOM_LIQUID_BLOCKS_CACHE.clear();
        if (COMMON.customLiquidBlocks != null) {
            COMMON.customLiquidBlocks.get().forEach(id -> CUSTOM_LIQUID_BLOCKS_CACHE.add(ResourceLocation.parse(id)));
        }
        CUSTOM_WOOL_BLOCKS_CACHE.clear();
        if (COMMON.customWoolBlocks != null) {
            COMMON.customWoolBlocks.get().forEach(id -> CUSTOM_WOOL_BLOCKS_CACHE.add(ResourceLocation.parse(id)));
        }
        CUSTOM_SOLID_BLOCKS_CACHE.clear();
        if (COMMON.customSolidBlocks != null) {
            COMMON.customSolidBlocks.get().forEach(id -> CUSTOM_SOLID_BLOCKS_CACHE.add(ResourceLocation.parse(id)));
        }
        CUSTOM_NON_SOLID_BLOCKS_CACHE.clear();
        if (COMMON.customNonSolidBlocks != null) {
            COMMON.customNonSolidBlocks.get().forEach(id -> CUSTOM_NON_SOLID_BLOCKS_CACHE.add(ResourceLocation.parse(id)));
        }
        CUSTOM_THIN_BLOCKS_CACHE.clear();
        if (COMMON.customThinBlocks != null) {
            COMMON.customThinBlocks.get().forEach(id -> CUSTOM_THIN_BLOCKS_CACHE.add(ResourceLocation.parse(id)));
        }
        CUSTOM_AIR_BLOCKS_CACHE.clear();
        if (COMMON.customAirBlocks != null) {
            COMMON.customAirBlocks.get().forEach(id -> CUSTOM_AIR_BLOCKS_CACHE.add(ResourceLocation.parse(id)));
        }

        parseAndCacheNonBlockingVisionAllowList();

        TACZ_ENABLED_CACHE = ModList.get().isLoaded("tacz") && (!serverReady() || SERVER.enableTaczIntegration.get());
        TACZ_RELOAD_RANGE_CACHE = COMMON.taczReloadRange.get();
        TACZ_RELOAD_WEIGHT_CACHE = COMMON.taczReloadWeight.get();
        TACZ_SHOOT_RANGE_CACHE = COMMON.taczShootRange.get();
        TACZ_SHOOT_WEIGHT_CACHE = COMMON.taczShootWeight.get();
        TACZ_ATTACHMENT_REDUCTION_DEFAULT_CACHE = COMMON.taczAttachmentReductionDefault.get();
        TACZ_GUN_SHOOT_DB_CACHE.clear();
        List<? extends String> rawShoot = COMMON.taczGunShootDecibels.get();
        for (String raw : rawShoot) {
            try {
                String[] parts = raw.split(";", 2);
                ResourceLocation rl = ResourceLocation.tryParse(parts[0]);
                double db = Double.parseDouble(parts[1]);
                if (rl != null && db >= 0) {
                    double weight = db / 10.0;
                    TACZ_GUN_SHOOT_DB_CACHE.put(rl, Pair.of(db, weight));
                }
            } catch (Exception e) {
            }
        }

        TACZ_ATTACHMENT_REDUCTION_DB_CACHE.clear();
        List<? extends String> rawAtt = COMMON.taczAttachmentReductions.get();
        for (String raw : rawAtt) {
            try {
                String[] parts = raw.split(";", 2);
                String idStr = parts[0].trim();
                ResourceLocation rl = ResourceLocation.tryParse(idStr);
                double reduction = Double.parseDouble(parts[1]);
                if (rl != null) {
                    TACZ_ATTACHMENT_REDUCTION_DB_CACHE.put(rl.toString(), reduction);
                }
            } catch (Exception e) {
            }
        }

        TACZ_MUZZLE_FLASH_REDUCTION_CACHE.clear();
        List<? extends String> rawFlashAtt = COMMON.taczMuzzleFlashReductions.get();
        for (String raw : rawFlashAtt) {
            try {
                String[] parts = raw.split(";", 2);
                ResourceLocation rl = ResourceLocation.tryParse(parts[0]);
                double reductionValue = Double.parseDouble(parts[1]);
                if (rl != null) {
                    TACZ_MUZZLE_FLASH_REDUCTION_CACHE.put(rl, reductionValue);
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("Failed to parse muzzle flash reduction entry: {}", raw, e);
            }
        }

        TACZ_ATTACHMENT_FLASH_REDUCTION_DEFAULT_CACHE = COMMON.taczAttachmentFlashReductionDefault.get();

        if (enableDataDriven && !DP_TACZ_GUN_SHOOT_DB_CACHE.isEmpty()) {
            if (datapackOverConfig) {
                TACZ_GUN_SHOOT_DB_CACHE.clear();
                TACZ_GUN_SHOOT_DB_CACHE.putAll(DP_TACZ_GUN_SHOOT_DB_CACHE);
            } else {
                for (Map.Entry<ResourceLocation, Pair<Double, Double>> e : DP_TACZ_GUN_SHOOT_DB_CACHE.entrySet()) {
                    TACZ_GUN_SHOOT_DB_CACHE.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }

        if (enableDataDriven && !DP_TACZ_ATTACHMENT_REDUCTION_DB_CACHE.isEmpty()) {
            if (datapackOverConfig) {
                TACZ_ATTACHMENT_REDUCTION_DB_CACHE.clear();
                TACZ_ATTACHMENT_REDUCTION_DB_CACHE.putAll(DP_TACZ_ATTACHMENT_REDUCTION_DB_CACHE);
            } else {
                for (Map.Entry<String, Double> e : DP_TACZ_ATTACHMENT_REDUCTION_DB_CACHE.entrySet()) {
                    TACZ_ATTACHMENT_REDUCTION_DB_CACHE.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }

        if (enableDataDriven && !DP_TACZ_MUZZLE_FLASH_REDUCTION_CACHE.isEmpty()) {
            if (datapackOverConfig) {
                TACZ_MUZZLE_FLASH_REDUCTION_CACHE.clear();
                TACZ_MUZZLE_FLASH_REDUCTION_CACHE.putAll(DP_TACZ_MUZZLE_FLASH_REDUCTION_CACHE);
            } else {
                for (Map.Entry<ResourceLocation, Double> e : DP_TACZ_MUZZLE_FLASH_REDUCTION_CACHE.entrySet()) {
                    TACZ_MUZZLE_FLASH_REDUCTION_CACHE.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }

        parseAndCacheCustomArmorColors();

        POINT_BLANK_ENABLED_CACHE = net.minecraftforge.fml.ModList.get().isLoaded("pointblank") && (!serverReady() || SERVER.enablePointBlankIntegration.get());
        POINT_BLANK_RELOAD_RANGE_CACHE = COMMON.pointBlankReloadRange.get();
        POINT_BLANK_RELOAD_WEIGHT_CACHE = COMMON.pointBlankReloadWeight.get();
        POINT_BLANK_SHOOT_RANGE_CACHE = COMMON.pointBlankShootRange.get();
        POINT_BLANK_SHOOT_WEIGHT_CACHE = COMMON.pointBlankShootWeight.get();

        POINT_BLANK_GUN_RANGE_CACHE.clear();
        java.util.List<? extends String> pbGunRanges = COMMON.pointBlankGunShootRanges.get();
        for (String raw : pbGunRanges) {
            try {
                String[] parts = raw.split(";", 2);
                ResourceLocation rl = ResourceLocation.tryParse(parts[0]);
                double range = Double.parseDouble(parts[1]);
                if (rl != null) {
                    POINT_BLANK_GUN_RANGE_CACHE.put(rl, range);
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("Failed to parse Point Blank gun range entry: {}", raw, e);
            }
        }

        POINT_BLANK_ATTACHMENT_REDUCTION_CACHE.clear();
        java.util.List<? extends String> pbAttachRed = COMMON.pointBlankAttachmentSoundReductions.get();
        for (String raw : pbAttachRed) {
            try {
                String[] parts = raw.split(";", 2);
                ResourceLocation rl = ResourceLocation.tryParse(parts[0]);
                double reduction = Double.parseDouble(parts[1]);
                if (rl != null) {
                    POINT_BLANK_ATTACHMENT_REDUCTION_CACHE.put(rl, reduction);
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("Failed to parse Point Blank attachment reduction entry: {}", raw, e);
            }
        }
        POINT_BLANK_ATTACHMENT_REDUCTION_DEFAULT_CACHE = COMMON.pointBlankAttachmentReductionDefault.get();

        POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE.clear();
        java.util.List<? extends String> pbFlashRed = COMMON.pointBlankMuzzleFlashReductions.get();
        for (String raw : pbFlashRed) {
            try {
                String[] parts = raw.split(";", 2);
                ResourceLocation rl = ResourceLocation.tryParse(parts[0]);
                double reduction = Double.parseDouble(parts[1]);
                if (rl != null) {
                    POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE.put(rl, reduction);
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("Failed to parse Point Blank muzzle flash reduction entry: {}", raw, e);
            }
        }

        if (enableDataDriven && !DP_POINT_BLANK_GUN_RANGE_CACHE.isEmpty()) {
            if (datapackOverConfig) {
                POINT_BLANK_GUN_RANGE_CACHE.clear();
                POINT_BLANK_GUN_RANGE_CACHE.putAll(DP_POINT_BLANK_GUN_RANGE_CACHE);
            } else {
                for (Map.Entry<ResourceLocation, Double> e : DP_POINT_BLANK_GUN_RANGE_CACHE.entrySet()) {
                    POINT_BLANK_GUN_RANGE_CACHE.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }

        if (enableDataDriven && !DP_POINT_BLANK_ATTACHMENT_REDUCTION_CACHE.isEmpty()) {
            if (datapackOverConfig) {
                POINT_BLANK_ATTACHMENT_REDUCTION_CACHE.clear();
                POINT_BLANK_ATTACHMENT_REDUCTION_CACHE.putAll(DP_POINT_BLANK_ATTACHMENT_REDUCTION_CACHE);
            } else {
                for (Map.Entry<ResourceLocation, Double> e : DP_POINT_BLANK_ATTACHMENT_REDUCTION_CACHE.entrySet()) {
                    POINT_BLANK_ATTACHMENT_REDUCTION_CACHE.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }

        if (enableDataDriven && !DP_POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE.isEmpty()) {
            if (datapackOverConfig) {
                POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE.clear();
                POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE.putAll(DP_POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE);
            } else {
                for (Map.Entry<ResourceLocation, Double> e : DP_POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE.entrySet()) {
                    POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }

        List<com.example.soundattract.config.MobProfile2> tmpMobProfiles = new ArrayList<>();
        if (COMMON.specialMobProfilesRaw != null) {
            List<String> rawProfiles = new ArrayList<>(COMMON.specialMobProfilesRaw.get());
            for (String profileString : rawProfiles) {
                String[] parts = profileString.split(";", -1);

                if (parts.length < 1 || parts[0].trim().isEmpty()) {
                    SoundAttractMod.LOGGER.warn("Skipping mob profile with empty or missing name: '{}'", profileString);
                    continue;
                }
                String profileName = parts[0].trim();

                if (parts.length < 5) {
                    SoundAttractMod.LOGGER.warn("Skipping malformed mob profile string for '{}' (expected 5 parts, got {}): '{}'", profileName, parts.length, profileString);
                    continue;
                }

                net.minecraft.resources.ResourceLocation mobId = null;
                net.minecraft.nbt.CompoundTag nbtMatcher = null;
                Map<net.minecraft.resources.ResourceLocation, com.example.soundattract.config.SoundOverride> soundOverrides = new HashMap<>();
                Map<PlayerStance, Double> detectionOverrides = new HashMap<>();

                String mobIdString = parts[1].trim();
                net.minecraft.advancements.critereon.EntityPredicate.Builder predicateBuilder = net.minecraft.advancements.critereon.EntityPredicate.Builder.entity();

                if (!mobIdString.isEmpty() && !mobIdString.equals("*")) {
                    mobId = net.minecraft.resources.ResourceLocation.tryParse(mobIdString);
                    if (mobId != null) {
                         net.minecraft.world.entity.EntityType<?> type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(mobId);
                         if (type != null && type != net.minecraft.world.entity.EntityType.PIG && !mobId.equals(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(net.minecraft.world.entity.EntityType.PIG))) {
                             predicateBuilder.of(type);
                         } else if (type == null) {
                            SoundAttractMod.LOGGER.warn("Invalid or missing mob ID '{}' for profile '{}'.", mobIdString, profileName);
                         }
                    } else {
                        SoundAttractMod.LOGGER.warn("Invalid mob ID format '{}' for profile '{}'. Matching will cover all mobs if allowed.", mobIdString, profileName);
                    }
                }

                String nbtMatcherString = parts[2].trim();
                if (!nbtMatcherString.isEmpty()) {
                    try {
                        nbtMatcher = net.minecraft.nbt.TagParser.parseTag(nbtMatcherString);
                        predicateBuilder.nbt(new net.minecraft.advancements.critereon.NbtPredicate(nbtMatcher));
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                        SoundAttractMod.LOGGER.warn("Invalid NBT matcher string '{}' for profile '{}': {}. NBT matching will be skipped for this profile.", nbtMatcherString, profileName, e.getMessage());
                    }
                }

                String soundOverridesString = parts[3].trim();
                if (!soundOverridesString.isEmpty()) {
                    for (String raw : soundOverridesString.split(",")) {
                        raw = raw.trim();
                        if (raw.isEmpty()) {
                            continue;
                        }
                        try {
                            com.example.soundattract.config.SoundOverride so = com.example.soundattract.config.SoundOverride.parse(raw);
                            soundOverrides.put(so.getSoundId(), so);
                        } catch (IllegalArgumentException e) {
                            SoundAttractMod.LOGGER.warn(
                                    "Malformed sound override entry '{}' for profile '{}': {}. Skipping.",
                                    raw, profileName, e.getMessage()
                            );
                        }
                    }
                }

                String detectionOverridesString = parts[4].trim();
                if (!detectionOverridesString.isEmpty()) {
                    for (String detectionOverrideEntry : detectionOverridesString.split(",")) {
                        String[] doParts = detectionOverrideEntry.trim().split(":");
                        if (doParts.length == 2) {
                            try {
                                PlayerStance stance = PlayerStance.valueOf(doParts[0].trim().toUpperCase(java.util.Locale.ROOT));
                                double value = Double.parseDouble(doParts[1].trim());
                                detectionOverrides.put(stance, value);
                            } catch (IllegalArgumentException e) {
                                SoundAttractMod.LOGGER.warn("Invalid player stance or value in detection override '{}' for profile '{}'. Valid stances: STANDING, SNEAKING, CRAWLING. Skipping this override.", detectionOverrideEntry.trim(), profileName, e);
                            }
                        } else {
                            SoundAttractMod.LOGGER.warn("Malformed detection override entry '{}' for profile '{}'. Expected 'stanceName:value'. Skipping this override.", detectionOverrideEntry.trim(), profileName);
                        }
                    }
                }


                com.example.soundattract.config.MobProfile2 profile = new com.example.soundattract.config.MobProfile2(
                    profileName,
                    java.util.Optional.of(predicateBuilder.build()),
                    new ArrayList<>(soundOverrides.values()),
                    detectionOverrides,
                    java.util.Optional.empty()
                );

                tmpMobProfiles.add(profile);
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SOUND_ATTRACTION)) {
                    SoundAttractMod.LOGGER.info("Successfully parsed and cached LEGACY mob profile: {}", profileName);
                }
            }
        }
        boolean enableDataDrivenProfiles = COMMON != null && COMMON.enableDataDriven != null && COMMON.enableDataDriven.get();
        String profilePriority = COMMON != null && COMMON.datapackPriority != null ? COMMON.datapackPriority.get() : "datapack_over_config";
        boolean profilesDpOverConfig = "datapack_over_config".equalsIgnoreCase(profilePriority);

        if (enableDataDrivenProfiles && DP_MOB_PROFILES_CACHE != null && !DP_MOB_PROFILES_CACHE.isEmpty()) {
            List<com.example.soundattract.config.MobProfile2> merged = new ArrayList<>();
            if (profilesDpOverConfig) {
                merged.addAll(DP_MOB_PROFILES_CACHE);
            } else {
                merged.addAll(tmpMobProfiles);
                merged.addAll(DP_MOB_PROFILES_CACHE);
            }
            SPECIAL_MOB_PROFILES_CACHE = List.copyOf(merged);
        } else {
            SPECIAL_MOB_PROFILES_CACHE = List.copyOf(tmpMobProfiles);
        }

        List<com.example.soundattract.config.PlayerProfile2> tmpPlayerProfiles = new ArrayList<>();
        if (COMMON.specialPlayerProfilesRaw != null) {
            List<String> rawPlayerProfiles = new ArrayList<>(COMMON.specialPlayerProfilesRaw.get());
            for (String profileString : rawPlayerProfiles) {
                String[] parts = profileString.split(";", -1);

                if (parts.length < 1 || parts[0].trim().isEmpty()) {
                    SoundAttractMod.LOGGER.warn("Skipping player profile with empty or missing name: '{}'", profileString);
                    continue;
                }
                String profileName = parts[0].trim();

                if (parts.length < 3) {
                    SoundAttractMod.LOGGER.warn("Skipping malformed player profile string for '{}' (expected 3 parts, got {}): '{}'", profileName, parts.length, profileString);
                    continue;
                }

                String nbtMatcherString = parts[1].trim();
                java.util.Map<PlayerStance, Double> detectionOverrides = new java.util.HashMap<>();
                net.minecraft.advancements.critereon.EntityPredicate.Builder predicateBuilder = net.minecraft.advancements.critereon.EntityPredicate.Builder.entity();

                if (!nbtMatcherString.isEmpty()) {
                     try {
                        net.minecraft.nbt.CompoundTag nbtMatcher = net.minecraft.nbt.TagParser.parseTag(nbtMatcherString);
                        predicateBuilder.nbt(new net.minecraft.advancements.critereon.NbtPredicate(nbtMatcher));
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                        SoundAttractMod.LOGGER.warn("Invalid NBT matcher string '{}' for player profile '{}': {}. NBT matching will be skipped for this profile.", nbtMatcherString, profileName, e.getMessage());
                    }
                }

                String detectionOverridesString = parts[2].trim();
                if (!detectionOverridesString.isEmpty()) {
                    for (String detectionOverrideEntry : detectionOverridesString.split(",")) {
                        String[] doParts = detectionOverrideEntry.trim().split(":");
                        if (doParts.length == 2) {
                            try {
                                PlayerStance stance = PlayerStance.valueOf(doParts[0].trim().toUpperCase(java.util.Locale.ROOT));
                                double value = Double.parseDouble(doParts[1].trim());
                                detectionOverrides.put(stance, value);
                            } catch (IllegalArgumentException e) {
                                SoundAttractMod.LOGGER.warn("Invalid player stance or value in detection override '{}' for player profile '{}'. Valid stances: STANDING, SNEAKING, CRAWLING. Skipping this override.", detectionOverrideEntry.trim(), profileName, e);
                            }
                        } else {
                            SoundAttractMod.LOGGER.warn("Malformed detection override entry '{}' for player profile '{}'. Expected 'stanceName:value'. Skipping this override.", detectionOverrideEntry.trim(), profileName);
                        }
                    }
                }

                com.example.soundattract.config.PlayerProfile2 profile = new com.example.soundattract.config.PlayerProfile2(
                    profileName,
                    java.util.Optional.of(predicateBuilder.build()),
                    detectionOverrides,
                    java.util.Optional.empty(),
                    java.util.Optional.of(com.example.soundattract.config.ScentVisibilityConfig.DEFAULT)
                );
                tmpPlayerProfiles.add(profile);
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.STEALTH_DETECTION)) {
                    SoundAttractMod.LOGGER.info("Successfully parsed and cached LEGACY player profile: {}", profileName);
                }
            }
        }
        if (enableDataDrivenProfiles && DP_PLAYER_PROFILES_CACHE != null && !DP_PLAYER_PROFILES_CACHE.isEmpty()) {
            List<com.example.soundattract.config.PlayerProfile2> mergedPlayers = new ArrayList<>();
            if (profilesDpOverConfig) {
                mergedPlayers.addAll(DP_PLAYER_PROFILES_CACHE);
            } else {
                mergedPlayers.addAll(tmpPlayerProfiles);
                mergedPlayers.addAll(DP_PLAYER_PROFILES_CACHE);
            }
            SPECIAL_PLAYER_PROFILES_CACHE = List.copyOf(mergedPlayers);
        } else {
            SPECIAL_PLAYER_PROFILES_CACHE = List.copyOf(tmpPlayerProfiles);
        }

        parseAndCachePlayerActionConfig();
        com.example.soundattract.camo.CamoMaterialRegistry.parseConfig();
        com.example.soundattract.integration.hotbath.HotBathScentRules.rebuildFromConfig();

        com.example.soundattract.event.StealthDetectionEvents.reinitializeCaches();
        com.example.soundattract.event.ScentEvents.reinitializeCaches();
        com.example.soundattract.pathfinding.PathTaskScheduler.reinitializeCaches();

        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.DATAPACK_RELOAD)) {
            SoundAttractMod.LOGGER.info(
                    "SoundAttractConfig: Baked config. {} special mob profiles loaded. {} special player profiles loaded. {} sound defaults. {} whitelist.",
                    SPECIAL_MOB_PROFILES_CACHE.size(),
                    SPECIAL_PLAYER_PROFILES_CACHE.size(),
                    SOUND_DEFAULT_ENTRIES_CACHE.size(),
                    SOUND_ID_WHITELIST_CACHE.size()
            );
        }

        FLEE_FROM_FIRE_ELIGIBLE_SET.clear();
        List<? extends String> fleeMobs = COMMON.fleeFromFireEligibleMobs.get();
        if (fleeMobs != null) {
            for (String raw : fleeMobs) {
                if (raw != null && !raw.trim().isEmpty()) {
                    FLEE_FROM_FIRE_ELIGIBLE_SET.add(raw.trim());
                }
            }
        }
    }

    public static com.example.soundattract.config.MobProfile2 getMatchingProfile(Mob mob) {
        if (SPECIAL_MOB_PROFILES_CACHE == null || SPECIAL_MOB_PROFILES_CACHE.isEmpty()) {
            return null;
        }

        if (mob == null) {
            return null;
        }

        if (QuantifiedCacheCompat.isUsable()) {
            long staggerTicks = Math.abs(mob.getUUID().getLeastSignificantBits() % 20);
            String key = new StringBuilder(96)
                .append(mob.getUUID().toString()).append('|')
                .append(SPECIAL_MOB_PROFILES_CACHE.size())
                .toString();
            return QuantifiedCacheCompat.getCached(
                "soundattract_mob_profile_match",
                key,
                () -> getMatchingProfileUncached(mob),
                1200L + staggerTicks,
                8192L
            );
        }

        return getMatchingProfileUncached(mob);
    }

    private static com.example.soundattract.config.MobProfile2 getMatchingProfileUncached(Mob mob) {
        for (com.example.soundattract.config.MobProfile2 profile : SPECIAL_MOB_PROFILES_CACHE) {
            if (profile.matches(mob)) {
                return profile;
            }
        }
        return null;
    }

    public static com.example.soundattract.config.PlayerProfile2 getMatchingPlayerProfile(Player player) {
        if (SPECIAL_PLAYER_PROFILES_CACHE == null || SPECIAL_PLAYER_PROFILES_CACHE.isEmpty()) {
            return null;
        }

        if (player == null) {
            return null;
        }

        if (QuantifiedCacheCompat.isUsable()) {
            String key = new StringBuilder(96)
                .append(player.getUUID().toString()).append('|')
                .append(SPECIAL_PLAYER_PROFILES_CACHE.size())
                .toString();
            return QuantifiedCacheCompat.getCached(
                "soundattract_player_profile_match",
                key,
                () -> getMatchingPlayerProfileUncached(player),
                1L,
                8192L
            );
        }

        return getMatchingPlayerProfileUncached(player);
    }

    private static com.example.soundattract.config.PlayerProfile2 getMatchingPlayerProfileUncached(Player player) {
        for (com.example.soundattract.config.PlayerProfile2 profile : SPECIAL_PLAYER_PROFILES_CACHE) {
            if (profile.matches(player)) {
                return profile;
            }
        }
        return null;
    }
}
