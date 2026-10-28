package com.example.soundattract.client;

import com.example.soundattract.config.ClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "soundattract", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AwarenessHud {

    private static final ResourceLocation[] VIS_ICONS = {
        ResourceLocation.fromNamespaceAndPath("soundattract", "textures/hud/visibility_1.png"),
        ResourceLocation.fromNamespaceAndPath("soundattract", "textures/hud/visibility_2.png"),
        ResourceLocation.fromNamespaceAndPath("soundattract", "textures/hud/visibility_3.png"),
        ResourceLocation.fromNamespaceAndPath("soundattract", "textures/hud/visibility_4.png"),
        ResourceLocation.fromNamespaceAndPath("soundattract", "textures/hud/visibility_5.png"),
    };
    private static final ResourceLocation[] NOISE_ICONS = {
        ResourceLocation.fromNamespaceAndPath("soundattract", "textures/hud/noise_0.png"),
        ResourceLocation.fromNamespaceAndPath("soundattract", "textures/hud/noise_1.png"),
        ResourceLocation.fromNamespaceAndPath("soundattract", "textures/hud/noise_2.png"),
        ResourceLocation.fromNamespaceAndPath("soundattract", "textures/hud/noise_3.png"),
        ResourceLocation.fromNamespaceAndPath("soundattract", "textures/hud/noise_4.png"),
    };
    private static final ResourceLocation[] SCENT_ICONS = {
        ResourceLocation.fromNamespaceAndPath("soundattract", "textures/hud/scent_0.png"),
        ResourceLocation.fromNamespaceAndPath("soundattract", "textures/hud/scent_1.png"),
        ResourceLocation.fromNamespaceAndPath("soundattract", "textures/hud/scent_2.png"),
        ResourceLocation.fromNamespaceAndPath("soundattract", "textures/hud/scent_3.png"),
        ResourceLocation.fromNamespaceAndPath("soundattract", "textures/hud/scent_4.png"),
    };
    private static final ResourceLocation WIND_SHIELD =
        ResourceLocation.fromNamespaceAndPath("soundattract", "textures/hud/windshield.png");

    private static final float[] ANGLES_4 = {14.0f, 36.0f, 58.0f, 80.0f};
    private static final int TEX_SIZE = 512;

    private static float windShieldProgress = 0.0f;
    private static float visSmooth = 0.0f;
    private static float noiseSmooth = 0.0f;
    private static float scentSmooth = 0.0f;
    private static long lastRenderTime = 0L;
    private static boolean registered = false;

    private AwarenessHud() {}

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("soundattract", "awareness"),
            (graphics, deltaTracker) -> render(graphics, graphics.guiWidth(), graphics.guiHeight()));
        NeoForge.EVENT_BUS.register(Events.class);
        registered = true;
    }

    public static final class Events {
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            
            if (AwarenessKeybinds.TOGGLE_HUD.consumeClick()) {
                ClientConfig.AWARENESS_HUD_ENABLED.set(!ClientConfig.AWARENESS_HUD_ENABLED.get());
                ClientConfig.AWARENESS_HUD_ENABLED.save();
            }
        }

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            AwarenessClientState.clear();
            windShieldProgress = 0.0f;
            visSmooth = 0.0f;
            noiseSmooth = 0.0f;
            scentSmooth = 0.0f;
            lastRenderTime = 0L;
        }
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
        int   iconSize  = Math.round(20 * scale);
        int   badge     = Math.round(24 * scale);
        int   arcRadius = Math.round(62 * scale);

        int direction = ClientConfig.HUD_POSITION.get() == ClientConfig.HudPosition.BOTTOM_RIGHT ? -1 : 1;
        int padding = Math.round(8 * scale);
        int extentX = Math.round(arcRadius * (float) Math.cos(Math.toRadians(ANGLES_4[0]))) + badge / 2 + 2;
        int extentY = Math.round(arcRadius * (float) Math.sin(Math.toRadians(ANGLES_4[3]))) + badge / 2 + 2;
        int insetX = Math.min(padding + ClientConfig.HUD_OFFSET_X.get(), Math.max(padding, width - extentX));
        int insetY = Math.min(padding + ClientConfig.HUD_OFFSET_Y.get(), Math.max(padding, height - extentY));
        int originX = direction == 1 ? insetX : width - insetX;
        int originY = height - insetY;

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

        ResourceLocation[] icons = { visIcon(), noiseIcon(), scentIcon(), WIND_SHIELD };

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

    private static ResourceLocation visIcon() {
        int stage = Math.max(0, Math.min(4, (int) Math.floor(AwarenessClientState.visibility() * 5.0f)));
        return VIS_ICONS[stage];
    }

    private static ResourceLocation noiseIcon() {
        int idx = Math.max(0, Math.min(4, (int) Math.floor(AwarenessClientState.noise() * 5.0f)));
        return NOISE_ICONS[idx];
    }

    private static ResourceLocation scentIcon() {
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
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(Math.max(0.0, (double) r * r - (double) dy * dy));
            graphics.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static void drawIcon(GuiGraphics graphics, ResourceLocation texture,
                                 int x, int y, int size,
                                 float r, float g, float b, float a) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(r, g, b, a);
        float s = (float) size / TEX_SIZE;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(s, s, 1.0f);
        graphics.blit(texture, 0, 0, 0.0f, 0.0f, TEX_SIZE, TEX_SIZE, TEX_SIZE, TEX_SIZE);
        graphics.pose().popPose();
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
