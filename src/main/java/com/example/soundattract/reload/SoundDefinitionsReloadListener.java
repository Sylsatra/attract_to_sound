package com.example.soundattract.reload;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.config.SoundAttractConfig.SoundDefaultEntry;
import com.example.soundattract.event.SoundAttractionEvents;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
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

public class SoundDefinitionsReloadListener extends IdentifiedJsonReloadListener {

    public SoundDefinitionsReloadListener() {
        super("sounds", "sound_definitions", true);
    }

    @Override
    protected void applyElements(Map<Identifier, JsonElement> elements, ResourceManager resourceManager, ProfilerFiller profiler) {
        Set<Identifier> whitelist = new HashSet<>();
        Map<Identifier, SoundDefaultEntry> defaults = new HashMap<>();

        for (Map.Entry<Identifier, JsonElement> entry : elements.entrySet()) {
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
                    String idOrTag = e.toString().trim();
                    if (idOrTag.isEmpty()) continue;
                    if (idOrTag.startsWith("#")) {

                        String tagIdStr = idOrTag.substring(1);
                        Identifier tagId = Identifier.tryParse(tagIdStr);
                        if (tagId != null) {
                            expandSoundTag(tagId, whitelist);
                        }
                    } else {
                        Identifier soundId = Identifier.tryParse(idOrTag);
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
                            Identifier tagId = Identifier.tryParse(tagIdStr);
                            if (tagId != null) {
                                expandSoundTagWithDefault(tagId, range, weight, defaults);
                            }
                        } else {
                            Identifier soundId = Identifier.tryParse(key);
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
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.warn("[SoundDefinitionsReloadListener] Failed to re-bake config after datapack reload", t);
            }
        }
    }

    private void expandSoundTag(Identifier tagId, Set<Identifier> whitelist) {
        MinecraftServer server = com.example.soundattract.Soundattract.getServer();
        if (server == null) return;
        HolderLookup<SoundEvent> lookup = server.registryAccess().lookupOrThrow(Registries.SOUND_EVENT);
        net.minecraft.tags.TagKey<SoundEvent> tagKey = net.minecraft.tags.TagKey.create(Registries.SOUND_EVENT, tagId);
        for (Holder<SoundEvent> holder : lookup.getOrThrow(tagKey)) {
            holder.unwrapKey().ifPresent(k -> whitelist.add(k.identifier()));
        }
    }

    private void expandSoundTagWithDefault(Identifier tagId, double range, double weight, Map<Identifier, SoundDefaultEntry> defaults) {
        MinecraftServer server = com.example.soundattract.Soundattract.getServer();
        if (server == null) return;
        HolderLookup<SoundEvent> lookup = server.registryAccess().lookupOrThrow(Registries.SOUND_EVENT);
        net.minecraft.tags.TagKey<SoundEvent> tagKey = net.minecraft.tags.TagKey.create(Registries.SOUND_EVENT, tagId);
        for (Holder<SoundEvent> holder : lookup.getOrThrow(tagKey)) {
            holder.unwrapKey().ifPresent(k -> defaults.put(k.identifier(), new SoundDefaultEntry(range, weight)));
        }
    }
}
