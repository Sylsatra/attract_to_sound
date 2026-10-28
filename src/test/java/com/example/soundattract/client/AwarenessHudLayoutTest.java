package com.example.soundattract.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AwarenessHudLayoutTest {
    @Test
    void keepsHudExtentsInsideCommonGuiSizesAtEverySupportedScaleAndCorner() {
        int[][] dimensions = {{320, 240}, {640, 360}, {1920, 1080}};
        float[] scales = {0.5f, 1.0f, 2.0f};
        int[] offsets = {0, 4096};

        for (int[] size : dimensions) {
            for (float scale : scales) {
                for (int offsetX : offsets) {
                    for (int offsetY : offsets) {
                        for (boolean bottomRight : new boolean[]{false, true}) {
                            AwarenessHudLayout.Layout layout = AwarenessHudLayout.calculate(
                                    size[0], size[1], scale, offsetX, offsetY, bottomRight);
                            String context = "screen=" + size[0] + "x" + size[1] + ", scale=" + scale
                                    + ", offset=" + offsetX + "/" + offsetY + ", bottomRight=" + bottomRight;
                            assertTrue(layout.originY() <= size[1], "HUD origin exceeds bottom edge: " + context);
                            assertTrue(layout.originY() - layout.extentY() >= 0,
                                    "HUD extends above screen: " + context + " layout=" + layout);
                            if (bottomRight) {
                                assertTrue(layout.originX() <= size[0], "HUD origin exceeds right edge: " + context);
                                assertTrue(layout.originX() - layout.extentX() >= 0,
                                        "HUD extends left of screen: " + context + " layout=" + layout);
                            } else {
                                assertTrue(layout.originX() >= 0, "HUD origin precedes left edge: " + context);
                                assertTrue(layout.originX() + layout.extentX() <= size[0],
                                        "HUD extends right of screen: " + context + " layout=" + layout);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void clampsInvalidScaleAndOffsetsBeforeCalculatingPlacement() {
        AwarenessHudLayout.Layout lowScale = AwarenessHudLayout.calculate(640, 360, 0.1f, -100, -100, false);
        AwarenessHudLayout.Layout minimumScale = AwarenessHudLayout.calculate(640, 360, 0.5f, 0, 0, false);
        AwarenessHudLayout.Layout highScale = AwarenessHudLayout.calculate(640, 360, 4.0f, Integer.MAX_VALUE,
                Integer.MAX_VALUE, true);
        AwarenessHudLayout.Layout maximumScale = AwarenessHudLayout.calculate(640, 360, 2.0f, 4096, 4096, true);

        assertTrue(lowScale.equals(minimumScale), "Out-of-range scale/offset values must normalize consistently");
        assertTrue(highScale.equals(maximumScale), "Oversized scale/offset values must clamp to supported bounds");
    }
}
