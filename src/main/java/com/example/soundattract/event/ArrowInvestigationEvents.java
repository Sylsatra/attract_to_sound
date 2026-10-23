package com.example.soundattract.event;

import com.example.soundattract.Soundattract;
import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.ai.LeaderAttractionGoal;
import com.example.soundattract.ai.FollowerEdgeRelayGoal;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.mixin.MobAccessor;
import com.example.soundattract.tracking.SoundTracker;
import com.example.soundattract.util.ArrowInvestigationHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class ArrowInvestigationEvents {

    public static final TagKey<EntityType<?>> INVESTIGATE_PROJECTILES =
            TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath("soundattract", "investigate_projectiles"));

    private static final int MAX_TRACKED_ARROWS = 4096;
    private static final long MAX_ARROW_AGE_TICKS = 1200L;

    public static final Map<ArrowKey, ArrowState> ARROWS = new ConcurrentHashMap<>();
    private static final Cache<UUID, Long> COOLDOWNS = Caffeine.newBuilder()
            .maximumSize(8192)
            .expireAfterWrite(java.time.Duration.ofMinutes(10))
            .build();

    public static final UUID DISPENSER_SENTINEL_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public record ArrowKey(Identifier dimension, int entityId) {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (world instanceof ServerLevel) {
                onEntityJoin(entity, (ServerLevel) world);
            }
        });

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            onEntityLeave(entity);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> onServerStopping());

        ServerWorldEvents.UNLOAD.register((server, level) -> onLevelUnload(level));

        ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (level instanceof ServerLevel) {
                onServerTick((ServerLevel) level);
            }
        });
    }

    public static void onEntityJoin(Entity entity, ServerLevel level) {
        if (level.isClientSide()) return;
        if (SoundAttractConfig.COMMON == null) return;
        Entity e = entity;
        if (!(e instanceof Projectile projectile)) return;
        if (!isWatchedProjectile(projectile)) return;
        if (ARROWS.size() >= MAX_TRACKED_ARROWS) {
            if (SoundAttractConfig.COMMON.debugLogging != null && SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.debug("[ArrowInvestigation] ARROWS cap reached; dropping new projectile {}", projectile.getId());
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
        ARROWS.put(new ArrowKey(level.dimension().identifier(), projectile.getId()),
                new ArrowState(projectile.position(), now, shooterUuid, isMob));
    }

    public static void onEntityLeave(Entity entity) {
        if (entity != null && entity.level() != null) {
            ARROWS.remove(new ArrowKey(entity.level().dimension().identifier(), entity.getId()));
        }
    }

    public static void onLevelUnload(ServerLevel level) {
        if (level == null) return;
        Identifier dimension = level.dimension().identifier();
        ARROWS.keySet().removeIf(key -> key != null && dimension.equals(key.dimension()));
    }

    public static void onServerStopping() {
        ARROWS.clear();
        COOLDOWNS.invalidateAll();
    }

    public static void onServerTick(ServerLevel level) {
        if (SoundAttractConfig.COMMON == null) return;
        onServerTick(level, SoundAttractConfig.COMMON.enableArrowInvestigation.get(),
                SoundAttractConfig.COMMON.arrowNearMissRadius.get(),
                SoundAttractConfig.COMMON.arrowInvestigationRespectCurrentTarget.get(),
                SoundAttractConfig.COMMON.arrowInvestigationOffset.get());
    }

    /** Runs the same production scan with explicit controls for deterministic GameTests. */
    public static void onServerTickForTesting(ServerLevel level, double radius) {
        onServerTick(level, true, radius, false, 0);
    }

    /** Clears one mob's dispatch cooldown before a deterministic GameTest scan. */
    public static void clearInvestigationCooldownForTesting(UUID mobId) {
        if (mobId != null) COOLDOWNS.invalidate(mobId);
    }

    /** Replays a prior projectile segment in a deterministic GameTest scan. */
    public static void setPreviousPositionForTesting(ServerLevel level, int projectileId, Vec3 previousPosition) {
        ArrowState state = getStateForTesting(level, projectileId);
        if (state != null && previousPosition != null) state.prevPos = previousPosition;
    }

    public static boolean isTrackedForTesting(ServerLevel level, int projectileId) {
        return getStateForTesting(level, projectileId) != null;
    }

    public static ArrowState getStateForTesting(ServerLevel level, int projectileId) {
        if (level == null) return null;
        return ARROWS.get(new ArrowKey(level.dimension().identifier(), projectileId));
    }

    /** Reads the production cooldown deadline for deterministic GameTest assertions. */
    public static Long getInvestigationCooldownEndForTesting(UUID mobId) {
        return mobId == null ? null : COOLDOWNS.getIfPresent(mobId);
    }

    private static void onServerTick(ServerLevel level, boolean enabled, double radius,
                                     boolean respectCurrentTarget, int investigationOffset) {
        if (!enabled || SoundAttractConfig.COMMON == null) return;
        final double radiusSq = radius * radius;
        final long now = level.getGameTime();
        final int checkInterval = 4;

        ARROWS.entrySet().removeIf(entry -> {
            ArrowState state = entry.getValue();
            if (state == null) return true;
            ArrowKey key = entry.getKey();
            if (key == null || !level.dimension().identifier().equals(key.dimension())) return false;
            if (now - state.spawnTick > MAX_ARROW_AGE_TICKS) return true;

            Entity e = level.getEntity(key.entityId());
            if (!(e instanceof Projectile projectile)) return false;
            if (projectile.level() != level) return false;
            if (!projectile.isAlive()) {
                return true;
            }
            Vec3 velocity = projectile.getDeltaMovement();
            double speedSqr = velocity.lengthSqr();
            boolean stopped = projectile instanceof AbstractArrow && speedSqr < 1.0e-4;

            Vec3 curr = projectile.position();


            if (stopped && state.prevPos.distanceToSqr(curr) < 1.0e-8) return true;

            int arrowId = key.entityId();
            if (!stopped && (arrowId + (int)now) % checkInterval != 0) {
                return false;
            }



            double observerRadius = AwarenessServerEvents.alertScanRadius() + radius;
            double cullRadiusSq = observerRadius * observerRadius;
            boolean anyPlayerNearby = false;
            for (var player : level.players()) {
                if (player.isAlive() && ArrowInvestigationHelper.segmentToPointSquaredDistance(
                        state.prevPos, curr, player.position()) <= cullRadiusSq) {
                    anyPlayerNearby = true;
                    break;
                }
            }
            if (!anyPlayerNearby && level.players().size() > 0) {
                state.prevPos = curr;
                return stopped;
            }
            AABB segmentAabb = new AABB(state.prevPos, curr).inflate(radius + 1.0);

            List<Mob> mobs = level.getEntitiesOfClass(Mob.class, segmentAabb, m -> m.isAlive()
                    && !java.util.Objects.equals(m.getUUID(), state.shooterUuid)
                    && hasSoundPursuitGoal(m));
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
                    BlockPos investigationPos = dispatchInvestigation(level, projectile, state.spawnPos, mob,
                            "arrow_miss", respectCurrentTarget, investigationOffset);
                    if (investigationPos != null) state.lastInvestigationPos = investigationPos;
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
            return stopped;
        });

    }

    public static void onProjectileImpact(Projectile projectile, HitResult hitResult, ServerLevel level) {
        if (SoundAttractConfig.COMMON == null) return;
        investigateProjectileImpact(projectile, hitResult, level,
                SoundAttractConfig.COMMON.enableArrowInvestigation.get(),
                SoundAttractConfig.COMMON.arrowInvestigationRespectCurrentTarget.get(),
                SoundAttractConfig.COMMON.arrowInvestigationOffset.get());
    }

    /** Runs the production impact scan with deterministic policy controls for GameTests. */
    public static void onProjectileImpactForTesting(Projectile projectile, HitResult hitResult, ServerLevel level) {
        investigateProjectileImpact(projectile, hitResult, level, true, false, 0);
    }

    private static void investigateProjectileImpact(Projectile projectile, HitResult hitResult, ServerLevel level,
                                                    boolean enabled, boolean respectCurrentTarget,
                                                    int investigationOffset) {
        if (!enabled) return;
        if (projectile == null) return;
        if (!isWatchedProjectile(projectile)) return;

        HitResult rt = hitResult;
        if (rt == null) return;
        Vec3 impact = rt.getLocation();
        if (impact == null) return;

        double r = SoundAttractConfig.COMMON.arrowImpactRadius.get();
        AABB scan = AABB.ofSize(impact, 2 * r, 2 * r, 2 * r);
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, scan, m -> m.isAlive() && hasSoundPursuitGoal(m));
        ArrowState state = getStateForTesting(level, projectile.getId());
        Vec3 spawnPos = state != null ? state.spawnPos : projectile.position();
        for (Mob mob : mobs) {
            BlockPos investigationPos = dispatchInvestigation(level, projectile, spawnPos, mob,
                    "arrow_impact", respectCurrentTarget, investigationOffset);
            if (state != null && investigationPos != null) state.lastInvestigationPos = investigationPos;
        }
    }

    private static BlockPos dispatchInvestigation(ServerLevel level, Projectile projectile, Vec3 spawnPos,
                                                  Mob mob, String kind, boolean respectCurrentTarget,
                                                  int investigationOffset) {
        if (respectCurrentTarget) {
            if (mob.getTarget() != null) return null;
            BlockPos existing = getMobSoundTargetPos(mob);
            if (existing != null) return null;
        }

        long now = level.getGameTime();
        Long cdEnd = COOLDOWNS.getIfPresent(mob.getUUID());
        if (cdEnd != null && cdEnd > now) return null;
        int cooldown = SoundAttractConfig.COMMON.arrowInvestigationCooldownTicks.get();
        COOLDOWNS.put(mob.getUUID(), now + cooldown);

        Vec3 origin = resolveOrigin(level, projectile, spawnPos);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double tx = origin.x + ArrowInvestigationHelper.randomOffset(rng, investigationOffset);
        double tz = origin.z + ArrowInvestigationHelper.randomOffset(rng, investigationOffset);
        int ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(tx), (int) Math.floor(tz));
        BlockPos target = BlockPos.containing(tx, ty, tz);

        String soundId = "soundattract:" + kind;
        double range = kind.equals("arrow_impact") ? 24.0 : 16.0;
        double weight = kind.equals("arrow_impact") ? 15.0 : 10.0;
        Identifier loc = Identifier.tryParse(soundId);
        if (loc != null && SoundAttractConfig.SOUND_DEFAULT_ENTRIES_CACHE != null) {
            SoundAttractConfig.SoundDefaultEntry entry = SoundAttractConfig.SOUND_DEFAULT_ENTRIES_CACHE.get(loc);
            if (entry != null) {
                range = entry.range();
                weight = entry.weight();
            }
        }

        UUID sourcePlayer = (projectile.getOwner() instanceof net.minecraft.world.entity.player.Player p)
                ? p.getUUID() : null;
        String dimKey = level.dimension().identifier().toString();
        int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
        try {
            SoundTracker.addVirtualSound(target, dimKey, range, weight, lifetime, sourcePlayer, "arrow_investigation");
        } catch (Throwable t) {
            Soundattract.LOGGER.error("[ArrowInvestigation] addVirtualSound failed", t);
            return null;
        }

        if (SoundAttractConfig.COMMON.debugLogging != null && SoundAttractConfig.COMMON.debugLogging.get()) {
            Entity owner = projectile.getOwner();
            Soundattract.LOGGER.debug("[ArrowInvestigation] {} -> mob={} at {} (kind={})",
                    owner != null ? owner.getName().getString() : "unknown",
                    mob.getName().getString(), target, kind);
        }
        return target;
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
        if (mob == null) return false;
        MobAccessor accessor = (MobAccessor) mob;
        if (accessor.getGoalSelector() == null) return false;
        for (WrappedGoal wg : accessor.getGoalSelector().getAvailableGoals()) {
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
        if (mob == null) return null;
        MobAccessor accessor = (MobAccessor) mob;
        if (accessor.getGoalSelector() == null) return null;
        for (WrappedGoal wg : accessor.getGoalSelector().getAvailableGoals()) {
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
        /** Start of the flight segment not yet scanned for near misses. */
        Vec3 prevPos;
        /** Independent scent distance cursor; scent ticks must not consume the scan. */
        Vec3 prevScentPos;
        public final Vec3 spawnPos;
        public final long spawnTick;
        public final Set<UUID> notifiedMobIds = ConcurrentHashMap.newKeySet();
        /** Last origin investigate target accepted by dispatch, retained for diagnostics. */
        public volatile BlockPos lastInvestigationPos;

        public UUID shooterUuid;
        public boolean isMobShooter = false;
        public long lastScentTick = -1;
        public double distanceSinceLastScent = 0.0;

        public ArrowState(Vec3 spawn, long spawnTick, UUID shooter, boolean mobShooter) {
            this.prevPos = spawn;
            this.prevScentPos = spawn;
            this.spawnPos = spawn;
            this.spawnTick = spawnTick;
            this.shooterUuid = shooter;
            this.isMobShooter = mobShooter;
        }
    }
}
