package com.example.soundattract.integration.wind;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.fml.ModList;

@GameTestHolder("soundattract_projectatmosphere_absent")
@PrefixGameTestTemplate(false)
public final class ProjectAtmosphereAbsentGameTests {
    @GameTest(template = "empty")
    public void windProviderFallsBackWhenProjectAtmosphereIsAbsent(GameTestHelper helper) {
        helper.assertTrue(!ModList.get().isLoaded("projectatmosphere"),
                "the absent integration profile must launch without Project Atmosphere");
        boolean previous = SoundAttractConfig.COMMON.enableProjectAtmosphereWindIntegration.get();
        try {
            SoundAttractConfig.COMMON.enableProjectAtmosphereWindIntegration.set(true);
            var sample = AtmosphericWindCompat.sample(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
            helper.assertTrue(sample.source() != AtmosphericWindCompat.Source.PROJECT_ATMOSPHERE,
                    "wind selection must not report Project Atmosphere when that mod is absent");
            helper.assertTrue(Double.isFinite(sample.direction().x) && Double.isFinite(sample.direction().z)
                            && Double.isFinite(sample.speed()) && sample.speed() >= 0.0,
                    "fallback wind sample must remain finite when Project Atmosphere is absent");
            helper.succeed();
        } finally {
            SoundAttractConfig.COMMON.enableProjectAtmosphereWindIntegration.set(previous);
        }
    }
}
