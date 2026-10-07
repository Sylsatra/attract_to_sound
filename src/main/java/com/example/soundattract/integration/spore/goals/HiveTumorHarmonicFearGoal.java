package com.example.soundattract.integration.spore.goals;

import com.Harbinger.Spore.Sentities.Organoids.HiveTumor;
import com.example.soundattract.tracking.SoundTracker;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraftforge.fml.ModList;

import java.util.List;

public class HiveTumorHarmonicFearGoal extends Goal {
    private final HiveTumor hiveTumor;

    public HiveTumorHarmonicFearGoal(HiveTumor hiveTumor) {
        this.hiveTumor = hiveTumor;
    }

    @Override
    public boolean canUse() {
        if (!ModList.get().isLoaded("spore") || hiveTumor.level().isClientSide) return false;

        if (hiveTumor.isScared()) return false;

        return hiveTumor.tickCount % 20 == 0;
    }

    @Override
    public void tick() {
        if (hiveTumor.level().isClientSide) return;

        double range = 32.0;

        List<SoundTracker.SoundRecord> playerSounds = SoundTracker.getNearbySounds(hiveTumor.level(), hiveTumor.blockPosition(), range);

        boolean heardThreat = false;
        for (SoundTracker.SoundRecord sound : playerSounds) {
            if (sound.isPlayerProduced() || sound.weight > 15) {
                heardThreat = true;
                break;
            }
        }

        if (heardThreat) {
            hiveTumor.setScaredTicks(6000);
        }
    }
}