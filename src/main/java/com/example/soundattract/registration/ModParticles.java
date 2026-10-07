package com.example.soundattract.registration;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, SoundAttractMod.MOD_ID);

    public static final RegistryObject<SimpleParticleType> WIND_SCENT_WISP =
            PARTICLE_TYPES.register("wind_scent_wisp", () -> new SimpleParticleType(false));

    private ModParticles() {
    }

    public static void register(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }
}
