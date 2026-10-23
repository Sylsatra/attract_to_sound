package com.example.soundattract.mixin;

import com.example.soundattract.enchantment.EnchantmentConceal;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enchantment.class)
public abstract class EnchantmentConcealMixin {
    @Unique
    private boolean soundattract$isConceal() {
        return ((Enchantment) (Object) this).description().getContents() instanceof TranslatableContents contents
                && "enchantment.soundattract.conceal".equals(contents.getKey());
    }

    @Inject(method = "canEnchant", at = @At("HEAD"), cancellable = true)
    private void soundattract$preserveConcealCanEnchant(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (soundattract$isConceal()) {
            cir.setReturnValue(EnchantmentConceal.canEnchant(stack));
        }
    }

    @Inject(method = "isPrimaryItem", at = @At("HEAD"), cancellable = true)
    private void soundattract$preserveConcealPrimaryItems(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (soundattract$isConceal()) {
            cir.setReturnValue(EnchantmentConceal.canEnchant(stack));
        }
    }
}
