package com.example.soundattract.reload;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.MobProfile2;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.config.SoundOverride;
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

public class MobProfilesReloadListener extends IdentifierJsonReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final RegistryOps<JsonElement> ops;

    public MobProfilesReloadListener(RegistryAccess registryAccess) {
        super("mob_profiles");
        this.ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess);
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> elements, ResourceManager resourceManager, ProfilerFiller profiler) {
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
        } catch (Throwable t) {
            LOGGER.warn("[MobProfilesReloadListener] Failed to re-bake config after datapack reload", t);
        }
    }
}
