package com.example.soundattract.ai;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder("soundattract_mobjump")
public final class MeleeJumpAttemptGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public void meleeMobJumpsAndAttacksAnElevatedTarget(GameTestHelper helper) {
        BlockPos mobSpawn = new BlockPos(7, 2, 8);
        BlockPos targetSupport = new BlockPos(8, 2, 8);
        for (int x = 5; x <= 10; x++) {
            for (int z = 5; z <= 10; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE.defaultBlockState());
                for (int y = 2; y <= 6; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        helper.setBlock(targetSupport, Blocks.STONE.defaultBlockState());

        Mob mob = helper.spawn(EntityType.ZOMBIE, mobSpawn);
        Mob target = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(8, 3, 8));
        target.setNoGravity(true);
        removeGoals(mob.goalSelector);
        removeGoals(mob.targetSelector);
        mob.getNavigation().stop();
        mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
        mob.setDeltaMovement(0.0D, 0.0D, 0.0D);
        mob.setOnGround(true);
        mob.goalSelector.addGoal(100, new MeleeAttackGoal((PathfinderMob) mob, 1.0D, false) {
            @Override
            public boolean canUse() {
                return false;
            }

            @Override
            public boolean canContinueToUse() {
                return false;
            }
        });

        if (!MeleeJumpAttemptGoal.supports(mob)) {
            helper.fail("the zombie fixture must support the melee jump goal");
            return;
        }
        mob.setTarget(target);
        MeleeJumpAttemptGoal jumpGoal = new MeleeJumpAttemptGoal(mob);
        double startingY = mob.getY();
        setReadyTarget(jumpGoal, target.getUUID());
        jumpGoal.start();
        if (!isJumping(jumpGoal)) {
            helper.fail("the melee jump goal rejected the eligible elevated target");
            return;
        }
        if (!hasJumpRequest(mob)) {
            helper.fail("the melee jump goal did not request a jump from the mob controller");
            return;
        }
        mob.setPos(mob.getX(), startingY + 0.25D, mob.getZ());
        mob.setDeltaMovement(0.0D, 0.0D, 0.0D);
        mob.setOnGround(false);

        float startingHealth = target.getHealth();
        long startedAt = helper.getLevel().getGameTime();
        boolean[] observedJump = {false};
        boolean[] observedDamage = {false};
        boolean[] goalRunning = {true};

        helper.onEachTick(() -> {
            if (!mob.isAlive() || !target.isAlive()) {
                helper.fail("the jump fixture entity died before the attack could be observed");
                return;
            }

            if (mob.getY() > startingY + 0.15D) observedJump[0] = true;
            if (target.getHealth() < startingHealth) observedDamage[0] = true;

            if (goalRunning[0] && !jumpGoal.canContinueToUse()) {
                jumpGoal.stop();
                goalRunning[0] = false;
            }
            if (goalRunning[0]) jumpGoal.tick();

            if (observedJump[0] && observedDamage[0]) {
                mob.discard();
                target.discard();
                helper.succeed();
                return;
            }

            long elapsed = helper.getLevel().getGameTime() - startedAt;
            if (elapsed >= 90L) {
                helper.fail("melee jump did not complete: jumped=" + observedJump[0]
                        + " mobY=" + mob.getY() + " startY=" + startingY
                        + " damage=" + observedDamage[0] + " targetHealth=" + target.getHealth()
                        + " startHealth=" + startingHealth + " grounded=" + mob.onGround()
                        + " targetPos=" + target.blockPosition());
            }
        });
    }

    private static void removeGoals(net.minecraft.world.entity.ai.goal.GoalSelector selector) {
        List<Goal> goals = new ArrayList<>();
        for (WrappedGoal wrapped : selector.getAvailableGoals()) goals.add(wrapped.getGoal());
        for (Goal goal : goals) selector.removeGoal(goal);
    }

    private static void setReadyTarget(MeleeJumpAttemptGoal goal, java.util.UUID target) {
        try {
            Field field = MeleeJumpAttemptGoal.class.getDeclaredField("readyTarget");
            field.setAccessible(true);
            field.set(goal, target);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static boolean isJumping(MeleeJumpAttemptGoal goal) {
        try {
            Field field = MeleeJumpAttemptGoal.class.getDeclaredField("jumping");
            field.setAccessible(true);
            return field.getBoolean(goal);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static boolean hasJumpRequest(Mob mob) {
        try {
            Field field = mob.getJumpControl().getClass().getDeclaredField("jump");
            field.setAccessible(true);
            return field.getBoolean(mob.getJumpControl());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
