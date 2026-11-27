package com.example.soundattract.pathfinding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathTaskSchedulerTest {
    @Test
    void optionalWorkLeavesCommittedReservation() {
        PathTaskScheduler.TickBudget budget = new PathTaskScheduler.TickBudget();
        budget.reset(1L);
        for (int i = 0; i < 32; i++) {
            assertTrue(budget.canConsume(PathTaskScheduler.Priority.OPTIONAL, 40));
            budget.consume();
        }
        assertFalse(budget.canConsume(PathTaskScheduler.Priority.OPTIONAL, 40));
        assertTrue(budget.canConsume(PathTaskScheduler.Priority.COMBAT, 40));
        assertTrue(budget.canConsume(PathTaskScheduler.Priority.COMMITTED, 40));
    }

    @Test
    void combatLeavesCommittedReservationAndZeroAllowsNoWork() {
        PathTaskScheduler.TickBudget budget = new PathTaskScheduler.TickBudget();
        budget.reset(2L);
        for (int i = 0; i < 32; i++) {
            budget.consume();
        }
        for (int i = 0; i < 4; i++) {
            assertTrue(budget.canConsume(PathTaskScheduler.Priority.COMBAT, 40));
            budget.consume();
        }
        assertFalse(budget.canConsume(PathTaskScheduler.Priority.COMBAT, 40));
        assertTrue(budget.canConsume(PathTaskScheduler.Priority.COMMITTED, 40));
        assertFalse(budget.canConsume(PathTaskScheduler.Priority.COMMITTED, 0));
    }

    @Test
    void budgetResetsWhenTickChanges() {
        PathTaskScheduler.TickBudget budget = new PathTaskScheduler.TickBudget();
        budget.reset(3L);
        budget.consume();
        budget.reset(4L);
        assertEquals(0, budget.attempts());
        assertTrue(budget.canConsume(PathTaskScheduler.Priority.OPTIONAL, 5));
    }

    @Test
    void exhaustedBudgetDefersUntilNextTick() {
        PathTaskScheduler.TickBudget budget = new PathTaskScheduler.TickBudget();
        budget.reset(6L);
        for (int i = 0; i < 32; i++) {
            assertTrue(budget.canConsume(PathTaskScheduler.Priority.OPTIONAL, 40));
            budget.consume();
        }

        assertFalse(budget.canConsume(PathTaskScheduler.Priority.OPTIONAL, 40));

        budget.reset(7L);

        assertEquals(0, budget.attempts());
        assertTrue(budget.canConsume(PathTaskScheduler.Priority.OPTIONAL, 40));
    }

    @Test
    void oneAttemptCeilingDoesNotDeadlockOptionalWork() {
        PathTaskScheduler.TickBudget budget = new PathTaskScheduler.TickBudget();
        budget.reset(5L);
        assertTrue(budget.canConsume(PathTaskScheduler.Priority.OPTIONAL, 1));
        budget.consume();
        assertFalse(budget.canConsume(PathTaskScheduler.Priority.OPTIONAL, 1));
        PathTaskScheduler.TickBudget committedBudget = new PathTaskScheduler.TickBudget();
        committedBudget.reset(5L);
        assertTrue(committedBudget.canConsume(PathTaskScheduler.Priority.COMMITTED, 1));
    }
}
