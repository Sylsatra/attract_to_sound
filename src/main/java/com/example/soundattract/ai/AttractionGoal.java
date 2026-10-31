package com.example.soundattract.ai;

import java.util.EnumSet;

import com.example.soundattract.runtime.DynamicScanCooldownManager;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.event.SoundAttractionEvents;
import com.example.soundattract.tracking.SoundTracker;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.util.ScentQueryHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class AttractionGoal extends Goal {

    private final Mob mob;
    private final double moveSpeed;
    private BlockPos targetSoundPos;
    private double currentTargetWeight = -1.0;
    private BlockPos lastPos = null;
    private int stuckTicks = 0;
    private int scanCooldownCounter = 0;
    private SoundTracker.SoundRecord cachedSound = null;
    private boolean isPursuingSound = false;
    private int pursuingSoundTicksRemaining = 0;
    private BlockBreakerPosGoal blockBreakerGoal = null;
    private SoundTracker.SoundRecord soundResultCache = null;
    private long cacheTick = -1L;
    private long lastMoveToTick = -1L;
    private BlockPos lastMoveToTarget = null;
    private static final int MOVE_TO_COOLDOWN_TICKS = 20;

    private long lastSeenSoundSequence = -1L;
    private long lastScanBypassTick = -1L;
    private static final int CUSTOM_NPCS_BYPASS_MIN_INTERVAL_TICKS = 5;
    private boolean highWeightOverrideActive = false;

    public AttractionGoal(Mob mob, double moveSpeed) {
        this.mob = mob;
        this.moveSpeed = SoundAttractConfig.COMMON.mobMoveSpeed.get();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    private int scanCooldownTicks() {
        return DynamicScanCooldownManager.currentScanCooldownTicks;
    }

    private void refreshScanCooldownOnNewSoundForCustomNpcs() {
        if (!SoundAttractionEvents.isCustomNpcsMob(this.mob)) {
            return;
        }
        long nowTick = this.mob.level().getGameTime();
        if (this.lastScanBypassTick >= 0 && (nowTick - this.lastScanBypassTick) < CUSTOM_NPCS_BYPASS_MIN_INTERVAL_TICKS) {
            return;
        }
        long seq = SoundTracker.getSoundSequence();
        if (seq != this.lastSeenSoundSequence) {
            this.lastSeenSoundSequence = seq;
            this.scanCooldownCounter = 0;
            this.lastScanBypassTick = nowTick;
        }
    }

    private boolean isMobEligible() {
        java.util.Set<net.minecraft.world.entity.EntityType<?>> attractedTypes = SoundAttractionEvents.getCachedAttractedEntityTypes();
        boolean byType = attractedTypes.contains(this.mob.getType());
        boolean hasProfile = com.example.soundattract.config.SoundAttractConfig.getMatchingProfile(this.mob) != null;
        boolean isCustomNpcs = SoundAttractionEvents.isCustomNpcsMob(this.mob);
        return byType || hasProfile || isCustomNpcs;
    }

    private double getArrivalDistance() {
        return SoundAttractConfig.COMMON.arrivalDistance.get();
    }

    private boolean shouldSuppressTargeting() {
        return SoundAttractConfig.COMMON.enableStealthMechanics.get()
                && com.example.soundattract.event.StealthDetectionEvents.shouldSuppressTargeting(this.mob);
    }

    @Override
    public boolean canUse() {
        if (this.mob.isVehicle() || this.mob.isSleeping() || shouldSuppressTargeting()) {
            return false;
        }

        if (SoundAttractConfig.COMMON.skipSoundScanWhenHasTarget.get()
                && this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
            return false;
        }

        if (SoundAttractConfig.COMMON.raidLeaderOnlySoundScan.get()) {
            Mob leader = MobGroupManager.getLeader(this.mob);
            if (leader != null && leader != this.mob && RaidManager.isRaidAdvancing(leader)) {
                return false;
            }
        }


        if (!isMobEligible()) {
            return false;
        }

        refreshScanCooldownOnNewSoundForCustomNpcs();

        if (scanCooldownCounter > 0) {
            scanCooldownCounter--;
            return false;
        }
        scanCooldownCounter = scanCooldownTicks();

        SoundTracker.SoundRecord newSound = findInterestingSoundRecord();
        if (newSound == null) {
            return false;
        }

        if (SoundAttractConfig.COMMON.scentOverrideSoundPriority.get()) {
            int maxAge = SoundAttractConfig.COMMON.arrowScentNodeDurationTicks.get();
            double radius = SoundAttractConfig.COMMON.scentDetectionRadius.get();
            com.example.soundattract.util.ScentQueryCache.Result scent =
                com.example.soundattract.util.ScentQueryHelper.computeFreshScentInfo(this.mob, maxAge, radius);
            if (scent != null && scent.hasFresh()) {
                double threshold = SoundAttractConfig.COMMON.scentOverrideSoundWeightThreshold.get();
                double multiplier = SoundAttractConfig.COMMON.scentVsSoundHybridMultiplier.get();
                if (newSound.weight < threshold) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[AttractionGoal] Scent overrides low-weight sound ({} < {}) for {}",
                                newSound.weight, threshold, this.mob.getName().getString());
                    }
                    return false;
                }
                if (newSound.weight < scent.strongestStrength() * multiplier) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[AttractionGoal] Scent strength {} beats sound weight {} for {}",
                                scent.strongestStrength(), newSound.weight, this.mob.getName().getString());
                    }
                    return false;
                }
            }
        }

        if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
            double threshold = SoundAttractConfig.COMMON.highSoundWeightTargetOverride.get();
            if (threshold > 0 && newSound.weight >= threshold && isEligibleForOverride()) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[AttractionGoal] Mob {} dropping target for high-weight sound ({}).", this.mob.getName().getString(), newSound.weight);
                }
                this.mob.setTarget(null);
                this.highWeightOverrideActive = true;
                this.isPursuingSound = true;
                this.pursuingSoundTicksRemaining = 600;
            }
        }

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info(
                    "[AttractionGoal] Mob {} found sound: pos={}, range={}, weight={}",
                    mob.getName().getString(),
                    newSound.pos,
                    newSound.range,
                    newSound.weight
            );
        }
        this.targetSoundPos = newSound.pos;
        this.currentTargetWeight = newSound.weight;
        this.cachedSound = newSound;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!isMobEligible() || this.mob.isVehicle() || this.mob.isSleeping() || shouldSuppressTargeting()) {
            return false;
        }

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
        if (this.targetSoundPos == null) {
            return false;
        }





        if (this.mob.getNavigation().isDone()) {
            BlockPos destination = null;
            destination = this.targetSoundPos;


            if (destination != null && this.mob.blockPosition().distSqr(destination) < 4.0D) {
                return false;
            }


        }

        

        SoundTracker.SoundRecord bestSoundNow = getCachedNearestSound();
        if (bestSoundNow == null) {

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
                SoundAttractMod.LOGGER.info("[AttractionGoal] Mob {} is switching from target {} (weight {}) to {} (weight {})", mob.getName().getString(), this.targetSoundPos, this.currentTargetWeight, bestSoundNow.pos, bestSoundNow.weight);
            }
            return false;
        }


        return true;
    }

    protected boolean isBiomassBypass() {
        return false;
    }

    protected java.util.Collection<net.minecraft.world.level.ChunkPos> getExtendedListeningChunks() {
        return null;
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
            this.cachedSound != null ? this.cachedSound.soundId : null,
            this.getExtendedListeningChunks(),
            this.isBiomassBypass()
        );
        return this.soundResultCache;
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        this.targetSoundPos = null;
        this.currentTargetWeight = -1.0;
        this.cachedSound = null;
        this.isPursuingSound = false;
        this.pursuingSoundTicksRemaining = 0;
        this.lastMoveToTick = -1L;
        this.lastMoveToTarget = null;
        this.highWeightOverrideActive = false;
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("AttractionGoal stop: " + mob.getName().getString());
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
                        SoundAttractMod.LOGGER.info("[AttractionGoal] Mining mob {} found a better sound ({} > {}). Stopping block breaking.", this.mob.getName().getString(), bestPossibleSound.weight, this.cachedSound.weight);
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
        SoundTracker.SoundRecord fresh = findInterestingSoundRecord();
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
                    this.mob.getNavigation().stop();
                }
            }
        }
        if (targetSoundPos == null) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                        "AttractionGoal tick: targetSoundPos is null for "
                        + mob.getName().getString() + ", goal will likely stop."
                );
            }
            return;
        }

        if (scanCooldownCounter > 0) {
            scanCooldownCounter--;
        }

        double arrivalDist = SoundAttractConfig.COMMON.arrivalDistance.get();
        if (mob.position().distanceToSqr(Vec3.atCenterOf(targetSoundPos)) < arrivalDist * arrivalDist) {
            this.mob.getLookControl().setLookAt(
                    targetSoundPos.getX() + 0.5,
                    targetSoundPos.getY() + 0.5,
                    targetSoundPos.getZ() + 0.5,
                    (float)this.mob.getMaxHeadYRot(),
                    (float)this.mob.getMaxHeadXRot()
            );
        }



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
                SoundAttractMod.LOGGER.info("[AttractionGoal DEBUG] Mob {} truly stuck. stuckTicks: {}, navigation.isStuck(): {}", mob.getName().getString(), stuckTicks, this.mob.getNavigation().isStuck());
            }


            if (this.blockBreakerGoal == null) {




                BlockPos destination = this.targetSoundPos;


                if (this.mob.getNavigation().isDone()) {


                    if (destination != null && this.mob.blockPosition().distSqr(destination) > 4.0) {

                        stuckTicks++;


                        if (stuckTicks >= 20 && SoundAttractConfig.COMMON.enableBlockBreaking.get()) {


                            if (this.blockBreakerGoal == null) {
                                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                                    SoundAttractMod.LOGGER.info("[AttractionGoal] Mob {} navigation is DONE and it's far from its goal. Deploying BlockBreakerPosGoal.", mob.getName().getString());
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

        SoundTracker.SoundRecord currentPursuedSound = this.cachedSound;
        if (currentPursuedSound == null || !currentPursuedSound.pos.equals(this.targetSoundPos)) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                        "AttractionGoal tick: Cached sound mismatch for {} during expiration check. Target: {}, Cached: {}",
                        mob.getName().getString(),
                        targetSoundPos,
                        (currentPursuedSound != null ? currentPursuedSound.pos : "null")
                );
            }
            currentPursuedSound = null;
        }

        if (currentPursuedSound == null || currentPursuedSound.ticksRemaining <= 0) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                        "AttractionGoal tick: Sound at " + targetSoundPos
                        + " (from cache) expired or removed for " + mob.getName().getString()
                );
            }
            targetSoundPos = null;
            return;
        }

        boolean targetChanged = this.lastMoveToTarget == null || !this.lastMoveToTarget.equals(this.targetSoundPos);
        long nowTick = this.mob.level().getGameTime();
        boolean cooldownElapsed = (this.lastMoveToTick < 0) || (nowTick - this.lastMoveToTick >= MOVE_TO_COOLDOWN_TICKS);
        boolean navDone = this.mob.getNavigation().isDone();
        boolean shouldRecalc = targetChanged || navDone || cooldownElapsed || this.mob.getNavigation().isStuck();
        if (shouldRecalc && this.targetSoundPos != null) {
            this.mob.getNavigation().moveTo(
                    targetSoundPos.getX(),
                    targetSoundPos.getY(),
                    targetSoundPos.getZ(),
                    this.moveSpeed
            );
            this.lastMoveToTick = nowTick;
            this.lastMoveToTarget = this.targetSoundPos;
        }

        if (isPursuingSound && this.cachedSound != null) {
            if (pursuingSoundTicksRemaining > 0) {
                pursuingSoundTicksRemaining--;
                if (pursuingSoundTicksRemaining <= 0) {
                    isPursuingSound = false;
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info(
                                "AttractionGoal tick: " + mob.getName().getString()
                                + " pursuit timer expired. Will re-evaluate sounds."
                        );
                    }
                }
            }
        } else if (!isPursuingSound) {
            pursuingSoundTicksRemaining = 0;
        }

        if (mob.position().distanceToSqr(Vec3.atCenterOf(targetSoundPos))
                < getArrivalDistance() * getArrivalDistance()) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                        "AttractionGoal tick: " + mob.getName().getString()
                        + " arrived at " + targetSoundPos
                        + ". Scan cooldown will apply if it re-evaluates canUse."
                );
            }
        }
    }

    protected SoundTracker.SoundRecord findInterestingSoundRecord() {
        Level level = this.mob.level();
        if (level.isClientSide()) {
            return null;
        }

        BlockPos mobPos = this.mob.blockPosition();
        SoundTracker.SoundRecord bestSoundOverall = getCachedNearestSound();

        SoundTracker.SoundRecord currentTargetSound = this.cachedSound;
        if (currentTargetSound != null && bestSoundOverall != null
                && !areSoundsEffectivelySame(currentTargetSound, bestSoundOverall)) {
            double switchRatio = SoundAttractConfig.COMMON.soundSwitchRatio.get();
            boolean canSwitch = bestSoundOverall.weight > currentTargetSound.weight * switchRatio
                    || (Math.abs(bestSoundOverall.weight - currentTargetSound.weight) < 0.001
                    && bestSoundOverall.pos.distSqr(mobPos)
                    < currentTargetSound.pos.distSqr(mobPos));
            if (!canSwitch) {
                this.isPursuingSound = true;
                this.pursuingSoundTicksRemaining = DynamicScanCooldownManager.currentScanCooldownTicks;
                return currentTargetSound;
            }
        }

        if (bestSoundOverall != null) {
            this.isPursuingSound = true;
            this.pursuingSoundTicksRemaining = DynamicScanCooldownManager.currentScanCooldownTicks;
        } else {
            this.isPursuingSound = false;
            this.pursuingSoundTicksRemaining = 0;
        }
        this.cachedSound = bestSoundOverall;
        return bestSoundOverall;
    }

    /**
     * Returns true if this mob qualifies for the high-sound-weight target override:
     * - Must be a Leader, Edge, or Deserter role mob
     * - Must detect a sound at or above the configured weight threshold
     * - Must NOT be in an active raid phase
     */
    private boolean shouldOverrideTarget() {
        double threshold = SoundAttractConfig.COMMON.highSoundWeightTargetOverride.get();
        if (threshold <= 0) return false;
        boolean isEligible = MobGroupManager.getLeader(this.mob) == this.mob
                || MobGroupManager.isEdgeMob(this.mob)
                || MobGroupManager.isDeserter(this.mob);
        if (!isEligible) return false;
        if (RaidManager.isRaidAdvancing(this.mob) || RaidManager.isRaidTicking(this.mob)) return false;
        SoundTracker.SoundRecord best = SoundTracker.findNearestSound(
                this.mob, this.mob.level(),
                this.mob.blockPosition(), this.mob.getEyePosition(), null);
        if (best == null || best.weight < threshold) return false;
        this.targetSoundPos = best.pos;
        this.currentTargetWeight = best.weight;
        this.cachedSound = best;
        this.highWeightOverrideActive = true;
        return true;
    }

    private boolean isEligibleForOverride() {
        return !RaidManager.isRaidAdvancing(this.mob) && !RaidManager.isRaidTicking(this.mob);
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

    public boolean isPursuingSound() {
        return isPursuingSound;
    }

    public BlockPos getTargetSoundPos() {
        return this.targetSoundPos;
    }

    public double getCurrentTargetWeight() {
        return this.currentTargetWeight;
    }

    public boolean isHighWeightOverrideActive() {
        return this.highWeightOverrideActive;
    }
}

