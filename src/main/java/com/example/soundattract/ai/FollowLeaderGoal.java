package com.example.soundattract.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import java.util.EnumSet;
import java.util.stream.StreamSupport;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

public class FollowLeaderGoal extends Goal {
    private final Mob mob;
    private final double moveSpeed;
    private Mob leader;
    private AttractionGoal leaderAttractionGoal = null;
    private Goal leaderPursuitGoal = null;
    private Vec3 lastPos = null;
    private int stuckTicks = 0;
    private int stuckThreshold = com.example.soundattract.config.SoundAttractConfig.COMMON.scanCooldownTicks.get();
    private int dynamicTickCounter = 0;
    private Vec3 lastRandomDest = null;
    private boolean hasPickedDest = false;
    private boolean isSpreadingOut = false;
    private BlockPos lastAnchorPos = null;

    public FollowLeaderGoal(Mob mob, double moveSpeed) {
        this.mob = mob;
        this.moveSpeed = moveSpeed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    private double getGroupDistance() {
        return com.example.soundattract.config.SoundAttractConfig.COMMON.groupDistance.get(); 
    }

    @Override
    public boolean canUse() {
        if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
            if (!shouldMemberDropTargetForHighWeightSound()) return false;
            if (com.example.soundattract.config.SoundAttractConfig.COMMON.debugLogging.get()) {
                com.example.soundattract.SoundAttractMod.LOGGER.info(
                    "[FollowLeaderGoal] Member {} dropping target - leader is in high-weight sound override.",
                    mob.getName().getString());
            }
            this.mob.setTarget(null);
        }
        leader = MobGroupManager.getLeader(mob);
        if (leader == null || leader == mob) return false; 
        if (!leader.isAlive()) return false;


        if (RaidManager.isRaidTicking(leader)) {
            return true;
        }

        if (RaidManager.isRaidAdvancing(leader)) {
            return true;
        }


        if (this.mob.distanceToSqr(leader) > getGroupDistance() * getGroupDistance()) return false; 
        boolean smartEdge = com.example.soundattract.config.SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        if (smartEdge && MobGroupManager.isEdgeMob(mob)) return false;
        leaderAttractionGoal = null;
        leaderPursuitGoal = null;
        leader.goalSelector.getAvailableGoals().stream().filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning).forEach(goal -> {
            if (leaderPursuitGoal == null) {
                if (goal.getGoal() instanceof AttractionGoal ag) {
                    leaderAttractionGoal = ag;
                    leaderPursuitGoal = ag;
                } else if (goal.getGoal() instanceof LeaderAttractionGoal lag) {
                    leaderPursuitGoal = lag;
                }
            }
        });
        if (leaderPursuitGoal == null) return false;
        if (leaderPursuitGoal instanceof AttractionGoal ag) {
            if (!ag.isPursuingSound() && !ag.isHighWeightOverrideActive()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
            if (!shouldMemberDropTargetForHighWeightSound()) return false;
            this.mob.setTarget(null);
        }
        if (leader == null || !leader.isAlive()) return false;


        if (RaidManager.isRaidTicking(leader)) {
            return true;
        }

        if (RaidManager.isRaidAdvancing(leader)) {
            net.minecraft.core.BlockPos raidTarget = RaidManager.getRaidTarget(leader);
            if (raidTarget == null) return false;
            double arrivalDistance = com.example.soundattract.config.SoundAttractConfig.COMMON.arrivalDistance.get();
            if (this.mob.blockPosition().distSqr(raidTarget) < (arrivalDistance + 2.0) * (arrivalDistance + 2.0)) {
                return false;
            }
            return true;
        }

        if (this.mob.distanceToSqr(leader) > getGroupDistance() * getGroupDistance()) return false;
        leaderAttractionGoal = null;
        leaderPursuitGoal = StreamSupport.stream(
            leader.goalSelector.getAvailableGoals().stream().filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning).spliterator(), false)
            .map(WrappedGoal::getGoal)
            .filter(g -> (g instanceof AttractionGoal) || (g instanceof LeaderAttractionGoal))
            .findFirst()
            .orElse(null);

