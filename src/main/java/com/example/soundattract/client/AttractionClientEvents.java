package com.example.soundattract.client;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.network.SoundAttractNetwork;
import com.example.soundattract.network.SoundMessage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public class AttractionClientEvents {

    private static final boolean FEAR_OF_SOUND_LOADED = FabricLoader.getInstance().isModLoaded("fear_of_sound");

    public static void register() {
    }

    public static void onPlaySound(SoundInstance soundInstance) {
        if (FEAR_OF_SOUND_LOADED) return;
        if (!SoundAttractConfig.serverReady()) return;
        if (!SoundAttractConfig.SERVER.enablePlayerActionSounds.get()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (soundInstance == null || soundInstance.getLocation() == null) return;

        ResourceLocation soundLoc = soundInstance.getLocation();
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

        ResourceLocation virtualSoundId = ResourceLocation.fromNamespaceAndPath("soundattract", "player_action." + action.toLowerCase(Locale.ROOT));

        SoundMessage msg = new SoundMessage(
                virtualSoundId,
                soundX, soundY, soundZ,
                player.level().dimension().location(),
                Optional.of(player.getUUID()),
                action
        );
        SoundAttractNetwork.sendSoundToServer(msg);
    }
}
