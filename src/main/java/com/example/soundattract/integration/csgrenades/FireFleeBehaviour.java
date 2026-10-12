package com.example.soundattract.integration.csgrenades;

import com.mojang.datafixers.util.Pair;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.config.DebugLoggingConfig;
import com.example.soundattract.SoundAttractMod;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.tslat.smartbrainlib.api.core.behaviour.ExtendedBehaviour;
import net.tslat.smartbrainlib.util.BrainUtils;

import java.util.List;

/** SmartBrainLib equivalent of {@link FleeFromFireGoal} for brain-driven mobs. */
public class FireFleeBehaviour<E extends PathfinderMob> extends ExtendedBehaviour<E> {
    private static final List<Pair<MemoryModuleType<?>, MemoryStatus>> MEMORY_REQUIREMENTS = ObjectArrayList.of();

    private Path fleePath;
    private LivingEntity previousTarget;

    public FireFleeBehaviour() {
        noTimeout();
    }

    @Override
    protected List<Pair<MemoryModuleType<?>, MemoryStatus>> getMemoryRequirements() {
        return MEMORY_REQUIREMENTS;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, E mob) {
        return isEnabled(mob) && findFleePath(mob);
    }

    @Override
    protected void start(E mob) {
        previousTarget = mob.getTarget();
        if (previousTarget == null) {
            previousTarget = BrainUtils.getMemory(mob, MemoryModuleType.ATTACK_TARGET);
        }
        suppressCombat(mob);
        moveAway(mob);
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
            Vec3 firePos = activeFireWithinDangerRadius(mob);
            SoundAttractMod.LOGGER.debug("[CSGrenades] SmartBrainLib mob {} fleeing incendiary at {} toward {}",
                    mob.getType().getDescriptionId(), firePos, fleePath == null ? null : fleePath.getTarget());
        }
    }

    @Override
    protected boolean shouldKeepRunning(E mob) {
        Vec3 firePos = activeFireWithinDangerRadius(mob);
        return firePos != null;
    }

    @Override
    protected void tick(E mob) {
        // Stop TACZ's combat movement and shooting from taking priority while fleeing.
        suppressCombat(mob);
        if (fleePath == null || mob.getNavigation().getPath() != fleePath || mob.getNavigation().isDone()) {
            if (findFleePath(mob)) {
                moveAway(mob);
            }
        }
    }

    @Override
    protected void stop(E mob) {
        if (fleePath != null && mob.getNavigation().getPath() == fleePath) {
            mob.getNavigation().stop();
        }
        fleePath = null;

        LivingEntity target = previousTarget;
        previousTarget = null;
        if (target != null && target.isAlive() && mob.canAttack(target)) {
            mob.setTarget(target);
            BrainUtils.setMemory(mob.getBrain(), MemoryModuleType.ATTACK_TARGET, target);
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
                SoundAttractMod.LOGGER.debug("[CSGrenades] SmartBrainLib mob {} resuming combat against {} after fleeing",
                        mob.getType().getDescriptionId(), target.getType().getDescriptionId());
            }
        }
    }

    private boolean findFleePath(E mob) {
        Vec3 firePos = activeFireWithinDangerRadius(mob);
        if (firePos == null) {
            fleePath = null;
            return false;
        }

        int awayDistance = Math.max(1, (int) Math.ceil(SoundAttractConfig.COMMON.fireFleeAwayDistance.get()));
        for (int attempt = 0; attempt < 8; attempt++) {
            Vec3 fleePos = DefaultRandomPos.getPosAway(mob, awayDistance, 7, firePos);
            if (fleePos == null) {
                continue;
            }

            Path candidate = mob.getNavigation().createPath(fleePos.x, fleePos.y, fleePos.z, 0);
            if (candidate != null && candidate.canReach()) {
                fleePath = candidate;
                return true;
            }
        }

        fleePath = null;
        return false;
    }

    private void moveAway(E mob) {
        if (fleePath != null) {
            mob.getNavigation().moveTo(fleePath, SoundAttractConfig.COMMON.fireFleeSpeedModifier.get());
        }
    }

    private Vec3 activeFireWithinDangerRadius(E mob) {
        if (!isEnabled(mob)) {
            return null;
        }

        Vec3 recentHit = CsGrenadesEventHandler.recentFireHitPosition(mob, mob.level().getGameTime());
        if (recentHit != null) {
            return recentHit;
        }

        Vec3 firePos = CsGrenadesTracker.nearestActiveFire(
                mob.level(), mob.position(), Integer.MAX_VALUE, mob.level().getGameTime());
        if (firePos == null) {
            return null;
        }

        double dangerRadius = SoundAttractConfig.COMMON.fireFleeDangerRadius.get();
        return mob.position().distanceToSqr(firePos) <= dangerRadius * dangerRadius ? firePos : null;
    }

    private boolean isEnabled(E mob) {
        return mob != null
                && CsGrenadesCompat.isLoaded()
                && SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()
                && SoundAttractConfig.COMMON.enableFireFlee.get()
                && !mob.fireImmune();
    }

    private void suppressCombat(E mob) {
        mob.setTarget(null);
        BrainUtils.clearMemory(mob.getBrain(), MemoryModuleType.ATTACK_TARGET);
        BrainUtils.clearMemory(mob.getBrain(), MemoryModuleType.WALK_TARGET);
        BrainUtils.clearMemory(mob.getBrain(), MemoryModuleType.PATH);
        BrainUtils.clearMemory(mob.getBrain(), MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }
}
