package com.example.soundattract.integration.tacz;

import net.neoforged.neoforge.common.NeoForge;

public class TaczIntegrationHandler {

    public static void register() {
        NeoForge.EVENT_BUS.register(TaczIntegration.class);
    }
}
