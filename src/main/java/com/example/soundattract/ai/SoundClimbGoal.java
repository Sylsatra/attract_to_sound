package com.example.soundattract.ai;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.integration.relentlessundead.RelentlessUndeadIntegration;
import com.example.soundattract.mixin.MobAccessor;
import com.example.soundattract.quantified.QuantifiedCacheCompat;

import net.minecraft.core.BlockPos;
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

    private BlockPos targetSoundPos;
    private BlockPos lastKnownTargetPos = null;
    private double initialTargetY = 0.0D;
    private long nextPathCheckTick = 0;
    private int checkBestSoundTicker = 0;
    private boolean forceStop = false;

    public SoundClimbGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    private boolean isMobEligible(Entity entity) {
        if (!(entity instanceof Mob)) return false;
        return RelentlessUndeadIntegration.isMobEligibleForClimbing((Mob) entity);
    }
    


    private boolean isStandingOnHorde() {
        int loadFactor = Math.max(1, com.example.soundattract.runtime.DynamicScanCooldownManager.currentScanCooldownTicks / 5);
        int interval = 5 * loadFactor;
        
        long ttl = interval;

        Level world = this.mob.level();
        Vec3 selfPos = this.mob.position();
        AABB searchBoxUnder = (new AABB(selfPos.x, selfPos.y - 0.6D, selfPos.z, selfPos.x, selfPos.y - 0.2D, selfPos.z)).inflate(0.4D, 0.05D, 0.4D);
        
        List<Entity> mobsUnder;
        if (QuantifiedCacheCompat.isUsable()) {
           mobsUnder = QuantifiedCacheCompat.getCached(
               "SoundClimbGoal",
               "standing_on_" + this.mob.getId(),
               () -> world.getEntitiesOfClass(Entity.class, searchBoxUnder, this::isMobEligible),
               ttl, 
               10
           );
        } else {
           mobsUnder = world.getEntitiesOfClass(Entity.class, searchBoxUnder, this::isMobEligible);
        }
        
        return mobsUnder.stream().anyMatch(e -> e != this.mob);
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
        if (LivingWallCoordinator.isControlled(this.mob) || LivingWallCoordinator.shouldDeferSoundClimb(this.mob)) return false;
        if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
            return false;
        }

        if (!RelentlessUndeadIntegration.isMobEligibleForClimbing(this.mob)) {
            return false;
        }

        long currentTick = this.mob.level().getGameTime();
        if (currentTick < this.nextPathCheckTick && !this.isStandingOnHorde()) {
             return false;
        }

        BlockPos foundTarget = null;
        for (WrappedGoal wrapped : ((MobAccessor) this.mob).getGoalSelector().getAvailableGoals()) {
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
                return false;
            }
            this.nextPathCheckTick = currentTick + 15 + this.mob.getRandom().nextInt(15);
            return false;
        } else {
            if (!this.isStuckHorizontally(foundTarget) && !this.mob.horizontalCollision) {
               this.nextPathCheckTick = currentTick + 15 + this.mob.getRandom().nextInt(15);
               return false;
            }
            this.nextPathCheckTick = currentTick + 15 + this.mob.getRandom().nextInt(15);
            
            Level world = this.mob.level();
            Vec3 selfPos = this.mob.position();
            AABB searchBoxBase = (new AABB(selfPos.x, selfPos.y - 0.5D, selfPos.z, selfPos.x, selfPos.y, selfPos.z)).inflate(BASE_SEARCH_RADIUS);
            
            List<Entity> nearbyBase;
             if (QuantifiedCacheCompat.isUsable()) {
                nearbyBase = QuantifiedCacheCompat.getCached(
                    "SoundClimbGoal",
                    "nearby_base_" + this.mob.getId(),
                    () -> world.getEntitiesOfClass(Entity.class, searchBoxBase, e -> e != this.mob && isMobEligible(e) && e.onGround()),
                    5, 
                    20
                );
             } else {
                nearbyBase = world.getEntitiesOfClass(Entity.class, searchBoxBase, e -> e != this.mob && isMobEligible(e) && e.onGround());
             }

             if (nearbyBase.size() < 1) { 
               return false;
            }
            
            this.targetSoundPos = foundTarget;
            this.lastKnownTargetPos = foundTarget;
            this.initialTargetY = foundTarget.getY();
            
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                com.example.soundattract.Soundattract.LOGGER.info("SoundClimbGoal starting for {} at {} with target {}", 
                    this.mob, this.mob.blockPosition(), foundTarget);
            }
            return true;
        }
    }

    public void start() {
        this.forceStop = false;
        this.checkBestSoundTicker = 0;
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
        if (LivingWallCoordinator.isControlled(this.mob) || LivingWallCoordinator.shouldDeferSoundClimb(this.mob)) return false;
        if (this.forceStop) {
            return false;
        }
        if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
            return false;
        }
        
        if (this.targetSoundPos == null) return false;

        
        if (this.mob.getY() - this.initialTargetY > -0.2D) {
            return false;
        }
        
        if (!this.isStandingOnHorde()) {
             Path path = this.mob.getNavigation().createPath(this.targetSoundPos, 0);
             if (path != null && path.canReach() && path.getTarget().distSqr(this.targetSoundPos) <= 1.0D) {
                  return false;
             }
        }
        
        return true;
    }

    public void tick() {
        if (LivingWallCoordinator.isControlled(this.mob) || LivingWallCoordinator.shouldDeferSoundClimb(this.mob)) return;
        if (this.targetSoundPos == null) return;

        if (this.checkBestSoundTicker++ > 10) {
            this.checkBestSoundTicker = 0;
            com.example.soundattract.tracking.SoundTracker.SoundRecord best = com.example.soundattract.tracking.SoundTracker.findNearestSound(this.mob, this.mob.level(), this.mob.blockPosition(), this.mob.getEyePosition());
            if (best != null && !best.pos.equals(this.targetSoundPos)) {
                 if (SoundAttractConfig.COMMON.debugLogging.get()) {
                     com.example.soundattract.Soundattract.LOGGER.info("SoundClimbGoal aborting: found better sound at {}", best.pos);
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
           if (SoundAttractConfig.COMMON.debugLogging.get() && this.mob.tickCount % 20 == 0) {
                com.example.soundattract.Soundattract.LOGGER.info("SoundClimbGoal: {} standing on horde, climbing...", this.mob);
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
           
           if (!this.mob.isInWater() && !this.mob.hasEffect(MobEffects.JUMP_BOOST)) {
              this.mob.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 2, 0, false, false));
           }
        } else {
           if (this.mob.horizontalCollision && !this.mob.isInWater()) {
               Level world = this.mob.level();
               Vec3 selfPos = this.mob.position();
               AABB searchBoxBase = (new AABB(selfPos.x, selfPos.y - 0.5D, selfPos.z, selfPos.x, selfPos.y, selfPos.z)).inflate(BASE_SEARCH_RADIUS);
               
               List<Entity> nearby;
               if (QuantifiedCacheCompat.isUsable()) {
                   nearby = QuantifiedCacheCompat.getCached(
                       "SoundClimbGoal_JumpCheck",
                       "nearby_" + this.mob.getId(),
                       () -> world.getEntitiesOfClass(Entity.class, searchBoxBase, e -> e != this.mob && isMobEligible(e)),
                       5,
                       20
                   );
               } else {
                   nearby = world.getEntitiesOfClass(Entity.class, searchBoxBase, e -> e != this.mob && isMobEligible(e));
               }

               if (nearby.size() >= 2 && this.mob.getRandom().nextFloat() < 0.15F) {
                    this.mob.getJumpControl().jump();
               }
           }

           if (horizontalDistanceSq > HORIZONTAL_STOP_DISTANCE_SQ) {
               this.mob.getNavigation().moveTo(climbTargetPos.x, climbTargetPos.y, climbTargetPos.z, SoundAttractConfig.COMMON.mobMoveSpeed.get());
           } else {
               if (this.mob.getRandom().nextFloat() < 0.2F) {
                   this.mob.getNavigation().stop();
               }
           }
        }
    }
}
