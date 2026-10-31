package com.example.soundattract.mixin;

import com.example.soundattract.enchantment.ModEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "hasFoil", at = @At("HEAD"), cancellable = true, remap = false)
    private void soundattract_removeFoilForConceal(CallbackInfoReturnable<Boolean> cir) {
        ItemStack thisStack = (ItemStack) (Object) this;

        ItemEnchantments enchantments = thisStack.get(DataComponents.ENCHANTMENTS);

        if (enchantments != null) {
            for (Holder<Enchantment> enchantmentHolder : enchantments.keySet()) {
                if (enchantmentHolder.is(ModEnchantments.CONCEAL)) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
    }
}