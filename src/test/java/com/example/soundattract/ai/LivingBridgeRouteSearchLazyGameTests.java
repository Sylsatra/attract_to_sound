package com.example.soundattract.ai;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("soundattract_routeroutelazy")
@PrefixGameTestTemplate(false)
public final class LivingBridgeRouteSearchLazyGameTests {
    @GameTest(template = "empty", batch = "lazyRouteSnapshot", timeoutTicks = 80)
    public void lazyTerrainSamplingPreservesRouteAndReadsOnlyVisitedCells(GameTestHelper helper) {
        for (int x = 0; x <= 32; x++) {
            for (int z = 0; z <= 16; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
                for (int y = 1; y <= 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }

        BlockPos start = helper.absolutePos(new BlockPos(5, 1, 8));
        BlockPos end = helper.absolutePos(new BlockPos(21, 1, 8));
        List<BlockPos> eagerOpen = eagerRoute(helper, start, end);
        List<BlockPos> liveOpen = LivingBridgeRouteSearch.findRoute(
                helper.getLevel(), start, end, 0.6D, 1.8D, 512);
        LivingBridgeRouteSearch.SnapshotWorldAccess lazyOpenSnapshot =
                LivingBridgeRouteSearch.SnapshotWorldAccess.captureLazy(
                        helper.getLevel(), start, end, 0.6D, 1.8D);
        List<BlockPos> lazyOpen = LivingBridgeRouteSearch.findRoute(
                lazyOpenSnapshot, start, end, 0.6D, 1.8D, 512);
        helper.assertTrue(!eagerOpen.isEmpty() && eagerOpen.equals(liveOpen) && eagerOpen.equals(lazyOpen),
                "open-lane route changed under lazy snapshot sampling");
        helper.assertTrue(lazyOpenSnapshot.classifiedCellCount() * 4
                        < lazyOpenSnapshot.volumeCellCount(),
                "open-lane search classified too much of its bounded volume: "
                        + lazyOpenSnapshot.classifiedCellCount() + "/" + lazyOpenSnapshot.volumeCellCount());

        helper.setBlock(new BlockPos(12, 1, 8), Blocks.OAK_SLAB.defaultBlockState());
        helper.setBlock(new BlockPos(14, 1, 8), Blocks.LAVA.defaultBlockState());
        List<BlockPos> eagerObstructed = eagerRoute(helper, start, end);
        List<BlockPos> liveObstructed = LivingBridgeRouteSearch.findRoute(
                helper.getLevel(), start, end, 0.6D, 1.8D, 512);
        LivingBridgeRouteSearch.SnapshotWorldAccess lazyObstructedSnapshot =
                LivingBridgeRouteSearch.SnapshotWorldAccess.captureLazy(
                        helper.getLevel(), start, end, 0.6D, 1.8D);
        List<BlockPos> lazyObstructed = LivingBridgeRouteSearch.findRoute(
                lazyObstructedSnapshot, start, end, 0.6D, 1.8D, 512);
        helper.assertTrue(eagerObstructed.equals(liveObstructed)
                        && eagerObstructed.equals(lazyObstructed) && !lazyObstructed.isEmpty(),
                "partial-block and lava detour changed under lazy sampling");
        helper.assertTrue(lazyObstructedSnapshot.classifiedCellCount() * 4
                        < lazyObstructedSnapshot.volumeCellCount(),
                "detour search classified too much of its bounded volume: "
                        + lazyObstructedSnapshot.classifiedCellCount() + "/"
                        + lazyObstructedSnapshot.volumeCellCount());

        helper.setBlock(new BlockPos(21, 1, 8), Blocks.STONE.defaultBlockState());
        List<BlockPos> eagerBlocked = eagerRoute(helper, start, end);
        List<BlockPos> liveBlocked = LivingBridgeRouteSearch.findRoute(
                helper.getLevel(), start, end, 0.6D, 1.8D, 512);
        LivingBridgeRouteSearch.SnapshotWorldAccess lazyBlockedSnapshot =
                LivingBridgeRouteSearch.SnapshotWorldAccess.captureLazy(
                        helper.getLevel(), start, end, 0.6D, 1.8D);
        List<BlockPos> lazyBlocked = LivingBridgeRouteSearch.findRoute(
                lazyBlockedSnapshot, start, end, 0.6D, 1.8D, 512);
        helper.assertTrue(eagerBlocked.equals(liveBlocked)
                        && eagerBlocked.equals(lazyBlocked) && lazyBlocked.isEmpty(),
                "blocked-endpoint result changed under lazy sampling");
        helper.assertTrue(lazyBlockedSnapshot.classifiedCellCount() * 4
                        < lazyBlockedSnapshot.volumeCellCount(),
                "blocked-endpoint search classified too much of its bounded volume: "
                        + lazyBlockedSnapshot.classifiedCellCount() + "/"
                        + lazyBlockedSnapshot.volumeCellCount());
        com.example.soundattract.SoundAttractMod.LOGGER.info(
                "[RouteSearchLazyTest] open={}/{} detour={}/{} blocked={}/{}",
                lazyOpenSnapshot.classifiedCellCount(), lazyOpenSnapshot.volumeCellCount(),
                lazyObstructedSnapshot.classifiedCellCount(), lazyObstructedSnapshot.volumeCellCount(),
                lazyBlockedSnapshot.classifiedCellCount(), lazyBlockedSnapshot.volumeCellCount());
        helper.succeed();
    }

    private static List<BlockPos> eagerRoute(GameTestHelper helper, BlockPos start, BlockPos end) {
        LivingBridgeRouteSearch.SnapshotWorldAccess snapshot = LivingBridgeRouteSearch.SnapshotWorldAccess.capture(
                helper.getLevel(), start, end, 0.6D, 1.8D);
        return LivingBridgeRouteSearch.findRoute(snapshot, start, end, 0.6D, 1.8D, 512);
    }
}
