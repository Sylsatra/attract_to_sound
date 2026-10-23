package com.example.soundattract.reload;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.MobProfile2;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.config.SoundOverride;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MobProfilesReloadListener extends IdentifiedJsonReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    public MobProfilesReloadListener() {
        super("mob_profiles", "mob_profiles", true);
    }

    @Override
    protected void applyElements(Map<Identifier, JsonElement> elements, ResourceManager resourceManager, ProfilerFiller profiler) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, Soundattract.getServer().registryAccess());
        List<MobProfile2> profiles = new ArrayList<>();

        for (Map.Entry<Identifier, JsonElement> entry : elements.entrySet()) {
            Identifier location = entry.getKey();
            JsonElement element = entry.getValue();





            MobProfile2.CODEC.parse(ops, element).resultOrPartial(error -> {
                LOGGER.error("Failed to parse mob profile '{}': {}", location, error);
            }).ifPresent(profile -> {
                profiles.add(profile.withId(location.toString()));
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    LOGGER.info("Loaded mob profile: {}", location);
                }
            });
        }

        SoundAttractConfig.DP_MOB_PROFILES_CACHE = profiles;

        try {
            SoundAttractConfig.bakeConfig();
            com.example.soundattract.event.SoundAttractionEvents.invalidateCachedEntityTypes();
        } catch (Throwable t) {
            LOGGER.warn("[MobProfilesReloadListener] Failed to re-bake config after datapack reload", t);
        }
    }
}
