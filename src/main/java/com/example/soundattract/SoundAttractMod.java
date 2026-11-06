package com.example.soundattract;

import com.example.soundattract.camo.CamoAttachments;
import com.example.soundattract.config.ConfigHelper;
import com.example.soundattract.config.DebugLoggingConfig;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.config.separate.GeneralConfig;
import com.example.soundattract.config.separate.GunsConfig;
import com.example.soundattract.config.separate.IntegrationConfig;
import com.example.soundattract.config.separate.LivingWallConfig;
import com.example.soundattract.config.separate.PathfindingConfig;
import com.example.soundattract.config.separate.PerformanceConfig;
import com.example.soundattract.config.separate.RaidConfig;
import com.example.soundattract.config.separate.ScentConfig;
import com.example.soundattract.config.separate.StealthConfig;
import com.example.soundattract.config.separate.VoiceConfig;
import com.example.soundattract.enchantment.ModEnchantments;
import com.example.soundattract.event.ArrowInvestigationEvents;
import com.example.soundattract.event.FloorCreekEvents;
import com.example.soundattract.event.FovEvents;
import com.example.soundattract.event.ScentEvents;
import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.event.client.SoundAttractClientEvents;
import com.example.soundattract.integration.voicechat.PlasmoVoiceBootstrap;
import com.example.soundattract.quantified.QuantifiedIntegration;
import com.example.soundattract.loot.ModLootModifiers;
import com.example.soundattract.network.SoundAttractNetwork;
import com.example.soundattract.worker.WorkSchedulerManager;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

@Mod(SoundAttractMod.MOD_ID)
public class SoundAttractMod {
    public static final String MOD_ID = "soundattract";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SoundAttractMod(IEventBus modEventBus, ModContainer container) {
        ModLootModifiers.register(modEventBus);
        com.example.soundattract.registration.ModSounds.register(modEventBus);
        com.example.soundattract.registration.ModParticles.register(modEventBus);
        CamoAttachments.register(modEventBus);

        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(SoundAttractMod::onClientSetup);
        modEventBus.addListener(this::onConfigLoading);
        modEventBus.addListener(this::onConfigReloading);
        modEventBus.addListener(SoundAttractNetwork::register);

        ConfigHelper.register(container);

        NeoForge.EVENT_BUS.register(new FloorCreekEvents());
        NeoForge.EVENT_BUS.register(new ArrowInvestigationEvents());
        if (ModList.get().isLoaded("plasmovoice")) {
            NeoForge.EVENT_BUS.register(new PlasmoVoiceBootstrap());
        }
        NeoForge.EVENT_BUS.register(this);
    }

    public void onConfigLoading(final ModConfigEvent.Loading event) {
    }

    public void onConfigReloading(final ModConfigEvent.Reloading event) {
        if (isSoundAttractConfig(event) && SoundAttractConfig.isConfigReady()) {
            SoundAttractConfig.bakeConfig();
            SoundAttractConfig.parseAndCachePlayerActionConfig();
        }
    }

    private boolean isSoundAttractConfig(ModConfigEvent event) {
        return event.getConfig().getSpec() == GeneralConfig.SPEC
                || event.getConfig().getSpec() == StealthConfig.SPEC
                || event.getConfig().getSpec() == GunsConfig.SPEC
                || event.getConfig().getSpec() == VoiceConfig.SPEC
                || event.getConfig().getSpec() == IntegrationConfig.SPEC
                || event.getConfig().getSpec() == ScentConfig.SPEC
                || event.getConfig().getSpec() == PerformanceConfig.SPEC
                || event.getConfig().getSpec() == RaidConfig.SPEC
                || event.getConfig().getSpec() == PathfindingConfig.SPEC
                || event.getConfig().getSpec() == LivingWallConfig.SPEC
                || event.getConfig().getSpec() == DebugLoggingConfig.SPEC
                || event.getConfig().getSpec() == SoundAttractConfig.SERVER_SPEC;
    }

    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        SoundAttractConfig.migrateCommonToServerIfNeeded();
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SoundAttractConfig.bakeConfig();
            SoundAttractConfig.parseAndCachePlayerActionConfig();
            SoundAttractConfig.markConfigReady();
            QuantifiedIntegration.bootstrap();
            registerTaczIntegrationIfPresent();
            registerToughAsNailsIntegrationIfPresent();
            WorkSchedulerManager.refresh();
        });
    }

    private static void registerToughAsNailsIntegrationIfPresent() {
        if (!ModList.get().isLoaded("toughasnails") || !SoundAttractConfig.COMMON.enableToughAsNailsIntegration.get()) {
            return;
        }
        try {
            Class<?> integration = Class.forName("com.example.soundattract.integration.toughasnails.ToughAsNailsIntegration");
            integration.getMethod("registerIfPresent", IEventBus.class).invoke(null, NeoForge.EVENT_BUS);
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.error("Failed to register ToughAsNails integration. Support will be disabled.", exception);
        }
    }

    private static void registerTaczIntegrationIfPresent() {
        if (!ModList.get().isLoaded("tacz") || !SoundAttractConfig.COMMON.enableTaczIntegration.get()) {
            return;
        }
        try {
            Class<?> integration = Class.forName("com.example.soundattract.integration.tacz.TaczIntegrationHandler");
            integration.getMethod("register").invoke(null);
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.error("Failed to register TaCZ integration. Support will be disabled.", exception);
        }
    }

    private static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (ModList.get().isLoaded("voicechat")) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    LOGGER.info("[SoundAttractMod] VoiceChat mod is loaded; plugin registration is handled by Simple Voice Chat");
                }
            } else if (SoundAttractConfig.COMMON.debugLogging.get()) {
                LOGGER.info("[SoundAttractMod] VoiceChat mod not present; skipping integration");
            }
        });
    }
}
