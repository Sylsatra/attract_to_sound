package com.example.soundattract.loot;

import com.example.soundattract.enchantment.ModEnchantments;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@GameTestHolder("soundattract")
@PrefixGameTestTemplate(false)
public final class ConcealLootGameTests {
    private ConcealLootGameTests() {
    }

    @GameTest(template = "empty")
    public static void toolModifierSelectsOneRandomUnenchantedEligibleStack(GameTestHelper helper) {
        var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        EnchantRandomToolModifier modifier = EnchantRandomToolModifier.CODEC.codec().parse(ops,
                JsonParser.parseString("""
                        {"conditions": [], "enchantment": "minecraft:unbreaking"}
                        """)).getOrThrow();
        Holder<Enchantment> unbreaking = enchantment(helper, Enchantments.UNBREAKING);
        Holder<Enchantment> mending = enchantment(helper, Enchantments.MENDING);
        for (Item item : List.of(Items.IRON_SWORD, Items.IRON_PICKAXE, Items.BOW, Items.CROSSBOW,
                Items.TRIDENT, Items.SHIELD, Items.SHEARS, Items.FISHING_ROD)) {
            ItemStack stack = new ItemStack(item);
            modifier.apply(new ObjectArrayList<>(List.of(stack)), context(helper));
            helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(unbreaking, stack) == 1,
                    "Every Forge tool category should accept the configured enchantment");
        }

        ItemStack alreadyEnchanted = new ItemStack(Items.IRON_SWORD);
        alreadyEnchanted.enchant(mending, 1);
        ItemStack armor = new ItemStack(Items.IRON_HELMET);
        ItemStack first = new ItemStack(Items.IRON_PICKAXE);
        ItemStack second = new ItemStack(Items.IRON_AXE);
        modifier.apply(new ObjectArrayList<>(List.of(ItemStack.EMPTY, alreadyEnchanted, armor, first, second)),
                context(helper));
        helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(unbreaking, second) == 1,
                "The seeded loot context should select the second eligible stack");
        helper.assertTrue(!first.isEnchanted() && !armor.isEnchanted()
                        && EnchantmentHelper.getItemEnchantmentLevel(unbreaking, alreadyEnchanted) == 0,
                "The tool modifier should enchant one eligible item and preserve prior enchantments");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void armorModifierAndConcealBookUseTheirDatapackValues(GameTestHelper helper) throws IOException {
        var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        EnchantRandomArmorModifier modifier = EnchantRandomArmorModifier.CODEC.codec().parse(ops,
                JsonParser.parseString("""
                        {"conditions": [], "enchantment": "soundattract:conceal"}
                        """)).getOrThrow();
        AddItemModifier customItem = AddItemModifier.CODEC.codec().parse(ops, JsonParser.parseString("""
                {"conditions": [], "item": {"id": "minecraft:diamond", "count": 3}}
                """)).getOrThrow();
        LootContext lootContext = context(helper);
        Holder<Enchantment> conceal = enchantment(helper, ModEnchantments.CONCEAL);
        ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
        ItemStack first = new ItemStack(Items.IRON_HELMET);
        ItemStack second = new ItemStack(Items.IRON_BOOTS);
        var loot = new ObjectArrayList<>(List.of(tool, first, second));
        modifier.apply(loot, lootContext);
        helper.assertTrue(!tool.isEnchanted() && !first.isEnchanted()
                        && EnchantmentHelper.getItemEnchantmentLevel(conceal, second) == 1,
                "The armor modifier should select one unenchanted armor item using its configured enchantment");

        customItem.apply(loot, lootContext);
        loot.getLast().shrink(1);
        customItem.apply(loot, lootContext);
        helper.assertTrue(loot.getLast().is(Items.DIAMOND) && loot.getLast().getCount() == 3,
                "The item modifier should append fresh copies of the configured item");

        ResourceLocation definition = ResourceLocation.fromNamespaceAndPath(
                "soundattract", "loot_modifiers/add_conceal_book.json");
        AddItemModifier suppliedBook;
        try (var reader = helper.getLevel().getServer().getResourceManager().getResourceOrThrow(definition).openAsReader()) {
            suppliedBook = AddItemModifier.CODEC.codec().parse(ops, JsonParser.parseReader(reader)).getOrThrow();
        }
        ItemStack book = suppliedBook.doApply(new ObjectArrayList<>(), lootContext).getFirst();
        helper.assertTrue(book.is(Items.ENCHANTED_BOOK)
                        && book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
                                .getLevel(conceal) == 1,
                "The default loot resource must create a book with stored level-one Conceal");
        helper.assertTrue(conceal.value().getMinCost(1) == 25 && conceal.value().getMaxCost(1) == 50,
                "Conceal must retain the Forge enchantment costs");
        helper.succeed();
    }

    private static Holder<Enchantment> enchantment(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
    }

    private static LootContext context(GameTestHelper helper) {
        LootParams parameters = new LootParams.Builder(helper.getLevel()).create(LootContextParamSets.EMPTY);
        return new LootContext.Builder(parameters).withOptionalRandomSeed(1L).create(Optional.empty());
    }
}
