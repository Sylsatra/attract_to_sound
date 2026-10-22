package com.example.soundattract.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.example.soundattract.Soundattract;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;

public final class LootTableMarkerFunction implements LootItemFunction {
    private static final MapCodec<LootTableMarkerFunction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(ResourceLocation.CODEC.fieldOf("table").forGetter(function -> function.tableId))
            .apply(instance, LootTableMarkerFunction::new));
    private static final LootItemFunctionType<LootTableMarkerFunction> TYPE = Registry.register(
            BuiltInRegistries.LOOT_FUNCTION_TYPE,
            ResourceLocation.fromNamespaceAndPath(Soundattract.MOD_ID, "loot_table_marker"),
            new LootItemFunctionType<>(CODEC));

    private final ResourceLocation tableId;

    public static void register() {
        if (TYPE == null) throw new IllegalStateException("Loot table marker function type failed to register");
    }

    public LootTableMarkerFunction(ResourceLocation tableId) { this.tableId = tableId; }

    @Override public ItemStack apply(ItemStack stack, LootContext context) {
        LootTableMarkerState.mark(context, tableId);
        return stack;
    }

    @Override public LootItemFunctionType<LootTableMarkerFunction> getType() { return TYPE; }

    public static LootItemFunction.Builder builder(ResourceLocation tableId) {
        return () -> new LootTableMarkerFunction(tableId);
    }
}
