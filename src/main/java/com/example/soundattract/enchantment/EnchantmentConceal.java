package com.example.soundattract.enchantment;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

public final class EnchantmentConceal {
    public static boolean canEnchant(ItemStack stack) {
        return stack != null && !stack.isEmpty() && (stack.has(DataComponents.EQUIPPABLE)
                || stack.has(DataComponents.TOOL) || stack.isDamageableItem());
    }

    private EnchantmentConceal() {}
}
