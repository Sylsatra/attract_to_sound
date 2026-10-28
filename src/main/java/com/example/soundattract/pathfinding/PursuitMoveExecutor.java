package com.example.soundattract.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public final class PursuitMoveExecutor {
    private PursuitMoveExecutor() {
    }

    public static boolean issueVanillaMove(Mob mob, Vec3 destination, double speed) {
        if (mob == null || destination == null || mob.level() == null) {
            return false;
        }
        if (!isFinite(destination)) {
            return false;
        }

        PathNavigation navigation = mob.getNavigation();
        Path path = navigation.createPath(BlockPos.containing(destination), 0);
        if (path == null || !isPathSafe(mob, path)) {
            navigation.stop();
            return false;
        }

        return navigation.moveTo(path, speed);
    }

    public static boolean issueVanillaMove(Mob mob, Path path, double speed) {
        if (mob == null || path == null || mob.level() == null) {
            return false;
        }
        if (!isPathSafe(mob, path)) {
            mob.getNavigation().stop();
            return false;
        }
        return mob.getNavigation().moveTo(path, speed);
    }

    private static boolean isPathSafe(Mob mob, Path path) {
        if (mob == null || path == null || mob.level() == null) {
            return false;
        }
        try {
            int count = path.getNodeCount();
            if (count <= 0) {
                return false;
            }
            net.minecraft.world.level.Level realLevel = mob.level();
            for (int i = 0; i < count; i++) {
                var node = path.getNode(i);
                if (node == null) {
                    return false;
                }
                HazardClassifier.HazardType type = HazardClassifier.classify(realLevel, node.x, node.y, node.z);
                if (type == HazardClassifier.HazardType.LAVA && !mob.fireImmune()) {
                    return false;
                }
                if (type == HazardClassifier.HazardType.TRAPDOOR_DROP) {
                    return false;
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}