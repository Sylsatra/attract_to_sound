package com.example.soundattract.reload;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;

public class ArmorColorsReloadListener extends IdentifierJsonReloadListener {

    public ArmorColorsReloadListener() {
        super("camo/armor_colors");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> elements, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, Integer> colors = new HashMap<>();

        for (Map.Entry<Identifier, JsonElement> entry : elements.entrySet()) {
            JsonElement element = entry.getValue();
            if (!element.isJsonObject()) continue;
            JsonObject obj = element.getAsJsonObject();

            if (obj.has("replace") && obj.get("replace").isJsonPrimitive() && obj.get("replace").getAsJsonPrimitive().isBoolean()) {
                if (obj.get("replace").getAsBoolean()) {
                    colors.clear();
                }
            }

            if (!obj.has("colors") || !obj.get("colors").isJsonObject()) continue;

            JsonObject colorsObj = obj.getAsJsonObject("colors");
            for (Map.Entry<String, JsonElement> colorEntry : colorsObj.entrySet()) {
                String key = colorEntry.getKey().trim();
                JsonElement val = colorEntry.getValue();
                if (!val.isJsonPrimitive()) continue;
                String colorStr = val.toString().trim();
                if (colorStr.isEmpty()) continue;

                Integer colorInt = parseColor(colorStr);
                if (colorInt == null) continue;

                if (key.startsWith("#")) {
                    String tagIdStr = key.substring(1);
                    Identifier tagId = Identifier.tryParse(tagIdStr);
                    if (tagId != null) {
                        expandItemTagWithColor(tagId, colorInt, colors);
                    }
                } else {
                    Identifier itemId = Identifier.tryParse(key);
                    if (itemId != null) {
                        colors.put(itemId, colorInt);
                    }
                }
            }
        }

        SoundAttractConfig.DP_CUSTOM_ARMOR_COLORS.clear();
        SoundAttractConfig.DP_CUSTOM_ARMOR_COLORS.putAll(colors);

        try {
            SoundAttractConfig.bakeConfig();
        } catch (Throwable t) {
            SoundAttractMod.LOGGER.warn("[ArmorColorsReloadListener] Failed to re-bake config after datapack reload", t);
        }
    }

    private static Integer parseColor(String colorStr) {
        String s = colorStr.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() != 6) {
            return null;
        }
        try {
            int rgb = Integer.parseInt(s, 16);
            return rgb & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void expandItemTagWithColor(Identifier tagId, int color, Map<Identifier, Integer> target) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        HolderLookup<Item> lookup = server.registryAccess().lookupOrThrow(Registries.ITEM);
        net.minecraft.tags.TagKey<Item> tagKey = net.minecraft.tags.TagKey.create(Registries.ITEM, tagId);
        for (Holder<Item> holder : lookup.getOrThrow(tagKey)) {
            holder.unwrapKey().ifPresent(k -> target.put(k.identifier(), color));
        }
    }
}
