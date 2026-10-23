package com.example.soundattract.registration;

import com.example.soundattract.Soundattract;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
    public static SoundEvent WOODEN_FLOOR_CREEK;

    public static void register() {
        WOODEN_FLOOR_CREEK = Registry.register(BuiltInRegistries.SOUND_EVENT,
                Identifier.fromNamespaceAndPath(Soundattract.MOD_ID, "wooden_floor_creek"),
                SoundEvent.createVariableRangeEvent(
                        Identifier.fromNamespaceAndPath(Soundattract.MOD_ID, "wooden_floor_creek")));
    }

    private ModSounds() {}
}
