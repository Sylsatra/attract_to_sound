package com.example.soundattract.pathfinding;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;

public final class NodeRouter {
    private NodeRouter() {}
    private static final Cache<Mob, Mem> MEMORY = Caffeine.newBuilder()
            .weakKeys()
            .maximumSize(8192)
            .expireAfterAccess(2, TimeUnit.MINUTES)
            .build();
    private static final class Mem {
        double off;
        long until;
        Vec3 lastPos;
        Vec3 lastWaypoint;
        long lastWaypointUntil;
        int lastDx, lastDy, lastDz;
    }

    public static Vec3 getWaypoint(Mob mob, Vec3 desired) {
        if (mob == null || mob.level() == null || desired == null) return desired;
        if (!isFinite(desired)) return mob.position();
        if (HazardClassifier.isDangerous(mob.level(), mob, desired)) return mob.position();
        try {
            if (!SoundAttractConfig.COMMON.enableNodeRouter.get()) return desired;
        } catch (Throwable ignored) {}

        Mem mem = MEMORY.get(mob, ignored -> new Mem());

        Vec3 pos = mob.position();
        double dx = desired.x - pos.x;
        double dz = desired.z - pos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double threshold = 4.0;

        if (dist <= threshold) {
            double vDeltaToDesired = Math.abs(desired.y - pos.y);
            if (vDeltaToDesired <= 2.5) {
                double ny = snapGroundY(mob.level(), desired.x, desired.y + 1.0, desired.z);
                return new Vec3(desired.x, ny, desired.z);
            }
        }

        double step = Math.max(6.0, Math.min(16.0, threshold * 0.5));
        double ang = Math.atan2(dz, dx);

        Vec3 best = null;
        double bestPenalty = Double.MAX_VALUE;
        double vDelta = Math.abs(desired.y - pos.y);
        boolean steep = vDelta > 3.0;
        boolean longDist = dist > threshold * 1.25;
        double[] offsets;
        if (longDist && !steep) {
            offsets = new double[]{0.0, 0.35, -0.35};
        } else if (steep) {
            offsets = new double[]{0.0, 0.25, -0.25, 0.5, -0.5, 0.75, -0.75, 1.0, -1.0};
        } else {
            offsets = new double[]{0.0, 0.4, -0.4};
        }

        double curDist = pos.distanceTo(desired);
        Vec3[] candidates = new Vec3[offsets.length];
        double[] penalties = new double[offsets.length];
        java.util.Arrays.fill(penalties, Double.MAX_VALUE);
        int bestIdx = -1;

        for (int i = 0; i < offsets.length; i++) {
            double off = offsets[i];
            double a = ang + off;
            double nx = pos.x + Math.cos(a) * step;
            double nz = pos.z + Math.sin(a) * step;
            double ny = pos.y;
            try { ny = snapGroundY(mob.level(), nx, pos.y + 1.0, nz); } catch (Throwable t) {}

            double dxd = desired.x - nx;
            double dyd = desired.y - ny;
            double dzd = desired.z - nz;
            double candDist = Math.sqrt(dxd * dxd + dyd * dyd + dzd * dzd);

            double hPenalty = steep ? Math.abs(ny - pos.y) * 0.75 : 0.0;
            double verticalProgress = vDelta - Math.abs(desired.y - ny);
            double improveBase = Math.max(0.0, candDist - curDist);
            double detourPenalty = improveBase * (verticalProgress > 1.0 ? 0.015 : verticalProgress > 0.25 ? 0.035 : 0.08);

            boolean earlyPrune = (hPenalty + detourPenalty > bestPenalty + 2.0);

            Vec3 candidate = new Vec3(nx, ny, nz);
            if (HazardClassifier.isDangerous(mob.level(), mob, candidate)) {
                penalties[i] = Double.MAX_VALUE;
                continue;
            }
            candidates[i] = candidate;

            double verticalPenalty = verticalProgress < -0.75 ? Math.abs(verticalProgress) * 0.25 : 0.0;
            double verticalBonus = 0.0;
            if (verticalProgress > 0.25) {
                double gain = verticalProgress * 0.7;
                double detourOffset = improveBase * 0.5;
                verticalBonus = Math.min(gain + detourOffset, verticalProgress * 1.2);
            }

            double currentRoughPenalty = hPenalty + detourPenalty + verticalPenalty - verticalBonus;

            if (earlyPrune || currentRoughPenalty > bestPenalty + 4.0) {
                penalties[i] = currentRoughPenalty + 10.0;
                continue;
            }

            double rayPen = rayPenalty(mob, pos, candidate);
            double p = currentRoughPenalty + rayPen;
            if (rayPen < 8.0) {
                p -= visibilityBonus(mob, candidate, desired) * (rayPen < 1.0 ? 1.0 : 0.5);
            }

            penalties[i] = p;

            if (p < bestPenalty) {
                bestPenalty = p;
                best = candidate;
                bestIdx = i;
            }
        }

        if (best != null) {
            if (!isWalkable(mob, best)) {
                best = null;
                bestPenalty = Double.MAX_VALUE;
                for (int i = 0; i < candidates.length; i++) {
                    if (isWalkable(mob, candidates[i])) {
                        if (penalties[i] < bestPenalty) {
                            bestPenalty = penalties[i];
                            best = candidates[i];
                            bestIdx = i;
                        }
                    }
                }
            }
        }

        if (best == null || bestPenalty > 500.0) {
            double step2 = Math.max(3.0, step * 0.5);
            double[] offsets2 = steep || longDist
                    ? new double[]{0.0, 0.25, -0.25, 0.5, -0.5, 0.75, -0.75, 1.0, -1.0, 1.25, -1.25}
                    : new double[]{0.0, 0.35, -0.35, 0.7, -0.7};
            Vec3 best2 = null;
            double bestPenalty2 = Double.MAX_VALUE;
            for (int i = 0; i < offsets2.length; i++) {
                double off = offsets2[i];
                double a = ang + off;
                double nx = pos.x + Math.cos(a) * step2;
                double nz = pos.z + Math.sin(a) * step2;
                double ny = snapGroundY(mob.level(), nx, pos.y + 1.0, nz);
                Vec3 candidate = new Vec3(nx, ny, nz);
                if (HazardClassifier.isDangerous(mob.level(), mob, candidate)) {
                    continue;
                }
                double hPenalty = steep ? Math.abs(ny - pos.y) * 0.75 : 0.0;
                double candDist = candidate.distanceTo(desired);
                double verticalProgress = vDelta - Math.abs(desired.y - ny);
                double improveBase = Math.max(0.0, candDist - curDist);
                double detourPenalty = improveBase * (verticalProgress > 1.0 ? 0.012 : verticalProgress > 0.25 ? 0.03 : 0.075);
                double verticalPenalty = verticalProgress < -0.75 ? Math.abs(verticalProgress) * 0.25 : 0.0;
                double verticalBonus = 0.0;
                if (verticalProgress > 0.25) {
                    double gain = verticalProgress * 0.65;
                    double detourOffset = improveBase * 0.45;
                    verticalBonus = Math.min(gain + detourOffset, verticalProgress * 1.1);
                }
                double rayPen2 = rayPenalty(mob, pos, candidate);
                double p = hPenalty + detourPenalty + verticalPenalty + rayPen2 - verticalBonus;
                if (rayPen2 < 8.0) {
                    p -= visibilityBonus(mob, candidate, desired) * (rayPen2 < 1.0 ? 1.0 : 0.5);
                }

                if (p < bestPenalty2 && isWalkable(mob, candidate)) {
                    bestPenalty2 = p;
                    best2 = candidate;
                }
            }

            if (best2 != null) {
                best = best2;
            }
        }

        return best != null ? best : new Vec3(pos.x + Math.cos(ang) * step, pos.y, pos.z + Math.sin(ang) * step);
    }

