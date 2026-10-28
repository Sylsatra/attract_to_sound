package com.example.soundattract.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record ScentEmissionConfig(
    boolean enabled,
    double scentStrengthMultiplier,
    double creationIntervalBlocks,
    boolean showScentParticles,
    String scentParticleColor
) {
    public static final Codec<ScentEmissionConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.BOOL.optionalFieldOf("enabled", true).forGetter(ScentEmissionConfig::enabled),
        Codec.DOUBLE.optionalFieldOf("scent_strength_multiplier", 1.0).forGetter(ScentEmissionConfig::scentStrengthMultiplier),
        Codec.DOUBLE.optionalFieldOf("creation_interval_blocks", 5.0).forGetter(ScentEmissionConfig::creationIntervalBlocks),
        Codec.BOOL.optionalFieldOf("show_scent_particles", true).forGetter(ScentEmissionConfig::showScentParticles),
        Codec.STRING.optionalFieldOf("scent_particle_color", "").forGetter(ScentEmissionConfig::scentParticleColor)
    ).apply(instance, ScentEmissionConfig::new));

    public static final ScentEmissionConfig DEFAULT = new ScentEmissionConfig(true, 1.0, 5.0, true, "");
}
