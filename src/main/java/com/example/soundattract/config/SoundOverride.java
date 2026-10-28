package com.example.soundattract.config;

import net.minecraft.resources.Identifier;

public class SoundOverride {
    private final Identifier soundId;
    private final double range;
    private final double weight;

    public SoundOverride(Identifier soundId, double range, double weight) {
        if (soundId == null) {
            throw new IllegalArgumentException("SoundOverride soundId cannot be null");
        }
        this.soundId = soundId;
        this.range = range;
        this.weight = weight;
    }

    public Identifier getSoundId() {
        return soundId;
    }

    public double getRange() {
        return range;
    }

    public double getWeight() {
        return weight;
    }

    @Override
    public String toString() {
        return "SoundOverride{" +
               "soundId=" + soundId +
               ", range=" + range +
               ", weight=" + weight +
               '}';
    }

    public static SoundOverride parse(String entry) {
        if (entry == null || entry.trim().isEmpty()) {
            throw new IllegalArgumentException("SoundOverride entry cannot be null or empty.");
        }

        String[] parts = entry.trim().split(":", -1);

        if (parts.length < 3 || parts.length > 4) {
            throw new IllegalArgumentException(
                "Malformed SoundOverride entry '" + entry + "'. Expected 3 or 4 parts separated by ':', got " + parts.length + " parts. Format: '[namespace:]path:range:weight'");
        }

        Identifier soundId;
        String rangeStr;
        String weightStr;

        if (parts.length == 4) { 
            if (parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) {
                throw new IllegalArgumentException("Malformed SoundOverride entry '" + entry + "'. Namespace or path part is empty when 4 parts are provided.");
            }
            soundId = Identifier.tryParse(parts[0].trim() + ":" + parts[1].trim());
            rangeStr = parts[2].trim();
            weightStr = parts[3].trim();
        } else {
            if (parts[0].trim().isEmpty()) {
                 throw new IllegalArgumentException("Malformed SoundOverride entry '" + entry + "'. Path part is empty when 3 parts are provided.");
            }
            soundId = Identifier.tryParse("minecraft:" + parts[0].trim());
            rangeStr = parts[1].trim();
            weightStr = parts[2].trim();
        }

        if (soundId == null) {
            throw new IllegalArgumentException("Invalid sound ID format in SoundOverride entry '" + entry + "' from parts '" + (parts.length == 4 ? parts[0]+":"+parts[1] : "minecraft:"+parts[0]) + "'.");
        }

        double range;
        double weight;

        try {
            if (rangeStr.isEmpty()) throw new NumberFormatException("Range string is empty");
            range = Double.parseDouble(rangeStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid range value '" + rangeStr + "' in SoundOverride entry '" + entry + "'", e);
        }

        try {
            if (weightStr.isEmpty()) throw new NumberFormatException("Weight string is empty");
            weight = Double.parseDouble(weightStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid weight value '" + weightStr + "' in SoundOverride entry '" + entry + "'", e);
        }

        return new SoundOverride(soundId, range, weight);
    }
    public static final com.mojang.serialization.Codec<SoundOverride> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
            net.minecraft.resources.Identifier.CODEC.fieldOf("sound").forGetter(SoundOverride::getSoundId),
            com.mojang.serialization.Codec.DOUBLE.optionalFieldOf("range", 32.0).forGetter(SoundOverride::getRange),
            com.mojang.serialization.Codec.DOUBLE.optionalFieldOf("weight", 1.0).forGetter(SoundOverride::getWeight)
    ).apply(instance, SoundOverride::new));
}
