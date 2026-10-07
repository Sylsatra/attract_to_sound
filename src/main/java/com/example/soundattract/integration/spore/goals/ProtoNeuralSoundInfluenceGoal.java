package com.example.soundattract.integration.spore.goals;

import com.Harbinger.Spore.Sentities.Organoids.Proto;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.tracking.SoundTracker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraftforge.fml.ModList;

import java.util.List;

public class ProtoNeuralSoundInfluenceGoal extends Goal {
    private final Proto proto;
    private int executionTimer = 0;

    public ProtoNeuralSoundInfluenceGoal(Proto proto) {
        this.proto = proto;
    }

    @Override
    public boolean canUse() {
        if (!ModList.get().isLoaded("spore") || proto.level().isClientSide) return false;
        return proto.tickCount % 40 == 0;
    }

    @Override
    public void tick() {
        if (proto.level().isClientSide) return;

        List<SoundTracker.SoundRecord> loudSounds = SoundTracker.getNearbySounds(proto.level(), proto.blockPosition(), 64);
        if (loudSounds.isEmpty()) return;

        double maxWeight = 0;
        for (SoundTracker.SoundRecord sound : loudSounds) {
            maxWeight = Math.max(maxWeight, sound.weight);
        }

        List<LivingEntity> nearbyEntities = proto.level().getEntitiesOfClass(LivingEntity.class,
                proto.getBoundingBox().inflate(64),
                e -> e.getPersistentData().contains("hivemind") &&
                     e.getPersistentData().getInt("hivemind") == proto.getId());

        if (nearbyEntities.isEmpty()) return;

        for (LivingEntity mob : nearbyEntities) {
            CompoundTag data = mob.getPersistentData();
            int decision = data.getInt("decision");
            int member = data.getInt("member");

            if (maxWeight > 30) {
                proto.praisedForDecision(decision, member);
            } else if (maxWeight < 10 && maxWeight > 0) {
                if (proto.level().random.nextFloat() < 0.1f) {
                    proto.punishForDecision(decision, member);
                }
            }
        }
    }
}