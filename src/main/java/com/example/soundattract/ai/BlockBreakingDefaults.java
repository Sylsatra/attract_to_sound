package com.example.soundattract.ai;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Defaults for the standalone Sound Attract block breaking behavior. */
public final class BlockBreakingDefaults {
    private static final TagKey<Block> EMPTY_BLACKLIST = TagKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath("soundattract", "empty_block_tag"));

    private BlockBreakingDefaults() {
    }

    public static int getMaxY() {
        return 256;
    }

    public static boolean shouldBlacklistTileEntities() {
        return true;
    }

    public static boolean isBlacklistAsWhitelist() {
        return false;
    }

    public static TagKey<Block> getBlockBlacklistTag() {
        return EMPTY_BLACKLIST;
    }
}
