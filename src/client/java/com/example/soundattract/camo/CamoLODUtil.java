package com.example.soundattract.camo;

import com.example.soundattract.config.separate.StealthConfig;
import com.example.soundattract.event.FovEvents;
import com.example.soundattract.los.OptimizedLOS;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class CamoLODUtil {
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();

    public static record LODResult(boolean shouldRender, int resolution) {}

    /**
     * Calculates if an entity should render its camouflage and at what resolution.
     */
    public static LODResult getLOD(LivingEntity entity) {
        if (entity == null || entity.level() == null || !entity.level().isClientSide()) {
            return new LODResult(false, 16);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameRenderer == null) return new LODResult(false, 16);

        if (entity == mc.player || entity.getUUID().equals(mc.player.getUUID())) {
            return new LODResult(true, StealthConfig.CAMO_TEXTURE_RESOLUTION.get());
        }

        net.minecraft.client.Camera camera = mc.gameRenderer.mainCamera();
        Vec3 cameraPos = camera.position();
        
        double distSq = entity.distanceToSqr(cameraPos);
        
        if (distSq > 64 * 64) {
            return new LODResult(false, 16);
        }
        if (!isWithinCameraView(camera, entity, 110.0)) {
            return new LODResult(false, 16);
        }

        if (distSq > 8 * 8) {
             Vec3 start = cameraPos;
             Vec3 end = entity.getEyePosition();
             if (!OptimizedLOS.hasLineOfSight(entity.level(), start, end, mc.player)) {
                 return new LODResult(false, 16);
             }
        }

        int baseRes = StealthConfig.CAMO_TEXTURE_RESOLUTION.get();
        double dist = Math.sqrt(distSq);
        
        int resolution = baseRes;
        if (dist > 32) {
            resolution = baseRes / 4;
        } else if (dist > 16) {
            resolution = baseRes / 2;
        }
        
        resolution = Math.max(32, resolution);

        return new LODResult(true, resolution);
    }

    private static boolean isWithinCameraView(net.minecraft.client.Camera camera, LivingEntity target, double horizontalFovDegrees) {
        Vec3 cameraPos = camera.position();
        Vec3 lookVector = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
        
        Vec3 toTargetVector = target.position().add(0, target.getEyeHeight() / 2.0, 0)
                .subtract(cameraPos);
        
        if (toTargetVector.lengthSqr() < 0.01) return true;
        toTargetVector = toTargetVector.normalize();

        double dot = lookVector.dot(toTargetVector);
        double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
        
        return angle <= horizontalFovDegrees / 1.5;
    }
}
