package com.example.soundattract.mixin;

import com.example.soundattract.pathfinding.HazardClassifier;
import com.example.soundattract.pathfinding.TrapdoorNavigationHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorMixin {
    private static final ThreadLocal<BlockPos.MutableBlockPos> SCRATCH_POS = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    @Inject(method = "getPathTypeStatic(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/pathfinder/PathType;", at = @At("RETURN"), cancellable = true)
    private static void soundattract$blockDangerousPursuitNodes(Mob mob, BlockPos pos,
                                                         CallbackInfoReturnable<PathType> cir) {
        if (cir.getReturnValue() == PathType.BLOCKED) {
            return;
        }
        if (mob != null && HazardClassifier.isActivePursuer(mob)) {
            if (HazardClassifier.isDangerous(mob.level(), mob, pos)) {
                cir.setReturnValue(PathType.BLOCKED);
            }
        }
    }

    @Inject(method = "getPathTypeStatic(Lnet/minecraft/world/level/pathfinder/PathfindingContext;Lnet/minecraft/core/BlockPos$MutableBlockPos;)Lnet/minecraft/world/level/pathfinder/PathType;", at = @At("RETURN"), cancellable = true)
    private static void soundattract$blockStaticTrapdoorHazards(PathfindingContext context, net.minecraft.core.BlockPos.MutableBlockPos pos,
                                                               CallbackInfoReturnable<PathType> cir) {
        BlockGetter level = context.level();
        PathType ret = cir.getReturnValue();
        if (ret == PathType.BLOCKED) {
            return;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (ret == PathType.TRAPDOOR) {
            if (TrapdoorNavigationHelper.isTrapdoorDropHazard(level, x, y, z)) {
                cir.setReturnValue(PathType.BLOCKED);
            }
            return;
        }
        BlockPos.MutableBlockPos scratch = SCRATCH_POS.get();
        scratch.set(x, y - 1, z);
        BlockState supportState = level.getBlockState(scratch);
        if (supportState.is(BlockTags.TRAPDOORS)) {
            if (TrapdoorNavigationHelper.isTrapdoorDropHazard(level, x, y - 1, z)) {
                cir.setReturnValue(PathType.BLOCKED);
            }
        }
    }
}
