package com.example.soundattract.ai;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.integration.relentlessundead.RelentlessUndeadIntegration;
import com.example.soundattract.tracking.SoundTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder("soundattract_soundclimb")
public final class SoundClimbGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public void configuredMobClimbsAnEligibleMobTowardsTrackedSound(GameTestHelper helper) {
        var config = SoundAttractConfig.COMMON;
        boolean previousClimbing = config.enableRelentlessClimbing.get();
        boolean previousSmartEdge = config.edgeMobSmartBehavior.get();
        boolean previousStealth = config.enableStealthMechanics.get();
        boolean previousScentOverride = config.scentOverrideSoundPriority.get();
        config.enableRelentlessClimbing.set(false);
        config.edgeMobSmartBehavior.set(false);
        config.enableStealthMechanics.set(false);
        config.scentOverrideSoundPriority.set(false);

        for (int x = 3; x <= 14; x++) {
            for (int z = 3; z <= 9; z++) {
                helper.setBlock(new BlockPos(x, 80, z), Blocks.STONE);
                for (int y = 81; y <= 86; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
        }

        Mob disabledZombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 6, 81, 4);
        config.enableRelentlessClimbing.set(true);
        Mob support = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 8, 81, 6);
        Mob climber = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 8, 82, 6);
        support.setNoGravity(true);
        climber.setNoGravity(true);

        helper.runAfterDelay(60, () -> {
            try {
                helper.assertTrue(!RelentlessUndeadIntegration.isLoaded(), "fixture must exercise native fallback");
                helper.assertTrue(disabledZombie.goalSelector.getAvailableGoals().stream()
                        .noneMatch(goal -> goal.getGoal() instanceof SoundClimbGoal), "disabled mob join must skip sound climbing");

                var supportPosition = helper.absoluteVec(new Vec3(8.5, 81, 6.5));
                support.setPos(supportPosition);
                climber.setPos(supportPosition.add(0.0, 1.0, 0.0));
                climber.setDeltaMovement(0, 0, 0);

                BlockPos sound = helper.absolutePos(new BlockPos(13, 84, 6));
                var level = helper.getLevel();
                String dimension = level.dimension().location().toString();
                SoundTracker.addSound(null, sound, dimension, 48.0, 50.0, 80, "minecraft:entity.generic.explode");
                SoundTracker.SoundRecord selected = SoundTracker.findNearestSound(climber, level,
                        climber.blockPosition(), climber.getEyePosition());
                helper.assertTrue(selected != null && sound.equals(selected.pos),
                        "climber must be in range of the tracked sound: recorded="
                                + SoundTracker.getNearbySounds(level, dimension, climber.blockPosition(), null).size()
                                + " climber=" + climber.blockPosition() + " expected=" + sound
                                + " selected=" + (selected == null ? null : selected.pos));
                AttractionGoal attraction = climber.goalSelector.getAvailableGoals().stream().map(WrappedGoal::getGoal)
                        .filter(AttractionGoal.class::isInstance).map(AttractionGoal.class::cast).findFirst().orElseThrow();
                WrappedGoal wrappedClimb = climber.goalSelector.getAvailableGoals().stream()
                        .filter(wrapped -> wrapped.getGoal() instanceof SoundClimbGoal).findFirst().orElseThrow();
                helper.assertTrue(wrappedClimb.getPriority() == 0, "native climbing must take priority over its attraction goal");
                boolean attracted = attraction.canUse();
                helper.assertTrue(attracted && sound.equals(attraction.getTargetSoundPos()),
                        "native attraction must supply the tracked sound to the climb goal: attracted=" + attracted
                                + " target=" + attraction.getTargetSoundPos() + " combatTarget=" + climber.getTarget()
                                + " controlled=" + LivingWallCoordinator.isControlled(climber));
                SoundClimbGoal climb = (SoundClimbGoal) wrappedClimb.getGoal();
                helper.assertTrue(climb.canUse(), "eligible climber on an eligible support mob must start");
                climb.start();
                climb.tick();
                helper.assertTrue(climber.getDeltaMovement().y > 0.0 && climber.getDeltaMovement().x > 0.0
                                && climber.hasEffect(MobEffects.JUMP),
                        "climb goal must move the mob upward and toward the sound and apply the short jump effect");
                climber.setTarget(disabledZombie);
                helper.assertTrue(!climb.canContinueToUse(), "a direct combat target must interrupt sound climbing");
                climb.stop();
                helper.succeed();
            } finally {
                config.enableRelentlessClimbing.set(previousClimbing);
                config.edgeMobSmartBehavior.set(previousSmartEdge);
                config.enableStealthMechanics.set(previousStealth);
                config.scentOverrideSoundPriority.set(previousScentOverride);
                disabledZombie.discard();
                support.discard();
                climber.discard();
            }
        });
    }
}
