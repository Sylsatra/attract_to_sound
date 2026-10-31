package com.example.soundattract.util;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;

/** Compatibility helpers for UUID values in persistent entity data across the 1.21.11 NBT API. */
public final class NbtCompat {
    private NbtCompat() {}

    public static void putUUID(CompoundTag tag, String key, UUID value) {
        tag.putIntArray(key, UUIDUtil.uuidToIntArray(value));
    }

    public static UUID getUUID(CompoundTag tag, String key) {
        int[] value = tag.getIntArray(key).orElse(new int[0]);
        if (value.length == 4) return UUIDUtil.uuidFromIntArray(value);
        String legacy = tag.getStringOr(key, "");
        if (legacy.isEmpty()) return null;
        try { return UUID.fromString(legacy); } catch (IllegalArgumentException ignored) { return null; }
    }

    public static boolean hasUUID(CompoundTag tag, String key) {
        return getUUID(tag, key) != null;
    }
}
