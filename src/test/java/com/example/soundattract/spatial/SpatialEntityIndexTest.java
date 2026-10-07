package com.example.soundattract.spatial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SpatialEntityIndexTest {
    private static final ResourceLocation DIM = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void partitionsAcrossTileBoundariesAndSortsDeterministically() {
        SpatialEntityIndex index = new SpatialEntityIndex();
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
        index.upsert(b, 1L, DIM, new Vec3(65.0D, 64.0D, 0.0D), true, true, 20.0D, null, null, 2L);
        index.upsert(a, 1L, DIM, new Vec3(1.0D, 64.0D, 1.0D), true, true, 20.0D, null, null, 1L);

        List<SpatialContracts.IndexedEntitySnapshot> queried = index.queryRadius(DIM, new Vec3(0.0D, 64.0D, 0.0D), 96.0D);
        assertEquals(List.of(a, b), queried.stream().map(SpatialContracts.IndexedEntitySnapshot::entityUuid).toList());
    }

    @Test
    void haloExcludesInnerRadiusAndIncludesOuterBand() {
        SpatialEntityIndex index = new SpatialEntityIndex();
        UUID inner = UUID.randomUUID();
        UUID outer = UUID.randomUUID();
        index.upsert(inner, 1L, DIM, new Vec3(2.0D, 64.0D, 0.0D), true, true, 20.0D, null, null, 1L);
        index.upsert(outer, 1L, DIM, new Vec3(20.0D, 64.0D, 0.0D), true, true, 20.0D, null, null, 1L);

        List<SpatialContracts.IndexedEntitySnapshot> halo = index.queryHalo(DIM, new Vec3(0.0D, 64.0D, 0.0D), 8.0D, 32.0D);
        assertFalse(halo.stream().anyMatch(s -> s.entityUuid().equals(inner)));
        assertTrue(halo.stream().anyMatch(s -> s.entityUuid().equals(outer)));
    }

    @Test
    void unloadAndInvalidateClearState() {
        SpatialEntityIndex index = new SpatialEntityIndex();
        UUID id = UUID.randomUUID();
        index.upsert(id, 1L, DIM, new Vec3(1.0D, 64.0D, 1.0D), true, true, 20.0D, null, null, 1L);
        index.unloadChunk(DIM, 0, 0);
        assertEquals(0, index.queryRadius(DIM, new Vec3(0.0D, 64.0D, 0.0D), 16.0D).size());
        index.upsert(id, 2L, DIM, new Vec3(1.0D, 64.0D, 1.0D), true, true, 20.0D, null, null, 2L);
        index.unloadLevel(DIM);
        assertEquals(0, index.queryRadius(DIM, new Vec3(0.0D, 64.0D, 0.0D), 16.0D).size());
    }
}
