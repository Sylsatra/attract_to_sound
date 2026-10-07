package com.example.soundattract.ai;

import com.example.soundattract.config.DebugLoggingConfig;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.integration.relentlessundead.RelentlessUndeadIntegration;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SoundClimbGoal extends Goal {
    private final Mob mob;
    private static final double BASE_SEARCH_RADIUS = 1.5D;
    private static final double ON_ZOMBIE_HORIZONTAL_TOLERANCE = 0.4D;
    private static final double MIN_VERTICAL_DIFFERENCE = 2.0D;
    private static final double HORIZONTAL_STOP_DISTANCE_SQ = 2.25D;
    private static final int STANDING_RESULT_TICKS = 3;
    private static final int SOUND_RECHECK_TICKS = 40;
    private static final int JUMP_QUERY_INTERVAL_TICKS = 10;
    private static final int MAX_SPATIAL_CACHE_ENTRIES = 4096;
    private static final Map<Level, SpatialFrameCache> SPATIAL_CACHES = new WeakHashMap<>();

    private static final class SpatialFrameCache {
        long tick = Long.MIN_VALUE;
        final Map<Long, Boolean> standing = new HashMap<>();
        final Map<Long, Integer> grounded = new HashMap<>();
        final Map<Long, Integer> nearby = new HashMap<>();

        void advance(long now) {
            if (tick == now) return;
            tick = now;
            standing.clear();
            grounded.clear();
            nearby.clear();
        }
    }

    private BlockPos targetSoundPos;
    private BlockPos lastKnownTargetPos = null;
    private double initialTargetY = 0.0D;
    private long nextPathCheckTick = 0;
    private int checkBestSoundTicker = 0;
    private boolean forceStop = false;
    private long standingCacheTick = Long.MIN_VALUE;
    private long standingCachePosition = Long.MIN_VALUE;
    private boolean standingCache;
    private long nextNavigationTick;
    private long nextEligibilityCheckTick;
    private long nextJumpQueryTick;

    public SoundClimbGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    private boolean isMobEligible(Entity entity) {
        if (!(entity instanceof Mob)) return false;
        return RelentlessUndeadIntegration.isMobEligibleForClimbing((Mob) entity);
    }


    private boolean isStandingOnHorde() {
        long now = this.mob.level().getGameTime();
        long positionKey = this.mob.blockPosition().asLong();
        if (this.standingCachePosition == positionKey
                && now - this.standingCacheTick >= 0L
                && now - this.standingCacheTick < STANDING_RESULT_TICKS) {
            return this.standingCache;
        }
        Level world = this.mob.level();
        Vec3 selfPos = this.mob.position();
        AABB searchBoxUnder = (new AABB(selfPos.x, selfPos.y - 0.6D, selfPos.z, selfPos.x, selfPos.y - 0.2D, selfPos.z)).inflate(0.4D, 0.05D, 0.4D);

        boolean mobsUnder = spatialBoolean(world, now, positionKey, searchBoxUnder);
        this.standingCacheTick = now;
        this.standingCachePosition = positionKey;
        this.standingCache = mobsUnder;
        return mobsUnder;
    }

    private boolean spatialBoolean(Level world, long now, long key, AABB box) {
        SpatialFrameCache cache = spatialCache(world, now);
        Boolean cached = cache.standing.get(key);
        if (cached != null) return cached;
        boolean result = world.getEntitiesOfClass(Entity.class, box,
                entity -> entity != this.mob && isMobEligible(entity)).stream().findAny().isPresent();
        if (cache.standing.size() < MAX_SPATIAL_CACHE_ENTRIES) cache.standing.put(key, result);
        return result;
    }

    private int spatialCount(Level world, long now, long key, AABB box, boolean groundedOnly) {
        SpatialFrameCache cache = spatialCache(world, now);
        Map<Long, Integer> values = groundedOnly ? cache.grounded : cache.nearby;
        Integer cached = values.get(key);
        if (cached != null) return cached;
        int result = world.getEntitiesOfClass(Entity.class, box,
                entity -> entity != this.mob && isMobEligible(entity) && (!groundedOnly || entity.onGround())).size();
        if (values.size() < MAX_SPATIAL_CACHE_ENTRIES) values.put(key, result);
        return result;
    }

    private static SpatialFrameCache spatialCache(Level world, long now) {
        synchronized (SPATIAL_CACHES) {
            SpatialFrameCache cache = SPATIAL_CACHES.computeIfAbsent(world, ignored -> new SpatialFrameCache());
            cache.advance(now);
            return cache;
        }
    }

    private int eligibilityInterval() {
        int dynamic = com.example.soundattract.runtime.DynamicScanCooldownManager.currentScanCooldownTicks;
        return Math.max(10, Math.min(40, dynamic));
    }

    private boolean isNearLava() {
        BlockPos pos = this.mob.blockPosition();
        return this.mob.level().getFluidState(pos).is(FluidTags.LAVA)
                || this.mob.level().getFluidState(pos.below()).is(FluidTags.LAVA)
                || this.mob.level().getFluidState(pos.north().below()).is(FluidTags.LAVA)
                || this.mob.level().getFluidState(pos.south().below()).is(FluidTags.LAVA)
                || this.mob.level().getFluidState(pos.east().below()).is(FluidTags.LAVA)
                || this.mob.level().getFluidState(pos.west().below()).is(FluidTags.LAVA);
    }

    private boolean isStuckHorizontally(BlockPos targetPos) {
        if (targetPos == null) {
            return false;
        } else {
            Path path = this.mob.getNavigation().createPath(targetPos, 0);
            if (path == null) {
                return true;
            } else if (!path.canReach()) {
                return true;
            } else {
                BlockPos endPos = path.getTarget();
                double pathEndToTargetDist = endPos != null ? endPos.distSqr(targetPos) : Double.MAX_VALUE;
                return pathEndToTargetDist > 1.0D;
            }
        }
    }

    public boolean canUse() {
        if (LivingWallCoordinator.isControlled(this.mob) || LivingWallCoordinator.shouldDeferSoundClimb(this.mob)) {
            return false;
        }
        if (this.isNearLava()) return false;
        if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
            return false;
        }

        if (!RelentlessUndeadIntegration.isMobEligibleForClimbing(this.mob)) {
            return false;
        }

        long currentTick = this.mob.level().getGameTime();
        if (currentTick < this.nextEligibilityCheckTick) return false;
        this.nextEligibilityCheckTick = currentTick + eligibilityInterval() + Math.floorMod(this.mob.getId(), 5);

        BlockPos foundTarget = null;
        for (WrappedGoal wrapped : this.mob.goalSelector.getAvailableGoals()) {
            Goal goal = wrapped.getGoal();
            if (goal instanceof AttractionGoal ag) {
                if (ag.getTargetSoundPos() != null) foundTarget = ag.getTargetSoundPos();
            } else if (goal instanceof LeaderAttractionGoal lag) {
                if (lag.getTargetSoundPos() != null) foundTarget = lag.getTargetSoundPos();
            } else if (goal instanceof FollowLeaderGoal flg) {
                if (flg.getTargetSoundPos() != null) foundTarget = flg.getTargetSoundPos();
            }
            if (foundTarget != null) break;
        }

        if (foundTarget == null) {
            return false;
        }

        if (this.isStandingOnHorde()) {
            this.targetSoundPos = foundTarget;
            this.lastKnownTargetPos = foundTarget;
            this.initialTargetY = foundTarget.getY();
            return true;
        }

        boolean ignoreHeight = RelentlessUndeadIntegration.getEffectiveZombiesIgnoreHeight();
        double distY = foundTarget.getY() - this.mob.getY();
        boolean isTargetElevated = distY >= MIN_VERTICAL_DIFFERENCE;

        if (!ignoreHeight && !isTargetElevated) {
            if (!this.isStuckHorizontally(foundTarget) && !this.mob.horizontalCollision) {
                this.nextPathCheckTick = currentTick + 15 + this.mob.getRandom().nextInt(15);
                return false;
            }
        } else {
            if (!this.isStuckHorizontally(foundTarget) && !this.mob.horizontalCollision) {
               this.nextPathCheckTick = currentTick + 15 + this.mob.getRandom().nextInt(15);
               return false;
            }
        }
        this.nextPathCheckTick = currentTick + 15 + this.mob.getRandom().nextInt(15);

        Level world = this.mob.level();
        Vec3 selfPos = this.mob.position();
        AABB searchBoxBase = (new AABB(selfPos.x, selfPos.y - 0.5D, selfPos.z, selfPos.x, selfPos.y, selfPos.z)).inflate(BASE_SEARCH_RADIUS);

        int nearbyBaseCount = spatialCount(world, currentTick, this.mob.blockPosition().asLong(),
                searchBoxBase, true);

        if (nearbyBaseCount < 1) {
           return false;
        }

        this.targetSoundPos = foundTarget;
        this.lastKnownTargetPos = foundTarget;
        this.initialTargetY = foundTarget.getY();

        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SOUND_ATTRACTION)) {
            com.example.soundattract.SoundAttractMod.LOGGER.info("SoundClimbGoal starting for {} at {} with target {}",
                this.mob, this.mob.blockPosition(), foundTarget);
        }
        return true;
    }

    public void start() {
        this.forceStop = false;
        this.checkBestSoundTicker = Math.floorMod(this.mob.getId(), SOUND_RECHECK_TICKS);
    }

    public void stop() {
        super.stop();
        this.mob.getNavigation().stop();
        if (LivingWallCoordinator.isControlled(this.mob) || LivingWallCoordinator.shouldDeferSoundClimb(this.mob)) {
            Vec3 movement = this.mob.getDeltaMovement();
            this.mob.setDeltaMovement(movement.x, Math.min(0.0D, movement.y), movement.z);
        }
    }

    public boolean canContinueToUse() {
        if (LivingWallCoordinator.isControlled(this.mob) || LivingWallCoordinator.shouldDeferSoundClimb(this.mob)) {
            return false;
        }
        if (this.isNearLava()) return false;
        if (this.forceStop) {
            return false;
        }
        if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
            return false;
        }

        if (this.targetSoundPos == null) return false;


        if (!this.isStandingOnHorde() && this.mob.getY() - this.initialTargetY > -0.2D) {
            return false;
        }

        long currentTick = this.mob.level().getGameTime();
        if (!this.isStandingOnHorde() && currentTick >= this.nextPathCheckTick) {
             this.nextPathCheckTick = currentTick + 20L;
             Path path = this.mob.getNavigation().createPath(this.targetSoundPos, 0);
             if (path != null && path.canReach() && path.getTarget().distSqr(this.targetSoundPos) <= 1.0D) {
                  return false;
             }
        }

        return true;
    }

    public void tick() {
        if (LivingWallCoordinator.isControlled(this.mob) || LivingWallCoordinator.shouldDeferSoundClimb(this.mob)) {
            return;
        }
        if (this.targetSoundPos == null) return;

        if (this.checkBestSoundTicker++ >= SOUND_RECHECK_TICKS) {
            this.checkBestSoundTicker = 0;
            com.example.soundattract.tracking.SoundTracker.SoundRecord best = com.example.soundattract.tracking.SoundTracker.findNearestSound(this.mob, this.mob.level(), this.mob.blockPosition(), this.mob.getEyePosition());
            if (best != null && !best.pos.equals(this.targetSoundPos)) {
                 if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SOUND_ATTRACTION)) {
                     com.example.soundattract.SoundAttractMod.LOGGER.info("SoundClimbGoal aborting: found better sound at {}", best.pos);
                 }
                 this.forceStop = true;
                 return;
            }
        }

        Vec3 climbTargetPos = Vec3.atCenterOf(this.targetSoundPos);

        double dx_horiz = climbTargetPos.x - this.mob.getX();
        double dz_horiz = climbTargetPos.z - this.mob.getZ();
        double horizontalDistanceSq = dx_horiz * dx_horiz + dz_horiz * dz_horiz;

        float yaw = (float)(Math.atan2(dz_horiz, dx_horiz) * 180.0D / Math.PI) - 90.0F;
        this.mob.setYRot(yaw);
        this.mob.yBodyRot = yaw;

        if (this.isStandingOnHorde()) {
           if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SOUND_ATTRACTION) && this.mob.tickCount % 20 == 0) {
                com.example.soundattract.SoundAttractMod.LOGGER.info("SoundClimbGoal: {} standing on horde, climbing...", this.mob);
           }
           this.mob.getNavigation().stop();
           double xzVel = 0.05D;
           double yVel = this.mob.isInWater() ? 0.2D : 0.08D;

           Vec3 selfPos = this.mob.position();
           Vec3 direction = climbTargetPos.subtract(selfPos).normalize();
           double forwardX = direction.x * xzVel;
           double forwardZ = direction.z * xzVel;

           this.mob.setDeltaMovement(this.mob.getDeltaMovement().x + forwardX, yVel, this.mob.getDeltaMovement().z + forwardZ);
           this.mob.fallDistance = 0.0F;

           if (!this.mob.isInWater() && !this.mob.hasEffect(MobEffects.JUMP)) {
              this.mob.addEffect(new MobEffectInstance(MobEffects.JUMP, 2, 0, false, false));
           }
        } else {
           long currentTick = this.mob.level().getGameTime();
           if (this.mob.horizontalCollision && !this.mob.isInWater()
                   && currentTick >= this.nextJumpQueryTick) {
               this.nextJumpQueryTick = currentTick + JUMP_QUERY_INTERVAL_TICKS
                       + Math.floorMod(this.mob.getId(), 4);
               Level world = this.mob.level();
               Vec3 selfPos = this.mob.position();
               AABB searchBoxBase = (new AABB(selfPos.x, selfPos.y - 0.5D, selfPos.z, selfPos.x, selfPos.y, selfPos.z)).inflate(BASE_SEARCH_RADIUS);
               int nearbyCount = spatialCount(world, currentTick, this.mob.blockPosition().asLong(),
                       searchBoxBase, false);

               if (nearbyCount >= 2 && this.mob.getRandom().nextFloat() < 0.15F) {
                    this.mob.getJumpControl().jump();
               }
           }

            if (horizontalDistanceSq > HORIZONTAL_STOP_DISTANCE_SQ
                    && (currentTick >= this.nextNavigationTick || this.mob.getNavigation().isDone())) {
                this.nextNavigationTick = currentTick + 10L + Math.floorMod(this.mob.getId(), 5);
                this.mob.getNavigation().moveTo(climbTargetPos.x, climbTargetPos.y, climbTargetPos.z, SoundAttractConfig.COMMON.mobMoveSpeed.get());
           } else {
               if (this.mob.getRandom().nextFloat() < 0.2F) {
                   this.mob.getNavigation().stop();
               }
           }
        }
    }
}