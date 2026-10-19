package com.example.soundattract.loot;

import com.example.soundattract.Soundattract;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.Serializer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.core.Registry;

public final class LootTableMarkerFunction implements LootItemFunction {
    private static final LootItemFunctionType TYPE = Registry.register(
            BuiltInRegistries.LOOT_FUNCTION_TYPE,
            new ResourceLocation(Soundattract.MOD_ID, "loot_table_marker"),
            new LootItemFunctionType(new Serializer<LootTableMarkerFunction>() {
                @Override
                public void serialize(JsonObject json, LootTableMarkerFunction function, com.google.gson.JsonSerializationContext context) {
                    json.addProperty("table", function.tableId.toString());
                }

                @Override
                public LootTableMarkerFunction deserialize(JsonObject json, com.google.gson.JsonDeserializationContext context) {
                    return new LootTableMarkerFunction(ResourceLocation.tryParse(GsonHelper.getAsString(json, "table")));
                }
            })
    );

    private final ResourceLocation tableId;

    /** Force the function type to register during mod initialization, before
     * loot tables are loaded and the built-in registries are frozen. */
    public static void register() {

        if (TYPE == null) {
            throw new IllegalStateException("Loot table marker function type failed to register");
        }
    }

    public LootTableMarkerFunction(ResourceLocation tableId) {
        this.tableId = tableId;
    }

    @Override
    public ItemStack apply(ItemStack stack, LootContext context) {
        LootTableMarkerState.mark(context, tableId);
        return stack;
    }

    @Override
    public LootItemFunctionType getType() {
        return TYPE;
    }

    public static LootItemFunction.Builder builder(ResourceLocation tableId) {
        return () -> new LootTableMarkerFunction(tableId);
    }
}
