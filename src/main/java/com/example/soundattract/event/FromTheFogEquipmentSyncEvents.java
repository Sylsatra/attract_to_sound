package com.example.soundattract.event;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.network.FromTheFogEquipmentMarkerMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends the equipment-rendered invisible model marker only to clients tracking that entity. */
@EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public final class FromTheFogEquipmentSyncEvents {
    private static final String FROM_THE_FOG_MOD_ID = "watching";
    private static final String EQUIPMENT_MODEL_TAG = "herobrineModel";

    private FromTheFogEquipmentSyncEvents() {}

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && isEquipmentModel(event.getTarget())) {
            send(player, event.getTarget(), true);
        }
    }

    @SubscribeEvent
    public static void onStopTracking(PlayerEvent.StopTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && isEquipmentModel(event.getTarget())) {
            send(player, event.getTarget(), false);
        }
    }

    private static boolean isEquipmentModel(Entity entity) {
        return ModList.get().isLoaded(FROM_THE_FOG_MOD_ID) && entity.getTags().contains(EQUIPMENT_MODEL_TAG);
    }

    private static void send(ServerPlayer player, Entity entity, boolean keepEquipmentVisible) {
        PacketDistributor.sendToPlayer(player,
                new FromTheFogEquipmentMarkerMessage(entity.getUUID(), keepEquipmentVisible));
    }
}
