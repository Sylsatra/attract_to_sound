package com.example.soundattract.loot;

import com.example.soundattract.enchantment.ModEnchantments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

import java.util.Set;

public final class LootModifierTables {
    public static final Set<ResourceLocation> ARMOR_TABLES = Set.of(
            new ResourceLocation("minecraft", "chests/ancient_city"),
            new ResourceLocation("minecraft", "chests/bastion_treasure"),
            new ResourceLocation("minecraft", "chests/nether_bridge")
    );
    public static final Set<ResourceLocation> TOOL_TABLES = Set.of(
            new ResourceLocation("minecraft", "chests/ancient_city"),
            new ResourceLocation("minecraft", "chests/end_city_treasure"),
            new ResourceLocation("minecraft", "chests/stronghold_crossing"),
            new ResourceLocation("minecraft", "chests/bastion_other")
    );
    public static final Set<ResourceLocation> BOOK_TABLES = Set.of(
            new ResourceLocation("minecraft", "chests/ancient_city"),
            new ResourceLocation("minecraft", "chests/end_city_treasure"),
            new ResourceLocation("minecraft", "chests/stronghold_library"),
            new ResourceLocation("minecraft", "chests/woodland_mansion"),
            new ResourceLocation("minecraft", "chests/buried_treasure"),
            new ResourceLocation("minecraft", "chests/desert_pyramid")
    );
    private static final Set<ResourceLocation> MARKER_TABLES = createMarkerTables();

    public static Set<ResourceLocation> markerTables() {
        return MARKER_TABLES;
    }

    public static boolean isMarkerTable(ResourceLocation tableId) {
        return MARKER_TABLES.contains(tableId);
    }

    public static boolean isEligibleArmor(ItemStack stack) {
        return stack != null && !stack.isEmpty() && !stack.isEnchanted()
                && stack.getItem() instanceof ArmorItem
                && ModEnchantments.CONCEAL.canEnchant(stack);
    }

    public static boolean isEligibleTool(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.isEnchanted()) {
            return false;
        }
        Item item = stack.getItem();
        boolean supportedType = item instanceof TieredItem || item instanceof BowItem
                || item instanceof CrossbowItem || item instanceof TridentItem
                || item instanceof ShieldItem || item instanceof ShearsItem
                || item instanceof FishingRodItem;
        return supportedType && ModEnchantments.CONCEAL.canEnchant(stack);
    }

    private static Set<ResourceLocation> createMarkerTables() {
        Set<ResourceLocation> tables = new java.util.HashSet<>(ARMOR_TABLES);
        tables.addAll(TOOL_TABLES);
        tables.addAll(BOOK_TABLES);
        return Set.copyOf(tables);
    }

    private LootModifierTables() {
    }
}
