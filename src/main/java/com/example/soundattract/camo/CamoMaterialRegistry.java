package com.example.soundattract.camo;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.separate.StealthConfig;
import net.minecraft.resources.Identifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CamoMaterialRegistry {

    public record CamoMaterialEntry(int color, String category, boolean blocksScent) {
        public com.example.soundattract.camo.CamoLayer getLayer(long tick, long seed, com.example.soundattract.util.CamoUtil.ClimateData climate) {
            int max = Math.max(1, com.example.soundattract.config.separate.StealthConfig.MAX_CAMO_LAYERS.get());
            float initialDurability = 1.0f / max;
            return new com.example.soundattract.camo.CamoLayer(color, category, initialDurability, initialDurability, blocksScent, tick, seed, climate.erosion(), climate.humidity(), climate.temperature());
        }
    }
    public record CategoryRules(float minTemp, float maxTemp, boolean waterSensitive) {}

    private static final Map<Identifier, CamoMaterialEntry> MATERIALS = new ConcurrentHashMap<>();
    private static final Map<String, CategoryRules> CATEGORIES = new ConcurrentHashMap<>();

    public static void parseConfig() {
        MATERIALS.clear();
        CATEGORIES.clear();

        List<? extends String> materialLines = StealthConfig.CAMOUFLAGE_MATERIALS.get();
        for (String line : materialLines) {
            try {
                String[] parts = line.split(";");
                if (parts.length == 4) {
                    Identifier id = Identifier.tryParse(parts[0].trim());
                    String hex = parts[1].trim();
                    String category = parts[2].trim();
                    boolean blocksScent = Boolean.parseBoolean(parts[3].trim());

                    if (id != null && hex.startsWith("#") && hex.length() == 7) {
                        int color = Integer.parseInt(hex.substring(1), 16);
                        MATERIALS.put(id, new CamoMaterialEntry(color, category, blocksScent));
                    }
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("[CamoRegistry] Failed to parse material line: {}", line);
            }
        }

        List<? extends String> categoryLines = StealthConfig.CAMO_CATEGORY_RULES.get();
        for (String line : categoryLines) {
            try {
                String[] parts = line.split(";");
                if (parts.length == 3) {
                    String category = parts[0].trim();
                    String tempRange = parts[1].trim();
                    boolean waterSensitive = Boolean.parseBoolean(parts[2].trim());

                    String[] temps = tempRange.split("-");
                    float minTemp = Float.parseFloat(temps[0]);
                    float maxTemp = Float.parseFloat(temps[1]);

                    CATEGORIES.put(category, new CategoryRules(minTemp, maxTemp, waterSensitive));
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("[CamoRegistry] Failed to parse category line: {}", line);
            }
        }
    }

    public static CamoMaterialEntry getMaterial(Identifier id) {
        return MATERIALS.get(id);
    }

    public static CategoryRules getCategoryRules(String category) {
        return CATEGORIES.get(category);
    }
}
