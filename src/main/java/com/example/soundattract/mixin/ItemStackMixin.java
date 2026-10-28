package com.example.soundattract.mixin;

import com.example.soundattract.Soundattract;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "hasFoil", at = @At("RETURN"), cancellable = true)
    private void soundattract_modifyGlintOnReturn(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            ItemStack thisStack = (ItemStack) (Object) this;

            boolean hasConceal = thisStack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                    .keySet().stream().anyMatch(holder -> holder.unwrapKey().map(key -> key.equals(
                            com.example.soundattract.enchantment.ModEnchantments.CONCEAL)).orElse(false));
            if (hasConceal) cir.setReturnValue(false);
        }
    }
}
