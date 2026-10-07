package com.example.soundattract.ai;

import com.example.soundattract.config.DebugLoggingConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.integration.enhancedai.EnhancedAICompat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.ForgeEventFactory;

public class BlockBreakerPosGoal extends Goal {

    private final Mob miner;
    private final BlockPos destinationPos;
    private final double reachDistance;
    private final double timeToBreakMultiplier;
    private final List<BlockPos> targetBlocks = new ArrayList<>();
    private int tickToBreak = 0;
    private int breakingTick = 0;
    private BlockState blockState = null;
    private int prevBreakProgress = 0;
    private final boolean toolOnly;
    private final boolean properToolOnly;
    private final boolean properToolRequired;

    public BlockBreakerPosGoal(Mob miner, BlockPos destination, double timeToBreakMultiplier, boolean toolOnly, boolean properToolOnly, boolean properToolRequired) {
        this.miner = miner;
        this.destinationPos = destination;
        this.reachDistance = 4.9;
        this.timeToBreakMultiplier = timeToBreakMultiplier;
        this.toolOnly = toolOnly;
        this.properToolOnly = properToolOnly;
        this.properToolRequired = properToolRequired;
        this.setFlags(EnumSet.of(Flag.LOOK, Flag.MOVE));
    }


    @Override
    public boolean canUse() {
        if (!this.miner.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.BLOCK_BREAKING)) {
                SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] canUse FAILED for {}: mobGriefing is false.", this.miner.getName().getString());
            }
            return false;
        }
        if (this.destinationPos == null) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.BLOCK_BREAKING)) {
                SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] canUse FAILED for {}: destinationPos is null.", this.miner.getName().getString());
            }
            return false;
        }
        if (this.toolOnly && !(this.miner.getOffhandItem().getItem() instanceof DiggerItem)) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.BLOCK_BREAKING)) {
                SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] canUse FAILED for {}: toolOnly is true, but no DiggerItem in offhand.", this.miner.getName().getString());
            }
            return false;
        }

        this.fillTargetBlocks();
        if (this.targetBlocks.isEmpty()) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.BLOCK_BREAKING)) {
                SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] canUse FAILED for {}: fillTargetBlocks() found no blocks to break.", this.miner.getName().getString());
            }
            return false;
        }

        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.BLOCK_BREAKING)) {
            SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] canUse PASSED for {}. Target block: {}.", this.miner.getName().getString(), this.targetBlocks.get(0));
        }
        return true;
    }


    @Override
    public boolean canContinueToUse() {
        if (this.targetBlocks.isEmpty() || this.destinationPos == null) {
            return false;
        }

        if (this.properToolOnly && this.blockState != null && !this.canBreakBlock()) {
            return false;
        }

        if (this.miner.blockPosition().distSqr(this.destinationPos) < 4.0d) {
            return false;
        }

        return !this.miner.level().getBlockState(this.targetBlocks.get(0)).isAir();
    }

    @Override
    public void start() {
        if (!this.targetBlocks.isEmpty()) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.BLOCK_BREAKING)) {
                SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] STARTING for {}. Beginning to break {}.", this.miner.getName().getString(), this.targetBlocks.get(0));
            }
            initBlockBreak();
            this.miner.setAggressive(true);
        } else {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.BLOCK_BREAKING)) {
                SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] Attempted to START for {}, but targetBlocks was empty.", this.miner.getName().getString());
            }
        }
    }

    @Override
    public void stop() {
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.BLOCK_BREAKING)) {
            SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] STOPPING for {}.", this.miner.getName().getString());
        }
        if (!this.targetBlocks.isEmpty()) {
            this.miner.level().destroyBlockProgress(this.miner.getId(), targetBlocks.get(0), -1);
            this.targetBlocks.clear();
        }
        this.tickToBreak = 0;
        this.breakingTick = 0;
        this.blockState = null;
        this.prevBreakProgress = 0;
        this.miner.setAggressive(false);
    }

    @Override
    public void tick() {
        if (this.targetBlocks.isEmpty()) {
            return;
        }
        if (this.properToolOnly && this.blockState != null && !this.canBreakBlock()) {
            return;
        }

        BlockPos pos = this.targetBlocks.get(0);
        this.breakingTick++;
        this.miner.getLookControl().setLookAt(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d);

        if (this.prevBreakProgress != (int) ((this.breakingTick / (float) this.tickToBreak) * 10)) {
            this.prevBreakProgress = (int) ((this.breakingTick / (float) this.tickToBreak) * 10);
            this.miner.level().destroyBlockProgress(this.miner.getId(), pos, this.prevBreakProgress);
        }

        if (this.breakingTick % 6 == 0) {
            this.miner.swing(InteractionHand.MAIN_HAND);
        }
        if (this.breakingTick % 4 == 0) {
            SoundType soundType = this.blockState.getSoundType(this.miner.level(), pos, this.miner);
            this.miner.level().playSound(null, pos, soundType.getHitSound(), SoundSource.BLOCKS, (soundType.getVolume() + 1.0F) / 8.0F, soundType.getPitch() * 0.5F);
        }

        if (this.breakingTick >= this.tickToBreak && this.miner.level() instanceof ServerLevel level) {
            if (ForgeEventFactory.onEntityDestroyBlock(this.miner, this.targetBlocks.get(0), this.blockState) && this.miner.level().destroyBlock(pos, false, this.miner)) {
                BlockEntity blockentity = this.blockState.hasBlockEntity() ? this.miner.level().getBlockEntity(pos) : null;
                LootParams.Builder lootparams$builder = (new LootParams.Builder(level)).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).withParameter(LootContextParams.TOOL, this.miner.getOffhandItem()).withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockentity).withOptionalParameter(LootContextParams.THIS_ENTITY, this.miner);
                this.blockState.spawnAfterBreak(level, pos, this.miner.getOffhandItem(), false);
                this.blockState.getDrops(lootparams$builder).forEach((itemStack) -> level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f, itemStack)));
            }
            this.miner.level().destroyBlockProgress(this.miner.getId(), pos, -1);
            this.targetBlocks.remove(0);
            if (!this.targetBlocks.isEmpty()) {
                initBlockBreak();
            }
        }
    }

    private void initBlockBreak() {
        this.blockState = this.miner.level().getBlockState(this.targetBlocks.get(0));
        this.tickToBreak = computeTickToBreak();
        this.breakingTick = 0;
    }


    private void fillTargetBlocks() {
        this.targetBlocks.clear();
        if (this.destinationPos == null) {
            return;
        }

        Vec3 destinationVec = Vec3.atCenterOf(this.destinationPos);
        int mobHeight = Mth.ceil(this.miner.getBbHeight());

        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.BLOCK_BREAKING)) {
            SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] fillTargetBlocks for {}: Raycasting from mob eyes to {}.", this.miner.getName().getString(), destinationVec);
        }

        for (int i = 0; i < mobHeight; i++) {
            Vec3 rayStart = this.miner.getEyePosition().add(0, i, 0);
            BlockHitResult rayTraceResult = this.miner.level().clip(new ClipContext(rayStart, destinationVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.miner));

            if (rayTraceResult.getType() == HitResult.Type.MISS) {
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.BLOCK_BREAKING)) {
                    SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] -> Raycast MISS. No blocks in the way.");
                }
                continue;
            }

            BlockPos hitPos = rayTraceResult.getBlockPos();
            BlockState state = this.miner.level().getBlockState(hitPos);

            if (this.targetBlocks.contains(hitPos)) {
                continue;
            }
            if (hitPos.getY() > EnhancedAICompat.getMaxY()) {
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.BLOCK_BREAKING)) {
                    SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] -> FAILED check for {}: Block {} at Y={} is above maxY of {}.", this.miner.getName().getString(), state.getBlock().getName().getString(), hitPos.getY(), EnhancedAICompat.getMaxY());
                }
                continue;
            }

            double distance = this.miner.distanceToSqr(rayTraceResult.getLocation());
            if (distance > this.reachDistance * this.reachDistance) {
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.BLOCK_BREAKING)) {
                    SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] -> FAILED check for {}: Block {} is too far away (distSqr {} > {}).", this.miner.getName().getString(), state.getBlock().getName().getString(), distance, this.reachDistance * this.reachDistance);
                }
                continue;
            }

            if (state.hasBlockEntity() && EnhancedAICompat.shouldBlacklistTileEntities()) {
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.BLOCK_BREAKING)) {
                    SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] -> FAILED check for {}: Block {} is a blacklisted Tile Entity.", this.miner.getName().getString(), state.getBlock().getName().getString());
                }
                continue;
            }

            if (state.getDestroySpeed(this.miner.level(), hitPos) == -1) {
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.BLOCK_BREAKING)) {
                    SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] -> FAILED check for {}: Block {} is unbreakable (destroy speed -1).", this.miner.getName().getString(), state.getBlock().getName().getString());
                }
                continue;
            }

            if ((!EnhancedAICompat.isBlacklistAsWhitelist() && state.is(EnhancedAICompat.getBlockBlacklistTag())) || (EnhancedAICompat.isBlacklistAsWhitelist() && !state.is(EnhancedAICompat.getBlockBlacklistTag()))) {
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.BLOCK_BREAKING)) {
                    SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] -> FAILED check for {}: Block {} is in the blacklist/not in whitelist.", this.miner.getName().getString(), state.getBlock().getName().getString());
                }
                continue;
            }

            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.BLOCK_BREAKING)) {
                SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] -> SUCCESS: Found valid block to break: {} at {}.", state.getBlock().getName().getString(), hitPos);
            }
            this.targetBlocks.add(hitPos);
        }
        Collections.reverse(this.targetBlocks);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private int computeTickToBreak() {
        int canHarvestBlock = this.canHarvestBlock() ? 30 : 100;
        if (this.blockState.getDestroySpeed(this.miner.level(), this.targetBlocks.get(0)) == 0f) {
            return 1;
        }
        double diggingSpeed = this.getDigSpeed() / this.blockState.getDestroySpeed(this.miner.level(), this.targetBlocks.get(0)) / canHarvestBlock;
        return Mth.ceil((1f / diggingSpeed) * this.timeToBreakMultiplier);
    }

    private float getDigSpeed() {
        float digSpeed = this.miner.getOffhandItem().getDestroySpeed(this.blockState);
        if (digSpeed > 1.0F) {
            int efficiencyLevel = EnchantmentHelper.getBlockEfficiency(this.miner);
            ItemStack itemstack = this.miner.getOffhandItem();
            if (efficiencyLevel > 0 && !itemstack.isEmpty()) {
                digSpeed += (float) (efficiencyLevel * efficiencyLevel + 1);
            }
        }

        if (MobEffectUtil.hasDigSpeed(this.miner)) {
            digSpeed *= 1.0F + (float) (MobEffectUtil.getDigSpeedAmplification(this.miner) + 1) * 0.2F;
        }

        if (this.miner.hasEffect(MobEffects.DIG_SLOWDOWN)) {
            float miningFatigueAmplifier = switch (this.miner.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) {
                case 0 ->
                    0.3F;
                case 1 ->
                    0.09F;
                case 2 ->
                    0.0027F;
                default ->
                    8.1E-4F;
            };
            digSpeed *= miningFatigueAmplifier;
        }

        if (this.miner.isEyeInFluidType(ForgeMod.WATER_TYPE.get()) && !EnchantmentHelper.hasAquaAffinity(this.miner)) {
            digSpeed /= 5.0F;
        }

        return digSpeed;
    }

    private boolean canBreakBlock() {
        if (!this.blockState.requiresCorrectToolForDrops() || !this.properToolRequired) {
            return true;
        }

        ItemStack stack = this.miner.getOffhandItem();
        return !stack.isEmpty() && stack.isCorrectToolForDrops(this.blockState);
    }

    private boolean canHarvestBlock() {
        if (!this.blockState.requiresCorrectToolForDrops()) {
            return true;
        }

        ItemStack stack = this.miner.getOffhandItem();
        return !stack.isEmpty() && stack.isCorrectToolForDrops(this.blockState);
    }

}