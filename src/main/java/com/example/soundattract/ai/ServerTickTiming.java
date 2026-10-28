package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Tracks server work time for features that scale their per-tick budgets under load. */
@EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
final class ServerTickTiming {
    private static final double INITIAL_TICK_MS = 50.0D;
    private static final double MAX_SAMPLE_MS = 250.0D;
    private static final double EWMA_ALPHA = 0.1D;
    private static final Map<MinecraftServer, TickSample> SAMPLES = new ConcurrentHashMap<>();

    private ServerTickTiming() {
    }

    static double averageTickMillis(MinecraftServer server) {
        if (server == null) return INITIAL_TICK_MS;
        TickSample sample = SAMPLES.get(server);
        return sample == null ? INITIAL_TICK_MS : sample.averageMillis;
    }

    @SubscribeEvent
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        SAMPLES.computeIfAbsent(event.getServer(), ignored -> new TickSample()).startedAtNanos = System.nanoTime();
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        TickSample sample = SAMPLES.computeIfAbsent(event.getServer(), ignored -> new TickSample());
        long startedAt = sample.startedAtNanos;
        if (startedAt == 0L) return;

        sample.startedAtNanos = 0L;
        double elapsedMillis = Math.min(MAX_SAMPLE_MS, Math.max(0.0D, (System.nanoTime() - startedAt) / 1_000_000.0D));
        sample.averageMillis += (elapsedMillis - sample.averageMillis) * EWMA_ALPHA;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SAMPLES.remove(event.getServer());
    }

    private static final class TickSample {
        private volatile long startedAtNanos;
        private volatile double averageMillis = INITIAL_TICK_MS;
    }
}
