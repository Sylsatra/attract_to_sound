package com.example.soundattract.integration.relentlessundead;

import java.util.List;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;

public class RelentlessUndeadIntegration {

    public static boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded("relentlessundead");
    }

    public static boolean getEffectiveZombiesIgnoreHeight() {
        if (isLoaded()) {
            try {
                return Inner.getZombiesIgnoreHeight();
            } catch (Throwable t) {
                Soundattract.LOGGER.error("Failed to get Relentless Undead config", t);
            }
        }
        return SoundAttractConfig.COMMON.zombiesIgnoreHeight.get();
    }

    public static boolean getEffectiveZombiesCanStack() {
        if (isLoaded()) {
             try {
                return Inner.getZombiesCanStack();
             } catch (Throwable t) {
                Soundattract.LOGGER.error("Failed to get Relentless Undead config", t);
             }
        }
        return SoundAttractConfig.COMMON.zombiesCanStack.get();
    }

    public static double getEffectiveZombieFallDamageMultiplier() {
        if (isLoaded()) {
             try {
                return Inner.getZombieFallDamageMultiplier();
             } catch (Throwable t) {
                Soundattract.LOGGER.error("Failed to get Relentless Undead config", t);
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
                        Soundattract.LOGGER.error("Failed to check Relentless Undead eligibility", t);
                   }
              } else {
                   List<? extends String> list = SoundAttractConfig.COMMON.relentlessEligibleMobs.get();
                   Identifier mobId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
                   return mobId != null && list.contains(mobId.toString());
              }
         }
         return false;
    }

    private static class Inner {
         static boolean getZombiesIgnoreHeight() { return false; }
         static boolean getZombiesCanStack() { return false; }
         static double getZombieFallDamageMultiplier() { return 1.0; }
         static boolean isMobEligible(Mob mob) {
             net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> hordeTag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("forge", "hordes"));
             return mob.getType().is(hordeTag);
         }
    }
}
