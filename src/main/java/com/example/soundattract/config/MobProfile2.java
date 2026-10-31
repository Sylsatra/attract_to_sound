package com.example.soundattract.config;

import com.example.soundattract.SoundAttractMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.criterion.EntityPredicate;
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
