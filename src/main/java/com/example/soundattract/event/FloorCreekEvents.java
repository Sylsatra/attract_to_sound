package com.example.soundattract.event;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.registration.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class FloorCreekEvents {

    public static final TagKey<Block> FLOOR_CREEK_BLOCKS =
            TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                    Identifier.fromNamespaceAndPath("soundattract", "floor_creek_blocks"));

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

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
    public void onPlayerLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            STATES.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        STATES.clear();
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        if (SoundAttractConfig.COMMON == null) return;
        if (!SoundAttractConfig.COMMON.enableFloorCreek.get()) return;
        if (!serverPlayer.isAlive() || serverPlayer.isSpectator()) return;

        UUID id = serverPlayer.getUUID();
        State state = STATES.computeIfAbsent(id, k -> new State(serverPlayer.position(), serverPlayer.onGround()));

        Vec3 curr = serverPlayer.position();
        boolean onGroundNow = serverPlayer.onGround();
        double dx = Math.hypot(curr.x - state.lastPos.x, curr.z - state.lastPos.z);

        ServerLevel level = (ServerLevel) serverPlayer.level();
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
        if (SoundAttractConfig.COMMON.debugLogging != null && SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.debug("[FloorCreek] {} creak at {} (p={})", player.getName().getString(), player.blockPosition(), probability);
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
