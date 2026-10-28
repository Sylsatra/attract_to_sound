package com.example.soundattract.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.junit.jupiter.api.Test;

class AlertAnimationFramesTest {
    private static final String SPRITES = "/assets/soundattract/textures/mob_alert/";

    @Test
    void eyeMarkerCyclesAllEightForgeFramesAndWraps() {
        long durationMs = 1800L;
        for (int frame = 0; frame < 8; frame++) {
            assertEquals(frame, AlertAnimationFrames.frameIndex(frame * 225L, durationMs, 8));
            assertEquals(frame, AlertAnimationFrames.frameIndex(frame * 225L + 224L, durationMs, 8));
        }
        assertEquals(0, AlertAnimationFrames.frameIndex(1800L, durationMs, 8));
        assertEquals(7, AlertAnimationFrames.frameIndex(-1L, durationMs, 8));
    }

    @Test
    void allAlertDurationsVisitEveryFrameAndWrapWithoutAnExtraHold() {
        for (long duration : new long[]{1800L, 1150L, 1900L, 1600L}) {
            for (int frame = 0; frame < 8; frame++) {
                long start = (frame * duration + 7) / 8;
                long end = ((frame + 1) * duration + 7) / 8 - 1;
                assertEquals(frame, AlertAnimationFrames.frameIndex(start, duration, 8));
                assertEquals(frame, AlertAnimationFrames.frameIndex(end, duration, 8));
                assertEquals(frame, AlertAnimationFrames.frameIndex(duration + start, duration, 8));
            }
            assertEquals(7, AlertAnimationFrames.frameIndex(duration - 1, duration, 8));
            assertEquals(0, AlertAnimationFrames.frameIndex(duration, duration, 8));
        }
    }

    @Test
    void rejectsInvalidAnimationDurationAndFrameCount() {
        assertThrows(IllegalArgumentException.class,
                () -> AlertAnimationFrames.frameIndex(0L, 0L, 8));
        assertThrows(IllegalArgumentException.class,
                () -> AlertAnimationFrames.frameIndex(0L, 1000L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> AlertAnimationFrames.frameIndex(0L, 1000L, -1));
    }

    @Test
    void manuallyAnimatedSheetsNeverDeclareLoaderAnimationMetadata() throws IOException {
        // GeckoLib's TextureManager hook reads this exact metadata section. An
        // animation section would replace the 64x512 sheet with a 64x64 texture.
        for (String name : new String[]{"detected", "proximity", "near_miss", "scent", "raid"}) {
            try (var stream = getClass().getResourceAsStream(SPRITES + name + ".png.mcmeta")) {
                assertNotNull(stream, "Missing texture metadata for " + name);
                ResourceMetadata metadata = ResourceMetadata.fromJsonStream(stream);
                assertTrue(metadata.getSection(AnimationMetadataSection.TYPE).isEmpty(),
                        name + " must be animated only by MobAlertRenderer");
                var texture = metadata.getSection(TextureMetadataSection.TYPE).orElseThrow();
                assertFalse(texture.blur(), name + " must preserve Forge's nearest texture filtering");
                assertFalse(texture.clamp(), name + " must preserve Forge's default texture wrapping");
            }
        }
    }

    @Test
    void eyeFramesKeepTheirVisiblePixelsAtTheSameBillboardCenter() throws IOException {
        assertEyeFramesCentered("detected", false);
        // Forge's proximity artwork deliberately starts with one empty blink frame.
        assertEyeFramesCentered("proximity", true);
    }

    private void assertEyeFramesCentered(String name, boolean emptyFirstFrame) throws IOException {
        try (var stream = getClass().getResourceAsStream(SPRITES + name + ".png")) {
            assertNotNull(stream, "Missing alert sprite " + name);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, "Unreadable alert sprite " + name);
            assertEquals(64, image.getWidth());
            assertEquals(512, image.getHeight());
            for (int frame = 0; frame < 8; frame++) {
                int visiblePixels = 0;
                long sumX = 0L;
                long sumY = 0L;
                for (int y = 0; y < 64; y++) {
                    for (int x = 0; x < 64; x++) {
                        if ((image.getRGB(x, frame * 64 + y) >>> 24) != 0) {
                            visiblePixels++;
                            sumX += x;
                            sumY += y;
                        }
                    }
                }
                if (emptyFirstFrame && frame == 0) {
                    assertEquals(0, visiblePixels, "Preserve Forge's proximity blink artwork");
                } else {
                    assertTrue(visiblePixels > 0, name + " frame " + frame + " must contain the eye");
                    assertEquals(31.5, (double) sumX / visiblePixels, 1.0,
                            name + " frame " + frame + " must stay horizontally centered");
                    assertEquals(31.5, (double) sumY / visiblePixels, 1.0,
                            name + " frame " + frame + " must stay vertically centered");
                }
            }
        }
    }
}


