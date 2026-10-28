package com.example.soundattract.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record ScentVisibilityConfig(boolean showPlayerWalk, boolean showArrowScent, boolean showWindScent) {
    public static final Codec<ScentVisibilityConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("showPlayerWalk").forGetter(ScentVisibilityConfig::showPlayerWalk),
            Codec.BOOL.fieldOf("showArrowScent").forGetter(ScentVisibilityConfig::showArrowScent),
            Codec.BOOL.optionalFieldOf("showWindScent", false).forGetter(ScentVisibilityConfig::showWindScent)
    ).apply(instance, ScentVisibilityConfig::new));
    public static final ScentVisibilityConfig DEFAULT = new ScentVisibilityConfig(true, true, false);

    public static ScentVisibilityConfig fromStringList(java.util.List<String> list) {
        if (list == null || list.isEmpty()) return DEFAULT;
        boolean playerWalk = true;
        boolean arrowScent = true;
        boolean windScent = false;
        for (String s : list) {
            String[] kv = s.split("=", 2);
            if (kv.length == 2) {
                String key = kv[0].trim();
                String val = kv[1].trim();
                switch (key) {
                    case "showPlayerWalk" -> playerWalk = Boolean.parseBoolean(val);
                    case "showArrowScent" -> arrowScent = Boolean.parseBoolean(val);
                    case "showWindScent" -> windScent = Boolean.parseBoolean(val);
                }
            }
        }
        return new ScentVisibilityConfig(playerWalk, arrowScent, windScent);
    }

    public java.util.List<String> toStringList() {
        java.util.List<String> list = new java.util.ArrayList<>();
        list.add("showPlayerWalk=" + showPlayerWalk);
        list.add("showArrowScent=" + showArrowScent);
        list.add("showWindScent=" + showWindScent);
        return list;
    }
}
