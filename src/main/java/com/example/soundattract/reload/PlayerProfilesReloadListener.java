package com.example.soundattract.reload;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.PlayerProfile2;
import com.example.soundattract.config.SoundAttractConfig;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PlayerProfilesReloadListener extends IdentifiedJsonReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    public PlayerProfilesReloadListener() {
        super("player_profiles", "player_profiles", true);
    }

    @Override
    protected void applyElements(Map<ResourceLocation, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, Soundattract.getServer().registryAccess());
        List<PlayerProfile2> profiles = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            ResourceLocation location = entry.getKey();
            JsonElement element = entry.getValue();




            
            PlayerProfile2.CODEC.parse(ops, element).resultOrPartial(error -> {
                LOGGER.error("Failed to parse player profile '{}': {}", location, error);
            }).ifPresent(profile -> {
                profiles.add(profile.withId(location.toString()));
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    LOGGER.info("Loaded player profile: {}", location);
                }
            });
        }

        SoundAttractConfig.DP_PLAYER_PROFILES_CACHE = profiles;

        try {
            SoundAttractConfig.bakeConfig();
        } catch (Throwable t) {
            LOGGER.warn("[PlayerProfilesReloadListener] Failed to re-bake config after datapack reload", t);
        }
    }
}
