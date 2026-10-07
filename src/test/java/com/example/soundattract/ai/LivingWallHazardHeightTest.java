package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class LivingWallHazardHeightTest {
    private boolean near(BlockPos feet, BlockPos hazard) throws Exception {
        Method method = LivingWallCoordinator.class.getDeclaredMethod("isNearHazard",
                ServerLevel.class, BlockPos.class, List.class, Vec3.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, null, feet, List.of(hazard), new Vec3(1, 0, 0));
    }

    @Test
    void unrelatedHeightInSameColumnDoesNotBlockStanding() throws Exception {
        assertFalse(near(new BlockPos(8, 64, 3), new BlockPos(8, 50, 3)));
        assertFalse(near(new BlockPos(8, 64, 3), new BlockPos(8, 72, 3)));
    }

    @Test
    void feetHeadAndSupportHazardsStillBlockStanding() throws Exception {
        for (int y = 63; y <= 65; y++) {
            assertTrue(near(new BlockPos(8, 64, 3), new BlockPos(8, y, 3)));
        }
    }

    @Test
    void crossingAheadDoesNotMakeSafeTakeoffHazardous() throws Exception {
        assertFalse(near(new BlockPos(8, 64, 3), new BlockPos(9, 64, 3)));
        assertTrue(near(new BlockPos(9, 64, 3), new BlockPos(9, 64, 3)));
    }
}
