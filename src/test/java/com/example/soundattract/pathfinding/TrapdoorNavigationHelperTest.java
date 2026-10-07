package com.example.soundattract.pathfinding;

import static org.junit.jupiter.api.Assertions.assertFalse;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class TrapdoorNavigationHelperTest {

    @Test
    void nullInputsReturnFalseSafely() {
        assertFalse(TrapdoorNavigationHelper.isOpenTrapdoor(null, null));
        assertFalse(TrapdoorNavigationHelper.isOpenTrapdoor(null, new BlockPos(0, 64, 0)));
        assertFalse(TrapdoorNavigationHelper.isTrapdoorDropHazard(null, null));
        assertFalse(TrapdoorNavigationHelper.isTrapdoorDropHazard(null, new BlockPos(0, 64, 0)));
        assertFalse(TrapdoorNavigationHelper.shouldJumpOverTrapdoor(null, null));
        assertFalse(TrapdoorNavigationHelper.tryJumpOverTrapdoor(null));
    }
}
