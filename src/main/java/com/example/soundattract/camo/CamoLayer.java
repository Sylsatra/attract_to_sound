package com.example.soundattract.camo;

import net.minecraft.nbt.CompoundTag;

public record CamoLayer(int color, String category, float upperDurability, float lowerDurability, boolean blocksScent, long appliedTick, long seed, float erosion, float humidity, float temperature) {

    public CamoLayer withDurability(float newUpper, float newLower) {
        return new CamoLayer(this.color, this.category, newUpper, newLower, this.blocksScent, this.appliedTick, this.seed, this.erosion, this.humidity, this.temperature);
    }

    public float durability() {
        return (upperDurability + lowerDurability) / 2.0f;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Color", color);
        tag.putString("Category", category);
        tag.putFloat("DurabilityUpper", upperDurability);
        tag.putFloat("DurabilityLower", lowerDurability);
        tag.putBoolean("BlocksScent", blocksScent);
        tag.putLong("AppliedTick", appliedTick);
        tag.putLong("Seed", seed);
        tag.putFloat("Erosion", erosion);
        tag.putFloat("Humidity", humidity);
        tag.putFloat("Temperature", temperature);
        return tag;
    }

    public static CamoLayer load(CompoundTag tag) {
        float upper = tag.contains("DurabilityUpper") ? tag.getFloat("DurabilityUpper") : tag.getFloat("Durability");
        float lower = tag.contains("DurabilityLower") ? tag.getFloat("DurabilityLower") : tag.getFloat("Durability");
        return new CamoLayer(
            tag.getInt("Color"),
            tag.getString("Category"),
            upper,
            lower,
            tag.getBoolean("BlocksScent"),
            tag.getLong("AppliedTick"),
            tag.getLong("Seed"),
            tag.getFloat("Erosion"),
            tag.getFloat("Humidity"),
            tag.getFloat("Temperature")
        );
    }
}