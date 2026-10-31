package com.example.soundattract.camo;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = SoundAttractMod.MOD_ID, value = Dist.CLIENT)
public final class CamoClientEvents {
    private CamoClientEvents() {}

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            AvatarRenderer<?> avatar = event.getPlayerRenderer(skin);
            if (avatar != null) avatar.addLayer(new CamoRenderLayer(avatar));
        }
    }
}
