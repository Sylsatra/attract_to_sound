package com.example.soundattract.data;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.sounds.SoundEvent;

public final class DataDrivenTags {

    public static final TagKey<EntityType<?>> ATTRACTED_MOBS = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "attracted"));

    public static final TagKey<EntityType<?>> BLACKLISTED_MOBS = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "blacklist"));

    public static final TagKey<SoundEvent> SOUND_WHITELIST = TagKey.create(Registries.SOUND_EVENT,
            Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "whitelist"));

    public static final TagKey<Block> MUFFLING_WOOL = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "muffling/wool"));

    public static final TagKey<Block> MUFFLING_SOLID = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "muffling/solid"));

    public static final TagKey<Block> MUFFLING_NON_SOLID = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "muffling/non_solid"));

    public static final TagKey<Block> MUFFLING_THIN = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "muffling/thin"));

    public static final TagKey<Block> MUFFLING_LIQUID = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "muffling/liquid"));

    public static final TagKey<Block> MUFFLING_AIR = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "muffling/air"));

    public static final TagKey<Block> NON_BLOCKING_VISION = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "vision/non_blocking"));

    public static final TagKey<Item> CAMOUFLAGE_ARMOR = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "camouflage_armor"));

    private DataDrivenTags() {
    }
}
