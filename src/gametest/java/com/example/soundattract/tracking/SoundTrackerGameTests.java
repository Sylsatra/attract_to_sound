package com.example.soundattract.tracking;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

public final class SoundTrackerGameTests {
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void duplicateSoundIsSuppressedAcrossTwoTicksThenStrongerReplacementIsAccepted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String dimension = level.dimension().identifier().toString();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        Identifier soundId = SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.stream().findFirst()
                .orElse(Identifier.fromNamespaceAndPath("minecraft", "entity.generic.hurt"));
        // Give each test run a unique metadata suffix so the tracker's global
        // two-tick dedup window cannot inherit a key from another test that
        // reused this GameTest structure origin.
        String id = soundId + "#gametest_" + java.util.UUID.randomUUID();
        // These records must survive the shared global cap even if other
        // concurrently running GameTests have already filled it.
        double firstWeight = 1_000_000.0;
        double strongerWeight = 5_000_000.0;

        try {
            SoundTracker.addSound(null, pos, dimension, 8.0, firstWeight, 200, id);
            boolean firstWasInserted = hasSingleRecord(level, dimension, pos, id, firstWeight);

            SoundTracker.addSound(null, pos, dimension, 8.0, strongerWeight, 200, id);
            boolean sameTickDuplicateSuppressed = hasSingleRecord(level, dimension, pos, id, firstWeight);

            SoundTracker.advanceDedupWindow();
            SoundTracker.addSound(null, pos, dimension, 8.0, strongerWeight, 200, id);
            boolean previousTickDuplicateSuppressed = hasSingleRecord(level, dimension, pos, id, firstWeight);

            SoundTracker.advanceDedupWindow();
            SoundTracker.addSound(null, pos, dimension, 8.0, strongerWeight, 200, id);
            boolean strongerRecordReplacedOldRecord = hasSingleRecord(level, dimension, pos, id, strongerWeight);

            if (!firstWasInserted || !sameTickDuplicateSuppressed || !previousTickDuplicateSuppressed
                    || !strongerRecordReplacedOldRecord) {
                helper.fail("Sound duplicate window/replacement behavior failed: first=" + firstWasInserted
                        + ", sameTickSuppressed=" + sameTickDuplicateSuppressed
                        + ", previousTickSuppressed=" + previousTickDuplicateSuppressed
                        + ", strongerReplacement=" + strongerRecordReplacedOldRecord);
                return;
            }
        } finally {
            SoundTracker.removeSoundAt(pos, dimension);
        }
        helper.succeed();
    }

    private static boolean hasSingleRecord(ServerLevel level, String dimension, BlockPos pos, String id, double weight) {
        List<SoundTracker.SoundRecord> matching = SoundTracker.getNearbySounds(level, dimension, pos, null).stream()
                .filter(record -> pos.equals(record.pos) && id.equals(record.soundId))
                .toList();
        return matching.size() == 1 && Math.abs(matching.get(0).weight - weight) < 0.0001;
    }
}

