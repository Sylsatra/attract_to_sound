package com.example.soundattract.ai;

import java.util.EnumSet;
import java.util.UUID;

import com.example.soundattract.tracking.SoundTracker;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.integration.enhancedai.EnhancedAICompat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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
    private static final UUID MOVEMENT_SPEED_MODIFIER_UUID = UUID.fromString("31506c13-0cbd-4f60-be15-62445a6d0842");

    private final Mob mob;
    private final TargetingConditions targetingConditions;

    private Mob toTeleport;
    private int unreachableTime;
    private int cooldown;
    private int teleportTick;

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
        TagKey<EntityType<?>> teleporterTag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(teleporterTagStr));
        if (!this.mob.getType().is(teleporterTag)) return false;

        double chance = EnhancedAICompat.getTeleportToTargetChance(this.mob.level());
        if (this.mob.getRandom().nextDouble() >= chance) return false;

        this.targetSound = SoundTracker.findNearestSound(this.mob, this.mob.level(), this.mob.blockPosition(), this.mob.getEyePosition());
        if (this.targetSound == null) return false;

        String targetTagStr = SoundAttractConfig.COMMON.teleportCanBeTeleportedTag.get();
        if (targetTagStr == null || targetTagStr.isBlank()) return false;
        TagKey<EntityType<?>> targetTag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(targetTagStr));
        this.targetingConditions.range(this.getFollowDistance());
        this.toTeleport = this.mob.level().getNearestEntity(
            this.mob.level().getEntitiesOfClass(Mob.class,
                this.mob.getBoundingBox().inflate(this.getFollowDistance()),
                other -> other != this.mob && other.isAlive() && other.getType().is(targetTag)
            ),
            this.targetingConditions,
            this.mob,
            this.mob.getX(),
            this.mob.getEyeY(),
            this.mob.getZ()
        );
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
                hide(this.mob);
                hide(this.toTeleport);
                this.teleportTick = this.adjustedTickDelay(30);
            }
        }
        else {
            if (--this.teleportTick <= 0) {
                show(this.mob);
                teleportSafely(this.toTeleport, soundPos.getX() + 0.5, soundPos.getY(), soundPos.getZ() + 0.5);
                show(this.toTeleport);
                this.cooldown = this.adjustedTickDelay(EnhancedAICompat.getTeleportCooldownTicks());
                this.stop();
                return;
            }
        }
        if (++this.unreachableTime > this.adjustedTickDelay(120)) {
            this.cooldown = this.adjustedTickDelay(EnhancedAICompat.getTeleportCooldownTicks());
            this.stop();
        }
    }

    private void hide(LivingEntity entity) {
        applySpeedFreeze(entity);
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.PORTAL, entity.getX(), entity.getEyeY(), entity.getZ(), 200, 0.5, 0.5, 0.5, 0.5);
        }
        entity.playSound(SoundEvents.ENDERMAN_TELEPORT, 4f, 0.5f);
        entity.setNoGravity(true);
        entity.setInvisible(true);
    }

    private void show(LivingEntity entity) {
        removeSpeedFreeze(entity);
        entity.setNoGravity(false);
        entity.setInvisible(false);
        entity.playSound(SoundEvents.ENDERMAN_TELEPORT, 1f, 2f);
    }

    private void applySpeedFreeze(LivingEntity entity) {
        AttributeInstance inst = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst != null && inst.getModifier(MOVEMENT_SPEED_MODIFIER_UUID) == null) {
            inst.addTransientModifier(new AttributeModifier(MOVEMENT_SPEED_MODIFIER_UUID, "TeleportToSound freeze", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private void removeSpeedFreeze(LivingEntity entity) {
        AttributeInstance inst = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst != null) {
            inst.removeModifier(MOVEMENT_SPEED_MODIFIER_UUID);
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
            } while (entity.getY() < entity.level().getMaxBuildHeight() && !entity.level().noCollision(entity));
        } else {
            Vec3 oldPos = entity.position();
            entity.teleportTo(pX, pY, pZ);
            entity.level().gameEvent(GameEvent.TELEPORT, oldPos, GameEvent.Context.of(entity));
        }
    }
}