package com.example.soundattract.integration.csgrenades;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class FleeFromFireGoal extends Goal {
    private final PathfinderMob mob;
    private Vec3 fleeTarget;
    private double speedModifier;

    public FleeFromFireGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!CsGrenadesCompat.isLoaded()) {
            return false;
        }
        if (!SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()) {
            return false;
        }
        if (!SoundAttractConfig.COMMON.enableFireFlee.get()) {
            return false;
        }
        if (mob.fireImmune()) {
            return false;
        }

        Vec3 firePos = CsGrenadesTracker.nearestActiveFire(
                mob.level(), mob.position(),
                Integer.MAX_VALUE,
                mob.level().getGameTime()
        );
        if (firePos == null) {
            return false;
        }

        double dangerRadius = SoundAttractConfig.COMMON.fireFleeDangerRadius.get();
        if (mob.position().distanceToSqr(firePos) > dangerRadius * dangerRadius) {
            return false;
        }

        double awayDistance = SoundAttractConfig.COMMON.fireFleeAwayDistance.get();
        Vec3 fleeTo = DefaultRandomPos.getPosAway(mob, (int) awayDistance, 7, firePos);
        if (fleeTo == null) {
            return false;
        }

        this.fleeTarget = fleeTo;
        this.speedModifier = SoundAttractConfig.COMMON.fireFleeSpeedModifier.get();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (fleeTarget == null) {
            return false;
        }
        if (!mob.getNavigation().isInProgress()) {
            return false;
        }
        Vec3 firePos = CsGrenadesTracker.nearestActiveFire(
                mob.level(), mob.position(),
                Integer.MAX_VALUE,
                mob.level().getGameTime()
        );
        if (firePos == null) {
            return false;
        }
        double dangerRadius = SoundAttractConfig.COMMON.fireFleeDangerRadius.get();
        return mob.position().distanceToSqr(firePos) <= dangerRadius * dangerRadius;
    }

    @Override
    public void start() {
        if (fleeTarget != null) {
            mob.getNavigation().moveTo(fleeTarget.x, fleeTarget.y, fleeTarget.z, speedModifier);
        }
    }

    @Override
    public void stop() {
        fleeTarget = null;
        mob.getNavigation().stop();
    }
}