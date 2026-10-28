package com.example.soundattract.event;

import com.example.soundattract.Soundattract;
import com.example.soundattract.async.AsyncManager;
import com.example.soundattract.event.config.ConfigReloadListener;
import com.example.soundattract.network.CamoSyncMessage;
import com.example.soundattract.worker.WorkSchedulerManager;
import dev.architectury.event.EventResult;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import com.example.soundattract.tracking.SoundTracker;

public class SoundAttractFabricEvents {

    public static void register() {
        Soundattract.LOGGER.info("Registering SoundAttract Fabric events...");
        WindScentEvents.register();

        ServerTickEvents.START_SERVER_TICK.register(server -> {
            com.example.soundattract.los.OptimizedLOS.onServerTick(server);
            StealthDetectionEvents.onServerTick(server);
            AsyncManager.onServerTick(server);
            SoundAttractionEvents.onServerTick(server);
        });

        ServerTickEvents.END_SERVER_TICK.register(server ->
        {
            WorkSchedulerManager.flushPendingSoundScores();
            com.example.soundattract.pathfinding.FlowFieldManager.onEndServerTick();
        });

        ServerTickEvents.START_LEVEL_TICK.register(level -> {
            if (level instanceof ServerLevel) {
                ServerLevel serverLevel = (ServerLevel) level;
                ScentEvents.onLevelTick(serverLevel);
                ScentEvents.onLevelTickCleanup(serverLevel);
            }
        });




        ServerTickEvents.END_LEVEL_TICK.register(SoundAttractionEvents::onLevelTick);

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof Mob && world instanceof ServerLevel) {
                Mob mob = (Mob) entity;
                ServerLevel level = (ServerLevel) world;
                ScentEvents.onEntityJoinLevel(mob, level);
                SoundAttractionEvents.onMobJoinLevel(mob, level);
                AIModificationEvents.onEntityJoinWorld(mob, level);
            }
            if (entity instanceof Projectile && world instanceof ServerLevel) {
                ServerLevel serverLevel = (ServerLevel) world;
                ArrowInvestigationEvents.onEntityJoin(entity, serverLevel);
            }
        });

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof Mob) {
                Mob mob = (Mob) entity;
                ScentEvents.onEntityLeaveScentCache(mob);
                if (world instanceof ServerLevel) SoundTracker.onMobUnload(mob.getUUID(), world);
            }
            ArrowInvestigationEvents.onEntityLeave(entity);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ScentEvents.onPlayerLoggedOut(handler.getPlayer());
            FloorCreekEvents.onPlayerLogout(handler.getPlayer());
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            AsyncManager.onServerStopped();
            com.example.soundattract.los.OptimizedLOS.onServerStopped();
            WorkSchedulerManager.clearResults();
            SoundAttractionEvents.onServerStopped();
            com.example.soundattract.ai.RaidManager.onServerStopped(server);
            com.example.soundattract.ai.MobGroupManager.onServerStopped();
            com.example.soundattract.pathfinding.FlowFieldManager.onServerStopped();
            SoundTracker.onServerStopped();
            ScentEvents.onServerStopped(server);
            ArrowInvestigationEvents.onServerStopping();
            FloorCreekEvents.onServerStopping();
        });

        ServerLevelEvents.UNLOAD.register((server, level) -> {
            com.example.soundattract.ai.RaidManager.onLevelUnload(level);
            com.example.soundattract.ai.MobGroupManager.onLevelUnload(level);
            com.example.soundattract.pathfinding.FlowFieldManager.onLevelUnload(level);
            SoundTracker.onLevelUnload(level);
            ScentEvents.onLevelUnload(level);
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            com.example.soundattract.los.OptimizedLOS.onServerStarting();
            com.example.soundattract.pathfinding.FlowFieldManager.onServerStarting();
            ConfigReloadListener.onConfigLoad();
        });

        Soundattract.LOGGER.info("SoundAttract Fabric events registered successfully.");
    }

}


