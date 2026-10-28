package com.example.soundattract.ai;

import com.example.soundattract.event.StealthDetectionEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob; 
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class FleeFromUnseenAttackerGoal extends Goal {

    protected final Mob mob;
    private final double speedModifier;

    private double wantedX;
    private double wantedY;
    private double wantedZ;
    private Path plannedPath;
    private PathNavigation plannedNavigation;

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

        for (int attempt = 0; attempt < 5; attempt++) {
            Vec3 fleePos = DefaultRandomPos.getPosAway(pathfinderMob, 16, 7, this.attacker.position());
            if (fleePos == null) continue;
            PathNavigation navigation = pathfinderMob.getNavigation();
            Path path = navigation.createPath(BlockPos.containing(fleePos), 0);
            if (path == null || !path.canReach()) continue;

            this.wantedX = fleePos.x;
            this.wantedY = fleePos.y;
            this.wantedZ = fleePos.z;
            this.plannedPath = path;
            this.plannedNavigation = navigation;
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        if (this.plannedPath != null && this.plannedNavigation == this.mob.getNavigation()) {
            this.plannedNavigation.moveTo(this.plannedPath, this.speedModifier);
        } else {
            this.mob.getNavigation().moveTo(this.wantedX, this.wantedY, this.wantedZ, this.speedModifier);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return !this.mob.getNavigation().isDone();
    }

    @Override
    public void stop() {
        this.attacker = null;
        this.plannedPath = null;
        this.plannedNavigation = null;
    }
}
