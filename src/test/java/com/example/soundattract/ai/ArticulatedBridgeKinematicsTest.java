package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.example.soundattract.config.separate.LivingWallConfig;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ArticulatedBridgeKinematicsTest {

    @BeforeAll
    static void setUpConfig() {
        CommentedConfig configData = CommentedConfig.inMemory();
        LivingWallConfig.SPEC.correct(configData);
        LivingWallConfig.SPEC.acceptConfig(configData);
    }

    @Test
    void straightLineCalculatesInteriorAngleOf180Degrees() {
        Vec3 prev = new Vec3(0.0D, 0.0D, 0.0D);
        Vec3 elbow = new Vec3(2.0D, 0.0D, 0.0D);
        Vec3 dest = new Vec3(5.0D, 0.0D, 0.0D);

        double angle = LivingWallCoordinator.calculateInteriorAngleDegrees(prev, elbow, dest);
        assertEquals(180.0D, angle, 0.001D);
        assertTrue(angle >= LivingWallConfig.MIN_BEND_ANGLE_DEGREES.get());
    }

    @Test
    void rightAngleBendCalculatesInteriorAngleOf90Degrees() {
        Vec3 prev = new Vec3(0.0D, 0.0D, 0.0D);
        Vec3 elbow = new Vec3(3.0D, 0.0D, 0.0D);
        Vec3 dest = new Vec3(3.0D, 0.0D, 4.0D);

        double angle = LivingWallCoordinator.calculateInteriorAngleDegrees(prev, elbow, dest);
        assertEquals(90.0D, angle, 0.001D);
        assertTrue(angle >= LivingWallConfig.MIN_BEND_ANGLE_DEGREES.get());
    }

    @Test
    void sharpHairpinTurnCalculatesInteriorAngleBelowPhysicalLimit() {
        Vec3 prev = new Vec3(0.0D, 0.0D, 0.0D);
        Vec3 elbow = new Vec3(2.0D, 0.0D, 0.0D);
        Vec3 dest = new Vec3(0.5D, 0.0D, 0.0D);

        double angle = LivingWallCoordinator.calculateInteriorAngleDegrees(prev, elbow, dest);
        assertEquals(0.0D, angle, 0.001D);
        assertFalse(angle >= LivingWallConfig.MIN_BEND_ANGLE_DEGREES.get());
    }

    @Test
    void acuteFortyFiveDegreeBendIsRejectedByPhysicalLimit() {
        Vec3 prev = new Vec3(0.0D, 0.0D, 0.0D);
        Vec3 elbow = new Vec3(2.0D, 0.0D, 0.0D);
        Vec3 dest = new Vec3(2.0D - 1.0D, 0.0D, 1.0D);

        double angle = LivingWallCoordinator.calculateInteriorAngleDegrees(prev, elbow, dest);
        assertEquals(45.0D, angle, 0.01D);
        assertFalse(angle >= LivingWallConfig.MIN_BEND_ANGLE_DEGREES.get());
    }

    @Test
    void multiBendChainVerifiesMultipleArticulationsWithinLimits() {
        Vec3 start = new Vec3(0.0D, 0.0D, 0.0D);
        Vec3 elbow1 = new Vec3(2.0D, 0.0D, 0.0D);
        Vec3 elbow2 = new Vec3(2.0D, 0.0D, 3.0D);
        Vec3 destination = new Vec3(5.0D, 0.0D, 3.0D);

        double angle1 = LivingWallCoordinator.calculateInteriorAngleDegrees(start, elbow1, elbow2);
        double angle2 = LivingWallCoordinator.calculateInteriorAngleDegrees(elbow1, elbow2, destination);

        assertEquals(90.0D, angle1, 0.001D);
        assertEquals(90.0D, angle2, 0.001D);

        double minAngle = LivingWallConfig.MIN_BEND_ANGLE_DEGREES.get();
        assertTrue(angle1 >= minAngle);
        assertTrue(angle2 >= minAngle);

        int maxBends = LivingWallConfig.MAX_BRIDGE_BENDS.get();
        assertTrue(2 <= maxBends);
    }

    @Test
    void degenerateVectorsReturnZeroDegreesGracefully() {
        Vec3 zero = new Vec3(0.0D, 0.0D, 0.0D);
        assertEquals(0.0D, LivingWallCoordinator.calculateInteriorAngleDegrees(null, zero, zero));
        assertEquals(0.0D, LivingWallCoordinator.calculateInteriorAngleDegrees(zero, null, zero));
        assertEquals(0.0D, LivingWallCoordinator.calculateInteriorAngleDegrees(zero, zero, null));
        assertEquals(0.0D, LivingWallCoordinator.calculateInteriorAngleDegrees(zero, zero, zero));
    }

    @Test
    void configDefaultsMatchPhysicalRequirements() {
        assertNotNull(LivingWallConfig.ENABLE_ARTICULATED_BRIDGES);
        assertTrue(LivingWallConfig.ENABLE_ARTICULATED_BRIDGES.get());
        assertEquals(2, LivingWallConfig.MAX_BRIDGE_BENDS.get());
        assertEquals(75.0D, LivingWallConfig.MIN_BEND_ANGLE_DEGREES.get(), 0.001D);
        assertEquals(2, LivingWallConfig.OBSTACLE_DEFLECTION_RADIUS.get());
    }

    @Test
    void canonicalRouteFingerprintIsDeterministicAndQuantized() {
        List<Vec3> bends = List.of(new Vec3(1.0D / 4096.0D, 2.0D, 3.0D));
        String serialized = LivingWallCoordinator.canonicalBendSerialization(LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                new BlockPos(0, 64, 0), new BlockPos(8, 65, 0), new BlockPos(8, 65, 0), bends);
        assertTrue(serialized.startsWith("bridge-route-v1|WALL_LEDGE|"));
        assertTrue(serialized.endsWith("|1,8192,12288"));
        assertEquals(LivingWallCoordinator.articulationFingerprint(LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                new BlockPos(0, 64, 0), new BlockPos(8, 65, 0), new BlockPos(8, 65, 0), bends),
                LivingWallCoordinator.articulationFingerprint(LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                        new BlockPos(0, 64, 0), new BlockPos(8, 65, 0), new BlockPos(8, 65, 0), bends));
    }

    @Test
    void duplicateBendsAreRejectedBeforeFingerprinting() {
        assertTrue(LivingWallCoordinator.hasConsecutiveDuplicateBends(
                List.of(new Vec3(1, 2, 3), new Vec3(1, 2, 3))));
        assertFalse(LivingWallCoordinator.hasConsecutiveDuplicateBends(
                List.of(new Vec3(1, 2, 3), new Vec3(1, 2, 3.01))));
    }

    @Test
    void orderedBendsChangeCanonicalRoute() {
        List<Vec3> first = List.of(new Vec3(1, 2, 3), new Vec3(4, 5, 6));
        List<Vec3> second = List.of(new Vec3(4, 5, 6), new Vec3(1, 2, 3));
        assertNotEquals(LivingWallCoordinator.articulationFingerprint(LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0), first),
                LivingWallCoordinator.articulationFingerprint(LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                        new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0), second));
    }
}
