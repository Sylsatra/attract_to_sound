package com.example.soundattract.integration.customnpcs;

import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.ai.FollowerEdgeRelayGoal;
import com.example.soundattract.ai.FollowLeaderGoal;
import com.example.soundattract.ai.LeaderAttractionGoal;
import com.example.soundattract.ai.SoundInterruptWrapperGoal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class GoalWrappingHelper {
    private record GoalToWrap(Goal goal, int priority) {}

    private static final Map<Mob, Set<Goal>> WRAPPED_GOALS = new WeakHashMap<>();
    private static final Map<Mob, Set<Goal>> DELEGATE_GOALS = new WeakHashMap<>();

    public static void wrapCustomNpcsGoals(Mob mob, GoalSelector goalSelector) {
        if (mob == null || goalSelector == null) {
            return;
        }

        Set<Goal> wrapped = WRAPPED_GOALS.computeIfAbsent(mob, k -> new HashSet<>());
        Set<Goal> delegates = DELEGATE_GOALS.computeIfAbsent(mob, k -> new HashSet<>());

        List<GoalToWrap> replacements = new ArrayList<>();
        for (WrappedGoal wrappedGoal : goalSelector.getAvailableGoals()) {
            if (wrappedGoal == null) {
                continue;
            }
            Goal goal = wrappedGoal.getGoal();

            if (isWrappedGoal(goal, wrapped) || isOurGoal(goal)) {
                continue;
            }

            if (isCustomNpcsAiGoal(goal)) {
                replacements.add(new GoalToWrap(goal, wrappedGoal.getPriority()));
            }
        }
        for (GoalToWrap replacement : replacements) {
            SoundInterruptWrapperGoal wrapper = new SoundInterruptWrapperGoal(replacement.goal(), mob);
            replaceGoal(goalSelector, replacement.goal(), wrapper, replacement.priority());
            wrapped.add(wrapper);
            delegates.add(replacement.goal());
        }
    }

    private static boolean isWrappedGoal(Goal goal, Set<Goal> wrapped) {
        if (goal instanceof SoundInterruptWrapperGoal) {
            return wrapped.contains(goal);
        }
        return false;
    }

    private static boolean isOurGoal(Goal goal) {
        return goal instanceof AttractionGoal || 
               goal instanceof LeaderAttractionGoal ||
               goal instanceof FollowerEdgeRelayGoal ||
               goal instanceof FollowLeaderGoal;
    }

    private static boolean isCustomNpcsAiGoal(Goal goal) {
        String className = goal.getClass().getName();
        return className.startsWith("noppes.npcs.ai.") && 
               !className.contains("EntityNPCInterface") &&
               !isOurGoal(goal);
    }

    private static void replaceGoal(GoalSelector goalSelector, Goal oldGoal, Goal newGoal, int priority) {
        try {
            goalSelector.getAvailableGoals().removeIf(wg -> wg.getGoal() == oldGoal);
            goalSelector.addGoal(priority, newGoal);
        } catch (Throwable t) {
            com.example.soundattract.Soundattract.LOGGER.warn("[CustomNPCs GoalWrapper] Failed to replace goal: {}", t.getMessage());
        }
    }

    public static void clearCache(Mob mob) {
        WRAPPED_GOALS.remove(mob);
        DELEGATE_GOALS.remove(mob);
    }

    public static void clearAllCache() {
        WRAPPED_GOALS.clear();
        DELEGATE_GOALS.clear();
    }
}
