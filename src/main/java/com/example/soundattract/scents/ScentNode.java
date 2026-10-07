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
        double x = tag.getDouble("X");
        double y = tag.getDouble("Y");
        double z = tag.getDouble("Z");
        long ts = tag.getLong("Timestamp");
        float str = tag.getFloat("Strength");
        UUID owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ScentSourceType type = tag.contains("SourceType") ? ScentSourceType.fromNameOrDefault(tag.getString("SourceType")) : ScentSourceType.PLAYER_WALK;

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
            tag.putUUID("Owner", ownerUUID);
        }
        tag.putString("SourceType", sourceType.name());
        return tag;
    }
}