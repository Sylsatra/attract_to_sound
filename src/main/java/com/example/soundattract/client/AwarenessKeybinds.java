package com.example.soundattract.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

public final class AwarenessKeybinds {
    private static KeyMapping toggleHud;
    private static boolean registered;

    public static void register() {
        if (registered) {
            return;
        }
        toggleHud = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.soundattract.toggle_awareness_hud",
                InputConstants.UNKNOWN.getValue(),
                "key.categories.soundattract"));
        registered = true;
    }

    public static boolean consumeToggleHudClick() {
        return toggleHud != null && toggleHud.consumeClick();
    }

    private AwarenessKeybinds() {
    }
}
