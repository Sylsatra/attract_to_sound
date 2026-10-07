package com.example.soundattract.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import java.util.function.Supplier;

public class EnchantRandomArmorModifier extends EnchantRandomlyModifier {
    public static final Supplier<Codec<EnchantRandomArmorModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst ->
                    codecStart(inst).and(
                            enchantmentCodecStart(inst)
                    ).apply(inst, EnchantRandomArmorModifier::new)
            )
    );

    public EnchantRandomArmorModifier(LootItemCondition[] conditionsIn, Enchantment enchantment) {
        super(conditionsIn, enchantment);
    }

    @Override
    protected boolean isValidItem(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}