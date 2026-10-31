package com.example.soundattract.event;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.ai.FollowScentGoal;
import com.example.soundattract.ai.MobGroupManager;
import com.example.soundattract.ai.RaidManager;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.config.DebugLoggingConfig;
import com.example.soundattract.integration.weather2.Weather2WindCompat;
import com.example.soundattract.integration.weather2.Weather2WindDebugLog;
import com.example.soundattract.integration.spoiled.SpoiledScentIntegration;
import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.network.AwarenessSnapshotMessage;
import com.example.soundattract.network.SoundAttractNetwork;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.camo.CamouflageCapability;
import com.example.soundattract.tracking.SoundTracker;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public final class AwarenessServerEvents {
    private static final int ALERT_PROXIMITY = 1;
    private static final int ALERT_DETECTED = 2;
    private static final int ALERT_NEAR_MISS = 3;
    private static final int ALERT_SCENT = 4;
    private static final int ALERT_RAID = 5;


    private static final Cache<UUID, AlertEntry> EXTRA_ALERTS = Caffeine.newBuilder()
            .maximumSize(8192)
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .build();
    private static final Cache<UUID, OdorEntry> ODOR_CACHE = Caffeine.newBuilder()
            .maximumSize(4096)
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .build();
    private static final Cache<UUID, Long> LAST_SCENT_DEBUG_LOG = Caffeine.newBuilder()
            .maximumSize(4096)
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .build();
    private static final Cache<UUID, WindshieldState> WINDSHIELD_STATES = Caffeine.newBuilder()
            .maximumSize(4096)
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .build();
    private static final Cache<UUID, Long> LAST_WINDSHIELD_DEBUG_LOG = Caffeine.newBuilder()
            .maximumSize(4096)
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .build();
    private static final Cache<DetectionKey, Long> LAST_DETECTED = Caffeine.newBuilder()
            .maximumSize(16384)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();
    private static volatile List<String> cachedOdorEntries = List.of();
    private static volatile Map<String, Double> cachedOdorValues = Map.of();

    private AwarenessServerEvents() {
    }

    public static void recordNearMiss(Mob mob) {
        if (mob != null && mob.level() != null) {
            long expiresAt = mob.level().getGameTime() + 30L;
            EXTRA_ALERTS.put(mob.getUUID(), new AlertEntry(ALERT_NEAR_MISS, 0xFFFFB000, 0.0f, expiresAt));
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.ARROW_INVESTIGATION)) {
                SoundAttractMod.LOGGER.info("[MobAlertDebug] Near-miss alert recorded: entityId={}, uuid={}, expiresAt={}",
                        mob.getId(), mob.getUUID(), expiresAt);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        EXTRA_ALERTS.invalidateAll();
        ODOR_CACHE.invalidateAll();
        LAST_SCENT_DEBUG_LOG.invalidateAll();
        WINDSHIELD_STATES.invalidateAll();
        LAST_WINDSHIELD_DEBUG_LOG.invalidateAll();
        LAST_DETECTED.invalidateAll();
        cachedOdorEntries = List.of();
        cachedOdorValues = Map.of();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            long gameTime = level.getGameTime();
            for (ServerPlayer player : level.players()) {
                if ((player.getId() + gameTime) % 5L == 0L) {
                    sendSnapshot(level, player);
                }
            }
        }
    }

    private static void sendSnapshot(ServerLevel level, ServerPlayer player) {
        AwarenessScan scan = scanAwareness(level, player);
        float visibility = calculateVisibility(player, scan.maximumDetectionRange());
        float noise = calculateNoise(level, player);
        int scent = calculateScent(level, player);
        boolean windshielded = isWindshielded(level, player);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new AwarenessSnapshotMessage(visibility, noise, scent, windshielded, scan.alerts()));
    }

    private static float calculateVisibility(ServerPlayer player, double maximum) {
        double configuredMaximum = SoundAttractConfig.COMMON.maxStealthDetectionRange.get();
        if (maximum <= 0.0) {
            maximum = player.isVisuallySwimming() ? SoundAttractConfig.COMMON.crawlingDetectionRangePlayer.get()
                    : player.isCrouching() ? SoundAttractConfig.COMMON.sneakingDetectionRangePlayer.get()
                    : SoundAttractConfig.COMMON.standingDetectionRangePlayer.get();
        }
        return clamp((float) (maximum / Math.max(1.0, configuredMaximum)));
    }

    private static float calculateNoise(ServerLevel level, ServerPlayer player) {
        double strongest = 0.0;
        double maxRange = Math.max(1.0, SoundAttractConfig.COMMON.maxStealthDetectionRange.get());
        BlockPos playerPos = player.blockPosition();
        for (SoundTracker.SoundRecord record : SoundTracker.getNearbySounds(level, playerPos, maxRange)) {
            if (record == null || record.dimensionKey == null || record.pos == null
                    || !record.dimensionKey.equals(level.dimension().identifier().toString())
                    || !Double.isFinite(record.range) || !Double.isFinite(record.weight)
                    || record.range <= 0.0) {
                continue;
            }
            double distance = Math.sqrt(record.pos.distSqr(playerPos));
            if (distance > record.range) {
                continue;
            }
            double distanceFactor = 1.0 - (distance / Math.max(1.0, record.range));
            double lifetimeFactor = Math.min(1.0, Math.max(0.0, record.ticksRemaining / (double) Math.max(1, SoundAttractConfig.COMMON.soundLifetimeTicks.get())));
            double intensity = Math.min(1.0, Math.max(0.0, record.weight / 10.0)) * distanceFactor * lifetimeFactor;
            strongest = Math.max(strongest, intensity);
        }
        double horizontalSpeed = player.getDeltaMovement().horizontalDistance();
        if (player.isSprinting() && horizontalSpeed > 0.01) {
            strongest = Math.max(strongest, 1.0);
        } else if (player.isCrouching() && horizontalSpeed > 0.005) {
            strongest = Math.max(strongest, 0.22);
        } else if (horizontalSpeed > 0.005) {
            strongest = Math.max(strongest, 0.58);
        }
        return clamp((float) strongest);
    }

    private static int calculateScent(ServerLevel level, ServerPlayer player) {
        double effective = getEffectivePlayerScent(level, player);
        return calculateScentLevel(effective);
    }

    private static int calculateScentLevel(double effective) {
        if (effective <= 0.0) {
            return 0;
        }
        return Math.max(1, Math.min(5, (int) Math.ceil(effective / 16.0 * 5.0)));
    }

    public static double getEffectivePlayerScent(ServerLevel level, ServerPlayer player) {
        if (level == null || player == null) {
            return 0.0;
        }
        OdorEntry cached = ODOR_CACHE.getIfPresent(player.getUUID());
        long now = level.getGameTime();
        if (cached == null || now >= cached.nextScan()) {
            double foodOdor = 0.0;
            boolean spoiledEnabled = SpoiledScentIntegration.isEnabled();
            double spoiledOdorPerItem = spoiledEnabled ? SoundAttractConfig.COMMON.spoiledFoodOdorPerItem.get() : 0.0;
            SpoiledOdorScan spoiledOdor = spoiledEnabled && spoiledOdorPerItem > 0.0 ? new SpoiledOdorScan() : null;
            Map<String, Double> values = parseOdorValues();
            for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id != null) {
                    foodOdor += stack.getCount() * values.getOrDefault(id.toString(), 0.0);
                }
                if (spoiledOdor != null) {
                    spoiledOdor.add(stack, spoiledOdorPerItem);
                }
            }
            for (ItemStack stack : com.example.soundattract.util.ArmorSlots.get(player)) {
                Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id != null) {
                    foodOdor += stack.getCount() * values.getOrDefault(id.toString(), 0.0);
                }
                if (spoiledOdor != null) {
                    spoiledOdor.add(stack, spoiledOdorPerItem);
                }
            }
            Identifier offhandId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem());
            if (offhandId != null) {
                foodOdor += values.getOrDefault(offhandId.toString(), 0.0) * player.getOffhandItem().getCount();
            }
            if (spoiledOdor != null) {
                spoiledOdor.add(player.getOffhandItem(), spoiledOdorPerItem);
                if (spoiledOdor.stacks > 0 && DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SPOILED)) {
                    SoundAttractMod.LOGGER.info("[Spoiled] Added {} odor for player {} from {} tagged stack(s) ({} item(s)).",
                            spoiledOdor.odor, player.getName().getString(), spoiledOdor.stacks, spoiledOdor.items);
                }
            }
            cached = new OdorEntry(foodOdor, spoiledOdor == null ? 0.0 : spoiledOdor.odor,
                    now + SoundAttractConfig.COMMON.playerOdorScanIntervalTicks.get());
            ODOR_CACHE.put(player.getUUID(), cached);
        }
        double scentBlockFactor = player.getData(com.example.soundattract.camo.CamoAttachments.CAMOUFLAGE).getScentBlockFactor();
        double scentBlockMultiplier = 1.0 - Math.max(0.0, Math.min(1.0, scentBlockFactor));
        double hotBathMultiplier = com.example.soundattract.integration.hotbath.HotBathScentStateCache.scentMultiplier(player, now);
        double temperatureMultiplier = com.example.soundattract.integration.toughasnails.ToughAsNailsStateCache.scentMultiplier(player, now);
        double effective = (1.0 + cached.rawFoodOdor() + cached.spoiledOdor())
                * scentBlockMultiplier * hotBathMultiplier * temperatureMultiplier;
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SCENT)) {
            Long previousLog = LAST_SCENT_DEBUG_LOG.getIfPresent(player.getUUID());
            if (previousLog == null || now - previousLog >= 100L) {
                int scentLevel = calculateScentLevel(effective);
                SoundAttractMod.LOGGER.info("[Scent] Player {} odor: baseFoodItems={}, spoiledFood={}, scentBlockMultiplier={}, hotBathMultiplier={}, temperatureMultiplier={}, effective={}, HUD level={}",
                        player.getName().getString(), cached.rawFoodOdor(), cached.spoiledOdor(), scentBlockMultiplier,
                        hotBathMultiplier, temperatureMultiplier, effective, scentLevel);
                LAST_SCENT_DEBUG_LOG.put(player.getUUID(), now);
            }
        }
        return effective;
    }

    private static Map<String, Double> parseOdorValues() {
        List<? extends String> configured = SoundAttractConfig.COMMON.playerOdorItemValues.get();
        List<String> snapshot = new ArrayList<>();
        if (configured != null) {
            for (String raw : configured) {
                snapshot.add(raw == null ? "" : raw);
            }
        }
        List<String> previousEntries = cachedOdorEntries;
        if (snapshot.equals(previousEntries)) {
            return cachedOdorValues;
        }

        synchronized (AwarenessServerEvents.class) {
            if (snapshot.equals(cachedOdorEntries)) {
                return cachedOdorValues;
            }
            Map<String, Double> values = new HashMap<>();
            for (String raw : snapshot) {
                if (raw.isBlank()) {
                    continue;
                }
                String[] parts = raw.split("=", 2);
                if (parts.length == 2) {
                    try {
                        values.put(parts[0].trim(), Double.parseDouble(parts[1].trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            cachedOdorEntries = Collections.unmodifiableList(snapshot);
            cachedOdorValues = Collections.unmodifiableMap(values);
            return cachedOdorValues;
        }
    }

    public static boolean isWindshielded(ServerLevel level, ServerPlayer player) {
        boolean weather2Enabled = SoundAttractConfig.COMMON.enableWeather2WindIntegration.get()
                && ModList.get().isLoaded("weather2");
        Weather2WindCompat.WindSample windSample;
        double windSpeed;
        if (weather2Enabled) {
            windSample = Weather2WindCompat.sample(level, player.blockPosition(), true);
            windSpeed = windSample.weather2Backed() ? windSample.speed() : 1.0;
        } else {
            boolean integrationEnabled = SoundAttractConfig.COMMON.enableWeather2WindIntegration.get();
            String reason = integrationEnabled ? "weather2_missing" : "disabled_by_config";
            Weather2WindDebugLog.report(level, "windshield", reason,
                    () -> "Windshield check is using westward fallback wind in " + level.dimension().identifier()
                            + (integrationEnabled ? " because Weather2 is not installed." : " because Weather2 integration is disabled by config."));
            windSample = new Weather2WindCompat.WindSample(new Vec3(-1.0, 0.0, 0.0), 1.0, false);
            windSpeed = 1.0;
        }
        WindshieldCoverage coverage = getWindshieldCoverage(level, player, windSample.direction());
        UUID playerId = player.getUUID();
        Identifier dimension = level.dimension().identifier();
        WindshieldState previousState = WINDSHIELD_STATES.getIfPresent(playerId);
        boolean wasActive = previousState != null && previousState.dimension().equals(dimension) && previousState.active();
        double minimumWindSpeed = SoundAttractConfig.COMMON.windshieldMinimumWindSpeed.get();
        double activationThreshold = SoundAttractConfig.COMMON.windshieldActivationThreshold.get();
        double threshold = wasActive
                ? Math.min(activationThreshold, SoundAttractConfig.COMMON.windshieldDeactivationThreshold.get())
                : activationThreshold;
        boolean speedSufficient = windSpeed >= minimumWindSpeed;
        boolean active = coverage.strength() >= threshold && speedSufficient;
        WINDSHIELD_STATES.put(playerId, new WindshieldState(active, dimension));
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.WEATHER2_WIND)) {
            Long previousLog = LAST_WINDSHIELD_DEBUG_LOG.getIfPresent(playerId);
            boolean stateChanged = previousState == null || !previousState.dimension().equals(dimension)
                    || previousState.active() != active;
            if (stateChanged || previousLog == null || level.getGameTime() - previousLog >= 100L) {
                String reason = active ? "protected" : !speedSufficient ? "wind_below_minimum" : "coverage_below_threshold";
                SoundAttractMod.LOGGER.info("[Weather2 Wind] Windshield for {} in {}: state={}, reason={}, source={}, speed={}, minimumSpeed={}, direction={}, laneCoverage=[{},{},{}], strength={}, threshold={}, canSeeSky={}",
                        player.getName().getString(), dimension, active, reason,
                        windSample.weather2Backed() ? "Weather2" : "fallback", windSpeed, minimumWindSpeed,
                        windSample.direction(), coverage.firstLane(), coverage.middleLane(), coverage.lastLane(),
                        coverage.strength(), threshold, coverage.canSeeSky());
                LAST_WINDSHIELD_DEBUG_LOG.put(playerId, level.getGameTime());
            }
        }
        return active;
    }

    private static final class SpoiledOdorScan {
        private double odor;
        private int stacks;
        private int items;

        private void add(ItemStack stack, double odorPerItem) {
            double progress = SpoiledScentIntegration.spoilageProgress(stack);
            if (progress <= 0.0 || odorPerItem <= 0.0) {
                return;
            }
            odor += progress * odorPerItem * stack.getCount();
            stacks++;
            items += stack.getCount();
        }
    }

    public static double getWindshieldStrength(ServerLevel level, ServerPlayer player, Vec3 windDirection) {
        return getWindshieldCoverage(level, player, windDirection).strength();
    }

    private static WindshieldCoverage getWindshieldCoverage(ServerLevel level, ServerPlayer player, Vec3 windDirection) {
        BlockPos origin = player.blockPosition();
        boolean canSeeSky = level.canSeeSky(origin);
        if (!canSeeSky || windDirection == null
                || windDirection.x * windDirection.x + windDirection.z * windDirection.z < 1.0E-8) {
            return new WindshieldCoverage(0.0, 0.0, 0.0, canSeeSky);
        }

        Vec3 upwind = windDirection.normalize().scale(-1.0);
        Vec3 lateral = new Vec3(-upwind.z, 0.0, upwind.x);
        double firstLane = 0.0;
        double middleLane = 0.0;
        double lastLane = 0.0;
        for (int lane = -1; lane <= 1; lane++) {
            double laneCoverage = 0.0;
            for (int distance = 1; distance <= SoundAttractConfig.COMMON.windshieldScanDistance.get(); distance++) {
                BlockPos windward = BlockPos.containing(
                        origin.getX() + 0.5 + upwind.x * distance + lateral.x * lane,
                        origin.getY(),
                        origin.getZ() + 0.5 + upwind.z * distance + lateral.z * lane);
                BlockState state = level.getBlockState(windward);
                double opacity = state.isSolidRender() ? 1.0 : state.is(BlockTags.LEAVES) ? 0.35 : 0.0;
                laneCoverage = Math.max(laneCoverage, opacity);
                if (laneCoverage >= 1.0) {
                    break;
                }
            }
            if (lane < 0) {
                firstLane = laneCoverage;
            } else if (lane == 0) {
                middleLane = laneCoverage;
            } else {
                lastLane = laneCoverage;
            }
        }
        return new WindshieldCoverage(firstLane, middleLane, lastLane, true);
    }

    private static AwarenessScan scanAwareness(ServerLevel level, ServerPlayer player) {
        double maximum = 0.0;
        List<AwarenessSnapshotMessage.Alert> alerts = new ArrayList<>(128);
        long now = level.getGameTime();
        double maxConfigRange = SoundAttractConfig.COMMON.maxStealthDetectionRange.get();
        double alertRadius = Math.max(48.0, maxConfigRange);
        net.minecraft.world.phys.AABB alertBounds = player.getBoundingBox().inflate(alertRadius);
        double maxConfigRangeSq = maxConfigRange * maxConfigRange;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, alertBounds, Mob::isAlive)) {
            double distSq = mob.distanceToSqr(player);
            double detectionRange;
            if (distSq <= maxConfigRangeSq) {
                detectionRange = StealthDetectionEvents.getRealisticStealthDetectionRange(player, mob, level);
                maximum = Math.max(maximum, detectionRange);
            } else {
                detectionRange = 0.0;
            }
            if (distSq > alertRadius * alertRadius) {
                continue;
            }
            AwarenessSnapshotMessage.Alert alert = createAlert(level, player, mob, detectionRange, distSq, now);
            if (alert != null) {
                alerts.add(alert);
                if (alerts.size() >= 128) {
                    break;
                }
            }
        }
        return new AwarenessScan(maximum, alerts);
    }

    private static AwarenessSnapshotMessage.Alert createAlert(ServerLevel level, ServerPlayer player, Mob mob,
                                                              double detectionRange, double distSq, long now) {
        double distance = Math.sqrt(distSq);
        AlertEntry extra = EXTRA_ALERTS.getIfPresent(mob.getUUID());
        DetectionKey detectionKey = new DetectionKey(mob.getUUID(), player.getUUID());
        Long detectedUntil = LAST_DETECTED.getIfPresent(detectionKey);
        if (extra != null) {
            if (extra.expiresAt() >= now) {
                long triggerId = extra.type() == ALERT_NEAR_MISS ? extra.expiresAt() - 29L : 0L;
                return new AwarenessSnapshotMessage.Alert(mob.getUUID(), mob.getId(), extra.type(), extra.color(), extra.progress(), triggerId);
            } else {
                EXTRA_ALERTS.invalidate(mob.getUUID());
            }
        }
        if (detectionRange > 0.0 && distSq <= detectionRange * detectionRange) {
            if (StealthDetectionEvents.canMobDetectPlayerWithRange(mob, player, detectionRange)) {
                LAST_DETECTED.put(detectionKey, now + 40L);
                return new AwarenessSnapshotMessage.Alert(mob.getUUID(), mob.getId(), ALERT_DETECTED, 0xFFFF3030, 1.0f);
            }
        } else if (detectionRange <= 0.0) {
            if (StealthDetectionEvents.canMobDetectPlayerWithRange(mob, player, 0.0)) {
                LAST_DETECTED.put(detectionKey, now + 40L);
                return new AwarenessSnapshotMessage.Alert(mob.getUUID(), mob.getId(), ALERT_DETECTED, 0xFFFF3030, 1.0f);
            }
        }
        if (detectedUntil != null) {
            if (detectedUntil >= now) {
                return new AwarenessSnapshotMessage.Alert(mob.getUUID(), mob.getId(), ALERT_DETECTED, 0xFFFF3030, 1.0f);
            } else {
                LAST_DETECTED.invalidate(detectionKey);
            }
        }
        if (isRaidLeader(mob) && (RaidManager.isRaidTicking(mob) || RaidManager.isRaidAdvancing(mob))) {
            return new AwarenessSnapshotMessage.Alert(mob.getUUID(), mob.getId(), ALERT_RAID,
                    RaidManager.isRaidAdvancing(mob) ? 0xFFFF3030 : 0xFFFFC040, 1.0f);
        }
        FollowScentGoal scentGoal = getScentGoal(mob);
        if (scentGoal != null && scentGoal.isFollowingScent()) {
            double targetDistance = scentGoal.getTargetPosition() == null ? 0.0 : mob.position().distanceTo(scentGoal.getTargetPosition());
            float progress = clamp((float) (1.0 - targetDistance / Math.max(1.0, SoundAttractConfig.COMMON.scentDetectionRadius.get())));
            return new AwarenessSnapshotMessage.Alert(mob.getUUID(), mob.getId(), ALERT_SCENT,
                    blendColor(0xFFFFFF30, 0xFFFF3030, progress), progress);
        }
        if (detectionRange > 0.0 && distance >= detectionRange * 0.8 && distance < detectionRange) {
            return new AwarenessSnapshotMessage.Alert(mob.getUUID(), mob.getId(), ALERT_PROXIMITY, 0xFFFFFF30, 0.0f);
        }
        return null;
    }

    private static boolean isRaidLeader(Mob mob) {
        return MobGroupManager.getLeader(mob) == mob;
    }

    private static FollowScentGoal getScentGoal(Mob mob) {
        if (!com.example.soundattract.config.separate.ScentConfig.ENABLE_SCENT_SYSTEM.get()) {
            return null;
        }
        if (mob.getNavigation() == null || mob.getNavigation().isDone()) {
            return null;
        }
        for (net.minecraft.world.entity.ai.goal.WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.isRunning() && wrapped.getGoal() instanceof FollowScentGoal scentGoal) {
                return scentGoal;
            }
        }
        return null;
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static int blendColor(int start, int end, float progress) {
        int r = (int) (((start >> 16) & 255) + ((((end >> 16) & 255) - ((start >> 16) & 255)) * progress));
        int g = (int) (((start >> 8) & 255) + ((((end >> 8) & 255) - ((start >> 8) & 255)) * progress));
        int b = (int) ((start & 255) + (((end & 255) - (start & 255)) * progress));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private record AlertEntry(int type, int color, float progress, long expiresAt) {
    }

    private record OdorEntry(double rawFoodOdor, double spoiledOdor, long nextScan) {
    }

    private record WindshieldCoverage(double firstLane, double middleLane, double lastLane, boolean canSeeSky) {
        private double strength() {
            return (firstLane + middleLane + lastLane) / 3.0;
        }
    }

    private record WindshieldState(boolean active, Identifier dimension) {
    }

    private record DetectionKey(UUID mobId, UUID playerId) {
    }

    private record AwarenessScan(double maximumDetectionRange, List<AwarenessSnapshotMessage.Alert> alerts) {
    }
}
