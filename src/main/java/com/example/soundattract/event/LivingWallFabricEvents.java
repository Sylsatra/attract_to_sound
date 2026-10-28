package com.example.soundattract.event;

import com.example.soundattract.ai.LivingBridgeRouteData;
import com.example.soundattract.ai.LivingWallCoordinator;
import com.example.soundattract.spatial.SpatialLifecycleEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;

/** Fabric lifecycle bridge for the server-authoritative living-wall controller. */
public final class LivingWallFabricEvents {
    private static boolean registered;

    private LivingWallFabricEvents() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        SpatialLifecycleEvents.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                LivingWallCoordinator.registerCommands(dispatcher));
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            LivingBridgeRouteData.onEntityJoinLevel(world, entity);
            LivingWallCoordinator.onEntityJoinLevel(world, entity);
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            LivingWallCoordinator.onEntityLeaveLevel(world, entity);
            LivingBridgeRouteData.onEntityLeaveLevel(world, entity);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            LivingWallCoordinator.onLivingDeath(entity, damageSource);
            LivingBridgeRouteData.onLivingDeath(entity);
        });
        ServerLevelEvents.UNLOAD.register((server, world) -> LivingWallCoordinator.onLevelUnload(world));
        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) ->
                LivingWallCoordinator.onChunkUnload(world, chunk.getPos()));
        ServerLifecycleEvents.SERVER_STOPPING.register(LivingWallCoordinator::onServerStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(LivingWallCoordinator::onServerStopped);
    }
}
