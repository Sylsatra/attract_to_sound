package com.example.soundattract.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

final class CanonicalFeetCorridor {
    static final int MAX_CORRIDOR_INTERVAL_CELLS = 96;
    enum MovementPhase { SOURCE, CROSSING, LANDING, EGRESS }
    enum PhaseMode { WALK, TILT, JUMP, DROP }
    record Phase(MovementPhase type, int startIndex, int endIndex, PhaseMode mode) {
        Phase {
            Objects.requireNonNull(type);
            Objects.requireNonNull(mode);
            if (startIndex < 0 || endIndex < startIndex) throw new IllegalArgumentException("invalid phase bounds");
        }
    }
    record Envelope(List<BlockPos> cells) {
        Envelope {
            if (cells == null) throw new IllegalArgumentException("missing envelope");
            cells = immutableCells(cells);
        }
    }
    record ValidatedContinuation(String scenario, BlockPos egressFeet, BlockPos supportCell,
                                 double progress, String fingerprint, String corridorId, long revision) {
        ValidatedContinuation {
            if (scenario == null || egressFeet == null || supportCell == null || !Double.isFinite(progress)
                    || fingerprint == null || corridorId == null || revision < 0L) {
                throw new IllegalArgumentException("invalid continuation");
            }
            egressFeet = egressFeet.immutable();
        }
    }
    private final Identifier dimension;
    private final String scenario;
    private final BlockPos pivot;
    private final BlockPos tacticalGoal;
    private final BlockPos takeoffFeet;
    private final BlockPos bridgeLandingFeet;
    private final BlockPos egressFeet;
    private final BlockPos obstacleContactCell;
    private final List<HazardWitness> hazardWitnesses;
    private final List<BlockPos> hazardCells;
    private final List<WallProfileScanner.CollisionKind> hazardKinds;
    private final List<Long> sourceRayIds;
    private final List<BlockPos> centerlineCells;
    private final String corridorId;
    private final int crossingStartIndex;
    private final int landingIndex;
    private final int egressIndex;
    private final List<Phase> phases;
    private final Envelope bodyEnvelope;
    private final Envelope supportEnvelope;
    private final Envelope hazardEnvelope;
    private final ValidatedContinuation continuation;
    private final long requestRevision;

    static record HazardWitness(BlockPos cell, WallProfileScanner.CollisionKind kind) {
        HazardWitness {
            cell = cell == null ? null : cell.immutable();
        }
    }

    CanonicalFeetCorridor(Identifier dimension, String scenario, BlockPos pivot, BlockPos takeoffFeet,
                          BlockPos tacticalGoal, BlockPos bridgeLandingFeet, BlockPos egressFeet, BlockPos obstacleContactCell,
                          List<BlockPos> hazardCells, List<WallProfileScanner.CollisionKind> hazardKinds,
                          List<Long> sourceRayIds) {
        this(dimension, scenario, pivot, takeoffFeet, tacticalGoal, bridgeLandingFeet, egressFeet,
                obstacleContactCell, hazardCells, hazardKinds, sourceRayIds, null);
    }