        if (leaderPursuitGoal == null) return false;
        if (leaderPursuitGoal instanceof AttractionGoal ag) {
            if (!ag.isPursuingSound() && !ag.isHighWeightOverrideActive()) {
                return false;
            }
        }
        return true;
    }



    @Override
    public void tick() {
        if (leader == null) return;

        boolean debug = com.example.soundattract.config.SoundAttractConfig.COMMON.debugLogging.get();


        if (RaidManager.isRaidTicking(leader)) {
            if (debug) {
                com.example.soundattract.SoundAttractMod.LOGGER.info(
                    "[FollowLeaderGoal][RAID Rally] Mob {} rallying to leader {}",
                    mob.getName().getString(), leader.getName().getString());
            }
            if (!mob.isSprinting()) mob.setSprinting(true);
            double sprintMult = com.example.soundattract.config.SoundAttractConfig.COMMON.groupSprintMultiplier.get();
            mob.getNavigation().moveTo(leader.getX(), leader.getY(), leader.getZ(), moveSpeed * sprintMult);
            return;
        }


        if (RaidManager.isRaidAdvancing(leader)) {
            int scanCooldown = com.example.soundattract.config.SoundAttractConfig.COMMON.scanCooldownTicks.get();
            int updateInterval = Math.max(1, scanCooldown / 2);
            dynamicTickCounter = (dynamicTickCounter + 1) % updateInterval;
            if (dynamicTickCounter != 0) return;

            BlockPos raidTarget = RaidManager.getRaidTarget(leader);
            if (raidTarget == null) return;
            double arrivalDistance = com.example.soundattract.config.SoundAttractConfig.COMMON.arrivalDistance.get();

            if (!mob.isSprinting()) mob.setSprinting(true);

            if (!hasPickedDest) {
                java.util.Random rand = new java.util.Random(
                    mob.getUUID().hashCode() ^ raidTarget.hashCode()
                );
                double angle = rand.nextDouble() * 2 * Math.PI;
                double radius = arrivalDistance * Math.sqrt(rand.nextDouble());
                double offsetX = Math.cos(angle) * radius;
                double offsetZ = Math.sin(angle) * radius;

                int destX = raidTarget.getX() + (int) Math.floor(offsetX);
                int destZ = raidTarget.getZ() + (int) Math.floor(offsetZ);
                int groundY = mob.level().getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    destX, destZ
                );

                double finalX = destX + 0.5;
                double finalY = groundY;
                double finalZ = destZ + 0.5;
                lastRandomDest = new Vec3(finalX, finalY, finalZ);
                hasPickedDest = true;
                if (debug) {
                    com.example.soundattract.SoundAttractMod.LOGGER.info(
                        "[FollowLeaderGoal][RAID Advance] Mob {} advancing to raid target area near {}",
                        mob.getName().getString(), raidTarget);
                }
            }

            if (lastRandomDest != null) {
                Vec3 curPos = mob.position();
                if (curPos.distanceToSqr(lastRandomDest) > 2.25) {
                    double sprintMult = com.example.soundattract.config.SoundAttractConfig.COMMON.groupSprintMultiplier.get();
                    double finalSpeed = moveSpeed * sprintMult;

                    boolean useFlowField = false;
                    try {
                        useFlowField = com.example.soundattract.config.SoundAttractConfig.COMMON.enableFlowField.get();
                    } catch (Throwable ignored) {}
                    int ffThreshold = 15;
                    try {
                        ffThreshold = com.example.soundattract.config.SoundAttractConfig.COMMON.flowFieldMobThreshold.get();
                    } catch (Throwable ignored) {}

                    boolean moved = false;
                    if (useFlowField) {
                        int groupSize = MobGroupManager.getFollowerCount(leader);
                        if (groupSize >= ffThreshold) {
                            Vec3 flowVec = com.example.soundattract.pathfinding.FlowFieldManager.getNextStep(mob, lastRandomDest);
                            if (flowVec != null) {
                                double fx = curPos.x + flowVec.x * 3.0;
                                double fy = curPos.y + flowVec.y * 3.0;
                                double fz = curPos.z + flowVec.z * 3.0;
                                moved = com.example.soundattract.pathfinding.NavLimiter.maybeMoveTo(mob, fx, fy, fz, finalSpeed);
                            }
                        }
                    }
                    if (!moved) {
                        com.example.soundattract.pathfinding.NavLimiter.maybeMoveTo(
                            mob, lastRandomDest.x, lastRandomDest.y, lastRandomDest.z, finalSpeed
                        );
                    }
                }

                if (lastPos != null && curPos.distanceToSqr(lastPos) < 0.04) {
                    stuckTicks++;
                    if (stuckTicks > stuckThreshold) {
                        hasPickedDest = false;
                        stuckTicks = 0;
                    }
                } else {
                    stuckTicks = 0;
                }
                lastPos = curPos;
            }
            return;
        }


        if (leaderPursuitGoal == null) return;
        if (leaderPursuitGoal instanceof AttractionGoal ag) {
            if (!ag.isPursuingSound() && !ag.isHighWeightOverrideActive()) {
                return;
            }
        }
        if (debug) {
            com.example.soundattract.SoundAttractMod.LOGGER.info(
                "[FollowLeaderGoal] Mob {} following leader {} (leader is pursuing sound)",
                mob.getName().getString(),
                leader.getName().getString()
            );
        }

        int scanCooldown = com.example.soundattract.config.SoundAttractConfig.COMMON.scanCooldownTicks.get();
        int updateInterval = Math.max(1, scanCooldown / 2);
        dynamicTickCounter = (dynamicTickCounter + 1) % updateInterval;
        if (dynamicTickCounter != 0) return;

        BlockPos soundPos = null;
        double soundWeight = -1.0;
        try {
            Object pursuit = (leaderPursuitGoal != null) ? leaderPursuitGoal : leaderAttractionGoal;
            if (pursuit == null) return;
            java.lang.reflect.Field f = pursuit.getClass().getDeclaredField("targetSoundPos");
            f.setAccessible(true);
            soundPos = (BlockPos) f.get(pursuit);

            try {
                java.lang.reflect.Field w = pursuit.getClass().getDeclaredField("currentTargetWeight");
                w.setAccessible(true);
                Object val = w.get(pursuit);
                if (val instanceof Number n) {
                    soundWeight = n.doubleValue();
                }
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
        }
        if (soundPos == null) return;

        double arrivalDistance = com.example.soundattract.config.SoundAttractConfig.COMMON.arrivalDistance.get();

        if (lastAnchorPos == null || !lastAnchorPos.equals(soundPos)) {
            lastAnchorPos = soundPos;
            hasPickedDest = false;
            lastRandomDest = null;
            isSpreadingOut = false;
            stuckTicks = 0;
        }

        double minWeightToSpreadOut = com.example.soundattract.config.SoundAttractConfig.COMMON.followLeaderMinSoundWeightToSpreadOut.get();

        if (!isSpreadingOut && soundPos != null) {
            double distToSound = mob.distanceToSqr(soundPos.getX() + 0.5, soundPos.getY(), soundPos.getZ() + 0.5);
            if (distToSound <= (arrivalDistance + 2.0) * (arrivalDistance + 2.0)) {
                if (soundWeight >= minWeightToSpreadOut) {
                    isSpreadingOut = true;
                    hasPickedDest = false;
                    lastRandomDest = null;
                    if (debug) {
                        com.example.soundattract.SoundAttractMod.LOGGER.info(
                            "[FollowLeaderGoal] Mob {} reached sound location at {}, starting to spread",
                            mob.getName().getString(), soundPos);
                    }
                }
            }
        }

        if (!isSpreadingOut && lastRandomDest != null && mob.position().distanceToSqr(lastRandomDest) <= 2.25) {
            if (soundWeight >= minWeightToSpreadOut) {
                isSpreadingOut = true;
                hasPickedDest = false;
                lastRandomDest = null;
            }
        }

        if (!hasPickedDest) {
            if (isSpreadingOut) {
                int dirIdx = Math.floorMod(mob.getUUID().hashCode() ^ soundPos.hashCode(), 4);
                int dx = 0;
                int dz = 0;
                switch (dirIdx) {
                    case 0: dx = 1; break;
                    case 1: dx = -1; break;
                    case 2: dz = 1; break;
                    default: dz = -1; break;
                }
                double spreadDistance = com.example.soundattract.config.SoundAttractConfig.COMMON.followLeaderSpreadOutDistance.get();
                if (spreadDistance <= 0.0) {
                    spreadDistance = Math.max(8.0, arrivalDistance * 2.0);
                }
                int step = (int) Math.round(spreadDistance);
                int destX = soundPos.getX() + dx * step;
                int destZ = soundPos.getZ() + dz * step;
                int groundY = mob.level().getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    destX, destZ
                );
                lastRandomDest = new Vec3(destX + 0.5, groundY, destZ + 0.5);
                hasPickedDest = true;
            } else {
                java.util.Random rand = new java.util.Random(
                    mob.getUUID().hashCode() ^ soundPos.hashCode()
                );
                double angle = rand.nextDouble() * 2 * Math.PI;
                double radius = arrivalDistance * Math.sqrt(rand.nextDouble());
                double offsetX = Math.cos(angle) * radius;
                double offsetZ = Math.sin(angle) * radius;

                int destX = soundPos.getX() + (int) Math.floor(offsetX);
                int destZ = soundPos.getZ() + (int) Math.floor(offsetZ);
                int groundY = mob.level().getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    destX, destZ
                );

                double finalX = destX + 0.5;
                double finalY = groundY;
                double finalZ = destZ + 0.5;
                lastRandomDest = new Vec3(finalX, finalY, finalZ);
                hasPickedDest = true;
            }
        }

        if (lastRandomDest != null) {
            Vec3 curPos = mob.position();
            if (curPos.distanceToSqr(lastRandomDest) > 2.25) {
                double finalSpeed = moveSpeed;

                boolean useFlowField = false;
                try {
                    useFlowField = com.example.soundattract.config.SoundAttractConfig.COMMON.enableFlowField.get();
                } catch (Throwable ignored) {}
                int ffThreshold = 15;
                try {
                    ffThreshold = com.example.soundattract.config.SoundAttractConfig.COMMON.flowFieldMobThreshold.get();
                } catch (Throwable ignored) {}

                boolean moved = false;
                if (useFlowField) {
                    int groupSize = MobGroupManager.getFollowerCount(leader);
                    if (groupSize >= ffThreshold) {
                        Vec3 flowVec = com.example.soundattract.pathfinding.FlowFieldManager.getNextStep(mob, lastRandomDest);
                        if (flowVec != null) {
                            double fx = curPos.x + flowVec.x * 3.0;
                            double fy = curPos.y + flowVec.y * 3.0;
                            double fz = curPos.z + flowVec.z * 3.0;
                            moved = com.example.soundattract.pathfinding.NavLimiter.maybeMoveTo(mob, fx, fy, fz, finalSpeed);
                        }
                    }
                }
                if (!moved) {
                    com.example.soundattract.pathfinding.NavLimiter.maybeMoveTo(
                        mob, lastRandomDest.x, lastRandomDest.y, lastRandomDest.z, finalSpeed
                    );
                }
            }

            if (lastPos != null && curPos.distanceToSqr(lastPos) < 0.04) {
                stuckTicks++;
                if (stuckTicks > stuckThreshold) {
                    hasPickedDest = false;
                    stuckTicks = 0;
                }
            } else {
                stuckTicks = 0;
            }
            lastPos = curPos;
        }

        

        if (!RaidManager.isRaidTicking(leader) && !RaidManager.isRaidAdvancing(leader) && mob.isSprinting()) {
            mob.setSprinting(false);
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        if (mob.isSprinting()) mob.setSprinting(false);
        leader = null;
        hasPickedDest = false;
        lastRandomDest = null;
        isSpreadingOut = false;
        lastAnchorPos = null;
    }
    public BlockPos getTargetSoundPos() {
        return this.lastAnchorPos;
    }

    /**
     * Returns true if the member mob's current leader is actively navigating to a
     * high-weight sound via the override mechanism. When true, the member should
     * drop its combat target and follow the leader.
     */
    private boolean shouldMemberDropTargetForHighWeightSound() {
        double threshold = com.example.soundattract.config.SoundAttractConfig.COMMON
                .highSoundWeightTargetOverride.get();
        if (threshold <= 0) return false;

        Mob leader = MobGroupManager.getLeader(mob);
        if (leader == null || leader == mob) return false;

        if (RaidManager.isRaidTicking(leader) || RaidManager.isRaidAdvancing(leader)) return false;

        return leader.goalSelector.getAvailableGoals().stream().filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning)
                .map(WrappedGoal::getGoal)
                .anyMatch(g ->
                        (g instanceof AttractionGoal ag && ag.isHighWeightOverrideActive()) ||
                        (g instanceof LeaderAttractionGoal lag && lag.isHighWeightOverrideActive()));
    }
}
