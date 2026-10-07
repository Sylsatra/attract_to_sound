package com.example.soundattract.enchantment;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, SoundAttractMod.MOD_ID);

    public static final RegistryObject<Enchantment> CONCEAL =
            ENCHANTMENTS.register("conceal",
                    () -> new EnchantmentConceal(
                            net.minecraft.world.item.enchantment.Enchantment.Rarity.VERY_RARE,
                            EnchantmentCategory.BREAKABLE,
                            new EquipmentSlot[]{
                                    EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
                                    EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                                    EquipmentSlot.LEGS, EquipmentSlot.FEET
                            }
                    ));

    public static void register(IEventBus eventBus) {
        ENCHANTMENTS.register(eventBus);
    }
}