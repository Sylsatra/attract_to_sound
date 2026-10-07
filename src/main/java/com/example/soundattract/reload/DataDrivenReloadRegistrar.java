package com.example.soundattract.reload;

import com.example.soundattract.SoundAttractMod;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DataDrivenReloadRegistrar {

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SoundDefinitionsReloadListener());
        event.addListener(new ArmorColorsReloadListener());
        event.addListener(new MobProfilesReloadListener(event.getRegistryAccess()));
        event.addListener(new PlayerProfilesReloadListener(event.getRegistryAccess()));
        event.addListener(new TaczGunsReloadListener());
        event.addListener(new PointBlankGunsReloadListener());
    }
}