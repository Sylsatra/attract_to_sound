package com.example.soundattract.event;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.ai.LeaderAttractionGoal;
import com.example.soundattract.ai.FollowerEdgeRelayGoal;
import com.example.soundattract.config.DebugLoggingConfig;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.tracking.SoundTracker;
import com.example.soundattract.util.ArrowInvestigationHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class ArrowInvestigationEvents {

    public static final TagKey<EntityType<?>> INVESTIGATE_PROJECTILES =
            TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath("soundattract", "investigate_projectiles"));

    private static final int MAX_TRACKED_ARROWS = 4096;
    private static final long MAX_ARROW_AGE_TICKS = 1200L;

    public static final Map<Integer, ArrowState> ARROWS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();

    public static final UUID DISPENSER_SENTINEL_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (SoundAttractConfig.COMMON == null) return;
        Entity e = event.getEntity();
        if (!(e instanceof Projectile projectile)) return;
        if (!isWatchedProjectile(projectile)) return;
        if (ARROWS.size() >= MAX_TRACKED_ARROWS) {
            if (SoundAttractConfig.COMMON.debugLogging != null && SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.debug("[ArrowInvestigation] ARROWS cap reached; dropping new projectile {}", projectile.getId());
            }
            return;
        }
        long now = projectile.level().getGameTime();
        Entity owner = projectile.getOwner();
        UUID shooterUuid;
        boolean isMob;
        if (owner != null) {
            shooterUuid = owner.getUUID();
            isMob = (owner instanceof Mob);
        } else {
            shooterUuid = DISPENSER_SENTINEL_UUID;
            isMob = false;
        }
        ARROWS.put(projectile.getId(), new ArrowState(projectile.position(), now, shooterUuid, isMob));
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.ARROW_INVESTIGATION)) {
            SoundAttractMod.LOGGER.info("[ArrowInvestigation] Tracking projectile: id={}, type={}, dimension={}",
                    projectile.getId(), net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()),
                    projectile.level().dimension().location());
        }
    }

    @SubscribeEvent
    public void onEntityLeave(EntityLeaveLevelEvent event) {
        Entity e = event.getEntity();
        if (e != null) {
            ARROWS.remove(e.getId());
        }
    }

    @SubscribeEvent
    public void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        ARROWS.clear();
        COOLDOWNS.clear();
    }

    @SubscribeEvent
    public void onServerTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (SoundAttractConfig.COMMON == null) return;
        if (!SoundAttractConfig.COMMON.enableArrowInvestigation.get()) return;

        final double radius = SoundAttractConfig.COMMON.arrowNearMissRadius.get();
        final double radiusSq = radius * radius;
        final long now = level.getGameTime();
        final int checkInterval = 4;

        ARROWS.entrySet().removeIf(entry -> {
            ArrowState state = entry.getValue();
            if (state == null) return true;
            if (now - state.spawnTick > MAX_ARROW_AGE_TICKS) return true;

            Entity e = level.getEntity(entry.getKey());
            if (!(e instanceof Projectile projectile)) return false;
            if (projectile.level() != level) return false;
            if (!projectile.isAlive()) {
                return true;
            }
            Vec3 velocity = projectile.getDeltaMovement();
            double speedSqr = velocity.lengthSqr();
            if (projectile instanceof AbstractArrow && speedSqr < 1.0e-4) {
                return true;
            }

            Vec3 curr = projectile.position();

            int arrowId = entry.getKey();
            if ((arrowId + (int)now) % checkInterval != 0) {


                return false;
            }

            double cullRadiusSq = (radius * 2) * (radius * 2);
            boolean anyPlayerNearby = false;
            for (var player : level.players()) {
                if (player.isAlive() && curr.distanceToSqr(player.position()) <= cullRadiusSq) {
                    anyPlayerNearby = true;
                    break;
                }
            }
            AABB segmentAabb = new AABB(state.prevPos, curr).inflate(radius + 1.0);
            List<Mob> mobs = level.getEntitiesOfClass(Mob.class, segmentAabb, m -> m.isAlive() && hasSoundPursuitGoal(m));
            if (!ArrowInvestigationHelper.shouldScanProjectileSegment(anyPlayerNearby, !mobs.isEmpty())) {
                state.prevPos = curr;
                return false;
            }
            for (Mob mob : mobs) {
                UUID mobId = mob.getUUID();
                if (state.notifiedMobIds.contains(mobId)) continue;
                if (state.notifiedMobIds.size() >= 64) break;
                var mobBox = mob.getBoundingBox();
                if (mobBox == null) continue;
                Vec3 mobCenter = mobBox.getCenter();
                if (mobCenter == null) continue;
                double d2 = ArrowInvestigationHelper.segmentToPointSquaredDistance(state.prevPos, curr, mobCenter);
                if (d2 <= radiusSq) {
                    state.notifiedMobIds.add(mobId);
                    AwarenessServerEvents.recordNearMiss(mob);
                    if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.ARROW_INVESTIGATION)) {
                        SoundAttractMod.LOGGER.info(
                                "[ArrowInvestigation] Near-miss sweep matched: projectileId={}, type={}, mobId={}, distance={}, intervalTicks={}",
                                projectile.getId(), net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()),
                                mob.getId(), Math.sqrt(d2), checkInterval);
                    }
                    dispatchInvestigation(level, projectile, state.spawnPos, mob, "arrow_miss");
                }
            }

            if (!level.players().isEmpty()) {
                double speedBpt = Math.sqrt(speedSqr);
                double speedMps = speedBpt * 20.0;
                float pitch = 0.5f + (float)((speedMps / 60.0) * 1.5f);
                pitch = Math.max(0.5f, Math.min(2.0f, pitch));

                List<net.minecraft.world.entity.player.Player> players = level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, segmentAabb, p -> p.isAlive());
                for (net.minecraft.world.entity.player.Player player : players) {
                    if (state.notifiedMobIds.size() >= 64) break;
                    UUID playerId = player.getUUID();
                    if (state.notifiedMobIds.contains(playerId)) continue;
                    var boundingBox = player.getBoundingBox();
                    if (boundingBox == null) continue;
                    Vec3 playerCenter = boundingBox.getCenter();
                    if (playerCenter == null) continue;
                    double d2 = ArrowInvestigationHelper.segmentToPointSquaredDistance(state.prevPos, curr, playerCenter);
                    if (d2 <= radiusSq) {
                        state.notifiedMobIds.add(playerId);
                        level.playSound(player, player.getX(), player.getY(), player.getZ(),
                                net.minecraft.sounds.SoundEvents.ARROW_SHOOT,
                                net.minecraft.sounds.SoundSource.PLAYERS,
                                0.6F, pitch);
                    }
                }
            }

            state.prevPos = curr;
            return false;
        });

        COOLDOWNS.entrySet().removeIf(e -> e.getValue() == null || e.getValue() < now);
    }

    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (SoundAttractConfig.COMMON == null) return;
        if (!SoundAttractConfig.COMMON.enableArrowInvestigation.get()) return;
        Projectile projectile = event.getProjectile();
        if (projectile == null) return;
        if (!isWatchedProjectile(projectile)) return;
        if (!(projectile.level() instanceof ServerLevel level)) return;

        HitResult rt = event.getRayTraceResult();
        if (rt == null) return;
        Vec3 impact = rt.getLocation();
        if (impact == null) return;

        double r = SoundAttractConfig.COMMON.arrowImpactRadius.get();
        AABB scan = AABB.ofSize(impact, 2 * r, 2 * r, 2 * r);
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, scan, m -> m.isAlive() && hasSoundPursuitGoal(m));
        ArrowState state = ARROWS.get(projectile.getId());
        Vec3 spawnPos = state != null ? state.spawnPos : projectile.position();
        for (Mob mob : mobs) {
            dispatchInvestigation(level, projectile, spawnPos, mob, "arrow_impact");
        }
    }

    private void dispatchInvestigation(ServerLevel level, Projectile projectile, Vec3 spawnPos, Mob mob, String kind) {
        if (SoundAttractConfig.COMMON.arrowInvestigationRespectCurrentTarget.get()) {
            if (mob.getTarget() != null) return;
            BlockPos existing = getMobSoundTargetPos(mob);
            if (existing != null) return;
        }

        long now = level.getGameTime();
        Long cdEnd = COOLDOWNS.get(mob.getUUID());
        if (cdEnd != null && cdEnd > now) return;
        int cooldown = SoundAttractConfig.COMMON.arrowInvestigationCooldownTicks.get();
        COOLDOWNS.put(mob.getUUID(), now + cooldown);

        Vec3 origin = resolveOrigin(level, projectile, spawnPos);
        int off = SoundAttractConfig.COMMON.arrowInvestigationOffset.get();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double tx = origin.x + ArrowInvestigationHelper.randomOffset(rng, off);
        double tz = origin.z + ArrowInvestigationHelper.randomOffset(rng, off);
        int ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(tx), (int) Math.floor(tz));
        BlockPos target = BlockPos.containing(tx, ty, tz);

        String soundId = "soundattract:" + kind;
        double range = kind.equals("arrow_impact") ? 24.0 : 16.0;
        double weight = kind.equals("arrow_impact") ? 15.0 : 10.0;
        ResourceLocation loc = ResourceLocation.tryParse(soundId);
        if (loc != null && SoundAttractConfig.SOUND_DEFAULT_ENTRIES_CACHE != null) {
            SoundAttractConfig.SoundDefaultEntry entry = SoundAttractConfig.SOUND_DEFAULT_ENTRIES_CACHE.get(loc);
            if (entry != null) {
                range = entry.range();
                weight = entry.weight();
            }
        }

        UUID sourcePlayer = (projectile.getOwner() instanceof net.minecraft.world.entity.player.Player p)
                ? p.getUUID() : null;
        String dimKey = level.dimension().location().toString();
        int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
        try {
            SoundTracker.addVirtualSound(target, dimKey, range, weight, lifetime, sourcePlayer, "arrow_investigation");
        } catch (Throwable t) {
            SoundAttractMod.LOGGER.error("[ArrowInvestigation] addVirtualSound failed", t);
            return;
        }

        if (SoundAttractConfig.COMMON.debugLogging != null && SoundAttractConfig.COMMON.debugLogging.get()) {
            Entity owner = projectile.getOwner();
            SoundAttractMod.LOGGER.debug("[ArrowInvestigation] {} -> mob={} at {} (kind={})",
                    owner != null ? owner.getName().getString() : "unknown",
                    mob.getName().getString(), target, kind);
        }
    }

    private static boolean isWatchedProjectile(Projectile projectile) {
        EntityType<?> type = projectile.getType();
        if (type.is(INVESTIGATE_PROJECTILES)) return true;
        Set<EntityType<?>> cache = SoundAttractConfig.INVESTIGATE_PROJECTILE_TYPES_CACHE;
        return cache != null && cache.contains(type);
    }

    private static Vec3 resolveOrigin(ServerLevel level, Projectile projectile, Vec3 spawnPos) {
        Entity owner = projectile.getOwner();
        if (owner != null) {
            Vec3 p = owner.position();
            if (p != null) return p;
        }
        if (spawnPos != null) return spawnPos;

        Vec3 velocity = projectile.getDeltaMovement();
        if (velocity == null || velocity.lengthSqr() < 1.0e-6) return projectile.position();
        Vec3 dir = velocity.normalize();
        double maxDist = SoundAttractConfig.COMMON.arrowReverseExtrapolateMaxDistance.get();
        Vec3 start = projectile.position();
        Vec3 end = start.subtract(dir.scale(maxDist));
        BlockHitResult hit = level.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    private static boolean hasSoundPursuitGoal(Mob mob) {
        if (mob == null || mob.goalSelector == null) return false;
        for (WrappedGoal wg : mob.goalSelector.getAvailableGoals()) {
            Goal g = wg.getGoal();
            if (g instanceof AttractionGoal
                    || g instanceof LeaderAttractionGoal
                    || g instanceof FollowerEdgeRelayGoal) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos getMobSoundTargetPos(Mob mob) {
        if (mob == null || mob.goalSelector == null) return null;
        for (WrappedGoal wg : mob.goalSelector.getAvailableGoals()) {
            Goal g = wg.getGoal();
            if (g instanceof AttractionGoal ag) {
                return ag.getTargetSoundPos();
            }
            if (g instanceof LeaderAttractionGoal lag) {
                return lag.getTargetSoundPos();
            }
            if (g instanceof FollowerEdgeRelayGoal feg) {
                return feg.getTargetSoundPos();
            }
        }
        return null;
    }

    public static final class ArrowState {
        Vec3 prevPos;
        public final Vec3 spawnPos;
        public final long spawnTick;
        public final Set<UUID> notifiedMobIds = ConcurrentHashMap.newKeySet();

        public UUID shooterUuid;
        public boolean isMobShooter = false;
        public long lastScentTick = -1;
        public double distanceSinceLastScent = 0.0;

        public ArrowState(Vec3 spawn, long spawnTick, UUID shooter, boolean mobShooter) {
            this.prevPos = spawn;
            this.spawnPos = spawn;
            this.spawnTick = spawnTick;
            this.shooterUuid = shooter;
            this.isMobShooter = mobShooter;
        }
    }
}
