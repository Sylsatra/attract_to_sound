package com.example.soundattract.client;

/** Pure frame selection for the manually rendered mob-alert spritesheets. */
public final class AlertAnimationFrames {
    private AlertAnimationFrames() {
    }

    public static int frameIndex(long elapsedMs, long durationMs, int sheetFrameCount) {
        if (durationMs <= 0L) {
            throw new IllegalArgumentException("Animation duration must be positive");
        }
        if (sheetFrameCount <= 0) {
            throw new IllegalArgumentException("Animation must contain at least one frame");
        }
        long phaseMs = Math.floorMod(elapsedMs, durationMs);
        return (int) (phaseMs * sheetFrameCount / durationMs);
    }

}
