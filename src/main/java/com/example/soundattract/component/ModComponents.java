package com.example.soundattract.component;

import com.example.soundattract.Soundattract;
import com.example.soundattract.camo.CamouflageCapability;
import com.example.soundattract.component.LivingBridgeEntityData;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import net.minecraft.resources.Identifier;

public final class ModComponents {

    public static final ComponentKey<CamouflageCapability> CAMOUFLAGE =
        ComponentRegistry.getOrCreate(
            Identifier.fromNamespaceAndPath(Soundattract.MOD_ID, "camouflage"),
            CamouflageCapability.class
        );

    public static final ComponentKey<LivingBridgeEntityData> LIVING_BRIDGE_DATA =
        ComponentRegistry.getOrCreate(
            Identifier.fromNamespaceAndPath(Soundattract.MOD_ID, "living_bridge_data"),
            LivingBridgeEntityData.class
        );

    private ModComponents() {}
}
