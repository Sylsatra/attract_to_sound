package com.example.soundattract.event;

import com.example.soundattract.integration.csgrenades.CsGrenadesCompat;
import com.example.soundattract.integration.csgrenades.FlashbangEffect;

import net.minecraft.core.registries.BuiltInRegistries;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.DebugLogLimiter;
import com.example.soundattract.config.DebugLoggingConfig;
import com.example.soundattract.config.PlayerStance;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.util.CamoUtil;
import com.example.soundattract.enchantment.ModEnchantments;
import com.example.soundattract.ai.MobGroupManager;
import com.example.soundattract.ai.GroupTargetSharingManager;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.component.DataComponents;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import com.example.soundattract.integration.enhancedai.EnhancedAICompat;
import com.example.soundattract.quantified.QuantifiedCacheCompat;
import com.example.soundattract.pathfinding.PathTaskScheduler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.Vec3;
import com.example.soundattract.los.OptimizedLOS; 
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import com.example.soundattract.camo.CamoAttachments;
import com.example.soundattract.camo.CamouflageCapability;
import com.example.soundattract.integration.spore.SporeIntegration;
import com.example.soundattract.integration.spore.SporeGoalInjectorProxy;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;

@EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class StealthDetectionEvents {

    private static final int MAX_RUNTIME_GLOBAL_CACHE_SIZE = 65536;
    private static final Cache<String, Boolean> FRIENDLY_FIRE_LOG_CACHE = Caffeine.newBuilder()
            .maximumSize(4096)
            .expireAfterWrite(2, TimeUnit.SECONDS)
            .build();

    private static volatile Map<UUID, Integer> mobOutOfRangeTicks = newInitialCache();
    private static volatile Map<UUID, net.minecraft.world.phys.Vec3> lastPlayerPositions = newInitialCache();
    private static volatile Map<java.util.UUID, net.minecraft.world.phys.Vec3> lastMobPositions = newInitialCache();
    private static long lastStealthCheckTick = -1;
    private static final int MAX_PENDING_ROUTE_PROBES = 1024;
    private static final int MAX_ROUTE_PROBES_PER_TICK = 4;
    private static final long ROUTE_PROBE_TTL_TICKS = 10L;
    private static final Map<RouteProbeKey, RouteProbe> pendingRouteProbes = new LinkedHashMap<>();
    private static final Map<RouteProbeKey, RouteEvidence> routeEvidence = new LinkedHashMap<>();
    private static volatile Map<UUID, GunshotInfo> playerGunshotInfo = newInitialCache();

    private static volatile Map<UUID, Double> XRAY_RANGE_CACHE = newInitialCache();
    private static volatile Map<UUID, PlayerStealthBaseline> PLAYER_STEALTH_CACHE = newInitialCache();

    public record PlayerStealthBaseline(
            ResourceKey<Level> dimension,
            long gameTime,
            PlayerStance stance,
            Optional<Double> gunshotRangeOpt,
            double defaultBaseRange,
            double combinedMultiplier,
            int finalColor
    ) {}

    private static <K, V> Map<K, V> newInitialCache() {
        return Caffeine.newBuilder()
                .maximumSize(8192)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .<K, V>build()
                .asMap();
    }

    public static void reinitializeCaches() {
        GroupTargetSharingManager.invalidatePolicy();
        int max = Math.min(MAX_RUNTIME_GLOBAL_CACHE_SIZE,
                Math.max(100, SoundAttractConfig.COMMON.globalCacheMaxSize.get()));
        int mins = SoundAttractConfig.COMMON.globalCacheExpireMins.get();

        Map<UUID, Integer> oldObj1 = mobOutOfRangeTicks;
        mobOutOfRangeTicks = Caffeine.newBuilder().expireAfterWrite(mins, TimeUnit.MINUTES).maximumSize(max).<UUID, Integer>build().asMap();
        mobOutOfRangeTicks.putAll(oldObj1);

        Map<UUID, net.minecraft.world.phys.Vec3> oldObj2 = lastPlayerPositions;
        lastPlayerPositions = Caffeine.newBuilder().expireAfterWrite(mins, TimeUnit.MINUTES).maximumSize(max).<UUID, net.minecraft.world.phys.Vec3>build().asMap();
        lastPlayerPositions.putAll(oldObj2);

        Map<java.util.UUID, net.minecraft.world.phys.Vec3> oldObj3 = lastMobPositions;
        lastMobPositions = Caffeine.newBuilder().expireAfterWrite(mins, TimeUnit.MINUTES).maximumSize(max).<java.util.UUID, net.minecraft.world.phys.Vec3>build().asMap();
        lastMobPositions.putAll(oldObj3);

        Map<UUID, GunshotInfo> oldObj4 = playerGunshotInfo;
        playerGunshotInfo = Caffeine.newBuilder().expireAfterWrite(mins, TimeUnit.MINUTES).maximumSize(max).<UUID, GunshotInfo>build().asMap();
        playerGunshotInfo.putAll(oldObj4);

        Map<UUID, Double> oldObj5 = XRAY_RANGE_CACHE;
        XRAY_RANGE_CACHE = Caffeine.newBuilder().expireAfterWrite(mins, TimeUnit.MINUTES).maximumSize(max).<UUID, Double>build().asMap();
        XRAY_RANGE_CACHE.putAll(oldObj5);

        PLAYER_STEALTH_CACHE = Caffeine.newBuilder().expireAfterWrite(mins, TimeUnit.MINUTES).maximumSize(max).<UUID, PlayerStealthBaseline>build().asMap();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        mobOutOfRangeTicks.clear();
        lastPlayerPositions.clear();
        lastMobPositions.clear();
        playerGunshotInfo.clear();
        XRAY_RANGE_CACHE.clear();
        PLAYER_STEALTH_CACHE.clear();
        FRIENDLY_FIRE_LOG_CACHE.invalidateAll();
        suppressedEdgeDetections.invalidateAll();
        pendingRouteProbes.clear();
        routeEvidence.clear();
        lastStealthCheckTick = -1L;
        lastEstimatedTpsGameTime = -1L;
    }

    private static final Cache<UUID, Boolean> suppressedEdgeDetections = Caffeine.newBuilder()
            .maximumSize(MAX_RUNTIME_GLOBAL_CACHE_SIZE)
            .build();

    public enum StealthPerfTier {
        FULL,
        SKIP_EXPENSIVE,
        CURRENT_TARGETS_ONLY,
        SHARE_NEARBY,
        VANILLA
    }

    private static volatile double lastEstimatedTps = 20.0;
    private static volatile long lastEstimatedTpsGameTime = -1L;

    public static double getLastEstimatedTps() {
        return lastEstimatedTps;
    }

    private static void updateEstimatedTps(MinecraftServer server) {
        if (server == null) return;
        try {
            double avgMs = server.getAverageTickTimeNanos() / 1_000_000.0D;
            if (!Double.isFinite(avgMs) || avgMs <= 0.0D) {
                return;
            }
            double tps = 1000.0 / Math.max(1.0, avgMs);
            if (tps > 20.0) tps = 20.0;
            if (tps < 0.0) tps = 0.0;
            lastEstimatedTps = tps;
        } catch (Throwable ignored) {
        }
    }

    public static StealthPerfTier getPerfTier(MinecraftServer server) {
        if (SoundAttractConfig.COMMON == null) {
            return StealthPerfTier.FULL;
        }
        if (!SoundAttractConfig.COMMON.enableTieredStealthPerformance.get()) {
            return StealthPerfTier.FULL;
        }
        double tps = lastEstimatedTps;

        double vanillaTps = SoundAttractConfig.COMMON.stealthTierVanillaTps.get();
        double shareTps = SoundAttractConfig.COMMON.stealthTierSharedRangeTps.get();
        double currentOnlyTps = SoundAttractConfig.COMMON.stealthTierCurrentTargetsOnlyTps.get();
        double skipExpensiveTps = SoundAttractConfig.COMMON.stealthTierSkipExpensiveChecksTps.get();

        if (vanillaTps > 0.0 && tps <= vanillaTps) return StealthPerfTier.VANILLA;
        if (shareTps > 0.0 && tps <= shareTps) return StealthPerfTier.SHARE_NEARBY;
        if (currentOnlyTps > 0.0 && tps <= currentOnlyTps) return StealthPerfTier.CURRENT_TARGETS_ONLY;
        if (skipExpensiveTps > 0.0 && tps <= skipExpensiveTps) return StealthPerfTier.SKIP_EXPENSIVE;
        return StealthPerfTier.FULL;
    }

    public static StealthPerfTier getPerfTier(Level level) {
        MinecraftServer server = null;
        try {
            if (level instanceof ServerLevel sl) {
                server = sl.getServer();
            }
        } catch (Throwable ignored) {
        }
        return getPerfTier(server);
    }

    private static boolean shouldSkipExpensiveChecks(Level level) {
        return SoundAttractConfig.COMMON.legacyStealthVanillaFallback.get()
                && getPerfTier(level) != StealthPerfTier.FULL;
    }

    private static boolean shouldUseVanillaTargeting(Level level) {
        return SoundAttractConfig.COMMON.legacyStealthVanillaFallback.get()
                && getPerfTier(level) == StealthPerfTier.VANILLA;
    }

    private static boolean shouldInterceptNewTargeting(Level level) {
        return !shouldUseVanillaTargeting(level);
    }

    private static LivingEntity getAttackTargetCompat(Mob mob) {
        if (mob == null) return null;
        LivingEntity direct = mob.getTarget();
        if (direct != null) return direct;
        Optional<LivingEntity> memory = mob.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
        return memory == null ? null : memory.orElse(null);
    }

    private static boolean isTargetingPlayerCompat(Mob mob) {
        return getAttackTargetCompat(mob) instanceof Player;
    }

    public static void recordSuppressedEdgeDetection(Mob mob) {
        if (mob != null) suppressedEdgeDetections.put(mob.getUUID(), Boolean.TRUE);
    }

    public static boolean consumeSuppressedEdgeDetection(Mob mob) {
        if (mob == null) return false;
        return suppressedEdgeDetections.asMap().remove(mob.getUUID()) != null;
    }

    private static int getStealthCheckInterval() {
        return SoundAttractConfig.COMMON.stealthCheckInterval.get();
    }
    private static boolean hasConcealmentEnchant(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null) {
            return false;
        }
        return enchantments.keySet().stream().anyMatch(holder -> holder.is(com.example.soundattract.enchantment.ModEnchantments.CONCEAL));
    }
    public static class GunshotInfo {
        public final long timestamp;
        public final double detectionRange;
        public GunshotInfo(long timestamp, double detectionRange) {
            this.timestamp = timestamp;
            this.detectionRange = detectionRange;
        }
    }

    public static double getRealisticStealthDetectionRange(Mob target, Mob looker, Level level) {
        if (MobGroupManager.isMobInHighWeightOverride(looker) || isFlashbangBlind(looker, level)) {
            return 0.0;
        }

        if (!SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return SoundAttractConfig.COMMON.maxStealthDetectionRange.get();
        }

        if (shouldUseVanillaTargeting(level)) {
            return SoundAttractConfig.COMMON.maxStealthDetectionRange.get();
        }

        return computeMobDetectionRange(target, looker, level);
    }

    private static double computeMobDetectionRange(Mob target, Mob looker, Level level) {
        boolean skipExpensive = shouldSkipExpensiveChecks(level);

        double baseRange = SoundAttractConfig.COMMON.standingDetectionRangePlayer.get();

        if (target.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
            double invisFactor = SoundAttractConfig.COMMON.invisibilityStealthFactor.get();
            baseRange *= invisFactor;
        }

        int effectiveLight = 0;
        net.minecraft.core.BlockPos feet = target.blockPosition();
        net.minecraft.core.BlockPos eyes = feet.above();
        long dayTime = level.getDayTime() % 24000L;
        boolean isDay = dayTime >= 0 && dayTime < 12000L;
        if (level.isLoaded(feet)) {
            effectiveLight = Math.max(effectiveLight, level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, feet));
            if (isDay && level.canSeeSky(feet)) {
                effectiveLight = Math.max(effectiveLight, level.getBrightness(net.minecraft.world.level.LightLayer.SKY, feet));
            }
        }
        if (level.isLoaded(eyes)) {
            effectiveLight = Math.max(effectiveLight, level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, eyes));
            if (isDay && level.canSeeSky(eyes)) {
                effectiveLight = Math.max(effectiveLight, level.getBrightness(net.minecraft.world.level.LightLayer.SKY, eyes));
            }
        }
        double neutral = SoundAttractConfig.COMMON.neutralLightLevel.get();
        double sensitivity = SoundAttractConfig.COMMON.lightLevelSensitivity.get();
        double lightEffect = (effectiveLight - neutral) * (sensitivity / 15.0);
        double lightFactor = 1.0 + lightEffect;
        lightFactor = Math.max(SoundAttractConfig.COMMON.minLightFactor.get(), lightFactor);
        lightFactor = Math.min(SoundAttractConfig.COMMON.maxLightFactor.get(), lightFactor);
        baseRange *= lightFactor;

        if (!skipExpensive) {
            if (level.isRainingAt(feet)) {
                baseRange *= SoundAttractConfig.COMMON.rainStealthFactor.get();
            }
            if (level.isThundering()) {
                baseRange *= SoundAttractConfig.COMMON.thunderStealthFactor.get();
            }
        }

        if (SoundAttractConfig.COMMON.enableHeldItemPenalty.get()) {
            int held = (target.getMainHandItem().isEmpty() ? 0 : 1) + (target.getOffhandItem().isEmpty() ? 0 : 1);
            if (held > 0) {
                double penaltyPerItem = SoundAttractConfig.COMMON.heldItemPenaltyFactor.get();
                for (int i = 0; i < held; i++) baseRange *= penaltyPerItem;
            }
        }

        if (SoundAttractConfig.COMMON.enableEnchantmentPenalty.get()) {
            int enchantedArmor = 0;
            for (net.minecraft.world.item.ItemStack armor : target.getArmorSlots()) {
                if (!armor.isEmpty() && armor.isEnchanted() && !hasConcealmentEnchant(armor)) enchantedArmor++;
            }
            if (enchantedArmor > 0) {
                double armorPenaltyFactor = SoundAttractConfig.COMMON.armorEnchantmentPenaltyFactor.get();
                for (int i = 0; i < enchantedArmor; i++) baseRange *= armorPenaltyFactor;
            }
            int enchantedHeld = 0;
            if (!target.getMainHandItem().isEmpty() && target.getMainHandItem().isEnchanted() && !hasConcealmentEnchant(target.getMainHandItem())) enchantedHeld++;
            if (!target.getOffhandItem().isEmpty() && target.getOffhandItem().isEnchanted() && !hasConcealmentEnchant(target.getOffhandItem())) enchantedHeld++;
            if (enchantedHeld > 0) {
                double heldItemEnchantPenalty = SoundAttractConfig.COMMON.heldItemEnchantmentPenaltyFactor.get();
                for (int i = 0; i < enchantedHeld; i++) baseRange *= heldItemEnchantPenalty;
            }
        }

        if (!skipExpensive && SoundAttractConfig.COMMON.enableEnvironmentalCamouflage.get()) {
            int finalColor = CamoUtil.getFinalPerceptionColor(target);
            if (DebugLogLimiter.allow(DebugLogLimiter.Channel.CAMOUFLAGE_PERCEPTION)) {
                SoundAttractMod.LOGGER.info("[EnvCamo_Mob] {} finalColor=0x{}",
                        target.getName().getString(), Integer.toHexString(finalColor).toUpperCase(java.util.Locale.ROOT));
            }
            java.util.Optional<Integer> envColorOpt = com.example.soundattract.util.EnvironmentalCamoColor.getEnvironmentalColor(
                    target, looker, level, () -> getAverageEnvironmentalColor(target, level));
            
            if (envColorOpt.isPresent()) {
                int envColor = envColorOpt.get();
                int rC = (finalColor >> 16) & 0xFF;
                int gC = (finalColor >> 8) & 0xFF;
                int bC = finalColor & 0xFF;
                int rE = (envColor >> 16) & 0xFF;
                int gE = (envColor >> 8) & 0xFF;
                int bE = envColor & 0xFF;
                
                int diff = Math.abs(rC - rE) + Math.abs(gC - gE) + Math.abs(bC - bE);
                int matchThreshold = SoundAttractConfig.COMMON.environmentalCamouflageColorMatchThreshold.get();
                
                if (diff <= matchThreshold) {
                    float strength = CamoUtil.getCombinedCamoStrength(target);

                    float effectiveStrength = 0.5f + (strength * 0.5f); 
                    
                    double maxBonus = SoundAttractConfig.COMMON.environmentalCamouflageMaxEffectiveness.get();
                    double ratio = (matchThreshold > 0) ? 1.0 - ((double) diff / matchThreshold) : ((diff == 0) ? 1.0 : 0.0);
                    double actualBonus = maxBonus * ratio * effectiveStrength;
                    baseRange *= (1.0 - actualBonus);
                } else if (SoundAttractConfig.COMMON.enableEnvironmentalMismatchPenalty.get()) {
                    int mismatchThreshold = SoundAttractConfig.COMMON.environmentalMismatchThreshold.get();
                    if (diff > mismatchThreshold) {
                        baseRange *= SoundAttractConfig.COMMON.environmentalMismatchPenaltyFactor.get();
                    }
                }
            }
        }

        double moveThreshold = SoundAttractConfig.COMMON.movementThreshold.get();
        if (isMobMoving(target, moveThreshold)) {
            baseRange *= SoundAttractConfig.COMMON.movementStealthPenalty.get();
        } else {
            baseRange *= SoundAttractConfig.COMMON.stationaryStealthBonusFactor.get();
        }

        if (SoundAttractConfig.COMMON.enableCamouflage.get()) {
            java.util.List<String> camouflageItems = new java.util.ArrayList<>(SoundAttractConfig.COMMON.camouflageArmorItems.get());
            if (!camouflageItems.isEmpty()) {
                double effectToApply = 0.0;
                int totalActualArmorPieces = 0;
                long wornListedCamouflagePieces = 0;
                java.util.List<net.minecraft.world.item.ItemStack> armorItemsList = new java.util.ArrayList<>();
                target.getArmorSlots().forEach(armorItemsList::add);
                for (net.minecraft.world.item.ItemStack stack : armorItemsList) {
                    if (!stack.isEmpty()) totalActualArmorPieces++;
                    if (CamoUtil.isCamouflageArmorItem(stack.getItem())) {
                        wornListedCamouflagePieces++;
                    }
                }
                boolean fullSet = (totalActualArmorPieces == 4 && wornListedCamouflagePieces == totalActualArmorPieces && totalActualArmorPieces > 0);
                if (SoundAttractConfig.COMMON.requireFullSetForCamouflageBonus.get()) {
                    if (fullSet) {
                        effectToApply = SoundAttractConfig.COMMON.fullArmorStealthBonus.get();
                    } else {
                        double totalEffectiveness = 0.0;
                        for (int i = 0; i < armorItemsList.size(); i++) {
                            net.minecraft.world.item.ItemStack stack = armorItemsList.get(i);
                            if (stack.isEmpty()) continue;
                            if (CamoUtil.isCamouflageArmorItem(stack.getItem())) {
                                switch (i) {
                                    case 0: totalEffectiveness += SoundAttractConfig.COMMON.bootsCamouflageEffectiveness.get(); break;
                                    case 1: totalEffectiveness += SoundAttractConfig.COMMON.leggingsCamouflageEffectiveness.get(); break;
                                    case 2: totalEffectiveness += SoundAttractConfig.COMMON.chestplateCamouflageEffectiveness.get(); break;
                                    case 3: totalEffectiveness += SoundAttractConfig.COMMON.helmetCamouflageEffectiveness.get(); break;
                                }
                            }
                        }
                        effectToApply = totalEffectiveness;
                    }
                } else {
                    if (fullSet && SoundAttractConfig.COMMON.fullArmorStealthBonus.get() > 0) {
                        effectToApply = SoundAttractConfig.COMMON.fullArmorStealthBonus.get();
                    } else {
                        double totalEffectiveness = 0.0;
                        for (int i = 0; i < armorItemsList.size(); i++) {
                            net.minecraft.world.item.ItemStack stack = armorItemsList.get(i);
                            if (stack.isEmpty()) continue;
                            if (CamoUtil.isCamouflageArmorItem(stack.getItem())) {
                                switch (i) {
                                    case 0: totalEffectiveness += SoundAttractConfig.COMMON.bootsCamouflageEffectiveness.get(); break;
                                    case 1: totalEffectiveness += SoundAttractConfig.COMMON.leggingsCamouflageEffectiveness.get(); break;
                                    case 2: totalEffectiveness += SoundAttractConfig.COMMON.chestplateCamouflageEffectiveness.get(); break;
                                    case 3: totalEffectiveness += SoundAttractConfig.COMMON.helmetCamouflageEffectiveness.get(); break;
                                }
                            }
                        }
                        effectToApply = totalEffectiveness;
                    }
                }
                if (effectToApply > 0.0) {
                    double itemCamoMultiplier = 1.0 - Math.min(effectToApply, 0.99);
                    baseRange *= itemCamoMultiplier;
                }
            }
        }

        return Math.max(SoundAttractConfig.COMMON.minStealthDetectionRange.get(), Math.min(baseRange, SoundAttractConfig.COMMON.maxStealthDetectionRange.get()));
    }

    private static boolean isMobMoving(Mob mob, double threshold) {
        if (mob == null) return false;
        net.minecraft.world.phys.Vec3 current = mob.position();
        java.util.UUID id = mob.getUUID();
        net.minecraft.world.phys.Vec3 last = lastMobPositions.get(id);
        boolean moved = false;
        if (last != null) {
            double distSq = current.distanceToSqr(last);
            moved = distSq > (threshold * threshold);
        }
        lastMobPositions.put(id, current);
        return moved;
    }

    public static double getEffectiveXrayRange(Mob mob) {
        if (mob == null) return 0d;
        if (!SoundAttractConfig.COMMON.enableXrayTargeting.get()) return 0d;

        if (EnhancedAICompat.isEnhancedAiLoaded()) {
            double v = EnhancedAICompat.getXrayAttributeValue(mob);
            if (v > 0d) {
                return v;
            }
            return 0d;
        }

        try {
            String applyTagStr = SoundAttractConfig.COMMON.xrayApplyTag.get();
            if (applyTagStr == null || applyTagStr.isBlank()) {
                return 0d;
            }
            TagKey<EntityType<?>> applyTag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(applyTagStr));
            boolean inApplyTag = mob.getType().is(applyTag);
            if (!inApplyTag) {
                return 0d;
            }

            if (SoundAttractConfig.COMMON.xrayRequireBetterNearby.get()) {
                String betterTagStr = SoundAttractConfig.COMMON.xrayBetterNearbyTag.get();
                if (betterTagStr == null || betterTagStr.isBlank()) {
                    return 0d;
                }
                TagKey<EntityType<?>> betterTag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(betterTagStr));
                boolean inBetterTag = mob.getType().is(betterTag);
                if (!inBetterTag) {
                    return 0d;
                }
            }
        } catch (Exception e) {
            return 0d;
        }

        Double cached = XRAY_RANGE_CACHE.get(mob.getUUID());
        if (cached != null) {
            return cached;
        }

        int max = SoundAttractConfig.COMMON.xrayMaxRange.get();
        if (max <= 0) {
            XRAY_RANGE_CACHE.put(mob.getUUID(), 0d);
            return 0;
        }
        int min = SoundAttractConfig.COMMON.xrayMinRange.get();
        min = Math.max(0, Math.min(min, max));
        double chance = SoundAttractConfig.COMMON.xrayChance.get();
        if (mob.getRandom().nextDouble() >= chance) {
            XRAY_RANGE_CACHE.put(mob.getUUID(), 0d);
            return 0d;
        }
        int spread = max - min;
        int chosen = spread <= 0 ? max : (min + mob.getRandom().nextInt(spread + 1));
        double result = (double) chosen;
        XRAY_RANGE_CACHE.put(mob.getUUID(), result);
        return result;
    }

    public static void recordPlayerGunshot(Player player, double detectionRange) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        long currentTime = player.level().getGameTime();
        playerGunshotInfo.put(player.getUUID(), new GunshotInfo(currentTime, detectionRange));
    }
    private static Optional<Double> getActiveGunshotRange(Player player) {
        GunshotInfo info = playerGunshotInfo.get(player.getUUID());
        if (info == null) {
            return Optional.empty();
        }

        long currentTime = player.level().getGameTime();
        long duration = SoundAttractConfig.COMMON.gunshotDetectionDurationTicks.get();

        if ((currentTime - info.timestamp) < duration) {
            return Optional.of(info.detectionRange);
        } else {
            playerGunshotInfo.remove(player.getUUID());
            return Optional.empty();
        }
    }

    @SubscribeEvent
    public static void onMobAttemptTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel)) {
            return;
        }

        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        boolean mobTarget = event.getTargetType() == LivingChangeTargetEvent.LivingTargetType.MOB_TARGET;
        if (newTarget != null && isFlashbangBlind(mob, mob.level())) {
            event.setCanceled(true);
            return;
        }
        if (newTarget == null || (mobTarget && com.example.soundattract.ai.GroupTargetSharingManager.isShareAssignment(mob, newTarget))) return;
        if (mobTarget && mob.getTarget() == newTarget
                && !com.example.soundattract.ai.GroupTargetSharingManager.tryDirectReacquisition(mob, newTarget)) return;

        if (event.getNewAboutToBeSetTarget() != null && SporeIntegration.isSporeLoaded()) {
            if (SporeGoalInjectorProxy.shouldBypassStealth(mob, event.getNewAboutToBeSetTarget())) {
                return;
            }
        }

        if (event.getNewAboutToBeSetTarget() != null && com.example.soundattract.ai.MobGroupManager.isMobInHighWeightOverride(mob)) {
            event.setCanceled(true);
            return;
        }

        if (SoundAttractConfig.isStealthBypassed(mob)) {
            return;
        }

        if (com.example.soundattract.ai.LivingWallCoordinator.isControlled(mob)) {
            return;
        }

        if (!SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return;
        }

        if (!shouldInterceptNewTargeting(mob.level())) {
            return;
        }

        boolean scout = newTarget instanceof Player && SoundAttractConfig.COMMON.edgeMobSmartBehavior.get()
                && MobGroupManager.isEdgeMob(mob) && !MobGroupManager.isDeserter(mob)
                && !hasActiveXrayOnTarget(mob, newTarget);
        boolean supplemental = newTarget instanceof Player && SoundAttractConfig.COMMON.edgeMobSmartBehavior.get()
                && MobGroupManager.getLeader(mob) != mob && !scout && !MobGroupManager.isDeserter(mob)
                && com.example.soundattract.ai.GroupTargetSharingManager.trySupplementalSpotter(mob);

        if (newTarget instanceof Player playerTarget) {
            if (playerTarget.isCreative() || playerTarget.isSpectator() || !playerTarget.isAlive()) {
                return;
            }

            if (!canMobDetectLivingEntity(mob, playerTarget, !scout && !supplemental)) {
                event.setCanceled(true);
                if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
                    LivingEntity mem = mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
                    if (mem == playerTarget) {
                        mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                    }
                }
            }
        } else if (newTarget instanceof Mob targetMob) {
            if (!FovEvents.isTargetInFov(mob, targetMob, true)) {
                double xrayRange = getEffectiveXrayRange(mob);
                if (xrayRange > 0) {
                    double distSqXray = mob.distanceToSqr(targetMob);
                    if (distSqXray > xrayRange * xrayRange) {
                        event.setCanceled(true);
                        return;
                    }
                } else {
                    event.setCanceled(true);
                    return;
                }
            }
            double range = getRealisticStealthDetectionRange(targetMob, mob, mob.level());
            double distSq = mob.distanceToSqr(targetMob);
            if (distSq > range * range) {
                event.setCanceled(true);
            }
        }

        if (event.isCanceled()) return;
        if (!hasClearTargetSight(mob, newTarget)) {
            if (scout || !canRouteTowardTarget(mob, newTarget)) event.setCanceled(true);
            return;
        }
        boolean directSight = FovEvents.isTargetInFov(mob, newTarget, false);
        if (scout) {
            if (directSight && mobTarget && event.getOriginalAboutToBeSetTarget() == newTarget) {
                recordSuppressedEdgeDetection(mob);
                com.example.soundattract.ai.GroupTargetSharingManager.noteScoutSighting(mob, newTarget);
            }
            event.setCanceled(true);
        } else if (directSight) {
            com.example.soundattract.ai.GroupTargetSharingManager.noteDirectTargetAttempt(mob, newTarget, event);
        }
    }


    public static boolean canMobDetectLivingEntity(Mob mob, LivingEntity target) {
        return canMobDetectLivingEntity(mob, target, true);
    }

    private static boolean canMobDetectLivingEntity(Mob mob, LivingEntity target, boolean respectGroupRoles) {
        if (mob == null || target == null) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.STEALTH_DETECTION)) {
                SoundAttractMod.LOGGER.warn("[CanDetect] Called with null mob or target. Defaulting to detectable.");
            }
            return true;
        }

        if (isFlashbangBlind(mob, mob.level())) return false;

        if (com.example.soundattract.ai.MobGroupManager.isMobInHighWeightOverride(mob)) {
            if (DebugLogLimiter.allow(DebugLogLimiter.Channel.STEALTH_QUERY)) {
                SoundAttractMod.LOGGER.info("[CanDetect] Mob {} is blind due to high-weight sound override.", mob.getName().getString());
            }
            return false;
        }

        if (SoundAttractConfig.isStealthBypassed(mob)) {
            return true;
        }

        if (shouldUseVanillaTargeting(mob.level())) {
            return true;
        }

        if (target instanceof Player player) {
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
                if (DebugLogLimiter.allow(DebugLogLimiter.Channel.STEALTH_QUERY)) {
                    SoundAttractMod.LOGGER.info("[CanDetect] Player {} is creative/spectator/dead. Bypassing stealth. Mob {}.", player.getName().getString(), mob.getName().getString());
                }
                return true;
            }
        } else if (!target.isAlive()) {
            return true;
        }

        if (!SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return true;
        }

        if (respectGroupRoles && SoundAttractConfig.COMMON.edgeMobSmartBehavior.get()) {
            try {
                net.minecraft.world.entity.Mob leader = com.example.soundattract.ai.MobGroupManager.getLeader(mob);
                if (leader != null && leader != mob) {
                    boolean isEdge = com.example.soundattract.ai.MobGroupManager.isEdgeMob(mob);
                    boolean isDeserter = com.example.soundattract.ai.MobGroupManager.isDeserter(mob);
                    if (!isEdge && !isDeserter) {
                        return false;
                    }
                }
            } catch (Throwable t) {}
        }

        Level level = mob.level();

        double xrayRange = getEffectiveXrayRange(mob);
        if (xrayRange > 0) {
            double distSqXray = mob.distanceToSqr(target);
            if (distSqXray <= xrayRange * xrayRange) {
                if (DebugLogLimiter.allow(DebugLogLimiter.Channel.STEALTH_QUERY)) {
                    SoundAttractMod.LOGGER.info(
                        "[XRAY] Mob {} detects {} within XRAY range {} (distSq {}).",
                        mob.getName().getString(), target.getName().getString(), String.format("%.2f", xrayRange), String.format("%.2f", distSqXray)
                    );
                }
                return true;
            }
        }

        double detectionRange;
        if (target instanceof Player player) {
            detectionRange = computePlayerDetectionRange(player, mob, level);
        } else if (target instanceof Mob targetMob) {
            detectionRange = computeMobDetectionRange(targetMob, mob, level);
        } else {
            detectionRange = SoundAttractConfig.COMMON.maxStealthDetectionRange.get();
        }

        double distSq = mob.distanceToSqr(target);

        if (distSq > detectionRange * detectionRange) {
            if (DebugLogLimiter.allow(DebugLogLimiter.Channel.STEALTH_QUERY)) {
                SoundAttractMod.LOGGER.info(
                        "[StealthQuery] {} is OUT OF RANGE for mob {} (distSq: {}, rangeSq: {}).",
                        target.getName().getString(), mob.getName().getString(),
                        String.format("%.2f", distSq),
                        String.format("%.2f", (detectionRange * detectionRange))
                );
            }
            return false;
        }

        if (!FovEvents.isTargetInFov(mob, target, true)) {
            if (DebugLogLimiter.allow(DebugLogLimiter.Channel.STEALTH_QUERY)) {
                SoundAttractMod.LOGGER.info(
                        "[StealthQuery] {} is IN RANGE for mob {} but OUTSIDE FOV. Denying detection.",
                        target.getName().getString(), mob.getName().getString()
                );
            }
            return false;
        }

        if (respectGroupRoles && SoundAttractConfig.COMMON.edgeMobSmartBehavior.get()) {
            try {
                boolean isEdge = MobGroupManager.isEdgeMob(mob);
                boolean isDeserter = MobGroupManager.isDeserter(mob);
                if (isEdge && !isDeserter) {
                    recordSuppressedEdgeDetection(mob);
                    if (DebugLogLimiter.allow(DebugLogLimiter.Channel.STEALTH_QUERY)) {
                        SoundAttractMod.LOGGER.info("[CanDetect] Suppressing EDGE mob {} (non-deserter) despite detectability; signaling RAID.", mob.getName().getString());
                    }
                    return false;
                }
            } catch (Throwable t) {
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.STEALTH_DETECTION)) {
                    SoundAttractMod.LOGGER.warn("[CanDetect] Edge suppression check failed: {}", t.getMessage());
                }
            }
        }
        if (DebugLogLimiter.allow(DebugLogLimiter.Channel.STEALTH_QUERY)) {
            SoundAttractMod.LOGGER.info(
                    "[StealthQuery] {} IS DETECTABLE by mob {} (in range and in FOV).",
                    target.getName().getString(), mob.getName().getString()
            );
        }
        return true;
    }

    public static boolean canReceiveSharedTarget(Mob mob, LivingEntity target) {
        if (mob == null || target == null || !SoundAttractConfig.COMMON.enableStealthMechanics.get()
                || shouldUseVanillaTargeting(mob.level()) || isFlashbangBlind(mob, mob.level())) return false;
        double maximum = SoundAttractConfig.COMMON.maxStealthDetectionRange.get();
        if (SoundAttractConfig.COMMON.enableXrayTargeting.get()) {
            maximum = Math.max(maximum, SoundAttractConfig.COMMON.xrayMaxRange.get());
        }
        double distanceSquared = mob.distanceToSqr(target);
        if (distanceSquared > maximum * maximum) return false;
        double range = target instanceof Player player ? getRealisticStealthDetectionRange(player, mob, mob.level())
                : target instanceof Mob targetMob ? getRealisticStealthDetectionRange(targetMob, mob, mob.level())
                : MobGroupManager.isMobInHighWeightOverride(mob) ? 0.0 : maximum;
        return range > 0 && (distanceSquared <= range * range || hasActiveXrayOnTarget(mob, target))
                && !FovEvents.isSmokeBlockingLineOfSight(mob, target);
    }

    public static boolean hasDirectGroupSight(Mob mob, LivingEntity target) {
        return canReceiveSharedTarget(mob, target) && FovEvents.isTargetInFov(mob, target, false)
                && hasClearTargetSight(mob, target);
    }

    private static boolean hasClearTargetSight(Mob mob, LivingEntity target) {
        if (FovEvents.isSmokeBlockingLineOfSight(mob, target)) return false;
        Vec3 eye = mob.getEyePosition();
        boolean ignoreEntityBlockers = hasActiveXrayOnTarget(mob, target);
        return isPathClear(mob, target, eye, target.getEyePosition(), ignoreEntityBlockers)
                || isPathClear(mob, target, eye, target.position().add(0, target.getBbHeight() * 0.5, 0), ignoreEntityBlockers)
                || isPathClear(mob, target, eye, target.position().add(0, Math.max(0.1, target.getBbHeight() * 0.15), 0), ignoreEntityBlockers);
    }

    private static boolean canRouteTowardTarget(Mob mob, LivingEntity target) {
        if (mob == null || target == null || mob.level().isClientSide()) return false;
        if (com.example.soundattract.ai.LivingWallCoordinator.isControlled(mob)) {
            return true;
        }
        if (com.example.soundattract.ai.LivingWallCoordinator.hasFormingOrActiveBridgeNear(mob, target)) {
            return true;
        }
        if (mob.getLastHurtByMob() == target && (mob.tickCount - mob.getLastHurtByMobTimestamp() < 200)) {
            return true;
        }
        RouteProbeKey key = new RouteProbeKey(mob.level().dimension().location(), mob.getUUID(), target.getUUID());
        RouteEvidence evidence = routeEvidence.remove(key);
        long now = mob.level().getGameTime();
        if (evidence != null && now - evidence.tick() <= ROUTE_PROBE_TTL_TICKS
                && evidence.mobPosition().distanceToSqr(mob.position()) <= 1.0
                && evidence.targetPosition().distanceToSqr(target.position()) <= 1.0) {
            if (!PathTaskScheduler.tryConsumePathAttempt(mob.level(), PathTaskScheduler.Priority.COMBAT)) {
                if (pendingRouteProbes.size() < MAX_PENDING_ROUTE_PROBES || pendingRouteProbes.containsKey(key)) {
                    pendingRouteProbes.put(key, new RouteProbe(key, mob.position(), target.position(),
                            mob.level().getServer() == null ? now : mob.level().getServer().getTickCount()));
                }
                return false;
            }
            return hasValidRoute(mob, target);
        }
        if (!PathTaskScheduler.tryConsumePathAttempt(mob.level(), PathTaskScheduler.Priority.COMBAT)) {
            if (pendingRouteProbes.size() < MAX_PENDING_ROUTE_PROBES || pendingRouteProbes.containsKey(key)) {
                pendingRouteProbes.put(key, new RouteProbe(key, mob.position(), target.position(),
                        mob.level().getServer() == null ? now : mob.level().getServer().getTickCount()));
            }
            return false;
        }
        return hasValidRoute(mob, target);
    }

    private static boolean hasValidRoute(Mob mob, LivingEntity target) {
        var path = mob.getNavigation().createPath(target, 0);
        if (path == null || !path.canReach()) {
            return false;
        }
        BlockPos pathTarget = path.getTarget();
        if (pathTarget == null) return false;
        Vec3 endpointEye = Vec3.atBottomCenterOf(pathTarget).add(0.0D, mob.getEyeHeight(), 0.0D);
        Vec3 targetEye = target.getEyePosition();
        Vec3 targetCenter = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        Vec3 targetFeet = target.position().add(0.0D, Math.max(0.1D, target.getBbHeight() * 0.15D), 0.0D);
        return isPathClear(mob, target, endpointEye, targetEye, true)
                || isPathClear(mob, target, endpointEye, targetCenter, true)
                || isPathClear(mob, target, endpointEye, targetFeet, true);
    }

    private static void processPendingRouteProbes(MinecraftServer server) {
        if (server == null || pendingRouteProbes.isEmpty()) return;
        long now = server.getTickCount();
        int processed = 0;
        int scanned = 0;
        var iterator = pendingRouteProbes.entrySet().iterator();
        while (iterator.hasNext() && processed < MAX_ROUTE_PROBES_PER_TICK
                && scanned < MAX_ROUTE_PROBES_PER_TICK * 4) {
            Map.Entry<RouteProbeKey, RouteProbe> entry = iterator.next();
            scanned++;
            RouteProbe probe = entry.getValue();
            if (now - probe.queuedServerTick() > ROUTE_PROBE_TTL_TICKS) {
                iterator.remove();
                continue;
            }
            ServerLevel level = server.getLevel(ResourceKey.create(
                    Registries.DIMENSION, probe.key().dimension()));
            if (level == null) {
                iterator.remove();
                continue;
            }
            Entity mobEntity = level.getEntity(probe.key().mobId());
            Entity targetEntity = level.getEntity(probe.key().targetId());
            if (!(mobEntity instanceof Mob mob) || !(targetEntity instanceof LivingEntity target)
                    || !mob.isAlive() || !target.isAlive()
                    || mob.position().distanceToSqr(probe.mobPosition()) > 1.0
                    || target.position().distanceToSqr(probe.targetPosition()) > 1.0
                    || com.example.soundattract.ai.LivingWallCoordinator.isControlled(mob)) {
                iterator.remove();
                continue;
            }
            if (!PathTaskScheduler.tryConsumePathAttempt(level, PathTaskScheduler.Priority.COMBAT)) break;
            iterator.remove();
            processed++;
            if (hasValidRoute(mob, target)) {
                routeEvidence.put(probe.key(), new RouteEvidence(target.position(), mob.position(), level.getGameTime()));
                while (routeEvidence.size() > MAX_PENDING_ROUTE_PROBES) {
                    routeEvidence.remove(routeEvidence.keySet().iterator().next());
                }
            }
        }
    }

    private record RouteProbeKey(ResourceLocation dimension, UUID mobId, UUID targetId) {}
    private record RouteProbe(RouteProbeKey key, Vec3 mobPosition, Vec3 targetPosition, long queuedServerTick) {}
    private record RouteEvidence(Vec3 targetPosition, Vec3 mobPosition, long tick) {}

    @Deprecated
    public static boolean canMobDetectPlayer(Mob mob, Player player) {
        return canMobDetectLivingEntity(mob, player);
    }

    public static boolean canMobDetectPlayerWithRange(Mob mob, Player player, double detectionRange) {
        if (mob == null || player == null) {
            return true;
        }
        if (SoundAttractConfig.isStealthBypassed(mob)) {
            return true;
        }
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
            return true;
        }
        if (!SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return true;
        }
        if (shouldUseVanillaTargeting(mob.level())) {
            return true;
        }
        if (SoundAttractConfig.COMMON.edgeMobSmartBehavior.get()) {
            try {
                Mob leader = MobGroupManager.getLeader(mob);
                if (leader != null && leader != mob) {
                    boolean isEdge = MobGroupManager.isEdgeMob(mob);
                    boolean isDeserter = MobGroupManager.isDeserter(mob);
                    if (!isEdge && !isDeserter) {
                        return false;
                    }
                }
            } catch (Throwable ignored) {}
        }
        double xrayRange = getEffectiveXrayRange(mob);
        if (xrayRange > 0) {
            double distSqXray = mob.distanceToSqr(player);
            if (distSqXray <= xrayRange * xrayRange) {
                return true;
            }
        }
        double distSq = mob.distanceToSqr(player);
        if (distSq > detectionRange * detectionRange) {
            return false;
        }
        return FovEvents.isTargetInFov(mob, player, true);
    }

    private static boolean canMobDetectTargetNoEdgeSuppression(Mob mob, LivingEntity target) {
        if (mob == null || target == null) {
            return true;
        }
        if (SoundAttractConfig.isStealthBypassed(mob)) {
            return true;
        }
        if (target instanceof Player player && (player.isCreative() || player.isSpectator() || !player.isAlive())) {
            return true;
        } else if (!target.isAlive()) {
            return true;
        }
        if (!SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return true;
        }
        if (shouldUseVanillaTargeting(mob.level())) {
            return true;
        }

        Level level = mob.level();

        double xrayRange = getEffectiveXrayRange(mob);
        if (xrayRange > 0) {
            double distSqXray = mob.distanceToSqr(target);
            if (distSqXray <= xrayRange * xrayRange) {
                return true;
            }
        }
        double detectionRange;
        if (target instanceof Player player) {
            detectionRange = getRealisticStealthDetectionRange(player, mob, level);
        } else if (target instanceof Mob targetMob) {
            detectionRange = getRealisticStealthDetectionRange(targetMob, mob, level);
        } else {
            detectionRange = SoundAttractConfig.COMMON.maxStealthDetectionRange.get();
        }

        double distSq = mob.distanceToSqr(target);
        if (distSq > detectionRange * detectionRange) {
            return false;
        }
        if (!FovEvents.isTargetInFov(mob, target, true)) {
            return false;
        }
        return true;
    }

    public static boolean shouldSuppressTargeting(Mob mob) {
        if (mob != null && isFlashbangBlind(mob, mob.level())) return true;
        if (!SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return false;
        }
        LivingEntity target = getAttackTargetCompat(mob);
        if (target == null) {
            return false;
        }
        return !canMobDetectLivingEntity(mob, target);
    }

    public static boolean shouldSuppressTargeting(Mob mob, LivingEntity target) {
        if (mob != null && isFlashbangBlind(mob, mob.level())) return true;
        return !canMobDetectLivingEntity(mob, target);
    }

    private static boolean isFlashbangBlind(Mob mob, Level level) {
        return CsGrenadesCompat.isLoaded()
                && SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()
                && SoundAttractConfig.COMMON.enableFlashbangBlinding.get()
                && FlashbangEffect.hasAnyBlindMobs()
                && FlashbangEffect.isMobBlind(mob, level.getGameTime());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null || server.overworld() == null) return;
        if (!SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return;
        }

        long gameTime = server.overworld().getGameTime();
        if (gameTime != lastEstimatedTpsGameTime) {
            lastEstimatedTpsGameTime = gameTime;
            updateEstimatedTps(server);
        }

        StealthPerfTier tier = getPerfTier(server);
        if (tier == StealthPerfTier.VANILLA && SoundAttractConfig.COMMON.legacyStealthVanillaFallback.get()) {
            pendingRouteProbes.clear();
            routeEvidence.clear();
            return;
        }
        processPendingRouteProbes(server);
        int stealthCheckInterval = getStealthCheckInterval();

        if (gameTime % stealthCheckInterval != 0 || gameTime == lastStealthCheckTick) {
            return;
        }
        lastStealthCheckTick = gameTime;

        Set<UUID> seenPlayerIdsAcrossLevels = new HashSet<>();
        Set<UUID> seenMobIdsAcrossLevels = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            int scanningRadius = Math.max(32, (int) Math.ceil(SoundAttractConfig.COMMON.maxStealthDetectionRange.get()) + 16);
            Set<Mob> mobsToCheck = new HashSet<>();
            Set<LivingEntity> entitiesToTickCamo = new HashSet<>();
            Set<UUID> seenPlayerIds = new HashSet<>();
            Set<UUID> seenMobIds = new HashSet<>();

            for (net.minecraft.server.level.ServerPlayer serverPlayer : level.players()) {
                seenPlayerIds.add(serverPlayer.getUUID());
                seenPlayerIdsAcrossLevels.add(serverPlayer.getUUID());
                AABB scanArea = serverPlayer.getBoundingBox().inflate(scanningRadius);
                
                mobsToCheck.addAll(level.getEntitiesOfClass(Mob.class, scanArea, entity -> entity.isAlive() && getAttackTargetCompat(entity) != null));
                
                entitiesToTickCamo.addAll(level.getEntitiesOfClass(LivingEntity.class, scanArea, e -> e.isAlive() && !(e instanceof Player)));
            }

            for (LivingEntity le : entitiesToTickCamo) {
                CamouflageCapability camo = le.getData(CamoAttachments.CAMOUFLAGE);
                camo.tickDegradation(le, level, tier);
            }

            for (Mob mob : mobsToCheck) {
                seenMobIds.add(mob.getUUID());
                seenMobIdsAcrossLevels.add(mob.getUUID());
                LivingEntity target = getAttackTargetCompat(mob);
                UUID mobId = mob.getUUID();
                
                if (target == null) {
                    mobOutOfRangeTicks.remove(mobId);
                    continue;
                }
                
                if (target instanceof Player playerTarget && (playerTarget.isCreative() || playerTarget.isSpectator())) {
                    mobOutOfRangeTicks.remove(mobId);
                    continue;
                }
                if (com.example.soundattract.ai.GroupTargetSharingManager.ownsTarget(mob, target)) {
                    mobOutOfRangeTicks.remove(mobId);
                    continue;
                }
                boolean canCurrentlyDetect = canMobDetectLivingEntity(mob, target, false);

                if (canCurrentlyDetect) {
                    if (mobOutOfRangeTicks.remove(mobId) != null) {
                        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.STEALTH_DETECTION)) {
                            SoundAttractMod.LOGGER.info(
                                    "[TickCheck] Mob {} regained direct detection of {}. Grace period reset.",
                                    mob.getName().getString(), target.getName().getString()
                            );
                        }
                    }
                } else {
                    int ticks = mobOutOfRangeTicks.getOrDefault(mobId, 0) + stealthCheckInterval;
                    if (isFlashbangBlind(mob, level) || ticks >= SoundAttractConfig.COMMON.stealthGracePeriodTicks.get()) {
                        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.STEALTH_DETECTION)) {
                            SoundAttractMod.LOGGER.info(
                                "[TickCheck] Mob {} lost target {} due to stealth grace period timeout.",
                                mob.getName().getString(), target.getName().getString()
                            );
                        }
                        if (mob.getBrain().hasMemoryValue(MemoryModuleType.ANGRY_AT)) {
                            mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
                        }
                        if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
                            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                        }
                        mob.setTarget(null);
                        mobOutOfRangeTicks.remove(mobId);
                    } else {
                        mobOutOfRangeTicks.put(mobId, ticks);
                        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.STEALTH_DETECTION)) {
                            SoundAttractMod.LOGGER.info(
                                "[TickCheck] Mob {} cannot detect {}. In grace period ({}/{}).",
                                mob.getName().getString(), target.getName().getString(),
                                ticks, SoundAttractConfig.COMMON.stealthGracePeriodTicks.get()
                            );
                        }
                    }
                }
            }

        }
        mobOutOfRangeTicks.keySet().removeIf(id -> !seenMobIdsAcrossLevels.contains(id));
        XRAY_RANGE_CACHE.keySet().removeIf(id -> !seenMobIdsAcrossLevels.contains(id));
        lastMobPositions.keySet().removeIf(id -> !seenMobIdsAcrossLevels.contains(id));
        suppressedEdgeDetections.asMap().keySet().removeIf(id -> !seenMobIdsAcrossLevels.contains(id));
        lastPlayerPositions.keySet().removeIf(id -> !seenPlayerIdsAcrossLevels.contains(id));
        playerGunshotInfo.keySet().removeIf(id -> !seenPlayerIdsAcrossLevels.contains(id));
    }

    public static boolean isPlayerMoving(Player player, double threshold) {
        if (player == null) {
            return false;
        }
        net.minecraft.world.phys.Vec3 currentPos = player.position();
        net.minecraft.world.phys.Vec3 lastPos = lastPlayerPositions.get(player.getUUID());
        boolean moved = false;
        if (lastPos != null) {
            double distSq = currentPos.distanceToSqr(lastPos);
            moved = distSq > (threshold * threshold);
        }
        lastPlayerPositions.put(player.getUUID(), currentPos);
        return moved;
    }

    private static PlayerStance determinePlayerStance(Player player) {
        Pose currentPose = player.getPose();
        boolean isVisuallyCrawling = player.isVisuallyCrawling();
        boolean isCrouching = player.isCrouching();
        float playerHeight = player.getDimensions(currentPose).height();

        if (DebugLogLimiter.allow(DebugLogLimiter.Channel.PLAYER_STANCE)) {
            SoundAttractMod.LOGGER.info(
                    "[DetermineStanceDetails] Player: {}, Pose: {}, isVisuallyCrawling: {}, isCrouching: {}, Height: {}",
                    player.getName().getString(),
                    currentPose,
                    isVisuallyCrawling,
                    isCrouching,
                    String.format("%.2f", playerHeight)
            );
        }

        if (isVisuallyCrawling || playerHeight <= 1.0F) {
            if (DebugLogLimiter.allow(DebugLogLimiter.Channel.PLAYER_STANCE)) {
                SoundAttractMod.LOGGER.info("[DetermineStance] Player {} is CRAWLING (VisualCrawl: true, Pose: {}, Height: {})",
                        player.getName().getString(), currentPose, String.format("%.2f", playerHeight));
            }
            return PlayerStance.CRAWLING;
        }

        if (isCrouching) {
            if (DebugLogLimiter.allow(DebugLogLimiter.Channel.PLAYER_STANCE)) {
                SoundAttractMod.LOGGER.info("[DetermineStance] Player {} is SNEAKING (Pose: {}, Crouching: {}, Height: {})",
                        player.getName().getString(), currentPose, isCrouching, String.format("%.2f", playerHeight));
            }
            return PlayerStance.SNEAKING;
        }

        if (DebugLogLimiter.allow(DebugLogLimiter.Channel.PLAYER_STANCE)) {
            SoundAttractMod.LOGGER.info("[DetermineStance] Player {} is STANDING (Pose: {}, Height: {})",
                    player.getName().getString(), currentPose, String.format("%.2f", playerHeight));
        }
        return PlayerStance.STANDING;
    }

    public static double getRealisticStealthDetectionRange(Player player, Mob mob, Level level) {
        if (MobGroupManager.isMobInHighWeightOverride(mob) || isFlashbangBlind(mob, level)) {
            return 0.0;
        }

        if (!SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return SoundAttractConfig.COMMON.maxStealthDetectionRange.get();
        }

        if (shouldUseVanillaTargeting(level)) {
            return SoundAttractConfig.COMMON.maxStealthDetectionRange.get();
        }
        return computePlayerDetectionRange(player, mob, level);
    }

    private static double computePlayerDetectionRange(Player player, Mob mob, Level level) {
        PlayerStealthBaseline baseline = getOrComputePlayerStealthBaseline(player, level);
        double baseRange;
        if (baseline.gunshotRangeOpt().isPresent()) {
            baseRange = baseline.defaultBaseRange();
        } else {
            com.example.soundattract.config.MobProfile2 mobProfile = SoundAttractConfig.getMatchingProfile(mob);
            Optional<Double> override = (mobProfile != null) ? mobProfile.getDetectionOverride(baseline.stance()) : Optional.empty();
            if (override.isPresent()) {
                baseRange = override.get();
            } else {
                baseRange = baseline.defaultBaseRange();
            }
        }

        baseRange *= baseline.combinedMultiplier();

        if (SoundAttractConfig.COMMON.enableEnvironmentalCamouflage.get()) {
            int finalColor = baseline.finalColor();
            java.util.Optional<Integer> envColorOpt = com.example.soundattract.util.EnvironmentalCamoColor.getEnvironmentalColor(
                    player, mob, level, () -> getAverageEnvironmentalColor(player, level));

            if (envColorOpt.isPresent()) {
                int envColor = envColorOpt.get();
                int rC = (finalColor >> 16) & 0xFF;
                int gC = (finalColor >> 8) & 0xFF;
                int bC = finalColor & 0xFF;
                int rE = (envColor >> 16) & 0xFF;
                int gE = (envColor >> 8) & 0xFF;
                int bE = envColor & 0xFF;
                int diff = Math.abs(rC - rE) + Math.abs(gC - gE) + Math.abs(bC - bE);
                int matchThreshold = SoundAttractConfig.COMMON.environmentalCamouflageColorMatchThreshold.get();
                if (diff <= matchThreshold) {
                    float strength = CamoUtil.getCombinedCamoStrength(player);
                    float effectiveStrength = 0.4f + (strength * 0.6f);
                    double maxBonus = SoundAttractConfig.COMMON.environmentalCamouflageMaxEffectiveness.get();
                    double ratio = (matchThreshold > 0) ? 1.0 - ((double) diff / matchThreshold) : ((diff == 0) ? 1.0 : 0.0);
                    double actualBonus = maxBonus * ratio * effectiveStrength;
                    baseRange *= (1.0 - actualBonus);
                } else if (SoundAttractConfig.COMMON.enableEnvironmentalMismatchPenalty.get()) {
                    int mismatchThreshold = SoundAttractConfig.COMMON.environmentalMismatchThreshold.get();
                    if (diff > mismatchThreshold) {
                        baseRange *= SoundAttractConfig.COMMON.environmentalMismatchPenaltyFactor.get();
                    }
                }
            }
        }

        return Math.max(SoundAttractConfig.COMMON.minStealthDetectionRange.get(),
                Math.min(baseRange, SoundAttractConfig.COMMON.maxStealthDetectionRange.get()));
    }

    public static PlayerStealthBaseline getOrComputePlayerStealthBaseline(Player player, Level level) {
        if (player == null || level == null) {
            return new PlayerStealthBaseline(null, 0L, PlayerStance.STANDING, Optional.empty(), 16.0, 1.0, 0);
        }
        long now = level.getGameTime();
        PlayerStealthBaseline cached = PLAYER_STEALTH_CACHE.get(player.getUUID());
        if (cached != null && cached.gameTime() == now && cached.dimension().equals(level.dimension())) {
            return cached;
        }

        boolean skipExpensive = shouldSkipExpensiveChecks(level);
        PlayerStance currentStance = determinePlayerStance(player);
        Optional<Double> gunshotRangeOpt = getActiveGunshotRange(player);
        double baseRange;
        if (gunshotRangeOpt.isPresent()) {
            baseRange = gunshotRangeOpt.get();
            double standingRange = SoundAttractConfig.COMMON.standingDetectionRangePlayer.get();
            double currentPoseBaseRange;
            switch (currentStance) {
                case CRAWLING -> currentPoseBaseRange = SoundAttractConfig.COMMON.crawlingDetectionRangePlayer.get();
                case SNEAKING -> currentPoseBaseRange = SoundAttractConfig.COMMON.sneakingDetectionRangePlayer.get();
                default -> currentPoseBaseRange = standingRange;
            }
            double poseReduction = Math.max(0, standingRange - currentPoseBaseRange);
            baseRange -= poseReduction;
        } else {
            com.example.soundattract.config.PlayerProfile2 playerProfile = SoundAttractConfig.getMatchingPlayerProfile(player);
            Optional<Double> playerOverride = (playerProfile != null) ? playerProfile.getDetectionOverride(currentStance) : Optional.empty();
            if (playerOverride.isPresent()) {
                baseRange = playerOverride.get();
            } else {
                switch (currentStance) {
                    case CRAWLING -> baseRange = SoundAttractConfig.COMMON.crawlingDetectionRangePlayer.get();
                    case SNEAKING -> baseRange = SoundAttractConfig.COMMON.sneakingDetectionRangePlayer.get();
                    case STANDING -> baseRange = SoundAttractConfig.COMMON.standingDetectionRangePlayer.get();
                    default -> baseRange = SoundAttractConfig.COMMON.standingDetectionRangePlayer.get();
                }
            }
        }

        double multiplier = 1.0;
        if (player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
            multiplier *= SoundAttractConfig.COMMON.invisibilityStealthFactor.get();
        }

        BlockPos basePos = player.blockPosition();
        long dayTime = level.getDayTime() % 24000L;
        boolean isDay = dayTime >= 0 && dayTime < 12000L;

        int effectiveLight = 0;
        BlockPos playerFeetPos = player.blockPosition();
        BlockPos playerEyesPos = playerFeetPos.above();

        if (level.isLoaded(playerFeetPos)) {
            effectiveLight = Math.max(effectiveLight, level.getBrightness(LightLayer.BLOCK, playerFeetPos));
            if (isDay && level.canSeeSky(playerFeetPos)) {
                effectiveLight = Math.max(effectiveLight, level.getBrightness(LightLayer.SKY, playerFeetPos));
            }
        }
        if (level.isLoaded(playerEyesPos)) {
            effectiveLight = Math.max(effectiveLight, level.getBrightness(LightLayer.BLOCK, playerEyesPos));
            if (isDay && level.canSeeSky(playerEyesPos)) {
                effectiveLight = Math.max(effectiveLight, level.getBrightness(LightLayer.SKY, playerEyesPos));
            }
        }

        for (ItemStack s : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (s.getItem() instanceof BlockItem bi) {
                BlockState def = bi.getBlock().defaultBlockState();
                if (level.isLoaded(basePos)) {
                    int emit = def.getLightEmission(level, basePos);
                    effectiveLight = Math.max(effectiveLight, emit);
                }
            }
        }

        double neutral = SoundAttractConfig.COMMON.neutralLightLevel.get();
        double sensitivity = SoundAttractConfig.COMMON.lightLevelSensitivity.get();
        double lightEffect = (effectiveLight - neutral) * (sensitivity / 15.0);
        double lightFactor = 1.0 + lightEffect;
        lightFactor = Math.max(SoundAttractConfig.COMMON.minLightFactor.get(), lightFactor);
        lightFactor = Math.min(SoundAttractConfig.COMMON.maxLightFactor.get(), lightFactor);
        multiplier *= lightFactor;

        if (SoundAttractConfig.COMMON.enableHeldItemPenalty.get()) {
            int heldItemCount = 0;
            if (!player.getMainHandItem().isEmpty()) heldItemCount++;
            if (!player.getOffhandItem().isEmpty()) heldItemCount++;
            if (heldItemCount > 0) {
                double penaltyPerItem = SoundAttractConfig.COMMON.heldItemPenaltyFactor.get();
                for (int i = 0; i < heldItemCount; i++) {
                    multiplier *= penaltyPerItem;
                }
            }
        }
        if (SoundAttractConfig.COMMON.enableEnchantmentPenalty.get()) {
            int visiblyEnchantedArmorPieces = 0;
            for (ItemStack armorStack : player.getArmorSlots()) {
                if (!armorStack.isEmpty() && armorStack.isEnchanted() && !hasConcealmentEnchant(armorStack)) {
                    visiblyEnchantedArmorPieces++;
                }
            }
            if (visiblyEnchantedArmorPieces > 0) {
                double armorPenaltyFactor = SoundAttractConfig.COMMON.armorEnchantmentPenaltyFactor.get();
                for (int i = 0; i < visiblyEnchantedArmorPieces; i++) {
                    multiplier *= armorPenaltyFactor;
                }
            }
            int visiblyEnchantedHeldItems = 0;
            if (!player.getMainHandItem().isEmpty() && player.getMainHandItem().isEnchanted() && !hasConcealmentEnchant(player.getMainHandItem())) {
                visiblyEnchantedHeldItems++;
            }
            if (!player.getOffhandItem().isEmpty() && player.getOffhandItem().isEnchanted() && !hasConcealmentEnchant(player.getOffhandItem())) {
                visiblyEnchantedHeldItems++;
            }
            if (visiblyEnchantedHeldItems > 0) {
                double heldItemEnchantPenalty = SoundAttractConfig.COMMON.heldItemEnchantmentPenaltyFactor.get();
                for (int i = 0; i < visiblyEnchantedHeldItems; i++) {
                    multiplier *= heldItemEnchantPenalty;
                }
            }
        }

        if (!skipExpensive) {
            if (level.isRainingAt(player.blockPosition())) {
                multiplier *= SoundAttractConfig.COMMON.rainStealthFactor.get();
            }
            if (level.isThundering()) {
                multiplier *= SoundAttractConfig.COMMON.thunderStealthFactor.get();
            }
        }

        if (currentStance != PlayerStance.SNEAKING && currentStance != PlayerStance.CRAWLING) {
            if (isPlayerMoving(player, SoundAttractConfig.COMMON.movementThreshold.get())) {
                multiplier *= SoundAttractConfig.COMMON.movementStealthPenalty.get();
            } else {
                multiplier *= SoundAttractConfig.COMMON.stationaryStealthBonusFactor.get();
            }
        }

        List<String> camouflageItems = new ArrayList<>(SoundAttractConfig.COMMON.camouflageArmorItems.get());
        if (SoundAttractConfig.COMMON.enableCamouflage.get() && !camouflageItems.isEmpty()) {
            double effectToApply = calculateArmorCamoEffect(player, camouflageItems);
            if (effectToApply > 0.0) {
                double itemCamoMultiplier = 1.0 - Math.min(effectToApply, 0.99);
                multiplier *= itemCamoMultiplier;
            }
        }

        int finalColor = 0;
        if (SoundAttractConfig.COMMON.enableEnvironmentalCamouflage.get()) {
            finalColor = CamoUtil.getFinalPerceptionColor(player);
        }

        PlayerStealthBaseline baseline = new PlayerStealthBaseline(level.dimension(), now, currentStance, gunshotRangeOpt, baseRange, multiplier, finalColor);
        PLAYER_STEALTH_CACHE.put(player.getUUID(), baseline);
        return baseline;
    }

    private static double calculateArmorCamoEffect(Player player, List<String> camouflageItems) {
        if (camouflageItems == null || camouflageItems.isEmpty()) return 0.0;
        double effectToApply = 0.0;
        int totalActualArmorPieces = 0;
        long wornListedCamouflagePieces = 0;
        for (ItemStack armorStack : player.getArmorSlots()) {
            if (!armorStack.isEmpty()) {
                totalActualArmorPieces++;
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(armorStack.getItem());
                if (itemId != null && camouflageItems.contains(itemId.toString())) {
                    wornListedCamouflagePieces++;
                }
            }
        }
        boolean isActuallyWearingFullSetOfListedItems = (totalActualArmorPieces == 4 && wornListedCamouflagePieces == totalActualArmorPieces && totalActualArmorPieces > 0);
        if (SoundAttractConfig.COMMON.requireFullSetForCamouflageBonus.get()) {
            if (isActuallyWearingFullSetOfListedItems) {
                effectToApply = SoundAttractConfig.COMMON.fullArmorStealthBonus.get();
            } else {
                double totalEffectiveness = 0.0;
                List<ItemStack> armorItemsList = new ArrayList<>();
                player.getArmorSlots().forEach(armorItemsList::add);
                for (int i = 0; i < armorItemsList.size(); i++) {
                    ItemStack stack = armorItemsList.get(i);
                    if (stack.isEmpty()) continue;
                    Item item = stack.getItem();
                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
                    if (itemId != null && camouflageItems.contains(itemId.toString())) {
                        switch (i) {
                            case 0 -> totalEffectiveness += SoundAttractConfig.COMMON.bootsCamouflageEffectiveness.get();
                            case 1 -> totalEffectiveness += SoundAttractConfig.COMMON.leggingsCamouflageEffectiveness.get();
                            case 2 -> totalEffectiveness += SoundAttractConfig.COMMON.chestplateCamouflageEffectiveness.get();
                            case 3 -> totalEffectiveness += SoundAttractConfig.COMMON.helmetCamouflageEffectiveness.get();
                        }
                    }
                }
                effectToApply = totalEffectiveness;
            }
        } else {
            if (isActuallyWearingFullSetOfListedItems && SoundAttractConfig.COMMON.fullArmorStealthBonus.get() > 0) {
                effectToApply = SoundAttractConfig.COMMON.fullArmorStealthBonus.get();
            } else {
                double totalEffectiveness = 0.0;
                List<ItemStack> armorItemsList = new ArrayList<>();
                player.getArmorSlots().forEach(armorItemsList::add);
                for (int i = 0; i < armorItemsList.size(); i++) {
                    ItemStack stack = armorItemsList.get(i);
                    if (stack.isEmpty()) continue;
                    Item item = stack.getItem();
                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
                    if (itemId != null && camouflageItems.contains(itemId.toString())) {
                        switch (i) {
                            case 0 -> totalEffectiveness += SoundAttractConfig.COMMON.bootsCamouflageEffectiveness.get();
                            case 1 -> totalEffectiveness += SoundAttractConfig.COMMON.leggingsCamouflageEffectiveness.get();
                            case 2 -> totalEffectiveness += SoundAttractConfig.COMMON.chestplateCamouflageEffectiveness.get();
                            case 3 -> totalEffectiveness += SoundAttractConfig.COMMON.helmetCamouflageEffectiveness.get();
                        }
                    }
                }
                effectToApply = totalEffectiveness;
            }
        }
        return effectToApply;
    }

    private static Optional<Integer> getAverageEnvironmentalColor(LivingEntity entity, Level level) {
        if (entity == null || level == null) {
            return Optional.empty();
        }
        if (QuantifiedCacheCompat.isUsable()) {
            BlockPos pos = entity.blockPosition();
            String key = new StringBuilder(96)
                .append(level.dimension().location().toString()).append('|')
                .append(pos.getX()).append(',').append(pos.getY()).append(',').append(pos.getZ())
                .toString();
            return QuantifiedCacheCompat.getCached(
                "soundattract_env_color_entity",
                key,
                () -> soundattract$computeAverageEnvironmentalColor(entity, level),
                2L,
                8192L
            );
        }

        return soundattract$computeAverageEnvironmentalColor(entity, level);
    }

    private static Optional<Integer> soundattract$computeAverageEnvironmentalColor(LivingEntity entity, Level level) {
        BlockPos entityPos = entity.blockPosition();
        int numColors = 0;
        int totalR = 0, totalG = 0, totalB = 0;

        for (int yOffset = 0; yOffset >= -1; yOffset--) {
            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                    BlockPos currentPos = entityPos.offset(xOffset, yOffset, zOffset);
                    if (level.isLoaded(currentPos)) {
                        BlockState blockState = level.getBlockState(currentPos);
                        if (!blockState.isAir()) {
                            int mapColor;
                            try {
                                mapColor = blockState.getMapColor(level, currentPos).col;
                            } catch (Throwable ignored) {
                                continue;
                            }
                            if (mapColor != 0) {
                                totalR += (mapColor >> 16) & 0xFF;
                                totalG += (mapColor >> 8) & 0xFF;
                                totalB += mapColor & 0xFF;
                                numColors++;
                            }
                        }
                    }
                }
            }
        }

        if (numColors == 0) {
             if (DebugLogLimiter.allow(DebugLogLimiter.Channel.CAMOUFLAGE_PERCEPTION)) {
                 SoundAttractMod.LOGGER.info("[GetEnvColor] No block map colors found in sampling area.");
            }
            return Optional.empty();
        }

        int avgR = totalR / numColors;
        int avgG = totalG / numColors;
        int avgB = totalB / numColors;
        int finalAvgColor = (avgR << 16) | (avgG << 8) | avgB;

        if (DebugLogLimiter.allow(DebugLogLimiter.Channel.CAMOUFLAGE_PERCEPTION)) {
            SoundAttractMod.LOGGER.info("[GetEnvColor] Average environment color: 0x{} from {} blocks.",
                    Integer.toHexString(finalAvgColor).toUpperCase(java.util.Locale.ROOT), numColors);
        }
        return Optional.of(finalAvgColor);
    }

    private static boolean isPathClear(Mob looker, LivingEntity target, Vec3 start, Vec3 end, boolean ignoreEntityBlockers) {
        Level level = looker.level();
        if (!OptimizedLOS.hasLineOfSight(level, start, end, looker)) {
            return false; 
        }
        if (ignoreEntityBlockers) {
            return true;
        }
        return !isViewBlockedByEntity(level, looker, target, start, end);
    }

    private static boolean hasActiveXrayOnTarget(Mob looker, LivingEntity target) {
        if (looker == null || target == null || !SoundAttractConfig.COMMON.enableXrayTargeting.get()) {
            return false;
        }
        double xrayRange = getEffectiveXrayRange(looker);
        if (xrayRange <= 0d) {
            return false;
        }
        double distSq = looker.distanceToSqr(target);
        return distSq <= xrayRange * xrayRange;
    }

    private static boolean isViewBlockedByEntity(Level level, Mob looker, LivingEntity target, Vec3 start, Vec3 end) {
        Vec3 viewVector = end.subtract(start);
        double dist = viewVector.length();
        if (dist < 1.0e-7) return false;

        AABB searchBox = looker.getBoundingBox().expandTowards(viewVector).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
            looker,
            start,
            end,
            searchBox,
            (e) -> e instanceof LivingEntity && !e.isSpectator() && e != target && e.isPickable(),
            dist * dist
        );

        if (hit != null) {
             if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.STEALTH_DETECTION)
                     && FRIENDLY_FIRE_LOG_CACHE.asMap().putIfAbsent(
                             looker.getUUID() + ":" + target.getUUID() + ":" + hit.getEntity().getUUID(), Boolean.TRUE) == null) {
                 Entity blocker = hit.getEntity();
                 SoundAttractMod.LOGGER.info("[FriendlyFire] Path from {} to {} blocked by {}",
                     looker.getName().getString(), target.getName().getString(), blocker.getName().getString());
             }
             return true;
        }
        return false;
    }
}
