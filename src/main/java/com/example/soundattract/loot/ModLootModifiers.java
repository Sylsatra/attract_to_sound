package com.example.soundattract.loot;

import com.example.soundattract.SoundAttractMod;
import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIER_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, SoundAttractMod.MOD_ID);

    public static final RegistryObject<Codec<AddItemModifier>> ADD_ITEM =
            LOOT_MODIFIER_SERIALIZERS.register("add_item", AddItemModifier.CODEC);

    public static final RegistryObject<Codec<EnchantRandomArmorModifier>> ENCHANT_RANDOM_ARMOR =
            LOOT_MODIFIER_SERIALIZERS.register("enchant_random_armor", EnchantRandomArmorModifier.CODEC);

    public static final RegistryObject<Codec<EnchantRandomToolModifier>> ENCHANT_RANDOM_TOOL =
            LOOT_MODIFIER_SERIALIZERS.register("enchant_random_tool", EnchantRandomToolModifier.CODEC);

    public static void register(IEventBus eventBus) {
        LOOT_MODIFIER_SERIALIZERS.register(eventBus);
    }
}