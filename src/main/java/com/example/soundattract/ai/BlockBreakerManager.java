package com.example.soundattract.ai;

import com.example.soundattract.mixin.MobAccessor;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import java.time.Duration;

public class BlockBreakerManager {

    private record GoalAction(ActionType type, Goal goal, int priority) {}
    private enum ActionType { ADD, REMOVE }


    private static final Cache<Mob, GoalAction> PENDING_ACTIONS = Caffeine.newBuilder()
            .weakKeys()
            .maximumSize(8192)
            .expireAfterWrite(Duration.ofSeconds(2))
            .build();

    static {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING_ACTIONS.invalidateAll());
    }


    public static void scheduleAdd(Mob mob, Goal goal, int priority) {
        if (mob != null && goal != null) PENDING_ACTIONS.put(mob, new GoalAction(ActionType.ADD, goal, priority));
    }


    public static void scheduleRemove(Mob mob, Goal goal) {
        if (goal != null) {
            if (mob != null) PENDING_ACTIONS.put(mob, new GoalAction(ActionType.REMOVE, goal, 0));
        }
    }


    public static void processPendingActions() {
        if (PENDING_ACTIONS.asMap().isEmpty()) {
            return;
        }

        for (Mob mob : PENDING_ACTIONS.asMap().keySet()) {
            GoalAction action = PENDING_ACTIONS.asMap().remove(mob);
            if (action != null && mob.isAlive() && !mob.isRemoved()) {
                if (action.type == ActionType.ADD) {
                    net.minecraft.world.entity.ai.goal.GoalSelector gs = ((MobAccessor) mob).getGoalSelector();
                    gs.getAvailableGoals().stream()
                        .filter(wrappedGoal -> wrappedGoal.getGoal().getClass().equals(action.goal.getClass()))
                        .findFirst()
                        .ifPresent(wrappedGoal -> gs.removeGoal(wrappedGoal.getGoal()));
                    gs.addGoal(action.priority, action.goal);
                } else if (action.type == ActionType.REMOVE) {
                    ((MobAccessor) mob).getGoalSelector().removeGoal(action.goal);
                }
            }
        }
    }
}
