package com.example.soundattract.event;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.PlayerStance;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.util.CamoUtil;
import com.example.soundattract.util.EnvironmentalCamoColor;
import com.example.soundattract.enchantment.ModEnchantments;
import com.example.soundattract.ai.MobGroupManager;
import com.example.soundattract.ai.RaidManager;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import com.example.soundattract.integration.enhancedai.EnhancedAICompat;
import com.example.soundattract.quantified.QuantifiedCacheCompat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.Vec3;
import com.example.soundattract.los.OptimizedLOS; 
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import com.example.soundattract.camo.CamouflageCapability;
import com.example.soundattract.camo.CamoMaterialRegistry;
import com.example.soundattract.config.separate.StealthConfig;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.TimeUnit;

public class StealthDetectionEvents {

    private static Map<UUID, Integer> mobOutOfRangeTicks = new ConcurrentHashMap<>();
    private static Map<UUID, net.minecraft.world.phys.Vec3> lastPlayerPositions = new ConcurrentHashMap<>();
    private static Map<java.util.UUID, net.minecraft.world.phys.Vec3> lastMobPositions = new ConcurrentHashMap<>();
    private static long lastStealthCheckTick = -1;
    private static Map<UUID, GunshotInfo> playerGunshotInfo = new ConcurrentHashMap<>();

    private static Map<UUID, Double> XRAY_RANGE_CACHE = new ConcurrentHashMap<>();

    public static void reinitializeCaches() {
        int max = SoundAttractConfig.COMMON.globalCacheMaxSize.get();
        int mins = SoundAttractConfig.COMMON.globalCacheExpireMins.get();

        Map<UUID, Integer> oldObj1 = mobOutOfRangeTicks;
        mobOutOfRangeTicks = CacheBuilder.newBuilder().expireAfterWrite(mins, TimeUnit.MINUTES).maximumSize(max).concurrencyLevel(4).<UUID, Integer>build().asMap();
        mobOutOfRangeTicks.putAll(oldObj1);

        Map<UUID, net.minecraft.world.phys.Vec3> oldObj2 = lastPlayerPositions;
        lastPlayerPositions = CacheBuilder.newBuilder().expireAfterWrite(mins, TimeUnit.MINUTES).maximumSize(max).concurrencyLevel(4).<UUID, net.minecraft.world.phys.Vec3>build().asMap();
        lastPlayerPositions.putAll(oldObj2);

        Map<java.util.UUID, net.minecraft.world.phys.Vec3> oldObj3 = lastMobPositions;
        lastMobPositions = CacheBuilder.newBuilder().expireAfterWrite(mins, TimeUnit.MINUTES).maximumSize(max).concurrencyLevel(4).<java.util.UUID, net.minecraft.world.phys.Vec3>build().asMap();
        lastMobPositions.putAll(oldObj3);

        Map<UUID, GunshotInfo> oldObj4 = playerGunshotInfo;
        playerGunshotInfo = CacheBuilder.newBuilder().expireAfterWrite(mins, TimeUnit.MINUTES).maximumSize(max).concurrencyLevel(4).<UUID, GunshotInfo>build().asMap();
        playerGunshotInfo.putAll(oldObj4);

        Map<UUID, Double> oldObj5 = XRAY_RANGE_CACHE;
        XRAY_RANGE_CACHE = CacheBuilder.newBuilder().expireAfterWrite(mins, TimeUnit.MINUTES).maximumSize(max).concurrencyLevel(4).<UUID, Double>build().asMap();
        XRAY_RANGE_CACHE.putAll(oldObj5);
    }

