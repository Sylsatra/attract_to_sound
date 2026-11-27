package com.example.soundattract.event;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.camo.CamoAttachments;
import com.example.soundattract.camo.CamouflageCapability;
import com.example.soundattract.config.DebugLoggingConfig;
import com.example.soundattract.config.PlayerProfile2;
import com.example.soundattract.config.ScentVisibilityConfig;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.integration.wind.AtmosphericWindCompat;
import com.example.soundattract.registration.ModParticles;
import com.example.soundattract.scents.GlobalScentRateLimiter;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import com.example.soundattract.scents.ScentBiomeModifier;
import com.example.soundattract.scents.ScentParticleColorManager;
import com.example.soundattract.scents.ScentSourceType;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public class ScentEvents {

    private static final int MAX_RUNTIME_GLOBAL_CACHE_SIZE = 65536;

    private static volatile Map<UUID, Vec3> lastScentPos = newInitialCache();

    private static <K, V> Map<K, V> newInitialCache() {
        return Caffeine.newBuilder()
                .maximumSize(8192)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .<K, V>build()
                .asMap();
    }

    public static void reinitializeCaches() {
        int max = Math.min(MAX_RUNTIME_GLOBAL_CACHE_SIZE,
                Math.max(100, SoundAttractConfig.COMMON.globalCacheMaxSize.get()));
        int mins = SoundAttractConfig.COMMON.globalCacheExpireMins.get();

        Map<UUID, Vec3> oldPositions = lastScentPos;
        lastScentPos = Caffeine.newBuilder()
                .expireAfterWrite(mins, TimeUnit.MINUTES)
                .maximumSize(max)
                .<UUID, Vec3>build()
                .asMap();
        lastScentPos.putAll(oldPositions);

        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SCENT)) {
            SoundAttractMod.LOGGER.info("[ScentEvents] Initialized Caffeine memory caches (max {}, {} mins)", max, mins);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!SoundAttractConfig.COMMON.enableScentSystem.get()) return;

        if (player.isSpectator() || player.isCreative()) return;
        
        if (SoundAttractConfig.COMMON.waterStopsScent.get() && player.isInWater()) {
            return;
        }

        UUID playerId = player.getUUID();
        Vec3 currentPos = player.position();
        long currentTime = player.level().getGameTime();

        Vec3 lastPos = lastScentPos.computeIfAbsent(playerId, k -> currentPos);

        double distSq = currentPos.distanceToSqr(lastPos);
        double interval = SoundAttractConfig.COMMON.scentCreationIntervalBlocks.get();
        double intervalSq = interval * interval;


        if (distSq >= intervalSq) {
            float durationMod = ScentBiomeModifier.getDurationModifier(player.level(), player.blockPosition());
            float baseStrength = 1.0f;
            

            CamouflageCapability camo = player.getData(CamoAttachments.CAMOUFLAGE);
            double blockFactor = camo.getScentBlockFactor();
            
            baseStrength *= (float) (1.0 - blockFactor);
            baseStrength *= (float) com.example.soundattract.integration.hotbath.HotBathScentStateCache.scentMultiplier(player, currentTime);
            baseStrength *= (float) com.example.soundattract.integration.toughasnails.ToughAsNailsStateCache.scentMultiplier(player, currentTime);

            if (baseStrength <= 0.05f) {
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SCENT)) {
                    SoundAttractMod.LOGGER.info("[ScentEvents] Scent for player {} fully blocked by camouflage.", player.getName().getString());
                }
                lastScentPos.put(playerId, currentPos);
                return;
            }


            ScentNode node = new ScentNode(currentPos, currentTime, baseStrength, playerId, ScentSourceType.PLAYER_WALK);
            
            ScentManager.get(player.level()).ifPresent(manager -> {
                manager.addScentNode(node);
            });

            lastScentPos.put(playerId, currentPos);

            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SCENT)) {
                SoundAttractMod.LOGGER.info("[ScentEvents] Created scent node for player {} at {}. Strength: {}",
                    player.getName().getString(), currentPos, baseStrength);
            }

            if (SoundAttractConfig.COMMON.enableScentParticles.get()) {
                ((ServerLevel) player.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, currentPos.x, currentPos.y + 0.5, currentPos.z, 1, 0, 0, 0, 0.0);
            }
        }
    }

    @SubscribeEvent
    public static void onWorldTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!SoundAttractConfig.COMMON.enableScentSystem.get()) {
            ScentManager.get(level).ifPresent(ScentManager::clear);
            return;
        }

        long currentTime = level.getGameTime();
        long maxDuration = SoundAttractConfig.COMMON.scentNodeDurationTicks.get();
        if (currentTime % 200L == 0L) {
            ScentManager.get(level).ifPresent(manager -> manager.cleanupExpired(currentTime, maxDuration));
        }
        boolean renderExistingParticles = SoundAttractConfig.COMMON.enableGameplayScentParticles.get()
                && currentTime % SoundAttractConfig.COMMON.scentParticleSpawnInterval.get() == 0;
        boolean renderWindParticles = SoundAttractConfig.COMMON.enableWindScentParticles.get()
                && currentTime % SoundAttractConfig.COMMON.windScentParticleIntervalTicks.get() == 0;
        if (!renderExistingParticles && !renderWindParticles) return;

        double renderDistance = SoundAttractConfig.COMMON.scentParticleRenderDistance.get();
        ScentManager.get(level).ifPresent(manager -> {
            List<ScentNode> nodes = manager.getNodesNearPlayers(level.players(), renderDistance);
            renderScentParticles(level, nodes, currentTime, maxDuration, renderDistance * renderDistance,
                    renderExistingParticles, renderWindParticles);
        });
    }

    private static void renderScentParticles(ServerLevel level, List<ScentNode> nodes, long currentTime,
                                             long maxDuration, double renderDistanceSqr,
                                             boolean renderExistingParticles, boolean renderWindParticles) {
        MinecraftServer server = level.getServer();
        Map<UUID, Boolean> ownerVisibility = new HashMap<>();
        Map<UUID, Vector3f> ownerColors = new HashMap<>();
        Map<UUID, Vec3> ownerWindDirections = new HashMap<>();
        Map<UUID, Integer> windCounts = new HashMap<>();
        Map<UUID, ScentVisibilityConfig> viewerVisibility = new HashMap<>();
        int maxWindPerViewer = SoundAttractConfig.COMMON.windScentParticleMaxPerViewer.get();

        for (ScentNode node : nodes) {
            boolean wind = node.getSourceType() == ScentSourceType.PLAYER_WIND;
            if (wind ? !renderWindParticles : !renderExistingParticles) continue;
            long age = currentTime - node.getTimestamp();
            UUID ownerId = node.getOwnerUUID();
            if (age > maxDuration || age < 0 || ownerId == null) continue;

            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
            if (!ownerVisibility.computeIfAbsent(ownerId, ignored -> showOwnerParticles(owner))) continue;
            Vec3 position = node.getPosition();
            if (wind) {
                if (owner == null) continue;
                Vec3 direction = ownerWindDirections.computeIfAbsent(ownerId,
                        ignored -> AtmosphericWindCompat.direction(level, owner.blockPosition()));
                for (ServerPlayer viewer : level.players()) {
                    UUID viewerId = viewer.getUUID();
                    if (viewer.position().distanceToSqr(position) > renderDistanceSqr
                            || windCounts.getOrDefault(viewerId, 0) >= maxWindPerViewer
                            || !viewerVisibility.computeIfAbsent(viewerId, ignored -> getViewerVisibility(viewer)).showWindScent()) {
                        continue;
                    }
                    level.sendParticles(viewer, ModParticles.WIND_SCENT_WISP.get(), false,
                            position.x, position.y + 0.35, position.z, 0,
                            direction.x * 0.055, 0.008, direction.z * 0.055, 1.0);
                    windCounts.merge(viewerId, 1, Integer::sum);
                }
                continue;
            }

            Vector3f color = ownerColors.computeIfAbsent(ownerId, ignored -> owner == null
                    ? ScentParticleColorManager.getColorForPlayer(ownerId)
                    : ScentParticleColorManager.getColorForPlayer(ownerId, owner));
            float lifeRatio = 1.0f - ((float) age / maxDuration);
            DustParticleOptions dustParticle = new DustParticleOptions(color, 0.4f + lifeRatio * 0.6f);
            int particleCount = lifeRatio > 0.5f ? 2 : 1;
            for (ServerPlayer viewer : level.players()) {
                if (viewer.position().distanceToSqr(position) > renderDistanceSqr) continue;
                ScentVisibilityConfig visibility = viewerVisibility.computeIfAbsent(viewer.getUUID(),
                        ignored -> getViewerVisibility(viewer));
                boolean showScent = switch (node.getSourceType()) {
                    case PLAYER_WALK -> visibility.showPlayerWalk();
                    case PLAYER_WIND -> visibility.showWindScent();
                    case ARROW_PATH, ARROW_ORIGIN, MOB_PROJECTILE_PATH, MOB_PROJECTILE_ORIGIN -> visibility.showArrowScent();
                };
                if (!showScent) continue;
                level.sendParticles(viewer, dustParticle, true, position.x, position.y + 0.3, position.z,
                        particleCount, 0.1, 0.05, 0.1, 0.0);
            }
        }
    }

    private static boolean showOwnerParticles(ServerPlayer owner) {
        PlayerProfile2 profile = owner == null ? null : SoundAttractConfig.getMatchingPlayerProfile(owner);
        return profile == null || profile.scentEmission().isEmpty() || profile.scentEmission().get().showScentParticles();
    }

    private static ScentVisibilityConfig getViewerVisibility(ServerPlayer viewer) {
        PlayerProfile2 profile = SoundAttractConfig.getMatchingPlayerProfile(viewer);
        return profile == null ? ScentVisibilityConfig.DEFAULT : profile.scentVisibility().orElse(ScentVisibilityConfig.DEFAULT);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        lastScentPos.remove(playerId);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (!SoundAttractConfig.COMMON.enableScentSystem.get()) return;
        
        if (event.getEntity() instanceof net.minecraft.world.entity.PathfinderMob mob) {
            String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
            java.util.List<? extends String> whitelist = SoundAttractConfig.COMMON.scentEligibleMobs.get();
            
            if (whitelist.contains(id)) {

                if (SoundAttractConfig.COMMON.enableSmartBrainLibIntegration.get()) {
                    if (com.example.soundattract.integration.smartbrainlib.SmartBrainLibCompat.tryAttachSoundAttractBrain(mob)) {
                        return;
                    }
                }

                mob.goalSelector.addGoal(5, new com.example.soundattract.ai.FollowScentGoal(mob));
            }
        }
    }

    @SubscribeEvent
    public static void onServerTickScentCacheCleanup(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (SoundAttractConfig.COMMON == null) return;
        long now = level.getGameTime();
        if (now % 200L != 0L) return;
        com.example.soundattract.util.ScentQueryCache.cleanup(
            now,
            SoundAttractConfig.COMMON.scentQueryCacheTtlTicks.get(),
            SoundAttractConfig.COMMON.scentQueryCacheMaxEntries.get()
        );
    }

    @SubscribeEvent
    public static void onEntityLeaveScentCache(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.Mob m) {
            com.example.soundattract.util.ScentQueryCache.invalidate(m.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStoppedScentCache(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        lastScentPos.clear();
        com.example.soundattract.util.ScentQueryCache.clear();
        GlobalScentRateLimiter.clear();
        ScentParticleColorManager.clearCache();
    }
}
