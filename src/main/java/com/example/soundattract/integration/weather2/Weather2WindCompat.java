package com.example.soundattract.integration.weather2;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Weather2 has no NeoForge 1.21.11 build, so callers use the established fallback wind. */
public final class Weather2WindCompat {
    private static final Vec3 FALLBACK_DIRECTION = new Vec3(-1.0, 0.0, 0.0);

    private Weather2WindCompat() {}

    public static WindSample sample(ServerLevel level, BlockPos position, boolean useWeather2) {
        return new WindSample(FALLBACK_DIRECTION, 1.0, false);
    }

    public static Vec3 direction(ServerLevel level, BlockPos position, boolean useWeather2) {
        return FALLBACK_DIRECTION;
    }

    public record WindSample(Vec3 direction, double speed, boolean weather2Backed) {}
}
