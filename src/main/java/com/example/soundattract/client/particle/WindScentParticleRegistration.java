package com.example.soundattract.client.particle;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.registration.ModParticles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = SoundAttractMod.MOD_ID, value = Dist.CLIENT)
public final class WindScentParticleRegistration {
    private WindScentParticleRegistration() {
    }

    @SubscribeEvent
    public static void registerProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.WIND_SCENT_WISP.get(), WindScentWispParticle.Provider::new);
    }
}
