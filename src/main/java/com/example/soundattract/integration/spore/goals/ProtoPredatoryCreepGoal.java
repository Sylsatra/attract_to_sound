package com.example.soundattract.integration.spore.goals;

import com.Harbinger.Spore.Sentities.Organoids.Proto;
import com.example.soundattract.config.separate.IntegrationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumSet;

public class ProtoPredatoryCreepGoal extends Goal {
    private final Proto proto;
    private int creepTickTimer = 0;
    private boolean huntAnnounced = false;

    public ProtoPredatoryCreepGoal(Proto proto) {
        this.proto = proto;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (!IntegrationConfig.ENABLE_PROTO_PREDATORY_CREEP.get()) return false;

        CompoundTag nbt = proto.getPersistentData();
        return nbt.contains("SoundAttract_HuntTarget") && proto.isAlive();
    }

    @Override
    public void start() {
        this.creepTickTimer = proto.getId() % IntegrationConfig.PROTO_CREEP_INTERVAL_TICKS.get();
        this.huntAnnounced = false;
    }

    @Override
    public void tick() {
        if (creepTickTimer > 0) {
            creepTickTimer--;
            return;
        }

        CompoundTag nbt = proto.getPersistentData();
        long targetLong = nbt.getLong("SoundAttract_HuntTarget");
        BlockPos targetPos = BlockPos.of(targetLong);
        BlockPos nodePos = proto.getEntityData().get(Proto.NODE);

        if (nodePos.equals(BlockPos.ZERO)) return;

        double distSqr = nodePos.distSqr(targetPos);

        if (!huntAnnounced) {
            announceHunt(proto);
            huntAnnounced = true;
        }

        if (distSqr < 144) {
            nbt.remove("SoundAttract_HuntTarget");
            nbt.remove("SoundAttract_HuntStartTime");
            return;
        }

        double advanceDist = IntegrationConfig.PROTO_CREEP_ADVANCE_DISTANCE.get();
        double totalDist = Math.sqrt(distSqr);
        double lerpFactor = Math.min(1.0, advanceDist / totalDist);

        int nextX = (int) Mth.lerp(lerpFactor, nodePos.getX(), targetPos.getX());
        int nextY = (int) Mth.lerp(lerpFactor, nodePos.getY(), targetPos.getY());
        int nextZ = (int) Mth.lerp(lerpFactor, nodePos.getZ(), targetPos.getZ());
        BlockPos nextStepPos = new BlockPos(nextX, nextY, nextZ);

        if (shouldGrowAt(nextStepPos)) {
            proto.generateChasing(nextStepPos, proto, 8, 1);
        }

        creepTickTimer = IntegrationConfig.PROTO_CREEP_INTERVAL_TICKS.get();
    }

    private boolean shouldGrowAt(BlockPos pos) {
        if (proto.level().isOutsideBuildHeight(pos)) return false;
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(proto.level().getBlockState(pos).getBlock());
        return id == null || !id.getNamespace().equals("spore");
    }

    private void announceHunt(Proto proto) {
        if (proto.level() instanceof ServerLevel serverLevel) {
            String soundId = IntegrationConfig.PROTO_HUNT_BEGIN_SOUND.get();
            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(ResourceLocation.tryParse(soundId));
            if (sound != null) {
                proto.playSound(sound, 2.0F, 0.8F);
            }

            BlockPos nodePos = proto.getEntityData().get(Proto.NODE);
            if (!nodePos.equals(BlockPos.ZERO)) {
                String particleId = IntegrationConfig.PROTO_HUNT_BEGIN_PARTICLE.get();
            }
        }
    }
}