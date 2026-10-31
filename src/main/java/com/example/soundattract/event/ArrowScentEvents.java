package com.example.soundattract.event;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.ai.RaidManager;
import com.example.soundattract.scents.GlobalScentRateLimiter;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import com.example.soundattract.scents.ScentSourceType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
@EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public class ArrowScentEvents {

    @SubscribeEvent
    public static void onProjectileTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (SoundAttractConfig.COMMON == null) return;
        if (!SoundAttractConfig.COMMON.enableArrowScentTrail.get()) return;

        long now = level.getGameTime();
        double interval = SoundAttractConfig.COMMON.arrowScentEmissionIntervalBlocks.get();
        double intervalSq = interval * interval;
        float strength = (float) (double) SoundAttractConfig.COMMON.arrowScentStrength.get();
        int nodeDuration = SoundAttractConfig.COMMON.arrowScentNodeDurationTicks.get();

        ArrowInvestigationEvents.ARROWS.entrySet().removeIf(entry -> {
            ArrowInvestigationEvents.ArrowState state = entry.getValue();
            if (state == null) return true;
            if (now - state.spawnTick > 1200L) return true;

            Projectile projectile = (Projectile) level.getEntity(entry.getKey());
            if (projectile == null || !projectile.isAlive()) return true;

            Vec3 curr = projectile.position();
            state.distanceSinceLastScent += state.prevPos.distanceTo(curr);

            if (state.lastScentTick == -1 || state.distanceSinceLastScent >= interval) {
                if (GlobalScentRateLimiter.tryConsume(state.shooterUuid)) {
                    BlockPos emissionPos = snapToGround(level, curr);
                    ScentSourceType sourceType = state.isMobShooter ? ScentSourceType.MOB_PROJECTILE_PATH : ScentSourceType.ARROW_PATH;
                    ScentNode node = new ScentNode(Vec3.atCenterOf(emissionPos), now, strength, state.shooterUuid, sourceType);
                    ScentManager.get(level).ifPresent(manager -> manager.addScentNode(node));

                    if (SoundAttractConfig.COMMON.enableScentParticlesForArrows.get()) {
                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                                emissionPos.getX() + 0.5, emissionPos.getY() + 0.5, emissionPos.getZ() + 0.5,
                                1, 0.2, 0.2, 0.2, 0.0);
                    }

                    state.lastScentTick = now;
                    state.distanceSinceLastScent = 0.0;
                }
            }

            state.prevPos = curr;
            return false;
        });

        ArrowInvestigationEvents.ARROWS.forEach((id, state) -> {
            if (state == null) return;
            if (!state.isMobShooter) return;
            if (now - state.spawnTick < 20 || now - state.spawnTick > 200) return;
            if (state.notifiedMobIds == null || state.notifiedMobIds.isEmpty()) return;

            Projectile projectile = (Projectile) level.getEntity(id);
            if (projectile == null) return;
            Vec3 curr = projectile.position();
            BlockPos emissionPos = snapToGround(level, curr);

            if (state.shooterUuid != null) {
                RaidManager.onScentEmittedByMobProjectile(level, state.shooterUuid, emissionPos, state.notifiedMobIds);
            }
        });
    }

    private static BlockPos snapToGround(ServerLevel level, Vec3 pos) {
        BlockPos blockPos = BlockPos.containing(pos);
        int y = level.getChunkAt(blockPos).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockPos.getX(), blockPos.getZ());
        return new BlockPos(blockPos.getX(), y, blockPos.getZ());
    }
}
