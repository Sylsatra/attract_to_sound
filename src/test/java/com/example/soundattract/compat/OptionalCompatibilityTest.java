package com.example.soundattract.compat;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OptionalCompatibilityTest {
    @Test void rejectsMissingAndBridgeOnlyHostilesTick() {
        Set<String> base = Set.of(
            "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lcom/corrinedev/tachostiles/common/entity/AbstractScavEntity;)Z",
            "shouldKeepRunning(Lcom/corrinedev/tachostiles/common/entity/AbstractScavEntity;)Z",
            "stop(Lcom/corrinedev/tachostiles/common/entity/AbstractScavEntity;)V");
        assertEquals(OptionalCompatibilityResolver.Decision.REJECT_MIXIN_UNSUPPORTED_SHAPE, OptionalCompatibilityResolver.resolveHostiles(base));
        assertEquals(OptionalCompatibilityResolver.Decision.REJECT_MIXIN_UNSUPPORTED_SHAPE, OptionalCompatibilityResolver.resolveHostiles(Set.of("tick(Lnet/minecraft/world/entity/PathfinderMob;)V")));
    }
    @Test void rejectsOldFlashAccessorAndInvalidFogMarkers() {
        assertEquals(OptionalCompatibilityResolver.Decision.FLASHBANG_UNSUPPORTED_SMOKE_MOLOTOV_INDEPENDENT, OptionalCompatibilityResolver.resolveFlashbang(Set.of("isExplodedAccessor()")));
        assertEquals(OptionalCompatibilityResolver.Decision.FROM_FOG_EXEMPTION_UNSUPPORTED_NORMAL_HIDING, OptionalCompatibilityResolver.resolveFromFog("1.9.1", Set.of("herobrineModel", "herobrine")));
        assertEquals(OptionalCompatibilityResolver.Decision.FROM_FOG_EXEMPTION_UNSUPPORTED_NORMAL_HIDING, OptionalCompatibilityResolver.resolveFromFog("1.9.2", Set.of("herobrineModel")));
    }
}
