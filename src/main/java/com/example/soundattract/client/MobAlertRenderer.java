package com.example.soundattract.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.example.soundattract.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = "soundattract", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MobAlertRenderer {
    private static final ResourceLocation EYE       = ResourceLocation.withDefaultNamespace("textures/item/spyglass.png");
    private static final ResourceLocation DETECTED   = ResourceLocation.fromNamespaceAndPath("soundattract", "textures/mob_alert/detected.png");
    private static final ResourceLocation PROXIMITY  = ResourceLocation.fromNamespaceAndPath("soundattract", "textures/mob_alert/proximity.png");
    private static final ResourceLocation NEAR_MISS  = ResourceLocation.fromNamespaceAndPath("soundattract", "textures/mob_alert/near_miss.png");
    private static final ResourceLocation SCENT      = ResourceLocation.fromNamespaceAndPath("soundattract", "textures/mob_alert/scent.png");
    private static final ResourceLocation RAID       = ResourceLocation.fromNamespaceAndPath("soundattract", "textures/mob_alert/raid.png");

    private static final int  EYE_FRAMES        = 8;

    private static final long EYE_DURATION_MS   = 1800L;

    private static final int  NEAR_MISS_FRAMES      = 8;

    private static final long NEAR_MISS_DURATION_MS = 1150L;

    private static final int  RAID_FRAMES      = 8;

    private static final long RAID_DURATION_MS = 1600L;

    private static final int  SCENT_FRAMES      = 8;

    private static final long SCENT_DURATION_MS = 1900L;

    private MobAlertRenderer() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !ClientConfig.MOB_ALERTS_ENABLED.get()) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        float partialTick = event.getPartialTick();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        double maxDistance = ClientConfig.MOB_ALERT_MAX_DISTANCE.get();
        double heightOffset = ClientConfig.MOB_ALERT_HEIGHT_OFFSET.get();
        float alertScale = ClientConfig.MOB_ALERT_SCALE.get().floatValue();
        for (var entry : AwarenessClientState.alerts()) {
            AwarenessClientState.MobAlert mobAlert = entry.getValue();
            if (!isIconEnabled(mobAlert.type())) {
                continue;
            }
            Entity entity = mc.level.getEntity(mobAlert.entityId());
            if (!(entity instanceof Mob mob) || mob.isRemoved() || !mob.isAlive()
                    || !entry.getKey().equals(mob.getUUID()) || mob.distanceToSqr(mc.player) > maxDistance * maxDistance) {
                continue;
            }
            float alpha = alpha(mobAlert);
            if (alpha <= 0.01f) {
                continue;
            }
            poseStack.pushPose();
            Vec3 interpolated = new Vec3(
                    Mth.lerp(partialTick, mob.xo, mob.getX()),
                    Mth.lerp(partialTick, mob.yo, mob.getY()),
                    Mth.lerp(partialTick, mob.zo, mob.getZ()));
            Vec3 pos = interpolated.subtract(cameraPos).add(0.0, mob.getBbHeight() + heightOffset, 0.0);
            poseStack.translate(pos.x, pos.y, pos.z);
            poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            float distanceScale = Mth.clamp(8.0f / Math.max(8.0f, mob.distanceTo(mc.player)), 0.65f, 1.15f);
            float size = 0.025f * distanceScale * alertScale;
            poseStack.scale(-size, -size, size);
            int type = mobAlert.type();
            if (type == 1) {
                renderAnimatedIcon(poseStack, buffers, PROXIMITY, EYE_FRAMES, EYE_DURATION_MS, mobAlert.color(), alpha, mobAlert.startedAt());
            } else if (type == 2) {
                renderAnimatedIcon(poseStack, buffers, DETECTED,  EYE_FRAMES, EYE_DURATION_MS, mobAlert.color(), alpha, mobAlert.startedAt());
            } else if (type == 3) {
                renderAnimatedIcon(poseStack, buffers, NEAR_MISS, NEAR_MISS_FRAMES, NEAR_MISS_DURATION_MS, mobAlert.color(), alpha, mobAlert.startedAt());
            } else if (type == 4) {
                renderAnimatedIcon(poseStack, buffers, SCENT, SCENT_FRAMES, SCENT_DURATION_MS, mobAlert.color(), alpha, mobAlert.startedAt());
            } else if (type == 5) {
                renderAnimatedIcon(poseStack, buffers, RAID, RAID_FRAMES, RAID_DURATION_MS, mobAlert.color(), alpha, mobAlert.startedAt());
            } else {
                renderIcon(poseStack, buffers, EYE, mobAlert.color(), alpha);
            }
            poseStack.popPose();
        }
        buffers.endBatch();
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

    private static float alpha(AwarenessClientState.MobAlert alert) {
        long age = System.currentTimeMillis() - alert.startedAt();
        if (age < 100L) {
            return age / 100.0f;
        }
        if (alert.type() == 3) {
            return Mth.clamp(1.0f - (age - 500L) / 1000.0f, 0.0f, 1.0f);
        }
        return 1.0f;
    }

    private static ResourceLocation texture(int type) {


        return EYE;
    }

    private static void renderAnimatedIcon(PoseStack poseStack, MultiBufferSource buffers,
                                           ResourceLocation texture, int frames, long durationMs,
                                           int color, float alpha, long startedAt) {
        long elapsed = System.currentTimeMillis() - startedAt;
        int frame = (int) ((elapsed % durationMs) * frames / durationMs);
        float vMin = (float) frame       / frames;
        float vMax = (float) (frame + 1) / frames;

        VertexConsumer consumer = buffers.getBuffer(RenderType.text(texture));
        Matrix4f matrix = poseStack.last().pose();
        float r = ((color >> 16) & 255) / 255.0f;
        float g = ((color >>  8) & 255) / 255.0f;
        float b = (color         & 255) / 255.0f;
        float a = ((color >>> 24) & 255) / 255.0f * alpha;
        consumer.vertex(matrix, -8.0f, -8.0f, 0.0f).color(r,g,b,a).uv(0.0f,vMin).uv2(LightTexture.FULL_BRIGHT).endVertex();
        consumer.vertex(matrix, -8.0f,  8.0f, 0.0f).color(r,g,b,a).uv(0.0f,vMax).uv2(LightTexture.FULL_BRIGHT).endVertex();
        consumer.vertex(matrix,  8.0f,  8.0f, 0.0f).color(r,g,b,a).uv(1.0f,vMax).uv2(LightTexture.FULL_BRIGHT).endVertex();
        consumer.vertex(matrix,  8.0f, -8.0f, 0.0f).color(r,g,b,a).uv(1.0f,vMin).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }

    private static void renderIcon(PoseStack poseStack, MultiBufferSource buffers, ResourceLocation texture, int color, float alpha) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.text(texture));
        Matrix4f matrix = poseStack.last().pose();
        float r = ((color >> 16) & 255) / 255.0f;
        float g = ((color >> 8) & 255) / 255.0f;
        float b = (color & 255) / 255.0f;
        float a = ((color >>> 24) & 255) / 255.0f * alpha;
        consumer.vertex(matrix, -8.0f, -8.0f, 0.0f).color(r, g, b, a).uv(0.0f, 0.0f).uv2(LightTexture.FULL_BRIGHT).endVertex();
        consumer.vertex(matrix, -8.0f, 8.0f, 0.0f).color(r, g, b, a).uv(0.0f, 1.0f).uv2(LightTexture.FULL_BRIGHT).endVertex();
        consumer.vertex(matrix, 8.0f, 8.0f, 0.0f).color(r, g, b, a).uv(1.0f, 1.0f).uv2(LightTexture.FULL_BRIGHT).endVertex();
        consumer.vertex(matrix, 8.0f, -8.0f, 0.0f).color(r, g, b, a).uv(1.0f, 0.0f).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }
}
