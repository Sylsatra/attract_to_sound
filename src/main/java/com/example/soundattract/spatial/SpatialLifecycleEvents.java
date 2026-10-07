package com.example.soundattract.spatial;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpatialLifecycleEvents {
    private static final SpatialEntityIndex INDEX = new SpatialEntityIndex();
    private static final InterestRegistry INTERESTS = new InterestRegistry(2048);
    private static final Map<UUID, Long> INCARNATIONS = new ConcurrentHashMap<>();

    private SpatialLifecycleEvents() {
    }

    public static SpatialEntityIndex index() {
        return INDEX;
    }

    public static InterestRegistry interests() {
        return INTERESTS;
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !isEnabled()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof Mob mob) || mob.level() == null) {
            return;
        }
        ResourceLocation dimension = mob.level().dimension().location();
        long incarnation = INCARNATIONS.merge(mob.getUUID(), 1L, Long::sum);
        indexMob(mob, dimension, incarnation, mob.level().getGameTime());
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        INDEX.remove(mob.level().dimension().location(), mob.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isEnabled()) {
            return;
        }
        INTERESTS.tick(event.getServer().getTickCount());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            INDEX.unloadLevel(serverLevel.dimension().location());
        }
    }

    private static void indexMob(Mob mob, ResourceLocation dimension, long incarnation, long entityEpoch) {
        if (mob == null || dimension == null || mob.isRemoved()) {
            return;
        }
        INDEX.upsert(mob.getUUID(), incarnation, dimension, new Vec3(mob.getX(), mob.getY(), mob.getZ()),
                isEligible(mob), mob.isAlive(), mob.getHealth(), null, null, entityEpoch);
    }

    public static void refreshMob(Mob mob) {
        if (!isEnabled() || mob == null || mob.level() == null || mob.isRemoved()) {
            return;
        }
        ResourceLocation dimension = mob.level().dimension().location();
        long incarnation = INCARNATIONS.getOrDefault(mob.getUUID(), 1L);
        indexMob(mob, dimension, incarnation, mob.level().getGameTime());
    }

    public static void removeMob(Mob mob) {
        if (mob == null || mob.level() == null) {
            return;
        }
        INDEX.remove(mob.level().dimension().location(), mob.getUUID());
    }

    public static boolean isEligible(Mob mob) {
        return mob != null && mob.isAlive() && !mob.isRemoved()
                && (com.example.soundattract.event.SoundAttractionEvents.getCachedAttractedEntityTypes().contains(mob.getType())
                || SoundAttractConfig.getMatchingProfile(mob) != null
                || com.example.soundattract.event.SoundAttractionEvents.isCustomNpcsMob(mob));
    }

    private static boolean isEnabled() {
        return com.example.soundattract.config.separate.PerformanceConfig.ENABLE_SPATIAL_SCHEDULER.get();
    }
}