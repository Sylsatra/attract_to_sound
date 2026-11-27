
package com.example.soundattract.ai;

import java.util.EnumSet;
import java.util.UUID;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.phys.AABB;

public final class MeleeJumpAttemptGoal extends Goal {
    private static final int CHECK_INTERVAL_TICKS = 4;
    private static final int STATIONARY_TICKS = 12;
    private static final int ATTEMPT_COOLDOWN_TICKS = 30;
    private static final int BLOCKED_RECHECK_TICKS = 16;
    private static final int ATTACK_COOLDOWN_TICKS = 20;
    private static final int JUMP_START_GRACE_TICKS = 5;
    private static final int MAX_JUMP_TRACK_TICKS = 30;

    private static final double MIN_TARGET_RISE = 0.5;
    private static final double MAX_TARGET_RISE = 6.0;
    private static final double MAX_HORIZONTAL_DISTANCE = 2.5;
    private static final double MIN_PROGRESS_DISTANCE = 0.20;
    private static final double JUMP_CLEARANCE = 1.25;

    private final Mob mob;

    private long nextCheckTick;
    private long stationarySince;
    private long jumpStartedTick;
    private long lastJumpAttackTick = -ATTACK_COOLDOWN_TICKS;

    private UUID watchedTarget;
    private UUID readyTarget;
    private UUID jumpingTarget;

    private double bestHorizontalDistance;

    private boolean jumping;
    private boolean leftGround;

    public MeleeJumpAttemptGoal(Mob mob) {
        this.mob = mob;
        this.nextCheckTick = mob.level().getGameTime()
                + Math.floorMod(mob.getUUID().hashCode(), CHECK_INTERVAL_TICKS);
        setFlags(EnumSet.of(Flag.JUMP));
    }

    public static boolean supports(Mob mob) {
        if (!(mob.getNavigation() instanceof GroundPathNavigation)
                || mob instanceof RangedAttackMob
                || mob.goalSelector == null) {
            return false;
        }

        for (WrappedGoal goal : mob.goalSelector.getAvailableGoals()) {
            if (goal.getGoal() instanceof MeleeAttackGoal) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean canUse() {
        readyTarget = null;

        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }

        long now = level.getGameTime();

        if (now < nextCheckTick) {
            return false;
        }

        nextCheckTick = now + CHECK_INTERVAL_TICKS;

        LivingEntity target = mob.getTarget();

        if (!canJumpToward(target)) {
            watchedTarget = null;
            return false;
        }

        UUID targetId = target.getUUID();

        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();

        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        if (!targetId.equals(watchedTarget)) {
            watchedTarget = targetId;
            bestHorizontalDistance = horizontalDistance;
            stationarySince = now;
            return false;
        }

        if (horizontalDistance < bestHorizontalDistance - MIN_PROGRESS_DISTANCE) {
            bestHorizontalDistance = horizontalDistance;
            stationarySince = now;
            return false;
        }

        if (now - stationarySince < STATIONARY_TICKS) {
            return false;
        }

        if (hasOtherMovementOwner()
                || level.getEntity(targetId) != target
                || !mob.canAttack(target)
                || mob.isAlliedTo(target)
                || mob.onClimbable()
                || !hasHeadroom(level)) {
            nextCheckTick = now + BLOCKED_RECHECK_TICKS;
            watchedTarget = null;
            return false;
        }

        readyTarget = targetId;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!jumping || !mob.isAlive()) {
            return false;
        }

        LivingEntity target = mob.getTarget();

        if (target == null
                || !target.isAlive()
                || !target.getUUID().equals(jumpingTarget)
                || target.level() != mob.level()
                || !mob.canAttack(target)
                || mob.isAlliedTo(target)) {
            return false;
        }

        long elapsed = mob.level().getGameTime() - jumpStartedTick;

        if (elapsed > MAX_JUMP_TRACK_TICKS) {
            return false;
        }

        if (leftGround) {
            return !mob.onGround();
        }

        return elapsed <= JUMP_START_GRACE_TICKS;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        LivingEntity target = mob.getTarget();

        if (target != null
                && target.getUUID().equals(readyTarget)
                && canJumpToward(target)) {
            jumping = true;
            leftGround = false;
            jumpingTarget = target.getUUID();
            jumpStartedTick = mob.level().getGameTime();

            mob.getJumpControl().jump();

            nextCheckTick = jumpStartedTick
                    + ATTEMPT_COOLDOWN_TICKS;
        }

        readyTarget = null;
        watchedTarget = null;
    }

