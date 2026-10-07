package com.example.soundattract.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class EnchantmentConceal extends Enchantment {

    public EnchantmentConceal(Rarity pRarity, EnchantmentCategory pCategory, EquipmentSlot[] pApplicableSlots) {
        super(pRarity, pCategory, pApplicableSlots);
    }

    @Override
    public int getMinCost(int pEnchantmentLevel) {
        return 25;
    }

    @Override
    public int getMaxCost(int pEnchantmentLevel) {
        return 50;
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    @Override
    public boolean canEnchant(ItemStack pStack) {
        Item item = pStack.getItem();
        return item instanceof ArmorItem ||
               item instanceof DiggerItem ||
               item instanceof SwordItem ||
               item instanceof BowItem ||
               item instanceof CrossbowItem ||
               item instanceof TridentItem ||
               item instanceof ShieldItem ||
               item instanceof ShearsItem ||
               item instanceof ElytraItem ||
               (item instanceof TieredItem && !(item instanceof SwordItem)) ||
               pStack.isDamageableItem() && !(item instanceof BookItem);
    }

}