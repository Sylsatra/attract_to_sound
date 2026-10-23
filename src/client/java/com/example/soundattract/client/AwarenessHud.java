package com.example.soundattract.client;

import com.example.soundattract.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public final class AwarenessHud {

    private static final Identifier[] VIS_ICONS = {
        Identifier.fromNamespaceAndPath("soundattract", "textures/hud/visibility_1.png"),
        Identifier.fromNamespaceAndPath("soundattract", "textures/hud/visibility_2.png"),
        Identifier.fromNamespaceAndPath("soundattract", "textures/hud/visibility_3.png"),
        Identifier.fromNamespaceAndPath("soundattract", "textures/hud/visibility_4.png"),
        Identifier.fromNamespaceAndPath("soundattract", "textures/hud/visibility_5.png"),
    };
    private static final Identifier[] NOISE_ICONS = {
        Identifier.fromNamespaceAndPath("soundattract", "textures/hud/noise_0.png"),
        Identifier.fromNamespaceAndPath("soundattract", "textures/hud/noise_1.png"),
        Identifier.fromNamespaceAndPath("soundattract", "textures/hud/noise_2.png"),
        Identifier.fromNamespaceAndPath("soundattract", "textures/hud/noise_3.png"),
        Identifier.fromNamespaceAndPath("soundattract", "textures/hud/noise_4.png"),
    };
    private static final Identifier[] SCENT_ICONS = {
        Identifier.fromNamespaceAndPath("soundattract", "textures/hud/scent_0.png"),
        Identifier.fromNamespaceAndPath("soundattract", "textures/hud/scent_1.png"),
        Identifier.fromNamespaceAndPath("soundattract", "textures/hud/scent_2.png"),
        Identifier.fromNamespaceAndPath("soundattract", "textures/hud/scent_3.png"),
        Identifier.fromNamespaceAndPath("soundattract", "textures/hud/scent_4.png"),
    };
    private static final Identifier WIND_SHIELD =
        Identifier.fromNamespaceAndPath("soundattract", "textures/hud/windshield.png");

    private static final float[] ANGLES_4 = {14.0f, 36.0f, 58.0f, 80.0f};
    private static final int TEX_SIZE = 512;

    private static float windShieldProgress = 0.0f;
    private static float visSmooth = 0.0f;
    private static float noiseSmooth = 0.0f;
    private static float scentSmooth = 0.0f;
    private static long lastRenderTime = 0L;
    private static boolean registered = false;

    private AwarenessHud() {}

    public static void register() {
        if (registered) return;
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> {
            Minecraft mc = Minecraft.getInstance();
            render(graphics, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        });
        registered = true;
    }

    public static void reset() {
        windShieldProgress = 0.0f;
        visSmooth = 0.0f;
        noiseSmooth = 0.0f;
        scentSmooth = 0.0f;
        lastRenderTime = 0L;
    }

    private static void render(GuiGraphics graphics, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (!registered || player == null || mc.level == null
                || !ClientConfig.AWARENESS_HUD_ENABLED.get() || mc.options.hideGui
                || mc.screen != null) {
            return;
        }

        long now = System.currentTimeMillis();
        float dt = lastRenderTime > 0L ? Math.min(0.1f, (now - lastRenderTime) / 1000.0f) : 0.016f;
        lastRenderTime = now;

        float target = AwarenessClientState.windshielded() ? 1.0f : 0.0f;
        float lerpRate = 1.0f - (float) Math.exp(-6.0 * dt);
        windShieldProgress += (target - windShieldProgress) * lerpRate;
        visSmooth += (AwarenessClientState.visibility() - visSmooth) * lerpRate;
        noiseSmooth += (AwarenessClientState.noise() - noiseSmooth) * lerpRate;
        scentSmooth += ((float) AwarenessClientState.scent() / 4.0f - scentSmooth) * lerpRate;

        float scale     = ClientConfig.HUD_SCALE.get().floatValue();
        int   opacity   = ClientConfig.HUD_OPACITY.get();
        AwarenessHudLayout.Layout layout = AwarenessHudLayout.calculate(
                width, height, scale,
                ClientConfig.HUD_OFFSET_X.get(), ClientConfig.HUD_OFFSET_Y.get(),
                ClientConfig.HUD_POSITION.get() == ClientConfig.HudPosition.BOTTOM_RIGHT);
        int iconSize = layout.iconSize();
        int badge = layout.badgeSize();
        int arcRadius = layout.arcRadius();
        int direction = layout.direction();
        int originX = layout.originX();
        int originY = layout.originY();

        boolean[] showIcon = {
            ClientConfig.SHOW_VISIBILITY.get(),
            ClientConfig.SHOW_ENVIRONMENTAL_NOISE.get(),
            ClientConfig.SHOW_SCENT.get(),
            ClientConfig.SHOW_WIND_SHIELD.get()
        };
        if (!showIcon[0] && !showIcon[1] && !showIcon[2] && !showIcon[3]) {
            return;
        }
        drawArcLine(graphics, originX, originY, arcRadius, ANGLES_4, opacity, direction);

        Identifier[] icons = { visIcon(), noiseIcon(), scentIcon(), WIND_SHIELD };

        for (int i = 0; i < 4; i++) {
            if (!showIcon[i]) continue;
            float rad = (float) Math.toRadians(ANGLES_4[i]);
            int cx = originX + direction * Math.round(arcRadius * (float) Math.cos(rad));
            int cy = originY - Math.round(arcRadius * (float) Math.sin(rad));

            if (i == 3) {
                float p = Math.max(0.0f, Math.min(1.0f, windShieldProgress));
                int bgAlpha = Math.min(255, opacity);
                int rimAlpha = Math.round(opacity * (0.85f + 0.15f * p));

                int rimR = Math.round(0x6E + (0x38 - 0x6E) * p);
                int rimG = Math.round(0x82 + (0xF0 - 0x82) * p);
                int rimB = Math.round(0x8E + (0xFF - 0x8E) * p);
                int rimRgb = (rimR << 16) | (rimG << 8) | rimB;

                float glowIntensity = p > 0.1f ? p : 0.0f;
                drawBadge(graphics, cx, cy, badge, bgAlpha, rimAlpha, rimRgb, glowIntensity, 0x38F0FF, opacity);

                float iconR = 0.65f + 0.35f * p;
                float iconG = 0.75f + 0.25f * p;
                float iconB = 0.82f + 0.18f * p;
                float iconA = (opacity / 255.0f) * (0.75f + 0.25f * p);

                drawIcon(graphics, icons[i], cx - iconSize / 2, cy - iconSize / 2, iconSize,
                         iconR, iconG, iconB, iconA);
            } else {
                float threat;
                if (i == 0) {
                    threat = visSmooth;
                } else if (i == 1) {
                    threat = noiseSmooth;
                } else {
                    threat = scentSmooth;
                }
                threat = Math.max(0.0f, Math.min(1.0f, threat));

                int bgAlpha = Math.min(255, opacity);
                int rimAlpha = Math.round(opacity * (0.45f + 0.55f * threat));
                int rimRgb = getThreatColor(threat);

                float glowIntensity = 0.0f;
                if (threat > 0.60f) {
                    float baseGlow = (threat - 0.60f) / 0.40f;
                    if (threat >= 0.85f) {
                        float pulse = 0.75f + 0.25f * (float) Math.sin(now * 0.009);
                        glowIntensity = baseGlow * pulse;
                    } else {
                        glowIntensity = baseGlow * 0.7f;
                    }
                }

                drawBadge(graphics, cx, cy, badge, bgAlpha, rimAlpha, rimRgb, glowIntensity, rimRgb, opacity);

                float r = 1.0f;
                float g = 1.0f;
                float b = 1.0f;
                float a = opacity / 255.0f;

                if (i == 0) {
                    r = ((rimRgb >> 16) & 255) / 255.0f;
                    g = ((rimRgb >> 8)  & 255) / 255.0f;
                    b = (rimRgb         & 255) / 255.0f;
                }

                drawIcon(graphics, icons[i], cx - iconSize / 2, cy - iconSize / 2, iconSize,
                         r, g, b, a);
            }
        }
    }

    private static int getThreatColor(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int r, g, b;
        if (t < 0.40f) {
            float f = t / 0.40f;
            r = Math.round(0x4A + (0x98 - 0x4A) * f);
            g = Math.round(0x98 + (0xB8 - 0x98) * f);
            b = Math.round(0x58 + (0x38 - 0x58) * f);
        } else if (t < 0.70f) {
            float f = (t - 0.40f) / 0.30f;
            r = Math.round(0x98 + (0xDC - 0x98) * f);
            g = Math.round(0xB8 + (0x80 - 0xB8) * f);
            b = Math.round(0x38 + (0x28 - 0x38) * f);
        } else {
            float f = (t - 0.70f) / 0.30f;
            r = Math.round(0xDC + (0xE8 - 0xDC) * f);
            g = Math.round(0x80 + (0x34 - 0x80) * f);
            b = Math.round(0x28 + (0x34 - 0x28) * f);
        }
        return (r << 16) | (g << 8) | b;
    }

    private static Identifier visIcon() {
        int stage = Math.max(0, Math.min(4, (int) Math.floor(AwarenessClientState.visibility() * 5.0f)));
        return VIS_ICONS[stage];
    }

    private static Identifier noiseIcon() {
        int idx = Math.max(0, Math.min(4, (int) Math.floor(AwarenessClientState.noise() * 5.0f)));
        return NOISE_ICONS[idx];
    }

    private static Identifier scentIcon() {
        int idx = Math.max(0, Math.min(4, AwarenessClientState.scent()));
        return SCENT_ICONS[idx];
    }

    private static void drawArcLine(GuiGraphics graphics, int ox, int oy,
                                    int radius, float[] angles, int opacity, int direction) {
        int lineColor = (Math.max(15, opacity / 8) << 24) | 0x4A6A50;
        float startDeg = angles[0] - 3.0f;
        float endDeg   = angles[angles.length - 1] + 3.0f;
        for (int s = 0; s <= 50; s++) {
            float t = startDeg + (endDeg - startDeg) * s / 50.0f;
            int x = ox + direction * Math.round(radius * (float) Math.cos(Math.toRadians(t)));
            int y = oy - Math.round(radius * (float) Math.sin(Math.toRadians(t)));
            graphics.fill(x, y, x + 1, y + 1, lineColor);
        }
    }

    private static void drawBadge(GuiGraphics graphics, int cx, int cy, int diameter,
                                  int bgAlpha, int rimAlpha, int rimRgb,
                                  float glowIntensity, int glowRgb, int opacity) {
        int r = diameter / 2;
        if (glowIntensity > 0.05f) {
            int glowAlpha2 = Math.round(opacity * 0.28f * glowIntensity);
            int glowAlpha1 = Math.round(opacity * 0.55f * glowIntensity);
            drawFilledCircle(graphics, cx, cy, r + 2, (glowAlpha2 << 24) | (glowRgb & 0xFFFFFF));
            drawFilledCircle(graphics, cx, cy, r + 1, (glowAlpha1 << 24) | (glowRgb & 0xFFFFFF));
        }
        int bg  = (Math.max(0, Math.min(255, bgAlpha))  << 24) | 0x10151B;
        int rim = (Math.max(0, Math.min(255, rimAlpha)) << 24) | (rimRgb & 0xFFFFFF);
        drawFilledCircle(graphics, cx, cy, r,     rim);
        drawFilledCircle(graphics, cx, cy, r - 1, bg);
    }

    private static void drawFilledCircle(GuiGraphics graphics, int cx, int cy, int r, int color) {
        if ((color >>> 24) == 0) return;
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(Math.max(0.0, (double) r * r - (double) dy * dy));
            graphics.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static void drawIcon(GuiGraphics graphics, Identifier texture,
                                 int x, int y, int size,
                                 float r, float g, float b, float a) {
        int color = (Math.round(clamp(a) * 255.0f) << 24)
                | (Math.round(clamp(r) * 255.0f) << 16)
                | (Math.round(clamp(g) * 255.0f) << 8)
                | Math.round(clamp(b) * 255.0f);
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture,
                x, y, 0.0f, 0.0f, size, size,
                TEX_SIZE, TEX_SIZE, TEX_SIZE, TEX_SIZE, color);
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
