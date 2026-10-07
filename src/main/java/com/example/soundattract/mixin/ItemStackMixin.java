package com.example.soundattract.mixin;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.enchantment.ModEnchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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

            if (ModEnchantments.CONCEAL != null && ModEnchantments.CONCEAL.isPresent()) {
                Enchantment concealEnchantmentInstance = ModEnchantments.CONCEAL.get();

                if (EnchantmentHelper.getEnchantments(thisStack).getOrDefault(concealEnchantmentInstance, 0) > 0) {
                    cir.setReturnValue(false);
                }
            } else {
                if (ModEnchantments.CONCEAL == null) {
                    if (SoundAttractMod.LOGGER != null) {
                        SoundAttractMod.LOGGER.error("[ItemStackMixin @RETURN] ModEnchantments.CONCEAL RegistryObject is null! Critical mod setup issue.");
                    } else {
                        System.err.println("[ItemStackMixin @RETURN] CRITICAL: ModEnchantments.CONCEAL RegistryObject is null!");
                    }
                } else {
                    if (SoundAttractMod.LOGGER != null) {
                        SoundAttractMod.LOGGER.warn("[ItemStackMixin @RETURN] ModEnchantments.CONCEAL is not present. This might be a loading order issue or registration failure.");
                    } else {
                        System.err.println("[ItemStackMixin @RETURN] WARN: ModEnchantments.CONCEAL is not present!");
                    }
                }
            }
        }
    }
}