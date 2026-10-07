package com.example.soundattract.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import java.util.function.Supplier;

public class EnchantRandomToolModifier extends EnchantRandomlyModifier {
    public static final Supplier<Codec<EnchantRandomToolModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst ->
                    codecStart(inst).and(
                            enchantmentCodecStart(inst)
                    ).apply(inst, EnchantRandomToolModifier::new)
            )
    );

    public EnchantRandomToolModifier(LootItemCondition[] conditionsIn, Enchantment enchantment) {
        super(conditionsIn, enchantment);
    }

    @Override
    protected boolean isValidItem(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof TieredItem ||
               item instanceof BowItem ||
               item instanceof CrossbowItem ||
               item instanceof TridentItem ||
               item instanceof ShieldItem ||
               item instanceof ShearsItem ||
               item instanceof FishingRodItem;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}