    private static final Set<UUID> suppressedEdgeDetections = ConcurrentHashMap.newKeySet();

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
            float avgMs = server.getAverageTickTimeNanos() / 1_000_000.0f;
            if (Float.isNaN(avgMs) || avgMs <= 0.0f) {
                return;
            }
            double tps = 1000.0 / Math.max(1.0, (double) avgMs);
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
        StealthPerfTier tier = getPerfTier(level);
        return tier != StealthPerfTier.FULL;
    }

    private static boolean shouldUseVanillaTargeting(Level level) {
        return getPerfTier(level) == StealthPerfTier.VANILLA;
    }

    private static boolean shouldInterceptNewTargeting(Level level) {
        StealthPerfTier tier = getPerfTier(level);
        return tier == StealthPerfTier.FULL || tier == StealthPerfTier.SKIP_EXPENSIVE;
    }

    private static LivingEntity getAttackTargetCompat(Mob mob) {
        if (mob == null) return null;
        LivingEntity direct = mob.getTarget();
        if (direct != null) return direct;
        if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            return mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        }
        return null;
    }

    private static boolean isTargetingPlayerCompat(Mob mob) {
        return getAttackTargetCompat(mob) instanceof Player;
    }

    public static void recordSuppressedEdgeDetection(Mob mob) {
        if (mob != null) suppressedEdgeDetections.add(mob.getUUID());
    }

    public static boolean consumeSuppressedEdgeDetection(Mob mob) {
        if (mob == null) return false;
        UUID id = mob.getUUID();
        if (suppressedEdgeDetections.remove(id)) {
            return true;
        }
        return false;
    }

    private static int getStealthCheckInterval() {
        return SoundAttractConfig.COMMON.stealthCheckInterval.get();
    }
    private static boolean hasConcealmentEnchant(ItemStack stack) {
        if (stack.isEmpty() || !stack.isEnchanted()) return false;
        return stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet().stream()
                .anyMatch(entry -> entry.getIntValue() > 0 && entry.getKey().unwrapKey()
                        .map(key -> key.equals(ModEnchantments.CONCEAL)).orElse(false));
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
        if (com.example.soundattract.ai.MobGroupManager.isMobInHighWeightOverride(looker)) {
            return 0.0;
        }

        if (!SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return SoundAttractConfig.COMMON.maxStealthDetectionRange.get();
        }

        if (shouldUseVanillaTargeting(level)) {
            return SoundAttractConfig.COMMON.maxStealthDetectionRange.get();
        }

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
            for (net.minecraft.world.item.ItemStack armor : com.example.soundattract.util.ArmorSlots.get(target)) {
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
            java.util.Optional<Integer> envColorOpt = EnvironmentalCamoColor.getEnvironmentalColor(
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
                com.example.soundattract.util.ArmorSlots.get(target).forEach(armorItemsList::add);
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
            Identifier applyTagLoc = Identifier.tryParse(applyTagStr);
            if (applyTagLoc == null) return 0d;
            TagKey<EntityType<?>> applyTag = TagKey.create(Registries.ENTITY_TYPE, applyTagLoc);
            boolean inApplyTag = mob.getType().is(applyTag);
            if (!inApplyTag) {
                return 0d;
            }

            if (SoundAttractConfig.COMMON.xrayRequireBetterNearby.get()) {
                String betterTagStr = SoundAttractConfig.COMMON.xrayBetterNearbyTag.get();
                if (betterTagStr == null || betterTagStr.isBlank()) {
                    return 0d;
                }
                Identifier betterTagLoc = Identifier.tryParse(betterTagStr);
                if (betterTagLoc == null) return 0d;
                TagKey<EntityType<?>> betterTag = TagKey.create(Registries.ENTITY_TYPE, betterTagLoc);
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

    public static boolean shouldAllowTargetChange(Mob mob, LivingEntity newTarget) {
        if (mob == null) {
            return true;
        }

        if (newTarget != null && com.example.soundattract.ai.MobGroupManager.isMobInHighWeightOverride(mob)) {
            return false;
        }

        if (SoundAttractConfig.isStealthBypassed(mob)) {
            return true;
        }

        if (!SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return true;
        }

        if (!shouldInterceptNewTargeting(mob.level())) {
            return true;
        }

        if (newTarget instanceof Player playerTarget) {
            if (playerTarget.isCreative() || playerTarget.isSpectator() || !playerTarget.isAlive()) {
                return true;
            }

            if (!canMobDetectLivingEntity(mob, playerTarget)) {
                if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
                    LivingEntity mem = mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
                    if (mem == playerTarget) {
                        mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                    }
                }
                return false;
            }
        } else if (newTarget instanceof Mob targetMob) {
            if (!FovEvents.isTargetInFov(mob, targetMob, true)) {
                double xrayRange = getEffectiveXrayRange(mob);
                if (xrayRange > 0) {
                    double distSqXray = mob.distanceToSqr(targetMob);
                    if (distSqXray > xrayRange * xrayRange) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
            double range = getRealisticStealthDetectionRange(targetMob, mob, mob.level());
            double distSq = mob.distanceToSqr(targetMob);
            if (distSq > range * range) {
                return false;
            }
        }

        if (true) {
            boolean friendlyFireCheckEnabled = true; 
            try {
            } catch (Throwable t) {}

            if (friendlyFireCheckEnabled && newTarget != null) {
                Vec3 start = mob.getEyePosition();
                Vec3 targetEye = newTarget.getEyePosition();
                Vec3 targetCenter = newTarget.position().add(0, newTarget.getBbHeight() * 0.5, 0);
                Vec3 targetFeet = newTarget.position().add(0, Math.max(0.1, newTarget.getBbHeight() * 0.15), 0);

                boolean ignoreEntityBlockers = hasActiveXrayOnTarget(mob, newTarget);
                boolean eyeClear = isPathClear(mob, newTarget, start, targetEye, ignoreEntityBlockers);
                boolean centerClear = isPathClear(mob, newTarget, start, targetCenter, ignoreEntityBlockers);
                boolean feetClear = isPathClear(mob, newTarget, start, targetFeet, ignoreEntityBlockers);

                if (!eyeClear && !centerClear && !feetClear) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        Soundattract.LOGGER.debug(
                            "[LivingChangeTargetEvent] Mob {} targeting of {} CANCELED. All line of sights blocked by entities/walls.",
                            mob.getName().getString(), newTarget.getName().getString()
                        );
                    }
                    return false;
                }
            }
        }
        return true;
    }


    public static boolean canMobDetectLivingEntity(Mob mob, LivingEntity target) {
        if (mob == null || target == null) {
            return true;
        }

        if (com.example.soundattract.ai.MobGroupManager.isMobInHighWeightOverride(mob)) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info("[CanDetect] Mob {} is blind due to high-weight sound override.", mob.getName().getString());
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
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[CanDetect] Player {} is creative/spectator/dead. Bypassing stealth. Mob {}.", player.getName().getString(), mob.getName().getString());
                }
                return true;
            }
        } else if (!target.isAlive()) {
            return true;
        }

        if (!SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return true;
        }

        if (SoundAttractConfig.COMMON.edgeMobSmartBehavior.get()) {
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
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info(
                        "[XRAY] Mob {} detects {} within XRAY range {} (distSq {}).",
                        mob.getName().getString(), target.getName().getString(), String.format("%.2f", xrayRange), String.format("%.2f", distSqXray)
                    );
                }
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
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info(
                        "[StealthQuery] {} is OUT OF RANGE for mob {} (distSq: {}, rangeSq: {}).",
                        target.getName().getString(), mob.getName().getString(),
                        String.format("%.2f", distSq),
                        String.format("%.2f", (detectionRange * detectionRange))
                );
            }
            return false;
        }

        if (!FovEvents.isTargetInFov(mob, target, true)) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info(
                        "[StealthQuery] {} is IN RANGE for mob {} but OUTSIDE FOV. Denying detection.",
                        target.getName().getString(), mob.getName().getString()
                );
            }
            return false;
        }

        if (SoundAttractConfig.COMMON.edgeMobSmartBehavior.get()) {
            try {
                boolean isEdge = MobGroupManager.isEdgeMob(mob);
                boolean isDeserter = MobGroupManager.isDeserter(mob);
                if (isEdge && !isDeserter) {
                    recordSuppressedEdgeDetection(mob);
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        Soundattract.LOGGER.info("[CanDetect] Suppressing EDGE mob {} (non-deserter) despite detectability; signaling RAID.", mob.getName().getString());
                    }
                    return false;
                }
            } catch (Throwable t) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.warn("[CanDetect] Edge suppression check failed: {}", t.getMessage());
                }
            }
        }
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info(
                    "[StealthQuery] {} IS DETECTABLE by mob {} (in range and in FOV).",
                    target.getName().getString(), mob.getName().getString()
            );
        }
        return true;
    }

    @Deprecated
    public static boolean canMobDetectPlayer(Mob mob, Player player) {
        return canMobDetectLivingEntity(mob, player);
    }

    /**
     * Checks awareness visibility against the exact range already computed for the current snapshot.
     * Keeping this separate from the general targeting query prevents range-dependent state from being
     * recalculated with a different result while an alert snapshot is being assembled.
     */
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
                Mob leader = com.example.soundattract.ai.MobGroupManager.getLeader(mob);
                if (leader != null && leader != mob) {
                    boolean isEdge = com.example.soundattract.ai.MobGroupManager.isEdgeMob(mob);
                    boolean isDeserter = com.example.soundattract.ai.MobGroupManager.isDeserter(mob);
                    if (!isEdge && !isDeserter) {
                        return false;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        double xrayRange = getEffectiveXrayRange(mob);
        if (xrayRange > 0.0 && mob.distanceToSqr(player) <= xrayRange * xrayRange) {
            return true;
        }
        if (mob.distanceToSqr(player) > detectionRange * detectionRange) {
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

        if (SoundAttractConfig.COMMON.edgeMobSmartBehavior.get()) {
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
        return !canMobDetectLivingEntity(mob, target);
    }

    public static void onServerTick(MinecraftServer server) {
        if (!SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return;
        }

        long gameTime = server.overworld().getGameTime();
        if (gameTime != lastEstimatedTpsGameTime) {
            lastEstimatedTpsGameTime = gameTime;
            updateEstimatedTps(server);
        }

        StealthPerfTier tier = getPerfTier(server);
        if (tier == StealthPerfTier.VANILLA) {
            return;
        }
        int stealthCheckInterval = getStealthCheckInterval();

        if (gameTime % stealthCheckInterval != 0 || gameTime == lastStealthCheckTick) {
            return;
        }
        lastStealthCheckTick = gameTime;

        for (ServerLevel level : server.getAllLevels()) {
            java.util.Map<Long, Boolean> sharedStealthCache = (tier == StealthPerfTier.SHARE_NEARBY
                    && SoundAttractConfig.COMMON.stealthShareTargetToNearbyMobsRadius.get() > 0.0)
                    ? new java.util.HashMap<>()
                    : null;

            int scanningRadius = Math.max(32, (int) Math.ceil(SoundAttractConfig.COMMON.maxStealthDetectionRange.get()) + 16);
            Set<Mob> mobsToCheck = new HashSet<>();
            Set<LivingEntity> entitiesToTickCamo = new HashSet<>();
            Set<UUID> activelyTargetedEntityIds = new HashSet<>();
            Set<UUID> seenPlayerIds = new HashSet<>();
            Set<UUID> seenMobIds = new HashSet<>();

            for (net.minecraft.server.level.ServerPlayer serverPlayer : level.players()) {
                seenPlayerIds.add(serverPlayer.getUUID());
                AABB scanArea = serverPlayer.getBoundingBox().inflate(scanningRadius);
                
                mobsToCheck.addAll(level.getEntitiesOfClass(Mob.class, scanArea, entity -> entity.isAlive() && getAttackTargetCompat(entity) != null));
                
                entitiesToTickCamo.addAll(level.getEntitiesOfClass(LivingEntity.class, scanArea, e -> e.isAlive() && !(e instanceof Player)));
            }

            for (LivingEntity le : entitiesToTickCamo) {
                CamouflageCapability.getCapability(le).ifPresent(camo -> {
                    camo.tickDegradation(le, level, tier);
                });
            }

            for (Mob mob : mobsToCheck) {
                seenMobIds.add(mob.getUUID());
                LivingEntity target = getAttackTargetCompat(mob);
                UUID mobId = mob.getUUID();
                
                if (target == null) {
                    mobOutOfRangeTicks.remove(mobId);
                    continue;
                }
                
                UUID targetId = target.getUUID();
                activelyTargetedEntityIds.add(targetId);

                if (target instanceof Player playerTarget && (playerTarget.isCreative() || playerTarget.isSpectator())) {
                    mobOutOfRangeTicks.remove(mobId);
                    continue;
                }

                boolean canCurrentlyDetect;

                boolean raidFollowerSkipped = false;
                try {
                    if (SoundAttractConfig.COMMON.raidFollowerInheritTarget.get()) {
                        Mob leader = MobGroupManager.getLeader(mob);
                        if (leader != null && RaidManager.isRaidAdvancing(leader)) {
                            boolean isEdge = MobGroupManager.isEdgeMob(mob);
                            boolean isLeader = (mob == leader);
                            if (!isEdge && !isLeader) {
                                raidFollowerSkipped = true;
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                if (raidFollowerSkipped) {
                    canCurrentlyDetect = false;
                } else
                if (sharedStealthCache != null) {
                    double radius = SoundAttractConfig.COMMON.stealthShareTargetToNearbyMobsRadius.get();
                    int cell = (int) Math.max(1, Math.floor(radius));
                    int cx = (int) Math.floor(mob.getX() / (double) cell);
                    int cz = (int) Math.floor(mob.getZ() / (double) cell);
                    long tHash = targetId.getMostSignificantBits() ^ targetId.getLeastSignificantBits();
                    long key = tHash;
                    key = 31L * key + (long) cx;
                    key = 31L * key + (long) cz;

                    Boolean cached = sharedStealthCache.get(key);
                    if (cached == null) {
                        cached = canMobDetectTargetNoEdgeSuppression(mob, target);
                        sharedStealthCache.put(key, cached);
                    }
                    canCurrentlyDetect = cached.booleanValue();

                    if (SoundAttractConfig.COMMON.edgeMobSmartBehavior.get()) {
                        try {
                            boolean isEdge = MobGroupManager.isEdgeMob(mob);
                            boolean isDeserter = MobGroupManager.isDeserter(mob);
                            if (isEdge && !isDeserter) {
                                Mob edgeLeader = MobGroupManager.getLeader(mob);
                                if (edgeLeader != null && RaidManager.isRaidAdvancing(edgeLeader) && canCurrentlyDetect && target != null) {
                                    try {
                                        String action = SoundAttractConfig.COMMON.raidEdgeDetectionAction.get();
                                        if ("redirect".equalsIgnoreCase(action)) {
                                            RaidManager.updateRaidTarget(edgeLeader, target.blockPosition());
                                        } else if ("share".equalsIgnoreCase(action)) {
                                            for (Mob nearby : mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(16.0), m -> m.isAlive())) {
                                                if (MobGroupManager.getLeader(nearby) == edgeLeader && nearby != mob) {
                                                    nearby.setTarget(target);
                                                }
                                            }
                                        }
                                    } catch (Throwable ignored) {}
                                }
                                recordSuppressedEdgeDetection(mob);
                                canCurrentlyDetect = false;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                } else {
                    canCurrentlyDetect = canMobDetectLivingEntity(mob, target);
                }

                if (canCurrentlyDetect) {
                    if (mobOutOfRangeTicks.remove(mobId) != null) {
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            Soundattract.LOGGER.info(
                                    "[TickCheck] Mob {} regained direct detection of {}. Grace period reset.",
                                    mob.getName().getString(), target.getName().getString()
                            );
                        }
                    }
                } else {
                    int ticks = mobOutOfRangeTicks.getOrDefault(mobId, 0) + stealthCheckInterval;
                    if (ticks >= SoundAttractConfig.COMMON.stealthGracePeriodTicks.get()) {
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            Soundattract.LOGGER.info(
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
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            Soundattract.LOGGER.info(
                                    "[TickCheck] Mob {} cannot detect {}. In grace period ({}/{}).",
                                    mob.getName().getString(), target.getName().getString(),
                                    ticks, SoundAttractConfig.COMMON.stealthGracePeriodTicks.get()
                            );
                        }
                    }
                }
            }

            if (gameTime % Math.max(stealthCheckInterval * 5L, 20L) == 0) {
                mobOutOfRangeTicks.keySet().removeIf(id -> !seenMobIds.contains(id));
                XRAY_RANGE_CACHE.keySet().removeIf(id -> !seenMobIds.contains(id));
                lastMobPositions.keySet().removeIf(id -> !seenMobIds.contains(id));
                suppressedEdgeDetections.removeIf(id -> !seenMobIds.contains(id));
                lastPlayerPositions.keySet().removeIf(id -> !seenPlayerIds.contains(id));
                playerGunshotInfo.keySet().removeIf(id -> !seenPlayerIds.contains(id));
            }
        }
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

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info(
                    "[DetermineStanceDetails] Player: {}, Pose: {}, isVisuallyCrawling: {}, isCrouching: {}, Height: {}",
                    player.getName().getString(),
                    currentPose,
                    isVisuallyCrawling,
                    isCrouching,
                    String.format("%.2f", playerHeight)
            );
        }

        if (isVisuallyCrawling || playerHeight <= 1.0F) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info("[DetermineStance] Player {} is CRAWLING (VisualCrawl: true, Pose: {}, Height: {})",
                        player.getName().getString(), currentPose, String.format("%.2f", playerHeight));
            }
            return PlayerStance.CRAWLING;
        }

        if (isCrouching) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info("[DetermineStance] Player {} is SNEAKING (Pose: {}, Crouching: {}, Height: {})",
                        player.getName().getString(), currentPose, isCrouching, String.format("%.2f", playerHeight));
            }
            return PlayerStance.SNEAKING;
        }

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info("[DetermineStance] Player {} is STANDING (Pose: {}, Height: {})",
                    player.getName().getString(), currentPose, String.format("%.2f", playerHeight));
        }
        return PlayerStance.STANDING;
    }

    public static double getRealisticStealthDetectionRange(Player player, Mob mob, Level level) {
        if (com.example.soundattract.ai.MobGroupManager.isMobInHighWeightOverride(mob)) {
            return 0.0;
        }

        if (!SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return SoundAttractConfig.COMMON.maxStealthDetectionRange.get();
        }

        if (shouldUseVanillaTargeting(level)) {
            return SoundAttractConfig.COMMON.maxStealthDetectionRange.get();
        }

        boolean skipExpensive = shouldSkipExpensiveChecks(level);
        double baseRange;
        Optional<Double> gunshotRangeOpt = getActiveGunshotRange(player);
        PlayerStance currentStance = determinePlayerStance(player);
        if (gunshotRangeOpt.isPresent()) {
            baseRange = gunshotRangeOpt.get();
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info(
                "[GRSDR_Update] Player {} has active gunshot flash. Initial range set to {}.",
                player.getName().getString(), String.format("%.2f", baseRange)
                );
            }
            double standingRange = SoundAttractConfig.COMMON.standingDetectionRangePlayer.get();
            double currentPoseBaseRange;
            switch (currentStance) {
                case CRAWLING:
                    currentPoseBaseRange = SoundAttractConfig.COMMON.crawlingDetectionRangePlayer.get();
                    break;
                case SNEAKING:
                    currentPoseBaseRange = SoundAttractConfig.COMMON.sneakingDetectionRangePlayer.get();
                    break;
                default:
                    currentPoseBaseRange = standingRange;
                    break;
            }
            double poseReduction = Math.max(0, standingRange - currentPoseBaseRange); 
            baseRange -= poseReduction;
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info(
                "[GRSDR_Update] Gunshot range adjusted by pose {}. Reduction of {}. New range: {}.",
                currentStance, String.format("%.2f", poseReduction), String.format("%.2f", baseRange)
                );
            }
        } else {
            com.example.soundattract.config.MobProfile2 mobProfile = SoundAttractConfig.getMatchingProfile(mob);
            Optional<Double> override = (mobProfile != null) ? mobProfile.getDetectionOverride(currentStance) : Optional.empty();
            if (override.isPresent()) {
                baseRange = override.get();
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info(
                    "[GRSDR_Update] Mob {} using profile '{}' detection range for stance {}: {}",
                    mob.getName().getString(), mobProfile.id(), currentStance, baseRange
                    );
                }
            } else {
                com.example.soundattract.config.PlayerProfile2 playerProfile = SoundAttractConfig.getMatchingPlayerProfile(player);
                Optional<Double> playerOverride = (playerProfile != null) ? playerProfile.getDetectionOverride(currentStance) : Optional.empty();
                if (playerOverride.isPresent()) {
                    baseRange = playerOverride.get();
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        Soundattract.LOGGER.info(
                                "[GRSDR_Update] Player {} matched player profile '{}' for stance {}: {}",
                                player.getName().getString(), playerProfile.id(), currentStance, baseRange
                        );
                    }
                } else {
                    switch (currentStance) {
                        case CRAWLING:
                            baseRange = SoundAttractConfig.COMMON.crawlingDetectionRangePlayer.get();
                            break;
                        case SNEAKING:
                            baseRange = SoundAttractConfig.COMMON.sneakingDetectionRangePlayer.get();
                            break;
                        case STANDING:
                        default:
                            baseRange = SoundAttractConfig.COMMON.standingDetectionRangePlayer.get();
                            break;
                    }
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        if (mobProfile != null) {
                            Soundattract.LOGGER.info(
                                    "[GRSDR_Update] Mob {} profile '{}' has no override for stance {}. No matching player profile override. Using default: {}",
                                    mob.getName().getString(), mobProfile.id(), currentStance, baseRange
                            );
                        } else {
                            Soundattract.LOGGER.info(
                                    "[GRSDR_Update] No mob profile override and no player profile override for Mob {}. Using default for stance {}: {}",
                                    mob.getName().getString(), currentStance, baseRange
                            );
                        }
                    }
                }
            }
        }
        if (player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
            double invisFactor = SoundAttractConfig.COMMON.invisibilityStealthFactor.get();
            baseRange *= invisFactor;
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info(
                        "[GRSDR_Update] Player {} is invisible, reducing baseRange to {}",
                        player.getName().getString(), String.format("%.2f", baseRange)
                );
            }
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
                    int emit = def.getLightEmission();
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
        baseRange *= lightFactor;
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info("[GRSDR_Update] Light - EffectiveLight: {}, LightFactor (clamped): {}, baseRange: {}",
                    effectiveLight, String.format("%.2f", lightFactor), String.format("%.2f", baseRange));
        }
        if (SoundAttractConfig.COMMON.enableHeldItemPenalty.get()) {
            int heldItemCount = 0;
            if (!player.getMainHandItem().isEmpty()) heldItemCount++;
            if (!player.getOffhandItem().isEmpty()) heldItemCount++;
            if (heldItemCount > 0) {
                double penaltyPerItem = SoundAttractConfig.COMMON.heldItemPenaltyFactor.get();
                for (int i = 0; i < heldItemCount; i++) {
                    baseRange *= penaltyPerItem;
                }
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[GRSDR_Update] Held Item Penalty: {} items, factor {:.2f} (applied {} times) -> {:.2f}",
                            heldItemCount, penaltyPerItem, heldItemCount, baseRange);
                }
            }
        }
        if (SoundAttractConfig.COMMON.enableEnchantmentPenalty.get()) {
            int visiblyEnchantedArmorPieces = 0;
            for (ItemStack armorStack : com.example.soundattract.util.ArmorSlots.get(player)) {
                if (!armorStack.isEmpty() && armorStack.isEnchanted() && !hasConcealmentEnchant(armorStack)) {
                    visiblyEnchantedArmorPieces++;
                }
            }
            if (visiblyEnchantedArmorPieces > 0) {
                double armorPenaltyFactor = SoundAttractConfig.COMMON.armorEnchantmentPenaltyFactor.get();
                for (int i = 0; i < visiblyEnchantedArmorPieces; i++) {
                    baseRange *= armorPenaltyFactor;
                }
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[GRSDR_Update] Armor Enchant Penalty: {} pieces, factor {:.2f} (applied {} times) -> {:.2f}",
                            visiblyEnchantedArmorPieces, armorPenaltyFactor, visiblyEnchantedArmorPieces, baseRange);
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
                    baseRange *= heldItemEnchantPenalty;
                }
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[GRSDR_Update] Held Item Enchant Penalty: {} items, factor {:.2f} (applied {} times) -> {:.2f}",
                            visiblyEnchantedHeldItems, heldItemEnchantPenalty, visiblyEnchantedHeldItems, baseRange);
                }
            }
        }
        List<String> camouflageItems = new ArrayList<>(SoundAttractConfig.COMMON.camouflageArmorItems.get());


        if (SoundAttractConfig.COMMON.enableEnvironmentalCamouflage.get()) {
            int finalColor = CamoUtil.getFinalPerceptionColor(player);
            java.util.Optional<Integer> envColorOpt = EnvironmentalCamoColor.getEnvironmentalColor(
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

                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        Soundattract.LOGGER.info(
                                "[EnvCamo_Player] {} BONUS: finalColor=0x{}, envColor=0x{}, diff={}, strength={}, effect={}, newRange={}",
                                player.getName().getString(), String.format("%06X", finalColor), String.format("%06X", envColor),
                                diff, String.format("%.2f", strength), String.format("%.2f", actualBonus), String.format("%.2f", baseRange)
                        );
                    }
                } else if (SoundAttractConfig.COMMON.enableEnvironmentalMismatchPenalty.get()) {
                    int mismatchThreshold = SoundAttractConfig.COMMON.environmentalMismatchThreshold.get();
                    if (diff > mismatchThreshold) {
                        double penalty = SoundAttractConfig.COMMON.environmentalMismatchPenaltyFactor.get();
                        baseRange *= penalty;
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            Soundattract.LOGGER.info("[EnvCamo_Player] {} PENALTY: diff={}, penalty={}, newRange={}",
                                    player.getName().getString(), diff, String.format("%.2f", penalty), String.format("%.2f", baseRange));
                        }
                    }
                }
            }
        }

        if (!skipExpensive) {
            if (level.isRainingAt(player.blockPosition())) {
                baseRange *= SoundAttractConfig.COMMON.rainStealthFactor.get();
                 if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[GRSDR_Update] Raining. Factor applied. baseRange: {}", String.format("%.2f", baseRange));
                }
            }
            if (level.isThundering()) {
                baseRange *= SoundAttractConfig.COMMON.thunderStealthFactor.get();
                 if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[GRSDR_Update] Thundering. Factor applied. baseRange: {}", String.format("%.2f", baseRange));
                }
            }
        }

        if (currentStance != PlayerStance.SNEAKING && currentStance != PlayerStance.CRAWLING) {
            if (isPlayerMoving(player, SoundAttractConfig.COMMON.movementThreshold.get())) {
                baseRange *= SoundAttractConfig.COMMON.movementStealthPenalty.get();
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[GRSDR_Update] Player moving (not sneak/crawl). Penalty applied. baseRange: {}", String.format("%.2f", baseRange));
                }
            } else {
                baseRange *= SoundAttractConfig.COMMON.stationaryStealthBonusFactor.get();
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[GRSDR_Update] Player stationary (not sneak/crawl). Bonus applied. baseRange: {}", String.format("%.2f", baseRange));
                }
            }
        } else {
             if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info("[GRSDR_Update] Player sneaking/crawling. Movement penalty/bonus not applied here (handled by stance base range). baseRange: {}", String.format("%.2f", baseRange));
            }
        }

        if (SoundAttractConfig.COMMON.enableCamouflage.get()) {
            if (!camouflageItems.isEmpty()) {
                double effectToApply = 0.0;
                int totalActualArmorPieces = 0;
                long wornListedCamouflagePieces = 0;
                for (ItemStack armorStack : com.example.soundattract.util.ArmorSlots.get(player)) {
                    if (!armorStack.isEmpty()) {
                        totalActualArmorPieces++;
                        Identifier itemId = BuiltInRegistries.ITEM.getKey(armorStack.getItem());
                        if (itemId != null && camouflageItems.contains(itemId.toString())) {
                            wornListedCamouflagePieces++;
                        }
                    }
                }
                boolean isActuallyWearingFullSetOfListedItems = (totalActualArmorPieces == 4 && wornListedCamouflagePieces == totalActualArmorPieces && totalActualArmorPieces > 0);

                if (SoundAttractConfig.COMMON.requireFullSetForCamouflageBonus.get()) {
                    if (isActuallyWearingFullSetOfListedItems) {
                        effectToApply = SoundAttractConfig.COMMON.fullArmorStealthBonus.get();
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            Soundattract.LOGGER.info(
                                    "[GRSDR_Update ItemCamo] Player {} wearing full set of listed items (requireFullSet=true). Applying fullArmorStealthBonus: {}",
                                    player.getName().getString(), effectToApply
                            );
                        }
                    } else {
                        double totalEffectiveness = 0.0;
                        List<ItemStack> armorItemsList = new ArrayList<>();
                        com.example.soundattract.util.ArmorSlots.get(player).forEach(armorItemsList::add);
                        for (int i = 0; i < armorItemsList.size(); i++) {
                            ItemStack stack = armorItemsList.get(i);
                            if (stack.isEmpty()) continue;
                            Item item = stack.getItem();
                            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
                            if (itemId != null && camouflageItems.contains(itemId.toString())) {
                                switch (i) {
                                    case 0: totalEffectiveness += SoundAttractConfig.COMMON.bootsCamouflageEffectiveness.get(); break;
                                    case 1: totalEffectiveness += SoundAttractConfig.COMMON.leggingsCamouflageEffectiveness.get(); break;
                                    case 2: totalEffectiveness += SoundAttractConfig.COMMON.chestplateCamouflageEffectiveness.get(); break;
                                    case 3: totalEffectiveness += SoundAttractConfig.COMMON.helmetCamouflageEffectiveness.get(); break;
                                }
                            }
                        }
                        effectToApply = totalEffectiveness;
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                             Soundattract.LOGGER.info(
                                    "[GRSDR_Update ItemCamo] Player {} wearing partial listed camo (requireFullSet=true). Applying summed per-piece effectiveness: {}",
                                    player.getName().getString(), effectToApply
                            );
                        }
                    }
                } else {
                    if (isActuallyWearingFullSetOfListedItems && SoundAttractConfig.COMMON.fullArmorStealthBonus.get() > 0) {
                        effectToApply = SoundAttractConfig.COMMON.fullArmorStealthBonus.get();
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            Soundattract.LOGGER.info(
                                    "[GRSDR_Update ItemCamo] Player {} wearing full set of listed items (requireFullSet=false, using full bonus). Applying fullArmorStealthBonus: {}",
                                    player.getName().getString(), effectToApply
                            );
                        }
                    } else {
                        double totalEffectiveness = 0.0;
                        List<ItemStack> armorItemsList = new ArrayList<>();
                        com.example.soundattract.util.ArmorSlots.get(player).forEach(armorItemsList::add);
                        for (int i = 0; i < armorItemsList.size(); i++) {
                            ItemStack stack = armorItemsList.get(i);
                            if (stack.isEmpty()) continue;
                            Item item = stack.getItem();
                            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
                            if (itemId != null && camouflageItems.contains(itemId.toString())) {
                                switch (i) {
                                    case 0: totalEffectiveness += SoundAttractConfig.COMMON.bootsCamouflageEffectiveness.get(); break;
                                    case 1: totalEffectiveness += SoundAttractConfig.COMMON.leggingsCamouflageEffectiveness.get(); break;
                                    case 2: totalEffectiveness += SoundAttractConfig.COMMON.chestplateCamouflageEffectiveness.get(); break;
                                    case 3: totalEffectiveness += SoundAttractConfig.COMMON.helmetCamouflageEffectiveness.get(); break;
                                }
                            }
                        }
                        effectToApply = totalEffectiveness;
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            Soundattract.LOGGER.info(
                                    "[GRSDR_Update ItemCamo] Player {} (requireFullSet=false). Applying summed per-piece effectiveness: {}",
                                    player.getName().getString(), effectToApply
                            );
                        }
                    }
                }

                if (effectToApply > 0.0) {
                    double itemCamoMultiplier = 1.0 - Math.min(effectToApply, 0.99);
                    baseRange *= itemCamoMultiplier;
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        Soundattract.LOGGER.info(
                                "[GRSDR_Update ItemCamo] Player {} range after general item camouflage: {}. Applied multiplier: {} (Effect: {})",
                                player.getName().getString(), String.format("%.2f", baseRange),
                                String.format("%.2f", itemCamoMultiplier), String.format("%.2f", effectToApply)
                        );
                    }
                }

                double finalCalculatedRange = Math.max(SoundAttractConfig.COMMON.minStealthDetectionRange.get(), Math.min(baseRange, SoundAttractConfig.COMMON.maxStealthDetectionRange.get()));
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info(
                            "[GRSDR_End] Mob: {}, Player: {}, Final Calculated Range (camo items processed): {}",
                            mob.getName().getString(), player.getName().getString(), String.format("%.2f", finalCalculatedRange)
                    );
                }
                return finalCalculatedRange;
            } else {
                double finalCalculatedRange = Math.max(SoundAttractConfig.COMMON.minStealthDetectionRange.get(), Math.min(baseRange, SoundAttractConfig.COMMON.maxStealthDetectionRange.get()));
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info(
                            "[GRSDR_End] Mob: {}, Player: {}, Item Camo enabled but no items configured. Final Range: {}",
                            mob.getName().getString(), player.getName().getString(), String.format("%.2f", finalCalculatedRange)
                    );
                }
                return finalCalculatedRange;
            }
        } else {
            double finalCalculatedRange = Math.max(SoundAttractConfig.COMMON.minStealthDetectionRange.get(), Math.min(baseRange, SoundAttractConfig.COMMON.maxStealthDetectionRange.get()));
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info(
                        "[GRSDR_End] Mob: {}, Player: {}, Item Camo system disabled. Final Range: {}",
                        mob.getName().getString(), player.getName().getString(), String.format("%.2f", finalCalculatedRange)
                );
            }
            return finalCalculatedRange;
        }
    }




    private static Optional<Integer> getAverageEnvironmentalColor(LivingEntity entity, Level level) {
        if (entity == null || level == null) {
            return Optional.empty();
        }
        if (QuantifiedCacheCompat.isUsable()) {
            BlockPos pos = entity.blockPosition();
            String key = new StringBuilder(96)
                .append(level.dimension().identifier().toString()).append('|')
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
        List<Integer> blockColors = new ArrayList<>();
        BlockPos entityPos = entity.blockPosition();

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
                            if (mapColor != 0) blockColors.add(mapColor);
                        }
                    }
                }
            }
        }

        if (blockColors.isEmpty()) {
             if (SoundAttractConfig.COMMON.debugLogging.get()) {
                 Soundattract.LOGGER.info("[GetEnvColor] No block map colors found in sampling area.");
            }
            return Optional.empty();
        }

        long totalR = 0, totalG = 0, totalB = 0;
        for (int color : blockColors) {
            totalR += (color >> 16) & 0xFF;
            totalG += (color >> 8) & 0xFF;
            totalB += color & 0xFF;
        }

        int numColors = blockColors.size();
        if (numColors == 0) {
             if (SoundAttractConfig.COMMON.debugLogging.get()) {
                 Soundattract.LOGGER.warn("[GetEnvColor] numColors is 0 after processing, this should not happen if blockColors list was not empty.");
            }
            return Optional.empty();
        }
        int avgR = (int) (totalR / numColors);
        int avgG = (int) (totalG / numColors);
        int avgB = (int) (totalB / numColors);
        int finalAvgColor = (avgR << 16) | (avgG << 8) | avgB;

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info("[GetEnvColor] Average environment color: 0x{} from {} blocks.",
                    String.format("%06X", finalAvgColor), numColors);
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
             if (SoundAttractConfig.COMMON.debugLogging.get()) {
                 Entity blocker = hit.getEntity();
                 Soundattract.LOGGER.info("[FriendlyFire] Path from {} to {} blocked by {}", 
                     looker.getName().getString(), target.getName().getString(), blocker.getName().getString());
             }
             return true;
        }
        return false;
    }
}



