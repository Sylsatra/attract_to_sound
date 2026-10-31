package com.example.soundattract.integration.spore.goals;

import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.ai.LeaderAttractionGoal;
import com.example.soundattract.config.separate.IntegrationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;

/**
 * Propagates sound positions to nearby linked Infected entities.
 * Leverages Spore's built-in searchPos and SearchAreaGoal.
 */
public class InfectedSoundRelayGoal extends net.minecraft.world.entity.ai.goal.Goal {
    private final Infected mob;
    private int relayTimer = 0;

    public InfectedSoundRelayGoal(Infected mob) {
        this.mob = mob;
        this.setFlags(EnumSet.noneOf(net.minecraft.world.entity.ai.goal.Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        return mob.getLinked() && com.example.soundattract.config.separate.IntegrationConfig.ENABLE_SOUND_RELAY.get();
    }

    @Override
    public void tick() {
        if (++relayTimer < 20) return; 
        relayTimer = 0;

        BlockPos targetSound = findCurrentSoundTarget();
        if (targetSound != null) {
            relayToNearby(targetSound);
        }
    }

    private BlockPos findCurrentSoundTarget() {
        return com.example.soundattract.integration.spore.SporeGoalInjectorProxy.getActiveSoundTarget(mob);
    }

    private void relayToNearby(BlockPos pos) {
        double range = com.example.soundattract.config.separate.IntegrationConfig.SOUND_RELAY_RANGE.get();
        AABB area = mob.getBoundingBox().inflate(range);
        List<Infected> nearby = mob.level().getEntitiesOfClass(Infected.class, area, 
            e -> e != mob && e.getLinked() && e.getSearchPos() == null && e.getTarget() == null);

        for (Infected other : nearby) {
            other.setSearchPos(pos);
        }
    }
}
