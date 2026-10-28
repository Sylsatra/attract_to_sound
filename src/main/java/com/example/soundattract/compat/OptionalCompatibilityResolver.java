package com.example.soundattract.compat;

import java.util.Set;

public final class OptionalCompatibilityResolver {
    public enum Decision { ACCEPT, REJECT_MIXIN_UNSUPPORTED_SHAPE, FLASHBANG_UNSUPPORTED_SMOKE_MOLOTOV_INDEPENDENT, FROM_FOG_EXEMPTION_UNSUPPORTED_NORMAL_HIDING }
    private OptionalCompatibilityResolver() {}

    public static Decision resolveHostiles(Set<String> descriptors) {
        return descriptors != null && descriptors.contains("checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lcom/corrinedev/tachostiles/common/entity/AbstractScavEntity;)Z")
                && descriptors.contains("shouldKeepRunning(Lcom/corrinedev/tachostiles/common/entity/AbstractScavEntity;)Z")
                && descriptors.contains("tick(Lcom/corrinedev/tachostiles/common/entity/AbstractScavEntity;)V")
                && descriptors.contains("stop(Lcom/corrinedev/tachostiles/common/entity/AbstractScavEntity;)V") ? Decision.ACCEPT : Decision.REJECT_MIXIN_UNSUPPORTED_SHAPE;
    }
    public static Decision resolveFlashbang(Set<String> descriptors) {
        return descriptors != null && descriptors.contains("access$isActivatedAccessor$cp()Lnet/minecraft/network/syncher/EntityDataAccessor;") ? Decision.ACCEPT : Decision.FLASHBANG_UNSUPPORTED_SMOKE_MOLOTOV_INDEPENDENT;
    }
    public static Decision resolveFromFog(String version, Set<String> tags) {
        return "1.9.2".equals(version) && tags != null && tags.contains("herobrineModel") && (tags.contains("herobrineEntity") || tags.contains("herobrine")) ? Decision.ACCEPT : Decision.FROM_FOG_EXEMPTION_UNSUPPORTED_NORMAL_HIDING;
    }
}