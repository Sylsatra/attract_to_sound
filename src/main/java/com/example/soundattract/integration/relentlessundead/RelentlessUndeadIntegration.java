package com.example.soundattract.integration.relentlessundead;

import java.util.List;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.fml.ModList;

public class RelentlessUndeadIntegration {

    public static boolean isLoaded() {
        return ModList.get().isLoaded("relentlessundead");
    }

    public static boolean getEffectiveZombiesIgnoreHeight() {
        if (isLoaded()) {
            try {
                return Inner.getZombiesIgnoreHeight();
            } catch (Throwable t) {
                SoundAttractMod.LOGGER.error("Failed to get Relentless Undead config", t);
            }
        }
        return SoundAttractConfig.COMMON.zombiesIgnoreHeight.get();
    }

    public static boolean getEffectiveZombiesCanStack() {
        if (isLoaded()) {
             try {
                return Inner.getZombiesCanStack();
             } catch (Throwable t) {
                SoundAttractMod.LOGGER.error("Failed to get Relentless Undead config", t);
             }
        }
        return SoundAttractConfig.COMMON.zombiesCanStack.get();
    }

    public static double getEffectiveZombieFallDamageMultiplier() {
        if (isLoaded()) {
             try {
                return Inner.getZombieFallDamageMultiplier();
             } catch (Throwable t) {
                SoundAttractMod.LOGGER.error("Failed to get Relentless Undead config", t);
             }
        }
        return SoundAttractConfig.COMMON.zombieFallDamageMultiplier.get();
    }

    public static boolean isMobEligibleForClimbing(Mob mob) {
         if (SoundAttractConfig.COMMON.enableRelentlessClimbing.get()) {
              if (isLoaded()) {
                   try {
                        return Inner.isMobEligible(mob);
                   } catch (Throwable t) {
                        SoundAttractMod.LOGGER.error("Failed to check Relentless Undead eligibility", t);
                   }
              } else {
                   List<? extends String> list = SoundAttractConfig.COMMON.relentlessEligibleMobs.get();
                   ResourceLocation mobId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
                   return mobId != null && list.contains(mobId.toString());
              }
         }
         return false;
    }

    private static class Inner {
         static boolean getZombiesIgnoreHeight() {
             return com.raiiiden.relentlessundead.common.config.RelentlessUndeadConfig.COMMON.zombiesIgnoreHeight.get();
         }
         static boolean getZombiesCanStack() {
             return com.raiiiden.relentlessundead.common.config.RelentlessUndeadConfig.COMMON.zombiesCanStack.get();
         }
         static double getZombieFallDamageMultiplier() {
             return com.raiiiden.relentlessundead.common.config.RelentlessUndeadConfig.COMMON.zombieFallDamageMultiplier.get();
         }
         static boolean isMobEligible(Mob mob) {
             net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> hordeTag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("forge", "hordes"));
             return mob.getType().is(hordeTag);
         }
    }
}