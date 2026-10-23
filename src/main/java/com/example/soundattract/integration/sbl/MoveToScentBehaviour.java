package com.example.soundattract.integration.sbl;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.event.StealthDetectionEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;
import net.tslat.smartbrainlib.api.core.behaviour.ExtendedBehaviour;
import net.tslat.smartbrainlib.util.BrainUtil;

import java.util.List;

public class MoveToScentBehaviour<E extends PathfinderMob> extends ExtendedBehaviour<E> {
    private static final List<Pair<MemoryModuleType<?>, MemoryStatus>> MEMORY_REQUIREMENTS = ObjectArrayList.of(
        Pair.of(ScentSensor.SCENT_TARGET, MemoryStatus.VALUE_PRESENT),
        Pair.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED),
        Pair.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED)
    );

    private BlockPos target;

    public MoveToScentBehaviour() {
        runFor(e -> 100);
        startCondition(e -> e != null 
            && !e.isVehicle() 
            && !e.isSleeping()
            && (!SoundAttractConfig.COMMON.enableStealthMechanics.get() || !StealthDetectionEvents.shouldSuppressTargeting(e)));
    }

    @Override
    protected List<Pair<MemoryModuleType<?>, MemoryStatus>> getMemoryRequirements() {
        return MEMORY_REQUIREMENTS;
    }

    @Override
    protected boolean checkExtraStartConditions(net.minecraft.server.level.ServerLevel level, E entity) {
        if (entity.getTarget() != null) {
            BrainUtil.clearMemory(entity, ScentSensor.SCENT_TARGET);
            return false;
        }

        BlockPos pos = BrainUtil.getMemory(entity, ScentSensor.SCENT_TARGET);
        if (pos == null) {
            return false;
        }


        if (entity.distanceToSqr(Vec3.atCenterOf(pos)) <= 2.0 * 2.0) {


        }

        this.target = pos;
        return true;
    }

    @Override
    protected void start(E entity) {
        BlockPos pos = this.target;
        this.target = null;
        if (pos == null) return;

        float speed = (float) SoundAttractConfig.COMMON.mobMoveSpeed.get().doubleValue();
        
        BrainUtil.setMemory(entity, MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));
        BrainUtil.setMemory(entity, MemoryModuleType.WALK_TARGET, new WalkTarget(Vec3.atCenterOf(pos), speed, 1));
    }
}
