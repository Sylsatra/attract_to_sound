package com.example.soundattract.worker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;

public final class WorkerComputations {
    private WorkerComputations() {}

    public static WorkerScheduler.GroupComputeResult computeGroups(List<WorkerScheduler.MobSnapshot> mobs, WorkerScheduler.ConfigSnapshot cfg, long deadlineMs, ResourceLocation dimension) {
        return computeGroups(mobs, cfg, deadlineMs, dimension, cfg == null ? 0L : cfg.generation());
    }

    public static WorkerScheduler.GroupComputeResult computeGroups(List<WorkerScheduler.MobSnapshot> mobs, WorkerScheduler.ConfigSnapshot cfg, long deadlineMs, ResourceLocation dimension, long generation) {
        if (cfg == null || dimension == null) return null;
        if (mobs == null || mobs.isEmpty()) {
            return new WorkerScheduler.GroupComputeResult(dimension, Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet(), generation);
        }
        List<WorkerScheduler.MobSnapshot> candidates = new ArrayList<>(mobs.size());
        Map<UUID, WorkerScheduler.MobSnapshot> mobLookup = new HashMap<>((int) (mobs.size() / 0.75f) + 1);

        for (WorkerScheduler.MobSnapshot m : mobs) {
            if (isUsableMobSnapshot(m)) {
                if (mobLookup.putIfAbsent(m.uuid(), m) == null) {
                    candidates.add(m);
                }
            }
        }
        if (candidates.isEmpty()) {
            return new WorkerScheduler.GroupComputeResult(dimension, Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet(), generation);
        }

        candidates.sort(Comparator.comparing(WorkerScheduler.MobSnapshot::preferredLeader).reversed()
                .thenComparing(WorkerScheduler.MobSnapshot::flying)
                .thenComparing(Comparator.comparingDouble(WorkerScheduler.MobSnapshot::health).reversed())
                .thenComparing(WorkerScheduler.MobSnapshot::uuid));

        List<WorkerScheduler.MobSnapshot> leaders = new ArrayList<>();
        Map<UUID, UUID> mobToLeader = new HashMap<>();

        double groupRadius = cfg.leaderGroupRadius();
        double leaderSpacing = groupRadius * cfg.leaderSpacingMultiplier();
        double leaderSpacingSq = leaderSpacing * leaderSpacing;
        int maxLeaders = Math.max(1, cfg.maxLeaders());
        int maxGroupSize = Math.max(1, cfg.maxGroupSize());
        int sectors = Math.max(1, cfg.numEdgeSectors());
        int perSector = cfg.edgeMobsPerSector();

        double cellSize = leaderSpacing;
        Map<GridCell, List<WorkerScheduler.MobSnapshot>> leaderGrid = new HashMap<>();

        for (WorkerScheduler.MobSnapshot p : candidates) {
            if (!p.preferredLeader() && leaders.size() >= maxLeaders) break;

            GridCell cellKey = getCellKey(p.x(), p.y(), p.z(), cellSize);
            boolean tooClose = false;

            if (!p.preferredLeader()) {
                for (long x = cellKey.x() - 1; x <= cellKey.x() + 1 && !tooClose; x++) {
                    for (long y = cellKey.y() - 1; y <= cellKey.y() + 1 && !tooClose; y++) {
                        for (long z = cellKey.z() - 1; z <= cellKey.z() + 1 && !tooClose; z++) {
                            List<WorkerScheduler.MobSnapshot> cellLeaders = leaderGrid.get(new GridCell(x, y, z));
                            if (cellLeaders == null) continue;
                            for (WorkerScheduler.MobSnapshot l : cellLeaders) {
                                double dx = p.x() - l.x();
                                double dy = p.y() - l.y();
                                double dz = p.z() - l.z();
                                if (dx * dx + dy * dy + dz * dz < leaderSpacingSq) {
                                    tooClose = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            if (!tooClose) {
                leaders.add(p);
                leaderGrid.computeIfAbsent(cellKey, k -> new ArrayList<>()).add(p);
            }
        }

        if (leaders.isEmpty()) {
            leaders.add(candidates.get(0));
        }
        Set<UUID> leaderIds = new HashSet<>(leaders.size());
        for (WorkerScheduler.MobSnapshot leader : leaders) {
            leaderIds.add(leader.uuid());
        }

        Map<UUID, List<UUID>> leaderToGroup = new HashMap<>();
        Map<GridCell, List<Integer>> assignmentGrid = new HashMap<>();
        double assignmentCellSize = groupRadius > 0.0 ? groupRadius : 1.0;
        for (int leaderIndex = 0; leaderIndex < leaders.size(); leaderIndex++) {
            WorkerScheduler.MobSnapshot l = leaders.get(leaderIndex);
            leaderToGroup.put(l.uuid(), new ArrayList<>(Collections.singletonList(l.uuid())));
            GridCell cellKey = getCellKey(l.x(), l.y(), l.z(), assignmentCellSize);
            assignmentGrid.computeIfAbsent(cellKey, ignored -> new ArrayList<>()).add(leaderIndex);
        }

        Set<UUID> assigned = new HashSet<>();

        double groupRadiusSq = groupRadius * groupRadius;
        for (WorkerScheduler.MobSnapshot m : candidates) {
            if (leaderIds.contains(m.uuid())) {
                assigned.add(m.uuid());
                mobToLeader.put(m.uuid(), m.uuid());
                continue;
            }

            WorkerScheduler.MobSnapshot bestLeader = null;
            double bestDistSq = Double.MAX_VALUE;
            int bestLeaderIndex = Integer.MAX_VALUE;
            GridCell centerKey = getCellKey(m.x(), m.y(), m.z(), assignmentCellSize);
            for (long x = centerKey.x() - 1; x <= centerKey.x() + 1; x++) {
                for (long y = centerKey.y() - 1; y <= centerKey.y() + 1; y++) {
                    for (long z = centerKey.z() - 1; z <= centerKey.z() + 1; z++) {
                        List<Integer> nearbyLeaders = assignmentGrid.get(new GridCell(x, y, z));
                        if (nearbyLeaders == null) continue;
                        for (int leaderIndex : nearbyLeaders) {
                            WorkerScheduler.MobSnapshot l = leaders.get(leaderIndex);
                            List<UUID> group = leaderToGroup.get(l.uuid());
                            if (group.size() >= maxGroupSize) continue;

                            double dx = m.x() - l.x();
                            double dy = m.y() - l.y();
                            double dz = m.z() - l.z();
                            double distSq = dx * dx + dy * dy + dz * dz;

                            if (distSq <= groupRadiusSq && (distSq < bestDistSq
                                    || (distSq == bestDistSq && leaderIndex < bestLeaderIndex))) {
                                bestDistSq = distSq;
                                bestLeader = l;
                                bestLeaderIndex = leaderIndex;
                            }
                        }
                    }
                }
            }

            if (bestLeader == null) {
                bestLeader = m;
                leaders.add(m);
                leaderIds.add(m.uuid());
                leaderToGroup.put(m.uuid(), new ArrayList<>(Collections.singletonList(m.uuid())));
                GridCell cellKey = getCellKey(m.x(), m.y(), m.z(), assignmentCellSize);
                assignmentGrid.computeIfAbsent(cellKey, ignored -> new ArrayList<>()).add(leaders.size() - 1);
                mobToLeader.put(m.uuid(), m.uuid());
            } else {
                leaderToGroup.get(bestLeader.uuid()).add(m.uuid());
                mobToLeader.put(m.uuid(), bestLeader.uuid());
            }
            assigned.add(m.uuid());
        }

        Map<UUID, Set<UUID>> edgeByLeader = new HashMap<>();
        for (WorkerScheduler.MobSnapshot leader : leaders) {
            List<UUID> group = leaderToGroup.getOrDefault(leader.uuid(), Collections.emptyList());
            Map<Integer, List<UUID>> sectorLists = new HashMap<>();

            for (UUID memberId : group) {
                if (memberId.equals(leader.uuid())) continue;
                WorkerScheduler.MobSnapshot m = mobLookup.get(memberId);
                if (m == null) continue;

                double dx = m.x() - leader.x();
                double dz = m.z() - leader.z();
                int sector = getFastSector(dx, dz, sectors);
                sectorLists.computeIfAbsent(sector, k -> new ArrayList<>()).add(memberId);
            }

            Set<UUID> edge = new HashSet<>();
            for (Map.Entry<Integer, List<UUID>> e : sectorLists.entrySet()) {
                List<UUID> ids = e.getValue();

                ids.sort((a, b) -> {
                    WorkerScheduler.MobSnapshot ma = mobLookup.get(a);
                    WorkerScheduler.MobSnapshot mb = mobLookup.get(b);
                    if (ma != null && mb != null && ma.flying() != mb.flying()) {
                        return ma.flying() ? -1 : 1;
                    }
                    double da = (ma == null) ? 0 : distanceSq(ma, leader);
                    double db = (mb == null) ? 0 : distanceSq(mb, leader);
                    return Double.compare(db, da);
                });

                int count = Math.min(perSector, ids.size());
                for (int i = 0; i < count; i++) edge.add(ids.get(i));
            }

            if (edge.isEmpty() && group.size() > 1) {
                UUID far = null;
                double bestSq = -1;
                for (UUID id : group) {
                    if (id.equals(leader.uuid())) continue;
                    WorkerScheduler.MobSnapshot m = mobLookup.get(id);
                    if (m == null) continue;
                    double dSq = distanceSq(m, leader);
                    WorkerScheduler.MobSnapshot currentFar = mobLookup.get(far);
                    if (far == null || (m.flying() && !isFlying(currentFar))
                            || (m.flying() == isFlying(currentFar) && dSq > bestSq)) {
                        bestSq = dSq;
                        far = id;
                    }
                }
                if (far != null) edge.add(far);
            }
            edgeByLeader.put(leader.uuid(), edge);
        }

        Set<UUID> deserters = new HashSet<>();
        for (WorkerScheduler.MobSnapshot m : candidates) {
            if (!assigned.contains(m.uuid())) deserters.add(m.uuid());
        }

        return new WorkerScheduler.GroupComputeResult(dimension, mobToLeader, edgeByLeader, deserters, generation);
    }

    public static List<WorkerScheduler.SoundScoreResult> computeSoundScores(List<WorkerScheduler.SoundScoreRequest> batch, long deadlineMs) {
        if (batch == null || batch.isEmpty()) return Collections.emptyList();
        List<WorkerScheduler.SoundScoreResult> out = new ArrayList<>(batch.size());
        for (WorkerScheduler.SoundScoreRequest req : batch) {
            if (System.currentTimeMillis() > deadlineMs) break;
            out.add(computeSoundScore(req, deadlineMs));
        }
        return out;
    }

    public static WorkerScheduler.SoundScoreResult computeSoundScore(WorkerScheduler.SoundScoreRequest req, long deadlineMs) {
        if (req == null) {
            return new WorkerScheduler.SoundScoreResult(null, null, 0.0);
        }
        if (req.candidates == null || req.candidates.isEmpty()) {
            return new WorkerScheduler.SoundScoreResult(req.mobUuid, null, 0.0);
        }

        Double currentScore = null;
        if (req.currentTargetSoundId != null) {
            for (WorkerScheduler.SoundCandidate c : req.candidates) {
                if (c == null) {
                    continue;
                }
                if (req.currentTargetSoundId.equals(c.soundId)) {
                    currentScore = scoreCandidate(req, c);
                    break;
                }
                if (System.currentTimeMillis() > deadlineMs) break;
            }
        }

        String bestId = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double bestDist = Double.MAX_VALUE;
        for (WorkerScheduler.SoundCandidate c : req.candidates) {
            if (c == null) {
                continue;
            }
            double dx = req.mobX - c.x;
            double dz = req.mobZ - c.z;
            double dist = Math.hypot(dx, dz);
            double s = scoreCandidate(req, c, dist);
            if (s > bestScore || (Math.abs(s - bestScore) < 0.001 && dist < bestDist)) {
                bestScore = s;
                bestId = c.soundId;
                bestDist = dist;
            }
            if (System.currentTimeMillis() > deadlineMs) break;
        }

        if (currentScore != null && req.currentTargetSoundId != null && bestId != null && !req.currentTargetSoundId.equals(bestId)) {
            if (bestScore < currentScore * req.switchRatio) {
                bestId = req.currentTargetSoundId;
                bestScore = currentScore;
            }
        }

        return new WorkerScheduler.SoundScoreResult(req.mobUuid, bestId,
                bestScore == Double.NEGATIVE_INFINITY ? 0.0 : bestScore, req.dimensionKey);
    }

    private static double scoreCandidate(WorkerScheduler.SoundScoreRequest req, WorkerScheduler.SoundCandidate c) {
        if (req == null || c == null) {
            return Double.NEGATIVE_INFINITY;
        }
        double dx = req.mobX - c.x;
        double dz = req.mobZ - c.z;
        return scoreCandidate(req, c, Math.hypot(dx, dz));
    }

    private static double scoreCandidate(WorkerScheduler.SoundScoreRequest req,
                                         WorkerScheduler.SoundCandidate c,
                                         double dist) {
        if (req == null || c == null) {
            return Double.NEGATIVE_INFINITY;
        }
        double effectiveRange = Math.max(0.0001, c.range);
        if (dist > effectiveRange) return Double.NEGATIVE_INFINITY;

        double score = c.weight;
        long ageTicks = Math.max(0L, req.gameTime - c.gameTime);
        if (ageTicks <= req.noveltyTicks) {
            score += req.noveltyBonus;
        }
        return score;
    }

    private static double distanceSq(WorkerScheduler.MobSnapshot a, WorkerScheduler.MobSnapshot b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean isFlying(WorkerScheduler.MobSnapshot snapshot) {
        return snapshot != null && snapshot.flying();
    }

    private static GridCell getCellKey(double x, double y, double z, double cellSize) {
        if (cellSize <= 0) cellSize = 1;
        return new GridCell((long) Math.floor(x / cellSize), (long) Math.floor(y / cellSize),
                (long) Math.floor(z / cellSize));
    }

    private record GridCell(long x, long y, long z) {}

    private static int getFastSector(double dx, double dz, int totalSectors) {
        double angle = Math.atan2(dz, dx);
        return (int) Math.floor(((angle + Math.PI) / (2 * Math.PI)) * totalSectors) % totalSectors;
    }

    private static boolean isUsableMobSnapshot(WorkerScheduler.MobSnapshot snapshot) {
        return snapshot != null
                && snapshot.uuid() != null
                && snapshot.alive()
                && Double.isFinite(snapshot.x())
                && Double.isFinite(snapshot.y())
                && Double.isFinite(snapshot.z())
                && Double.isFinite(snapshot.health());
    }
}
