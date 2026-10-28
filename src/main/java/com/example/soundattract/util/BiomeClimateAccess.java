package com.example.soundattract.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.world.level.biome.Biome;

/** Reads the biome downfall field absent from the Fabric 1.20.1 named API. */
public final class BiomeClimateAccess {
    private static final Field CLIMATE_SETTINGS = findField(Biome.class, "climateSettings", "field_26393");
    private static final Method DOWNFALL = findMethod(CLIMATE_SETTINGS.getType(), "downfall", "comp_846");

    private BiomeClimateAccess() {}

    public static float getDownfall(Biome biome) {
        try {
            Object climateSettings = CLIMATE_SETTINGS.get(biome);
            return ((Number) DOWNFALL.invoke(climateSettings)).floatValue();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read biome downfall", e);
        }
    }

    private static Field findField(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {

            }
        }
        throw new ExceptionInInitializerError("No biome climate field found in " + owner.getName());
    }

    private static Method findMethod(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Method method = owner.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {

            }
        }
        throw new ExceptionInInitializerError("No biome downfall accessor found in " + owner.getName());
    }
}
