package com.example.soundattract;

import com.example.soundattract.config.DebugLoggingConfig;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.example.soundattract.config.ConfigHelper;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.config.separate.*;
import com.example.soundattract.enchantment.ModEnchantments;
import com.example.soundattract.loot.ModLootModifiers;
import com.example.soundattract.integration.voicechat.PlasmoVoiceBootstrap;
import com.example.soundattract.integration.vanilla.VanillaIntegrationEvents;
import com.example.soundattract.event.FovEvents;
import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.event.client.SoundAttractClientEvents;
import com.example.soundattract.network.SoundAttractNetwork;
import com.example.soundattract.quantified.QuantifiedIntegration;
import com.example.soundattract.worker.WorkSchedulerManager;
import com.example.soundattract.camo.CamouflageCapability;
import com.example.soundattract.integration.spore.BiomassSoundHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraft.resources.ResourceLocation;

@Mod(SoundAttractMod.MOD_ID)
public class SoundAttractMod {
    public static final String MOD_ID = "soundattract";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SoundAttractMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModEnchantments.register(modEventBus);
        ModLootModifiers.register(modEventBus);
        com.example.soundattract.registration.ModParticles.register(modEventBus);
        com.example.soundattract.registration.ModSounds.register(modEventBus);
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(SoundAttractMod::onClientSetup);

        ConfigHelper.register(ModLoadingContext.get());

        MinecraftForge.EVENT_BUS.register(new FovEvents());
        MinecraftForge.EVENT_BUS.register(new StealthDetectionEvents());
        MinecraftForge.EVENT_BUS.register(new PlasmoVoiceBootstrap());
        MinecraftForge.EVENT_BUS.register(new VanillaIntegrationEvents());
        MinecraftForge.EVENT_BUS.register(new com.example.soundattract.event.ScentEvents());
        MinecraftForge.EVENT_BUS.register(new com.example.soundattract.event.FloorCreekEvents());
        MinecraftForge.EVENT_BUS.register(new com.example.soundattract.event.ArrowInvestigationEvents());
        MinecraftForge.EVENT_BUS.register(this);

        modEventBus.addListener(this::onRegister);

