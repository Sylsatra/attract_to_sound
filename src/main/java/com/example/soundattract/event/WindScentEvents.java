package com.example.soundattract.event;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.integration.weather2.Weather2WindCompat;
import com.example.soundattract.integration.weather2.Weather2WindDebugLog;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import com.example.soundattract.scents.ScentSourceType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WindScentEvents {


    private static final int MAX_RUNTIME_PARCELS_PER_PLAYER = 4096;
    private static final Map<ResourceLocation, Map<UUID, List<Parcel>>> PARCELS = new ConcurrentHashMap<>();

    private WindScentEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            tickLevel(level);
        }
    }

    private static void tickLevel(ServerLevel level) {
        Map<UUID, List<Parcel>> levelParcels = PARCELS.computeIfAbsent(level.dimension().location(), key -> new HashMap<>());
        if (!SoundAttractConfig.COMMON.enableWindScent.get()) {
            if (!levelParcels.isEmpty()) {
                Weather2WindDebugLog.report(level, "wind_scent_config", "disabled", () -> "Wind scent is disabled in "
                        + level.dimension().location() + "; clearing active wind trails.");
            }
            for (UUID owner : levelParcels.keySet()) {
                Weather2WindDebugLog.clear(level, windScentComponent(owner));
            }
            clearLevel(level, levelParcels);
            return;
        }
        int interval = Math.max(1, SoundAttractConfig.COMMON.windScentUpdateIntervalTicks.get());
        if (level.getGameTime() % interval != 0L) {
            return;
        }

        java.util.Set<UUID> activePlayers = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            UUID owner = player.getUUID();
            activePlayers.add(owner);
            double scent = AwarenessServerEvents.getEffectivePlayerScent(level, player);
            double normalized = Math.max(0.0, Math.min(1.0, scent / 16.0));
            List<Parcel> previous = levelParcels.get(owner);
            boolean continuing = previous != null && normalized >= SoundAttractConfig.COMMON.windScentDeactivationThreshold.get();
            boolean starting = normalized >= SoundAttractConfig.COMMON.windScentActivationThreshold.get();
            if (!level.canSeeSky(player.blockPosition())
                    || (SoundAttractConfig.COMMON.waterStopsScent.get() && player.isInWater())
                    || (!continuing && !starting)) {
                if (previous != null && !previous.isEmpty()) {
                    Weather2WindDebugLog.report(level, windScentComponent(owner), "inactive", () -> "Wind scent trail ended for "
                            + player.getGameProfile().getName() + " in " + level.dimension().location() + ".");
                }
                levelParcels.remove(owner);
                Weather2WindDebugLog.clear(level, windScentComponent(owner));
                clearNodes(level, owner);
                continue;
            }

            boolean useWeather2Wind = SoundAttractConfig.COMMON.enableWeather2WindIntegration.get()
                    && ModList.get().isLoaded("weather2");
            Vec3 direction = new Vec3(-1.0, 0.0, 0.0);
            double windSpeed = 1.0;
            boolean weather2Backed = false;
            if (useWeather2Wind) {
                Weather2WindCompat.WindSample wind = Weather2WindCompat.sample(level, player.blockPosition(), true);
                direction = wind.direction();
                windSpeed = wind.speed();
                weather2Backed = wind.weather2Backed();
            } else {
                String reason = SoundAttractConfig.COMMON.enableWeather2WindIntegration.get()
                        ? "weather2_missing" : "disabled_by_config";
                Weather2WindDebugLog.report(level, "wind_scent_source", reason,
                        () -> "Wind scent is using fallback wind in " + level.dimension().location()
                                + " because Weather2 integration is "
                                + (SoundAttractConfig.COMMON.enableWeather2WindIntegration.get()
                                ? "enabled but Weather2 is not installed" : "disabled by config") + ".");
            }
            Vec3 lateral = new Vec3(-direction.z, 0.0, direction.x);
            double movement = weather2Backed
                    ? Math.max(0.0, Math.min(2.0, windSpeed * SoundAttractConfig.COMMON.windScentSpeedScale.get()))
                    : 1.0;

            List<Parcel> next = new ArrayList<>();
            if (previous != null) {
                for (int parcelIndex = 0; parcelIndex < previous.size(); parcelIndex++) {
                    Parcel parcel = previous.get(parcelIndex);
                    double spread = Math.min(0.5, movement * SoundAttractConfig.COMMON.windScentSpreadFactor.get());
                    double lateralOffset = deterministicOffset(owner, level.getGameTime(), parcelIndex, spread);
                    Vec3 start = parcel.position();
                    Vec3 position = start
                            .add(direction.scale(movement))
                            .add(lateral.scale(lateralOffset));
                    if (position.distanceTo(player.position()) > SoundAttractConfig.COMMON.windScentMaximumDistance.get()) {
                        continue;
                    }

                    int sampleCount = Math.max(1, (int) Math.ceil(start.distanceTo(position) * 4.0));
                    BlockPos startBlockPos = BlockPos.containing(start);
                    BlockState startState = level.getBlockState(startBlockPos);
                    boolean crossedLeaves = startState.is(BlockTags.LEAVES);
                    boolean crossedRain = SoundAttractConfig.COMMON.windScentRainDecay.get()
                            && level.isRainingAt(startBlockPos);
                    boolean blocked = false;
                    BlockPos lastBlockPos = startBlockPos;
                    for (int step = 1; step <= sampleCount; step++) {
                        double fraction = step / (double) sampleCount;
                        Vec3 samplePosition = start.add(position.subtract(start).scale(fraction));
                        BlockPos sampleBlockPos = BlockPos.containing(samplePosition);
                        if (sampleBlockPos.equals(lastBlockPos)) {
                            continue;
                        }
                        lastBlockPos = sampleBlockPos;
                        BlockState sampleState = level.getBlockState(sampleBlockPos);
                        if (!sampleState.getCollisionShape(level, sampleBlockPos).isEmpty()
                                && !sampleState.is(BlockTags.LEAVES)) {
                            position = samplePosition;
                            blocked = true;
                            break;
                        }
                        if (sampleState.is(BlockTags.LEAVES)) {
                            crossedLeaves = true;
                        }
                        if (SoundAttractConfig.COMMON.windScentRainDecay.get() && level.isRainingAt(sampleBlockPos)) {
                            crossedRain = true;
                        }
                    }
                    if (blocked) {
                        double pathStrength = parcel.strength() * (crossedLeaves ? 0.55 : 1.0);
                        if (crossedRain) {
                            pathStrength /= Math.max(1.0, SoundAttractConfig.COMMON.rainDecayMultiplier.get());
                        }
                        addLeewardPuffs(level, next, position, lateral, pathStrength,
                                SoundAttractConfig.COMMON.windScentBarrierLeakage.get(),
                                SoundAttractConfig.COMMON.windScentMaximumDistance.get(), player.position());
                        continue;
                    }
                    double strength = parcel.strength() * 0.88;
                    if (crossedLeaves) {
                        strength *= 0.55;
                    }
                    if (crossedRain) {
                        strength /= Math.max(1.0, SoundAttractConfig.COMMON.rainDecayMultiplier.get());
                    }
                    if (strength > 0.03) {
                        next.add(new Parcel(position, strength));
                    }
                }
            }
            if (starting) {
                double sourceStrength = normalized;
                double shielding = AwarenessServerEvents.getWindshieldStrength(level, player, direction);
                sourceStrength *= 1.0 - SoundAttractConfig.COMMON.windshieldScentReduction.get() * shielding;
                if (sourceStrength > 0.03) {
                    next.add(new Parcel(player.position(), sourceStrength));
                }
            }
            int maximum = Math.max(1, Math.min(MAX_RUNTIME_PARCELS_PER_PLAYER,
                    SoundAttractConfig.COMMON.windScentMaximumParcels.get()));
            if (next.size() > maximum) {
                next = new ArrayList<>(next.subList(next.size() - maximum, next.size()));
            }
            if (next.isEmpty()) {
                levelParcels.remove(owner);
                Weather2WindDebugLog.clear(level, windScentComponent(owner));
                clearNodes(level, owner);
            } else {
                levelParcels.put(owner, next);
                replaceNodes(level, owner, next, level.getGameTime());
                if (!continuing) {
                    String source = weather2Backed ? "Weather2" : "fallback wind";
                    int parcelCount = next.size();
                    double loggedWindSpeed = windSpeed;
                    Weather2WindDebugLog.report(level, windScentComponent(owner), "active", () -> "Wind scent trail active for "
                            + player.getGameProfile().getName() + " in " + level.dimension().location() + " using "
                            + source + " (speed=" + loggedWindSpeed + ", parcels=" + parcelCount + ").");
                }
            }
        }
        for (UUID owner : new ArrayList<>(levelParcels.keySet())) {
            if (!activePlayers.contains(owner)) {
                levelParcels.remove(owner);
                Weather2WindDebugLog.clear(level, windScentComponent(owner));
                clearNodes(level, owner);
            }
        }
    }

    private static void replaceNodes(ServerLevel level, UUID owner, List<Parcel> parcels, long now) {
        level.getCapability(ScentManager.INSTANCE).ifPresent(manager -> {
            List<ScentNode> nodes = parcels.stream()
                    .map(parcel -> new ScentNode(parcel.position(), now, (float) parcel.strength(), owner, ScentSourceType.PLAYER_WIND))
                    .toList();
            manager.replaceWindNodes(owner, nodes);
        });
    }

    private static void clearNodes(ServerLevel level, UUID owner) {
        level.getCapability(ScentManager.INSTANCE).ifPresent(manager -> manager.clearWindNodes(owner));
    }

    private static String windScentComponent(UUID owner) {
        return "wind_scent:" + owner;
    }

    private static void addLeewardPuffs(ServerLevel level, List<Parcel> parcels, Vec3 blockedPosition,
                                        Vec3 lateral, double strength, double leakage, int maximumDistance,
                                        Vec3 sourcePosition) {
        for (int side = -1; side <= 1; side += 2) {
            Vec3 position = blockedPosition.add(lateral.scale(side));
            if (position.distanceTo(sourcePosition) > maximumDistance) {
                continue;
            }
            BlockPos blockPos = BlockPos.containing(position);
            BlockState state = level.getBlockState(blockPos);
            if (!state.getCollisionShape(level, blockPos).isEmpty() && !state.is(BlockTags.LEAVES)) {
                continue;
            }
            double leakedStrength = strength * leakage * 0.88;
            if (state.is(BlockTags.LEAVES)) {
                leakedStrength *= 0.55;
            }
            if (SoundAttractConfig.COMMON.windScentRainDecay.get() && level.isRainingAt(blockPos)) {
                leakedStrength /= Math.max(1.0, SoundAttractConfig.COMMON.rainDecayMultiplier.get());
            }
            if (leakedStrength > 0.03) {
                parcels.add(new Parcel(position, leakedStrength));
            }
        }
    }

    private static double deterministicOffset(UUID owner, long gameTime, int parcelIndex, double spread) {
        if (spread <= 0.0) {
            return 0.0;
        }
        long value = owner.getMostSignificantBits()
                ^ owner.getLeastSignificantBits()
                ^ (gameTime * 0x9E3779B97F4A7C15L)
                ^ (parcelIndex * 0xBF58476D1CE4E5B9L);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        double unit = (value >>> 11) * 0x1.0p-53;
        return (unit * 2.0 - 1.0) * spread;
    }

    private static void clearLevel(ServerLevel level, Map<UUID, List<Parcel>> levelParcels) {
        for (UUID owner : levelParcels.keySet()) {
            clearNodes(level, owner);
        }
        levelParcels.clear();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Map<UUID, List<Parcel>> removed = PARCELS.remove(level.dimension().location());
            if (removed != null) {
                for (UUID owner : removed.keySet()) {
                    Weather2WindDebugLog.clear(level, windScentComponent(owner));
                }
                clearLevel(level, removed);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            Map<UUID, List<Parcel>> removed = PARCELS.remove(level.dimension().location());
            if (removed != null) {
                clearLevel(level, removed);
            }
        }
        PARCELS.clear();
        Weather2WindDebugLog.clear();
    }

    private record Parcel(Vec3 position, double strength) {
    }
}
