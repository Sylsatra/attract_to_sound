package com.example.soundattract.registration;

import com.example.soundattract.Soundattract;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class ModParticles {
    public static final SimpleParticleType WIND_SCENT_WISP = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE,
            Identifier.fromNamespaceAndPath(Soundattract.MOD_ID, "wind_scent_wisp"),
            new SimpleParticleType(false) {});

    public static void register() {
    }

    private ModParticles() {
    }
}
