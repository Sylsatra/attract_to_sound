package com.example.soundattract.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.ClientConfig;
import com.example.soundattract.config.DebugLoggingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.joml.Matrix4f;

import java.util.Set;
import java.util.TreeSet;

@EventBusSubscriber(modid = "soundattract", value = Dist.CLIENT)
public final class MobAlertRenderer {
    private static final Identifier EYE       = Identifier.withDefaultNamespace("textures/item/spyglass.png");
    private static final Identifier DETECTED   = Identifier.fromNamespaceAndPath("soundattract", "textures/mob_alert/detected.png");
    private static final Identifier PROXIMITY  = Identifier.fromNamespaceAndPath("soundattract", "textures/mob_alert/proximity.png");
    private static final Identifier NEAR_MISS  = Identifier.fromNamespaceAndPath("soundattract", "textures/mob_alert/near_miss.png");
    private static final Identifier SCENT      = Identifier.fromNamespaceAndPath("soundattract", "textures/mob_alert/scent.png");
    private static final Identifier RAID       = Identifier.fromNamespaceAndPath("soundattract", "textures/mob_alert/raid.png");

    private static final int  EYE_FRAMES        = 8;

    private static final long EYE_DURATION_MS   = 1800L;

    private static final int  NEAR_MISS_FRAMES      = 8;

    private static final long NEAR_MISS_DURATION_MS = 1150L;

    private static final int  RAID_FRAMES      = 8;

    private static final long RAID_DURATION_MS = 1600L;

    private static final int  SCENT_FRAMES      = 8;

    private static final long SCENT_DURATION_MS = 1900L;
    private static final MultiBufferSource.BufferSource ALERT_BUFFERS =
            MultiBufferSource.immediate(new ByteBufferBuilder(2048));
    private static long lastDiagnosticAt;
    private static Set<String> lastDetectedRenderState = Set.of();
    private static Set<String> lastNearMissRenderState = Set.of();

