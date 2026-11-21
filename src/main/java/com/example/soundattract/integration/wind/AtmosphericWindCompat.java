package com.example.soundattract.integration.wind;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.integration.projectatmosphere.ProjectAtmosphereWindCompat;
import com.example.soundattract.integration.weather2.Weather2WindCompat;
import com.example.soundattract.integration.weather2.Weather2WindDebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

public final class AtmosphericWindCompat {
    private static final Vec3 FALLBACK_DIRECTION = new Vec3(-1.0, 0.0, 0.0);

    private AtmosphericWindCompat() {
    }

    public static WindSample sample(ServerLevel level, BlockPos position) {
        if (level == null || position == null) return WindSample.fallback();

        if (SoundAttractConfig.COMMON.enableProjectAtmosphereWindIntegration.get()
                && ModList.get().isLoaded("projectatmosphere")) {
            try {
                ProjectAtmosphereWindCompat.WindSample projectWind = ProjectAtmosphereWindCompat.sample(level, position);
                if (projectWind.available()) {
                    reportSource(level, Source.PROJECT_ATMOSPHERE, projectWind.speed(), projectWind.direction());
                    return new WindSample(projectWind.direction(), projectWind.speed(), Source.PROJECT_ATMOSPHERE);
                }
            } catch (LinkageError | RuntimeException ignored) {
            }
        }

        if (SoundAttractConfig.COMMON.enableWeather2WindIntegration.get()
                && ModList.get().isLoaded("weather2")) {
            Weather2WindCompat.WindSample weatherWind = Weather2WindCompat.sample(level, position, true);
            if (weatherWind.weather2Backed()) {
                reportSource(level, Source.WEATHER2, weatherWind.speed(), weatherWind.direction());
                return new WindSample(weatherWind.direction(), weatherWind.speed(), Source.WEATHER2);
            }
        }

        reportSource(level, Source.FALLBACK, 1.0, FALLBACK_DIRECTION);
        return WindSample.fallback();
    }

    private static void reportSource(ServerLevel level, Source source, double speed, Vec3 direction) {
        Weather2WindDebugLog.report(level, "wind_provider", source.name(),
                () -> "Using " + source.label() + " in " + level.dimension().location()
                        + " (speed=" + speed + ", direction=" + direction + ").");
    }

    public static Vec3 direction(ServerLevel level, BlockPos position) {
        return sample(level, position).direction();
    }

    public enum Source {
        PROJECT_ATMOSPHERE("Project Atmosphere"),
        WEATHER2("Weather2"),
        FALLBACK("fallback wind");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record WindSample(Vec3 direction, double speed, Source source) {
        public boolean backedByMod() {
            return source != Source.FALLBACK;
        }

        private static WindSample fallback() {
            return new WindSample(FALLBACK_DIRECTION, 1.0, Source.FALLBACK);
        }
    }
}
