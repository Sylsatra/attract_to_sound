package com.example.soundattract.integration.vanilla;

import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.runtime.DynamicScanCooldownManager;
import com.example.soundattract.tracking.SoundTracker;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public class VanillaIntegrationEvents {
    private static boolean wasOnGround = true;
    private static int tickCounter = 0;
    private static boolean didLogInit = false;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;

        if (!didLogInit && SoundAttractConfig.COMMON.debugLogging.get()) {
            didLogInit = true;
            SoundAttractMod.LOGGER.info("[VanillaIntegration] PlayerTick handler active on server");
        }

        tickCounter++;
        int cooldown = Math.max(1, DynamicScanCooldownManager.currentScanCooldownTicks);
        if (tickCounter % cooldown != 0) return;
        Player player = event.getEntity();
        Identifier dim = player.level().dimension().identifier();
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

    private static void sendVanillaSound(String animatorClass, String soundId, double x, double y, double z, Identifier dim, Optional<java.util.UUID> uuid, int range, double weight, double volume, double pitch) {
        net.minecraft.resources.Identifier baseId = Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "virtual");
        if (!SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty()
            && (baseId == null || !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(baseId))) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                    "[VanillaIntegration] Skipping virtual sound because {} is not in whitelist (whitelistSize={})",
                    baseId,
                    SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.size()
                );
            }
            return;
        }
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(x, y, z);
        String dimString = dim.toString();
        int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
        String meta = (uuid != null && uuid.isPresent() ? uuid.get().toString() : "unknown")
            + "/" + (animatorClass != null && !animatorClass.isEmpty() ? animatorClass : "unknown");
        String soundIdToUse = SoundTracker.buildIntegrationSoundId(baseId, meta);
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[VanillaIntegration] addSound: soundId={} | range={} | weight={} | pos=({}, {}, {}) | dim={}", soundIdToUse, range, weight, x, y, z, dim);
        }
        SoundTracker.addSound(null, pos, dimString, range, weight, lifetime, soundIdToUse);
    }
}
