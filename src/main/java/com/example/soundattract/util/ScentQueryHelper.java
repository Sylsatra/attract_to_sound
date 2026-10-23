package com.example.soundattract.util;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ScentQueryHelper {

    public static boolean hasFreshScentNear(Mob mob, int maxAgeTicks, double radius) {
        return computeFreshScentInfo(mob, maxAgeTicks, radius).hasFresh();
    }

    /**
     * Combined query: returns whether any fresh scent exists in radius AND the strongest
     * strength among matching nodes. Cached per mob.
     */
    public static ScentQueryCache.Result computeFreshScentInfo(Mob mob, int maxAgeTicks, double radius) {
        if (mob == null || mob.level() == null || mob.level().isClientSide()) return ScentQueryCache.Result.EMPTY;
        return computeFreshScentInfo(mob, ScentManager.getForLevel(mob.level()), maxAgeTicks, radius);
    }

    /** Same production query with an explicit manager, useful for isolated level fixtures. */
    public static ScentQueryCache.Result computeFreshScentInfo(
            Mob mob,
            ScentManager manager,
            int maxAgeTicks,
            double radius) {
        if (mob == null || mob.level() == null || mob.level().isClientSide()) return ScentQueryCache.Result.EMPTY;
        if (SoundAttractConfig.COMMON == null) return ScentQueryCache.Result.EMPTY;

        long now = mob.level().getGameTime();
        int ttl = SoundAttractConfig.COMMON.scentQueryCacheTtlTicks.get();
        net.minecraft.resources.Identifier dimension = mob.level().dimension().identifier();
        ScentQueryCache.Result cached = ScentQueryCache.getCached(mob.getUUID(), dimension, now, ttl);
        if (cached != null) return cached;

        ScentQueryCache.Result computed;
        try {
            if (manager == null) {
                computed = ScentQueryCache.Result.EMPTY;
            } else {
                ChunkPos chunk = new ChunkPos(mob.blockPosition());
                List<ScentNode> nodes = manager.getNodesInArea(chunk);
                if (nodes == null || nodes.isEmpty()) {
                    computed = new ScentQueryCache.Result(now, false, 0.0);
                } else {
                    double radiusSq = radius * radius;
                    Vec3 mobPos = mob.position();
                    if (mobPos == null) {
                        computed = ScentQueryCache.Result.EMPTY;
                    } else {
                        boolean anyFresh = false;
                        double strongest = 0.0;
                        for (ScentNode node : nodes) {
                            if (node == null) continue;
                            Vec3 np = node.getPosition();
                            if (np == null) continue;
                            long age = now - node.getTimestamp();
                            if (age < 0 || age > maxAgeTicks) continue;
                            if (mobPos.distanceToSqr(np) > radiusSq) continue;
                            anyFresh = true;
                            float s = node.getStrength();
                            if (s > strongest) strongest = s;
                        }
                        computed = new ScentQueryCache.Result(now, anyFresh, strongest);
                    }
                }
            }
        } catch (Throwable t) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.warn("[ScentQueryHelper] computeFreshScentInfo failed: {}", t.toString());
            }
            computed = ScentQueryCache.Result.EMPTY;
        }

        ScentQueryCache.put(mob.getUUID(), dimension, computed);
        return computed;
    }
}
