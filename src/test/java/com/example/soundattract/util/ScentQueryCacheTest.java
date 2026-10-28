package com.example.soundattract.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ScentQueryCacheTest {
    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Identifier NETHER = Identifier.fromNamespaceAndPath("minecraft", "the_nether");

    @AfterEach
    void clearCache() {
        ScentQueryCache.clear();
    }

    @Test
    void queryResultsAreScopedToDimension() {
        UUID mobId = UUID.randomUUID();
        ScentQueryCache.put(mobId, OVERWORLD, new ScentQueryCache.Result(100L, true, 0.75));

        assertNull(ScentQueryCache.getCached(mobId, NETHER, 100L, 5));
        assertEquals(0.75, ScentQueryCache.getCached(mobId, OVERWORLD, 100L, 5).strongestStrength());
    }

    @Test
    void tickRollbackInvalidatesInsteadOfReturningStaleData() {
        UUID mobId = UUID.randomUUID();
        ScentQueryCache.put(mobId, OVERWORLD, new ScentQueryCache.Result(100L, true, 0.75));

        assertNull(ScentQueryCache.getCached(mobId, OVERWORLD, 1L, 5));
        assertEquals(0, ScentQueryCache.size());
    }

    @Test
    void emptySentinelIsNeverCached() {
        ScentQueryCache.put(UUID.randomUUID(), OVERWORLD, ScentQueryCache.Result.EMPTY);

        assertEquals(0, ScentQueryCache.size());
    }

    @Test
    void cleanupUsesDimensionLocalTicksAndEnforcesCapacity() {
        UUID overworldMob = UUID.randomUUID();
        UUID netherMob = UUID.randomUUID();
        ScentQueryCache.put(overworldMob, OVERWORLD, new ScentQueryCache.Result(100L, true, 0.5));
        ScentQueryCache.put(netherMob, NETHER, new ScentQueryCache.Result(5L, true, 0.75));

        ScentQueryCache.cleanup(OVERWORLD, 1L, 5, 10);

        assertEquals(1, ScentQueryCache.size());
        assertEquals(0.75, ScentQueryCache.getCached(netherMob, NETHER, 5L, 5).strongestStrength());

        ScentQueryCache.put(UUID.randomUUID(), OVERWORLD, new ScentQueryCache.Result(10L, true, 0.25));
        ScentQueryCache.put(UUID.randomUUID(), OVERWORLD, new ScentQueryCache.Result(10L, true, 0.25));
        ScentQueryCache.cleanup(OVERWORLD, 10L, 5, 2);

        assertEquals(2, ScentQueryCache.size());
    }
}

