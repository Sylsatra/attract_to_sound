package com.example.soundattract.ai;

import com.example.soundattract.config.separate.LivingWallConfig;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("soundattract_bridge_canopy")
@PrefixGameTestTemplate(false)
public final class LivingBridgeCanopyRecoveryGameTests {
    private static final String CONTROL = "livingBridgeControl";
    private static final String MARKER = "soundattract_living_bridge";
    private static final int TIMEOUT = 225;
    private static final double MAX_STEP = 0.2D + Math.sqrt(3.0D) / 4096.0D;

    @GameTest(template = "empty", setupTicks = 20, timeoutTicks = TIMEOUT)
    public void floatingOwnerDetoursAroundBlockedLandingColumn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LivingBridgeRouteData data = LivingBridgeRouteData.get(level);
        boolean previouslyEnabled = LivingWallConfig.ENABLE_LIVING_WALL.get();
        LivingWallConfig.ENABLE_LIVING_WALL.set(true);
        Mob mob = null;
        try {
            BlockPos localFeet = new BlockPos(10, 3, 10);
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    helper.setBlock(localFeet.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                    for (int y = 0; y <= 18; y++) {
                        helper.setBlock(localFeet.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }
            helper.setBlock(localFeet.above(8), Blocks.SPRUCE_LEAVES.defaultBlockState()
                    .setValue(LeavesBlock.PERSISTENT, true));
            BlockPos feet = helper.absolutePos(localFeet);
            Vec3 landing = Vec3.atBottomCenterOf(feet);
            Vec3 origin = landing.add(0.0D, 14.0D, 0.0D);
            mob = EntityType.ZOMBIE.create(level);
            helper.assertTrue(mob != null, "canopy recovery zombie could not be created");
            mob.setPos(landing.x, landing.y, landing.z);
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            UUID formation = UUID.randomUUID();
            LivingBridgeRouteSnapshot claim = new LivingBridgeRouteSnapshot(UUID.randomUUID(), mob.getUUID(),
                    formation, level.dimension().location().toString(), 0, UUID.randomUUID(), 1,
                    "SAFE_STAGING_NAVIGATION", "WALKING_TO_STAGING", false, false, false,
                    List.of(point(origin), point(landing)), Math.round(mob.getBbWidth() * 2048.0D),
                    List.of(), 0, point(landing), mob.chunkPosition().x, mob.chunkPosition().z);
            helper.assertTrue(data.beginInitialClaim(claim, mob), "canopy recovery claim could not be persisted");
            CompoundTag marker = new CompoundTag();
            marker.putUUID("session", formation);
            marker.putBoolean("originalGravity", true);
            marker.putBoolean("originalNoPhysics", false);
            mob.getPersistentData().put(MARKER, marker);
            mob.setPos(origin.x, origin.y, origin.z);
            mob.setOnGround(true);
            mob.setDeltaMovement(0.0D, 0.13549D, 0.0D);
            mob.setNoGravity(true);
            mob.noPhysics = true;
            CompoundTag savedEntity = new CompoundTag();
            helper.assertTrue(mob.save(savedEntity), "canopy recovery zombie could not be serialized");
            Mob reloaded = (Mob) EntityType.loadEntityRecursive(savedEntity, level, java.util.function.Function.identity());
            helper.assertTrue(reloaded != null && reloaded.getDeltaMovement().y > 0.13D,
                    "canopy fixture must reload the saved upward velocity");
            mob = reloaded;
            helper.assertTrue(reloaded.isAlive() && !hasFootContact(level, reloaded)
                            && level.noCollision(reloaded, reloaded.getBoundingBox())
                            && level.getBlockState(feet.above(8)).is(Blocks.SPRUCE_LEAVES),
                    "reloaded canopy fixture must begin alive, unsupported, clear, and obstructed");
            level.addLegacyChunkEntities(Stream.of(reloaded));
            reloaded.goalSelector.removeAllGoals(goal -> true);
            reloaded.targetSelector.removeAllGoals(goal -> true);
            helper.assertTrue(reloaded.getDeltaMovement().equals(Vec3.ZERO),
                    "join recovery must clear saved upward motion before the first tick");
            awaitRecovery(helper, data, reloaded, claim, origin, feet, reloaded.position(), previouslyEnabled, 1);
        } catch (RuntimeException | AssertionError failure) {
            LivingWallConfig.ENABLE_LIVING_WALL.set(previouslyEnabled);
            if (mob != null && !mob.isRemoved()) mob.discard();
            throw failure;
        }
    }

    private static void awaitRecovery(GameTestHelper helper, LivingBridgeRouteData data, Mob mob,
                                     LivingBridgeRouteSnapshot claim, Vec3 origin, BlockPos originalFeet,
                                     Vec3 previousPosition, boolean previouslyEnabled, int elapsedTicks) {
        helper.runAfterDelay(1, () -> {
            try {
                LivingBridgeRouteSnapshot state = assertCopies(helper, data, mob, claim);
                Vec3 position = mob.position();
                helper.assertTrue(previousPosition.distanceTo(position) <= MAX_STEP,
                        "canopy recovery moved farther than one journaled step in a tick");
                if (LivingBridgeRouteData.isTerminalLaneState(state.lifecycleState())) {
                    ServerLevel level = helper.getLevel();
                    BlockPos endpoint = state.safeEndpoint() == null ? null
                            : BlockPos.containing(state.safeEndpoint().x() / 4096.0D,
                            state.safeEndpoint().y() / 4096.0D, state.safeEndpoint().z() / 4096.0D);
                    boolean supported = hasFootContact(level, mob);
                    boolean collisionFree = level.noCollision(mob, mob.getBoundingBox());
                    boolean normalPhysics = !mob.isNoGravity() && !mob.noPhysics;
                    boolean adjacent = endpoint != null && !endpoint.equals(originalFeet)
                            && mob.blockPosition().equals(endpoint)
                            && Math.abs(position.x - origin.x) + Math.abs(position.z - origin.z) >= 0.99D;
                    helper.assertTrue("SAFE_RECOVERY_COMPLETE".equals(state.lifecycleState()) && supported
                                    && collisionFree && normalPhysics && !state.recoveryRequired() && adjacent,
                            "canopy terminal mismatch: pos=" + position + " endpoint=" + endpoint
                                    + " blockPos=" + mob.blockPosition() + " originalFeet=" + originalFeet
                                    + " supported=" + supported + " collisionFree=" + collisionFree
                                    + " normalPhysics=" + normalPhysics + " recoveryRequired=" + state.recoveryRequired()
                                    + " state=" + state.lifecycleState());
                    mob.discard();
                    LivingWallConfig.ENABLE_LIVING_WALL.set(previouslyEnabled);
                    helper.succeed();
                } else if (elapsedTicks >= TIMEOUT) {
                    throw new AssertionError("canopy recovery never found a clear adjacent landing at " + position);
                } else {
                    helper.assertTrue(mob.isNoGravity() && mob.noPhysics,
                            "canopy owner lost protected physics before reaching verified support");
                    if ("ORPHAN_RECOVERY_TO_SAFE_ENDPOINT".equals(state.movementMode())) {
                        List<LivingBridgeRouteSnapshot.Point> route = state.recoveryRoute();
                        helper.assertTrue(route.size() > 1
                                        && (route.get(0).x() != route.get(1).x()
                                        || route.get(0).z() != route.get(1).z())
                                        && route.get(0).y() == route.get(1).y()
                                        && helper.getLevel().getBlockState(originalFeet.above(8)).is(Blocks.SPRUCE_LEAVES),
                                "canopy detour was not journaled horizontally above the still-present foliage");
                    }
                    awaitRecovery(helper, data, mob, claim, origin, originalFeet, position,
                            previouslyEnabled, elapsedTicks + 1);
                }
            } catch (RuntimeException | AssertionError failure) {
                LivingWallConfig.ENABLE_LIVING_WALL.set(previouslyEnabled);
                if (!mob.isRemoved()) mob.discard();
                helper.fail(failure.getMessage());
            }
        });
    }

    private static LivingBridgeRouteSnapshot assertCopies(GameTestHelper helper, LivingBridgeRouteData data,
                                                           Mob mob, LivingBridgeRouteSnapshot claim) {
        LivingBridgeRouteSnapshot saved = data.snapshot(claim.reservationId());
        helper.assertTrue(mob.isAlive() && saved != null && saved.ownerId().equals(mob.getUUID()),
                "canopy recovery owner diverged from saved route data");
        CompoundTag root = mob.getPersistentData();
        if (!LivingBridgeRouteData.isTerminalLaneState(saved.lifecycleState()) || root.contains(CONTROL)) {
            CompoundTag capsule = root.getCompound(CONTROL);
            helper.assertTrue(capsule.getInt("schema") == LivingBridgeRouteData.CURRENT_SCHEMA
                            && saved.digest().equals(capsule.getString("snapshotDigest"))
                            && saved.exactlyEquals(LivingBridgeRouteSnapshot.fromTag(capsule.getCompound("snapshot"))),
                    "canopy recovery diverged between entity and saved route copies");
        }
        boolean journalMatches = data.save(new CompoundTag(), null).getList("transitionJournal", Tag.TAG_COMPOUND).stream()
                .map(CompoundTag.class::cast)
                .anyMatch(row -> row.hasUUID("reservationId") && claim.reservationId().equals(row.getUUID("reservationId"))
                        && row.hasUUID("transactionId") && saved.transactionId().equals(row.getUUID("transactionId"))
                        && (saved.commitGeneration() <= claim.commitGeneration() || row.getBoolean("hasFrom"))
                        && row.getLong("commitGeneration") == saved.commitGeneration()
                        && saved.digest().equals(row.getString("toDigest"))
                        && saved.exactlyEquals(LivingBridgeRouteSnapshot.fromTag(row.getCompound("to"))));
        helper.assertTrue(journalMatches, "canopy recovery diverged from the durable transition journal");
        return saved;
    }

    private static boolean hasFootContact(ServerLevel level, Mob mob) {
        return level.getBlockCollisions(mob, mob.getBoundingBox().move(0.0D, -0.01D, 0.0D)).iterator().hasNext();
    }

    private static LivingBridgeRouteSnapshot.Point point(Vec3 position) {
        return LivingBridgeRouteSnapshot.Point.from(position.x, position.y, position.z);
    }
}
