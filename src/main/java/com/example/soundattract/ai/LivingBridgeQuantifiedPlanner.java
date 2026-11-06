package com.example.soundattract.ai;

import com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class LivingBridgeQuantifiedPlanner {
    static final int MAX_TOKENS = 128;
    static final int MAX_CLUSTERS = 8;
    static final int MAX_MEMBERS = 48;
    static final int MAX_NEIGHBOR_COMPARISONS = 4096;
    static final int MAX_PARALLEL_SLICES = 8;
    static final Duration TIMEOUT = Duration.ofMillis(75);

    record MobToken(UUID id, double x, double y, double z) { }
    record ClusterResult(List<List<MobToken>> clusters, boolean truncated, boolean fallback) {
        ClusterResult {
            clusters = clusters == null ? List.of() : List.copyOf(clusters);
        }
    }

    private LivingBridgeQuantifiedPlanner() { }

    static CompletableFuture<ClusterResult> submit(List<MobToken> input, double radius, double verticalDelta,
                                                    int parallelism, String batchKey) {
        List<MobToken> bounded = boundedTokens(input);
        if (bounded.isEmpty()) return CompletableFuture.completedFuture(new ClusterResult(List.of(), false, true));
        CompletableFuture<ClusterResult> quantified = QuantifiedOptionalBridge.trySubmitTask("soundattract", "soundattract_living_bridge_cluster",
                () -> new ClusterResult(cluster(bounded, radius, verticalDelta), bounded.size() < input.size(), false),
                true, false, TIMEOUT, batchKey);
        return quantified != null ? quantified : CompletableFuture.completedFuture(new ClusterResult(cluster(bounded, radius, verticalDelta),
                bounded.size() < input.size(), true));
    }

    static List<MobToken> boundedTokens(List<MobToken> input) {
        if (input == null) return List.of();
        return input.stream().filter(token -> token != null && token.id() != null)
                .sorted(Comparator.comparing(token -> token.id().toString()))
                .limit(MAX_TOKENS).toList();
    }

    static List<List<MobToken>> cluster(List<MobToken> input, double radius, double verticalDelta) {
        List<MobToken> tokens = boundedTokens(input);
        if (tokens.size() < 2) return List.of();
        double radiusSq = radius * radius;
        double cellSize = Math.max(1.0D, radius);
        double verticalSize = Math.max(1.0D, verticalDelta);
        int[] parent = new int[tokens.size()];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        Map<Long, List<Integer>> grid = new HashMap<>();
        for (int i = 0; i < tokens.size(); i++) {
            MobToken token = tokens.get(i);
            long key = spatialBucketKey((int) Math.floor(token.x() / cellSize),
                    (int) Math.floor(token.y() / verticalSize), (int) Math.floor(token.z() / cellSize));
            grid.computeIfAbsent(key, ignored -> new ArrayList<>()).add(i);
        }
        int comparisons = 0;
        for (int i = 0; i < tokens.size() && comparisons < MAX_NEIGHBOR_COMPARISONS; i++) {
            MobToken first = tokens.get(i);
            int bx = (int) Math.floor(first.x() / cellSize);
            int by = (int) Math.floor(first.y() / verticalSize);
            int bz = (int) Math.floor(first.z() / cellSize);
            for (int dx = -1; dx <= 1 && comparisons < MAX_NEIGHBOR_COMPARISONS; dx++) {
                for (int dy = -1; dy <= 1 && comparisons < MAX_NEIGHBOR_COMPARISONS; dy++) {
                    for (int dz = -1; dz <= 1 && comparisons < MAX_NEIGHBOR_COMPARISONS; dz++) {
                        List<Integer> neighbors = grid.get(spatialBucketKey(bx + dx, by + dy, bz + dz));
                        if (neighbors == null) continue;
                        for (int j : neighbors) {
                            if (j <= i) continue;
                            if (++comparisons > MAX_NEIGHBOR_COMPARISONS) break;
                            MobToken second = tokens.get(j);
                            if (Math.abs(second.y() - first.y()) <= verticalDelta
                                    && squaredDistance(second, first) <= radiusSq) {
                                int firstRoot = findRoot(parent, i);
                                int secondRoot = findRoot(parent, j);
                                if (firstRoot != secondRoot) parent[secondRoot] = firstRoot;
                            }
                        }
                    }
                }
            }
        }
        Map<Integer, List<MobToken>> groups = new LinkedHashMap<>();
        for (int i = 0; i < tokens.size(); i++) {
            groups.computeIfAbsent(findRoot(parent, i), ignored -> new ArrayList<>()).add(tokens.get(i));
        }
        List<List<MobToken>> result = new ArrayList<>();
        for (List<MobToken> group : groups.values()) {
            if (group.size() < 2) continue;
            group.sort(Comparator.comparing(token -> token.id().toString()));
            result.add(List.copyOf(group.subList(0, Math.min(MAX_MEMBERS, group.size()))));
            if (result.size() >= MAX_CLUSTERS) break;
        }
        result.sort(Comparator.<List<MobToken>>comparingInt(List::size).reversed()
                .thenComparing(cluster -> cluster.get(0).id().toString()));
        return List.copyOf(result);
    }

    private static double squaredDistance(MobToken a, MobToken b) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return dx * dx + dz * dz;
    }

    private static int findRoot(int[] parent, int index) {
        int root = index;
        while (root != parent[root]) root = parent[root];
        while (index != root) {
            int next = parent[index];
            parent[index] = root;
            index = next;
        }
        return root;
    }

    private static long spatialBucketKey(int x, int y, int z) {
        return ((long) x & 0x1FFFFFL) << 42 | ((long) y & 0xFFFFFL) << 22 | ((long) z & 0x3FFFFFL);
    }
}