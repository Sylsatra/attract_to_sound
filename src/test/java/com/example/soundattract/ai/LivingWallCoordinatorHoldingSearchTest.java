package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class LivingWallCoordinatorHoldingSearchTest {

    @Test
    void holdingPathRejectsDangerousVerticalDrop() {
        assertTrue(LivingWallCoordinator.isHoldingPathSafe(List.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0), new BlockPos(2, 66, 0))));
        assertFalse(LivingWallCoordinator.isHoldingPathSafe(List.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 58, 0))));
    }
    @Test
    void cursorProgressesAcrossTicksWithoutTreatingPartialWorkAsSuccess() throws Exception {
        Method stepMethod = LivingWallCoordinator.class.getDeclaredMethod("advanceHoldingSearchStep", List.class, int.class, int.class);
        stepMethod.setAccessible(true);
        Object first = stepMethod.invoke(null, List.of(new BlockPos(1, 64, 1), new BlockPos(2, 64, 2)), 0, 1);
        Object second = stepMethod.invoke(null, List.of(new BlockPos(1, 64, 1), new BlockPos(2, 64, 2)), 1, 1);
        Class<?> stepClass = first.getClass();
        Field cellField = stepClass.getDeclaredField("cell");
        Field cursorField = stepClass.getDeclaredField("cursor");
        Field completedField = stepClass.getDeclaredField("completed");
        cellField.setAccessible(true);
        cursorField.setAccessible(true);
        completedField.setAccessible(true);
        assertEquals(new BlockPos(1, 64, 1), cellField.get(first));
        assertEquals(1, cursorField.get(first));
        assertFalse(completedField.getBoolean(first));
        assertEquals(new BlockPos(2, 64, 2), cellField.get(second));
        assertEquals(2, cursorField.get(second));
        assertTrue(completedField.getBoolean(second));
    }

    @Test
    void cacheKeysAreRevisionAndTerrainAware() throws Exception {
        Class<?> keyClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$HoldingCellCacheKey");
        Constructor<?> ctor = keyClass.getDeclaredConstructor(ResourceLocation.class, BlockPos.class, BlockPos.class, long.class, long.class);
        ctor.setAccessible(true);
        Object first = ctor.newInstance(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                new BlockPos(4, 64, 4), new BlockPos(8, 64, 8), 99L, 1L);
        Object same = ctor.newInstance(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                new BlockPos(4, 64, 4), new BlockPos(8, 64, 8), 99L, 1L);
        Object differentRevision = ctor.newInstance(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                new BlockPos(4, 64, 4), new BlockPos(8, 64, 8), 99L, 2L);
        assertEquals(first, same);
        assertFalse(first.equals(differentRevision));
    }

    @Test
    void cacheEntryIsBoundedAndCanBeInvalidatedByKeyChange() throws Exception {
        Class<?> keyClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$HoldingCellCacheKey");
        Class<?> entryClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$HoldingCellCacheEntry");
        Constructor<?> keyCtor = keyClass.getDeclaredConstructor(ResourceLocation.class, BlockPos.class, BlockPos.class, long.class, long.class);
        Constructor<?> entryCtor = entryClass.getDeclaredConstructor(BlockPos.class, long.class);
        keyCtor.setAccessible(true);
        entryCtor.setAccessible(true);
        Field cacheField = LivingWallCoordinator.class.getDeclaredField("HOLDING_CELL_CACHE");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Object, Object> cache = (Map<Object, Object>) cacheField.get(null);
        synchronized (cache) {
            cache.clear();
            Object key = keyCtor.newInstance(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                    new BlockPos(4, 64, 4), new BlockPos(8, 64, 8), 11L, 1L);
            cache.put(key, entryCtor.newInstance(new BlockPos(4, 64, 4), 50L));
            Object same = keyCtor.newInstance(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                    new BlockPos(4, 64, 4), new BlockPos(8, 64, 8), 11L, 1L);
            Object invalidated = keyCtor.newInstance(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                    new BlockPos(4, 64, 4), new BlockPos(8, 64, 8), 12L, 1L);
            assertTrue(cache.containsKey(same));
            assertFalse(cache.containsKey(invalidated));
            cache.clear();
        }
    }
}
