package com.example.soundattract;

import com.example.soundattract.config.ConfigHelper;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.enchantment.ModEnchantments;
import com.example.soundattract.loot.ModLootModifiers;
import com.example.soundattract.network.SoundAttractNetwork;
import com.example.soundattract.registration.ModSounds;
import com.example.soundattract.worker.WorkSchedulerManager;
import com.example.soundattract.quantified.QuantifiedIntegration;
import com.example.soundattract.integration.vanilla.VanillaIntegrationEvents;
import com.example.soundattract.integration.immersive_melodies.ImmersiveMelodiesIntegration;
import com.example.soundattract.integration.voicechat.PlasmoVoiceBootstrap;
import com.example.soundattract.integration.toughasnails.ToughAsNailsIntegration;
import com.example.soundattract.camo.CamoEvents;
import com.example.soundattract.integration.scent.ScentCapabilityHandler;
import com.example.soundattract.event.ArrowInvestigationEvents;
import com.example.soundattract.event.ArrowScentEvents;
import com.example.soundattract.event.FovEvents;
import com.example.soundattract.event.SoundAttractFabricEvents;
import fuzs.forgeconfigapiport.fabric.api.forge.v4.ForgeConfigRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Soundattract implements ModInitializer {
    public static final String MOD_ID = "soundattract";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static MinecraftServer currentServer;

    public static MinecraftServer getServer() {
        return currentServer;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Sound Attract mod...");

        registerConfigs();
        registerContent();
        com.example.soundattract.reload.DataDrivenReloadRegistrar.register();
        registerEvents();
        registerNetwork();
        com.example.soundattract.event.AwarenessServerEvents.register();
        com.example.soundattract.event.FromTheFogEquipmentSyncEvents.register();
        com.example.soundattract.event.LivingWallFabricEvents.register();
        if (ModChecker.isLoaded("toughasnails")) {
            ToughAsNailsIntegration.register();
        }
        PlasmoVoiceBootstrap.init();

        LOGGER.info("Sound Attract mod initialized successfully!");
    }

    private void registerConfigs() {
        ConfigHelper.register(ForgeConfigRegistry.INSTANCE);



        com.example.soundattract.event.config.ConfigReloadListener.register();
        com.example.soundattract.event.config.ConfigReloadListener.onConfigLoad();

        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
    }

    private void registerContent() {
        ModEnchantments.register();
        ModSounds.register();
        com.example.soundattract.registration.ModParticles.register();
        ModLootModifiers.register();
    }

    private void registerEvents() {
        SoundAttractFabricEvents.register();
        VanillaIntegrationEvents.register();
        FovEvents.register();
        CamoEvents.register();
        ArrowScentEvents.register();
        ArrowInvestigationEvents.register();
        ScentCapabilityHandler.register();
    }

    private void registerNetwork() {
        SoundAttractNetwork.register();
        QuantifiedIntegration.bootstrap();
    }

    private void onServerStarting(MinecraftServer server) {
        currentServer = server;
        SoundAttractConfig.migrateCommonToServerIfNeeded();
        SoundAttractConfig.bakeConfig();
        WorkSchedulerManager.refresh();

        if (SoundAttractConfig.TACZ_ENABLED_CACHE) {
            LOGGER.info("Tacz mod found and integration is enabled.");
            try {
                com.example.soundattract.integration.tacz.TaczIntegrationHandler.register();
            } catch (NoClassDefFoundError e) {
                LOGGER.error("Failed to register Tacz integration events.", e);
            }
        }

        ImmersiveMelodiesIntegration.init();
    }
}
