package com.example.soundattract.integration.scent;

import com.example.soundattract.scents.ScentManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

public class ScentCapabilityHandler {

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ScentManager manager = ScentManager.getForLevel(server.overworld());
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            for (Level level : server.getAllLevels()) {
                ScentManager.removeForLevel(level);
            }
        });
    }
}

