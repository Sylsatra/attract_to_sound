package com.example.soundattract.mixin;

import com.example.soundattract.enchantment.ModEnchantments;
import com.example.soundattract.loot.LootModifierTables;
import com.example.soundattract.loot.LootTableMarkerFunction;
import com.example.soundattract.loot.LootTableMarkerState;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Arrays;
import java.util.function.Consumer;

@Mixin(LootTable.class)
public abstract class LootTableMixin {
    @Unique
    private static final ThreadLocal<Deque<soundattract$LootBufferFrame>> soundattract$lootBuffers =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Shadow @Final private LootItemFunction[] functions;

    @ModifyVariable(method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V", at = @At("HEAD"), argsOnly = true)
    private Consumer<ItemStack> soundattract$beginLootBuffer(Consumer<ItemStack> output) {
        boolean targetTable = Arrays.stream(this.functions).anyMatch(LootTableMarkerFunction.class::isInstance);
        soundattract$LootBufferFrame frame = new soundattract$LootBufferFrame(targetTable, output);
        soundattract$lootBuffers.get().push(frame);
        return targetTable ? frame.generated::add : output;
    }

    @Inject(method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V", at = @At("RETURN"))
    private void soundattract$applyGlobalLootModifiers(LootContext context, Consumer<ItemStack> output, CallbackInfo ci) {
        Deque<soundattract$LootBufferFrame> buffers = soundattract$lootBuffers.get();
        if (buffers.isEmpty()) return;

        soundattract$LootBufferFrame frame = buffers.pop();
        if (frame.capture) {
            ResourceLocation tableId = LootTableMarkerState.consume(context);
            if (tableId != null) {
                soundattract$applyModifiers(tableId, frame.generated, context);
            }




            frame.generated.forEach(frame.output);
        }
        if (buffers.isEmpty()) soundattract$lootBuffers.remove();
    }

    @Unique
    private static final class soundattract$LootBufferFrame {
        private final boolean capture;
        private final Consumer<ItemStack> output;
        private final ObjectArrayList<ItemStack> generated = new ObjectArrayList<>();

        private soundattract$LootBufferFrame(boolean capture, Consumer<ItemStack> output) {
            this.capture = capture;
            this.output = output;
        }
    }

    @Unique
    private static void soundattract$applyModifiers(ResourceLocation tableId, List<ItemStack> generated, LootContext context) {
        if (LootModifierTables.ARMOR_TABLES.contains(tableId)
                && context.getRandom().nextFloat() < 0.05F) {
            soundattract$enchantOne(generated, context, LootModifierTables::isEligibleArmor);
        }
        if (LootModifierTables.TOOL_TABLES.contains(tableId)
                && context.getRandom().nextFloat() < 0.05F) {
            soundattract$enchantOne(generated, context, LootModifierTables::isEligibleTool);
        }
        if (LootModifierTables.BOOK_TABLES.contains(tableId)
                && context.getRandom().nextFloat() < 0.05F) {
            generated.add(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(ModEnchantments.CONCEAL, 1)));
        }
    }

    @Unique
    private static void soundattract$enchantOne(List<ItemStack> generated, LootContext context,
                                                 java.util.function.Predicate<ItemStack> itemFilter) {
        List<ItemStack> candidates = generated.stream()
                .filter(stack -> !stack.isEmpty() && !stack.isEnchanted() && itemFilter.test(stack)
                        && ModEnchantments.CONCEAL.canEnchant(stack))
                .toList();
        if (!candidates.isEmpty()) {
            candidates.get(context.getRandom().nextInt(candidates.size())).enchant(ModEnchantments.CONCEAL, 1);
        }
    }
}
