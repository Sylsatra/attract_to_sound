package com.example.soundattract.config;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.Optional;

public record PlayerProfile2(
    String id,
    Optional<EntityPredicate> condition,
    Map<PlayerStance, Double> detectionOverrides,
    Optional<ScentEmissionConfig> scentEmission,
    Optional<ScentVisibilityConfig> scentVisibility
) {
    public static final Codec<EntityPredicate> ENTITY_PREDICATE_CODEC = EntityPredicate.CODEC;

    public static final Codec<PlayerProfile2> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.optionalFieldOf("id", "unknown").forGetter(PlayerProfile2::id),
        ENTITY_PREDICATE_CODEC.optionalFieldOf("condition").forGetter(PlayerProfile2::condition),
        Codec.unboundedMap(PlayerStance.CODEC, Codec.DOUBLE).optionalFieldOf("detection_overrides", Map.of()).forGetter(PlayerProfile2::detectionOverrides),
        ScentEmissionConfig.CODEC.optionalFieldOf("scent_emission").forGetter(PlayerProfile2::scentEmission),
        ScentVisibilityConfig.CODEC.optionalFieldOf("scent_visibility").forGetter(PlayerProfile2::scentVisibility)
    ).apply(instance, PlayerProfile2::new));

    public PlayerProfile2 withId(String newId) {
        return new PlayerProfile2(newId, condition, detectionOverrides, scentEmission, scentVisibility);
    }

    public boolean matches(Player player) {
        if (condition.isEmpty()) return true;
        if (player instanceof ServerPlayer serverPlayer) {
            return condition.get().matches((ServerLevel) serverPlayer.level(), serverPlayer.position(), serverPlayer);
        }
        return false;
    }

    public Optional<Double> getDetectionOverride(PlayerStance stance) {
        if (detectionOverrides == null) return Optional.empty();
        return Optional.ofNullable(detectionOverrides.get(stance));
    }
}
