package com.example.soundattract.ai;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

record LivingBridgeRouteSnapshot(UUID reservationId, UUID ownerId, UUID formationId, String dimension,
                                 long commitGeneration, UUID transactionId, long routeRevision,
                                 String movementMode, String lifecycleState, boolean originalNoPhysics,
                                 boolean originalNoGravity, boolean recoveryRequired,
                                 List<Point> corridor, long halfWidth, List<Point> recoveryRoute,
                                 int recoveryIndex, Point safeEndpoint, int lastChunkX, int lastChunkZ) {
    private static final int SCHEMA = 2;
    private static final int MAX_ROUTE_POINTS = 256;
    private static final long COORDINATE_LIMIT = 30_000_000L * 4096L;

    record Point(long x, long y, long z) {
        Point {
            if (x < -COORDINATE_LIMIT || x > COORDINATE_LIMIT
                    || y < -COORDINATE_LIMIT || y > COORDINATE_LIMIT
                    || z < -COORDINATE_LIMIT || z > COORDINATE_LIMIT) {
                throw new IllegalArgumentException("quantized route point is out of bounds");
            }
        }

        static Point from(double x, double y, double z) {
            return new Point(quantize(x), quantize(y), quantize(z));
        }

        private static long quantize(double value) {
            if (!Double.isFinite(value) || Math.abs(value) > 30_000_000.0D) {
                throw new IllegalArgumentException("route coordinate is non-finite or out of bounds");
            }
            double scaled = Math.abs(value) * 4096.0D;
            long rounded = (long) Math.floor(scaled + 0.5D);
            return value < 0.0D ? -rounded : rounded;
        }

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("x", x);
            tag.putLong("y", y);
            tag.putLong("z", z);
            return tag;
        }

        static Point fromTag(CompoundTag tag) {
            return new Point(tag.getLongOr("x", 0L), tag.getLongOr("y", 0L), tag.getLongOr("z", 0L));
        }
    }

    LivingBridgeRouteSnapshot {
        if (reservationId == null || ownerId == null || formationId == null || transactionId == null
                || dimension == null || dimension.isBlank() || movementMode == null || movementMode.isBlank()
                || lifecycleState == null || lifecycleState.isBlank() || commitGeneration < 0L
                || routeRevision < 0L || halfWidth <= 0L || corridor == null || corridor.isEmpty()
                || corridor.size() > MAX_ROUTE_POINTS || recoveryRoute == null
                || recoveryRoute.size() > MAX_ROUTE_POINTS || recoveryIndex < 0
                || recoveryIndex > recoveryRoute.size()) {
            throw new IllegalArgumentException("invalid living bridge route snapshot");
        }
        dimension = dimension.toLowerCase(java.util.Locale.ROOT);
        corridor = List.copyOf(corridor);
        recoveryRoute = List.copyOf(recoveryRoute);
    }

    byte[] canonicalBytes() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            writeUuid(out, reservationId);
            writeUuid(out, ownerId);
            writeUuid(out, formationId);
            writeString(out, dimension);
            out.writeLong(commitGeneration);
            writeUuid(out, transactionId);
            out.writeLong(routeRevision);
            writeString(out, movementMode);
            writeString(out, lifecycleState);
            out.writeBoolean(originalNoPhysics);
            out.writeBoolean(originalNoGravity);
            out.writeBoolean(recoveryRequired);
            writePoints(out, corridor);
            out.writeLong(halfWidth);
            writePoints(out, recoveryRoute);
            out.writeInt(recoveryIndex);
            out.writeBoolean(safeEndpoint != null);
            if (safeEndpoint != null) writePoint(out, safeEndpoint);
            out.writeInt(lastChunkX);
            out.writeInt(lastChunkZ);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    String digest() {
        try {
            return HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonicalBytes()));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    boolean exactlyEquals(LivingBridgeRouteSnapshot other) {
        return other != null && digest().equals(other.digest())
                && java.util.Arrays.equals(canonicalBytes(), other.canonicalBytes())
                && reservationId.equals(other.reservationId) && ownerId.equals(other.ownerId)
                && formationId.equals(other.formationId) && dimension.equals(other.dimension)
                && commitGeneration == other.commitGeneration && transactionId.equals(other.transactionId)
                && routeRevision == other.routeRevision && movementMode.equals(other.movementMode)
                && lifecycleState.equals(other.lifecycleState)
                && originalNoPhysics == other.originalNoPhysics && originalNoGravity == other.originalNoGravity
                && recoveryRequired == other.recoveryRequired && corridor.equals(other.corridor)
                && halfWidth == other.halfWidth && recoveryRoute.equals(other.recoveryRoute)
                && recoveryIndex == other.recoveryIndex && java.util.Objects.equals(safeEndpoint, other.safeEndpoint)
                && lastChunkX == other.lastChunkX && lastChunkZ == other.lastChunkZ;
    }

    CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA);
        tag.putString("reservationId", reservationId.toString());
        tag.putString("ownerId", ownerId.toString());
        tag.putString("formationId", formationId.toString());
        tag.putString("dimension", dimension);
        tag.putLong("commitGeneration", commitGeneration);
        tag.putString("transactionId", transactionId.toString());
        tag.putLong("routeRevision", routeRevision);
        tag.putString("movementMode", movementMode);
        tag.putString("lifecycleState", lifecycleState);
        tag.putBoolean("originalNoPhysics", originalNoPhysics);
        tag.putBoolean("originalNoGravity", originalNoGravity);
        tag.putBoolean("recoveryRequired", recoveryRequired);
        tag.put("corridor", pointsTag(corridor));
        tag.putLong("halfWidth", halfWidth);
        tag.put("recoveryRoute", pointsTag(recoveryRoute));
        tag.putInt("recoveryIndex", recoveryIndex);
        if (safeEndpoint != null) tag.put("safeEndpoint", safeEndpoint.toTag());
        tag.putInt("lastChunkX", lastChunkX);
        tag.putInt("lastChunkZ", lastChunkZ);
        tag.putString("snapshotDigest", digest());
        return tag;
    }

    static LivingBridgeRouteSnapshot fromTag(CompoundTag tag) {
        if (tag == null || tag.getIntOr("schema", 0) != SCHEMA) throw new IllegalArgumentException("unsupported route schema");
        LivingBridgeRouteSnapshot snapshot = new LivingBridgeRouteSnapshot(java.util.UUID.fromString(tag.getStringOr("reservationId", "00000000-0000-0000-0000-000000000000")),
                java.util.UUID.fromString(tag.getStringOr("ownerId", "00000000-0000-0000-0000-000000000000")), java.util.UUID.fromString(tag.getStringOr("formationId", "00000000-0000-0000-0000-000000000000")), tag.getStringOr("dimension", ""),
                tag.getLongOr("commitGeneration", 0L), java.util.UUID.fromString(tag.getStringOr("transactionId", "00000000-0000-0000-0000-000000000000")), tag.getLongOr("routeRevision", 0L),
                tag.getStringOr("movementMode", ""), tag.getStringOr("lifecycleState", ""), tag.getBooleanOr("originalNoPhysics", false),
                tag.getBooleanOr("originalNoGravity", false), tag.getBooleanOr("recoveryRequired", false),
                readPoints(tag.getListOrEmpty("corridor")), tag.getLongOr("halfWidth", 0L),
                readPoints(tag.getListOrEmpty("recoveryRoute")), tag.getIntOr("recoveryIndex", 0),
                tag.get("safeEndpoint") instanceof net.minecraft.nbt.CompoundTag
                        ? Point.fromTag(tag.getCompoundOrEmpty("safeEndpoint")) : null,
                tag.getIntOr("lastChunkX", 0), tag.getIntOr("lastChunkZ", 0));
        if (!snapshot.digest().equals(tag.getStringOr("snapshotDigest", ""))) {
            throw new IllegalArgumentException("living bridge route digest mismatch");
        }
        return snapshot;
    }

    private static ListTag pointsTag(List<Point> points) {
        ListTag tags = new ListTag();
        for (Point point : points) tags.add(point.toTag());
        return tags;
    }

    private static List<Point> readPoints(ListTag tags) {
        if (tags.size() > MAX_ROUTE_POINTS) throw new IllegalArgumentException("route point budget exceeded");
        List<Point> points = new ArrayList<>(tags.size());
        for (Tag tag : tags) points.add(Point.fromTag((CompoundTag) tag));
        return List.copyOf(points);
    }

    private static void writePoints(DataOutputStream out, List<Point> points) throws IOException {
        out.writeInt(points.size());
        for (Point point : points) writePoint(out, point);
    }

    private static void writePoint(DataOutputStream out, Point point) throws IOException {
        out.writeLong(point.x());
        out.writeLong(point.y());
        out.writeLong(point.z());
    }

    private static void writeUuid(DataOutputStream out, UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits());
        out.writeLong(value.getLeastSignificantBits());
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }
}