package com.example.soundattract.reload;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Map;

public class TaczGunsReloadListener extends IdentifiedJsonReloadListener {

    public TaczGunsReloadListener() {
        super("guns/tacz", "tacz_guns", false);
    }

    @Override
    protected void applyElements(Map<ResourceLocation, JsonElement> elements, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, Pair<Double, Double>> guns = new HashMap<>();
        Map<String, Double> attachments = new HashMap<>();
        Map<ResourceLocation, Double> muzzle = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : elements.entrySet()) {
            JsonElement element = entry.getValue();
            if (!element.isJsonObject()) continue;
            JsonObject root = element.getAsJsonObject();

            boolean replace = root.has("replace") && root.get("replace").isJsonPrimitive()
                    && root.get("replace").getAsJsonPrimitive().isBoolean()
                    && root.get("replace").getAsBoolean();
            if (replace) {
                guns.clear();
                attachments.clear();
                muzzle.clear();
            }

            if (root.has("guns") && root.get("guns").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("guns");
                for (JsonElement gunEl : arr) {
                    if (!gunEl.isJsonObject()) continue;
                    JsonObject gObj = gunEl.getAsJsonObject();
                    if (!gObj.has("item") || !gObj.get("item").isJsonPrimitive()) continue;
                    String idStr = gObj.get("item").getAsString().trim();
                    ResourceLocation id = ResourceLocation.tryParse(idStr);
                    if (id == null) continue;
                    if (!gObj.has("db")) continue;
                    double db;
                    try {
                        db = gObj.get("db").getAsDouble();
                    } catch (Exception e) {
                        continue;
                    }
                    double weight = gObj.has("weight") ? gObj.get("weight").getAsDouble() : db / 10.0;
                    guns.put(id, Pair.of(db, weight));
                }
            }

            if (root.has("attachments") && root.get("attachments").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("attachments");
                for (JsonElement attEl : arr) {
                    if (!attEl.isJsonObject()) continue;
                    JsonObject aObj = attEl.getAsJsonObject();
                    if (!aObj.has("item") || !aObj.get("item").isJsonPrimitive()) continue;
                    String idStr = aObj.get("item").getAsString().trim();
                    ResourceLocation id = ResourceLocation.tryParse(idStr);
                    if (id == null) continue;
                    if (!aObj.has("db")) continue;
                    double db;
                    try {
                        db = aObj.get("db").getAsDouble();
                    } catch (Exception e) {
                        continue;
                    }
                    attachments.put(id.toString(), db);
                }
            }

            if (root.has("muzzle_flash") && root.get("muzzle_flash").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("muzzle_flash");
                for (JsonElement mfEl : arr) {
                    if (!mfEl.isJsonObject()) continue;
                    JsonObject mObj = mfEl.getAsJsonObject();
                    if (!mObj.has("item") || !mObj.get("item").isJsonPrimitive()) continue;
                    String idStr = mObj.get("item").getAsString().trim();
                    ResourceLocation id = ResourceLocation.tryParse(idStr);
                    if (id == null) continue;
                    if (!mObj.has("reduction")) continue;
                    double reduction;
                    try {
                        reduction = mObj.get("reduction").getAsDouble();
                    } catch (Exception e) {
                        continue;
                    }
                    muzzle.put(id, reduction);
                }
            }
        }

        SoundAttractConfig.DP_TACZ_GUN_SHOOT_DB_CACHE.clear();
        SoundAttractConfig.DP_TACZ_GUN_SHOOT_DB_CACHE.putAll(guns);

        SoundAttractConfig.DP_TACZ_ATTACHMENT_REDUCTION_DB_CACHE.clear();
        SoundAttractConfig.DP_TACZ_ATTACHMENT_REDUCTION_DB_CACHE.putAll(attachments);

        SoundAttractConfig.DP_TACZ_MUZZLE_FLASH_REDUCTION_CACHE.clear();
        SoundAttractConfig.DP_TACZ_MUZZLE_FLASH_REDUCTION_CACHE.putAll(muzzle);

        try {
            SoundAttractConfig.bakeConfig();
        } catch (Throwable t) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.warn("[TaczGunsReloadListener] Failed to re-bake config after datapack reload", t);
            }
        }
    }
}
