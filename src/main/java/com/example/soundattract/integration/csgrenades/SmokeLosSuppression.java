package com.example.soundattract.integration.csgrenades;

import net.minecraft.world.phys.Vec3;

public class SmokeLosSuppression {

    public static boolean blocksRay(Vec3 from, Vec3 to, Vec3 smokeCenter, double smokeRadius) {
        if (smokeRadius <= 0) {
            return false;
        }
        Vec3 d = to.subtract(from);
        Vec3 m = from.subtract(smokeCenter);

        double a = d.dot(d);
        double b = 2.0 * m.dot(d);
        double c = m.dot(m) - smokeRadius * smokeRadius;

        double discriminant = b * b - 4.0 * a * c;
        if (discriminant < 0) {
            return false;
        }

        double sqrtDisc = Math.sqrt(discriminant);
        double t1 = (-b - sqrtDisc) / (2.0 * a);
        double t2 = (-b + sqrtDisc) / (2.0 * a);

        return (t1 >= 0 && t1 <= 1) || (t2 >= 0 && t2 <= 1) || (t1 < 0 && t2 > 1);
    }
}