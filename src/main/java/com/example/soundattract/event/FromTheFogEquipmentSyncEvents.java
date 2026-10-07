package com.example.soundattract.event;

import com.example.soundattract.network.FromTheFogEquipmentMarkerMessage;
import com.example.soundattract.network.SoundAttractNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** Optional, tag-based bridge for From The Fog's equipment-rendered invisible model entities. */
@Mod.EventBusSubscriber(modid = "soundattract")
public final class FromTheFogEquipmentSyncEvents {
    private static final String FROM_THE_FOG_MOD_ID = "watching";
    private static final String EQUIPMENT_MODEL_TAG = "herobrineModel";

    private FromTheFogEquipmentSyncEvents() {}

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isEquipmentModel(event.getTarget())) {
            return;
        }
        send(player, event.getTarget(), true);
    }

    @SubscribeEvent
    public static void onStopTracking(PlayerEvent.StopTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isEquipmentModel(event.getTarget())) {
            return;
        }
        send(player, event.getTarget(), false);
    }

    private static boolean isEquipmentModel(Entity entity) {
        return ModList.get().isLoaded(FROM_THE_FOG_MOD_ID)
                && entity.getTags().contains(EQUIPMENT_MODEL_TAG);
    }

    private static void send(ServerPlayer player, Entity entity, boolean keepEquipmentVisible) {
        SoundAttractNetwork.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new FromTheFogEquipmentMarkerMessage(entity.getUUID(), keepEquipmentVisible)
        );
    }
}