        MinecraftForge.EVENT_BUS.addGenericListener(net.minecraft.world.entity.Entity.class, this::attachPlayerCapabilities);

        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::onConfigLoading);
        modEventBus.addListener(this::onConfigReloading);
    }

    private void onRegister(net.minecraftforge.registries.RegisterEvent event) {
        if (event.getRegistryKey().equals(net.minecraft.core.registries.Registries.ITEM)) {
            com.example.soundattract.integration.immersive_melodies.ImmersiveMelodiesIntegration.init();
        }
    }

    private void registerCapabilities(net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent event) {
        event.register(com.example.soundattract.scents.ScentManager.class);
        event.register(CamouflageCapability.class);
    }

    private void attachPlayerCapabilities(AttachCapabilitiesEvent<net.minecraft.world.entity.Entity> event) {
        if (event.getObject() instanceof net.minecraft.world.entity.LivingEntity) {
            ResourceLocation id = ResourceLocation.tryBuild(MOD_ID, "camouflage");
            event.addCapability(id, new CamouflageCapability.Provider());
        }
    }

    public void onConfigLoading(final net.minecraftforge.fml.event.config.ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == GeneralConfig.SPEC ||
            event.getConfig().getSpec() == StealthConfig.SPEC ||
            event.getConfig().getSpec() == GunsConfig.SPEC ||
            event.getConfig().getSpec() == VoiceConfig.SPEC ||
            event.getConfig().getSpec() == IntegrationConfig.SPEC ||
            event.getConfig().getSpec() == ScentConfig.SPEC ||
            event.getConfig().getSpec() == PerformanceConfig.SPEC ||
            event.getConfig().getSpec() == RaidConfig.SPEC ||
            event.getConfig().getSpec() == PathfindingConfig.SPEC ||
            event.getConfig().getSpec() == SoundAttractConfig.SERVER_SPEC) {
            SoundAttractConfig.bakeConfig();
            SoundAttractConfig.parseAndCachePlayerActionConfig();
        }
    }

    public void onConfigReloading(final net.minecraftforge.fml.event.config.ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == GeneralConfig.SPEC ||
            event.getConfig().getSpec() == StealthConfig.SPEC ||
            event.getConfig().getSpec() == GunsConfig.SPEC ||
            event.getConfig().getSpec() == VoiceConfig.SPEC ||
            event.getConfig().getSpec() == IntegrationConfig.SPEC ||
            event.getConfig().getSpec() == ScentConfig.SPEC ||
            event.getConfig().getSpec() == PerformanceConfig.SPEC ||
            event.getConfig().getSpec() == RaidConfig.SPEC ||
            event.getConfig().getSpec() == PathfindingConfig.SPEC ||
            event.getConfig().getSpec() == SoundAttractConfig.SERVER_SPEC) {
            SoundAttractConfig.bakeConfig();
            SoundAttractConfig.parseAndCachePlayerActionConfig();
        }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onServerAboutToStart(net.minecraftforge.event.server.ServerAboutToStartEvent event) {
        SoundAttractConfig.migrateCommonToServerIfNeeded();
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SoundAttractConfig.bakeConfig();
            QuantifiedIntegration.bootstrap();
            WorkSchedulerManager.refresh();
            SoundAttractNetwork.register();
            com.example.soundattract.integration.csgrenades.CsGrenadesIntegration.register();
            com.example.soundattract.integration.customnpcs.CustomNpcsStealthTargetBridge.registerIfPresent();
            registerHotBathIntegrationIfPresent();
            registerToughAsNailsIntegrationIfPresent();
            com.example.soundattract.integration.spoiled.SpoiledScentIntegration.logStatus();

            if (ModList.get().isLoaded("spore")) {
                MinecraftForge.EVENT_BUS.register(BiomassSoundHandler.class);
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.SPORE)) {
                    LOGGER.info("Spore mod detected. Registered BiomassSoundHandler.");
                }
            }
        });
        event.enqueueWork(this::handleTaczIntegration);
    }

    private static void registerHotBathIntegrationIfPresent() {
        if (!ModList.get().isLoaded("hotbath") || !SoundAttractConfig.COMMON.enableHotBathIntegration.get()) {
            return;
        }
        try {
            Class<?> integration = Class.forName("com.example.soundattract.integration.hotbath.HotBathIntegration");
            integration.getMethod("registerIfPresent", IEventBus.class).invoke(null, MinecraftForge.EVENT_BUS);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.error("Failed to register HotBath integration. HotBath support will be disabled.", e);
        }
    }

    private static void registerToughAsNailsIntegrationIfPresent() {
        if (!ModList.get().isLoaded("toughasnails") || !SoundAttractConfig.COMMON.enableToughAsNailsIntegration.get()) {
            return;
        }
        try {
            Class<?> integration = Class.forName("com.example.soundattract.integration.toughasnails.ToughAsNailsIntegration");
            integration.getMethod("registerIfPresent", IEventBus.class).invoke(null, MinecraftForge.EVENT_BUS);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.error("Failed to register ToughAsNails integration. ToughAsNails support will be disabled.", e);
        }
    }

    private void handleTaczIntegration() {
        if (ModList.get().isLoaded("tacz") && SoundAttractConfig.TACZ_ENABLED_CACHE) {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.TACZ)) {
                LOGGER.info("Tacz mod found and integration is enabled. Registering event listeners.");
            }
            try {
                com.example.soundattract.integration.tacz.TaczIntegrationHandler.register();
            } catch (NoClassDefFoundError e) {
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.TACZ)) {
                    LOGGER.error("Failed to register Tacz integration events. The Tacz API might be missing or has changed.", e);
                }
            }
        } else {
            if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.TACZ)) {
                LOGGER.info("Tacz integration is disabled or mod not found.");
            }
        }
    }

    private static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (ModList.get().isLoaded("voicechat")) {
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.VOICE_CHAT)) {
                    LOGGER.info("[SoundAttractMod] Registering VoiceChat integration on client setup");
                }
                SoundAttractClientEvents.registerVoiceChatIntegration();
            } else {
                if (DebugLoggingConfig.isEnabled(DebugLoggingConfig.Feature.VOICE_CHAT)) {
                    LOGGER.info("[SoundAttractMod] VoiceChat mod not present; skipping integration");
                }
            }
        });
    }
}
