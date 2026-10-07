package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class LivingWallEvidenceFlowTest {
    @Test
    void constructionRequestCarriesIdentityAndBuckets() throws Exception {
        Class<?> requestClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionRequest");
        Constructor<?> ctor = requestClass.getDeclaredConstructor(Vec3.class, BlockPos.class, BlockPos.class, BlockPos.class, BlockPos.class,
                WallProfileScanner.TargetEvidence.class, long.class, long.class, long.class);
        ctor.setAccessible(true);
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(11L, 12L),
                List.of(new BlockPos(5, 64, 4), new BlockPos(3, 64, 4)),
                new BlockPos(6, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8),
                "WALL_LEDGE",
                3.0D,
                null);
        Object request = ctor.newInstance(new Vec3(1.5D, 64.0D, 1.5D), new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8), new BlockPos(4, 64, 4), new BlockPos(2, 16, 2), evidence, 123L, 77L, 200L);
        Method revision = requestClass.getDeclaredMethod("revision");
        Method expiresAt = requestClass.getDeclaredMethod("expiresAt");
        Method scanExpiresAt = requestClass.getDeclaredMethod("scanExpiresAt");
        Method selectedAnchor = requestClass.getDeclaredMethod("selectedAnchor");
        Method threatBucket = requestClass.getDeclaredMethod("threatBucket");
        Method evidenceAccessor = requestClass.getDeclaredMethod("evidence");
        revision.setAccessible(true);
        expiresAt.setAccessible(true);
        scanExpiresAt.setAccessible(true);
        selectedAnchor.setAccessible(true);
        threatBucket.setAccessible(true);
        evidenceAccessor.setAccessible(true);
        assertEquals(200L, revision.invoke(request));
        assertEquals(123L, expiresAt.invoke(request));
        assertEquals(77L, scanExpiresAt.invoke(request));
        assertEquals(new BlockPos(4, 64, 4), selectedAnchor.invoke(request));
        assertEquals(new BlockPos(2, 16, 2), threatBucket.invoke(request));
        assertEquals(evidence, evidenceAccessor.invoke(request));
    }

    @Test
    void canonicalCorridorSeparatesTacticalGoalFromBridgeLandingAndBuildPivot() throws Exception {
        Class<?> corridorClass = Class.forName("com.example.soundattract.ai.CanonicalFeetCorridor");
        Constructor<?> ctor = corridorClass.getDeclaredConstructor(
                net.minecraft.resources.ResourceLocation.class, String.class, BlockPos.class, BlockPos.class,
                BlockPos.class, BlockPos.class, BlockPos.class, BlockPos.class, List.class, List.class, List.class);
        ctor.setAccessible(true);
        Object corridor = ctor.newInstance(new net.minecraft.resources.ResourceLocation("minecraft", "overworld"),
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE.name(),
                new BlockPos(8, 64, 8), new BlockPos(3, 64, 8), new BlockPos(4, 64, 8),
                new BlockPos(10, 66, 8), new BlockPos(11, 66, 8), new BlockPos(9, 65, 8),
                List.of(new BlockPos(5, 64, 8)), List.of(WallProfileScanner.CollisionKind.LAVA), List.of(17L));
        Method buildPivot = corridorClass.getDeclaredMethod("buildPivot");
        Method tacticalGoal = corridorClass.getDeclaredMethod("tacticalGoal");
        Method landingFeet = corridorClass.getDeclaredMethod("landingFeet");
        Method egressFeet = corridorClass.getDeclaredMethod("egressFeet");
        buildPivot.setAccessible(true);
        tacticalGoal.setAccessible(true);
        landingFeet.setAccessible(true);
        egressFeet.setAccessible(true);
        assertEquals(new BlockPos(8, 64, 8), buildPivot.invoke(corridor));
        assertEquals(new BlockPos(4, 64, 8), tacticalGoal.invoke(corridor));
        assertEquals(new BlockPos(10, 66, 8), landingFeet.invoke(corridor));
        assertEquals(new BlockPos(11, 66, 8), egressFeet.invoke(corridor));
    }

    @Test
    void canonicalCorridorAllowsTacticalGoalAsTerminalEgress() throws Exception {
        Class<?> corridorClass = Class.forName("com.example.soundattract.ai.CanonicalFeetCorridor");
        Method maybeFromEvidence = corridorClass.getDeclaredMethod("maybeFromEvidence",
                net.minecraft.resources.ResourceLocation.class, BlockPos.class,
                Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionScenario"),
                WallProfileScanner.TargetEvidence.class);
        maybeFromEvidence.setAccessible(true);
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(11L),
                List.of(new BlockPos(5, 64, 4)),
                new BlockPos(6, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8),
                "WALL_LEDGE",
                1.0D,
                null);
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> maybeFromEvidence.invoke(null,
                new net.minecraft.resources.ResourceLocation("minecraft", "overworld"), new BlockPos(8, 64, 8),
                Enum.valueOf((Class<Enum>) Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionScenario").asSubclass(Enum.class), "WALL_LEDGE"), evidence));
    }

    @Test
    void immutableSnapshotHandoffPreservesEvidenceAndFingerprint() throws Exception {
        Class<?> snapshotClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$TargetScanSnapshot");
        Class<?> requestClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionRequest");
        Class<?> fingerClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$CellFingerprint");
        Constructor<?> fingerCtor = fingerClass.getDeclaredConstructor(BlockPos.class, String.class, String.class);
        fingerCtor.setAccessible(true);
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(11L),
                List.of(new BlockPos(5, 64, 4)),
                new BlockPos(6, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8),
                "JUMP_BRIDGE",
                2.0D,
                null);
        Object snapshot = snapshotClass.getDeclaredConstructor(Vec3.class, Vec3.class, BlockPos.class,
                WallProfileScanner.TargetEvidence.class, long.class, long.class, long.class, double.class,
                double.class, double.class, Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionScenario"), List.class)
                .newInstance(new Vec3(1.5D, 64.0D, 1.5D), new Vec3(2.5D, 64.0D, 2.5D), new BlockPos(8, 64, 8),
                        evidence, 77L, 200L, 277L, 0.31D, 1.5D, 4.0D,
                        Enum.valueOf((Class<Enum>) Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionScenario").asSubclass(Enum.class), "JUMP_BRIDGE"),
                        List.of(fingerCtor.newInstance(new BlockPos(4, 64, 4), "anchor", "minecraft:stone:1:true")));
        Constructor<?> requestCtor = requestClass.getDeclaredConstructor(Vec3.class, BlockPos.class, BlockPos.class, BlockPos.class, BlockPos.class,
                snapshotClass, WallProfileScanner.TargetEvidence.class, long.class, long.class, long.class);
        requestCtor.setAccessible(true);
        Object request = requestCtor.newInstance(new Vec3(1.5D, 64.0D, 1.5D), new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8), new BlockPos(4, 64, 4), new BlockPos(2, 16, 2), snapshot, evidence, 123L, 77L, 200L);
        assertEquals(snapshot, requestClass.getDeclaredMethod("snapshot").invoke(request));
        assertTrue(snapshotClass.getDeclaredMethod("identity").invoke(snapshot) instanceof String);
    }

    @Test
    void targetScanSnapshotKeepsProvenanceAndFingerprintStable() throws Exception {
        Class<?> snapshotClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$TargetScanSnapshot");
        Class<?> fingerClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$CellFingerprint");
        Constructor<?> fingerCtor = fingerClass.getDeclaredConstructor(BlockPos.class, String.class, String.class);
        fingerCtor.setAccessible(true);
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(11L, 12L),
                List.of(new BlockPos(5, 64, 4), new BlockPos(6, 64, 4)),
                List.of(WallProfileScanner.CollisionKind.SOLID, WallProfileScanner.CollisionKind.LAVA),
                new BlockPos(4, 65, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8),
                "WALL_LEDGE",
                3.0D,
                null);
        Object snapshot = snapshotClass.getDeclaredConstructor(Vec3.class, Vec3.class, BlockPos.class,
                WallProfileScanner.TargetEvidence.class, long.class, long.class, long.class, double.class,
                double.class, double.class, Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionScenario"), List.class)
                .newInstance(new Vec3(1.5D, 64.0D, 1.5D), new Vec3(2.5D, 64.0D, 2.5D), new BlockPos(8, 64, 8),
                        evidence, 77L, 200L, 277L, 0.31D, 1.5D, 4.0D,
                        Enum.valueOf((Class<Enum>) Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionScenario").asSubclass(Enum.class), "WALL_LEDGE"),
                        List.of(fingerCtor.newInstance(new BlockPos(4, 64, 4), "anchor", "minecraft:stone:1:true"),
                                fingerCtor.newInstance(new BlockPos(5, 64, 4), "hazard", "minecraft:magma_block:1:false")));
        assertEquals(77L, snapshotClass.getDeclaredMethod("captureTick").invoke(snapshot));
        assertEquals(200L, snapshotClass.getDeclaredMethod("revision").invoke(snapshot));
        assertEquals(277L, snapshotClass.getDeclaredMethod("expiryTick").invoke(snapshot));
        assertTrue(((String) snapshotClass.getDeclaredMethod("identity").invoke(snapshot)).contains("277"));
    }

    @Test
    void expiredSnapshotRejectsNewAdmissionWithoutCollapsingExistingState() throws Exception {
        Class<?> snapshotClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$TargetScanSnapshot");
        Class<?> requestClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionRequest");
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(11L),
                List.of(new BlockPos(5, 64, 4)),
                new BlockPos(4, 65, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8),
                "WALL_LEDGE",
                3.0D,
                null);
        Object snapshot = snapshotClass.getDeclaredConstructor(Vec3.class, Vec3.class, BlockPos.class,
                WallProfileScanner.TargetEvidence.class, long.class, long.class, long.class, double.class,
                double.class, double.class, Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionScenario"), List.class)
                .newInstance(new Vec3(1.5D, 64.0D, 1.5D), new Vec3(2.5D, 64.0D, 2.5D), new BlockPos(8, 64, 8),
                        evidence, 10L, 20L, 30L, 0.31D, 1.5D, 4.0D,
                        Enum.valueOf((Class<Enum>) Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionScenario").asSubclass(Enum.class), "WALL_LEDGE"),
                        List.of());
        Constructor<?> requestCtor = requestClass.getDeclaredConstructor(Vec3.class, BlockPos.class, BlockPos.class, BlockPos.class, BlockPos.class,
                snapshotClass, WallProfileScanner.TargetEvidence.class, long.class, long.class, long.class);
        requestCtor.setAccessible(true);
        Object request = requestCtor.newInstance(new Vec3(1.5D, 64.0D, 1.5D), new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8), new BlockPos(4, 64, 4), new BlockPos(2, 16, 2), snapshot, evidence, 30L, 20L, 200L);
        Method helper = LivingWallCoordinator.class.getDeclaredMethod("snapshotAdmissible", ServerLevel.class, requestClass, long.class);
        helper.setAccessible(true);
        assertFalse((Boolean) helper.invoke(null, null, request, 25L));
    }

    @Test
    void cachedEvidenceSelectionUsesTheSelectedAnchorAndLeavesThreatIdentityUntouched() throws Exception {
        WallProfileScanner.TargetEvidence first = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(11L),
                List.of(new BlockPos(5, 64, 4)),
                new BlockPos(6, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8),
                "WALL_LEDGE",
                1.0D,
                null);
        WallProfileScanner.TargetEvidence second = new WallProfileScanner.TargetEvidence(
                new BlockPos(5, 64, 4),
                List.of(12L),
                List.of(new BlockPos(6, 64, 4)),
                new BlockPos(7, 64, 4),
                new BlockPos(5, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8),
                "WALL_LEDGE",
                1.0D,
                null);
        Method helper = LivingWallCoordinator.class.getDeclaredMethod("selectCachedTargetEvidence", List.class, BlockPos.class);
        helper.setAccessible(true);
        assertEquals(first, helper.invoke(null, List.of(first, second), new BlockPos(4, 64, 4)));
        assertEquals(second, helper.invoke(null, List.of(first, second), new BlockPos(5, 64, 4)));
        assertEquals(null, helper.invoke(null, List.of(first, second), new BlockPos(9, 64, 4)));
    }

    @Test
    void targetScanKeyDependsOnThreatButNotSelectedAnchorAndExpiredCacheRejects() throws Exception {
        Method keyMethod = LivingWallCoordinator.class.getDeclaredMethod("targetScanKey", Vec3.class, BlockPos.class, long.class);
        keyMethod.setAccessible(true);
        Object first = keyMethod.invoke(null, new Vec3(1.5D, 64.0D, 1.5D), new BlockPos(8, 64, 8), 42L);
        Object sameThreat = keyMethod.invoke(null, new Vec3(1.5D, 64.0D, 1.5D), new BlockPos(8, 64, 8), 42L);
        Object differentThreat = keyMethod.invoke(null, new Vec3(1.5D, 64.0D, 1.5D), new BlockPos(12, 64, 8), 42L);
        Object differentGeneration = keyMethod.invoke(null, new Vec3(1.5D, 64.0D, 1.5D), new BlockPos(8, 64, 8), 43L);
        assertEquals(first, sameThreat);
        assertFalse(first.equals(differentThreat));
        assertFalse(first.equals(differentGeneration));

        Class<?> cachedScanClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$CachedTargetScan");
        Constructor<?> scanCtor = cachedScanClass.getDeclaredConstructor(List.class, long.class, long.class, int.class, long.class, long.class);
        scanCtor.setAccessible(true);
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4), List.of(1L), List.of(new BlockPos(5, 64, 4)),
                new BlockPos(6, 64, 4), new BlockPos(4, 63, 4), new BlockPos(0, 64, 0), new BlockPos(8, 64, 0),
                "WALL_LEDGE", 1.0D, null);
        Object cached = scanCtor.newInstance(List.of(evidence), 12L, 50L, 0, 0L, 0L);
        Method select = LivingWallCoordinator.class.getDeclaredMethod("selectCachedTargetEvidence", List.class, BlockPos.class);
        select.setAccessible(true);
        assertEquals(evidence, select.invoke(null, List.of(evidence), new BlockPos(4, 64, 4)));
        assertEquals(null, select.invoke(null, List.of(evidence), new BlockPos(9, 64, 4)));
        var expiresField = cachedScanClass.getDeclaredField("expiresAt");
        expiresField.setAccessible(true);
        assertEquals(50L, expiresField.getLong(cached));
    }

    @Test
    void cachedScanSupportsBothAnchorsButRejectsWrongThreatAndExpiredLookup() throws Exception {
        WallProfileScanner.TargetEvidence anchorA = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(11L),
                List.of(new BlockPos(5, 64, 4)),
                new BlockPos(6, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8),
                "WALL_LEDGE",
                1.0D,
                null);
        WallProfileScanner.TargetEvidence anchorB = new WallProfileScanner.TargetEvidence(
                new BlockPos(5, 64, 4),
                List.of(12L),
                List.of(new BlockPos(6, 64, 4)),
                new BlockPos(7, 64, 4),
                new BlockPos(5, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8),
                "WALL_LEDGE",
                1.0D,
                null);
        Method lookup = LivingWallCoordinator.class.getDeclaredMethod("findClusterTargetEvidenceForTest",
                Vec3.class, BlockPos.class, long.class, List.class, long.class, long.class, BlockPos.class);
        lookup.setAccessible(true);
        Object anchorAResult = lookup.invoke(null, new Vec3(1.5D, 64.0D, 1.5D), new BlockPos(8, 64, 8), 42L,
                List.of(anchorA, anchorB), 200L, 100L, new BlockPos(4, 64, 4));
        Object anchorBResult = lookup.invoke(null, new Vec3(1.5D, 64.0D, 1.5D), new BlockPos(8, 64, 8), 42L,
                List.of(anchorA, anchorB), 200L, 100L, new BlockPos(5, 64, 4));
        assertEquals(anchorA, anchorAResult.getClass().getDeclaredMethod("evidence").invoke(anchorAResult));
        assertEquals(anchorB, anchorBResult.getClass().getDeclaredMethod("evidence").invoke(anchorBResult));
        assertNull(lookup.invoke(null, new Vec3(1.5D, 64.0D, 1.5D), new BlockPos(12, 64, 8), 42L,
                List.of(anchorA, anchorB), 200L, 100L, new BlockPos(4, 64, 4)));
        assertNull(lookup.invoke(null, new Vec3(1.5D, 64.0D, 1.5D), new BlockPos(8, 64, 8), 42L,
                List.of(anchorA, anchorB), 100L, 100L, new BlockPos(4, 64, 4)));
    }

    @Test
    void constructionIntentKeepsTheBoundedRecruitmentWindow() throws Exception {
        Class<?> requestClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionRequest");
        Constructor<?> requestCtor = requestClass.getDeclaredConstructor(Vec3.class, BlockPos.class, BlockPos.class, BlockPos.class, BlockPos.class,
                WallProfileScanner.TargetEvidence.class, long.class, long.class, long.class);
        requestCtor.setAccessible(true);
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(1L),
                List.of(new BlockPos(5, 64, 4)),
                new BlockPos(6, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 0),
                "WALL_LEDGE",
                1.0D,
                null);
        Object request = requestCtor.newInstance(new Vec3(1.5D, 64.0D, 1.5D), new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8), new BlockPos(4, 64, 4), new BlockPos(2, 16, 2), evidence, 123L, 77L, 200L);
        Class<?> scenarioClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionScenario");
        Object wallLedge = Enum.valueOf((Class<Enum>) scenarioClass.asSubclass(Enum.class), "WALL_LEDGE");
        Class<?> siteKeyClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionSiteKey");
        Constructor<?> siteCtor = siteKeyClass.getDeclaredConstructor(long.class, BlockPos.class, BlockPos.class, BlockPos.class,
                BlockPos.class, BlockPos.class, scenarioClass, List.class);
        siteCtor.setAccessible(true);
        Object siteKey = siteCtor.newInstance(77L, new BlockPos(0, 64, 0), new BlockPos(8, 64, 8),
                new BlockPos(1, 64, 1), new BlockPos(4, 64, 4), new BlockPos(5, 64, 4), wallLedge, List.of(new BlockPos(5, 64, 4)));
        Class<?> intentClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionIntent");
        Constructor<?> intentCtor = intentClass.getDeclaredConstructor(UUID.class, requestClass, siteKeyClass, long.class);
        intentCtor.setAccessible(true);
        Object intent = intentCtor.newInstance(UUID.randomUUID(), request, siteKey, 100L);
        var deadlineField = intentClass.getDeclaredField("recruitmentDeadline");
        deadlineField.setAccessible(true);
        assertEquals(300L, deadlineField.getLong(intent));
    }

    @Test
    void dynamicRecruitmentFailuresNeverPoisonTerrain() throws Exception {
        Method poison = LivingWallCoordinator.class.getDeclaredMethod("shouldPoisonExactSiteCooldown", String.class);
        poison.setAccessible(true);
        assertFalse((Boolean) poison.invoke(null, "recruitment_timeout"));
        assertFalse((Boolean) poison.invoke(null, "mob_route_failed:to_assembly"));
        assertFalse((Boolean) poison.invoke(null, "mob_route_failed:to_feeder"));
        assertFalse((Boolean) poison.invoke(null, "intake_failed:timeout"));
        assertTrue((Boolean) poison.invoke(null, "plan_invalid"));
    }

    @Test
    void stableRouteNodesKeepTheProbeOnTheSourceSideOnly() throws Exception {
        Class<?> windowClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$IntakeWindow");
        Constructor<?> windowCtor = windowClass.getDeclaredConstructor(BlockPos.class, BlockPos.class, boolean.class);
        windowCtor.setAccessible(true);
        Object window = windowCtor.newInstance(new BlockPos(6, 64, 6), new BlockPos(5, 64, 6), false);
        Class<?> routeClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$RouteEvidence");
        Constructor<?> routeCtor = routeClass.getDeclaredConstructor(UUID.class, windowClass, BlockPos.class, List.class,
                boolean.class, long.class, String.class);
        routeCtor.setAccessible(true);
        Object routeEvidence = routeCtor.newInstance(UUID.randomUUID(), window, new BlockPos(20, 64, 20),
                List.of(new BlockPos(5, 64, 6)), true, 99L, "source-only");
        assertEquals(new BlockPos(20, 64, 20), routeClass.getDeclaredMethod("anchor").invoke(routeEvidence));
        assertEquals(List.of(new BlockPos(5, 64, 6)), routeClass.getDeclaredMethod("routeNodes").invoke(routeEvidence));
    }

    @Test
    void routeProgressIsBoundedToTheCertifiedPolylineAndOrderedForward() throws Exception {
        Method progress = LivingWallCoordinator.class.getDeclaredMethod("routeProgressIndex",
                Vec3.class, List.class, BlockPos.class, Vec3.class);
        progress.setAccessible(true);
        List<BlockPos> nodes = List.of(new BlockPos(0, 64, 0), new BlockPos(2, 64, 0), new BlockPos(4, 64, 0));
        Vec3 start = new Vec3(-0.5D, 64.0D, 0.5D);
        BlockPos endpoint = new BlockPos(5, 64, 0);
        int atStart = (int) progress.invoke(null, start, nodes, endpoint, start);
        int forward = (int) progress.invoke(null, new Vec3(1.5D, 64.0D, 0.5D), nodes, endpoint, start);
        int offRoute = (int) progress.invoke(null, new Vec3(1.5D, 64.0D, 4.5D), nodes, endpoint, start);

        assertTrue(atStart >= 0);
        assertTrue(forward > atStart);
        assertEquals(-1, offRoute);
    }

    @Test
    void constructionSiteKeyUsesTheActualPlanPivot() throws Exception {
        Class<?> requestClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionRequest");
        Constructor<?> requestCtor = requestClass.getDeclaredConstructor(Vec3.class, BlockPos.class, BlockPos.class, BlockPos.class, BlockPos.class,
                WallProfileScanner.TargetEvidence.class, long.class, long.class, long.class);
        requestCtor.setAccessible(true);
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(1L),
                List.of(new BlockPos(5, 64, 4)),
                new BlockPos(6, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 0),
                "WALL_LEDGE",
                1.0D,
                null);
        Object request = requestCtor.newInstance(new Vec3(1.5D, 64.0D, 1.5D), new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8), new BlockPos(4, 64, 4), new BlockPos(2, 16, 2), evidence, 123L, 77L, 200L);
        Class<?> scenarioClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionScenario");
        Object wallLedge = Enum.valueOf((Class<Enum>) scenarioClass.asSubclass(Enum.class), "WALL_LEDGE");
        Class<?> planClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$SitePlan");
        Constructor<?> planCtor = planClass.getDeclaredConstructor(scenarioClass, BlockPos.class, BlockPos.class, BlockPos.class,
                Vec3.class, List.class, List.class, List.class, java.util.Set.class, List.class);
        planCtor.setAccessible(true);
        Object plan = planCtor.newInstance(wallLedge, new BlockPos(11, 64, 11), new BlockPos(4, 64, 4), new BlockPos(5, 64, 4),
                new Vec3(1.0D, 0.0D, 0.0D), List.of(new BlockPos(11, 64, 11)), intakeWindows(new BlockPos(10, 64, 11)),
                List.of(new BlockPos(12, 64, 11)), java.util.Set.of(new BlockPos(12, 64, 11)),
                List.of(new BlockPos(6, 64, 4), new BlockPos(5, 64, 4)));
        var helper = LivingWallCoordinator.class.getDeclaredMethod("constructionSiteKey", requestClass, BlockPos.class, planClass);
        helper.setAccessible(true);
        Object siteKey = helper.invoke(null, request, new BlockPos(11, 64, 11), plan);
        var pivotField = siteKey.getClass().getDeclaredField("pivot");
        pivotField.setAccessible(true);
        assertEquals(new BlockPos(11, 64, 11), pivotField.get(siteKey));
    }

    @Test
    void formationIdentityCarriesEvidenceAndCommittedGeometry() throws Exception {
        Class<?> requestClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionRequest");
        Constructor<?> requestCtor = requestClass.getDeclaredConstructor(Vec3.class, BlockPos.class, BlockPos.class, BlockPos.class, BlockPos.class,
                WallProfileScanner.TargetEvidence.class, long.class, long.class, long.class);
        requestCtor.setAccessible(true);
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(1L),
                List.of(new BlockPos(3, 64, 4)),
                new BlockPos(5, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 0),
                "WALL_LEDGE",
                1.0D,
                null);
        Object request = requestCtor.newInstance(new Vec3(1.5D, 64.0D, 1.5D), new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8), new BlockPos(4, 64, 4), new BlockPos(2, 16, 2), evidence, 123L, 77L, 200L);
        Class<?> scenarioClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionScenario");
        Object wallLedge = Enum.valueOf((Class<Enum>) scenarioClass.asSubclass(Enum.class), "WALL_LEDGE");
        Class<?> planClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$SitePlan");
        Constructor<?> planCtor = planClass.getDeclaredConstructor(scenarioClass, BlockPos.class, BlockPos.class, BlockPos.class,
                Vec3.class, List.class, List.class, List.class, java.util.Set.class, List.class);
        planCtor.setAccessible(true);
        Object planA = planCtor.newInstance(wallLedge, new BlockPos(11, 64, 11), new BlockPos(4, 64, 4), new BlockPos(5, 64, 4),
                new Vec3(1.0D, 0.0D, 0.0D), List.of(new BlockPos(11, 64, 11)), intakeWindows(new BlockPos(10, 64, 11)),
                List.of(new BlockPos(12, 64, 11)), java.util.Set.of(new BlockPos(12, 64, 11)),
                List.of(new BlockPos(6, 64, 4), new BlockPos(5, 64, 4)));
        Object planB = planCtor.newInstance(wallLedge, new BlockPos(12, 64, 11), new BlockPos(4, 64, 4), new BlockPos(5, 64, 4),
                new Vec3(1.0D, 0.0D, 0.0D), List.of(new BlockPos(12, 64, 11)), intakeWindows(new BlockPos(10, 64, 11)),
                List.of(new BlockPos(12, 64, 11)), java.util.Set.of(new BlockPos(12, 64, 11)),
                List.of(new BlockPos(6, 64, 4), new BlockPos(5, 64, 4)));
        Method identity = LivingWallCoordinator.class.getDeclaredMethod("formationIdentity", requestClass, planClass);
        identity.setAccessible(true);
        String first = (String) identity.invoke(null, request, planA);
        String second = (String) identity.invoke(null, request, planB);
        assertFalse(first.equals(second));
        assertTrue(first.contains("WALL_LEDGE"));
        assertTrue(first.contains(Long.toString(new BlockPos(4, 64, 4).asLong())));
    }

    @Test
    void intentDeadlineDoesNotResetWhenTheSamePrePlanCandidateIsReused() throws Exception {
        Class<?> requestClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionRequest");
        Constructor<?> requestCtor = requestClass.getDeclaredConstructor(Vec3.class, BlockPos.class, BlockPos.class, BlockPos.class, BlockPos.class,
                WallProfileScanner.TargetEvidence.class, long.class, long.class, long.class);
        requestCtor.setAccessible(true);
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(1L),
                List.of(new BlockPos(5, 64, 4)),
                new BlockPos(6, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 0),
                "WALL_LEDGE",
                1.0D,
                null);
        Object request = requestCtor.newInstance(new Vec3(1.5D, 64.0D, 1.5D), new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8), new BlockPos(4, 64, 4), new BlockPos(2, 16, 2), evidence, 123L, 77L, 200L);
        Class<?> intentClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionIntent");
        Class<?> siteKeyClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionSiteKey");
        Constructor<?> intentCtor = intentClass.getDeclaredConstructor(UUID.class, requestClass, siteKeyClass, long.class);
        intentCtor.setAccessible(true);
        Object intent = intentCtor.newInstance(UUID.randomUUID(), request, null, 100L);
        var deadlineField = intentClass.getDeclaredField("recruitmentDeadline");
        deadlineField.setAccessible(true);
        assertEquals(300L, deadlineField.getLong(intent));
    }

    @Test
    void constructionIntentDeadlineRemainsIndependentOfShortScanExpiry() throws Exception {
        Class<?> requestClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionRequest");
        Constructor<?> requestCtor = requestClass.getDeclaredConstructor(Vec3.class, BlockPos.class, BlockPos.class, BlockPos.class, BlockPos.class,
                WallProfileScanner.TargetEvidence.class, long.class, long.class, long.class);
        requestCtor.setAccessible(true);
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(1L),
                List.of(new BlockPos(5, 64, 4)),
                new BlockPos(6, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 0),
                "WALL_LEDGE",
                1.0D,
                null);
        Object request = requestCtor.newInstance(new Vec3(1.5D, 64.0D, 1.5D), new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8), new BlockPos(4, 64, 4), new BlockPos(2, 16, 2), evidence, 123L, 1L, 200L);
        Class<?> intentClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionIntent");
        Class<?> siteKeyClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionSiteKey");
        Constructor<?> intentCtor = intentClass.getDeclaredConstructor(UUID.class, requestClass, siteKeyClass, long.class);
        intentCtor.setAccessible(true);
        Object intent = intentCtor.newInstance(UUID.randomUUID(), request, null, 100L);
        var deadlineField = intentClass.getDeclaredField("recruitmentDeadline");
        deadlineField.setAccessible(true);
        assertEquals(300L, deadlineField.getLong(intent));
        var scanExpiresAt = requestClass.getDeclaredMethod("scanExpiresAt");
        scanExpiresAt.setAccessible(true);
        assertEquals(1L, scanExpiresAt.invoke(request));
    }

    @Test
    void requestBuildabilityAllowsExpiredScanWhenIntentIsStillAlive() throws Exception {
        Class<?> requestClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionRequest");
        Constructor<?> requestCtor = requestClass.getDeclaredConstructor(Vec3.class, BlockPos.class, BlockPos.class, BlockPos.class, BlockPos.class,
                WallProfileScanner.TargetEvidence.class, long.class, long.class, long.class);
        requestCtor.setAccessible(true);
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(1L),
                List.of(new BlockPos(5, 64, 4)),
                new BlockPos(6, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 0),
                "WALL_LEDGE",
                1.0D,
                null);
        Object request = requestCtor.newInstance(new Vec3(1.5D, 64.0D, 1.5D), new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8), new BlockPos(4, 64, 4), new BlockPos(2, 16, 2), evidence, 123L, 100L, 200L);
        Class<?> intentClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionIntent");
        Class<?> siteKeyClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionSiteKey");
        Constructor<?> intentCtor = intentClass.getDeclaredConstructor(UUID.class, requestClass, siteKeyClass, long.class);
        intentCtor.setAccessible(true);
        Object liveIntent = intentCtor.newInstance(UUID.randomUUID(), request, null, 100L);
        Object deadIntent = intentCtor.newInstance(UUID.randomUUID(), request, null, -200L);
        var helper = LivingWallCoordinator.class.getDeclaredMethod("isRequestBuildable", requestClass, intentClass, long.class);
        helper.setAccessible(true);
        assertTrue((Boolean) helper.invoke(null, request, liveIntent, 101L));
        assertFalse((Boolean) helper.invoke(null, request, deadIntent, 101L));
        assertFalse((Boolean) helper.invoke(null, null, liveIntent, 101L));
    }

    @Test
    void legacyRallyPhasesAndGoalAreAbsentFromPreconstruction() throws Exception {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.example.soundattract.ai.BridgeRallyGoal"));
        Class<?> phaseClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$Phase");
        List<String> names = java.util.Arrays.stream(phaseClass.getEnumConstants()).map(Object::toString).toList();
        assertFalse(names.contains("VALIDATE_RALLY"));
        assertFalse(names.contains("RALLY"));
    }

    @Test
    void constructionIntentCleanupHelperRemovesPlannerOwnership() throws Exception {
        Class<?> stateClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$PerLevelState");
        var stateCtor = stateClass.getDeclaredConstructor();
        stateCtor.setAccessible(true);
        Object state = stateCtor.newInstance();
        var intentsField = stateClass.getDeclaredField("constructionIntents");
        intentsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<java.util.UUID, Object> intents = (java.util.Map<java.util.UUID, Object>) intentsField.get(state);
        java.util.UUID plannerId = java.util.UUID.randomUUID();
        intents.put(plannerId, new Object());
        java.lang.reflect.Method clear = LivingWallCoordinator.class.getDeclaredMethod("clearConstructionIntent",
                stateClass, java.util.UUID.class);
        clear.setAccessible(true);
        clear.invoke(null, state, plannerId);
        assertFalse(intents.containsKey(plannerId));
    }

    @Test
    void constructionIntentNoLongerOwnsPreconstructionWorkers() throws Exception {
        Class<?> stateClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$PerLevelState");
        var stateCtor = stateClass.getDeclaredConstructor();
        stateCtor.setAccessible(true);
        Object state = stateCtor.newInstance();
        Class<?> requestClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionRequest");
        Class<?> siteKeyClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionSiteKey");
        Class<?> intentClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionIntent");
        Constructor<?> intentCtor = intentClass.getDeclaredConstructor(UUID.class, requestClass, siteKeyClass, long.class);
        intentCtor.setAccessible(true);

        UUID plannerId = UUID.randomUUID();
        Object intent = intentCtor.newInstance(plannerId, null, null, 1L);
        assertThrows(NoSuchFieldException.class, () -> intentClass.getDeclaredField("reservedWorkers"));

        var intentsField = stateClass.getDeclaredField("constructionIntents");
        intentsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Object> intents = (Map<UUID, Object>) intentsField.get(state);
        intents.put(plannerId, intent);

        Method clear = LivingWallCoordinator.class.getDeclaredMethod("clearConstructionIntent", stateClass, intentClass);
        clear.setAccessible(true);
        clear.invoke(null, state, intent);

        assertFalse(intents.containsKey(plannerId));
    }

    @Test
    void staleIntentTokenCannotClearItsSuccessorAndQueueDeduplicates() throws Exception {
        Class<?> stateClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$PerLevelState");
        var stateCtor = stateClass.getDeclaredConstructor();
        stateCtor.setAccessible(true);
        Object state = stateCtor.newInstance();
        Class<?> requestClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionRequest");
        Class<?> siteKeyClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionSiteKey");
        Class<?> intentClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionIntent");
        Constructor<?> intentCtor = intentClass.getDeclaredConstructor(UUID.class, requestClass, siteKeyClass, long.class);
        intentCtor.setAccessible(true);
        UUID plannerId = UUID.randomUUID();
        Object stale = intentCtor.newInstance(plannerId, null, null, 1L);
        Object successor = intentCtor.newInstance(plannerId, null, null, 2L);
        var intentsField = stateClass.getDeclaredField("constructionIntents");
        intentsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<UUID, Object> intents = (java.util.Map<UUID, Object>) intentsField.get(state);
        intents.put(plannerId, successor);
        java.lang.reflect.Method clear = LivingWallCoordinator.class.getDeclaredMethod("clearConstructionIntent", stateClass, intentClass);
        clear.setAccessible(true);
        clear.invoke(null, state, stale);
        assertEquals(successor, intents.get(plannerId));
        java.lang.reflect.Method enqueue = LivingWallCoordinator.class.getDeclaredMethod("enqueueFormation", stateClass, UUID.class);
        enqueue.setAccessible(true);
        UUID formationId = UUID.randomUUID();
        enqueue.invoke(null, state, formationId);
        enqueue.invoke(null, state, formationId);
        var serviceRing = stateClass.getDeclaredField("serviceRing");
        serviceRing.setAccessible(true);
        assertEquals(1, ((java.util.ArrayDeque<?>) serviceRing.get(state)).size());
    }

    @Test
    void rawHazardOrderStaysInConstructionEvidenceAndSiteKeysUseCanonicalCopies() throws Exception {
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(1L),
                List.of(new BlockPos(5, 64, 4), new BlockPos(3, 64, 4)),
                new BlockPos(6, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 0),
                "WALL_LEDGE",
                1.0D,
                null);
        assertEquals(List.of(new BlockPos(5, 64, 4), new BlockPos(3, 64, 4)), evidence.hazardCells());
        Method hazardsForPlan = LivingWallCoordinator.class.getDeclaredMethod("constructionHazardsForPlan", List.class, BlockPos.class, BlockPos.class);
        hazardsForPlan.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<BlockPos> planHazards = (List<BlockPos>) hazardsForPlan.invoke(null,
                List.of(new BlockPos(5, 64, 4), new BlockPos(3, 64, 4)),
                new BlockPos(1, 64, 1), new BlockPos(4, 64, 1));
        assertEquals(List.of(new BlockPos(5, 64, 4), new BlockPos(3, 64, 4)), planHazards);
        Class<?> scenarioClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionScenario");
        Object wallLedge = Enum.valueOf((Class<Enum>) scenarioClass.asSubclass(Enum.class), "WALL_LEDGE");
        Method exactSiteKey = LivingWallCoordinator.class.getDeclaredMethod("exactSiteKey",
                BlockPos.class, BlockPos.class, BlockPos.class, scenarioClass, List.class);
        exactSiteKey.setAccessible(true);
        Object canonicalA = exactSiteKey.invoke(null, new BlockPos(1, 64, 1), new BlockPos(4, 64, 1),
                new BlockPos(5, 64, 1), wallLedge, List.of(new BlockPos(7, 64, 1), new BlockPos(6, 64, 1)));
        Object canonicalB = exactSiteKey.invoke(null, new BlockPos(1, 64, 1), new BlockPos(4, 64, 1),
                new BlockPos(5, 64, 1), wallLedge, List.of(new BlockPos(6, 64, 1), new BlockPos(7, 64, 1)));
        assertFalse(canonicalA.equals(canonicalB));
    }

    @Test
    void profileCacheAndFormationIdentityUseProductionHelpers() throws Exception {
        Method cacheUsable = LivingWallCoordinator.class.getDeclaredMethod("profileCacheUsable",
                long.class, long.class, long.class, long.class, long.class, long.class, long.class);
        cacheUsable.setAccessible(true);
        assertTrue((Boolean) cacheUsable.invoke(null, 120L, 130L, 100L, 7L, 7L, 11L, 11L));
        assertFalse((Boolean) cacheUsable.invoke(null, 90L, 130L, 100L, 7L, 7L, 11L, 11L));
        assertFalse((Boolean) cacheUsable.invoke(null, 120L, 130L, 100L, 7L, 8L, 11L, 11L));
        assertFalse((Boolean) cacheUsable.invoke(null, 120L, 130L, 100L, 7L, 7L, 11L, 12L));

        Class<?> requestClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionRequest");
        Constructor<?> requestCtor = requestClass.getDeclaredConstructor(Vec3.class, BlockPos.class, BlockPos.class, BlockPos.class, BlockPos.class,
                WallProfileScanner.TargetEvidence.class, long.class, long.class, long.class);
        requestCtor.setAccessible(true);
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4), List.of(1L), List.of(new BlockPos(5, 64, 4)),
                new BlockPos(6, 64, 4), new BlockPos(4, 63, 4), new BlockPos(0, 64, 0), new BlockPos(8, 64, 0),
                "WALL_LEDGE", 1.0D, null);
        Object request = requestCtor.newInstance(new Vec3(1.5D, 64.0D, 1.5D), new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8), new BlockPos(4, 64, 4), new BlockPos(2, 16, 2), evidence, 123L, 77L, 200L);
        Method formationIdentity = LivingWallCoordinator.class.getDeclaredMethod("formationIdentity", requestClass,
                Class.forName("com.example.soundattract.ai.LivingWallCoordinator$SitePlan"));
        formationIdentity.setAccessible(true);
        Class<?> scenarioClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionScenario");
        Object wallLedge = Enum.valueOf((Class<Enum>) scenarioClass.asSubclass(Enum.class), "WALL_LEDGE");
        Class<?> planClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$SitePlan");
        Constructor<?> planCtor = planClass.getDeclaredConstructor(scenarioClass, BlockPos.class, BlockPos.class, BlockPos.class,
                Vec3.class, List.class, List.class, List.class, java.util.Set.class, List.class);
        planCtor.setAccessible(true);
        Object firstPlan = planCtor.newInstance(wallLedge, new BlockPos(1, 64, 1), new BlockPos(4, 64, 1),
                new BlockPos(5, 64, 1), new Vec3(1.0D, 0.0D, 0.0D), List.of(new BlockPos(1, 64, 1)),
                intakeWindows(new BlockPos(0, 64, 1)), List.of(new BlockPos(1, 64, 1)), java.util.Set.of(new BlockPos(2, 64, 1)),
                List.of(new BlockPos(7, 64, 1), new BlockPos(6, 64, 1)));
        Object secondPlan = planCtor.newInstance(wallLedge, new BlockPos(1, 64, 1), new BlockPos(4, 64, 1),
                new BlockPos(6, 64, 1), new Vec3(1.0D, 0.0D, 0.0D), List.of(new BlockPos(1, 64, 1)),
                intakeWindows(new BlockPos(0, 64, 1)), List.of(new BlockPos(1, 64, 1)), java.util.Set.of(new BlockPos(2, 64, 1)),
                List.of(new BlockPos(7, 64, 1), new BlockPos(6, 64, 1)));
        String firstIdentity = (String) formationIdentity.invoke(null, request, firstPlan);
        String secondIdentity = (String) formationIdentity.invoke(null, request, secondPlan);
        assertTrue(firstIdentity.contains("77"));
        assertFalse(firstIdentity.equals(secondIdentity));
    }

    @Test
    void longDropHelperTreatsThirteenToSixteenAsLongDropAndRejectsOverMax() throws Exception {
        Method helper = LivingWallCoordinator.class.getDeclaredMethod("usesLongDropPlan", int.class, int.class, int.class, int.class);
        helper.setAccessible(true);
        assertTrue((Boolean) helper.invoke(null, 70, 57, 12, 16));
        assertTrue((Boolean) helper.invoke(null, 70, 56, 12, 16));
        assertTrue((Boolean) helper.invoke(null, 70, 55, 12, 16));
        assertTrue((Boolean) helper.invoke(null, 70, 54, 12, 16));
        assertFalse((Boolean) helper.invoke(null, 70, 53, 12, 16));
    }

    @Test
    void commitHelperPinsTheAcceptedSiteIdentityAndTerrainSignature() throws Exception {
        Class<?> requestClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionRequest");
        Constructor<?> requestCtor = requestClass.getDeclaredConstructor(Vec3.class, BlockPos.class, BlockPos.class, BlockPos.class, BlockPos.class,
                WallProfileScanner.TargetEvidence.class, long.class, long.class, long.class);
        requestCtor.setAccessible(true);
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(1L),
                List.of(new BlockPos(3, 64, 4)),
                new BlockPos(5, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 0),
                "WALL_LEDGE",
                1.0D,
                null);
        Object request = requestCtor.newInstance(new Vec3(1.5D, 64.0D, 1.5D), new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8), new BlockPos(4, 64, 4), new BlockPos(2, 16, 2), evidence, 123L, 77L, 200L);
        Class<?> scenarioClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionScenario");
        Object wallLedge = Enum.valueOf((Class<Enum>) scenarioClass.asSubclass(Enum.class), "WALL_LEDGE");
        Class<?> planClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$SitePlan");
        Constructor<?> planCtor = planClass.getDeclaredConstructor(scenarioClass, BlockPos.class, BlockPos.class, BlockPos.class,
                Vec3.class, List.class, List.class, List.class, java.util.Set.class, List.class);
        planCtor.setAccessible(true);
        Object plan = planCtor.newInstance(wallLedge, new BlockPos(11, 64, 11), new BlockPos(4, 64, 4), new BlockPos(5, 64, 4),
                new Vec3(1.0D, 0.0D, 0.0D), List.of(new BlockPos(11, 64, 11)), intakeWindows(new BlockPos(10, 64, 11)),
                List.of(new BlockPos(12, 64, 11)), java.util.Set.of(new BlockPos(12, 64, 11)),
                List.of(new BlockPos(6, 64, 4), new BlockPos(5, 64, 4)));
        Method constructionSiteKey = LivingWallCoordinator.class.getDeclaredMethod("constructionSiteKey", requestClass, BlockPos.class, planClass);
        constructionSiteKey.setAccessible(true);
        Object acceptedSiteKey = constructionSiteKey.invoke(null, request, new BlockPos(11, 64, 11), plan);
        Class<?> formationClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$Formation");
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Object formation = unsafeClass.getDeclaredMethod("allocateInstance", Class.class).invoke(unsafe, formationClass);
        var committedField = formationClass.getDeclaredField("committedSiteKey");
        committedField.setAccessible(true);
        committedField.set(formation, acceptedSiteKey);
        Method committedSiteKey = LivingWallCoordinator.class.getDeclaredMethod("committedSiteKey", formationClass);
        committedSiteKey.setAccessible(true);
        assertEquals(acceptedSiteKey, committedSiteKey.invoke(null, formation));
    }

    @Test
    void provisionalAndAcceptedSiteKeysShareTheSameCanonicalIdentityPath() throws Exception {
        Class<?> requestClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionRequest");
        Constructor<?> requestCtor = requestClass.getDeclaredConstructor(Vec3.class, BlockPos.class, BlockPos.class, BlockPos.class, BlockPos.class,
                WallProfileScanner.TargetEvidence.class, long.class, long.class, long.class);
        requestCtor.setAccessible(true);
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(1L),
                List.of(new BlockPos(3, 64, 4)),
                new BlockPos(5, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 0),
                "WALL_LEDGE",
                1.0D,
                null);
        Object requestV1 = requestCtor.newInstance(new Vec3(1.5D, 64.0D, 1.5D), new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8), new BlockPos(4, 64, 4), new BlockPos(2, 16, 2), evidence, 123L, 77L, 200L);
        Object requestV2 = requestCtor.newInstance(new Vec3(1.5D, 64.0D, 1.5D), new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8), new BlockPos(4, 64, 4), new BlockPos(2, 16, 2), evidence, 123L, 77L, 201L);
        Class<?> scenarioClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionScenario");
        Object wallLedge = Enum.valueOf((Class<Enum>) scenarioClass.asSubclass(Enum.class), "WALL_LEDGE");
        Class<?> planClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$SitePlan");
        Constructor<?> planCtor = planClass.getDeclaredConstructor(scenarioClass, BlockPos.class, BlockPos.class, BlockPos.class,
                Vec3.class, List.class, List.class, List.class, java.util.Set.class, List.class);
        planCtor.setAccessible(true);
        Object planA = planCtor.newInstance(wallLedge, new BlockPos(11, 64, 11), new BlockPos(4, 64, 4), new BlockPos(5, 64, 4),
                new Vec3(1.0D, 0.0D, 0.0D), List.of(new BlockPos(11, 64, 11)), intakeWindows(new BlockPos(10, 64, 11)),
                List.of(new BlockPos(12, 64, 11)), java.util.Set.of(new BlockPos(12, 64, 11)),
                List.of(new BlockPos(6, 64, 4), new BlockPos(5, 64, 4)));
        Object planB = planCtor.newInstance(wallLedge, new BlockPos(12, 64, 11), new BlockPos(4, 64, 4), new BlockPos(5, 64, 4),
                new Vec3(1.0D, 0.0D, 0.0D), List.of(new BlockPos(12, 64, 11)), intakeWindows(new BlockPos(10, 64, 11)),
                List.of(new BlockPos(12, 64, 11)), java.util.Set.of(new BlockPos(12, 64, 11)),
                List.of(new BlockPos(6, 64, 4), new BlockPos(5, 64, 4)));
        Method candidateKey = LivingWallCoordinator.class.getDeclaredMethod("candidateConstructionSiteKey",
                requestClass, BlockPos.class, planClass);
        candidateKey.setAccessible(true);
        Object acceptedA = candidateKey.invoke(null, requestV1, new BlockPos(11, 64, 11), planA);
        Object acceptedB = candidateKey.invoke(null, requestV1, new BlockPos(12, 64, 11), planB);
        Object differentRevision = candidateKey.invoke(null, requestV2, new BlockPos(11, 64, 11), planA);
        assertEquals(acceptedA, candidateKey.invoke(null, requestV1, new BlockPos(11, 64, 11), planA));
        assertFalse(acceptedA.equals(acceptedB));
        assertFalse(acceptedA.equals(differentRevision));
    }

    @Test
    void acceptedCommitWritesTheStableSiteIdentity() throws Exception {
        Class<?> requestClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionRequest");
        Constructor<?> requestCtor = requestClass.getDeclaredConstructor(Vec3.class, BlockPos.class, BlockPos.class, BlockPos.class, BlockPos.class,
                WallProfileScanner.TargetEvidence.class, long.class, long.class, long.class);
        requestCtor.setAccessible(true);
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(1L),
                List.of(new BlockPos(3, 64, 4)),
                new BlockPos(5, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 0),
                "WALL_LEDGE",
                1.0D,
                null);
        Object request = requestCtor.newInstance(new Vec3(1.5D, 64.0D, 1.5D), new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8), new BlockPos(4, 64, 4), new BlockPos(2, 16, 2), evidence, 123L, 77L, 200L);
        Class<?> scenarioClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionScenario");
        Object wallLedge = Enum.valueOf((Class<Enum>) scenarioClass.asSubclass(Enum.class), "WALL_LEDGE");
        Class<?> planClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$SitePlan");
        Constructor<?> planCtor = planClass.getDeclaredConstructor(scenarioClass, BlockPos.class, BlockPos.class, BlockPos.class,
                Vec3.class, List.class, List.class, List.class, java.util.Set.class, List.class);
        planCtor.setAccessible(true);
        Object plan = planCtor.newInstance(wallLedge, new BlockPos(11, 64, 11), new BlockPos(4, 64, 4), new BlockPos(5, 64, 4),
                new Vec3(1.0D, 0.0D, 0.0D), List.of(new BlockPos(11, 64, 11)), intakeWindows(new BlockPos(10, 64, 11)),
                List.of(new BlockPos(12, 64, 11)), java.util.Set.of(new BlockPos(12, 64, 11)),
                List.of(new BlockPos(6, 64, 4), new BlockPos(5, 64, 4)));
        Method acceptedKeyMethod = LivingWallCoordinator.class.getDeclaredMethod("constructionSiteKey", requestClass, BlockPos.class, planClass);
        acceptedKeyMethod.setAccessible(true);
        Object acceptedKey = acceptedKeyMethod.invoke(null, request, new BlockPos(11, 64, 11), plan);
        Class<?> intentClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionIntent");
        Constructor<?> intentCtor = intentClass.getDeclaredConstructor(UUID.class, requestClass, Class.forName("com.example.soundattract.ai.LivingWallCoordinator$ConstructionSiteKey"), long.class);
        intentCtor.setAccessible(true);
        Object intent = intentCtor.newInstance(UUID.randomUUID(), request, acceptedKey, 100L);
        Class<?> formationClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$Formation");
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Object formation = unsafeClass.getDeclaredMethod("allocateInstance", Class.class).invoke(unsafe, formationClass);
        for (String fieldName : List.of("plannerId", "request", "plan", "pivot", "anchor", "committedSiteKey", "terrainSignature", "intentToken")) {
            var field = formationClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            switch (fieldName) {
                case "plannerId" -> field.set(formation, UUID.randomUUID());
                case "request" -> field.set(formation, request);
                case "plan" -> field.set(formation, plan);
                case "pivot" -> field.set(formation, new BlockPos(11, 64, 11));
                case "anchor" -> field.set(formation, new BlockPos(4, 64, 4));
                case "committedSiteKey" -> field.set(formation, acceptedKey);
                case "terrainSignature" -> field.setLong(formation, -1L);
                case "intentToken" -> field.set(formation, intent);
                default -> { }
            }
        }
        var committedField = formationClass.getDeclaredField("committedSiteKey");
        committedField.setAccessible(true);
        assertEquals(acceptedKey, committedField.get(formation));
        Method committedSiteKey = LivingWallCoordinator.class.getDeclaredMethod("committedSiteKey", formationClass);
        committedSiteKey.setAccessible(true);
        assertEquals(acceptedKey, committedSiteKey.invoke(null, formation));
    }

    @Test
    void onlyTerrainFailuresPoisonTheExactSiteCooldown() throws Exception {
        Method helper = LivingWallCoordinator.class.getDeclaredMethod("shouldPoisonExactSiteCooldown", String.class);
        helper.setAccessible(true);
        assertTrue((Boolean) helper.invoke(null, "plan_invalid"));
        assertTrue((Boolean) helper.invoke(null, "anchor_invalid"));
        assertFalse((Boolean) helper.invoke(null, "candidate_builder_count_low"));
        assertFalse((Boolean) helper.invoke(null, "path_cooldown"));
        assertFalse((Boolean) helper.invoke(null, "rally_surface_unresolved"));
    }

    @Test
    void completeConstructionContractIsRequiredBeforePlanningOrCommit() throws Exception {
        Method helper = LivingWallCoordinator.class.getDeclaredMethod("hasCompleteConstructionContract",
                BlockPos.class, BlockPos.class, WallProfileScanner.TargetEvidence.class);
        helper.setAccessible(true);
        WallProfileScanner.TargetEvidence complete = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(1L),
                List.of(new BlockPos(3, 64, 4)),
                new BlockPos(5, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8),
                "WALL_LEDGE",
                1.0D,
                null);
        WallProfileScanner.TargetEvidence missingScenario = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(1L),
                List.of(new BlockPos(3, 64, 4)),
                new BlockPos(5, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8),
                null,
                1.0D,
                null);
        assertTrue((Boolean) helper.invoke(null, new BlockPos(4, 64, 4), new BlockPos(8, 64, 8), complete));
        assertFalse((Boolean) helper.invoke(null, new BlockPos(4, 64, 4), new BlockPos(8, 64, 8), missingScenario));
    }

    @Test
    void hazardAndWallEvidenceRejectsInsteadOfNormalizing() throws Exception {
        Method helper = LivingWallCoordinator.class.getDeclaredMethod("scenarioFromEvidence",
                WallProfileScanner.TargetEvidence.class, List.class);
        helper.setAccessible(true);
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                new BlockPos(4, 64, 4),
                List.of(1L),
                List.of(new BlockPos(5, 63, 4)),
                new BlockPos(5, 64, 4),
                new BlockPos(4, 63, 4),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 64, 8),
                "HAZARD_AND_WALL",
                1.0D,
                null);
        assertEquals(LivingWallCoordinator.ConstructionScenario.NONE,
                helper.invoke(null, evidence, List.of(new BlockPos(5, 63, 4))));
        assertEquals(LivingWallCoordinator.ConstructionScenario.NONE,
                helper.invoke(null, evidence, List.of()));
    }

    @Test
    void hazardCorridorWallLandingUsesTheCommittedProfileEvidence() throws Exception {
        Method contract = LivingWallCoordinator.class.getDeclaredMethod("hasCompleteConstructionContract",
                BlockPos.class, BlockPos.class, WallProfileScanner.TargetEvidence.class);
        contract.setAccessible(true);
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                new BlockPos(8, 67, 0),
                List.of(7L),
                List.of(new BlockPos(6, 64, 0)),
                List.of(WallProfileScanner.CollisionKind.LAVA),
                new BlockPos(8, 66, 1),
                new BlockPos(8, 66, 0),
                new BlockPos(0, 64, 0),
                new BlockPos(12, 64, 0),
                "WALL_LEDGE",
                1.0D,
                null,
                List.of(new BlockPos(8, 66, 1), new BlockPos(8, 66, 0)),
                new BlockPos(8, 66, 1),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 67, 0),
                new BlockPos(6, 67, 0));
        LivingWallCoordinator.SitePlan plan = new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                new BlockPos(0, 64, 0), new BlockPos(8, 67, 0), new BlockPos(6, 67, 0),
                new Vec3(1.0D, 0.0D, 0.0D), List.of(new BlockPos(0, 64, 0)),
                List.of(new LivingWallCoordinator.IntakeWindow(new BlockPos(-1, 64, 0))),
                List.of(new BlockPos(1, 64, 0)), Set.of(new BlockPos(0, 64, 0)),
                List.of(new BlockPos(6, 64, 0)));

        assertTrue((Boolean) contract.invoke(null, new BlockPos(0, 64, 0), new BlockPos(12, 64, 0), evidence));
        assertTrue(LivingWallCoordinator.committedCorridorMatchesEvidence(plan, evidence));
    }

    @Test
    void randomTerrainDropWithoutTargetSideEvidenceIsRejected() throws Exception {
        Method contract = LivingWallCoordinator.class.getDeclaredMethod("hasCompleteConstructionContract",
                BlockPos.class, BlockPos.class, WallProfileScanner.TargetEvidence.class);
        contract.setAccessible(true);
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                new BlockPos(8, 58, 0),
                List.of(9L),
                List.of(),
                List.of(),
                new BlockPos(8, 59, 1),
                new BlockPos(8, 57, 0),
                new BlockPos(0, 70, 0),
                new BlockPos(12, 58, 0),
                "DROP_TO_LEDGE",
                1.0D,
                null,
                List.of(new BlockPos(8, 59, 1), new BlockPos(8, 57, 0)),
                new BlockPos(8, 59, 1),
                new BlockPos(0, 70, 0),
                new BlockPos(8, 58, 0),
                null);

        assertFalse((Boolean) contract.invoke(null, new BlockPos(0, 70, 0), new BlockPos(12, 58, 0), evidence));
    }

    @Test
    void committedCorridorStaysValidWhenLiveThreatProgressMovesAway() {
        WallProfileScanner.TargetEvidence evidence = new WallProfileScanner.TargetEvidence(
                new BlockPos(8, 67, 0),
                List.of(7L),
                List.of(new BlockPos(6, 64, 0)),
                List.of(WallProfileScanner.CollisionKind.LAVA),
                new BlockPos(8, 66, 1),
                new BlockPos(8, 66, 0),
                new BlockPos(0, 64, 0),
                new BlockPos(12, 64, 0),
                "WALL_LEDGE",
                1.0D,
                null,
                List.of(new BlockPos(8, 66, 1), new BlockPos(8, 66, 0)),
                new BlockPos(8, 66, 1),
                new BlockPos(0, 64, 0),
                new BlockPos(8, 67, 0),
                new BlockPos(6, 67, 0));
        LivingWallCoordinator.SitePlan plan = new LivingWallCoordinator.SitePlan(
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE,
                new BlockPos(0, 64, 0), new BlockPos(8, 67, 0), new BlockPos(6, 67, 0),
                new Vec3(1.0D, 0.0D, 0.0D), List.of(new BlockPos(0, 64, 0)),
                List.of(new LivingWallCoordinator.IntakeWindow(new BlockPos(-1, 64, 0))),
                List.of(new BlockPos(1, 64, 0)), Set.of(new BlockPos(0, 64, 0)),
                List.of(new BlockPos(6, 64, 0)));

        assertFalse(LivingWallCoordinator.hasThreatProgressingSpan(
                LivingWallCoordinator.ConstructionScenario.WALL_LEDGE, plan.pivot(), plan.spanEnd(), new BlockPos(-8, 67, 0)));
        assertTrue(LivingWallCoordinator.committedCorridorMatchesEvidence(plan, evidence));
    }

    private static List<LivingWallCoordinator.IntakeWindow> intakeWindows(BlockPos... cells) {
        return java.util.Arrays.stream(cells).map(LivingWallCoordinator.IntakeWindow::new).toList();
    }
}
