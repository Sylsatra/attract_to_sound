package com.example.soundattract.component;

import org.ladysnake.cca.api.v3.component.Component;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/** Fabric persistence replacement for Forge's Entity#getPersistentData used by bridge recovery. */
public final class LivingBridgeEntityData implements Component {
    private final CompoundTag data = new CompoundTag();

    public CompoundTag data() {
        return data;
    }

    public static CompoundTag get(Entity entity) {
        return ModComponents.LIVING_BRIDGE_DATA.get(entity).data();
    }

    @Override
    public void readData(ValueInput input) {
        CompoundTag tag = input.read("data", CompoundTag.CODEC).orElseGet(CompoundTag::new);
        for (String key : java.util.Set.copyOf(data.keySet())) data.remove(key);
        data.merge(tag.copy());
    }

    @Override
    public void writeData(ValueOutput output) {
        output.store("data", CompoundTag.CODEC, data.copy());
    }
}