    @Override
    public void tick() {
        if (!jumping || !(mob.level() instanceof ServerLevel level)) {
            return;
        }

        LivingEntity target = mob.getTarget();

        if (target == null
                || !target.isAlive()
                || !target.getUUID().equals(jumpingTarget)) {
            return;
        }

        if (mob.onGround()) {
            return;
        }

        leftGround = true;

        if (!mob.canAttack(target)
                || mob.isAlliedTo(target)
                || !mob.isWithinMeleeAttackRange(target)
                || !mob.hasLineOfSight(target)) {
            return;
        }

        if (hasActiveMeleeGoal()) {
            return;
        }

        long now = level.getGameTime();

        if (now - lastJumpAttackTick < ATTACK_COOLDOWN_TICKS) {
            return;
        }

        lastJumpAttackTick = now;

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        mob.swing(InteractionHand.MAIN_HAND);
        mob.doHurtTarget(target);
    }

    @Override
    public void stop() {
        jumping = false;
        leftGround = false;
        jumpingTarget = null;
        readyTarget = null;
    }

    private boolean canJumpToward(LivingEntity target) {
        if (target == null
                || !target.isAlive()
                || !mob.isAlive()
                || target.level() != mob.level()
                || !mob.onGround()
                || mob.isInWater()
                || mob.isInLava()
                || mob.isPassenger()
                || mob.isVehicle()
                || !(mob.getNavigation() instanceof GroundPathNavigation)) {
            return false;
        }

        double rise = target.getY() - mob.getY();

        if (rise < MIN_TARGET_RISE || rise > MAX_TARGET_RISE) {
            return false;
        }

        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();

        return dx * dx + dz * dz
                <= MAX_HORIZONTAL_DISTANCE * MAX_HORIZONTAL_DISTANCE;
    }

    private boolean hasActiveMeleeGoal() {
        for (WrappedGoal goal : mob.goalSelector.getAvailableGoals()) {
            if (goal.isRunning()
                    && goal.getGoal() instanceof MeleeAttackGoal) {
                return true;
            }
        }

        return false;
    }

    private boolean hasOtherMovementOwner() {
        if (LivingWallCoordinator.isControlled(mob)) {
            return true;
        }

        Mob leader = MobGroupManager.getLeader(mob);

        if (leader == null) {
            leader = mob;
        }

        if (RaidManager.isRaidTicking(leader)
                || RaidManager.isRaidAdvancing(leader)) {
            return true;
        }

        for (WrappedGoal goal : mob.goalSelector.getAvailableGoals()) {
            if (goal.isRunning()
                    && goal.getFlags().contains(Flag.MOVE)
                    && !(goal.getGoal() instanceof MeleeAttackGoal)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasHeadroom(ServerLevel level) {
        AABB bounds = mob.getBoundingBox();

        AABB overhead = new AABB(
                bounds.minX,
                bounds.maxY,
                bounds.minZ,
                bounds.maxX,
                bounds.maxY + JUMP_CLEARANCE,
                bounds.maxZ
        );

        if (overhead.maxY >= level.getMaxBuildHeight()) {
            return false;
        }

        int minChunkX = SectionPos.blockToSectionCoord(
                Mth.floor(overhead.minX) - 1
        );

        int maxChunkX = SectionPos.blockToSectionCoord(
                Mth.floor(overhead.maxX) + 1
        );

        int minChunkZ = SectionPos.blockToSectionCoord(
                Mth.floor(overhead.minZ) - 1
        );

        int maxChunkZ = SectionPos.blockToSectionCoord(
                Mth.floor(overhead.maxZ) + 1
        );

        ServerChunkCache chunks = level.getChunkSource();

        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                if (!chunks.hasChunk(x, z)) {
                    return false;
                }
            }
        }

        return !level.getBlockCollisions(mob, overhead)
                .iterator().hasNext();
    }
}
