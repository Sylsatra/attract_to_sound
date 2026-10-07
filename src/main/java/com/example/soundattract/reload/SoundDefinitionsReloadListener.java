package com.example.soundattract.reload;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.config.SoundAttractConfig.SoundDefaultEntry;
import com.example.soundattract.event.SoundAttractionEvents;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SoundDefinitionsReloadListener extends SimpleJsonResourceReloadListener {

    public SoundDefinitionsReloadListener() {
        super(new Gson(), "sounds");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> elements, ResourceManager resourceManager, ProfilerFiller profiler) {
        Set<ResourceLocation> whitelist = new HashSet<>();
        Map<ResourceLocation, SoundDefaultEntry> defaults = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : elements.entrySet()) {
            JsonElement element = entry.getValue();
            if (!element.isJsonObject()) continue;
            JsonObject obj = element.getAsJsonObject();

            if (obj.has("replace_whitelist") && obj.get("replace_whitelist").isJsonPrimitive() && obj.get("replace_whitelist").getAsJsonPrimitive().isBoolean()) {
                if (obj.get("replace_whitelist").getAsBoolean()) {
                    whitelist.clear();
                }
            }
            if (obj.has("replace_defaults") && obj.get("replace_defaults").isJsonPrimitive() && obj.get("replace_defaults").getAsJsonPrimitive().isBoolean()) {
                if (obj.get("replace_defaults").getAsBoolean()) {
                    defaults.clear();
                }
            }

            if (obj.has("whitelist") && obj.get("whitelist").isJsonArray()) {
                for (JsonElement e : obj.get("whitelist").getAsJsonArray()) {
                    if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) continue;
                    String idOrTag = e.getAsString().trim();
                    if (idOrTag.isEmpty()) continue;
                    if (idOrTag.startsWith("#")) {

                        String tagIdStr = idOrTag.substring(1);
                        ResourceLocation tagId = ResourceLocation.tryParse(tagIdStr);
                        if (tagId != null) {
                            expandSoundTag(tagId, whitelist);
                        }
                    } else {
                        ResourceLocation soundId = ResourceLocation.tryParse(idOrTag);
                        if (soundId != null) {
                            whitelist.add(soundId);
                        }
                    }
                }
            }

            if (obj.has("defaults") && obj.get("defaults").isJsonObject()) {
                JsonObject defaultsObj = obj.getAsJsonObject("defaults");
                for (Map.Entry<String, JsonElement> defEntry : defaultsObj.entrySet()) {
                    String key = defEntry.getKey().trim();
                    JsonElement valueEl = defEntry.getValue();
                    if (!valueEl.isJsonObject()) continue;
                    JsonObject defObj = valueEl.getAsJsonObject();
                    if (!defObj.has("range") || !defObj.has("weight")) continue;
                    try {
                        double range = defObj.get("range").getAsDouble();
                        double weight = defObj.get("weight").getAsDouble();
                        if (key.startsWith("#")) {
                            String tagIdStr = key.substring(1);
                            ResourceLocation tagId = ResourceLocation.tryParse(tagIdStr);
                            if (tagId != null) {
                                expandSoundTagWithDefault(tagId, range, weight, defaults);
                            }
                        } else {
                            ResourceLocation soundId = ResourceLocation.tryParse(key);
                            if (soundId != null) {
                                defaults.put(soundId, new SoundDefaultEntry(range, weight));
                            }
                        }
                    } catch (Exception ex) {

                    }
                }
            }
        }

        SoundAttractConfig.DP_SOUND_WHITELIST_CACHE.clear();
        SoundAttractConfig.DP_SOUND_WHITELIST_CACHE.addAll(whitelist);

        SoundAttractConfig.DP_SOUND_DEFAULTS_CACHE.clear();
        SoundAttractConfig.DP_SOUND_DEFAULTS_CACHE.putAll(defaults);

        try {
            SoundAttractConfig.bakeConfig();
            SoundAttractionEvents.invalidateCachedEntityTypes();
        } catch (Throwable t) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SOUND_TRACKING)) {
                SoundAttractMod.LOGGER.warn("[SoundDefinitionsReloadListener] Failed to re-bake config after datapack reload", t);
            }
        }
    }

    private void expandSoundTag(ResourceLocation tagId, Set<ResourceLocation> whitelist) {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        HolderLookup<SoundEvent> lookup = server.registryAccess().lookupOrThrow(Registries.SOUND_EVENT);
        net.minecraft.tags.TagKey<SoundEvent> tagKey = net.minecraft.tags.TagKey.create(Registries.SOUND_EVENT, tagId);
        for (Holder<SoundEvent> holder : lookup.getOrThrow(tagKey)) {
            holder.unwrapKey().ifPresent(k -> whitelist.add(k.location()));
        }
    }

    private void expandSoundTagWithDefault(ResourceLocation tagId, double range, double weight, Map<ResourceLocation, SoundDefaultEntry> defaults) {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        HolderLookup<SoundEvent> lookup = server.registryAccess().lookupOrThrow(Registries.SOUND_EVENT);
        net.minecraft.tags.TagKey<SoundEvent> tagKey = net.minecraft.tags.TagKey.create(Registries.SOUND_EVENT, tagId);
        for (Holder<SoundEvent> holder : lookup.getOrThrow(tagKey)) {
            holder.unwrapKey().ifPresent(k -> defaults.put(k.location(), new SoundDefaultEntry(range, weight)));
        }
    }
}