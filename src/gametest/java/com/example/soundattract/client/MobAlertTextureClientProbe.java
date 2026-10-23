package com.example.soundattract.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in real-client check for mob alert texture assets. */
public final class MobAlertTextureClientProbe implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("soundattract-texture-probe");
    private static final String[] ALERT_TEXTURES = {"detected", "proximity", "near_miss", "scent", "raid"};
    private boolean completed;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("soundattract.textureProbe")) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!completed && client.getOverlay() == null && client.screen instanceof TitleScreen) {
                completed = true;
                run(client);
            }
        });
    }

    private static void run(Minecraft client) {
        Path output = Path.of(System.getProperty("soundattract.textureProbeOutput", "build/texture-probe"));
        StringBuilder report = new StringBuilder();
        try {
            Files.createDirectories(output);
            for (String name : ALERT_TEXTURES) {
                Identifier id = Identifier.fromNamespaceAndPath("soundattract", "textures/mob_alert/" + name + ".png");
                try (var stream = client.getResourceManager().getResourceOrThrow(id).open();
                     NativeImage image = NativeImage.read(stream)) {
                    int width = image.getWidth();
                    int height = image.getHeight();
                    if (width != 64 || height != 64) {
                        throw new AssertionError(name + " sheet is " + width + "x" + height + ", expected 64x64");
                    }
                    client.getTextureManager().getTexture(id);
                    report.append(name).append(": loaded 64x64 sprite sheet\n");
                }
            }
            report.append("PASS: all five mob alert sheets are present and load as 64x64 assets.\n");
            LOGGER.info("[MobAlertTextureProbe] {}", report);
        } catch (Throwable failure) {
            StringWriter stack = new StringWriter();
            failure.printStackTrace(new PrintWriter(stack));
            report.append("FAIL: ").append(stack);
            LOGGER.error("[MobAlertTextureProbe] Failed", failure);
        } finally {
            try {
                Files.writeString(output.resolve("runtime-result.txt"), report);
            } catch (Exception failure) {
                LOGGER.error("Unable to write texture probe result", failure);
            }
            client.stop();
        }
    }
}
