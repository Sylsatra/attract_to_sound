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
        if (mobs == null || mobs.isEmpty()) return null;
        List<WorkerScheduler.MobSnapshot> candidates = new ArrayList<>(mobs.size());
        Map<UUID, WorkerScheduler.MobSnapshot> mobLookup = new HashMap<>((int) (mobs.size() / 0.75f) + 1);
        
        for (WorkerScheduler.MobSnapshot m : mobs) {
            if (m.alive()) {
                candidates.add(m);
                mobLookup.put(m.uuid(), m);
            }
        }
        if (candidates.isEmpty()) return null;

        candidates.sort((a, b) -> Double.compare(b.health(), a.health()));

        List<WorkerScheduler.MobSnapshot> leaders = new ArrayList<>();
        Map<UUID, UUID> mobToLeader = new HashMap<>();

        double groupRadius = cfg.leaderGroupRadius();
        double leaderSpacing = groupRadius * cfg.leaderSpacingMultiplier();
        double leaderSpacingSq = leaderSpacing * leaderSpacing;
        int maxLeaders = cfg.maxLeaders();
        int maxGroupSize = cfg.maxGroupSize();
        int sectors = cfg.numEdgeSectors();
        int perSector = cfg.edgeMobsPerSector();

        double cellSize = leaderSpacing;
        Map<Long, List<WorkerScheduler.MobSnapshot>> leaderGrid = new HashMap<>();

        for (WorkerScheduler.MobSnapshot p : candidates) {
            if (leaders.size() >= maxLeaders) break;
            
            long cellKey = getCellKey(p.x(), p.z(), cellSize);
            boolean tooClose = false;
            
            long[] adjacentCells = getAdjacentCells(cellKey, p.x(), p.z(), cellSize);
            for (long adjKey : adjacentCells) {
                List<WorkerScheduler.MobSnapshot> cellLeaders = leaderGrid.get(adjKey);
                if (cellLeaders != null) {
                    for (WorkerScheduler.MobSnapshot l : cellLeaders) {
                        double dx = p.x() - l.x();
                        double dz = p.z() - l.z();
                        if (dx * dx + dz * dz < leaderSpacingSq) {
                            tooClose = true;
                            break;
                        }
                    }
                }
                if (tooClose) break;
            }
            
            if (!tooClose) {
                leaders.add(p);
                leaderGrid.computeIfAbsent(cellKey, k -> new ArrayList<>()).add(p);
            }
            if (System.currentTimeMillis() > deadlineMs) break;
        }
        
        if (leaders.isEmpty()) {
            leaders.add(candidates.get(0));
        }

        Map<UUID, List<UUID>> leaderToGroup = new HashMap<>();
        for (WorkerScheduler.MobSnapshot l : leaders) {
            leaderToGroup.put(l.uuid(), new ArrayList<>(Collections.singletonList(l.uuid())));
        }

        Set<UUID> assigned = new HashSet<>();
        
        double groupRadiusSq = groupRadius * groupRadius;
        for (WorkerScheduler.MobSnapshot m : candidates) {
            if (leaders.contains(m)) {
                assigned.add(m.uuid());
                mobToLeader.put(m.uuid(), m.uuid());
                continue;
            }
            
            WorkerScheduler.MobSnapshot bestLeader = null;
            double bestDistSq = Double.MAX_VALUE;
            
            for (WorkerScheduler.MobSnapshot l : leaders) {
                List<UUID> group = leaderToGroup.get(l.uuid());
                if (group.size() >= maxGroupSize) continue;
                
                double dx = m.x() - l.x();
                double dz = m.z() - l.z();
                double distSq = dx * dx + dz * dz;
                
                if (distSq <= groupRadiusSq && distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestLeader = l;
                }
            }
            
            if (bestLeader != null) {
                leaderToGroup.get(bestLeader.uuid()).add(m.uuid());
                mobToLeader.put(m.uuid(), bestLeader.uuid());
                assigned.add(m.uuid());
            }
            if (System.currentTimeMillis() > deadlineMs) break;
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
                    if (dSq > bestSq) {
                        bestSq = dSq;
                        far = id;
                    }
                }
                if (far != null) edge.add(far);
            }
            edgeByLeader.put(leader.uuid(), edge);
            if (System.currentTimeMillis() > deadlineMs) break;
        }

        Set<UUID> deserters = new HashSet<>();
        for (WorkerScheduler.MobSnapshot m : candidates) {
            if (!assigned.contains(m.uuid())) deserters.add(m.uuid());
        }

        return new WorkerScheduler.GroupComputeResult(dimension, mobToLeader, edgeByLeader, deserters);
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
            return new WorkerScheduler.SoundScoreResult(null, null, 0.0, null);
        }
        if (req.candidates == null || req.candidates.isEmpty()) {
            return new WorkerScheduler.SoundScoreResult(req.mobUuid, null, 0.0, req.dimensionKey);
        }

        Double currentScore = null;
        if (req.currentTargetSoundId != null) {
            for (WorkerScheduler.SoundCandidate c : req.candidates) {
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
            double dx = req.mobX - c.x;
            double dz = req.mobZ - c.z;
            double dist = Math.hypot(dx, dz);
            double s = scoreCandidate(req, c);
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

        return new WorkerScheduler.SoundScoreResult(req.mobUuid, bestId, bestScore == Double.NEGATIVE_INFINITY ? 0.0 : bestScore, req.dimensionKey);
    }

    private static double scoreCandidate(WorkerScheduler.SoundScoreRequest req, WorkerScheduler.SoundCandidate c) {
        double dx = req.mobX - c.x;
        double dz = req.mobZ - c.z;
        double dist = Math.hypot(dx, dz);
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
        double dz = a.z() - b.z();
        return dx * dx + dz * dz;
    }

    private static long getCellKey(double x, double z, double cellSize) {
        if (cellSize <= 0) cellSize = 1;
        long cx = (long) Math.floor(x / cellSize);
        long cz = (long) Math.floor(z / cellSize);
        return (cx & 0xFFFFFFFFL) | (cz << 32);
    }

    private static long[] getAdjacentCells(long centerKey, double x, double z, double cellSize) {
        if (cellSize <= 0) return new long[]{centerKey};
        long cx = (long) Math.floor(x / cellSize);
        long cz = (long) Math.floor(z / cellSize);
        return new long[]{
            centerKey,
            ((cx - 1) & 0xFFFFFFFFL) | (cz << 32),
            ((cx + 1) & 0xFFFFFFFFL) | (cz << 32),
            (cx & 0xFFFFFFFFL) | ((cz - 1) << 32),
            (cx & 0xFFFFFFFFL) | ((cz + 1) << 32),
            ((cx - 1) & 0xFFFFFFFFL) | ((cz - 1) << 32),
            ((cx + 1) & 0xFFFFFFFFL) | ((cz + 1) << 32),
            ((cx - 1) & 0xFFFFFFFFL) | ((cz + 1) << 32),
            ((cx + 1) & 0xFFFFFFFFL) | ((cz - 1) << 32)
        };
    }

    private static int getFastSector(double dx, double dz, int totalSectors) {
        double angle = Math.atan2(dz, dx);
        return (int) Math.floor(((angle + Math.PI) / (2 * Math.PI)) * totalSectors) % totalSectors;
    }
}
