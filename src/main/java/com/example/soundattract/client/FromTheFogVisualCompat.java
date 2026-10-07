package com.example.soundattract.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "soundattract", value = Dist.CLIENT)
public final class FromTheFogVisualCompat {
    private static final Set<UUID> EQUIPMENT_MODEL_ENTITIES = ConcurrentHashMap.newKeySet();

    private FromTheFogVisualCompat() {}

    public static boolean shouldKeepInvisibleEquipment(Entity entity) {
        return entity != null && EQUIPMENT_MODEL_ENTITIES.contains(entity.getUUID());
    }

    public static void applyMarker(UUID entityId, boolean keepEquipmentVisible) {
        if (keepEquipmentVisible) {
            EQUIPMENT_MODEL_ENTITIES.add(entityId);
        } else {
            EQUIPMENT_MODEL_ENTITIES.remove(entityId);
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            EQUIPMENT_MODEL_ENTITIES.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onClientLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            EQUIPMENT_MODEL_ENTITIES.clear();
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        EQUIPMENT_MODEL_ENTITIES.clear();
    }
}
