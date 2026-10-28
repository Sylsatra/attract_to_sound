package com.example.soundattract.ai;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class LivingWallBridgeAdapterPlanner {
    static final int MAX_BODIES = 8;
    static final int MAX_ROPE_BRIDGE_LENGTH = 24;
    static final int MAX_DROP_BRIDGE_LENGTH = 16;
    static final int RECRUITMENT_RADIUS = 32;
    static final int IB3D_ADMISSION_SAFE_FALL_DEPTH = 4;

    private LivingWallBridgeAdapterPlanner() {
    }

    enum AdapterKind {
        DIRECT,
        DROP,
        JUMP,
        TILT,
        PILLAR_TILT
    }

    enum LegacyProjection {
        NONE,
        JUMP_BRIDGE,
        WALL_LEDGE,
        DROP_TO_LEDGE,
        UNSUPPORTED_LEGACY_SCENARIO
    }

    record AdapterSpec(AdapterKind kind, LegacyProjection legacyProjection,
                       boolean directSpan, boolean requiresTilt, boolean pillarAssembly,
                       String stagePlan, String handoffPlan, String exitPlan, String cleanupPlan) {
        AdapterSpec {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(legacyProjection, "legacyProjection");
            Objects.requireNonNull(stagePlan, "stagePlan");
            Objects.requireNonNull(handoffPlan, "handoffPlan");
            Objects.requireNonNull(exitPlan, "exitPlan");
            Objects.requireNonNull(cleanupPlan, "cleanupPlan");
        }
    }

    record Candidate(String label, AdapterKind kind, int bodyCount, double length3d, int hazardCells,
                     int dropEdgeCells, int softFluidCells, int trapdoorCells, int portalCells,
                     int unknownRequiredCells, int requiredCellCount, int routePhaseCount,
                     int reachabilityProbeCount, int hazardTransitionCount, int approachDistance,
                     int pivotThreatDistance, int endpointThreatDistance, boolean supportedEgress,
                     boolean shortDropEgress, boolean safeFallEgress, int connectedStructuralCells,
                     long pivot, long anchor, long span) {
        Candidate {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(kind, "kind");
        }
    }

    record ScoredCandidate(Candidate candidate, AdapterSpec spec, int scoreBp, int lengthMm) {
    }

    record ScoreVector(int bodyBp, int lengthBp, int hazardBp, int unknownBp, int replanBp, int approachBp,
                       int threatBp, int egressBp, int structureBp) {
    }

    static Optional<AdapterSpec> adapterFor(String legacyScenario, AdapterKind kind) {
        if (kind == null) return Optional.empty();
        if ("HAZARD_AND_WALL".equals(legacyScenario)) return Optional.empty();
        return switch (kind) {
            case DIRECT -> "NONE".equals(legacyScenario) || legacyScenario == null
                    ? Optional.of(new AdapterSpec(kind, LegacyProjection.NONE, true, false, false,
                    "BASE_APPROACH → DIRECT_STAGE → DIRECT_SPAN",
                    "DIRECT_CROSSER_HANDOFF", "DIRECT_EXIT", "DIRECT_CLEANUP"))
                    : Optional.empty();
            case DROP -> "DROP_TO_LEDGE".equals(legacyScenario)
                    ? Optional.of(new AdapterSpec(kind, LegacyProjection.DROP_TO_LEDGE, true, false, false,
                    "BASE_APPROACH → DROP_STAGE → LANDING_STAGE",
                    "DROP_CROSSER_HANDOFF", "DROP_EXIT", "DROP_CLEANUP"))
                    : Optional.empty();
            case JUMP -> "JUMP_BRIDGE".equals(legacyScenario)
                    ? Optional.of(new AdapterSpec(kind, LegacyProjection.JUMP_BRIDGE, true, false, false,
                    "BASE_APPROACH → JUMP_STAGE → JUMP_SPAN",
                    "JUMP_CROSSER_HANDOFF", "JUMP_EXIT", "JUMP_CLEANUP"))
                    : Optional.empty();
            case TILT -> {
                if ("WALL_LEDGE".equals(legacyScenario)) yield Optional.empty();
                if (legacyScenario != null && !"NONE".equals(legacyScenario)) yield Optional.empty();
                yield Optional.of(new AdapterSpec(kind, LegacyProjection.NONE, false, true, false,
                        "BASE_APPROACH → TILT_STAGE → NON_PILLAR_TILT_WAVE",
                        "NON_PILLAR_TILT_CROSSER_HANDOFF", "NON_PILLAR_TILT_EXIT", "NON_PILLAR_TILT_CLEANUP"));
            }
            case PILLAR_TILT -> "WALL_LEDGE".equals(legacyScenario)
                    ? Optional.of(new AdapterSpec(kind, LegacyProjection.WALL_LEDGE, false, true, true,
                    "BASE_APPROACH → VERTICAL_LIFT → DOCK → PILLAR_ASSEMBLY → PILLAR_TILT_WAVE",
                    "PILLAR_TILT_CROSSER_HANDOFF", "PILLAR_TILT_EXIT", "PILLAR_TILT_CLEANUP"))
                    : Optional.empty();
        };
    }

    static Optional<ScoredCandidate> score(Candidate candidate, AdapterSpec spec) {
        return score(candidate, spec, MAX_BODIES);
    }

    static Optional<ScoredCandidate> score(Candidate candidate, AdapterSpec spec, int bodyLimit) {
        if (bodyLimit < 3) return Optional.empty();
        if (candidate == null || spec == null) return Optional.empty();
        if (!isSupported(candidate.kind(), spec)) return Optional.empty();
        if (candidate.unknownRequiredCells() > 0) return Optional.empty();
        if (candidate.bodyCount() < 0 || candidate.bodyCount() > bodyLimit) return Optional.empty();
        if (!candidate.supportedEgress() && !candidate.shortDropEgress() && !candidate.safeFallEgress()) return Optional.empty();
        if (candidate.pivotThreatDistance() > 0 && candidate.endpointThreatDistance() >= candidate.pivotThreatDistance()) return Optional.empty();
        ScoreVector vector = new ScoreVector(
                roundBp((double) (candidate.bodyCount() - 3) / Math.max(1, bodyLimit - 3)),
                roundBp(candidate.length3d() / Math.max(1, lengthLimit(spec.kind()))),
                roundBp((2.0D * candidate.dropEdgeCells() + 3.0D * candidate.softFluidCells()
                        + 4.0D * candidate.trapdoorCells() + 5.0D * candidate.portalCells()) / (5.0D * 64.0D)),
                roundBp((double) candidate.unknownRequiredCells() / Math.max(1, candidate.requiredCellCount())),
                roundBp((candidate.routePhaseCount() - 1.0D + 2.0D * candidate.reachabilityProbeCount()
                        + 4.0D * candidate.hazardTransitionCount()) / 26.0D),
                roundBp((double) candidate.approachDistance() / Math.max(1, RECRUITMENT_RADIUS)),
                roundBp(clamp((candidate.pivotThreatDistance() - candidate.endpointThreatDistance())
                        / (double) Math.max(1, candidate.pivotThreatDistance()), 0.0D, 1.0D)),
                candidate.supportedEgress() ? 10000 : candidate.shortDropEgress() ? 7500 : candidate.safeFallEgress() ? 5000 : 0,
                roundBp(Math.min(candidate.connectedStructuralCells(), 8) / 8.0D));
        int scoreBp = scoreBp(vector);
        int lengthMm = (int) Math.floor(1000.0D * candidate.length3d() + 0.5D);
        return Optional.of(new ScoredCandidate(candidate, spec, scoreBp, lengthMm));
    }

    static int scoreBp(ScoreVector vector) {
        if (vector == null) throw new IllegalArgumentException("vector");
        return 12 * vector.bodyBp() + 10 * vector.lengthBp() + 8 * vector.hazardBp() + 4 * vector.unknownBp()
                + 4 * vector.replanBp() + 3 * vector.approachBp() - 10 * vector.threatBp()
                - 6 * vector.egressBp() - 2 * vector.structureBp();
    }

    static Comparator<ScoredCandidate> ordering() {
        return Comparator.comparingInt(ScoredCandidate::scoreBp)
                .thenComparing(Comparator.comparingInt((ScoredCandidate value) -> value.candidate().pivotThreatDistance()).reversed())
                .thenComparing(Comparator.comparingInt((ScoredCandidate value) -> egressRank(value.candidate())).reversed())
                .thenComparingInt(value -> value.candidate().bodyCount())
                .thenComparingInt(ScoredCandidate::lengthMm)
                .thenComparingInt(value -> value.candidate().kind().ordinal())
                .thenComparing((left, right) -> Long.compareUnsigned(left.candidate().pivot(), right.candidate().pivot()))
                .thenComparing((left, right) -> Long.compareUnsigned(left.candidate().anchor(), right.candidate().anchor()))
                .thenComparing((left, right) -> Long.compareUnsigned(left.candidate().span(), right.candidate().span()));
    }

    private static int egressRank(Candidate candidate) {
        if (candidate.supportedEgress()) return 3;
        if (candidate.shortDropEgress()) return 2;
        if (candidate.safeFallEgress()) return 1;
        return 0;
    }

    private static boolean isSupported(AdapterKind candidateKind, AdapterSpec spec) {
        if (candidateKind != spec.kind()) return false;
        return switch (candidateKind) {
            case DIRECT -> spec.legacyProjection() == LegacyProjection.NONE;
            case DROP -> spec.legacyProjection() == LegacyProjection.DROP_TO_LEDGE;
            case JUMP -> spec.legacyProjection() == LegacyProjection.JUMP_BRIDGE;
            case TILT -> spec.legacyProjection() == LegacyProjection.NONE || spec.legacyProjection() == LegacyProjection.WALL_LEDGE;
            case PILLAR_TILT -> spec.legacyProjection() == LegacyProjection.WALL_LEDGE;
        };
    }

    static Optional<AdapterKind> fromLegacyScenario(String scenario) {
        if (scenario == null) return Optional.empty();
        return switch (scenario) {
            case "JUMP_BRIDGE" -> Optional.of(AdapterKind.JUMP);
            case "DROP_TO_LEDGE" -> Optional.of(AdapterKind.DROP);
            case "WALL_LEDGE" -> Optional.of(AdapterKind.PILLAR_TILT);
            case "NONE" -> Optional.empty();
            default -> Optional.empty();
        };
    }

    private static int lengthLimit(AdapterKind kind) {
        return switch (kind) {
            case DROP, JUMP -> MAX_DROP_BRIDGE_LENGTH;
            case DIRECT, TILT, PILLAR_TILT -> MAX_ROPE_BRIDGE_LENGTH;
        };
    }

    private static int roundBp(double ratio) {
        long rounded = (long) Math.floor(10000.0D * ratio + 0.5D);
        if (rounded < 0L) return 0;
        return (int) Math.min(10000L, rounded);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

}