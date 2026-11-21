package com.example.soundattract.event;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.network.AwarenessSnapshotMessage;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder("soundattract_awareness")
public final class AwarenessServerGameTests {
    @GameTest(template = "empty")
    public void preservesAlertOrderCapAndLivePositions(GameTestHelper helper) {
        var level = helper.getLevel();
        Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 81, 3)));
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "awareness"));
        player.setPos(origin.x, origin.y, origin.z);
        var config = SoundAttractConfig.COMMON;
        boolean previousStealth = config.enableStealthMechanics.get();
        double previousRange = config.maxStealthDetectionRange.get();
        List<Mob> mobs = new ArrayList<>();

        try {
            config.enableStealthMechanics.set(false);
            config.maxStealthDetectionRange.set(8.0);
            for (int i = 0; i < 130; i++) {
                var mob = EntityType.ZOMBIE.create(level);
                helper.assertTrue(mob != null, "test zombie must be created");
                mob.setPos(origin.x, origin.y, origin.z + 1.0);
                mob.setNoAi(true);
                mob.setNoGravity(true);
                helper.assertTrue(level.addFreshEntity(mob), "test zombie must join the world");
                mobs.add(mob);
            }

            level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48.0), Mob::isAlive)
                    .stream().filter(mob -> !mobs.contains(mob)).toList().forEach(Mob::discard);
            List<Mob> ordered = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48.0), Mob::isAlive);
            helper.assertTrue(ordered.size() == 130 && ordered.containsAll(mobs),
                    "the player must query exactly the 130 test mobs");
            Mob nearMiss = ordered.get(0);
            AwarenessServerEvents.recordNearMiss(nearMiss);
            var initial = AwarenessServerEvents.scanAwareness(level, player);
            helper.assertTrue(initial.maximumDetectionRange() == 8.0,
                    "the visibility maximum must include live mobs inside detection range");
            assertAlerts(helper, initial.alerts(), ordered.subList(0, 128));
            helper.assertTrue(initial.alerts().get(0).type() == 3 && initial.alerts().get(1).type() == 2,
                    "a near miss must take precedence over current detection");

            nearMiss.setPos(origin.x, origin.y + 50.0, origin.z);
            var updated = AwarenessServerEvents.scanAwareness(level, player);
            assertAlerts(helper, updated.alerts(), ordered.subList(1, 129));
        } finally {
            config.enableStealthMechanics.set(previousStealth);
            config.maxStealthDetectionRange.set(previousRange);
            mobs.forEach(Mob::discard);
        }
        helper.succeed();
    }

    private static void assertAlerts(GameTestHelper helper, List<AwarenessSnapshotMessage.Alert> alerts,
                                     List<Mob> expected) {
        helper.assertTrue(alerts.size() == expected.size(), "the snapshot must contain the first 128 alerts");
        for (int i = 0; i < expected.size(); i++) {
            Mob mob = expected.get(i);
            AwarenessSnapshotMessage.Alert alert = alerts.get(i);
            helper.assertTrue(alert.mobId().equals(mob.getUUID()) && alert.entityId() == mob.getId(),
                    "the alert must preserve entity query order and identity at index " + i);
        }
    }
}
