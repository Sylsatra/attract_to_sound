package com.example.soundattract.integration.sbl;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import com.example.soundattract.event.StealthDetectionEvents;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor;
import net.tslat.smartbrainlib.util.BrainUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ScentSensor<E extends PathfinderMob> extends ExtendedSensor<E> {
    public static final MemoryModuleType<BlockPos> SCENT_TARGET = new MemoryModuleType<>(Optional.of(BlockPos.CODEC));

    private static final SensorType<ScentSensor<?>> TYPE = new SensorType<>(ScentSensor::new);

    private static final List<MemoryModuleType<?>> MEMORIES = ObjectArrayList.of(
        SCENT_TARGET,
        MemoryModuleType.WALK_TARGET
    );

    private UUID targetScentOwner;
    private long lastScentTime;

    public ScentSensor() {

        setScanRate(e -> 10);
    }

    @Override
    public List<MemoryModuleType<?>> memoriesUsed() {
        return MEMORIES;
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return new ObjectOpenHashSet<>(memoriesUsed());
    }

    @Override
    public SensorType<? extends ExtendedSensor<?>> type() {
        return TYPE;
    }

    @Override
    protected void doTick(ServerLevel level, E entity) {
        if (!SoundAttractConfig.COMMON.enableScentSystem.get()) return;

        if (entity.getTarget() != null && entity.getTarget().isAlive()) {
            BrainUtils.clearMemory(entity, SCENT_TARGET);
            return;
        }


        if (SoundAttractConfig.COMMON.enableStealthMechanics.get() && StealthDetectionEvents.shouldSuppressTargeting(entity)) {
            BrainUtils.clearMemory(entity, SCENT_TARGET);
            return;
        }

        ScentManager scentManager = level.getCapability(ScentManager.INSTANCE).orElse(null);
        if (scentManager == null) return;

        ChunkPos chunkPos = entity.chunkPosition();
        List<ScentNode> nodes = scentManager.getNodesInArea(chunkPos);

        if (nodes.isEmpty()) {

             BrainUtils.clearMemory(entity, SCENT_TARGET);
             return;
        }

        long currentTime = level.getGameTime();
        long maxDuration = SoundAttractConfig.COMMON.scentNodeDurationTicks.get();
        ScentNode bestNode = null;

        if (targetScentOwner == null) {

            ScentNode freshestNode = nodes.stream()
                .filter(n -> (currentTime - n.getTimestamp()) <= maxDuration)
                .max(Comparator.comparingLong(ScentNode::getTimestamp))
                .orElse(null);

            if (freshestNode != null) {
                targetScentOwner = freshestNode.getOwnerUUID();


                bestNode = nodes.stream()
                    .filter(n -> n.getOwnerUUID().equals(targetScentOwner))
                    .filter(n -> (currentTime - n.getTimestamp()) <= maxDuration)
                    .min(Comparator.comparingDouble(n -> n.getPosition().distanceToSqr(entity.position())))
                    .orElse(freshestNode);
            }
        } else {

            bestNode = nodes.stream()
                .filter(n -> n.getOwnerUUID().equals(targetScentOwner))
                .filter(n -> (currentTime - n.getTimestamp()) <= maxDuration)
                .filter(n -> n.getTimestamp() > lastScentTime)
                .min(Comparator.comparingLong(ScentNode::getTimestamp))
                .orElse(null);


            if (bestNode == null) {

            }
        }

        if (bestNode != null) {
            lastScentTime = bestNode.getTimestamp();
            BrainUtils.setMemory(entity, SCENT_TARGET, bestNode.getBlockPos());
        } else if (targetScentOwner != null) {



        }
    }
}