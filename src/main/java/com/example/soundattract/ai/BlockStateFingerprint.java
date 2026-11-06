package com.example.soundattract.ai;

import net.minecraft.world.level.block.state.BlockState;

final class BlockStateFingerprint {
    private BlockStateFingerprint() {
    }

    static String properties(BlockState state) {
        if (state == null) return "";
        return state.getValues().map(Object::toString).sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    static int propertyHash(BlockState state) {
        return properties(state).hashCode();
    }
}
