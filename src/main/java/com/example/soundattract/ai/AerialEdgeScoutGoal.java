package com.example.soundattract.ai;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public final class AerialEdgeScoutGoal extends Goal {
    private static final long PATROL_INTERVAL_TICKS = 40L;
    private static final long SWOOP_INTERVAL_TICKS = 120L;
    private static final long SWOOP_YIELD_TICKS = 30L;
    private static final long MOVE_RETRY_TICKS = 10L;
    private static final long MOVE_TIMEOUT_TICKS = 80L;

    private final Mob mob;
    private long nextScoutTick;
    private long nextSwoopTick;
    private long nextMoveAttemptTick;
    private long moveTimeoutTick;
    private Vec3 destination;
    private boolean swooping;
    private boolean patrolSide;
    private boolean initialized;
    private boolean arrived;
    private boolean ownsNavigation;
    private boolean sampleAttempted;
    private Path ownedNavigationPath;
    private Mob assignedLeader;

    public AerialEdgeScoutGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!SoundAttractConfig.COMMON.edgeMobSmartBehavior.get()
                || LivingWallCoordinator.isControlled(mob)
                || !(mob.getNavigation() instanceof FlyingPathNavigation)
                || !mob.isAlive()
                || mob.isVehicle()
                || mob.isSleeping()
                || hasLiveTarget()
                || mob.getNavigation().isInProgress()
                || !MobGroupManager.isEdgeMob(mob)) {
            return false;
        }

        Mob leader = MobGroupManager.getLeader(mob);
        if (leader == null || leader == mob || leader.isRemoved()) {
            return false;
        }
        assignedLeader = leader;

        long now = mob.level().getGameTime();
        if (!initialized) {
            nextScoutTick = now + Math.floorMod(mob.getId(), (int) PATROL_INTERVAL_TICKS);
            nextSwoopTick = now + PATROL_INTERVAL_TICKS + Math.floorMod(mob.getId(), 40);
            initialized = true;
        }
        if (now < nextScoutTick) {
            return false;
        }

        swooping = canFollowScent() && now >= nextSwoopTick;
        destination = swooping ? getSwoopDestination() : getSectorPatrolDestination(leader);
        return destination != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (LivingWallCoordinator.isControlled(mob)
                || hasLiveTarget()
                || destination == null
                || !SoundAttractConfig.COMMON.edgeMobSmartBehavior.get()
                || !MobGroupManager.isEdgeMob(mob)
                || MobGroupManager.getLeader(mob) != assignedLeader) {
            return false;
        }
        if (mob.position().distanceToSqr(destination) <= 2.25) {
            arrived = ownsNavigation && ownedNavigationPath != null
                    && mob.getNavigation().getPath() == ownedNavigationPath;
            return false;
        }
        if (!mob.getNavigation().isDone()
                && (!ownsNavigation || mob.getNavigation().getPath() != ownedNavigationPath)) return false;
        return mob.level().getGameTime() < moveTimeoutTick;
    }

    @Override
    public void start() {
        long now = mob.level().getGameTime();
        arrived = false;
        ownsNavigation = false;
        ownedNavigationPath = null;
        sampleAttempted = false;
        moveTimeoutTick = now + MOVE_TIMEOUT_TICKS;
        nextMoveAttemptTick = now;
        issueMove();
    }

    @Override
    public void tick() {
        long now = mob.level().getGameTime();
        if (now >= nextMoveAttemptTick && mob.getNavigation().isDone()
                && mob.position().distanceToSqr(destination) > 2.25) {
            issueMove();
        }
    }

    @Override
    public void stop() {
        if (ownsNavigation && ownedNavigationPath != null && mob.getNavigation().getPath() == ownedNavigationPath) {
            mob.getNavigation().stop();
        }
        if (SoundAttractConfig.COMMON.enableSecondBrain.get() && swooping && arrived && !sampleAttempted
                && MobGroupManager.isEdgeMob(mob) && MobGroupManager.getLeader(mob) == assignedLeader
                && mob.level().hasChunkAt(mob.blockPosition())) {
            sampleAttempted = true;
            sampleScentAtLanding();
        }
        long now = mob.level().getGameTime();
        if (swooping) {
            nextSwoopTick = now + SWOOP_INTERVAL_TICKS;
            nextScoutTick = now + SWOOP_YIELD_TICKS;
        } else {
            nextScoutTick = now + PATROL_INTERVAL_TICKS;
        }
        destination = null;
        assignedLeader = null;
        ownedNavigationPath = null;
        ownsNavigation = false;
        super.stop();
    }

    private void issueMove() {
        if (destination == null) {
            return;
        }
        nextMoveAttemptTick = mob.level().getGameTime() + MOVE_RETRY_TICKS;
        ownsNavigation = com.example.soundattract.pathfinding.PursuitNavigation.maybeMoveTo(mob, destination, 1.0);
        ownedNavigationPath = ownsNavigation ? mob.getNavigation().getPath() : null;
        ownsNavigation = ownsNavigation && ownedNavigationPath != null;
    }

    private Vec3 getSwoopDestination() {
        BlockPos current = mob.blockPosition();
        int groundY = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, current.getX(), current.getZ());
        return new Vec3(mob.getX(), groundY, mob.getZ());
    }

    private Vec3 getSectorPatrolDestination(Mob leader) {
        int sectorCount = Math.max(1, SoundAttractConfig.COMMON.numEdgeSectors.get());
        double dx = mob.getX() - leader.getX();
        double dz = mob.getZ() - leader.getZ();
        double radius = Math.sqrt(dx * dx + dz * dz);
        if (radius < 2.0) {
            radius = Math.min(8.0, SoundAttractConfig.COMMON.leaderGroupRadius.get());
        }

        double angle = Math.atan2(dz, dx);
        int sector = Math.floorMod((int) Math.floor(((angle + Math.PI) / (Math.PI * 2.0)) * sectorCount), sectorCount);
        double sectorWidth = (Math.PI * 2.0) / sectorCount;
        double sectorCenter = -Math.PI + (sector + 0.5) * sectorWidth;
        patrolSide = !patrolSide;
        double patrolAngle = sectorCenter + sectorWidth * (patrolSide ? 0.25 : -0.25);
        double groupRadius = Math.max(2.0, SoundAttractConfig.COMMON.leaderGroupRadius.get());
        radius = Math.max(2.0, Math.min(radius, groupRadius * 0.9));

        double x = leader.getX() + Math.cos(patrolAngle) * radius;
        double z = leader.getZ() + Math.sin(patrolAngle) * radius;
        BlockPos targetChunkPos = BlockPos.containing(x, mob.getY(), z);
        if (!mob.level().hasChunkAt(targetChunkPos)) {
            return null;
        }
        return new Vec3(x, mob.getY(), z);
    }

    private boolean canFollowScent() {
        if (!SoundAttractConfig.COMMON.enableScentSystem.get()) {
            return false;
        }
        List<? extends String> eligibleTypes = SoundAttractConfig.COMMON.scentEligibleMobs.get();
        net.minecraft.resources.ResourceLocation typeKey = net.minecraft.world.entity.EntityType.getKey(mob.getType());
        return typeKey != null && eligibleTypes.contains(typeKey.toString());
    }

    private void sampleScentAtLanding() {
        if (!canFollowScent() || !FollowScentGoal.tryConsumeScentScanBudget(mob)) return;
        ScentManager manager = ScentManager.get(mob.level()).orElse(null);
        if (manager == null) return;
        long now = mob.level().getGameTime();
        long maxAge = SoundAttractConfig.COMMON.scentNodeDurationTicks.get();
        double radius = SoundAttractConfig.COMMON.scentDetectionRadius.get();
        ScentNode node = manager.findFreshPlayerScentAt(mob.blockPosition(), now, maxAge, radius);
        if (node == null) return;
        ClueDirector.reportScoutScent(mob, node);
    }

    private boolean hasLiveTarget() {
        return mob.getTarget() != null && mob.getTarget().isAlive();
    }
}
