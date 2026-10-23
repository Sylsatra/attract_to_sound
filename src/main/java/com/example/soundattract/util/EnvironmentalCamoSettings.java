package com.example.soundattract.util;

public final class EnvironmentalCamoSettings {
    private EnvironmentalCamoSettings() {
    }

    public enum SamplingMode {
        AVERAGE_AREA,
        VIEWER_BACKDROP,
        HYBRID
    }

    public static SamplingMode parseMode(String raw) {
        if (raw == null) {
            return SamplingMode.HYBRID;
        }
        String normalized = raw.trim().toLowerCase();
        if ("average_area".equals(normalized) || "average".equals(normalized) || "legacy".equals(normalized)) {
            return SamplingMode.AVERAGE_AREA;
        }
        if ("viewer_backdrop".equals(normalized) || "backdrop".equals(normalized)) {
            return SamplingMode.VIEWER_BACKDROP;
        }
        return SamplingMode.HYBRID;
    }

    public static boolean shouldUseViewerBackdrop(double tps, boolean currentlyUsingViewer, double minTps, double recoveryTps) {
        if (!Double.isFinite(tps)) {
            return currentlyUsingViewer;
        }
        double min = Double.isFinite(minTps) ? minTps : 17.0D;
        double recovery = Double.isFinite(recoveryTps) ? recoveryTps : 18.5D;
        if (currentlyUsingViewer) {
            return tps >= min;
        }
        return tps >= recovery;
    }

    public static int parseHexColor(String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        String normalized = raw.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (normalized.length() != 6) {
            return fallback;
        }
        try {
            return Integer.parseInt(normalized, 16) & 0xFFFFFF;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
