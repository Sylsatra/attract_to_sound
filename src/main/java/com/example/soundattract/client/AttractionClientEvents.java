package com.example.soundattract.client;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.network.SoundMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

@EventBusSubscriber(modid = "soundattract", value = Dist.CLIENT)
public class AttractionClientEvents {

    private static final boolean FEAR_OF_SOUND_LOADED = ModList.get().isLoaded("fear_of_sound");
    private static int lastStepTick = 0;

    @SubscribeEvent
    public static void onPlaySoundEvent(PlaySoundEvent event) {
        if (FEAR_OF_SOUND_LOADED) return;
        if (!SoundAttractConfig.serverReady()) return;
        if (!SoundAttractConfig.SERVER.enablePlayerActionSounds.get()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (event.getSound() == null || event.getSound().getIdentifier() == null) return;

        Identifier soundLoc = event.getSound().getIdentifier();
        String path = soundLoc.getPath();

        boolean isStepSound = path.endsWith(".step") || path.endsWith(".swim") || path.contains(".step."); 
        
        if (!isStepSound) return;

        double soundX = event.getSound().getX();
        double soundY = event.getSound().getY();
        double soundZ = event.getSound().getZ();

        if (player.position().distanceToSqr(soundX, soundY, soundZ) > 2.25) { 
            return;
        }

        double checkRadius = SoundAttractConfig.SERVER.playerActionCheckRadius.get();
        net.minecraft.world.phys.AABB checkArea = player.getBoundingBox().inflate(checkRadius);
        java.util.List<net.minecraft.world.entity.Mob> nearbyMobs = player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, checkArea, m -> m.isAlive());
        
        if (nearbyMobs.isEmpty()) {
            return;
        }

        String action = "WALKING";
        
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
        Identifier virtualSoundId = Identifier.fromNamespaceAndPath("soundattract", "player_action." + action.toLowerCase(java.util.Locale.ROOT));

        ClientPacketDistributor.sendToServer(new SoundMessage(
                virtualSoundId,
                soundX, soundY, soundZ,
                player.level().dimension().identifier(),
                java.util.Optional.of(player.getUUID()),
                action
        ));
    }
}
