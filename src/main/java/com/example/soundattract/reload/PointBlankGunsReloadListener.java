package com.example.soundattract.reload;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;

public class PointBlankGunsReloadListener extends IdentifiedJsonReloadListener {

    public PointBlankGunsReloadListener() {
        super("guns/pointblank", "pointblank_guns", false);
    }

    @Override
    protected void applyElements(Map<Identifier, JsonElement> elements, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, Double> guns = new HashMap<>();
        Map<Identifier, Double> attachments = new HashMap<>();
        Map<Identifier, Double> muzzle = new HashMap<>();

        for (Map.Entry<Identifier, JsonElement> entry : elements.entrySet()) {
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
                    String idStr = gObj.get("item").toString().trim();
                    Identifier id = Identifier.tryParse(idStr);
                    if (id == null) continue;
                    if (!gObj.has("range")) continue;
                    double range;
                    try {
                        range = gObj.get("range").getAsDouble();
                    } catch (Exception e) {
                        continue;
                    }
                    guns.put(id, range);
                }
            }

            if (root.has("attachments") && root.get("attachments").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("attachments");
                for (JsonElement attEl : arr) {
                    if (!attEl.isJsonObject()) continue;
                    JsonObject aObj = attEl.getAsJsonObject();
                    if (!aObj.has("item") || !aObj.get("item").isJsonPrimitive()) continue;
                    String idStr = aObj.get("item").toString().trim();
                    Identifier id = Identifier.tryParse(idStr);
                    if (id == null) continue;
                    if (!aObj.has("reduction")) continue;
                    double reduction;
                    try {
                        reduction = aObj.get("reduction").getAsDouble();
                    } catch (Exception e) {
                        continue;
                    }
                    attachments.put(id, reduction);
                }
            }

            if (root.has("muzzle_flash") && root.get("muzzle_flash").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("muzzle_flash");
                for (JsonElement mfEl : arr) {
                    if (!mfEl.isJsonObject()) continue;
                    JsonObject mObj = mfEl.getAsJsonObject();
                    if (!mObj.has("item") || !mObj.get("item").isJsonPrimitive()) continue;
                    String idStr = mObj.get("item").toString().trim();
                    Identifier id = Identifier.tryParse(idStr);
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

        SoundAttractConfig.DP_POINT_BLANK_GUN_RANGE_CACHE.clear();
        SoundAttractConfig.DP_POINT_BLANK_GUN_RANGE_CACHE.putAll(guns);

        SoundAttractConfig.DP_POINT_BLANK_ATTACHMENT_REDUCTION_CACHE.clear();
        SoundAttractConfig.DP_POINT_BLANK_ATTACHMENT_REDUCTION_CACHE.putAll(attachments);

        SoundAttractConfig.DP_POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE.clear();
        SoundAttractConfig.DP_POINT_BLANK_MUZZLE_FLASH_REDUCTION_CACHE.putAll(muzzle);

        try {
            SoundAttractConfig.bakeConfig();
        } catch (Throwable t) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.warn("[PointBlankGunsReloadListener] Failed to re-bake config after datapack reload", t);
            }
        }
    }
}
