package com.example.soundattract.gametest;

import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.ai.BlockBreakerPosGoal;
import com.example.soundattract.ai.SoundClimbGoal;
import com.example.soundattract.ai.FollowScentGoal;
import com.example.soundattract.ai.PickUpAndThrowToSoundGoal;
import com.example.soundattract.ai.TeleportToSoundGoal;
import com.example.soundattract.network.AwarenessSnapshotMessage;
import com.example.soundattract.network.SoundMessage;
import com.example.soundattract.event.FloorCreekEvents;
import com.example.soundattract.event.ArrowInvestigationEvents;
import com.example.soundattract.event.ScentEvents;
import com.example.soundattract.event.AwarenessServerEvents;
import com.example.soundattract.pathfinding.PursuitNavigation;
import com.example.soundattract.pathfinding.HazardClassifier;
import com.example.soundattract.los.OptimizedLOS;
import com.example.soundattract.quantified.QuantifiedCacheCompat;
import com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge;
import com.example.soundattract.quantified.QuantifiedWorkScheduler;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import com.example.soundattract.scents.ScentSourceType;
import com.example.soundattract.scents.GlobalScentRateLimiter;
import com.example.soundattract.tracking.SoundTracker;
import com.example.soundattract.util.ArrowInvestigationHelper;
import com.example.soundattract.loot.LootTableMarkerState;
import com.example.soundattract.util.ScentQueryHelper;
import com.example.soundattract.config.SoundAttractConfig;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-side vertical-slice tests. Each test owns its game state and uses
 * production APIs; the suite does not change config gates to accommodate a fixture.
 */
public final class SoundAttractGameTests {
    private static final int TEST_TIMEOUT_TICKS = 100;

