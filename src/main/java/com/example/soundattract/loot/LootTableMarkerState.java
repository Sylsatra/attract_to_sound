package com.example.soundattract.loot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootContext;

import java.util.Map;
import java.util.WeakHashMap;

public final class LootTableMarkerState {
    private static final Map<LootContext, ResourceLocation> TABLES = new WeakHashMap<>();

    private LootTableMarkerState() {
    }

    public static synchronized void mark(LootContext context, ResourceLocation tableId) {
        if (context != null && tableId != null) {
            TABLES.put(context, tableId);
        }
    }

    public static synchronized ResourceLocation consume(LootContext context) {
        return TABLES.remove(context);
    }
}
