package com.example.soundattract.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record LivingBridgePositionPlan(
        String dimension,
        long windowGeneration,
        long startTick,
        long expiryTick,
        BlockPos sourceOrigin,
        BlockPos lockedDestination,
        Vec3 direction,
        String scenario,
        int wallHeight,
        int gapWidth,
        long terrainEpoch,
        long terrainFingerprint,
        List<UUID> rosterIdentity,
        long requestRevision) {

    public static final long WINDOW_TICKS = 40L;
    public static final int DESTINATION_BUCKET_SIZE = 4;

    public LivingBridgePositionPlan {
        dimension = Objects.requireNonNullElse(dimension, "");
        if (windowGeneration < 0L || startTick < 0L || expiryTick != startTick + WINDOW_TICKS) {
            throw new IllegalArgumentException("invalid scan window");
        }
        sourceOrigin = Objects.requireNonNull(sourceOrigin, "sourceOrigin").immutable();
        lockedDestination = Objects.requireNonNull(lockedDestination, "lockedDestination").immutable();
        direction = Objects.requireNonNull(direction, "direction");
        if (!Double.isFinite(direction.x) || !Double.isFinite(direction.y) || !Double.isFinite(direction.z)) {
            throw new IllegalArgumentException("invalid direction");
        }
        scenario = Objects.requireNonNullElse(scenario, "NONE");
        if (wallHeight < 0 || gapWidth < 0 || terrainEpoch < 0L || requestRevision < 0L) {
            throw new IllegalArgumentException("invalid plan metadata");
        }
        rosterIdentity = rosterIdentity == null ? List.of() : rosterIdentity.stream().filter(Objects::nonNull).sorted().toList();
    }

    public boolean isLive(long now) {
        return startTick <= now && now < expiryTick;
    }

    public boolean hasCurrentTerrain(long currentEpoch) {
        return terrainEpoch == currentEpoch;
    }

    public boolean destinationBucketContains(BlockPos position) {
        if (position == null) return false;
        return bucket(position.getX()) == bucket(lockedDestination.getX())
                && bucket(position.getY()) == bucket(lockedDestination.getY())
                && bucket(position.getZ()) == bucket(lockedDestination.getZ());
    }

    public boolean acceptsDestination(BlockPos position, boolean endpointSafe) {
        return endpointSafe && destinationBucketContains(position);
    }

    public boolean acceptsRoster(List<UUID> roster) {
        if (roster == null) return rosterIdentity.isEmpty();
        return roster.stream().filter(Objects::nonNull).sorted().toList().equals(rosterIdentity);
    }

    public WorkerToken workerToken(UUID workerId, BlockPos position, BlockPos supportCell, boolean grounded,
                                   long positionFingerprint) {
        return new WorkerToken(windowGeneration, requestRevision, workerId, position, supportCell, grounded,
                positionFingerprint, lockedDestination, terrainEpoch, rosterIdentity);
    }

    private static int bucket(int coordinate) {
        return Math.floorDiv(coordinate, DESTINATION_BUCKET_SIZE);
    }

    public record WorkerToken(long windowGeneration, long requestRevision, UUID workerId, BlockPos position,
                              BlockPos supportCell, boolean grounded, long positionFingerprint,
                              BlockPos lockedDestination, long terrainEpoch, List<UUID> rosterIdentity) {
        public WorkerToken {
            workerId = Objects.requireNonNull(workerId, "workerId");
            position = Objects.requireNonNull(position, "position").immutable();
            supportCell = supportCell == null ? null : supportCell.immutable();
            lockedDestination = Objects.requireNonNull(lockedDestination, "lockedDestination").immutable();
            rosterIdentity = rosterIdentity == null ? List.of() : List.copyOf(rosterIdentity);
        }
    }
}