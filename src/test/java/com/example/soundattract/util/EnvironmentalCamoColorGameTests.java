package com.example.soundattract.util;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

@GameTestHolder("soundattract_camo")
@PrefixGameTestTemplate(false)
public final class EnvironmentalCamoColorGameTests {
    @GameTest(template = "empty")
    public void mixedBackdropColorsAndInsufficientSamples(GameTestHelper helper) {
        var config = SoundAttractConfig.COMMON;
        String oldMode = config.environmentalCamouflageSamplingMode.get();
        int oldDistance = config.envBackdropMaxDistance.get();
        int oldRays = config.envBackdropSampleRays.get();
        int oldMinimum = config.envBackdropMinSamples.get();
        double oldMinTps = config.envBackdropMinTps.get();
        double oldRecoveryTps = config.envBackdropRecoveryTps.get();
        boolean oldUseSky = config.envBackdropUseSkyColor.get();

        try {
            config.environmentalCamouflageSamplingMode.set("hybrid");
            config.envBackdropMaxDistance.set(4);
            config.envBackdropSampleRays.set(5);
            config.envBackdropMinSamples.set(4);
            config.envBackdropMinTps.set(1.0);
            config.envBackdropRecoveryTps.set(1.0);
            config.envBackdropUseSkyColor.set(false);

            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            var level = helper.getLevel();
            Mob viewer = EntityType.ZOMBIE.create(level);
            Mob target = EntityType.ZOMBIE.create(level);
            viewer.setPos(origin.getX() + 2.1, origin.getY() + 2.0, origin.getZ() + 2.2);
            target.setPos(origin.getX() + 4.6, origin.getY() + 2.0, origin.getZ() + 3.6);

            level.setBlockAndUpdate(origin.offset(5, 1, 4), Blocks.GREEN_WOOL.defaultBlockState());
            level.setBlockAndUpdate(origin.offset(5, 2, 4), Blocks.RED_WOOL.defaultBlockState());
            level.setBlockAndUpdate(origin.offset(5, 3, 4), Blocks.BLUE_WOOL.defaultBlockState());
            level.setBlockAndUpdate(origin.offset(5, 2, 3), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(origin.offset(6, 2, 4), Blocks.GLASS.defaultBlockState());
            level.setBlockAndUpdate(origin.offset(7, 2, 4), Blocks.YELLOW_WOOL.defaultBlockState());

            int red = MapColor.COLOR_RED.col;
            int green = MapColor.COLOR_GREEN.col;
            int blue = MapColor.COLOR_BLUE.col;
            int expected = (meanChannel(red, green, blue, 16) << 16)
                    | (meanChannel(red, green, blue, 8) << 8) | meanChannel(red, green, blue, 0);
            Optional<Integer> backdrop = EnvironmentalCamoColor.getEnvironmentalColor(target, viewer, level, Optional::empty);
            helper.assertTrue(backdrop.equals(Optional.of(expected)), "four of five backdrop rays must contribute to the RGB average: " + backdrop);

            config.envBackdropMinSamples.set(5);
            Mob secondTarget = EntityType.ZOMBIE.create(level);
            secondTarget.setPos(target.position());
            Optional<Integer> fallback = Optional.of(0x123456);
            helper.assertTrue(EnvironmentalCamoColor.getEnvironmentalColor(secondTarget, viewer, level, () -> fallback).equals(fallback),
                    "the glass hit must stop the ray and make hybrid sampling use the fallback");
            helper.succeed();
        } finally {
            config.environmentalCamouflageSamplingMode.set(oldMode);
            config.envBackdropMaxDistance.set(oldDistance);
            config.envBackdropSampleRays.set(oldRays);
            config.envBackdropMinSamples.set(oldMinimum);
            config.envBackdropMinTps.set(oldMinTps);
            config.envBackdropRecoveryTps.set(oldRecoveryTps);
            config.envBackdropUseSkyColor.set(oldUseSky);
        }
    }

    private static int meanChannel(int red, int green, int blue, int shift) {
        return (2 * ((red >> shift) & 0xFF) + ((green >> shift) & 0xFF) + ((blue >> shift) & 0xFF)) / 4;
    }
}
