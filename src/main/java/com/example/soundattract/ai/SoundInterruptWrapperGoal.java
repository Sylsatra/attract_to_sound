package com.example.soundattract.ai;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.tracking.SoundTracker;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

public class SoundInterruptWrapperGoal extends Goal {
    private final Goal delegate;
    private final Mob mob;
    private long lastYieldCheckTick = -1;
    private Boolean cachedYieldResult = null;

    public SoundInterruptWrapperGoal(Goal delegate, Mob mob) {
        this.delegate = delegate;
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        if (shouldYieldToSound()) {
            stopNavigationAndClearTarget();
            return false;
        }
        return delegate.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        if (shouldYieldToSound()) {
            stopNavigationAndClearTarget();
            return false;
        }
        return delegate.canContinueToUse();
    }

    @Override
    public void start() {
        delegate.start();
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public void tick() {
        if (shouldYieldToSound()) {
            stopNavigationAndClearTarget();
            return;
        }
        delegate.tick();
    }

    private boolean shouldYieldToSound() {
        if (mob == null || mob.level() == null || mob.level().isClientSide()) {
            return false;
        }
        if (mob.getTarget() != null && mob.getTarget().isAlive()) {
            return false;
        }
        if (SoundAttractConfig.COMMON == null || !SoundAttractConfig.COMMON.enableCustomNpcsIntegration.get()) {
            return false;
        }

        long currentTick = mob.level().getGameTime();
        if (lastYieldCheckTick == currentTick && cachedYieldResult != null) {
            return cachedYieldResult;
        }

        boolean result = checkForSound();
        lastYieldCheckTick = currentTick;
        cachedYieldResult = result;
        return result;
    }

    private boolean checkForSound() {
        try {
            SoundTracker.SoundRecord sr = SoundTracker.findNearestSound(
                mob,
                mob.level(),
                mob.blockPosition(),
                mob.getEyePosition()
            );
            if (sr != null && SoundAttractConfig.COMMON.debugLogging.get()) {
                com.example.soundattract.Soundattract.LOGGER.info("[CustomNPCs GoalWrapper] Interrupting {} due to sound at {}", 
                    mob.getName().getString(), sr.pos);
            }
            return sr != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private void stopNavigationAndClearTarget() {
        try {
            mob.getNavigation().stop();
            if (mob.getTarget() != null && !mob.getTarget().isAlive()) {
                mob.setTarget(null);
            }
        } catch (Throwable t) {
        }
    }

    public Goal getDelegate() {
        return delegate;
    }
}
