package com.example.soundattract.ai;

import java.util.EnumSet;
import java.util.UUID;

import com.example.soundattract.tracking.SoundTracker;
import com.example.soundattract.config.SoundAttractConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public class TeleportToSoundGoal extends Goal {
    private static final Identifier MOVEMENT_SPEED_MODIFIER_ID = Identifier.fromNamespaceAndPath("soundattract", "teleport_speed_freeze");

    private final Mob mob;
    private final TargetingConditions targetingConditions;

    private Mob toTeleport;
    private int unreachableTime;
    private int cooldown;
    private int teleportTick;
    private boolean mobWasNoGravity;
    private boolean mobWasInvisible;
    private boolean targetWasNoGravity;
    private boolean targetWasInvisible;
    private boolean mobSpeedFreezeApplied;
    private boolean targetSpeedFreezeApplied;
    private boolean mobHidden;
    private boolean targetHidden;

    private SoundTracker.SoundRecord targetSound;

    public TeleportToSoundGoal(Mob mob) {
        this.mob = mob;
        this.targetingConditions = TargetingConditions.forNonCombat().range(this.getFollowDistance());
        this.setFlags(EnumSet.of(Flag.TARGET, Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!SoundAttractConfig.COMMON.enableTeleportToSound.get()) return false;
        if (--this.cooldown > 0) return false;
        if (this.mob.isBaby()) return false;

        String teleporterTagStr = SoundAttractConfig.COMMON.teleportCanTeleportTag.get();
        if (teleporterTagStr == null || teleporterTagStr.isBlank()) return false;
        Identifier teleporterTagLoc = Identifier.tryParse(teleporterTagStr);
        if (teleporterTagLoc == null) return false;
        TagKey<EntityType<?>> teleporterTag = TagKey.create(Registries.ENTITY_TYPE, teleporterTagLoc);
        if (!this.mob.getType().is(teleporterTag)) return false;

        double chance = SoundAttractConfig.COMMON.teleportChance.get();
        if (this.mob.getRandom().nextDouble() >= chance) return false;

        this.targetSound = SoundTracker.findNearestSound(this.mob, this.mob.level(), this.mob.blockPosition(), this.mob.getEyePosition());
        if (this.targetSound == null) return false;

        String targetTagStr = SoundAttractConfig.COMMON.teleportCanBeTeleportedTag.get();
        if (targetTagStr == null || targetTagStr.isBlank()) return false;
        Identifier targetTagLoc = Identifier.tryParse(targetTagStr);
        if (targetTagLoc == null) return false;
        TagKey<EntityType<?>> targetTag = TagKey.create(Registries.ENTITY_TYPE, targetTagLoc);
        this.targetingConditions.range(this.getFollowDistance());
        this.toTeleport = this.mob.level().getEntitiesOfClass(Mob.class,
                        this.mob.getBoundingBox().inflate(this.getFollowDistance()),
                        other -> other != this.mob && other.isAlive() && other.getType().is(targetTag))
                .stream()
                .filter(candidate -> this.targetingConditions.test((net.minecraft.server.level.ServerLevel) this.mob.level(), this.mob, candidate))
                .min(java.util.Comparator.comparingDouble(this.mob::distanceToSqr))
                .orElse(null);
        return this.toTeleport != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.toTeleport != null && this.toTeleport.isAlive() && this.targetSound != null && this.targetSound.ticksRemaining > 0;
    }

    @Override
    public void start() {
        this.mob.getLookControl().setLookAt(this.toTeleport);
        this.mob.getNavigation().stop();
        this.mob.getNavigation().moveTo(this.toTeleport, 1.5f);
        this.toTeleport.getNavigation().stop();
    }

    @Override
    public void stop() {
        restoreHiddenEntities(false);
        this.toTeleport = null;
        this.unreachableTime = 0;
        this.targetSound = null;
        this.teleportTick = 0;
    }

    protected double getFollowDistance() {
        return this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
    }

    @Override
    public void tick() {
        if (this.targetSound == null) {
            this.stop();
            return;
        }
        BlockPos soundPos = this.targetSound.pos;
        if (this.teleportTick <= 0) {
            this.mob.getLookControl().setLookAt(this.toTeleport);
            this.toTeleport.getNavigation().stop();
            if (this.mob.getNavigation().isDone())
                this.mob.getNavigation().moveTo(this.toTeleport, 1.5f);
            if (this.mob.distanceToSqr(this.toTeleport) <= 4f) {
                hide(this.mob, true);
                hide(this.toTeleport, false);
                this.teleportTick = this.adjustedTickDelay(30);
            }
        }
        else {
            if (--this.teleportTick <= 0) {
                restoreMob(true);
                try {
                    teleportSafely(this.toTeleport, soundPos.getX() + 0.5, soundPos.getY(), soundPos.getZ() + 0.5);
                } finally {
                    restoreTarget(true);
                }
                this.cooldown = this.adjustedTickDelay(SoundAttractConfig.COMMON.teleportCooldownTicks.get());
                this.stop();
                return;
            }
        }
        if (++this.unreachableTime > this.adjustedTickDelay(120)) {
            this.cooldown = this.adjustedTickDelay(SoundAttractConfig.COMMON.teleportCooldownTicks.get());
            this.stop();
        }
    }

    private void hide(LivingEntity entity, boolean isMob) {
        if (isMob) {
            mobHidden = true;
            mobWasNoGravity = entity.isNoGravity();
            mobWasInvisible = entity.isInvisible();
            mobSpeedFreezeApplied = applySpeedFreeze(entity);
        } else {
            targetHidden = true;
            targetWasNoGravity = entity.isNoGravity();
            targetWasInvisible = entity.isInvisible();
            targetSpeedFreezeApplied = applySpeedFreeze(entity);
        }
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.PORTAL, entity.getX(), entity.getEyeY(), entity.getZ(), 200, 0.5, 0.5, 0.5, 0.5);
        }
        entity.playSound(SoundEvents.ENDERMAN_TELEPORT, 4f, 0.5f);
        entity.setNoGravity(true);
        entity.setInvisible(true);
    }

    private void restoreHiddenEntities(boolean playSound) {
        restoreMob(playSound);
        restoreTarget(playSound);
    }

    private void restoreMob(boolean playSound) {
        if (mobHidden) restoreEntity(this.mob, mobWasNoGravity, mobWasInvisible, mobSpeedFreezeApplied, playSound);
        mobSpeedFreezeApplied = false;
        mobWasNoGravity = false;
        mobWasInvisible = false;
        mobHidden = false;
    }

    private void restoreTarget(boolean playSound) {
        if (toTeleport != null && targetHidden) {
            restoreEntity(this.toTeleport, targetWasNoGravity, targetWasInvisible, targetSpeedFreezeApplied, playSound);
        }
        targetSpeedFreezeApplied = false;
        targetWasNoGravity = false;
        targetWasInvisible = false;
        targetHidden = false;
    }

    private void restoreEntity(LivingEntity entity, boolean wasNoGravity, boolean wasInvisible,
                               boolean removeFreeze, boolean playSound) {
        if (removeFreeze) removeSpeedFreeze(entity);
        entity.setNoGravity(wasNoGravity);
        entity.setInvisible(wasInvisible);
        if (playSound) entity.playSound(SoundEvents.ENDERMAN_TELEPORT, 1f, 2f);
    }

    private boolean applySpeedFreeze(LivingEntity entity) {
        AttributeInstance inst = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst != null && inst.getModifier(MOVEMENT_SPEED_MODIFIER_ID) == null) {
            inst.addTransientModifier(new AttributeModifier(MOVEMENT_SPEED_MODIFIER_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            return true;
        }
        return false;
    }

    private void removeSpeedFreeze(LivingEntity entity) {
        AttributeInstance inst = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst != null) {
            inst.removeModifier(MOVEMENT_SPEED_MODIFIER_ID);
        }
    }

    private void teleportSafely(LivingEntity entity, double pX, double pY, double pZ) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(pX, pY, pZ);
        BlockState state = entity.level().getBlockState(mutable);
        boolean blocksMotion = state.isCollisionShapeFullBlock(entity.level(), mutable);
        boolean isWater = state.getFluidState().is(FluidTags.WATER);
        if (blocksMotion && !isWater) {
            do {
                pY++;
                Vec3 oldPos = entity.position();
                entity.teleportTo(pX, pY, pZ);
                entity.level().gameEvent(GameEvent.TELEPORT, oldPos, GameEvent.Context.of(entity));
            } while (entity.getY() < entity.level().getMaxY() && !entity.level().noCollision(entity));
        } else {
            Vec3 oldPos = entity.position();
            entity.teleportTo(pX, pY, pZ);
            entity.level().gameEvent(GameEvent.TELEPORT, oldPos, GameEvent.Context.of(entity));
        }
    }
}

