package com.example.soundattract.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;

@EventBusSubscriber(modid = "soundattract", value = Dist.CLIENT)
public final class AwarenessKeybinds {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("soundattract", "general"));
    public static final KeyMapping TOGGLE_HUD = new KeyMapping(
            "key.soundattract.toggle_awareness_hud", InputConstants.UNKNOWN.getValue(), CATEGORY);

    private AwarenessKeybinds() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_HUD);
    }
}