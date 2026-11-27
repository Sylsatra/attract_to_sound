package com.example.soundattract.pathfinding;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public final class PursuitNavigation {
    private PursuitNavigation() {
    }

    public static boolean maybeMoveTo(Mob mob, Vec3 destination, double speed) {
        return maybeMoveTo(mob, destination, speed, PathTaskScheduler.Priority.OPTIONAL);
    }

    public static boolean maybeMoveTo(Mob mob, Vec3 destination, double speed, PathTaskScheduler.Priority priority) {
        if (mob == null || destination == null) {
            return false;
        }
        return NavLimiter.maybeMoveTo(mob, destination.x, destination.y, destination.z, speed, priority);
    }
}
