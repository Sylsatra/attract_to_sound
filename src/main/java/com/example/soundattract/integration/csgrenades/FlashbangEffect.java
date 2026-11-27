package com.example.soundattract.integration.csgrenades;

import com.example.soundattract.config.DebugLoggingConfig;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlashbangEffect {
    private static final Map<UUID, BlindEntry> BLIND_MAP = new ConcurrentHashMap<>();
    private static final int MAX_ENTRIES = 4096;

    private record BlindEntry(long expireTick) {}

    public static void blind(Mob mob, long durationTicks, long currentTick) {
        if (mob == null || durationTicks <= 0) {
            return;
        }
        UUID mobId = mob.getUUID();
        if (BLIND_MAP.size() >= MAX_ENTRIES) {
            cleanup(currentTick);
            if (BLIND_MAP.size() >= MAX_ENTRIES && !BLIND_MAP.containsKey(mobId)) {
                return;
            }
        }
        long expireTick = currentTick + durationTicks;
        BLIND_MAP.put(mobId, new BlindEntry(expireTick));
        mob.setTarget(null);
        mob.setLastHurtByMob(null);
        if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
        if (mob.getBrain().hasMemoryValue(MemoryModuleType.ANGRY_AT)) {
            mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
        }
        if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
            SoundAttractMod.LOGGER.debug("[CSGrenades] FlashbangEffect.blind: mob {} blinded until tick {} (duration {}), target cleared", mob.getUUID(), expireTick, durationTicks);
        }
    }

    public static boolean isMobBlind(Mob mob, long currentTick) {
        if (mob == null) {
            return false;
        }
        BlindEntry entry = BLIND_MAP.get(mob.getUUID());
        if (entry == null) {
            return false;
        }
        if (currentTick > entry.expireTick) {
            BLIND_MAP.remove(mob.getUUID());
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.CS_GRENADES)) {
                SoundAttractMod.LOGGER.debug("[CSGrenades] FlashbangEffect.isMobBlind: expired blind for mob {}", mob.getUUID());
            }
            return false;
        }
        return true;
    }

    public static void cleanup(long currentTick) {
        Iterator<Map.Entry<UUID, BlindEntry>> it = BLIND_MAP.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BlindEntry> e = it.next();
            if (currentTick > e.getValue().expireTick) {
                it.remove();
            }
        }
    }

    public static void clear() {
        BLIND_MAP.clear();
    }

    public static boolean hasAnyBlindMobs() {
        return !BLIND_MAP.isEmpty();
    }
}