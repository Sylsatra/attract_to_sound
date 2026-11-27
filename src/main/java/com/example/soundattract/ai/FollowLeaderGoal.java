package com.example.soundattract.ai;

import com.example.soundattract.config.DebugLoggingConfig;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.pathfinder.Path;

public class FollowLeaderGoal extends Goal {
    private static final Map<Mob, LeaderPursuit> LEADER_PURSUITS = new WeakHashMap<>();

    private record LeaderPursuit(long tick, boolean active, BlockPos position, double weight, boolean overridesTarget) {}

    private final Mob mob;
    private final double moveSpeed;
    private Mob leader;
    private Vec3 lastPos = null;
    private int stuckTicks = 0;
    private int stuckThreshold = com.example.soundattract.config.SoundAttractConfig.COMMON.scanCooldownTicks.get();
    private int dynamicTickCounter = 0;
    private Vec3 lastRandomDest = null;
    private boolean hasPickedDest = false;
    private boolean isSpreadingOut = false;
    private BlockPos lastAnchorPos = null;
    private long nextSightingMoveTick;
    private Path ownedClueTrailPath;
    private boolean clueTrailMovement;

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
        if (LivingWallCoordinator.isControlled(this.mob)) {
            return false;
        }
        if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
            if (!shouldMemberDropTargetForHighWeightSound()) return false;
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.MOB_GROUPS)) {
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
        BlockPos sighting = GroupTargetSharingManager.getFollowPosition(mob, leader);
        if (sighting != null) return mob.blockPosition().distSqr(sighting) > 4.0;
        BlockPos trail = GroupTargetSharingManager.getClueTrailPosition(mob, leader);
        if (trail != null) return mob.blockPosition().distSqr(trail) > 4.0;
        boolean smartEdge = com.example.soundattract.config.SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        if (smartEdge && MobGroupManager.isEdgeMob(mob)) return false;
        return leaderPursuit(leader).active();
    }

    @Override
    public boolean canContinueToUse() {
        if (LivingWallCoordinator.isControlled(this.mob)) {
            return false;
        }
        if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
            if (!shouldMemberDropTargetForHighWeightSound()) return false;
            this.mob.setTarget(null);
        }
        if (leader == null || !leader.isAlive() || MobGroupManager.getLeader(mob) != leader) return false;

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
        BlockPos sighting = GroupTargetSharingManager.getFollowPosition(mob, leader);
        if (sighting != null) return mob.blockPosition().distSqr(sighting) > 4.0;
        BlockPos trail = GroupTargetSharingManager.getClueTrailPosition(mob, leader);
        if (trail != null) return mob.blockPosition().distSqr(trail) > 4.0;
        return leaderPursuit(leader).active();
    }

    @Override
    public void tick() {
        if (LivingWallCoordinator.isControlled(this.mob)) {
            return;
        }
        if (leader == null) return;

        boolean debug = DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.MOB_GROUPS);

        if (RaidManager.isRaidTicking(leader)) {
            if (debug) {
                com.example.soundattract.SoundAttractMod.LOGGER.info(
                    "[FollowLeaderGoal][RAID Rally] Mob {} rallying to leader {}",
                    mob.getName().getString(), leader.getName().getString());
            }
            if (!mob.isSprinting()) mob.setSprinting(true);
            double sprintMult = com.example.soundattract.config.SoundAttractConfig.COMMON.groupSprintMultiplier.get();
            com.example.soundattract.pathfinding.PursuitNavigation.maybeMoveTo(
                    mob, leader.position(), moveSpeed * sprintMult);
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
                                moved = com.example.soundattract.pathfinding.PursuitNavigation.maybeMoveTo(mob, new Vec3(fx, fy, fz), finalSpeed);
                            }
                        }
                    }
                    if (!moved) {
                        com.example.soundattract.pathfinding.PursuitNavigation.maybeMoveTo(
                            mob, lastRandomDest, finalSpeed
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

        BlockPos sighting = GroupTargetSharingManager.getFollowPosition(mob, leader);
        if (sighting != null) {
            long now = mob.level().getGameTime();
            if (now >= nextSightingMoveTick && mob.level().hasChunkAt(sighting)
                    && mob.blockPosition().distSqr(sighting) > 4.0) {
                nextSightingMoveTick = now + 8 + Math.floorMod(mob.getId(), 5);
                com.example.soundattract.pathfinding.PursuitNavigation.maybeMoveTo(
                        mob, Vec3.atBottomCenterOf(sighting), moveSpeed);
            }
            return;
        }

        BlockPos clueTrail = GroupTargetSharingManager.getClueTrailPosition(mob, leader);
        if (clueTrail != null) {
            clueTrailMovement = true;
            Path current = mob.getNavigation().getPath();
            if (!mob.getNavigation().isDone()) {
                if (current != ownedClueTrailPath) ownedClueTrailPath = null;
                return;
            }
            ownedClueTrailPath = null;
            long now = mob.level().getGameTime();
            if (now >= nextSightingMoveTick && mob.blockPosition().distSqr(clueTrail) > 4.0) {
                nextSightingMoveTick = now + 12 + Math.floorMod(mob.getId(), 9);
                if (com.example.soundattract.pathfinding.PursuitNavigation.maybeMoveTo(
                        mob, Vec3.atBottomCenterOf(clueTrail), moveSpeed)) {
                    Path issued = mob.getNavigation().getPath();
                    ownedClueTrailPath = issued != null && !mob.getNavigation().isDone() ? issued : null;
                }
            }
            return;
        }
        if (clueTrailMovement) {
            if (ownedClueTrailPath != null && mob.getNavigation().getPath() == ownedClueTrailPath) {
                mob.getNavigation().stop();
            }
            ownedClueTrailPath = null;
            clueTrailMovement = false;
        }

        LeaderPursuit pursuit = leaderPursuit(leader);
        if (!pursuit.active()) return;
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

        BlockPos soundPos = pursuit.position();
        double soundWeight = pursuit.weight();
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
                            moved = com.example.soundattract.pathfinding.PursuitNavigation.maybeMoveTo(mob, new Vec3(fx, fy, fz), finalSpeed);
                        }
                    }
                }
                if (!moved) {
                    com.example.soundattract.pathfinding.PursuitNavigation.maybeMoveTo(
                        mob, lastRandomDest, finalSpeed
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
        if (clueTrailMovement) {
            if (ownedClueTrailPath != null && mob.getNavigation().getPath() == ownedClueTrailPath) {
                mob.getNavigation().stop();
            }
        } else {
            mob.getNavigation().stop();
        }
        ownedClueTrailPath = null;
        clueTrailMovement = false;
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


    private boolean shouldMemberDropTargetForHighWeightSound() {
        double threshold = com.example.soundattract.config.SoundAttractConfig.COMMON
                .highSoundWeightTargetOverride.get();
        if (threshold <= 0) return false;

        Mob leader = MobGroupManager.getLeader(mob);
        if (leader == null || leader == mob) return false;

        if (RaidManager.isRaidTicking(leader) || RaidManager.isRaidAdvancing(leader)) return false;

        return leaderPursuit(leader).overridesTarget();
    }

    private static LeaderPursuit leaderPursuit(Mob leader) {
        long now = leader.level().getGameTime();
        LeaderPursuit previous = LEADER_PURSUITS.get(leader);
        if (previous != null && previous.tick() == now) return previous;
        boolean selected = false;
        boolean active = false;
        boolean overridesTarget = false;
        BlockPos position = null;
        double weight = -1.0;
        for (WrappedGoal wrapped : leader.goalSelector.getAvailableGoals()) {
            if (!wrapped.isRunning()) continue;
            if (wrapped.getGoal() instanceof AttractionGoal goal) {
                overridesTarget |= goal.isHighWeightOverrideActive();
                if (!selected) {
                    selected = true;
                    active = goal.isPursuingSound() || goal.isHighWeightOverrideActive();
                    position = goal.getTargetSoundPos();
                    weight = goal.getCurrentTargetWeight();
                }
            } else if (wrapped.getGoal() instanceof LeaderAttractionGoal goal) {
                overridesTarget |= goal.isHighWeightOverrideActive();
                if (!selected) {
                    selected = true;
                    active = true;
                    position = goal.getTargetSoundPos();
                    weight = goal.getCurrentTargetWeight();
                }
            }
        }
        LeaderPursuit result = new LeaderPursuit(now, active, position, weight, overridesTarget);
        LEADER_PURSUITS.put(leader, result);
        return result;
    }
}
