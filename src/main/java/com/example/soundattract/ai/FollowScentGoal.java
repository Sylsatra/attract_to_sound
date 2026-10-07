package com.example.soundattract.ai;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import com.example.soundattract.scents.ScentSourceType;
import com.example.soundattract.pathfinding.HazardClassifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import com.example.soundattract.ai.BlockBreakerManager;
import com.example.soundattract.ai.BlockBreakerPosGoal;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class FollowScentGoal extends Goal {
    private final PathfinderMob mob;
    private final ScentManager scentManager;
    private UUID targetScentOwner;
    private long lastScentTime;
    private Vec3 targetPos;
    private int ambushTimer;
    private boolean isAmbushing;

    private BlockPos lastPos = null;
    private int stuckTicks = 0;
    private BlockBreakerPosGoal blockBreakerGoal = null;

    private long pathfindingCooldownTo;
    private static long lastScanTick = -1;
    private static int scansThisTick = 0;

    public FollowScentGoal(PathfinderMob mob) {
        this.mob = mob;
        this.scentManager = mob.level().getCapability(ScentManager.INSTANCE).orElse(null);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public boolean isFollowingScent() {
        return targetPos != null && !isAmbushing;
    }

    public Vec3 getTargetPosition() {
        return targetPos;
    }

    @Override
    public boolean canUse() {
        if (LivingWallCoordinator.isControlled(mob)) return false;
        if (!SoundAttractConfig.COMMON.enableScentSystem.get()) return false;
        if (scentManager == null) return false;

        long currentTime = mob.level().getGameTime();
        if (currentTime < pathfindingCooldownTo) return false;

        if (SoundAttractConfig.COMMON.edgeMobSmartBehavior.get()) {
            net.minecraft.world.entity.Mob leader = com.example.soundattract.ai.MobGroupManager.getLeader(mob);
            if (leader != null && leader != mob) {
                if (!com.example.soundattract.ai.MobGroupManager.isEdgeMob(mob) &&
                    !com.example.soundattract.ai.MobGroupManager.isDeserter(mob)) {
                    return false;
                }
            }
        }

        if (mob.getRandom().nextInt(10) != 0) return false;

        if (mob.getTarget() != null && mob.getTarget().isAlive()) return false;

        if (currentTime != lastScanTick) {
            lastScanTick = currentTime;
            scansThisTick = 0;
        }

        if (scansThisTick >= SoundAttractConfig.COMMON.scentScanBudgetPerTick.get()) {
            return false;
        }

        boolean found = findScentTrail();
        if (found) {
            scansThisTick++;
        }
        return found;
    }

    @Override
    public boolean canContinueToUse() {
        if (LivingWallCoordinator.isControlled(mob)) return false;
        if (mob.getTarget() != null && mob.getTarget().isAlive()) return false;

        if (isAmbushing && ambushTimer <= 0) return false;

        return targetPos != null && mob.getNavigation().getPath() != null && !mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        this.ambushTimer = SoundAttractConfig.COMMON.scentAmbushDurationTicks.get();
        this.stuckTicks = 0;
        this.lastPos = null;
        if (!moveToTarget()) {
            this.targetPos = null;
        }
    }

    @Override
    public void tick() {
        if (LivingWallCoordinator.isControlled(mob)) return;
        if (targetPos == null) return;

        if (this.blockBreakerGoal != null && this.mob.goalSelector.getRunningGoals().anyMatch(g -> g.getGoal() == this.blockBreakerGoal)) {
            if (lastPos != null && mob.position().distanceToSqr(Vec3.atCenterOf(lastPos)) >= 1.0) {
                BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
                this.blockBreakerGoal = null;
                stuckTicks = 0;
                if (!moveToTarget()) {
                    this.targetPos = null;
                }
            } else {
                lastPos = mob.blockPosition();
            }
        } else {
            if (lastPos != null && mob.position().distanceToSqr(Vec3.atCenterOf(lastPos)) < 1.0) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
                if (this.blockBreakerGoal != null) {
                    BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
                    this.blockBreakerGoal = null;
                }
            }
            lastPos = mob.blockPosition();

            if (mob.distanceToSqr(targetPos) < 4.0) {
                stuckTicks = 0;
                if (!findScentTrail()) {
                    startAmbush();
                } else {
                    if (!moveToTarget()) {
                        this.targetPos = null;
                    }
                }
            } else if (stuckTicks > 60) {
                handleStuckMob();
            }
        }

        if (isAmbushing) {
            ambushTimer--;
        }
    }

    private void handleStuckMob() {
        if (SoundAttractConfig.COMMON.enableBlockBreaking.get() && this.blockBreakerGoal == null) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SCENT)) {
                SoundAttractMod.LOGGER.info("[FollowScentGoal] Mob {} is stuck moving to scent. Deploying BlockBreakerPosGoal.", mob.getName().getString());
            }
            double timeMultiplier = SoundAttractConfig.COMMON.blockBreakingTimeMultiplier.get();
            boolean toolOnly = SoundAttractConfig.COMMON.blockBreakingToolOnly.get();
            boolean properTool = SoundAttractConfig.COMMON.blockBreakingProperToolOnly.get();

            BlockPos dest = BlockPos.containing(targetPos);
            BlockBreakerPosGoal newGoal = new BlockBreakerPosGoal(this.mob, dest, timeMultiplier, toolOnly, properTool, properTool);
            BlockBreakerManager.scheduleAdd(this.mob, newGoal, 1);
            this.blockBreakerGoal = newGoal;
            return;
        }

        if (stuckTicks > 200) {
            ScentNode freshestNode = getFreshestNodeForTarget();
            if (freshestNode != null && freshestNode.getPosition().distanceToSqr(mob.position()) < 400.0) {
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SCENT)) {
                    SoundAttractMod.LOGGER.info("[FollowScentGoal] Mob {} stuck for >200 ticks near freshest node. Triggering ambush from outside.", mob.getName().getString());
                }
                startAmbush();
                this.targetPos = null;
            } else {
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SCENT)) {
                    SoundAttractMod.LOGGER.info("[FollowScentGoal] Mob {} stuck for >200 ticks mid-trail. Canceling scent tracking.", mob.getName().getString());
                }
                this.targetPos = null;
            }
            if (this.blockBreakerGoal != null) {
                BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
                this.blockBreakerGoal = null;
            }
        }
    }

    private ScentNode getFreshestNodeForTarget() {
        if (scentManager == null || targetScentOwner == null) return null;
        ChunkPos chunkPos = mob.chunkPosition();
        List<ScentNode> nodes = scentManager.getNodesInArea(chunkPos);
        long currentTime = mob.level().getGameTime();
        long maxDuration = SoundAttractConfig.COMMON.scentNodeDurationTicks.get();

        return nodes.stream()
            .filter(n -> n.getOwnerUUID().equals(targetScentOwner))
            .filter(n -> (currentTime - n.getTimestamp()) <= maxDuration)
            .max(Comparator.comparingLong(ScentNode::getTimestamp))
            .orElse(null);
    }

    @Override
    public void stop() {
        this.targetPos = null;
        this.ambushTimer = 0;
        this.isAmbushing = false;
        this.stuckTicks = 0;
        this.lastPos = null;
        if (this.blockBreakerGoal != null) {
            BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
            this.blockBreakerGoal = null;
        }
        super.stop();
    }

    private void startAmbush() {
        if (isAmbushing) return;
        isAmbushing = true;

        try {
            if (!SoundAttractConfig.COMMON.enableScentRaid.get()) return;
            if (targetPos == null) return;

            net.minecraft.core.BlockPos raidTarget = net.minecraft.core.BlockPos.containing(targetPos);
            long now = mob.level().getGameTime();

            boolean smartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
            if (smartEdge) {
                if (MobGroupManager.isEdgeMob(mob)) {
                    net.minecraft.world.entity.Mob leader = MobGroupManager.getLeader(mob);
                    if (leader != null && !RaidManager.isRaidTicking(leader) && !RaidManager.isRaidAdvancing(leader)) {
                        RaidManager.scheduleRaid(leader, raidTarget, now);
                        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SCENT)) {
                            SoundAttractMod.LOGGER.info("[FollowScentGoal] Edge mob {} triggered scent raid for leader {} at {}",
                                    mob.getName().getString(), leader.getName().getString(), raidTarget);
                        }
                    }
                }
            } else {
                net.minecraft.world.entity.Mob leader = MobGroupManager.getNearestLeader(mob);
                if (leader != null && !RaidManager.isRaidTicking(leader) && !RaidManager.isRaidAdvancing(leader)) {
                    RaidManager.scheduleRaid(leader, raidTarget, now);
                    if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SCENT)) {
                        SoundAttractMod.LOGGER.info("[FollowScentGoal] Mob {} triggered scent raid via nearest leader {} at {}",
                                mob.getName().getString(), leader.getName().getString(), raidTarget);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private boolean moveToTarget() {
        isAmbushing = false;
        HazardClassifier.markPursuer(mob);
        boolean moved = com.example.soundattract.pathfinding.PursuitNavigation.maybeMoveTo(mob, targetPos, 1.0);
        if (!moved) {
            this.pathfindingCooldownTo = mob.level().getGameTime() + 10;
        }
        return moved;
    }

    private boolean findScentTrail() {
        if (scentManager == null) return false;

        ChunkPos chunkPos = mob.chunkPosition();
        List<ScentNode> nodes = scentManager.getNodesInArea(chunkPos);

        if (nodes.isEmpty()) return false;

        long currentTime = mob.level().getGameTime();
        long maxDuration = SoundAttractConfig.COMMON.scentNodeDurationTicks.get();

        if (targetScentOwner == null) {

            ScentNode freshestNode = nodes.stream()
                .filter(n -> (currentTime - n.getTimestamp()) <= maxDuration)
                .filter(n -> n.getOwnerUUID() != null)
                .max(Comparator.comparingDouble(n -> n.getTimestamp() * getSourceTypeWeightMultiplier(n.getSourceType())))
                .orElse(null);

            if (freshestNode == null) return false;

            this.targetScentOwner = freshestNode.getOwnerUUID();


            ScentNode startNode = nodes.stream()
                .filter(n -> n.getOwnerUUID() != null)
                .filter(n -> n.getOwnerUUID().equals(targetScentOwner))
                .filter(n -> (currentTime - n.getTimestamp()) <= maxDuration)
                .min(Comparator.comparingDouble(n -> n.getPosition().distanceToSqr(mob.position())))
                .orElse(freshestNode);

            this.targetPos = startNode.getPosition();
            this.lastScentTime = startNode.getTimestamp();
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SCENT)) {
                SoundAttractMod.LOGGER.info("[FollowScentGoal] Mob {} acquired INITIAL scent trail for owner {} at {}.",
                    mob.getName().getString(), targetScentOwner, targetPos);
            }
            return true;
        } else {


            ScentNode nextNode = nodes.stream()
                .filter(n -> n.getOwnerUUID() != null)
                .filter(n -> n.getOwnerUUID().equals(targetScentOwner))
                .filter(n -> (currentTime - n.getTimestamp()) <= maxDuration)
                .filter(n -> n.getTimestamp() > lastScentTime)
                .min(Comparator.comparingLong(ScentNode::getTimestamp))
                .orElse(null);

            if (nextNode != null) {
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SCENT)) {
                    SoundAttractMod.LOGGER.info("[FollowScentGoal] Mob {} found NEXT scent node at {} (time: {}).",
                        mob.getName().getString(), nextNode.getPosition(), nextNode.getTimestamp());
                }
                this.targetPos = nextNode.getPosition();
                this.lastScentTime = nextNode.getTimestamp();
                return true;
            }
        }

        return false;
    }

    private static double getSourceTypeWeightMultiplier(ScentSourceType type) {
        if (type == null) return 1.0;
        return switch (type) {
            case PLAYER_WALK -> 1.0;
            case PLAYER_WIND -> 1.1;
            case ARROW_PATH, ARROW_ORIGIN -> 0.8;
            case MOB_PROJECTILE_PATH, MOB_PROJECTILE_ORIGIN -> 0.6;
        };
    }
}