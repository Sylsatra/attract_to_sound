package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public final class LivingBridgeMotionDiagnostics {
    static final int MAX_DIRECT_EVENTS = 16;
    static final int MAX_WINDOW_TICKS = 20;
    private static final Map<UUID, Attempt> ACTIVE = new HashMap<>();

    enum EventKind {
        PAIR,
        VECTOR
    }

    record HookObservation(EventKind kind, UUID member, long tick) {
    }

    static final class Attempt {
        final UUID bridge;
        final UUID member;
        final int slot;
        final int number;
        final ResourceLocation dimension;
        final long startTick;
        final Vec3 target;
        final List<HookObservation> observations = new ArrayList<>(MAX_DIRECT_EVENTS);
        Vec3 previousPosition;
        Vec3 previousLoggedPosition;
        Vec3 previousVelocity;
        int directEvents;
        int omittedPairs;
        int omittedVectors;
        int samples;
        boolean moved;
        boolean navigationActive;

        Attempt(UUID bridge, UUID member, int slot, int number, ResourceLocation dimension,
                long startTick, Vec3 target) {
            this.bridge = bridge;
            this.member = member;
            this.slot = slot;
            this.number = number;
            this.dimension = dimension;
            this.startTick = startTick;
            this.target = target;
        }
    }

    private LivingBridgeMotionDiagnostics() {
    }

    static Attempt begin(UUID bridge, UUID member, int slot, int number,
                         ResourceLocation dimension, long tick, Vec3 target) {
        Attempt attempt = new Attempt(bridge, member, slot, number, dimension, tick, target);
        ACTIVE.put(member, attempt);
        try {
            SoundAttractMod.LOGGER.info("[LivingBridgeMotion] motion_diag_start bridge={} member={} slot={} attempt={} dimension={} tick={} target={}",
                    bridge, member, slot, number, dimension, tick, target);
        } catch (Throwable ignored) {
        }
        return attempt;
    }

    static void sample(Attempt attempt, Mob mob, long tick, Boolean commandAccepted, Boolean fallbackAccepted) {
        try {
            sampleSafely(attempt, mob, tick, commandAccepted, fallbackAccepted);
        } catch (Throwable ignored) {
        }
    }

    private static void sampleSafely(Attempt attempt, Mob mob, long tick, Boolean commandAccepted,
                                    Boolean fallbackAccepted) {
        if (attempt == null || mob == null || tick < attempt.startTick
                || tick - attempt.startTick > MAX_WINDOW_TICKS || attempt.samples >= MAX_WINDOW_TICKS) return;
        Vec3 position = mob.position();
        Vec3 velocity = mob.getDeltaMovement();
        Vec3 displacement = attempt.previousPosition == null ? Vec3.ZERO : position.subtract(attempt.previousPosition);
        Vec3 deltaVelocity = attempt.previousVelocity == null ? Vec3.ZERO : velocity.subtract(attempt.previousVelocity);
        boolean navDone = mob.getNavigation().isDone();
        boolean emitSample = attempt.samples == 0 || (tick - attempt.startTick) % 5L == 0L;
        attempt.samples++;
        attempt.moved |= displacement.lengthSqr() > 1.0E-6D;
        attempt.navigationActive |= !navDone;
        attempt.previousPosition = position;
        attempt.previousVelocity = velocity;
        if (emitSample) {
            Vec3 sampleDisplacement = attempt.previousLoggedPosition == null
                    ? Vec3.ZERO : position.subtract(attempt.previousLoggedPosition);
            attempt.previousLoggedPosition = position;
            Path path = mob.getNavigation().getPath();
            BlockPos nextNode = path == null || path.isDone() || path.getNextNodeIndex() >= path.getNodeCount()
                    ? null : path.getNode(path.getNextNodeIndex()).asBlockPos();
            String runningGoals = mob.goalSelector.getAvailableGoals().stream().filter(goal -> goal.isRunning())
                    .map(wrapped -> wrapped.getGoal().getClass().getSimpleName() + "@" + wrapped.getPriority())
                    .reduce((first, second) -> first + "," + second).orElse("");
            boolean moveWanted = mob.getMoveControl().hasWanted();
            double wantedX = moveWanted ? mob.getMoveControl().getWantedX() : Double.NaN;
            double wantedY = moveWanted ? mob.getMoveControl().getWantedY() : Double.NaN;
            double wantedZ = moveWanted ? mob.getMoveControl().getWantedZ() : Double.NaN;
            UUID targetId = mob.getTarget() == null ? null : mob.getTarget().getUUID();
            SoundAttractMod.LOGGER.info("[LivingBridgeMotion] motion_diag_sample bridge={} member={} slot={} attempt={} tick={} sample={} pos={} tickDisplacement={} sampleDisplacement={} velocity={} deltaVelocity={} navDone={} navTarget={} nextNode={} commandAccepted={} fallbackAccepted={} horizontalCollision={} onGround={} moveWanted={} wanted=({},{},{}) target={} runningGoals={}",
                    attempt.bridge, attempt.member, attempt.slot, attempt.number, tick, attempt.samples,
                    position, displacement, sampleDisplacement, velocity, deltaVelocity, navDone,
                    mob.getNavigation().getTargetPos(), nextNode, commandAccepted, fallbackAccepted,
                    mob.horizontalCollision, mob.onGround(), moveWanted, wantedX, wantedY, wantedZ,
                    targetId, runningGoals);
        }
    }

    public static void recordPair(Entity receiver, Entity argument) {
        try {
            recordPairSafely(receiver, argument);
        } catch (Throwable ignored) {
        }
    }

    private static void recordPairSafely(Entity receiver, Entity argument) {
        if (receiver == null || argument == null || !isServerThread(receiver)) return;
        long tick = receiver.level().getGameTime();
        Attempt first = active(receiver, tick);
        Attempt second = active(argument, tick);
        if (first != null) recordPairFor(first, receiver, argument, second == first ? "both" : "receiver", tick);
        if (second != null && second != first) recordPairFor(second, receiver, argument, "argument", tick);
    }

    private static void recordPairFor(Attempt attempt, Entity receiver, Entity argument, String activeSide, long tick) {
        if (!reserve(attempt, EventKind.PAIR, tick)) return;
        SoundAttractMod.LOGGER.info("[LivingBridgeMotion] collision_pair_invoked bridge={} member={} slot={} attempt={} dimension={} tick={} event_seq={} activeSide={} receiver={} receiverType={} argument={} argumentType={}",
                attempt.bridge, attempt.member, attempt.slot, attempt.number, attempt.dimension, tick,
                attempt.directEvents, activeSide, receiver.getUUID(), receiver.getType(), argument.getUUID(), argument.getType());
    }

    public static void recordVector(Entity entity, double x, double y, double z, boolean blockedByGuard) {
        try {
            recordVectorSafely(entity, x, y, z, blockedByGuard);
        } catch (Throwable ignored) {
        }
    }

    private static void recordVectorSafely(Entity entity, double x, double y, double z, boolean blockedByGuard) {
        if (entity == null || !isServerThread(entity)) return;
        Attempt attempt = active(entity, entity.level().getGameTime());
        if (attempt == null || !reserve(attempt, EventKind.VECTOR, entity.level().getGameTime())) return;
        Vec3 velocity = entity.getDeltaMovement();
        SoundAttractMod.LOGGER.info("[LivingBridgeMotion] vector_push_request bridge={} member={} slot={} attempt={} dimension={} tick={} event_seq={} vector=({},{},{}) preVelocity={} blocked_by_existing_hazard_guard={} effect_status={}",
                attempt.bridge, attempt.member, attempt.slot, attempt.number, attempt.dimension,
                entity.level().getGameTime(), attempt.directEvents, x, y, z, velocity,
                blockedByGuard, blockedByGuard ? "blocked" : "effect_unconfirmed");
    }

    static boolean reserve(Attempt attempt, EventKind kind, long tick) {
        attempt.directEvents++;
        if (attempt.directEvents > MAX_DIRECT_EVENTS) {
            if (kind == EventKind.PAIR) attempt.omittedPairs++;
            else attempt.omittedVectors++;
            return false;
        }
        attempt.observations.add(new HookObservation(kind, attempt.member, tick));
        return true;
    }

    private static Attempt active(Entity entity, long tick) {
        if (!isServerThread(entity)) return null;
        Attempt attempt = ACTIVE.get(entity.getUUID());
        return attempt != null && attempt.dimension.equals(entity.level().dimension().location())
                && tick >= attempt.startTick
                && tick - attempt.startTick <= MAX_WINDOW_TICKS ? attempt : null;
    }

    private static boolean isServerThread(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level) || level.getServer() == null) return false;
        return level.getServer().isSameThread();
    }

    static void finish(Attempt attempt, long tick, String reason) {
        if (attempt == null) return;
        ACTIVE.remove(attempt.member, attempt);
        try {
            String classification = classify(attempt.observations, attempt.omittedPairs, attempt.omittedVectors);
            String motionState = attempt.moved ? "displacement_observed"
                    : attempt.navigationActive ? "navigation_active_without_displacement"
                    : "no_displacement_navigation_idle";
            SoundAttractMod.LOGGER.info("[LivingBridgeMotion] motion_diag_terminal bridge={} member={} slot={} attempt={} tick={} reason={} samples={} motionState={} pairEvents={} vectorEvents={} omittedPairs={} omittedVectors={} classification={}",
                    attempt.bridge, attempt.member, attempt.slot, attempt.number, tick, reason, attempt.samples,
                    motionState, count(attempt.observations, EventKind.PAIR),
                    count(attempt.observations, EventKind.VECTOR), attempt.omittedPairs, attempt.omittedVectors,
                    classification);
        } catch (Throwable ignored) {
        }
    }

    static String classify(List<HookObservation> observations, int omittedPairs, int omittedVectors) {
        if (omittedPairs > 0 || omittedVectors > 0) return "truncated_ambiguous";
        int pairs = count(observations, EventKind.PAIR);
        int vectors = count(observations, EventKind.VECTOR);
        if (pairs == 0 && vectors == 0) return "no_push_hook_observed";
        if (pairs > 1 || vectors > 1) return "multiple_candidates_ambiguous";
        if (pairs == 1 && vectors == 0) return "pair_only_no_vector_observed";
        if (pairs == 0) return "vector_only_no_pair_observed";
        HookObservation pair = first(observations, EventKind.PAIR);
        HookObservation vector = first(observations, EventKind.VECTOR);
        return pair.member().equals(vector.member()) && pair.tick() == vector.tick()
                ? "unique_same_member_tick_correlation_noncausal" : "pair_vector_different_tick_ambiguous";
    }

    private static HookObservation first(List<HookObservation> observations, EventKind kind) {
        for (HookObservation observation : observations) {
            if (observation.kind() == kind) return observation;
        }
        return null;
    }

    private static int count(List<HookObservation> observations, EventKind kind) {
        int count = 0;
        for (HookObservation observation : observations) {
            if (observation.kind() == kind) count++;
        }
        return count;
    }
}