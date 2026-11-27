package com.example.soundattract.pathfinding;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("soundattract_hazard_readreuse")
@PrefixGameTestTemplate(false)
public final class HazardClassifierReadReuseGameTests {
    private static final Method CLASSIFY_UNCACHED = classifierMethod();

    @GameTest(template = "empty", batch = "hazardReadReuse", timeoutTicks = 40)
    public void preservesHazardsAndReadsEachColumnCellOnce(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        BlockPos feet = helper.absolutePos(new BlockPos(4, 6, 4));
        BlockPos support = feet.below();
        BlockPos head = feet.above();
        BlockPos lowerSupport = support.below();
        BlockPos deepSupport = support.below(2);
        BlockPos[] cells = {feet, support, head, lowerSupport, deepSupport};
        for (BlockPos cell : cells) level.setBlockAndUpdate(cell, Blocks.AIR.defaultBlockState());
        level.getChunkSource().getChunk(feet.getX() >> 4, feet.getZ() >> 4,
                net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);

        level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(lowerSupport, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(deepSupport, Blocks.STONE.defaultBlockState());
        CountingBlockGetter supported = new CountingBlockGetter(level);
        require(classify(supported, feet) == HazardClassifier.HazardType.SAFE,
                "supported terrain changed classification");
        require(supported.reads.get() == 5, "supported terrain repeated block reads: " + supported.reads.get());

        for (BlockPos cell : cells) level.setBlockAndUpdate(cell, Blocks.AIR.defaultBlockState());
        CountingBlockGetter empty = new CountingBlockGetter(level);
        require(classify(empty, feet) == HazardClassifier.HazardType.DEEP_DROP,
                "empty column changed classification");
        require(empty.reads.get() == 5, "empty column repeated block reads: " + empty.reads.get());

        for (int index = 0; index < cells.length; index++) {
            level.setBlockAndUpdate(cells[index], Blocks.LAVA.defaultBlockState());
            CountingBlockGetter lava = new CountingBlockGetter(level);
            require(classify(lava, feet) == HazardClassifier.HazardType.LAVA,
                    "lava position changed classification at index " + index);
            require(lava.reads.get() == index + 1,
                    "lava short-circuit order changed at index " + index + ": reads=" + lava.reads.get());
            level.setBlockAndUpdate(cells[index], Blocks.AIR.defaultBlockState());
        }

        level.setBlockAndUpdate(feet, Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, true));
        CountingBlockGetter openTrapdoor = new CountingBlockGetter(level);
        require(classify(openTrapdoor, feet) == HazardClassifier.HazardType.TRAPDOOR_DROP,
                "unsupported open trapdoor changed classification");
        require(openTrapdoor.reads.get() == 5,
                "trapdoor check reread a known cell: " + openTrapdoor.reads.get());

        level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(lowerSupport, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(deepSupport, Blocks.STONE.defaultBlockState());
        CountingBlockGetter supportedTrapdoor = new CountingBlockGetter(level);
        require(classify(supportedTrapdoor, feet) == HazardClassifier.HazardType.SAFE,
                "open trapdoor on full support changed classification");
        helper.succeed();
    }

    private static HazardClassifier.HazardType classify(BlockGetter level, BlockPos feet) throws Exception {
        try {
            return (HazardClassifier.HazardType) CLASSIFY_UNCACHED.invoke(null, level, feet);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            throw failure;
        }
    }

    private static Method classifierMethod() {
        try {
            Method method = HazardClassifier.class.getDeclaredMethod("classifyUncached", BlockGetter.class, BlockPos.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class CountingBlockGetter implements BlockGetter {
        private final ServerLevel level;
        private final AtomicInteger reads = new AtomicInteger();
        private final BlockGetter proxy;

        private CountingBlockGetter(ServerLevel level) {
            this.level = level;
            this.proxy = (BlockGetter) Proxy.newProxyInstance(BlockGetter.class.getClassLoader(),
                    new Class<?>[]{BlockGetter.class}, (instance, method, arguments) -> {
                        if (method.getName().equals("getBlockState")) {
                            reads.incrementAndGet();
                        }
                        try {
                            return method.invoke(this.level, arguments);
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return proxy.getBlockState(pos);
        }

        @Override
        public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
            return proxy.getBlockEntity(pos);
        }

        @Override
        public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) {
            return proxy.getFluidState(pos);
        }

        @Override
        public int getMinBuildHeight() {
            return level.getMinBuildHeight();
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }
    }
}
