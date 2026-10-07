package com.example.soundattract.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(
        modid = "soundattract",
        bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE
)
public class BlockBreakerManager {

    private record GoalAction(ActionType type, Goal goal, int priority) {}
    private enum ActionType { ADD, REMOVE }

    private static final Cache<Mob, GoalAction> PENDING_ACTIONS = Caffeine.newBuilder()
            .weakKeys()
            .maximumSize(8192)
            .expireAfterWrite(Duration.ofSeconds(2))
            .build();

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        PENDING_ACTIONS.invalidateAll();
    }

    public static void scheduleAdd(Mob mob, Goal goal, int priority) {
        if (mob == null || goal == null) return;
        PENDING_ACTIONS.put(mob, new GoalAction(ActionType.ADD, goal, priority));
    }

    public static void scheduleRemove(Mob mob, Goal goal) {
        if (mob != null && goal != null) {
            PENDING_ACTIONS.put(mob, new GoalAction(ActionType.REMOVE, goal, 0));
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