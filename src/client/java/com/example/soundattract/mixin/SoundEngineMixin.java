package com.example.soundattract.mixin;

import com.example.soundattract.client.AttractionClientEvents;
import com.example.soundattract.event.client.SoundAttractClientEvents;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    @Inject(method = "play", at = @At("HEAD"))
    private void onPlay(SoundInstance soundInstance, CallbackInfoReturnable<?> cir) {
        // The general handler forwards registered vanilla sounds (bells, explosions,
        // doors, etc.). Player movement sounds are handled by the action-aware path below.
        AttractionClientEvents.onPlaySound(soundInstance);
        if (soundInstance instanceof AbstractSoundInstance abstractSoundInstance) {
            SoundAttractClientEvents.onPlaySound(abstractSoundInstance);
        }
    }
}
