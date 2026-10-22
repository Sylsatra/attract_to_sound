package com.example.soundattract.enchantment;

import net.minecraft.world.item.*;

/** Item support predicate retained from the pre-1.21 enchantment implementation. */
public final class EnchantmentConceal {
    public static boolean canEnchant(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof ArmorItem || item instanceof DiggerItem || item instanceof SwordItem
                || item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem
                || item instanceof ShieldItem || item instanceof ShearsItem || item instanceof ElytraItem
                || item instanceof TieredItem && !(item instanceof SwordItem)
                || stack.isDamageableItem() && !(item instanceof BookItem);
    }
    private EnchantmentConceal() {}
}
