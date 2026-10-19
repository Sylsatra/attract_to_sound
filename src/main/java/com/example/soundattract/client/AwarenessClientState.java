package com.example.soundattract.client;

import com.example.soundattract.SoundAttractClient;
import com.example.soundattract.config.DebugLoggingConfig;
import com.example.soundattract.network.AwarenessSnapshotMessage;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

public final class AwarenessClientState {
    private static final int MAX_ALERTS = 4096;
    private static final long MISSING_ALERT_GRACE_MS = 750L;
    private static float visibility;
    private static float noise;
    private static int scent;
    private static boolean windshielded;
    private static long lastUpdate;
    private static long lastAlertDiagnosticAt;
    private static Set<Integer> lastDetectedSnapshotEntityIds = Set.of();



    private static volatile Set<Map.Entry<UUID, MobAlert>> alertSnapshot = Set.of();
    private static final Cache<UUID, MobAlert> ALERTS = Caffeine.newBuilder()
            .maximumSize(MAX_ALERTS)
            .expireAfterWrite(2500L, TimeUnit.MILLISECONDS)
            .removalListener((UUID ignored, MobAlert ignoredAlert, com.github.benmanes.caffeine.cache.RemovalCause ignoredCause) -> refreshAlertSnapshot())
            .build();

    private AwarenessClientState() {
    }

    private static synchronized void refreshAlertSnapshot() {
        alertSnapshot = ALERTS.asMap().entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static synchronized void apply(AwarenessSnapshotMessage message) {
        applyAt(message, System.currentTimeMillis());
    }

    static synchronized void applyAt(AwarenessSnapshotMessage message, long now) {
        if (message == null || message.alerts() == null) return;
        visibility = message.visibility();
        noise = message.noise();
        scent = message.scent();
        windshielded = message.windshielded();
        lastUpdate = now;
        Set<UUID> received = new HashSet<>();
        Set<Integer> detectedEntityIds = new HashSet<>();
        for (AwarenessSnapshotMessage.Alert alert : message.alerts()) {
            if (alert == null || alert.mobId() == null) continue;
            received.add(alert.mobId());
            if (alert.type() == 2) {
                detectedEntityIds.add(alert.entityId());
            }
            MobAlert previous = ALERTS.getIfPresent(alert.mobId());
            boolean sameAnimation = previous != null && previous.type() == alert.type()
                    && previous.triggerId() == alert.triggerId();
            long startedAt = sameAnimation ? previous.startedAt() : lastUpdate;
            ALERTS.put(alert.mobId(), new MobAlert(alert.entityId(), alert.type(), alert.color(), alert.progress(), startedAt, lastUpdate, alert.triggerId()));
        }
        ALERTS.asMap().entrySet().removeIf(entry -> !received.contains(entry.getKey())
                && !shouldRetainMissingAlert(entry.getValue().lastSeenAt(), lastUpdate));
        refreshAlertSnapshot();
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.STEALTH_DETECTION)
                && !lastDetectedSnapshotEntityIds.equals(detectedEntityIds)) {
            lastDetectedSnapshotEntityIds = Set.copyOf(detectedEntityIds);
            String detectedEntities = detectedEntityIds.stream().sorted()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(","));
            SoundAttractClient.LOGGER.info(
                    "[MobAlertDebug] Detected snapshot changed: count={}, entityIds={}, retainedClientAlerts={}",
                    detectedEntityIds.size(), detectedEntities, ALERTS.asMap().size());
        }
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.STEALTH_DETECTION)
                && now - lastAlertDiagnosticAt >= 2500L) {
            lastAlertDiagnosticAt = now;
            long detectedCount = message.alerts().stream().filter(alert -> alert != null && alert.type() == 2).count();
            String detectedEntities = message.alerts().stream()
                    .filter(alert -> alert != null && alert.type() == 2)
                    .map(alert -> Integer.toString(alert.entityId()))
                    .collect(java.util.stream.Collectors.joining(","));
            SoundAttractClient.LOGGER.info(
                    "[MobAlertDebug] Snapshot received: total={}, detected={}, detectedEntityIds={}, retainedClientAlerts={}",
                    message.alerts().size(), detectedCount, detectedEntities, ALERTS.asMap().size());
        }
    }

    static boolean shouldRetainMissingAlert(long lastSeenAt, long now) {
        return now - lastSeenAt <= MISSING_ALERT_GRACE_MS;
    }

    public static synchronized float visibility() {
        return visibility;
    }

    public static synchronized float noise() {
        return noise;
    }

    public static synchronized int scent() {
        return scent;
    }

    public static synchronized boolean windshielded() {
        return windshielded;
    }

    public static synchronized void clear() {
        visibility = 0.0f;
        noise = 0.0f;
        scent = 0;
        windshielded = false;
        lastUpdate = 0L;
        lastAlertDiagnosticAt = 0L;
        lastDetectedSnapshotEntityIds = Set.of();
        ALERTS.invalidateAll();
        alertSnapshot = Set.of();
    }

    public static synchronized MobAlert alert(UUID id) {
        return id == null ? null : ALERTS.getIfPresent(id);
    }

    public static synchronized Set<Map.Entry<UUID, MobAlert>> alerts() {
        ALERTS.cleanUp();


        if (alertSnapshot.size() != ALERTS.asMap().size()) {
            refreshAlertSnapshot();
        }
        return alertSnapshot;
    }

    public record MobAlert(int entityId, int type, int color, float progress, long startedAt, long lastSeenAt, long triggerId) {
        public MobAlert(int entityId, int type, int color, float progress, long startedAt, long lastSeenAt) {
            this(entityId, type, color, progress, startedAt, lastSeenAt, 0L);
        }
    }
}
