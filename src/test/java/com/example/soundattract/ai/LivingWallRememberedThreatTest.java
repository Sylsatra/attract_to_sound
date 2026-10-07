package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class LivingWallRememberedThreatTest {

    @Test
    void rememberedThreatValidWithinTtl() {
        UUID id = UUID.randomUUID();
        BlockPos pos = new BlockPos(10, 65, 20);
        long seenTick = 1000L;
        long ttl = 600L;
        LivingWallCoordinator.RememberedThreat threat = new LivingWallCoordinator.RememberedThreat(id, pos, seenTick);

        assertEquals(id, threat.targetUuid());
        assertEquals(pos, threat.lastKnownPos());
        assertEquals(seenTick, threat.lastSeenTick());

        assertTrue(threat.isValid(1000L, ttl));
        assertTrue(threat.isValid(1599L, ttl));
        assertTrue(threat.isValid(1600L, ttl));
        assertFalse(threat.isValid(1601L, ttl));
        assertFalse(threat.isValid(999L, ttl));
    }

    @Test
    void rememberedThreatDefendsAgainstNullsAndMutablePositions() {
        assertThrows(NullPointerException.class, () -> new LivingWallCoordinator.RememberedThreat(null, new BlockPos(0, 0, 0), 100L));

        UUID id = UUID.randomUUID();
        LivingWallCoordinator.RememberedThreat nullPosThreat = new LivingWallCoordinator.RememberedThreat(id, null, 100L);
        assertEquals(BlockPos.ZERO, nullPosThreat.lastKnownPos());

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos(5, 10, 15);
        LivingWallCoordinator.RememberedThreat threat = new LivingWallCoordinator.RememberedThreat(id, mutablePos, 100L);
        mutablePos.set(99, 99, 99);

        assertEquals(new BlockPos(5, 10, 15), threat.lastKnownPos());
    }
}
