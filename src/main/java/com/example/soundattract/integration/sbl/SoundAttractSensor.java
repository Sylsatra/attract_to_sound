package com.example.soundattract.integration.sbl;

import com.example.soundattract.runtime.DynamicScanCooldownManager;
import com.example.soundattract.tracking.SoundTracker;
import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.config.SoundAttractConfig;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor;
import net.tslat.smartbrainlib.util.BrainUtil;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SoundAttractSensor<E extends PathfinderMob> extends ExtendedSensor<E> {
    public static final MemoryModuleType<BlockPos> SOUND_ATTRACT_TARGET = new MemoryModuleType<>(Optional.of(BlockPos.CODEC));

    private static final SensorType<SoundAttractSensor<?>> TYPE = new SensorType<>(SoundAttractSensor::new);

    private static final List<MemoryModuleType<?>> MEMORIES = ObjectArrayList.of(
        SOUND_ATTRACT_TARGET,
        MemoryModuleType.WALK_TARGET
    );

    public SoundAttractSensor() {
        scanRate(e -> 1);
    }

    @Override
    public List<MemoryModuleType<?>> memoriesUsed() {
        return MEMORIES;
    }

    @Override
    public SensorType<? extends ExtendedSensor<?>> type() {
        return TYPE;
    }

    @Override
    protected void doTick(ServerLevel level, E entity) {
        if (!DynamicScanCooldownManager.shouldScanThisTick(entity.getId(), level.getGameTime())) {
            return;
        }

        if (entity.getTarget() != null) {
            BrainUtil.clearMemory(entity, SOUND_ATTRACT_TARGET);
            return;
        }

        if (SoundAttractConfig.COMMON.enableStealthMechanics.get() && StealthDetectionEvents.shouldSuppressTargeting(entity)) {
            BrainUtil.clearMemory(entity, SOUND_ATTRACT_TARGET);
            return;
        }

        SoundTracker.SoundRecord best = SoundTracker.findNearestSound(entity, level, entity.blockPosition(), entity.getEyePosition(), null);
        if (best == null || best.pos == null) {
            BrainUtil.clearMemory(entity, SOUND_ATTRACT_TARGET);
            return;
        }

        int maxLifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
        int lifetime = best.ticksRemaining > 0 ? Math.min(best.ticksRemaining, maxLifetime) : maxLifetime;
        lifetime = Math.max(1, lifetime);

        BrainUtil.setForgettableMemory(entity, SOUND_ATTRACT_TARGET, best.pos.immutable(), lifetime);
    }
}
