package com.example.soundattract.component;

import com.example.soundattract.camo.CamouflageCapability;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;
import net.minecraft.world.entity.LivingEntity;

public final class EntityComponents implements EntityComponentInitializer {

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.beginRegistration(LivingEntity.class, ModComponents.CAMOUFLAGE)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY)
                .end(entity -> new CamouflageCapability());
        registry.beginRegistration(net.minecraft.world.entity.Entity.class, ModComponents.LIVING_BRIDGE_DATA)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(entity -> new LivingBridgeEntityData());
    }
}
