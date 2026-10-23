package com.example.soundattract.client;

/** Pure screen-space placement for the awareness HUD. */
public final class AwarenessHudLayout {
    private static final float FIRST_ANGLE = 14.0f;
    private static final float LAST_ANGLE = 80.0f;

    private AwarenessHudLayout() {
    }

    public static Layout calculate(int width, int height, float requestedScale,
                                   int requestedOffsetX, int requestedOffsetY, boolean bottomRight) {
        float scale = Math.max(0.5f, Math.min(2.0f, requestedScale));
        int safeWidth = Math.max(0, width);
        int safeHeight = Math.max(0, height);
        int iconSize = Math.round(20 * scale);
        int badgeSize = Math.round(24 * scale);
        int arcRadius = Math.round(62 * scale);
        int padding = Math.round(8 * scale);
        int extentX = Math.round(arcRadius * (float) Math.cos(Math.toRadians(FIRST_ANGLE)))
                + badgeSize / 2 + 2;
        int extentY = Math.round(arcRadius * (float) Math.sin(Math.toRadians(LAST_ANGLE)))
                + badgeSize / 2 + 2;
        int insetX = (int) Math.min((long) padding + Math.max(0, requestedOffsetX),
                Math.max(padding, safeWidth - extentX));
        int insetY = (int) Math.min((long) padding + Math.max(0, requestedOffsetY),
                Math.max(padding, safeHeight - extentY));
        int direction = bottomRight ? -1 : 1;
        int originX = bottomRight ? safeWidth - insetX : insetX;
        int originY = safeHeight - insetY;
        return new Layout(originX, originY, direction, iconSize, badgeSize, arcRadius, extentX, extentY);
    }

    public record Layout(int originX, int originY, int direction, int iconSize, int badgeSize,
                        int arcRadius, int extentX, int extentY) {
    }
}
