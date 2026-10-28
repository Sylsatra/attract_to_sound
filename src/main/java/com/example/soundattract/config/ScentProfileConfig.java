package com.example.soundattract.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

public record ScentProfileConfig(
    boolean enabled,
    double detectionRange,
    int nodeDurationTicks,
    Optional<Integer> ambushDurationTicks
) {
    public static final Codec<ScentProfileConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.BOOL.optionalFieldOf("enabled", true).forGetter(ScentProfileConfig::enabled),
        Codec.DOUBLE.optionalFieldOf("detection_range", 16.0).forGetter(ScentProfileConfig::detectionRange),
        Codec.INT.optionalFieldOf("node_duration_ticks", 6000).forGetter(ScentProfileConfig::nodeDurationTicks),
        Codec.INT.optionalFieldOf("ambush_duration_ticks").forGetter(ScentProfileConfig::ambushDurationTicks)
    ).apply(instance, ScentProfileConfig::new));

    public static final ScentProfileConfig DEFAULT = new ScentProfileConfig(true, 16.0, 6000, Optional.empty());
}
