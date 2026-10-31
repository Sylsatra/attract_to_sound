package com.example.soundattract.reload;

import com.example.soundattract.SoundAttractMod;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

@EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public class DataDrivenReloadRegistrar {

    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "sounds"), new SoundDefinitionsReloadListener());
        event.addListener(Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "armor_colors"), new ArmorColorsReloadListener());
        event.addListener(Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "mob_profiles"), new MobProfilesReloadListener(event.getRegistryAccess()));
        event.addListener(Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "player_profiles"), new PlayerProfilesReloadListener(event.getRegistryAccess()));

        event.addListener(Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "pointblank_guns"), new PointBlankGunsReloadListener());
    }
}
