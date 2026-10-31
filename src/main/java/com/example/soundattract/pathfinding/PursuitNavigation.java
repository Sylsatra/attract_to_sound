package com.example.soundattract.pathfinding;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public final class PursuitNavigation {
    private PursuitNavigation() {
    }

    public static boolean maybeMoveTo(Mob mob, Vec3 destination, double speed) {
        return PursuitMoveExecutor.issueVanillaMove(mob, destination, speed);
    }
}