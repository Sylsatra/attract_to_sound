package com.example.soundattract.ai;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Reproduction of a saved staging owner stranded twenty blocks above its old corridor. */
public final class LivingBridgeOrphanRecoveryGameTests {
    public static void verifyLiveOwner(GameTestHelper helper) {
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++)
            helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        helper.setBlock(new BlockPos(4, 5, 5), Blocks.STONE);
        java.util.ArrayList<Mob> members = new java.util.ArrayList<>();
        for (int slot = 0; slot < 7; slot++) members.add(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 2)));
        try (var session = new LivingWallCoordinator.DeploymentTestSession(helper.getLevel(), members,
                helper.absolutePos(new BlockPos(3, 2, 2)), helper.absolutePos(new BlockPos(4, 6, 5)))) {
            Mob worker = members.getLast();
            Vec3 before = worker.position();
            Vec3 endpoint = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4)));
            var safe = LivingBridgeRouteSnapshot.Point.from(endpoint.x, endpoint.y, endpoint.z);
            var snapshot = new LivingBridgeRouteSnapshot(UUID.randomUUID(), worker.getUUID(), session.formationIdForTest(),
                    helper.getLevel().dimension().identifier().toString(), 0, UUID.randomUUID(), 4,
                    "SAFE_STAGING_NAVIGATION", "WALKING_TO_STAGING", false, false, false,
                    List.of(LivingBridgeRouteSnapshot.Point.from(before.x, before.y, before.z), safe),
                    1229, List.of(), 0, safe, worker.chunkPosition().x, worker.chunkPosition().z);
            var data = LivingBridgeRouteData.get(helper.getLevel());
            if (!data.beginInitialClaim(snapshot, worker)) { helper.fail("Live-owner fixture could not claim its snapshot"); return; }
            LivingWallCoordinator.recoverPeriodicMarkerForTest(helper.getLevel(), worker);
            if (!worker.position().equals(before) || !data.snapshot(snapshot.reservationId()).exactlyEquals(snapshot)) {
                helper.fail("Orphan cleanup took control of a live structural worker"); return;
            }
            helper.succeed();
        } finally { members.forEach(Mob::discard); }
    }

    public static void verify(GameTestHelper helper, boolean obstructed) {
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++)
            helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 4));
        Vec3 endpoint = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4)));
        Vec3 stranded = endpoint.add(-2, 20, 0);
        mob.setPos(stranded.x, stranded.y, stranded.z);
        var safe = LivingBridgeRouteSnapshot.Point.from(endpoint.x, endpoint.y, endpoint.z);
        var reservation = new LivingBridgeRouteSnapshot(UUID.randomUUID(), mob.getUUID(), UUID.randomUUID(),
                helper.getLevel().dimension().identifier().toString(), 0, UUID.randomUUID(), 4,
                "SAFE_STAGING_NAVIGATION", "WALKING_TO_STAGING", false, false, false,
                List.of(LivingBridgeRouteSnapshot.Point.from(endpoint.x + 2, endpoint.y, endpoint.z), safe),
                1229, List.of(), 0, safe, mob.chunkPosition().x, mob.chunkPosition().z);
        LivingBridgeRouteData data = LivingBridgeRouteData.get(helper.getLevel());
        if (!data.beginInitialClaim(reservation, mob)) { helper.fail("Could not persist representative orphan ownership"); return; }
        mob.setNoGravity(true);
        mob.noPhysics = true;
        BlockPos obstacle = new BlockPos(3, 12, 4);
        if (obstructed) helper.setBlock(obstacle, Blocks.STONE);
        LivingWallCoordinator.enqueueOrphanRecoveryForTest(helper.getLevel(), mob);
        long now = helper.getLevel().getGameTime();
        float health = mob.getHealth();
        try {
            if (obstructed) {
                for (int tick = 0; tick < 50; tick++) LivingWallCoordinator.tickOrphanRecoveriesForTest(helper.getLevel(), now++);
                if (!mob.position().equals(stranded) || !mob.isNoGravity()
                        || !data.snapshot(reservation.reservationId()).exactlyEquals(reservation)) {
                    helper.fail("An obstructed orphan bypassed collision or discarded its durable owner"); return;
                }
                helper.setBlock(obstacle, Blocks.AIR);
            }
            int pieces = (int) Math.ceil(stranded.distanceTo(endpoint) / 0.2);
            Vec3 prior = stranded;
            for (int piece = 1; piece <= pieces; piece++) {
                Vec3 point = stranded.lerp(endpoint, piece / (double) pieces);
                if (!LivingWallCoordinator.bridgeMovementClearForTest(helper.getLevel(), mob, prior, point)) {
                    var box = mob.getBoundingBox().move(point.subtract(mob.position()));
                    var blocks = helper.getLevel().getBlockCollisions(mob, box).iterator();
                    helper.fail("Recovery fixture sweep blocked at " + point + " blocks=" + (blocks.hasNext() ? blocks.next() : "none")
                            + " entities=" + helper.getLevel().getEntities(mob, box).stream().map(e -> e.getType().toString()).toList());
                    return;
                }
                prior = point;
            }
            for (int tick = 0; tick < 210; tick++) {
                Vec3 previous = mob.position();
                LivingWallCoordinator.tickOrphanRecoveriesForTest(helper.getLevel(), now++);
                if (mob.position().distanceTo(previous) > 0.201
                        || helper.getLevel().getBlockCollisions(mob, mob.getBoundingBox().deflate(1.0E-7)).iterator().hasNext()) {
                    helper.fail("Orphan recovery teleported or crossed an obstacle"); return;
                }
                if (!mob.isNoGravity()) {
                    var terminal = data.snapshot(reservation.reservationId());
                    if (mob.position().distanceTo(endpoint) > 0.01 || mob.noPhysics || mob.getHealth() != health
                            || !LivingBridgeRouteData.isTerminalLaneState(terminal.lifecycleState())
                            || mob.getPersistentData().contains("livingBridgeControl")) {
                        helper.fail("Orphan recovery did not restore its endpoint, physics and durable terminal state"); return;
                    }
                    helper.succeed();
                    return;
                }
            }
            helper.fail("The twenty-block orphan remained suspended: recoverable=" + data.canRecoverOwner(data.snapshot(reservation.reservationId()))
                    + " state=" + data.snapshot(reservation.reservationId()).lifecycleState());
        } finally { mob.discard(); }
    }
}