    private MobAlertRenderer() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        long now = System.currentTimeMillis();
        boolean report = shouldReportDiagnostic(now);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (report) {
                SoundAttractMod.LOGGER.info("[MobAlertDebug] Render callback skipped: levelReady={}, playerReady={}",
                        mc.level != null, mc.player != null);
            }
            return;
        }
        if (!ClientConfig.MOB_ALERTS_ENABLED.get()) {
            if (report) SoundAttractMod.LOGGER.info("[MobAlertDebug] Render callback skipped: mob alerts disabled in client config");
            return;
        }
        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        PoseStack poseStack = event.getPoseStack();
        if (poseStack == null) {
            if (report) SoundAttractMod.LOGGER.info("[MobAlertDebug] Render pass skipped: pose stack unavailable");
            return;
        }
        MultiBufferSource.BufferSource buffers = ALERT_BUFFERS;
        double maxDistance = ClientConfig.MOB_ALERT_MAX_DISTANCE.get();
        double heightOffset = ClientConfig.MOB_ALERT_HEIGHT_OFFSET.get();
        float alertScale = ClientConfig.MOB_ALERT_SCALE.get().floatValue();
        int detectedAlerts = 0;
        int detectedDrawn = 0;
        int detectedHiddenByConfig = 0;
        int detectedMissingEntity = 0;
        int detectedOutOfRange = 0;
        int detectedTransparent = 0;
        int nearMissAlerts = 0;
        int nearMissDrawn = 0;
        Set<String> detectedRenderState = new TreeSet<>();
        Set<String> nearMissRenderState = new TreeSet<>();
        Set<java.util.Map.Entry<java.util.UUID, AwarenessClientState.MobAlert>> alerts = AwarenessClientState.alerts();
        for (var entry : alerts) {
            AwarenessClientState.MobAlert mobAlert = entry.getValue();
            boolean isDetected = mobAlert.type() == 2;
            boolean isNearMiss = mobAlert.type() == 3;
            if (isDetected) detectedAlerts++;
            if (isNearMiss) nearMissAlerts++;
            if (!isIconEnabled(mobAlert.type())) {
                if (isNearMiss) nearMissRenderState.add(mobAlert.entityId() + ":disabled");
                if (isDetected) {
                    detectedHiddenByConfig++;
                    detectedRenderState.add(mobAlert.entityId() + ":disabled");
                }
                continue;
            }
            Entity entity = mc.level.getEntity(mobAlert.entityId());
            if (!(entity instanceof Mob mob) || mob.isRemoved() || !mob.isAlive()
                    || !entry.getKey().equals(mob.getUUID())) {
                if (isNearMiss) {
                    nearMissRenderState.add(mobAlert.entityId() + ":missing_or_uuid_mismatch");
                }
                if (isDetected) {
                    detectedMissingEntity++;
                    detectedRenderState.add(mobAlert.entityId() + ":missing_entity");
                }
                continue;
            }
            if (mob.distanceToSqr(mc.player) > maxDistance * maxDistance) {
                if (isNearMiss) nearMissRenderState.add(mobAlert.entityId() + ":out_of_range");
                if (isDetected) {
                    detectedOutOfRange++;
                    detectedRenderState.add(mobAlert.entityId() + ":out_of_range");
                }
                continue;
            }
            float alpha = alpha(mobAlert, now);
            if (alpha <= 0.01f) {
                if (isNearMiss) nearMissRenderState.add(mobAlert.entityId() + ":transparent");
                if (isDetected) {
                    detectedTransparent++;
                    detectedRenderState.add(mobAlert.entityId() + ":transparent");
                }
                continue;
            }
            poseStack.pushPose();
            Vec3 interpolated = new Vec3(
                    Mth.lerp(partialTick, mob.xo, mob.getX()),
                    Mth.lerp(partialTick, mob.yo, mob.getY()),
                    Mth.lerp(partialTick, mob.zo, mob.getZ()));
            Vec3 pos = interpolated.subtract(cameraPos).add(0.0, mob.getBbHeight() + heightOffset, 0.0);
            poseStack.translate(pos.x, pos.y, pos.z);
            poseStack.mulPose(event.getLevelRenderState().cameraRenderState.orientation);
            float distanceScale = Mth.clamp(8.0f / Math.max(8.0f, mob.distanceTo(mc.player)), 0.65f, 1.15f);
            float size = 0.025f * distanceScale * alertScale;
            poseStack.scale(size, -size, size);
            int type = mobAlert.type();
            if (type == 1) {
                renderAnimatedIcon(poseStack, buffers, PROXIMITY, EYE_FRAMES, EYE_DURATION_MS, mobAlert.color(), alpha, mobAlert.startedAt(), now);
            } else if (type == 2) {
                renderAnimatedIcon(poseStack, buffers, DETECTED,  EYE_FRAMES, EYE_DURATION_MS, mobAlert.color(), alpha, mobAlert.startedAt(), now);
            } else if (type == 3) {
                renderAnimatedIcon(poseStack, buffers, NEAR_MISS, NEAR_MISS_FRAMES, NEAR_MISS_DURATION_MS, mobAlert.color(), alpha, mobAlert.startedAt(), now);
            } else if (type == 4) {
                renderAnimatedIcon(poseStack, buffers, SCENT, SCENT_FRAMES, SCENT_DURATION_MS, mobAlert.color(), alpha, mobAlert.startedAt(), now);
            } else if (type == 5) {
                renderAnimatedIcon(poseStack, buffers, RAID, RAID_FRAMES, RAID_DURATION_MS, mobAlert.color(), alpha, mobAlert.startedAt(), now);
            } else {
                renderIcon(poseStack, buffers, EYE, mobAlert.color(), alpha);
            }
            poseStack.popPose();
            if (isNearMiss) {
                nearMissDrawn++;
                nearMissRenderState.add(mobAlert.entityId() + ":queued_for_render");
            }
            if (isDetected) {
                detectedDrawn++;
                detectedRenderState.add(mobAlert.entityId() + ":drawn");
            }
        }
        buffers.endBatch();
        reportDetectedRenderTransition(detectedRenderState);
        reportNearMissRenderTransition(nearMissRenderState);
        if (report) {
            SoundAttractMod.LOGGER.info(
                    "[MobAlertDebug] Render pass: stage=AFTER_TRANSLUCENT_BLOCKS, coordinateSpace=camera_relative, dedicatedBufferFlushed=true, alertsEnabled={}, detectedVisible={}, detectedDrawn={}, nearMissVisible={}, nearMissDrawn={}, hiddenByTypeConfig={}, missingEntity={}, outOfRange={}, transparent={}, totalAlerts={}",
                    ClientConfig.MOB_ALERTS_ENABLED.get(), detectedAlerts, detectedDrawn, nearMissAlerts, nearMissDrawn,
                    detectedHiddenByConfig, detectedMissingEntity, detectedOutOfRange, detectedTransparent, alerts.size());
        }
    }

    private static boolean isIconEnabled(int type) {
        return switch (type) {
            case 1 -> ClientConfig.SHOW_PROXIMITY_ALERT.get();
            case 2 -> ClientConfig.SHOW_DETECTED_ALERT.get();
            case 3 -> ClientConfig.SHOW_NEAR_MISS_ALERT.get();
            case 4 -> ClientConfig.SHOW_SCENT_ALERT.get();
            case 5 -> ClientConfig.SHOW_RAID_ALERT.get();
            default -> ClientConfig.SHOW_OTHER_ALERT.get();
        };
    }

    private static float alpha(AwarenessClientState.MobAlert alert, long now) {
        long age = now - alert.startedAt();
        if (age < 100L) {
            return age / 100.0f;
        }
        if (alert.type() == 3) {
            return Mth.clamp(1.0f - (age - 500L) / 1000.0f, 0.0f, 1.0f);
        }
        return 1.0f;
    }

    private static void reportDetectedRenderTransition(Set<String> renderState) {
        if (!DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.STEALTH_DETECTION)
                || lastDetectedRenderState.equals(renderState)) {
            return;
        }
        lastDetectedRenderState = Set.copyOf(renderState);
        String details = renderState.stream().limit(64).collect(java.util.stream.Collectors.joining(","));
        SoundAttractMod.LOGGER.info("[MobAlertDebug] Detected-marker render state changed: count={}, entities=[{}]{}",
                renderState.size(), details, renderState.size() > 64 ? ",..." : "");
    }

    private static void reportNearMissRenderTransition(Set<String> renderState) {
        if (!DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.STEALTH_DETECTION)
                || lastNearMissRenderState.equals(renderState)) {
            return;
        }
        lastNearMissRenderState = Set.copyOf(renderState);
        String details = renderState.stream().limit(64).collect(java.util.stream.Collectors.joining(","));
        SoundAttractMod.LOGGER.info(
                "[MobAlertDebug] Near-miss render state changed: count={}, entities=[{}]{}",
                renderState.size(), details, renderState.size() > 64 ? ",..." : "");
    }

    private static boolean shouldReportDiagnostic(long now) {
        if (!DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.STEALTH_DETECTION)
                || now - lastDiagnosticAt < 2500L) {
            return false;
        }
        lastDiagnosticAt = now;
        return true;
    }

    private static Identifier texture(int type) {


        return EYE;
    }

    private static void renderAnimatedIcon(PoseStack poseStack, MultiBufferSource buffers,
                                           Identifier texture, int frames, long durationMs,
                                           int color, float alpha, long startedAt, long now) {
        long elapsed = now - startedAt;
        int frame = AlertAnimationFrames.frameIndex(elapsed, durationMs, frames);
        float vMin = (float) frame       / frames;
        float vMax = (float) (frame + 1) / frames;

        VertexConsumer consumer = buffers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.text(texture));
        Matrix4f matrix = poseStack.last().pose();
        float r = ((color >> 16) & 255) / 255.0f;
        float g = ((color >>  8) & 255) / 255.0f;
        float b = (color         & 255) / 255.0f;
        float a = ((color >>> 24) & 255) / 255.0f * alpha;
        consumer.addVertex(matrix, -8.0f, -8.0f, 0.0f).setColor(r,g,b,a).setUv(0.0f,vMin).setLight(LightTexture.FULL_BRIGHT);
        consumer.addVertex(matrix, -8.0f,  8.0f, 0.0f).setColor(r,g,b,a).setUv(0.0f,vMax).setLight(LightTexture.FULL_BRIGHT);
        consumer.addVertex(matrix,  8.0f,  8.0f, 0.0f).setColor(r,g,b,a).setUv(1.0f,vMax).setLight(LightTexture.FULL_BRIGHT);
        consumer.addVertex(matrix,  8.0f, -8.0f, 0.0f).setColor(r,g,b,a).setUv(1.0f,vMin).setLight(LightTexture.FULL_BRIGHT);
    }

    private static void renderIcon(PoseStack poseStack, MultiBufferSource buffers, Identifier texture, int color, float alpha) {
        VertexConsumer consumer = buffers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.text(texture));
        Matrix4f matrix = poseStack.last().pose();
        float r = ((color >> 16) & 255) / 255.0f;
        float g = ((color >> 8) & 255) / 255.0f;
        float b = (color & 255) / 255.0f;
        float a = ((color >>> 24) & 255) / 255.0f * alpha;
        consumer.addVertex(matrix, -8.0f, -8.0f, 0.0f).setColor(r, g, b, a).setUv(0.0f, 0.0f).setLight(LightTexture.FULL_BRIGHT);
        consumer.addVertex(matrix, -8.0f, 8.0f, 0.0f).setColor(r, g, b, a).setUv(0.0f, 1.0f).setLight(LightTexture.FULL_BRIGHT);
        consumer.addVertex(matrix, 8.0f, 8.0f, 0.0f).setColor(r, g, b, a).setUv(1.0f, 1.0f).setLight(LightTexture.FULL_BRIGHT);
        consumer.addVertex(matrix, 8.0f, -8.0f, 0.0f).setColor(r, g, b, a).setUv(1.0f, 0.0f).setLight(LightTexture.FULL_BRIGHT);
    }
}