    private CanonicalFeetCorridor(Identifier dimension, String scenario, BlockPos pivot, BlockPos takeoffFeet,
                                  BlockPos tacticalGoal, BlockPos bridgeLandingFeet, BlockPos egressFeet,
                                  BlockPos obstacleContactCell, List<BlockPos> hazardCells,
                                  List<WallProfileScanner.CollisionKind> hazardKinds, List<Long> sourceRayIds,
                                  List<BlockPos> executableCenterline) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.scenario = Objects.requireNonNull(scenario, "scenario");
        this.pivot = immutable(pivot);
        this.tacticalGoal = immutable(tacticalGoal);
        this.takeoffFeet = immutable(takeoffFeet);
        this.bridgeLandingFeet = immutable(bridgeLandingFeet);
        this.egressFeet = immutable(egressFeet);
        this.obstacleContactCell = immutable(obstacleContactCell);
        this.hazardWitnesses = copyHazardWitnesses(hazardCells, hazardKinds);
        this.hazardCells = this.hazardWitnesses.stream().map(HazardWitness::cell).toList();
        this.hazardKinds = this.hazardWitnesses.stream().map(HazardWitness::kind).toList();
        this.sourceRayIds = sourceRayIds == null ? List.of() : List.copyOf(sourceRayIds);
        this.centerlineCells = List.copyOf(executableCenterline == null
                ? buildCenterline(this.takeoffFeet, this.bridgeLandingFeet, this.egressFeet)
                : executableCenterline);
        this.crossingStartIndex = indexOfRequired(this.centerlineCells, this.takeoffFeet, "takeoff");
        this.landingIndex = indexOfRequired(this.centerlineCells, this.bridgeLandingFeet, "landing");
        this.egressIndex = indexOfRequired(this.centerlineCells, this.egressFeet, "egress");
        if (this.crossingStartIndex > this.landingIndex || this.landingIndex > this.egressIndex
                || !isConnected(this.centerlineCells)) throw new IllegalArgumentException("centerline_order_invalid");
        this.phases = List.of(new Phase(MovementPhase.SOURCE, 0, crossingStartIndex, PhaseMode.WALK),
                new Phase(MovementPhase.CROSSING, crossingStartIndex, landingIndex, phaseMode(scenario)),
                new Phase(MovementPhase.LANDING, landingIndex, egressIndex, PhaseMode.WALK),
                new Phase(MovementPhase.EGRESS, egressIndex, egressIndex, PhaseMode.WALK));
        this.bodyEnvelope = new Envelope(this.centerlineCells);
        this.supportEnvelope = new Envelope(List.of(this.takeoffFeet, this.bridgeLandingFeet, this.egressFeet));
        this.hazardEnvelope = new Envelope(this.hazardCells);
        this.requestRevision = 0L;
        String identity = identityPayload();
        this.corridorId = sha256Hex(identity);
        this.continuation = new ValidatedContinuation(this.scenario, this.egressFeet,
                this.bridgeLandingFeet, progress(this.takeoffFeet, this.egressFeet, this.tacticalGoal),
                sha256Hex(Objects.toString(this.egressFeet) + "|" + Objects.toString(this.bridgeLandingFeet)),
                this.corridorId, this.requestRevision);
    }

    CanonicalFeetCorridor withCenterline(List<BlockPos> executableCenterline) {
        if (executableCenterline == null || executableCenterline.size() < 2) return null;
        return new CanonicalFeetCorridor(dimension, scenario, pivot, takeoffFeet, tacticalGoal,
                bridgeLandingFeet, egressFeet, obstacleContactCell, hazardCells, hazardKinds, sourceRayIds,
                executableCenterline);
    }

    static CanonicalFeetCorridor fromEvidence(Identifier dimension, BlockPos pivot,
                                              LivingWallCoordinator.ConstructionScenario scenario,
                                              WallProfileScanner.TargetEvidence evidence, BlockPos egressFeet) {
        if (pivot == null || scenario == null || evidence == null) return null;
        BlockPos tacticalGoal = evidence.anchor();
        BlockPos takeoff = evidence.sourceApproachSurface() != null ? evidence.sourceApproachSurface() : pivot;
        BlockPos contact = evidence.wallFace();
        if (tacticalGoal == null || egressFeet == null) return null;
        BlockPos landing = evidence.landingSurface() != null ? evidence.landingSurface() : tacticalGoal;
        return new CanonicalFeetCorridor(dimension, scenario.name(), pivot, takeoff, tacticalGoal, landing, egressFeet, contact,
                evidence.hazardCells(), evidence.hazardKinds(), evidence.sourceRayIds());
    }

    static CanonicalFeetCorridor maybeFromEvidence(Identifier dimension, BlockPos pivot,
                                                   LivingWallCoordinator.ConstructionScenario scenario,
                                                   WallProfileScanner.TargetEvidence evidence) {
        if (dimension == null || pivot == null || scenario == null || evidence == null) return null;
        if (evidence.sourceApproachSurface() == null || evidence.landingSurface() == null) return null;
        BlockPos egress = evidence.invasionSurface();
        if (egress == null) return null;
        if (egress.equals(evidence.landingSurface()) && !egress.equals(evidence.anchor())) return null;
        return fromEvidence(dimension, pivot, scenario, evidence, egress);
    }

    String corridorId() {
        return corridorId;
    }

    BlockPos buildPivot() {
        return pivot;
    }

    BlockPos pivot() {
        return pivot;
    }

    BlockPos tacticalGoal() {
        return tacticalGoal;
    }

    BlockPos takeoffFeet() {
        return takeoffFeet;
    }

    BlockPos landingFeet() {
        return bridgeLandingFeet;
    }

    BlockPos bridgeLandingFeet() {
        return bridgeLandingFeet;
    }

    BlockPos egressFeet() {
        return egressFeet;
    }

    Vec3 traversalForward() {
        Vec3 delta = Vec3.atBottomCenterOf(egressFeet).subtract(Vec3.atBottomCenterOf(pivot));
        double length = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        return length < 1.0E-6D ? Vec3.ZERO : new Vec3(delta.x / length, 0.0D, delta.z / length);
    }

    BlockPos obstacleContactCell() {
        return obstacleContactCell;
    }

    List<BlockPos> hazardCells() {
        return hazardCells;
    }

    List<WallProfileScanner.CollisionKind> hazardKinds() {
        return hazardKinds;
    }

    List<Long> sourceRayIds() {
        return sourceRayIds;
    }

    List<BlockPos> centerlineCells() {
        return centerlineCells;
    }

    List<Phase> phases() { return phases; }
    Envelope bodyEnvelope() { return bodyEnvelope; }
    Envelope supportEnvelope() { return supportEnvelope; }
    Envelope hazardEnvelope() { return hazardEnvelope; }
    ValidatedContinuation continuation() { return continuation; }
    long requestRevision() { return requestRevision; }

    int phaseStartIndex(MovementPhase phase) {
        return switch (phase) {
            case SOURCE -> 0;
            case CROSSING, LANDING -> landingIndex;
            case EGRESS -> egressIndex;
        };
    }

    int phaseEndIndex(MovementPhase phase) {
        return switch (phase) {
            case SOURCE -> crossingStartIndex;
            case CROSSING -> landingIndex;
            case LANDING, EGRESS -> egressIndex;
        };
    }

    boolean matchesEvidence(WallProfileScanner.TargetEvidence evidence) {
        if (evidence == null) return false;
        BlockPos tacticalGoal = evidence.anchor();
        BlockPos landing = evidence.landingSurface() != null ? evidence.landingSurface() : tacticalGoal;
        BlockPos takeoff = evidence.sourceApproachSurface() != null ? evidence.sourceApproachSurface() : pivot;
        BlockPos contact = evidence.wallFace();
        if (!Objects.equals(this.tacticalGoal, tacticalGoal)) return false;
        return Objects.equals(takeoffFeet, takeoff)
                && Objects.equals(bridgeLandingFeet, landing)
                && Objects.equals(egressFeet, evidence.invasionSurface())
                && Objects.equals(obstacleContactCell, contact)
                && Objects.equals(hazardWitnesses, copyHazardWitnesses(evidence.hazardCells(), evidence.hazardKinds()))
                && Objects.equals(sourceRayIds, evidence.sourceRayIds());
    }

    private String identityPayload() {
        return Objects.toString(dimension) + "|" + scenario + "|" + Objects.toString(pivot) + "|"
                + Objects.toString(tacticalGoal) + "|" + Objects.toString(takeoffFeet) + "|"
                + Objects.toString(bridgeLandingFeet) + "|"
                + Objects.toString(egressFeet) + "|" + Objects.toString(obstacleContactCell) + "|"
                + hazardWitnesses + "|" + sourceRayIds + "|" + centerlineCells;
    }

    private static BlockPos immutable(BlockPos pos) {
        return pos == null ? null : pos.immutable();
    }

    private static List<HazardWitness> copyHazardWitnesses(List<BlockPos> cells, List<WallProfileScanner.CollisionKind> kinds) {
        if (cells == null || cells.isEmpty()) return List.of();
        if (kinds == null || cells.size() != kinds.size()) throw new IllegalArgumentException("hazard_pair_length_mismatch");
        List<HazardWitness> copy = new ArrayList<>(cells.size());
        for (int i = 0; i < cells.size(); i++) {
            BlockPos cell = cells.get(i);
            WallProfileScanner.CollisionKind kind = kinds != null && i < kinds.size() ? kinds.get(i) : null;
            if (cell == null || kind == null) throw new IllegalArgumentException("hazard_pair_missing");
            copy.add(new HazardWitness(cell, kind));
        }
        return List.copyOf(copy);
    }

    private static List<BlockPos> buildCenterline(BlockPos start, BlockPos middle, BlockPos end) {
        if (start == null || middle == null || end == null) throw new IllegalArgumentException("missing_centerline_endpoint");
        List<BlockPos> cells = new ArrayList<>();
        appendSupercover(cells, start, middle);
        appendSupercover(cells, middle, end);
        if (cells.isEmpty() || cells.size() > MAX_CORRIDOR_INTERVAL_CELLS) {
            throw new IllegalArgumentException("centerline_budget_exceeded");
        }
        return cells;
    }

    private static int indexOfRequired(List<BlockPos> cells, BlockPos target, String name) {
        for (int i = 0; i < cells.size(); i++) {
            if (target.equals(cells.get(i))) return i;
        }
        throw new IllegalArgumentException(name + "_not_on_centerline");
    }

    private static void appendSupercover(List<BlockPos> sink, BlockPos start, BlockPos end) {
        if (start == null || end == null) return;
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();
        double distance = Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
        int steps = Math.max(1, (int) Math.ceil(distance / 0.25D));
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            BlockPos cell = new BlockPos(
                    Math.round((float) (start.getX() + dx * t)),
                    Math.round((float) (start.getY() + dy * t)),
                    Math.round((float) (start.getZ() + dz * t)));
            if (sink.isEmpty() || !sink.get(sink.size() - 1).equals(cell)) sink.add(cell.immutable());
        }
    }

    private static boolean isConnected(List<BlockPos> cells) {
        for (int i = 1; i < cells.size(); i++) {
            BlockPos a = cells.get(i - 1), b = cells.get(i);
            if (Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ()))) > 1) return false;
        }
        return !cells.isEmpty();
    }

    private static PhaseMode phaseMode(String scenario) {
        if ("JUMP_BRIDGE".equals(scenario)) return PhaseMode.JUMP;
        if ("DROP_TO_LEDGE".equals(scenario)) return PhaseMode.DROP;
        return PhaseMode.TILT;
    }

    private static double progress(BlockPos from, BlockPos to, BlockPos goal) {
        if (from == null || to == null || goal == null) return 0.0D;
        double gx = goal.getX() - from.getX(), gy = goal.getY() - from.getY(), gz = goal.getZ() - from.getZ();
        double length = Math.sqrt(gx * gx + gy * gy + gz * gz);
        return length == 0.0D ? 0.0D : Math.max(0.0D, ((to.getX() - from.getX()) * gx + (to.getY() - from.getY()) * gy + (to.getZ() - from.getZ()) * gz) / length);
    }

    private static List<BlockPos> immutableCells(List<BlockPos> cells) {
        List<BlockPos> copy = new ArrayList<>(cells.size());
        for (BlockPos cell : cells) {
            if (cell == null) throw new IllegalArgumentException("null envelope cell");
            copy.add(cell.immutable());
        }
        return List.copyOf(copy);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}