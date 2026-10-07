package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.example.soundattract.spatial.SpatialEntityIndex;
import com.example.soundattract.spatial.SpatialLifecycleEvents;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MobGroupManagerSpatialParityTest {
    private static final ResourceLocation DIM = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void tileAndRadiusDiscoveryStayDeterministicAcrossBoundaries() {
        SpatialEntityIndex index = SpatialLifecycleEvents.index();
        index.invalidateConfig();
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
        index.upsert(a, 1L, DIM, new Vec3(63.9D, 64.0D, 63.9D), true, true, 20.0D, null, null, 1L);
        index.upsert(b, 1L, DIM, new Vec3(64.0D, 64.0D, 64.0D), true, true, 20.0D, null, null, 1L);

        Set<UUID> radius = Set.copyOf(index.queryRadius(DIM, new Vec3(64.0D, 64.0D, 64.0D), 1.0D).stream()
                .map(s -> s.entityUuid()).toList());
        assertEquals(Set.of(a, b), radius);
    }

    @Test
    void queryExactEdgesAndOverlappingRadiiRemainStable() {
        SpatialEntityIndex index = SpatialLifecycleEvents.index();
        index.invalidateConfig();
        UUID edge = UUID.randomUUID();
        UUID near = UUID.randomUUID();
        index.upsert(edge, 1L, DIM, new Vec3(10.0D, 64.0D, 0.0D), true, true, 20.0D, null, null, 1L);
        index.upsert(near, 1L, DIM, new Vec3(9.0D, 64.0D, 0.0D), true, true, 20.0D, null, null, 1L);
        assertTrue(index.queryRadius(DIM, new Vec3(0.0D, 64.0D, 0.0D), 10.0D).stream().anyMatch(s -> s.entityUuid().equals(edge)));
        assertTrue(index.queryRadius(DIM, new Vec3(0.0D, 64.0D, 0.0D), 9.5D).stream().anyMatch(s -> s.entityUuid().equals(near)));
    }

    @Test
    void dimensionSeparationRemovalAndStaleIncarnationRemainIsolated() {
        SpatialEntityIndex index = SpatialLifecycleEvents.index();
        index.invalidateConfig();
        UUID overworldId = UUID.randomUUID();
        UUID netherId = UUID.randomUUID();
        index.upsert(overworldId, 1L, DIM, new Vec3(4.0D, 64.0D, 4.0D), true, true, 20.0D, null, null, 1L);
        index.upsert(netherId, 2L, ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether"), new Vec3(4.0D, 64.0D, 4.0D), true, true, 20.0D, null, null, 2L);
        assertTrue(index.queryRadius(DIM, new Vec3(4.0D, 64.0D, 4.0D), 8.0D).stream().anyMatch(s -> s.entityUuid().equals(overworldId)));
        assertTrue(index.queryRadius(ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether"), new Vec3(4.0D, 64.0D, 4.0D), 8.0D).stream().anyMatch(s -> s.entityUuid().equals(netherId)));
        index.remove(DIM, overworldId);
        assertFalse(index.queryRadius(DIM, new Vec3(4.0D, 64.0D, 4.0D), 8.0D).stream().anyMatch(s -> s.entityUuid().equals(overworldId)));
    }
}
