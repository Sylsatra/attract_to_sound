package com.example.soundattract.integration.relentlessundead;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.fml.ModList;

import java.util.function.Supplier;

public final class RelentlessUndeadIntegration {
    private static final String MOD_ID = "relentlessundead";
    private static final TagKey<EntityType<?>> HORDES = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("forge", "hordes"));

    private RelentlessUndeadIntegration() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean getEffectiveZombiesIgnoreHeight() {
        return isLoaded() ? read(UpstreamConfig.IGNORE_HEIGHT, Boolean.class, SoundAttractConfig.COMMON.zombiesIgnoreHeight)
                : SoundAttractConfig.COMMON.zombiesIgnoreHeight.get();
    }

    public static boolean getEffectiveZombiesCanStack() {
        return isLoaded() ? read(UpstreamConfig.CAN_STACK, Boolean.class, SoundAttractConfig.COMMON.zombiesCanStack)
                : SoundAttractConfig.COMMON.zombiesCanStack.get();
    }

    public static double getEffectiveZombieFallDamageMultiplier() {
        return isLoaded() ? read(UpstreamConfig.FALL_DAMAGE_MULTIPLIER, Double.class, SoundAttractConfig.COMMON.zombieFallDamageMultiplier)
                : SoundAttractConfig.COMMON.zombieFallDamageMultiplier.get();
    }

    public static boolean isMobEligibleForClimbing(Mob mob) {
        if (!SoundAttractConfig.COMMON.enableRelentlessClimbing.get()) return false;
        if (isLoaded()) return mob.getType().is(HORDES);

        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return mobId != null && SoundAttractConfig.COMMON.relentlessEligibleMobs.get().contains(mobId.toString());
    }

    private static <T> T read(Supplier<?> upstream, Class<T> type, Supplier<T> fallback) {
        if (upstream != null) {
            try {
                Object value = upstream.get();
                if (type.isInstance(value)) return type.cast(value);
            } catch (RuntimeException | LinkageError ignored) {
                return fallback.get();
            }
        }
        return fallback.get();
    }

    private static final class UpstreamConfig {
        private static final Object COMMON = findCommon();
        private static final Supplier<?> IGNORE_HEIGHT = findValue("zombiesIgnoreHeight");
        private static final Supplier<?> CAN_STACK = findValue("zombiesCanStack");
        private static final Supplier<?> FALL_DAMAGE_MULTIPLIER = findValue("zombieFallDamageMultiplier");

        private static Object findCommon() {
            try {
                Class<?> configType = Class.forName("com.raiiiden.relentlessundead.common.config.RelentlessUndeadConfig");
                return configType.getField("COMMON").get(null);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                SoundAttractMod.LOGGER.warn("Could not access the optional Relentless Undead config", exception);
                return null;
            }
        }

        private static Supplier<?> findValue(String name) {
            if (COMMON == null) return null;
            try {
                Object value = COMMON.getClass().getField(name).get(COMMON);
                if (value instanceof Supplier<?> supplier) return supplier;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                SoundAttractMod.LOGGER.warn("Could not access the optional Relentless Undead setting {}", name, exception);
            }
            return null;
        }
    }
}
