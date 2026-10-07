package com.example.soundattract.ai;

import java.util.EnumSet;

import com.example.soundattract.tracking.SoundTracker;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.integration.enhancedai.EnhancedAICompat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.phys.Vec3;

public class PickUpAndThrowToSoundGoal extends Goal {
    private final Mob mob;
    private final TargetingConditions targetingConditions;
    private Mob pickUp;
    private int unreachableTime;
    private int cooldown;

    private SoundTracker.SoundRecord targetSound;

    public PickUpAndThrowToSoundGoal(Mob mob) {
        this.mob = mob;
        this.targetingConditions = TargetingConditions.forNonCombat()
                .range(this.getFollowDistance())
                .selector(livingEntity -> !livingEntity.isPassenger());
        this.setFlags(EnumSet.of(Flag.TARGET, Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!SoundAttractConfig.COMMON.enablePickUpAndThrowToSound.get()) return false;
        if (this.mob.isVehicle()) return false;
        if (--this.cooldown > 0) return false;

        double chance = EnhancedAICompat.getPickUpAndThrowChance(this.mob.level());
        if (this.mob.getRandom().nextDouble() >= chance) return false;

        this.targetSound = SoundTracker.findNearestSound(this.mob, this.mob.level(), this.mob.blockPosition(), this.mob.getEyePosition());
        if (this.targetSound == null) return false;

        String canPickUpTagStr = SoundAttractConfig.COMMON.pickUpCanPickUpTag.get();
        if (canPickUpTagStr == null || canPickUpTagStr.isBlank()) return false;
        TagKey<EntityType<?>> canPickUpTag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(canPickUpTagStr));
        if (!this.mob.getType().is(canPickUpTag)) return false;

        int minDist = EnhancedAICompat.getPickUpMinDistanceToPickUp();
        double distToSound = Math.sqrt(this.mob.blockPosition().distSqr(this.targetSound.pos));
        if (distToSound < minDist) return false;

        String canBePickedUpTagStr = SoundAttractConfig.COMMON.pickUpCanBePickedUpTag.get();
        if (canBePickedUpTagStr == null || canBePickedUpTagStr.isBlank()) return false;
        TagKey<EntityType<?>> canBePickedUpTag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(canBePickedUpTagStr));

        this.targetingConditions.range(this.getFollowDistance());
        this.pickUp = this.mob.level().getNearestEntity(
                this.mob.level().getEntitiesOfClass(Mob.class,
                        this.mob.getBoundingBox().inflate(this.getFollowDistance()),
                        living -> living.getType().is(canBePickedUpTag)),
                this.targetingConditions,
                this.mob,
                this.mob.getX(),
                this.mob.getEyeY(),
                this.mob.getZ());
        return this.pickUp != null && this.pickUp.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return this.pickUp != null && this.pickUp.isAlive() && this.targetSound != null && this.targetSound.ticksRemaining > 0;
    }

    @Override
    public void start() {
        this.mob.getLookControl().setLookAt(this.pickUp);
        this.mob.getNavigation().stop();
        this.mob.getNavigation().moveTo(this.pickUp, EnhancedAICompat.getPickUpSpeedModifier());
        this.pickUp.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.pickUp = null;
        this.unreachableTime = 0;
        this.targetSound = null;
    }

    protected double getFollowDistance() {
        return this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
    }

    @Override
    public void tick() {

        if (this.targetSound == null || this.targetSound.ticksRemaining <= 0) {
            this.targetSound = SoundTracker.findNearestSound(this.mob, this.mob.level(), this.mob.blockPosition(), this.mob.getEyePosition());
            if (this.targetSound == null) {
                this.stop();
                return;
            }
        }

        if (!this.mob.isVehicle()) {
            this.pickUp.getNavigation().stop();
            if (this.mob.getNavigation().isDone())
                this.mob.getNavigation().moveTo(this.pickUp, EnhancedAICompat.getPickUpSpeedModifier());
            if (this.mob.distanceToSqr(this.pickUp) <= 4f) {
                this.pickUp.startRiding(this.mob, false);
                this.cooldown = this.adjustedTickDelay(20);
            }
        }
        else {
            BlockPos soundPos = this.targetSound.pos;
            int maxThrow = EnhancedAICompat.getPickUpMaxDistanceToThrow();
            double distToSoundSq = this.mob.blockPosition().distSqr(soundPos);
            if (--this.cooldown <= 0 && distToSoundSq <= (double) (maxThrow * maxThrow)) {
                double distanceY = soundPos.getY() + 0.5 - this.pickUp.getY();
                double distanceX = soundPos.getX() + 0.5 - this.pickUp.getX();
                double distanceZ = soundPos.getZ() + 0.5 - this.pickUp.getZ();
                double distanceXZ = Math.sqrt(distanceX * distanceX + distanceZ * distanceZ);

                Vec3 motion = new Vec3(distanceX * 0.1d, Mth.clamp(distanceY, 4d, 40d) / 10d + distanceXZ / 100d, distanceZ * 0.1d);
                this.pickUp.stopRiding();
                this.pickUp.setDeltaMovement(motion);
                this.mob.playSound(SoundEvents.AXE_STRIP, 3f, 1.5F);

                this.cooldown = this.adjustedTickDelay(EnhancedAICompat.getPickUpCooldownTicks());
                this.stop();
                return;
            }
        }
        if (++this.unreachableTime > this.adjustedTickDelay(120)) {
            this.cooldown = this.adjustedTickDelay(EnhancedAICompat.getPickUpCooldownTicks());
            this.stop();
        }
    }
}