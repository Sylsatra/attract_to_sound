package com.example.soundattract.ai;

import com.example.soundattract.event.StealthDetectionEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob; 
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class FleeFromUnseenAttackerGoal extends Goal {

    protected final Mob mob;
    private final double speedModifier;

    private double wantedX;
    private double wantedY;
    private double wantedZ;

    private LivingEntity attacker;

    public FleeFromUnseenAttackerGoal(Mob mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(this.mob instanceof PathfinderMob pathfinderMob)) {
            return false;
        }

        this.attacker = this.mob.getLastHurtByMob();
        if (this.attacker == null) {
            return false;
        }

        if (!(this.attacker instanceof Player player)) {
            return false;
        }

        if (StealthDetectionEvents.canMobDetectPlayer(this.mob, player)) {
            return false;
        }

        Vec3 fleePos = DefaultRandomPos.getPosAway(pathfinderMob, 16, 7, this.attacker.position());
        if (fleePos == null) {
            return false;
        }
        
        this.wantedX = fleePos.x;
        this.wantedY = fleePos.y;
        this.wantedZ = fleePos.z;
        return true;
    }

    @Override
    public void start() {
        this.mob.getNavigation().moveTo(this.wantedX, this.wantedY, this.wantedZ, this.speedModifier);
    }

    @Override
    public boolean canContinueToUse() {
        return !this.mob.getNavigation().isDone();
    }

    @Override
    public void stop() {
        this.attacker = null;
    }
}
