package com.example.soundattract.ai;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder("soundattract_bridgehandoff")
public final class CrosserLandingBudgetGameTest {
    @GameTest(template = "empty", timeoutTicks = 100)
    public void landingHandoffRetriesAfterCommittedPathBudgetDenial(GameTestHelper helper) throws Exception {
        LivingWallTraversalRegressionTest.landingHandoffRetriesAfterCommittedPathBudgetDenial(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public void lateralDismountReachesFallAndKeepsTransitWhileAirborne(GameTestHelper helper) throws Exception {
        LivingWallTraversalRegressionTest.lateralDismountReachesFallAndKeepsTransitWhileAirborne(helper);
    }
}
