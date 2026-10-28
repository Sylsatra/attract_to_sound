package com.example.soundattract.integration.sbl;


import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.config.SoundAttractConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;
import net.tslat.smartbrainlib.api.core.behaviour.base.ExtendedBehaviour;
import net.tslat.smartbrainlib.library.object.MemoryTest;
import net.tslat.smartbrainlib.util.BrainUtil;

import java.util.Set;

public class MoveToSoundBehaviour<E extends PathfinderMob> extends ExtendedBehaviour<E> {
    private static final MemoryTest MEMORY_REQUIREMENTS = MemoryTest.builder(3)
        .hasMemory(SoundAttractSensor.SOUND_ATTRACT_TARGET)
        .usesMemory(MemoryModuleType.WALK_TARGET)
        .usesMemory(MemoryModuleType.LOOK_TARGET);

    private BlockPos target;

    public MoveToSoundBehaviour() {
        runFor(e -> 1);
        startCondition(e -> e != null
            && !e.isVehicle()
            && !e.isSleeping()
            && (!SoundAttractConfig.COMMON.enableStealthMechanics.get() || !StealthDetectionEvents.shouldSuppressTargeting(e)));
    }

    @Override
    public Set<net.minecraft.world.entity.ai.behavior.declarative.MemoryCondition<?, ?>> getMemoryRequirements() {
        return MEMORY_REQUIREMENTS;
    }

    @Override
    protected boolean checkExtraStartConditions(net.minecraft.server.level.ServerLevel level, E entity) {
        if (entity.getTarget() != null) {
            BrainUtil.clearMemory(entity, SoundAttractSensor.SOUND_ATTRACT_TARGET);
            return false;
        }

        BlockPos pos = BrainUtil.getMemory(entity, SoundAttractSensor.SOUND_ATTRACT_TARGET);
        if (pos == null) {
            return false;
        }

        double arrival = SoundAttractConfig.COMMON.arrivalDistance.get();
        if (entity.distanceToSqr(Vec3.atCenterOf(pos)) <= arrival * arrival) {
            BrainUtil.clearMemory(entity, SoundAttractSensor.SOUND_ATTRACT_TARGET);
            return false;
        }

        this.target = pos;
        return true;
    }

    @Override
    protected void start(E entity) {
        BlockPos pos = this.target;
        this.target = null;
        if (pos == null) {
            return;
        }

        float speed = (float) SoundAttractConfig.COMMON.mobMoveSpeed.get().doubleValue();
        int closeEnough = Math.max(1, (int) Math.round(SoundAttractConfig.COMMON.arrivalDistance.get()));

        BrainUtil.setMemory(entity, MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));
        BrainUtil.setMemory(entity, MemoryModuleType.WALK_TARGET, new WalkTarget(Vec3.atCenterOf(pos), speed, closeEnough));
    }
}
