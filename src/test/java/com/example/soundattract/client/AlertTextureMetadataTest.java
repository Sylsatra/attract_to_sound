package com.example.soundattract.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.junit.jupiter.api.Test;

class AlertTextureMetadataTest {
    private static final String SPRITES = "/assets/soundattract/textures/mob_alert/";

    @Test
    void manuallyAnimatedAlertSheetsRemainFullTextures() throws IOException {
        for (String name : new String[]{"detected", "proximity", "near_miss", "scent", "raid"}) {
            try (var stream = getClass().getResourceAsStream(SPRITES + name + ".png.mcmeta")) {
                assertNotNull(stream, "Missing texture metadata for " + name);
                ResourceMetadata metadata = ResourceMetadata.fromJsonStream(stream);
                assertTrue(metadata.getSection(AnimationMetadataSection.SERIALIZER).isEmpty(),
                        name + " must be sliced by MobAlertRenderer, not GeckoLib");
                var texture = metadata.getSection(TextureMetadataSection.SERIALIZER).orElseThrow();
                assertFalse(texture.isBlur(), name + " must keep nearest texture filtering");
                assertFalse(texture.isClamp(), name + " must keep the existing texture wrapping");
            }
        }
    }
}
