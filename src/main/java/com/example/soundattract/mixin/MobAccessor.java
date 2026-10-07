package com.example.soundattract.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Mob.class)
public interface MobAccessor {
    @Accessor("persistenceRequired")
    void setPersistenceRequiredFlag(boolean persistenceRequired);

    @Accessor("persistenceRequired")
    boolean isPersistenceRequiredFlag();
}