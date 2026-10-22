package com.example.soundattract.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.example.soundattract.Soundattract;
import com.example.soundattract.config.separate.*;
import fuzs.forgeconfigapiport.fabric.api.forge.v4.ForgeConfigRegistry;
import net.minecraftforge.fml.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ConfigHelper {

    public static void register(ForgeConfigRegistry registry) {

        migrateLegacyConfigFileNames();
        migrateDebugLogging();

        registry.register(Soundattract.MOD_ID, ModConfig.Type.COMMON, GeneralConfig.SPEC, "soundattract/general.toml");
        registry.register(Soundattract.MOD_ID, ModConfig.Type.COMMON, StealthConfig.SPEC, "soundattract/stealth.toml");
        registry.register(Soundattract.MOD_ID, ModConfig.Type.COMMON, GunsConfig.SPEC, "soundattract/guns.toml");
        registry.register(Soundattract.MOD_ID, ModConfig.Type.COMMON, VoiceConfig.SPEC, "soundattract/voice.toml");
        registry.register(Soundattract.MOD_ID, ModConfig.Type.COMMON, IntegrationConfig.SPEC, "soundattract/integration.toml");
        registry.register(Soundattract.MOD_ID, ModConfig.Type.COMMON, com.example.soundattract.config.separate.ScentConfig.SPEC, "soundattract/scent.toml");
        registry.register(Soundattract.MOD_ID, ModConfig.Type.COMMON, PerformanceConfig.SPEC, "soundattract/performance.toml");
        registry.register(Soundattract.MOD_ID, ModConfig.Type.COMMON, RaidConfig.SPEC, "soundattract/raid.toml");
        registry.register(Soundattract.MOD_ID, ModConfig.Type.COMMON, PathfindingConfig.SPEC, "soundattract/pathfinding.toml");
        registry.register(Soundattract.MOD_ID, ModConfig.Type.COMMON, LivingWallConfig.SPEC, "soundattract/living_wall.toml");
        registry.register(Soundattract.MOD_ID, ModConfig.Type.COMMON, DebugLoggingConfig.SPEC, "soundattract/debug_logging.toml");
        registry.register(Soundattract.MOD_ID, ModConfig.Type.SERVER, SoundAttractConfig.SERVER_SPEC, "soundattract/server-rules.toml");
        registry.register(Soundattract.MOD_ID, ModConfig.Type.CLIENT, ClientConfig.SPEC, "soundattract/awareness.toml");


        Path oldConfigPath = FabricLoader.getInstance().getConfigDir().resolve(Soundattract.MOD_ID + "-common.toml");
        if (Files.exists(oldConfigPath)) {
            migrateOldConfig(oldConfigPath);
        }
    }

    private static void migrateLegacyConfigFileNames() {
        Path configDirectory = FabricLoader.getInstance().getConfigDir().resolve(Soundattract.MOD_ID);
        migrateConfigFileName(configDirectory.resolve("living-wall.toml"), configDirectory.resolve("living_wall.toml"));
        migrateConfigFileName(configDirectory.resolve("debug-logging.toml"), configDirectory.resolve("debug_logging.toml"));
    }

    private static void migrateConfigFileName(Path oldPath, Path newPath) {
        if (!Files.isRegularFile(oldPath) || Files.exists(newPath)) {
            return;
        }
        try {
            Files.createDirectories(newPath.getParent());
            Files.move(oldPath, newPath);
            Soundattract.LOGGER.info("Migrated config file {} to {}", oldPath.getFileName(), newPath.getFileName());
        } catch (IOException e) {
            Soundattract.LOGGER.error("Failed to migrate config file {} to {}", oldPath, newPath, e);
        }
    }

    private static void migrateDebugLogging() {
        Path configDirectory = FabricLoader.getInstance().getConfigDir().resolve("soundattract");
        Path generalPath = configDirectory.resolve("general.toml");
        Path debugPath = configDirectory.resolve("debug_logging.toml");
        Path legacyPath = FabricLoader.getInstance().getConfigDir().resolve(Soundattract.MOD_ID + "-common.toml");
        try {
            Boolean legacyValue = readLegacyDebugLogging(generalPath);
            if (!Files.exists(debugPath) && legacyValue == null) {
                legacyValue = readLegacyDebugLogging(legacyPath);
            }
            if (!Files.exists(debugPath) && legacyValue != null) {
                Files.createDirectories(configDirectory);
                try (CommentedFileConfig newConfig = CommentedFileConfig.builder(debugPath)
                        .sync().writingMode(WritingMode.REPLACE).build()) {
                    newConfig.load();
                    DebugLoggingConfig.SPEC.correct(newConfig);
                    newConfig.save();
                }
                Soundattract.LOGGER.info("Migrated general.debugLogging to feature switches in debug_logging.toml; all switches remain disabled");
            }
            if (Files.exists(debugPath)) {
                removeLegacyAllSwitch(debugPath);
                removeLegacyDebugLogging(generalPath);
            }
        } catch (IOException | RuntimeException e) {
            Soundattract.LOGGER.error("Failed to migrate general.debugLogging to debug_logging.toml", e);
        }
    }

    private static void removeLegacyAllSwitch(Path path) {
        try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().build()) {
            config.load();
            if (config.contains("debug_logging.all")) {
                config.remove("debug_logging.all");
                DebugLoggingConfig.SPEC.correct(config);
                config.save();
            }
        }
    }

    private static Boolean readLegacyDebugLogging(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().build()) {
            config.load();
            Object value = config.getRaw("general.debugLogging");
            return value instanceof Boolean enabled ? enabled : null;
        }
    }

    private static void removeLegacyDebugLogging(Path path) {
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().build()) {
            config.load();
            if (config.contains("general.debugLogging")) {
                config.remove("general.debugLogging");
                config.save();
            }
        }
    }

    private static void migrateOldConfig(Path oldPath) {
        Soundattract.LOGGER.info("Old configuration file found at {}. Starting migration...", oldPath);

        CommentedFileConfig oldConfig = CommentedFileConfig.builder(oldPath)
                .sync()
                .build();
        oldConfig.load();


        migrateToSpec(oldConfig, GeneralConfig.SPEC, FabricLoader.getInstance().getConfigDir().resolve("soundattract/general.toml"), List.of(
                new Mapping("general.enableDataDriven", "general.enableDataDriven"),
                new Mapping("general.datapackPriority", "general.datapackPriority"),
                new Mapping("mobs.attractedEntities", "mobs.attractedEntities"),
                new Mapping("mobs.mobBlacklist", "mobs.mobBlacklist"),
                new Mapping("mobs.stealthBypassMobIds", "mobs.stealthBypassMobIds"),
                new Mapping("mobs.stealthBypassModNamespaces", "mobs.stealthBypassModNamespaces"),
                new Mapping("mobs.soundLifetimeTicks", "mobs.soundLifetimeTicks"),
                new Mapping("mobs.arrivalDistance", "mobs.arrivalDistance"),
                new Mapping("mobs.mobMoveSpeed", "mobs.mobMoveSpeed"),
                new Mapping("Sounds White List.soundIdWhitelist", "sounds.soundIdWhitelist"),
                new Mapping("Sounds White List.soundIdBlacklist", "sounds.soundIdBlacklist"),
                new Mapping("sound_defaults.soundDefaults", "sounds.soundDefaults"),
                new Mapping("groups.maxGroupSize", "groups.maxGroupSize"),
                new Mapping("groups.maxLeaderGroupRadius", "groups.leaderGroupRadius"),
                new Mapping("groups.groupDistance", "groups.groupDistance"),
                new Mapping("groups.leaderSpacingMultiplier", "groups.leaderSpacingMultiplier"),
                new Mapping("groups.numEdgeSectors", "groups.numEdgeSectors"),
                new Mapping("groups.groupUpdateInterval", "groups.groupUpdateInterval"),
                new Mapping("groups.maxLeaders", "groups.maxLeaders"),

                new Mapping("muffling.enableBlockMuffling", "muffling.enableBlockMuffling"),
                new Mapping("muffling.mufflingFactorWool", "muffling.mufflingFactorWool"),
                new Mapping("muffling.mufflingFactorSolid", "muffling.mufflingFactorSolid"),
                new Mapping("muffling.mufflingFactorNonSolid", "muffling.mufflingFactorNonSolid"),
                new Mapping("muffling.mufflingFactorThin", "muffling.mufflingFactorThin"),
                new Mapping("muffling.mufflingFactorLiquid", "muffling.mufflingFactorLiquid"),
                new Mapping("muffling.mufflingFactorAir", "muffling.mufflingFactorAir"),
                new Mapping("muffling.customWoolBlocks", "muffling.customWoolBlocks"),
                new Mapping("muffling.customSolidBlocks", "muffling.customSolidBlocks"),
                new Mapping("muffling.customNonSolidBlocks", "muffling.customNonSolidBlocks"),
                new Mapping("muffling.customThinBlocks", "muffling.customThinBlocks"),
                new Mapping("muffling.customLiquidBlocks", "muffling.customLiquidBlocks"),
                new Mapping("muffling.customAirBlocks", "muffling.customAirBlocks"),
                new Mapping("legacy_profiles.specialMobProfilesRaw", "legacy_profiles.specialMobProfilesRaw"),
                new Mapping("legacy_profiles.specialPlayerProfilesRaw", "legacy_profiles.specialPlayerProfilesRaw")
        ));


        migrateToSpec(oldConfig, StealthConfig.SPEC, FabricLoader.getInstance().getConfigDir().resolve("soundattract/stealth.toml"), List.of(
                new Mapping("sound_attract_stealth.fov.defaultHorizontalFov", "sound_attract_stealth.fov.defaultHorizontalFov"),
                new Mapping("sound_attract_stealth.fov.defaultVerticalFov", "sound_attract_stealth.fov.defaultVerticalFov"),
                new Mapping("sound_attract_stealth.fov.customFovOverrides", "sound_attract_stealth.fov.customFovOverrides"),
                new Mapping("sound_attract_stealth.fov.fovExclusionList", "sound_attract_stealth.fov.fovExclusionList"),
                new Mapping("sound_attract_stealth.fov.nonBlockingVisionAllowList", "sound_attract_stealth.fov.nonBlockingVisionAllowList"),
                new Mapping("sound_attract_stealth.general_stealth_settings.enableStealthMechanics", "sound_attract_stealth.general_stealth_settings.enableStealthMechanics"),
                new Mapping("sound_attract_stealth.general_stealth_settings.stealthCheckInterval", "sound_attract_stealth.general_stealth_settings.stealthCheckInterval"),
                new Mapping("sound_attract_stealth.general_stealth_settings.stealthGracePeriodTicks", "sound_attract_stealth.general_stealth_settings.stealthGracePeriodTicks"),
                new Mapping("sound_attract_stealth.player_stance_detection_ranges.standingDetectionRangePlayer", "sound_attract_stealth.player_stance_detection_ranges.standingDetectionRangePlayer"),
                new Mapping("sound_attract_stealth.player_stance_detection_ranges.sneakingDetectionRangePlayer", "sound_attract_stealth.player_stance_detection_ranges.sneakingDetectionRangePlayer"),
                new Mapping("sound_attract_stealth.player_stance_detection_ranges.crawlingDetectionRangePlayer", "sound_attract_stealth.player_stance_detection_ranges.crawlingDetectionRangePlayer"),
                new Mapping("sound_attract_stealth.environmental_factors.light_level.neutralLightLevel", "sound_attract_stealth.environmental_factors.light_level.neutralLightLevel"),
                new Mapping("sound_attract_stealth.environmental_factors.light_level.lightLevelSensitivity", "sound_attract_stealth.environmental_factors.light_level.lightLevelSensitivity"),
                new Mapping("sound_attract_stealth.environmental_factors.light_level.minLightFactor", "sound_attract_stealth.environmental_factors.light_level.minLightFactor"),
                new Mapping("sound_attract_stealth.environmental_factors.light_level.maxLightFactor", "sound_attract_stealth.environmental_factors.light_level.maxLightFactor"),
                new Mapping("sound_attract_stealth.environmental_factors.light_level.lightSampleRadiusHorizontal", "sound_attract_stealth.environmental_factors.light_level.lightSampleRadiusHorizontal"),
                new Mapping("sound_attract_stealth.environmental_factors.light_level.lightSampleRadiusVertical", "sound_attract_stealth.environmental_factors.light_level.lightSampleRadiusVertical"),
                new Mapping("sound_attract_stealth.environmental_factors.weather.rainStealthFactor", "sound_attract_stealth.environmental_factors.weather.rainStealthFactor"),
                new Mapping("sound_attract_stealth.environmental_factors.weather.thunderStealthFactor", "sound_attract_stealth.environmental_factors.weather.thunderStealthFactor"),
                new Mapping("sound_attract_stealth.player_actions.movement.movementStealthPenalty", "sound_attract_stealth.player_actions.movement.movementStealthPenalty"),
                new Mapping("sound_attract_stealth.player_actions.movement.stationaryStealthBonusFactor", "sound_attract_stealth.player_actions.movement.stationaryStealthBonusFactor"),
                new Mapping("sound_attract_stealth.player_actions.movement.movementThreshold", "sound_attract_stealth.player_actions.movement.movementThreshold"),
                new Mapping("sound_attract_stealth.player_actions.invisibility.invisibilityStealthFactor", "sound_attract_stealth.player_actions.invisibility.invisibilityStealthFactor"),
                new Mapping("sound_attract_stealth.camouflage_system.enableCamouflage", "sound_attract_stealth.camouflage_system.enableCamouflage"),
                new Mapping("sound_attract_stealth.camouflage_system.enableHeldItemPenalty", "sound_attract_stealth.camouflage_system.enableHeldItemPenalty"),
                new Mapping("sound_attract_stealth.camouflage_system.heldItemPenaltyFactor", "sound_attract_stealth.camouflage_system.heldItemPenaltyFactor"),
                new Mapping("sound_attract_stealth.camouflage_system.enableEnchantmentPenalty", "sound_attract_stealth.camouflage_system.enableEnchantmentPenalty"),
                new Mapping("sound_attract_stealth.camouflage_system.armorEnchantmentPenaltyFactor", "sound_attract_stealth.camouflage_system.armorEnchantmentPenaltyFactor"),
                new Mapping("sound_attract_stealth.camouflage_system.heldItemEnchantmentPenaltyFactor", "sound_attract_stealth.camouflage_system.heldItemEnchantmentPenaltyFactor"),
                new Mapping("sound_attract_stealth.camouflage_system.item_camouflage.camouflageArmorItems", "sound_attract_stealth.camouflage_system.item_camouflage.camouflageArmorItems"),
                new Mapping("sound_attract_stealth.camouflage_system.item_camouflage.requireFullSetForCamouflageBonus", "sound_attract_stealth.camouflage_system.item_camouflage.requireFullSetForCamouflageBonus"),
                new Mapping("sound_attract_stealth.camouflage_system.item_camouflage.fullArmorStealthBonus", "sound_attract_stealth.camouflage_system.item_camouflage.fullArmorStealthBonus"),
                new Mapping("sound_attract_stealth.camouflage_system.item_camouflage.helmetCamouflageEffectiveness", "sound_attract_stealth.camouflage_system.item_camouflage.helmetCamouflageEffectiveness"),
                new Mapping("sound_attract_stealth.camouflage_system.item_camouflage.chestplateCamouflageEffectiveness", "sound_attract_stealth.camouflage_system.item_camouflage.chestplateCamouflageEffectiveness"),
                new Mapping("sound_attract_stealth.camouflage_system.item_camouflage.leggingsCamouflageEffectiveness", "sound_attract_stealth.camouflage_system.item_camouflage.leggingsCamouflageEffectiveness"),
                new Mapping("sound_attract_stealth.camouflage_system.item_camouflage.bootsCamouflageEffectiveness", "sound_attract_stealth.camouflage_system.item_camouflage.bootsCamouflageEffectiveness"),
                new Mapping("sound_attract_stealth.camouflage_system.item_camouflage.maxCamouflageEffectivenessCap", "sound_attract_stealth.camouflage_system.item_camouflage.maxCamouflageEffectivenessCap"),
                new Mapping("sound_attract_stealth.camouflage_system.item_camouflage.allowPartialBonusIfFullSetRequired", "sound_attract_stealth.camouflage_system.item_camouflage.allowPartialBonusIfFullSetRequired"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.enableEnvironmentalCamouflage", "sound_attract_stealth.camouflage_system.environmental_camouflage.enableEnvironmentalCamouflage"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.enableEnvironmentalMismatchPenalty", "sound_attract_stealth.camouflage_system.environmental_camouflage.enableEnvironmentalMismatchPenalty"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.environmentalCamouflageMaxEffectiveness", "sound_attract_stealth.camouflage_system.environmental_camouflage.environmentalCamouflageMaxEffectiveness"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.environmentalCamouflageColorMatchThreshold", "sound_attract_stealth.camouflage_system.environmental_camouflage.environmentalCamouflageColorMatchThreshold"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.environmentalMismatchPenaltyFactor", "sound_attract_stealth.camouflage_system.environmental_camouflage.environmentalMismatchPenaltyFactor"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.environmentalMismatchThreshold", "sound_attract_stealth.camouflage_system.environmental_camouflage.environmentalMismatchThreshold"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.environmentalCamouflageOnlyDyedLeather", "sound_attract_stealth.camouflage_system.environmental_camouflage.environmentalCamouflageOnlyDyedLeather"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.customArmorColors", "sound_attract_stealth.camouflage_system.environmental_camouflage.customArmorColors"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.envColorSampleRadius", "sound_attract_stealth.camouflage_system.environmental_camouflage.envColorSampleRadius"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.envColorSampleYOffsetStart", "sound_attract_stealth.camouflage_system.environmental_camouflage.envColorSampleYOffsetStart"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.envColorSampleYOffsetEnd", "sound_attract_stealth.camouflage_system.environmental_camouflage.envColorSampleYOffsetEnd"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.environmentalCamouflageSamplingMode", "sound_attract_stealth.camouflage_system.environmental_camouflage.environmentalCamouflageSamplingMode"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.envBackdropMaxDistance", "sound_attract_stealth.camouflage_system.environmental_camouflage.envBackdropMaxDistance"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.envBackdropSampleRays", "sound_attract_stealth.camouflage_system.environmental_camouflage.envBackdropSampleRays"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.envBackdropMinSamples", "sound_attract_stealth.camouflage_system.environmental_camouflage.envBackdropMinSamples"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.envBackdropViewerCellSize", "sound_attract_stealth.camouflage_system.environmental_camouflage.envBackdropViewerCellSize"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.envBackdropCacheTtlTicks", "sound_attract_stealth.camouflage_system.environmental_camouflage.envBackdropCacheTtlTicks"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.envBackdropMinTps", "sound_attract_stealth.camouflage_system.environmental_camouflage.envBackdropMinTps"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.envBackdropRecoveryTps", "sound_attract_stealth.camouflage_system.environmental_camouflage.envBackdropRecoveryTps"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.envBackdropUseSkyColor", "sound_attract_stealth.camouflage_system.environmental_camouflage.envBackdropUseSkyColor"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.envBackdropDaySkyColor", "sound_attract_stealth.camouflage_system.environmental_camouflage.envBackdropDaySkyColor"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.envBackdropNightSkyColor", "sound_attract_stealth.camouflage_system.environmental_camouflage.envBackdropNightSkyColor"),
                new Mapping("sound_attract_stealth.camouflage_system.environmental_camouflage.envBackdropRainSkyColor", "sound_attract_stealth.camouflage_system.environmental_camouflage.envBackdropRainSkyColor"),
                new Mapping("sound_attract_stealth.detection_range_limits.minStealthDetectionRange", "sound_attract_stealth.detection_range_limits.minStealthDetectionRange"),
                new Mapping("sound_attract_stealth.detection_range_limits.maxStealthDetectionRange", "sound_attract_stealth.detection_range_limits.maxStealthDetectionRange")
        ));


        migrateToSpec(oldConfig, GunsConfig.SPEC, FabricLoader.getInstance().getConfigDir().resolve("soundattract/guns.toml"), List.of(
                new Mapping("guns.tacz.enableTaczIntegration", "guns.tacz.enableTaczIntegration"),
                new Mapping("guns.tacz.taczReloadRange", "guns.tacz.taczReloadRange"),
                new Mapping("guns.tacz.taczReloadWeight", "guns.tacz.taczReloadWeight"),
                new Mapping("guns.tacz.taczShootRange", "guns.tacz.taczShootRange"),
                new Mapping("guns.tacz.taczShootWeight", "guns.tacz.taczShootWeight"),
                new Mapping("guns.tacz.taczGunShootDecibels", "guns.tacz.taczGunShootDecibels"),
                new Mapping("guns.tacz.taczAttachmentReductions", "guns.tacz.taczAttachmentReductions"),
                new Mapping("guns.tacz.taczAttachmentReductionDefault", "guns.tacz.taczAttachmentReductionDefault"),
                new Mapping("guns.tacz.gunshotBaseDetectionRange", "guns.tacz.gunshotBaseDetectionRange"),
                new Mapping("guns.tacz.gunshotDetectionDurationTicks", "guns.tacz.gunshotDetectionDurationTicks"),
                new Mapping("guns.tacz.taczMuzzleFlashReductions", "guns.tacz.taczMuzzleFlashReductions"),
                new Mapping("guns.tacz.taczAttachmentFlashReductionDefault", "guns.tacz.taczAttachmentFlashReductionDefault"),
                new Mapping("guns.pointblank.enablePointBlankIntegration", "guns.pointblank.enablePointBlankIntegration"),
                new Mapping("guns.pointblank.pointBlankReloadRange", "guns.pointblank.pointBlankReloadRange"),
                new Mapping("guns.pointblank.pointBlankReloadWeight", "guns.pointblank.pointBlankReloadWeight"),
                new Mapping("guns.pointblank.pointBlankShootRange", "guns.pointblank.pointBlankShootRange"),
                new Mapping("guns.pointblank.pointBlankShootWeight", "guns.pointblank.pointBlankShootWeight"),
                new Mapping("guns.pointblank.pointBlankGunShootRanges", "guns.pointblank.pointBlankGunShootRanges"),
                new Mapping("guns.pointblank.pointBlankAttachmentSoundReductions", "guns.pointblank.pointBlankAttachmentSoundReductions"),
                new Mapping("guns.pointblank.pointBlankAttachmentReductionDefault", "guns.pointblank.pointBlankAttachmentReductionDefault"),
                new Mapping("guns.pointblank.pointBlankMuzzleFlashReductions", "guns.pointblank.pointBlankMuzzleFlashReductions")
        ));


        migrateToSpec(oldConfig, VoiceConfig.SPEC, FabricLoader.getInstance().getConfigDir().resolve("soundattract/voice.toml"), List.of(
                new Mapping("voice_chat.Simple VC.enableVoiceChatIntegration", "voice_chat.Simple VC.enableVoiceChatIntegration"),
                new Mapping("voice_chat.Simple VC.voiceChatWhisperRange", "voice_chat.Simple VC.voiceChatWhisperRange"),
                new Mapping("voice_chat.Simple VC.voiceChatNormalRange", "voice_chat.Simple VC.voiceChatNormalRange"),
                new Mapping("voice_chat.Simple VC.voiceChatWeight", "voice_chat.Simple VC.voiceChatWeight"),
                new Mapping("voice_chat.Simple VC.voiceChatDbThresholdMap", "voice_chat.Simple VC.voiceChatDbThresholdMap")
        ));

        migrateToSpec(oldConfig, IntegrationConfig.SPEC, FabricLoader.getInstance().getConfigDir().resolve("soundattract/integration.toml"), List.of(
                new Mapping("integration.enhanced_ai.enableBlockBreaking", "integration.enhanced_ai_inspired.enableBlockBreaking"),
                new Mapping("integration.enhanced_ai.allowTeleportInvestigation", "integration.enhanced_ai_inspired.enableTeleportToSound"),
                new Mapping("integration.relentless_undead.preventRelentlessUndeadTargeting", "integration.relentless_undead_inspired.enableRelentlessClimbing"),
                new Mapping("integration.custom_npcs.enableCustomNpcsSupport", "integration.enableCustomNpcsIntegration"),
                new Mapping("integration.quantified.enableQuantifiedIntegration", "integration.enableQuantifiedIntegration"),
                new Mapping("integration.smartbrainlib.enableSblIntegration", "integration.enableSmartBrainLibIntegration"),                
                new Mapping("mob_ai.pick_up_and_throw.enablePickUpAndThrowToSound", "integration.enhanced_ai_inspired.enablePickUpAndThrowToSound"),
                new Mapping("mob_ai.pick_up_and_throw.pickUpChance", "integration.enhanced_ai_inspired.pickUpChance"),
                new Mapping("mob_ai.pick_up_and_throw.pickUpCooldownTicks", "integration.enhanced_ai_inspired.pickUpCooldownTicks"),
                new Mapping("mob_ai.pick_up_and_throw.pickUpMinDistanceToPickUp", "integration.enhanced_ai_inspired.pickUpMinDistanceToPickUp"),
                new Mapping("mob_ai.pick_up_and_throw.pickUpMaxDistanceToThrow", "integration.enhanced_ai_inspired.pickUpMaxDistanceToThrow"),
                new Mapping("mob_ai.pick_up_and_throw.pickUpSpeedModifier", "integration.enhanced_ai_inspired.pickUpSpeedModifier"),
                new Mapping("mob_ai.pick_up_and_throw.pickUpCanPickUpTag", "integration.enhanced_ai_inspired.pickUpCanPickUpTag"),
                new Mapping("mob_ai.pick_up_and_throw.pickUpCanBePickedUpTag", "integration.enhanced_ai_inspired.pickUpCanBePickedUpTag"),
                new Mapping("mob_ai.xray_targeting.enableXrayTargeting", "integration.enhanced_ai_inspired.enableXrayTargeting"),
                new Mapping("mob_ai.xray_targeting.xrayApplyTag", "integration.enhanced_ai_inspired.xrayApplyTag"),
                new Mapping("mob_ai.xray_targeting.xrayRequireBetterNearby", "integration.enhanced_ai_inspired.xrayRequireBetterNearby"),
                new Mapping("mob_ai.xray_targeting.xrayBetterNearbyTag", "integration.enhanced_ai_inspired.xrayBetterNearbyTag"),
                new Mapping("mob_ai.xray_targeting.xrayMinRange", "integration.enhanced_ai_inspired.xrayMinRange"),
                new Mapping("mob_ai.xray_targeting.xrayMaxRange", "integration.enhanced_ai_inspired.xrayMaxRange"),
                new Mapping("mob_ai.xray_targeting.xrayChance", "integration.enhanced_ai_inspired.xrayChance")
        ));


        migrateToSpec(oldConfig, com.example.soundattract.config.separate.ScentConfig.SPEC, FabricLoader.getInstance().getConfigDir().resolve("soundattract/scent.toml"), List.of(
                new Mapping("scent_system.enableScentSystem", "scent_system.enableScentSystem"),
                new Mapping("scent_system.scentLifetimeTicks", "scent_system.scentLifetimeTicks"),
                new Mapping("scent_system.scentDetectionRadius", "scent_system.scentDetectionRadius"),
                new Mapping("scent_system.rainWashesScents", "scent_system.rainWashesScents"),
                new Mapping("scent_system.scentUpdateInterval", "scent_system.scentUpdateInterval"),
                new Mapping("scent_system.scentStrengthDecay", "scent_system.scentStrengthDecay")
        ));
        

        migrateToSpec(oldConfig, PerformanceConfig.SPEC, FabricLoader.getInstance().getConfigDir().resolve("soundattract/performance.toml"), List.of(
                new Mapping("performance.enableOptimizedLos", "performance.enableOptimizedLos"),
                new Mapping("performance.enableOptimizedLosPairCache", "performance.enableOptimizedLosPairCache"),
                new Mapping("performance.optimizedLosPairCacheMaxEntries", "performance.optimizedLosPairCacheMaxEntries"),
                new Mapping("performance.enableOptimizedLosVanillaFallback", "performance.enableOptimizedLosVanillaFallback"),
                new Mapping("performance.enableLosBatching", "performance.enableLosBatching"),
                new Mapping("performance.losBatchBudgetPerTick", "performance.losBatchBudgetPerTick"),
                new Mapping("performance.losBatchQueueMaxSize", "performance.losBatchQueueMaxSize"),
                new Mapping("performance.enableLivingEntityLosOverride", "performance.enableLivingEntityLosOverride"),
                new Mapping("muffling.maxMufflingBlocksToCheck", "performance.maxMufflingBlocksToCheck")
        ));

        oldConfig.close();


        try {
            Files.move(oldPath, oldPath.resolveSibling(oldPath.getFileName().toString() + ".migrated"));
            Soundattract.LOGGER.info("Successfully migrated old configuration and renamed it to .migrated");
        } catch (IOException e) {
            Soundattract.LOGGER.error("Failed to rename old configuration file after migration!", e);
        }
    }

    private static void migrateToSpec(CommentedFileConfig oldConfig, net.minecraftforge.common.ForgeConfigSpec newSpec, Path newPath, List<Mapping> mappings) {

        try {
            Files.createDirectories(newPath.getParent());
        } catch (IOException e) {
            Soundattract.LOGGER.error("Failed to create config directory for migration: {}", newPath.getParent(), e);
            return;
        }

        CommentedFileConfig newConfig = CommentedFileConfig.builder(newPath)
                .sync()
                .writingMode(WritingMode.REPLACE)
                .build();
        newConfig.load();

        int migratedCount = 0;
        for (Mapping mapping : mappings) {
            if (oldConfig.contains(mapping.oldPath)) {
                Object value = oldConfig.get(mapping.oldPath);
                newConfig.set(mapping.newPath, value);
                migratedCount++;
            }
        }

        if (migratedCount > 0) {
            newSpec.correct(newConfig);
            newConfig.save();
            Soundattract.LOGGER.info("Migrated {} values to {}", migratedCount, newPath.getFileName());
        }
        newConfig.close();
    }

    private static class Mapping {
        final String oldPath;
        final String newPath;

        Mapping(String oldPath, String newPath) {
            this.oldPath = oldPath;
            this.newPath = newPath;
        }
    }
}
