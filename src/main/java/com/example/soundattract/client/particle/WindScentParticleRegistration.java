package com.example.soundattract.client.particle;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.registration.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WindScentParticleRegistration {
    private WindScentParticleRegistration() {
    }

    @SubscribeEvent
    public static void registerProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.WIND_SCENT_WISP.get(), WindScentWispParticle.Provider::new);
    }
}
