package com.example.soundattract.gametest;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.ai.LivingWallCoordinator;
import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.ai.FollowScentGoal;
import com.example.soundattract.camo.CamoLayer;
import com.example.soundattract.camo.CamouflageCapability;
import com.example.soundattract.client.AlertAnimationFrames;
import com.example.soundattract.client.AwarenessClientState;
import com.example.soundattract.event.ArrowInvestigationEvents;
import com.example.soundattract.event.FloorCreekEvents;
import com.example.soundattract.event.WindScentEvents;
import com.example.soundattract.util.ArrowInvestigationHelper;
import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.network.AwarenessSnapshotMessage;
import com.example.soundattract.pathfinding.HazardClassifier;
import com.example.soundattract.pathfinding.FlowFieldManager;
import com.example.soundattract.pathfinding.NodeRouter;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import com.example.soundattract.scents.ScentSourceType;
import com.example.soundattract.tracking.SoundTracker;
import com.example.soundattract.enchantment.ModEnchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.List;
import java.util.Locale;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.UUID;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;


@EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public final class CoreMechanicsGameTests {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface CoreTest { int timeoutTicks(); String template() default "core_mechanics"; }
    private static final String TEMPLATE = "core_mechanics";

    @SuppressWarnings("unchecked")
    private static EntityType<Mob> zombieType() {
        return (EntityType<Mob>) (EntityType<?>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getValue(Identifier.withDefaultNamespace("zombie"));
    }

    @CoreTest(timeoutTicks = 40, template = "pillar_diagonal")
    public static void livingBridgeCompletedPillarSurvivesMissingPlanner(GameTestHelper helper) {
        verifyRecentPillar(helper, true);
    }

    @CoreTest(timeoutTicks = 40, template = "pillar_straight")
    public static void livingBridgeCompletedPillarReachesLanding(GameTestHelper helper) {
        verifyRecentPillar(helper, false);
    }

    @CoreTest(timeoutTicks = 80, template = "living_bridge_live_slice")
    public static void livingBridgeAutomaticAdmissionKeepsSelectedEvidence(GameTestHelper helper) {
        List<Mob> members = new java.util.ArrayList<>();
        for (BlockPos position : List.of(new BlockPos(19, 123, 25), new BlockPos(20, 123, 25),
                new BlockPos(21, 123, 25), new BlockPos(19, 123, 26), new BlockPos(20, 123, 26),
                new BlockPos(21, 123, 26))) {
            Mob member = helper.spawnWithNoFreeWill(zombieType(), position);
            member.setInvulnerable(true);
            members.add(member);
        }
        helper.runAfterDelay(3, () -> {
            try {
                BlockPos threat = helper.absolutePos(new BlockPos(6, 125, 26));
                List<BlockPos> targets = LivingWallCoordinator.findClusterTargetsForTest(helper.getLevel(), members, threat);
                if (targets.isEmpty()) {
                    helper.fail("Automatic admission fixture produced no target anchors members="
                            + members.stream().map(member -> member.blockPosition() + ":"
                            + helper.getLevel().getBlockState(member.blockPosition().below())).toList()
                            + " threat=" + threat);
                    return;
                }
                for (BlockPos anchor : targets) {
                    if (LivingWallCoordinator.findClusterTargetEvidenceForTest(helper.getLevel(), members, threat, anchor) == null) {
                        helper.fail("Selected target evidence was lost before admission: " + anchor);
                        return;
                    }
                }
                helper.succeed();
            } finally {
                members.forEach(Mob::discard);
            }
        });
    }

    private static void verifyRecentPillar(GameTestHelper helper, boolean diagonal) {
        BlockPos pivot = diagonal ? new BlockPos(6, 2, 4) : new BlockPos(4, 2, 4);
        BlockPos anchor = diagonal ? new BlockPos(2, 6, 8) : new BlockPos(4, 6, 8);
        BlockPos base = diagonal ? new BlockPos(6, 2, 2) : new BlockPos(4, 2, 3);
        java.util.ArrayList<Mob> members = new java.util.ArrayList<>();
        Mob planner = helper.spawnWithNoFreeWill(zombieType(), pivot);
        try {
            for (int i = 0; i < (diagonal ? 11 : 8); i++) {
                Mob mob = helper.spawnWithNoFreeWill(zombieType(), base);
                mob.setInvulnerable(true);
                members.add(mob);
            }
            try (var session = new LivingWallCoordinator.DeploymentTestSession(helper.getLevel(), members,
                    helper.absolutePos(pivot), helper.absolutePos(anchor), planner)) {
                session.commitAssembledFoundation(Vec3.atBottomCenterOf(helper.absolutePos(base)));
                if (diagonal) planner.discard();
                int required = session.requiredBodiesForTest();
                if (required != (diagonal ? 14 : 10)) {
                    helper.fail("Committed foundation did not resize the actual span: " + required);
                    return;
                }
                while (members.size() < required) {
                    Mob worker = helper.spawnWithNoFreeWill(zombieType(), base);
                    worker.setInvulnerable(true);
                    members.add(worker);
                    session.addAssemblyWorkerForTest(worker);
                    int assembled = 0;
                    for (int step = 0; step < 400; step++) {
                        assembled = session.assembleStep();
                        if (assembled >= members.size()) break;
                    }
                    if (assembled < members.size()) {
                        helper.fail("Extra worker did not finish real assembly");
                        return;
                    }
                }
                for (Mob member : members) {
                    if (helper.getLevel().getBlockCollisions(member, member.getBoundingBox().deflate(1.0E-7)).iterator().hasNext()) {
                        helper.fail("Logged source pillar fixture intersects terrain: " + member.position());
                        return;
                    }
                }
                String rejection = session.assemblyClearanceForTest();
                if (rejection != null) {
                    helper.fail("Completed logged pillar rejected: " + rejection);
                    return;
                }
                Vec3 foundation = members.getFirst().position();
                List<Vec3> previous = members.stream().map(Mob::position).toList();
                boolean settled = false;
                boolean rotated = false;
                boolean anchored = false;
                for (int step = 0; step < 600; step++) {
                    session.step();
                    settled |= session.phase().equals("SETTLE");
                    rotated |= session.phase().equals("ROTATE");
                    anchored |= session.phase().equals("ANCHOR");
                    if (session.phase().startsWith("RELEASED")) {
                        helper.fail("Logged pillar released: " + session.phase());
                        return;
                    }
                    if (!foundation.equals(members.getFirst().position())) {
                        helper.fail("Foundation moved");
                        return;
                    }
                    for (int slot = 0; slot < members.size(); slot++) {
                        Mob member = members.get(slot);
                        if (member.position().distanceTo(previous.get(slot)) > 0.251
                                || helper.getLevel().getBlockCollisions(member, member.getBoundingBox().deflate(1.0E-7)).iterator().hasNext()
                                || (slot > 0 && !member.getBoundingBox().inflate(0.081).intersects(members.get(slot - 1).getBoundingBox()))) {
                            helper.fail("Logged deployment jumped, penetrated or disconnected slot=" + slot + " phase=" + session.phase());
                            return;
                        }
                    }
                    previous = members.stream().map(Mob::position).toList();
                    if (session.phase().equals("TRAVERSE")) {
                        if (!settled || !rotated || !anchored
                                || members.getLast().position().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(anchor))) > 0.01) {
                            helper.fail("Logged pillar skipped deployment or missed landing");
                            return;
                        }
                        helper.succeed();
                        return;
                    }
                }
                helper.fail("Logged pillar did not deploy: " + session.phase());
            }
        } finally {
            members.forEach(Mob::discard);
            planner.discard();
        }
    }

    @SuppressWarnings("unchecked")
    private static EntityType<AbstractArrow> arrowType() {
        return (EntityType<AbstractArrow>) (EntityType<?>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getValue(Identifier.withDefaultNamespace("arrow"));
    }

    private static long countBehavior(Object value, String className) {
        if (value == null) {
            return 0;
        }
        if (value.getClass().getName().equals(className)) {
            return 1;
        }
        if (value instanceof Iterable<?> iterable) {
            long count = 0;
            for (Object element : iterable) {
                count += countBehavior(element, className);
            }
            return count;
        }
        return 0;
    }

    private static void describeBehaviorTree(Object value, List<String> description) {
        if (value == null) {
            return;
        }
        description.add(value.getClass().getName());
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                describeBehaviorTree(element, description);
            }
        }
    }

    private static final class SmartBrainProbeZombie extends net.minecraft.world.entity.monster.zombie.Zombie
            implements net.tslat.smartbrainlib.api.SmartBrainOwner<SmartBrainProbeZombie> {
        private SmartBrainProbeZombie(net.minecraft.world.level.Level level) {
            super((EntityType<? extends net.minecraft.world.entity.monster.zombie.Zombie>)
                    (EntityType<?>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                            .getValue(Identifier.withDefaultNamespace("zombie")), level);
        }

        @Override
        public List<? extends net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor<SmartBrainProbeZombie>>
                getSensors(SmartBrainProbeZombie entity) {
            return List.of();
        }
    }

    private CoreMechanicsGameTests() {}

    

    @CoreTest(timeoutTicks = 20)
    public static void awarenessSnapshotIsRegisteredClientbound(GameTestHelper helper) {
        var codec = net.neoforged.neoforge.network.registration.NetworkRegistry.getCodec(
                com.example.soundattract.network.AwarenessSnapshotMessage.TYPE.id(),
                net.minecraft.network.ConnectionProtocol.PLAY,
                net.minecraft.network.protocol.PacketFlow.CLIENTBOUND);
        if (codec == null) {
            helper.fail("Awareness snapshot payload is not registered for clientbound play traffic");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 20)
    public static void arrowNearMissSweepCoversSkippedProjectileTicks(GameTestHelper helper) {
        if (!ArrowInvestigationHelper.shouldScanProjectileSegment(false, true)
                || ArrowInvestigationHelper.shouldScanProjectileSegment(false, false)
                || !ArrowInvestigationHelper.shouldScanProjectileSegment(true, false)) {
            helper.fail("An arrow segment near an eligible mob must be scanned even when far from players");
            return;
        }
        Vec3 lastSample = new Vec3(0.0, 64.0, 0.0);
        Vec3 current = new Vec3(12.0, 64.0, 0.0);
        Vec3 mobCenter = new Vec3(6.0, 66.9, 0.0);
        double radiusSquared = 3.0 * 3.0;
        double fullSweepDistance = ArrowInvestigationHelper.segmentToPointSquaredDistance(lastSample, current, mobCenter);
        double incorrectlyShortenedDistance = ArrowInvestigationHelper.segmentToPointSquaredDistance(
                new Vec3(9.0, 64.0, 0.0), current, mobCenter);
        if (fullSweepDistance > radiusSquared || incorrectlyShortenedDistance <= radiusSquared) {
            helper.fail("The regression geometry must intersect only when the last sampled flight segment is retained");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 100)
    public static void trackedProjectileNearMissFindsSoundPursuerWithoutNearbyPlayer(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 8, z), Blocks.STONE);
            }
        }

        Mob zombie = helper.spawnWithNoFreeWill(zombieType(), new BlockPos(2, 9, 2));
        zombie.goalSelector.addGoal(0, new AttractionGoal(zombie, 1.0));
        AbstractArrow arrow = null;
        long now = helper.getLevel().getGameTime();
        for (int attempt = 0; attempt < 4; attempt++) {
            AbstractArrow candidate = (AbstractArrow) arrowType().create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
            if (candidate == null) break;
            candidate.setNoGravity(true);
            BlockPos arrowStart = helper.absolutePos(new BlockPos(0, 10, 4));
            candidate.setPos(arrowStart.getX() + 0.5, arrowStart.getY(), arrowStart.getZ() + 0.5);
            candidate.setDeltaMovement(1.0, 0.0, 0.0);
            boolean added = helper.getLevel().addFreshEntity(candidate);
            if (added && !ArrowInvestigationEvents.ARROWS.containsKey(candidate.getId())) {
                new ArrowInvestigationEvents().onEntityJoin(
                        new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(candidate, helper.getLevel()));
            }
            if (!added || !ArrowInvestigationEvents.ARROWS.containsKey(candidate.getId())) {
                helper.fail("The watched projectile was not registered: added=" + added
                        + ", tagged=" + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .wrapAsHolder(candidate.getType()).is(ArrowInvestigationEvents.INVESTIGATE_PROJECTILES));
                return;
            }
            if ((candidate.getId() + (int) now) % 4 == 0) {
                arrow = candidate;
                break;
            }
            candidate.discard();
            ArrowInvestigationEvents.ARROWS.remove(candidate.getId());
        }
        if (arrow == null) {
            helper.fail("Could not align the tracked arrow with the server's four-tick sweep interval");
            return;
        }
        BlockPos arrowEnd = helper.absolutePos(new BlockPos(4, 10, 4));
        arrow.setPos(arrowEnd.getX() + 0.5, arrowEnd.getY(), arrowEnd.getZ() + 0.5);
        new ArrowInvestigationEvents().onServerTick(new LevelTickEvent.Post(() -> true, helper.getLevel()));
        ArrowInvestigationEvents.ArrowState state = ArrowInvestigationEvents.ARROWS.get(arrow.getId());
        if (state != null && state.notifiedMobIds.contains(zombie.getUUID())) {
            String dimension = helper.getLevel().dimension().identifier().toString();
            boolean investigationSoundAdded = SoundTracker.getNearbySounds(helper.getLevel(), dimension,
                            zombie.blockPosition(), null).stream()
                    .anyMatch(record -> record instanceof SoundTracker.VirtualSoundRecord virtual
                            && "arrow_investigation".equals(virtual.animationClass));
            arrow.discard();
            if (!investigationSoundAdded) {
                helper.fail("The near-miss sweep found the pursuer but did not dispatch its investigation sound");
                return;
            }
            helper.succeed();
            return;
        }
        arrow.discard();
        helper.fail("The scheduled sweep did not alert the pursuer or dispatch its investigation sound");
    }



    

    

    

    

    

    

    

    

    @CoreTest(timeoutTicks = 100)
    public static void zombieAttractionGoalAcquiresTrackedSound(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 8, z), Blocks.STONE);
            }
        }

        Mob zombie = helper.spawnWithNoFreeWill(zombieType(), new BlockPos(2, 9, 2));
        BlockPos source = helper.absolutePos(new BlockPos(7, 9, 2));
        String dimension = helper.getLevel().dimension().identifier().toString();
        String soundId = "soundattract:arrow_miss#gametest_" + source.asLong();

        try {
            SoundTracker.addSound(null, source, dimension, 32.0, 8.0, 200, soundId);
            AttractionGoal goal = new AttractionGoal(zombie, 1.0);
            if (!goal.canUse()) {
                helper.fail("An eligible zombie did not acquire the nearby tracked sound: sounds="
                        + SoundTracker.getNearbySounds(helper.getLevel(), dimension, zombie.blockPosition(), null).stream()
                        .map(record -> record.soundId + "@" + record.pos + ":" + record.range + ":" + record.weight).toList()
                        + ", attracted=" + com.example.soundattract.event.SoundAttractionEvents.getCachedAttractedEntityTypes().contains(zombie.getType())
                        + ", pos=" + zombie.blockPosition() + ", source=" + source);
                return;
            }
            if (!source.equals(goal.getTargetSoundPos())) {
                helper.fail("Attraction goal selected " + goal.getTargetSoundPos() + " instead of " + source);
                return;
            }

            goal.tick();
            if (!source.equals(goal.getTargetSoundPos())) {
                helper.fail("Attraction goal discarded its tracked sound while updating pursuit");
                return;
            }
            helper.succeed();
        } finally {
            SoundTracker.removeSoundAt(source, dimension);
        }
    }

    

    

    @CoreTest(timeoutTicks = 100)
    public static void trackedSoundExpiresAfterItsLifetime(GameTestHelper helper) {
        BlockPos source = helper.absolutePos(new BlockPos(3, 9, 3));
        String dimension = helper.getLevel().dimension().identifier().toString();
        String soundId = "soundattract:arrow_miss#expiry_" + source.asLong();

        SoundTracker.addSound(null, source, dimension, 24.0, 3.0, 1, soundId);
        if (SoundTracker.getNearbySounds(helper.getLevel(), dimension, source, null).stream()
                .noneMatch(record -> soundId.equals(record.soundId))) {
            helper.fail("Newly tracked sound was not available to nearby listeners");
            return;
        }
        SoundTracker.tick();
        if (SoundTracker.getNearbySounds(helper.getLevel(), dimension, source, null).stream()
                .anyMatch(record -> soundId.equals(record.soundId))) {
            helper.fail("Expired tracked sound remained in the spatial index");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 100)
    public static void stealthDetectionUsesTheSuppliedRange(GameTestHelper helper) {
        Mob zombie = helper.spawnWithNoFreeWill(zombieType(), new BlockPos(2, 9, 2));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos playerPos = helper.absolutePos(new BlockPos(5, 9, 2));
        player.snapTo(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5, 180.0f, 0.0f);

        if (!StealthDetectionEvents.canMobDetectPlayerWithRange(zombie, player, 64.0)) {
            helper.fail("A nearby visible survival player was rejected inside the supplied detection range");
            return;
        }
        if (StealthDetectionEvents.canMobDetectPlayerWithRange(zombie, player, 0.25)) {
            helper.fail("A player outside the supplied detection range was accepted");
            return;
        }
        helper.succeed();
    }

    

    

    @CoreTest(timeoutTicks = 100)
    public static void woolOcclusionReducesSoundReach(GameTestHelper helper) {
        BlockPos source = helper.absolutePos(new BlockPos(2, 10, 2));
        BlockPos listener = helper.absolutePos(new BlockPos(7, 10, 2));
        helper.setBlock(new BlockPos(4, 10, 2), net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("white_wool")).defaultBlockState());

        net.minecraft.world.level.Level level = helper.getLevel();
        com.example.soundattract.los.OptimizedLOS.resetMufflingBudget();
        double[] muffled = SoundTracker.applyBlockMuffling(
                level, source, listener, 40.0, 10.0, "soundattract:gametest/wool_wall", false);
        if (!(muffled[0] < 40.0 || muffled[1] < 10.0)) {
            helper.fail("A wool wall did not reduce the tracked sound's range or weight");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 100)
    public static void scentNodesPersistAndWindReplacementIsOwnerScoped(GameTestHelper helper) {
        ScentManager manager = new ScentManager(helper.getLevel());
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        long now = helper.getLevel().getGameTime();
        ScentNode ordinary = new ScentNode(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 9, 2))), now, 0.7f, ownerA,
                ScentSourceType.PLAYER_WALK);
        ScentNode windA = new ScentNode(Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 9, 2))), now, 0.9f, ownerA,
                ScentSourceType.PLAYER_WIND);
        ScentNode windB = new ScentNode(Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 9, 2))), now, 0.8f, ownerB,
                ScentSourceType.PLAYER_WIND);
        manager.addScentNode(ordinary);
        manager.replaceWindNodes(ownerA, List.of(windA));
        manager.replaceWindNodes(ownerB, List.of(windB));

        manager.replaceWindNodes(ownerA, List.of(new ScentNode(Vec3.atCenterOf(helper.absolutePos(new BlockPos(6, 9, 2))), now, 1.0f,
                ownerA, ScentSourceType.PLAYER_WIND)));
        List<ScentNode> current = manager.getAllNodes();
        if (current.size() != 3 || current.stream().anyMatch(node -> node == windA)) {
            helper.fail("Replacing one owner's wind trail did not replace only that owner's prior nodes");
            return;
        }

        ScentManager restored = new ScentManager(helper.getLevel());
        restored.deserializeNBT(manager.serializeNBT());
        List<ScentNode> saved = restored.getAllNodes();
        if (saved.size() != current.size()
                || saved.stream().noneMatch(node -> node.getOwnerUUID().equals(ownerB)
                    && node.getSourceType() == ScentSourceType.PLAYER_WIND)
                || saved.stream().noneMatch(node -> node.getOwnerUUID().equals(ownerA)
                    && node.getSourceType() == ScentSourceType.PLAYER_WALK)) {
            helper.fail("Scent NBT round-trip lost nodes or their owner/source metadata");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 100)
    public static void eligibleZombieAcquiresNearbyScentTrail(GameTestHelper helper) {
        Mob zombie = helper.spawnWithNoFreeWill(zombieType(), new BlockPos(2, 9, 2));
        ScentManager manager = ScentManager.get(helper.getLevel()).orElseThrow();
        manager.clear();
        UUID owner = UUID.randomUUID();
        BlockPos trail = helper.absolutePos(new BlockPos(5, 9, 2));
        manager.addScentNode(new ScentNode(Vec3.atCenterOf(trail), helper.getLevel().getGameTime(), 1.0f,
                owner, ScentSourceType.PLAYER_WALK));

        FollowScentGoal goal = new FollowScentGoal((net.minecraft.world.entity.PathfinderMob) zombie);
        boolean acquired = false;
        for (int attempt = 0; attempt < 100 && !acquired; attempt++) {
            acquired = goal.canUse();
        }
        if (!acquired || !goal.isFollowingScent()
                || !Vec3.atCenterOf(trail).equals(goal.getTargetPosition())) {
            helper.fail("Eligible zombie did not acquire the nearby player's scent trail");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 100)
    public static void hazardClassifierRecognizesSafeLavaTrapdoorAndDrop(GameTestHelper helper) {
        HazardClassifier.clear();
        BlockPos safeFeet = helper.absolutePos(new BlockPos(1, 9, 1));
        helper.setBlock(new BlockPos(1, 8, 1), Blocks.STONE);
        if (HazardClassifier.classify(helper.getLevel(), safeFeet) != HazardClassifier.HazardType.SAFE) {
            helper.fail("A solid supported position was not classified as safe");
            return;
        }

        BlockPos lavaFeet = helper.absolutePos(new BlockPos(3, 9, 1));
        helper.setBlock(new BlockPos(3, 9, 1), Blocks.LAVA);
        if (HazardClassifier.classify(helper.getLevel(), lavaFeet) != HazardClassifier.HazardType.LAVA) {
            helper.fail("A lava position was not classified as lava");
            return;
        }

        BlockPos trapdoorFeet = helper.absolutePos(new BlockPos(5, 9, 1));
        helper.setBlock(new BlockPos(5, 9, 1), Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, true));
        if (HazardClassifier.classify(helper.getLevel(), trapdoorFeet)
                != HazardClassifier.HazardType.TRAPDOOR_DROP) {
            helper.fail("An open trapdoor over a drop was not classified as a trapdoor hazard");
            return;
        }

        BlockPos dropFeet = helper.absolutePos(new BlockPos(7, 9, 1));
        if (HazardClassifier.classify(helper.getLevel(), dropFeet) != HazardClassifier.HazardType.DEEP_DROP) {
            helper.fail("An unsupported three-block drop was not classified as dangerous");
            return;
        }
        Mob zombie = helper.spawnWithNoFreeWill(zombieType(), new BlockPos(2, 9, 5));
        if (NodeRouter.isWalkable(zombie, Vec3.atBottomCenterOf(lavaFeet))) {
            helper.fail("NodeRouter accepted lava as a walkable waypoint");
            return;
        }
        Vec3 finiteFallback = NodeRouter.getWaypoint(zombie, new Vec3(Double.NaN, 9.0, 5.0));
        if (!finiteFallback.equals(zombie.position())) {
            helper.fail("NodeRouter did not reject a non-finite waypoint");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 20)
    public static void toughAsNailsIntegrationIsEnabledWhenInstalled(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("toughasnails")
                || !com.example.soundattract.integration.toughasnails.ToughAsNailsIntegration.isEnabledAndLoaded()) {
            helper.fail("Installed ToughAsNails integration was not registered and enabled");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 40)
    public static void immersiveMelodiesPlayingInstrumentAddsTrackedSound(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("immersive_melodies")) {
            helper.succeed();
            return;
        }

        var enabled = com.example.soundattract.config.separate.IntegrationConfig.ENABLE_IMMERSIVE_MELODIES_INTEGRATION;
        boolean previousEnabled = enabled.get();
        var level = helper.getLevel();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "melodies_test"));
        level.addNewPlayer(player);
        BlockPos playerPos = helper.absolutePos(new BlockPos(2, 9, 2));
        player.snapTo(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5, 0.0f, 0.0f);
        playerPos = player.blockPosition();
        String dimension = level.dimension().identifier().toString();
        try {
            enabled.set(true);
            Class<?> instrumentItemClass = Class.forName("immersive_melodies.item.InstrumentItem");
            var playing = (net.minecraft.core.component.DataComponentType<Boolean>) instrumentItemClass
                    .getField("PLAYING").get(null);
            var melody = (net.minecraft.core.component.DataComponentType<Identifier>) instrumentItemClass
                    .getField("MELODY").get(null);
            var instrument = net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
                    .filter(instrumentItemClass::isInstance).findFirst().orElse(null);
            if (instrument == null) {
                helper.fail("Immersive Melodies loaded without registering any instrument items");
                return;
            }
            var stack = new net.minecraft.world.item.ItemStack(instrument);
            stack.set(playing, true);
            stack.set(melody, Identifier.fromNamespaceAndPath("immersive_melodies", "gametest"));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);

            Class<?> events = Class.forName("com.example.soundattract.integration.immersive_melodies.ImmersiveMelodiesEvents");
            var initialize = events.getDeclaredMethod("initializeReflection");
            initialize.setAccessible(true);
            initialize.invoke(null);
            var addMusicSound = events.getDeclaredMethod("checkAndAddMusicSound",
                    net.minecraft.server.level.ServerPlayer.class);
            addMusicSound.setAccessible(true);
            addMusicSound.invoke(null, player);

            boolean tracked = SoundTracker.getNearbySounds(level, dimension, playerPos, null).stream()
                    .anyMatch(record -> record.soundId != null && record.soundId.toLowerCase(java.util.Locale.ROOT)
                            .contains("immersivemelody"));
            if (!tracked) {
                helper.fail("Playing an Immersive Melodies instrument did not add a tracked sound: item="
                        + instrumentItemClass.isInstance(stack.getItem()) + ", playing=" + stack.getOrDefault(playing, false)
                        + ", melody=" + stack.get(melody) + ", enabled=" + enabled.get() + ", nearby="
                        + SoundTracker.getNearbySounds(level, dimension, playerPos, null).stream()
                        .map(record -> record.soundId + "@" + record.pos + ":" + record.dimensionKey).toList());
                return;
            }
            helper.succeed();
        } catch (ReflectiveOperationException | LinkageError e) {
            helper.fail("Immersive Melodies integration could not inspect its runtime API: " + e.getMessage());
        } finally {
            SoundTracker.removeSoundAt(playerPos, dimension);
            player.discard();
            enabled.set(previousEnabled);
        }
    }

    @CoreTest(timeoutTicks = 100)
    public static void quantifiedApiBridgeSubmitsWorkWhenInstalled(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("quantified")) {
            helper.succeed();
            return;
        }
        var enabled = com.example.soundattract.config.SoundAttractConfig.COMMON.enableQuantifiedIntegration;
        boolean previousEnabled = enabled.get();
        try {
            enabled.set(true);
            var future = com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge.trySubmitTask(
                    "soundattract", "gametest_probe", () -> 0x7B13, true, false,
                    java.time.Duration.ofSeconds(5), "gametest_probe");
            if (future == null || future.get(5, java.util.concurrent.TimeUnit.SECONDS) != 0x7B13) {
                helper.fail("Quantified API did not execute submitted SoundAttract work");
                return;
            }
            helper.succeed();
        } catch (Exception e) {
            helper.fail("Quantified API bridge failed its work probe: " + e.getMessage());
        } finally {
            enabled.set(previousEnabled);
        }
    }

    @CoreTest(timeoutTicks = 20)
    public static void taczGunShootAndReloadEventsAreTrackedWhenInstalled(GameTestHelper helper) {
        var mods = net.neoforged.fml.ModList.get();
        if (!mods.isLoaded("tacz")) {
            helper.succeed();
            return;
        }
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath("tacz", "modern_kinetic_gun"));
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            helper.fail("TaCZ did not register its modern kinetic gun probe item");
            return;
        }
        var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "soundattract-tacz-test"),
                net.minecraft.server.level.ClientInformation.createDefault());
        var gun = new net.minecraft.world.item.ItemStack(item);
        var enabled = com.example.soundattract.config.SoundAttractConfig.SERVER.enableTaczIntegration;
        boolean previousEnabled = enabled.get();
        try {
            enabled.set(true);
            long beforeShot = SoundTracker.getSoundSequence();
            com.tacz.guns.api.event.common.GunShootEvent.CALLBACK.invoker().post(
                    new com.tacz.guns.api.event.common.GunShootEvent(player, gun,
                            cn.sh1rocu.tacz.api.LogicalSide.SERVER));
            if (SoundTracker.getSoundSequence() <= beforeShot) {
                helper.fail("TaCZ gun shoot event did not add a tracked sound");
                return;
            }
            long beforeReload = SoundTracker.getSoundSequence();
            com.tacz.guns.api.event.common.GunReloadEvent.CALLBACK.invoker().post(
                    new com.tacz.guns.api.event.common.GunReloadEvent(player, gun,
                            cn.sh1rocu.tacz.api.LogicalSide.SERVER));
            if (SoundTracker.getSoundSequence() <= beforeReload) {
                helper.fail("TaCZ gun reload event did not add a tracked sound");
                return;
            }
        } finally {
            enabled.set(previousEnabled);
            player.discard();
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 20)
    public static void installedOptionalIntegrationApisResolve(GameTestHelper helper) {
        if (!com.example.soundattract.config.DebugLoggingConfig.SPEC.isLoaded()) {
            helper.fail("Debug logging config spec was not registered for the compatibility runtime");
            return;
        }
        if (!com.example.soundattract.config.SoundAttractConfig.commonConfigsReady()) {
            helper.fail("A common config spec was not registered and loaded before GameTests started");
            return;
        }
        if (com.example.soundattract.config.SoundAttractConfig.COMMON.configSchemaVersion.get() < 19) {
            helper.fail("Configuration schema migration did not reach the 7.0.2 schema version");
            return;
        }
        var mods = net.neoforged.fml.ModList.get();
        List<java.util.Map.Entry<String, String>> optionalApis = List.of(
                java.util.Map.entry("smartbrainlib", "net.tslat.smartbrainlib.api.SmartBrainOwner"),
                java.util.Map.entry("tacz", "com.tacz.guns.api.event.common.GunShootEvent"),
                java.util.Map.entry("voicechat", "de.maxhenkel.voicechat.api.VoicechatPlugin"),
                java.util.Map.entry("plasmovoice", "su.plo.voice.api.addon.AddonInitializer"),
                java.util.Map.entry("quantified", "org.admany.quantified.api.QuantifiedAPI"),
                java.util.Map.entry("geckolib", "com.geckolib.renderer.layer.GeoRenderLayer"),
                java.util.Map.entry("toughasnails", "toughasnails.api.temperature.TemperatureHelper"),
                java.util.Map.entry("immersive_melodies", "immersive_melodies.item.InstrumentItem"));
        for (var optionalApi : optionalApis) {
            if (!mods.isLoaded(optionalApi.getKey())) {
                continue;
            }
            try {
                Class.forName(optionalApi.getValue(), false, CoreMechanicsGameTests.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError error) {
                helper.fail("Loaded integration " + optionalApi.getKey() + " does not expose "
                        + optionalApi.getValue() + ": " + error.getMessage());
                return;
            }
        }
        if (mods.isLoaded("geckolib")) {
            try {
                Class.forName("com.example.soundattract.gecko.CamoGeoLayer", false,
                        CoreMechanicsGameTests.class.getClassLoader());
                Class.forName("com.example.soundattract.gecko.GeckoCamoLayerRegistration", false,
                        CoreMechanicsGameTests.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError error) {
                helper.fail("GeckoLib camouflage layer does not resolve against the installed renderer API: "
                        + error.getMessage());
                return;
            }
        }
        if (mods.isLoaded("smartbrainlib")) {
            try {
                Class<?> brainUtil = Class.forName("net.tslat.smartbrainlib.util.BrainUtil");
                Class<?> brain = Class.forName("net.minecraft.world.entity.ai.Brain");
                brainUtil.getMethod("getAllBehaviours", brain);
                brainUtil.getMethod("addMemories", brain,
                        net.minecraft.world.entity.ai.memory.MemoryModuleType[].class);
                brainUtil.getMethod("addSensor", brain,
                        net.minecraft.world.entity.ai.sensing.SensorType.class,
                        net.minecraft.world.entity.ai.sensing.Sensor.class,
                        net.minecraft.util.RandomSource.class);
                brainUtil.getMethod("addBehaviour", brain,
                        net.minecraft.world.entity.schedule.Activity.class,
                        int.class, net.minecraft.world.entity.ai.behavior.BehaviorControl.class);
            } catch (ReflectiveOperationException error) {
                helper.fail("SmartBrainLib 2.0 brain integration API does not match its installed signatures: "
                        + error.getMessage());
                return;
            }
            try {
                var probe = new SmartBrainProbeZombie(helper.getLevel());
                boolean isOwner = Class.forName("net.tslat.smartbrainlib.api.SmartBrainOwner").isInstance(probe);
                var preinstalledSoundBehavior = new com.example.soundattract.integration.sbl.MoveToSoundBehaviour<net.minecraft.world.entity.PathfinderMob>();
                Class<?> brainUtil = Class.forName("net.tslat.smartbrainlib.util.BrainUtil");
                brainUtil.getMethod("addBehaviour", Class.forName("net.minecraft.world.entity.ai.Brain"),
                        net.minecraft.world.entity.schedule.Activity.class, int.class,
                        net.minecraft.world.entity.ai.behavior.BehaviorControl.class)
                        .invoke(null, probe.getBrain(), net.minecraft.world.entity.schedule.Activity.IDLE,
                                3, preinstalledSoundBehavior);
                var initialBehaviors = ((java.util.stream.Stream<?>) brainUtil.getMethod("getAllBehaviours",
                        Class.forName("net.minecraft.world.entity.ai.Brain")).invoke(null, probe.getBrain())).toList();
                long initialSoundBehaviors = countBehavior(initialBehaviors,
                        "com.example.soundattract.integration.sbl.MoveToSoundBehaviour");
                if (initialSoundBehaviors != 1) {
                    helper.fail("SmartBrainLib behavior probe did not retain its preinstalled behavior: "
                            + initialSoundBehaviors);
                    probe.discard();
                    return;
                }
                boolean attachedFirst = com.example.soundattract.integration.smartbrainlib.SmartBrainLibCompat
                        .tryAttachSoundAttractBrain(probe);
                boolean attachedSecond = attachedFirst && com.example.soundattract.integration.smartbrainlib.SmartBrainLibCompat
                        .tryAttachSoundAttractBrain(probe);
                if (!isOwner || !attachedFirst || !attachedSecond) {
                    helper.fail("SmartBrainLib behavior attach result was unexpected: owner=" + isOwner
                            + ", first=" + attachedFirst + ", second=" + attachedSecond);
                    probe.discard();
                    return;
                }
                var behaviors = ((java.util.stream.Stream<?>) brainUtil.getMethod("getAllBehaviours",
                        Class.forName("net.minecraft.world.entity.ai.Brain"))
                        .invoke(null, probe.getBrain())).toList();
                long soundBehaviors = countBehavior(behaviors,
                        "com.example.soundattract.integration.sbl.MoveToSoundBehaviour");
                long scentBehaviors = countBehavior(behaviors,
                        "com.example.soundattract.integration.sbl.MoveToScentBehaviour");
                probe.discard();
                if (soundBehaviors != 1 || scentBehaviors != 1) {
                    var behaviorDescription = new java.util.ArrayList<String>();
                    describeBehaviorTree(behaviors, behaviorDescription);
                    helper.fail("SmartBrainLib attachment did not install exactly one sound and scent behavior: behaviors="
                            + behaviorDescription);
                    return;
                }
            } catch (ReflectiveOperationException | LinkageError error) {
                helper.fail("SmartBrainLib behavior attachment probe failed: " + error.getMessage());
                return;
            }
        }
        if (mods.isLoaded("spoiled")
                && com.example.soundattract.integration.spoiled.SpoiledScentIntegration.spoilageProgress(
                        net.minecraft.world.item.ItemStack.EMPTY) != 0.0) {
            helper.fail("Spoiled integration did not safely ignore an empty item stack");
            return;
        }
        var wind = com.example.soundattract.integration.weather2.Weather2WindCompat.sample(
                helper.getLevel(), helper.absolutePos(new BlockPos(3, 9, 3)), mods.isLoaded("weather2"));
        if (wind.direction() == null || !Double.isFinite(wind.speed()) || wind.speed() < 0.0
                || !Double.isFinite(wind.direction().x) || !Double.isFinite(wind.direction().z)) {
            helper.fail("Weather wind adapter returned an invalid sample");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 20)
    public static void playerScentVisibilityReadsLegacyAndWindProfiles(GameTestHelper helper) {
        var legacy = com.example.soundattract.config.ScentVisibilityConfig.CODEC.parse(
                com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"showPlayerWalk\":true,\"showArrowScent\":false}"))
                .result().orElseThrow();
        var wind = com.example.soundattract.config.ScentVisibilityConfig.fromStringList(
                List.of("showPlayerWalk=false", "showArrowScent=true", "showWindScent=true"));
        if (legacy.showWindScent() || wind.showPlayerWalk() || !wind.showArrowScent() || !wind.showWindScent()
                || !wind.toStringList().contains("showWindScent=true")) {
            helper.fail("Scent visibility profiles did not preserve legacy values or wind visibility");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 20)
    public static void sprintingPlayerEmitsWindScentTrail(GameTestHelper helper) {
        var enabled = com.example.soundattract.config.SoundAttractConfig.COMMON.enableWindScent;
        var interval = com.example.soundattract.config.SoundAttractConfig.COMMON.windScentUpdateIntervalTicks;
        var odorValues = com.example.soundattract.config.SoundAttractConfig.COMMON.playerOdorItemValues;
        boolean previousEnabled = enabled.get();
        int previousInterval = interval.get();
        List<? extends String> previousOdorValues = odorValues.get();
        try {
            enabled.set(true);
            interval.set(1);
            odorValues.set(List.of("minecraft:stone=20.0"));
            var level = helper.getLevel();
            var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "wind_scent_test"));
            level.addNewPlayer(player);
            BlockPos playerPos = helper.absolutePos(new BlockPos(2, 250, 2));
            player.snapTo(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5, 0.0f, 0.0f);
            player.setSprinting(true);
            player.setDeltaMovement(0.2, 0.0, 0.0);
            player.getInventory().add(new net.minecraft.world.item.ItemStack(Blocks.STONE));
            var manager = ScentManager.get(level).orElseThrow();
            manager.clear();

            var tickLevel = WindScentEvents.class.getDeclaredMethod("tickLevel", net.minecraft.server.level.ServerLevel.class);
            tickLevel.setAccessible(true);
            tickLevel.invoke(null, level);

            if (manager.getAllNodes().stream().noneMatch(node -> player.getUUID().equals(node.getOwnerUUID())
                    && node.getSourceType() == ScentSourceType.PLAYER_WIND)) {
                helper.fail("A sprinting player did not create a wind scent trail: listed=" + level.players().contains(player)
                        + ", sky=" + level.canSeeSky(player.blockPosition()) + ", scent="
                        + com.example.soundattract.event.AwarenessServerEvents.getEffectivePlayerScent(level, player)
                        + ", parcels=" + manager.getAllNodes().size());
                player.discard();
                return;
            }
            player.discard();
            helper.succeed();
        } catch (ReflectiveOperationException e) {
            helper.fail("Wind scent tick could not be invoked: " + e.getMessage());
        } finally {
            enabled.set(previousEnabled);
            interval.set(previousInterval);
            odorValues.set(previousOdorValues);
        }
    }

    @CoreTest(timeoutTicks = 20)
    public static void trackedSoundIdsRemainIsolatedByDimension(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(3, 9, 3));
        String soundId = "soundattract:arrow_miss#dimension_isolation";
        String overworld = "minecraft:overworld";
        String nether = "minecraft:the_nether";
        try {
            SoundTracker.addSound(null, position, overworld, 32.0, 8.0, 200, soundId);
            SoundTracker.addSound(null, position, nether, 48.0, 12.0, 200, soundId);
            var first = SoundTracker.getNearbySounds(helper.getLevel(), overworld, position, null).stream()
                    .filter(record -> soundId.equals(record.soundId)).toList();
            var second = SoundTracker.getNearbySounds(helper.getLevel(), nether, position, null).stream()
                    .filter(record -> soundId.equals(record.soundId)).toList();
            if (first.size() != 1 || second.size() != 1 || first.getFirst().range != 32.0 || second.getFirst().range != 48.0) {
                helper.fail("Equal sound IDs in separate dimensions overwrote one another: overworld="
                        + first.stream().map(record -> record.dimensionKey + ":" + record.range + ":" + record.weight).toList()
                        + ", nether=" + second.stream().map(record -> record.dimensionKey + ":" + record.range + ":" + record.weight).toList()
                        + ", whitelist=" + com.example.soundattract.config.SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(
                        Identifier.withDefaultNamespace("arrow_miss")));
                return;
            }
            SoundTracker.removeSoundAt(position, overworld);
            if (!SoundTracker.getNearbySounds(helper.getLevel(), nether, position, null).stream()
                    .anyMatch(record -> soundId.equals(record.soundId))) {
                helper.fail("Removing a sound in one dimension removed the other dimension's record");
                return;
            }
            helper.succeed();
        } finally {
            SoundTracker.removeSoundAt(position, overworld);
            SoundTracker.removeSoundAt(position, nether);
        }
    }

    @CoreTest(timeoutTicks = 400)
    public static void flowFieldReturnsProgressTowardTarget(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 8, z), Blocks.STONE);
            }
        }

        Mob zombie = helper.spawnWithNoFreeWill(zombieType(), new BlockPos(2, 9, 2));
        BlockPos targetBlock = zombie.blockPosition().offset(0, 0, 4);
        Vec3 target = Vec3.atBottomCenterOf(targetBlock);
        helper.runAfterDelay(1, () -> checkFlowFieldResult(helper, zombie, target));
    }

    @CoreTest(timeoutTicks = 20)
    public static void floorCreekUsesMovementPoseProbabilitiesAndBlockTag(GameTestHelper helper) {
        double swimming = FloorCreekEvents.poseProbability(true, false, false, false, 0.2, 0.3, 0.4, 0.5);
        double sprinting = FloorCreekEvents.poseProbability(false, false, true, true, 0.2, 0.3, 0.4, 0.5);
        double sneaking = FloorCreekEvents.poseProbability(false, false, true, false, 0.2, 0.3, 0.4, 0.5);
        double walking = FloorCreekEvents.poseProbability(false, false, false, false, 0.2, 0.3, 0.4, 0.5);
        boolean creekFloor = Blocks.SPRUCE_PLANKS.defaultBlockState().is(FloorCreekEvents.FLOOR_CREEK_BLOCKS);
        boolean nonCreekFloor = Blocks.STONE.defaultBlockState().is(FloorCreekEvents.FLOOR_CREEK_BLOCKS);
        if (swimming != 0.2 || sprinting != 0.5 || sneaking != 0.3 || walking != 0.4
                || FloorCreekEvents.poseProbability(true, true, false, false, 0.2, 0.3, 0.4, 0.5) != 0.0
                || !creekFloor || nonCreekFloor) {
            helper.fail("Floor creak probabilities or the configured floor block tag did not match movement state");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 20)
    public static void camouflageLayerPersistenceAndScentStateSurviveRoundTrip(GameTestHelper helper) {
        CamoLayer original = new CamoLayer(0x336699, "forest", 0.25f, 0.75f, true, 123L, 456L,
                0.1f, 0.2f, 0.3f);
        CamoLayer restored = CamoLayer.load(original.save());
        var legacyTag = new net.minecraft.nbt.CompoundTag();
        legacyTag.putInt("Color", 0xAABBCC);
        legacyTag.putString("Category", "legacy");
        legacyTag.putFloat("Durability", 0.4f);
        CamoLayer migratedLegacy = CamoLayer.load(legacyTag);
        CamouflageCapability capability = new CamouflageCapability();
        capability.setLayers(List.of(restored));
        if (!original.equals(restored)
                || migratedLegacy.upperDurability() != 0.4f || migratedLegacy.lowerDurability() != 0.4f
                || capability.getBlendedColor().orElse(-1) != original.color()
                || Math.abs(capability.getVisualCamoStrength() - 0.5f) > 0.0001f
                || capability.getScentBlockFactor() <= 0.0) {
            helper.fail("Camouflage layer data, appearance strength, or scent blocking changed after serialization");
            return;
        }
        helper.succeed();
    }

    @CoreTest(timeoutTicks = 20)
    public static void concealEnchantmentAndLootModifiersLoad(GameTestHelper helper) {
        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var conceal = enchantments.get(ModEnchantments.CONCEAL).orElse(null);
        if (conceal == null) {
            helper.fail("Conceal enchantment did not load from the 26.2 datapack registry");
            return;
        }
        var chestplate = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE);
        if (!conceal.value().canEnchant(chestplate)) {
            helper.fail("Conceal enchantment does not accept armor items");
            return;
        }
        chestplate.enchant(conceal, 1);
        if (net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(conceal, chestplate) != 1
                || chestplate.hasFoil()) {
            helper.fail("Conceal enchantment did not apply or its glint suppression mixin did not run");
            return;
        }
        var resources = helper.getLevel().getServer().getResourceManager().listResources("loot_modifiers",
                id -> id.getNamespace().equals("soundattract"));
        var expected = List.of("add_conceal_book", "enchant_conceal_armor", "enchant_conceal_tool");
        for (String path : expected) {
            if (resources.keySet().stream().noneMatch(id -> id.getPath().endsWith("loot_modifiers/" + path + ".json"))) {
                helper.fail("Conceal loot modifier resource was not discoverable: " + path);
                return;
            }
        }
        helper.succeed();
    }

    private static void checkFlowFieldResult(GameTestHelper helper, Mob zombie, Vec3 target) {
        if (zombie.isRemoved() || !zombie.isAlive()) {
            helper.fail("Flow-field requester did not remain alive while its scan was pending");
            return;
        }

        Vec3 direction = FlowFieldManager.getNextStep(zombie, target);
        if (direction == null) {
            helper.runAfterDelay(1, () -> checkFlowFieldResult(helper, zombie, target));
            return;
        }

        Vec3 toTarget = target.subtract(zombie.position()).normalize();
        if (!Double.isFinite(direction.x) || !Double.isFinite(direction.y) || !Double.isFinite(direction.z)
                || direction.dot(toTarget) <= 0.5 || direction.lengthSqr() < 0.9 || direction.lengthSqr() > 1.1) {
            helper.fail("Flow field returned invalid or non-progressing direction " + direction);
            return;
        }
        helper.succeed();
    }
    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "core_mechanics"),
                new TestEnvironmentDefinition.AllOf());
        for (Method method : CoreMechanicsGameTests.class.getDeclaredMethods()) {
            CoreTest metadata = method.getAnnotation(CoreTest.class);
            if (metadata == null) continue;
            Identifier structure = Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID,
                    "coremechanicsgametests." + metadata.template());
            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, structure,
                    metadata.timeoutTicks(), 0, true);
            Identifier testId = Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID,
                    "core_mechanics/" + toSnakeCase(method.getName()));
            event.registerTest(testId, testData -> new CallbackGameTestInstance(testData, method), data);
        }
    }

    private static String toSnakeCase(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private static final class CallbackGameTestInstance extends GameTestInstance {
        private final Method testMethod;

        private CallbackGameTestInstance(TestData<Holder<TestEnvironmentDefinition<?>>> data, Method testMethod) {
            super(data);
            this.testMethod = testMethod;
        }

        @Override
        public void run(GameTestHelper helper) {
            try {
                testMethod.invoke(null, helper);
            } catch (ReflectiveOperationException exception) {
                if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
                if (exception.getCause() instanceof Error error) throw error;
                helper.fail("Could not execute test " + testMethod.getName() + ": " + exception.getMessage());
            }
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return MapCodec.unit(this);
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal(testMethod.getName());
        }
    }

}
