package com.example.soundattract.config;

import com.example.soundattract.Soundattract;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record MobProfile2(
    String id,
    Optional<EntityPredicate> condition,
    List<SoundOverride> soundOverrides,
    Map<PlayerStance, Double> detectionOverrides,
    Optional<ScentProfileConfig> scentConfig
) {
    public static final Codec<EntityPredicate> ENTITY_PREDICATE_CODEC = EntityPredicate.CODEC;

    public static final Codec<MobProfile2> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.optionalFieldOf("id", "unknown").forGetter(MobProfile2::id),
        ENTITY_PREDICATE_CODEC.optionalFieldOf("condition").forGetter(MobProfile2::condition),
        SoundOverride.CODEC.listOf().optionalFieldOf("sound_overrides", List.of()).forGetter(MobProfile2::soundOverrides),
        Codec.unboundedMap(PlayerStance.CODEC, Codec.DOUBLE).optionalFieldOf("detection_overrides", Map.of()).forGetter(MobProfile2::detectionOverrides),
        ScentProfileConfig.CODEC.optionalFieldOf("scent_config").forGetter(MobProfile2::scentConfig)
    ).apply(instance, MobProfile2::new));

    public MobProfile2 withId(String newId) {
        return new MobProfile2(newId, condition, soundOverrides, detectionOverrides, scentConfig);
    }

    public boolean matches(Mob mob) {
        if (condition.isEmpty()) return true;
        if (mob.level() instanceof ServerLevel serverLevel) {
            return condition.get().matches(serverLevel, mob.position(), mob);
        }
        return false;
    }

    /**
     * Entity predicates changed shape in 26.2: the old unnamespaced {@code type}
     * entity-type field is now {@code minecraft:entity_type}. Normalize the old
     * form so existing 1.21.11 datapack profiles retain their meaning.
     */
    public static JsonElement normalizeLegacyPredicate(JsonElement predicate) {
        if (!predicate.isJsonObject()) return predicate;
        JsonObject object = predicate.getAsJsonObject();
        if (object.has("type") && object.get("type").isJsonPrimitive()
                && object.get("type").getAsJsonPrimitive().isString()) {
            JsonElement oldType = object.remove("type");
            if (!object.has("minecraft:entity_type")) object.add("minecraft:entity_type", oldType);
        }
        for (String child : List.of("vehicle", "passenger", "targeted_entity")) {
            if (object.has(child)) normalizeLegacyPredicate(object.get(child));
        }
        return object;
    }

    public Optional<SoundOverride> getSoundOverride(net.minecraft.resources.Identifier soundId) {
        if (soundOverrides == null || soundOverrides.isEmpty()) return Optional.empty();
        for (SoundOverride so : soundOverrides) {
            if (so.getSoundId().equals(soundId)) {
                return Optional.of(so);
            }
        }
        return Optional.empty();
    }

    public Optional<Double> getDetectionOverride(PlayerStance stance) {
        if (detectionOverrides == null) return Optional.empty();
        return Optional.ofNullable(detectionOverrides.get(stance));
    }
}
