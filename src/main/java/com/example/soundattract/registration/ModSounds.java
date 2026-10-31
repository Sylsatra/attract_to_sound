package com.example.soundattract.registration;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, SoundAttractMod.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> WOODEN_FLOOR_CREEK =
            SOUND_EVENTS.register("wooden_floor_creek",
                    () -> SoundEvent.createVariableRangeEvent(
                            Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "wooden_floor_creek")));

    public static void register(IEventBus bus) {
        SOUND_EVENTS.register(bus);
    }

    private ModSounds() {}
}
