package com.example.soundattract.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;

@Mod.EventBusSubscriber(modid = "soundattract", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AwarenessKeybinds {
    public static final KeyMapping TOGGLE_HUD = new KeyMapping(
            "key.soundattract.toggle_awareness_hud",
            InputConstants.UNKNOWN.getValue(),
            "key.categories.soundattract");

    private AwarenessKeybinds() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_HUD);
    }
}