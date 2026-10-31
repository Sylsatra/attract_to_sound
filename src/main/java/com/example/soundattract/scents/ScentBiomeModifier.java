package com.example.soundattract.scents;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.quantified.QuantifiedCacheCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public class ScentBiomeModifier {

    public static float getDurationModifier(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) return 1.0f;

        float weatherMod = 1.0f;
        if (level.isRainingAt(pos)) {
            weatherMod /= (float) SoundAttractConfig.COMMON.rainDecayMultiplier.get().doubleValue(); 
        }

        Biome biome = level.getBiome(pos).value();
        




        
        float biomeMod = 1.0f;
        float temp = biome.getBaseTemperature();
        float humidity = biome.getModifiedClimateSettings().downfall();

        double hotThresh = SoundAttractConfig.COMMON.tempHeavyDecayThreshold.get();
        double coldThresh = SoundAttractConfig.COMMON.tempFreezeThreshold.get();
        double humidThresh = SoundAttractConfig.COMMON.humidityPreserveThreshold.get();

        if (temp > hotThresh) {
            biomeMod *= 0.5f; 
        } else if (temp < coldThresh) {
            biomeMod *= 1.5f;
        }

        if (humidity > humidThresh) {
            biomeMod *= 1.3f;
        } else if (humidity < 0.2f && temp > 0.5f) { 
             biomeMod *= 0.8f;
        }

        return weatherMod * biomeMod;
    }
}
