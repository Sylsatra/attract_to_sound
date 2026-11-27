package com.example.soundattract.integration.wind;

import com.example.soundattract.config.SoundAttractConfig;
import java.util.HashMap;
import net.Gabou.projectatmosphere.api.WindVectorApi;
import net.Gabou.projectatmosphere.manager.ForecastGenerator;
import net.Gabou.projectatmosphere.modules.region.ForecastRegion;
import net.Gabou.projectatmosphere.modules.wind.WindEngine;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.fml.ModList;

@GameTestHolder("soundattract_projectatmosphere_present")
@PrefixGameTestTemplate(false)
public final class ProjectAtmospherePresentGameTests {
    @GameTest(template = "empty")
    public void windProviderUsesProjectAtmosphereWhenAvailable(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("projectatmosphere"),
                "the present integration profile must launch with Project Atmosphere");
        boolean previous = SoundAttractConfig.COMMON.enableProjectAtmosphereWindIntegration.get();
        var position = helper.absolutePos(BlockPos.ZERO);
        var key = RegionInstanceKey.from(position);
        var originalForecasts = new HashMap<>(ForecastGenerator.getRegionForecasts());
        try {
            SoundAttractConfig.COMMON.enableProjectAtmosphereWindIntegration.set(true);
            var seededForecasts = new HashMap<>(originalForecasts);
            seededForecasts.put(key, new ForecastRegion(key, position));
            WindEngine.rebuildFromRegions(seededForecasts);
            var expected = WindVectorApi.getSurface(key, helper.getLevel().getGameTime());
            var sample = AtmosphericWindCompat.sample(helper.getLevel(), position);
            helper.assertTrue(sample.source() == AtmosphericWindCompat.Source.PROJECT_ATMOSPHERE,
                    "wind selection must use Project Atmosphere when its API returns a surface vector: " + sample);
            helper.assertTrue(expected.speedMps() > 0.0F && sample.speed() > 0.0,
                    "the seeded forecast and selected wind must both be nonzero: " + sample
                            + " expected=" + expected);
            helper.assertTrue(Double.isFinite(sample.direction().x) && Double.isFinite(sample.direction().z)
                            && Double.isFinite(sample.speed()) && sample.speed() >= 0.0
                            && Math.abs(sample.direction().length() - 1.0) < 1.0E-6,
                    "Project Atmosphere wind sample must have a finite normalized direction");
            helper.succeed();
        } finally {
            WindEngine.rebuildFromRegions(originalForecasts);
            SoundAttractConfig.COMMON.enableProjectAtmosphereWindIntegration.set(previous);
        }
    }
}
