package noppes.npcs.ai;

import net.minecraft.world.entity.ai.goal.Goal;

/** A goal with a CustomNPCs package name so the production wrapper can be tested without an NPC template. */
public final class SoundAttractGameTestGoal extends Goal {
    public int starts;
    public int stops;
    public int ticks;

    public SoundAttractGameTestGoal() {
        setFlags(java.util.EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return true;
    }

    @Override
    public void start() {
        starts++;
    }

    @Override
    public void stop() {
        stops++;
    }

    @Override
    public void tick() {
        ticks++;
    }
}
