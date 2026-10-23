package com.example.soundattract.integration.sbl;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import com.example.soundattract.tracking.SoundTracker;
import com.example.soundattract.config.SoundAttractConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;
import net.tslat.smartbrainlib.api.core.behaviour.ExtendedBehaviour;
import net.tslat.smartbrainlib.util.BrainUtil;

import java.util.List;

public class SoundAttractSblBehaviour<E extends PathfinderMob> extends ExtendedBehaviour<E> {
    private static final List<Pair<MemoryModuleType<?>, MemoryStatus>> MEMORY_REQUIREMENTS = ObjectArrayList.of(
        Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED),
        Pair.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED),
        Pair.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED)
    );

    private SoundTracker.SoundRecord picked;

    public SoundAttractSblBehaviour() {
        runFor(e -> 1);
        startCondition(e -> e != null
                && !e.isVehicle()
                && !e.isSleeping()
                && (!SoundAttractConfig.COMMON.enableStealthMechanics.get() || !com.example.soundattract.event.StealthDetectionEvents.shouldSuppressTargeting(e)));
    }

    @Override
    protected List<Pair<MemoryModuleType<?>, MemoryStatus>> getMemoryRequirements() {
        return MEMORY_REQUIREMENTS;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, E entity) {
        if (BrainUtil.getMemory(entity, MemoryModuleType.ATTACK_TARGET) != null) {
            return false;
        }

        SoundTracker.SoundRecord best = SoundTracker.findNearestSound(entity, entity.level(), entity.blockPosition(), entity.getEyePosition(), null);
        if (best == null || best.pos == null) {
            return false;
        }

        this.picked = best;
        return true;
    }

    @Override
    protected void start(E entity) {
        SoundTracker.SoundRecord best = this.picked;
        this.picked = null;
        if (best == null || best.pos == null) {
            return;
        }

        BlockPos pos = best.pos;
        float speed = (float) SoundAttractConfig.COMMON.mobMoveSpeed.get().doubleValue();
        int closeEnough = Math.max(1, (int) Math.round(SoundAttractConfig.COMMON.arrivalDistance.get()));

        BrainUtil.setMemory(entity, MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));
        BrainUtil.setMemory(entity, MemoryModuleType.WALK_TARGET, new WalkTarget(Vec3.atCenterOf(pos), speed, closeEnough));
        entity.getBrain().setActiveActivityIfPossible(Activity.IDLE);
    }
}
