package com.example.soundattract.mixin;

import com.example.soundattract.pathfinding.HazardClassifier;
import com.example.soundattract.pathfinding.TrapdoorNavigationHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorMixin {
    private static final ThreadLocal<BlockPos.MutableBlockPos> SCRATCH_POS = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    @Inject(method = "getBlockPathType(Lnet/minecraft/world/level/BlockGetter;IIILnet/minecraft/world/entity/Mob;)Lnet/minecraft/world/level/pathfinder/BlockPathTypes;", at = @At("RETURN"), cancellable = true)
    private void soundattract$blockDangerousPursuitNodes(BlockGetter level, int x, int y, int z, Mob mob,
                                                         CallbackInfoReturnable<BlockPathTypes> cir) {
        if (cir.getReturnValue() == BlockPathTypes.BLOCKED) {
            return;
        }
        if (mob != null && HazardClassifier.isActivePursuer(mob)) {
            if (HazardClassifier.isDangerous(level, mob, x, y, z)) {
                cir.setReturnValue(BlockPathTypes.BLOCKED);
            }
        }
    }

    @Inject(method = "getBlockPathTypeStatic(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos$MutableBlockPos;)Lnet/minecraft/world/level/pathfinder/BlockPathTypes;", at = @At("RETURN"), cancellable = true)
    private static void soundattract$blockStaticTrapdoorHazards(BlockGetter level, net.minecraft.core.BlockPos.MutableBlockPos pos,
                                                               CallbackInfoReturnable<BlockPathTypes> cir) {
        BlockPathTypes ret = cir.getReturnValue();
        if (ret == BlockPathTypes.BLOCKED) {
            return;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (ret == BlockPathTypes.TRAPDOOR) {
            if (TrapdoorNavigationHelper.isTrapdoorDropHazard(level, x, y, z)) {
                cir.setReturnValue(BlockPathTypes.BLOCKED);
            }
            return;
        }
        BlockPos.MutableBlockPos scratch = SCRATCH_POS.get();
        scratch.set(x, y - 1, z);
        BlockState supportState = level.getBlockState(scratch);
        if (supportState.is(BlockTags.TRAPDOORS)) {
            if (TrapdoorNavigationHelper.isTrapdoorDropHazard(level, x, y - 1, z)) {
                cir.setReturnValue(BlockPathTypes.BLOCKED);
            }
        }
    }
}