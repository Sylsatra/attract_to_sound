package com.example.soundattract;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;

public final class NbtCompat {
    private NbtCompat() {}

    public static void putUuid(CompoundTag tag, String key, UUID value) {
        tag.store(key, UUIDUtil.CODEC, value);
    }

    public static UUID getUuid(CompoundTag tag, String key) {
        return tag.read(key, UUIDUtil.CODEC).or(() -> tag.read(key, UUIDUtil.STRING_CODEC)).orElse(null);
    }

    public static boolean hasUuid(CompoundTag tag, String key) {
        return getUuid(tag, key) != null;
    }
}
