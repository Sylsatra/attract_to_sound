package com.example.soundattract.scents;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class ScentNode {
    private final Vec3 position;
    private final long timestamp;
    private final float strength;
    private final UUID ownerUUID;
    private final ScentSourceType sourceType;

    public ScentNode(Vec3 position, long timestamp, float strength, UUID ownerUUID) {
        this(position, timestamp, strength, ownerUUID, ScentSourceType.PLAYER_WALK);
    }

    public ScentNode(Vec3 position, long timestamp, float strength, UUID ownerUUID, ScentSourceType sourceType) {
        this.position = position;
        this.timestamp = timestamp;
        this.strength = strength;
        this.ownerUUID = ownerUUID;
        this.sourceType = sourceType != null ? sourceType : ScentSourceType.PLAYER_WALK;
    }

    public Vec3 getPosition() {
        return position;
    }

    public BlockPos getBlockPos() {
        return BlockPos.containing(position);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public float getStrength() {
        return strength;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public ScentSourceType getSourceType() {
        return sourceType;
    }

    public static ScentNode load(CompoundTag tag) {
        double x = tag.getDoubleOr("X", 0.0D);
        double y = tag.getDoubleOr("Y", 0.0D);
        double z = tag.getDoubleOr("Z", 0.0D);
        long ts = tag.getLongOr("Timestamp", 0L);
        float str = tag.getFloatOr("Strength", 0.0F);
        UUID owner = com.example.soundattract.util.NbtCompat.getUUID(tag, "Owner");
        ScentSourceType type = tag.contains("SourceType") ? ScentSourceType.fromNameOrDefault(tag.getStringOr("SourceType", "")) : ScentSourceType.PLAYER_WALK;

        return new ScentNode(new Vec3(x, y, z), ts, str, owner, type);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("X", position.x);
        tag.putDouble("Y", position.y);
        tag.putDouble("Z", position.z);
        tag.putLong("Timestamp", timestamp);
        tag.putFloat("Strength", strength);
        if (ownerUUID != null) {
            com.example.soundattract.util.NbtCompat.putUUID(tag, "Owner", ownerUUID);
        }
        tag.putString("SourceType", sourceType.name());
        return tag;
    }
}
