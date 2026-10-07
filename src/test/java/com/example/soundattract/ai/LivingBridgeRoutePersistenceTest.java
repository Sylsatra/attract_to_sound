package com.example.soundattract.ai;

import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LivingBridgeRoutePersistenceTest {
    private static LivingBridgeRouteSnapshot snapshot(String dimension, long revision) {
        return new LivingBridgeRouteSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"), dimension,
                4L, UUID.fromString("00000000-0000-0000-0000-000000000004"), revision,
                "CONTROLLED_APPROACH", "RECOVERING", true, true, true,
                List.of(LivingBridgeRouteSnapshot.Point.from(-2.5D, 64.0D, 1.25D),
                        LivingBridgeRouteSnapshot.Point.from(-1.5D, 64.0D, 1.25D)),
                2048L, List.of(LivingBridgeRouteSnapshot.Point.from(-2.0D, 64.0D, 1.25D)),
                0, LivingBridgeRouteSnapshot.Point.from(-2.0D, 64.0D, 1.25D), -1, 3);
    }

    @Test
    void schemaV2SnapshotRoundTripsWithCanonicalDigestAndQuantizedCoordinates() {
        LivingBridgeRouteSnapshot source = snapshot("MINECRAFT:OVERWORLD", 17L);
        LivingBridgeRouteSnapshot decoded = LivingBridgeRouteSnapshot.fromTag(source.toTag());

        assertEquals("minecraft:overworld", decoded.dimension());
        assertTrue(source.exactlyEquals(decoded));
        assertEquals(source.digest(), decoded.digest());
        assertEquals(-10241L, LivingBridgeRouteSnapshot.Point.from(-2.5002D, 0.0D, 0.0D).x());
        assertEquals(10240L, LivingBridgeRouteSnapshot.Point.from(2.5D, 0.0D, 0.0D).x());
    }

    @Test
    void recoveryExceptionRequiresExactOwnerAndNeverClearsOrBypassesGlobalBarrier() {
        LivingBridgeRouteSnapshot owner = snapshot("minecraft:overworld", 17L);
        LivingBridgeRouteData scoped = LivingBridgeRouteData.load(new CompoundTag());
        scoped.putSnapshot(owner);
        scoped.quarantine("OPAQUE_V1_ENTITY", new CompoundTag(), null, owner.dimension());

        assertTrue(scoped.admissionBlocked(owner.dimension()));
        assertTrue(scoped.canRecoverOwner(owner));
        assertTrue(scoped.hasConflictingLane(owner.dimension(), owner.ownerId(), owner.reservationId(),
                List.of(net.minecraft.core.BlockPos.ZERO), 1.0D, 0.5D));
        assertFalse(scoped.canRecoverOwner(snapshot(owner.dimension(), owner.routeRevision() + 1L)));

        LivingBridgeRouteData global = LivingBridgeRouteData.load(new CompoundTag());
        global.quarantine("OPAQUE_AMBIGUOUS_ROOT", new CompoundTag(), null, null);
        global.putSnapshot(owner);
        assertTrue(global.admissionBlocked("minecraft:the_nether"));
        assertFalse(global.canRecoverOwner(owner));
        assertTrue(global.hasConflictingRecoveryLane(owner,
                List.of(net.minecraft.core.BlockPos.ZERO), 1.0D, 0.5D));
    }

    @Test
    void divergentValidSnapshotsQuarantineWithoutChoosingOrReleasing() {
        LivingBridgeRouteSnapshot saved = snapshot("minecraft:overworld", 17L);
        LivingBridgeRouteSnapshot same = snapshot("minecraft:overworld", 17L);
        LivingBridgeRouteSnapshot divergent = snapshot("minecraft:overworld", 18L);

        assertEquals(LivingBridgeRouteData.CopyAgreement.EXACT,
                LivingBridgeRouteData.compareCopies(saved, same));
        assertEquals(LivingBridgeRouteData.CopyAgreement.DIVERGENCE,
                LivingBridgeRouteData.compareCopies(saved, divergent));
        assertEquals(LivingBridgeRouteData.CopyAgreement.MISSING_SAVED_COPY,
                LivingBridgeRouteData.compareCopies(null, same));
    }

    @Test
    void journalRecognizesOnlyExactAdjacentSnapshotsAndRetainsUnionLane() {
        LivingBridgeRouteSnapshot from = snapshot("minecraft:overworld", 17L);
        LivingBridgeRouteSnapshot to = snapshot("minecraft:overworld", 18L);
        CompoundTag journal = new CompoundTag();
        journal.putUUID("reservationId", to.reservationId());
        journal.putUUID("transactionId", to.transactionId());
        journal.putLong("commitGeneration", to.commitGeneration());
        journal.putBoolean("hasFrom", true);
        journal.put("from", from.toTag());
        journal.putString("fromDigest", from.digest());
        journal.put("to", to.toTag());
        journal.putString("toDigest", to.digest());

        assertEquals(LivingBridgeRouteData.CopyAgreement.JOURNALED_ADJACENT,
                LivingBridgeRouteData.compareCopies(from, to, journal));
        assertEquals(LivingBridgeRouteData.CopyAgreement.DIVERGENCE,
                LivingBridgeRouteData.compareCopies(from, snapshot("minecraft:overworld", 19L), journal));
        LivingBridgeRouteData data = LivingBridgeRouteData.load(new CompoundTag());
        data.quarantine("JOURNALED_ADJACENT_UNION_LANE", from.toTag(), to.toTag(), from.dimension());
        assertTrue(data.admissionBlocked(from.dimension()));
        CompoundTag entry = data.save(new CompoundTag()).getList("quarantineLedger", Tag.TAG_COMPOUND)
                .getCompound(0);
        assertEquals(from.toTag(), entry.getCompound("firstRaw"));
        assertEquals(to.toTag(), entry.getCompound("secondRaw"));
    }

    @Test
    void futureSavedDataSchemaIsRoundTrippedAndBlocksAdmissions() {
        CompoundTag future = new CompoundTag();
        future.putInt("schema", 9);
        future.putString("futureField", "leave-this-exactly-alone");
        future.putByteArray("opaqueBytes", new byte[]{0, 1, -1, 42});
        CompoundTag nested = new CompoundTag();
        nested.putLong("value", 991L);
        future.put("unknownNested", nested);

        LivingBridgeRouteData data = LivingBridgeRouteData.load(future);

        assertTrue(data.opaqueFutureSchema());
        assertTrue(data.admissionBlocked("minecraft:overworld"));
        assertEquals(future, data.save(new CompoundTag()));
    }

    @Test
    void divergenceQuarantineRetainsBothRawCopiesAndAddsAdmissionBarrier() {
        LivingBridgeRouteData data = LivingBridgeRouteData.load(new CompoundTag());
        CompoundTag savedRaw = snapshot("minecraft:overworld", 1L).toTag();
        CompoundTag entityRaw = snapshot("minecraft:overworld", 2L).toTag();

        data.quarantine("VALID_DIVERGENCE", savedRaw, entityRaw, "minecraft:overworld");

        CompoundTag persisted = data.save(new CompoundTag());
        CompoundTag entry = persisted.getList("quarantineLedger", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(savedRaw, entry.getCompound("firstRaw"));
        assertEquals(entityRaw, entry.getCompound("secondRaw"));
        assertTrue(data.admissionBlocked("minecraft:overworld"));
        assertFalse(data.admissionBlocked("minecraft:the_nether"));
    }

    @Test
    void conflictIdIsStableAcrossCompoundInsertionOrder() {
        CompoundTag first = new CompoundTag();
        first.putInt("alpha", 1);
        CompoundTag nestedFirst = new CompoundTag();
        nestedFirst.putString("left", "a");
        nestedFirst.putLong("right", 2L);
        first.put("nested", nestedFirst);

        CompoundTag reorderedFirst = new CompoundTag();
        CompoundTag reorderedNestedFirst = new CompoundTag();
        reorderedNestedFirst.putLong("right", 2L);
        reorderedNestedFirst.putString("left", "a");
        reorderedFirst.put("nested", reorderedNestedFirst);
        reorderedFirst.putInt("alpha", 1);

        CompoundTag second = new CompoundTag();
        second.putString("evidence", "other-copy");
        assertEquals(quarantineConflictId(first, second), quarantineConflictId(reorderedFirst, second));
        assertEquals(quarantineConflictId(first, second), quarantineConflictId(second, first));
    }

    @Test
    void validSnapshotConflictIdUsesSortedCanonicalSnapshotDigests() throws Exception {
        LivingBridgeRouteSnapshot first = snapshot("minecraft:overworld", 17L);
        LivingBridgeRouteSnapshot second = snapshot("minecraft:overworld", 18L);
        List<String> digests = new java.util.ArrayList<>(List.of(first.digest(), second.digest()));
        digests.sort(String::compareTo);
        String input = digests.get(0) + "\n" + digests.get(1) + "\nVALID_DIVERGENCE";
        String expected = java.util.HexFormat.of().withUpperCase().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertEquals(expected, LivingBridgeRouteData.conflictId("VALID_DIVERGENCE",
                first.toTag(), second.toTag()));
        assertEquals(expected, LivingBridgeRouteData.conflictId("VALID_DIVERGENCE",
                second.toTag(), first.toTag()));
    }

    @Test
    void durableActiveLaneExcludesOtherOwnersAndTerminalReceiptNoLongerOccupiesLane() {
        LivingBridgeRouteSnapshot active = snapshot("minecraft:overworld", 17L);
        LivingBridgeRouteData data = LivingBridgeRouteData.load(new CompoundTag());
        data.putSnapshot(active);
        List<net.minecraft.core.BlockPos> overlapping = List.of(
                new net.minecraft.core.BlockPos(-3, 64, 1),
                new net.minecraft.core.BlockPos(-2, 64, 1));
        UUID otherOwner = UUID.fromString("00000000-0000-0000-0000-000000000099");

        UUID alternateReservation = UUID.fromString("00000000-0000-0000-0000-000000000098");
        assertTrue(data.hasConflictingLane(active.dimension(), otherOwner, alternateReservation,
                overlapping, 0.6D, 0.5D));
        assertTrue(data.hasConflictingLane(active.dimension(), active.ownerId(), alternateReservation,
                overlapping, 0.6D, 0.5D));
        assertFalse(data.hasConflictingLane(active.dimension(), active.ownerId(), active.reservationId(),
                overlapping, 0.6D, 0.5D));
        assertFalse(data.hasConflictingLane("minecraft:the_nether", otherOwner, alternateReservation,
                overlapping, 0.6D, 0.5D));

        LivingBridgeRouteSnapshot.Point endpoint = LivingBridgeRouteSnapshot.Point.from(-2.0D, 64.0D, 1.25D);
        assertThrows(IllegalArgumentException.class,
                () -> LivingBridgeRouteData.dockedSnapshot(active, null, -1, 3));
        LivingBridgeRouteSnapshot terminal = LivingBridgeRouteData.dockedSnapshot(active, endpoint, -1, 3);
        assertEquals(active.commitGeneration() + 1L, terminal.commitGeneration());
        assertEquals("DOCKED", terminal.lifecycleState());
        assertEquals("DOCKED", terminal.movementMode());
        assertEquals(endpoint, terminal.safeEndpoint());
        assertFalse(terminal.recoveryRequired());
        data.putSnapshot(terminal);

        assertFalse(data.hasConflictingLane(active.dimension(), otherOwner, alternateReservation,
                overlapping, 0.6D, 0.5D));
    }

    @Test
    void safeRecoveryReceiptIsAdjacentDeterministicAndTerminal() {
        LivingBridgeRouteSnapshot active = snapshot("minecraft:overworld", 17L);
        LivingBridgeRouteSnapshot.Point endpoint = LivingBridgeRouteSnapshot.Point.from(-2.0D, 64.0D, 1.25D);
        LivingBridgeRouteSnapshot recoverable = new LivingBridgeRouteSnapshot(active.reservationId(),
                active.ownerId(), active.formationId(), active.dimension(), active.commitGeneration(),
                active.transactionId(), active.routeRevision(), active.lifecycleState(), active.movementMode(),
                active.originalNoPhysics(), active.originalNoGravity(), false, active.corridor(),
                active.halfWidth(), active.recoveryRoute(), active.recoveryIndex(), endpoint, -1, 3);

        LivingBridgeRouteSnapshot recovered = LivingBridgeRouteData.safeRecoverySnapshot(recoverable, -1, 3);

        assertEquals(recoverable.commitGeneration() + 1L, recovered.commitGeneration());
        assertEquals(endpoint, recovered.safeEndpoint());
        assertEquals("SAFE_RECOVERY_COMPLETE", recovered.lifecycleState());
        assertEquals("SAFE_RECOVERY_COMPLETE", recovered.movementMode());
        assertFalse(recovered.recoveryRequired());
        assertTrue(LivingBridgeRouteData.isTerminalLaneState(recovered.lifecycleState()));
        assertEquals(LivingBridgeRouteData.CopyAgreement.JOURNALED_ADJACENT,
                LivingBridgeRouteData.compareCopies(recoverable, recovered,
                        journal(recoverable, recovered)));
    }

    @Test
    void deathReceiptIsDeterministicAndTerminal() {
        LivingBridgeRouteSnapshot active = snapshot("minecraft:overworld", 18L);

        LivingBridgeRouteSnapshot first = LivingBridgeRouteData.deadSnapshot(active, 7, -3);
        LivingBridgeRouteSnapshot repeat = LivingBridgeRouteData.deadSnapshot(active, 7, -3);

        assertTrue(first.exactlyEquals(repeat));
        assertEquals("DEAD", first.lifecycleState());
        assertEquals("DEAD", first.movementMode());
        assertFalse(first.recoveryRequired());
        assertTrue(LivingBridgeRouteData.isTerminalLaneState(first.lifecycleState()));
        assertEquals(active.corridor(), first.corridor());
    }

    @Test
    void verifiedRemovalReceiptIsDeterministicAndTerminal() {
        LivingBridgeRouteSnapshot current = snapshot("minecraft:overworld", 17L);

        LivingBridgeRouteSnapshot first = LivingBridgeRouteData.removedSnapshot(current, -1, 3);
        LivingBridgeRouteSnapshot second = LivingBridgeRouteData.removedSnapshot(current, -1, 3);

        assertTrue(first.exactlyEquals(second));
        assertEquals("REMOVED", first.lifecycleState());
        assertEquals("REMOVED", first.movementMode());
        assertEquals(current.commitGeneration() + 1L, first.commitGeneration());
        assertTrue(LivingBridgeRouteData.isTerminalLaneState(first.lifecycleState()));
    }

    private static CompoundTag journal(LivingBridgeRouteSnapshot from, LivingBridgeRouteSnapshot to) {
        CompoundTag journal = new CompoundTag();
        journal.putUUID("reservationId", to.reservationId());
        journal.putUUID("transactionId", to.transactionId());
        journal.putLong("commitGeneration", to.commitGeneration());
        journal.putBoolean("hasFrom", true);
        journal.put("from", from.toTag());
        journal.putString("fromDigest", from.digest());
        journal.put("to", to.toTag());
        journal.putString("toDigest", to.digest());
        return journal;
    }

    @Test
    void schemaV1EvidenceIsPreservedAndQuarantinedWithoutPairedOwnership() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("schema", 1);
        legacy.putString("unparsedOwnershipEvidence", "preserve");

        LivingBridgeRouteData data = LivingBridgeRouteData.load(legacy);

        assertEquals(1, data.quarantineCount());
        assertTrue(data.admissionBlocked("minecraft:overworld"));
        assertEquals("preserve", data.save(new CompoundTag()).getList(
                "quarantineLedger", Tag.TAG_COMPOUND).getCompound(0)
                .getCompound("raw").getString("unparsedOwnershipEvidence"));
    }

    @Test
    void exactEmptyLegacyRootIsAcceptedAsNoOwnership() {
        LivingBridgeRouteData data = LivingBridgeRouteData.load(new CompoundTag());

        assertEquals(0, data.reservationCount());
        assertEquals(0, data.quarantineCount());
        assertFalse(data.admissionBlocked("minecraft:overworld"));
    }

    @Test
    void exactSchemaZeroOnlyRootIsAcceptedAsNoOwnership() {
        CompoundTag root = new CompoundTag();
        root.putInt("schema", 0);
        LivingBridgeRouteData data = LivingBridgeRouteData.load(root);

        assertEquals(0, data.reservationCount());
        assertEquals(0, data.quarantineCount());
        assertFalse(data.admissionBlocked("minecraft:overworld"));
    }

    @Test
    void missingSchemaPayloadIsQuarantinedOpaqueAndBlocksAdmissionAfterReload() {
        CompoundTag raw = new CompoundTag();
        raw.put("reservations", new ListTag());
        assertOpaqueRootRoundTrips(raw, "OPAQUE_AMBIGUOUS_ROOT");
    }

    @Test
    void wrongTypedSchemaPayloadIsQuarantinedOpaqueAndBlocksAdmissionAfterReload() {
        CompoundTag raw = new CompoundTag();
        raw.putString("schema", "0");
        assertOpaqueRootRoundTrips(raw, "OPAQUE_AMBIGUOUS_ROOT");

        CompoundTag onlyWrongType = new CompoundTag();
        onlyWrongType.putString("schema", "0");
        assertOpaqueRootRoundTrips(onlyWrongType, "OPAQUE_AMBIGUOUS_ROOT");
    }

    @Test
    void malformedSchemaQuarantineRoundTripsRawRootAndGlobalBarrier() {
        CompoundTag schemaZeroWithEmptyKey = new CompoundTag();
        schemaZeroWithEmptyKey.putInt("schema", 0);
        schemaZeroWithEmptyKey.put("reservations", new ListTag());
        assertOpaqueRootRoundTrips(schemaZeroWithEmptyKey, "OPAQUE_AMBIGUOUS_ROOT");

        CompoundTag schemaZeroWithUnknownKey = new CompoundTag();
        schemaZeroWithUnknownKey.putInt("schema", 0);
        schemaZeroWithUnknownKey.putString("unrecognized", "opaque");
        assertOpaqueRootRoundTrips(schemaZeroWithUnknownKey, "OPAQUE_AMBIGUOUS_ROOT");

        CompoundTag negativeSchema = new CompoundTag();
        negativeSchema.putInt("schema", -1);
        assertOpaqueRootRoundTrips(negativeSchema, "OPAQUE_AMBIGUOUS_ROOT");
    }

    @Test
    void schemaOneRootIsOpaqueAndNeverMigrated() {
        CompoundTag raw = new CompoundTag();
        raw.putInt("schema", 1);
        raw.putString("reservations", "lookalike payload");
        assertOpaqueRootRoundTrips(raw, "OPAQUE_V1_SAVED_DATA");
    }

    @Test
    void pairedSchemaOneCopiesAreNotMigratedEvenIfTheyResemblePriorProposal() {
        CompoundTag savedRoot = new CompoundTag();
        savedRoot.putInt("schema", 1);
        savedRoot.putString("reservations", "same-looking bytes");
        CompoundTag entityCapsule = new CompoundTag();
        entityCapsule.putInt("schema", 1);
        entityCapsule.putString("reservations", "same-looking bytes");

        LivingBridgeRouteData data = LivingBridgeRouteData.load(savedRoot);
        LivingBridgeRouteData.quarantineEntityCapsule(data, entityCapsule,
                "minecraft:overworld", true);
        CompoundTag saved = data.save(new CompoundTag());
        ListTag entries = saved.getList("quarantineLedger", Tag.TAG_COMPOUND);

        assertEquals(2, entries.size());
        assertTrue(entries.stream().map(tag -> (CompoundTag) tag).anyMatch(entry ->
                entry.getString("classification").equals("OPAQUE_V1_SAVED_DATA")
                        && entry.getCompound("raw").equals(savedRoot)));
        assertTrue(entries.stream().map(tag -> (CompoundTag) tag).anyMatch(entry ->
                entry.getString("classification").equals("OPAQUE_V1_ENTITY")
                        && entry.getCompound("firstRaw").equals(entityCapsule)));
        assertTrue(data.admissionBlocked("minecraft:overworld"));
        assertEquals(1, entityCapsule.getInt("schema"));
    }

    @Test
    void schemaOneEntityCapsuleIsQuarantinedOpaqueBeforeFirstTick() {
        CompoundTag capsule = new CompoundTag();
        capsule.putInt("schema", 1);
        capsule.putString("mode", "WALKING_TO_STAGING");
        LivingBridgeRouteData data = LivingBridgeRouteData.load(new CompoundTag());

        LivingBridgeRouteData.quarantineEntityCapsule(data, capsule, "minecraft:overworld", true);
        CompoundTag entry = data.save(new CompoundTag()).getList("quarantineLedger", Tag.TAG_COMPOUND)
                .getCompound(0);

        assertEquals("OPAQUE_V1_ENTITY", entry.getString("classification"));
        assertEquals(capsule, entry.getCompound("firstRaw"));
        assertTrue(data.admissionBlocked("minecraft:overworld"));
    }

    @Test
    void quarantinedV1OwnerCannotReleasePhysicsUntilSafeEndpointOrDeath() {
        CompoundTag capsule = new CompoundTag();
        capsule.putInt("schema", 1);
        capsule.putString("mode", "RECOVERING");
        LivingBridgeRouteData data = LivingBridgeRouteData.load(new CompoundTag());
        LivingBridgeRouteData.quarantineEntityCapsule(data, capsule, "minecraft:overworld", true);
        LivingBridgeRouteData reloaded = LivingBridgeRouteData.load(data.save(new CompoundTag()));

        assertTrue(reloaded.admissionBlocked("minecraft:overworld"));
        assertEquals("OPAQUE_V1_ENTITY", reloaded.save(new CompoundTag())
                .getList("quarantineLedger", Tag.TAG_COMPOUND).getCompound(0)
                .getString("classification"));
        assertEquals(capsule, reloaded.save(new CompoundTag())
                .getList("quarantineLedger", Tag.TAG_COMPOUND).getCompound(0)
                .getCompound("firstRaw"));
    }

    @Test
    void schemaOneSavedDataIsQuarantinedOpaqueAndBlocksAdmissions() {
        CompoundTag root = new CompoundTag();
        root.putInt("schema", 1);
        root.putString("reservations", "opaque");
        assertOpaqueRootRoundTrips(root, "OPAQUE_V1_SAVED_DATA");
    }

    @Test
    void legacyRootQuarantineSurvivesSaveReloadWithoutLosingRawCompound() {
        CompoundTag root = new CompoundTag();
        root.putString("schema", "ambiguous");
        root.putLong("ownerUuid", 123L);
        assertOpaqueRootRoundTrips(root, "OPAQUE_AMBIGUOUS_ROOT");
    }

    private static void assertOpaqueRootRoundTrips(CompoundTag raw, String classification) {
        LivingBridgeRouteData firstLoad = LivingBridgeRouteData.load(raw);
        CompoundTag firstSave = firstLoad.save(new CompoundTag());
        ListTag quarantine = firstSave.getList("quarantineLedger", Tag.TAG_COMPOUND);
        assertEquals(1, quarantine.size());
        assertEquals(classification, quarantine.getCompound(0).getString("classification"));
        assertEquals(raw, quarantine.getCompound(0).getCompound("raw"));
        assertTrue(firstLoad.admissionBlocked("minecraft:overworld"));
        assertTrue(firstLoad.admissionBlocked("minecraft:the_nether"));

        LivingBridgeRouteData reloaded = LivingBridgeRouteData.load(firstSave);
        CompoundTag secondSave = reloaded.save(new CompoundTag());
        CompoundTag reloadedEntry = secondSave.getList("quarantineLedger", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(raw, reloadedEntry.getCompound("raw"));
        assertEquals(classification, reloadedEntry.getString("classification"));
        assertTrue(reloaded.admissionBlocked("minecraft:overworld"));
        assertTrue(reloaded.admissionBlocked("minecraft:the_nether"));
        assertTrue(secondSave.getList("globalQuarantine", Tag.TAG_COMPOUND).stream()
                .anyMatch(tag -> ((CompoundTag) tag).getString("dimension").equals("*")));
    }

    private static String quarantineConflictId(CompoundTag first, CompoundTag second) {
        LivingBridgeRouteData data = new LivingBridgeRouteData();
        data.quarantine("CANONICAL_CONFLICT_TEST", first, second, "minecraft:overworld");
        return data.save(new CompoundTag()).getList("quarantineLedger", Tag.TAG_COMPOUND)
                .getCompound(0).getString("conflictId");
    }
}
