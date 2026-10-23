package com.example.soundattract.event.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.network.SoundMessage;
import com.example.soundattract.network.SoundAttractNetwork;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;

@Environment(EnvType.CLIENT)
public class SoundAttractClientEvents {

    @Environment(EnvType.CLIENT)
    public static void onPlaySound(AbstractSoundInstance soundInstance) {
        if (soundInstance == null) {
            return;
        }

        Level clientWorld = Minecraft.getInstance().level;
        net.minecraft.client.player.LocalPlayer clientPlayer = Minecraft.getInstance().player;
        if (clientWorld == null || clientPlayer == null) {
            return;
        }

        ResourceLocation soundRL = soundInstance.getLocation();
        if (soundRL == null || soundRL.equals(SoundMessage.VOICE_CHAT_SOUND_ID)) {
            return;
        }

        if (SoundAttractConfig.POINT_BLANK_ENABLED_CACHE && "pointblank".equals(soundRL.getNamespace())) {
            return;
        }

        SoundEvent se = BuiltInRegistries.SOUND_EVENT.get(soundRL);
        if (se == null) {
            return;
        }

        double x = soundInstance.getX();
        double y = soundInstance.getY();
        double z = soundInstance.getZ();
        ResourceLocation dim = clientWorld.dimension().location();
        Optional<UUID> sourcePlayerUUID = Optional.empty();
        String actionName = null;

        boolean isStep = se != null && se.getLocation().getPath().contains("step")
                && clientPlayer.position().distanceToSqr(x, y, z) < 1.5 * 1.5;

        if (isStep) {
            sourcePlayerUUID = Optional.of(clientPlayer.getUUID());
            Vec3 motion = clientPlayer.getDeltaMovement();
            double horizontalSpeedSq = motion.x * motion.x + motion.z * motion.z;
            boolean isOnGround = clientPlayer.onGround();
            boolean isSneaking = clientPlayer.isShiftKeyDown();
            boolean isSprinting = clientPlayer.isSprinting();

            PlayerAction currentAction = PlayerAction.IDLE;

            if (isSneaking) {
                if (horizontalSpeedSq > 0.001 * 0.001 && horizontalSpeedSq <= 0.03 * 0.03) {
                    currentAction = PlayerAction.CRAWLING;
                } else if (horizontalSpeedSq > 0.03 * 0.03 && horizontalSpeedSq <= 0.066 * 0.066 * 1.1) {
                    currentAction = PlayerAction.SNEAKING;
                }
            } else {
                if (isSprinting && !isOnGround) {
                    currentAction = PlayerAction.SPRINT_JUMPING;
                } else if (isSprinting && horizontalSpeedSq > 0.216 * 0.216) {
                    currentAction = PlayerAction.SPRINTING;
                } else if (isOnGround && horizontalSpeedSq > 0.001 * 0.001 && horizontalSpeedSq <= 0.216 * 0.216) {
                    currentAction = PlayerAction.WALKING;
                }
            }

            if (currentAction == PlayerAction.IDLE) return;
            actionName = currentAction.name();
        }

        SoundMessage msg = new SoundMessage(soundRL, x, y, z, dim, sourcePlayerUUID, actionName);
        SoundAttractNetwork.sendSoundToServer(msg);
    }

    public enum PlayerAction {
        IDLE,
        CRAWLING,
        SNEAKING,
        WALKING,
        SPRINTING,
        SPRINT_JUMPING
    }
}
