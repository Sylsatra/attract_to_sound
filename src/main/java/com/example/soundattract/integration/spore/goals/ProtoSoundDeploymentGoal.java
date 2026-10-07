package com.example.soundattract.integration.spore.goals;

import com.Harbinger.Spore.Core.SConfig;
import com.Harbinger.Spore.Core.Sentities;
import com.Harbinger.Spore.Sentities.Organoids.Proto;
import com.Harbinger.Spore.Sentities.Organoids.Vigil;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.integration.spore.SporeGoalInjectorProxy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class ProtoSoundDeploymentGoal extends net.minecraft.world.entity.ai.goal.Goal {
    private final Proto proto;
    private int cooldown = 0;

    public ProtoSoundDeploymentGoal(Proto proto) {
        this.proto = proto;
        this.setFlags(EnumSet.noneOf(net.minecraft.world.entity.ai.goal.Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }

        if (!com.example.soundattract.config.separate.IntegrationConfig.ENABLE_PROTO_SOUND_DEPLOYMENT.get()) {
            return false;
        }

        if (proto.getBiomass() < com.example.soundattract.config.separate.IntegrationConfig.PROTO_SOUND_BIOMASS_THRESHOLD.get()) {
            return false;
        }

        double weight = SporeGoalInjectorProxy.getActiveSoundWeight(proto);
        double threshold = com.example.soundattract.config.separate.IntegrationConfig.PROTO_SOUND_WEIGHT_THRESHOLD.get();
        return weight >= threshold;
    }

    @Override
    public void start() {
        BlockPos targetPos = SporeGoalInjectorProxy.getActiveSoundTarget(proto);
        if (targetPos == null) return;

        if (!com.example.soundattract.config.separate.IntegrationConfig.SMART_SPREAD_TO_SOUND.get()) {
            applyBiasedSpread(targetPos);
        }

        deployScout(targetPos);

        cooldown = com.example.soundattract.config.separate.IntegrationConfig.PROTO_SOUND_DEPLOYMENT_COOLDOWN_TICKS.get();
    }

    private void applyBiasedSpread(BlockPos soundPos) {
        BlockPos nodePos = proto.getEntityData().get(Proto.NODE);
        if (nodePos.equals(BlockPos.ZERO)) return;

        double maxDist = com.example.soundattract.config.separate.IntegrationConfig.PROTO_SPREAD_MAX_DISTANCE.get();
        if (proto.distanceToSqr(soundPos.getX(), soundPos.getY(), soundPos.getZ()) > maxDist * maxDist) {
            return;
        }

        double biasFactor = com.example.soundattract.config.separate.IntegrationConfig.PROTO_SPREAD_LERP_FACTOR.get();

        int targetX = (int) Mth.lerp(biasFactor, nodePos.getX(), soundPos.getX());
        int targetY = (int) Mth.lerp(biasFactor, nodePos.getY(), soundPos.getY());
        int targetZ = (int) Mth.lerp(biasFactor, nodePos.getZ(), soundPos.getZ());

        BlockPos biasedCenter = new BlockPos(targetX, targetY, targetZ);

        proto.generateChasing(biasedCenter, proto, 32, 2);
    }

    private void deployScout(BlockPos soundPos) {
        if (!(proto.level() instanceof ServerLevel serverLevel)) return;

        long vigilCount = serverLevel.getEntitiesOfClass(Vigil.class, proto.getBoundingBox().inflate(128)).size();
        if (vigilCount >= 3) return;

        Vigil vigil = Sentities.VIGIL.get().create(serverLevel);
        if (vigil != null) {
            vigil.moveTo(soundPos.getX(), soundPos.getY(), soundPos.getZ());
            vigil.setProto(proto);
            vigil.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(soundPos), MobSpawnType.EVENT, null, null);
            serverLevel.addFreshEntity(vigil);
        }
    }
}