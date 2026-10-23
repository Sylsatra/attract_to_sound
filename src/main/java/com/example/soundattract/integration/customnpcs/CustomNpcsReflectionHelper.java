package com.example.soundattract.integration.customnpcs;

import com.example.soundattract.Soundattract;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class CustomNpcsReflectionHelper {

    private static final Map<Class<?>, Optional<Field>> GOAL_SELECTOR_CACHE = new ConcurrentHashMap<>();
    private static boolean hasLoggedError = false;

    /**
     * Get the goalSelector field from a Mob entity using reflection.
     * Results are cached for performance.
     *
     * @param mob The mob to get goalSelector from
     * @return GoalSelector if accessible, null otherwise
     */
    public static GoalSelector getGoalSelector(Mob mob) {
        if (mob == null) {
            return null;
        }

        Class<?> clazz = mob.getClass();
        Optional<Field> cachedField = GOAL_SELECTOR_CACHE.get(clazz);

        if (cachedField != null) {
            return getGoalSelectorFromField(cachedField.orElse(null), mob);
        }

        try {
            Field field = clazz.getDeclaredField("goalSelector");
            field.setAccessible(true);
            GOAL_SELECTOR_CACHE.put(clazz, Optional.of(field));
            return (GoalSelector) field.get(mob);
        } catch (NoSuchFieldException e) {
            GOAL_SELECTOR_CACHE.put(clazz, Optional.empty());
            return null;
        } catch (IllegalAccessException e) {
            logErrorOnce("Cannot access goalSelector field on " + clazz.getName(), e);
            GOAL_SELECTOR_CACHE.put(clazz, Optional.empty());
            return null;
        }
    }

    private static GoalSelector getGoalSelectorFromField(Field field, Mob mob) {
        if (field == null) {
            return null;
        }
        try {
            return (GoalSelector) field.get(mob);
        } catch (IllegalAccessException e) {
            logErrorOnce("Cannot read goalSelector field", e);
            return null;
        }
    }

    private static void logErrorOnce(String message, Exception e) {
        if (!hasLoggedError) {
            Soundattract.LOGGER.error("[CustomNPCs] " + message, e);
            hasLoggedError = true;
        }
    }

    /**
     * Clear the cache. Useful for testing or if classes are reloaded.
     */
    public static void clearCache() {
        GOAL_SELECTOR_CACHE.clear();
        hasLoggedError = false;
    }
}
