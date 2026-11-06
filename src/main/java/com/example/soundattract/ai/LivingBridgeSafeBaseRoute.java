package com.example.soundattract.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.function.BiPredicate;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class LivingBridgeSafeBaseRoute {
    static final int MAX_SWEEP_SUBDIVISIONS = 16;
    static final double MAX_SWEEP_STEP = 0.20D;

    enum Rejection {
        NONE,
        INVALID_FRAME,
        CERTIFICATE_INVALID,
        SOURCE_SIDE_AMBIGUOUS,
        BRIDGE_ENVELOPE_INTERSECTION,
        NO_PATH,
        INCOMPLETE_PATH,
        WRONG_ENDPOINT,
        SOURCE_SIDE_CROSSING,
        SWEEP_BUDGET_EXCEEDED,
        UNLOADED_CELL,
        COLLISION,
        LAVA,
        TRAPDOOR_DROP,
        UNSUPPORTED
    }

    record Result(Rejection rejection, BlockPos firstUnsafeCell) {
        boolean safe() {
            return rejection == Rejection.NONE;
        }
    }

    private LivingBridgeSafeBaseRoute() {
    }

    static Vec3 verifiedForward(BlockPos pivot, BlockPos spanEnd, Vec3 plannedForward) {
        if (pivot == null || spanEnd == null || plannedForward == null
                || !Double.isFinite(plannedForward.x) || !Double.isFinite(plannedForward.z)) return null;
        double dx = spanEnd.getX() - pivot.getX();
        double dz = spanEnd.getZ() - pivot.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        double plannedLength = Math.sqrt(plannedForward.x * plannedForward.x + plannedForward.z * plannedForward.z);
        if (length < 1.0E-6D || plannedLength < 1.0E-6D) return null;
        Vec3 actual = new Vec3(dx / length, 0.0D, dz / length);
        Vec3 planned = new Vec3(plannedForward.x / plannedLength, 0.0D, plannedForward.z / plannedLength);
        return actual.dot(planned) >= 0.98D ? actual : null;
    }

    static boolean trustedSourceIsBehind(Vec3 pivot, Vec3 forward, Vec3 sourceCenter, BlockPos sourceBucket) {
        if (pivot == null || forward == null || sourceCenter == null || sourceBucket == null) return false;
        double centerStation = station(pivot, forward, sourceCenter);
        double bucketStation = station(pivot, forward, Vec3.atCenterOf(sourceBucket));
        return Double.isFinite(centerStation) && Double.isFinite(bucketStation)
                && centerStation < -1.0E-4D && bucketStation < -1.0E-4D;
    }

    static Result validateTrustedSource(Vec3 pivot, Vec3 forward, Vec3 sourceCenter, BlockPos sourceBucket) {
        if (!finite(pivot) || !finite(forward) || Math.abs(forward.y) > 1.0E-6D
                || forward.horizontalDistanceSqr() < 1.0E-12D) {
            return new Result(Rejection.INVALID_FRAME, null);
        }
        if (!finite(sourceCenter) || sourceBucket == null
                || !trustedSourceIsBehind(pivot, forward, sourceCenter, sourceBucket)) {
            return new Result(Rejection.SOURCE_SIDE_AMBIGUOUS, sourceBucket);
        }
        return new Result(Rejection.NONE, null);
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    static double station(Vec3 pivot, Vec3 forward, Vec3 point) {
        return (point.x - pivot.x) * forward.x + (point.z - pivot.z) * forward.z;
    }

    static boolean bodyRemainsBehind(Vec3 pivot, Vec3 forward, AABB body, double margin) {
        if (pivot == null || forward == null || body == null || !Double.isFinite(margin) || margin < 0.0D) return false;
        double maxStation = Double.NEGATIVE_INFINITY;
        for (double x : new double[]{body.minX, body.maxX}) {
            for (double z : new double[]{body.minZ, body.maxZ}) {
                maxStation = Math.max(maxStation, station(pivot, forward, new Vec3(x, body.minY, z)));
            }
        }
        return maxStation <= -margin;
    }

    static boolean mustPreserveOwner(boolean exactCopies, boolean terminalState) {
        return !exactCopies || !terminalState;
    }

    static Result validatePathShape(List<BlockPos> nodes, BlockPos requestedEnd, boolean complete,
                                    BlockPos actualEnd, Vec3 pivot, Vec3 forward, double sourceMargin) {
        if (nodes == null || nodes.isEmpty() || !complete) return new Result(Rejection.INCOMPLETE_PATH, null);
        if (requestedEnd == null || actualEnd == null || actualEnd.distSqr(requestedEnd) > 1.0D) {
            return new Result(Rejection.WRONG_ENDPOINT, actualEnd);
        }
        if (pivot == null || forward == null) return new Result(Rejection.INVALID_FRAME, null);
        for (BlockPos node : nodes) {
            if (node == null || !bodyRemainsBehind(pivot, forward,
                    new AABB(node.getX(), node.getY(), node.getZ(), node.getX() + 1.0D,
                            node.getY() + 1.0D, node.getZ() + 1.0D), sourceMargin)) {
                return new Result(Rejection.SOURCE_SIDE_CROSSING, node);
            }
        }
        return new Result(Rejection.NONE, null);
    }

    static Result validateCompletePathShape(List<BlockPos> nodes, BlockPos requestedEnd, boolean complete,
                                            BlockPos actualEnd) {
        if (nodes == null || nodes.isEmpty() || !complete) return new Result(Rejection.INCOMPLETE_PATH, null);
        if (nodes.stream().anyMatch(node -> node == null)) return new Result(Rejection.INVALID_FRAME, null);
        if (requestedEnd == null || actualEnd == null || actualEnd.distSqr(requestedEnd) > 1.0D) {
            return new Result(Rejection.WRONG_ENDPOINT, actualEnd);
        }
        return new Result(Rejection.NONE, null);
    }

    static boolean bodyOutsideFiniteEnvelope(AABB body, BlockPos pivot, BlockPos spanEnd,
                                             double halfWidth, double minY, double maxY) {
        if (body == null || pivot == null || spanEnd == null || !Double.isFinite(halfWidth)
                || halfWidth < 0.0D || !Double.isFinite(minY) || !Double.isFinite(maxY) || minY > maxY) return false;
        if (body.maxY <= minY || body.minY >= maxY) return true;
        double dx = spanEnd.getX() - pivot.getX();
        double dz = spanEnd.getZ() - pivot.getZ();
        double lengthSqr = dx * dx + dz * dz;
        if (lengthSqr < 1.0E-12D) return false;
        double length = Math.sqrt(lengthSqr);
        double ux = dx / length;
        double uz = dz / length;
        double px = -uz;
        double pz = ux;
        double cx = (pivot.getX() + spanEnd.getX() + 1.0D) * 0.5D;
        double cz = (pivot.getZ() + spanEnd.getZ() + 1.0D) * 0.5D;
        double halfLength = length * 0.5D + Math.abs(ux) * 0.5D + Math.abs(uz) * 0.5D;
        double centerStation = (body.getCenter().x - cx) * ux + (body.getCenter().z - cz) * uz;
        double bodyStationRadius = (body.getXsize() * Math.abs(ux) + body.getZsize() * Math.abs(uz)) * 0.5D;
        if (Math.abs(centerStation) >= halfLength + bodyStationRadius) return true;
        double halfWidthProjected = halfWidth + Math.abs(px) * 0.5D + Math.abs(pz) * 0.5D;
        double centerLateral = (body.getCenter().x - cx) * px + (body.getCenter().z - cz) * pz;
        double bodyLateralRadius = (body.getXsize() * Math.abs(px) + body.getZsize() * Math.abs(pz)) * 0.5D;
        if (Math.abs(centerLateral) >= halfWidthProjected + bodyLateralRadius) return true;
        double halfLengthProjected = halfLength;
        double envelopeXRadius = Math.abs(ux) * halfLengthProjected + Math.abs(px) * halfWidthProjected;
        double envelopeZRadius = Math.abs(uz) * halfLengthProjected + Math.abs(pz) * halfWidthProjected;
        return body.maxX <= cx - envelopeXRadius || body.minX >= cx + envelopeXRadius
                || body.maxZ <= cz - envelopeZRadius || body.minZ >= cz + envelopeZRadius;
    }

    static List<Integer> boundedCandidateIndices(int candidateCount, int maxAttempts) {
        if (candidateCount <= 0 || maxAttempts <= 0) return List.of();
        if (candidateCount <= maxAttempts) {
            List<Integer> all = new ArrayList<>(candidateCount);
            for (int index = 0; index < candidateCount; index++) all.add(index);
            return List.copyOf(all);
        }
        if (maxAttempts == 1) return List.of(0);
        List<Integer> sampled = new ArrayList<>(maxAttempts);
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            sampled.add((int) ((long) attempt * (candidateCount - 1) / (maxAttempts - 1)));
        }
        return List.copyOf(sampled);
    }

    static Result validateFinitePathShape(List<BlockPos> nodes, BlockPos requestedEnd, boolean complete,
                                          BlockPos actualEnd, BlockPos pivot, BlockPos spanEnd,
                                          double halfWidth, double minY, double maxY) {
        if (nodes == null || nodes.isEmpty() || !complete) return new Result(Rejection.INCOMPLETE_PATH, null);
        if (requestedEnd == null || actualEnd == null || actualEnd.distSqr(requestedEnd) > 1.0D) {
            return new Result(Rejection.WRONG_ENDPOINT, actualEnd);
        }
        if (pivot == null || spanEnd == null || spanEnd.equals(pivot)) return new Result(Rejection.INVALID_FRAME, null);
        for (BlockPos node : nodes) {
            if (node == null || !bodyOutsideFiniteEnvelope(new AABB(node.getX(), node.getY(), node.getZ(),
                    node.getX() + 1.0D, node.getY() + 1.0D, node.getZ() + 1.0D),
                    pivot, spanEnd, halfWidth, minY, maxY)) {
                return new Result(Rejection.BRIDGE_ENVELOPE_INTERSECTION, node);
            }
        }
        return new Result(Rejection.NONE, null);
    }

    static boolean routeContains(Vec3 position, List<BlockPos> nodes, BlockPos endpoint, double tolerance) {
        if (position == null || nodes == null || nodes.isEmpty() || endpoint == null
                || !Double.isFinite(tolerance) || tolerance < 0.0D) return false;
        double toleranceSqr = tolerance * tolerance;
        Vec3 last = null;
        for (BlockPos node : nodes) {
            if (node == null) return false;
            Vec3 current = Vec3.atBottomCenterOf(node);
            if (last == null && position.distanceToSqr(current) <= toleranceSqr) return true;
            if (last != null && distanceToSegmentSqr(position, last, current) <= toleranceSqr) return true;
            last = current;
        }
        return last != null && (distanceToSegmentSqr(position, last, Vec3.atBottomCenterOf(endpoint)) <= toleranceSqr
                || position.distanceToSqr(Vec3.atBottomCenterOf(endpoint)) <= toleranceSqr);
    }

    private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double lengthSqr = delta.lengthSqr();
        double t = lengthSqr < 1.0E-12D ? 0.0D
                : Mth.clamp(point.subtract(start).dot(delta) / lengthSqr, 0.0D, 1.0D);
        return point.distanceToSqr(start.add(delta.scale(t)));
    }

    static int subdivisions(double distance) {
        if (!Double.isFinite(distance) || distance < 0.0D) return MAX_SWEEP_SUBDIVISIONS + 1;
        return Math.max(1, (int) Math.ceil(distance / MAX_SWEEP_STEP));
    }

    static boolean laneCorridorsConflict(List<BlockPos> first, double firstWidth,
                                         List<BlockPos> second, double secondWidth,
                                         double spacing) {
        if (first == null || first.isEmpty() || second == null || second.isEmpty()
                || !Double.isFinite(firstWidth) || firstWidth <= 0.0D
                || !Double.isFinite(secondWidth) || secondWidth <= 0.0D
                || !Double.isFinite(spacing) || spacing < 0.0D) return true;
        if (first.stream().anyMatch(point -> point == null)
                || second.stream().anyMatch(point -> point == null)) return true;
        double minimumDistance = (firstWidth + secondWidth) * 0.5D + spacing;
        double minimumDistanceSqr = minimumDistance * minimumDistance;
        for (int firstIndex = 0; firstIndex < Math.max(1, first.size() - 1); firstIndex++) {
            BlockPos firstStart = first.get(firstIndex);
            BlockPos firstEnd = first.get(Math.min(firstIndex + 1, first.size() - 1));
            for (int secondIndex = 0; secondIndex < Math.max(1, second.size() - 1); secondIndex++) {
                BlockPos secondStart = second.get(secondIndex);
                BlockPos secondEnd = second.get(Math.min(secondIndex + 1, second.size() - 1));
                if (segmentDistanceSquared(firstStart, firstEnd, secondStart, secondEnd) <= minimumDistanceSqr) {
                    return true;
                }
            }
        }
        return false;
    }

    static <T> boolean transferLaneClaim(Map<UUID, T> claims, UUID owner, T replacement,
                                         BiPredicate<T, T> conflicts) {
        if (claims == null || owner == null || replacement == null || conflicts == null) return false;
        for (Map.Entry<UUID, T> entry : claims.entrySet()) {
            if (!owner.equals(entry.getKey()) && conflicts.test(replacement, entry.getValue())) return false;
        }
        claims.put(owner, replacement);
        return true;
    }

    private static double segmentDistanceSquared(BlockPos firstStart, BlockPos firstEnd,
                                                 BlockPos secondStart, BlockPos secondEnd) {
        Vec3 first = new Vec3(firstStart.getX(), firstStart.getY(), firstStart.getZ());
        Vec3 firstDirection = new Vec3(firstEnd.getX() - firstStart.getX(),
                firstEnd.getY() - firstStart.getY(), firstEnd.getZ() - firstStart.getZ());
        Vec3 second = new Vec3(secondStart.getX(), secondStart.getY(), secondStart.getZ());
        Vec3 secondDirection = new Vec3(secondEnd.getX() - secondStart.getX(),
                secondEnd.getY() - secondStart.getY(), secondEnd.getZ() - secondStart.getZ());
        Vec3 offset = first.subtract(second);
        double firstLengthSqr = firstDirection.lengthSqr();
        double secondLengthSqr = secondDirection.lengthSqr();
        double cross = firstDirection.dot(secondDirection);
        double firstProjection = firstDirection.dot(offset);
        double secondProjection = secondDirection.dot(offset);
        double denominator = firstLengthSqr * secondLengthSqr - cross * cross;
        double firstParameter = denominator < 1.0E-12D ? 0.0D
                : Mth.clamp((cross * secondProjection - secondLengthSqr * firstProjection) / denominator,
                0.0D, 1.0D);
        double secondParameter = secondLengthSqr < 1.0E-12D ? 0.0D
                : Mth.clamp((cross * firstParameter + secondProjection) / secondLengthSqr, 0.0D, 1.0D);
        if (secondParameter == 0.0D && firstLengthSqr > 1.0E-12D) {
            firstParameter = Mth.clamp(-firstProjection / firstLengthSqr, 0.0D, 1.0D);
        } else if (secondParameter == 1.0D && firstLengthSqr > 1.0E-12D) {
            firstParameter = Mth.clamp((cross - firstProjection) / firstLengthSqr, 0.0D, 1.0D);
        }
        Vec3 closest = offset.add(firstDirection.scale(firstParameter))
                .subtract(secondDirection.scale(secondParameter));
        return closest.lengthSqr();
    }
}