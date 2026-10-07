package com.example.soundattract.ai;

import java.util.List;
import com.example.soundattract.SoundAttractMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(SoundAttractMod.MOD_ID)
public class LivingWallLidarGameTests {
    @GameTest(template = "empty")
    public void hazardCellsExcludeNonHazardCells(GameTestHelper helper) {
        WallProfileScanner.RayEvidence ray = new WallProfileScanner.RayEvidence(
                23L,
                new net.minecraft.world.phys.Vec3(0.0D, 0.0D, 0.0D),
                new net.minecraft.world.phys.Vec3(1.0D, 0.0D, 0.0D),
                BlockPos.ZERO,
                List.of(new BlockPos(1, 1, 1), new BlockPos(2, 1, 1)),
                List.of(WallProfileScanner.CollisionKind.SOLID, WallProfileScanner.CollisionKind.OPEN_TRAPDOOR),
                false,
                0.31D,
                0.0D);
        if (!WallProfileScanner.hazardCells(ray).equals(List.of(new BlockPos(2, 1, 1)))) {
            throw new AssertionError("hazard filtering failed");
        }
        helper.succeed();
    }
}