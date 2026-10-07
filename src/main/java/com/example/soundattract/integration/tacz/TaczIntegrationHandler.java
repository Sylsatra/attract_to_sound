package com.example.soundattract.integration.tacz;

import net.minecraftforge.common.MinecraftForge;

public class TaczIntegrationHandler {

    public static void register() {
        MinecraftForge.EVENT_BUS.register(TaczIntegration.class);
    }
}