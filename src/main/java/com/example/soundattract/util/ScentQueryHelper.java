package com.example.soundattract.util;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.DebugLoggingConfig;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.scents.ScentManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class ScentQueryHelper {

    public static boolean hasFreshScentNear(Mob mob, int maxAgeTicks, double radius) {
        return computeFreshScentInfo(mob, maxAgeTicks, radius).hasFresh();
    }

    public static ScentQueryCache.Result computeFreshScentInfo(Mob mob, int maxAgeTicks, double radius) {
        if (mob == null) return ScentQueryCache.Result.EMPTY;
        Level level = mob.level();
        if (level == null || level.isClientSide) return ScentQueryCache.Result.EMPTY;
        if (SoundAttractConfig.COMMON == null) return ScentQueryCache.Result.EMPTY;

        long now = level.getGameTime();
        ScentQueryCache.Result computed;
        try {
            computed = ScentManager.get(level).map(manager -> {
                Vec3 mobPos = mob.position();
                if (mobPos == null) return ScentQueryCache.Result.EMPTY;
                UUID mobId = mob.getUUID();
                ResourceLocation dimension = level.dimension().location();
                long revision = manager.revision();
                ScentQueryCache.Result cached = ScentQueryCache.getCached(
                        mobId, dimension, now, mobPos.x, mobPos.y, mobPos.z, radius, maxAgeTicks, revision);
                if (cached != null) return cached;
                double radiusSq = radius * radius;

                double[] strongest = {0.0};
                boolean[] anyFresh = {false};
                manager.forEachNodeInArea(new ChunkPos(mob.blockPosition()), node -> {
                    if (node == null) return;
                    Vec3 np = node.getPosition();
                    if (np == null) return;
                    long age = now - node.getTimestamp();
                    if (age < 0 || age > maxAgeTicks) return;
                    if (mobPos.distanceToSqr(np) > radiusSq) return;
                    anyFresh[0] = true;
                    float s = node.getStrength();
                    if (s > strongest[0]) strongest[0] = s;
                });
                ScentQueryCache.Result result = new ScentQueryCache.Result(now, anyFresh[0], strongest[0]);
                ScentQueryCache.put(mobId, dimension,
                        mobPos.x, mobPos.y, mobPos.z, radius, maxAgeTicks, revision, result);
                return result;
            }).orElse(ScentQueryCache.Result.EMPTY);
        } catch (Throwable t) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SCENT)) {
                SoundAttractMod.LOGGER.warn("[ScentQueryHelper] computeFreshScentInfo failed: {}", t.toString());
            }
            computed = ScentQueryCache.Result.EMPTY;
        }

        return computed;
    }
}
