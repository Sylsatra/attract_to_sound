package com.example.soundattract.loot;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.LootModifier;

import java.util.ArrayList;
import java.util.List;

public abstract class EnchantRandomlyModifier extends LootModifier {
    private final Holder<Enchantment> enchantment;

    protected EnchantRandomlyModifier(LootItemCondition[] conditions, Holder<Enchantment> enchantment) {
        super(conditions);
        this.enchantment = enchantment;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        List<ItemStack> eligible = new ArrayList<>();
        for (ItemStack stack : generatedLoot) {
            if (!stack.isEmpty() && !stack.isEnchanted() && isValidItem(stack)
                    && stack.supportsEnchantment(enchantment)) {
                eligible.add(stack);
            }
        }
        if (!eligible.isEmpty()) {
            eligible.get(context.getRandom().nextInt(eligible.size())).enchant(enchantment, 1);
        }
        return generatedLoot;
    }

    protected abstract boolean isValidItem(ItemStack stack);

    protected static <T extends EnchantRandomlyModifier> RecordCodecBuilder<T, Holder<Enchantment>> enchantmentCodec() {
        return Enchantment.CODEC.fieldOf("enchantment")
                .forGetter(EnchantRandomlyModifier::configuredEnchantment);
    }

    private Holder<Enchantment> configuredEnchantment() {
        return enchantment;
    }
}
