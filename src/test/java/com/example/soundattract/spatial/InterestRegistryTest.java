package com.example.soundattract.spatial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class InterestRegistryTest {
    private static final SpatialContracts.TileKey TILE = new SpatialContracts.TileKey(
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), 0, 0, 0L);

    @Test
    void expiresAndReplacesOwnerTickets() {
        InterestRegistry registry = new InterestRegistry(8);
        registry.register("player-a", "sound", TILE, 3L);
        registry.tick(4L);
        assertEquals(0, registry.size());
        registry.register("player-a", "raid", TILE, 5L);
        registry.register("player-a", "bridge", TILE, 5L);
        assertEquals(1, registry.size());
        assertTrue(registry.snapshot().get(0).interestType().equals("bridge"));
    }

    @Test
    void evictsOldestWhenAtCapacity() {
        InterestRegistry registry = new InterestRegistry(2);
        registry.register("a", "one", TILE, 10L);
        registry.register("b", "two", TILE, 10L);
        registry.register("c", "three", TILE, 10L);
        assertEquals(2, registry.size());
        assertFalse(registry.snapshot().stream().anyMatch(ticket -> ticket.ownerKey().equals("a")));
    }

    @Test
    void invalidationDropsAllTicketsAndAdvancesEpoch() {
        InterestRegistry registry = new InterestRegistry(4);
        registry.register("a", "one", TILE, 10L);
        registry.invalidateEpoch();
        assertEquals(0, registry.size());
        assertEquals(0, registry.snapshot().size());
    }
}
