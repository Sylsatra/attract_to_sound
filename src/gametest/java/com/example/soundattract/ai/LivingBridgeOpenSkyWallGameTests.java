package com.example.soundattract.ai;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Saved-world terrain with actual air above it, clear of the GameTest barrier roof. */
public final class LivingBridgeOpenSkyWallGameTests {
    @GameTest(structure = "soundattract:living_bridge_open_sky_wall_regression", maxTicks = 1600)
    public void reportedOpenSkyWallBuildsUprightThenTiltsWithoutBlockPenetration(GameTestHelper helper) {
        LivingBridgeWallContactGameTests.runReportedWallScene(helper, true, 4);
    }
}
