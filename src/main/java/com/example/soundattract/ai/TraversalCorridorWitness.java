package com.example.soundattract.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

final class TraversalCorridorWitness {
    static final int CENTERLINE_BUDGET = 96;
    static final int ENVELOPE_BUDGET = 2048;
    static final int SHAPE_TEST_BUDGET = 8192;

    record WitnessMember(UUID id, ResourceLocation type, int ceilWidthUnits, int ceilHeightUnits) {
        String fingerprint() {
            return Objects.toString(id) + "|" + Objects.toString(type) + "|" + ceilWidthUnits + "|" + ceilHeightUnits;
        }
    }

    private record WitnessCell(BlockPos pos, String fingerprint) {
        WitnessCell {
            pos = pos == null ? null : pos.immutable();
            fingerprint = fingerprint == null ? "" : fingerprint;
        }
    }

    private record EnvelopeBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        EnvelopeBounds {
            if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                    || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                    || maxX < minX || maxY < minY || maxZ < minZ) {
                throw new IllegalArgumentException("malformed envelope");
            }
        }

        static EnvelopeBounds from(AABB box) {
            return new EnvelopeBounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        }
    }

    private final ResourceLocation dimension;
    private final String corridorId;
    private final List<WitnessMember> roster;
    private final long rosterSignature;
    private final long membershipToken;
    private final String identityFingerprint;
    private final EnvelopeBounds maxEnvelope;
    private final Map<Long, WitnessCell> observedCenterline;
    private final Map<Long, WitnessCell> observedEnvelope;
    private int shapeTests;

    private TraversalCorridorWitness(ResourceLocation dimension, String corridorId, List<WitnessMember> roster, long rosterSignature,
                                     long membershipToken, String identityFingerprint, EnvelopeBounds maxEnvelope,
                                     Map<Long, WitnessCell> observedCenterline, Map<Long, WitnessCell> observedEnvelope,
                                     int shapeTests) {
        this.dimension = dimension;
        this.corridorId = corridorId == null ? "" : corridorId;
        this.roster = List.copyOf(roster);
        this.rosterSignature = rosterSignature;
        this.membershipToken = membershipToken;
        this.identityFingerprint = identityFingerprint == null ? "" : identityFingerprint;
        this.maxEnvelope = maxEnvelope;
        this.observedCenterline = observedCenterline;
        this.observedEnvelope = observedEnvelope;
        this.shapeTests = shapeTests;
    }

    static TraversalCorridorWitness create(ResourceLocation dimension, List<Mob> mobs, AABB maxEnvelope) {
        return create(dimension, "", mobs, maxEnvelope);
    }

    static TraversalCorridorWitness create(ResourceLocation dimension, String corridorId, List<Mob> mobs, AABB maxEnvelope) {
        if (dimension == null || mobs == null || mobs.isEmpty() || maxEnvelope == null) return null;
        List<WitnessMember> roster = new ArrayList<>();
        for (Mob mob : mobs) {
            if (mob == null) return null;
            ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
            int ceilWidthUnits = (int) Math.ceil(mob.getBbWidth() * 4096.0D);
            int ceilHeightUnits = (int) Math.ceil(mob.getBbHeight() * 4096.0D);
            roster.add(new WitnessMember(mob.getUUID(), type, ceilWidthUnits, ceilHeightUnits));
        }
        roster.sort(Comparator.comparing(WitnessMember::id).thenComparing(member -> Objects.toString(member.type())));
        EnvelopeBounds bounds = EnvelopeBounds.from(maxEnvelope);
        String fingerprint = identityFingerprint(dimension, corridorId, roster, bounds);
        long signature = stableToken("roster|" + fingerprint);
        long membershipToken = stableToken("membership|" + fingerprint);
        return new TraversalCorridorWitness(dimension, corridorId, roster, signature, membershipToken, fingerprint, bounds,
                new LinkedHashMap<>(), new LinkedHashMap<>(), 0);
    }

    static TraversalCorridorWitness forTest(ResourceLocation dimension, List<UUID> rosterIds, AABB maxEnvelope) {
        return forTest(dimension, "", rosterIds, maxEnvelope, 4096, 4096);
    }

    static TraversalCorridorWitness forTest(ResourceLocation dimension, List<UUID> rosterIds, AABB maxEnvelope,
                                            int ceilWidthUnits, int ceilHeightUnits) {
        return forTest(dimension, "", rosterIds, maxEnvelope, ceilWidthUnits, ceilHeightUnits);
    }

    static TraversalCorridorWitness forTest(ResourceLocation dimension, String corridorId, List<UUID> rosterIds, AABB maxEnvelope,
                                            int ceilWidthUnits, int ceilHeightUnits) {
        List<WitnessMember> roster = new ArrayList<>();
        for (UUID id : rosterIds) {
            roster.add(new WitnessMember(id, ResourceLocation.parse("minecraft:zombie"), ceilWidthUnits, ceilHeightUnits));
        }
        roster.sort(Comparator.comparing(WitnessMember::id).thenComparing(member -> Objects.toString(member.type())));
        EnvelopeBounds bounds = EnvelopeBounds.from(maxEnvelope);
        String fingerprint = identityFingerprint(dimension, corridorId, roster, bounds);
        long signature = stableToken("roster|" + fingerprint);
        long membershipToken = stableToken("membership|" + fingerprint);
        return new TraversalCorridorWitness(dimension, corridorId, roster, signature, membershipToken, fingerprint, bounds,
                new LinkedHashMap<>(), new LinkedHashMap<>(), 0);
    }

    TraversalCorridorWitness copy() {
        return new TraversalCorridorWitness(dimension, corridorId, roster, rosterSignature, membershipToken, identityFingerprint, maxEnvelope,
                new LinkedHashMap<>(observedCenterline), new LinkedHashMap<>(observedEnvelope), shapeTests);
    }

    ResourceLocation dimension() {
        return dimension;
    }

    String corridorId() {
        return corridorId;
    }

    long rosterSignature() {
        return rosterSignature;
    }

    long membershipToken() {
        return membershipToken;
    }

    String identityFingerprint() {
        return identityFingerprint;
    }

    double maxWidth() {
        double width = 0.0D;
        for (WitnessMember member : roster) {
            width = Math.max(width, member.ceilWidthUnits() / 4096.0D);
        }
        return width;
    }

    double maxHeight() {
        double height = 0.0D;
        for (WitnessMember member : roster) {
            height = Math.max(height, member.ceilHeightUnits() / 4096.0D);
        }
        return height;
    }

    int centerlineCount() {
        return observedCenterline.size();
    }

    int envelopeCount() {
        return observedEnvelope.size();
    }

    int shapeTests() {
        return shapeTests;
    }

    boolean identityMatches(TraversalCorridorWitness other) {
        return other != null
                && Objects.equals(dimension, other.dimension)
                && rosterSignature == other.rosterSignature
                && membershipToken == other.membershipToken
                && Objects.equals(corridorId, other.corridorId)
                && Objects.equals(identityFingerprint, other.identityFingerprint)
                && Objects.equals(roster, other.roster)
                && Objects.equals(maxEnvelope, other.maxEnvelope);
    }

    boolean rosterMatches(ResourceLocation currentDimension, Set<UUID> currentRoster) {
        if (!Objects.equals(dimension, currentDimension) || currentRoster == null) return false;
        Set<UUID> expected = new LinkedHashSet<>();
        for (WitnessMember member : roster) expected.add(member.id());
        return expected.equals(currentRoster);
    }

    boolean observeCenterline(ServerLevel level, BlockPos pos) {
        return observe(level, pos, observedCenterline, CENTERLINE_BUDGET);
    }

    boolean observeEnvelopeBox(ServerLevel level, AABB box) {
        if (level == null || box == null) return false;
        int minX = net.minecraft.util.Mth.floor(box.minX);
        int maxX = net.minecraft.util.Mth.floor(box.maxX - 1.0E-7D);
        int minY = net.minecraft.util.Mth.floor(box.minY);
        int maxY = net.minecraft.util.Mth.floor(box.maxY - 1.0E-7D);
        int minZ = net.minecraft.util.Mth.floor(box.minZ);
        int maxZ = net.minecraft.util.Mth.floor(box.maxZ - 1.0E-7D);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!observe(level, new BlockPos(x, y, z), observedEnvelope, ENVELOPE_BUDGET)) return false;
                }
            }
        }
        return true;
    }

    boolean recordShapeTest() {
        if (shapeTests >= SHAPE_TEST_BUDGET) return false;
        shapeTests++;
        return true;
    }

    void beginShapeTestPass() {
        shapeTests = 0;
    }

    boolean recordCenterlineFingerprintForTest(BlockPos pos, String fingerprint) {
        return recordForTest(pos, fingerprint, observedCenterline, CENTERLINE_BUDGET);
    }

    boolean recordEnvelopeFingerprintForTest(BlockPos pos, String fingerprint) {
        return recordForTest(pos, fingerprint, observedEnvelope, ENVELOPE_BUDGET);
    }

    boolean revalidate(ServerLevel level) {
        if (level == null || !Objects.equals(level.dimension().location(), dimension)) return false;
        for (WitnessCell cell : observedCenterline.values()) {
            if (!matches(level, cell)) return false;
        }
        for (WitnessCell cell : observedEnvelope.values()) {
            if (!matches(level, cell)) return false;
        }
        return true;
    }

    private boolean contains(AABB box) {
        double margin = 1.0E-4D;
        return box.minX >= maxEnvelope.minX - margin && box.minY >= maxEnvelope.minY - margin
                && box.minZ >= maxEnvelope.minZ - margin && box.maxX <= maxEnvelope.maxX + margin
                && box.maxY <= maxEnvelope.maxY + margin && box.maxZ <= maxEnvelope.maxZ + margin;
    }

    private static String identityFingerprint(ResourceLocation dimension, String corridorId, List<WitnessMember> roster, EnvelopeBounds bounds) {
        StringBuilder builder = new StringBuilder();
        builder.append(Objects.toString(dimension)).append('|');
        builder.append(Objects.toString(corridorId)).append('|');
        builder.append(bounds.minX).append(',').append(bounds.minY).append(',').append(bounds.minZ).append('|');
        builder.append(bounds.maxX).append(',').append(bounds.maxY).append(',').append(bounds.maxZ).append('|');
        for (WitnessMember member : roster) {
            builder.append(member.fingerprint()).append(';');
        }
        return builder.toString();
    }

    private static long stableToken(String value) {
        long hash = 1125899906842597L;
        for (int i = 0; i < value.length(); i++) {
            hash = 31L * hash + value.charAt(i);
        }
        return hash;
    }

    private static boolean observe(ServerLevel level, BlockPos pos, Map<Long, WitnessCell> sink, int budget) {
        if (level == null || pos == null) return false;
        long key = pos.asLong();
        WitnessCell existing = sink.get(key);
        String fingerprint = WallProfileScanner.stateFingerprint(level, pos);
        if (fingerprint == null || fingerprint.isEmpty() || "unloaded".equals(fingerprint)) return false;
        if (existing != null) return Objects.equals(existing.fingerprint(), fingerprint);
        if (sink.size() >= budget) return false;
        sink.put(key, new WitnessCell(pos, fingerprint));
        return true;
    }

    private static boolean matches(ServerLevel level, WitnessCell cell) {
        if (cell == null || cell.pos() == null) return false;
        return Objects.equals(cell.fingerprint(), WallProfileScanner.stateFingerprint(level, cell.pos()));
    }

    private static boolean recordForTest(BlockPos pos, String fingerprint, Map<Long, WitnessCell> sink, int budget) {
        if (pos == null || fingerprint == null || fingerprint.isEmpty() || "unloaded".equals(fingerprint)) return false;
        long key = pos.asLong();
        WitnessCell existing = sink.get(key);
        if (existing != null) return Objects.equals(existing.fingerprint(), fingerprint);
        if (sink.size() >= budget) return false;
        sink.put(key, new WitnessCell(pos, fingerprint));
        return true;
    }
}