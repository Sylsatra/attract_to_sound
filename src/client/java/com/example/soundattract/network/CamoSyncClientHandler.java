package com.example.soundattract.network;

import com.example.soundattract.camo.CamouflageCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class CamoSyncClientHandler {
    private CamoSyncClientHandler() {
    }

    public static void apply(CamoSyncMessage message, Minecraft client) {
        if (client.level == null) return;
        Entity entity = client.level.getEntity(message.entityId());
        if (entity instanceof LivingEntity living) {
            CamouflageCapability.getCapability(living).ifPresent(capability -> capability.setLayers(message.layers()));
        }
    }
}
