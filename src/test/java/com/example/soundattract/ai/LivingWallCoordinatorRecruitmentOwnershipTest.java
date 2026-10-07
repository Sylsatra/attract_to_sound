package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class LivingWallCoordinatorRecruitmentOwnershipTest {
    @Test
    void acquiredAssemblyHoldReplacesPreAcquisitionHoldAndPreAcquisitionProgressRemainsIndependent() {
        UUID worker = UUID.randomUUID();
        Map<UUID, net.minecraft.world.phys.Vec3> holds = new HashMap<>();
        var staging = new net.minecraft.world.phys.Vec3(2.5D, 64.0D, 2.5D);
        var slot = new net.minecraft.world.phys.Vec3(8.5D, 65.0D, 8.5D);
        holds.put(worker, staging);
        assertEquals(staging, holds.get(worker));
        assertEquals(slot, LivingWallCoordinator.recordAssemblyAcquisitionHold(holds, worker, slot));
        assertEquals(slot, holds.get(worker));
    }

    @Test
    void ticketStartsInRecruitmentTransitionState() throws Exception {
        Class<?> ticketClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$MobTicket");
        Constructor<?> ctor = ticketClass.getDeclaredConstructor(UUID.class, BlockPos.class);
        ctor.setAccessible(true);
        Object ticket = ctor.newInstance(UUID.fromString("00000000-0000-0000-0000-000000000001"), new BlockPos(1, 64, 1));
        assertTrue(LivingWallCoordinator.canTransitionTicketState(LivingWallCoordinator.MobTicketState.WAITING,
                LivingWallCoordinator.MobTicketState.MOVING_TO_ASSEMBLY));
        assertFalse(LivingWallCoordinator.canTransitionTicketState(LivingWallCoordinator.MobTicketState.RELEASED,
                LivingWallCoordinator.MobTicketState.MOVING_TO_ASSEMBLY));
        assertTrue(ticket != null);
        assertFalse(getBooleanField(ticketClass, ticket, "goalStarted"));
    }

    @Test
    void releasedStateDoesNotAllowBackTransition() {
        assertFalse(LivingWallCoordinator.canTransitionTicketState(LivingWallCoordinator.MobTicketState.RELEASED,
                LivingWallCoordinator.MobTicketState.MOVING_TO_FEEDER));
    }

    @Test
    void staleGenerationCallbacksAreRejected() throws Exception {
        Class<?> ticketClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$MobTicket");
        Constructor<?> ctor = ticketClass.getDeclaredConstructor(UUID.class, BlockPos.class, long.class);
        ctor.setAccessible(true);
        Object ticket = ctor.newInstance(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                new BlockPos(1, 64, 1), 7L);
        assertTrue(LivingWallCoordinator.isStaleRecruitmentCallback(ticket, 8L));
        assertFalse(LivingWallCoordinator.isStaleRecruitmentCallback(ticket, 7L));
    }

    @Test
    void acceptedPathProgressClearsFailureAndRetryBaselines() {
        LivingWallCoordinator.MobTicket ticket = new LivingWallCoordinator.MobTicket(
                UUID.fromString("00000000-0000-0000-0000-000000000003"), new BlockPos(1, 64, 1), 1L);
        ticket.pathFailures = 2;
        ticket.retries = 2;
        LivingWallCoordinator.recordAcceptedRecruitmentProgress(ticket, 4.0D, 40L);
        assertEquals(0, ticket.pathFailures);
        assertEquals(0, ticket.retries);
        assertEquals(40L, ticket.observationTick);
        assertEquals(4.0D, ticket.bestDistanceSqr);
    }

    @Test
    void invalidFeederPoseRetainsAssignmentWithinBoundedRetryWindow() {
        assertTrue(LivingWallCoordinator.shouldRetryIntakePoseFailure(1));
        assertTrue(LivingWallCoordinator.shouldRetryIntakePoseFailure(2));
        assertFalse(LivingWallCoordinator.shouldTerminateIntakeCaptureFailure(2));
    }

    @Test
    void windowLostIsAReplanRetryBeforeTerminalRelease() {
        LivingWallCoordinator.MobTicket ticket = new LivingWallCoordinator.MobTicket(
                UUID.fromString("00000000-0000-0000-0000-000000000005"), new BlockPos(1, 64, 1), 1L);
        ticket.captureRetryUntil = 40L;
        ticket.intakeWindowFailures = 1;
        assertTrue(LivingWallCoordinator.shouldRetryIntakePoseFailure(ticket.intakeWindowFailures));
        assertEquals(LivingWallCoordinator.MobTicketState.MOVING_TO_ASSEMBLY, ticket.state);
        assertEquals(40L, ticket.captureRetryUntil);
    }

    @Test
    void successfulMultiTickSuctionRemainsRecoverableAfterTransientJitter() {
        assertTrue(LivingWallCoordinator.shouldRetryIntakePoseFailure(0));
        assertTrue(LivingWallCoordinator.shouldRetryIntakePoseFailure(1));
        assertFalse(LivingWallCoordinator.shouldTerminateIntakeCaptureFailure(1));
    }

    @Test
    void suctioningTicketRetainsControlledSuctionOwnership() {
        assertTrue(LivingWallCoordinator.retainsControlledSuctionOwnership(
                LivingWallCoordinator.MobTicketState.SUCTIONING));
        assertFalse(LivingWallCoordinator.retainsControlledSuctionOwnership(
                LivingWallCoordinator.MobTicketState.MOVING_TO_FEEDER));
        assertFalse(LivingWallCoordinator.retainsControlledSuctionOwnership(
                LivingWallCoordinator.MobTicketState.BUILDING));
        assertFalse(LivingWallCoordinator.retainsControlledSuctionOwnership(null));
    }

    @Test
    void activeFreezeTicketPredicateIncludesExactFiveLiveStates() {
        assertTrue(LivingWallCoordinator.isActiveFreezeTicketState(
                LivingWallCoordinator.MobTicketState.MOVING_TO_ASSEMBLY));
        assertTrue(LivingWallCoordinator.isActiveFreezeTicketState(
                LivingWallCoordinator.MobTicketState.STAGING_AT_ASSEMBLY));
        assertTrue(LivingWallCoordinator.isActiveFreezeTicketState(
                LivingWallCoordinator.MobTicketState.ASSEMBLED));
        assertTrue(LivingWallCoordinator.isActiveFreezeTicketState(
                LivingWallCoordinator.MobTicketState.MOVING_TO_FEEDER));
        assertTrue(LivingWallCoordinator.isActiveFreezeTicketState(
                LivingWallCoordinator.MobTicketState.SUCTIONING));
        assertTrue(LivingWallCoordinator.isActiveFreezeTicketState(
                LivingWallCoordinator.MobTicketState.BUILDING));
    }

    @Test
    void activeFreezeTicketPredicateRejectsInactiveStates() {
        assertFalse(LivingWallCoordinator.isActiveFreezeTicketState(
                LivingWallCoordinator.MobTicketState.WAITING));
        assertFalse(LivingWallCoordinator.isActiveFreezeTicketState(
                LivingWallCoordinator.MobTicketState.RELEASED));
        assertFalse(LivingWallCoordinator.isActiveFreezeTicketState(null));
    }

    @Test
    void terminalFailureOccursOnlyAfterConsecutivePoseFailures() {
        assertFalse(LivingWallCoordinator.shouldTerminateIntakeCaptureFailure(0));
        assertFalse(LivingWallCoordinator.shouldTerminateIntakeCaptureFailure(2));
        assertTrue(LivingWallCoordinator.shouldTerminateIntakeCaptureFailure(3));
    }

    @Test
    void stalledRecruitmentReleasesAfterBoundedObservationWindows() {
        assertFalse(LivingWallCoordinator.shouldReleaseAfterStalledRecruitment(2));
        assertTrue(LivingWallCoordinator.shouldReleaseAfterStalledRecruitment(3));
    }

    @Test
    void ticketLegTransitionResetsProgressDeadlineAndRetryBaseline() {
        LivingWallCoordinator.MobTicket ticket = new LivingWallCoordinator.MobTicket(
                UUID.fromString("00000000-0000-0000-0000-000000000004"), new BlockPos(1, 64, 1), 1L);
        ticket.retries = 2;
        ticket.pathFailures = 2;
        ticket.progressDeadline = 80L;
        ticket.assemblyStageTicks = 3;
        ticket.assemblyBestDistanceSqr = 9.0D;
        assertTrue(LivingWallCoordinator.canTransitionTicketState(ticket.state,
                LivingWallCoordinator.MobTicketState.STAGING_AT_ASSEMBLY));
        try {
            var transition = LivingWallCoordinator.class.getDeclaredMethod("transitionTicket",
                    LivingWallCoordinator.MobTicket.class, LivingWallCoordinator.MobTicketState.class);
            transition.setAccessible(true);
            transition.invoke(null, ticket, LivingWallCoordinator.MobTicketState.STAGING_AT_ASSEMBLY);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        assertEquals(0, ticket.retries);
        assertEquals(0, ticket.pathFailures);
        assertEquals(Long.MIN_VALUE, ticket.progressDeadline);
        assertEquals(0, ticket.assemblyStageTicks);
        assertEquals(Double.MAX_VALUE, ticket.assemblyBestDistanceSqr);
    }

    @Test
    void assembledTicketMayDemoteToStagingButNotSkipToBuilding() {
        assertTrue(LivingWallCoordinator.canTransitionTicketState(
                LivingWallCoordinator.MobTicketState.ASSEMBLED,
                LivingWallCoordinator.MobTicketState.STAGING_AT_ASSEMBLY));
        assertFalse(LivingWallCoordinator.canTransitionTicketState(
                LivingWallCoordinator.MobTicketState.MOVING_TO_ASSEMBLY,
                LivingWallCoordinator.MobTicketState.ASSEMBLED));
    }

    @Test
    void routeProbeLedgerReusesOwnerReservationForSameTarget() throws Exception {
        Object formation = newBareFormation();
        Class<?> ownerClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$RouteProbeOwner");
        Object owner = Enum.valueOf((Class<Enum>) ownerClass.asSubclass(Enum.class), "HOLDING_SURVEY");
        var helper = LivingWallCoordinator.class.getDeclaredMethod("reserveRouteProbeForTest",
                Class.forName("com.example.soundattract.ai.LivingWallCoordinator$Formation"),
                ownerClass, BlockPos.class);
        helper.setAccessible(true);
        BlockPos target = new BlockPos(4, 64, 4);
        assertTrue((Boolean) helper.invoke(null, formation, owner, target));
        assertTrue((Boolean) helper.invoke(null, formation, owner, target));
        assertEquals(1, getIntField(formation, "routeProbeReservations"));
    }

    @Test
    void routeProbeLedgerRejectsFourthDistinctReservation() throws Exception {
        Object formation = newBareFormation();
        Class<?> ownerClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$RouteProbeOwner");
        var helper = LivingWallCoordinator.class.getDeclaredMethod("reserveRouteProbeForTest",
                Class.forName("com.example.soundattract.ai.LivingWallCoordinator$Formation"),
                ownerClass, BlockPos.class);
        helper.setAccessible(true);
        assertTrue((Boolean) helper.invoke(null, formation, enumValue(ownerClass, "TARGET_SURVEY"), new BlockPos(1, 64, 1)));
        assertTrue((Boolean) helper.invoke(null, formation, enumValue(ownerClass, "HOLDING_SURVEY"), new BlockPos(2, 64, 2)));
        assertTrue((Boolean) helper.invoke(null, formation, enumValue(ownerClass, "INTAKE_SURVEY"), new BlockPos(3, 64, 3)));
        assertFalse((Boolean) helper.invoke(null, formation, enumValue(ownerClass, "CROSSER_CAPTURE"), new BlockPos(4, 64, 4)));
        assertEquals(3, getIntField(formation, "routeProbeReservations"));
    }

    @Test
    void claimedWorkersAreBoundedByUsableBodies() throws Exception {
        Object formation = newBareFormation();
        setField(formation, "usableBodies", 2);
        setField(formation, "requiredBodies", 2);
        List<UUID> claimed = List.of(
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                UUID.fromString("00000000-0000-0000-0000-000000000102"),
                UUID.fromString("00000000-0000-0000-0000-000000000103"));
        @SuppressWarnings("unchecked")
        Set<UUID> passiveClaims = (Set<UUID>) getField(formation, "claimedWorkerIds");
        passiveClaims.addAll(claimed.subList(0, 2));
        Method claimedMethod = LivingWallCoordinator.class.getDeclaredMethod(
                "isBridgeWorkerClaimed", formation.getClass(), UUID.class);
        claimedMethod.setAccessible(true);
        assertTrue((Boolean) claimedMethod.invoke(null, formation, claimed.get(0)));
        assertTrue((Boolean) claimedMethod.invoke(null, formation, claimed.get(1)));
        assertFalse((Boolean) claimedMethod.invoke(null, formation, claimed.get(2)));
        assertEquals(2, passiveClaims.size());
    }

    @Test
    void targetSuppressionAppliesOnlyWhileWorkerIsClaimed() throws Exception {
        Object formation = newBareFormation();
        UUID worker = UUID.fromString("00000000-0000-0000-0000-000000000201");
        Method claimedMethod = LivingWallCoordinator.class.getDeclaredMethod(
                "isBridgeWorkerClaimed", formation.getClass(), UUID.class);
        claimedMethod.setAccessible(true);
        assertFalse((Boolean) claimedMethod.invoke(null, formation, worker));
        @SuppressWarnings("unchecked")
        Set<UUID> passiveClaims = (Set<UUID>) getField(formation, "claimedWorkerIds");
        passiveClaims.add(worker);
        assertTrue((Boolean) claimedMethod.invoke(null, formation, worker));
        passiveClaims.remove(worker);
        assertFalse((Boolean) claimedMethod.invoke(null, formation, worker));
    }

    @Test
    void plannerSessionStaysAliveDuringAssembleWithClaimedWorkers() throws Exception {
        Object formation = newBareFormation();
        setEnumField(formation, "phase", "ASSEMBLE");
        @SuppressWarnings("unchecked")
        Set<UUID> passiveClaims = (Set<UUID>) getField(formation, "claimedWorkerIds");
        passiveClaims.add(UUID.fromString("00000000-0000-0000-0000-000000000301"));
        Method aliveMethod = LivingWallCoordinator.class.getDeclaredMethod(
                "plannerSessionAlive", formation.getClass(), boolean.class);
        aliveMethod.setAccessible(true);
        assertTrue((Boolean) aliveMethod.invoke(null, formation, false));
        passiveClaims.clear();
        assertFalse((Boolean) aliveMethod.invoke(null, formation, false));
    }

    @Test
    void releaseCleanupClearsPassiveClaims() throws Exception {
        Object formation = newBareFormation();
        UUID worker = UUID.fromString("00000000-0000-0000-0000-000000000401");
        @SuppressWarnings("unchecked")
        Set<UUID> passiveClaims = (Set<UUID>) getField(formation, "claimedWorkerIds");
        passiveClaims.add(worker);
        passiveClaims.clear();
        assertTrue(passiveClaims.isEmpty());
    }

    private static boolean getBooleanField(Class<?> type, Object instance, String fieldName) throws Exception {
        var field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(instance);
    }

    private static int getIntField(Object instance, String fieldName) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(instance);
    }

    private static Object getField(Object instance, String fieldName) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static void setField(Object instance, String fieldName, Object value) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(instance, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setEnumField(Object instance, String fieldName, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(instance, Enum.valueOf((Class<Enum>) field.getType().asSubclass(Enum.class), name));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class<Enum>) type.asSubclass(Enum.class), name);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object newBareFormation() throws Exception {
        Class<?> formationClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$Formation");
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Object formation = unsafeClass.getDeclaredMethod("allocateInstance", Class.class).invoke(unsafe, formationClass);
        Class<?> ownerClass = Class.forName("com.example.soundattract.ai.LivingWallCoordinator$RouteProbeOwner");
        Field ledgerField = formationClass.getDeclaredField("routeProbeLedger");
        ledgerField.setAccessible(true);
        ledgerField.set(formation, new EnumMap(ownerClass.asSubclass(Enum.class)));
        Field reservationsField = formationClass.getDeclaredField("routeProbeReservations");
        reservationsField.setAccessible(true);
        reservationsField.setInt(formation, 0);
        Field successField = formationClass.getDeclaredField("pathSuccessCount");
        successField.setAccessible(true);
        successField.setInt(formation, 0);
        setField(formation, "claimedWorkerIds", new HashSet<UUID>());
        setField(formation, "builderReservations", new HashSet<UUID>());
        setField(formation, "pendingBuilderIds", new ArrayList<UUID>());
        setField(formation, "transitCrosserIds", new HashSet<UUID>());
        setField(formation, "reserved", new HashSet<UUID>());
        setField(formation, "members", new ArrayList<UUID>());
        setField(formation, "structuralBuilderIds", new HashSet<UUID>());
        setField(formation, "candidateHoldingGoals", new HashMap<UUID, Boolean>());
        setField(formation, "ticketGoals", new HashMap<UUID, Boolean>());
        setField(formation, "mobTickets", new HashMap<UUID, Object>());
        setField(formation, "phase", enumValue(Class.forName("com.example.soundattract.ai.LivingWallCoordinator$Phase"), "SCAN"));
        return formation;
    }
}
