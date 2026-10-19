package com.example.soundattract.spatial;

import com.example.soundattract.config.SoundAttractConfig;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public final class SpatialLifecycleEvents {
    private static final SpatialEntityIndex INDEX = new SpatialEntityIndex();
    private static final InterestRegistry INTERESTS = new InterestRegistry(2048);
    private static final AtomicLong INCARNATION_SEQUENCE = new AtomicLong();
    private static boolean registered;

    private SpatialLifecycleEvents() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        ServerEntityEvents.ENTITY_LOAD.register(SpatialLifecycleEvents::onEntityLoad);
        ServerEntityEvents.ENTITY_UNLOAD.register(SpatialLifecycleEvents::onEntityUnload);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (isEnabled()) INTERESTS.tick(server.getTickCount());
        });
        ServerWorldEvents.UNLOAD.register((server, world) -> INDEX.unloadLevel(world.dimension().location()));
    }

    public static SpatialEntityIndex index() { return INDEX; }
    public static InterestRegistry interests() { return INTERESTS; }

    private static void onEntityLoad(Entity entity, ServerLevel level) {
        if (isEnabled() && entity instanceof Mob mob) {
            long incarnation = INCARNATION_SEQUENCE.incrementAndGet();
            indexMob(mob, level.dimension().location(), incarnation, level.getGameTime());
        }
    }

    private static void onEntityUnload(Entity entity, ServerLevel level) {
        if (entity instanceof Mob mob) INDEX.remove(level.dimension().location(), mob.getUUID());
    }

    private static void indexMob(Mob mob, ResourceLocation dimension, long incarnation, long entityEpoch) {
        if (mob == null || dimension == null || mob.isRemoved()) return;
        INDEX.upsert(mob.getUUID(), incarnation, dimension, new Vec3(mob.getX(), mob.getY(), mob.getZ()),
                isEligible(mob), mob.isAlive(), mob.getHealth(), null, null, entityEpoch);
    }

    public static void refreshMob(Mob mob) {
        if (!isEnabled() || mob == null || mob.level().isClientSide || mob.isRemoved()) return;
        ResourceLocation dimension = mob.level().dimension().location();
        var indexed = INDEX.get(dimension, mob.getUUID());
        long incarnation = indexed == null ? INCARNATION_SEQUENCE.incrementAndGet() : indexed.incarnation();
        indexMob(mob, dimension, incarnation, mob.level().getGameTime());
    }

    public static void removeMob(Mob mob) {
        if (mob != null && !mob.level().isClientSide) INDEX.remove(mob.level().dimension().location(), mob.getUUID());
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
