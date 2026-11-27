package com.example.soundattract.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;

public class EnchantRandomArmorModifier extends EnchantRandomlyModifier {
    public static final MapCodec<EnchantRandomArmorModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).and(EnchantRandomlyModifier.<EnchantRandomArmorModifier>enchantmentCodec())
                    .apply(instance, EnchantRandomArmorModifier::new));

    public EnchantRandomArmorModifier(LootItemCondition[] conditions, Holder<Enchantment> enchantment) {
        super(conditions, enchantment);
    }

    @Override
    protected boolean isValidItem(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
