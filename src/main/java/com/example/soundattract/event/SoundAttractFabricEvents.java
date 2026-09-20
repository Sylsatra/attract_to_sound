package com.example.soundattract.event;

import com.example.soundattract.Soundattract;
import com.example.soundattract.async.AsyncManager;
import com.example.soundattract.event.config.ConfigReloadListener;
import com.example.soundattract.network.CamoSyncMessage;
import com.example.soundattract.util.ScentQueryCache;
import dev.architectury.event.EventResult;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
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

        ServerTickEvents.START_SERVER_TICK.register(server -> {
            StealthDetectionEvents.onServerTick(server);
            AsyncManager.onServerTick(server);
            SoundAttractionEvents.onServerTick(server);
            ArrowInvestigationEvents.onServerTick(server.overworld());
        });

        ServerTickEvents.START_WORLD_TICK.register(level -> {
            if (level instanceof ServerLevel) {
                ServerLevel serverLevel = (ServerLevel) level;
                ScentEvents.onLevelTick(serverLevel);
                ScentEvents.onLevelTickCleanup(serverLevel);
                SoundAttractionEvents.onLevelTick(serverLevel);
                ArrowScentEvents.onLevelTick(serverLevel);
            }
        });

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
            }
            ArrowInvestigationEvents.onEntityLeave(entity);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ScentEvents.onPlayerLoggedOut(handler.getPlayer());
            FloorCreekEvents.onPlayerLogout(handler.getPlayer());
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SoundTracker.clearCaches();
            ScentQueryCache.clear();
            ArrowInvestigationEvents.onServerStopping();
            FloorCreekEvents.onServerStopping();
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ConfigReloadListener.onConfigLoad();
        });

        Soundattract.LOGGER.info("SoundAttract Fabric events registered successfully.");
    }

    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(CamoSyncMessage.ID, (client, handler, buf, responseSender) -> {
            CamoSyncMessage msg = CamoSyncMessage.read(buf);
            client.execute(() -> CamoSyncMessage.handle(msg, client));
        });
    }
}
