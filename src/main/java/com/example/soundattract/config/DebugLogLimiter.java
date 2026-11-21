package com.example.soundattract.config;

import com.example.soundattract.SoundAttractMod;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class DebugLogLimiter {
    public enum Channel {
        STEALTH_QUERY(DebugLoggingConfig.Feature.STEALTH_DETECTION),
        PLAYER_STANCE(DebugLoggingConfig.Feature.STEALTH_DETECTION),
        CAMOUFLAGE_PERCEPTION(DebugLoggingConfig.Feature.CAMOUFLAGE),
        CAMOUFLAGE_BACKDROP(DebugLoggingConfig.Feature.CAMOUFLAGE),
        CAMOUFLAGE_FALLBACK(DebugLoggingConfig.Feature.CAMOUFLAGE);

        private final DebugLoggingConfig.Feature feature;

        Channel(DebugLoggingConfig.Feature feature) {
            this.feature = feature;
        }
    }

    private static final long LOG_INTERVAL_NANOS = 1_000_000_000L;
    private static final EnumMap<Channel, Bucket> BUCKETS = new EnumMap<>(Channel.class);

    static {
        for (Channel channel : Channel.values()) BUCKETS.put(channel, new Bucket());
    }

    private DebugLogLimiter() {}

    public static boolean allow(Channel channel) {
        if (channel == null || !DebugLoggingConfig.isEnabled(channel.feature)) return false;
        Bucket bucket = BUCKETS.get(channel);
        long now = System.nanoTime();
        long previous = bucket.lastLogNanos.get();
        if (previous == 0L || now - previous >= LOG_INTERVAL_NANOS) {
            if (bucket.lastLogNanos.compareAndSet(previous, now)) {
                int suppressed = bucket.suppressed.getAndSet(0);
                if (suppressed > 0) {
                    SoundAttractMod.LOGGER.debug("[DebugLogLimiter] channel={} suppressed={} interval=1s",
                            channel, suppressed);
                }
                return true;
            }
        }
        bucket.suppressed.incrementAndGet();
        return false;
    }

    private static final class Bucket {
        private final AtomicLong lastLogNanos = new AtomicLong();
        private final AtomicInteger suppressed = new AtomicInteger();
    }
}
