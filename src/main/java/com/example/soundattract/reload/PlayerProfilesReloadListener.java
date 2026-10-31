package com.example.soundattract.reload;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.PlayerProfile2;
import com.example.soundattract.config.SoundAttractConfig;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PlayerProfilesReloadListener extends IdentifierJsonReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final RegistryOps<JsonElement> ops;

    public PlayerProfilesReloadListener(RegistryAccess registryAccess) {

        super("player_profiles");
        this.ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess);
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<PlayerProfile2> profiles = new ArrayList<>();

        for (Map.Entry<Identifier, JsonElement> entry : objects.entrySet()) {
            Identifier location = entry.getKey();
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
