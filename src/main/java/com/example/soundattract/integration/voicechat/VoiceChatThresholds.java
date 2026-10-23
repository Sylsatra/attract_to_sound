package com.example.soundattract.integration.voicechat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class VoiceChatThresholds {
    private static volatile List<Threshold> cached = Collections.emptyList();
    private static volatile int cachedHash = 0;

    private VoiceChatThresholds() {}

    static List<Threshold> getThresholds(List<? extends String> rawMap) {
        if (rawMap == null || rawMap.isEmpty()) {
            cached = Collections.emptyList();
            cachedHash = 0;
            return cached;
        }
        int hash = rawMap.hashCode();
        List<Threshold> local = cached;
        if (hash == cachedHash && local != null) {
            return local;
        }
        List<Threshold> parsed = new ArrayList<>();
        for (String entry : rawMap) {
            if (entry == null || entry.isEmpty()) continue;
            String[] parts = entry.split(":", 2);
            if (parts.length != 2) continue;
            try {
                double th = Double.parseDouble(parts[0].trim());
                double mul = Double.parseDouble(parts[1].trim());
                parsed.add(new Threshold(th, mul));
            } catch (Exception ignored) {
            }
        }
        parsed.sort(Comparator.comparingDouble((Threshold t) -> t.threshold).reversed());
        List<Threshold> unmodifiable = Collections.unmodifiableList(parsed);
        cached = unmodifiable;
        cachedHash = hash;
        return unmodifiable;
    }

    static final class Threshold {
        final double threshold;
        final double multiplier;

        Threshold(double threshold, double multiplier) {
            this.threshold = threshold;
            this.multiplier = multiplier;
        }
    }
}
