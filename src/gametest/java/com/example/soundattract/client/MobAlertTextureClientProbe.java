package com.example.soundattract.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.cache.texture.AnimatableTexture;

/** Opt-in real-client check; absent from the production mod jar. No world is opened. */
public final class MobAlertTextureClientProbe implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("soundattract-texture-probe");
    private static final int CELL = 128;
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
        Path output = Path.of(System.getProperty("soundattract.textureProbeOutput"));
        StringBuilder report = new StringBuilder();
        try {
            Files.createDirectories(output);
            ResourceLocation legacy = new ResourceLocation("soundattract_gametest", "textures/legacy_detected.png");
            AbstractTexture oldTexture = client.getTextureManager().getTexture(legacy);
            require(oldTexture instanceof AnimatableTexture, "Legacy metadata must reproduce GeckoLib wrapping");
            assertDimensions(oldTexture, 64, 64, "legacy_detected", report);

            for (String name : new String[]{"detected", "proximity", "near_miss", "scent", "raid"}) {
                ResourceLocation id = new ResourceLocation("soundattract", "textures/mob_alert/" + name + ".png");
                AbstractTexture texture = client.getTextureManager().getTexture(id);
                require(!(texture instanceof AnimatableTexture), name + " must remain a complete texture sheet");
                try (var stream = client.getResourceManager().getResourceOrThrow(id).open();
                     NativeImage image = NativeImage.read(stream)) {
                    assertDimensions(texture, image.getWidth(), image.getHeight(), name, report);
                    texture.load(client.getResourceManager());
                    assertDimensions(texture, image.getWidth(), image.getHeight(), name + " after reload", report);
                }
            }

            renderComparison(client, legacy,
                    new ResourceLocation("soundattract", "textures/mob_alert/detected.png"), output, report);
            renderAllAlertAnimations(client, output, report);
            report.append("PASS: legacy cropping reproduced; all five fixed sheets keep their full GPU dimensions; ")
                    .append("all eight detected frames stay centered; every alert dispatch, animation phase, and fade matches its GPU reference.\n");
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

    private static void assertDimensions(AbstractTexture texture, int width, int height,
                                         String name, StringBuilder report) {
        texture.bind();
        int actualWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int actualHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        report.append(name).append(": ").append(texture.getClass().getName()).append(' ')
                .append(actualWidth).append('x').append(actualHeight).append('\n');
        require(actualWidth == width && actualHeight == height,
                name + " GPU texture is " + actualWidth + "x" + actualHeight + ", expected " + width + "x" + height);
    }

    private static void renderComparison(Minecraft client, ResourceLocation legacy, ResourceLocation fixed,
                                         Path output, StringBuilder report) throws Exception {
        // Exercise the same private frame emitter and RenderType.text shader used above mobs.
        Method renderFrame = MobAlertRenderer.class.getDeclaredMethod("renderFrame", PoseStack.class,
                MultiBufferSource.class, ResourceLocation.class, int.class, int.class, int.class, float.class);
        renderFrame.setAccessible(true);
        TextureTarget target = new TextureTarget(CELL * 8, CELL * 2, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            modelView.setIdentity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, CELL * 8, CELL * 2, 0, -1000, 1000),
                    VertexSorting.ORTHOGRAPHIC_Z);
            target.setClearColor(0, 0, 0, 0);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(new BufferBuilder(4096));
            for (int row = 0; row < 2; row++) {
                for (int frame = 0; frame < 8; frame++) {
                    PoseStack pose = new PoseStack();
                    pose.translate((frame + 0.5) * CELL, (row + 0.5) * CELL, 0);
                    pose.scale(-CELL / 16.0f, -CELL / 16.0f, 1);
                    renderFrame.invoke(null, pose, buffers, row == 0 ? legacy : fixed, 8, frame, -1, 1.0f);
                }
            }
            buffers.endBatch();
            try (NativeImage capture = Screenshot.takeScreenshot(target)) {
                capture.writeToFile(output.resolve("gpu-legacy-above-fixed.png"));
                int legacyBlankFrames = 0;
                for (int frame = 0; frame < 8; frame++) {
                    double oldCenter = frameCenter(capture, 0, frame);
                    double newCenter = frameCenter(capture, 1, frame);
                    if (Double.isNaN(oldCenter)) {
                        legacyBlankFrames++;
                    }
                    report.append("frame ").append(frame).append(" legacy center=").append(oldCenter)
                            .append(" fixed center=").append(newCenter).append('\n');
                    require(!Double.isNaN(newCenter) && Math.abs(newCenter - (CELL - 1) / 2.0) <= 3.0,
                            "Fixed frame " + frame + " must render at the billboard center");
                }
                require(legacyBlankFrames == 6, "Legacy metadata must reproduce six blank UV slices");
            }
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            client.getMainRenderTarget().bindWrite(true);
            target.destroyBuffers();
        }
    }

    private static void renderAllAlertAnimations(Minecraft client, Path output, StringBuilder report) throws Exception {
        String[] names = {"proximity", "detected", "near_miss", "scent", "raid"};
        long[] durations = {1800L, 1800L, 1150L, 1900L, 1600L};
        int[] colors = {0xFFFFFF30, 0xFFFF3030, 0xFFFFB000, 0xFFFF8030, 0xFFFFC040};
        Method dispatch = MobAlertRenderer.class.getDeclaredMethod("renderAlertIcon", PoseStack.class,
                MultiBufferSource.class, AwarenessClientState.MobAlert.class, float.class, long.class);
        Method alphaMethod = MobAlertRenderer.class.getDeclaredMethod("alpha", AwarenessClientState.MobAlert.class, long.class);
        Method frameMethod = MobAlertRenderer.class.getDeclaredMethod("renderFrame", PoseStack.class,
                MultiBufferSource.class, ResourceLocation.class, int.class, int.class, int.class, float.class);
        dispatch.setAccessible(true); alphaMethod.setAccessible(true); frameMethod.setAccessible(true);
        TextureTarget target = new TextureTarget(CELL * 8, CELL * 10, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            modelView.setIdentity(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, CELL * 8, CELL * 10, 0, -1000, 1000),
                    VertexSorting.ORTHOGRAPHIC_Z);
            target.setClearColor(0, 0, 0, 0); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(new BufferBuilder(4096));
            for (int kind = 0; kind < names.length; kind++) {
                for (int frame = 0; frame < 8; frame++) {
                    long elapsed = (frame * 2L + 1L) * durations[kind] / 16L;
                    long now = 10_000L + elapsed;
                    var alert = new AwarenessClientState.MobAlert(1, kind + 1, colors[kind], 0.6f, 10_000L, now);
                    float alpha = (float) alphaMethod.invoke(null, alert, now);
                    float expectedAlpha = elapsed < 100L ? elapsed / 100.0f
                            : kind == 2 ? Math.max(0.0f, Math.min(1.0f, 1.0f - (elapsed - 500L) / 1000.0f)) : 1.0f;
                    require(Math.abs(alpha - expectedAlpha) < 0.00001f, names[kind] + " fade differs from its trigger-age contract");
                    for (int reference = 0; reference < 2; reference++) {
                        PoseStack pose = new PoseStack();
                        pose.translate((frame + 0.5) * CELL, (kind * 2 + reference + 0.5) * CELL, 0);
                        pose.scale(-CELL / 16.0f, -CELL / 16.0f, 1);
                        if (reference == 0) {
                            dispatch.invoke(null, pose, buffers, alert, alpha, now);
                        } else {
                            frameMethod.invoke(null, pose, buffers,
                                    new ResourceLocation("soundattract", "textures/mob_alert/" + names[kind] + ".png"),
                                    8, frame, colors[kind], expectedAlpha);
                        }
                    }
                }
            }
            buffers.endBatch();
            try (NativeImage capture = Screenshot.takeScreenshot(target)) {
                capture.writeToFile(output.resolve("gpu-all-alerts-with-reference.png"));
                for (int kind = 0; kind < names.length; kind++) {
                    java.util.Set<Long> distinctFrames = new java.util.HashSet<>();
                    for (int frame = 0; frame < 8; frame++) {
                        long signature = 1L;
                        for (int y = 0; y < CELL; y++) for (int x = 0; x < CELL; x++) {
                            int actual = capture.getPixelRGBA(frame * CELL + x, kind * 2 * CELL + y);
                            int expected = capture.getPixelRGBA(frame * CELL + x, (kind * 2 + 1) * CELL + y);
                            require(actual == expected, names[kind] + " dispatch/frame " + frame + " differs from reference at " + x + "," + y);
                            signature = signature * 31L + actual;
                        }
                        distinctFrames.add(signature);
                        boolean blank = Double.isNaN(frameCenter(capture, kind * 2, frame));
                        require(blank == (kind == 0 && frame == 0),
                                names[kind] + " frame " + frame + " has an unexpected blank/nonblank result");
                    }
                    require(distinctFrames.size() > 1, names[kind] + " must animate instead of remaining static");
                    report.append(names[kind]).append(": 8/8 dispatch phases match GPU reference; ")
                            .append(distinctFrames.size()).append(" distinct rendered frames\n");
                }
                var expired = new AwarenessClientState.MobAlert(1, 3, colors[2], 0, 10_000L, 11_500L);
                require((float) alphaMethod.invoke(null, expired, 11_500L) == 0.0f,
                        "Near-miss marker must finish fading by 1500 ms");
            }
        } finally {
            modelView.popPose(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            client.getMainRenderTarget().bindWrite(true); target.destroyBuffers();
        }
    }

    private static double frameCenter(NativeImage capture, int row, int frame) {
        long sum = 0;
        int count = 0;
        for (int y = 0; y < CELL; y++) {
            for (int x = 0; x < CELL; x++) {
                // Screenshot.takeScreenshot returns RGB pixels with opaque alpha,
                // including the cleared black background. Measure lit pixels.
                int pixel = capture.getPixelRGBA(frame * CELL + x, row * CELL + y);
                if ((pixel & 255) > 25 || ((pixel >>> 8) & 255) > 25 || ((pixel >>> 16) & 255) > 25) {
                    sum += y;
                    count++;
                }
            }
        }
        return count == 0 ? Double.NaN : (double) sum / count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
