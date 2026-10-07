package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class LivingWallTacticalSnapshotTest {
    @Test
    void boundsStayInclusiveAndFailClosedOutsideTheBox() {
        LivingWallTacticalSnapshot.Bounds bounds = new LivingWallTacticalSnapshot.Bounds(
                new BlockPos(0, 60, 0), new BlockPos(15, 75, 15), 16, 16);
        assertTrue(LivingWallTacticalSnapshot.isWithinBounds(bounds, new BlockPos(0, 60, 0)));
        assertTrue(LivingWallTacticalSnapshot.isWithinBounds(bounds, new BlockPos(15, 75, 15)));
        assertFalse(LivingWallTacticalSnapshot.isWithinBounds(bounds, new BlockPos(16, 75, 15)));
    }

    @Test
    void loadedStateProjectionDistinguishesUnknownLoadedAndVoid() {
        assertEquals(LivingWallTacticalSnapshot.CellKind.UNLOADED_BOUNDARY,
                LivingWallTacticalSnapshot.projectLoadedState(false, true, false, false, false, false, false, false, false, false, false, false));
        assertEquals(LivingWallTacticalSnapshot.CellKind.UNKNOWN,
                LivingWallTacticalSnapshot.projectLoadedState(true, false, false, false, false, false, false, false, false, false, false, false));
        assertEquals(LivingWallTacticalSnapshot.CellKind.VOID_EXPOSED,
                LivingWallTacticalSnapshot.projectLoadedState(true, true, false, false, false, false, false, false, false, false, false, true));
    }

    @Test
    void cacheKeyIdentityChangesWithTerrainAndFormationInputs() {
        LivingWallTacticalSnapshot.Identity first = new LivingWallTacticalSnapshot.Identity(
                new ResourceLocation("soundattract", "test"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "a,b,c",
                "formation-a",
                10L,
                11L);
        LivingWallTacticalSnapshot.Identity second = new LivingWallTacticalSnapshot.Identity(
                new ResourceLocation("soundattract", "test"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "a,b,c",
                "formation-b",
                10L,
                11L);
        LivingWallTacticalSnapshot.CacheKey firstKey = new LivingWallTacticalSnapshot.CacheKey(first, 1L, 4, 16);
        LivingWallTacticalSnapshot.CacheKey secondKey = new LivingWallTacticalSnapshot.CacheKey(second, 1L, 4, 16);
        assertFalse(first.key().equals(second.key()));
        assertFalse(firstKey.equals(secondKey));
    }

    @Test
    void cellInvalidationRequiresMatchingKindRevisionAndFingerprint() {
        LivingWallTacticalSnapshot.Cell previous = new LivingWallTacticalSnapshot.Cell(new BlockPos(1, 64, 1),
                LivingWallTacticalSnapshot.CellKind.UNKNOWN, 10L, "unloaded");
        LivingWallTacticalSnapshot.Cell current = new LivingWallTacticalSnapshot.Cell(new BlockPos(1, 64, 1),
                LivingWallTacticalSnapshot.CellKind.VOID_EXPOSED, 10L, "unloaded");
        LivingWallTacticalSnapshot.Cell same = new LivingWallTacticalSnapshot.Cell(new BlockPos(1, 64, 1),
                LivingWallTacticalSnapshot.CellKind.UNKNOWN, 10L, "unloaded");
        assertTrue(LivingWallTacticalSnapshot.invalidates(previous, current));
        assertFalse(LivingWallTacticalSnapshot.invalidates(previous, same));
    }

    @Test
    void snapshotCountsStayBoundedAndImmutable() {
        LivingWallTacticalSnapshot.Tile tile = new LivingWallTacticalSnapshot.Tile(new BlockPos(0, 60, 0),
                List.of(new LivingWallTacticalSnapshot.Cell(new BlockPos(0, 60, 0),
                                LivingWallTacticalSnapshot.CellKind.UNKNOWN, 1L, "a"),
                        new LivingWallTacticalSnapshot.Cell(new BlockPos(1, 60, 0),
                                LivingWallTacticalSnapshot.CellKind.UNLOADED_BOUNDARY, 1L, "b")),
                1L);
        LivingWallTacticalSnapshot.Snapshot snapshot = new LivingWallTacticalSnapshot.Snapshot(
                new LivingWallTacticalSnapshot.Bounds(new BlockPos(0, 60, 0), new BlockPos(15, 75, 15), 16, 16),
                new LivingWallTacticalSnapshot.Identity(new ResourceLocation("soundattract", "test"), null, null,
                        "r", "f", 22L, 23L),
                null,
                List.of(tile),
                1L,
                41L);
        assertEquals(1, snapshot.tileCount());
        assertEquals(2, snapshot.cellCount());
        assertFalse(snapshot.isExpired(40L));
        assertTrue(snapshot.isExpired(41L));
    }

    @Test
    void routeDecisionTreatsKnownHazardsAsBlocked() {
        LivingWallTacticalSnapshot.Snapshot snapshot = new LivingWallTacticalSnapshot.Snapshot(
                new LivingWallTacticalSnapshot.Bounds(new BlockPos(0, 60, 0), new BlockPos(15, 75, 15), 16, 16),
                new LivingWallTacticalSnapshot.Identity(new ResourceLocation("soundattract", "test"), null, null,
                        "r", "f", 22L, 23L),
                null,
                List.of(new LivingWallTacticalSnapshot.Tile(new BlockPos(0, 60, 0),
                        List.of(new LivingWallTacticalSnapshot.Cell(new BlockPos(1, 60, 1),
                                        LivingWallTacticalSnapshot.CellKind.SUPPORTED_AIR, 1L, "a"),
                                new LivingWallTacticalSnapshot.Cell(new BlockPos(2, 60, 1),
                                        LivingWallTacticalSnapshot.CellKind.VOID_EXPOSED, 1L, "b")),
                        1L)),
                1L,
                41L);
        assertEquals(LivingWallTacticalSnapshot.RouteDecision.REACHABLE,
                snapshot.routeDecision(List.of(new BlockPos(1, 60, 1)), true));
        assertEquals(LivingWallTacticalSnapshot.RouteDecision.BLOCKED,
                snapshot.routeDecision(List.of(new BlockPos(2, 60, 1)), true));
    }

    @Test
    void routeDecisionRequestsExpansionForUnknownOrUnloadedCells() {
        LivingWallTacticalSnapshot.Snapshot snapshot = new LivingWallTacticalSnapshot.Snapshot(
                new LivingWallTacticalSnapshot.Bounds(new BlockPos(0, 60, 0), new BlockPos(15, 75, 15), 16, 16),
                new LivingWallTacticalSnapshot.Identity(new ResourceLocation("soundattract", "test"), null, null,
                        "r", "f", 22L, 23L),
                null,
                List.of(new LivingWallTacticalSnapshot.Tile(new BlockPos(0, 60, 0),
                        List.of(new LivingWallTacticalSnapshot.Cell(new BlockPos(1, 60, 1),
                                LivingWallTacticalSnapshot.CellKind.UNKNOWN, 1L, "u")),
                        1L)),
                1L,
                41L);
        assertEquals(LivingWallTacticalSnapshot.RouteDecision.UNKNOWN,
                snapshot.routeDecision(List.of(new BlockPos(1, 60, 1)), true));
        assertEquals(LivingWallTacticalSnapshot.RouteDecision.UNKNOWN,
                snapshot.routeDecision(List.of(new BlockPos(1, 60, 1)), false));
    }

    @Test
    void cacheMutationIsolationRequiresIdentityAndRevisionChanges() {
        LivingWallTacticalSnapshot.Identity identity = new LivingWallTacticalSnapshot.Identity(
                new ResourceLocation("soundattract", "test"), UUID.randomUUID(), UUID.randomUUID(), "r1", "f1", 10L, 11L);
        LivingWallTacticalSnapshot.CacheKey first = new LivingWallTacticalSnapshot.CacheKey(identity, 1L, 4, 8);
        LivingWallTacticalSnapshot.CacheKey same = new LivingWallTacticalSnapshot.CacheKey(identity, 1L, 4, 8);
        LivingWallTacticalSnapshot.CacheKey revision = new LivingWallTacticalSnapshot.CacheKey(identity, 2L, 4, 8);
        LivingWallTacticalSnapshot.Identity other = new LivingWallTacticalSnapshot.Identity(
                new ResourceLocation("soundattract", "test"), UUID.randomUUID(), UUID.randomUUID(), "r2", "f2", 10L, 11L);
        LivingWallTacticalSnapshot.CacheKey otherIdentity = new LivingWallTacticalSnapshot.CacheKey(other, 1L, 4, 8);
        assertEquals(first, same);
        assertFalse(first.equals(revision));
        assertFalse(first.equals(otherIdentity));
    }

    @Test
    void completeLocalRouteProceedsWithinBoundedWindow() {
        LivingWallTacticalSnapshot.Snapshot snapshot = snapshotWithCell(
                new BlockPos(4, 64, 4), LivingWallTacticalSnapshot.CellKind.SUPPORTED_AIR);
        assertEquals(LivingWallTacticalSnapshot.ExpansionStatus.COMPLETE,
                snapshot.expansionStatus(List.of(new BlockPos(4, 64, 4)), LivingWallTacticalSnapshot.MAX_TILES));
    }

    @Test
    void unknownRouteRequestsExpansionWithoutFullWorldScan() {
        LivingWallTacticalSnapshot.Snapshot snapshot = snapshotWithCell(
                new BlockPos(4, 64, 4), LivingWallTacticalSnapshot.CellKind.UNKNOWN);
        assertEquals(LivingWallTacticalSnapshot.ExpansionStatus.EXPANDING,
                snapshot.expansionStatus(List.of(new BlockPos(4, 64, 4)), 1));
    }

    @Test
    void budgetExhaustionDefersInsteadOfAdmitting() {
        LivingWallTacticalSnapshot.Snapshot snapshot = snapshotWithCell(
                new BlockPos(4, 64, 4), LivingWallTacticalSnapshot.CellKind.UNKNOWN);
        assertEquals(LivingWallTacticalSnapshot.ExpansionStatus.UNKNOWN,
                snapshot.expansionStatus(List.of(new BlockPos(4, 64, 4)), 0));
    }

    @Test
    void unknownBoundaryOutsideWindowIsBlocked() {
        LivingWallTacticalSnapshot.Snapshot snapshot = new LivingWallTacticalSnapshot.Snapshot(
                new LivingWallTacticalSnapshot.Bounds(new BlockPos(0, 60, 0), new BlockPos(48, 75, 48), 24, 24),
                new LivingWallTacticalSnapshot.Identity(new ResourceLocation("soundattract", "test"), null, null,
                        "r", "f", 22L, 23L),
                null,
                List.of(new LivingWallTacticalSnapshot.Tile(new BlockPos(0, 60, 0),
                        List.of(new LivingWallTacticalSnapshot.Cell(new BlockPos(0, 60, 0),
                                LivingWallTacticalSnapshot.CellKind.UNKNOWN, 1L, "u")),
                        1L)),
                1L,
                41L);
        assertEquals(LivingWallTacticalSnapshot.ExpansionStatus.BLOCKED,
                snapshot.expansionStatus(List.of(new BlockPos(49, 60, 49)), LivingWallTacticalSnapshot.MAX_TILES));
    }

    private static LivingWallTacticalSnapshot.Snapshot snapshotWithCell(BlockPos pos, LivingWallTacticalSnapshot.CellKind kind) {
        return new LivingWallTacticalSnapshot.Snapshot(
                new LivingWallTacticalSnapshot.Bounds(new BlockPos(0, 60, 0), new BlockPos(48, 75, 48), 24, 24),
                new LivingWallTacticalSnapshot.Identity(new ResourceLocation("soundattract", "test"), null, null,
                        "r", "f", 22L, 23L),
                null,
                List.of(new LivingWallTacticalSnapshot.Tile(new BlockPos(0, 60, 0),
                        List.of(new LivingWallTacticalSnapshot.Cell(pos, kind, 1L, "u")),
                        1L)),
                1L,
                41L);
    }

    @Test
    void destinationEgressWitnessRoundTripsAndInvalidatesOnChange() {
        LivingWallTacticalSnapshot.DestinationEgressWitness witness = new LivingWallTacticalSnapshot.DestinationEgressWitness(
                new BlockPos(4, 64, 4), new BlockPos(5, 64, 4), "SUPPORTED", 11L, "fingerprint-a");
        LivingWallTacticalSnapshot.Snapshot snapshot = new LivingWallTacticalSnapshot.Snapshot(
                new LivingWallTacticalSnapshot.Bounds(new BlockPos(0, 60, 0), new BlockPos(48, 75, 48), 24, 24),
                new LivingWallTacticalSnapshot.Identity(new ResourceLocation("soundattract", "test"), null, null,
                        "r", "f", 22L, 23L),
                witness,
                List.of(),
                1L,
                41L);
        assertEquals(witness, snapshot.destinationEgressWitness());
        assertFalse(LivingWallTacticalSnapshot.witnessInvalidates(witness,
                new LivingWallTacticalSnapshot.DestinationEgressWitness(new BlockPos(4, 64, 4), new BlockPos(5, 64, 4), "SUPPORTED", 11L, "fingerprint-a")));
        assertTrue(LivingWallTacticalSnapshot.witnessInvalidates(witness,
                new LivingWallTacticalSnapshot.DestinationEgressWitness(new BlockPos(4, 64, 4), new BlockPos(6, 64, 4), "SUPPORTED", 11L, "fingerprint-a")));
    }
}
