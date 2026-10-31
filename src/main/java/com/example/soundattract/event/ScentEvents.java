package com.example.soundattract.event;

import net.minecraft.core.registries.BuiltInRegistries;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.camo.CamoAttachments;
import com.example.soundattract.camo.CamouflageCapability;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import com.example.soundattract.scents.ScentBiomeModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.DustParticleOptions;
import com.example.soundattract.scents.ScentParticleColorManager;
import org.joml.Vector3f;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.concurrent.ConcurrentHashMap;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.TimeUnit;

@EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public class ScentEvents {

    private static Map<UUID, Vec3> lastScentPos = new ConcurrentHashMap<>();
    private static Map<UUID, Long> lastScentTime = new ConcurrentHashMap<>();

    public static void reinitializeCaches() {
        int max = SoundAttractConfig.COMMON.globalCacheMaxSize.get();
        int mins = SoundAttractConfig.COMMON.globalCacheExpireMins.get();

        Map<UUID, Vec3> oldObj1 = lastScentPos;
        lastScentPos = CacheBuilder.newBuilder().expireAfterWrite(mins, TimeUnit.MINUTES).maximumSize(max).concurrencyLevel(4).<UUID, Vec3>build().asMap();
        lastScentPos.putAll(oldObj1);

        Map<UUID, Long> oldObj2 = lastScentTime;
        lastScentTime = CacheBuilder.newBuilder().expireAfterWrite(mins, TimeUnit.MINUTES).maximumSize(max).concurrencyLevel(4).<UUID, Long>build().asMap();
        lastScentTime.putAll(oldObj2);

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[ScentEvents] Initialized Guava internal memory caches (max {}, {} mins)", max, mins);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!SoundAttractConfig.COMMON.enableScentSystem.get()) return;

        ServerPlayer player = (ServerPlayer) event.getEntity();
        
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
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[ScentEvents] Scent for player {} fully blocked by camouflage.", player.getName().getString());
                }
                lastScentPos.put(playerId, currentPos);
                return;
            }


            ScentNode node = new ScentNode(currentPos, currentTime, baseStrength, playerId, com.example.soundattract.scents.ScentSourceType.PLAYER_WALK);
            
            ScentManager.get(player.level()).ifPresent(manager -> {
                manager.addScentNode(node);
            });

            lastScentPos.put(playerId, currentPos);

            if (SoundAttractConfig.COMMON.debugLogging.get()) {
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
        if (event.getLevel().isClientSide()) return;
        if (!SoundAttractConfig.COMMON.enableScentSystem.get()) return;
        

        if (event.getLevel().getGameTime() % 200 == 0) {

        }


        if (!SoundAttractConfig.COMMON.enableGameplayScentParticles.get()) return;

        int spawnInterval = SoundAttractConfig.COMMON.scentParticleSpawnInterval.get();
        if (event.getLevel().getGameTime() % spawnInterval != 0) return;

        ServerLevel serverLevel = (ServerLevel) event.getLevel();
        long currentTime = serverLevel.getGameTime();
        long maxDuration = SoundAttractConfig.COMMON.scentNodeDurationTicks.get();
        double baseRenderDist = SoundAttractConfig.COMMON.scentParticleRenderDistance.get();
        final double renderDistSq = baseRenderDist * baseRenderDist;

        ScentManager.get(serverLevel).ifPresent(manager -> {

            net.minecraft.server.MinecraftServer server = serverLevel.getServer();
            if (server == null) return;

            List<ScentNode> allNodes = manager.getAllNodes();


            Map<UUID, Boolean> particleEnabledCache = new java.util.HashMap<>();
            Map<UUID, org.joml.Vector3f> colorCache = new java.util.HashMap<>();

            for (ScentNode node : allNodes) {
                long age = currentTime - node.getTimestamp();
                if (age > maxDuration || age < 0) continue;

                UUID ownerUUID = node.getOwnerUUID();
                if (ownerUUID == null) continue;


                Boolean enabled = particleEnabledCache.get(ownerUUID);
                if (enabled == null) {
                    enabled = true;
                    net.minecraft.server.level.ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(ownerUUID);
                    if (ownerPlayer != null) {
                        com.example.soundattract.config.PlayerProfile2 profile = SoundAttractConfig.getMatchingPlayerProfile(ownerPlayer);
                        if (profile != null && profile.scentEmission().isPresent()) {
                            enabled = profile.scentEmission().get().showScentParticles();
                        }
                    }
                    particleEnabledCache.put(ownerUUID, enabled);
                }
                if (!enabled) continue;


                org.joml.Vector3f color = colorCache.get(ownerUUID);
                if (color == null) {
                    net.minecraft.server.level.ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(ownerUUID);
                    if (ownerPlayer != null) {
                        color = ScentParticleColorManager.getColorForPlayer(ownerUUID, ownerPlayer);
                    } else {
                        color = ScentParticleColorManager.getColorForPlayer(ownerUUID);
                    }
                    colorCache.put(ownerUUID, color);
                }


                float lifeRatio = 1.0f - ((float) age / maxDuration);
                float particleScale = 0.4f + (lifeRatio * 0.6f);
                int particleCount = lifeRatio > 0.5f ? 2 : 1;

                Vec3 pos = node.getPosition();


                net.minecraft.core.particles.DustParticleOptions dustParticle =
                        new net.minecraft.core.particles.DustParticleOptions((Math.round(color.x() * 255) << 16) | (Math.round(color.y() * 255) << 8) | Math.round(color.z() * 255), particleScale);

                for (ServerPlayer nearbyPlayer : serverLevel.players()) {
                    if (nearbyPlayer.position().distanceToSqr(pos) <= renderDistSq) {
                        com.example.soundattract.config.PlayerProfile2 viewerProfile = SoundAttractConfig.getMatchingPlayerProfile(nearbyPlayer);
                        boolean showScent = true;
                        if (viewerProfile != null && viewerProfile.scentVisibility().isPresent()) {
                            com.example.soundattract.config.ScentVisibilityConfig vis = viewerProfile.scentVisibility().get();
                            showScent = switch (node.getSourceType()) {
                                case PLAYER_WALK, PLAYER_WIND -> vis.showPlayerWalk();
                                case ARROW_PATH, ARROW_ORIGIN, MOB_PROJECTILE_PATH, MOB_PROJECTILE_ORIGIN -> vis.showArrowScent();
                            };
                        }
                        if (!showScent) continue;
                        serverLevel.sendParticles(nearbyPlayer, dustParticle, true, true,
                                pos.x, pos.y + 0.3, pos.z,
                                particleCount, 0.1, 0.05, 0.1, 0.0);
                    }
                }
            }
        });
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        lastScentPos.remove(playerId);
        lastScentTime.remove(playerId);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
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
        com.example.soundattract.util.ScentQueryCache.clear();
    }
}
