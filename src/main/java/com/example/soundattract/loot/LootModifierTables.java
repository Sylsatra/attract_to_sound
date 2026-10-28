package com.example.soundattract.loot;

import net.minecraft.core.component.DataComponents;

import com.example.soundattract.enchantment.ModEnchantments;
import com.example.soundattract.enchantment.EnchantmentConceal;
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.tags.ItemTags;

import java.util.Set;

public final class LootModifierTables {
    public static final Set<Identifier> ARMOR_TABLES = Set.of(
            Identifier.fromNamespaceAndPath("minecraft", "chests/ancient_city"),
            Identifier.fromNamespaceAndPath("minecraft", "chests/bastion_treasure"),
            Identifier.fromNamespaceAndPath("minecraft", "chests/nether_bridge")
    );
    public static final Set<Identifier> TOOL_TABLES = Set.of(
            Identifier.fromNamespaceAndPath("minecraft", "chests/ancient_city"),
            Identifier.fromNamespaceAndPath("minecraft", "chests/end_city_treasure"),
            Identifier.fromNamespaceAndPath("minecraft", "chests/stronghold_crossing"),
            Identifier.fromNamespaceAndPath("minecraft", "chests/bastion_other")
    );
    public static final Set<Identifier> BOOK_TABLES = Set.of(
            Identifier.fromNamespaceAndPath("minecraft", "chests/ancient_city"),
            Identifier.fromNamespaceAndPath("minecraft", "chests/end_city_treasure"),
            Identifier.fromNamespaceAndPath("minecraft", "chests/stronghold_library"),
            Identifier.fromNamespaceAndPath("minecraft", "chests/woodland_mansion"),
            Identifier.fromNamespaceAndPath("minecraft", "chests/buried_treasure"),
            Identifier.fromNamespaceAndPath("minecraft", "chests/desert_pyramid")
    );
    private static final Set<Identifier> MARKER_TABLES = createMarkerTables();

    public static Set<Identifier> markerTables() {
        return MARKER_TABLES;
    }

    public static boolean isMarkerTable(Identifier tableId) {
        return MARKER_TABLES.contains(tableId);
    }

    public static boolean isEligibleArmor(ItemStack stack) {
        return stack != null && !stack.isEmpty() && !stack.isEnchanted()
                && stack.is(ItemTags.ARMOR_ENCHANTABLE)
                && EnchantmentConceal.canEnchant(stack);
    }

    public static boolean isEligibleTool(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.isEnchanted()) {
            return false;
        }
        boolean supportedType = !stack.is(ItemTags.ARMOR_ENCHANTABLE)
                && stack.getItem() != Items.ELYTRA
                && (stack.has(DataComponents.TOOL) || stack.isDamageableItem());
        return supportedType && EnchantmentConceal.canEnchant(stack);
    }

    private static Set<Identifier> createMarkerTables() {
        Set<Identifier> tables = new java.util.HashSet<>(ARMOR_TABLES);
        tables.addAll(TOOL_TABLES);
        tables.addAll(BOOK_TABLES);
        return Set.copyOf(tables);
    }

    private LootModifierTables() {
    }
}
