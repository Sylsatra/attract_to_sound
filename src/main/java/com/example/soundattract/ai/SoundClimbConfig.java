package com.example.soundattract.ai;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;

/** Local settings for Sound Attract's horde climbing behavior. */
public final class SoundClimbConfig {
    private SoundClimbConfig() {
    }

    public static boolean isMobEligible(Mob mob) {
        if (!SoundAttractConfig.COMMON.enableRelentlessClimbing.get() || mob == null) {
            return false;
        }
        var mobId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return mobId != null && SoundAttractConfig.COMMON.relentlessEligibleMobs.get().contains(mobId.toString());
    }

    public static boolean ignoreTargetHeight() {
        return SoundAttractConfig.COMMON.zombiesIgnoreHeight.get();
    }
}
