package com.example.soundattract.integration.spore.goals;

import com.Harbinger.Spore.Sentities.Organoids.Proto;
import com.Harbinger.Spore.Sentities.Organoids.Vigil;
import com.example.soundattract.config.separate.IntegrationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * AI goal for Vigil scouts to report player sightings back to their Proto parent.
 * This triggers the Proto's predatory creeping behavior.
 */
public class VigilSightedReporterGoal extends Goal {
    private final Vigil vigil;
    private int reportCooldown = 0;

    public VigilSightedReporterGoal(Vigil vigil) {
        this.vigil = vigil;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (!IntegrationConfig.ENABLE_PROTO_PREDATORY_CREEP.get()) return false;
        
        if (reportCooldown > 0) {
            reportCooldown--;
            return false;
        }

        LivingEntity target = vigil.getTarget();
        return target instanceof Player && getProtoParent(vigil) != null;
    }

    @Override
    public void start() {
        Proto proto = getProtoParent(vigil);
        if (proto == null || !proto.isAlive()) return;

        LivingEntity target = vigil.getTarget();
        if (target == null) return;

        BlockPos targetPos = target.blockPosition();
        CompoundTag nbt = proto.getPersistentData();
        
        if (!nbt.contains("SoundAttract_HuntTarget") || 
            BlockPos.of(nbt.getLongOr("SoundAttract_HuntTarget", 0L)).distSqr(targetPos) > 64) {
            
            nbt.putLong("SoundAttract_HuntTarget", targetPos.asLong());
            nbt.putLong("SoundAttract_HuntStartTime", vigil.level().getGameTime());
            
            playAlertSound(vigil);
            
            reportCooldown = 400; 
        }
    }

    private Proto getProtoParent(Vigil vigil) {
        try {
            try {
                java.lang.reflect.Method getOwner = vigil.getClass().getMethod("getOwner");
                Object result = getOwner.invoke(vigil);
                if (result instanceof Proto proto) return proto;
            } catch (NoSuchMethodException ignored) {}

            java.lang.reflect.Field field = vigil.getClass().getDeclaredField("proto");
            field.setAccessible(true);
            Object result = field.get(vigil);
            if (result instanceof Proto proto) return proto;
        } catch (Exception e) {
        }
        return null;
    }

    private void playAlertSound(Vigil vigil) {
        String soundId = IntegrationConfig.VIGIL_SIGHTED_SOUND.get();
        SoundEvent sound = vigil.level().registryAccess().lookupOrThrow(Registries.SOUND_EVENT).get(Identifier.tryParse(soundId));
        if (sound != null) {
            vigil.playSound(sound, 1.0F, 1.0F);
        }
    }
}
