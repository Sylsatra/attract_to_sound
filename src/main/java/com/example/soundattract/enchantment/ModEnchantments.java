package com.example.soundattract.enchantment;

import com.example.soundattract.Soundattract;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

public final class ModEnchantments {
    public static final ResourceKey<Enchantment> CONCEAL = ResourceKey.create(
            Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(Soundattract.MOD_ID, "conceal"));

    public static void register() {

    }

    public static Holder<Enchantment> conceal(RegistryAccess registries) {
        return registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(CONCEAL);
    }

    private ModEnchantments() {}
}
