package com.example.soundattract.ai;

import java.util.EnumSet;

import com.example.soundattract.runtime.DynamicScanCooldownManager;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.event.SoundAttractionEvents;
import com.example.soundattract.tracking.SoundTracker;
import com.example.soundattract.config.SoundAttractConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public class FollowerEdgeRelayGoal extends Goal {

    private final Mob mob;
    private final double moveSpeed;

    private BlockPos targetSoundPos;
    private double currentTargetWeight = -1.0;
    private int scanCooldownCounter = 0;

    private long lastSeenSoundSequence = -1L;

    private BlockPos lastPos = null;
    private int stuckTicks = 0;

    private SoundTracker.SoundRecord cachedSound = null;
    private boolean highWeightOverrideActive = false;

    private BlockBreakerPosGoal blockBreakerGoal = null;

    private long cacheTick = -1L;
    private SoundTracker.SoundRecord soundResultCache = null;

    private enum EdgeMobState {
        GOING_TO_SOUND, RETURNING_TO_LEADER
    }
    private EdgeMobState edgeMobState = null;
    private boolean foundPlayerOrHit = false;
    private int edgeArrivalTicks = 0;
    private static final int EDGE_WAIT_TICKS = 15;

    private Mob cachedReturnLeader = null;

    private int returnLogCooldown = 0;

    private boolean raidScheduled = false;

    private BlockPos lastIssuedNavTarget = null;
    private int repathCooldown = 0;
    private static final int REPATH_COOLDOWN_TICKS = 6;

    public FollowerEdgeRelayGoal(Mob mob, double moveSpeed) {
        this.mob = mob;
        this.moveSpeed = SoundAttractConfig.COMMON.mobMoveSpeed.get();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    private int scanCooldownTicks() {
        return DynamicScanCooldownManager.currentScanCooldownTicks;
    }

    private boolean isMobEligible() {
        java.util.Set<net.minecraft.world.entity.EntityType<?>> attractedTypes = SoundAttractionEvents.getCachedAttractedEntityTypes();
        boolean byType = attractedTypes.contains(this.mob.getType());
        boolean hasProfile = SoundAttractConfig.getMatchingProfile(this.mob) != null;
        boolean isCustomNpcs = SoundAttractionEvents.isCustomNpcsMob(this.mob);
        return byType || hasProfile || isCustomNpcs;
    }

    private void refreshScanCooldownOnNewSoundForCustomNpcs() {
        if (!SoundAttractionEvents.isCustomNpcsMob(this.mob)) {
            return;
        }
        long seq = SoundTracker.getSoundSequence();
        if (seq != this.lastSeenSoundSequence) {
            this.lastSeenSoundSequence = seq;
            this.scanCooldownCounter = 0;
        }
    }

    private double getArrivalDistance() {
        return SoundAttractConfig.COMMON.arrivalDistance.get();
    }

    private SoundTracker.SoundRecord getCachedNearestSound() {
        long currentTick = this.mob.level().getGameTime();
        if (this.cacheTick == currentTick) {
            return this.soundResultCache;
        }
        this.cacheTick = currentTick;
        this.soundResultCache = SoundTracker.findNearestSound(
            this.mob,
            this.mob.level(),
            this.mob.blockPosition(),
            this.mob.getEyePosition(),
            this.cachedSound != null ? this.cachedSound.soundId : null
        );
        return this.soundResultCache;
    }



    private void moveToThrottled(BlockPos dest, double speed, boolean force) {
        if (dest == null) return;
        if (force) {
            this.mob.getNavigation().moveTo(dest.getX(), dest.getY(), dest.getZ(), speed);
            this.lastIssuedNavTarget = dest;
            this.repathCooldown = REPATH_COOLDOWN_TICKS;
            return;
        }
        boolean destChanged = (this.lastIssuedNavTarget == null) || !this.lastIssuedNavTarget.equals(dest);
        boolean navDoneOrStuck = this.mob.getNavigation().isDone() || this.mob.getNavigation().isStuck();
        if (this.repathCooldown > 0 && !destChanged && !navDoneOrStuck) {
            this.repathCooldown--;
            return;
        }

        double distSq = dest.distSqr(this.mob.blockPosition());
        double thresholdSq = 4.0;
        if (destChanged || navDoneOrStuck || distSq > thresholdSq) {
            this.mob.getNavigation().moveTo(dest.getX(), dest.getY(), dest.getZ(), speed);
            this.lastIssuedNavTarget = dest;
            this.repathCooldown = REPATH_COOLDOWN_TICKS;
        }
    }

    @Override
    public boolean canUse() {
        if (this.mob.isVehicle() || this.mob.isSleeping()) {
            return false;
        }

        if (SoundAttractConfig.COMMON.skipSoundScanWhenHasTarget.get()
                && this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
            return false;
        }

        if (!isMobEligible()) {
            return false;
        }

        refreshScanCooldownOnNewSoundForCustomNpcs();

        boolean smartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        if (!smartEdge) {
            return false;
        }
        Mob leader = MobGroupManager.getLeader(mob);
        boolean isDeserter = MobGroupManager.isDeserter(mob);
        if (leader == mob && !isDeserter) {
            return false;
        }
        if (!(MobGroupManager.isEdgeMob(mob) || isDeserter)) {
            return false;
        }
        if (scanCooldownCounter > 0) {
            scanCooldownCounter--;
            return false;
        }
        scanCooldownCounter = scanCooldownTicks();

        SoundTracker.SoundRecord newSound = getCachedNearestSound();
        if (newSound == null) {
            return false;
        }

        if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
            double threshold = SoundAttractConfig.COMMON.highSoundWeightTargetOverride.get();
            if (threshold > 0 && newSound.weight >= threshold && isEligibleForOverride()) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] Mob {} dropping target for high-weight sound ({}).", this.mob.getName().getString(), newSound.weight);
                }
                this.mob.setTarget(null);
                this.highWeightOverrideActive = true;
                this.edgeMobState = EdgeMobState.GOING_TO_SOUND;
                this.foundPlayerOrHit = false;
                this.edgeArrivalTicks = 0;
                this.cachedReturnLeader = null;
                this.returnLogCooldown = 0;
                this.raidScheduled = false;
            } else {
                return false;
            }
        }

        this.targetSoundPos = newSound.pos;
        this.currentTargetWeight = newSound.weight;
        this.cachedSound = newSound;
        this.edgeMobState = EdgeMobState.GOING_TO_SOUND;
        this.foundPlayerOrHit = false;
        this.edgeArrivalTicks = 0;
        this.cachedReturnLeader = null;
        this.returnLogCooldown = 0;
        this.raidScheduled = false;
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] START GOING_TO_SOUND: mob={} leader={} edgeMob={} deserter={} soundPos={} weight={}",
                    mob.getName().getString(),
                    (leader != null ? leader.getName().getString() : "null"),
                    MobGroupManager.isEdgeMob(mob),
                    isDeserter,
                    this.targetSoundPos,
                    String.format("%.2f", this.currentTargetWeight));
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
            double threshold = SoundAttractConfig.COMMON.highSoundWeightTargetOverride.get();
            SoundTracker.SoundRecord bestNow = getCachedNearestSound();
            if (threshold > 0 && bestNow != null && bestNow.weight >= threshold && isEligibleForOverride()) {
                this.mob.setTarget(null);
                this.highWeightOverrideActive = true;
            } else if (SoundAttractConfig.COMMON.skipSoundScanWhenHasTarget.get()) {
                return false;
            }
        }

        if (this.edgeMobState == EdgeMobState.RETURNING_TO_LEADER) {
            return true;
        }
        if (!isMobEligible() || this.mob.isVehicle() || this.mob.isSleeping()) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] canContinueToUse -> FALSE (suppressed or ineligible) for mob {}.", mob.getName().getString());
            }
            return false;
        }
        if (this.targetSoundPos == null) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] canContinueToUse -> FALSE (targetSoundPos null) for mob {}.", mob.getName().getString());
            }
            return false;
        }
        boolean smartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        Mob leader = MobGroupManager.getLeader(mob);
        if (leader != mob && smartEdge && mob.position().distanceToSqr(Vec3.atCenterOf(targetSoundPos)) < getArrivalDistance() * getArrivalDistance()) {
            boolean isDeserterHere = MobGroupManager.isDeserter(mob);
            if (isDeserterHere) {

                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] Arrived at sound; deserter will NOT return. Ending goal for mob {}.", mob.getName().getString());
                }
                return false;
            }

            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] Arrived at sound; holding to return for mob {}.", mob.getName().getString());
            }
            return true;
        }
        SoundTracker.SoundRecord bestSoundNow = getCachedNearestSound();
        if (bestSoundNow == null) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] canContinueToUse -> FALSE (no best sound now) for mob {}.", mob.getName().getString());
            }
            return false;
        }
        if (bestSoundNow.pos.equals(this.targetSoundPos)) {
            this.cachedSound = bestSoundNow;
            this.currentTargetWeight = bestSoundNow.weight;
            return true;
        }
        double switchRatio = SoundAttractConfig.COMMON.soundSwitchRatio.get();
        if (bestSoundNow.weight > this.currentTargetWeight * switchRatio) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] canContinueToUse -> FALSE (switching to stronger sound {} > {}) for mob {}.",
                        String.format("%.2f", bestSoundNow.weight),
                        String.format("%.2f", this.currentTargetWeight),
                        mob.getName().getString());
            }
            return false;
        }
        return true;
    }

    @Override
    public void stop() {
        boolean smartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        Mob leader = MobGroupManager.getLeader(mob);
        this.mob.getNavigation().stop();

        if (this.mob.isSprinting()) this.mob.setSprinting(false);
        if (leader != mob && smartEdge && edgeMobState == EdgeMobState.GOING_TO_SOUND) {
            edgeMobState = EdgeMobState.RETURNING_TO_LEADER;
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] stop(): follower {} transitioning to RETURNING_TO_LEADER.", mob.getName().getString());
            }
            edgeArrivalTicks = 0;
        } else {
            edgeMobState = null;
        }
        foundPlayerOrHit = false;
        edgeArrivalTicks = 0;
        cachedReturnLeader = null;
        returnLogCooldown = 0;
        raidScheduled = false;

        this.lastIssuedNavTarget = null;
        this.repathCooldown = 0;
        this.highWeightOverrideActive = false;
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] stop(): mob {} cleanup complete (state reset).", mob.getName().getString());
        }
    }

    @Override
    public void tick() {

        if (this.blockBreakerGoal != null && this.mob.goalSelector.getAvailableGoals().stream().filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning).anyMatch(g -> g.getGoal() == this.blockBreakerGoal)) {
            SoundTracker.SoundRecord bestPossibleSound = getCachedNearestSound();
            if (bestPossibleSound != null && this.cachedSound != null && !areSoundsEffectivelySame(bestPossibleSound, this.cachedSound)) {
                double switchRatio = SoundAttractConfig.COMMON.soundSwitchRatio.get();
                if (bestPossibleSound.weight > this.cachedSound.weight * switchRatio) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] {} found a better sound ({} > {}). Stopping block breaking.", this.mob.getName().getString(), bestPossibleSound.weight, this.cachedSound.weight);
                    }
                    BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
                    this.blockBreakerGoal = null;
                    this.stop();
                    return;
                }
            }
            if (lastPos != null && mob.position().distanceToSqr(Vec3.atCenterOf(lastPos)) >= 1.0) {
                BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
                this.blockBreakerGoal = null;
                this.stuckTicks = 0;
                this.lastPos = this.mob.blockPosition();
            }
            return;
        }


        if (this.edgeMobState != EdgeMobState.RETURNING_TO_LEADER) {
            SoundTracker.SoundRecord fresh = getCachedNearestSound();
            double switchRatio = SoundAttractConfig.COMMON.soundSwitchRatio.get();
            if (fresh != null) {
                if (this.targetSoundPos != null && fresh.pos.equals(this.targetSoundPos)) {
                    this.cachedSound = fresh;
                    this.currentTargetWeight = fresh.weight;
                } else {
                    boolean isBetterByWeight = fresh.weight > this.currentTargetWeight * switchRatio;
                    boolean isTieButCloser = false;
                    if (!isBetterByWeight && this.targetSoundPos != null && Math.abs(fresh.weight - this.currentTargetWeight) < 0.001) {
                        double freshDistSq = fresh.pos.distSqr(this.mob.blockPosition());
                        double currentDistSq = this.targetSoundPos.distSqr(this.mob.blockPosition());
                        isTieButCloser = freshDistSq < currentDistSq;
                    }
                    if (isBetterByWeight || isTieButCloser) {
                        this.targetSoundPos = fresh.pos;
                        this.cachedSound = fresh;
                        this.currentTargetWeight = fresh.weight;
                        this.edgeMobState = EdgeMobState.GOING_TO_SOUND;
                        this.foundPlayerOrHit = false;
                        this.mob.getNavigation().stop();
                        this.lastIssuedNavTarget = null;
                    }
                }
            }
        }
        if (targetSoundPos == null && this.edgeMobState != EdgeMobState.RETURNING_TO_LEADER) {
            return;
        }

        if (scanCooldownCounter > 0) {
            scanCooldownCounter--;
        }

        boolean smartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        Mob leader = MobGroupManager.getLeader(mob);


        if (lastPos != null && mob.position().distanceToSqr(Vec3.atCenterOf(lastPos)) < 1.0) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
            if (this.blockBreakerGoal != null) {
                BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
                this.blockBreakerGoal = null;
            }
        }
        if (stuckTicks == 0) {
            lastPos = mob.blockPosition();
        }
        if (stuckTicks >= 40 && SoundAttractConfig.COMMON.enableBlockBreaking.get()) {
            if (SoundAttractConfig.COMMON.debugLogging.get() && stuckTicks == 40) {
                SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal DEBUG] {} truly stuck. isStuck(): {}", mob.getName().getString(), this.mob.getNavigation().isStuck());
            }
            if (this.blockBreakerGoal == null) {

                Mob destLeader = (this.edgeMobState == EdgeMobState.RETURNING_TO_LEADER)
                        ? (this.cachedReturnLeader != null ? this.cachedReturnLeader : leader)
                        : null;
                BlockPos destination = (this.edgeMobState == EdgeMobState.RETURNING_TO_LEADER && destLeader != null)
                        ? destLeader.blockPosition()
                        : this.targetSoundPos;
                if (this.mob.getNavigation().isDone()) {
                    if (destination != null && this.mob.blockPosition().distSqr(destination) > 4.0) {
                        stuckTicks++;
                        if (stuckTicks >= 20 && SoundAttractConfig.COMMON.enableBlockBreaking.get()) {
                            if (this.blockBreakerGoal == null) {
                                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                                    if (this.edgeMobState == EdgeMobState.RETURNING_TO_LEADER && destLeader != null) {
                                        SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] {} returning to leader {} but stuck. Deploying BlockBreakerPosGoal toward leader.", mob.getName().getString(), destLeader.getName().getString());
                                    } else {
                                        SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] {} navigation DONE and far from goal. Deploying BlockBreakerPosGoal.", mob.getName().getString());
                                    }
                                }
                                double timeMultiplier = SoundAttractConfig.COMMON.blockBreakingTimeMultiplier.get();
                                boolean toolOnly = SoundAttractConfig.COMMON.blockBreakingToolOnly.get();
                                boolean properTool = SoundAttractConfig.COMMON.blockBreakingProperToolOnly.get();
                                BlockBreakerPosGoal newGoal = new BlockBreakerPosGoal(this.mob, destination, timeMultiplier, toolOnly, properTool, properTool);
                                BlockBreakerManager.scheduleAdd(this.mob, newGoal, 1);
                                this.blockBreakerGoal = newGoal;
                            }
                        }
                    } else {
                        stuckTicks = 0;
                    }
                } else {
                    stuckTicks = 0;
                    if (this.blockBreakerGoal != null) {
                        BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
                        this.blockBreakerGoal = null;
                    }
                }
            }
        }



        boolean isDeserter = MobGroupManager.isDeserter(this.mob);
        if (this.edgeMobState != EdgeMobState.RETURNING_TO_LEADER && (!smartEdge || (leader == mob && !isDeserter))) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal DEBUG] Fallback direct move to sound for {} (leader==mob: {}, smartEdge: {}, deserter: {}).", mob.getName().getString(), leader == mob, smartEdge, isDeserter);
            }
            moveToThrottled(this.targetSoundPos, this.moveSpeed, false);
            return;
        }


        if (edgeMobState == null) {
            edgeMobState = EdgeMobState.GOING_TO_SOUND;
            moveToThrottled(this.targetSoundPos, this.moveSpeed, true);
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                        "Follower {} (leader: {}) starting to move to sound at {} (EdgeMobState: GOING_TO_SOUND)",
                        mob.getName().getString(),
                        leader.getName().getString(),
                        targetSoundPos
                );
            }
        }

        if (edgeMobState == EdgeMobState.GOING_TO_SOUND) {
            moveToThrottled(this.targetSoundPos, this.moveSpeed, false);

            if (com.example.soundattract.event.StealthDetectionEvents.consumeSuppressedEdgeDetection(this.mob)) {
                foundPlayerOrHit = true;

                if (!MobGroupManager.isDeserter(this.mob) && !raidScheduled) {
                    Mob raidLeader = MobGroupManager.getLeader(this.mob);
                    boolean usedFallback = false;
                    if (raidLeader == this.mob) {
                        raidLeader = MobGroupManager.getNearestLeader(this.mob);
                        usedFallback = true;
                    }
                    if (raidLeader != null
                            && SoundAttractConfig.COMMON.enableSoundRaid.get()
                            && !com.example.soundattract.ai.RaidManager.isRaidTicking(raidLeader)
                            && !com.example.soundattract.ai.RaidManager.isRaidAdvancing(raidLeader)) {
                        com.example.soundattract.ai.RaidManager.scheduleRaid(
                                raidLeader,
                                this.targetSoundPos,
                                this.mob.level().getGameTime()
                        );
                        raidScheduled = true;
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            if (usedFallback) {
                                SoundAttractMod.LOGGER.info(
                                        "[FollowerEdgeRelayGoal] {} consumed suppression signal and scheduled RAID for nearest leader {} at {} (fallback, no immediate relay).",
                                        mob.getName().getString(), raidLeader.getName().getString(), this.targetSoundPos);
                            } else {
                                SoundAttractMod.LOGGER.info(
                                        "[FollowerEdgeRelayGoal] {} consumed suppression signal and scheduled RAID for leader {} at {} (no immediate relay).",
                                        mob.getName().getString(), raidLeader.getName().getString(), this.targetSoundPos);
                            }
                        }
                    } else if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info(
                                "[FollowerEdgeRelayGoal] {} suppression signal but no suitable leader found to schedule RAID.",
                                mob.getName().getString());
                    }
                }

                this.mob.getNavigation().stop();
                if (this.blockBreakerGoal != null) {
                    BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
                    this.blockBreakerGoal = null;
                }
                Mob chosenLeaderSupp = MobGroupManager.getLeader(this.mob);
                if (chosenLeaderSupp == this.mob) {
                    chosenLeaderSupp = MobGroupManager.getNearestLeader(this.mob);
                }
                this.cachedReturnLeader = chosenLeaderSupp;
                edgeMobState = EdgeMobState.RETURNING_TO_LEADER;
                this.lastIssuedNavTarget = null;
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    if (this.cachedReturnLeader != null) {
                        SoundAttractMod.LOGGER.info(
                                "[FollowerEdgeRelayGoal] {} suppression signal -> RETURNING_TO_LEADER (leader: {}).",
                                mob.getName().getString(), this.cachedReturnLeader.getName().getString()
                        );
                    } else {
                        SoundAttractMod.LOGGER.info(
                                "[FollowerEdgeRelayGoal] {} suppression signal but no leader to return to.", mob.getName().getString()
                        );
                    }
                }
                edgeArrivalTicks = 0;
                returnLogCooldown = 0;
                return;
            }
            if (mob.position().distanceToSqr(Vec3.atCenterOf(targetSoundPos)) < getArrivalDistance() * getArrivalDistance()) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info(
                            "Follower {} arrived at sound location {}. Waiting for {} ticks.",
                            mob.getName().getString(),
                            targetSoundPos,
                            EDGE_WAIT_TICKS
                    );
                }
                edgeArrivalTicks++;
                if (edgeArrivalTicks >= EDGE_WAIT_TICKS || foundPlayerOrHit) {

                    if (foundPlayerOrHit && !MobGroupManager.isDeserter(this.mob)) {
                        Mob raidLeader = MobGroupManager.getLeader(this.mob);
                        boolean usedFallbackArr = false;
                        if (raidLeader == this.mob) {
                            raidLeader = MobGroupManager.getNearestLeader(this.mob);
                            usedFallbackArr = true;
                        }
                        if (raidLeader != null && !raidScheduled
                                && SoundAttractConfig.COMMON.enableSoundRaid.get()
                                && !com.example.soundattract.ai.RaidManager.isRaidTicking(raidLeader)
                                && !com.example.soundattract.ai.RaidManager.isRaidAdvancing(raidLeader)) {
                            com.example.soundattract.ai.RaidManager.scheduleRaid(raidLeader, this.targetSoundPos, this.mob.level().getGameTime());
                            raidScheduled = true;
                            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                                if (usedFallbackArr) {
                                    SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] {} scheduled RAID for nearest leader {} at {} (arrival-phase, no immediate relay).",
                                            mob.getName().getString(), raidLeader.getName().getString(), this.targetSoundPos);
                                } else {
                                    SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] {} scheduled RAID for leader {} at {} (arrival-phase, no immediate relay).",
                                            mob.getName().getString(), raidLeader.getName().getString(), this.targetSoundPos);
                                }
                            }
                        } else if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] {} arrival-phase but no suitable leader found to schedule RAID.", mob.getName().getString());
                        }
                    }

                    if (MobGroupManager.isDeserter(this.mob)) {
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] {} is a deserter; will NOT return after arrival. Ending goal.", mob.getName().getString());
                        }
                        targetSoundPos = null;
                        edgeMobState = null;
                        cachedReturnLeader = null;
                        return;
                    }


                    this.mob.getNavigation().stop();
                    if (this.blockBreakerGoal != null) {
                        BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
                        this.blockBreakerGoal = null;
                    }
                    Mob chosenLeader = MobGroupManager.getLeader(this.mob);
                    if (chosenLeader == this.mob) {
                        chosenLeader = MobGroupManager.getNearestLeader(this.mob);
                    }
                    this.cachedReturnLeader = chosenLeader;
                    edgeMobState = EdgeMobState.RETURNING_TO_LEADER;
                    this.lastIssuedNavTarget = null;
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        if (this.cachedReturnLeader != null) {
                            SoundAttractMod.LOGGER.info(
                                    "Follower {} transitioning to RETURNING_TO_LEADER (leader: {}).",
                                    mob.getName().getString(), this.cachedReturnLeader.getName().getString()
                            );
                        } else {
                            SoundAttractMod.LOGGER.info(
                                    "Follower {} attempted to return but no leader found (deserter with no leaders in world).", mob.getName().getString()
                            );
                        }
                    }
                    edgeArrivalTicks = 0;
                    returnLogCooldown = 0;
                    return;
                }
            }
        } else if (edgeMobState == EdgeMobState.RETURNING_TO_LEADER) {

            if (MobGroupManager.isDeserter(this.mob)) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] {} is a deserter but entered RETURNING_TO_LEADER; aborting return.", mob.getName().getString());
                }
                if (this.mob.isSprinting()) this.mob.setSprinting(false);
                targetSoundPos = null;
                edgeMobState = null;
                cachedReturnLeader = null;
                this.lastIssuedNavTarget = null;
                return;
            }

            Mob returnLeader = this.cachedReturnLeader != null ? this.cachedReturnLeader : (leader != mob ? leader : MobGroupManager.getNearestLeader(this.mob));
            if (returnLeader != null && !returnLeader.isRemoved() && !returnLeader.isDeadOrDying()) {

                if (!this.mob.isSprinting()) this.mob.setSprinting(true);
                double sprintMult = SoundAttractConfig.COMMON.groupSprintMultiplier.get();
                moveToThrottled(returnLeader.blockPosition(), this.moveSpeed * sprintMult, false);
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    if (returnLogCooldown <= 0) {
                        double distSq = mob.distanceToSqr(returnLeader);
                        boolean navDone = mob.getNavigation().isDone();
                        boolean navStuck = mob.getNavigation().isStuck();
                        SoundAttractMod.LOGGER.info(
                                "[FollowerEdgeRelayGoal DEBUG] Returning: mob={} pos=({}, {}, {}) leader={} leaderPos=({}, {}, {}) distSq={} navDone={} navStuck={} cachedLeader={}",
                                mob.getName().getString(),
                                String.format("%.1f", mob.getX()), String.format("%.1f", mob.getY()), String.format("%.1f", mob.getZ()),
                                returnLeader.getName().getString(),
                                String.format("%.1f", returnLeader.getX()), String.format("%.1f", returnLeader.getY()), String.format("%.1f", returnLeader.getZ()),
                                String.format("%.1f", distSq), navDone, navStuck, this.cachedReturnLeader != null
                        );
                        returnLogCooldown = 20;
                    } else {
                        returnLogCooldown--;
                    }
                }

                double returnArrive = SoundAttractConfig.COMMON.leaderReturnArrivalDistance.get();
                if (mob.distanceToSqr(returnLeader) < (returnArrive * returnArrive)) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info(
                                "Follower {} reached leader {}. Stopping edge relay goal (will re-evaluate).",
                                mob.getName().getString(),
                                returnLeader.getName().getString()
                        );
                    }
                    if (this.mob.isSprinting()) this.mob.setSprinting(false);
                    targetSoundPos = null;
                    edgeMobState = null;
                    cachedReturnLeader = null;
                    this.lastIssuedNavTarget = null;
                    returnLogCooldown = 0;
                    return;
                }
            } else {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info(
                            "Follower {} lost its leader while returning. Stopping edge relay goal.",
                            mob.getName().getString()
                    );
                }
                if (this.mob.isSprinting()) this.mob.setSprinting(false);
                targetSoundPos = null;
                edgeMobState = null;
                cachedReturnLeader = null;
                this.lastIssuedNavTarget = null;
                return;
            }
        }

        if (this.edgeMobState != EdgeMobState.RETURNING_TO_LEADER && this.mob.isSprinting()) {
            this.mob.setSprinting(false);
        }
    }

    /**
     * Returns true if this mob qualifies for the high-sound-weight target override:
     * - Must detect a sound at or above the configured weight threshold
     * - Must NOT be in an active raid phase
     */
    private boolean shouldOverrideTarget() {
        double threshold = SoundAttractConfig.COMMON.highSoundWeightTargetOverride.get();
        if (threshold <= 0) return false;
        if (RaidManager.isRaidAdvancing(this.mob) || RaidManager.isRaidTicking(this.mob)) return false;
        SoundTracker.SoundRecord best = getCachedNearestSound();
        if (best == null || best.weight < threshold) return false;
        this.targetSoundPos = best.pos;
        this.currentTargetWeight = best.weight;
        this.cachedSound = best;
        return true;
    }

    private boolean isEligibleForOverride() {
        return !RaidManager.isRaidAdvancing(this.mob) && !RaidManager.isRaidTicking(this.mob);
    }

    public boolean isHighWeightOverrideActive() {
        return this.highWeightOverrideActive;
    }

    public BlockPos getTargetSoundPos() {
        return this.targetSoundPos;
    }

    private boolean areSoundsEffectivelySame(SoundTracker.SoundRecord s1, SoundTracker.SoundRecord s2) {
        if (s1 == null || s2 == null) {
            return s1 == s2;
        }
        return s1.pos.equals(s2.pos)
                && (s1.soundId != null && s1.soundId.equals(s2.soundId))
                && Math.abs(s1.range - s2.range) < 0.1
                && Math.abs(s1.weight - s2.weight) < 0.01;
    }
}

