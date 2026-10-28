package com.example.soundattract.gametest;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.ai.LivingWallCoordinator;
import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.ai.FollowScentGoal;
import com.example.soundattract.client.AlertAnimationFrames;
import com.example.soundattract.client.AwarenessClientState;
import com.example.soundattract.event.ArrowInvestigationEvents;
import com.example.soundattract.util.ArrowInvestigationHelper;
import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.network.AwarenessSnapshotMessage;
import com.example.soundattract.pathfinding.HazardClassifier;
import com.example.soundattract.pathfinding.FlowFieldManager;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import com.example.soundattract.scents.ScentSourceType;
import com.example.soundattract.tracking.SoundTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.world.entity.player.Player;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.List;
import java.util.UUID;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** In-world regression checks for gameplay systems that can be asserted headlessly. */
@GameTestHolder(SoundAttractMod.MOD_ID)
public final class CoreMechanicsGameTests {
    private static final String TEMPLATE = "core_mechanics";

    private CoreMechanicsGameTests() {}

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void alertAnimationUsesStableWrappingFrames(GameTestHelper helper) {
        for (int frame = 0; frame < 8; frame++) {
            long atFrame = frame * 1800L / 8;
            if (AlertAnimationFrames.frameIndex(atFrame, 1800L, 8) != frame) {
                helper.fail("Eye atlas selected the wrong frame at " + atFrame + "ms");
                return;
            }
        }
        if (AlertAnimationFrames.frameIndex(1800L, 1800L, 8) != 0
                || AlertAnimationFrames.frameIndex(-1L, 1800L, 8) != 7) {
            helper.fail("Eye atlas did not wrap cleanly at the animation boundary");
            return;
        }
        for (String texture : List.of("assets/soundattract/textures/mob_alert/detected.png",
                "assets/soundattract/textures/mob_alert/proximity.png")) {
            if (!eyeAtlasFramesStayCentered(texture)) {
                helper.fail("Eye atlas frames are not centered consistently: " + texture);
                return;
            }
        }
        for (String animation : List.of("detected", "proximity", "near_miss", "scent", "raid")) {
            if (!manualAnimationMetadataIsValid("assets/soundattract/textures/mob_alert/" + animation + ".png.mcmeta")) {
                helper.fail("Manually selected alert frames still have conflicting metadata: " + animation);
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
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

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void trackedProjectileNearMissFindsSoundPursuerWithoutNearbyPlayer(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 8, z), Blocks.STONE);
            }
        }

        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 9, 2));
        zombie.goalSelector.addGoal(0, new AttractionGoal(zombie, 1.0));
        AbstractArrow arrow = null;
        long now = helper.getLevel().getGameTime();
        for (int attempt = 0; attempt < 4; attempt++) {
            AbstractArrow candidate = (AbstractArrow) EntityType.ARROW.create(helper.getLevel());
            if (candidate == null) break;
            candidate.setNoGravity(true);
            BlockPos arrowStart = helper.absolutePos(new BlockPos(0, 10, 4));
            candidate.setPos(arrowStart.getX() + 0.5, arrowStart.getY(), arrowStart.getZ() + 0.5);
            candidate.setDeltaMovement(1.0, 0.0, 0.0);
            if (!helper.getLevel().addFreshEntity(candidate)
                    || !ArrowInvestigationEvents.ARROWS.containsKey(candidate.getId())) {
                helper.fail("The watched projectile was not registered by the server join event");
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
            String dimension = helper.getLevel().dimension().location().toString();
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

    private static boolean manualAnimationMetadataIsValid(String resource) {
        try (var input = CoreMechanicsGameTests.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) return false;
            String metadata = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return metadata.contains("\"texture\"") && !metadata.contains("\"animation\"");
        } catch (Exception exception) {
            return false;
        }
    }

    private static boolean eyeAtlasFramesStayCentered(String resource) {
        try (var input = CoreMechanicsGameTests.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) return false;
            BufferedImage image = ImageIO.read(input);
            if (image == null || image.getWidth() != 64 || image.getHeight() != 512) return false;
            for (int frame = 0; frame < 8; frame++) {
                double alphaTotal = 0.0;
                double xTotal = 0.0;
                double yTotal = 0.0;
                for (int y = 0; y < 64; y++) {
                    for (int x = 0; x < 64; x++) {
                        int alpha = image.getRGB(x, frame * 64 + y) >>> 24;
                        alphaTotal += alpha;
                        xTotal += x * alpha;
                        yTotal += y * alpha;
                    }
                }
                if (alphaTotal == 0.0) continue; // Proximity intentionally starts with an empty frame.
                double centerX = xTotal / alphaTotal;
                double centerY = yTotal / alphaTotal;
                if (Math.abs(centerX - 31.5) > 1.0 || Math.abs(centerY - 31.5) > 1.0) return false;
            }
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void awarenessAlertsSurviveSnapshotGapsAndRestartForNewEvents(GameTestHelper helper) {
        UUID mobId = UUID.randomUUID();
        try {
            AwarenessClientState.clear();
            AwarenessClientState.applyAt(new AwarenessSnapshotMessage(0, 0, 0, false,
                    List.of(new AwarenessSnapshotMessage.Alert(mobId, 42, 2, 0xFFFF3030, 1.0f))), 1_000L);
            AwarenessClientState.applyAt(new AwarenessSnapshotMessage(0, 0, 0, false, List.of()), 1_250L);
            AwarenessClientState.MobAlert retained = AwarenessClientState.alert(mobId);
            if (retained == null || retained.startedAt() != 1_000L) {
                helper.fail("A one-snapshot gap removed or restarted the eye animation");
                return;
            }

            AwarenessClientState.applyAt(new AwarenessSnapshotMessage(0, 0, 0, false,
                    List.of(new AwarenessSnapshotMessage.Alert(mobId, 42, 2, 0xFFFF3030, 1.0f))), 1_500L);
            if (AwarenessClientState.alert(mobId).startedAt() != 1_000L) {
                helper.fail("The same alert event restarted after a missing snapshot");
                return;
            }

            AwarenessClientState.applyAt(new AwarenessSnapshotMessage(0, 0, 0, false,
                    List.of(new AwarenessSnapshotMessage.Alert(mobId, 42, 3, 0xFFFFB000, 0.0f, 1L))), 1_600L);
            if (AwarenessClientState.alert(mobId).startedAt() != 1_600L) {
                helper.fail("A new near-miss event did not start its animation");
                return;
            }
            AwarenessClientState.applyAt(new AwarenessSnapshotMessage(0, 0, 0, false,
                    List.of(new AwarenessSnapshotMessage.Alert(mobId, 42, 3, 0xFFFFB000, 0.0f, 2L))), 1_700L);
            if (AwarenessClientState.alert(mobId).startedAt() != 1_700L) {
                helper.fail("A repeated near-miss event did not restart its animation");
                return;
            }

            AwarenessClientState.applyAt(new AwarenessSnapshotMessage(0, 0, 0, false, List.of()), 2_451L);
            if (AwarenessClientState.alert(mobId) != null) {
                helper.fail("An alert remained after the missing-snapshot grace period");
                return;
            }
            helper.succeed();
        } finally {
            AwarenessClientState.clear();
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void awarenessSnapshotCodecPreservesTriggerIdsAndReadsLegacyPackets(GameTestHelper helper) {
        ByteBuf encoded = Unpooled.buffer();
        ByteBuf legacy = Unpooled.buffer();
        try {
            UUID mobId = UUID.randomUUID();
            AwarenessSnapshotMessage message = new AwarenessSnapshotMessage(0.5f, 0.25f, 2, false,
                    List.of(new AwarenessSnapshotMessage.Alert(mobId, 42, 3, 0xFFFFB000, 0.0f, 1234L)));
            AwarenessSnapshotMessage.STREAM_CODEC.encode(encoded, message);
            AwarenessSnapshotMessage decoded = AwarenessSnapshotMessage.STREAM_CODEC.decode(encoded);
            if (decoded.alerts().size() != 1 || decoded.alerts().get(0).triggerId() != 1234L) {
                helper.fail("New awareness packet did not preserve the near-miss trigger ID");
                return;
            }

            legacy.writeFloat(0.5f);
            legacy.writeFloat(0.25f);
            legacy.writeByte(2);
            legacy.writeBoolean(false);
            legacy.writeByte(1); // one alert
            legacy.writeLong(mobId.getMostSignificantBits());
            legacy.writeLong(mobId.getLeastSignificantBits());
            legacy.writeByte(42); // entity ID varint
            legacy.writeByte(3); // alert type
            legacy.writeInt(0xFFFFB000);
            legacy.writeFloat(0.0f);
            AwarenessSnapshotMessage legacyDecoded = AwarenessSnapshotMessage.STREAM_CODEC.decode(legacy);
            if (legacyDecoded.alerts().size() != 1 || legacyDecoded.alerts().get(0).triggerId() != 0L) {
                helper.fail("Legacy awareness packet did not decode with the default trigger ID");
                return;
            }
            helper.succeed();
        } finally {
            encoded.release();
            legacy.release();
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void livingBridgeTiltOnlyAllowsItsLandingSupport(GameTestHelper helper) {
        BlockPos landingFeet = helper.absolutePos(new BlockPos(5, 10, 5));
        if (!LivingWallCoordinator.allowsTiltLandingSupportContact(landingFeet, landingFeet.below())) {
            helper.fail("The block directly supporting the final landing was rejected");
            return;
        }

        BlockPos wallFace = landingFeet.west();
        BlockPos ledgeAbove = landingFeet.above();
        BlockPos nearbyBlock = landingFeet.offset(1, -2, 1);
        if (LivingWallCoordinator.allowsTiltLandingSupportContact(landingFeet, wallFace)
                || LivingWallCoordinator.allowsTiltLandingSupportContact(landingFeet, ledgeAbove)
                || LivingWallCoordinator.allowsTiltLandingSupportContact(landingFeet, nearbyBlock)
                || LivingWallCoordinator.allowsTiltLandingSupportContact(null, landingFeet)
                || LivingWallCoordinator.allowsTiltLandingSupportContact(landingFeet, null)) {
            helper.fail("A wall, ledge, or nearby scanned obstacle was incorrectly exempted from tilt collision checks");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void zombieAttractionGoalAcquiresTrackedSound(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 8, z), Blocks.STONE);
            }
        }

        Mob zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 9, 2));
        BlockPos source = helper.absolutePos(new BlockPos(7, 9, 2));
        String dimension = helper.getLevel().dimension().location().toString();
        String soundId = "minecraft:entity.generic.explode#gametest_" + source.asLong();

        try {
            SoundTracker.addSound(null, source, dimension, 32.0, 8.0, 200, soundId);
            AttractionGoal goal = new AttractionGoal(zombie, 1.0);
            if (!goal.canUse()) {
                helper.fail("An eligible zombie did not acquire the nearby tracked sound");
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

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void trackedSoundExpiresAfterItsLifetime(GameTestHelper helper) {
        BlockPos source = helper.absolutePos(new BlockPos(3, 9, 3));
        String dimension = helper.getLevel().dimension().location().toString();
        String soundId = "minecraft:entity.generic.explode#expiry_" + source.asLong();

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

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void stealthDetectionUsesTheSuppliedRange(GameTestHelper helper) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 9, 2));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos playerPos = helper.absolutePos(new BlockPos(5, 9, 2));
        player.moveTo(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5, 180.0f, 0.0f);

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

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void latestPointBlankAttachmentApiMatchesIntegrationContract(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("pointblank")) {
            helper.fail("Point Blank is not present in the GameTest runtime");
            return;
        }
        try {
            ClassLoader loader = CoreMechanicsGameTests.class.getClassLoader();
            Class<?> attachments = loader.loadClass("com.vicmatskiv.pointblank.attachment.Attachments");
            Class<?> attachment = loader.loadClass("com.vicmatskiv.pointblank.attachment.Attachment");
            Class<?> category = loader.loadClass("com.vicmatskiv.pointblank.attachment.AttachmentCategory");
            attachments.getMethod("getAttachments", net.minecraft.world.item.ItemStack.class, boolean.class);
            attachment.getMethod("getCategory");
            category.getMethod("getName");
            category.getField("MUZZLE");
        } catch (ReflectiveOperationException exception) {
            helper.fail("Latest Point Blank attachment API no longer matches the integration reflection contract: "
                    + exception.getMessage());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void optionalIntegrationModsAreLoadedForCompatibilityRun(GameTestHelper helper) {
        if (!com.example.soundattract.config.DebugLoggingConfig.SPEC.isLoaded()) {
            helper.fail("Debug logging config spec was not registered and loaded for this runtime");
            return;
        }
        String[] expectedMods = {
                "coroutil", "enhancedai", "geckolib", "glitchcore", "hotbath",
                "immersive_melodies", "plasmovoice", "pointblank", "quantified",
                "insanelib", "smartbrainlib", "spoiled", "spore", "tacz", "tacznpcs",
                "toughasnails", "voicechat", "weather2"
        };
        java.util.List<String> missing = java.util.Arrays.stream(expectedMods)
                .filter(modId -> !net.neoforged.fml.ModList.get().isLoaded(modId))
                .toList();
        if (!missing.isEmpty()) {
            helper.fail("Optional integration jars are not loaded in GameTest runtime: " + missing);
            return;
        }

        try {
            ClassLoader loader = CoreMechanicsGameTests.class.getClassLoader();
            String[] integrationApiClasses = {
                    "insane96mcp.enhancedai.setup.EAIAttributes",
                    "com.crabmod.hotbath.custom_fluid.CustomFluidBlockEntity",
                    "immersive_melodies.item.InstrumentItem",
                    "su.plo.voice.api.addon.AddonInitializer",
                    "com.vicmatskiv.pointblank.attachment.Attachments",
                    "com.vicmatskiv.pointblank.attachment.Attachment",
                    "com.vicmatskiv.pointblank.attachment.AttachmentCategory",
                    "org.admany.quantified.api.QuantifiedAPI",
                    "net.tslat.smartbrainlib.api.core.SmartBrain",
                    "de.maxhenkel.voicechat.api.VoicechatPlugin",
                    "toughasnails.api.temperature.TemperatureHelper",
                    "com.Harbinger.Spore.core.SConfig",
                    "com.tacz.guns.api.event.common.GunShootEvent",
                    "weather2.weathersystem.wind.WindManager"
            };
            for (String apiClass : integrationApiClasses) {
                Class.forName(apiClass, false, loader);
            }
        } catch (ClassNotFoundException exception) {
            helper.fail("An optional integration API class is unavailable: " + exception.getMessage());
            return;
        }

        if (!com.example.soundattract.mixin.TaczMixinBootstrapStatus.isNpcPluginLoaded()) {
            helper.fail("TaCZ NPCs is loaded, but its compatibility Mixin plugin did not bootstrap");
            return;
        }
        if (!com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge.isAvailable()) {
            helper.fail("Quantified is loaded, but its optional compute/cache bridge did not resolve the 2.0 API");
            return;
        }

        try {
            Class<?> attachments = Class.forName("com.vicmatskiv.pointblank.attachment.Attachments");
            var attachmentMap = attachments.getMethod("getAttachments", net.minecraft.world.item.ItemStack.class, boolean.class)
                    .invoke(null, net.minecraft.world.item.ItemStack.EMPTY, true);
            if (!(attachmentMap instanceof java.util.Map<?, ?>)) {
                helper.fail("Point Blank attachment enumeration returned an unexpected value");
                return;
            }
        } catch (ReflectiveOperationException exception) {
            helper.fail("Point Blank attachment enumeration failed against the installed API: " + exception.getMessage());
            return;
        }

        var wind = com.example.soundattract.integration.weather2.Weather2WindCompat.sample(
                helper.getLevel(), helper.absolutePos(new BlockPos(3, 9, 3)), true);
        if (wind.direction() == null || !Double.isFinite(wind.speed()) || wind.speed() < 0.0
                || !Double.isFinite(wind.direction().x) || !Double.isFinite(wind.direction().z)) {
            helper.fail("Weather2 wind adapter returned an invalid sample");
            return;
        }
        if (!com.example.soundattract.integration.spoiled.SpoiledScentIntegration.isLoaded()
                || com.example.soundattract.integration.spoiled.SpoiledScentIntegration.spoilageProgress(
                        net.minecraft.world.item.ItemStack.EMPTY) != 0.0) {
            helper.fail("Spoiled integration did not safely handle an empty item stack");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void woolOcclusionReducesSoundReach(GameTestHelper helper) {
        BlockPos source = helper.absolutePos(new BlockPos(2, 10, 2));
        BlockPos listener = helper.absolutePos(new BlockPos(7, 10, 2));
        helper.setBlock(new BlockPos(4, 10, 2), Blocks.WHITE_WOOL);

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

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
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

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void eligibleZombieAcquiresNearbyScentTrail(GameTestHelper helper) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 9, 2));
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

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
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
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void flowFieldReturnsProgressTowardTarget(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 8, z), Blocks.STONE);
            }
        }

        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 9, 2));
        BlockPos targetBlock = zombie.blockPosition().offset(0, 0, 4);
        Vec3 target = Vec3.atBottomCenterOf(targetBlock);
        helper.runAfterDelay(1, () -> checkFlowFieldResult(helper, zombie, target));
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
}
