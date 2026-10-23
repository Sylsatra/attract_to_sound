package com.example.soundattract.integration.immersive_melodies;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import com.example.soundattract.Soundattract;

import java.lang.reflect.Field;

/** Resolves Immersive Melodies' optional item components without linking the mod into class signatures. */
final class ImmersiveMelodiesComponents {
    private static final String INSTRUMENT_ITEM = "immersive_melodies.item.InstrumentItem";
    private static final Identifier DEFAULT_MELODY =
            Identifier.fromNamespaceAndPath("immersive_melodies", "default");

    private static boolean resolved;
    private static DataComponentType<Boolean> playing;
    private static DataComponentType<Identifier> melody;

    private ImmersiveMelodiesComponents() {
    }

    static boolean isPlaying(ItemStack stack) {
        DataComponentType<Boolean> component = playingComponent();
        return component != null && stack.getOrDefault(component, false);
    }

    static String melodyId(ItemStack stack) {
        DataComponentType<Identifier> component = melodyComponent();
        return component == null ? DEFAULT_MELODY.toString() : stack.getOrDefault(component, DEFAULT_MELODY).toString();
    }

    private static synchronized DataComponentType<Boolean> playingComponent() {
        resolve();
        return playing;
    }

    private static synchronized DataComponentType<Identifier> melodyComponent() {
        resolve();
        return melody;
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        if (!FabricLoader.getInstance().isModLoaded("immersive_melodies")) {
            return;
        }

        try {
            Class<?> instrumentItem = Class.forName(INSTRUMENT_ITEM, false,
                    ImmersiveMelodiesComponents.class.getClassLoader());
            playing = readComponent(instrumentItem.getField("PLAYING"));
            melody = readComponent(instrumentItem.getField("MELODY"));
        } catch (ReflectiveOperationException | LinkageError failure) {
            Soundattract.LOGGER.error("Immersive Melodies is present, but its instrument data components could not be resolved", failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> DataComponentType<T> readComponent(Field field) throws ReflectiveOperationException {
        return (DataComponentType<T>) field.get(null);
    }
}
