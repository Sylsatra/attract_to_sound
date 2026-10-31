package com.example.soundattract.mixin;

import com.example.soundattract.event.SoundAttractionEvents;
import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.config.SoundAttractConfig;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

@Pseudo
@Mixin(targets = "noppes.npcs.ai.EntityAIReturn", remap = false)
public abstract class CustomNpcsEntityAIReturnMixin extends Goal {

    private static final Map<Mob, Long> SOUND_YIELD_TICK = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Mob, Boolean> SOUND_YIELD_RESULT = Collections.synchronizedMap(new WeakHashMap<>());

    private static Mob soundattract$getNpcMob(Object self) {
        try {
            Field f = self.getClass().getDeclaredField("npc");
            f.setAccessible(true);
            Object npc = f.get(self);
            if (npc instanceof Mob mob) {
                return mob;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean soundattract$isEligible(Mob mob) {
        if (mob == null) return false;
        if (SoundAttractConfig.COMMON == null || !SoundAttractConfig.COMMON.enableCustomNpcsIntegration.get()) {
            return false;
        }

        Set<EntityType<?>> blacklisted = SoundAttractionEvents.getCachedBlacklistedEntityTypes();
        if (blacklisted.contains(mob.getType())) {
            return false;
        }

        if (SoundAttractionEvents.isCustomNpcsMob(mob)) {
            return true;
        }

        Set<EntityType<?>> attracted = SoundAttractionEvents.getCachedAttractedEntityTypes();
        boolean isAttractedByType = attracted.contains(mob.getType());
        boolean hasMatchingProfile = SoundAttractConfig.getMatchingProfile(mob) != null;
        return isAttractedByType || hasMatchingProfile;
    }

    private static boolean soundattract$isPursuingSound(Mob mob) {
        if (mob == null || mob.goalSelector == null) return false;
        try {
            return mob.goalSelector.getAvailableGoals().stream()
                    .map(w -> w.getGoal())
                    .filter(g -> g instanceof AttractionGoal)
                    .map(g -> (AttractionGoal) g)
                    .anyMatch(AttractionGoal::isPursuingSound);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean soundattract$shouldYieldToSound(Mob mob) {
        if (mob == null || mob.level() == null || mob.level().isClientSide()) return false;
        if (mob.getTarget() != null && mob.getTarget().isAlive()) return false;

        long tick = mob.level().getGameTime();

        Long lastTick = SOUND_YIELD_TICK.get(mob);
        if (lastTick != null && lastTick.longValue() == tick) {
            Boolean cached = SOUND_YIELD_RESULT.get(mob);
            return cached != null && cached;
        }

        boolean result = soundattract$isPursuingSound(mob);
        if (!result) {
            try {
                com.example.soundattract.tracking.SoundTracker.SoundRecord sr = com.example.soundattract.tracking.SoundTracker.findNearestSound(
                    mob,
                    mob.level(),
                    mob.blockPosition(),
                    mob.getEyePosition()
                );
                result = sr != null;
            } catch (Throwable ignored) {
                result = false;
            }
        }

        SOUND_YIELD_TICK.put(mob, tick);
        SOUND_YIELD_RESULT.put(mob, result);
        return result;
    }

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void soundattract$canUse(CallbackInfoReturnable<Boolean> cir) {
        Mob mob = soundattract$getNpcMob(this);
        if (!soundattract$isEligible(mob)) return;
        if (soundattract$shouldYieldToSound(mob)) {
            try {
                mob.getNavigation().stop();
            } catch (Throwable ignored) {
            }
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void soundattract$canContinue(CallbackInfoReturnable<Boolean> cir) {
        Mob mob = soundattract$getNpcMob(this);
        if (!soundattract$isEligible(mob)) return;
        if (soundattract$shouldYieldToSound(mob)) {
            try {
                mob.getNavigation().stop();
            } catch (Throwable ignored) {
            }
            cir.setReturnValue(false);
        }
    }
}
