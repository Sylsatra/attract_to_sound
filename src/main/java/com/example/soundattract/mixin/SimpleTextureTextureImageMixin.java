package com.example.soundattract.mixin;

import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.example.soundattract.camo.CamoTextureGenerator;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

@Mixin(targets = "net.minecraft.client.renderer.texture.SimpleTexture$TextureImage")
public abstract class SimpleTextureTextureImageMixin {

    private static final Map<Class<?>, Field> OUTER_FIELD_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Class<?>, Field> LOCATION_FIELD_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    @Inject(method = "close", at = @At("HEAD"), require = 0)
    private void soundattract$onTextureImageClose(CallbackInfo ci) {
        try {
            Object outer = findOuterInstance(this);
            if (outer == null) return;

            Identifier loc = findLocationOnOuter(outer);
            if (loc != null) {
                CamoTextureGenerator.releaseCapturedMask(loc);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object findOuterInstance(Object innerInstance) {
        if (innerInstance == null) return null;
        Class<?> cls = innerInstance.getClass();

        if (OUTER_FIELD_CACHE.containsKey(cls)) {
            Field cached = OUTER_FIELD_CACHE.get(cls);
            return getFieldValueSafely(cached, innerInstance);
        }

        Field found = findOuterFieldRecursive(cls);
        OUTER_FIELD_CACHE.put(cls, found);
        return getFieldValueSafely(found, innerInstance);
    }

    private static Field findOuterFieldRecursive(Class<?> cls) {
        Class<?> c = cls;
        while (c != null) {
            try {
                Field f = c.getDeclaredField("this$0");
                if (f != null) {
                    f.setAccessible(true);
                    if (SimpleTexture.class.isAssignableFrom(f.getType())) return f;
                }
            } catch (NoSuchFieldException ignored) {}

            try {
                Field[] declared = c.getDeclaredFields();
                for (Field f : declared) {
                    if (SimpleTexture.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        return f;
                    }
                }
            } catch (Throwable ignored) {}

            c = c.getSuperclass();
        }
        return null;
    }

    private static Identifier findLocationOnOuter(Object outerInstance) {
        if (outerInstance == null) return null;
        Class<?> cls = outerInstance.getClass();

        if (LOCATION_FIELD_CACHE.containsKey(cls)) {
            Field cached = LOCATION_FIELD_CACHE.get(cls);
            return (Identifier) getFieldValueSafely(cached, outerInstance);
        }

        Field found = findLocationFieldRecursive(cls);
        LOCATION_FIELD_CACHE.put(cls, found);
        return (Identifier) getFieldValueSafely(found, outerInstance);
    }

    private static Field findLocationFieldRecursive(Class<?> cls) {
        String[] candidateNames = {"location", "f_118129_", "field_118129", "field_118129"};
        Class<?> c = cls;
        while (c != null) {
            for (String name : candidateNames) {
                try {
                    Field f = c.getDeclaredField(name);
                    if (Identifier.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        return f;
                    }
                } catch (NoSuchFieldException ignored) {}
            }

            try {
                Field[] declared = c.getDeclaredFields();
                for (Field f : declared) {
                    if (Identifier.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        return f;
                    }
                }
            } catch (Throwable ignored) {}

            c = c.getSuperclass();
        }
        return null;
    }

    private static Object getFieldValueSafely(Field field, Object instance) {
        if (field == null) return null;
        try {
            return field.get(instance);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
