package com.example.soundattract.event;

import com.example.soundattract.Soundattract;
import com.example.soundattract.ai.FollowScentGoal;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.mixin.MobAccessor;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import com.example.soundattract.scents.ScentBiomeModifier;
import com.example.soundattract.scents.ScentSourceType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.DustParticleOptions;
import com.example.soundattract.scents.ScentParticleColorManager;
import org.joml.Vector3f;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.TimeUnit;

public class ScentEvents {

    private static final int MAX_RUNTIME_GLOBAL_CACHE_SIZE = 65536;
    private static Map<UUID, Vec3> lastScentPos = newInitialCache();
    private static Map<UUID, Long> lastScentTime = newInitialCache();

    private static <V> Map<UUID, V> newInitialCache() {
        return CacheBuilder.newBuilder().maximumSize(8192).expireAfterWrite(10, TimeUnit.MINUTES)
                .<UUID, V>build().asMap();
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                onPlayerTick(player);
            }
        });

        ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (level instanceof ServerLevel) {
                ServerLevel serverLevel = (ServerLevel) level;
                onLevelTick(serverLevel);
                onLevelTickCleanup(serverLevel);
            }
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ServerPlayer player) {
                onPlayerLoggedOut(player);
            } else if (entity instanceof Mob mob) {
                onEntityJoinLevel(mob, world);
            }
        });

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof Mob mob) {
                onEntityLeaveScentCache(mob);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            onServerStopped(server);
        });
    }

    public static void reinitializeCaches() {
        int max = Math.min(MAX_RUNTIME_GLOBAL_CACHE_SIZE,
                Math.max(100, SoundAttractConfig.COMMON.globalCacheMaxSize.get()));
        int mins = SoundAttractConfig.COMMON.globalCacheExpireMins.get();

        Map<UUID, Vec3> oldObj1 = lastScentPos;
        lastScentPos = CacheBuilder.newBuilder().expireAfterWrite(mins, TimeUnit.MINUTES).maximumSize(max).concurrencyLevel(4).<UUID, Vec3>build().asMap();
        lastScentPos.putAll(oldObj1);

        Map<UUID, Long> oldObj2 = lastScentTime;
        lastScentTime = CacheBuilder.newBuilder().expireAfterWrite(mins, TimeUnit.MINUTES).maximumSize(max).concurrencyLevel(4).<UUID, Long>build().asMap();
        lastScentTime.putAll(oldObj2);

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info("[ScentEvents] Initialized Guava internal memory caches (max {}, {} mins)", max, mins);
        }
    }

    public static void onPlayerTick(ServerPlayer player) {
        onPlayerTick(player, SoundAttractConfig.COMMON.enableScentSystem.get(),
                SoundAttractConfig.COMMON.scentCreationIntervalBlocks.get());
    }

    /** Runs the production player scent update with explicit enable/spacing controls for GameTests. */
    public static void onPlayerTickForTesting(ServerPlayer player, double intervalBlocks) {
        onPlayerTick(player, true, intervalBlocks);
    }

    /** Deterministic weather input for producer integration GameTests. */
    public static void onPlayerTickForTesting(ServerPlayer player, double intervalBlocks,
                                              boolean rainingAtPosition) {
        onPlayerTick(player, true, intervalBlocks, rainingAtPosition);
    }

    private static void onPlayerTick(ServerPlayer player, boolean enabled, double intervalBlocks) {
        onPlayerTick(player, enabled, intervalBlocks, null);
    }

    private static void onPlayerTick(ServerPlayer player, boolean enabled, double intervalBlocks,
                                     Boolean rainingAtPositionOverride) {
        if (player.level().isClientSide()) return;
        if (!enabled) return;
        
        if (player.isSpectator() || player.isCreative()) return;
        
        if (SoundAttractConfig.COMMON.waterStopsScent.get() && player.isInWater()) {
            return;
        }

        UUID playerId = player.getUUID();
        Vec3 currentPos = player.position();
        long currentTime = player.level().getGameTime();

        Vec3 lastPos = lastScentPos.computeIfAbsent(playerId, k -> currentPos);
        Long lastTime = lastScentTime.put(playerId, currentTime);

        double distSq = currentPos.distanceToSqr(lastPos);
        double intervalSq = intervalBlocks * intervalBlocks;

        if (distSq >= intervalSq) {
            float durationMod = rainingAtPositionOverride == null
                    ? ScentBiomeModifier.getDurationModifier(player.level(), player.blockPosition())
                    : ScentBiomeModifier.getDurationModifierForTesting(
                            player.level(), player.blockPosition(), rainingAtPositionOverride);
            float baseStrength = 1.0f * durationMod;
            
            double blockFactor = com.example.soundattract.camo.CamouflageCapability.getCapability(player)
                    .map(com.example.soundattract.camo.CamouflageCapability::getScentBlockFactor)
                    .orElse(0.0);
            
            baseStrength *= (float) (1.0 - blockFactor);
            baseStrength *= (float) com.example.soundattract.integration.toughasnails.ToughAsNailsStateCache
                    .scentMultiplier(player, currentTime);

            if (baseStrength <= 0.05f) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[ScentEvents] Scent for player {} fully blocked by camouflage.", player.getName().getString());
                }
                lastScentPos.put(playerId, currentPos);
                return;
            }

            ScentNode node = new ScentNode(currentPos, currentTime, baseStrength, playerId, ScentSourceType.PLAYER_WALK);
            
            ScentManager manager = ScentManager.getForLevel((ServerLevel) player.level());
            if (manager != null) {
                manager.addScentNode(node);
            }

            lastScentPos.put(playerId, currentPos);

            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info("[ScentEvents] Created scent node for player {} at {}. Strength: {}", 
                    player.getName().getString(), currentPos, baseStrength);
            }

            if (SoundAttractConfig.COMMON.enableScentParticles.get()) {
                ((ServerLevel) player.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, currentPos.x, currentPos.y + 0.5, currentPos.z, 1, 0, 0, 0, 0.0);
            }
        }
    }

    public static void onLevelTick(ServerLevel level) {
        if (level.isClientSide()) return;
        if (!SoundAttractConfig.COMMON.enableScentSystem.get()) return;
        long currentTime = level.getGameTime();
        long maxDuration = SoundAttractConfig.COMMON.scentNodeDurationTicks.get();
        if (currentTime % 200L == 0L) {
            ScentManager manager = ScentManager.getForLevel(level);
            if (manager != null) manager.cleanupExpired(currentTime, maxDuration);
        }
        boolean renderExistingParticles = SoundAttractConfig.COMMON.enableGameplayScentParticles.get()
                && currentTime % SoundAttractConfig.COMMON.scentParticleSpawnInterval.get() == 0;
        boolean renderWindParticles = SoundAttractConfig.COMMON.enableWindScentParticles.get()
                && currentTime % SoundAttractConfig.COMMON.windScentParticleIntervalTicks.get() == 0;
        if (!renderExistingParticles && !renderWindParticles) return;

        double baseRenderDist = SoundAttractConfig.COMMON.scentParticleRenderDistance.get();
        double renderDistSq = baseRenderDist * baseRenderDist;
        ScentManager manager = ScentManager.getForLevel(level);
        if (manager == null) return;
        net.minecraft.server.MinecraftServer server = level.getServer();
        if (server == null) return;
        List<ScentNode> allNodes = manager.getNodesNearPlayers(level.players(), baseRenderDist);
        Map<UUID, Boolean> particleEnabledCache = new java.util.HashMap<>();
        Map<UUID, org.joml.Vector3f> colorCache = new java.util.HashMap<>();
        Map<UUID, Integer> windParticleCountByViewer = new java.util.HashMap<>();
        Map<UUID, com.example.soundattract.config.ScentVisibilityConfig> viewerVisibilityCache = new java.util.HashMap<>();

        for (ScentNode node : allNodes) {
            boolean windNode = node.getSourceType() == ScentSourceType.PLAYER_WIND;
            if (windNode ? !renderWindParticles : !renderExistingParticles) continue;
            long age = currentTime - node.getTimestamp();
            if (age > maxDuration || age < 0) continue;
            UUID ownerUUID = node.getOwnerUUID();
            if (ownerUUID == null) continue;
            Boolean enabled = particleEnabledCache.computeIfAbsent(ownerUUID, id -> {
                ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(id);
                if (ownerPlayer == null) return true;
                com.example.soundattract.config.PlayerProfile2 profile = SoundAttractConfig.getMatchingPlayerProfile(ownerPlayer);
                return profile == null || profile.scentEmission().map(com.example.soundattract.config.ScentEmissionConfig::showScentParticles).orElse(true);
            });
            if (!enabled) continue;
            Vec3 pos = node.getPosition();

            if (windNode) {
                for (ServerPlayer viewer : level.players()) {
                    if (viewer.position().distanceToSqr(pos) > renderDistSq) continue;
                    int sent = windParticleCountByViewer.getOrDefault(viewer.getUUID(), 0);
                    if (sent >= SoundAttractConfig.COMMON.windScentParticleMaxPerViewer.get()) continue;
                    com.example.soundattract.config.ScentVisibilityConfig visibility = viewerVisibilityCache.computeIfAbsent(
                            viewer.getUUID(), ignored -> {
                                com.example.soundattract.config.PlayerProfile2 profile = SoundAttractConfig.getMatchingPlayerProfile(viewer);
                                return profile == null ? com.example.soundattract.config.ScentVisibilityConfig.DEFAULT
                                        : profile.scentVisibility().orElse(com.example.soundattract.config.ScentVisibilityConfig.DEFAULT);
                            });
                    if (!visibility.showWindScent()) continue;
                    level.sendParticles(viewer, com.example.soundattract.registration.ModParticles.WIND_SCENT_WISP, false, false,
                            pos.x, pos.y + 0.35, pos.z, 0, -0.055, 0.008, 0.0, 1.0);
                    windParticleCountByViewer.put(viewer.getUUID(), sent + 1);
                }
                continue;
            }

            org.joml.Vector3f color = colorCache.computeIfAbsent(ownerUUID, id -> {
                ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(id);
                return ownerPlayer == null ? ScentParticleColorManager.getColorForPlayer(id)
                        : ScentParticleColorManager.getColorForPlayer(id, ownerPlayer);
            });
            float lifeRatio = 1.0f - ((float) age / maxDuration);
            float particleScale = 0.4f + (lifeRatio * 0.6f);
            int particleCount = lifeRatio > 0.5f ? 2 : 1;
            net.minecraft.core.particles.DustParticleOptions dustParticle =
                    new net.minecraft.core.particles.DustParticleOptions(0xFF000000 | ((int)(color.x * 255) << 16) | ((int)(color.y * 255) << 8) | (int)(color.z * 255), particleScale);
            for (ServerPlayer viewer : level.players()) {
                if (viewer.position().distanceToSqr(pos) > renderDistSq) continue;
                com.example.soundattract.config.ScentVisibilityConfig visibility = viewerVisibilityCache.computeIfAbsent(
                        viewer.getUUID(), ignored -> {
                            com.example.soundattract.config.PlayerProfile2 profile = SoundAttractConfig.getMatchingPlayerProfile(viewer);
                            return profile == null ? com.example.soundattract.config.ScentVisibilityConfig.DEFAULT
                                    : profile.scentVisibility().orElse(com.example.soundattract.config.ScentVisibilityConfig.DEFAULT);
                        });
                boolean showScent = switch (node.getSourceType()) {
                    case PLAYER_WALK -> visibility.showPlayerWalk();
                    case PLAYER_WIND -> visibility.showWindScent();
                    case ARROW_PATH, ARROW_ORIGIN, MOB_PROJECTILE_PATH, MOB_PROJECTILE_ORIGIN -> visibility.showArrowScent();
                };
                if (showScent) {
                    level.sendParticles(viewer, dustParticle, true, true, pos.x, pos.y + 0.3, pos.z,
                            particleCount, 0.1, 0.05, 0.1, 0.0);
                }
            }
        }
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        UUID playerId = player.getUUID();
        lastScentPos.remove(playerId);
        lastScentTime.remove(playerId);
    }

    public static void onEntityJoinLevel(Mob mob, Level level) {
        if (level.isClientSide()) return;
        if (!SoundAttractConfig.COMMON.enableScentSystem.get()) return;
        
        if (mob instanceof PathfinderMob pathfinderMob) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
            java.util.List<? extends String> whitelist = SoundAttractConfig.COMMON.scentEligibleMobs.get();
            
            if (whitelist.contains(id)) {
                if (SoundAttractConfig.COMMON.enableSmartBrainLibIntegration.get()) {
                    if (com.example.soundattract.integration.smartbrainlib.SmartBrainLibCompat.tryAttachSoundAttractBrain(pathfinderMob)) {
                        return;
                    }
                }
                
                MobAccessor accessor = (MobAccessor) mob;
                accessor.getGoalSelector().addGoal(5, new FollowScentGoal(pathfinderMob));
            }
        }
    }

    public static void onLevelTickCleanup(ServerLevel level) {
        if (SoundAttractConfig.COMMON == null) return;
        long now = level.getGameTime();
        if (now % 200L != 0L) return;
        com.example.soundattract.util.ScentQueryCache.cleanup(
            level.dimension().identifier(),
            now,
            SoundAttractConfig.COMMON.scentQueryCacheTtlTicks.get(),
            SoundAttractConfig.COMMON.scentQueryCacheMaxEntries.get()
        );
    }

    public static void onEntityLeaveScentCache(Mob mob) {
        com.example.soundattract.util.ScentQueryCache.invalidate(mob.getUUID(), mob.level().dimension().identifier());
    }

    public static void onLevelUnload(ServerLevel level) {
        ScentManager manager = ScentManager.getForLevel(level);
        if (manager != null) manager.clear();
    }

    public static void onServerStopped(MinecraftServer server) {
        lastScentPos.clear();
        lastScentTime.clear();
        com.example.soundattract.util.ScentQueryCache.clear();
        com.example.soundattract.scents.GlobalScentRateLimiter.clear();
        ScentParticleColorManager.clearCache();
    }
}
