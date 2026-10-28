package com.example.soundattract.integration.weather2;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import weather2.ServerTickHandler;
import weather2.config.WeatherUtilConfig;
import weather2.weathersystem.WeatherManagerServer;
import weather2.weathersystem.wind.WindManager;

public final class Weather2WindCompat {
    private static final Vec3 FALLBACK_DIRECTION = new Vec3(-1.0, 0.0, 0.0);

    private Weather2WindCompat() {
    }

    public static WindSample sample(ServerLevel level, BlockPos position, boolean useWeather2) {
        if (level == null || position == null) {
            return new WindSample(FALLBACK_DIRECTION, 1.0, false);
        }
        WindManager windManager = getWindManager(level, useWeather2);
        if (windManager == null) {
            return new WindSample(FALLBACK_DIRECTION, 1.0, false);
        }

        try {
            double angle = windManager.getWindAngle(Vec3.atCenterOf(position));
            Vec3 direction = direction(angle);
            double speed = windManager.getWindSpeed(position);
            if (!Double.isFinite(speed) || speed < 0.0) {
                speed = 0.0;
            }
            double sampleSpeed = speed;
            Weather2WindDebugLog.report(level, "wind_sample", "active", () -> "Weather2 wind sample active in "
                    + level.dimension().location() + ": angle=" + angle + " degrees, speed=" + sampleSpeed
                    + ", direction=" + direction);
            return new WindSample(direction, speed, true);
        } catch (LinkageError | RuntimeException exception) {
            reportFailure(level, "wind_sample", exception);
            return new WindSample(FALLBACK_DIRECTION, 1.0, false);
        }
    }

    public static Vec3 direction(ServerLevel level, BlockPos position, boolean useWeather2) {
        if (level == null || position == null) {
            return FALLBACK_DIRECTION;
        }
        WindManager windManager = getWindManager(level, useWeather2);
        if (windManager == null) {
            return FALLBACK_DIRECTION;
        }

        try {
            double angle = windManager.getWindAngle(Vec3.atCenterOf(position));
            Vec3 direction = direction(angle);
            Weather2WindDebugLog.report(level, "wind_direction", "active", () -> "Weather2 wind direction active in "
                    + level.dimension().location() + ": angle=" + angle + " degrees, direction=" + direction);
            return direction;
        } catch (LinkageError | RuntimeException exception) {
            reportFailure(level, "wind_direction", exception);
            return FALLBACK_DIRECTION;
        }
    }

    private static WindManager getWindManager(ServerLevel level, boolean useWeather2) {
        if (level == null) {
            return null;
        }
        if (!useWeather2) {
            Weather2WindDebugLog.report(level, "wind_sample", "disabled_by_config", () -> "Weather2 wind integration is disabled by config in "
                    + level.dimension().location() + "; using the westward fallback.");
            return null;
        }
        if (!ModList.get().isLoaded("weather2")) {
            Weather2WindDebugLog.report(level, "wind_sample", "weather2_missing", () -> "Weather2 is not installed in "
                    + level.dimension().location() + "; using the westward fallback.");
            return null;
        }

        try {
            if (!WeatherUtilConfig.listDimensionsWindEffects.contains(level.dimension().location().toString())) {
                Weather2WindDebugLog.report(level, "wind_sample", "dimension_disabled", () -> "Weather2 wind effects are disabled for "
                        + level.dimension().location() + "; using the westward fallback.");
                return null;
            }
            WeatherManagerServer manager = ServerTickHandler.getWeatherManagerFor(level);
            if (manager == null) {
                Weather2WindDebugLog.report(level, "wind_sample", "manager_unavailable", () -> "Weather2 has no wind manager for "
                        + level.dimension().location() + "; using the westward fallback.");
                return null;
            }
            WindManager windManager = manager.getWindManager();
            if (windManager == null) {
                Weather2WindDebugLog.report(level, "wind_sample", "manager_unavailable", () -> "Weather2 has no wind manager for "
                        + level.dimension().location() + "; using the westward fallback.");
            }
            return windManager;
        } catch (LinkageError | RuntimeException exception) {
            reportFailure(level, "wind_sample", exception);
            return null;
        }
    }

    private static Vec3 direction(double angle) {
        if (!Double.isFinite(angle)) {
            return FALLBACK_DIRECTION;
        }
        double radians = Math.toRadians(angle);
        Vec3 direction = new Vec3(-Math.sin(radians), 0.0, Math.cos(radians)).normalize();
        return Double.isFinite(direction.x) && Double.isFinite(direction.z) ? direction : FALLBACK_DIRECTION;
    }

    private static void reportFailure(ServerLevel level, String component, Throwable exception) {
        String state = "api_error:" + exception.getClass().getName();
        Weather2WindDebugLog.report(level, component, state, () -> "Weather2 wind access failed in "
                + level.dimension().location() + " (" + exception.getClass().getSimpleName() + ": "
                + exception.getMessage() + "); using the westward fallback.");
    }

    public record WindSample(Vec3 direction, double speed, boolean weather2Backed) {
    }
}
