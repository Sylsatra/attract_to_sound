package com.example.soundattract.integration.tacznpcs;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.DebugLoggingConfig;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.mixin.TaczMixinBootstrapStatus;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.fml.ModList;

public final class TaczNpcDebug {
    private static final Set<String> APPLIED_MIXINS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean HOOK_REPORTED = new AtomicBoolean();
    private TaczNpcDebug() {}
    public static boolean isIntegrationEnabled() { return SoundAttractConfig.COMMON.enableTaczNpcIntegration.get(); }
    private static boolean isDebugEnabled() { return DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.TACZ_NPCS); }
    public static void markMixinApplied(String name) { APPLIED_MIXINS.add(name); }
    public static void reportStartupStatus() {
        if (isDebugEnabled()) SoundAttractMod.LOGGER.info("[TACZ NPCs] Startup: loaded={} enabled={} pluginLoaded={} mixins={}", ModList.get().isLoaded("tacznpcs"), isIntegrationEnabled(), TaczMixinBootstrapStatus.isNpcPluginLoaded(), new java.util.TreeSet<>(APPLIED_MIXINS));
    }
    public static void logAiHookReached(Mob mob, String hook) {
        if (mob != null && isDebugEnabled() && HOOK_REPORTED.compareAndSet(false, true)) SoundAttractMod.LOGGER.info("[TACZ NPCs] AI hook running at {} for {}", hook, mob.getType());
    }
    public static void logNullTargetGuard(Mob mob) {
        if (mob != null && isDebugEnabled()) SoundAttractMod.LOGGER.debug("[TACZ NPCs] Null target guard ran for {}", mob.getType());
    }
    public static void logDecision(Mob mob, LivingEntity target, String hook, boolean suppressed) {
        if (mob != null && isDebugEnabled()) SoundAttractMod.LOGGER.debug("[TACZ NPCs] hook={} mob={} target={} suppressed={}", hook, mob.getUUID(), target == null ? "<none>" : target.getUUID(), suppressed);
    }
}
