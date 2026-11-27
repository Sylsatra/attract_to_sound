package com.example.soundattract.event;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.DebugLoggingConfig;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.registration.ModSounds;
import com.example.soundattract.tracking.SoundTracker;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class FloorCreekEvents {
    private static final long CREEK_COOLDOWN_TICKS = 30L;
    private static final double CREEK_RANGE = 12.0;
    private static final double CREEK_WEIGHT = 0.35;

    public static final TagKey<Block> FLOOR_CREEK_BLOCKS =
            TagKey.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "floor_creek_blocks"));

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
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.invalidate(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        STATES.invalidate(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        STATES.invalidateAll();
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        if (SoundAttractConfig.COMMON == null) return;
        if (!SoundAttractConfig.COMMON.enableFloorCreek.get()) return;
        if (!serverPlayer.isAlive() || serverPlayer.isSpectator()) return;

        ServerLevel level = serverPlayer.serverLevel();
        ResourceLocation dimension = level.dimension().location();
        Vec3 curr = serverPlayer.position();
        boolean onGroundNow = serverPlayer.onGround();
        State state = STATES.asMap().compute(serverPlayer.getUUID(),
                (key, old) -> old == null || !old.dimension.equals(dimension)
                        ? new State(curr, onGroundNow, dimension) : old);

        double dx = curr.x - state.lastPos.x;
        double dy = curr.y - state.lastPos.y;
        double dz = curr.z - state.lastPos.z;
        double horizontalDistanceSqr = dx * dx + dz * dz;
        long gameTime = level.getGameTime();
        boolean onWoodNow = onGroundNow && !serverPlayer.isInWater()
                && !serverPlayer.isPassenger() && !serverPlayer.isSwimming()
                && horizontalDistanceSqr < 4.0 && Math.abs(dy) < 1.5 && isOnWood(level, serverPlayer);

        if (!onGroundNow) state.fallDistance = Math.max(state.fallDistance, serverPlayer.fallDistance);

        if (!state.wasOnGround && onWoodNow && state.fallDistance >= 2.0f) {
            roll(serverPlayer, level, state, gameTime, SoundAttractConfig.COMMON.floorCreekProbJump.get());
        }
        if (onGroundNow) state.fallDistance = 0.0f;

        if (onWoodNow) {
            state.distanceAccumulator += Math.sqrt(horizontalDistanceSqr);
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
                roll(serverPlayer, level, state, gameTime, p);
            }
        } else {
            state.distanceAccumulator = 0.0;
        }

        state.lastPos = curr;
        state.wasOnGround = onGroundNow;
    }

    private static boolean isOnWood(ServerLevel level, Player player) {
        BlockPos feet = BlockPos.containing(player.getX(), player.getY() - 0.05, player.getZ());
        BlockState state = level.getBlockState(feet);
        return state.is(FLOOR_CREEK_BLOCKS);
    }

    private static void roll(ServerPlayer player, ServerLevel level, State state, long gameTime, double probability) {
        if (state.lastCandidateTick == gameTime) return;
        state.lastCandidateTick = gameTime;
        if (probability <= 0.0) return;
        if (gameTime - state.lastCreakTick < CREEK_COOLDOWN_TICKS) return;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (rng.nextDouble() >= probability) return;
        state.lastCreakTick = gameTime;
        float pitch = 0.9f + rng.nextFloat() * 0.2f;
        level.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                ModSounds.WOODEN_FLOOR_CREEK.get(),
                SoundSource.BLOCKS,
                0.16f,
                pitch);
        SoundTracker.addSound(ModSounds.WOODEN_FLOOR_CREEK.get(), player.blockPosition(),
                level.dimension().location().toString(), CREEK_RANGE, CREEK_WEIGHT,
                SoundAttractConfig.COMMON.soundLifetimeTicks.get());
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.FLOOR_CREEKS)) {
            SoundAttractMod.LOGGER.debug("[FloorCreek] {} creak at {} (p={})", player.getName().getString(), player.blockPosition(), probability);
        }
    }

    private static final class State {
        Vec3 lastPos;
        final ResourceLocation dimension;
        boolean wasOnGround;
        double distanceAccumulator;
        long lastCreakTick = Long.MIN_VALUE / 2;
        long lastCandidateTick = Long.MIN_VALUE;
        float fallDistance;

        State(Vec3 lastPos, boolean wasOnGround, ResourceLocation dimension) {
            this.lastPos = lastPos;
            this.dimension = dimension;
            this.wasOnGround = wasOnGround;
        }
    }
}
