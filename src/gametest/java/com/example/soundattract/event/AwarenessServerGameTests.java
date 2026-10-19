package com.example.soundattract.event;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class AwarenessServerGameTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:awareness_cadence", timeoutTicks = 100)
    public void awarenessSnapshotsAreStaggeredEveryFiveTicks(GameTestHelper helper) {
        int playerId = 4;
        long firstDueTick = 11L;
        boolean sendsAtDueTick = AwarenessServerEvents.shouldSendSnapshotAtTick(playerId, firstDueTick);
        boolean skipsFollowingTick = !AwarenessServerEvents.shouldSendSnapshotAtTick(playerId, firstDueTick + 1L);
        boolean sendsAtNextInterval = AwarenessServerEvents.shouldSendSnapshotAtTick(playerId, firstDueTick + 5L);
        boolean differentPlayerIsStaggered = !AwarenessServerEvents.shouldSendSnapshotAtTick(playerId + 1, firstDueTick);
        boolean staggeredPlayerGetsItsOwnSlot = AwarenessServerEvents.shouldSendSnapshotAtTick(playerId + 1, firstDueTick - 1L);

        if (!sendsAtDueTick || !skipsFollowingTick || !sendsAtNextInterval
                || !differentPlayerIsStaggered || !staggeredPlayerGetsItsOwnSlot) {
            helper.fail("Awareness five-tick snapshot cadence failed: due=" + sendsAtDueTick
                    + ", nextTickSkipped=" + skipsFollowingTick + ", nextInterval=" + sendsAtNextInterval
                    + ", playersStaggered=" + differentPlayerIsStaggered
                    + ", secondSlot=" + staggeredPlayerGetsItsOwnSlot);
            return;
        }
        helper.succeed();
    }
}
