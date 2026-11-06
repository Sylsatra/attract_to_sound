package com.example.soundattract.quantified;

import com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LivingBridgeAssignmentPlanner {
    private static final int MAX_RANKED_SLOTS = 6;

    private LivingBridgeAssignmentPlanner() {
    }

    public record Candidate(UUID id, double x, double y, double z, int blockY, boolean transferred) {
    }

    public record Slot(int x, int y, int z) {
    }

    public record WorkerToken(long generation, long terrainSignature, UUID workerId,
                              long positionFingerprint) {
        public WorkerToken {
            if (workerId == null) throw new IllegalArgumentException("workerId");
        }
    }

    public record Ranking(UUID candidateId, List<Slot> slots, WorkerToken workerToken) {
        public Ranking(UUID candidateId, List<Slot> slots) {
            this(candidateId, slots, null);
        }
    }

    public record Result(long generation, long terrainSignature, List<Ranking> rankings) {
    }

    public static boolean isCurrentRanking(Ranking ranking, UUID workerId, long generation,
                                           long terrainSignature, long positionFingerprint) {
        if (ranking == null || workerId == null || !workerId.equals(ranking.candidateId())) return false;
        WorkerToken token = ranking.workerToken();
        return token != null && token.generation() == generation
                && token.terrainSignature() == terrainSignature
                && workerId.equals(token.workerId())
                && token.positionFingerprint() == positionFingerprint;
    }

    public record WorkerNode(UUID id) {
        public WorkerNode {
            if (id == null) throw new IllegalArgumentException("id");
        }
    }

    public record SlotNode(Slot slot, Set<Slot> laneCells) {
        public SlotNode {
            if (slot == null) throw new IllegalArgumentException("slot");
            laneCells = laneCells == null ? Set.of() : Set.copyOf(laneCells);
        }
    }

    public record WorkerSlotEdge(UUID workerId, Slot slot, double routeLength, Set<Slot> laneCells) {
        public WorkerSlotEdge {
            if (workerId == null || slot == null || !Double.isFinite(routeLength) || routeLength < 0.0D) {
                throw new IllegalArgumentException("edge");
            }
            laneCells = laneCells == null ? Set.of() : Set.copyOf(laneCells);
        }
    }

    public record Match(UUID workerId, Slot slot, double routeLength, Set<Slot> laneCells) {
    }

    public record MatchingResult(List<Match> matches, int cardinality, double totalRouteLength) {
        public MatchingResult {
            matches = List.copyOf(matches);
        }
    }

    public static MatchingResult solve(List<WorkerNode> workers, List<SlotNode> slots,
                                       List<WorkerSlotEdge> edges) {
        if (workers == null || slots == null || edges == null) return new MatchingResult(List.of(), 0, 0.0D);
        Set<UUID> workerIds = workers.stream().map(WorkerNode::id).collect(java.util.stream.Collectors.toSet());
        Map<Slot, SlotNode> slotByValue = new HashMap<>();
        for (SlotNode slot : slots) slotByValue.putIfAbsent(slot.slot(), slot);
        Map<UUID, List<WorkerSlotEdge>> byWorker = new HashMap<>();
        for (WorkerSlotEdge edge : edges) {
            if (workerIds.contains(edge.workerId()) && slotByValue.containsKey(edge.slot())) {
                byWorker.computeIfAbsent(edge.workerId(), ignored -> new ArrayList<>()).add(edge);
            }
        }
        for (List<WorkerSlotEdge> workerEdges : byWorker.values()) {
            workerEdges.sort(Comparator.comparingDouble(WorkerSlotEdge::routeLength)
                    .thenComparing(edge -> edge.slot().y())
                    .thenComparing(edge -> edge.slot().x())
                    .thenComparing(edge -> edge.slot().z()));
        }
        List<UUID> orderedWorkers = workers.stream().map(WorkerNode::id).sorted().toList();
        Map<UUID, WorkerSlotEdge> selected = new HashMap<>();
        Map<Slot, UUID> slotOwners = new HashMap<>();
        for (UUID worker : orderedWorkers) {
            augment(worker, byWorker, selected, slotOwners, new HashSet<>(), new HashSet<>());
        }
        List<Match> matches = selected.values().stream()
                .sorted(Comparator.comparing(WorkerSlotEdge::workerId))
                .map(edge -> new Match(edge.workerId(), edge.slot(), edge.routeLength(), edge.laneCells()))
                .toList();
        return new MatchingResult(matches, matches.size(), matches.stream().mapToDouble(Match::routeLength).sum());
    }

    private static boolean augment(UUID worker, Map<UUID, List<WorkerSlotEdge>> byWorker,
                                   Map<UUID, WorkerSlotEdge> selected, Map<Slot, UUID> slotOwners,
                                   Set<UUID> visitedWorkers, Set<Slot> visitedSlots) {
        if (!visitedWorkers.add(worker)) return false;
        for (WorkerSlotEdge edge : byWorker.getOrDefault(worker, List.of())) {
            if (!visitedSlots.add(edge.slot())) continue;
            UUID owner = slotOwners.get(edge.slot());
            if (owner != null && !augment(owner, byWorker, selected, slotOwners, visitedWorkers, visitedSlots)) continue;
            UUID laneOwner = laneOwner(edge, selected);
            if (laneOwner != null && !laneOwner.equals(worker)) continue;
            WorkerSlotEdge previous = selected.put(worker, edge);
            if (previous != null) slotOwners.remove(previous.slot(), worker);
            slotOwners.put(edge.slot(), worker);
            return true;
        }
        return false;
    }

    private static UUID laneOwner(WorkerSlotEdge edge, Map<UUID, WorkerSlotEdge> selected) {
        for (Map.Entry<UUID, WorkerSlotEdge> entry : selected.entrySet()) {
            if (!java.util.Collections.disjoint(entry.getValue().laneCells(), edge.laneCells())) return entry.getKey();
        }
        return null;
    }

    public static CompletableFuture<Result> submit(List<Candidate> candidates, List<Slot> slots,
                                                    long generation, long terrainSignature) {
        if (candidates == null || candidates.isEmpty() || slots == null || slots.isEmpty()) return null;
        List<Candidate> candidateSnapshot = List.copyOf(candidates);
        List<Slot> slotSnapshot = List.copyOf(slots);
        return QuantifiedOptionalBridge.trySubmitTask(
                "soundattract",
                "soundattract_living_bridge_assignment",
                () -> rank(candidateSnapshot, slotSnapshot, generation, terrainSignature),
                true,
                false,
                Duration.ofMillis(100L),
                "living_bridge_assignment:" + generation + ":" + terrainSignature);
    }

    private static Result rank(List<Candidate> candidates, List<Slot> slots,
                               long generation, long terrainSignature) {
        List<Ranking> rankings = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            List<SlotScore> scored = new ArrayList<>(slots.size());
            for (Slot slot : slots) {
                double dx = candidate.x() - (slot.x() + 0.5D);
                double dy = candidate.y() - slot.y();
                double dz = candidate.z() - (slot.z() + 0.5D);
                double score = dx * dx + dz * dz + dy * dy * 2.0D;
                if (candidate.blockY() == slot.y()) score -= 4.0D;
                if (candidate.transferred()) score -= 2.0D;
                scored.add(new SlotScore(slot, score));
            }
            scored.sort(Comparator.comparingDouble(SlotScore::score)
                    .thenComparingInt(value -> value.slot().y())
                    .thenComparingInt(value -> value.slot().x())
                    .thenComparingInt(value -> value.slot().z()));
            rankings.add(new Ranking(candidate.id(), scored.stream()
                    .limit(MAX_RANKED_SLOTS)
                    .map(SlotScore::slot)
                    .toList(), new WorkerToken(generation, terrainSignature, candidate.id(),
                    positionFingerprint(candidate))));
        }
        return new Result(generation, terrainSignature, List.copyOf(rankings));
    }

    private record SlotScore(Slot slot, double score) {
    }

    private static long positionFingerprint(Candidate candidate) {
        long hash = 0xcbf29ce484222325L;
        hash = (hash ^ Double.doubleToLongBits(Math.floor(candidate.x() * 4.0D) / 4.0D)) * 0x100000001b3L;
        hash = (hash ^ Double.doubleToLongBits(Math.floor(candidate.y() * 4.0D) / 4.0D)) * 0x100000001b3L;
        hash = (hash ^ Double.doubleToLongBits(Math.floor(candidate.z() * 4.0D) / 4.0D)) * 0x100000001b3L;
        return hash;
    }
}