package com.example.soundattract.client;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(value = Dist.CLIENT)
public final class FromTheFogVisualCompat {
    private static final Set<UUID> EQUIPMENT_MODEL_ENTITIES = ConcurrentHashMap.newKeySet();

    private FromTheFogVisualCompat() {}

    public static boolean shouldKeepInvisibleEquipment(Entity entity) {
        return entity != null && EQUIPMENT_MODEL_ENTITIES.contains(entity.getUUID());
    }

    public static void applyMarker(UUID entityId, boolean keepEquipmentVisible) {
        if (keepEquipmentVisible) EQUIPMENT_MODEL_ENTITIES.add(entityId);
        else EQUIPMENT_MODEL_ENTITIES.remove(entityId);
    }

    static boolean hasMarker(UUID entityId) {
        return EQUIPMENT_MODEL_ENTITIES.contains(entityId);
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) EQUIPMENT_MODEL_ENTITIES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onClientLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) EQUIPMENT_MODEL_ENTITIES.clear();
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        EQUIPMENT_MODEL_ENTITIES.clear();
    }
}
