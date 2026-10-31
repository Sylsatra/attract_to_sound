package com.example.soundattract.integration.spore.goals;

import com.Harbinger.Spore.Sentities.Organoids.Proto;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.tracking.SoundTracker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.neoforged.fml.ModList;

import java.util.List;

/**
 * Advanced Spore Integration: Proto Neural-Net Sound Reinforcement.
 * 
 * This goal allows the Proto to "learn" from sound. If a mob it summoned is near 
 * a high-weight sound, the Proto is 'praised' for that decision, reinforcing the 
 * weight of that troop type in its neural network.
 */
public class ProtoNeuralSoundInfluenceGoal extends Goal {
    private final Proto proto;
    private int executionTimer = 0;

    public ProtoNeuralSoundInfluenceGoal(Proto proto) {
        this.proto = proto;
    }

    @Override
    public boolean canUse() {
        if (!ModList.get().isLoaded("spore") || proto.level().isClientSide()) return false;
        return proto.tickCount % 40 == 0;
    }

    @Override
    public void tick() {
        if (proto.level().isClientSide()) return;

        List<SoundTracker.SoundRecord> loudSounds = SoundTracker.getNearbySounds(proto.level(), proto.blockPosition(), 64);
        if (loudSounds.isEmpty()) return;

        double maxWeight = 0;
        for (SoundTracker.SoundRecord sound : loudSounds) {
            maxWeight = Math.max(maxWeight, sound.weight);
        }

        List<LivingEntity> nearbyEntities = proto.level().getEntitiesOfClass(LivingEntity.class, 
                proto.getBoundingBox().inflate(64), 
                e -> e.getPersistentData().contains("hivemind") && 
                     e.getPersistentData().getIntOr("hivemind", 0) == proto.getId());

        if (nearbyEntities.isEmpty()) return;

        for (LivingEntity mob : nearbyEntities) {
            CompoundTag data = mob.getPersistentData();
            int decision = data.getIntOr("decision", 0);
            int member = data.getIntOr("member", 0);

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
