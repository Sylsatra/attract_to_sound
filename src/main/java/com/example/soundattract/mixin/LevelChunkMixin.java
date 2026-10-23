package com.example.soundattract.mixin;

import com.example.soundattract.ai.LivingWallCoordinator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Shadow @Final private Level level;

    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void soundattract$invalidateBridgeTerrain(BlockPos pos, BlockState state, int flags,
                                                       CallbackInfoReturnable<BlockState> cir) {
        if (level instanceof ServerLevel serverLevel && !getBlockState(pos).equals(state)) {
            LivingWallCoordinator.onTerrainMutation(serverLevel, pos);
        }
    }

    @Shadow
    public abstract BlockState getBlockState(BlockPos pos);
}

