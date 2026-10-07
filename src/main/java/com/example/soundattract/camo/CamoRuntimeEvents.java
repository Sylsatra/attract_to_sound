package com.example.soundattract.camo;

import com.example.soundattract.SoundAttractMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, value = Dist.CLIENT)
public class CamoRuntimeEvents {

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        ArmorValidationTracker.clearForEntity(event.getEntity());
    }

    @SubscribeEvent
    public static void onClientLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            CamoTextureGenerator.clearCaches();
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CamoTextureGenerator.clearCaches();
    }
}