package com.example.soundattract;

import com.example.soundattract.camo.CamoClientEvents;
import com.example.soundattract.client.AttractionClientEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import com.example.soundattract.client.particle.WindScentWispParticle;
import com.example.soundattract.registration.ModParticles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SoundAttractClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(Soundattract.MOD_ID + "-client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Sound Attract client...");

        ParticleFactoryRegistry.getInstance().register(ModParticles.WIND_SCENT_WISP, WindScentWispParticle.Provider::new);

        com.example.soundattract.camo.CamoClientEvents.register();
        com.example.soundattract.client.AwarenessClientEvents.register();
        com.example.soundattract.network.AwarenessClientNetworking.register();
        AttractionClientEvents.register();
        com.example.soundattract.event.SoundAttractFabricClientEvents.register();

        LOGGER.info("Sound Attract client initialized successfully!");
    }
}
