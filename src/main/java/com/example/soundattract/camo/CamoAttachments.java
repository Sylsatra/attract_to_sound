package com.example.soundattract.camo;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class CamoAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SoundAttractMod.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CamouflageCapability>> CAMOUFLAGE =
            ATTACHMENT_TYPES.register("camouflage", () -> AttachmentType.builder(CamouflageCapability::new).build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
