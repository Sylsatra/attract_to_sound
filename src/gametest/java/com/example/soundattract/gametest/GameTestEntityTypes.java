package com.example.soundattract.gametest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.projectile.arrow.Arrow;

/** Registry-backed built-in entity types for GameTests (26.2 removed EntityType constants). */
public final class GameTestEntityTypes {
    @SuppressWarnings("unchecked")
    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> get(String path) {
        return (EntityType<T>) (EntityType<?>) BuiltInRegistries.ENTITY_TYPE
                .getValue(Identifier.fromNamespaceAndPath("minecraft", path));
    }

    public static final EntityType<Zombie> ZOMBIE = get("zombie");
    public static final EntityType<Cow> COW = get("cow");
    public static final EntityType<Horse> HORSE = get("horse");
    public static final EntityType<Sheep> SHEEP = get("sheep");
    public static final EntityType<Arrow> ARROW = get("arrow");
    public static final EntityType<Creeper> CREEPER = get("creeper");
    public static final EntityType<EnderMan> ENDERMAN = get("enderman");
    public static final EntityType<Skeleton> SKELETON = get("skeleton");

    private GameTestEntityTypes() {}
}
