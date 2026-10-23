package com.example.soundattract.component;

import com.example.soundattract.scents.ScentManager;
import org.ladysnake.cca.api.v3.world.WorldComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.world.WorldComponentInitializer;

public final class WorldComponents implements WorldComponentInitializer {

    @Override
    public void registerWorldComponentFactories(WorldComponentFactoryRegistry registry) {
        registry.register(ModComponents.SCENT, ScentManager::new);
    }
}
