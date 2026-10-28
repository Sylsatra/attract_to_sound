package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.gametest.GameTestEntityTypes;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Terrain copied read-only from the reported world, origin (-8,-62,-60), on 2026-10-27. */
public final class LivingBridgeWallContactGameTests {
    @GameTest(structure = "soundattract:living_bridge_wall_regression", maxTicks = 1600)
    public void reportedWallBuildsUprightThenTiltsWithoutBlockPenetration(GameTestHelper helper) {
        runReportedWallScene(helper, false);
    }

    static void runReportedWallScene(GameTestHelper helper, boolean east) {
        runReportedWallScene(helper, east, east ? 8 : 6);
    }

    static void runReportedWallScene(GameTestHelper helper, boolean east, int spawnZ) {
        ServerLevel level = helper.getLevel();
        level.getServer().clockManager().setTotalTicks(
                level.dimensionType().defaultClock().orElseThrow(), 13000L);
        // GameTestHelper's mock player overrides isCreative(); use a tracked survival player.
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "bridge-wall-threat");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer threat = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, threat, cookie);
        threat.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        // Stand visibly on the outer lip, as a player can in the reported scene.
        // A fresh target hidden inside the shaft is correctly rejected by stealth targeting.
        Vec3 threatPos = helper.absoluteVec(new Vec3(east ? 13.95D : 8.05D, 7.0D, east ? 11.5D : 9.5D));
        threat.setPos(threatPos);
        List<Zombie> workers = new ArrayList<>();
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 5; column++) {
                Zombie worker = helper.spawn(GameTestEntityTypes.ZOMBIE,
                        (east ? 15 : 2) + column, 2, spawnZ + row);
                if (com.example.soundattract.pathfinding.HazardClassifier.classify(level, worker.blockPosition())
                        != com.example.soundattract.pathfinding.HazardClassifier.HazardType.SAFE
                        || level.getBlockCollisions(worker, worker.getBoundingBox()).iterator().hasNext()) {
                    helper.fail("Fixture worker did not spawn safely: " + worker.position());
                    return;
                }
                Vec3 direction = threat.getEyePosition().subtract(worker.getEyePosition());
                float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
                worker.setYRot(yaw);
                worker.setYHeadRot(yaw);
                worker.setXRot((float) -Math.toDegrees(Math.atan2(direction.y,
                        Math.sqrt(direction.x * direction.x + direction.z * direction.z))));
                worker.setTarget(threat);
                workers.add(worker);
            }
        }
        SoundAttractMod.LOGGER.info("[WallContactGameTest] fixture threat={} support={} firstWorker={} workerSupport={} target={} ai={} collision={}",
                threat.position(), level.getBlockState(threat.blockPosition().below()), workers.getFirst().position(),
                level.getBlockState(workers.getFirst().blockPosition().below()), workers.getFirst().getTarget(),
                !workers.getFirst().isNoAi(), level.getBlockCollisions(workers.getFirst(), workers.getFirst().getBoundingBox()).iterator().hasNext());
        if (workers.stream().filter(worker -> worker.getTarget() == threat).count() < 3) {
            helper.fail("Fixture did not establish a visible, eligible player threat: acceptedTargets="
                    + workers.stream().filter(worker -> worker.getTarget() == threat).count()
                    + ", creative=" + threat.isCreative() + ", spectator=" + threat.isSpectator());
            return;
        }
        LivingWallCoordinator.clearBasinStateForTest(level);
        Observation observation = new Observation();
        observation.threat = threat;
        watch(helper, workers, observation, 0);
    }

    private static final class Observation {
        UUID bridge;
        boolean upright;
        int rotationTicks;
        int anchoredTicks;
        int traversalTicks;
        ServerPlayer threat;
        String lastPhase;
        String uprightViolation;
    }

    @SuppressWarnings("unchecked")
    private static void watch(GameTestHelper helper, List<Zombie> workers, Observation observation, int tick) {
        ServerLevel level = helper.getLevel();
        observation.threat.setHealth(observation.threat.getMaxHealth());
        try {
            Object selected = null;
            for (Object formation : LivingWallCoordinator.getOrCreateStateForTest(level).formations.values()) {
                List<UUID> members = (List<UUID>) field(formation, "members");
                if (observation.bridge != null
                        ? observation.bridge.equals(field(formation, "id"))
                        : members.stream().anyMatch(id -> workers.stream().anyMatch(mob -> mob.getUUID().equals(id)))) {
                    selected = formation;
                    break;
                }
            }
            if (selected != null) {
                observation.bridge = (UUID) field(selected, "id");
                String phase = field(selected, "phase").toString();
                if (!phase.equals(observation.lastPhase)) {
                    SoundAttractMod.LOGGER.info("[WallContactGameTest] tick={} bridge={} phase={} assembled={} required={}",
                            tick, observation.bridge, phase, field(selected, "assembledSlots"), field(selected, "requiredBodies"));
                    observation.lastPhase = phase;
                }
                List<UUID> members = (List<UUID>) field(selected, "members");
                int assembled = (int) field(selected, "assembledSlots");
                if (phase.equals("SETTLE")) {
                    int columns = (int) field(selected, "columnCount");
                    for (int slot = columns; slot < assembled; slot++) {
                        Mob foundation = (Mob) level.getEntity(members.get(slot % columns));
                        Mob member = (Mob) level.getEntity(members.get(slot));
                        if (foundation == null || member == null
                                || foundation.getBoundingBox().maxX <= member.getBoundingBox().minX
                                || member.getBoundingBox().maxX <= foundation.getBoundingBox().minX
                                || foundation.getBoundingBox().maxZ <= member.getBoundingBox().minZ
                                || member.getBoundingBox().maxZ <= foundation.getBoundingBox().minZ
                                || member.getY() <= foundation.getY()) {
                            if (observation.uprightViolation == null) {
                                observation.uprightViolation = "slot=" + slot + ", foundation="
                                        + (foundation == null ? null : foundation.position()) + ", member="
                                        + (member == null ? null : member.position()) + ", spacing="
                                        + field(selected, "pillarStep") + ", foundationNoPhysics="
                                        + (foundation != null && foundation.noPhysics) + ", foundationNoGravity="
                                        + (foundation != null && foundation.isNoGravity()) + ", foundationVelocity="
                                        + (foundation == null ? null : foundation.getDeltaMovement());
                                SoundAttractMod.LOGGER.info("[WallContactGameTest] upright_violation {}",
                                        observation.uprightViolation);
                            }
                        }
                        if (member != null && slot >= columns) {
                            Mob below = (Mob) level.getEntity(members.get(slot - columns));
                            if (below != null && Math.abs(member.getY() - below.getY() - 1.90D) > 0.15D) {
                                observation.uprightViolation = "Wrong upright spacing: slot=" + slot
                                        + ", below=" + below.position() + ", member=" + member.position();
                            }
                        }
                    }
                    observation.upright = assembled == (int) field(selected, "requiredBodies");
                }
                if (phase.equals("ROTATE") || phase.equals("ANCHOR") || phase.equals("TRAVERSE")) {
                    for (int slot = 0; slot < members.size(); slot++) {
                        Mob member = (Mob) level.getEntity(members.get(slot));
                        if (member == null || !member.isAlive()
                                || level.getBlockCollisions(member, member.getBoundingBox()).iterator().hasNext()) {
                            helper.fail("Structural member penetrated a block: tick=" + tick + ", phase=" + phase
                                    + ", slot=" + slot + ", box=" + (member == null ? null : member.getBoundingBox()));
                            return;
                        }
                        int columns = (int) field(selected, "columnCount");
                        if (slot >= columns) {
                            Mob below = (Mob) level.getEntity(members.get(slot - columns));
                            // Preserve the older live WALL_LEDGE member-contact range.
                            double reach = Math.max(3.0D, (double) field(selected, "pillarStep") + 1.0D);
                            if (slot == columns && !((List<?>) field(selected, "elbowPoints")).isEmpty()) reach = 5.0D;
                            if (below == null || member.position().distanceToSqr(below.position()) > reach * reach + 1.0E-6D) {
                                helper.fail("Tilt disconnected structural neighbors: tick=" + tick + ", phase=" + phase
                                        + ", slot=" + slot + ", box=" + member.getBoundingBox()
                                        + ", below=" + (below == null ? null : below.getBoundingBox()));
                                return;
                            }
                        }
                    }
                    if (phase.equals("ROTATE")) observation.rotationTicks++;
                    else observation.anchoredTicks++;
                    if (phase.equals("TRAVERSE")) observation.traversalTicks++;
                    if (observation.anchoredTicks >= 50 && observation.traversalTicks >= 30) {
                        if (!observation.upright || observation.uprightViolation != null || observation.rotationTicks < 2) {
                            helper.fail("Bridge skipped upright construction or observed tilt: upright="
                                    + observation.upright + ", rotationTicks=" + observation.rotationTicks
                                    + ", uprightViolation=" + observation.uprightViolation);
                            return;
                        }
                        SoundAttractMod.LOGGER.info("[WallContactGameTest] completed upright={} rotationTicks={} anchorTicks={} traversalTicks={}",
                                observation.upright, observation.rotationTicks, observation.anchoredTicks, observation.traversalTicks);
                        helper.succeed();
                        return;
                    }
                }
            } else if (observation.bridge != null) {
                helper.fail("Reported wall bridge disappeared before anchored verification: lastPhase="
                        + observation.lastPhase + ", tick=" + tick);
                return;
            }
            if (tick >= 200 && observation.bridge == null || tick >= 1500) {
                helper.fail("Reported wall bridge did not complete: tick=" + tick + ", bridge="
                        + observation.bridge + ", lastPhase=" + observation.lastPhase
                        + ", workers=" + workers.stream().map(mob -> mob.blockPosition() + "/target="
                        + (mob.getTarget() == null ? null : mob.getTarget().blockPosition())).toList());
                return;
            }
            helper.runAfterDelay(1, () -> watch(helper, workers, observation, tick + 1));
        } catch (ReflectiveOperationException e) {
            helper.fail("Could not observe actual Living Bridge lifecycle: " + e);
        }
    }

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
}
