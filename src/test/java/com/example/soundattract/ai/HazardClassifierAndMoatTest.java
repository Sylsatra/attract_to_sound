package com.example.soundattract.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HazardClassifierAndMoatTest {

    @Test
    void jumpBridgeIsDirectSpanAndJumpKind() {
        assertEquals(LivingWallCoordinator.BridgeKind.JUMP,
                LivingWallCoordinator.bridgeKind(LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE));
        assertTrue(LivingWallCoordinator.isDirectSpan(LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE));
        assertFalse(LivingWallCoordinator.requiresTilt(LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE));
    }

    @Test
    void adapterSpecForJumpBridgeProducesDirectJumpAdapter() {
        var specOpt = LivingWallCoordinator.adapterSpecForScenario(LivingWallCoordinator.ConstructionScenario.JUMP_BRIDGE);
        assertTrue(specOpt.isPresent());
        var spec = specOpt.get();
        assertTrue(spec.directSpan());
        assertFalse(spec.requiresTilt());
        assertFalse(spec.pillarAssembly());
        assertEquals(LivingWallBridgeAdapterPlanner.AdapterKind.JUMP, spec.kind());
    }

    @Test
    void jumpBridgeCompatibilityWithAdapterPlanner() {
        assertTrue(LivingWallBridgeAdapterPlanner.adapterFor("JUMP_BRIDGE", LivingWallBridgeAdapterPlanner.AdapterKind.JUMP).isPresent());
        assertFalse(LivingWallBridgeAdapterPlanner.adapterFor("JUMP_BRIDGE", LivingWallBridgeAdapterPlanner.AdapterKind.DIRECT).isPresent());
        assertFalse(LivingWallBridgeAdapterPlanner.adapterFor("JUMP_BRIDGE", LivingWallBridgeAdapterPlanner.AdapterKind.TILT).isPresent());
    }
}
