package com.example.soundattract.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class AwarenessKeybinds {
    private static KeyMapping toggleHud;
    private static boolean registered;

    public static void register() {
        if (registered) {
            return;
        }
        toggleHud = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.soundattract.toggle_awareness_hud",
                InputConstants.UNKNOWN.getValue(),
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("soundattract", "awareness_hud"))));
        registered = true;
    }

    public static boolean consumeToggleHudClick() {
        return toggleHud != null && toggleHud.consumeClick();
    }

    private AwarenessKeybinds() {
    }
}
