package com.example.soundattract.component;

import com.example.soundattract.Soundattract;
import com.example.soundattract.camo.CamouflageCapability;
import com.example.soundattract.component.LivingBridgeEntityData;
import com.example.soundattract.scents.ScentManager;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import net.minecraft.resources.ResourceLocation;

public final class ModComponents {

    public static final ComponentKey<CamouflageCapability> CAMOUFLAGE =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Soundattract.MOD_ID, "camouflage"),
            CamouflageCapability.class
        );

    public static final ComponentKey<ScentManager> SCENT =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Soundattract.MOD_ID, "scent"),
            ScentManager.class
        );

    public static final ComponentKey<LivingBridgeEntityData> LIVING_BRIDGE_DATA =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Soundattract.MOD_ID, "living_bridge_data"),
            LivingBridgeEntityData.class
        );

    private ModComponents() {}
}
