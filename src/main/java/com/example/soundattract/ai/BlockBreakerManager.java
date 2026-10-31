package com.example.soundattract.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BlockBreakerManager {

    private record GoalAction(ActionType type, Goal goal, int priority) {}
    private enum ActionType { ADD, REMOVE }


    private static final Map<Mob, GoalAction> PENDING_ACTIONS = new ConcurrentHashMap<>();


    public static void scheduleAdd(Mob mob, Goal goal, int priority) {
        PENDING_ACTIONS.put(mob, new GoalAction(ActionType.ADD, goal, priority));
    }


    public static void scheduleRemove(Mob mob, Goal goal) {
        if (goal != null) {
            PENDING_ACTIONS.put(mob, new GoalAction(ActionType.REMOVE, goal, 0));
        }
    }


    public static void processPendingActions() {
        if (PENDING_ACTIONS.isEmpty()) {
            return;
        }


        for (Mob mob : PENDING_ACTIONS.keySet()) {
            GoalAction action = PENDING_ACTIONS.remove(mob);
            if (action != null && mob.isAlive() && !mob.isRemoved()) {
                if (action.type == ActionType.ADD) {

                    mob.goalSelector.getAvailableGoals().stream()
                        .filter(wrappedGoal -> wrappedGoal.getGoal().getClass().equals(action.goal.getClass()))
                        .findFirst()
                        .ifPresent(wrappedGoal -> mob.goalSelector.removeGoal(wrappedGoal.getGoal()));
                    
                    mob.goalSelector.addGoal(action.priority, action.goal);
                } else if (action.type == ActionType.REMOVE) {
                    mob.goalSelector.removeGoal(action.goal);
                }
            }
        }
    }
}
