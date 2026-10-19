package com.example.soundattract.component;

import dev.onyxstudios.cca.api.v3.component.Component;
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
    public void readFromNbt(CompoundTag tag) {
        for (String key : java.util.Set.copyOf(data.getAllKeys())) data.remove(key);
        data.merge(tag.copy());
    }

    @Override
    public void writeToNbt(CompoundTag tag) {
        tag.merge(data.copy());
    }
}
