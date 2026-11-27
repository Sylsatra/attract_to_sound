package com.example.soundattract.integration.csgrenades;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmokeLosSuppressionTest {
    @Test
    void cloudBlocksCrossingAndContainedSightLines() {
        assertTrue(SmokeLosSuppression.blocksRay(new Vec3(-3, 0, 0), new Vec3(3, 0, 0), Vec3.ZERO, 2));
        assertTrue(SmokeLosSuppression.blocksRay(new Vec3(-1, 0, 0), new Vec3(1, 0, 0), Vec3.ZERO, 2));
    }

    @Test
    void cloudPastTargetOrOutsideSightLineDoesNotBlock() {
        assertFalse(SmokeLosSuppression.blocksRay(new Vec3(3, 0, 0), new Vec3(5, 0, 0), Vec3.ZERO, 2));
        assertFalse(SmokeLosSuppression.blocksRay(new Vec3(-3, 3, 0), new Vec3(3, 3, 0), Vec3.ZERO, 2));
        assertFalse(SmokeLosSuppression.blocksRay(new Vec3(-3, 0, 0), new Vec3(3, 0, 0), Vec3.ZERO, 0));
    }
}