    private static double rayPenalty(Mob mob, Vec3 from, Vec3 to) {
        try {
            Level lvl = mob.level();
            if (lvl == null) return 0.0;
            Vec3 s = new Vec3(from.x, mob.getEyeY(), from.z);
            double eyeTo = Math.min(1.6, Math.max(1.2, mob.getBbHeight() * 0.6));
            Vec3 e = new Vec3(to.x, to.y + eyeTo, to.z);
            HitResult res = lvl.clip(new ClipContext(s, e, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
            if (res == null || res.getType() == HitResult.Type.MISS) return 0.0;
            double seg = s.distanceTo(e);
            if (seg < 1e-3) return 0.0;
            double hit = res.getLocation().distanceTo(s);
            double frac = hit / seg;
            if (frac <= 0.15) return 60.0;
            double slack = Math.max(0.0, 1.0 - frac);
            return 10.0 * slack;
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private static double visibilityBonus(Mob mob, Vec3 from, Vec3 to) {
        try {
            Level lvl = mob.level();
            if (lvl == null) return 0.0;
            Vec3 s = new Vec3(from.x, from.y + 1.5, from.z);
            Vec3 e = new Vec3(to.x, to.y + 1.5, to.z);
            HitResult res = lvl.clip(new ClipContext(s, e, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
            return (res == null || res.getType() == HitResult.Type.MISS) ? 0.25 : 0.0;
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    public static double snapGroundY(Level lvl, double x, double yHint, double z) {
        if (lvl == null) return yHint;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos((int) Math.floor(x), (int) Math.floor(yHint), (int) Math.floor(z));
        int minY = lvl.getMinBuildHeight();
        int maxY = lvl.getMaxBuildHeight();
        int startY = Math.min(Math.max(mp.getY(), minY + 1), maxY - 2);
        mp.setY(startY);

        for (int i = 0; i < 8; i++) {
            if (!lvl.getBlockState(mp).isAir() && lvl.getBlockState(mp.above()).isAir()) {
                return mp.getY() + 1.0;
            }
            mp.move(0, -1, 0);
            if (mp.getY() <= minY + 1) break;
        }

        mp.setY(startY);
        for (int i = 0; i < 8; i++) {
            if (!lvl.getBlockState(mp).isAir() && lvl.getBlockState(mp.above()).isAir()) {
                return mp.getY() + 1.0;
            }
            mp.move(0, 1, 0);
            if (mp.getY() >= maxY - 2) break;
        }
        return yHint;
    }

    public static boolean isWalkable(Mob mob, Vec3 pos) {
        if (mob == null || mob.level() == null || !isFinite(pos)) return false;
        if (HazardClassifier.isDangerous(mob.level(), mob, pos)) return false;
        Level level = mob.level();
        BlockPos bp = new BlockPos((int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z));

        if (level.getBlockState(bp.below()).getCollisionShape(level, bp.below()).isEmpty()
                || !level.getFluidState(bp.below()).isEmpty()) {
            return false;
        }

        if (!level.getFluidState(bp).isEmpty()) {
            return false;
        }

        if (!level.getBlockState(bp).isPathfindable(level, bp, net.minecraft.world.level.pathfinder.PathComputationType.LAND)) {
            if (level.getBlockState(bp).blocksMotion()) return false;
        }

        if (!level.getBlockState(bp.above()).isPathfindable(level, bp.above(), net.minecraft.world.level.pathfinder.PathComputationType.LAND)) {
            if (level.getBlockState(bp.above()).blocksMotion()) return false;
        }

        return true;
    }

    public static boolean isPathable(Mob mob, Vec3 candidate) {
        if (mob == null || !isFinite(candidate)) return false;
        try {
            Level lvl = mob.level();
            if (lvl == null) return false;

            double ddx = candidate.x - mob.getX();
            double ddy = candidate.y - mob.getY();
            double ddz = candidate.z - mob.getZ();
            double d2 = ddx * ddx + ddy * ddy + ddz * ddz;

            if (d2 < 576.0) {
                if (!isWalkable(mob, candidate)) return false;

                net.minecraft.world.phys.HitResult res = lvl.clip(new net.minecraft.world.level.ClipContext(
                        mob.position().add(0, 0.5, 0),
                        candidate.add(0, 0.5, 0),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE,
                        mob
                ));
                if (res == null || res.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                    return true;
                }
            }

            BlockPos bp = new BlockPos((int) Math.floor(candidate.x), (int) Math.floor(candidate.y), (int) Math.floor(candidate.z));
            return mob.getNavigation().createPath(bp, 0) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void clear() {
        MEMORY.invalidateAll();
    }

    private static boolean isFinite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }
}
