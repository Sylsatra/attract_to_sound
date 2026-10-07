package com.example.soundattract.integration.spore.goals;

import com.Harbinger.Spore.Sentities.Organoids.Delusionare;
import com.Harbinger.Spore.Sentities.Organoids.Proto;
import com.example.soundattract.config.separate.IntegrationConfig;
import com.example.soundattract.tracking.SoundTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.List;

public class DelusionareSoundAttackGoal extends Goal {
    private final Delusionare delusionare;
    private int cooldown = 0;

    public DelusionareSoundAttackGoal(Delusionare delusionare) {
        this.delusionare = delusionare;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (!IntegrationConfig.ENABLE_SPORE_INTEGRATION.get() || !IntegrationConfig.ENABLE_DELUGE_OF_SOUND.get()) {
            return false;
        }
        if (delusionare.isCasting() || cooldown > 0) {
            if (cooldown > 0) cooldown--;
            return false;
        }
        return delusionare.isAlive() && delusionare.level().getGameTime() % 80 == 0;
    }

    @Override
    public void tick() {
        Level level = delusionare.level();
        double threshold = IntegrationConfig.DELUGE_OF_SOUND_WEIGHT_THRESHOLD.get();

        List<SoundTracker.SoundRecord> sounds = SoundTracker.getNearbySounds(
                level,
                level.dimension().location().toString(),
                delusionare.blockPosition(),
                null
        );

        for (SoundTracker.SoundRecord record : sounds) {
            if (record.weight >= threshold && delusionare.blockPosition().closerThan(record.pos, 64.0)) {
                triggerTeleport();
                return;
            }
        }

        CompoundTag data = delusionare.getPersistentData();
        if (data.contains("hivemind")) {
            int protoId = data.getInt("hivemind");
            Entity owner = level.getEntity(protoId);
            if (owner instanceof Proto proto) {
                com.Harbinger.Spore.Sentities.Signal signal = proto.getSignal();
                if (signal != null && signal.active() && signal.pos() != null && delusionare.blockPosition().closerThan(signal.pos(), 64.0)) {
                    triggerTeleport();
                }
            }
        }
    }

    private void triggerTeleport() {
        delusionare.setSpellId(3);
        delusionare.setSpellTime(1);
        cooldown = 400;
    }
}