package com.example.soundattract.client;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.network.SoundAttractClientNetworking;
import com.example.soundattract.network.SoundMessage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public class AttractionClientEvents {

    public static void register() {
    }

    public static void onPlaySound(SoundInstance soundInstance) {
        if (!SoundAttractConfig.serverReady()) return;
        if (!SoundAttractConfig.SERVER.enablePlayerActionSounds.get()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (soundInstance == null || soundInstance.getIdentifier() == null) return;

        Identifier soundLoc = soundInstance.getIdentifier();
        String path = soundLoc.getPath();

        boolean isStepSound = path.endsWith(".step") || path.endsWith(".swim") || path.contains(".step.");
        if (!isStepSound) return;

        double soundX = soundInstance.getX();
        double soundY = soundInstance.getY();
        double soundZ = soundInstance.getZ();

        if (player.position().distanceToSqr(soundX, soundY, soundZ) > 2.25) {
            return;
        }

        double checkRadius = SoundAttractConfig.SERVER.playerActionCheckRadius.get();
        net.minecraft.world.phys.AABB checkArea = player.getBoundingBox().inflate(checkRadius);
        java.util.List<net.minecraft.world.entity.Mob> nearbyMobs = player.level()
                .getEntitiesOfClass(net.minecraft.world.entity.Mob.class, checkArea, m -> m.isAlive());

        if (nearbyMobs.isEmpty()) {
            return;
        }

        String action;

        if (player.isVisuallySwimming()) {
            action = "CRAWLING";
        } else if (player.isCrouching()) {
            action = "SNEAKING";
        } else if (player.isSprinting()) {
            if (!player.onGround() && player.getDeltaMovement().y != 0) {
                action = "SPRINT_JUMPING";
            } else {
                action = "SPRINTING";
            }
        } else {
            double speedSq = player.getDeltaMovement().horizontalDistanceSqr();
            if (speedSq < 0.00001) return;
            action = "WALKING";
        }

        Identifier virtualSoundId = Identifier.fromNamespaceAndPath("soundattract", "player_action." + action.toLowerCase(Locale.ROOT));

        SoundMessage msg = new SoundMessage(
                virtualSoundId,
                soundX, soundY, soundZ,
                player.level().dimension().identifier(),
                Optional.of(player.getUUID()),
                action
        );
        SoundAttractClientNetworking.sendSoundToServer(msg);
    }
}