    private static ServerPlayer makeTrackedSurvivalPlayer(ServerLevel level, String name) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), name);
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override public boolean isCreative() { return false; }
            @Override public boolean isSpectator() { return false; }
        };
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static Vec3 fleeDestinationForDiagnostics(com.example.soundattract.ai.FleeFromUnseenAttackerGoal goal) {
        try {
            var x = goal.getClass().getDeclaredField("wantedX");
            var y = goal.getClass().getDeclaredField("wantedY");
            var z = goal.getClass().getDeclaredField("wantedZ");
            x.setAccessible(true); y.setAccessible(true); z.setAccessible(true);
            return new Vec3(x.getDouble(goal), y.getDouble(goal), z.getDouble(goal));
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static int concealLevel(net.minecraft.world.item.ItemStack stack,
            net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> conceal) {
        int regular = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(conceal, stack);
        net.minecraft.world.item.enchantment.ItemEnchantments stored =
                stack.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
        return Math.max(regular, stored == null ? 0 : stored.getLevel(conceal));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:los_batch", timeoutTicks = 40)
    public void batchedLineOfSightCompletesAndCoalescesDuplicateRequests(GameTestHelper helper) {
        Zombie looker = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        looker.setNoAi(true);
        var target = helper.spawn(EntityType.COW, 4, 1, 1);
        target.setNoAi(true);

        var first = OptimizedLOS.canSeeAsync(looker, target);
        var duplicate = OptimizedLOS.canSeeAsync(looker, target);
        if (first != duplicate) {
            helper.fail("Duplicate LOS requests were not coalesced");
            return;
        }

        OptimizedLOS.onServerTick(helper.getLevel().getServer());
        if (!first.isDone() || !first.join()) {
            helper.fail("Batched LOS request did not complete as visible");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:tracker_producer", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void soundProducerFeedsAttractionGoal(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 3);
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        zombie.setNoAi(true);
        BlockPos soundPos = helper.absolutePos(new BlockPos(5, 1, 1));
        String dimension = level.dimension().location().toString();
        // Outrank any scent nodes left by concurrently running fixtures even
        // when they temporarily toggle the shared scent-priority setting.
        SoundTracker.addVirtualSound(soundPos, dimension, 32.0, 1_000_000.0, 200, UUID.randomUUID(), "GameTest");
        SoundTracker.SoundRecord selected = SoundTracker.findNearestSound(
                zombie, level, zombie.blockPosition(), zombie.getEyePosition());
        AttractionGoal goal = new AttractionGoal(zombie, 1.0);
        boolean goalAccepted = goal.canUse();

        if (selected == null || !soundPos.equals(selected.pos)) {
            SoundTracker.removeSoundAt(soundPos, dimension);
            helper.fail("SoundTracker did not return the emitted in-range virtual sound: selected="
                    + (selected == null ? "null" : selected.soundId + "@" + selected.pos)
                    + ", nearby=" + SoundTracker.getNearbySounds(level, dimension, zombie.blockPosition(), null)
                            .stream().map(record -> record.soundId + "@" + record.pos + " range=" + record.range
                                    + " weight=" + record.weight).toList());
            return;
        }
        boolean immediateRescanAccepted = goal.canUse();
        if (immediateRescanAccepted) {
            goal.stop();
            SoundTracker.removeSoundAt(soundPos, dimension);
            helper.fail("AttractionGoal ignored its per-mob scan cooldown after an accepted scan");
            return;
        }
        if (!goalAccepted) {
            SoundTracker.removeSoundAt(soundPos, dimension);
            helper.fail("AttractionGoal rejected an eligible zombie with a valid sound record: selected="
                    + selected.pos + ", target=" + goal.getTargetSoundPos() + ", attracted="
                    + com.example.soundattract.event.SoundAttractionEvents.getCachedAttractedEntityTypes().contains(EntityType.ZOMBIE)
                    + ", skipTarget=" + SoundAttractConfig.COMMON.skipSoundScanWhenHasTarget.get()
                    + ", combatTarget=" + zombie.getTarget() + ", suppressStealth="
                    + com.example.soundattract.event.StealthDetectionEvents.shouldSuppressTargeting(zombie));
            return;
        }
        if (!soundPos.equals(goal.getTargetSoundPos()) || !goal.isPursuingSound()) {
            helper.fail("AttractionGoal did not retain the selected sound as its pursuit target");
            return;
        }

        SoundTracker.removeSoundAt(soundPos, dimension);
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:tracker_vanilla_sprint", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void vanillaSprintProducerPreservesPlayerSoundMetadata(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.server.level.ServerPlayer player = new net.minecraft.server.level.ServerPlayer(
                level.getServer(), level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "vanilla-sound-owner"), net.minecraft.server.level.ClientInformation.createDefault());
        BlockPos sourcePos = helper.absolutePos(new BlockPos(2, 1, 1));
        String dimension = level.dimension().location().toString();
        player.setPos(sourcePos.getX() + 0.5, sourcePos.getY(), sourcePos.getZ() + 0.5);
        player.setSprinting(true);
        int cooldown = Math.max(1,
                com.example.soundattract.runtime.DynamicScanCooldownManager.currentScanCooldownTicks);
        int previousSoundCap = reserveSoundCapacityForGameTest(2);
        try {
        emitVanillaProducerTicks(player, cooldown * 2);

        List<SoundTracker.SoundRecord> produced = SoundTracker.getNearbySounds(
                        level, dimension, sourcePos, null).stream()
                .filter(record -> record.soundId != null
                        && record.soundId.contains(player.getUUID().toString())
                        && record.soundId.toLowerCase(java.util.Locale.ROOT).contains("vanillasprint"))
                .toList();
        SoundTracker.removeSoundAt(sourcePos, dimension);
        boolean metadataCorrect = produced.size() == 1
                && produced.get(0).isPlayerProduced()
                && produced.get(0).pos.equals(sourcePos)
                && dimension.equals(produced.get(0).dimensionKey)
                && Math.abs(produced.get(0).range - 10.0) < 0.001
                && Math.abs(produced.get(0).weight - 1.2) < 0.001;
        if (!metadataCorrect) {
            helper.fail("Vanilla sprint producer lost source/action/range/weight metadata: "
                    + produced.stream().map(record -> record.soundId + "@" + record.pos + " range="
                            + record.range + " weight=" + record.weight + " dim=" + record.dimensionKey).toList());
            return;
        }

        player.setSprinting(false);
        player.setOnGround(true);
        player.setDeltaMovement(new Vec3(0.2, 0.0, 0.0));
        for (int attempt = 0; attempt <= cooldown; attempt++) {
            com.example.soundattract.integration.vanilla.VanillaIntegrationEvents.onPlayerTick(player);
        }
        List<SoundTracker.SoundRecord> walking = SoundTracker.getNearbySounds(level, dimension, sourcePos, null).stream()
                .filter(record -> record.soundId != null && record.soundId.contains(player.getUUID().toString())
                        && record.soundId.toLowerCase(java.util.Locale.ROOT).contains("vanillawalk"))
                .toList();
        SoundTracker.removeSoundAt(sourcePos, dimension);
        boolean walkingMetadataCorrect = walking.size() == 1 && walking.get(0).isPlayerProduced()
                && walking.get(0).pos.equals(sourcePos) && dimension.equals(walking.get(0).dimensionKey)
                && Math.abs(walking.get(0).range - 6.0) < 0.001
                && Math.abs(walking.get(0).weight - 0.6) < 0.001;
        if (!walkingMetadataCorrect) {
            helper.fail("Vanilla walking producer lost source/action/range/weight metadata: "
                    + walking.stream().map(record -> record.soundId + "@" + record.pos + " range="
                            + record.range + " weight=" + record.weight + " dim=" + record.dimensionKey).toList());
            return;
        }
        } finally {
            SoundTracker.removeSoundAt(sourcePos, dimension);
            SoundAttractConfig.COMMON.maxSoundsTracked.set(previousSoundCap);
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:tracker_vanilla_whitelist", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void vanillaSoundProducerHonorsWhitelist(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "muted-sound-owner"), net.minecraft.server.level.ClientInformation.createDefault());
        BlockPos sourcePos = helper.absolutePos(new BlockPos(2, 1, 1));
        String dimension = level.dimension().location().toString();
        player.setPos(sourcePos.getX() + 0.5, sourcePos.getY(), sourcePos.getZ() + 0.5);
        player.setSprinting(true);
        Set<ResourceLocation> previousWhitelist = Set.copyOf(SoundAttractConfig.SOUND_ID_WHITELIST_CACHE);
        int cooldown = Math.max(1,
                com.example.soundattract.runtime.DynamicScanCooldownManager.currentScanCooldownTicks);
        int previousSoundCap = reserveSoundCapacityForGameTest(1);

        try {
            SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.clear();
            SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.add(ResourceLocation.fromNamespaceAndPath("soundattract", "not_virtual"));
            emitVanillaProducerTicks(player, cooldown * 2);
            List<SoundTracker.SoundRecord> produced = findVanillaProducerRecords(
                    level, sourcePos, dimension, player, "vanillasprint");
            if (!produced.isEmpty()) {
                helper.fail("Vanilla producer emitted a virtual sound excluded by the configured whitelist: "
                        + describeSoundRecords(produced));
                return;
            }
        } finally {
            SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.clear();
            SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.addAll(previousWhitelist);
            SoundTracker.removeSoundAt(sourcePos, dimension);
            SoundAttractConfig.COMMON.maxSoundsTracked.set(previousSoundCap);
        }

        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:tracker_vanilla_quiet_movement", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void vanillaSneakAndCrawlProducersPreserveSoundMetadata(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "quiet-sound-owner"), net.minecraft.server.level.ClientInformation.createDefault());
        BlockPos sourcePos = helper.absolutePos(new BlockPos(2, 1, 1));
        String dimension = level.dimension().location().toString();
        player.setPos(sourcePos.getX() + 0.5, sourcePos.getY(), sourcePos.getZ() + 0.5);
        int cooldown = Math.max(1,
                com.example.soundattract.runtime.DynamicScanCooldownManager.currentScanCooldownTicks);
        int previousSoundCap = reserveSoundCapacityForGameTest(2);

        try {
            player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
            emitVanillaProducerTicks(player, cooldown);
            List<SoundTracker.SoundRecord> sneaking = findVanillaProducerRecords(
                    level, sourcePos, dimension, player, "vanillasneak");
            if (!hasVanillaProducerMetadata(sneaking, sourcePos, dimension, 3.0, 0.2)) {
                helper.fail("Vanilla sneaking producer lost source/action/range/weight metadata: "
                        + describeSoundRecords(sneaking));
                return;
            }
            sneaking.forEach(record -> SoundTracker.removeSoundAt(record.pos, dimension));

            player.setPose(net.minecraft.world.entity.Pose.STANDING);
            player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
            emitVanillaProducerTicks(player, cooldown);
            List<SoundTracker.SoundRecord> crawling = findVanillaProducerRecords(
                    level, sourcePos, dimension, player, "vanillacrawl");
            if (!hasVanillaProducerMetadata(crawling, sourcePos, dimension, 2.0, 0.1)) {
                helper.fail("Vanilla crawling producer lost source/action/range/weight metadata: "
                        + describeSoundRecords(crawling));
                return;
            }
        } finally {
            SoundTracker.removeSoundAt(sourcePos, dimension);
            SoundAttractConfig.COMMON.maxSoundsTracked.set(previousSoundCap);
        }

        helper.succeed();
    }

    private static void emitVanillaProducerTicks(ServerPlayer player, int cooldown) {
        for (int attempt = 0; attempt <= cooldown; attempt++) {
            com.example.soundattract.integration.vanilla.VanillaIntegrationEvents.onPlayerTick(player);
        }
    }

    private static int reserveSoundCapacityForGameTest(int additionalRecords) {
        int previousLimit = SoundAttractConfig.COMMON.maxSoundsTracked.get();
        int requestedLimit = SoundTracker.getTrackedSoundCount() + Math.max(1, additionalRecords);
        SoundAttractConfig.COMMON.maxSoundsTracked.set(Math.max(previousLimit, requestedLimit));
        return previousLimit;
    }

    private static List<SoundTracker.SoundRecord> findVanillaProducerRecords(ServerLevel level,
            BlockPos sourcePos, String dimension, ServerPlayer player, String action) {
        return SoundTracker.getNearbySounds(level, dimension, sourcePos, null).stream()
                .filter(record -> record.soundId != null
                        && record.soundId.contains(player.getUUID().toString())
                        && record.soundId.toLowerCase(java.util.Locale.ROOT).contains(action))
                .toList();
    }

    private static boolean hasVanillaProducerMetadata(List<SoundTracker.SoundRecord> records,
            BlockPos sourcePos, String dimension, double range, double weight) {
        return records.size() == 1 && records.get(0).isPlayerProduced()
                && records.get(0).pos.equals(sourcePos)
                && dimension.equals(records.get(0).dimensionKey)
                && Math.abs(records.get(0).range - range) < 0.001
                && Math.abs(records.get(0).weight - weight) < 0.001;
    }

    private static String describeSoundRecords(List<SoundTracker.SoundRecord> records) {
        return records.stream().map(record -> record.soundId + "@" + record.pos + " range="
                + record.range + " weight=" + record.weight + " dim=" + record.dimensionKey).toList().toString();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void pointBlankShotAndReloadPreserveSoundMetadata(GameTestHelper helper) {
        if (!FabricLoader.getInstance().isModLoaded("pointblank")) {
            helper.fail("Point Blank profile is required for the installed-mod integration GameTest");
            return;
        }
        net.minecraft.world.item.Item gunItem = null;
        for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (item instanceof com.vicmatskiv.pointblank.item.GunItem) {
                gunItem = item;
                break;
            }
        }
        if (gunItem == null) {
            helper.fail("Point Blank profile loaded without registering a real GunItem");
            return;
        }

        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "awareness-retention-gametest"), net.minecraft.server.level.ClientInformation.createDefault()) {
            @Override
            public boolean isCreative() {
                return false;
            }
        };
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 1));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        String dimension = level.dimension().location().toString();
        boolean oldEnabled = SoundAttractConfig.SERVER.enablePointBlankIntegration.get();
        int oldSoundCap = SoundAttractConfig.COMMON.maxSoundsTracked.get();
        List<SoundTracker.SoundRecord> emitted = new java.util.ArrayList<>();
        List<SoundTracker.SoundRecord> shot = List.of();
        List<SoundTracker.SoundRecord> reload = List.of();
        net.minecraft.world.item.ItemStack gun = new net.minecraft.world.item.ItemStack(gunItem);
        String expectedShotId = "pointblank:gun_action#" + player.getUUID() + "/shoot";
        String expectedReloadId = "pointblank:gun_action#" + player.getUUID() + "/reload";
        net.minecraft.resources.ResourceLocation gunId =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(gunItem);
        double expectedShotRange = SoundAttractConfig.POINT_BLANK_GUN_RANGE_CACHE.getOrDefault(
                gunId, SoundAttractConfig.POINT_BLANK_SHOOT_RANGE_CACHE);
        try {
            SoundAttractConfig.SERVER.enablePointBlankIntegration.set(true);
            SoundAttractConfig.COMMON.maxSoundsTracked.set(Math.max(oldSoundCap,
                    SoundTracker.getTrackedSoundCount() + 2));
            com.example.soundattract.integration.pointblank.PointBlankIntegration.onGunShoot(player, gun);
            shot = SoundTracker.getNearbySounds(level, dimension, pos, null).stream()
                    .filter(record -> expectedShotId.equals(record.soundId)).toList();
            emitted.addAll(shot);
            com.example.soundattract.integration.pointblank.PointBlankIntegration.onGunReload(player, gun);
            reload = SoundTracker.getNearbySounds(level, dimension, pos, null).stream()
                    .filter(record -> expectedReloadId.equals(record.soundId)).toList();
            emitted.addAll(reload);
        } finally {
            SoundAttractConfig.SERVER.enablePointBlankIntegration.set(oldEnabled);
            SoundAttractConfig.COMMON.maxSoundsTracked.set(oldSoundCap);
            emitted.forEach(record -> SoundTracker.removeSoundAt(record.pos, dimension));
            level.getServer().getPlayerList().remove(player);
        }

        boolean shotMetadataCorrect = shot.size() == 1 && shot.get(0).isPlayerProduced()
                && pos.equals(shot.get(0).pos) && dimension.equals(shot.get(0).dimensionKey)
                && Math.abs(shot.get(0).range - (int) expectedShotRange) < 0.001
                && Math.abs(shot.get(0).weight - expectedShotRange / 5.0) < 0.001;
        boolean reloadMetadataCorrect = reload.size() == 1 && reload.get(0).isPlayerProduced()
                && pos.equals(reload.get(0).pos) && dimension.equals(reload.get(0).dimensionKey)
                && Math.abs(reload.get(0).range - (int) SoundAttractConfig.POINT_BLANK_RELOAD_RANGE_CACHE) < 0.001
                && Math.abs(reload.get(0).weight - SoundAttractConfig.POINT_BLANK_RELOAD_WEIGHT_CACHE * 2.0) < 0.001;
        if (!shotMetadataCorrect || !reloadMetadataCorrect) {
            helper.fail("Point Blank shot/reload integration lost owner/action/range/weight metadata: shot="
                    + describeSoundRecords(shot) + ", reload=" + describeSoundRecords(reload));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:tracker_cooldown", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void attractionGoalCooldownRejectsExactlyConfiguredRescans(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 3);
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        zombie.setNoAi(true);
        BlockPos soundPos = helper.absolutePos(new BlockPos(5, 1, 1));
        String dimension = level.dimension().location().toString();
        SoundTracker.addVirtualSound(soundPos, dimension, 4.0, 0.5, 200,
                UUID.randomUUID(), "GameTestCooldownBoundary");
        AttractionGoal goal = new AttractionGoal(zombie, 1.0);
        boolean previousScentOverride = SoundAttractConfig.COMMON.scentOverrideSoundPriority.get();
        boolean previousStealth = SoundAttractConfig.COMMON.enableStealthMechanics.get();
        boolean previousSkipTarget = SoundAttractConfig.COMMON.skipSoundScanWhenHasTarget.get();
        boolean previousRaidLeaderOnly = SoundAttractConfig.COMMON.raidLeaderOnlySoundScan.get();
        boolean initialScan = false;
        boolean allCooldownScansRejected = true;
        boolean scanAcceptedAtBoundary = false;
        try {
            SoundAttractConfig.COMMON.scentOverrideSoundPriority.set(false);
            SoundAttractConfig.COMMON.enableStealthMechanics.set(false);
            SoundAttractConfig.COMMON.skipSoundScanWhenHasTarget.set(false);
            SoundAttractConfig.COMMON.raidLeaderOnlySoundScan.set(false);
            initialScan = goal.canUse();
            int configuredCooldown = Math.max(1,
                    com.example.soundattract.runtime.DynamicScanCooldownManager.currentScanCooldownTicks);
            for (int i = 0; i < configuredCooldown; i++) {
                if (goal.canUse()) {
                    allCooldownScansRejected = false;
                    break;
                }
            }
            if (allCooldownScansRejected) scanAcceptedAtBoundary = goal.canUse();
        } finally {
            goal.stop();
            SoundTracker.removeSoundAt(soundPos, dimension);
            SoundAttractConfig.COMMON.scentOverrideSoundPriority.set(previousScentOverride);
            SoundAttractConfig.COMMON.enableStealthMechanics.set(previousStealth);
            SoundAttractConfig.COMMON.skipSoundScanWhenHasTarget.set(previousSkipTarget);
            SoundAttractConfig.COMMON.raidLeaderOnlySoundScan.set(previousRaidLeaderOnly);
        }
        if (!initialScan || !allCooldownScansRejected || !scanAcceptedAtBoundary) {
            helper.fail("Attraction rescan cooldown boundary mismatch: initial=" + initialScan
                    + ", cooldownScansRejected=" + allCooldownScansRejected
                    + ", acceptedAtBoundary=" + scanAcceptedAtBoundary
                    + ", configuredTicks="
                    + com.example.soundattract.runtime.DynamicScanCooldownManager.currentScanCooldownTicks);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:tracker_removed_goal", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void removedSoundStopsAndClearsAttractionGoal(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 3);
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        BlockPos soundPos = helper.absolutePos(new BlockPos(5, 1, 1));
        String dimension = level.dimension().location().toString();
        UUID source = UUID.randomUUID();
        boolean previousScentOverride = SoundAttractConfig.COMMON.scentOverrideSoundPriority.get();
        SoundAttractConfig.COMMON.scentOverrideSoundPriority.set(false);
        SoundTracker.addVirtualSound(soundPos, dimension, 32.0, 4.0, 200, source, "gametest_remove");
        AttractionGoal goal = new AttractionGoal(zombie, 1.0);
        if (!goal.canUse() || !soundPos.equals(goal.getTargetSoundPos())) {
            SoundTracker.removeSoundAt(soundPos, dimension);
            SoundAttractConfig.COMMON.scentOverrideSoundPriority.set(previousScentOverride);
            helper.fail("AttractionGoal did not acquire the test sound before the removal check");
            return;
        }
        // Forge keeps a successful nearest-sound result for up to six game ticks
        // while pursuing. Let that parity cache expire before asserting removal.
        helper.runAfterDelay(7, () -> {
            try {
                SoundTracker.removeSoundAt(soundPos, dimension);
                boolean continued = goal.canContinueToUse();
                goal.stop();
                if (continued || goal.getTargetSoundPos() != null || goal.isPursuingSound()) {
                    helper.fail("AttractionGoal retained pursuit state or navigation after its selected sound was removed");
                    return;
                }
                helper.succeed();
            } finally {
                goal.stop();
                SoundTracker.removeSoundAt(soundPos, dimension);
                SoundAttractConfig.COMMON.scentOverrideSoundPriority.set(previousScentOverride);
            }
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:tracker_expired_goal", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void expiredSoundStopsRunningAttractionGoal(GameTestHelper helper) {
        prepareRaisedFloor(helper, 0, 0, 7, 3, 89);
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 90, 1);
        zombie.setNoAi(true);
        BlockPos soundPos = helper.absolutePos(new BlockPos(4, 90, 1));
        String dimension = level.dimension().location().toString();
        UUID source = UUID.randomUUID();
        boolean previousScentOverride = SoundAttractConfig.COMMON.scentOverrideSoundPriority.get();
        SoundAttractConfig.COMMON.scentOverrideSoundPriority.set(false);
        String soundId = "soundattract:virtual#" + source + "/gametest_expiry_goal/" + soundPos.asLong();
        // A one-tick record can be expired by another GameTest's global
        // SoundTracker.tick() before this fixture gets its initial scan.
        // Keep it alive for setup, then advance the production tracker locally.
        SoundTracker.addVirtualSound(soundPos, dimension, 4.0, 4.0, 200, source, "gametest_expiry_goal");
        AttractionGoal goal = new AttractionGoal(zombie, 1.0);
        if (!goal.canUse() || !soundPos.equals(goal.getTargetSoundPos())) {
            SoundTracker.removeSoundAt(soundPos, dimension);
            SoundAttractConfig.COMMON.scentOverrideSoundPriority.set(previousScentOverride);
            helper.fail("AttractionGoal did not acquire the short-lived sound before expiry");
            return;
        }
        helper.runAfterDelay(7, () -> {
            try {
                // The GameTest server does not reliably dispatch the mod's
                // server-tick callback. Put only this fixture's sound on its
                // expiry boundary, then invoke the production tracker tick once
                // so unrelated GameTests' global sounds do not expire early.
                SoundTracker.SoundRecord expiryCandidate = SoundTracker.getNearbySounds(
                                level, dimension, soundPos, null).stream()
                        .filter(record -> soundId.equals(record.soundId)).findFirst().orElse(null);
                if (expiryCandidate == null) {
                    helper.fail("The target sound disappeared before its controlled expiry tick");
                    return;
                }
                expiryCandidate.ticksRemaining = 1;
                SoundTracker.tick();
                boolean recordExpired = SoundTracker.getNearbySounds(level, dimension, soundPos, null).stream()
                        .noneMatch(record -> soundId.equals(record.soundId));
                boolean continued = goal.canContinueToUse();
                BlockPos goalTargetBeforeStop = goal.getTargetSoundPos();
                boolean pursuingBeforeStop = goal.isPursuingSound();
                goal.stop();
                if (!recordExpired || continued || goal.getTargetSoundPos() != null || goal.isPursuingSound()) {
                    helper.fail("Expired-sound cleanup mismatch: recordExpired=" + recordExpired
                            + ", continued=" + continued + ", targetBeforeStop=" + goalTargetBeforeStop
                            + ", pursuingBeforeStop=" + pursuingBeforeStop + ", targetAfterStop="
                            + goal.getTargetSoundPos() + ", pursuingAfterStop=" + goal.isPursuingSound());
                    return;
                }
                helper.succeed();
            } finally {
                goal.stop();
                SoundTracker.removeSoundAt(soundPos, dimension);
                SoundAttractConfig.COMMON.scentOverrideSoundPriority.set(previousScentOverride);
            }
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:tracker_combat_gates", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void attractionGoalRespectsCombatTargetConfigAndOverrideThreshold(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 3);
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        Zombie combatTarget = helper.spawn(EntityType.ZOMBIE, 2, 1, 1);
        zombie.setTarget(combatTarget);
        BlockPos soundPos = helper.absolutePos(new BlockPos(6, 1, 1));
        String dimension = level.dimension().location().toString();
        UUID source = UUID.randomUUID();
        SoundTracker.addVirtualSound(soundPos, dimension, 32.0, 20.0, 200, source, "GameTestCombatTarget");
        boolean previousSkipTarget = SoundAttractConfig.COMMON.skipSoundScanWhenHasTarget.get();
        double previousOverrideWeight = SoundAttractConfig.COMMON.highSoundWeightTargetOverride.get();
        boolean previousScentOverride = SoundAttractConfig.COMMON.scentOverrideSoundPriority.get();
        boolean previousStealth = SoundAttractConfig.COMMON.enableStealthMechanics.get();
        boolean rejectedDuringCombat;
        boolean acceptedBelowOverride;
        boolean acceptedAboveOverride;
        boolean combatTargetClearedAboveOverride;
        String lowWeightDiagnostic;
        try {
            SoundAttractConfig.COMMON.highSoundWeightTargetOverride.set(100.0);
            SoundAttractConfig.COMMON.skipSoundScanWhenHasTarget.set(true);
            SoundAttractConfig.COMMON.scentOverrideSoundPriority.set(false);
            SoundAttractConfig.COMMON.enableStealthMechanics.set(false);
            rejectedDuringCombat = !new AttractionGoal(zombie, 1.0).canUse();
            SoundAttractConfig.COMMON.skipSoundScanWhenHasTarget.set(false);
            AttractionGoal goal = new AttractionGoal(zombie, 1.0);
            boolean goalAccepted = goal.canUse();
            acceptedBelowOverride = goalAccepted && combatTarget == zombie.getTarget()
                    && soundPos.equals(goal.getTargetSoundPos());
            lowWeightDiagnostic = "goalAccepted=" + goalAccepted + ", selected="
                    + SoundTracker.findNearestSound(zombie, level, zombie.blockPosition(), zombie.getEyePosition())
                    + ", goalTarget=" + goal.getTargetSoundPos() + ", combatTargetPreserved="
                    + (combatTarget == zombie.getTarget()) + ", attracted="
                    + com.example.soundattract.event.SoundAttractionEvents.getCachedAttractedEntityTypes().contains(EntityType.ZOMBIE)
                    + ", skip=" + SoundAttractConfig.COMMON.skipSoundScanWhenHasTarget.get();

            Zombie overrideMob = helper.spawn(EntityType.ZOMBIE, 1, 1, 2);
            Zombie overrideCombatTarget = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
            overrideMob.setTarget(overrideCombatTarget);
            BlockPos overrideSoundPos = helper.absolutePos(new BlockPos(6, 1, 2));
            SoundTracker.addVirtualSound(overrideSoundPos, dimension, 32.0, 200.0, 200,
                    UUID.randomUUID(), "GameTestHighWeightOverride");
            SoundAttractConfig.COMMON.highSoundWeightTargetOverride.set(1.0);
            AttractionGoal overrideGoal = new AttractionGoal(overrideMob, 1.0);
            acceptedAboveOverride = overrideGoal.canUse();
            combatTargetClearedAboveOverride = overrideMob.getTarget() == null
                    && overrideSoundPos.equals(overrideGoal.getTargetSoundPos());
        } finally {
            SoundAttractConfig.COMMON.skipSoundScanWhenHasTarget.set(previousSkipTarget);
            SoundAttractConfig.COMMON.highSoundWeightTargetOverride.set(previousOverrideWeight);
            SoundAttractConfig.COMMON.scentOverrideSoundPriority.set(previousScentOverride);
            SoundAttractConfig.COMMON.enableStealthMechanics.set(previousStealth);
            SoundTracker.removeSoundAt(soundPos, dimension);
            SoundTracker.removeSoundAt(helper.absolutePos(new BlockPos(6, 1, 2)), dimension);
        }
        if (!rejectedDuringCombat || !acceptedBelowOverride || !acceptedAboveOverride
                || !combatTargetClearedAboveOverride) {
            helper.fail("Attraction combat-target gates failed: skip=" + rejectedDuringCombat
                    + ", lowWeightPreservedCombatTarget=" + acceptedBelowOverride
                    + ", highWeightAccepted=" + acceptedAboveOverride
                    + ", highWeightClearedTarget=" + combatTargetClearedAboveOverride
                    + " (" + lowWeightDiagnostic + ")");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:tracker_unconfigured_mob", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void attractionGoalRejectsUnconfiguredMobWithoutProfile(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 4, 3);
        net.minecraft.world.entity.animal.horse.Horse horse = helper.spawn(EntityType.HORSE, 1, 1, 1);
        boolean typeExcluded = !com.example.soundattract.event.SoundAttractionEvents
                .getCachedAttractedEntityTypes().contains(EntityType.HORSE);
        boolean profileAbsent = com.example.soundattract.config.SoundAttractConfig.getMatchingProfile(horse) == null;
        boolean goalRejected = !new AttractionGoal(horse, 1.0).canUse();
        if (!typeExcluded || !profileAbsent || !goalRejected) {
            helper.fail("An unconfigured horse was not excluded by the production attraction eligibility check: typeExcluded="
                    + typeExcluded + ", profileAbsent=" + profileAbsent + ", goalRejected=" + goalRejected);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:tracker_datapack_profile", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void datapackMobProfileMakesSheepEligibleForSound(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 3);
        ServerLevel level = helper.getLevel();
        net.minecraft.world.entity.animal.Sheep sheep = helper.spawn(EntityType.SHEEP, 1, 1, 1);
        BlockPos soundPos = helper.absolutePos(new BlockPos(5, 1, 1));
        String dimension = level.dimension().location().toString();
        SoundTracker.addVirtualSound(soundPos, dimension, 32.0, 1000.0, 200, UUID.randomUUID(), "GameTestProfile");
        boolean previousDataDriven = SoundAttractConfig.COMMON.enableDataDriven.get();
        com.example.soundattract.config.MobProfile2 profile;
        boolean profileLoaded;
        boolean goalAccepted;
        BlockPos selectedTarget;
        try {
            SoundAttractConfig.COMMON.enableDataDriven.set(true);
            SoundAttractConfig.bakeConfig();
            profile = com.example.soundattract.config.SoundAttractConfig.getMatchingProfile(sheep);
            profileLoaded = profile != null && "soundattract:gametest_cow".equals(profile.id());
            AttractionGoal goal = new AttractionGoal(sheep, 1.0);
            goalAccepted = goal.canUse();
            selectedTarget = goal.getTargetSoundPos();
        } finally {
            SoundAttractConfig.COMMON.enableDataDriven.set(previousDataDriven);
            SoundAttractConfig.bakeConfig();
            SoundTracker.removeSoundAt(soundPos, dimension);
        }
        if (!profileLoaded || !goalAccepted || !soundPos.equals(selectedTarget)) {
            helper.fail("Datapack mob profile did not make a sheep eligible for its sound: profile="
                    + (profile == null ? "null" : profile.id()) + ", goalAccepted=" + goalAccepted
                    + ", selected=" + selectedTarget);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:tracker_datapack_tag", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void datapackEntityTagMakesCowEligibleForSound(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 3);
        ServerLevel level = helper.getLevel();
        net.minecraft.world.entity.animal.Cow cow = helper.spawn(EntityType.COW, 1, 1, 1);
        BlockPos soundPos = helper.absolutePos(new BlockPos(5, 1, 1));
        String dimension = level.dimension().location().toString();
        SoundTracker.addVirtualSound(soundPos, dimension, 32.0, 1000.0, 200, UUID.randomUUID(), "GameTestTag");
        boolean previousDataDriven = SoundAttractConfig.COMMON.enableDataDriven.get();
        boolean tagIncludesCow;
        boolean hasNoProfile;
        boolean goalAccepted;
        BlockPos selectedTarget;
        try {
            SoundAttractConfig.COMMON.enableDataDriven.set(true);
            SoundAttractConfig.bakeConfig();
            com.example.soundattract.event.SoundAttractionEvents.invalidateCachedEntityTypes();
            tagIncludesCow = com.example.soundattract.event.SoundAttractionEvents
                    .getCachedAttractedEntityTypes().contains(EntityType.COW);
            hasNoProfile = com.example.soundattract.config.SoundAttractConfig.getMatchingProfile(cow) == null;
            AttractionGoal goal = new AttractionGoal(cow, 1.0);
            goalAccepted = goal.canUse();
            selectedTarget = goal.getTargetSoundPos();
        } finally {
            SoundAttractConfig.COMMON.enableDataDriven.set(previousDataDriven);
            SoundAttractConfig.bakeConfig();
            com.example.soundattract.event.SoundAttractionEvents.invalidateCachedEntityTypes();
            SoundTracker.removeSoundAt(soundPos, dimension);
        }
        if (!tagIncludesCow || !hasNoProfile || !goalAccepted || !soundPos.equals(selectedTarget)) {
            helper.fail("Datapack entity tag did not make a cow eligible for sound attraction: tag="
                    + tagIncludesCow + ", noProfile=" + hasNoProfile + ", accepted=" + goalAccepted
                    + ", selected=" + selectedTarget);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void datapackSoundDefaultsAreApplied(GameTestHelper helper) {
        boolean previousDataDriven = SoundAttractConfig.COMMON.enableDataDriven.get();
        try {
            SoundAttractConfig.COMMON.enableDataDriven.set(true);
            SoundAttractConfig.bakeConfig();
            net.minecraft.resources.ResourceLocation soundId =
                    ResourceLocation.fromNamespaceAndPath("minecraft", "entity.generic.explode");
            var datapackDefault = SoundAttractConfig.DP_SOUND_DEFAULTS_CACHE.get(soundId);
            var effectiveDefault = SoundAttractConfig.SOUND_DEFAULT_ENTRIES_CACHE.get(soundId);
            if (datapackDefault == null || effectiveDefault == null
                    || Math.abs(datapackDefault.range() - 31.0) > 0.0001
                    || Math.abs(datapackDefault.weight() - 7.5) > 0.0001
                    || Math.abs(effectiveDefault.range() - 31.0) > 0.0001
                    || Math.abs(effectiveDefault.weight() - 7.5) > 0.0001) {
                helper.fail("Fabric datapack reload did not load and apply sound definition defaults: "
                        + "datapack=" + datapackDefault + ", effective=" + effectiveDefault);
                return;
            }
        } finally {
            SoundAttractConfig.COMMON.enableDataDriven.set(previousDataDriven);
            SoundAttractConfig.bakeConfig();
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void datapackArmorColorIsApplied(GameTestHelper helper) {
        net.minecraft.resources.ResourceLocation itemId =
                ResourceLocation.fromNamespaceAndPath("minecraft", "leather_helmet");
        Integer datapackColor = SoundAttractConfig.DP_CUSTOM_ARMOR_COLORS.get(itemId);
        if (datapackColor == null || datapackColor != 0x123456) {
            helper.fail("Fabric armor-color reload did not apply the JSON color: " + datapackColor);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void datapackPlayerProfileIsApplied(GameTestHelper helper) {
        var profile = SoundAttractConfig.DP_PLAYER_PROFILES_CACHE.stream()
                .filter(candidate -> candidate.id().equals("soundattract:gametest"))
                .findFirst().orElse(null);
        if (profile == null
                || profile.getDetectionOverride(com.example.soundattract.config.PlayerStance.SNEAKING)
                    .filter(value -> Math.abs(value - 0.12) < 0.0001).isEmpty()
                || profile.scentEmission().filter(emission -> !emission.enabled()
                    && Math.abs(emission.scentStrengthMultiplier() - 0.4) < 0.0001
                    && !emission.showScentParticles()).isEmpty()
                || profile.scentVisibility().filter(visibility -> !visibility.showPlayerWalk()
                    && visibility.showArrowScent() && visibility.showWindScent()).isEmpty()) {
            helper.fail("Fabric player-profile reload did not preserve detection, scent emission, and visibility settings");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:stealthrange_pose", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void playerStealthRangeUsesPoseProfileAndInvisibility(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie mob = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        net.minecraft.server.level.ServerPlayer player = new net.minecraft.server.level.ServerPlayer(
                level.getServer(), level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "scent-gametest"), net.minecraft.server.level.ClientInformation.createDefault()) {
            @Override
            public boolean isCreative() {
                return false;
            }
        };
        player.setPos(mob.getX() + 2.0, mob.getY(), mob.getZ());
        boolean previousStealthEnabled = SoundAttractConfig.COMMON.enableStealthMechanics.get();
        boolean previousTieredStealth = SoundAttractConfig.COMMON.enableTieredStealthPerformance.get();
        double previousStandingRange = SoundAttractConfig.COMMON.standingDetectionRangePlayer.get();
        double previousSneakingRange = SoundAttractConfig.COMMON.sneakingDetectionRangePlayer.get();
        double previousMaximumRange = SoundAttractConfig.COMMON.maxStealthDetectionRange.get();
        double previousInvisibilityFactor = SoundAttractConfig.COMMON.invisibilityStealthFactor.get();

        double standingRange;
        double sneakingRange;
        double invisibleRange;
        try {
            SoundAttractConfig.COMMON.enableStealthMechanics.set(true);
            SoundAttractConfig.COMMON.enableTieredStealthPerformance.set(false);
            SoundAttractConfig.COMMON.standingDetectionRangePlayer.set(24.0);
            SoundAttractConfig.COMMON.sneakingDetectionRangePlayer.set(12.0);
            SoundAttractConfig.COMMON.maxStealthDetectionRange.set(64.0);
            SoundAttractConfig.COMMON.invisibilityStealthFactor.set(0.1);
            standingRange = com.example.soundattract.event.StealthDetectionEvents
                    .getRealisticStealthDetectionRange(player, mob, level);
            player.setShiftKeyDown(true);
            player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
            sneakingRange = com.example.soundattract.event.StealthDetectionEvents
                    .getRealisticStealthDetectionRange(player, mob, level);
            player.setShiftKeyDown(false);
            player.setPose(net.minecraft.world.entity.Pose.STANDING);
            // A GameTest ServerPlayer has no packet connection, so use the same active-effect
            // state production detection reads without invoking ServerPlayer's packet callback.
            player.getActiveEffectsMap().put(net.minecraft.world.effect.MobEffects.INVISIBILITY,
                    new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.INVISIBILITY, 200, 0, false, false));
            invisibleRange = com.example.soundattract.event.StealthDetectionEvents
                    .getRealisticStealthDetectionRange(player, mob, level);
        } finally {
            level.getServer().getPlayerList().remove(player);
            SoundAttractConfig.COMMON.enableStealthMechanics.set(previousStealthEnabled);
            SoundAttractConfig.COMMON.enableTieredStealthPerformance.set(previousTieredStealth);
            SoundAttractConfig.COMMON.standingDetectionRangePlayer.set(previousStandingRange);
            SoundAttractConfig.COMMON.sneakingDetectionRangePlayer.set(previousSneakingRange);
            SoundAttractConfig.COMMON.maxStealthDetectionRange.set(previousMaximumRange);
            SoundAttractConfig.COMMON.invisibilityStealthFactor.set(previousInvisibilityFactor);
        }

        double configuredSneakingRange = SoundAttractConfig.COMMON.sneakingDetectionRangePlayer.get();
        double invisibilityFactor = SoundAttractConfig.COMMON.invisibilityStealthFactor.get();
        boolean profileAndPoseApplied = sneakingRange < standingRange;
        boolean invisibilityApplied = Math.abs(invisibleRange - standingRange * invisibilityFactor) < 0.01;
        if (!profileAndPoseApplied || !invisibilityApplied) {
            helper.fail("Stealth range modifiers did not follow the production profile/pose/effect path: "
                    + "standing=" + standingRange + ", sneaking=" + sneakingRange
                    + ", configuredSneaking=" + configuredSneakingRange
                    + ", invisible=" + invisibleRange + ", invisFactor=" + invisibilityFactor);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:stealthrange_suppression", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void stealthTargetSuppressionUpdatesAfterVisibilityAndMovement(GameTestHelper helper) {
        helper.runAfterDelay(20, () -> runStealthTargetSuppressionScenario(helper));
    }

    private static void runStealthTargetSuppressionScenario(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie mob = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        // GameTest's mock overrides creative/spectator. Recreate its embedded connection
        // with a survival ServerPlayer so production visibility gates see a live in-level player.
        ServerPlayer player = makeTrackedSurvivalPlayer(level, "stealth-gametest");
        mob.setYRot(0.0F);
        // Keep the initial visible target inside the empty test structure's loaded boundary.
        // It remains outside the reduced invisibility detection radius (1.45 blocks).
        player.setPos(mob.getX(), mob.getY(), mob.getZ() + 3.0);

        boolean oldStealth = SoundAttractConfig.COMMON.enableStealthMechanics.get();
        boolean oldTiered = SoundAttractConfig.COMMON.enableTieredStealthPerformance.get();
        boolean oldXrayTargeting = SoundAttractConfig.COMMON.enableXrayTargeting.get();
        boolean oldEdgeBehavior = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        double oldMaxRange = SoundAttractConfig.COMMON.maxStealthDetectionRange.get();
        double oldInvisibilityFactor = SoundAttractConfig.COMMON.invisibilityStealthFactor.get();
        double oldStandingRange = SoundAttractConfig.COMMON.standingDetectionRangePlayer.get();
        boolean visibleTargetAccepted;
        boolean invisibleTargetSuppressed;
        boolean closeInvisibleTargetAccepted;
        double invisibleDetectionRange = -1.0;
        double invisibleDistance = -1.0;
        boolean invisibilityApplied = false;
        try {
            SoundAttractConfig.COMMON.enableStealthMechanics.set(true);
            SoundAttractConfig.COMMON.enableTieredStealthPerformance.set(false);
            SoundAttractConfig.COMMON.enableXrayTargeting.set(false);
            SoundAttractConfig.COMMON.edgeMobSmartBehavior.set(false);
            SoundAttractConfig.COMMON.maxStealthDetectionRange.set(40.0);
            SoundAttractConfig.COMMON.invisibilityStealthFactor.set(0.05);
            SoundAttractConfig.COMMON.standingDetectionRangePlayer.set(24.0);
            visibleTargetAccepted = !com.example.soundattract.event.StealthDetectionEvents
                    .shouldSuppressTargeting(mob, player);

            player.getActiveEffectsMap().put(net.minecraft.world.effect.MobEffects.INVISIBILITY,
                    new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.INVISIBILITY, 200, 0, false, false));
            invisibilityApplied = player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
            invisibleDetectionRange = com.example.soundattract.event.StealthDetectionEvents
                    .getRealisticStealthDetectionRange(player, mob, level);
            invisibleDistance = mob.distanceTo(player);
            invisibleTargetSuppressed = com.example.soundattract.event.StealthDetectionEvents
                    .shouldSuppressTargeting(mob, player);

            player.setPos(mob.getX(), mob.getY(), mob.getZ() + 1.0);
            closeInvisibleTargetAccepted = !com.example.soundattract.event.StealthDetectionEvents
                    .shouldSuppressTargeting(mob, player);
        } finally {
            level.getServer().getPlayerList().remove(player);
            SoundAttractConfig.COMMON.enableStealthMechanics.set(oldStealth);
            SoundAttractConfig.COMMON.enableTieredStealthPerformance.set(oldTiered);
            SoundAttractConfig.COMMON.enableXrayTargeting.set(oldXrayTargeting);
            SoundAttractConfig.COMMON.edgeMobSmartBehavior.set(oldEdgeBehavior);
            SoundAttractConfig.COMMON.maxStealthDetectionRange.set(oldMaxRange);
            SoundAttractConfig.COMMON.invisibilityStealthFactor.set(oldInvisibilityFactor);
            SoundAttractConfig.COMMON.standingDetectionRangePlayer.set(oldStandingRange);
        }

        if (!visibleTargetAccepted || !invisibleTargetSuppressed || !closeInvisibleTargetAccepted) {
            helper.fail("Stealth target suppression did not update for visibility and moved target: visible="
                    + visibleTargetAccepted + ", invisibleSuppressed=" + invisibleTargetSuppressed
                    + ", closeInvisibleAccepted=" + closeInvisibleTargetAccepted
                    + ", invisibilityApplied=" + invisibilityApplied + ", distance=" + invisibleDistance
                    + ", detectionRange=" + invisibleDetectionRange
                    + ", effectiveXray=" + com.example.soundattract.event.StealthDetectionEvents.getEffectiveXrayRange(mob)
                    + ", xrayEnabled=" + SoundAttractConfig.COMMON.enableXrayTargeting.get()
                    + ", bypass=" + SoundAttractConfig.isStealthBypassed(mob)
                    + ", perfTier=" + com.example.soundattract.event.StealthDetectionEvents.getPerfTier(level)
                    + ", stealthEnabled=" + SoundAttractConfig.COMMON.enableStealthMechanics.get()
                    + ", playerCreative=" + player.isCreative() + ", playerSpectator=" + player.isSpectator()
                    + ", playerAlive=" + player.isAlive()
                    + ", lineOfSight=" + com.example.soundattract.event.FovEvents.hasSmartLineOfSight(mob, player)
                    + ", inFov=" + com.example.soundattract.event.FovEvents.isTargetInFov(mob, player, false)
                    + ", yaw=" + mob.getYRot() + ", headYaw=" + mob.getYHeadRot()
                    + ", mobPos=" + mob.position() + ", playerPos=" + player.position());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:stealthrange_armor", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void listedCamouflageArmorReducesAndRestoresDetectionRange(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie mob = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        net.minecraft.server.level.ServerPlayer player = new net.minecraft.server.level.ServerPlayer(
                level.getServer(), level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "armor-camo-gametest"), net.minecraft.server.level.ClientInformation.createDefault());
        player.setPos(mob.getX() + 2.0, mob.getY(), mob.getZ());

        boolean oldStealth = SoundAttractConfig.COMMON.enableStealthMechanics.get();
        boolean oldTiered = SoundAttractConfig.COMMON.enableTieredStealthPerformance.get();
        boolean oldCamo = SoundAttractConfig.COMMON.enableCamouflage.get();
        boolean oldDataDriven = SoundAttractConfig.COMMON.enableDataDriven.get();
        boolean oldEnvironment = SoundAttractConfig.COMMON.enableEnvironmentalCamouflage.get();
        boolean oldFullSet = SoundAttractConfig.COMMON.requireFullSetForCamouflageBonus.get();
        List<? extends String> oldItems = SoundAttractConfig.COMMON.camouflageArmorItems.get();
        double oldHelmetEffect = SoundAttractConfig.COMMON.helmetCamouflageEffectiveness.get();
        double oldStandingRange = SoundAttractConfig.COMMON.standingDetectionRangePlayer.get();
        double oldMaximumRange = SoundAttractConfig.COMMON.maxStealthDetectionRange.get();
        double baseline;
        double equipped;
        double removed;
        try {
            SoundAttractConfig.COMMON.enableStealthMechanics.set(true);
            SoundAttractConfig.COMMON.enableTieredStealthPerformance.set(false);
            SoundAttractConfig.COMMON.enableCamouflage.set(true);
            SoundAttractConfig.COMMON.enableDataDriven.set(false);
            SoundAttractConfig.COMMON.enableEnvironmentalCamouflage.set(false);
            SoundAttractConfig.COMMON.requireFullSetForCamouflageBonus.set(false);
            SoundAttractConfig.COMMON.camouflageArmorItems.set(List.of("minecraft:leather_helmet"));
            SoundAttractConfig.COMMON.helmetCamouflageEffectiveness.set(0.30);
            SoundAttractConfig.COMMON.standingDetectionRangePlayer.set(24.0);
            SoundAttractConfig.COMMON.maxStealthDetectionRange.set(64.0);
            player.setPose(net.minecraft.world.entity.Pose.STANDING);
            baseline = com.example.soundattract.event.StealthDetectionEvents
                    .getRealisticStealthDetectionRange(player, mob, level);

            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
            equipped = com.example.soundattract.event.StealthDetectionEvents
                    .getRealisticStealthDetectionRange(player, mob, level);

            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
            removed = com.example.soundattract.event.StealthDetectionEvents
                    .getRealisticStealthDetectionRange(player, mob, level);
        } finally {
            level.getServer().getPlayerList().remove(player);
            SoundAttractConfig.COMMON.enableStealthMechanics.set(oldStealth);
            SoundAttractConfig.COMMON.enableTieredStealthPerformance.set(oldTiered);
            SoundAttractConfig.COMMON.enableCamouflage.set(oldCamo);
            SoundAttractConfig.COMMON.enableDataDriven.set(oldDataDriven);
            SoundAttractConfig.COMMON.enableEnvironmentalCamouflage.set(oldEnvironment);
            SoundAttractConfig.COMMON.requireFullSetForCamouflageBonus.set(oldFullSet);
            SoundAttractConfig.COMMON.camouflageArmorItems.set(oldItems);
            SoundAttractConfig.COMMON.helmetCamouflageEffectiveness.set(oldHelmetEffect);
            SoundAttractConfig.COMMON.standingDetectionRangePlayer.set(oldStandingRange);
            SoundAttractConfig.COMMON.maxStealthDetectionRange.set(oldMaximumRange);
        }

        double expectedEquipped = baseline * 0.70;
        if (baseline <= 0.0 || Math.abs(equipped - expectedEquipped) > 0.02
                || Math.abs(removed - baseline) > 0.02) {
            helper.fail("Listed armor camouflage was not applied live or did not revert after equipment removal: baseline="
                    + baseline + ", equipped=" + equipped + ", expected=" + expectedEquipped
                    + ", afterRemoval=" + removed);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:stealthrange_environment", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void environmentalColorMatchReducesProductionDetectionRange(GameTestHelper helper) {
        // Forge Config API Port corrects generated TOML files asynchronously
        // during test-server startup. Wait for that initialization before this
        // scenario mutates its live config values.
        helper.runAfterDelay(20, () -> runEnvironmentalColorMatchScenario(helper));
    }

    private static void runEnvironmentalColorMatchScenario(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 6, 6);
        ServerLevel level = helper.getLevel();
        Zombie mob = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        net.minecraft.server.level.ServerPlayer player = new net.minecraft.server.level.ServerPlayer(
                level.getServer(), level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "environment-camo-gametest"), net.minecraft.server.level.ClientInformation.createDefault());
        player.setPos(helper.absolutePos(new BlockPos(4, 1, 4)).getX() + 0.5,
                helper.absolutePos(new BlockPos(4, 1, 4)).getY(),
                helper.absolutePos(new BlockPos(4, 1, 4)).getZ() + 0.5);

        boolean oldStealth = SoundAttractConfig.COMMON.enableStealthMechanics.get();
        boolean oldTiered = SoundAttractConfig.COMMON.enableTieredStealthPerformance.get();
        boolean oldItemCamo = SoundAttractConfig.COMMON.enableCamouflage.get();
        boolean oldEnvCamo = SoundAttractConfig.COMMON.enableEnvironmentalCamouflage.get();
        String oldSamplingMode = SoundAttractConfig.COMMON.environmentalCamouflageSamplingMode.get();
        double oldMaxEffect = SoundAttractConfig.COMMON.environmentalCamouflageMaxEffectiveness.get();
        int oldMatchThreshold = SoundAttractConfig.COMMON.environmentalCamouflageColorMatchThreshold.get();
        double oldStandingRange = SoundAttractConfig.COMMON.standingDetectionRangePlayer.get();
        double oldMaximumRange = SoundAttractConfig.COMMON.maxStealthDetectionRange.get();
        double baseline;
        double camouflaged;
        int sampleColor;
        boolean perceptionColorMatchesFloor;
        try {
            SoundAttractConfig.COMMON.enableStealthMechanics.set(true);
            SoundAttractConfig.COMMON.enableTieredStealthPerformance.set(false);
            SoundAttractConfig.COMMON.enableCamouflage.set(false);
            SoundAttractConfig.COMMON.enableEnvironmentalCamouflage.set(false);
            SoundAttractConfig.COMMON.environmentalCamouflageSamplingMode.set("average_area");
            SoundAttractConfig.COMMON.standingDetectionRangePlayer.set(24.0);
            SoundAttractConfig.COMMON.maxStealthDetectionRange.set(64.0);
            player.setPose(net.minecraft.world.entity.Pose.STANDING);
            baseline = com.example.soundattract.event.StealthDetectionEvents
                    .getRealisticStealthDetectionRange(player, mob, level);

            BlockPos feet = player.blockPosition();
            sampleColor = level.getBlockState(feet.below()).getMapColor(level, feet.below()).col & 0xFFFFFF;
            var capability = com.example.soundattract.camo.CamouflageCapability.getCapability(player).orElse(null);
            if (capability == null) {
                helper.fail("Player camouflage component is not attached to the GameTest player");
                return;
            }
            com.example.soundattract.camo.CamoLayer matchingLayer = new com.example.soundattract.camo.CamoLayer(
                    sampleColor, "gametest", 1.0f, 1.0f, false, level.getGameTime(), 1L, 0.0f, 0.0f, 0.0f);
            // Combined camouflage strength is normalized over five full layers.
            capability.setLayers(List.of(matchingLayer, matchingLayer, matchingLayer, matchingLayer, matchingLayer));
            perceptionColorMatchesFloor = com.example.soundattract.util.CamoUtil.getFinalPerceptionColor(player)
                    == sampleColor;

            SoundAttractConfig.COMMON.environmentalCamouflageMaxEffectiveness.set(0.50);
            SoundAttractConfig.COMMON.environmentalCamouflageColorMatchThreshold.set(0);
            SoundAttractConfig.COMMON.enableEnvironmentalCamouflage.set(true);
            camouflaged = com.example.soundattract.event.StealthDetectionEvents
                    .getRealisticStealthDetectionRange(player, mob, level);
        } finally {
            level.getServer().getPlayerList().remove(player);
            SoundAttractConfig.COMMON.enableStealthMechanics.set(oldStealth);
            SoundAttractConfig.COMMON.enableTieredStealthPerformance.set(oldTiered);
            SoundAttractConfig.COMMON.enableCamouflage.set(oldItemCamo);
            SoundAttractConfig.COMMON.enableEnvironmentalCamouflage.set(oldEnvCamo);
            SoundAttractConfig.COMMON.environmentalCamouflageSamplingMode.set(oldSamplingMode);
            SoundAttractConfig.COMMON.environmentalCamouflageMaxEffectiveness.set(oldMaxEffect);
            SoundAttractConfig.COMMON.environmentalCamouflageColorMatchThreshold.set(oldMatchThreshold);
            SoundAttractConfig.COMMON.standingDetectionRangePlayer.set(oldStandingRange);
            SoundAttractConfig.COMMON.maxStealthDetectionRange.set(oldMaximumRange);
        }

        double expected = baseline * 0.50;
        if (!perceptionColorMatchesFloor || baseline <= 0.0 || Math.abs(camouflaged - expected) > 0.02) {
            helper.fail("Matching environmental/camouflage colors did not apply the configured range reduction: baseline="
                    + baseline + ", camouflaged=" + camouflaged + ", expected=" + expected
                    + ", perceptionColorMatchesFloor=" + perceptionColorMatchesFloor
                    + ", sampleColor=0x" + Integer.toHexString(sampleColor));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void mobFieldOfViewAcceptsFrontAndRejectsRearTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie mob = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        net.minecraft.server.level.ServerPlayer player = new net.minecraft.server.level.ServerPlayer(
                level.getServer(), level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "walking-scent-gametest"), net.minecraft.server.level.ClientInformation.createDefault()) {
            @Override
            public boolean isCreative() {
                return false;
            }
        };
        mob.setYRot(0.0F);
        mob.setXRot(0.0F);
        player.setPos(mob.getX(), mob.getY(), mob.getZ() + 3.0);
        boolean frontVisible = com.example.soundattract.event.FovEvents.isTargetInFov(mob, player, false);
        player.setPos(mob.getX(), mob.getY(), mob.getZ() - 3.0);
        boolean rearVisible = com.example.soundattract.event.FovEvents.isTargetInFov(mob, player, false);
        level.getServer().getPlayerList().remove(player);

        if (!frontVisible || rearVisible) {
            helper.fail("Configured mob FOV did not accept the forward target and reject the rear target: front="
                    + frontVisible + ", rear=" + rearVisible);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void mobFieldOfViewRejectsBlockedSightline(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 8, 8);
        ServerLevel level = helper.getLevel();
        Zombie clearMob = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        Zombie blockedMob = helper.spawn(EntityType.ZOMBIE, 1, 1, 5);
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        clearMob.setYRot(-90.0F);
        clearMob.setXRot(0.0F);
        blockedMob.setYRot(-90.0F);
        blockedMob.setXRot(0.0F);
        player.setPos(clearMob.getX() + 4.0, clearMob.getY(), clearMob.getZ());
        boolean clearSightAccepted = com.example.soundattract.event.FovEvents
                .isTargetInFov(clearMob, player, true);

        player.setPos(blockedMob.getX() + 4.0, blockedMob.getY(), blockedMob.getZ());
        BlockPos wallBase = helper.absolutePos(new BlockPos(3, 1, 5));
        level.setBlock(wallBase, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(wallBase.above(), Blocks.STONE.defaultBlockState(), 3);
        boolean blockedSightAccepted = com.example.soundattract.event.FovEvents
                .isTargetInFov(blockedMob, player, true);
        level.getServer().getPlayerList().remove(player);

        if (!clearSightAccepted || blockedSightAccepted) {
            helper.fail("FOV+LOS did not distinguish clear and wall-obstructed frontal targets: clear="
                    + clearSightAccepted + ", blocked=" + blockedSightAccepted);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:flee", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void fleeGoalRequiresAnUnseenPlayerAttacker(GameTestHelper helper) {
        // Keep the complete +/-16 horizontal flee search area inside the
        // GameTest ticking region without generating a 129x129 block platform.
        prepareFloor(helper, -16, -16, 32, 32);
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 8, 1, 8);
        Zombie nonPlayerAttacker = helper.spawn(EntityType.ZOMBIE, 4, 1, 4);
        net.minecraft.server.level.ServerPlayer player = makeTrackedSurvivalPlayer(level, "flee-goal-gametest");
        player.setPos(zombie.getX(), zombie.getY(), zombie.getZ() - 8.0);
        helper.runAfterDelay(10, () -> verifyFleeGoalAfterSettling(
                helper, level, zombie, nonPlayerAttacker, player));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:flee", timeoutTicks = 120)
    public void nativeGoalSelectorStopsFleeWhenNavigationEnds(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 32, 32);
        boolean previousFleeEnabled = SoundAttractConfig.COMMON.enableFleeFromUnseenAttackerGoal.get();
        SoundAttractConfig.COMMON.enableFleeFromUnseenAttackerGoal.set(true);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 8, 1, 8);
        SoundAttractConfig.COMMON.enableFleeFromUnseenAttackerGoal.set(previousFleeEnabled);
        ServerLevel level = helper.getLevel();
        net.minecraft.server.level.ServerPlayer player = makeTrackedSurvivalPlayer(level, "flee-selector-gametest");
        player.setPos(zombie.getX(), zombie.getY(), zombie.getZ() - 8.0);
        helper.runAfterDelay(3, () -> {
            var selector = ((com.example.soundattract.mixin.MobAccessor) zombie).getGoalSelector();
            var flee = selector.getAvailableGoals().stream()
                    .filter(wrapped -> wrapped.getGoal() instanceof com.example.soundattract.ai.FleeFromUnseenAttackerGoal)
                    .findFirst().orElse(null);
            if (flee == null) {
                helper.fail("Entity-load integration did not attach the configured flee goal");
                return;
            }
            for (var wrapped : new java.util.ArrayList<>(selector.getAvailableGoals())) {
                if (wrapped.getGoal() != flee.getGoal()) selector.removeGoal(wrapped.getGoal());
            }
            boolean previousStealthEnabled = SoundAttractConfig.COMMON.enableStealthMechanics.get();
            boolean previousTieredStealth = SoundAttractConfig.COMMON.enableTieredStealthPerformance.get();
            double previousStandingRange = SoundAttractConfig.COMMON.standingDetectionRangePlayer.get();
            double previousMaximumRange = SoundAttractConfig.COMMON.maxStealthDetectionRange.get();
            boolean started;
            try {
                SoundAttractConfig.COMMON.enableStealthMechanics.set(true);
                SoundAttractConfig.COMMON.enableTieredStealthPerformance.set(false);
                SoundAttractConfig.COMMON.standingDetectionRangePlayer.set(24.0);
                SoundAttractConfig.COMMON.maxStealthDetectionRange.set(64.0);
                zombie.setYRot(0.0F);
                zombie.setXRot(0.0F);
                player.setPos(zombie.getX(), zombie.getY(), zombie.getZ() - 8.0);
                zombie.setLastHurtByMob(player);
                for (int attempt = 0; attempt < 40 && !flee.isRunning(); attempt++) {
                    selector.tick();
                }
                started = flee.isRunning() && zombie.getNavigation().getPath() != null
                        && zombie.getNavigation().getPath().canReach();
                // An unseen attacker remains a valid flee trigger after arrival, so expose
                // the registered player before ending navigation to prevent an intentional replan.
                player.setPos(zombie.getX(), zombie.getY(), zombie.getZ() + 8.0);
                boolean attackerNowVisible = com.example.soundattract.event.StealthDetectionEvents
                        .canMobDetectPlayer(zombie, player);
                zombie.getNavigation().stop();
                for (int attempt = 0; attempt < 10 && flee.isRunning(); attempt++) {
                    selector.tick();
                }
                if (!attackerNowVisible) {
                    helper.fail("Flee selector fixture did not make its in-level attacker detectable before navigation stopped");
                    return;
                }
            } finally {
                SoundAttractConfig.COMMON.enableStealthMechanics.set(previousStealthEnabled);
                SoundAttractConfig.COMMON.enableTieredStealthPerformance.set(previousTieredStealth);
                SoundAttractConfig.COMMON.standingDetectionRangePlayer.set(previousStandingRange);
                SoundAttractConfig.COMMON.maxStealthDetectionRange.set(previousMaximumRange);
                level.getServer().getPlayerList().remove(player);
            }
            boolean stopped = !flee.isRunning() && zombie.getNavigation().isDone();
            if (!started || !stopped) {
                helper.fail("Native flee selector lifecycle mismatch: started=" + started
                        + ", runningAfterNavigationEnded=" + flee.isRunning()
                        + ", navigationDone=" + zombie.getNavigation().isDone()
                        + ", playerAlive=" + player.isAlive() + ", playerCreative=" + player.isCreative()
                        + ", lastHurtByPlayer=" + (zombie.getLastHurtByMob() == player)
                        + ", detectable=" + com.example.soundattract.event.StealthDetectionEvents.canMobDetectPlayer(zombie, player)
                        + ", inFov=" + com.example.soundattract.event.FovEvents.isTargetInFov(zombie, player, true)
                        + ", distance=" + zombie.distanceTo(player)
                        + ", fleePath=" + zombie.getNavigation().getPath());
                return;
            }
            helper.succeed();
        });
    }

    private static void verifyFleeGoalAfterSettling(GameTestHelper helper, ServerLevel level,
            Zombie zombie, Zombie nonPlayerAttacker, net.minecraft.server.level.ServerPlayer player) {
        boolean previousStealthEnabled = SoundAttractConfig.COMMON.enableStealthMechanics.get();
        boolean previousTieredStealth = SoundAttractConfig.COMMON.enableTieredStealthPerformance.get();
        double previousStandingRange = SoundAttractConfig.COMMON.standingDetectionRangePlayer.get();
        zombie.setYRot(0.0F);
        zombie.setXRot(0.0F);
            player.setPos(zombie.getX(), zombie.getY(), zombie.getZ() - 8.0);
        com.example.soundattract.ai.FleeFromUnseenAttackerGoal goal =
                new com.example.soundattract.ai.FleeFromUnseenAttackerGoal(zombie, 1.0);

        boolean unseenPlayerAccepted;
        boolean fleePathInstalled;
        boolean visiblePlayerAccepted;
        boolean nonPlayerAccepted;
        boolean attackerRetained;
        boolean unseenDetectable;
        boolean unseenInFov;
        double xrayRange;
        Vec3 fleeDestination = null;
        String destinationSupport = "unknown";
        String vanillaNeighborPath = "not-run";
        String vanillaSampledPath = "not-run";
        String vanillaMoveTo = "not-run";
        String navigationFixtureState = "not-run";
        try {
            // This test has its own batch so it can force the feature's full
            // detection path without racing other scenarios that tune config.
            SoundAttractConfig.COMMON.enableStealthMechanics.set(true);
            SoundAttractConfig.COMMON.enableTieredStealthPerformance.set(false);
            SoundAttractConfig.COMMON.standingDetectionRangePlayer.set(24.0);
            zombie.setLastHurtByMob(player);
            attackerRetained = zombie.getLastHurtByMob() == player;
            unseenDetectable = com.example.soundattract.event.StealthDetectionEvents
                    .canMobDetectPlayer(zombie, player);
            unseenInFov = com.example.soundattract.event.FovEvents.isTargetInFov(zombie, player, true);
            xrayRange = com.example.soundattract.event.StealthDetectionEvents.getEffectiveXrayRange(zombie);
            unseenPlayerAccepted = false;
            for (int attempt = 0; attempt < 40 && !unseenPlayerAccepted; attempt++) {
                unseenPlayerAccepted = goal.canUse();
            }
            if (unseenPlayerAccepted) {
                goal.start();
            }
            net.minecraft.world.entity.ai.navigation.PathNavigation navigation = zombie.getNavigation();
            fleePathInstalled = unseenPlayerAccepted && navigation.getPath() != null
                    && navigation.getPath().canReach();
            fleeDestination = fleeDestinationForDiagnostics(goal);
            // Establish whether this is a bad flee sample or a generally broken
            // navigation fixture. The adjacent floor coordinate is deliberately
            // deterministic and lies inside the same broad, flat test floor.
            BlockPos knownNeighbor = zombie.blockPosition().offset(0, 0, 5);
            var neighborPath = navigation.createPath(knownNeighbor, 0);
            vanillaNeighborPath = neighborPath == null ? "null" : "canReach=" + neighborPath.canReach()
                    + ",target=" + neighborPath.getTarget() + ",nodes=" + neighborPath.getNodeCount();
            navigationFixtureState = "mobBlock=" + zombie.blockPosition()
                    + ",onGround=" + zombie.onGround() + ",noAi=" + zombie.isNoAi()
                    + ",navigation=" + navigation.getClass().getName()
                    + ",knownNeighbor=" + knownNeighbor
                    + ",neighborLoaded=" + level.hasChunkAt(knownNeighbor)
                    + ",neighborStable=" + navigation.isStableDestination(knownNeighbor)
                    + ",neighborSupport=" + level.getBlockState(knownNeighbor.below())
                    + ",walkableMalus=" + zombie.getPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WALKABLE);
            if (fleeDestination != null) {
                var point = BlockPos.containing(fleeDestination);
                destinationSupport = level.getBlockState(point.below()).toString() + "/" + level.getBlockState(point).toString();
                var sampledPath = navigation.createPath(point, 0);
                vanillaSampledPath = sampledPath == null ? "null" : "canReach=" + sampledPath.canReach()
                        + ",target=" + sampledPath.getTarget() + ",nodes=" + sampledPath.getNodeCount();
                boolean accepted = navigation.moveTo(point.getX(), point.getY(), point.getZ(), 1.0);
                vanillaMoveTo = "accepted=" + accepted + ",path=" + navigation.getPath();
            }
            goal.stop();
            navigation.stop();

            player.setPos(zombie.getX(), zombie.getY(), zombie.getZ() + 8.0);
            zombie.setLastHurtByMob(player);
            visiblePlayerAccepted = goal.canUse();
            zombie.setLastHurtByMob(nonPlayerAttacker);
            nonPlayerAccepted = goal.canUse();
        } finally {
            SoundAttractConfig.COMMON.enableStealthMechanics.set(previousStealthEnabled);
            SoundAttractConfig.COMMON.enableTieredStealthPerformance.set(previousTieredStealth);
            SoundAttractConfig.COMMON.standingDetectionRangePlayer.set(previousStandingRange);
            level.getServer().getPlayerList().remove(player);
        }

        if (!unseenPlayerAccepted || !fleePathInstalled || visiblePlayerAccepted || nonPlayerAccepted) {
            helper.fail("Flee goal eligibility mismatch: unseen=" + unseenPlayerAccepted
                    + ", pathInstalled=" + fleePathInstalled + ", visible=" + visiblePlayerAccepted
                    + ", nonPlayer=" + nonPlayerAccepted + ", attackerRetained=" + attackerRetained
                    + ", unseenDetectable=" + unseenDetectable
                    + ", unseenInFov=" + unseenInFov + ", xrayRange=" + xrayRange
                    + ", mobPos=" + zombie.position() + ", fleeDestination=" + fleeDestination
                    + ", destinationDelta=" + (fleeDestination == null ? "unknown" : zombie.position().distanceTo(fleeDestination))
                    + ", destinationSupport=" + destinationSupport
                    + ", vanillaNeighborPath=" + vanillaNeighborPath
                    + ", vanillaSampledPath=" + vanillaSampledPath
                    + ", vanillaMoveTo=" + vanillaMoveTo
                    + ", navigationFixtureState=" + navigationFixtureState
                    + ", navPath=" + zombie.getNavigation().getPath()
                    + ", bypassed=" + SoundAttractConfig.isStealthBypassed(zombie)
                    + ", perfTier=" + com.example.soundattract.event.StealthDetectionEvents.getPerfTier(level)
                    + ", highWeight=" + com.example.soundattract.ai.MobGroupManager.isMobInHighWeightOverride(zombie));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void soundRecordsExpireAndRespectDimension(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 3);
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        BlockPos soundPos = helper.absolutePos(new BlockPos(3, 1, 1));
        String dimension = level.dimension().location().toString();
        UUID source = UUID.randomUUID();
        String soundId = "soundattract:virtual#" + source + "/GameTestExpiry/" + soundPos.asLong();

        SoundTracker.addVirtualSound(soundPos, dimension, 8.0, 2.0, 1, source, "GameTestExpiry");
        boolean wrongDimensionVisible = SoundTracker.getNearbySounds(level, "soundattract:gametest_other", soundPos, null)
                .stream().anyMatch(record -> soundId.equals(record.soundId));
        SoundTracker.tick();
        boolean expiredRecordVisible = SoundTracker.getNearbySounds(level, dimension, soundPos, null)
                .stream().anyMatch(record -> soundId.equals(record.soundId));
        SoundTracker.removeSoundAt(soundPos, dimension);

        if (wrongDimensionVisible) {
            helper.fail("Sound records leaked into a different dimension index");
            return;
        }
        if (expiredRecordVisible) {
            helper.fail("A one-tick sound remained indexed after the production tracker tick");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:tracker_score", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void soundScoringPrefersHigherWeightOverDistance(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 3);
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        String dimension = level.dimension().location().toString();
        BlockPos near = helper.absolutePos(new BlockPos(2, 1, 1));
        BlockPos far = helper.absolutePos(new BlockPos(6, 1, 1));
        BlockPos inRange = helper.absolutePos(new BlockPos(2, 1, 1));
        BlockPos outsideRange = helper.absolutePos(new BlockPos(6, 1, 1));
        for (int x = 1; x <= 6; x++) helper.setBlock(x, 1, 1, Blocks.AIR);
        int previousSoundCap = SoundAttractConfig.COMMON.maxSoundsTracked.get();
        boolean weightSelectionPassed = false;
        boolean rangeSelectionPassed = false;
        String rangeFailure = "";
        SoundTracker.SoundRecord weightSelection = null;
        double[] farMuffling = null;
        SoundAttractConfig.COMMON.maxSoundsTracked.set(Math.max(previousSoundCap,
                SoundTracker.getTrackedSoundCount() + 2));
        try {
            SoundTracker.addVirtualSound(near, dimension, 12.0, 100_000.0, 200, UUID.randomUUID(), "GameTestLow");
            SoundTracker.addVirtualSound(far, dimension, 12.0, 500_000.0, 200, UUID.randomUUID(), "GameTestHigh");
            farMuffling = com.example.soundattract.los.OptimizedLOS.computeMufflingDda(
                    level, far, zombie.blockPosition(), 12.0, 500_000.0, false);
            weightSelection = SoundTracker.findNearestSound(
                    zombie, level, zombie.blockPosition(), zombie.getEyePosition());
            weightSelectionPassed = weightSelection != null && far.equals(weightSelection.pos) && weightSelection.weight >= 500_000.0;

            SoundTracker.removeSoundAt(near, dimension);
            SoundTracker.removeSoundAt(far, dimension);
            SoundTracker.addVirtualSound(inRange, dimension, 4.0, 100_000.0, 200, UUID.randomUUID(), "GameTestRangeIn");
            SoundTracker.addVirtualSound(outsideRange, dimension, 2.0, 1_000_000.0, 200, UUID.randomUUID(), "GameTestRangeOut");
            SoundTracker.SoundRecord rangeSelected = SoundTracker.findNearestSound(
                    zombie, level, zombie.blockPosition(), zombie.getEyePosition());
            rangeSelectionPassed = rangeSelected != null && inRange.equals(rangeSelected.pos);
            if (!rangeSelectionPassed) {
                rangeFailure = rangeSelected == null ? "null" : rangeSelected.soundId + " at " + rangeSelected.pos
                        + " range=" + rangeSelected.range + " weight=" + rangeSelected.weight;
            }
        } finally {
            for (BlockPos pos : List.of(near, far, inRange, outsideRange)) {
                SoundTracker.removeSoundAt(pos, dimension);
            }
            SoundAttractConfig.COMMON.maxSoundsTracked.set(previousSoundCap);
        }

        if (!weightSelectionPassed) {
            helper.fail("Sound scoring chose the nearer weak sound instead of the stronger in-range sound: selected="
                    + (weightSelection == null ? "null" : describeSoundRecords(List.of(weightSelection)))
                    + ", directDda=" + java.util.Arrays.toString(farMuffling)
                    + ", mobPos=" + zombie.blockPosition() + ", farPos=" + far
                    + ", ray=" + java.util.stream.IntStream.rangeClosed(1, 6).mapToObj(x -> {
                        BlockPos pos = helper.absolutePos(new BlockPos(x, 1, 1));
                        BlockPos below = pos.below();
                        BlockPos above = pos.above();
                        return pos + "=" + level.getBlockState(pos) + ",below=" + level.getBlockState(below)
                                + ",above=" + level.getBlockState(above);
                    }).toList());
            return;
        }
        if (!rangeSelectionPassed) {
            helper.fail("Sound scoring selected a record outside the controlled in-range candidate: selected=" + rangeFailure);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:tracker_capacity", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void soundTrackerEnforcesConfiguredCapacityAndEvictsOnlyTheWeakestRecord(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String dimension = level.dimension().location().toString();
        BlockPos firstPos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos secondPos = helper.absolutePos(new BlockPos(2, 1, 1));
        BlockPos rejectedPos = helper.absolutePos(new BlockPos(3, 1, 1));
        BlockPos weakEvictionPos = helper.absolutePos(new BlockPos(4, 1, 1));
        BlockPos strongEvictionPos = helper.absolutePos(new BlockPos(5, 1, 1));
        int previousLimit = SoundAttractConfig.COMMON.maxSoundsTracked.get();
        int baselineCount = SoundTracker.getTrackedSoundCount();
        if (baselineCount + 2 > 65536) {
            helper.fail("Cannot isolate capacity fixture near the hard runtime sound ceiling: baseline=" + baselineCount);
            return;
        }

        boolean firstAccepted;
        boolean secondAccepted;
        boolean weakerRecordRejected;
        boolean sizeStayedAtLimit;
        boolean weakestFixtureAccepted;
        boolean strongerFixtureAccepted;
        boolean weakestFixtureEvicted;
        boolean sizeStayedAfterEviction;
        int sizeAfterCleanup;
        try {
            // GameTest methods run synchronously on the server thread. The
            // temporary cap is restored before this test yields to another test.
            SoundAttractConfig.COMMON.maxSoundsTracked.set(baselineCount + 2);
            SoundTracker.addVirtualSound(firstPos, dimension, 2.0, 100000.0, 200,
                    UUID.randomUUID(), "GameTestCapacityFirst");
            SoundTracker.addVirtualSound(secondPos, dimension, 2.0, 200000.0, 200,
                    UUID.randomUUID(), "GameTestCapacitySecond");
            firstAccepted = containsSoundAt(level, dimension, firstPos, "GameTestCapacityFirst");
            secondAccepted = containsSoundAt(level, dimension, secondPos, "GameTestCapacitySecond");
            int countAtLimit = SoundTracker.getTrackedSoundCount();

            double currentWeakest = SoundTracker.getTrackedSoundsSnapshot().stream()
                    .mapToDouble(record -> record.weight + record.range / 1000.0).min().orElse(0.0);
            SoundTracker.addVirtualSound(rejectedPos, dimension, 0.0, Math.nextDown(currentWeakest), 200,
                    UUID.randomUUID(), "GameTestCapacityRejected");
            weakerRecordRejected = !containsSoundAt(level, dimension, rejectedPos, "GameTestCapacityRejected");
            sizeStayedAtLimit = countAtLimit == baselineCount + 2
                    && SoundTracker.getTrackedSoundCount() == baselineCount + 2;

            SoundTracker.removeSoundAt(firstPos, dimension);
            SoundTracker.removeSoundAt(secondPos, dimension);
            SoundAttractConfig.COMMON.maxSoundsTracked.set(baselineCount + 1);
            double baselineWeakest = SoundTracker.getTrackedSoundsSnapshot().stream()
                    .mapToDouble(record -> record.weight + record.range / 1000.0).min().orElse(0.0);
            double testWeakWeight = Math.nextDown(baselineWeakest);
            SoundTracker.addVirtualSound(weakEvictionPos, dimension, 0.0, testWeakWeight, 200,
                    UUID.randomUUID(), "GameTestCapacityWeakEviction");
            weakestFixtureAccepted = containsSoundAt(level, dimension, weakEvictionPos, "GameTestCapacityWeakEviction");
            SoundTracker.addVirtualSound(strongEvictionPos, dimension, 0.0, Double.MAX_VALUE, 200,
                    UUID.randomUUID(), "GameTestCapacityStrongEviction");
            strongerFixtureAccepted = containsSoundAt(level, dimension, strongEvictionPos, "GameTestCapacityStrongEviction");
            weakestFixtureEvicted = !containsSoundAt(level, dimension, weakEvictionPos, "GameTestCapacityWeakEviction");
            sizeStayedAfterEviction = SoundTracker.getTrackedSoundCount() == baselineCount + 1;
        } finally {
            SoundTracker.removeSoundAt(firstPos, dimension);
            SoundTracker.removeSoundAt(secondPos, dimension);
            SoundTracker.removeSoundAt(rejectedPos, dimension);
            SoundTracker.removeSoundAt(weakEvictionPos, dimension);
            SoundTracker.removeSoundAt(strongEvictionPos, dimension);
            SoundAttractConfig.COMMON.maxSoundsTracked.set(previousLimit);
            sizeAfterCleanup = SoundTracker.getTrackedSoundCount();
        }

        if (!firstAccepted || !secondAccepted || !weakerRecordRejected || !sizeStayedAtLimit
                || !weakestFixtureAccepted || !strongerFixtureAccepted || !weakestFixtureEvicted
                || !sizeStayedAfterEviction || sizeAfterCleanup != baselineCount) {
            helper.fail("SoundTracker capacity/cleanup contract failed: accepted=" + firstAccepted + "/" + secondAccepted
                    + ", weakerRejected=" + weakerRecordRejected + ", limitHeld=" + sizeStayedAtLimit
                    + ", weakCandidate=" + weakestFixtureAccepted + ", strongCandidate=" + strongerFixtureAccepted
                    + ", weakestEvicted=" + weakestFixtureEvicted + ", limitAfterEviction=" + sizeStayedAfterEviction
                    + ", baseline=" + baselineCount + ", afterCleanup=" + sizeAfterCleanup);
            return;
        }
        helper.succeed();
    }

    private static boolean containsSoundAt(ServerLevel level, String dimension, BlockPos pos, String animationClass) {
        return SoundTracker.getNearbySounds(level, dimension, pos, null).stream()
                .filter(SoundTracker.VirtualSoundRecord.class::isInstance)
                .map(SoundTracker.VirtualSoundRecord.class::cast)
                .anyMatch(record -> pos.equals(record.pos) && animationClass.equals(record.animationClass));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:tracker_occluded_score", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void soundScoringUsesOccludedEffectiveWeightForSelection(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 4);
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        zombie.setNoAi(true);
        String dimension = level.dimension().location().toString();
        BlockPos occluded = helper.absolutePos(new BlockPos(5, 1, 1));
        BlockPos clear = helper.absolutePos(new BlockPos(1, 1, 3));
        helper.setBlock(3, 1, 1, Blocks.STONE);
        helper.setBlock(3, 2, 1, Blocks.STONE);
        helper.setBlock(3, 3, 1, Blocks.STONE);
        double previousNoveltyBonus = SoundAttractConfig.COMMON.soundNoveltyBonusWeight.get();
        int previousSoundCap = SoundAttractConfig.COMMON.maxSoundsTracked.get();
        UUID occludedOwner = UUID.randomUUID();
        UUID clearOwner = UUID.randomUUID();
        SoundAttractConfig.COMMON.maxSoundsTracked.set(Math.max(previousSoundCap,
                SoundTracker.getTrackedSoundCount() + 2));
        SoundTracker.addVirtualSound(occluded, dimension, 20.0, 10.0, 200, occludedOwner, "GameTestOccludedScore");
        SoundTracker.addVirtualSound(clear, dimension, 10.0, 9.0, 200, clearOwner, "GameTestClearScore");
        try {
            SoundAttractConfig.COMMON.soundNoveltyBonusWeight.set(0.0);
            // Other GameTests run in the same server tick and share the LOS budget.
            // Start this assertion with a clean budget so unrelated tests cannot
            // make the occluded candidate appear to have its original weight.
            com.example.soundattract.los.OptimizedLOS.resetMufflingBudget();
            SoundTracker.SoundRecord selected = SoundTracker.findNearestSound(
                    zombie, level, zombie.blockPosition(), zombie.getEyePosition());
            if (selected == null || !clear.equals(selected.pos)
                    || Math.abs(selected.weight - 9.0) > 0.001) {
                helper.fail("Sound selection ignored muffling-adjusted scores: selected="
                        + (selected == null ? "null" : selected.soundId + " at " + selected.pos
                                + " with effective weight " + selected.weight));
                return;
            }
        } finally {
            SoundTracker.removeSoundAt(occluded, dimension);
            SoundTracker.removeSoundAt(clear, dimension);
            SoundAttractConfig.COMMON.soundNoveltyBonusWeight.set(previousNoveltyBonus);
            SoundAttractConfig.COMMON.maxSoundsTracked.set(previousSoundCap);
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void soundOcclusionReducesRangeAndWeight(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 3);
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos listener = helper.absolutePos(new BlockPos(5, 2, 1));
        double[] unobstructed = com.example.soundattract.los.OptimizedLOS.computeMufflingDda(
                level, source, listener, 60.0, 10.0, false);
        helper.setBlock(3, 2, 1, Blocks.STONE);
        double[] obstructed = com.example.soundattract.los.OptimizedLOS.computeMufflingDda(
                level, source, listener, 60.0, 10.0, false);

        if (Math.abs(unobstructed[0] - 60.0) > 0.0001 || Math.abs(unobstructed[1] - 10.0) > 0.0001) {
            helper.fail("Clear sound path was unexpectedly muffled: range=" + unobstructed[0]
                    + ", weight=" + unobstructed[1] + ", path="
                    + java.util.stream.IntStream.rangeClosed(2, 5).mapToObj(x -> {
                        BlockPos pos = helper.absolutePos(new BlockPos(x, 2, 1));
                        var state = level.getBlockState(pos);
                        return pos + "=" + state + " air=" + state.isAir() + " nonSolid="
                                + SoundTracker.isCustomNonSolid(state, state.getBlock(), level, pos);
                    }).toList() + ", factors="
                    + SoundAttractConfig.COMMON.mufflingFactorAir.get() + "/"
                    + SoundAttractConfig.COMMON.mufflingFactorNonSolid.get());
            return;
        }
        if (!(obstructed[0] < unobstructed[0]) || !(obstructed[1] < unobstructed[1])) {
            helper.fail("A solid wall did not reduce sound range and weight: range=" + obstructed[0]
                    + ", weight=" + obstructed[1]);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void scentPersistsDecaysAndReplacesWindTrail(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 3);
        ServerLevel level = helper.getLevel();
        // This lifecycle test uses a private manager instance so cleanup cannot
        // erase scent nodes owned by another concurrently running GameTest.
        ScentManager scents = new ScentManager(level);
        UUID owner = UUID.randomUUID();
        BlockPos persistentPos = helper.absolutePos(new BlockPos(2, 1, 1));
        ScentNode persistent = new ScentNode(Vec3.atCenterOf(persistentPos), level.getGameTime(), 0.75f,
                owner, ScentSourceType.PLAYER_WALK);
        scents.addScentNode(persistent);
        if (scents.getAllNodes().size() != 1) {
            helper.fail("Scent producer did not make a queryable persistent node");
            return;
        }

        ScentManager restored = new ScentManager(level);
        restored.deserializeNBT(scents.serializeNBT());
        if (restored.getAllNodes().size() != 1
                || restored.getAllNodes().get(0).getSourceType() != ScentSourceType.PLAYER_WALK) {
            helper.fail("Scent NBT save/reload lost its node or source type");
            return;
        }

        ScentNode windA = new ScentNode(Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 1, 1))), level.getGameTime(), 0.4f,
                owner, ScentSourceType.PLAYER_WIND);
        ScentNode windB = new ScentNode(Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 1, 1))), level.getGameTime(), 0.3f,
                owner, ScentSourceType.PLAYER_WIND);
        scents.replaceWindNodes(owner, List.of(windA));
        scents.replaceWindNodes(owner, List.of(windB));
        ChunkPos testChunk = new ChunkPos(helper.absolutePos(new BlockPos(0, 0, 0)));
        long windCount = scents.getNodesInArea(testChunk).stream()
                .filter(node -> node.getSourceType() == ScentSourceType.PLAYER_WIND).count();
        scents.clearWindNodes(owner);
        scents.cleanupExpired(level.getGameTime() + 10_000L, 100L);
        int persistentCountAfterExpiry = scents.getAllNodes().size();
        boolean windRemains = scents.getNodesInArea(testChunk).stream()
                .anyMatch(node -> node.getSourceType() == ScentSourceType.PLAYER_WIND);
        if (windCount != 1L) {
            helper.fail("Wind update accumulated stale parcels instead of replacing the owner's trail");
            return;
        }
        if (windRemains) {
            helper.fail("Clearing a wind trail left transient nodes queryable");
            return;
        }
        if (persistentCountAfterExpiry != 0) {
            helper.fail("Expired persistent scent nodes were not removed by cleanup");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void scentOwnerCleanupRemovesPersistentAndWindNodes(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 3);
        ServerLevel level = helper.getLevel();
        ScentManager scents = new ScentManager(level);
        UUID removedOwner = UUID.randomUUID();
        UUID retainedOwner = UUID.randomUUID();
        BlockPos removedPersistentPos = helper.absolutePos(new BlockPos(2, 1, 1));
        BlockPos removedWindPos = helper.absolutePos(new BlockPos(3, 1, 1));
        BlockPos retainedPos = helper.absolutePos(new BlockPos(4, 1, 1));
        long now = level.getGameTime();
        scents.addScentNode(new ScentNode(Vec3.atCenterOf(removedPersistentPos), now, 0.8f,
                removedOwner, ScentSourceType.PLAYER_WALK));
        scents.replaceWindNodes(removedOwner, List.of(new ScentNode(Vec3.atCenterOf(removedWindPos), now, 0.7f,
                removedOwner, ScentSourceType.PLAYER_WIND)));
        scents.addScentNode(new ScentNode(Vec3.atCenterOf(retainedPos), now, 0.6f,
                retainedOwner, ScentSourceType.ARROW_PATH));

        scents.removeOwnerNodes(removedOwner);
        List<ScentNode> remaining = scents.getNodesInArea(new ChunkPos(removedPersistentPos));
        boolean removedOwnerGone = remaining.stream().noneMatch(node -> removedOwner.equals(node.getOwnerUUID()));
        boolean otherOwnerRetained = remaining.stream().anyMatch(node -> retainedOwner.equals(node.getOwnerUUID())
                && node.getSourceType() == ScentSourceType.ARROW_PATH);
        scents.clear();
        if (!removedOwnerGone || !otherOwnerRetained) {
            helper.fail("Owner cleanup must remove persistent and transient nodes for one UUID only: remaining="
                    + remaining.stream().map(node -> node.getSourceType() + "@" + node.getBlockPos()
                            + " owner=" + node.getOwnerUUID()).toList());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void scentDecayPreservesExactBoundaryAndRemovesOlderPersistentAndWindNodes(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 3);
        ServerLevel level = helper.getLevel();
        ScentManager scents = new ScentManager(level);
        UUID owner = UUID.randomUUID();
        long now = level.getGameTime();
        BlockPos freshPos = helper.absolutePos(new BlockPos(2, 1, 1));
        BlockPos stalePos = helper.absolutePos(new BlockPos(3, 1, 1));
        BlockPos freshWindPos = helper.absolutePos(new BlockPos(4, 1, 1));
        BlockPos staleWindPos = helper.absolutePos(new BlockPos(5, 1, 1));
        scents.addScentNode(new ScentNode(Vec3.atCenterOf(freshPos), now - 10, 0.8f,
                owner, ScentSourceType.PLAYER_WALK));
        scents.addScentNode(new ScentNode(Vec3.atCenterOf(stalePos), now - 11, 0.8f,
                owner, ScentSourceType.ARROW_PATH));
        scents.replaceWindNodes(owner, List.of(
                new ScentNode(Vec3.atCenterOf(freshWindPos), now - 10, 0.7f, owner, ScentSourceType.PLAYER_WIND),
                new ScentNode(Vec3.atCenterOf(staleWindPos), now - 11, 0.7f, owner, ScentSourceType.PLAYER_WIND)));

        scents.cleanupExpired(now, 10);
        List<ScentNode> persistent = scents.getAllNodes();
        List<ScentNode> wind = scents.getNodesInArea(new ChunkPos(freshWindPos)).stream()
                .filter(node -> node.getSourceType() == ScentSourceType.PLAYER_WIND).toList();
        boolean persistentBoundaryCorrect = persistent.size() == 1
                && persistent.get(0).getBlockPos().equals(freshPos);
        boolean windBoundaryCorrect = wind.size() == 1 && wind.get(0).getBlockPos().equals(freshWindPos);
        scents.clear();
        if (!persistentBoundaryCorrect || !windBoundaryCorrect) {
            helper.fail("Scent age boundary must retain age=maxDuration and remove older nodes: persistent="
                    + persistent.stream().map(node -> node.getSourceType() + "@" + node.getBlockPos()).toList()
                    + ", wind=" + wind.stream().map(node -> node.getSourceType() + "@" + node.getBlockPos()).toList());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void scentConsumerSelectsFreshTrailAndRejectsDecayedTrail(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 3);
        ServerLevel level = helper.getLevel();
        long now = level.getGameTime();
        long maxAge = SoundAttractConfig.COMMON.scentNodeDurationTicks.get();
        UUID freshOwner = UUID.randomUUID();
        UUID staleOwner = UUID.randomUUID();
        BlockPos freshPos = helper.absolutePos(new BlockPos(5, 1, 1));
        BlockPos stalePos = helper.absolutePos(new BlockPos(6, 1, 1));

        ScentManager freshScents = new ScentManager(level);
        freshScents.addScentNode(new ScentNode(Vec3.atCenterOf(freshPos), now, 0.8f,
                freshOwner, ScentSourceType.PLAYER_WALK));
        Zombie freshFollower = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        FollowScentGoal freshGoal = new FollowScentGoal(freshFollower, freshScents);
        boolean freshSelected = freshGoal.acquireScentForTesting()
                && freshPos.equals(BlockPos.containing(freshGoal.getTargetPosition()));

        ScentManager staleScents = new ScentManager(level);
        staleScents.addScentNode(new ScentNode(Vec3.atCenterOf(stalePos), now - maxAge - 1L, 0.8f,
                staleOwner, ScentSourceType.PLAYER_WALK));
        Zombie staleFollower = helper.spawn(EntityType.ZOMBIE, 1, 1, 2);
        FollowScentGoal staleGoal = new FollowScentGoal(staleFollower, staleScents);
        boolean staleRejected = !staleGoal.acquireScentForTesting() && staleGoal.getTargetPosition() == null;

        freshScents.clear();
        staleScents.clear();
        if (!freshSelected || !staleRejected) {
            helper.fail("Scent consumer freshness filter mismatch: freshSelected=" + freshSelected
                    + ", freshTarget=" + freshGoal.getTargetPosition() + ", staleRejected=" + staleRejected
                    + ", staleTarget=" + staleGoal.getTargetPosition() + ", maxAge=" + maxAge);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void scentTrailCanBeAcquiredAndStoppedByZombie(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 3);
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        // Isolate this goal from scent producers running in parallel GameTests.
        ScentManager scents = new ScentManager(level);
        BlockPos trailPos = helper.absolutePos(new BlockPos(6, 1, 1));
        UUID owner = UUID.randomUUID();
        scents.addScentNode(new ScentNode(Vec3.atCenterOf(trailPos), level.getGameTime(), 0.8f,
                owner, ScentSourceType.PLAYER_WALK));
        helper.runAfterDelay(2, () -> attemptScentGoal(helper, new FollowScentGoal(zombie, scents),
                zombie, scents, owner, trailPos, 100));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void zombieAcquiresTransientWindScentTrail(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 3);
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        ScentManager scents = new ScentManager(level);
        BlockPos trailPos = helper.absolutePos(new BlockPos(6, 1, 1));
        UUID owner = UUID.randomUUID();
        ScentNode wind = new ScentNode(Vec3.atCenterOf(trailPos), level.getGameTime(), 0.8f,
                owner, ScentSourceType.PLAYER_WIND);
        scents.replaceWindNodes(owner, List.of(wind));
        FollowScentGoal goal = new FollowScentGoal(zombie, scents);
        helper.runAfterDelay(2, () -> attemptTransientWindScentConsumer(
                helper, zombie, scents, owner, trailPos, goal, 60));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void zombieAcquiresPlayerAndMobProjectileScentSourceTypes(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 8, 4);
        ServerLevel level = helper.getLevel();
        Zombie consumer = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        List<Zombie> consumers = List.of(consumer, consumer, consumer, consumer);
        ScentManager scents = new ScentManager(level);
        List<ScentSourceType> sourceTypes = List.of(ScentSourceType.ARROW_ORIGIN, ScentSourceType.ARROW_PATH,
                ScentSourceType.MOB_PROJECTILE_ORIGIN, ScentSourceType.MOB_PROJECTILE_PATH);
        helper.runAfterDelay(2, () -> {
            // This fixture drives production source selection directly, avoiding
            // randomized polling and the shared per-tick scan budget used by live mobs.
            removeAllGoalsForFixture(consumer);
            attemptProjectileScentConsumers(helper, consumers, scents, sourceTypes, 0);
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void scentFollowerStaysWithAcquiredOwnerWhenAnotherTrailIsFresher(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 3);
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        zombie.setNoAi(true);
        ScentManager scents = new ScentManager(level);
        UUID acquiredOwner = UUID.randomUUID();
        UUID distractorOwner = UUID.randomUUID();
        BlockPos firstNode = helper.absolutePos(new BlockPos(2, 1, 1));
        BlockPos nextOwnedNode = helper.absolutePos(new BlockPos(4, 1, 1));
        BlockPos fresherOtherOwnerNode = helper.absolutePos(new BlockPos(6, 1, 1));
        long now = level.getGameTime();
        scents.addScentNode(new ScentNode(Vec3.atCenterOf(firstNode), now, 0.8f,
                acquiredOwner, ScentSourceType.PLAYER_WALK));
        FollowScentGoal goal = new FollowScentGoal(zombie, scents);
        boolean initiallyAcquired = goal.acquireScentForTesting();
        if (!initiallyAcquired || !firstNode.equals(BlockPos.containing(goal.getTargetPosition()))) {
            scents.clear();
            helper.fail("Follower did not establish its first scent owner before the competing trail was added");
            return;
        }

        scents.addScentNode(new ScentNode(Vec3.atCenterOf(nextOwnedNode), now + 1, 0.7f,
                acquiredOwner, ScentSourceType.PLAYER_WALK));
        scents.addScentNode(new ScentNode(Vec3.atCenterOf(fresherOtherOwnerNode), now + 2, 1.0f,
                distractorOwner, ScentSourceType.PLAYER_WALK));
        boolean followedSameOwner = goal.acquireScentForTesting();
        BlockPos selectedAfterUpdate = goal.getTargetPosition() == null
                ? null : BlockPos.containing(goal.getTargetPosition());
        goal.stop();
        scents.clear();
        if (!followedSameOwner || !nextOwnedNode.equals(selectedAfterUpdate)) {
            helper.fail("Follower switched to or ignored a newer different-owner trail: acquiredOwnerNode="
                    + nextOwnedNode + ", otherOwnerNode=" + fresherOtherOwnerNode
                    + ", selected=" + selectedAfterUpdate);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void scentQueryFiltersByRadiusAndFreshness(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 8, 3);
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        // Inject a private manager so this result is deterministic even though
        // all GameTests in the batch share a ServerLevel and its component.
        ScentManager scents = new ScentManager(level);
        long now = level.getGameTime();
        BlockPos nearby = helper.absolutePos(new BlockPos(3, 1, 1));
        BlockPos stale = helper.absolutePos(new BlockPos(4, 1, 1));
        scents.addScentNode(new ScentNode(Vec3.atCenterOf(nearby), now, 0.65f, UUID.randomUUID(), ScentSourceType.PLAYER_WALK));
        scents.addScentNode(new ScentNode(Vec3.atCenterOf(stale), now - 500, 0.99f, UUID.randomUUID(), ScentSourceType.ARROW_PATH));
        com.example.soundattract.util.ScentQueryCache.clear();
        var result = ScentQueryHelper.computeFreshScentInfo(zombie, scents, 100, 5.0);
        if (!result.hasFresh() || Math.abs(result.strongestStrength() - 0.65) > 0.0001) {
            helper.fail("Fresh-scent query included an expired node or missed the nearby live node: " + result);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void pursuitHazardsRequireStableGround(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 4, 4);
        ServerLevel level = helper.getLevel();
        BlockPos safeFeet = helper.absolutePos(new BlockPos(1, 1, 1));
        boolean safe = HazardClassifier.isSafeGround(level, safeFeet);
        helper.setBlock(3, 1, 1, Blocks.LAVA);
        HazardClassifier.clear();
        BlockPos lavaFeet = helper.absolutePos(new BlockPos(3, 1, 1));
        HazardClassifier.HazardType lava = HazardClassifier.classify(level, lavaFeet);
        Zombie routerMob = helper.spawn(EntityType.ZOMBIE, 1, 1, 3);
        Vec3 hazardWaypoint = com.example.soundattract.pathfinding.NodeRouter.getWaypoint(
                routerMob, Vec3.atCenterOf(lavaFeet));
        helper.setBlock(2, 0, 2, Blocks.AIR);
        HazardClassifier.clear();
        boolean edgeSafe = HazardClassifier.isSafeGround(level, helper.absolutePos(new BlockPos(2, 1, 2)));

        if (!safe) {
            helper.fail("A fully supported, clear floor was classified as unsafe at " + safeFeet
                    + " with support=" + level.getBlockState(safeFeet.below()));
            return;
        }
        if (lava != HazardClassifier.HazardType.LAVA) {
            helper.fail("Lava in the pursuer's feet cell was not classified as a lava hazard");
            return;
        }
        if (!routerMob.position().equals(hazardWaypoint)) {
            helper.fail("NodeRouter did not reject a waypoint intersecting a lava hazard: " + hazardWaypoint);
            return;
        }
        if (edgeSafe) {
            helper.fail("Safe-ground check accepted an unsupported adjacent edge cell");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void pursuitNavigationAcceptsSafePathAndRejectsNonFiniteTarget(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 8, 3);
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        BlockPos destinationPos = helper.absolutePos(new BlockPos(3, 1, 1));
        Vec3 destination = Vec3.atCenterOf(destinationPos);
        helper.runAfterDelay(2, () -> {
            net.minecraft.world.level.pathfinder.Path diagnosticPath = zombie.getNavigation().createPath(destinationPos, 0);
            boolean accepted = PursuitNavigation.maybeMoveTo(zombie, destination, 1.0);
            boolean pathInstalled = zombie.getNavigation().getPath() != null;
            boolean invalidAccepted = PursuitNavigation.maybeMoveTo(zombie, new Vec3(Double.NaN, 1.0, 1.0), 1.0);
            boolean pursuitMarked = com.example.soundattract.pathfinding.HazardClassifier.isActivePursuer(zombie);
            if (!accepted || !pathInstalled || invalidAccepted || !pursuitMarked) {
                helper.fail("Pursuit navigation state: accepted=" + accepted + ", path=" + pathInstalled
                        + ", candidatePath=" + (diagnosticPath == null ? "null" : diagnosticPath.getNodeCount())
                        + ", invalidAccepted=" + invalidAccepted + ", pursuitMarked=" + pursuitMarked
                        + ", mob=" + zombie.position() + ", target=" + destination
                        + ", targetGround=" + level.getBlockState(destinationPos.below())
                        + ", walkable=" + com.example.soundattract.pathfinding.NodeRouter.isWalkable(zombie, destination));
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void awarenessSnapshotRoundTripsBoundedState(GameTestHelper helper) {
        List<AwarenessSnapshotMessage.Alert> overCapacity = new java.util.ArrayList<>();
        for (int index = 0; index < 140; index++) {
            overCapacity.add(new AwarenessSnapshotMessage.Alert(UUID.randomUUID(), index, 3, 0x123456, 0.65f));
        }
        AwarenessSnapshotMessage original = new AwarenessSnapshotMessage(
                1.4f,
                -0.2f,
                9,
                true,
                overCapacity);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        AwarenessSnapshotMessage.encode(original, buffer);
        AwarenessSnapshotMessage decoded = AwarenessSnapshotMessage.decode(buffer);

        if (decoded.visibility() != 1.0f || decoded.noise() != 0.0f || decoded.scent() != 5
                || !decoded.windshielded() || decoded.alerts().size() != 128
                || !overCapacity.get(0).mobId().equals(decoded.alerts().get(0).mobId())
                || decoded.alerts().get(0).entityId() != 0
                || decoded.alerts().get(127).entityId() != 127) {
            helper.fail("Awareness payload did not clamp alert count and preserve bounded state/identity across encoding");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:awareness_client_state", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void awarenessClientStateAppliesSnapshotsAndClearsStaleAlerts(GameTestHelper helper) {
        com.example.soundattract.client.AwarenessClientState.clear();
        UUID firstMob = UUID.randomUUID();
        UUID secondMob = UUID.randomUUID();
        AwarenessSnapshotMessage initial = new AwarenessSnapshotMessage(
                0.75f,
                Float.NaN,
                4,
                true,
                List.of(new AwarenessSnapshotMessage.Alert(firstMob, 41, 2, 0xFF334455, 0.6f)));
        com.example.soundattract.client.AwarenessClientState.apply(initial);
        com.example.soundattract.client.AwarenessClientState.MobAlert originalAlert =
                com.example.soundattract.client.AwarenessClientState.alert(firstMob);
        long startedAt = originalAlert == null ? -1L : originalAlert.startedAt();

        AwarenessSnapshotMessage update = new AwarenessSnapshotMessage(
                0.25f,
                0.5f,
                2,
                false,
                List.of(new AwarenessSnapshotMessage.Alert(firstMob, 42, 2, 0xFF112233, 0.9f),
                        new AwarenessSnapshotMessage.Alert(secondMob, 43, 5, 0xFFABCDEF, 0.2f)));
        com.example.soundattract.client.AwarenessClientState.apply(update);
        com.example.soundattract.client.AwarenessClientState.MobAlert updatedAlert =
                com.example.soundattract.client.AwarenessClientState.alert(firstMob);
        boolean snapshotApplied = com.example.soundattract.client.AwarenessClientState.visibility() == 0.25f
                && com.example.soundattract.client.AwarenessClientState.noise() == 0.5f
                && com.example.soundattract.client.AwarenessClientState.scent() == 2
                && !com.example.soundattract.client.AwarenessClientState.windshielded();
        boolean alertStateApplied = updatedAlert != null && updatedAlert.entityId() == 42
                && updatedAlert.type() == 2 && updatedAlert.color() == 0xFF112233
                && updatedAlert.progress() == 0.9f && updatedAlert.startedAt() == startedAt
                && com.example.soundattract.client.AwarenessClientState.alert(secondMob) != null;

        com.example.soundattract.client.AwarenessClientState.apply(
                new AwarenessSnapshotMessage(0.0f, 0.0f, 0, false, List.of()));
        boolean briefSnapshotGapRetained = com.example.soundattract.client.AwarenessClientState.alerts().size() == 2
                && com.example.soundattract.client.AwarenessClientState.alert(firstMob) != null;

        if (!snapshotApplied || !alertStateApplied || !briefSnapshotGapRetained) {
            helper.fail("Awareness client state lifecycle failed: snapshot=" + snapshotApplied
                    + ", alertUpdate=" + alertStateApplied + ", briefGapRetained=" + briefSnapshotGapRetained
                    + ", sanitizedNoise="
                    + com.example.soundattract.client.AwarenessClientState.noise());
            return;
        }
        com.example.soundattract.client.AwarenessClientState.clear();
        boolean cleared = com.example.soundattract.client.AwarenessClientState.visibility() == 0.0f
                && com.example.soundattract.client.AwarenessClientState.noise() == 0.0f
                && com.example.soundattract.client.AwarenessClientState.scent() == 0
                && !com.example.soundattract.client.AwarenessClientState.windshielded()
                && com.example.soundattract.client.AwarenessClientState.alerts().isEmpty();
        if (!cleared) {
            helper.fail("Awareness state clear failed after a brief snapshot gap");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:flow_lava", timeoutTicks = 240)
    public void queuedFlowFieldRoutesAroundLava(GameTestHelper helper) {
        prepareFlowFieldCorridor(helper);
        BlockPos lava = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.setBlock(4, 1, 4, Blocks.LAVA);
        // Keep the fixture focused on a traversable lava obstacle instead of
        // allowing flowing lava to fill both lanes before the async scan runs.
        helper.setBlock(3, 1, 4, Blocks.STONE);
        helper.setBlock(5, 1, 4, Blocks.STONE);
        helper.setBlock(4, 1, 3, Blocks.STONE);
        helper.setBlock(4, 1, 5, Blocks.STONE);
        HazardClassifier.clear();
        Zombie follower = helper.spawn(EntityType.ZOMBIE, 1, 1, 4);
        follower.setNoAi(true);
        Vec3 target = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 1, 4)));
        helper.runAfterDelay(2, () -> attemptQueuedFlowFieldStep(helper, follower, target, lava, 180));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:flow_unsupported", timeoutTicks = 240)
    public void queuedFlowFieldRoutesAroundUnsupportedCell(GameTestHelper helper) {
        prepareFlowFieldCorridor(helper);
        BlockPos unsupported = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.setBlock(4, 0, 4, Blocks.AIR);
        HazardClassifier.clear();
        Zombie follower = helper.spawn(EntityType.ZOMBIE, 1, 1, 4);
        follower.setNoAi(true);
        Vec3 target = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 1, 4)));
        helper.runAfterDelay(2, () -> attemptQueuedFlowFieldStep(helper, follower, target, unsupported, 180));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:flow_cancel", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void queuedFlowFieldReplacesObsoleteTargetScan(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 8, 4);
        Zombie follower = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        follower.setNoAi(true);
        Vec3 firstTarget = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 1, 1)));
        Vec3 replacementTarget = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 1, 3)));
        com.example.soundattract.pathfinding.FlowFieldManager.getNextStep(follower, firstTarget);
        boolean firstQueued = com.example.soundattract.pathfinding.FlowFieldManager
                .isScanPendingForTesting(follower, firstTarget);
        com.example.soundattract.pathfinding.FlowFieldManager.getNextStep(follower, replacementTarget);
        boolean obsoleteCancelled = !com.example.soundattract.pathfinding.FlowFieldManager
                .isScanPendingForTesting(follower, firstTarget);
        boolean replacementQueued = com.example.soundattract.pathfinding.FlowFieldManager
                .isScanPendingForTesting(follower, replacementTarget);
        if (!firstQueued || !obsoleteCancelled || !replacementQueued) {
            helper.fail("Flow-field did not replace an obsolete target scan: firstQueued=" + firstQueued
                    + ", obsoleteCancelled=" + obsoleteCancelled + ", replacementQueued=" + replacementQueued);
            return;
        }
        helper.succeed();
    }

    // Deferred at the user's request; this Living Bridge behavior will be checked in-world.
    public void bridgeWorkGoalEvacuatesGroupAcrossCompletedDeck(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 12, 12);
        for (int x = 3; x <= 5; x++) {
            for (int z = 1; z <= 11; z++) {
                helper.setBlock(x, -1, z, Blocks.LAVA);
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
        ServerLevel level = helper.getLevel();
        List<Zombie> workers = new java.util.ArrayList<>();
        List<com.example.soundattract.ai.BridgeWorkGoal> goals = new java.util.ArrayList<>();
        List<Vec3> targets = new java.util.ArrayList<>();
        for (int index = 0; index < 3; index++) {
            int z = 3 + index * 3;
            Zombie worker = helper.spawn(EntityType.ZOMBIE, 1, 1, z);
            Vec3 target = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(10, 1, z)));
            com.example.soundattract.ai.LivingWallCoordinator.ensureBridgeWorkGoal(worker);
            var selector = ((com.example.soundattract.mixin.MobAccessor) worker).getGoalSelector();
            var goal = selector.getAvailableGoals().stream()
                    .map(wrapped -> wrapped.getGoal())
                    .filter(com.example.soundattract.ai.BridgeWorkGoal.class::isInstance)
                    .map(com.example.soundattract.ai.BridgeWorkGoal.class::cast)
                    .findFirst().orElse(null);
            if (goal == null) {
                helper.fail("Could not install a production bridge-work controller for group member " + index);
                return;
            }
            for (var wrapped : new java.util.ArrayList<>(selector.getAvailableGoals())) {
                if (wrapped.getGoal() != goal) selector.removeGoal(wrapped.getGoal());
            }
            if (!setBasinEvacuationForTest(level, worker, target, level.getGameTime() + 240L)) {
                helper.fail("Could not assign a production evacuation target to group member " + index);
                return;
            }
            workers.add(worker);
            goals.add(goal);
            targets.add(target);
        }
        helper.runAfterDelay(2, () -> awaitBridgeWorkGroupEvacuation(helper, workers, goals, targets, 220));
    }

    // Deferred at the user's request; this Living Bridge behavior will be checked in-world.
    public void livingWallScannerCertifiesCanonicalSiteAcrossLavaTrench(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.setDayTime(13000L);
        int baseY = 80;
        int zMin = 0;
        int zMax = 8;
        for (int x = 0; x <= 22; x++) {
            for (int z = zMin; z <= zMax; z++) {
                boolean trench = x >= 6 && x <= 10;
                for (int y = baseY - 4; y < baseY; y++) {
                    net.minecraft.world.level.block.state.BlockState underlay = trench && y == baseY - 1
                            ? Blocks.LAVA.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), underlay, 2);
                }
                if (trench) {
                    level.setBlock(helper.absolutePos(new BlockPos(x, baseY, z)), Blocks.AIR.defaultBlockState(), 2);
                } else {
                    level.setBlock(helper.absolutePos(new BlockPos(x, baseY, z)), Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }
        // Give the bounded scanner an explicit far-bank face to certify. The
        // straight-through ray then has a stable collision even when the
        // production pressure budget is limited to its first few directions.
        for (int y = baseY; y <= baseY + 3; y++) {
            for (int z = zMin; z <= zMax; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(13, y, z)), Blocks.STONE.defaultBlockState(), 2);
            }
        }

        ServerPlayer threat = helper.makeMockServerPlayerInLevel();
        // Keep the mock threat passive so vanilla target behavior does not pull
        // the scanner fixture's roster away from the test site.
        threat.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        threat.setPos(helper.absolutePos(new BlockPos(15, baseY + 1, 4)).getX() + 0.5,
                helper.absolutePos(new BlockPos(15, baseY + 1, 4)).getY(),
                helper.absolutePos(new BlockPos(15, baseY + 1, 4)).getZ() + 0.5);

        List<Zombie> cluster = new java.util.ArrayList<>();
        for (int index = 0; index < 36; index++) {
            int x = index % 6;
            int z = index / 6;
            Zombie zombie = helper.spawn(EntityType.ZOMBIE, x, baseY + 1, z);
            cluster.add(zombie);
        }

        helper.runAfterDelay(2, () -> {
            List<BlockPos> targets = findWallTargetsForTest(level, new java.util.ArrayList<>(cluster),
                    threat.blockPosition());
            if (targets.isEmpty()) {
                helper.fail("Living Wall did not certify a canonical bridge target across the controlled lava trench: "
                        + "roster=" + cluster.size() + ", workers="
                        + cluster.stream().map(net.minecraft.world.entity.Mob::blockPosition).toList()
                        + ", threat=" + threat.blockPosition());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:flow_queue_capacity", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void queuedFlowFieldRejectsRequestsBeyondCapacity(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 16, 16);
        int pendingBefore = com.example.soundattract.pathfinding.FlowFieldManager.getPendingScanCountForTesting();
        int capacity = com.example.soundattract.pathfinding.FlowFieldManager.getPendingScanCapacityForTesting();
        int available = Math.max(0, capacity - pendingBefore);
        List<Zombie> requesters = new java.util.ArrayList<>();
        int accepted = 0;

        for (int index = 0; index < capacity + 1; index++) {
            Zombie requester = helper.spawn(EntityType.ZOMBIE, 1 + index % 16, 1, 1 + index / 16);
            requester.setNoAi(true);
            requesters.add(requester);
            Vec3 target = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1 + index, 1, 20)));
            com.example.soundattract.pathfinding.FlowFieldManager.getNextStep(requester, target);
            if (com.example.soundattract.pathfinding.FlowFieldManager.isScanPendingForTesting(requester, target)) {
                accepted++;
            }
        }

        int pendingAfter = com.example.soundattract.pathfinding.FlowFieldManager.getPendingScanCountForTesting();
        boolean bounded = pendingAfter <= capacity;
        boolean acceptedToAvailableCapacity = accepted == Math.min(capacity + 1, available);
        boolean rejectedAfterCapacity = accepted < capacity + 1;
        java.util.concurrent.atomic.AtomicLong elapsedNanos = new java.util.concurrent.atomic.AtomicLong();
        int scannedThisTick;
        try {
            com.example.soundattract.pathfinding.FlowFieldManager.setScanClockForTesting(
                    () -> elapsedNanos.getAndAdd(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(1)));
            com.example.soundattract.pathfinding.FlowFieldManager.onEndServerTick();
            scannedThisTick = com.example.soundattract.pathfinding.FlowFieldManager
                    .getLastTickScannedPositionsForTesting();
        } finally {
            com.example.soundattract.pathfinding.FlowFieldManager.setScanClockForTesting(null);
        }
        int scanBudget = com.example.soundattract.pathfinding.FlowFieldManager.getScanPositionBudgetForTesting();
        boolean timeBudgetRespected = scannedThisTick == 128 && scannedThisTick < scanBudget;
        boolean scanBudgetRespected = scannedThisTick <= scanBudget && (accepted == 0 || scannedThisTick > 0);

        requesters.forEach(Zombie::discard);
        for (int attempt = 0; attempt < 8; attempt++) {
            com.example.soundattract.pathfinding.FlowFieldManager.onEndServerTick();
        }
        boolean fixtureRequestsCleared = requesters.stream().noneMatch(requester ->
                com.example.soundattract.pathfinding.FlowFieldManager.isScanPendingForTesting(requester,
                        Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1 + requesters.indexOf(requester), 1, 20)))));

        if (!bounded || !acceptedToAvailableCapacity || !rejectedAfterCapacity
                || !scanBudgetRespected || !timeBudgetRespected || !fixtureRequestsCleared) {
            helper.fail("Flow-field pending queue exceeded or ignored its capacity: before=" + pendingBefore
                    + ", accepted=" + accepted + ", after=" + pendingAfter + ", bounded=" + bounded
                    + ", acceptedToCapacity=" + acceptedToAvailableCapacity
                    + ", rejectedBeyondCapacity=" + rejectedAfterCapacity
                    + ", scannedThisTick=" + scannedThisTick + "/" + scanBudget
                    + ", scanBudgetRespected=" + scanBudgetRespected + ", timeBudgetRespected=" + timeBudgetRespected
                    + ", fixtureRequestsCleared=" + fixtureRequestsCleared);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void awarenessServerScanIncludesRecordedNearMiss(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Zombie mob = helper.spawn(EntityType.ZOMBIE, 3, 1, 1);
        player.setPos(helper.absolutePos(new BlockPos(1, 1, 1)).getX() + 0.5,
                helper.absolutePos(new BlockPos(1, 1, 1)).getY(),
                helper.absolutePos(new BlockPos(1, 1, 1)).getZ() + 0.5);
        try {
            com.example.soundattract.event.AwarenessServerEvents.recordNearMiss(mob);
            var scanMethod = com.example.soundattract.event.AwarenessServerEvents.class
                    .getDeclaredMethod("scanAwareness", ServerLevel.class,
                            net.minecraft.server.level.ServerPlayer.class);
            scanMethod.setAccessible(true);
            Object scan = scanMethod.invoke(null, level, player);
            var alertsMethod = scan.getClass().getDeclaredMethod("alerts");
            alertsMethod.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<AwarenessSnapshotMessage.Alert> alerts =
                    (List<AwarenessSnapshotMessage.Alert>) alertsMethod.invoke(scan);
            boolean nearMissIncluded = alerts.stream().anyMatch(alert -> alert.mobId().equals(mob.getUUID())
                    && alert.entityId() == mob.getId() && alert.type() == 3);
            if (!nearMissIncluded) {
                helper.fail("Server awareness scan omitted its unexpired near-miss alert: " + alerts);
                return;
            }
        } catch (ReflectiveOperationException e) {
            helper.fail("Could not invoke the production awareness scan for its server-side fixture: " + e);
            return;
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:awareness_detected_retention", timeoutTicks = 100)
    public void detectedEyeAlertSurvivesBriefOcclusionAndExpiresAfterGrace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "awareness-retention-gametest"), net.minecraft.server.level.ClientInformation.createDefault()) {
            @Override
            public boolean isCreative() {
                return false;
            }

            @Override
            public boolean isSpectator() {
                return false;
            }
        };
        Zombie mob = helper.spawn(EntityType.ZOMBIE, 3, 1, 1);
        BlockPos playerPos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
        mob.setNoAi(true);
        mob.setYRot(90.0f);
        mob.setYHeadRot(90.0f);
        boolean previousStealth = SoundAttractConfig.COMMON.enableStealthMechanics.get();
        boolean previousTieredStealth = SoundAttractConfig.COMMON.enableTieredStealthPerformance.get();
        boolean previousXrayTargeting = SoundAttractConfig.COMMON.enableXrayTargeting.get();
        double previousStandingRange = SoundAttractConfig.COMMON.standingDetectionRangePlayer.get();
        double previousMaximumRange = SoundAttractConfig.COMMON.maxStealthDetectionRange.get();
        SoundAttractConfig.COMMON.enableStealthMechanics.set(true);
        SoundAttractConfig.COMMON.enableTieredStealthPerformance.set(false);
        SoundAttractConfig.COMMON.enableXrayTargeting.set(false);
        SoundAttractConfig.COMMON.standingDetectionRangePlayer.set(24.0);
        SoundAttractConfig.COMMON.maxStealthDetectionRange.set(48.0);
        Runnable cleanup = () -> {
            level.getServer().getPlayerList().remove(player);
            mob.discard();
            SoundAttractConfig.COMMON.enableStealthMechanics.set(previousStealth);
            SoundAttractConfig.COMMON.enableTieredStealthPerformance.set(previousTieredStealth);
            SoundAttractConfig.COMMON.enableXrayTargeting.set(previousXrayTargeting);
            SoundAttractConfig.COMMON.standingDetectionRangePlayer.set(previousStandingRange);
            SoundAttractConfig.COMMON.maxStealthDetectionRange.set(previousMaximumRange);
        };

        double playerDistance = mob.distanceTo(player);
        boolean acceptedWithComputedRange = com.example.soundattract.event.StealthDetectionEvents
                .canMobDetectPlayerWithRange(mob, player, playerDistance + 0.01);
        boolean acceptedOutsideExplicitRange = com.example.soundattract.event.StealthDetectionEvents
                .canMobDetectPlayerWithRange(mob, player, Math.max(0.0, playerDistance - 0.01));
        if (!acceptedWithComputedRange || acceptedOutsideExplicitRange) {
            cleanup.run();
            helper.fail("Awareness detection did not honor its supplied range: distance=" + playerDistance
                    + ", acceptedWithRange=" + acceptedWithComputedRange
                    + ", acceptedOutsideRange=" + acceptedOutsideExplicitRange);
            return;
        }

        List<AwarenessSnapshotMessage.Alert> initial;
        try {
            initial = scanAwarenessAlerts(level, player);
        } catch (ReflectiveOperationException exception) {
            cleanup.run();
            helper.fail("Could not invoke the production awareness scan for the visible detection fixture: " + exception);
            return;
        }
        boolean initiallyDetected = initial.stream().anyMatch(alert -> alert.mobId().equals(mob.getUUID())
                && alert.type() == 2 && alert.color() == 0xFFFF3030);
        if (!initiallyDetected) {
            cleanup.run();
            helper.fail("A visible zombie did not produce the red detected-eye alert before occlusion: " + initial);
            return;
        }

        for (int z = 0; z <= 2; z++) {
            helper.setBlock(2, 1, z, Blocks.STONE);
            helper.setBlock(2, 2, z, Blocks.STONE);
        }
        helper.runAfterDelay(10, () -> {
            try {
                List<AwarenessSnapshotMessage.Alert> retained = scanAwarenessAlerts(level, player);
                boolean lineOfSightAfterOcclusion = com.example.soundattract.event.FovEvents
                        .hasSmartLineOfSight(mob, player);
                boolean detectableAfterOcclusion = com.example.soundattract.event.StealthDetectionEvents
                        .canMobDetectPlayer(mob, player);
                if (lineOfSightAfterOcclusion || detectableAfterOcclusion) {
                    cleanup.run();
                    helper.fail("Occlusion fixture still allowed detection: lineOfSight=" + lineOfSightAfterOcclusion
                            + ", detectable=" + detectableAfterOcclusion
                            + ", bypass=" + SoundAttractConfig.isStealthBypassed(mob)
                            + ", highWeightOverride=" + com.example.soundattract.ai.MobGroupManager.isMobInHighWeightOverride(mob)
                            + ", playerCreative=" + player.isCreative() + ", playerSpectator=" + player.isSpectator()
                            + ", playerAlive=" + player.isAlive()
                            + ", tier=" + com.example.soundattract.event.StealthDetectionEvents.getPerfTier(level)
                            + ", xray=" + com.example.soundattract.event.StealthDetectionEvents.getEffectiveXrayRange(mob)
                            + ", inFovWithoutOcclusion=" + com.example.soundattract.event.FovEvents.isTargetInFov(mob, player, false)
                            + ", alerts=" + retained);
                    return;
                }
                boolean redStillVisible = retained.stream().anyMatch(alert -> alert.mobId().equals(mob.getUUID())
                        && alert.type() == 2 && alert.color() == 0xFFFF3030);
                if (!redStillVisible) {
                    cleanup.run();
                    helper.fail("The detected-eye alert flickered out immediately after occlusion; alerts=" + retained);
                    return;
                }
                helper.runAfterDelay(31, () -> {
                    try {
                        List<AwarenessSnapshotMessage.Alert> expired = scanAwarenessAlerts(level, player);
                        boolean redExpired = expired.stream().noneMatch(alert -> alert.mobId().equals(mob.getUUID())
                                && alert.type() == 2);
                        if (!redExpired) {
                            helper.fail("The detected-eye alert did not expire after its 40-tick grace period: " + expired
                                    + ", gameTime=" + level.getGameTime() + ", lineOfSight="
                                    + com.example.soundattract.event.FovEvents.hasSmartLineOfSight(mob, player)
                                    + ", detectable=" + com.example.soundattract.event.StealthDetectionEvents
                                            .canMobDetectPlayer(mob, player));
                            return;
                        }
                        helper.succeed();
                    } catch (ReflectiveOperationException exception) {
                        helper.fail("Could not inspect the post-grace production awareness scan: " + exception);
                    } finally {
                        cleanup.run();
                    }
                });
            } catch (ReflectiveOperationException exception) {
                cleanup.run();
                helper.fail("Could not inspect the production awareness scan after brief occlusion: " + exception);
            }
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:awareness_noise", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void awarenessNoiseReflectsSoundAndSprinting(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos playerAnchor = helper.absolutePos(new BlockPos(160, 150, 160));
        player.setPos(playerAnchor.getX() + 0.5, playerAnchor.getY(), playerAnchor.getZ() + 0.5);
        BlockPos soundPos = BlockPos.containing(player.getX() + 1.0, player.getY(), player.getZ());
        String dimension = level.dimension().location().toString();
        float quietNoise;
        float recordedSoundNoise;
        float sprintNoise;
        try {
            var calculateNoise = AwarenessServerEvents.class.getDeclaredMethod(
                    "calculateNoise", ServerLevel.class, ServerPlayer.class);
            calculateNoise.setAccessible(true);
            player.setSprinting(false);
            player.setDeltaMovement(Vec3.ZERO);
            quietNoise = (float) calculateNoise.invoke(null, level, player);

            SoundTracker.addVirtualSound(soundPos, dimension, 32.0, 8.0, 200, UUID.randomUUID(), "awareness_noise");
            recordedSoundNoise = (float) calculateNoise.invoke(null, level, player);
            player.setSprinting(true);
            player.setDeltaMovement(new Vec3(0.2, 0.0, 0.0));
            sprintNoise = (float) calculateNoise.invoke(null, level, player);
        } catch (ReflectiveOperationException failure) {
            helper.fail("Could not exercise production awareness noise calculation: " + failure.getMessage());
            return;
        } finally {
            SoundTracker.removeSoundAt(soundPos, dimension);
            level.getServer().getPlayerList().remove(player);
        }

        if (!(recordedSoundNoise > quietNoise) || sprintNoise < recordedSoundNoise || sprintNoise > 1.0f) {
            helper.fail("Awareness noise did not incorporate nearby sound and sprint state: quiet=" + quietNoise
                    + ", sound=" + recordedSoundNoise + ", sprint=" + sprintNoise);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:from_the_fog_marker", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void fromTheFogEquipmentMarkerTracksAndClearsVisibility(GameTestHelper helper) {
        if (!FabricLoader.getInstance().isModLoaded("watching")) {
            helper.fail("From The Fog runtime profile is required for its equipment marker GameTest");
            return;
        }
        Zombie markedEntity = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        String markerTag = "herobrineModel";
        markedEntity.addTag(markerTag);
        java.util.UUID entityId = markedEntity.getUUID();
        ServerPlayer trackingPlayer = helper.makeMockServerPlayerInLevel();
        ServerLevel level = helper.getLevel();
        try {
        com.example.soundattract.client.FromTheFogVisualCompat.clear();
        boolean serverMarkerRecognized = markedEntity.getTags().contains(markerTag);
        FriendlyByteBuf markerBuffer = new FriendlyByteBuf(Unpooled.buffer());
        com.example.soundattract.network.FromTheFogEquipmentMarkerMessage.encode(
                new com.example.soundattract.network.FromTheFogEquipmentMarkerMessage(entityId, true), markerBuffer);
        com.example.soundattract.network.FromTheFogEquipmentMarkerMessage markerMessage =
                com.example.soundattract.network.FromTheFogEquipmentMarkerMessage.decode(markerBuffer);
        boolean packetRoundTrips = entityId.equals(markerMessage.entityId()) && markerMessage.keepEquipmentVisible();
        com.example.soundattract.client.FromTheFogVisualCompat.applyMarker(
                markerMessage.entityId(), markerMessage.keepEquipmentVisible());
        boolean markedVisible = com.example.soundattract.client.FromTheFogVisualCompat
                .shouldKeepInvisibleEquipment(markedEntity);
        FriendlyByteBuf clearBuffer = new FriendlyByteBuf(Unpooled.buffer());
        com.example.soundattract.network.FromTheFogEquipmentMarkerMessage.encode(
                new com.example.soundattract.network.FromTheFogEquipmentMarkerMessage(entityId, false), clearBuffer);
        com.example.soundattract.network.FromTheFogEquipmentMarkerMessage clearMessage =
                com.example.soundattract.network.FromTheFogEquipmentMarkerMessage.decode(clearBuffer);
        packetRoundTrips &= entityId.equals(clearMessage.entityId()) && !clearMessage.keepEquipmentVisible();
        com.example.soundattract.client.FromTheFogVisualCompat.applyMarker(
                clearMessage.entityId(), clearMessage.keepEquipmentVisible());
        boolean stopTrackingCleared = !com.example.soundattract.client.FromTheFogVisualCompat
                .shouldKeepInvisibleEquipment(markedEntity);
        com.example.soundattract.client.FromTheFogVisualCompat.applyMarker(entityId, true);
        com.example.soundattract.client.FromTheFogVisualCompat.clear();
        boolean disconnectCleared = !com.example.soundattract.client.FromTheFogVisualCompat
                .shouldKeepInvisibleEquipment(markedEntity);

        java.util.List<com.example.soundattract.gametest.FromTheFogMarkerPacketProbe.MarkerPacket> trackingPackets;
        boolean channelAdvertised;
        try {
            channelAdvertised = advertiseMarkerChannelToMockPlayer(trackingPlayer);
            com.example.soundattract.event.FromTheFogEquipmentSyncEvents.register();
            com.example.soundattract.gametest.FromTheFogMarkerPacketProbe.begin();
            try {
                net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.invoker()
                        .onStartTracking(markedEntity, trackingPlayer);
                net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.STOP_TRACKING.invoker()
                        .onStopTracking(markedEntity, trackingPlayer);
            } finally {
                trackingPackets = com.example.soundattract.gametest.FromTheFogMarkerPacketProbe.finish();
            }
        } catch (ReflectiveOperationException exception) {
            helper.fail("Could not model the mock client's negotiated marker channel: " + exception);
            return;
        }
        boolean startTrackingSent = trackingPackets.stream()
                .anyMatch(packet -> entityId.equals(packet.entityId()) && packet.keepEquipmentVisible());
        boolean stopTrackingSent = trackingPackets.stream()
                .anyMatch(packet -> entityId.equals(packet.entityId()) && !packet.keepEquipmentVisible());

        if (!serverMarkerRecognized || !packetRoundTrips || !markedVisible || !stopTrackingCleared || !disconnectCleared
                || !channelAdvertised || !startTrackingSent || !stopTrackingSent) {
            helper.fail("From The Fog marker state did not register/apply/clear: serverTag="
                    + serverMarkerRecognized + ", markedVisible=" + markedVisible
                    + ", stopTrackingCleared=" + stopTrackingCleared + ", disconnectCleared=" + disconnectCleared
                    + ", channelAdvertised=" + channelAdvertised + ", startPacket=" + startTrackingSent
                    + ", stopPacket=" + stopTrackingSent + ", packetCount=" + trackingPackets.size());
            return;
        }
        helper.succeed();
        } finally {
            level.getServer().getPlayerList().remove(trackingPlayer);
            com.example.soundattract.client.FromTheFogVisualCompat.clear();
        }
    }

    /**
     * A mock player has no client to perform Fabric's REGISTER handshake. Populate the same
     * server-side advertised-channel set so canSend() follows its real production branch.
     */
    private static boolean advertiseMarkerChannelToMockPlayer(ServerPlayer player) throws ReflectiveOperationException {
        Class<?> networkingImpl = Class.forName("net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl");
        java.lang.reflect.Method getAddon = java.util.Arrays.stream(networkingImpl.getDeclaredMethods())
                .filter(method -> method.getName().equals("getAddon") && java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isAssignableFrom(player.connection.getClass()))
                .findFirst().orElseThrow(() -> new NoSuchMethodException("ServerNetworkingImpl.getAddon"));
        getAddon.setAccessible(true);
        Object addon = getAddon.invoke(null, player.connection);
        java.lang.reflect.Method registerChannel = null;
        for (Class<?> type = addon.getClass(); type != null && registerChannel == null; type = type.getSuperclass()) {
            for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                if (method.getName().equals("register") && method.getParameterCount() == 1
                        && java.util.List.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    registerChannel = method;
                    break;
                }
            }
        }
        if (registerChannel == null) {
            throw new NoSuchMethodException("AbstractChanneledNetworkAddon.register(List)");
        }
        registerChannel.setAccessible(true);
        registerChannel.invoke(addon, java.util.List.of(
                com.example.soundattract.network.SoundAttractNetwork.FROM_THE_FOG_MARKER_ID));
        return net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(
                player, com.example.soundattract.network.SoundAttractNetwork.FROM_THE_FOG_MARKER_ID);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void soundMessageRoundTripsPlayerAndIntegrationMetadata(GameTestHelper helper) {
        UUID source = UUID.randomUUID();
        SoundMessage original = new SoundMessage(
                ResourceLocation.fromNamespaceAndPath("minecraft", "entity.zombie.ambient"),
                12.5, 64.0, -3.25,
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), Optional.of(source),
                "WALKING", 1.4f, true, "example.Animator", "rifle", "pistol");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        SoundMessage.write(buffer, original);
        SoundMessage decoded = SoundMessage.read(buffer);

        if (!original.soundId.equals(decoded.soundId) || original.x != decoded.x
                || !original.dimension.equals(decoded.dimension)
                || !Optional.of(source).equals(decoded.sourcePlayerUUID)
                || !"WALKING".equals(decoded.action) || decoded.intensity01 != 1.0f
                || !decoded.whispering || !"rifle".equals(decoded.taczType)
                || !"pistol".equals(decoded.pointBlankType)) {
            helper.fail("Sound packet lost source, integration metadata, or bounded intensity on decode");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:voicechat_server", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void voiceChatPacketResolvesConfiguredIntensityAndWhisperRange(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        if (!SoundAttractConfig.serverReady()) {
            helper.fail("Server-scoped voice chat config was not loaded");
            return;
        }

        ServerPlayer speaker = helper.makeMockServerPlayerInLevel();
        ResourceLocation dimension = level.dimension().location();
        BlockPos normalPos = helper.absolutePos(new BlockPos(4, 1, 2));
        BlockPos whisperPos = helper.absolutePos(new BlockPos(7, 1, 2));
        BlockPos quieterPos = helper.absolutePos(new BlockPos(10, 1, 2));
        int oldNormalRange = SoundAttractConfig.SERVER.voiceChatNormalRange.get();
        int oldWhisperRange = SoundAttractConfig.SERVER.voiceChatWhisperRange.get();
        double oldWeight = SoundAttractConfig.SERVER.voiceChatWeight.get();
        List<? extends String> oldThresholds = SoundAttractConfig.SERVER.voiceChatDbThresholdMap.get();
        double oldMaxRange = SoundAttractConfig.SERVER.maxClientSoundRange.get();
        int oldLifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
        int oldSoundCap = SoundAttractConfig.COMMON.maxSoundsTracked.get();
        try {
            SoundAttractConfig.SERVER.voiceChatNormalRange.set(60);
            SoundAttractConfig.SERVER.voiceChatWhisperRange.set(30);
            SoundAttractConfig.SERVER.voiceChatWeight.set(7.5);
            SoundAttractConfig.SERVER.voiceChatDbThresholdMap.set(List.of("100:2.0", "50:1.0", "0:0.5"));
            SoundAttractConfig.SERVER.maxClientSoundRange.set(128.0);
            SoundAttractConfig.COMMON.soundLifetimeTicks.set(100);
            SoundAttractConfig.COMMON.maxSoundsTracked.set(Math.max(oldSoundCap,
                    SoundTracker.getTrackedSoundCount() + 3));

            SoundMessage.handle(new SoundMessage(SoundMessage.VOICE_CHAT_SOUND_ID,
                    normalPos.getX() + 0.25, normalPos.getY(), normalPos.getZ() + 0.25,
                    dimension, Optional.of(speaker.getUUID()), 1.0f, false), speaker);
            SoundMessage.handle(new SoundMessage(SoundMessage.VOICE_CHAT_SOUND_ID,
                    whisperPos.getX() + 0.25, whisperPos.getY(), whisperPos.getZ() + 0.25,
                    dimension, Optional.of(speaker.getUUID()), 1.0f, true), speaker);
            SoundMessage.handle(new SoundMessage(SoundMessage.VOICE_CHAT_SOUND_ID,
                    quieterPos.getX() + 0.25, quieterPos.getY(), quieterPos.getZ() + 0.25,
                    dimension, Optional.of(speaker.getUUID()), 0.4f, false), speaker);

            List<SoundTracker.SoundRecord> records = List.of(normalPos, whisperPos, quieterPos).stream()
                    .map(pos -> SoundTracker.getNearbySounds(level, dimension.toString(), pos, null).stream()
                            .filter(record -> SoundMessage.VOICE_CHAT_SOUND_ID.toString().equals(record.soundId)
                                    && record.pos.equals(pos))
                            .findFirst().orElse(null))
                    .toList();
            boolean resolved = records.size() == 3 && records.stream().allMatch(java.util.Objects::nonNull)
                    && Math.abs(records.get(0).range - 120.0) < 0.001
                    && Math.abs(records.get(1).range - 60.0) < 0.001
                    && Math.abs(records.get(2).range - 60.0) < 0.001
                    && records.stream().allMatch(record -> Math.abs(record.weight - 7.5) < 0.001)
                    && records.stream().allMatch(record -> record.ticksRemaining == 100);
            if (!resolved) {
                helper.fail("Production voice packet handler did not resolve intensity/range/weight/lifetime: "
                        + records.stream().map(record -> record == null ? "missing"
                                : record.soundId + "@" + record.pos + " range=" + record.range
                                + " weight=" + record.weight + " ticksRemaining=" + record.ticksRemaining).toList());
                return;
            }
        } finally {
            for (BlockPos pos : List.of(normalPos, whisperPos, quieterPos)) {
                SoundTracker.removeSoundAt(pos, dimension.toString());
            }
            SoundAttractConfig.SERVER.voiceChatNormalRange.set(oldNormalRange);
            SoundAttractConfig.SERVER.voiceChatWhisperRange.set(oldWhisperRange);
            SoundAttractConfig.SERVER.voiceChatWeight.set(oldWeight);
            SoundAttractConfig.SERVER.voiceChatDbThresholdMap.set(oldThresholds);
            SoundAttractConfig.SERVER.maxClientSoundRange.set(oldMaxRange);
            SoundAttractConfig.COMMON.soundLifetimeTicks.set(oldLifetime);
            SoundAttractConfig.COMMON.maxSoundsTracked.set(oldSoundCap);
            level.getServer().getPlayerList().remove(speaker);
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:voicechat_plasmo", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void plasmoVoiceEventsResolveMinecraftPlayerAndTrackSpeech(GameTestHelper helper) {
        if (!FabricLoader.getInstance().isModLoaded("plasmovoice")) {
            helper.succeed();
            return;
        }
        if (!SoundAttractConfig.serverReady()) {
            helper.fail("Server-scoped voice chat config was not loaded");
            return;
        }

        ServerLevel level = helper.getLevel();
        ServerPlayer speaker = helper.makeMockServerPlayerInLevel();
        ResourceLocation dimension = level.dimension().location();
        BlockPos speakPos = speaker.blockPosition();
        int oldNormalRange = SoundAttractConfig.SERVER.voiceChatNormalRange.get();
        int oldVoiceLifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
        int oldSoundCap = SoundAttractConfig.COMMON.maxSoundsTracked.get();
        double oldWeight = SoundAttractConfig.SERVER.voiceChatWeight.get();
        boolean oldEnabled = SoundAttractConfig.SERVER.enableVoiceChatIntegration.get();
        int lifetime = 43;
        SoundAttractConfig.SERVER.voiceChatNormalRange.set(61);
        SoundAttractConfig.SERVER.voiceChatWeight.set(6.25);
        SoundAttractConfig.COMMON.soundLifetimeTicks.set(lifetime);
        SoundAttractConfig.COMMON.maxSoundsTracked.set(Math.max(oldSoundCap,
                SoundTracker.getTrackedSoundCount() + 2));
        SoundAttractConfig.SERVER.enableVoiceChatIntegration.set(true);
        SoundTracker.removeSoundAt(speakPos, dimension.toString());

        BlockPos[] packetPos = new BlockPos[1];
        try {
            Class<?> voicePlayerType = Class.forName("su.plo.voice.api.server.player.VoicePlayer");
            Class<?> mcPlayerType = Class.forName("su.plo.slib.api.entity.player.McPlayer");
            Class<?> audioPacketType = Class.forName("su.plo.voice.proto.packets.udp.serverbound.PlayerAudioPacket");
            Object audioPacket = audioPacketType.getConstructor().newInstance();
            ClassLoader apiLoader = voicePlayerType.getClassLoader();
            Object mcPlayerWrapper = java.lang.reflect.Proxy.newProxyInstance(apiLoader,
                    new Class<?>[]{mcPlayerType}, (proxy, method, args) -> {
                        if (method.getName().equals("getInstance")) return speaker;
                        if (method.getName().equals("getUuid")) return speaker.getUUID();
                        if (method.getName().equals("getName")) return speaker.getGameProfile().getName();
                        if (method.getName().equals("isOnline")) return true;
                        return proxyDefaultValue(method.getReturnType());
                    });
            Object voicePlayer = java.lang.reflect.Proxy.newProxyInstance(apiLoader,
                    new Class<?>[]{voicePlayerType}, (proxy, method, args) -> method.getName().equals("getInstance")
                            ? mcPlayerWrapper : proxyDefaultValue(method.getReturnType()));

            Class<?> listenerType = Class.forName("com.example.soundattract.integration.voicechat.PlasmoIntegration$VoiceListener");
            Object listener = listenerType.getConstructor().newInstance();
            Class<?> speakEventType = Class.forName("su.plo.voice.api.server.event.audio.source.PlayerSpeakEvent");
            Object speakEvent = speakEventType.getConstructor(voicePlayerType, audioPacketType).newInstance(voicePlayer, audioPacket);
            listenerType.getMethod("voiceActive", speakEventType).invoke(listener, speakEvent);

            helper.runAfterDelay(1, () -> {
                try {
                    SoundTracker.SoundRecord speakRecord = findVoiceRecord(level, dimension, speakPos);
                    if (speakRecord == null || Math.abs(speakRecord.range - 61.0) >= 0.001
                            || Math.abs(speakRecord.weight - 6.25) >= 0.001
                            || speakRecord.ticksRemaining != lifetime - 1) {
                        helper.fail("Plasmo PlayerSpeakEvent did not unwrap McPlayer and track speech with configured values: "
                                + describeVoiceRecord(speakRecord));
                        return;
                    }

                    SoundTracker.removeSoundAt(speakPos, dimension.toString());
                    speaker.teleportTo(speaker.getX() + 5.0, speaker.getY(), speaker.getZ());
                    packetPos[0] = speaker.blockPosition();

                    Class<?> packetEventType = Class.forName("su.plo.voice.api.server.event.audio.source.ServerSourceAudioPacketEvent");
                    Class<?> sourceType = Class.forName("su.plo.voice.api.server.audio.source.ServerAudioSource");
                    Class<?> sourcePacketType = Class.forName("su.plo.voice.proto.packets.udp.clientbound.SourceAudioPacket");
                    Class<?> activationInfoType = Class.forName("su.plo.voice.api.server.audio.capture.PlayerActivationInfo");
                    Object activationInfo = activationInfoType.getConstructor(voicePlayerType, audioPacketType)
                            .newInstance(voicePlayer, audioPacket);
                    Object source = java.lang.reflect.Proxy.newProxyInstance(apiLoader,
                            new Class<?>[]{sourceType}, (proxy, method, args) -> proxyDefaultValue(method.getReturnType()));
                    Object packetEvent = packetEventType.getConstructor(sourceType, sourcePacketType, short.class, activationInfoType)
                            .newInstance(source, null, (short) 27, activationInfo);
                    listenerType.getMethod("onServerSourceAudio", packetEventType).invoke(listener, packetEvent);

                    helper.runAfterDelay(1, () -> {
                        try {
                            SoundTracker.SoundRecord packetRecord = findVoiceRecord(level, dimension, packetPos[0]);
                            if (packetRecord == null || Math.abs(packetRecord.range - 27.0) >= 0.001
                                    || Math.abs(packetRecord.weight - 6.25) >= 0.001
                                    || packetRecord.ticksRemaining != lifetime - 1) {
                                helper.fail("Plasmo server audio event did not unwrap McPlayer or preserve source distance: "
                                        + describeVoiceRecord(packetRecord));
                                return;
                            }
                            helper.succeed();
                        } catch (Throwable throwable) {
                        helper.fail("Could not verify Plasmo server audio event: " + describeThrowable(throwable));
                        } finally {
                            cleanupPlasmoVoiceFixture(level, dimension, speaker, speakPos, packetPos[0], oldNormalRange,
                                    oldWeight, oldVoiceLifetime, oldEnabled, oldSoundCap);
                        }
                    });
                } catch (Throwable throwable) {
                    helper.fail("Could not verify Plasmo PlayerSpeakEvent: " + describeThrowable(throwable));
                    cleanupPlasmoVoiceFixture(level, dimension, speaker, speakPos, packetPos[0], oldNormalRange,
                            oldWeight, oldVoiceLifetime, oldEnabled, oldSoundCap);
                }
            });
        } catch (Throwable throwable) {
            cleanupPlasmoVoiceFixture(level, dimension, speaker, speakPos, packetPos[0], oldNormalRange,
                    oldWeight, oldVoiceLifetime, oldEnabled, oldSoundCap);
            helper.fail("Could not construct Plasmo Voice events for the production listener: " + describeThrowable(throwable));
        }
    }

    private static Object proxyDefaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        return null;
    }

    private static String describeThrowable(Throwable throwable) {
        StringBuilder description = new StringBuilder();
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (!description.isEmpty()) description.append(" <- ");
            description.append(cause.getClass().getName());
            if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
                description.append(": ").append(cause.getMessage());
            }
        }
        return description.toString();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:spoiled_fallback", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void spoiledScentFallsBackToNeutralWhenModIsAbsent(GameTestHelper helper) {
        if (com.example.soundattract.ModChecker.isLoaded("spoiled")) {
            helper.succeed();
            return;
        }
        if (com.example.soundattract.integration.spoiled.SpoiledScentIntegration.isEnabled()) {
            helper.fail("Spoiled scent integration reported enabled while Spoiled was absent");
            return;
        }

        ServerLevel level = helper.getLevel();
        ServerPlayer ordinaryPlayer = helper.makeMockServerPlayerInLevel();
        ServerPlayer taggedPlayer = helper.makeMockServerPlayerInLevel();
        net.minecraft.core.BlockPos ordinaryPos = helper.absolutePos(new BlockPos(1, 1, 1));
        net.minecraft.core.BlockPos taggedPos = helper.absolutePos(new BlockPos(3, 1, 1));
        ordinaryPlayer.setPos(ordinaryPos.getX() + 0.5, ordinaryPos.getY(), ordinaryPos.getZ() + 0.5);
        taggedPlayer.setPos(taggedPos.getX() + 0.5, taggedPos.getY(), taggedPos.getZ() + 0.5);
        net.minecraft.world.item.ItemStack ordinaryFood = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH, 3);
        net.minecraft.world.item.ItemStack taggedFood = ordinaryFood.copy();
        net.minecraft.nbt.CompoundTag spoiledTags = new net.minecraft.nbt.CompoundTag();
        spoiledTags.putInt("spoiled:SpoilTimer", 75);
        spoiledTags.putInt("spoiled:SpoilMaxTime", 100);
        taggedFood.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(spoiledTags));
        ordinaryPlayer.getInventory().setItem(0, ordinaryFood);
        taggedPlayer.getInventory().setItem(0, taggedFood);

        try {
            double ordinaryScent = AwarenessServerEvents.getEffectivePlayerScent(level, ordinaryPlayer);
            double taggedScent = AwarenessServerEvents.getEffectivePlayerScent(level, taggedPlayer);
            if (!Double.isFinite(ordinaryScent) || !Double.isFinite(taggedScent)
                    || Math.abs(taggedScent - ordinaryScent) > 0.001) {
                helper.fail("Spoiled-only NBT changed player scent without the Spoiled mod: ordinary="
                        + ordinaryScent + ", tagged=" + taggedScent);
                return;
            }
        } finally {
            level.getServer().getPlayerList().remove(ordinaryPlayer);
            level.getServer().getPlayerList().remove(taggedPlayer);
        }
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    private static List<AwarenessSnapshotMessage.Alert> scanAwarenessAlerts(
            ServerLevel level, ServerPlayer player) throws ReflectiveOperationException {
        java.lang.reflect.Method scanMethod = AwarenessServerEvents.class.getDeclaredMethod(
                "scanAwareness", ServerLevel.class, ServerPlayer.class);
        scanMethod.setAccessible(true);
        Object scan = scanMethod.invoke(null, level, player);
        java.lang.reflect.Method alertsMethod = scan.getClass().getDeclaredMethod("alerts");
        alertsMethod.setAccessible(true);
        return (List<AwarenessSnapshotMessage.Alert>) alertsMethod.invoke(scan);
    }

    private static SoundTracker.SoundRecord findVoiceRecord(ServerLevel level, ResourceLocation dimension, BlockPos pos) {
        return SoundTracker.getNearbySounds(level, dimension.toString(), pos, null).stream()
                .filter(record -> SoundMessage.VOICE_CHAT_SOUND_ID.toString().equals(record.soundId)
                        && record.pos.equals(pos))
                .findFirst().orElse(null);
    }

    private static String describeVoiceRecord(SoundTracker.SoundRecord record) {
        return record == null ? "missing" : record.soundId + "@" + record.pos
                + " range=" + record.range + " weight=" + record.weight + " ticksRemaining=" + record.ticksRemaining;
    }

    private static void cleanupPlasmoVoiceFixture(ServerLevel level, ResourceLocation dimension, ServerPlayer speaker,
                                                  BlockPos speakPos,
                                                  BlockPos packetPos, int normalRange, double weight,
                                                  int lifetime, boolean enabled, int soundCap) {
        SoundTracker.removeSoundAt(speakPos, dimension.toString());
        if (packetPos != null) SoundTracker.removeSoundAt(packetPos, dimension.toString());
        SoundAttractConfig.SERVER.voiceChatNormalRange.set(normalRange);
        SoundAttractConfig.SERVER.voiceChatWeight.set(weight);
        SoundAttractConfig.COMMON.soundLifetimeTicks.set(lifetime);
        SoundAttractConfig.COMMON.maxSoundsTracked.set(soundCap);
        SoundAttractConfig.SERVER.enableVoiceChatIntegration.set(enabled);
        level.getServer().getPlayerList().remove(speaker);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void floorCreekAndArrowGeometryContracts(GameTestHelper helper) {
        double swimmingInWater = FloorCreekEvents.poseProbability(true, true, false, false, 0.8, 0.4, 0.2, 0.6);
        double swimmingOnLand = FloorCreekEvents.poseProbability(true, false, false, false, 0.8, 0.4, 0.2, 0.6);
        double sprinting = FloorCreekEvents.poseProbability(false, false, true, true, 0.8, 0.4, 0.2, 0.6);
        double sneaking = FloorCreekEvents.poseProbability(false, false, true, false, 0.8, 0.4, 0.2, 0.6);
        double walking = FloorCreekEvents.poseProbability(false, false, false, false, 0.8, 0.4, 0.2, 0.6);
        double perpendicularDistance = ArrowInvestigationHelper.segmentToPointSquaredDistance(
                new Vec3(0, 0, 0), new Vec3(4, 0, 0), new Vec3(2, 3, 0));
        double beyondSegmentDistance = ArrowInvestigationHelper.segmentToPointSquaredDistance(
                new Vec3(0, 0, 0), new Vec3(4, 0, 0), new Vec3(6, 0, 0));
        double deterministicOffset = ArrowInvestigationHelper.randomOffset(new java.util.Random(17L), 4);
        if (swimmingInWater != 0.0 || swimmingOnLand != 0.8 || sprinting != 0.6
                || sneaking != 0.4 || walking != 0.2 || perpendicularDistance != 9.0
                || beyondSegmentDistance != 4.0 || deterministicOffset < -4.0 || deterministicOffset > 4.0) {
            helper.fail("Floor-creak pose selection or arrow near-miss geometry violated its expected contract");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void quantifiedCacheMemoizesAndSeparatesPersistenceMode(GameTestHelper helper) {
        String key = UUID.randomUUID().toString();
        String memoryKey = key + ":memory";
        String diskKey = key + ":disk";
        AtomicInteger memoryLoads = new AtomicInteger();
        AtomicInteger diskLoads = new AtomicInteger();
        String memoryFirst = QuantifiedCacheCompat.getCached("gametest", memoryKey,
                () -> "memory-" + memoryLoads.incrementAndGet(), 1200, 64);
        String memorySecond = QuantifiedCacheCompat.getCached("gametest", memoryKey,
                () -> "unexpected-memory-reload", 1200, 64);
        String diskValue = QuantifiedCacheCompat.getCachedDisk("gametest", diskKey,
                () -> "disk-" + diskLoads.incrementAndGet(), 1200, 64);
        if (!"memory-1".equals(memoryFirst) || !memoryFirst.equals(memorySecond)
                || memoryLoads.get() != 1 || !"disk-1".equals(diskValue) || diskLoads.get() != 1) {
            helper.fail("Quantified/Caffeine cache compatibility lost memoization: memory=" + memoryFirst + "/"
                    + memorySecond + " loads=" + memoryLoads + ", disk=" + diskValue + " loads=" + diskLoads);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:quantified_runtime", timeoutTicks = 240)
    public void quantifiedRuntimeBridgeSubmitsTasksAndUsesExternalCache(GameTestHelper helper) {
        if (!FabricLoader.getInstance().isModLoaded("quantified")) {
            helper.fail("The mandatory Quantified API runtime profile is not loaded");
            return;
        }
        if (!QuantifiedOptionalBridge.isAvailable()) {
            helper.fail("Quantified 2.2.3 API handles did not resolve through the production optional bridge");
            return;
        }

        String testKey = UUID.randomUUID().toString();
        AtomicInteger taskRuns = new AtomicInteger();
        CompletableFuture<String> task = QuantifiedOptionalBridge.trySubmitTask(
                com.example.soundattract.Soundattract.MOD_ID,
                "gametest_quantified_task",
                () -> "task-" + taskRuns.incrementAndGet(),
                true,
                false,
                Duration.ofSeconds(8),
                testKey);
        String taskResult;
        try {
            if (task == null) {
                helper.fail("Quantified accepted the API presence check but rejected task submission");
                return;
            }
            taskResult = task.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            helper.fail("Quantified task did not complete through its production bridge: " + exception);
            return;
        }

        AtomicInteger cacheLoads = new AtomicInteger();
        java.util.function.Supplier<String> loader = () -> "external-" + cacheLoads.incrementAndGet();
        String cacheName = "gametest_external_" + testKey;
        String first = QuantifiedOptionalBridge.tryGetCached(
                com.example.soundattract.Soundattract.MOD_ID, cacheName, testKey, loader,
                Duration.ofMinutes(1), 64, false);
        String second = QuantifiedOptionalBridge.tryGetCached(
                com.example.soundattract.Soundattract.MOD_ID, cacheName, testKey, loader,
                Duration.ofMinutes(1), 64, false);
        Object cacheManager = QuantifiedOptionalBridge.tryFetchCacheManager(
                com.example.soundattract.Soundattract.MOD_ID);
        Boolean memoryPressure = QuantifiedOptionalBridge.tryIsCacheMemoryPressureHigh(cacheManager);

        if (!"task-1".equals(taskResult) || taskRuns.get() != 1) {
            helper.fail("Quantified task result differed from the submitted work: result=" + taskResult
                    + ", runs=" + taskRuns.get());
            return;
        }
        if (!"external-1".equals(first) || !first.equals(second) || cacheLoads.get() != 1) {
            helper.fail("Quantified's external cache did not retain the value independently of Caffeine's front cache: first="
                    + first + ", second=" + second + ", loads=" + cacheLoads.get());
            return;
        }
        if (cacheManager == null || memoryPressure == null) {
            helper.fail("Quantified cache manager introspection failed through the production bridge: manager="
                    + cacheManager + ", memoryPressure=" + memoryPressure);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:quantified_runtime", timeoutTicks = 240)
    public void quantifiedWorkSchedulerFlushesScoresAndStopsCleanly(GameTestHelper helper) {
        QuantifiedWorkScheduler scheduler = new QuantifiedWorkScheduler();
        UUID mobId = UUID.randomUUID();
        String dimension = "minecraft:overworld";
        var request = new com.example.soundattract.worker.WorkerScheduler.SoundScoreRequest(
                mobId, 0.0, 64.0, 0.0, 100L, null, dimension,
                List.of(
                        new com.example.soundattract.worker.WorkerScheduler.SoundCandidate(
                                "soundattract:lower", 8.0, 64.0, 0.0, 100L, 32.0, 1.0, 1.0),
                        new com.example.soundattract.worker.WorkerScheduler.SoundCandidate(
                                "soundattract:higher", 3.0, 64.0, 0.0, 100L, 32.0, 2.0, 1.0)),
                1.0, 0.0, 0);
        CompletableFuture<?> completion = (CompletableFuture<?>) scheduler.submitSoundScore(List.of(request));
        scheduler.flushPendingSoundScores();
        try {
            completion.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            helper.fail("QuantifiedWorkScheduler failed to complete a flushed sound-score request: " + exception);
            return;
        }

        List<com.example.soundattract.worker.WorkerScheduler.SoundScoreResult> results = scheduler.drainSoundScoreResults();
        com.example.soundattract.worker.WorkerScheduler.SoundScoreResult result = results.stream()
                .filter(candidate -> mobId.equals(candidate.mobUuid()))
                .findFirst().orElse(null);
        if (result == null || !"soundattract:higher".equals(result.soundId())
                || !dimension.equals(result.dimensionKey()) || Math.abs(result.score() - 2.0) > 0.001) {
            helper.fail("QuantifiedWorkScheduler did not flush and return the expected best sound score: result="
                    + result + ", observed=" + results);
            return;
        }

        QuantifiedWorkScheduler stoppedScheduler = new QuantifiedWorkScheduler();
        CompletableFuture<?> pending = (CompletableFuture<?>) stoppedScheduler.submitSoundScore(List.of(request));
        stoppedScheduler.clearResults();
        if (!pending.isCompletedExceptionally() || !stoppedScheduler.drainSoundScoreResults().isEmpty()) {
            helper.fail("Stopping QuantifiedWorkScheduler did not fail pending requests and discard late results: exceptional="
                    + pending.isCompletedExceptionally());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:quantified_runtime")
    public void soundNoveltyBonusPrefersRecentSoundOnlyWithinConfiguredWindow(GameTestHelper helper) {
        UUID mobId = UUID.randomUUID();
        var recent = new com.example.soundattract.worker.WorkerScheduler.SoundCandidate(
                "soundattract:recent", 4.0, 64.0, 0.0, 150L, 32.0, 1.0, 1.0);
        var established = new com.example.soundattract.worker.WorkerScheduler.SoundCandidate(
                "soundattract:established", 2.0, 64.0, 0.0, 99L, 32.0, 5.0, 1.0);

        var withinWindow = new com.example.soundattract.worker.WorkerScheduler.SoundScoreRequest(
                mobId, 0.0, 64.0, 0.0, 200L, null, "minecraft:overworld",
                List.of(recent, established), 1.0, 9.5, 100);
        var recentResult = com.example.soundattract.worker.WorkerComputations.computeSoundScore(
                withinWindow, Long.MAX_VALUE);
        if (!"soundattract:recent".equals(recentResult.soundId())
                || Math.abs(recentResult.score() - 10.5) > 0.001) {
            helper.fail("A recent sound did not receive the configured novelty bonus: " + recentResult);
            return;
        }

        var pastWindow = new com.example.soundattract.worker.WorkerScheduler.SoundScoreRequest(
                mobId, 0.0, 64.0, 0.0, 251L, null, "minecraft:overworld",
                List.of(recent, established), 1.0, 9.5, 100);
        var establishedResult = com.example.soundattract.worker.WorkerComputations.computeSoundScore(
                pastWindow, Long.MAX_VALUE);
        if (!"soundattract:established".equals(establishedResult.soundId())
                || Math.abs(establishedResult.score() - 5.0) > 0.001) {
            helper.fail("An expired novelty bonus still changed sound selection: " + establishedResult);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:smartbrainlib_fallback", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void smartBrainLibIntegrationRejectsVanillaBrain(GameTestHelper helper) {
        Zombie vanillaBrainMob = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        boolean attached = com.example.soundattract.integration.smartbrainlib.SmartBrainLibCompat
                .tryAttachSoundAttractBrain(vanillaBrainMob);
        if (attached) {
            helper.fail("SmartBrainLib integration accepted a vanilla brain");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:quantified_runtime", timeoutTicks = 240)
    public void quantifiedSchedulerCoalescesDuplicatesAndBoundsPendingQueue(GameTestHelper helper) {
        QuantifiedWorkScheduler coalescingScheduler = new QuantifiedWorkScheduler();
        UUID mobId = UUID.randomUUID();
        String dimension = "minecraft:overworld";
        var firstRequest = new com.example.soundattract.worker.WorkerScheduler.SoundScoreRequest(
                mobId, 0.0, 64.0, 0.0, 100L, null, dimension,
                List.of(new com.example.soundattract.worker.WorkerScheduler.SoundCandidate(
                        "soundattract:first", 2.0, 64.0, 0.0, 100L, 32.0, 1.0, 1.0)),
                1.0, 0.0, 0);
        var duplicateRequest = new com.example.soundattract.worker.WorkerScheduler.SoundScoreRequest(
                mobId, 0.0, 64.0, 0.0, 100L, null, dimension,
                List.of(new com.example.soundattract.worker.WorkerScheduler.SoundCandidate(
                        "soundattract:duplicate", 1.0, 64.0, 0.0, 100L, 32.0, 5.0, 1.0)),
                1.0, 0.0, 0);
        CompletableFuture<?> coalesced = (CompletableFuture<?>) coalescingScheduler.submitSoundScore(
                List.of(firstRequest, duplicateRequest));
        coalescingScheduler.flushPendingSoundScores();
        try {
            coalesced.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            coalescingScheduler.clearResults();
            helper.fail("Quantified scheduler did not complete a coalesced duplicate request: " + exception);
            return;
        }
        List<com.example.soundattract.worker.WorkerScheduler.SoundScoreResult> coalescedResults =
                coalescingScheduler.drainSoundScoreResults().stream()
                        .filter(result -> mobId.equals(result.mobUuid())).toList();
        coalescingScheduler.clearResults();
        if (coalescedResults.size() != 1 || !"soundattract:first".equals(coalescedResults.get(0).soundId())) {
            helper.fail("Duplicate mob/dimension requests were not coalesced to the original queued request: "
                    + coalescedResults);
            return;
        }

        QuantifiedWorkScheduler capacityScheduler = new QuantifiedWorkScheduler();
        List<com.example.soundattract.worker.WorkerScheduler.SoundScoreRequest> uniqueRequests = new ArrayList<>(4096);
        for (int i = 0; i < 4096; i++) {
            uniqueRequests.add(new com.example.soundattract.worker.WorkerScheduler.SoundScoreRequest(
                    UUID.randomUUID(), 0.0, 64.0, 0.0, 100L, null, dimension, List.of(), 1.0, 0.0, 0));
        }
        CompletableFuture<?> atCapacity = (CompletableFuture<?>) capacityScheduler.submitSoundScore(uniqueRequests);
        var overflowRequest = new com.example.soundattract.worker.WorkerScheduler.SoundScoreRequest(
                UUID.randomUUID(), 0.0, 64.0, 0.0, 100L, null, dimension, List.of(), 1.0, 0.0, 0);
        CompletableFuture<?> overflow = (CompletableFuture<?>) capacityScheduler.submitSoundScore(List.of(overflowRequest));
        boolean rejectedAtCapacity = overflow.isCompletedExceptionally();
        capacityScheduler.clearResults();
        if (!rejectedAtCapacity || !atCapacity.isCompletedExceptionally()) {
            helper.fail("Quantified scheduler did not reject the request beyond its 4096-entry pending queue and stop-clean "
                    + "the accepted pending batch: overflowRejected=" + rejectedAtCapacity
                    + ", acceptedBatchStopped=" + atCapacity.isCompletedExceptionally());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:customnpcs_runtime", timeoutTicks = 160)
    public void customNpcsProfileAddsGoalsAndAppliesStealthTargetGate(GameTestHelper helper) {
        if (!FabricLoader.getInstance().isModLoaded("customnpcs")) {
            Zombie vanillaMob = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
            com.example.soundattract.mixin.CustomNpcsMixinPlugin mixinPlugin =
                    new com.example.soundattract.mixin.CustomNpcsMixinPlugin();
            mixinPlugin.onLoad("com.example.soundattract.mixin");
            boolean customNpcMixinsDisabled = !mixinPlugin.shouldApplyMixin(
                    "noppes.npcs.entity.EntityNPCInterface",
                    "com.example.soundattract.mixin.CustomNpcsUpdateTasksMixin");
            boolean vanillaMobNotMisidentified = !com.example.soundattract.event.SoundAttractionEvents
                    .isCustomNpcsMob(vanillaMob);
            if (!customNpcMixinsDisabled || !vanillaMobNotMisidentified) {
                helper.fail("CustomNPCs-absent fallback mismatch: mixinsDisabled=" + customNpcMixinsDisabled
                        + ", vanillaMobNotMisidentified=" + vanillaMobNotMisidentified);
                return;
            }
            helper.succeed();
            return;
        }
        var npcType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(
                ResourceLocation.fromNamespaceAndPath("customnpcs", "customnpc")).orElse(null);
        if (npcType == null) {
            helper.fail("CustomNPCs entity type customnpcs:customnpc is not registered");
            return;
        }
        var level = helper.getLevel();
        net.minecraft.world.entity.Entity created = npcType.create(level);
        if (!(created instanceof net.minecraft.world.entity.Mob npc)) {
            helper.fail("CustomNPCs registered entity did not create a Mob: " + created);
            return;
        }
        var probeNpcGoal = new noppes.npcs.ai.SoundAttractGameTestGoal();

        boolean oldIntegration = SoundAttractConfig.COMMON.enableCustomNpcsIntegration.get();
        boolean oldStealth = SoundAttractConfig.COMMON.enableStealthMechanics.get();
        boolean oldTieredStealth = SoundAttractConfig.COMMON.enableTieredStealthPerformance.get();
        boolean oldXray = SoundAttractConfig.COMMON.enableXrayTargeting.get();
        boolean oldSmartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        try {
            SoundAttractConfig.COMMON.enableCustomNpcsIntegration.set(true);
            SoundAttractConfig.COMMON.enableStealthMechanics.set(true);
            SoundAttractConfig.COMMON.enableTieredStealthPerformance.set(false);
            SoundAttractConfig.COMMON.enableXrayTargeting.set(false);
            SoundAttractConfig.COMMON.edgeMobSmartBehavior.set(false);
            npc.setPos(2.5, 1.0, 2.5);
            var originalNpcGoalSelector = ((com.example.soundattract.mixin.MobAccessor) npc).getGoalSelector();
            originalNpcGoalSelector.addGoal(99, probeNpcGoal);
            level.addFreshEntity(npc);
            // Entity-load goal installation reads the integration switches synchronously.
            SoundAttractConfig.COMMON.enableCustomNpcsIntegration.set(oldIntegration);
            SoundAttractConfig.COMMON.enableStealthMechanics.set(oldStealth);
            SoundAttractConfig.COMMON.enableTieredStealthPerformance.set(oldTieredStealth);
            SoundAttractConfig.COMMON.enableXrayTargeting.set(oldXray);
            SoundAttractConfig.COMMON.edgeMobSmartBehavior.set(oldSmartEdge);
        } catch (Throwable throwable) {
            SoundAttractConfig.COMMON.enableCustomNpcsIntegration.set(oldIntegration);
            SoundAttractConfig.COMMON.enableStealthMechanics.set(oldStealth);
            SoundAttractConfig.COMMON.enableTieredStealthPerformance.set(oldTieredStealth);
            SoundAttractConfig.COMMON.enableXrayTargeting.set(oldXray);
            SoundAttractConfig.COMMON.edgeMobSmartBehavior.set(oldSmartEdge);
            helper.fail("Could not spawn a CustomNPCs mob for the present-mod profile: " + throwable);
            return;
        }

        net.minecraft.world.entity.Mob[] stealthTargetHolder = new net.minecraft.world.entity.Mob[1];
        helper.runAfterDelay(2, () -> {
            try {
                if (!com.example.soundattract.event.SoundAttractionEvents.isCustomNpcsMob(npc)) {
                    helper.fail("Spawned CustomNPCs entity was not recognized by the production integration detector");
                    return;
                }
                var selector = ((com.example.soundattract.mixin.MobAccessor) npc).getGoalSelector();
                boolean hasAttraction = selector.getAvailableGoals().stream()
                        .anyMatch(wrapped -> wrapped.getGoal() instanceof AttractionGoal
                                || wrapped.getGoal() instanceof com.example.soundattract.ai.LeaderAttractionGoal);
                boolean hasFollowLeader = selector.getAvailableGoals().stream()
                        .anyMatch(wrapped -> wrapped.getGoal() instanceof com.example.soundattract.ai.FollowLeaderGoal);
                if (!hasAttraction || !hasFollowLeader) {
                    helper.fail("CustomNPCs entity-load integration did not install attraction and follow-leader goals: "
                            + selector.getAvailableGoals());
                    return;
                }

                // CustomNPCs keeps updateTasks private and rebuilds the live selectors there.
                // Invoke that real method so the TAIL mixin, not just its helper, must restore
                // Sound Attract goals after CustomNPCs replaces its task lists.
                boolean previousUpdateIntegration = SoundAttractConfig.COMMON.enableCustomNpcsIntegration.get();
                try {
                    SoundAttractConfig.COMMON.enableCustomNpcsIntegration.set(true);
                    java.lang.reflect.Method updateTasks = null;
                    for (Class<?> type = npc.getClass(); type != null && updateTasks == null; type = type.getSuperclass()) {
                        try {
                            updateTasks = type.getDeclaredMethod("updateTasks");
                        } catch (NoSuchMethodException ignored) {
                            // Continue up to EntityNPCInterface, which declares the private lifecycle method.
                        }
                    }
                    if (updateTasks == null) {
                        helper.fail("Could not locate CustomNPCs' private updateTasks lifecycle method");
                        return;
                    }
                    updateTasks.setAccessible(true);
                    updateTasks.invoke(npc);
                } catch (ReflectiveOperationException exception) {
                    helper.fail("Could not invoke CustomNPCs' real task rebuild lifecycle: " + exception);
                    return;
                } finally {
                    SoundAttractConfig.COMMON.enableCustomNpcsIntegration.set(previousUpdateIntegration);
                }
                selector = ((com.example.soundattract.mixin.MobAccessor) npc).getGoalSelector();
                boolean attractionRestoredByMixin = selector.getAvailableGoals().stream()
                        .anyMatch(wrapped -> wrapped.getGoal() instanceof AttractionGoal);
                boolean followLeaderRestoredByMixin = selector.getAvailableGoals().stream()
                        .anyMatch(wrapped -> wrapped.getGoal() instanceof com.example.soundattract.ai.FollowLeaderGoal);
                boolean nativeCustomNpcGoalWrappedByMixin = selector.getAvailableGoals().stream()
                        .map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal)
                        .filter(com.example.soundattract.ai.SoundInterruptWrapperGoal.class::isInstance)
                        .map(com.example.soundattract.ai.SoundInterruptWrapperGoal.class::cast)
                        .anyMatch(wrapper -> wrapper.getDelegate().getClass().getName().startsWith("noppes.npcs.ai."));
                if (!attractionRestoredByMixin || !followLeaderRestoredByMixin || !nativeCustomNpcGoalWrappedByMixin) {
                    helper.fail("CustomNPCs updateTasks() did not restore Sound Attract goals through its TAIL mixin: "
                            + "attraction=" + attractionRestoredByMixin + ", followLeader=" + followLeaderRestoredByMixin
                            + ", nativeNpcGoalWrapped=" + nativeCustomNpcGoalWrappedByMixin
                            + ", goals=" + selector.getAvailableGoals());
                    return;
                }

                // The default CustomNPC template has no built-in task list and therefore
                // has no native AI task to wrap; re-add the AI-package probe after testing
                // updateTasks() and exercise the production wrapper helper with it.
                selector.addGoal(99, probeNpcGoal);
                com.example.soundattract.integration.customnpcs.GoalWrappingHelper
                        .wrapCustomNpcsGoals(npc, selector);
                com.example.soundattract.ai.SoundInterruptWrapperGoal soundWrapper = selector.getAvailableGoals().stream()
                        .map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal)
                        .filter(com.example.soundattract.ai.SoundInterruptWrapperGoal.class::isInstance)
                        .map(com.example.soundattract.ai.SoundInterruptWrapperGoal.class::cast)
                        .filter(wrapper -> wrapper.getDelegate() == probeNpcGoal)
                        .findFirst().orElse(null);
                if (soundWrapper == null) {
                    helper.fail("CustomNPCs entity-load integration did not wrap the CustomNPC-package probe goal: "
                            + selector.getAvailableGoals().stream()
                            .map(wrapped -> wrapped.getGoal().getClass().getName()).toList());
                    return;
                }
                if (soundWrapper.getDelegate() != probeNpcGoal) {
                    helper.fail("CustomNPCs wrapper replaced a different goal: " + soundWrapper.getDelegate().getClass());
                    return;
                }

                int previousWrapperSoundCap = reserveSoundCapacityForGameTest(1);
                boolean previousWrapperIntegration = SoundAttractConfig.COMMON.enableCustomNpcsIntegration.get();
                BlockPos wrapperSoundPos = npc.blockPosition().offset(3, 0, 0);
                String wrapperSoundDimension = level.dimension().location().toString();
                try {
                    SoundAttractConfig.COMMON.enableCustomNpcsIntegration.set(true);
                    SoundTracker.addVirtualSound(wrapperSoundPos, wrapperSoundDimension, 64.0, 100.0, 100,
                            UUID.randomUUID(), "soundattract:customnpcs_wrapper_test");
                    if (soundWrapper.canUse()) {
                        helper.fail("CustomNPCs' wrapped goal did not yield to an available tracked sound");
                        return;
                    }
                } finally {
                    SoundTracker.removeSoundAt(wrapperSoundPos, wrapperSoundDimension);
                    SoundAttractConfig.COMMON.enableCustomNpcsIntegration.set(previousWrapperIntegration);
                    SoundAttractConfig.COMMON.maxSoundsTracked.set(previousWrapperSoundCap);
                }

                SoundAttractConfig.COMMON.enableStealthMechanics.set(true);
                SoundAttractConfig.COMMON.enableTieredStealthPerformance.set(false);
                SoundAttractConfig.COMMON.enableXrayTargeting.set(false);
                SoundAttractConfig.COMMON.edgeMobSmartBehavior.set(false);
                net.minecraft.world.entity.Mob distantTarget = EntityType.ZOMBIE.create(level);
                if (distantTarget == null) {
                    helper.fail("Could not create a living mob for the CustomNPCs stealth target gate");
                    return;
                }
                stealthTargetHolder[0] = distantTarget;
                distantTarget.setNoAi(true);
                distantTarget.setPos(npc.getX() + 128.0, npc.getY(), npc.getZ());
                level.addFreshEntity(distantTarget);
                if (com.example.soundattract.event.StealthDetectionEvents.canMobDetectLivingEntity(npc, distantTarget)) {
                    helper.fail("CustomNPCs stealth fixture unexpectedly allows a living target 128 blocks away");
                    return;
                }
                npc.setTarget(distantTarget);
                if (npc.getTarget() != null) {
                    helper.fail("CustomNPCs target-set mixin retained a target rejected by production stealth detection");
                    return;
                }
                helper.succeed();
            } finally {
                if (stealthTargetHolder[0] != null) stealthTargetHolder[0].discard();
                com.example.soundattract.integration.customnpcs.GoalWrappingHelper.clearCache(npc);
                npc.discard();
                SoundAttractConfig.COMMON.enableCustomNpcsIntegration.set(oldIntegration);
                SoundAttractConfig.COMMON.enableStealthMechanics.set(oldStealth);
                SoundAttractConfig.COMMON.enableTieredStealthPerformance.set(oldTieredStealth);
                SoundAttractConfig.COMMON.enableXrayTargeting.set(oldXray);
                SoundAttractConfig.COMMON.edgeMobSmartBehavior.set(oldSmartEdge);
            }
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void concealLootTablesAndItemEligibilityMatchForgeRules(GameTestHelper helper) {
        java.util.Set<ResourceLocation> expectedArmorTables = java.util.Set.of(
                ResourceLocation.fromNamespaceAndPath("minecraft", "chests/ancient_city"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "chests/bastion_treasure"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "chests/nether_bridge"));
        java.util.Set<ResourceLocation> expectedToolTables = java.util.Set.of(
                ResourceLocation.fromNamespaceAndPath("minecraft", "chests/ancient_city"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "chests/end_city_treasure"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "chests/stronghold_crossing"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "chests/bastion_other"));
        java.util.Set<ResourceLocation> expectedBookTables = java.util.Set.of(
                ResourceLocation.fromNamespaceAndPath("minecraft", "chests/ancient_city"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "chests/end_city_treasure"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "chests/stronghold_library"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "chests/woodland_mansion"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "chests/buried_treasure"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "chests/desert_pyramid"));

        boolean tableGroupsMatchForge = com.example.soundattract.loot.LootModifierTables.ARMOR_TABLES
                .equals(expectedArmorTables)
                && com.example.soundattract.loot.LootModifierTables.TOOL_TABLES.equals(expectedToolTables)
                && com.example.soundattract.loot.LootModifierTables.BOOK_TABLES.equals(expectedBookTables);

        net.minecraft.world.item.ItemStack enchantedArmor = new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.DIAMOND_CHESTPLATE);
        enchantedArmor.enchant(helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 1);
        boolean armorEligibilityMatchesForge = com.example.soundattract.loot.LootModifierTables.isEligibleArmor(
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE))
                && !com.example.soundattract.loot.LootModifierTables.isEligibleArmor(enchantedArmor)
                && !com.example.soundattract.loot.LootModifierTables.isEligibleArmor(
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ELYTRA))
                && !com.example.soundattract.loot.LootModifierTables.isEligibleArmor(
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE))
                && !com.example.soundattract.loot.LootModifierTables.isEligibleArmor(
                        net.minecraft.world.item.ItemStack.EMPTY);

        java.util.List<net.minecraft.world.item.Item> forgeEligibleTools = java.util.List.of(
                net.minecraft.world.item.Items.DIAMOND_PICKAXE,
                net.minecraft.world.item.Items.BOW,
                net.minecraft.world.item.Items.CROSSBOW,
                net.minecraft.world.item.Items.TRIDENT,
                net.minecraft.world.item.Items.SHIELD,
                net.minecraft.world.item.Items.SHEARS,
                net.minecraft.world.item.Items.FISHING_ROD);
        boolean everyForgeToolTypeAccepted = forgeEligibleTools.stream().allMatch(item ->
                com.example.soundattract.loot.LootModifierTables.isEligibleTool(
                        new net.minecraft.world.item.ItemStack(item)));
        net.minecraft.world.item.ItemStack enchantedTool = new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.DIAMOND_PICKAXE);
        enchantedTool.enchant(helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 1);
        boolean toolEligibilityMatchesForge = everyForgeToolTypeAccepted
                && !com.example.soundattract.loot.LootModifierTables.isEligibleTool(enchantedTool)
                && !com.example.soundattract.loot.LootModifierTables.isEligibleTool(
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE))
                && !com.example.soundattract.loot.LootModifierTables.isEligibleTool(
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ELYTRA))
                && !com.example.soundattract.loot.LootModifierTables.isEligibleTool(
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT))
                && !com.example.soundattract.loot.LootModifierTables.isEligibleTool(
                        net.minecraft.world.item.ItemStack.EMPTY);

        if (!tableGroupsMatchForge || !armorEligibilityMatchesForge || !toolEligibilityMatchesForge) {
            helper.fail("Fabric Conceal loot table groups or item eligibility diverged from Forge: armorTables="
                    + com.example.soundattract.loot.LootModifierTables.ARMOR_TABLES + "/" + expectedArmorTables
                    + ", toolTables=" + com.example.soundattract.loot.LootModifierTables.TOOL_TABLES
                    + "/" + expectedToolTables + ", bookTables="
                    + com.example.soundattract.loot.LootModifierTables.BOOK_TABLES + "/" + expectedBookTables
                    + ", armorEligible=" + armorEligibilityMatchesForge
                    + ", toolTypesAccepted=" + everyForgeToolTypeAccepted
                    + ", toolEligible=" + toolEligibilityMatchesForge);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:loot_pools", timeoutTicks = 2400)
    public void actualSupportedLootPoolsApplyConcealModifiers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.server.MinecraftServer server = level.getServer();
        java.util.Set<ResourceLocation> supported = com.example.soundattract.loot.LootModifierTables.markerTables();
        java.util.Map<ResourceLocation, Integer> generatedCounts = new java.util.HashMap<>();
        java.util.Map<ResourceLocation, Integer> armorConcealCounts = new java.util.HashMap<>();
        java.util.Map<ResourceLocation, Integer> toolConcealCounts = new java.util.HashMap<>();
        java.util.Map<ResourceLocation, Integer> bookConcealCounts = new java.util.HashMap<>();
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)));

        for (ResourceLocation tableId : supported) {
            net.minecraft.world.level.storage.loot.LootTable table = server.reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, tableId));
            boolean markerPresent = com.example.soundattract.loot.ModLootModifiers.wasMarkerTableModified(tableId)
                    && com.example.soundattract.loot.ModLootModifiers.markerCallbackCount(tableId) > 0L;
            if (!markerPresent) {
                helper.fail("Supported loot table was not marked by the production Fabric reload callback: " + tableId);
                return;
            }

            for (long seed = 0; seed < 2048; seed++) {
                LootParams params = new LootParams.Builder(level)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, origin)
                        .create(LootContextParamSets.CHEST);
                LootContext context = new LootContext.Builder(params).withOptionalRandomSeed(seed).create(java.util.Optional.of(tableId));
                List<net.minecraft.world.item.ItemStack> drops = new java.util.ArrayList<>();
                table.getRandomItems(context, drops::add);
                generatedCounts.merge(tableId, drops.size(), Integer::sum);

                for (net.minecraft.world.item.ItemStack stack : drops) {
                    int concealLevel = concealLevel(stack, helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(com.example.soundattract.enchantment.ModEnchantments.CONCEAL));
                    if (concealLevel <= 0) continue;

                    net.minecraft.world.item.ItemStack unenchantedItem =
                            new net.minecraft.world.item.ItemStack(stack.getItem(), stack.getCount());
                    boolean allowedArmor = com.example.soundattract.loot.LootModifierTables.ARMOR_TABLES
                            .contains(tableId)
                            && com.example.soundattract.loot.LootModifierTables.isEligibleArmor(unenchantedItem);
                    boolean allowedTool = com.example.soundattract.loot.LootModifierTables.TOOL_TABLES
                            .contains(tableId)
                            && com.example.soundattract.loot.LootModifierTables.isEligibleTool(unenchantedItem);
                    boolean allowedBook = com.example.soundattract.loot.LootModifierTables.BOOK_TABLES.contains(tableId)
                            && stack.is(net.minecraft.world.item.Items.ENCHANTED_BOOK);
                    if (allowedArmor) armorConcealCounts.merge(tableId, 1, Integer::sum);
                    if (allowedTool) toolConcealCounts.merge(tableId, 1, Integer::sum);
                    if (allowedBook) bookConcealCounts.merge(tableId, 1, Integer::sum);
                    // Vanilla's enchant_with_levels loot function can select this registered treasure
                    // enchantment on gear in any pool. Only the modifier-specific eligibility is
                    // constrained to these table groups; global output eligibility is not.
                }
            }
        }

        boolean everyTableGeneratedLoot = supported.stream().allMatch(id -> generatedCounts.getOrDefault(id, 0) > 0);
        boolean anyBookPoolAppliedConceal = com.example.soundattract.loot.LootModifierTables.BOOK_TABLES.stream()
                .anyMatch(id -> bookConcealCounts.getOrDefault(id, 0) > 0);
        boolean anyArmorPoolAppliedConceal = com.example.soundattract.loot.LootModifierTables.ARMOR_TABLES.stream()
                .anyMatch(id -> armorConcealCounts.getOrDefault(id, 0) > 0);
        boolean anyToolPoolAppliedConceal = com.example.soundattract.loot.LootModifierTables.TOOL_TABLES.stream()
                .anyMatch(id -> toolConcealCounts.getOrDefault(id, 0) > 0);
        if (!everyTableGeneratedLoot || !anyBookPoolAppliedConceal || !anyArmorPoolAppliedConceal
                || !anyToolPoolAppliedConceal) {
            helper.fail("Actual supported chest pools did not demonstrate the expected Conceal eligibility: "
                    + "generatedCounts=" + generatedCounts + ", armorConceal=" + armorConcealCounts
                    + ", toolConceal=" + toolConcealCounts + ", bookConceal=" + bookConcealCounts
                    + ", anyBookPool=" + anyBookPoolAppliedConceal
                    + ", anyArmorPool=" + anyArmorPoolAppliedConceal
                    + ", anyToolPool=" + anyToolPoolAppliedConceal);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void lootTableMarkerIsConsumedExactlyOnce(GameTestHelper helper) {
        ResourceLocation tableId = ResourceLocation.fromNamespaceAndPath("minecraft", "chests/simple_dungeon");
        LootParams params = new LootParams.Builder(helper.getLevel()).create(LootContextParamSets.EMPTY);
        LootContext context = new LootContext.Builder(params).create(java.util.Optional.of(tableId));
        LootTableMarkerState.mark(context, tableId);
        ResourceLocation firstConsume = LootTableMarkerState.consume(context);
        ResourceLocation secondConsume = LootTableMarkerState.consume(context);
        if (!tableId.equals(firstConsume) || secondConsume != null) {
            helper.fail("Loot-table marker was lost or could be consumed more than once");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:loot_reload", timeoutTicks = 2400)
    public void markedLootTableRunsConcealModifierWithoutDuplicatingDrops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation targetId = ResourceLocation.fromNamespaceAndPath("minecraft", "chests/ancient_city");
        net.minecraft.world.level.storage.loot.LootTable loadedTargetTable =
                level.getServer().reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, targetId));
        boolean loadedCallbackSawTarget = com.example.soundattract.loot.ModLootModifiers
                .wasMarkerTableModified(targetId);
        boolean loadedTableContainsMarkerFunction = com.example.soundattract.loot.ModLootModifiers.wasMarkerTableModified(targetId)
                && com.example.soundattract.loot.ModLootModifiers.markerCallbackCount(targetId) > 0L;
        boolean liveLoadedTableAppliedConceal = false;
        for (long seed = 0; seed < 512 && !liveLoadedTableAppliedConceal; seed++) {
            LootParams loadedParams = new LootParams.Builder(level)
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                            Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))))
                    .create(LootContextParamSets.CHEST);
            LootContext loadedContext = new LootContext.Builder(loadedParams)
                    .withOptionalRandomSeed(seed).create(java.util.Optional.of(targetId));
            List<net.minecraft.world.item.ItemStack> loadedDrops = new java.util.ArrayList<>();
            loadedTargetTable.getRandomItems(loadedContext, loadedDrops::add);
            liveLoadedTableAppliedConceal = loadedDrops.stream().anyMatch(stack ->
                    concealLevel(stack, helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(com.example.soundattract.enchantment.ModEnchantments.CONCEAL)) > 0);
        }
        boolean loadedFabricLootCallbackMarkedTable = loadedCallbackSawTarget
                && loadedTableContainsMarkerFunction && liveLoadedTableAppliedConceal;
        net.minecraft.world.level.storage.loot.LootTable.Builder targetBuilder = net.minecraft.world.level.storage.loot.LootTable.lootTable()
                .withPool(net.minecraft.world.level.storage.loot.LootPool.lootPool()
                        .setRolls(net.minecraft.world.level.storage.loot.providers.number.ConstantValue.exactly(1.0F))
                        .add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(
                                net.minecraft.world.item.Items.DIAMOND_CHESTPLATE)));
        boolean productionMarkerDecision = com.example.soundattract.loot.ModLootModifiers.applyMarker(targetId, targetBuilder);
        net.minecraft.world.level.storage.loot.LootTable targetTable = targetBuilder.build();
        java.util.Set<ResourceLocation> expectedMarkerTables = new java.util.HashSet<>(
                com.example.soundattract.loot.LootModifierTables.ARMOR_TABLES);
        expectedMarkerTables.addAll(com.example.soundattract.loot.LootModifierTables.TOOL_TABLES);
        expectedMarkerTables.addAll(com.example.soundattract.loot.LootModifierTables.BOOK_TABLES);
        java.util.Map<ResourceLocation, Long> callbackCountsBeforeReload = new java.util.HashMap<>();
        for (ResourceLocation supportedId : expectedMarkerTables) {
            callbackCountsBeforeReload.put(supportedId,
                    com.example.soundattract.loot.ModLootModifiers.markerCallbackCount(supportedId));
        }
        boolean everySupportedTableGetsMarked = expectedMarkerTables.equals(
                com.example.soundattract.loot.LootModifierTables.markerTables());
        boolean everySupportedTableLoadedThroughFabricCallback = expectedMarkerTables.stream()
                .allMatch(com.example.soundattract.loot.ModLootModifiers::wasMarkerTableModified);
        for (ResourceLocation supportedId : expectedMarkerTables) {
            everySupportedTableGetsMarked &= com.example.soundattract.loot.ModLootModifiers.applyMarker(
                    supportedId, net.minecraft.world.level.storage.loot.LootTable.lootTable());
        }
        boolean concealApplied = false;
        boolean markerConsumed = false;
        boolean oneDropOnly = false;
        long successfulSeed = -1L;
        int lastGeneratedCount = -1;
        boolean lastMarkerWasConsumed = false;
        LootContext successfulContext = null;

        // This table uses one deterministic entry and consumes no random value
        // before the production 5% roll. Pick a seed whose first Java RNG float
        // is below that threshold so the assertion stays deterministic.
        for (long seed = 0x100000800L; seed < 0x100000A00L && !concealApplied; seed++) {
            LootParams params = new LootParams.Builder(level)
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                            Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))))
                    .create(LootContextParamSets.CHEST);
            LootContext context = new LootContext.Builder(params).withOptionalRandomSeed(seed).create(java.util.Optional.of(targetId));
            List<net.minecraft.world.item.ItemStack> generated = new java.util.ArrayList<>();
            targetTable.getRandomItems(context, generated::add);
            lastGeneratedCount = generated.size();
            boolean markerWasConsumed = LootTableMarkerState.consume(context) == null;
            lastMarkerWasConsumed = markerWasConsumed;
            concealApplied = generated.stream().anyMatch(stack -> stack.is(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE)
                    && concealLevel(stack, helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(com.example.soundattract.enchantment.ModEnchantments.CONCEAL)) > 0);
            if (concealApplied) {
                successfulSeed = seed;
                successfulContext = context;
                markerConsumed = markerWasConsumed;
                oneDropOnly = generated.size() == 1 && generated.get(0).is(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE);
            }
        }

        boolean nonTargetRemainsUnmodified;
        ResourceLocation nonTargetId = ResourceLocation.fromNamespaceAndPath("minecraft", "chests/simple_dungeon");
        net.minecraft.world.level.storage.loot.LootTable.Builder nonTargetBuilder = net.minecraft.world.level.storage.loot.LootTable.lootTable()
                .withPool(net.minecraft.world.level.storage.loot.LootPool.lootPool()
                        .setRolls(net.minecraft.world.level.storage.loot.providers.number.ConstantValue.exactly(1.0F))
                        .add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(
                                net.minecraft.world.item.Items.DIAMOND_CHESTPLATE)));
        boolean nonTargetMarkerDecision = com.example.soundattract.loot.ModLootModifiers.applyMarker(nonTargetId, nonTargetBuilder);
        net.minecraft.world.level.storage.loot.LootTable nonTargetTable = nonTargetBuilder.build();
        LootParams nonTargetParams = new LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                        Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 1))))
                .create(LootContextParamSets.CHEST);
        LootContext nonTargetContext = new LootContext.Builder(nonTargetParams).withOptionalRandomSeed(7L).create(java.util.Optional.of(nonTargetId));
        List<net.minecraft.world.item.ItemStack> nonTargetDrops = new java.util.ArrayList<>();
        nonTargetTable.getRandomItems(nonTargetContext, nonTargetDrops::add);
        nonTargetRemainsUnmodified = nonTargetDrops.stream().noneMatch(stack ->
                concealLevel(stack, helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(com.example.soundattract.enchantment.ModEnchantments.CONCEAL)) > 0);

        List<net.minecraft.world.item.ItemStack> repeatedDrops = new java.util.ArrayList<>();
        if (successfulContext != null) targetTable.getRandomItems(successfulContext, repeatedDrops::add);
        boolean repeatedGenerationHasNoExtraDrops = successfulContext != null && repeatedDrops.size() == 1
                && repeatedDrops.get(0).is(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE);

        ResourceLocation toolTableId = ResourceLocation.fromNamespaceAndPath("minecraft", "chests/stronghold_crossing");
        net.minecraft.world.level.storage.loot.LootTable.Builder toolBuilder = net.minecraft.world.level.storage.loot.LootTable.lootTable()
                .withPool(net.minecraft.world.level.storage.loot.LootPool.lootPool()
                        .setRolls(net.minecraft.world.level.storage.loot.providers.number.ConstantValue.exactly(1.0F))
                        .add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(
                                net.minecraft.world.item.Items.DIAMOND_PICKAXE)));
        boolean toolMarkerDecision = com.example.soundattract.loot.ModLootModifiers.applyMarker(toolTableId, toolBuilder);
        net.minecraft.world.level.storage.loot.LootTable toolTable = toolBuilder.build();
        LootContext toolContext = new LootContext.Builder(new LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                        Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 1, 1))))
                .create(LootContextParamSets.CHEST)).withOptionalRandomSeed(0x100000800L).create(java.util.Optional.of(toolTableId));
        List<net.minecraft.world.item.ItemStack> toolDrops = new java.util.ArrayList<>();
        toolTable.getRandomItems(toolContext, toolDrops::add);
        boolean toolConcealApplied = toolMarkerDecision && toolDrops.size() == 1
                && toolDrops.get(0).is(net.minecraft.world.item.Items.DIAMOND_PICKAXE)
                && net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(com.example.soundattract.enchantment.ModEnchantments.CONCEAL), toolDrops.get(0)) > 0;

        ResourceLocation bookTableId = ResourceLocation.fromNamespaceAndPath("minecraft", "chests/stronghold_library");
        net.minecraft.world.level.storage.loot.LootTable.Builder bookBuilder = net.minecraft.world.level.storage.loot.LootTable.lootTable()
                .withPool(net.minecraft.world.level.storage.loot.LootPool.lootPool()
                        .setRolls(net.minecraft.world.level.storage.loot.providers.number.ConstantValue.exactly(1.0F))
                        .add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(
                                net.minecraft.world.item.Items.PAPER)));
        boolean bookMarkerDecision = com.example.soundattract.loot.ModLootModifiers.applyMarker(bookTableId, bookBuilder);
        net.minecraft.world.level.storage.loot.LootTable bookTable = bookBuilder.build();
        LootContext bookContext = new LootContext.Builder(new LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                        Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 1, 1))))
                .create(LootContextParamSets.CHEST)).withOptionalRandomSeed(0x100000800L).create(java.util.Optional.of(bookTableId));
        List<net.minecraft.world.item.ItemStack> bookDrops = new java.util.ArrayList<>();
        bookTable.getRandomItems(bookContext, bookDrops::add);
        boolean concealBookAdded = bookMarkerDecision && bookDrops.size() == 2
                && bookDrops.stream().anyMatch(stack -> stack.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)
                        && concealLevel(stack, helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(com.example.soundattract.enchantment.ModEnchantments.CONCEAL)) > 0);

        if (!loadedFabricLootCallbackMarkedTable || !everySupportedTableLoadedThroughFabricCallback
                || !productionMarkerDecision || !everySupportedTableGetsMarked || nonTargetMarkerDecision
                || !concealApplied || !markerConsumed || !oneDropOnly || !nonTargetRemainsUnmodified
                || !repeatedGenerationHasNoExtraDrops || !toolConcealApplied || !concealBookAdded) {
            helper.fail("Marked chest loot did not apply Conceal exactly through the real loot path: target=" + targetId
                    + ", loadedTableCallback=" + loadedFabricLootCallbackMarkedTable
                    + "/callbackSawTarget=" + loadedCallbackSawTarget
                    + ", loadedTableContainsMarker=" + loadedTableContainsMarkerFunction
                    + ", liveLootConceal=" + liveLoadedTableAppliedConceal
                    + ", everySupportedLiveCallback=" + everySupportedTableLoadedThroughFabricCallback
                    + ", markerDecision=" + productionMarkerDecision + ", nonTargetMarker=" + nonTargetMarkerDecision
                    + ", allowlistComplete=" + everySupportedTableGetsMarked
                    + ", drops=" + lastGeneratedCount
                    + ", seed=" + successfulSeed + ", conceal=" + concealApplied + ", markerConsumed="
                    + markerConsumed + "/" + lastMarkerWasConsumed
                    + ", armorTable=" + com.example.soundattract.loot.LootModifierTables.ARMOR_TABLES.contains(targetId)
                    + ", canEnchant=" + com.example.soundattract.enchantment.EnchantmentConceal.canEnchant(
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE))
                    + ", oneDrop=" + oneDropOnly + ", nonTargetUnmodified=" + nonTargetRemainsUnmodified
                    + ", repeatedNoExtraDrops=" + repeatedGenerationHasNoExtraDrops
                    + ", toolConceal=" + toolConcealApplied + ", concealBook=" + concealBookAdded);
            return;
        }
        net.minecraft.server.MinecraftServer server = level.getServer();
        server.reloadResources(new java.util.ArrayList<>(server.getPackRepository().getSelectedIds()))
                .whenComplete((ignored, reloadError) -> server.execute(() -> {
                    if (reloadError != null) {
                        helper.fail("Loot resource reload failed before the post-reload marker check: " + reloadError);
                        return;
                    }

                    net.minecraft.world.level.storage.loot.LootTable reloadedTarget =
                            server.reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, targetId));
                    boolean targetMarkerRestored = com.example.soundattract.loot.ModLootModifiers.markerCallbackCount(targetId)
                            > callbackCountsBeforeReload.getOrDefault(targetId, 0L);
                    boolean everyAllowlistedCallbackRepeated = expectedMarkerTables.stream().allMatch(id ->
                            com.example.soundattract.loot.ModLootModifiers.markerCallbackCount(id)
                                    > callbackCountsBeforeReload.getOrDefault(id, 0L));
                    boolean reloadedLootHasConceal = false;
                    for (long seed = 0; seed < 512 && !reloadedLootHasConceal; seed++) {
                        LootParams reloadedParams = new LootParams.Builder(level)
                                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                                        Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))))
                                .create(LootContextParamSets.CHEST);
                        LootContext reloadedContext = new LootContext.Builder(reloadedParams)
                                .withOptionalRandomSeed(seed).create(java.util.Optional.of(targetId));
                        List<net.minecraft.world.item.ItemStack> reloadedDrops = new java.util.ArrayList<>();
                        reloadedTarget.getRandomItems(reloadedContext, reloadedDrops::add);
                        reloadedLootHasConceal = reloadedDrops.stream().anyMatch(stack ->
                                concealLevel(stack, helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(com.example.soundattract.enchantment.ModEnchantments.CONCEAL)) > 0);
                    }

                    if (!targetMarkerRestored || !everyAllowlistedCallbackRepeated || !reloadedLootHasConceal) {
                        helper.fail("Loot marker integration did not survive a live resource reload: targetMarker="
                                + targetMarkerRestored + ", allAllowlistedCallbacksRepeated="
                                + everyAllowlistedCallbackRepeated + ", postReloadConceal=" + reloadedLootHasConceal
                                + ", callbackCounts=" + expectedMarkerTables.stream().collect(
                                        java.util.stream.Collectors.toMap(id -> id,
                                                com.example.soundattract.loot.ModLootModifiers::markerCallbackCount)));
                        return;
                    }
                    helper.succeed();
                }));
    }


    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void arrowInvestigationTracksAndRemovesProjectileState(GameTestHelper helper) {
        Arrow arrow = helper.spawn(EntityType.ARROW, 1, 1, 1);
        ServerLevel level = helper.getLevel();
        int entityId = arrow.getId();
        boolean trackedAfterJoin = ArrowInvestigationEvents.isTrackedForTesting(level, entityId);
        ArrowInvestigationEvents.onEntityLeave(arrow);
        boolean removedAfterLeave = !ArrowInvestigationEvents.isTrackedForTesting(level, entityId);
        if (!trackedAfterJoin || !removedAfterLeave) {
            helper.fail("Watched arrow entity lifecycle did not register and clean investigation state before stationary-arrow pruning: tracked="
                    + trackedAfterJoin + ", removed=" + removedAfterLeave);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:arrow_dimension", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void arrowScentTickPreservesProjectilesFromOtherDimensions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean oldEnabled = SoundAttractConfig.COMMON.enableArrowScentTrail.get();
        Arrow arrow = helper.spawn(EntityType.ARROW, 1, 1, 1);
        ArrowInvestigationEvents.ArrowKey foreignDimensionKey = new ArrowInvestigationEvents.ArrowKey(
                net.minecraft.world.level.Level.NETHER.location(), arrow.getId());
        ArrowInvestigationEvents.ArrowState foreignDimensionState = new ArrowInvestigationEvents.ArrowState(
                arrow.position(), level.getGameTime(), ArrowInvestigationEvents.DISPENSER_SENTINEL_UUID, false);
        boolean retainedAcrossForeignTick = false;
        try {
            SoundAttractConfig.COMMON.enableArrowScentTrail.set(true);
            ArrowInvestigationEvents.ARROWS.put(foreignDimensionKey, foreignDimensionState);
            com.example.soundattract.event.ArrowScentEvents.onLevelTick(level);
            retainedAcrossForeignTick = ArrowInvestigationEvents.isTrackedForTesting(level, arrow.getId())
                    && ArrowInvestigationEvents.ARROWS.get(foreignDimensionKey) == foreignDimensionState;
        } finally {
            SoundAttractConfig.COMMON.enableArrowScentTrail.set(oldEnabled);
            ArrowInvestigationEvents.ARROWS.remove(foreignDimensionKey, foreignDimensionState);
            ArrowInvestigationEvents.onEntityLeave(arrow);
            arrow.discard();
        }

        if (!retainedAcrossForeignTick) {
            helper.fail("The Overworld scent tick removed the Nether-keyed state sharing a projectile id, or lost its own arrow");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void arrowImpactDispatchesInvestigationToPursuingMob(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 3);
        ServerLevel level = helper.getLevel();
        String dimension = level.dimension().location().toString();
        BlockPos shooterPos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer shooter = helper.makeMockServerPlayerInLevel();
        shooter.setPos(shooterPos.getX() + 0.5, shooterPos.getY(), shooterPos.getZ() + 0.5);
        Zombie pursuer = helper.spawn(EntityType.ZOMBIE, 4, 1, 1);
        pursuer.setNoAi(true);
        ((com.example.soundattract.mixin.MobAccessor) pursuer).getGoalSelector()
                .addGoal(1, new AttractionGoal(pursuer, 1.0));
        Arrow arrow = new Arrow(net.minecraft.world.entity.EntityType.ARROW, level);
        arrow.setOwner(shooter);
        arrow.setPos(shooterPos.getX() + 0.5, shooterPos.getY() + 1.0, shooterPos.getZ() + 0.5);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(Vec3.ZERO);
        boolean dispatched = false;
        boolean playerOwnershipPreserved = false;
        boolean cooldownSetAndNotExtended = false;
        int previousCooldown = SoundAttractConfig.COMMON.arrowInvestigationCooldownTicks.get();
        try {
            SoundAttractConfig.COMMON.arrowInvestigationCooldownTicks.set(40);
            ArrowInvestigationEvents.clearInvestigationCooldownForTesting(pursuer.getUUID());
            if (!level.addFreshEntity(arrow)) {
                helper.fail("Could not add the watched arrow to the test level");
                return;
            }
            BlockPos impactPos = pursuer.blockPosition();
            net.minecraft.world.phys.BlockHitResult hit = net.minecraft.world.phys.BlockHitResult.miss(
                    Vec3.atCenterOf(impactPos), net.minecraft.core.Direction.UP, impactPos);
            long expectedCooldownEnd = level.getGameTime() + 40;
            ArrowInvestigationEvents.onProjectileImpactForTesting(arrow, hit, level);
            boolean firstImpactSetCooldown = java.util.Objects.equals(expectedCooldownEnd,
                    ArrowInvestigationEvents.getInvestigationCooldownEndForTesting(pursuer.getUUID()));
            ArrowInvestigationEvents.onProjectileImpactForTesting(arrow, hit, level);
            cooldownSetAndNotExtended = firstImpactSetCooldown && java.util.Objects.equals(expectedCooldownEnd,
                    ArrowInvestigationEvents.getInvestigationCooldownEndForTesting(pursuer.getUUID()));
            ArrowInvestigationEvents.ArrowState state = ArrowInvestigationEvents.getStateForTesting(level, arrow.getId());
            List<SoundTracker.SoundRecord> investigations = state == null || state.lastInvestigationPos == null
                    ? List.of()
                    : SoundTracker.getNearbySounds(level, state.lastInvestigationPos, 2.0)
                    .stream().filter(record -> record instanceof SoundTracker.VirtualSoundRecord virtual
                            && "arrow_investigation".equals(virtual.animationClass)
                            && state.lastInvestigationPos.equals(record.pos))
                    .toList();
            dispatched = investigations.size() == 1 && investigations.get(0).isPlayerProduced();
            playerOwnershipPreserved = investigations.size() == 1
                    && investigations.get(0) instanceof SoundTracker.VirtualSoundRecord virtual
                    && shooter.getUUID().equals(virtual.sourcePlayer);
            investigations.forEach(record -> SoundTracker.removeSoundAt(record.pos, dimension));
        } finally {
            SoundAttractConfig.COMMON.arrowInvestigationCooldownTicks.set(previousCooldown);
            ArrowInvestigationEvents.clearInvestigationCooldownForTesting(pursuer.getUUID());
            ArrowInvestigationEvents.onEntityLeave(arrow);
            arrow.discard();
            level.getServer().getPlayerList().remove(shooter);
        }
        if (!dispatched || !playerOwnershipPreserved || !cooldownSetAndNotExtended) {
            helper.fail("Tagged projectile impact did not dispatch once and retain its per-mob cooldown: dispatched="
                    + dispatched + ", playerOwnership=" + playerOwnershipPreserved
                    + ", cooldownSetAndNotExtended=" + cooldownSetAndNotExtended);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
    public void arrowImpactCooldownExpiresAtExactConfiguredBoundary(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 3);
        ServerLevel level = helper.getLevel();
        String dimension = level.dimension().location().toString();
        ServerPlayer shooter = helper.makeMockServerPlayerInLevel();
        BlockPos shooterPos = helper.absolutePos(new BlockPos(1, 1, 1));
        shooter.setPos(shooterPos.getX() + 0.5, shooterPos.getY(), shooterPos.getZ() + 0.5);
        Zombie pursuer = helper.spawn(EntityType.ZOMBIE, 4, 1, 1);
        pursuer.setNoAi(true);
        ((com.example.soundattract.mixin.MobAccessor) pursuer).getGoalSelector()
                .addGoal(1, new AttractionGoal(pursuer, 1.0));
        Arrow arrow = new Arrow(net.minecraft.world.entity.EntityType.ARROW, level);
        arrow.setOwner(shooter);
        arrow.setPos(shooterPos.getX() + 0.5, shooterPos.getY() + 1.0, shooterPos.getZ() + 0.5);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(Vec3.ZERO);
        int previousCooldown = SoundAttractConfig.COMMON.arrowInvestigationCooldownTicks.get();
        try {
            SoundAttractConfig.COMMON.arrowInvestigationCooldownTicks.set(40);
            ArrowInvestigationEvents.clearInvestigationCooldownForTesting(pursuer.getUUID());
            if (!level.addFreshEntity(arrow)) {
                helper.fail("Could not add the tagged arrow for cooldown-boundary testing");
                return;
            }
            BlockPos impactPos = pursuer.blockPosition();
            var hit = net.minecraft.world.phys.BlockHitResult.miss(
                    Vec3.atCenterOf(impactPos), net.minecraft.core.Direction.UP, impactPos);
            ArrowInvestigationEvents.onProjectileImpactForTesting(arrow, hit, level);
            ArrowInvestigationEvents.ArrowState state = ArrowInvestigationEvents.getStateForTesting(level, arrow.getId());
            if (state == null || state.lastInvestigationPos == null) {
                helper.fail("Initial impact did not establish a cooldown-boundary fixture");
                return;
            }
            BlockPos initialRecordPos = state.lastInvestigationPos;
            long expectedDeadline = level.getGameTime() + 40;
            boolean initialDispatch = java.util.Objects.equals(expectedDeadline,
                    ArrowInvestigationEvents.getInvestigationCooldownEndForTesting(pursuer.getUUID()));
            SoundTracker.removeSoundAt(initialRecordPos, dimension);
            if (!initialDispatch) {
                helper.fail("Initial impact did not set its cooldown deadline to now + 40");
                return;
            }
            helper.runAfterDelay(40, () -> {
                long boundaryTick = level.getGameTime();
                ArrowInvestigationEvents.onProjectileImpactForTesting(arrow, hit, level);
                ArrowInvestigationEvents.ArrowState boundaryState = ArrowInvestigationEvents.getStateForTesting(level, arrow.getId());
                Long boundaryDeadline = ArrowInvestigationEvents.getInvestigationCooldownEndForTesting(pursuer.getUUID());
                boolean dispatchedAtBoundary = java.util.Objects.equals(boundaryTick + 40, boundaryDeadline);
                if (boundaryState != null && boundaryState.lastInvestigationPos != null) {
                    SoundTracker.removeSoundAt(boundaryState.lastInvestigationPos, dimension);
                }
                ArrowInvestigationEvents.onEntityLeave(arrow);
                arrow.discard();
                level.getServer().getPlayerList().remove(shooter);
                SoundAttractConfig.COMMON.arrowInvestigationCooldownTicks.set(previousCooldown);
                ArrowInvestigationEvents.clearInvestigationCooldownForTesting(pursuer.getUUID());
                if (!dispatchedAtBoundary) {
                    helper.fail("Arrow impact cooldown did not expire inclusively at its configured boundary: tick="
                            + boundaryTick + ", observedDeadline=" + boundaryDeadline);
                    return;
                }
                helper.succeed();
            });
        } catch (RuntimeException | Error failure) {
            SoundAttractConfig.COMMON.arrowInvestigationCooldownTicks.set(previousCooldown);
            ArrowInvestigationEvents.clearInvestigationCooldownForTesting(pursuer.getUUID());
            ArrowInvestigationEvents.onEntityLeave(arrow);
            arrow.discard();
            level.getServer().getPlayerList().remove(shooter);
            throw failure;
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void movingArrowEmitsOwnedScentTrail(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 3);
        ServerLevel level = helper.getLevel();
        boolean previousEnabled = SoundAttractConfig.COMMON.enableArrowScentTrail.get();
        double previousInterval = SoundAttractConfig.COMMON.arrowScentEmissionIntervalBlocks.get();
        double previousStrength = SoundAttractConfig.COMMON.arrowScentStrength.get();
        Zombie shooter = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        ScentManager scents = ScentManager.getForLevel(level);
        Arrow arrow = null;
        boolean emitted = false;
        try {
            SoundAttractConfig.COMMON.enableArrowScentTrail.set(true);
            SoundAttractConfig.COMMON.arrowScentEmissionIntervalBlocks.set(0.5);
            SoundAttractConfig.COMMON.arrowScentStrength.set(1.25);

            BlockPos spawn = helper.absolutePos(new BlockPos(2, 2, 1));
            arrow = new Arrow(net.minecraft.world.entity.EntityType.ARROW, level);
            arrow.setOwner(shooter);
            arrow.setPos(spawn.getX() + 0.5, spawn.getY() + 0.5, spawn.getZ() + 0.5);
            if (!level.addFreshEntity(arrow)) {
                helper.fail("Could not add the owned arrow to the test level");
                return;
            }
            ArrowInvestigationEvents.ArrowState state = ArrowInvestigationEvents.getStateForTesting(level, arrow.getId());
            if (state == null || !shooter.getUUID().equals(state.shooterUuid)) {
                helper.fail("Projectile load callback did not track its shooter identity");
                return;
            }

            arrow.setPos(spawn.getX() + 2.0, spawn.getY() + 0.5, spawn.getZ() + 0.5);
            com.example.soundattract.event.ArrowScentEvents.onLevelTick(level);
            emitted = scents.getNodesInArea(new ChunkPos(spawn)).stream()
                    .anyMatch(node -> shooter.getUUID().equals(node.getOwnerUUID())
                            && node.getSourceType() == ScentSourceType.MOB_PROJECTILE_PATH
                            && Math.abs(node.getStrength() - 1.25f) < 0.0001f);
        } finally {
            SoundAttractConfig.COMMON.enableArrowScentTrail.set(previousEnabled);
            SoundAttractConfig.COMMON.arrowScentEmissionIntervalBlocks.set(previousInterval);
            SoundAttractConfig.COMMON.arrowScentStrength.set(previousStrength);
            if (arrow != null) {
                ArrowInvestigationEvents.onEntityLeave(arrow);
                arrow.discard();
            }
            scents.removeOwnerNodes(shooter.getUUID());
        }

        if (!emitted) {
            helper.fail("Moving owned arrow did not emit a persistent owner-attributed arrow-path scent node");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void playerOwnedArrowEmitsArrowPathScent(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 6, 3);
        ServerLevel level = helper.getLevel();
        boolean previousEnabled = SoundAttractConfig.COMMON.enableArrowScentTrail.get();
        double previousStrength = SoundAttractConfig.COMMON.arrowScentStrength.get();
        net.minecraft.server.level.ServerPlayer shooter = new net.minecraft.server.level.ServerPlayer(
                level.getServer(), level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "arrow-scent-owner"), net.minecraft.server.level.ClientInformation.createDefault());
        UUID owner = shooter.getUUID();
        ScentManager scents = ScentManager.getForLevel(level);
        Arrow arrow = null;
        boolean emitted = false;
        try {
            SoundAttractConfig.COMMON.enableArrowScentTrail.set(true);
            SoundAttractConfig.COMMON.arrowScentStrength.set(0.85);
            BlockPos spawn = helper.absolutePos(new BlockPos(2, 2, 1));
            shooter.setPos(spawn.getX(), spawn.getY(), spawn.getZ());
            arrow = new Arrow(net.minecraft.world.entity.EntityType.ARROW, level);
            arrow.setOwner(shooter);
            arrow.setPos(spawn.getX() + 0.5, spawn.getY() + 0.5, spawn.getZ() + 0.5);
            if (!level.addFreshEntity(arrow)) {
                helper.fail("Could not add the player-owned arrow to the test level");
                return;
            }
            ArrowInvestigationEvents.ArrowState state = ArrowInvestigationEvents.getStateForTesting(level, arrow.getId());
            if (state == null || !owner.equals(state.shooterUuid) || state.isMobShooter) {
                helper.fail("Arrow entity-load tracking did not preserve player ownership classification");
                return;
            }

            com.example.soundattract.event.ArrowScentEvents.onLevelTick(level);
            emitted = scents.getNodesInArea(new ChunkPos(spawn)).stream()
                    .anyMatch(node -> owner.equals(node.getOwnerUUID())
                            && node.getSourceType() == ScentSourceType.ARROW_PATH
                            && Math.abs(node.getStrength() - 0.85f) < 0.0001f);
        } finally {
            SoundAttractConfig.COMMON.enableArrowScentTrail.set(previousEnabled);
            SoundAttractConfig.COMMON.arrowScentStrength.set(previousStrength);
            if (arrow != null) {
                ArrowInvestigationEvents.onEntityLeave(arrow);
                arrow.discard();
            }
            scents.removeOwnerNodes(owner);
        }
        if (!emitted) {
            helper.fail("Player-owned arrow did not emit a persistent owner-attributed ARROW_PATH scent node");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void walkingPlayerEmitsRateLimitedOwnedScent(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 6, 3);
        ServerLevel level = helper.getLevel();
        net.minecraft.server.level.ServerPlayer player = new net.minecraft.server.level.ServerPlayer(
                level.getServer(), level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "walking-scent-gametest"), net.minecraft.server.level.ClientInformation.createDefault()) {
            @Override
            public boolean isCreative() {
                return false;
            }
        };
        ScentManager scents = ScentManager.getForLevel(level);
        boolean emittedOnceAfterMoving = false;
        boolean emittedAtRest = false;
        long afterMoveCount = -1L;
        long afterStationaryCount = -1L;
        BlockPos start = helper.absolutePos(new BlockPos(1, 1, 1));
        try {
            player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.getAbilities().instabuild = false;
            com.example.soundattract.camo.CamouflageCapability.getCapability(player)
                    .ifPresent(capability -> capability.setLayers(List.of()));
            com.example.soundattract.integration.toughasnails.ToughAsNailsStateCache.remove(player.getUUID());
            ScentEvents.onPlayerLoggedOut(player);
            player.setPos(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
            // GameTests share a server and config file. Use a comfortably large
            // movement delta so this producer assertion is independent of any
            // transient interval value observed during concurrent fixtures.

            // The first sample establishes a baseline and must not emit at rest.
            ScentEvents.onPlayerTickForTesting(player, 1.0);
            emittedAtRest = scents.getNodesInArea(new ChunkPos(start)).stream()
                    .anyMatch(node -> player.getUUID().equals(node.getOwnerUUID())
                            && node.getSourceType() == ScentSourceType.PLAYER_WALK);

            player.setPos(start.getX() + 5.5, start.getY(), start.getZ() + 0.5);
            ScentEvents.onPlayerTickForTesting(player, 1.0);
            afterMoveCount = scents.getNodesInArea(new ChunkPos(start)).stream()
                    .filter(node -> player.getUUID().equals(node.getOwnerUUID())
                            && node.getSourceType() == ScentSourceType.PLAYER_WALK)
                    .count();
            // A second stationary sample should be rate-limited by distance.
            ScentEvents.onPlayerTickForTesting(player, 1.0);
            afterStationaryCount = scents.getNodesInArea(new ChunkPos(start)).stream()
                            .filter(node -> player.getUUID().equals(node.getOwnerUUID())
                                    && node.getSourceType() == ScentSourceType.PLAYER_WALK)
                            .count();
            emittedOnceAfterMoving = !emittedAtRest && afterMoveCount == 1 && afterStationaryCount == 1;
        } finally {
            ScentEvents.onPlayerLoggedOut(player);
            scents.removeOwnerNodes(player.getUUID());
        }
        if (!emittedOnceAfterMoving) {
            helper.fail("Walking player did not produce one owner-attributed PLAYER_WALK node after crossing the configured distance interval: pos="
                    + player.position() + ", gameMode=" + player.gameMode.getGameModeForPlayer()
                    + ", spectator=" + player.isSpectator() + ", creative=" + player.isCreative()
                    + ", inWater=" + player.isInWater() + ", manager=" + (scents != null)
                    + ", emittedAtRest=" + emittedAtRest + ", afterMove=" + afterMoveCount
                    + ", afterStationary=" + afterStationaryCount + ", start=" + start
                    + ", camoFactor=" + com.example.soundattract.camo.CamouflageCapability.getCapability(player)
                            .map(com.example.soundattract.camo.CamouflageCapability::getScentBlockFactor).orElse(-1.0)
                    + ", tnaMultiplier=" + com.example.soundattract.integration.toughasnails.ToughAsNailsStateCache
                            .scentMultiplier(player, level.getGameTime())
                    + ", biomeMultiplier=" + com.example.soundattract.scents.ScentBiomeModifier
                            .getDurationModifier(level, player.blockPosition())
                    + ", nearbyNodes=" + (scents == null ? -1 : scents.getNodesInArea(new ChunkPos(player.blockPosition())).size()));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void playerInventoryFoodRaisesEffectiveScent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.server.level.ServerPlayer baselinePlayer = helper.makeMockServerPlayerInLevel();
        net.minecraft.server.level.ServerPlayer foodPlayer = helper.makeMockServerPlayerInLevel();
        BlockPos basePosition = helper.absolutePos(new BlockPos(1, 1, 1));
        double baseline;
        double withFood;
        try {
            baselinePlayer.setPos(basePosition.getX() + 0.5, basePosition.getY(), basePosition.getZ() + 0.5);
            foodPlayer.setPos(basePosition.getX() + 2.5, basePosition.getY(), basePosition.getZ() + 0.5);
            baseline = com.example.soundattract.event.AwarenessServerEvents
                    .getEffectivePlayerScent(level, baselinePlayer);
            foodPlayer.getInventory().setItem(0,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH, 8));
            withFood = com.example.soundattract.event.AwarenessServerEvents
                    .getEffectivePlayerScent(level, foodPlayer);
        } finally {
            level.getServer().getPlayerList().remove(baselinePlayer);
            level.getServer().getPlayerList().remove(foodPlayer);
        }
        if (!Double.isFinite(baseline) || !Double.isFinite(withFood) || withFood <= baseline + 0.01) {
            helper.fail("Configured inventory odor did not raise effective player scent: baseline=" + baseline
                    + ", withRottenFlesh=" + withFood);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:tough_as_nails_profile", timeoutTicks = 120)
    public void toughAsNailsPresentAndAbsentProfilesBehaveCorrectly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        com.example.soundattract.integration.toughasnails.ToughAsNailsStateCache.remove(player.getUUID());
        if (!FabricLoader.getInstance().isModLoaded("toughasnails")) {
            double scentMultiplier = com.example.soundattract.integration.toughasnails.ToughAsNailsStateCache
                    .scentMultiplier(player, level.getGameTime());
            double soundMultiplier = com.example.soundattract.integration.toughasnails.ToughAsNailsStateCache
                    .soundMultiplier(player, level.getGameTime());
            level.getServer().getPlayerList().remove(player);
            if (scentMultiplier != 1.0 || soundMultiplier != 1.0) {
                helper.fail("Absent Tough As Nails profile did not preserve neutral scent/sound multipliers: scent="
                        + scentMultiplier + ", sound=" + soundMultiplier);
                return;
            }
            helper.succeed();
            return;
        }

        net.minecraft.world.item.Item leafHelmet = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(
                ResourceLocation.fromNamespaceAndPath("toughasnails", "leaf_helmet")).orElse(null);
        net.minecraft.world.item.Item woolChestplate = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(
                ResourceLocation.fromNamespaceAndPath("toughasnails", "wool_chestplate")).orElse(null);
        if (leafHelmet == null || woolChestplate == null
                || !SoundAttractConfig.COMMON.enableToughAsNailsIntegration.get()) {
            level.getServer().getPlayerList().remove(player);
            helper.fail("Tough As Nails profile did not register its expected items or integration is disabled: leaf="
                    + leafHelmet + ", wool=" + woolChestplate + ", enabled="
                    + SoundAttractConfig.COMMON.enableToughAsNailsIntegration.get());
            return;
        }

        int previousPollInterval = SoundAttractConfig.COMMON.toughAsNailsPollIntervalTicks.get();
        SoundAttractConfig.COMMON.toughAsNailsPollIntervalTicks.set(1);
        helper.runAfterDelay(3, () -> beginToughAsNailsPresentProfileTest(
                helper, player, leafHelmet, woolChestplate, previousPollInterval));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void windScentAdvectsAndClearsWhenPlayerLeaves(GameTestHelper helper) {
        prepareRaisedFloor(helper, 0, 0, 8, 4, 149);
        ServerLevel level = helper.getLevel();
        boolean previousEnabled = SoundAttractConfig.COMMON.enableWindScent.get();
        double previousActivation = SoundAttractConfig.COMMON.windScentActivationThreshold.get();
        int previousUpdateInterval = SoundAttractConfig.COMMON.windScentUpdateIntervalTicks.get();
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ScentManager scents = ScentManager.getForLevel(level);
        UUID owner = player.getUUID();
        BlockPos floorPos = helper.absolutePos(new BlockPos(5, 149, 2));
        BlockPos start = new BlockPos(floorPos.getX(),
                level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        floorPos.getX(), floorPos.getZ()), floorPos.getZ());
        boolean emitted;
        boolean advectedDownwind;
        boolean clearedAfterLogout;
        double effectiveScent = Double.NaN;
        boolean skyVisible = false;
        boolean playerRemoved = false;
        try {
            SoundAttractConfig.COMMON.enableWindScent.set(true);
            SoundAttractConfig.COMMON.windScentActivationThreshold.set(0.8);
            SoundAttractConfig.COMMON.windScentUpdateIntervalTicks.set(1);
            player.setPos(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
            player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(
                    net.minecraft.world.item.Items.ROTTEN_FLESH, 64));
            effectiveScent = AwarenessServerEvents.getEffectivePlayerScent(level, player);
            skyVisible = level.canSeeSky(player.blockPosition());

            // Invoke the exact level-tick production routine synchronously so
            // the mock player cannot alter unrelated GameTest scenarios.
            tickWindScentLevel(level);
            tickWindScentLevel(level);
            List<ScentNode> windNodes = scents.getNodesInArea(new ChunkPos(start)).stream()
                    .filter(node -> owner.equals(node.getOwnerUUID())
                            && node.getSourceType() == ScentSourceType.PLAYER_WIND)
                    .toList();
            emitted = !windNodes.isEmpty();
            advectedDownwind = windNodes.stream()
                    .anyMatch(node -> node.getPosition().x < player.position().x - 0.5);
            level.getServer().getPlayerList().remove(player);
            playerRemoved = true;
            tickWindScentLevel(level);
            clearedAfterLogout = scents.getNodesInArea(new ChunkPos(start)).stream()
                    .noneMatch(node -> owner.equals(node.getOwnerUUID())
                            && node.getSourceType() == ScentSourceType.PLAYER_WIND);
        } finally {
            if (!playerRemoved) {
                level.getServer().getPlayerList().remove(player);
                tickWindScentLevel(level);
            }
            SoundAttractConfig.COMMON.enableWindScent.set(previousEnabled);
            SoundAttractConfig.COMMON.windScentActivationThreshold.set(previousActivation);
            SoundAttractConfig.COMMON.windScentUpdateIntervalTicks.set(previousUpdateInterval);
            scents.clearWindNodes(owner);
            scents.removeOwnerNodes(owner);
        }
        if (!emitted || !advectedDownwind || !clearedAfterLogout) {
            helper.fail("Wind scent lifecycle mismatch: emitted=" + emitted
                    + ", advectedDownwind=" + advectedDownwind
                    + ", clearedAfterLogout=" + clearedAfterLogout
                    + ", effectiveScent=" + effectiveScent + ", skyVisible=" + skyVisible
                    + ", enabled=" + SoundAttractConfig.COMMON.enableWindScent.get()
                    + ", threshold=" + SoundAttractConfig.COMMON.windScentActivationThreshold.get()
                    + ", playerPos=" + player.position() + ", start=" + start);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:wind_barrier", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void windScentLeaksAroundSolidBarrierWithoutCrossingIt(GameTestHelper helper) {
        prepareRaisedFloor(helper, 0, 0, 12, 12, 149);
        ServerLevel level = helper.getLevel();
        boolean previousEnabled = SoundAttractConfig.COMMON.enableWindScent.get();
        double previousActivation = SoundAttractConfig.COMMON.windScentActivationThreshold.get();
        int previousUpdateInterval = SoundAttractConfig.COMMON.windScentUpdateIntervalTicks.get();
        double previousSpread = SoundAttractConfig.COMMON.windScentSpreadFactor.get();
        double previousLeakage = SoundAttractConfig.COMMON.windScentBarrierLeakage.get();
        int previousMaximumDistance = SoundAttractConfig.COMMON.windScentMaximumDistance.get();
        List<? extends String> previousOdorValues = SoundAttractConfig.COMMON.playerOdorItemValues.get();
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ScentManager scents = ScentManager.getForLevel(level);
        UUID owner = player.getUUID();
        BlockPos source = helper.absolutePos(new BlockPos(6, 150, 6));
        BlockPos barrier = helper.absolutePos(new BlockPos(5, 150, 6));
        boolean leakedToLeewardSide;
        boolean didNotCrossBarrier;
        boolean retainedSourceParcel;
        List<String> skyColumnObstructions = new java.util.ArrayList<>();
        try {
            SoundAttractConfig.COMMON.enableWindScent.set(true);
            SoundAttractConfig.COMMON.windScentActivationThreshold.set(0.8);
            SoundAttractConfig.COMMON.windScentUpdateIntervalTicks.set(1);
            SoundAttractConfig.COMMON.windScentSpreadFactor.set(0.0);
            SoundAttractConfig.COMMON.windScentBarrierLeakage.set(0.4);
            SoundAttractConfig.COMMON.windScentMaximumDistance.set(16);
            SoundAttractConfig.COMMON.playerOdorItemValues.set(List.of("minecraft:rotten_flesh=1.0"));
            player.setPos(source.getX() + 0.5, source.getY(), source.getZ() + 0.5);
            player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(
                    net.minecraft.world.item.Items.ROTTEN_FLESH, 64));
            helper.setBlock(5, 150, 6, Blocks.STONE);
            // Clear the world-space column to the build limit: GameTest's
            // translated template origin may sit under existing terrain.
            for (int y = source.getY() + 1; y < level.getMaxBuildHeight(); y++) {
                BlockPos skyPos = new BlockPos(source.getX(), y, source.getZ());
                level.setBlockAndUpdate(skyPos, Blocks.AIR.defaultBlockState());
                if (!level.getBlockState(skyPos).isAir() && skyColumnObstructions.size() < 8) {
                    skyColumnObstructions.add(skyPos + "=" + level.getBlockState(skyPos));
                }
            }
            tickWindScentLevel(level);
            List<ScentNode> emittedNodes = scents.getNodesInArea(new ChunkPos(source)).stream()
                    .filter(node -> owner.equals(node.getOwnerUUID())
                            && node.getSourceType() == ScentSourceType.PLAYER_WIND)
                    .toList();
            retainedSourceParcel = emittedNodes.stream().anyMatch(node -> node.getBlockPos().equals(source));
            // Advance the source parcel into the barrier on the next production
            // tick, then inspect the leeward puffs emitted by collision handling.
            tickWindScentLevel(level);
            List<ScentNode> ownerNodes = scents.getNodesInArea(new ChunkPos(source)).stream()
                    .filter(node -> owner.equals(node.getOwnerUUID())
                            && node.getSourceType() == ScentSourceType.PLAYER_WIND)
                    .toList();
            leakedToLeewardSide = ownerNodes.stream().anyMatch(node ->
                    Math.abs(node.getBlockPos().getZ() - barrier.getZ()) == 1
                            && Math.abs(node.getBlockPos().getX() - barrier.getX()) <= 1);
            didNotCrossBarrier = ownerNodes.stream().noneMatch(node -> node.getBlockPos().getX() < barrier.getX());
        } finally {
            level.getServer().getPlayerList().remove(player);
            tickWindScentLevel(level);
            SoundAttractConfig.COMMON.enableWindScent.set(previousEnabled);
            SoundAttractConfig.COMMON.windScentActivationThreshold.set(previousActivation);
            SoundAttractConfig.COMMON.windScentUpdateIntervalTicks.set(previousUpdateInterval);
            SoundAttractConfig.COMMON.windScentSpreadFactor.set(previousSpread);
            SoundAttractConfig.COMMON.windScentBarrierLeakage.set(previousLeakage);
            SoundAttractConfig.COMMON.windScentMaximumDistance.set(previousMaximumDistance);
            SoundAttractConfig.COMMON.playerOdorItemValues.set(previousOdorValues);
            scents.clearWindNodes(owner);
            scents.removeOwnerNodes(owner);
        }
        if (!retainedSourceParcel || !leakedToLeewardSide || !didNotCrossBarrier) {
            helper.fail("Wind barrier flow mismatch: source=" + retainedSourceParcel
                    + ", leewardLeak=" + leakedToLeewardSide + ", crossedWall=" + !didNotCrossBarrier
                    + ", effectiveScent=" + AwarenessServerEvents.getEffectivePlayerScent(level, player)
                    + ", skyVisible=" + level.canSeeSky(player.blockPosition())
                    + ", source=" + source + ", playerPos=" + player.blockPosition()
                    + ", skyObstructions=" + skyColumnObstructions
                    + ", windNodes=" + scents.getNodesInArea(new ChunkPos(source)).stream()
                            .filter(node -> owner.equals(node.getOwnerUUID())
                                    && node.getSourceType() == ScentSourceType.PLAYER_WIND)
                            .map(node -> node.getPosition().toString()).toList());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void arrowNearMissDispatchesOnceToPursuer(GameTestHelper helper) {
        prepareRaisedFloor(helper, 0, 0, 8, 8, 149);
        ServerLevel level = helper.getLevel();
        // Keep this scan well clear of neighboring GameTest structures: the production event
        // intentionally queries every nearby pursuit mob in the level.
        Zombie shooter = helper.spawn(EntityType.ZOMBIE, 1, 150, 1);
        Zombie pursuer = helper.spawn(EntityType.ZOMBIE, 4, 150, 1);
        net.minecraft.world.entity.Mob mobWithoutPursuitGoal = helper.spawn(EntityType.COW, 4, 150, 2);
        Zombie pursuerOutsideRadius = helper.spawn(EntityType.ZOMBIE, 4, 150, 4);
        pursuer.setNoAi(true);
        mobWithoutPursuitGoal.setNoAi(true);
        pursuerOutsideRadius.setNoAi(true);
        ((com.example.soundattract.mixin.MobAccessor) pursuer).getGoalSelector()
                .addGoal(1, new AttractionGoal(pursuer, 1.0));
        ((com.example.soundattract.mixin.MobAccessor) mobWithoutPursuitGoal)
                .getGoalSelector().removeAllGoals(goal -> true);
        ((com.example.soundattract.mixin.MobAccessor) pursuerOutsideRadius).getGoalSelector()
                .addGoal(1, new AttractionGoal(pursuerOutsideRadius, 1.0));
        net.minecraft.server.level.ServerPlayer nearbyPlayer = helper.makeMockServerPlayerInLevel();
        nearbyPlayer.setPos(helper.absolutePos(new BlockPos(4, 150, 1)).getX() + 0.5,
                helper.absolutePos(new BlockPos(4, 150, 1)).getY(),
                helper.absolutePos(new BlockPos(4, 150, 1)).getZ() + 0.5);
        BlockPos initial = helper.absolutePos(new BlockPos(0, 152, 2));
        Arrow arrow = new Arrow(net.minecraft.world.entity.EntityType.ARROW, level);
        arrow.setOwner(shooter);
        arrow.setPos(initial.getX() + 0.5, initial.getY() + 0.5, initial.getZ() + 0.5);
        arrow.setDeltaMovement(new Vec3(0.2, 0.0, 0.0));
        if (!level.addFreshEntity(arrow)) {
            level.getServer().getPlayerList().remove(nearbyPlayer);
            helper.fail("Could not add the watched arrow to the near-miss fixture");
            return;
        }
        // The test seam disables current-target suppression and uses a zero
        // random offset without mutating process-wide config during the suite.
        helper.runAfterDelay(1, () -> attemptArrowNearMiss(helper, level, arrow, shooter, pursuer,
                mobWithoutPursuitGoal, pursuerOutsideRadius, nearbyPlayer, 8));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void arrowScentRateLimitIsPerShooter(GameTestHelper helper) {
        int previousLimit = SoundAttractConfig.COMMON.arrowScentRateLimitPerShooterPerMinute.get();
        UUID shooter = UUID.randomUUID();
        boolean first;
        boolean second;
        try {
            GlobalScentRateLimiter.clear();
            SoundAttractConfig.COMMON.arrowScentRateLimitPerShooterPerMinute.set(1);
            first = GlobalScentRateLimiter.tryConsume(shooter);
            second = GlobalScentRateLimiter.tryConsume(shooter);
        } finally {
            SoundAttractConfig.COMMON.arrowScentRateLimitPerShooterPerMinute.set(previousLimit);
            GlobalScentRateLimiter.clear();
        }
        if (!first || second) {
            helper.fail("Arrow scent rate limiter did not allow the first emission and reject the next for one shooter");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void separateCommonServerAndClientConfigsAreCreatedWithExpectedScopes(GameTestHelper helper) {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("soundattract");
        List<String> expectedFiles = List.of(
                "general.toml", "stealth.toml", "guns.toml", "voice.toml", "integration.toml",
                "scent.toml", "performance.toml", "raid.toml", "pathfinding.toml", "living_wall.toml",
                "debug_logging.toml", "server-rules.toml", "awareness.toml");
        List<String> missing = expectedFiles.stream()
                .filter(name -> !Files.isRegularFile(configDir.resolve(name)))
                .toList();
        if (!missing.isEmpty()) {
            helper.fail("Config registration failed to produce separate scope files: " + missing);
            return;
        }
        try {
            String serverRules = Files.readString(configDir.resolve("server-rules.toml"));
            String clientAwareness = Files.readString(configDir.resolve("awareness.toml"));
            boolean serverScope = SoundAttractConfig.SERVER_SPEC.isLoaded()
                    && serverRules.contains("[player_action_sounds]")
                    && serverRules.contains("enablePlayerActionSounds = true")
                    && serverRules.contains("[integrations]");
            boolean clientScope = clientAwareness.contains("[awareness_hud]")
                    && clientAwareness.contains("[mob_alerts]")
                    && clientAwareness.contains("enabled = true");
            if (!serverScope || !clientScope) {
                helper.fail("Dedicated config scopes were not populated correctly: server=" + serverScope
                        + ", client=" + clientScope);
                return;
            }
        } catch (java.io.IOException e) {
            helper.fail("Could not read generated server/client config files: " + e.getMessage());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void eligibleZombieReceivesProductionAttractionGoal(GameTestHelper helper) {
        boolean previousFleeEnabled = SoundAttractConfig.COMMON.enableFleeFromUnseenAttackerGoal.get();
        Zombie zombie;
        try {
            SoundAttractConfig.COMMON.enableFleeFromUnseenAttackerGoal.set(true);
            zombie = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        } finally {
            SoundAttractConfig.COMMON.enableFleeFromUnseenAttackerGoal.set(previousFleeEnabled);
        }
        helper.runAfterDelay(60, () -> {
            var goals = ((com.example.soundattract.mixin.MobAccessor) zombie)
                    .getGoalSelector().getAvailableGoals();
            boolean hasAttractionGoal = goals.stream().anyMatch(wrapped -> wrapped.getGoal() instanceof AttractionGoal
                    || wrapped.getGoal() instanceof com.example.soundattract.ai.LeaderAttractionGoal);
            boolean hasFleeGoal = goals.stream().anyMatch(wrapped ->
                    wrapped.getGoal() instanceof com.example.soundattract.ai.FleeFromUnseenAttackerGoal);
            if (!hasAttractionGoal || !hasFleeGoal) {
                helper.fail("Production entity-load integration did not attach attraction and enabled flee goals: attraction="
                        + hasAttractionGoal + ", flee=" + hasFleeGoal);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:tracker_native_selector_expiry", timeoutTicks = 120)
    public void nativeGoalSelectorStartsAndStopsExpiredSoundPursuit(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 3);
        int previousSoundCap = reserveSoundCapacityForGameTest(1);
        boolean previousScentOverride = SoundAttractConfig.COMMON.scentOverrideSoundPriority.get();
        SoundAttractConfig.COMMON.scentOverrideSoundPriority.set(false);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        helper.runAfterDelay(60, () -> {
            var selector = ((com.example.soundattract.mixin.MobAccessor) zombie).getGoalSelector();
            var attraction = selector.getAvailableGoals().stream()
                    .filter(wrapped -> wrapped.getGoal() instanceof AttractionGoal)
                    .findFirst().orElse(null);
            if (attraction == null) {
                SoundAttractConfig.COMMON.maxSoundsTracked.set(previousSoundCap);
                SoundAttractConfig.COMMON.scentOverrideSoundPriority.set(previousScentOverride);
                helper.fail("Spawned zombie did not receive the production AttractionGoal before selector lifecycle check");
                return;
            }

            removeAllGoalsExcept(zombie, attraction.getGoal());
            BlockPos mobStart = helper.absolutePos(new BlockPos(1, 1, 1));
            zombie.getNavigation().stop();
            zombie.setPos(mobStart.getX() + 0.5, mobStart.getY(), mobStart.getZ() + 0.5);
            zombie.setTarget(null);
            zombie.setDeltaMovement(Vec3.ZERO);
            SoundAttractConfig.COMMON.scentOverrideSoundPriority.set(false);

            ServerLevel level = helper.getLevel();
            BlockPos soundPos = helper.absolutePos(new BlockPos(5, 1, 1));
            String dimension = level.dimension().location().toString();
            // The overhead integration tests publish real projectile records
            // into the same per-world tracker. Remove only their test-produced
            // records so they cannot become this fixture's winning candidate.
            SoundTracker.getTrackedSoundsSnapshot().stream()
                    .filter(record -> dimension.equals(record.dimensionKey)
                            && record.soundId != null && record.soundId.contains("arrow_investigation"))
                    .forEach(record -> SoundTracker.removeSoundAt(record.pos, dimension));
            UUID source = UUID.randomUUID();
            String soundId = "soundattract:virtual#" + source + "/native_selector_expiry/" + soundPos.asLong();
            // Keep adjacent concurrent GameTest structures outside this fixture's listening range.
            SoundTracker.addVirtualSound(soundPos, dimension, 4.0, 100.0, 200, source, "native_selector_expiry");
            for (int attempt = 0; attempt < 100 && !attraction.isRunning(); attempt++) {
                selector.tick();
            }
            boolean started = attraction.isRunning()
                    && soundPos.equals(((AttractionGoal) attraction.getGoal()).getTargetSoundPos());
            net.minecraft.world.level.pathfinder.Path activePath = zombie.getNavigation().getPath();
            boolean routeStarted = started && activePath != null && !zombie.getNavigation().isDone();
            if (!routeStarted) {
                List<SoundTracker.SoundRecord> nearby = SoundTracker.getNearbySounds(level, dimension, soundPos, null);
                SoundTracker.SoundRecord directBest = SoundTracker.findNearestSound(
                        zombie, level, zombie.blockPosition(), zombie.getEyePosition());
                net.minecraft.world.level.pathfinder.Path diagnosticPath = zombie.getNavigation().createPath(soundPos, 0);
                SoundTracker.removeSoundAt(soundPos, dimension);
                SoundAttractConfig.COMMON.maxSoundsTracked.set(previousSoundCap);
                SoundAttractConfig.COMMON.scentOverrideSoundPriority.set(previousScentOverride);
                helper.fail("Minecraft GoalSelector did not start an active production attraction route: started="
                        + started + ", selected=" + ((AttractionGoal) attraction.getGoal()).getTargetSoundPos()
                        + ", path=" + (activePath == null ? "null" : activePath.getNodeCount())
                        + ", directPath=" + (diagnosticPath == null ? "null" : diagnosticPath.getNodeCount())
                        + ", mobPos=" + zombie.position() + ", onGround=" + zombie.onGround()
                        + ", support=" + level.getBlockState(zombie.blockPosition().below())
                        + ", destinationFeet=" + level.getBlockState(soundPos)
                        + ", navigationDone=" + zombie.getNavigation().isDone()
                        + ", eligibleType=" + com.example.soundattract.event.SoundAttractionEvents
                                .getCachedAttractedEntityTypes().contains(zombie.getType())
                        + ", nearby=" + describeSoundRecords(nearby) + ", directBest="
                        + (directBest == null ? "null" : describeSoundRecords(List.of(directBest))));
                return;
            }

            helper.runAfterDelay(7, () -> {
                // GameTest does not reliably dispatch the mod's world tick hook.
                for (int tick = 0; tick < 201; tick++) {
                    SoundTracker.tick();
                }
                boolean recordExpired = SoundTracker.getNearbySounds(level, dimension, soundPos, null).stream()
                        .noneMatch(record -> soundId.equals(record.soundId));
                // Pursuing goals cache sound lookups for six ticks. Advance beyond
                // that window so the goal observes that its record expired.
                helper.runAfterDelay(7, () -> {
                    try {
                    selector.tick();
                    boolean stopped = !attraction.isRunning();
                    boolean routeStopped = zombie.getNavigation().isDone();
                    AttractionGoal goal = (AttractionGoal) attraction.getGoal();
                    if (!recordExpired || !stopped || !routeStopped
                            || goal.getTargetSoundPos() != null || goal.isPursuingSound()) {
                        helper.fail("Native goal selector failed to stop and clear an expired sound pursuit: expired="
                                + recordExpired + ", running=" + attraction.isRunning() + ", routeStopped="
                                + routeStopped + ", target="
                                + goal.getTargetSoundPos() + ", pursuing=" + goal.isPursuingSound());
                        return;
                    }
                    helper.succeed();
                    } finally {
                        SoundTracker.removeSoundAt(soundPos, dimension);
                        SoundAttractConfig.COMMON.maxSoundsTracked.set(previousSoundCap);
                        SoundAttractConfig.COMMON.scentOverrideSoundPriority.set(previousScentOverride);
                    }
                });
            });
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void enhancedAiGoalsApplyActionsAndRestoreState(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 8, 4);
        ServerLevel level = helper.getLevel();
        boolean previousTeleportEnabled = SoundAttractConfig.COMMON.enableTeleportToSound.get();
        double previousTeleportChance = SoundAttractConfig.COMMON.teleportChance.get();
        String previousTeleportTag = SoundAttractConfig.COMMON.teleportCanTeleportTag.get();
        String previousTeleportTargetTag = SoundAttractConfig.COMMON.teleportCanBeTeleportedTag.get();
        boolean previousPickUpEnabled = SoundAttractConfig.COMMON.enablePickUpAndThrowToSound.get();
        double previousPickUpChance = SoundAttractConfig.COMMON.pickUpChance.get();
        int previousPickUpMinDistance = SoundAttractConfig.COMMON.pickUpMinDistanceToPickUp.get();
        int previousPickUpMaxThrow = SoundAttractConfig.COMMON.pickUpMaxDistanceToThrow.get();
        boolean teleportMovedTarget;
        boolean teleportLandingAvoidedSolidCollision;
        boolean teleportRestoredFlags;
        boolean foundTeleportTarget;
        Vec3 teleportTargetStart;
        Vec3 teleportTargetEnd;
        boolean pickupThrewTarget;
        boolean breakerCanUse;
        boolean breakerRemovedObstacle;
        boolean breakerDroppedLoot;
        String dimension = level.dimension().location().toString();
        BlockPos teleportSound = helper.absolutePos(new BlockPos(6, 1, 1));
        try {
            SoundAttractConfig.COMMON.enableTeleportToSound.set(true);
            SoundAttractConfig.COMMON.teleportChance.set(1.0);
            SoundAttractConfig.COMMON.teleportCanTeleportTag
                    .set("soundattract:mobs/teleport_to_target/can_teleport");
            SoundAttractConfig.COMMON.teleportCanBeTeleportedTag
                    .set("soundattract:mobs/teleport_to_target/can_be_teleported");
            EnderMan enderman = helper.spawn(EntityType.ENDERMAN, 1, 1, 1);
            Zombie teleportTarget = helper.spawn(EntityType.ZOMBIE, 2, 1, 1);
            teleportTargetStart = teleportTarget.position();
            helper.setBlock(6, 1, 1, Blocks.STONE);
            SoundTracker.addVirtualSound(teleportSound, dimension, 12.0, 5.0, 200, UUID.randomUUID(), "GameTestTeleport");
            TeleportToSoundGoal teleportGoal = new TeleportToSoundGoal(enderman);
            foundTeleportTarget = teleportGoal.canUse();
            if (foundTeleportTarget) {
                teleportGoal.start();
                for (int tick = 0; tick < 31; tick++) teleportGoal.tick();
            }
            teleportMovedTarget = foundTeleportTarget
                    && Math.abs(teleportTarget.getX() - (teleportSound.getX() + 0.5)) < 1.0;
            teleportLandingAvoidedSolidCollision = foundTeleportTarget
                    && teleportTarget.getY() > teleportSound.getY()
                    && level.noCollision(teleportTarget);
            teleportTargetEnd = teleportTarget.position();
            teleportRestoredFlags = !enderman.isInvisible() && !enderman.isNoGravity()
                    && !teleportTarget.isInvisible() && !teleportTarget.isNoGravity();
            teleportGoal.stop();
            SoundTracker.removeSoundAt(teleportSound, dimension);

            SoundAttractConfig.COMMON.enablePickUpAndThrowToSound.set(true);
            SoundAttractConfig.COMMON.pickUpChance.set(1.0);
            SoundAttractConfig.COMMON.pickUpMinDistanceToPickUp.set(1);
            SoundAttractConfig.COMMON.pickUpMaxDistanceToThrow.set(Math.max(8, previousPickUpMaxThrow));
            Zombie picker = helper.spawn(EntityType.ZOMBIE, 1, 1, 3);
            Zombie picked = helper.spawn(EntityType.ZOMBIE, 2, 1, 3);
            BlockPos pickupSound = helper.absolutePos(new BlockPos(6, 1, 3));
            SoundTracker.addVirtualSound(pickupSound, dimension, 16.0, 5.0, 200, UUID.randomUUID(), "GameTestPickup");
            PickUpAndThrowToSoundGoal pickUpGoal = new PickUpAndThrowToSoundGoal(picker);
            boolean canPick = pickUpGoal.canUse();
            if (canPick) {
                pickUpGoal.start();
                for (int tick = 0; tick < 24 && pickUpGoal.canContinueToUse(); tick++) pickUpGoal.tick();
            }
            pickupThrewTarget = canPick && !picked.isPassenger() && picked.getDeltaMovement().lengthSqr() > 0.01;
            pickUpGoal.stop();
            SoundTracker.removeSoundAt(pickupSound, dimension);

            BlockPos obstacle = helper.absolutePos(new BlockPos(3, 2, 2));
            BlockPos destination = helper.absolutePos(new BlockPos(6, 1, 2));
            helper.setBlock(3, 2, 2, Blocks.DIRT);
            Zombie miner = helper.spawn(EntityType.ZOMBIE, 1, 1, 2);
            BlockBreakerPosGoal breaker = new BlockBreakerPosGoal(miner, destination, 0.1, false, false, false);
            breakerCanUse = breaker.canUse();
            if (breakerCanUse) {
                breaker.start();
                for (int tick = 0; tick < 20; tick++) breaker.tick();
            }
            breakerRemovedObstacle = breakerCanUse && level.getBlockState(obstacle).isAir();
            breakerDroppedLoot = breakerRemovedObstacle && !level.getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(obstacle).inflate(2.0),
                    item -> item.getItem().is(Blocks.DIRT.asItem())).isEmpty();
            breaker.stop();
        } finally {
            SoundAttractConfig.COMMON.enableTeleportToSound.set(previousTeleportEnabled);
            SoundAttractConfig.COMMON.teleportChance.set(previousTeleportChance);
            SoundAttractConfig.COMMON.teleportCanTeleportTag.set(previousTeleportTag);
            SoundAttractConfig.COMMON.teleportCanBeTeleportedTag.set(previousTeleportTargetTag);
            SoundAttractConfig.COMMON.enablePickUpAndThrowToSound.set(previousPickUpEnabled);
            SoundAttractConfig.COMMON.pickUpChance.set(previousPickUpChance);
            SoundAttractConfig.COMMON.pickUpMinDistanceToPickUp.set(previousPickUpMinDistance);
            SoundAttractConfig.COMMON.pickUpMaxDistanceToThrow.set(previousPickUpMaxThrow);
            SoundTracker.removeSoundAt(teleportSound, dimension);
        }

        if (!teleportMovedTarget || !teleportLandingAvoidedSolidCollision || !teleportRestoredFlags) {
            helper.fail("Teleport-to-sound did not move the tagged target to a collision-safe landing or restore temporary invisibility/gravity state: moved="
                    + teleportMovedTarget + ", collisionSafe=" + teleportLandingAvoidedSolidCollision
                    + ", flagsRestored=" + teleportRestoredFlags + ", acquired=" + foundTeleportTarget
                    + ", targetStart=" + teleportTargetStart + ", targetEnd=" + teleportTargetEnd);
            return;
        }
        if (!pickupThrewTarget) {
            helper.fail("Pick-up-and-throw goal did not pick up and launch a valid tagged mob toward the sound");
            return;
        }
        if (!breakerRemovedObstacle) {
            helper.fail("Block-breaker goal did not remove the reachable obstacle between mob and destination"
                    + ": canUse=" + breakerCanUse + ", block="
                    + helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(3, 2, 2)))
                    + ", miner=" + helper.absolutePos(new BlockPos(1, 1, 2)));
            return;
        }
        if (!breakerDroppedLoot) {
            helper.fail("Block-breaker removed its obstruction but failed to produce the block's expected dirt drop");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void blockBreakerCancellationRestoresMobState(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 4);
        ServerLevel level = helper.getLevel();
        BlockPos obstacle = helper.absolutePos(new BlockPos(3, 2, 2));
        helper.setBlock(3, 2, 2, Blocks.DIRT);
        Zombie miner = helper.spawn(EntityType.ZOMBIE, 1, 1, 2);
        BlockBreakerPosGoal breaker = new BlockBreakerPosGoal(
                miner, helper.absolutePos(new BlockPos(6, 1, 2)), 100.0, false, false, false);

        boolean acquired = breaker.canUse();
        boolean aggressiveDuringBreak = false;
        if (acquired) {
            breaker.start();
            aggressiveDuringBreak = miner.isAggressive();
            breaker.tick();
            breaker.stop();
        }

        if (!acquired || !aggressiveDuringBreak) {
            helper.fail("Block-breaker cancellation fixture did not enter its active breaking state");
            return;
        }
        if (!Blocks.DIRT.equals(level.getBlockState(obstacle).getBlock())) {
            helper.fail("Cancelling a long block break removed its obstruction");
            return;
        }
        boolean droppedDuringCancellation = !level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(obstacle).inflate(2.0),
                item -> item.getItem().is(Blocks.DIRT.asItem())).isEmpty();
        if (droppedDuringCancellation) {
            helper.fail("Cancelling an unfinished block break created loot before the block was broken");
            return;
        }
        if (miner.isAggressive() || breaker.canContinueToUse()) {
            helper.fail("Stopping the block-breaker left aggression or an active target behind");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:enhanced_ai_breaker", timeoutTicks = 120)
    public void blockBreakerSendsAndClearsProgressPackets(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 4);
        ServerLevel level = helper.getLevel();
        BlockPos obstacle = helper.absolutePos(new BlockPos(3, 2, 2));
        helper.setBlock(3, 2, 2, Blocks.DIRT);
        Zombie miner = helper.spawn(EntityType.ZOMBIE, 1, 1, 2);
        ServerPlayer observer = helper.makeMockServerPlayerInLevel();
        observer.setPos(miner.getX() + 1.0, miner.getY(), miner.getZ());
        BlockBreakerPosGoal breaker = new BlockBreakerPosGoal(
                miner, helper.absolutePos(new BlockPos(6, 1, 2)), 1.0, false, false, false);

        boolean acquired = breaker.canUse();
        List<BlockBreakProgressPacketProbe.Progress> packets;
        BlockBreakProgressPacketProbe.begin();
        try {
            if (acquired) {
                breaker.start();
                for (int tick = 0; tick < 8; tick++) breaker.tick();
                breaker.stop();
            }
        } finally {
            packets = BlockBreakProgressPacketProbe.finish();
            level.getServer().getPlayerList().remove(observer);
        }

        boolean progressObserved = packets.stream().anyMatch(packet -> packet.breakerId() == miner.getId()
                && packet.pos().equals(obstacle) && packet.stage() >= 0);
        boolean cleanupObserved = packets.stream().anyMatch(packet -> packet.breakerId() == miner.getId()
                && packet.pos().equals(obstacle) && packet.stage() == -1);
        if (!acquired || !progressObserved || !cleanupObserved) {
            helper.fail("Block-breaker did not send an observable crack-stage update and cleanup packet to a nearby player: acquired="
                    + acquired + ", progress=" + progressObserved + ", cleanup=" + cleanupObserved
                    + ", packets=" + packets);
            return;
        }
        if (!Blocks.DIRT.equals(level.getBlockState(obstacle).getBlock())) {
            helper.fail("Packet observation fixture unexpectedly completed the block break before cancellation");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:enhanced_ai_breaker", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void blockBreakerHonorsBlacklistTagAndBreakTiming(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 8, 4);
        ServerLevel level = helper.getLevel();
        BlockPos destination = helper.absolutePos(new BlockPos(7, 1, 2));

        // Without Enhanced AI the compatibility layer deliberately uses an
        // empty tag, so ordinary blocks remain breakable and no external tag
        // data is referenced during startup.
        helper.setBlock(3, 2, 2, Blocks.DIAMOND_ORE);
        Zombie tagFixture = helper.spawn(EntityType.ZOMBIE, 1, 1, 2);
        BlockBreakerPosGoal blacklisted = new BlockBreakerPosGoal(
                tagFixture, destination, 1.0, false, false, false);
        boolean fallbackTagIsEmpty = com.example.soundattract.integration.enhancedai.EnhancedAICompat.isEnhancedAiLoaded()
                || ResourceLocation.fromNamespaceAndPath("soundattract", "empty_block_tag").equals(
                        com.example.soundattract.integration.enhancedai.EnhancedAICompat.getBlockBlacklistTag().location());
        boolean ordinaryBlockRemainsEligible = blacklisted.canUse()
                && level.getBlockState(helper.absolutePos(new BlockPos(3, 2, 2))).is(Blocks.DIAMOND_ORE);
        double xrayFollowRange = com.example.soundattract.integration.enhancedai.EnhancedAICompat
                .getXrayAttributeValue(tagFixture);
        boolean absentIntegrationUsesZeroXrayRange = com.example.soundattract.integration.enhancedai.EnhancedAICompat
                .isEnhancedAiLoaded() || xrayFollowRange == 0.0;

        BlockPos timedObstacle = helper.absolutePos(new BlockPos(3, 2, 3));
        helper.setBlock(3, 2, 3, Blocks.DIRT);
        Zombie miner = helper.spawn(EntityType.ZOMBIE, 1, 1, 3);
        BlockBreakerPosGoal timedBreaker = new BlockBreakerPosGoal(
                miner, helper.absolutePos(new BlockPos(7, 1, 3)), 1.0, false, false, false);
        boolean acquired = timedBreaker.canUse();
        if (acquired) {
            timedBreaker.start();
            // Empty offhand, dirt hardness 0.5, and no modifiers produce a
            // 15-tick break (the vanilla 30-tick divisor is retained).
            for (int tick = 0; tick < 14; tick++) timedBreaker.tick();
        }
        boolean keptUntilDeadline = acquired && level.getBlockState(timedObstacle).is(Blocks.DIRT);
        if (keptUntilDeadline) timedBreaker.tick();
        boolean brokeAtDeadline = keptUntilDeadline && level.getBlockState(timedObstacle).isAir();
        timedBreaker.stop();

        if (!fallbackTagIsEmpty || !ordinaryBlockRemainsEligible || !absentIntegrationUsesZeroXrayRange) {
            helper.fail("Block breaker compatibility fallback did not preserve ordinary block eligibility: tag="
                    + com.example.soundattract.integration.enhancedai.EnhancedAICompat.getBlockBlacklistTag()
                    + ", canUse=" + blacklisted.canUse() + ", xrayRange=" + xrayFollowRange);
            return;
        }
        if (!keptUntilDeadline || !brokeAtDeadline) {
            helper.fail("Block breaker did not preserve its expected 15-tick dirt break timing: acquired="
                    + acquired + ", beforeDeadline=" + keptUntilDeadline + ", atDeadline=" + brokeAtDeadline);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:enhanced_ai_breaker", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void blockBreakerClearsMultipleRayHitBlocks(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 8, 4);
        ServerLevel level = helper.getLevel();
        BlockPos lower = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos upper = helper.absolutePos(new BlockPos(2, 3, 2));
        helper.setBlock(2, 2, 2, Blocks.DIRT);
        helper.setBlock(2, 3, 2, Blocks.DIRT);
        Zombie miner = helper.spawn(EntityType.ZOMBIE, 1, 1, 2);
        BlockBreakerPosGoal breaker = new BlockBreakerPosGoal(
                miner, helper.absolutePos(new BlockPos(7, 1, 2)), 0.1, false, false, false);
        boolean acquired = breaker.canUse();
        if (acquired) {
            breaker.start();
            for (int tick = 0; tick < 12 && (!level.getBlockState(lower).isAir()
                    || !level.getBlockState(upper).isAir()); tick++) breaker.tick();
        }
        boolean removedBothHits = acquired && level.getBlockState(lower).isAir()
                && level.getBlockState(upper).isAir();
        breaker.stop();
        if (!removedBothHits) {
            helper.fail("Block breaker did not process both vertical ray hits: acquired=" + acquired
                    + ", lower=" + level.getBlockState(lower) + ", upper=" + level.getBlockState(upper));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:sound_climb", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void soundClimbRequiresConfiguredMobAndStopsNavigation(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 8, 4);
        boolean previousEnabled = SoundAttractConfig.COMMON.enableRelentlessClimbing.get();
        List<? extends String> previousEligible = SoundAttractConfig.COMMON.relentlessEligibleMobs.get();
        try {
            SoundAttractConfig.COMMON.enableRelentlessClimbing.set(true);
            SoundAttractConfig.COMMON.relentlessEligibleMobs.set(List.of("minecraft:zombie"));

            Zombie eligible = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
            SoundClimbGoal zombieGoal = new SoundClimbGoal(eligible);
            boolean eligibleWithoutSoundRejected = !zombieGoal.canUse();

            net.minecraft.world.entity.monster.Creeper excluded = helper.spawn(EntityType.CREEPER, 5, 1, 2);
            excluded.setNoAi(true);
            SoundClimbGoal excludedGoal = new SoundClimbGoal(excluded);
            boolean excludedMobRejected = !excludedGoal.canUse();

            BlockPos navTarget = helper.absolutePos(new BlockPos(6, 1, 2));
            eligible.getNavigation().moveTo(
                    navTarget.getX() + 0.5, navTarget.getY(), navTarget.getZ() + 0.5, 1.0);
            zombieGoal.stop();
            boolean navigationStopped = !eligible.getNavigation().isInProgress();
            if (!eligibleWithoutSoundRejected || !excludedMobRejected || !navigationStopped) {
                helper.fail("Sound climb eligibility or stop cleanup failed: noSound=" + eligibleWithoutSoundRejected
                        + ", excluded=" + excludedMobRejected + ", navStopped=" + navigationStopped);
                return;
            }
            helper.succeed();
        } finally {
            SoundAttractConfig.COMMON.enableRelentlessClimbing.set(previousEnabled);
            SoundAttractConfig.COMMON.relentlessEligibleMobs.set(previousEligible);
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:sound_climb_horde", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void soundClimbUsesNearbyHordeForVerticalMovement(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 8, 4);
        ServerLevel level = helper.getLevel();
        boolean previousEnabled = SoundAttractConfig.COMMON.enableRelentlessClimbing.get();
        List<? extends String> previousEligible = SoundAttractConfig.COMMON.relentlessEligibleMobs.get();
        String dimension = level.dimension().location().toString();
        BlockPos soundPos = helper.absolutePos(new BlockPos(7, 6, 2));
        try {
            SoundAttractConfig.COMMON.enableRelentlessClimbing.set(true);
            SoundAttractConfig.COMMON.relentlessEligibleMobs.set(List.of("minecraft:zombie"));

            Zombie base = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
            Zombie climber = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
            base.setNoAi(true);
            climber.setNoAi(true);
            SoundTracker.addVirtualSound(soundPos, dimension, 32.0, 1_000_000.0, 200,
                    UUID.randomUUID(), "GameTestSoundClimbHorde");

            AttractionGoal attraction = new AttractionGoal(climber, 1.0);
            ((com.example.soundattract.mixin.MobAccessor) climber).getGoalSelector().addGoal(1, attraction);
            boolean attractionSelectedSound = attraction.canUse()
                    && soundPos.equals(attraction.getTargetSoundPos());
            if (attractionSelectedSound) attraction.start();

            SoundClimbGoal climb = new SoundClimbGoal(climber);
            boolean acquiredHordeClimb = attractionSelectedSound && climb.canUse();
            if (acquiredHordeClimb) {
                climb.start();
                climb.tick();
            }
            Vec3 movement = climber.getDeltaMovement();
            boolean climbed = acquiredHordeClimb && movement.y > 0.0
                    && climber.hasEffect(net.minecraft.world.effect.MobEffects.JUMP)
                    && !climber.getNavigation().isInProgress();

            climb.stop();
            attraction.stop();
            if (!attractionSelectedSound || !acquiredHordeClimb || !climbed) {
                helper.fail("Horde-assisted sound climb did not start vertical movement: selected="
                        + attractionSelectedSound + ", acquired=" + acquiredHordeClimb
                        + ", climbed=" + climbed + ", velocity=" + movement
                        + ", jumpEffect=" + climber.hasEffect(net.minecraft.world.effect.MobEffects.JUMP));
                return;
            }
            helper.succeed();
        } finally {
            SoundTracker.removeSoundAt(soundPos, dimension);
            SoundAttractConfig.COMMON.enableRelentlessClimbing.set(previousEnabled);
            SoundAttractConfig.COMMON.relentlessEligibleMobs.set(previousEligible);
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "soundattract:xray_fallback", timeoutTicks = TEST_TIMEOUT_TICKS)
    public void xrayFallbackUsesConfiguredRangeOnlyForTaggedMobs(GameTestHelper helper) {
        boolean previousEnabled = SoundAttractConfig.COMMON.enableXrayTargeting.get();
        String previousApplyTag = SoundAttractConfig.COMMON.xrayApplyTag.get();
        boolean previousRequireBetterNearby = SoundAttractConfig.COMMON.xrayRequireBetterNearby.get();
        int previousMinRange = SoundAttractConfig.COMMON.xrayMinRange.get();
        int previousMaxRange = SoundAttractConfig.COMMON.xrayMaxRange.get();
        double previousChance = SoundAttractConfig.COMMON.xrayChance.get();
        double taggedRange;
        double untaggedRange;
        try {
            SoundAttractConfig.COMMON.enableXrayTargeting.set(true);
            SoundAttractConfig.COMMON.xrayApplyTag
                    .set("soundattract:mobs/teleport_to_target/can_teleport");
            SoundAttractConfig.COMMON.xrayRequireBetterNearby.set(false);
            SoundAttractConfig.COMMON.xrayMinRange.set(18);
            SoundAttractConfig.COMMON.xrayMaxRange.set(18);
            SoundAttractConfig.COMMON.xrayChance.set(1.0);

            EnderMan tagged = helper.spawn(EntityType.ENDERMAN, 1, 1, 1);
            Zombie untagged = helper.spawn(EntityType.ZOMBIE, 3, 1, 1);
            taggedRange = com.example.soundattract.event.StealthDetectionEvents.getEffectiveXrayRange(tagged);
            untaggedRange = com.example.soundattract.event.StealthDetectionEvents.getEffectiveXrayRange(untagged);
        } finally {
            SoundAttractConfig.COMMON.enableXrayTargeting.set(previousEnabled);
            SoundAttractConfig.COMMON.xrayApplyTag.set(previousApplyTag);
            SoundAttractConfig.COMMON.xrayRequireBetterNearby.set(previousRequireBetterNearby);
            SoundAttractConfig.COMMON.xrayMinRange.set(previousMinRange);
            SoundAttractConfig.COMMON.xrayMaxRange.set(previousMaxRange);
            SoundAttractConfig.COMMON.xrayChance.set(previousChance);
        }

        if (!com.example.soundattract.integration.enhancedai.EnhancedAICompat.isEnhancedAiLoaded()
                && (taggedRange != 18.0 || untaggedRange != 0.0)) {
            helper.fail("Enhanced-AI-inspired XRAY fallback ignored the configured tag/range gates: tagged="
                    + taggedRange + ", untagged=" + untaggedRange);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void pickupGoalStopsWhenItsTargetDiesBeforeMounting(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 3);
        ServerLevel level = helper.getLevel();
        boolean previousEnabled = SoundAttractConfig.COMMON.enablePickUpAndThrowToSound.get();
        double previousChance = SoundAttractConfig.COMMON.pickUpChance.get();
        int previousMinimumDistance = SoundAttractConfig.COMMON.pickUpMinDistanceToPickUp.get();
        String previousPickedUpTag = SoundAttractConfig.COMMON.pickUpCanBePickedUpTag.get();
        String dimension = level.dimension().location().toString();
        Zombie picker = null;
        double previousFollowRange = Double.NaN;
        BlockPos soundPos = helper.absolutePos(new BlockPos(6, 1, 1));
        boolean acquired;
        boolean stoppedAfterTargetRemoval;
        boolean mountStateClean;
        try {
            SoundAttractConfig.COMMON.enablePickUpAndThrowToSound.set(true);
            SoundAttractConfig.COMMON.pickUpChance.set(1.0);
            SoundAttractConfig.COMMON.pickUpMinDistanceToPickUp.set(1);
            SoundAttractConfig.COMMON.pickUpCanBePickedUpTag.set("soundattract:gametest/pickup_target");
            picker = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
            var followRange = picker.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
            previousFollowRange = followRange.getBaseValue();
            // Keep the production nearest-entity query inside this fixture so
            // mobs from neighboring GameTests cannot become the selected target.
            // Put the intended pickup target at the same position and constrain
            // acquisition tightly so mobs from neighboring fixtures cannot win
            // the production nearest-entity query.
            followRange.setBaseValue(0.5);
            Creeper target = helper.spawn(EntityType.CREEPER, 1, 1, 1);
            SoundTracker.addVirtualSound(soundPos, dimension, 16.0, 5.0, 200, UUID.randomUUID(), "GameTestPickupInterrupted");
            PickUpAndThrowToSoundGoal goal = new PickUpAndThrowToSoundGoal(picker);
            acquired = goal.canUse();
            if (acquired) {
                goal.start();
                target.discard();
            }
            stoppedAfterTargetRemoval = acquired && !goal.canContinueToUse();
            goal.stop();
            mountStateClean = !picker.isVehicle() && !target.isPassenger() && !goal.canContinueToUse();
        } finally {
            if (picker != null && Double.isFinite(previousFollowRange)) {
                picker.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)
                        .setBaseValue(previousFollowRange);
            }
            SoundAttractConfig.COMMON.enablePickUpAndThrowToSound.set(previousEnabled);
            SoundAttractConfig.COMMON.pickUpChance.set(previousChance);
            SoundAttractConfig.COMMON.pickUpMinDistanceToPickUp.set(previousMinimumDistance);
            SoundAttractConfig.COMMON.pickUpCanBePickedUpTag.set(previousPickedUpTag);
            SoundTracker.removeSoundAt(soundPos, dimension);
        }
        if (!acquired || !stoppedAfterTargetRemoval || !mountStateClean) {
            helper.fail("Pickup goal did not stop cleanly when the selected mob died before mounting: acquired="
                    + acquired + ", stopped=" + stoppedAfterTargetRemoval + ", mountClean=" + mountStateClean);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = TEST_TIMEOUT_TICKS)
    public void enhancedAiGoalsRejectUntaggedTeleportersAndMobGriefingDisabled(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 7, 3);
        ServerLevel level = helper.getLevel();
        boolean previousTeleportEnabled = SoundAttractConfig.COMMON.enableTeleportToSound.get();
        double previousTeleportChance = SoundAttractConfig.COMMON.teleportChance.get();
        boolean previousMobGriefing = level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
        String dimension = level.dimension().location().toString();
        BlockPos soundPos = helper.absolutePos(new BlockPos(6, 1, 1));
        BlockPos obstacle = helper.absolutePos(new BlockPos(3, 2, 2));
        int previousSoundCap = reserveSoundCapacityForGameTest(1);
        boolean untaggedTeleporterRejected;
        boolean teleportChanceRejected;
        boolean teleportRangeRejected;
        boolean configuredTeleporterAndTargetTagsAccepted;
        boolean breakerRejectedWithoutMobGriefing;
        boolean breakerRejectedWithoutRequiredTool;
        try {
            SoundAttractConfig.COMMON.enableTeleportToSound.set(true);
            SoundAttractConfig.COMMON.teleportChance.set(1.0);
            Zombie untaggedTeleporter = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
            helper.spawn(EntityType.ZOMBIE, 2, 1, 1);
            SoundTracker.addVirtualSound(soundPos, dimension, 12.0, 5.0, 200, UUID.randomUUID(), "GameTestTeleportGate");
            untaggedTeleporterRejected = !new TeleportToSoundGoal(untaggedTeleporter).canUse();
            EnderMan taggedTeleporter = helper.spawn(EntityType.ENDERMAN, 1, 1, 3);
            helper.spawn(EntityType.ZOMBIE, 2, 1, 3);
            TeleportToSoundGoal chanceGatedGoal = new TeleportToSoundGoal(taggedTeleporter);
            SoundAttractConfig.COMMON.teleportChance.set(0.0);
            teleportChanceRejected = !chanceGatedGoal.canUse();
            SoundAttractConfig.COMMON.teleportChance.set(1.0);
            var followRange = taggedTeleporter.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
            double previousFollowRange = followRange.getBaseValue();
            followRange.setBaseValue(0.25);
            teleportRangeRejected = !new TeleportToSoundGoal(taggedTeleporter).canUse();
            followRange.setBaseValue(previousFollowRange);
            configuredTeleporterAndTargetTagsAccepted = new TeleportToSoundGoal(taggedTeleporter).canUse();

            helper.setBlock(3, 2, 2, Blocks.DIRT);
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)
                    .set(false, level.getServer());
            Zombie miner = helper.spawn(EntityType.ZOMBIE, 1, 1, 2);
            BlockBreakerPosGoal breaker = new BlockBreakerPosGoal(
                    miner, helper.absolutePos(new BlockPos(6, 1, 2)), 0.1, false, false, false);
            breakerRejectedWithoutMobGriefing = !breaker.canUse()
                    && Blocks.DIRT == level.getBlockState(obstacle).getBlock();
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)
                    .set(true, level.getServer());
            BlockBreakerPosGoal toolRequiredBreaker = new BlockBreakerPosGoal(
                    miner, helper.absolutePos(new BlockPos(6, 1, 2)), 0.1, true, false, false);
            breakerRejectedWithoutRequiredTool = !toolRequiredBreaker.canUse()
                    && Blocks.DIRT == level.getBlockState(obstacle).getBlock();
        } finally {
            SoundAttractConfig.COMMON.enableTeleportToSound.set(previousTeleportEnabled);
            SoundAttractConfig.COMMON.teleportChance.set(previousTeleportChance);
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)
                    .set(previousMobGriefing, level.getServer());
            SoundTracker.removeSoundAt(soundPos, dimension);
            SoundAttractConfig.COMMON.maxSoundsTracked.set(previousSoundCap);
        }

        if (!untaggedTeleporterRejected) {
            helper.fail("Teleport-to-sound accepted a zombie absent from the configured can-teleport entity tag");
            return;
        }
        if (!teleportChanceRejected || !teleportRangeRejected || !configuredTeleporterAndTargetTagsAccepted) {
            helper.fail("Teleport-to-sound did not reject zero-chance/out-of-range attempts and accept tagged Enderman/zombie candidates: chanceRejected="
                    + teleportChanceRejected + ", rangeRejected=" + teleportRangeRejected
                    + ", taggedAccepted=" + configuredTeleporterAndTargetTagsAccepted);
            return;
        }
        if (!breakerRejectedWithoutMobGriefing) {
            helper.fail("Block-breaker goal ignored mobGriefing=false or modified its obstruction");
            return;
        }
        if (!breakerRejectedWithoutRequiredTool) {
            helper.fail("Tool-only block-breaker accepted a mob without a digging tool or changed the obstruction");
            return;
        }
        helper.succeed();
    }

    private static void prepareFloor(GameTestHelper helper, int minX, int minZ, int maxX, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
                helper.setBlock(x, 1, z, Blocks.AIR);
                helper.setBlock(x, 2, z, Blocks.AIR);
            }
        }
    }

    private static void prepareFlowFieldCorridor(GameTestHelper helper) {
        prepareFloor(helper, 0, 0, 8, 8);
        for (int y = 1; y <= 2; y++) {
            for (int x = 0; x <= 8; x++) {
                helper.setBlock(x, y, 0, Blocks.STONE);
                helper.setBlock(x, y, 8, Blocks.STONE);
            }
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(0, y, z, Blocks.STONE);
                helper.setBlock(8, y, z, Blocks.STONE);
            }
        }
    }

    private static void prepareRaisedFloor(GameTestHelper helper, int minX, int minZ, int maxX, int maxZ, int y) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                helper.setBlock(x, y, z, Blocks.STONE);
                helper.setBlock(x, y + 1, z, Blocks.AIR);
                helper.setBlock(x, y + 2, z, Blocks.AIR);
            }
        }
    }

    private static void tickWindScentLevel(ServerLevel level) {
        try {
            var tickLevel = com.example.soundattract.event.WindScentEvents.class
                    .getDeclaredMethod("tickLevel", ServerLevel.class);
            tickLevel.setAccessible(true);
            tickLevel.invoke(null, level);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not execute the wind-scent level tick", e);
        }
    }

    private static void beginToughAsNailsPresentProfileTest(GameTestHelper helper,
                                                            net.minecraft.server.level.ServerPlayer player,
                                                            net.minecraft.world.item.Item leafHelmet,
                                                            net.minecraft.world.item.Item woolChestplate,
                                                            int previousPollInterval) {
        ServerLevel level = helper.getLevel();
        long now = level.getGameTime();
        double baselineScentMultiplier = com.example.soundattract.integration.toughasnails.ToughAsNailsStateCache
                .scentMultiplier(player, now);
        double baselineSoundMultiplier = com.example.soundattract.integration.toughasnails.ToughAsNailsStateCache
                .soundMultiplier(player, now);
        double baselineEffectiveScent = com.example.soundattract.event.AwarenessServerEvents
                .getEffectivePlayerScent(level, player);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                new net.minecraft.world.item.ItemStack(leafHelmet));
        helper.runAfterDelay(3, () -> {
            long leafUpdateTime = level.getGameTime();
            double leafScentMultiplier = com.example.soundattract.integration.toughasnails.ToughAsNailsStateCache
                    .scentMultiplier(player, leafUpdateTime);
            double leafEffectiveScent = com.example.soundattract.event.AwarenessServerEvents
                    .getEffectivePlayerScent(level, player);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,
                    new net.minecraft.world.item.ItemStack(woolChestplate));
            helper.runAfterDelay(3, () -> finishToughAsNailsPresentProfileTest(helper, player,
                    previousPollInterval, baselineScentMultiplier, baselineSoundMultiplier,
                    baselineEffectiveScent, leafScentMultiplier, leafEffectiveScent));
        });
    }

    private static void finishToughAsNailsPresentProfileTest(GameTestHelper helper,
                                                            net.minecraft.server.level.ServerPlayer player,
                                                            int previousPollInterval,
                                                            double baselineScentMultiplier,
                                                            double baselineSoundMultiplier,
                                                            double baselineEffectiveScent,
                                                            double leafScentMultiplier,
                                                            double leafEffectiveScent) {
        ServerLevel level = helper.getLevel();
        long now = level.getGameTime();
        double woolSoundMultiplier = com.example.soundattract.integration.toughasnails.ToughAsNailsStateCache
                .soundMultiplier(player, now);
        boolean leafReducedScent = leafScentMultiplier < baselineScentMultiplier - 0.001
                && leafEffectiveScent < baselineEffectiveScent - 0.001;
        boolean woolReducedSound = woolSoundMultiplier < baselineSoundMultiplier - 0.001;
        BlockPos soundPos = helper.absolutePos(new BlockPos(5, 1, 5));
        String dimension = level.dimension().location().toString();
        Integer baseRange = SoundAttractConfig.PLAYER_ACTION_RANGES_CACHE.get("WALKING");
        Double baseWeight = SoundAttractConfig.PLAYER_ACTION_WEIGHTS_CACHE.get("WALKING");
        double expectedRange = baseRange == null ? 0.0
                : Math.min(baseRange * woolSoundMultiplier, SoundAttractConfig.SERVER.maxClientSoundRange.get());
        double expectedWeight = baseWeight == null ? 0.0 : baseWeight * woolSoundMultiplier;
        com.example.soundattract.tracking.SoundTracker.removeSoundAt(soundPos, dimension);
        com.example.soundattract.network.SoundMessage.handle(new SoundMessage(
                com.example.soundattract.network.SoundMessage.POINT_BLANK_SOUND_ID,
                soundPos.getX() + 0.5, soundPos.getY() + 0.5, soundPos.getZ() + 0.5,
                level.dimension().location(), Optional.of(player.getUUID()),
                "WALKING", 0.0f, false, null, null, null), player);
        com.example.soundattract.tracking.SoundTracker.SoundRecord soundRecord =
                com.example.soundattract.tracking.SoundTracker.getNearbySounds(level, soundPos, 128.0)
                        .stream().filter(record -> record.pos.equals(soundPos)
                                && com.example.soundattract.network.SoundMessage.POINT_BLANK_SOUND_ID.toString()
                                .equals(record.soundId))
                        .findFirst().orElse(null);
        boolean weightApplied = soundRecord != null && (Math.abs(soundRecord.weight - expectedWeight) < 0.0001
                || Math.abs(soundRecord.weight - expectedWeight * 2.0) < 0.0001);
        boolean playerSoundApplied = soundRecord != null && baseRange != null && baseWeight != null
                && soundRecord.range == (int) expectedRange
                && weightApplied;
        com.example.soundattract.tracking.SoundTracker.removeSoundAt(soundPos, dimension);
        ScentEvents.onPlayerLoggedOut(player);
        com.example.soundattract.integration.toughasnails.ToughAsNailsStateCache.remove(player.getUUID());
        level.getServer().getPlayerList().remove(player);
        SoundAttractConfig.COMMON.toughAsNailsPollIntervalTicks.set(previousPollInterval);
        if (!leafReducedScent || !woolReducedSound || !playerSoundApplied) {
            helper.fail("Tough As Nails runtime profile did not reach its production consumers: leafScent="
                    + leafScentMultiplier + " vs " + baselineScentMultiplier + ", effectiveScent="
                    + leafEffectiveScent + " vs " + baselineEffectiveScent + ", woolSound="
                    + woolSoundMultiplier + " vs " + baselineSoundMultiplier + ", playerSound="
                    + playerSoundApplied + ", soundRecord=" + soundRecord + ", expectedRange=" + expectedRange
                    + ", expectedWeight=" + expectedWeight);
            return;
        }
        helper.succeed();
    }

    private static boolean setBasinEvacuationForTest(ServerLevel level, Zombie worker, Vec3 target, long expiresTick) {
        try {
            var setter = com.example.soundattract.ai.LivingWallCoordinator.class.getDeclaredMethod(
                    "setBasinEvacuationForTest", ServerLevel.class, UUID.class, Vec3.class, long.class);
            setter.setAccessible(true);
            setter.invoke(null, level, worker.getUUID(), target, expiresTick);
            return true;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<BlockPos> findWallTargetsForTest(ServerLevel level,
                                                         List<? extends net.minecraft.world.entity.Mob> cluster,
                                                         BlockPos threat) {
        try {
            var finder = com.example.soundattract.ai.LivingWallCoordinator.class.getDeclaredMethod(
                    "findClusterTargetsForTest", ServerLevel.class, List.class, BlockPos.class);
            finder.setAccessible(true);
            return (List<BlockPos>) finder.invoke(null, level, cluster, threat);
        } catch (ReflectiveOperationException exception) {
            return List.of();
        }
    }

    private static Vec3 recruitmentTargetForTest(net.minecraft.world.entity.Mob worker) {
        try {
            var getter = com.example.soundattract.ai.LivingWallCoordinator.class
                    .getDeclaredMethod("getRecruitmentTarget", net.minecraft.world.entity.Mob.class);
            getter.setAccessible(true);
            return (Vec3) getter.invoke(null, worker);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static long mobGroupGenerationForTest() {
        try {
            var generation = com.example.soundattract.ai.MobGroupManager.class
                    .getDeclaredMethod("currentServerGeneration");
            generation.setAccessible(true);
            return (long) generation.invoke(null);
        } catch (ReflectiveOperationException exception) {
            return Long.MIN_VALUE;
        }
    }

    private static boolean applyMobGroupResultForTest(ServerLevel level,
                                                      com.example.soundattract.worker.WorkerScheduler.GroupComputeResult result,
                                                      List<? extends net.minecraft.world.entity.Mob> candidates) {
        try {
            var apply = com.example.soundattract.ai.MobGroupManager.class.getDeclaredMethod(
                    "applyGroupResult", ServerLevel.class,
                    com.example.soundattract.worker.WorkerScheduler.GroupComputeResult.class,
                    java.util.Collection.class);
            apply.setAccessible(true);
            apply.invoke(null, level, result, candidates);
            return true;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    private static Object getBasinEvacuationForTest(ServerLevel level, Zombie worker) {
        try {
            var getter = com.example.soundattract.ai.LivingWallCoordinator.class.getDeclaredMethod(
                    "getBasinEvacuationForTest", ServerLevel.class, UUID.class);
            getter.setAccessible(true);
            return getter.invoke(null, level, worker.getUUID());
        } catch (ReflectiveOperationException exception) {
            return exception;
        }
    }

    private static void awaitBridgeWorkGroupEvacuation(
            GameTestHelper helper, List<Zombie> workers,
            List<com.example.soundattract.ai.BridgeWorkGoal> goals, List<Vec3> targets,
            int ticksRemaining) {
        boolean allCrossed = true;
        List<String> evidence = new java.util.ArrayList<>();
        for (int index = 0; index < workers.size(); index++) {
            Zombie worker = workers.get(index);
            var expectedGoal = goals.get(index);
            boolean running = ((com.example.soundattract.mixin.MobAccessor) worker).getGoalSelector()
                    .getAvailableGoals().stream().filter(wrapped -> wrapped.isRunning()).anyMatch(wrapped -> wrapped.getGoal() == expectedGoal);
            // BridgeWorkGoal ends navigation inside a 1.5-block arrival radius.
            // Match that production threshold instead of requiring the mob's
            // center to pass a stricter x-plane it is designed to stop short of.
            boolean crossed = worker.getX() >= targets.get(index).x - 1.5D;
            allCrossed &= running && crossed;
            evidence.add("worker=" + worker.getUUID() + ", running=" + running + ", crossed=" + crossed
                    + ", pos=" + worker.position() + ", target=" + targets.get(index));
        }
        if (allCrossed) {
            ServerLevel level = helper.getLevel();
            for (int index = 0; index < workers.size(); index++) {
                if (!setBasinEvacuationForTest(level, workers.get(index), targets.get(index), level.getGameTime())) {
                    helper.fail("Could not expire group member " + index + " evacuation after crossing: " + evidence);
                    return;
                }
            }
            helper.runAfterDelay(2, () -> {
                boolean allReleased = true;
                for (int index = 0; index < workers.size(); index++) {
                    Zombie worker = workers.get(index);
                    var expectedGoal = goals.get(index);
                    boolean stopped = ((com.example.soundattract.mixin.MobAccessor) worker).getGoalSelector()
                            .getAvailableGoals().stream().filter(wrapped -> wrapped.isRunning()).noneMatch(wrapped -> wrapped.getGoal() == expectedGoal);
                    boolean targetCleared = getBasinEvacuationForTest(level, worker) == null;
                    allReleased &= stopped && worker.getNavigation().isDone() && targetCleared;
                    evidence.add("released=" + stopped + "/" + worker.getNavigation().isDone()
                            + "/" + targetCleared + " worker=" + worker.getUUID());
                }
                if (!allReleased) {
                    helper.fail("Group crossed the deck but bridge-work assignments did not all release: " + evidence);
                    return;
                }
                com.example.soundattract.SoundAttractMod.LOGGER.info(
                        "[GameTest] Production bridge-work evacuation crossed and released all {} group members: {}",
                        workers.size(), evidence);
                helper.succeed();
            });
            return;
        }
        if (ticksRemaining <= 0) {
            helper.fail("Production bridge-work controller did not evacuate the full group across the completed deck: "
                    + evidence);
            return;
        }
        helper.runAfterDelay(1, () -> awaitBridgeWorkGroupEvacuation(
                helper, workers, goals, targets, ticksRemaining - 1));
    }

    private static void attemptScentGoal(GameTestHelper helper, FollowScentGoal goal, Zombie zombie,
                                         ScentManager scents, UUID owner, BlockPos trailPos, int ticksRemaining) {
        if (goal.canUse()) {
            if (!goal.isFollowingScent() || goal.getTargetPosition() == null
                    || BlockPos.containing(goal.getTargetPosition()).distSqr(trailPos) > 1L) {
                scents.removeOwnerNodes(owner);
                helper.fail("Scent goal selected a target other than the nearby fresh owner trail");
                return;
            }
            goal.start();
            boolean navigationStarted = zombie.getNavigation().getPath() != null;
            goal.stop();
            boolean stopped = !goal.isFollowingScent();
            scents.removeOwnerNodes(owner);
            if (!navigationStarted || !stopped) {
                helper.fail("Scent goal cleanup/path state mismatch: path=" + navigationStarted
                        + ", stopped=" + stopped + ", mob=" + zombie.position()
                        + ", trail=" + trailPos + ", ground=" + helper.getLevel().getBlockState(trailPos.below()));
                return;
            }
            helper.succeed();
            return;
        }
        if (ticksRemaining <= 0) {
            scents.removeOwnerNodes(owner);
            helper.fail("Eligible zombie did not acquire the nearby fresh scent owner trail within the retry window");
            return;
        }
        helper.runAfterDelay(1, () -> attemptScentGoal(helper, goal, zombie, scents, owner, trailPos, ticksRemaining - 1));
    }

    private static void attemptQueuedFlowFieldStep(GameTestHelper helper, Zombie follower,
                                                   Vec3 target, BlockPos lava, int ticksRemaining) {
        Set<BlockPos> visited = new java.util.HashSet<>();
        for (int routeStep = 0; routeStep < 16; routeStep++) {
            Vec3 step = com.example.soundattract.pathfinding.FlowFieldManager.getNextStep(follower, target);
            if (step == null) {
                if (ticksRemaining <= 0) {
                    helper.fail("Queued flow-field scan did not produce a step before its retry deadline");
                    return;
                }
                helper.runAfterDelay(1, () -> attemptQueuedFlowFieldStep(
                        helper, follower, target, lava, ticksRemaining - 1));
                return;
            }

            if (follower.blockPosition().equals(BlockPos.containing(target))) {
                helper.succeed();
                return;
            }
            if (!visited.add(follower.blockPosition())) {
                helper.fail("Flow-field route repeated a cell before reaching its target: "
                        + follower.blockPosition() + ", visited=" + visited);
                return;
            }
            Vec3 candidate = follower.position().add(step);
            boolean finite = Double.isFinite(step.x) && Double.isFinite(step.y) && Double.isFinite(step.z);
            BlockPos candidateCell = BlockPos.containing(candidate);
            boolean walkable = com.example.soundattract.pathfinding.NodeRouter.isWalkable(follower, candidate);
            if (!finite || !walkable || candidateCell.equals(lava)) {
                helper.fail("Flow-field returned an invalid, unsupported, or hazardous follower step: step=" + step
                        + ", target=" + target + ", candidate=" + candidate + ", walkable=" + walkable
                        + ", support="
                        + follower.level().getBlockState(candidateCell.below()) + ", feet="
                        + follower.level().getBlockState(candidateCell) + ", above="
                        + follower.level().getBlockState(candidateCell.above()) + ", footFluid="
                        + follower.level().getFluidState(candidateCell) + ", supportFluid="
                        + follower.level().getFluidState(candidateCell.below()) + ", hazard="
                        + com.example.soundattract.pathfinding.HazardClassifier.classify(
                                follower.level(), candidateCell) + ", dangerous="
                        + com.example.soundattract.pathfinding.HazardClassifier.isDangerous(
                                follower.level(), follower, candidate) + ", lava=" + lava);
                return;
            }
            follower.setPos(candidate.x, candidate.y, candidate.z);
        }
        helper.fail("Flow-field route did not reach its target within the safe-route bound: mob="
                + follower.blockPosition() + ", target=" + BlockPos.containing(target));
    }

    private static void attemptTransientWindScentConsumer(GameTestHelper helper, Zombie zombie,
                                                          ScentManager scents, UUID owner, BlockPos trailPos,
                                                          FollowScentGoal goal, int ticksRemaining) {
        if (!goal.canUse()) {
            if (ticksRemaining > 0) {
                helper.runAfterDelay(1, () -> attemptTransientWindScentConsumer(
                        helper, zombie, scents, owner, trailPos, goal, ticksRemaining - 1));
                return;
            }
            List<String> available = scents.getNodesInArea(new ChunkPos(trailPos)).stream()
                    .filter(node -> owner.equals(node.getOwnerUUID()))
                    .map(node -> node.getSourceType() + "@" + node.getPosition())
                    .toList();
            scents.removeOwnerNodes(owner);
            helper.fail("Zombie did not acquire the transient PLAYER_WIND node within 60 ticks: target="
                    + goal.getTargetPosition() + ", nodes=" + available);
            return;
        }
        if (!goal.isFollowingScent() || goal.getTargetPosition() == null
                || BlockPos.containing(goal.getTargetPosition()).distSqr(trailPos) > 1L) {
            scents.removeOwnerNodes(owner);
            helper.fail("Wind-scent goal selected an unexpected target: " + goal.getTargetPosition());
            return;
        }
        goal.start();
        boolean navigationStarted = zombie.getNavigation().getPath() != null;
        goal.stop();
        boolean stopped = !goal.isFollowingScent();
        scents.removeOwnerNodes(owner);
        if (!navigationStarted || !stopped) {
            helper.fail("Transient wind-scent consumer cleanup/path mismatch: navigation="
                    + navigationStarted + ", stopped=" + stopped);
            return;
        }
        helper.succeed();
    }

    private static void attemptProjectileScentConsumers(GameTestHelper helper, List<Zombie> consumers, ScentManager scents,
                                                         List<ScentSourceType> sourceTypes, int sourceIndex) {
        if (sourceIndex >= sourceTypes.size()) {
            helper.succeed();
            return;
        }
        ScentSourceType sourceType = sourceTypes.get(sourceIndex);
        Zombie zombie = consumers.get(sourceIndex);
        zombie.getNavigation().stop();
        zombie.setDeltaMovement(Vec3.ZERO);
        UUID owner = UUID.randomUUID();
        // Keep the route inside the local support corridor so nearby test
        // structures and unloaded edges cannot influence vanilla path search.
        BlockPos trailPos = helper.absolutePos(new BlockPos(6, 1, 1));
        ScentNode node = new ScentNode(Vec3.atCenterOf(trailPos), helper.getLevel().getGameTime(), 0.8f,
                owner, sourceType);
        scents.addScentNode(node);
        attemptProjectileScentConsumerForType(helper, zombie, consumers, scents, sourceTypes, sourceIndex,
                sourceType, owner, trailPos);
    }

    private static void removeAllGoalsForFixture(Zombie zombie) {
        var accessor = (com.example.soundattract.mixin.MobAccessor) zombie;
        var goals = accessor.getGoalSelector();
        for (var wrapped : new java.util.ArrayList<>(goals.getAvailableGoals())) {
            goals.removeGoal(wrapped.getGoal());
        }
        var targets = accessor.getTargetSelector();
        for (var wrapped : new java.util.ArrayList<>(targets.getAvailableGoals())) {
            targets.removeGoal(wrapped.getGoal());
        }
        zombie.setTarget(null);
        zombie.getNavigation().stop();
    }

    private static void removeAllGoalsExcept(Zombie zombie, net.minecraft.world.entity.ai.goal.Goal retainedGoal) {
        var accessor = (com.example.soundattract.mixin.MobAccessor) zombie;
        var goals = accessor.getGoalSelector();
        for (var wrapped : new java.util.ArrayList<>(goals.getAvailableGoals())) {
            if (wrapped.getGoal() != retainedGoal) goals.removeGoal(wrapped.getGoal());
        }
        var targets = accessor.getTargetSelector();
        for (var wrapped : new java.util.ArrayList<>(targets.getAvailableGoals())) {
            targets.removeGoal(wrapped.getGoal());
        }
    }

    private static void attemptProjectileScentConsumerForType(GameTestHelper helper, Zombie zombie,
                                                               List<Zombie> consumers, ScentManager scents,
                                                               List<ScentSourceType> sourceTypes, int sourceIndex,
                                                               ScentSourceType sourceType, UUID owner, BlockPos trailPos) {
        zombie.getNavigation().stop();
        zombie.setTarget(null);
        FollowScentGoal goal = new FollowScentGoal(zombie, scents);
        if (!goal.acquireScentForTesting()) {
            scents.removeOwnerNodes(owner);
            helper.fail("Scent follower did not select projectile source " + sourceType
                    + " when directly evaluated against its isolated manager");
            return;
        }

        boolean selectedOwnedNode = goal.isFollowingScent() && goal.getTargetPosition() != null
                && BlockPos.containing(goal.getTargetPosition()).distSqr(trailPos) <= 1L;
        goal.start();
        goal.stop();
        zombie.getNavigation().stop();
        scents.removeOwnerNodes(owner);
        if (!selectedOwnedNode || goal.isFollowingScent() || goal.getTargetPosition() != null) {
            helper.fail("Projectile scent consumer did not select its owned " + sourceType
                    + " node and clear state on stop: selected=" + selectedOwnedNode
                    + ", followingAfterStop=" + goal.isFollowingScent()
                    + ", targetAfterStop=" + goal.getTargetPosition());
            return;
        }
        helper.runAfterDelay(1, () -> attemptProjectileScentConsumers(
                helper, consumers, scents, sourceTypes, sourceIndex + 1));
    }

    private static void attemptArrowNearMiss(GameTestHelper helper, ServerLevel level, Arrow arrow,
                                             Zombie shooter, Zombie pursuer, net.minecraft.world.entity.Mob mobWithoutPursuitGoal,
                                             Zombie pursuerOutsideRadius, ServerPlayer nearbyPlayer,
                                             int ticksRemaining) {
        if (!arrow.isAlive() || ticksRemaining <= 0) {
            ArrowInvestigationEvents.onEntityLeave(arrow);
            arrow.discard();
            level.getServer().getPlayerList().remove(nearbyPlayer);
            helper.fail("Arrow near-miss scan did not reach an eligible four-tick investigation window");
            return;
        }
        long now = level.getGameTime();
        if (Math.floorMod(arrow.getId() + (int) now, 4) != 0) {
            helper.runAfterDelay(1, () -> attemptArrowNearMiss(helper, level, arrow, shooter, pursuer,
                    mobWithoutPursuitGoal, pursuerOutsideRadius, nearbyPlayer, ticksRemaining - 1));
            return;
        }

        double y = pursuer.getY() + pursuer.getBbHeight() * 0.5;
        Vec3 start = new Vec3(pursuer.getX() - 3.0, y, pursuer.getZ());
        Vec3 end = new Vec3(pursuer.getX() + 3.0, y, pursuer.getZ());
        mobWithoutPursuitGoal.setPos(pursuer.getX(), pursuer.getY(), pursuer.getZ() + 1.0);
        pursuerOutsideRadius.setPos(pursuer.getX(), pursuer.getY(), pursuer.getZ() + 3.5);
        ArrowInvestigationEvents.ArrowState state = new ArrowInvestigationEvents.ArrowState(
                start, now, shooter.getUUID(), true);
        ArrowInvestigationEvents.ARROWS.put(new ArrowInvestigationEvents.ArrowKey(
                level.dimension().location(), arrow.getId()), state);
        ((com.example.soundattract.mixin.MobAccessor) mobWithoutPursuitGoal)
                .getGoalSelector().removeAllGoals(goal -> true);
        ArrowInvestigationEvents.clearInvestigationCooldownForTesting(pursuer.getUUID());
        arrow.setNoGravity(true);
        arrow.setPos(end);
        arrow.setDeltaMovement(new Vec3(1.0, 0.0, 0.0));
        int previousSoundCap = SoundAttractConfig.COMMON.maxSoundsTracked.get();
        Long firstCooldownDeadline;
        try {
            // The shared tracker may contain records from other fixtures. Keep
            // this event-path assertion independent of the configured global
            // capacity while the synchronous dispatch and record check run.
            SoundAttractConfig.COMMON.maxSoundsTracked.set(1_000_000);
            ArrowInvestigationEvents.onServerTickForTesting(level, 3.0);
            firstCooldownDeadline = ArrowInvestigationEvents.getInvestigationCooldownEndForTesting(pursuer.getUUID());
            ArrowInvestigationEvents.setPreviousPositionForTesting(level, arrow.getId(), start);
            ArrowInvestigationEvents.onServerTickForTesting(level, 3.0);
        } finally {
            SoundAttractConfig.COMMON.maxSoundsTracked.set(previousSoundCap);
        }

        String dimension = level.dimension().location().toString();
        BlockPos origin = state.lastInvestigationPos;
        List<SoundTracker.SoundRecord> investigations = SoundTracker.getNearbySounds(
                        level, origin == null ? BlockPos.containing(state.spawnPos) : origin, 2.0).stream()
                .filter(record -> record instanceof SoundTracker.VirtualSoundRecord virtual
                        && "arrow_investigation".equals(virtual.animationClass))
                .toList();
        boolean notified = state.notifiedMobIds.contains(pursuer.getUUID());
        Long repeatedCooldownDeadline = ArrowInvestigationEvents.getInvestigationCooldownEndForTesting(pursuer.getUUID());
        List<SoundTracker.SoundRecord> dispatchRecords = investigations.stream()
                .filter(record -> state.lastInvestigationPos != null
                        && state.lastInvestigationPos.equals(record.pos))
                .toList();
        boolean dispatchedOnce = dispatchRecords.size() == 1 && dispatchRecords.get(0).isPlayerProduced()
                && java.util.Objects.equals(firstCooldownDeadline, repeatedCooldownDeadline);
        boolean excludedInvalidTargets = !state.notifiedMobIds.contains(mobWithoutPursuitGoal.getUUID())
                && !state.notifiedMobIds.contains(pursuerOutsideRadius.getUUID())
                && !state.notifiedMobIds.contains(shooter.getUUID());
        dispatchRecords.forEach(record -> SoundTracker.removeSoundAt(record.pos, dimension));
        ArrowInvestigationEvents.onEntityLeave(arrow);
        arrow.discard();
        level.getServer().getPlayerList().remove(nearbyPlayer);

        if (!notified || !dispatchedOnce || !excludedInvalidTargets) {
            helper.fail("Arrow segment target filters/cooldown failed: notified=" + notified
                    + ", dispatchedOnce=" + dispatchedOnce + ", excludedInvalidTargets=" + excludedInvalidTargets
                    + " (noGoal=" + !state.notifiedMobIds.contains(mobWithoutPursuitGoal.getUUID())
                    + ", outsideRadius=" + !state.notifiedMobIds.contains(pursuerOutsideRadius.getUUID())
                    + ", shooterExcluded=" + !state.notifiedMobIds.contains(shooter.getUUID()) + ")"
                    + ", virtualRecords=" + investigations.size() + ", exactDispatchRecords="
                    + dispatchRecords.size() + ", notifiedIds=" + state.notifiedMobIds
                    + ", expected=" + pursuer.getUUID() + ", pursuerPos=" + pursuer.position()
                    + ", dispatchPos=" + state.lastInvestigationPos
                    + ", arrowStart=" + start + ", arrowEnd=" + end);
            return;
        }
        helper.succeed();
    }


}















