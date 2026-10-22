package com.example.soundattract.integration.vanilla;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class VanillaIntegrationEvents {
    private static boolean wasOnGround = true;
    private static int tickCounter = 0;
    private static boolean didLogInit = false;

    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            server.getPlayerList().getPlayers().forEach(VanillaIntegrationEvents::onPlayerTick);
        });
    }

    public static void onPlayerTick(ServerPlayer player) {
        if (player.level().isClientSide) return;

        if (!didLogInit && com.example.soundattract.config.SoundAttractConfig.COMMON.debugLogging.get()) {
            didLogInit = true;
            com.example.soundattract.Soundattract.LOGGER.info("[VanillaIntegration] PlayerTick handler active on server");
        }

        tickCounter++;
        int cooldown = Math.max(1, com.example.soundattract.runtime.DynamicScanCooldownManager.currentScanCooldownTicks);
        if (tickCounter % cooldown != 0) return;
        ResourceLocation dim = player.level().dimension().location();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        Optional<java.util.UUID> uuid = Optional.of(player.getUUID());

        if (player.isSprinting()) {
            if (player instanceof ServerPlayer) {
                sendVanillaSound("VanillaSprint", "minecraft:entity.player.sprint", x, y, z, dim, uuid, 10, 1.2, 1.0, 1.0);
            }
        }
        if (!player.isSprinting() && !player.isCrouching() && player.getDeltaMovement().horizontalDistanceSqr() > 0.01 && player.onGround() && !player.getPose().name().equalsIgnoreCase("SWIMMING")) {
            if (player instanceof ServerPlayer) {
                sendVanillaSound("VanillaWalk", "minecraft:entity.player.walk", x, y, z, dim, uuid, 6, 0.6, 0.8, 1.0);
            }
        }

        boolean isJumping = player.fallDistance == 0 && player.getDeltaMovement().y > 0.1 && !wasOnGround;
        if (isJumping) {
            if (player instanceof ServerPlayer) {
                sendVanillaSound("VanillaJump", "minecraft:entity.player.jump", x, y, z, dim, uuid, 7, 0.7, 0.8, 1.0);
            }
        }
        wasOnGround = player.onGround();

        if (player.isCrouching()) {
            if (player instanceof ServerPlayer) {
                sendVanillaSound("VanillaSneak", "minecraft:entity.player.sneak", x, y, z, dim, uuid, 3, 0.2, 0.4, 1.0);
            }
        }
        boolean isCrawling = player.getPose() == net.minecraft.world.entity.Pose.SWIMMING && !player.isInWater();
        if (isCrawling) {
            if (player instanceof ServerPlayer) {
                sendVanillaSound("VanillaCrawl", "minecraft:block.wool.step", x, y, z, dim, uuid, 2, 0.1, 0.2, 1.0);
            }
        }
    }

    private static void sendVanillaSound(String animatorClass, String soundId, double x, double y, double z, ResourceLocation dim, Optional<java.util.UUID> uuid, int range, double weight, double volume, double pitch) {
        net.minecraft.resources.ResourceLocation baseId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.example.soundattract.Soundattract.MOD_ID, "virtual");
        if (!com.example.soundattract.config.SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty()
            && (baseId == null || !com.example.soundattract.config.SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(baseId))) {
            if (com.example.soundattract.config.SoundAttractConfig.COMMON.debugLogging.get()) {
                com.example.soundattract.Soundattract.LOGGER.info(
                    "[VanillaIntegration] Skipping virtual sound because {} is not in whitelist (whitelistSize={})",
                    baseId,
                    com.example.soundattract.config.SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.size()
                );
            }
            return;
        }
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(x, y, z);
        String dimString = dim.toString();
        int lifetime = com.example.soundattract.config.SoundAttractConfig.COMMON.soundLifetimeTicks.get();
        String meta = (uuid != null && uuid.isPresent() ? uuid.get().toString() : "unknown")
            + "/" + (animatorClass != null && !animatorClass.isEmpty() ? animatorClass : "unknown");
        String soundIdToUse = com.example.soundattract.tracking.SoundTracker.buildIntegrationSoundId(baseId, meta);
        if (com.example.soundattract.config.SoundAttractConfig.COMMON.debugLogging.get()) {
            com.example.soundattract.Soundattract.LOGGER.info("[VanillaIntegration] addSound: soundId={} | range={} | weight={} | pos=({}, {}, {}) | dim={}", soundIdToUse, range, weight, x, y, z, dim);
        }
        com.example.soundattract.tracking.SoundTracker.addSound(null, pos, dimString, range, weight, lifetime, soundIdToUse);
    }
}


