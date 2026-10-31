package com.example.soundattract.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class TrapdoorNavigationHelper {
    private static final ThreadLocal<BlockPos.MutableBlockPos> SCRATCH_POS = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    private TrapdoorNavigationHelper() {}

    public static boolean isOpenTrapdoor(BlockGetter level, BlockPos pos) {
        if (level == null || pos == null) return false;
        BlockState state = level.getBlockState(pos);
        return isOpenTrapdoorState(state);
    }

    public static boolean isOpenTrapdoorState(BlockState state) {
        return state != null
                && state.is(BlockTags.TRAPDOORS)
                && state.hasProperty(TrapDoorBlock.OPEN)
                && state.getValue(TrapDoorBlock.OPEN);
    }

    public static boolean isTrapdoorDropHazard(BlockGetter level, BlockPos trapdoorPos) {
        if (level == null || trapdoorPos == null) return false;
        return isTrapdoorDropHazard(level, trapdoorPos.getX(), trapdoorPos.getY(), trapdoorPos.getZ());
    }

    public static boolean isTrapdoorDropHazard(BlockGetter level, int x, int y, int z) {
        if (level == null) return false;
        BlockPos.MutableBlockPos scratch = SCRATCH_POS.get();
        scratch.set(x, y, z);
        BlockState trapdoorState = level.getBlockState(scratch);
        if (!isOpenTrapdoorState(trapdoorState)) return false;
        int belowY = y - 1;
        if (belowY < level.getMinY()) return true;
        scratch.set(x, belowY, z);
        BlockState belowState = level.getBlockState(scratch);
        if (belowState.isAir() || belowState.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) return true;
        if (belowState.is(BlockTags.TRAPDOORS)) return true;
        net.minecraft.world.phys.shapes.VoxelShape shape = belowState.getCollisionShape(level, scratch, net.minecraft.world.phys.shapes.CollisionContext.empty());
        return shape.isEmpty() || shape.max(net.minecraft.core.Direction.Axis.Y) < 1.0D;
    }

    public static boolean shouldJumpOverTrapdoor(Level level, Mob mob) {
        if (level == null || mob == null || !mob.onGround() || !mob.horizontalCollision) {
            return false;
        }

        Vec3 moveVec = null;
        if (mob.getNavigation() != null && mob.getNavigation().getPath() != null
                && !mob.getNavigation().getPath().isDone()) {
            net.minecraft.world.level.pathfinder.Node nextNode = mob.getNavigation().getPath().getNextNode();
            if (nextNode != null) {
                double nx = nextNode.x + 0.5D - mob.getX();
                double nz = nextNode.z + 0.5D - mob.getZ();
                if (nx * nx + nz * nz > 0.001D) {
                    double dist = Math.sqrt(nx * nx + nz * nz);
                    moveVec = new Vec3(nx / dist, 0.0D, nz / dist);
                }
            }
        }
        if (moveVec == null) {
            Vec3 look = mob.getLookAngle();
            double len = Math.sqrt(look.x * look.x + look.z * look.z);
            if (len > 0.001D) {
                moveVec = new Vec3(look.x / len, 0.0D, look.z / len);
            }
        }
        if (moveVec == null) return false;

        BlockPos feet = mob.blockPosition();
        BlockPos frontFeet = BlockPos.containing(mob.getX() + moveVec.x * 0.5D, mob.getY(), mob.getZ() + moveVec.z * 0.5D);

        boolean trapdoorAtFeet = isOpenTrapdoor(level, feet);
        boolean trapdoorInFront = isOpenTrapdoor(level, frontFeet);
        if (!trapdoorAtFeet && !trapdoorInFront) {
            return false;
        }

        BlockPos obstacle = trapdoorInFront ? frontFeet : feet;
        BlockPos aboveObstacle = obstacle.above();
        if (!level.getBlockState(aboveObstacle).getCollisionShape(level, aboveObstacle).isEmpty()) {
            return false;
        }

        BlockPos headAboveMob = feet.above(2);
        if (!level.getBlockState(headAboveMob).getCollisionShape(level, headAboveMob).isEmpty()) {
            return false;
        }

        BlockPos landing = BlockPos.containing(mob.getX() + moveVec.x * 1.0D, mob.getY(), mob.getZ() + moveVec.z * 1.0D);
        if (HazardClassifier.isDangerous(level, mob, Vec3.atBottomCenterOf(landing))) {
            return false;
        }

        return true;
    }

    public static boolean tryJumpOverTrapdoor(Mob mob) {
        if (mob == null) return false;
        Level level = mob.level();
        if (level == null || level.isClientSide()) {
            return false;
        }
        if (shouldJumpOverTrapdoor(level, mob)) {
            mob.getJumpControl().jump();
            return true;
        }
        return false;
    }
}