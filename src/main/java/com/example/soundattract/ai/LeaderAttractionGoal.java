package com.example.soundattract.ai;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import com.example.soundattract.Soundattract;
import com.example.soundattract.tracking.SoundTracker;
import com.example.soundattract.config.SoundAttractConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class LeaderAttractionGoal extends net.minecraft.world.entity.ai.goal.Goal {

    protected boolean isBiomassBypass() {
        return false;
    }

    protected java.util.Collection<net.minecraft.world.level.ChunkPos> getExtendedListeningChunks() {
        return null;
    }

    private final Mob mob;
    private final double moveSpeed;

    private BlockPos targetSoundPos;
    private double currentTargetWeight = -1.0;

    private Vec3 chosenDest = null;
    private boolean hasPicked = false;

    private BlockPos lastPos = null;
    private int stuckTicks = 0;

    private BlockBreakerPosGoal blockBreakerGoal = null;

    private SoundTracker.SoundRecord cachedSound = null;
    private boolean highWeightOverrideActive = false;

    public LeaderAttractionGoal(Mob mob, double moveSpeed) {
        this.mob = mob;
        this.moveSpeed = SoundAttractConfig.COMMON.mobMoveSpeed.get();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    private boolean isMobEligible() {
        java.util.Set<net.minecraft.world.entity.EntityType<?>> attractedTypes = com.example.soundattract.event.SoundAttractionEvents.getCachedAttractedEntityTypes();
        boolean byType = attractedTypes.contains(this.mob.getType());
        boolean hasProfile = com.example.soundattract.config.SoundAttractConfig.getMatchingProfile(this.mob) != null;
        return byType || hasProfile;
    }

    private double getArrivalDistance() {
        return SoundAttractConfig.COMMON.arrivalDistance.get();
    }

    private SoundTracker.SoundRecord findBestRelayedSound() {
        List<MobGroupManager.SoundRelay> relays = MobGroupManager.consumeRelayedSounds(this.mob);
        SoundTracker.SoundRecord best = null;
        if (relays != null && !relays.isEmpty()) {
            for (MobGroupManager.SoundRelay relay : relays) {
                SoundTracker.SoundRecord rec = new SoundTracker.SoundRecord(
                        null,
                        relay.soundId,
                        new BlockPos((int) relay.x, (int) relay.y, (int) relay.z),
                        200,
                        this.mob.level().dimension().identifier().toString(),
                        relay.range,
                        relay.weight
                );
                if (best == null || rec.weight > best.weight) {
                    best = rec;
                }
            }
        }
        return best;
    }

    @Override
    public boolean canUse() {
        if (LivingWallCoordinator.isControlled(this.mob)) return false;
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
        if (MobGroupManager.getLeader(mob) != mob) {
            return false;
        }

        if (RaidManager.isRaidTicking(this.mob)) {
            return false;
        }

        if (RaidManager.isRaidAdvancing(this.mob)) {
            BlockPos raidTarget = RaidManager.getRaidTarget(this.mob);
            if (raidTarget == null) return false;
            this.cachedSound = null;
            this.targetSoundPos = raidTarget;
            this.currentTargetWeight = 1000.0;
            this.hasPicked = false;
            this.chosenDest = null;
            return true;
        }

        SoundTracker.SoundRecord relayed = findBestRelayedSound();
        if (relayed == null) {
            if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
                if (shouldOverrideTarget()) {
                    this.mob.setTarget(null);
                    this.highWeightOverrideActive = true;
                    return true;
                }
            }
            return false;
        }

        if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
            double threshold = SoundAttractConfig.COMMON.highSoundWeightTargetOverride.get();
            if (threshold > 0 && relayed.weight >= threshold) {
                this.mob.setTarget(null);
                this.highWeightOverrideActive = true;
            } else {
                return false;
            }
        }

        this.cachedSound = relayed;
        this.targetSoundPos = relayed.pos;
        this.currentTargetWeight = relayed.weight;
        this.hasPicked = false;
        this.chosenDest = null;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (LivingWallCoordinator.isControlled(this.mob)) return false;
        if (!isMobEligible() || this.mob.isVehicle() || this.mob.isSleeping()) {
            return false;
        }

        if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
            double threshold = SoundAttractConfig.COMMON.highSoundWeightTargetOverride.get();
            if (threshold > 0 && this.currentTargetWeight >= threshold) {
                this.mob.setTarget(null);
                this.highWeightOverrideActive = true;
            } else if (SoundAttractConfig.COMMON.skipSoundScanWhenHasTarget.get()) {
                return false;
            }
        }
        if (this.targetSoundPos == null) {
            return false;
        }

        if (RaidManager.isRaidTicking(this.mob)) {
            return false;
        }

        if (RaidManager.isRaidAdvancing(this.mob)) {
            BlockPos destination = this.chosenDest != null ? BlockPos.containing(this.chosenDest) : this.targetSoundPos;
            if (this.mob.getNavigation().isDone()) {
                if (destination != null && this.mob.blockPosition().distSqr(destination) < 4.0D) {
                    RaidManager.clearRaid(this.mob);
                    return false;
                }
            }
            return true;
        }

        BlockPos destination = this.chosenDest != null ? BlockPos.containing(this.chosenDest) : this.targetSoundPos;
        if (this.mob.getNavigation().isDone()) {
            if (destination != null && this.mob.blockPosition().distSqr(destination) < 4.0D) {
                return false;
            }
        }

        List<MobGroupManager.SoundRelay> relays = MobGroupManager.consumeRelayedSounds(this.mob);
        if (relays != null) {
            for (MobGroupManager.SoundRelay relay : relays) {
                double weight = relay.weight;
                double switchRatio = SoundAttractConfig.COMMON.soundSwitchRatio.get();
                if (weight > this.currentTargetWeight * switchRatio) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        Soundattract.LOGGER.info("[LeaderAttractionGoal] {} switching target due to better relay ({} > {}).", this.mob.getName().getString(), weight, this.currentTargetWeight);
                    }
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        this.targetSoundPos = null;
        this.currentTargetWeight = -1.0;
        this.cachedSound = null;
        this.hasPicked = false;
        this.chosenDest = null;
        this.highWeightOverrideActive = false;
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info("LeaderAttractionGoal stop: {}", mob.getName().getString());
        }
    }

    @Override
    public void tick() {
        if (LivingWallCoordinator.isControlled(this.mob)) return;

        if (RaidManager.isRaidAdvancing(this.mob)) {
            if (this.targetSoundPos == null) {
                BlockPos raidTarget = RaidManager.getRaidTarget(this.mob);
                if (raidTarget != null) this.targetSoundPos = raidTarget;
            }
            if (this.targetSoundPos == null) return;

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
                if (this.blockBreakerGoal == null) {
                    BlockBreakerPosGoal newGoal = new BlockBreakerPosGoal(this.mob, this.targetSoundPos, SoundAttractConfig.COMMON.blockBreakingTimeMultiplier.get(), SoundAttractConfig.COMMON.blockBreakingToolOnly.get(), SoundAttractConfig.COMMON.blockBreakingProperToolOnly.get(), SoundAttractConfig.COMMON.blockBreakingProperToolOnly.get());
                    BlockBreakerManager.scheduleAdd(this.mob, newGoal, 1);
                    this.blockBreakerGoal = newGoal;
                }
            }

            if (!hasPicked) {
                double arrivalDist = getArrivalDistance();
                long seed = mob.getUUID().getMostSignificantBits() ^ mob.getUUID().getLeastSignificantBits() ^ targetSoundPos.hashCode();
                Random rand = new Random(seed);
                double angle = rand.nextDouble() * (Math.PI * 2.0);
                double radius = arrivalDist * Math.sqrt(rand.nextDouble());
                int blockX = targetSoundPos.getX() + (int) Math.floor(Math.cos(angle) * radius);
                int blockZ = targetSoundPos.getZ() + (int) Math.floor(Math.sin(angle) * radius);
                int groundY = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
                chosenDest = new Vec3(blockX + 0.5, groundY, blockZ + 0.5);
                hasPicked = true;
            }
            if (chosenDest != null) {
                Vec3 cur = mob.position();
                if (cur.distanceToSqr(chosenDest) > 1.5 * 1.5) {
                    com.example.soundattract.pathfinding.PursuitNavigation.maybeMoveTo(mob, chosenDest, moveSpeed);
                }
                if (mob.getNavigation().isDone()) {
                    hasPicked = false;
                    chosenDest = null;
                }
            }
            return;
        }

        if (this.blockBreakerGoal != null &&
            ((com.example.soundattract.mixin.MobAccessor) this.mob).getGoalSelector().getAvailableGoals().stream()
                .anyMatch(g -> g.isRunning() && g.getGoal() == this.blockBreakerGoal)) {

            List<MobGroupManager.SoundRelay> relays = MobGroupManager.consumeRelayedSounds(this.mob);
            if (relays != null) {
                for (MobGroupManager.SoundRelay relay : relays) {
                    double switchRatio = SoundAttractConfig.COMMON.soundSwitchRatio.get();
                    if (relay.weight > this.currentTargetWeight * switchRatio) {
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            Soundattract.LOGGER.info("[LeaderAttractionGoal] {} found better relay while breaking. Stopping block breaking.", this.mob.getName().getString());
                        }
                        BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
                        this.blockBreakerGoal = null;
                        this.stop();
                        return;
                    }
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


        List<MobGroupManager.SoundRelay> relays = MobGroupManager.consumeRelayedSounds(this.mob);
        if (relays != null && !relays.isEmpty()) {
            for (MobGroupManager.SoundRelay relay : relays) {
                double switchRatio = SoundAttractConfig.COMMON.soundSwitchRatio.get();
                if (relay.weight > this.currentTargetWeight * switchRatio) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        Soundattract.LOGGER.info("[LeaderAttractionGoal] {} switching to relayed sound at ({}, {}, {}) (w:{}, r:{})", this.mob.getName().getString(), relay.x, relay.y, relay.z, relay.weight, relay.range);
                    }
                    this.cachedSound = new SoundTracker.SoundRecord(
                        null,
                        relay.soundId,
                        new BlockPos((int) relay.x, (int) relay.y, (int) relay.z),
                        200,
                        this.mob.level().dimension().identifier().toString(),
                        relay.range,
                        relay.weight
                    );
                    this.targetSoundPos = this.cachedSound.pos;
                    this.currentTargetWeight = this.cachedSound.weight;
                    this.hasPicked = false;
                    this.chosenDest = null;
                }
            }
        }

        if (targetSoundPos == null) {
            return;
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
                Soundattract.LOGGER.info("[LeaderAttractionGoal DEBUG] {} truly stuck. isStuck(): {}", mob.getName().getString(), this.mob.getNavigation().isStuck());
            }
            if (this.blockBreakerGoal == null) {
                BlockPos destination = this.targetSoundPos;
                if (this.mob.getNavigation().isDone()) {
                    if (destination != null && this.mob.blockPosition().distSqr(destination) > 4.0) {
                        stuckTicks++;
                        if (stuckTicks >= 20 && SoundAttractConfig.COMMON.enableBlockBreaking.get()) {
                            if (this.blockBreakerGoal == null) {
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


        if (!hasPicked) {
            double arrivalDist = getArrivalDistance();
            long seed = mob.getUUID().getMostSignificantBits() ^ mob.getUUID().getLeastSignificantBits() ^ targetSoundPos.hashCode();
            Random rand = new Random(seed);
            double angle = rand.nextDouble() * (Math.PI * 2.0);
            double radius = arrivalDist * Math.sqrt(rand.nextDouble());
            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;
            int blockX = targetSoundPos.getX() + (int) Math.floor(offsetX);
            int blockZ = targetSoundPos.getZ() + (int) Math.floor(offsetZ);
            int groundY = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
            double finalX = blockX + 0.5;
            double finalY = groundY;
            double finalZ = blockZ + 0.5;
            chosenDest = new Vec3(finalX, finalY, finalZ);
            hasPicked = true;
        }
        if (chosenDest != null) {
            Vec3 cur = mob.position();
            if (cur.distanceToSqr(chosenDest) > 1.5 * 1.5) {
                com.example.soundattract.pathfinding.PursuitNavigation.maybeMoveTo(mob, chosenDest, moveSpeed);
            }
            if (mob.getNavigation().isDone()) {
                hasPicked = false;
                chosenDest = null;
            }
        }


        if (mob.position().distanceToSqr(Vec3.atCenterOf(targetSoundPos)) < getArrivalDistance() * getArrivalDistance()) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info("[LeaderAttractionGoal] {} arrived near {}.", mob.getName().getString(), targetSoundPos);
            }
        }
    }
    /**
     * Returns true if this leader qualifies for the high-sound-weight target override.
     * Leaders directly scan the SoundTracker for this check (rather than relayed sounds)
     * so they can react to extremely loud nearby events even without follower input.
     */
    private boolean shouldOverrideTarget() {
        double threshold = SoundAttractConfig.COMMON.highSoundWeightTargetOverride.get();
        if (threshold <= 0) return false;
        if (RaidManager.isRaidAdvancing(this.mob) || RaidManager.isRaidTicking(this.mob)) return false;
        com.example.soundattract.tracking.SoundTracker.SoundRecord best =
                com.example.soundattract.tracking.SoundTracker.findNearestSound(
                        this.mob, this.mob.level(),
                        this.mob.blockPosition(), this.mob.getEyePosition(), 
                        null, this.getExtendedListeningChunks(), this.isBiomassBypass());
        if (best == null || best.weight < threshold) return false;
        this.cachedSound = new com.example.soundattract.tracking.SoundTracker.SoundRecord(
                null, best.soundId, best.pos, 200,
                this.mob.level().dimension().identifier().toString(), best.range, best.weight);
        this.targetSoundPos = best.pos;
        this.currentTargetWeight = best.weight;
        this.hasPicked = false;
        this.chosenDest = null;
        return true;
    }

    private boolean isEligibleForOverride() {
        return !RaidManager.isRaidAdvancing(this.mob) && !RaidManager.isRaidTicking(this.mob);
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
