package com.example.soundattract.loot;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ModLootModifiers {
    private static final Set<ResourceLocation> MODIFIED_MARKER_TABLES = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<ResourceLocation, AtomicLong> MARKER_CALLBACK_COUNTS = new ConcurrentHashMap<>();

    public static void register() {
        LootTableMarkerFunction.register();
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            if (applyMarker(id, tableBuilder)) {
                MODIFIED_MARKER_TABLES.add(id);
                MARKER_CALLBACK_COUNTS.computeIfAbsent(id, ignored -> new AtomicLong()).incrementAndGet();
            }
        });
    }

    /** Test/diagnostic evidence that Fabric's real table-load callback handled this ID. */
    public static boolean wasMarkerTableModified(ResourceLocation id) {
        return id != null && MODIFIED_MARKER_TABLES.contains(id);
    }

    /** Number of times Fabric's real loot reload callback has marked a table. */
    public static long markerCallbackCount(ResourceLocation id) {
        AtomicLong count = id == null ? null : MARKER_CALLBACK_COUNTS.get(id);
        return count == null ? 0L : count.get();
    }

    /** Shared by the reload callback and GameTests to exercise the production marker decision. */
    public static boolean applyMarker(ResourceLocation id, LootTable.Builder tableBuilder) {
        if (LootModifierTables.isMarkerTable(id)) {
            tableBuilder.apply(LootTableMarkerFunction.builder(id));
            return true;
        }
        return false;
    }
}
