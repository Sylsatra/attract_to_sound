package com.example.soundattract.event;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.registration.ModSounds;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class FloorCreekEvents {

    public static final TagKey<Block> FLOOR_CREEK_BLOCKS =
            TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath("soundattract", "floor_creek_blocks"));

    private static final Cache<UUID, State> STATES = Caffeine.newBuilder()
            .maximumSize(4096)
            .build();

    public static double poseProbability(boolean visuallySwimming,
                                         boolean inWater,
                                         boolean crouching,
                                         boolean sprinting,
                                         double swimCrawlProb,
                                         double sneakProb,
                                         double walkProb,
                                         double sprintProb) {
        if (visuallySwimming && inWater) return 0.0;
        if (visuallySwimming) return swimCrawlProb;
        if (sprinting) return sprintProb;
        if (crouching) return sneakProb;
        return walkProb;
    }

    @SubscribeEvent
    public void onPlayerLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            STATES.invalidate(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        STATES.invalidateAll();
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer serverPlayer)) return;
        if (SoundAttractConfig.COMMON == null) return;
        if (!SoundAttractConfig.COMMON.enableFloorCreek.get()) return;
        if (!serverPlayer.isAlive() || serverPlayer.isSpectator()) return;

        UUID id = serverPlayer.getUUID();
        State state = STATES.asMap().computeIfAbsent(id, k -> new State(serverPlayer.position(), serverPlayer.onGround()));

        Vec3 curr = serverPlayer.position();
        boolean onGroundNow = serverPlayer.onGround();
        double dx = Math.hypot(curr.x - state.lastPos.x, curr.z - state.lastPos.z);

        ServerLevel level = serverPlayer.serverLevel();
        boolean onWoodNow = isOnWood(level, serverPlayer);

        double jumpProb = SoundAttractConfig.COMMON.floorCreekProbJump.get();
        if (state.wasOnGround && !onGroundNow && state.wasOnWood) {
            roll(serverPlayer, level, jumpProb);
        }
        if (!state.wasOnGround && onGroundNow && onWoodNow) {
            roll(serverPlayer, level, jumpProb);
        }

        if (onGroundNow && onWoodNow) {
            state.distanceAccumulator += dx;
            double step = SoundAttractConfig.COMMON.floorCreekDistanceStep.get();
            if (step <= 0.0) step = 2.0;
            int safetyBudget = 8;
            while (state.distanceAccumulator >= step && safetyBudget-- > 0) {
                state.distanceAccumulator -= step;
                double p = poseProbability(
                        serverPlayer.isVisuallySwimming(),
                        serverPlayer.isInWater(),
                        serverPlayer.isCrouching(),
                        serverPlayer.isSprinting(),
                        SoundAttractConfig.COMMON.floorCreekProbSwimmingCrawling.get(),
                        SoundAttractConfig.COMMON.floorCreekProbSneaking.get(),
                        SoundAttractConfig.COMMON.floorCreekProbWalking.get(),
                        SoundAttractConfig.COMMON.floorCreekProbSprinting.get());
                roll(serverPlayer, level, p);
            }
        } else {
            state.distanceAccumulator = 0.0;
        }

        state.lastPos = curr;
        state.wasOnGround = onGroundNow;
        state.wasOnWood = onWoodNow;
    }

    private static boolean isOnWood(ServerLevel level, Player player) {
        if (!player.onGround()) return false;
        BlockPos feet = BlockPos.containing(player.getX(), player.getY() - 0.05, player.getZ());
        BlockState state = level.getBlockState(feet);
        return state.is(FLOOR_CREEK_BLOCKS);
    }

    private static void roll(ServerPlayer player, ServerLevel level, double probability) {
        if (probability <= 0.0) return;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (rng.nextDouble() >= probability) return;
        float pitch = 0.9f + rng.nextFloat() * 0.2f;
        level.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                ModSounds.WOODEN_FLOOR_CREEK.get(),
                SoundSource.BLOCKS,
                0.3f,
                pitch);
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.FLOOR_CREEKS)) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.FLOOR_CREEKS)) {
                SoundAttractMod.LOGGER.debug("[FloorCreek] {} creak at {} (p={})", player.getName().getString(), player.blockPosition(), probability);
            }
        }
    }

    private static final class State {
        Vec3 lastPos;
        boolean wasOnGround;
        boolean wasOnWood;
        double distanceAccumulator;

        State(Vec3 lastPos, boolean wasOnGround) {
            this.lastPos = lastPos;
            this.wasOnGround = wasOnGround;
            this.wasOnWood = false;
            this.distanceAccumulator = 0.0;
        }
    }
}