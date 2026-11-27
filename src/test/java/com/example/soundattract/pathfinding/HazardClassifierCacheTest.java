package com.example.soundattract.pathfinding;

import static com.example.soundattract.pathfinding.HazardClassifier.HazardType.LAVA;
import static com.example.soundattract.pathfinding.HazardClassifier.HazardType.SAFE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.soundattract.pathfinding.HazardClassifier.CachedHazard;
import com.example.soundattract.pathfinding.HazardClassifier.RecentHazards;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class HazardClassifierCacheTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    private static final ResourceLocation NETHER = ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether");

    private long now;
    private final RecentHazards cache = new RecentHazards(() -> now);

    @Test
    void retainsNearbyPositionsAlongBothPositiveAndNegativeZ() {
        assertRetainsZRow(0, 63, 0);
        assertRetainsZRow(-16, -32, -16);
    }

    private void assertRetainsZRow(int x, int y, int startZ) {
        for (int z = startZ; z < startZ + 16; z++) {
            long position = BlockPos.asLong(x, y, z);
            cache.put(cache.index(position), new CachedHazard(OVERWORLD, position, SAFE, now));
        }

        for (int z = startZ; z < startZ + 16; z++) {
            long position = BlockPos.asLong(x, y, z);
            assertEquals(SAFE, cache.get(cache.index(position), OVERWORLD, position));
        }
    }

    @Test
    void importedValueExpiresAtItsOriginalWriteDeadline() {
        long position = BlockPos.asLong(0, 63, 0);
        int index = cache.index(position);
        long writtenAt = TimeUnit.SECONDS.toNanos(1);
        now = writtenAt + TimeUnit.MILLISECONDS.toNanos(499);
        cache.put(index, new CachedHazard(OVERWORLD, position, LAVA, writtenAt));

        assertEquals(LAVA, cache.get(index, OVERWORLD, position));
        now = writtenAt + TimeUnit.MILLISECONDS.toNanos(500);
        assertNull(cache.get(index, OVERWORLD, position));
    }

    @Test
    void checksTheFullKeyAndHonorsClear() {
        long position = BlockPos.asLong(0, 63, 0);
        int index = cache.index(position);
        cache.put(index, new CachedHazard(OVERWORLD, position, LAVA, now));

        assertEquals(LAVA, cache.get(index, ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), position));
        assertNull(cache.get(index, NETHER, position));
        assertNull(cache.get(index, OVERWORLD, BlockPos.asLong(0, 63, 1)));
        cache.clear();
        assertNull(cache.get(index, OVERWORLD, position));
    }
}
