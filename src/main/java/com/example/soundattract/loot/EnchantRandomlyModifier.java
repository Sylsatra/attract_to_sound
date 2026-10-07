package com.example.soundattract.loot;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public abstract class EnchantRandomlyModifier extends LootModifier {
    protected final Enchantment enchantment;

    public EnchantRandomlyModifier(LootItemCondition[] conditionsIn, Enchantment enchantment) {
        super(conditionsIn);
        this.enchantment = enchantment;
    }

    @NotNull
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        List<ItemStack> potentialTargets = generatedLoot.stream()
                .filter(stack -> !stack.isEmpty() && !stack.isEnchanted() && this.isValidItem(stack) && this.enchantment.canEnchant(stack))
                .collect(Collectors.toList());

        if (!potentialTargets.isEmpty()) {
            ItemStack toEnchant = potentialTargets.get(context.getRandom().nextInt(potentialTargets.size()));
            toEnchant.enchant(this.enchantment, 1);
        }

        return generatedLoot;
    }

    protected abstract boolean isValidItem(ItemStack stack);

    public static <T extends EnchantRandomlyModifier> RecordCodecBuilder<T, Enchantment> enchantmentCodecStart(RecordCodecBuilder.Instance<T> instance) {
        return ForgeRegistries.ENCHANTMENTS.getCodec().fieldOf("enchantment").forGetter(m -> m.enchantment);
    }
}