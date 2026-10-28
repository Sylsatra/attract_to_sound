package com.example.soundattract.ai;

import com.example.soundattract.gametest.GameTestEntityTypes;
import com.example.soundattract.pathfinding.HazardClassifier;
import com.example.soundattract.pathfinding.NodeRouter;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class BridgeHazardRegressionGameTests {
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void nativeCrowdPushAvoidsLavaWithoutBlockingEscape(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(x, 2, z)),
                        (x == 5 ? Blocks.LAVA : Blocks.STONE).defaultBlockState(), 2);
                level.setBlock(helper.absolutePos(new BlockPos(x, 3, z)), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(helper.absolutePos(new BlockPos(x, 4, z)), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        var edge = helper.spawn(GameTestEntityTypes.ZOMBIE, 4, 3, 4);
        var behind = helper.spawn(GameTestEntityTypes.ZOMBIE, 3, 3, 4);
        behind.setPos(edge.position().add(-0.5D, 0.0D, 0.0D));
        edge.setDeltaMovement(Vec3.ZERO);
        edge.push(0.08D, 0.0D, 0.0D);
        if (edge.getDeltaMovement().lengthSqr() > 1.0E-12D) {
            helper.fail("Native push toward adjacent lava was applied: " + edge.getDeltaMovement());
            return;
        }
        edge.push(new Vec3(-0.08D, 0.0D, 0.0D));
        if (Math.abs(edge.getDeltaMovement().x + 0.08D) > 1.0E-9D) {
            helper.fail("Native push away from lava was blocked: " + edge.getDeltaMovement());
            return;
        }
        edge.setDeltaMovement(Vec3.ZERO);
        behind.setDeltaMovement(Vec3.ZERO);
        behind.push(edge);
        if (edge.getDeltaMovement().lengthSqr() > 1.0E-12D || behind.getDeltaMovement().x >= -1.0E-9D) {
            helper.fail("Collision pair did not block only the lava-directed impulse: edge="
                    + edge.getDeltaMovement() + ", behind=" + behind.getDeltaMovement());
            return;
        }
        var safe = helper.spawn(GameTestEntityTypes.ZOMBIE, 2, 3, 6);
        safe.setDeltaMovement(Vec3.ZERO);
        safe.push(0.08D, 0.0D, 0.0D);
        if (Math.abs(safe.getDeltaMovement().x - 0.08D) > 1.0E-9D) {
            helper.fail("Ordinary safe-ground push was blocked: " + safe.getDeltaMovement());
            return;
        }
        var fireType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "blaze"));
        var immuneEntity = helper.spawn(fireType, 4, 3, 6);
        if (!(immuneEntity instanceof net.minecraft.world.entity.Mob immune) || !immune.fireImmune()) {
            helper.fail("Fire-immune push fixture did not spawn a registered fire-immune mob");
            return;
        }
        immune.setDeltaMovement(Vec3.ZERO);
        immune.push(0.08D, 0.0D, 0.0D);
        if (Math.abs(immune.getDeltaMovement().x - 0.08D) > 1.0E-9D) {
            helper.fail("Fire-immune mob lost its ordinary lava-side push: " + immune.getDeltaMovement());
            return;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void supportSearchIncludesWallLipAboveFractionalFeet(GameTestHelper helper) {
        BlockPos lip = helper.absolutePos(new BlockPos(4, 3, 4));
        helper.getLevel().setBlock(lip, Blocks.COBBLESTONE.defaultBlockState(), 2);
        double feetY = lip.getY() - 0.11754375726504D;
        AABB body = new AABB(lip.getX() + 0.08888887696796D, feetY, lip.getZ() + 0.1D,
                lip.getX() + 0.688888900809818D, feetY + 1.950000047683716D, lip.getZ() + 0.7D);
        try {
            var search = LivingWallCoordinator.class.getDeclaredMethod("findSupportLift",
                    net.minecraft.server.level.ServerLevel.class, AABB.class, double.class);
            search.setAccessible(true);
            double lift = (double) search.invoke(null, helper.getLevel(), body, 2.030000047683716D);
            if (Math.abs(lift - 1.11754375726504D) > 1.0E-7D) {
                helper.fail("Reported fractional-foot wall lip was missed: feetY=" + feetY + ", lip=" + lip + ", lift=" + lift);
                return;
            }
            if (helper.getLevel().getBlockCollisions(null, body.move(0.0D, lift, 0.0D)).iterator().hasNext()) {
                helper.fail("Wall-lip support search approved a raised body still inside blocks");
                return;
            }
            helper.succeed();
        } catch (ReflectiveOperationException e) {
            helper.fail("Could not inspect the reported wall-lip support search: " + e);
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void lavaPlacedAfterSafeProbeImmediatelyBlocksPursuit(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos feet = helper.absolutePos(new BlockPos(4, 3, 4));
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlock(feet.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(feet.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(feet.offset(dx, 1, dz), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        var pursuer = helper.spawn(GameTestEntityTypes.ZOMBIE, 2, 3, 4);
        HazardClassifier.markPursuer(pursuer);
        WalkNodeEvaluator evaluator = new WalkNodeEvaluator();
        evaluator.prepare(new net.minecraft.world.level.PathNavigationRegion(level,
                feet.offset(-3, -3, -3), feet.offset(3, 3, 3)), pursuer);
        var safe = HazardClassifier.classify(level, feet);
        boolean safeGround = HazardClassifier.isSafeGround(level, feet);
        PathType safeNativeNode = evaluator.getPathTypeOfMob(new PathfindingContext(level, pursuer),
                feet.getX(), feet.getY(), feet.getZ(), pursuer);
        if (safe != HazardClassifier.HazardType.SAFE || !safeGround || safeNativeNode == PathType.BLOCKED) {
            helper.fail("Hazard fixture did not start with safe walkable support: type=" + safe
                    + ", safeGround=" + safeGround + ", nativeNode=" + safeNativeNode);
            return;
        }
        level.setBlock(feet.below(), Blocks.LAVA.defaultBlockState(), 2);
        var lava = HazardClassifier.classify(level, feet);
        boolean lavaGround = HazardClassifier.isSafeGround(level, feet);
        PathType lavaNativeNode = evaluator.getPathTypeOfMob(new PathfindingContext(level, pursuer),
                feet.getX(), feet.getY(), feet.getZ(), pursuer);
        Vec3 routed = NodeRouter.getWaypoint(pursuer, Vec3.atBottomCenterOf(feet));
        if (lava != HazardClassifier.HazardType.LAVA || lavaGround || lavaNativeNode != PathType.BLOCKED
                || routed.distanceToSqr(pursuer.position()) > 1.0E-9D) {
            helper.fail("New lava retained a safe pursuit decision: type=" + lava + ", safeGround=" + lavaGround
                    + ", nativeNode=" + lavaNativeNode + ", waypoint=" + routed);
            return;
        }
        level.setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 2);
        if (HazardClassifier.classify(level, feet) != HazardClassifier.HazardType.SAFE
                || !HazardClassifier.isSafeGround(level, feet)) {
            helper.fail("Removing lava did not restore safe support");
            return;
        }
        // Staging requires a safe 3x3 footing even when the center cell stays solid.
        level.setBlock(feet.below().east(), Blocks.LAVA.defaultBlockState(), 2);
        if (HazardClassifier.isSafeGround(level, feet)
                || HazardClassifier.classify(level, feet) != HazardClassifier.HazardType.SAFE) {
            helper.fail("Neighbor lava left stale safe staging or changed unrelated center classification");
            return;
        }
        level.setBlock(feet.below().east(), Blocks.STONE.defaultBlockState(), 2);
        if (!HazardClassifier.isSafeGround(level, feet)) {
            helper.fail("Restoring neighboring support left stale unsafe staging");
            return;
        }
        evaluator.done();
        helper.succeed();
    }
}
