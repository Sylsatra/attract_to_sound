package com.example.soundattract.util;

import net.minecraft.world.phys.Vec3;

import java.util.Random;

public final class ArrowInvestigationHelper {

    public static double segmentToPointSquaredDistance(Vec3 a, Vec3 b, Vec3 p) {
        double abx = b.x - a.x, aby = b.y - a.y, abz = b.z - a.z;
        double lenSq = abx * abx + aby * aby + abz * abz;
        if (lenSq < 1e-12) {
            double dx = p.x - a.x, dy = p.y - a.y, dz = p.z - a.z;
            return dx * dx + dy * dy + dz * dz;
        }
        double apx = p.x - a.x, apy = p.y - a.y, apz = p.z - a.z;
        double t = (apx * abx + apy * aby + apz * abz) / lenSq;
        if (t < 0.0) t = 0.0;
        else if (t > 1.0) t = 1.0;
        double cx = a.x + t * abx, cy = a.y + t * aby, cz = a.z + t * abz;
        double dx = p.x - cx, dy = p.y - cy, dz = p.z - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    public static double randomOffset(Random rng, int maxOffset) {
        if (maxOffset <= 0) return 0.0;
        return (rng.nextDouble() * 2.0 - 1.0) * maxOffset;
    }

    private ArrowInvestigationHelper() {}
}
