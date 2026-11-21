package com.example.soundattract.util;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ScentQueryCacheTest {
    @Test
    void separatesResultsByDimension() {
        UUID mobId = UUID.randomUUID();
        ResourceLocation overworld = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        ResourceLocation nether = ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether");
        ScentQueryCache.Result overworldResult = new ScentQueryCache.Result(100L, true, 1.0D);
        ScentQueryCache.Result netherResult = new ScentQueryCache.Result(100L, false, 0.0D);

        ScentQueryCache.put(mobId, overworld, overworldResult);
        ScentQueryCache.put(mobId, nether, netherResult);

        assertEquals(overworldResult, ScentQueryCache.getCached(mobId, overworld, 101L, 20));
        assertEquals(netherResult, ScentQueryCache.getCached(mobId, nether, 101L, 20));

        ScentQueryCache.invalidate(mobId);
        assertNull(ScentQueryCache.getCached(mobId, overworld, 101L, 20));
        assertNull(ScentQueryCache.getCached(mobId, nether, 101L, 20));
    }

    @Test
    void cleanupTrimsToConfiguredMaximumWithoutSorting() {
        ScentQueryCache.clear();
        ResourceLocation overworld = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        ScentQueryCache.put(UUID.randomUUID(), overworld, new ScentQueryCache.Result(100L, true, 1.0D));
        ScentQueryCache.put(UUID.randomUUID(), overworld, new ScentQueryCache.Result(101L, true, 1.0D));

        ScentQueryCache.cleanup(101L, 20, 0);

        assertEquals(0, ScentQueryCache.size());
    }

    @Test
    void exactQueryCacheSeparatesPositionRangeAgeAndRevision() {
        ScentQueryCache.clear();
        UUID mobId = UUID.randomUUID();
        ResourceLocation overworld = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        ScentQueryCache.Result result = new ScentQueryCache.Result(100L, true, 0.8D);
        ScentQueryCache.put(mobId, overworld, 0.5, 64.0, 0.5, 12.0, 400, 7L, result);

        assertEquals(result, ScentQueryCache.getCached(mobId, overworld, 100L,
                0.5, 64.0, 0.5, 12.0, 400, 7L));
        assertNull(ScentQueryCache.getCached(mobId, overworld, 101L,
                0.5, 64.0, 0.5, 12.0, 400, 7L));
        assertNull(ScentQueryCache.getCached(mobId, overworld, 100L,
                1.5, 64.0, 0.5, 12.0, 400, 7L));
        assertNull(ScentQueryCache.getCached(mobId, overworld, 100L,
                0.5, 64.0, 0.5, 6.0, 400, 7L));
        assertNull(ScentQueryCache.getCached(mobId, overworld, 100L,
                0.5, 64.0, 0.5, 12.0, 200, 7L));
        assertNull(ScentQueryCache.getCached(mobId, overworld, 100L,
                0.5, 64.0, 0.5, 12.0, 400, 8L));
    }
}
