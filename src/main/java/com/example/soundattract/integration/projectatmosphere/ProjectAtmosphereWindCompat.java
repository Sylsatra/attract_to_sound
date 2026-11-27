package com.example.soundattract.integration.projectatmosphere;

import net.Gabou.projectatmosphere.api.WindVectorApi;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class ProjectAtmosphereWindCompat {
    private static final Vec3 FALLBACK_DIRECTION = new Vec3(-1.0, 0.0, 0.0);

    private ProjectAtmosphereWindCompat() {
    }

    public static WindSample sample(ServerLevel level, BlockPos position) {
        if (level == null || position == null) return WindSample.unavailable();
        try {
            WindVectorApi.WindSample wind = WindVectorApi.getSurface(
                    RegionInstanceKey.from(position), level.getGameTime());
            if (wind == null || !Float.isFinite(wind.speedMps()) || wind.speedMps() < 0.0F
                    || !Float.isFinite(wind.directionDeg())) {
                return WindSample.unavailable();
            }
            double radians = Math.toRadians(wind.directionDeg());
            Vec3 direction = new Vec3(-Math.sin(radians), 0.0, Math.cos(radians)).normalize();
            if (!Double.isFinite(direction.x) || !Double.isFinite(direction.z)) return WindSample.unavailable();
            return new WindSample(direction, wind.speedMps(), true);
        } catch (LinkageError | RuntimeException exception) {
            return WindSample.unavailable();
        }
    }

    public record WindSample(Vec3 direction, double speed, boolean available) {
        private static WindSample unavailable() {
            return new WindSample(FALLBACK_DIRECTION, 1.0, false);
        }
    }
}
