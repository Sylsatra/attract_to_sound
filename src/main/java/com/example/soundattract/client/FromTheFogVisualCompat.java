package com.example.soundattract.client;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;

public final class FromTheFogVisualCompat {
    private static final Set<UUID> EQUIPMENT_MODEL_ENTITIES = ConcurrentHashMap.newKeySet();

    private FromTheFogVisualCompat() {
    }

    public static boolean shouldKeepInvisibleEquipment(Entity entity) {
        return entity != null && isEquipmentModelEntity(entity.getUUID());
    }

    static boolean isEquipmentModelEntity(UUID entityId) {
        return entityId != null && EQUIPMENT_MODEL_ENTITIES.contains(entityId);
    }

    public static void applyMarker(UUID entityId, boolean keepEquipmentVisible) {
        if (entityId == null) {
            return;
        }
        if (keepEquipmentVisible) {
            EQUIPMENT_MODEL_ENTITIES.add(entityId);
        } else {
            EQUIPMENT_MODEL_ENTITIES.remove(entityId);
        }
    }

    public static void clear() {
        EQUIPMENT_MODEL_ENTITIES.clear();
    }
}
