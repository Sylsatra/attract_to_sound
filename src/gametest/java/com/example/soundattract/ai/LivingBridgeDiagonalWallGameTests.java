package com.example.soundattract.ai;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class LivingBridgeDiagonalWallGameTests {
    @GameTest(structure = "soundattract:living_bridge_east_wall_regression", maxTicks = 1600)
    public void reportedDiagonalApproachBuildsAcrossLava(GameTestHelper helper) {
        LivingBridgeWallContactGameTests.runReportedWallScene(helper, true, 4);
    }
}
