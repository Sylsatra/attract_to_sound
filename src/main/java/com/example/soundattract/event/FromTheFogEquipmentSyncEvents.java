package com.example.soundattract.event;

import com.example.soundattract.ModChecker;
import com.example.soundattract.network.FromTheFogEquipmentMarkerMessage;
import com.example.soundattract.network.SoundAttractNetwork;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class FromTheFogEquipmentSyncEvents {
    private static final String FROM_THE_FOG_MOD_ID = "watching";
    private static final String EQUIPMENT_MODEL_TAG = "herobrineModel";
    private static boolean registered;

    private FromTheFogEquipmentSyncEvents() {
    }

    public static void register() {
        if (registered || !ModChecker.isLoaded(FROM_THE_FOG_MOD_ID)) {
            return;
        }
        registered = true;
        EntityTrackingEvents.START_TRACKING.register(FromTheFogEquipmentSyncEvents::onStartTracking);
        EntityTrackingEvents.STOP_TRACKING.register(FromTheFogEquipmentSyncEvents::onStopTracking);
    }

    private static void onStartTracking(Entity entity, ServerPlayer player) {
        if (isEquipmentModel(entity)) {
            send(player, entity, true);
        }
    }

    private static void onStopTracking(Entity entity, ServerPlayer player) {
        if (isEquipmentModel(entity)) {
            send(player, entity, false);
        }
    }

    private static boolean isEquipmentModel(Entity entity) {
        return entity != null && entity.getTags().contains(EQUIPMENT_MODEL_TAG);
    }

    private static void send(ServerPlayer player, Entity entity, boolean keepEquipmentVisible) {
        if (player == null || entity == null) return;
        SoundAttractNetwork.sendFromTheFogMarker(player,
                new com.example.soundattract.network.FromTheFogEquipmentMarkerMessage(entity.getUUID(), keepEquipmentVisible));
    }
}
