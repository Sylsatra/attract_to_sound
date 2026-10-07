package com.example.soundattract.integration.tacznpcs;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.DebugLoggingConfig;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.event.FovEvents;
import com.example.soundattract.integration.csgrenades.CsGrenadesCompat;
import com.example.soundattract.integration.csgrenades.FlashbangEffect;
import com.example.soundattract.mixin.TaczMixinBootstrapStatus;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.minecraftforge.fml.ModList;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TaczNpcDebug {
    private static final AtomicBoolean MIXIN_APPLIED_REPORTED = new AtomicBoolean();
    private static final AtomicBoolean AI_HOOK_REPORTED = new AtomicBoolean();
    private static final AtomicBoolean NULL_TARGET_GUARD_REPORTED = new AtomicBoolean();
    private static final Set<String> APPLIED_MIXINS = ConcurrentHashMap.newKeySet();
    private static final Cache<UUID, Long> LAST_DECISION_LOG = Caffeine.newBuilder()
            .maximumSize(4096)
            .expireAfterAccess(Duration.ofMinutes(2))
            .build();

    private TaczNpcDebug() { }

    public static boolean isIntegrationEnabled() {
        return SoundAttractConfig.COMMON.enableTaczNpcIntegration.get();
    }

    private static boolean isDebugEnabled() {
        return DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.TACZ_NPCS);
    }

    public static void markMixinApplied(String mixinClassName) {
        APPLIED_MIXINS.add(mixinClassName);
    }

    public static void reportStartupStatus() {
        if (!isDebugEnabled()) {
            return;
        }
        SoundAttractMod.LOGGER.info("[TACZ NPCs] Startup status: modLoaded={} integrationEnabled={} debugEnabled=true mixinPluginLoaded={} appliedMixins={}",
                ModList.get().isLoaded("tacz_npc"), isIntegrationEnabled(), TaczMixinBootstrapStatus.isNpcPluginLoaded(),
                new java.util.TreeSet<>(APPLIED_MIXINS));
    }

    public static void logAiHookReached(Mob mob, String hook) {
        if (mob == null || !isDebugEnabled()) {
            return;
        }
        if (MIXIN_APPLIED_REPORTED.compareAndSet(false, true)) {
            SoundAttractMod.LOGGER.info("[TACZ NPCs] Optional mixins applied: {}.", APPLIED_MIXINS);
        }
        if (AI_HOOK_REPORTED.compareAndSet(false, true)) {
            SoundAttractMod.LOGGER.info("[TACZ NPCs] Shoot-AI hook is running at {} (first entity type: {}).",
                    hook, mob.getType().getDescriptionId());
        }
    }

    public static void logNullTargetGuard(Mob mob) {
        if (mob != null && isDebugEnabled() && NULL_TARGET_GUARD_REPORTED.compareAndSet(false, true)) {
            SoundAttractMod.LOGGER.info("[TACZ NPCs] Null-target AI guard ran for {} ({}); canceled the unsafe core-task predicate.",
                    mob.getType().getDescriptionId(), mob.getUUID());
        }
    }

    public static void logDecision(Mob mob, LivingEntity target, String hook, boolean suppressed) {
        if (mob == null || !isDebugEnabled()) {
            return;
        }
        long gameTime = mob.level().getGameTime();
        UUID mobId = mob.getUUID();
        AtomicBoolean shouldLog = new AtomicBoolean();
        LAST_DECISION_LOG.asMap().compute(mobId, (ignored, previousLogTick) -> {
            if (previousLogTick == null || gameTime < previousLogTick || gameTime - previousLogTick >= 40L) {
                shouldLog.set(true);
                return gameTime;
            }
            return previousLogTick;
        });
        if (!shouldLog.get()) {
            return;
        }

        boolean flashbangBlind = CsGrenadesCompat.isLoaded()
                && SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()
                && SoundAttractConfig.COMMON.enableFlashbangBlinding.get()
                && FlashbangEffect.isMobBlind(mob, gameTime);
        boolean smokeEnabled = CsGrenadesCompat.isLoaded()
                && SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()
                && SoundAttractConfig.COMMON.enableSmokeLosBlocking.get();
        boolean smokeBlocked = target != null && FovEvents.isSmokeBlockingLineOfSight(mob, target);
        String reason = suppressed && flashbangBlind ? "flashbang"
                : suppressed && smokeBlocked ? "smoke"
                : suppressed ? "stealth/detection" : "none";
        String targetId = target == null ? "<none>" : target.getUUID().toString();
        String targetType = target == null ? "<none>" : target.getType().toString();
        SoundAttractMod.LOGGER.info("[TACZ NPCs] Shoot-AI decision: hook={} mob={} type={} target={} targetType={} suppressed={} reason={} blind={} smokeEnabled={} smokeBlocked={} stealthEnabled={} tick={}",
                hook, mobId, mob.getType(), targetId, targetType, suppressed, reason, flashbangBlind, smokeEnabled, smokeBlocked,
                SoundAttractConfig.COMMON.enableStealthMechanics.get(), gameTime);
    }

}
