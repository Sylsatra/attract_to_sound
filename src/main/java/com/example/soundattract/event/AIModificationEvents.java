package com.example.soundattract.event;

import com.example.soundattract.Soundattract;
import com.example.soundattract.ai.FleeFromUnseenAttackerGoal;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.mixin.MobAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;

public class AIModificationEvents {

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof Mob mob && world instanceof ServerLevel) {
                onEntityJoinWorld(mob, (ServerLevel) world);
            }
        });
    }

    public static void onEntityJoinWorld(Mob mob, ServerLevel level) {
        if (!SoundAttractConfig.COMMON.enableFleeFromUnseenAttackerGoal.get()) {
            return;
        }

        MobAccessor accessor = (MobAccessor) mob;
        boolean alreadyHasGoal = accessor.getGoalSelector().getAvailableGoals().stream()
                .anyMatch(wrappedGoal -> wrappedGoal.getGoal() instanceof FleeFromUnseenAttackerGoal);

        if (!alreadyHasGoal) {
            Goal fleeGoal = new FleeFromUnseenAttackerGoal(mob, 1.25D);
            accessor.getGoalSelector().addGoal(3, fleeGoal);
        }
    }
}
