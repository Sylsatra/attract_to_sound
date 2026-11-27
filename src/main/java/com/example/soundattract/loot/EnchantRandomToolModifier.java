package com.example.soundattract.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;

public class EnchantRandomToolModifier extends EnchantRandomlyModifier {
    public static final MapCodec<EnchantRandomToolModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).and(EnchantRandomlyModifier.<EnchantRandomToolModifier>enchantmentCodec())
                    .apply(instance, EnchantRandomToolModifier::new));

    public EnchantRandomToolModifier(LootItemCondition[] conditions, Holder<Enchantment> enchantment) {
        super(conditions, enchantment);
    }

    @Override
    protected boolean isValidItem(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof TieredItem
                || item instanceof BowItem
                || item instanceof CrossbowItem
                || item instanceof TridentItem
                || item instanceof ShieldItem
                || item instanceof ShearsItem
                || item instanceof FishingRodItem;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
