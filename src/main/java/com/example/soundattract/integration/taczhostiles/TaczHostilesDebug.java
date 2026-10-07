package com.example.soundattract.integration.taczhostiles;

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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Narrow, opt-in diagnostics for the optional TACZ Hostiles AI mixin. */
public final class TaczHostilesDebug {
    private static final AtomicBoolean MIXIN_APPLIED_REPORTED = new AtomicBoolean();
    private static final AtomicBoolean HOOK_REACHED_REPORTED = new AtomicBoolean();
    private static volatile boolean mixinApplied;
    private static final Cache<UUID, Long> LAST_DECISION_LOG = Caffeine.newBuilder()
            .maximumSize(4096)
            .expireAfterAccess(Duration.ofMinutes(2))
            .build();

    private TaczHostilesDebug() {
    }

    public static boolean isEnabled() {
        return DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.TACZ_HOSTILES);
    }

    public static void reportStartupStatus() {
        if (!isEnabled()) {
            return;
        }
        SoundAttractMod.LOGGER.info("[TACZ Hostiles] Startup status: modLoaded={} debugEnabled=true mixinPluginLoaded={} applied={}",
                ModList.get().isLoaded("tacz_hostiles"), TaczMixinBootstrapStatus.isHostilesPluginLoaded(), mixinApplied);
    }

    public static void logMixinApplied(String mixinClassName) {
        mixinApplied = true;
        if (isEnabled() && MIXIN_APPLIED_REPORTED.compareAndSet(false, true)) {
            SoundAttractMod.LOGGER.info("[TACZ Hostiles] Optional shoot-AI mixin applied to {}.", mixinClassName);
        }
    }

    public static void logHookReached(Mob mob) {
        if (mob != null && isEnabled() && HOOK_REACHED_REPORTED.compareAndSet(false, true)) {
            SoundAttractMod.LOGGER.info("[TACZ Hostiles] Shoot-AI hook is running (first mob type: {}).", mob.getType().getDescriptionId());
        }
    }

    public static void logDecision(Mob mob, LivingEntity target, String hook, boolean suppressed) {
        if (mob == null || !isEnabled()) {
            return;
        }
        long gameTime = mob.level().getGameTime();
        UUID mobId = mob.getUUID();
        Long previousLogTick = LAST_DECISION_LOG.getIfPresent(mobId);
        if (previousLogTick != null && gameTime - previousLogTick < 40L) {
            return;
        }
        LAST_DECISION_LOG.put(mobId, gameTime);

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
        SoundAttractMod.LOGGER.info("[TACZ Hostiles] Shoot-AI decision: hook={} mob={} type={} target={} targetType={} suppressed={} reason={} blind={} smokeEnabled={} smokeBlocked={} stealthEnabled={} tick={}",
                hook, mobId, mob.getType(), targetId, targetType, suppressed, reason, flashbangBlind, smokeEnabled, smokeBlocked,
                SoundAttractConfig.COMMON.enableStealthMechanics.get(), gameTime);
    }

}
