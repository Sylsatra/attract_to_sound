package com.example.soundattract.client;

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
    private static float visibility;
    private static float noise;
    private static int scent;
    private static boolean windshielded;
    private static long lastUpdate;



    private static volatile Set<Map.Entry<UUID, MobAlert>> alertSnapshot = Set.of();
    private static final Cache<UUID, MobAlert> ALERTS = Caffeine.newBuilder()
            .maximumSize(MAX_ALERTS)
            .expireAfterWrite(2500L, TimeUnit.MILLISECONDS)
            .removalListener((UUID ignored, MobAlert ignoredAlert, com.github.benmanes.caffeine.cache.RemovalCause ignoredCause) -> refreshAlertSnapshot())
            .build();

    private AwarenessClientState() {
    }

    private static void refreshAlertSnapshot() {
        alertSnapshot = ALERTS.asMap().entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static synchronized void apply(AwarenessSnapshotMessage message) {
        if (message == null || message.alerts() == null) return;
        visibility = message.visibility();
        noise = message.noise();
        scent = message.scent();
        windshielded = message.windshielded();
        lastUpdate = System.currentTimeMillis();
        Set<UUID> received = new HashSet<>();
        for (AwarenessSnapshotMessage.Alert alert : message.alerts()) {
            if (alert == null || alert.mobId() == null) continue;
            received.add(alert.mobId());
            MobAlert previous = ALERTS.getIfPresent(alert.mobId());
            long startedAt = previous != null && previous.type() == alert.type() ? previous.startedAt() : lastUpdate;
            ALERTS.put(alert.mobId(), new MobAlert(alert.entityId(), alert.type(), alert.color(), alert.progress(), startedAt, lastUpdate));
        }
        ALERTS.asMap().keySet().removeIf(id -> !received.contains(id));
        refreshAlertSnapshot();
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
        ALERTS.invalidateAll();
        alertSnapshot = Set.of();
    }

    public static synchronized MobAlert alert(UUID id) {
        return id == null ? null : ALERTS.getIfPresent(id);
    }

    public static Set<Map.Entry<UUID, MobAlert>> alerts() {
        ALERTS.cleanUp();
        return alertSnapshot;
    }

    public record MobAlert(int entityId, int type, int color, float progress, long startedAt, long lastSeenAt) {
    }
}