package com.example.soundattract.client;

/** Shared, deterministic frame selection for vertically stacked alert atlases. */
public final class AlertAnimationFrames {
    private AlertAnimationFrames() {
    }

    public static int frameIndex(long elapsedMs, long durationMs, int frameCount) {
        if (durationMs <= 0L) throw new IllegalArgumentException("durationMs must be positive");
        if (frameCount <= 0) throw new IllegalArgumentException("frameCount must be positive");
        long phaseMs = Math.floorMod(elapsedMs, durationMs);
        return (int) (phaseMs * frameCount / durationMs);
    }
}
