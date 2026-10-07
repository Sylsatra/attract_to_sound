package com.example.soundattract.integration.hotbath;

import com.crabmod.hotbath.custom_fluid.CustomFluidBlockEntity;
import com.crabmod.hotbath.custom_fluid.CustomFluidDefinition;
import com.crabmod.hotbath.dirtiness.DirtinessHandler;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.camo.CamouflageCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.UUID;

public final class HotBathIntegration {
    private static int cursor;

    private HotBathIntegration() {}

    public static boolean isEnabledAndLoaded() {
        return ModList.get().isLoaded("hotbath")
                && SoundAttractConfig.COMMON != null
                && SoundAttractConfig.COMMON.enableHotBathIntegration.get();
    }

    public static void registerIfPresent(net.minecraftforge.eventbus.api.IEventBus eventBus) {
        if (isEnabledAndLoaded()) {
            eventBus.register(HotBathIntegration.class);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isEnabledAndLoaded()) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;

        int interval = Math.max(1, SoundAttractConfig.COMMON.hotBathPollIntervalTicks.get());
        int perTick = Math.max(1, (players.size() + interval - 1) / interval);
        for (int i = 0; i < perTick && !players.isEmpty(); i++) {
            if (cursor >= players.size()) cursor = 0;
            ServerPlayer player = players.get(cursor++);
            updatePlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HotBathScentStateCache.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        cursor = 0;
        HotBathScentStateCache.clear();
    }

    public static void applySplashWash(ThrowableItemProjectile projectile, ResourceLocation fluidId) {
        if (projectile == null || projectile.level().isClientSide || !isEnabledAndLoaded()) return;
        ResourceLocation effectiveFluid = fluidId != null ? fluidId : fluidIdFromStack(projectile.getItem());
        if (effectiveFluid == null) return;
        HotBathConfigParser.CamoWashRate rate = HotBathScentRules.splashWashRate(effectiveFluid);
        if (rate == null) return;

        AABB aabb = projectile.getBoundingBox().inflate(4.0D, 2.0D, 4.0D);
        List<LivingEntity> entities = projectile.level().getEntitiesOfClass(LivingEntity.class, aabb);
        for (LivingEntity entity : entities) {
            if (!entity.isAffectedByPotions() || projectile.distanceToSqr(entity) >= 16.0D) continue;
            entity.getCapability(CamouflageCapability.INSTANCE).ifPresent(camo ->
                    camo.applyCamoWash(entity, rate.skinRate(), rate.armorRate(), true));
            if (entity instanceof ServerPlayer player) {
                applyAroma(player, effectiveFluid, player.level().getGameTime());
            }
        }
    }

    private static void updatePlayer(ServerPlayer player) {
        if (player == null || player.isRemoved()) return;
        long now = player.level().getGameTime();
        double dirtyMultiplier = dirtyMultiplier(player);
        ResourceLocation fluidId = currentHotBathFluid(player);
        ResourceLocation aromaFluid = null;
        double aromaMultiplier = 1.0;
        long aromaExpiresAt = 0L;

        if (fluidId != null) {
            HotBathConfigParser.FluidModifier modifier = HotBathScentRules.scentModifier(fluidId);
            if (modifier != null) {
                aromaFluid = fluidId;
                aromaMultiplier = modifier.scentMultiplier() * HotBathScentRules.biomeMultiplier(fluidId, player.level(), player.blockPosition());
                aromaExpiresAt = now + modifier.durationTicks();
            }
            applyCamoWash(player, fluidId);
        } else {
            HotBathScentStateCache.State previous = HotBathScentStateCache.get(player.getUUID());
            if (now <= previous.aromaExpiresAt()) {
                aromaFluid = previous.aromaFluidId();
                aromaMultiplier = previous.aromaMultiplier();
                aromaExpiresAt = previous.aromaExpiresAt();
            }
        }

        HotBathScentStateCache.update(player.getUUID(), new HotBathScentStateCache.State(dirtyMultiplier, aromaFluid, aromaMultiplier, aromaExpiresAt));
    }

    private static void applyAroma(ServerPlayer player, ResourceLocation fluidId, long now) {
        HotBathConfigParser.FluidModifier modifier = HotBathScentRules.scentModifier(fluidId);
        if (modifier == null) return;
        double aroma = modifier.scentMultiplier() * HotBathScentRules.biomeMultiplier(fluidId, player.level(), player.blockPosition());
        double dirty = dirtyMultiplier(player);
        HotBathScentStateCache.update(player.getUUID(), new HotBathScentStateCache.State(dirty, fluidId, aroma, now + modifier.durationTicks()));
    }

    private static void applyCamoWash(ServerPlayer player, ResourceLocation fluidId) {
        HotBathConfigParser.CamoWashRate rate = HotBathScentRules.camoWashRate(fluidId);
        if (rate == null) return;
        player.getCapability(CamouflageCapability.INSTANCE).ifPresent(camo ->
                camo.applyCamoWash(player, rate.skinRate(), rate.armorRate(), true));
    }

    private static double dirtyMultiplier(ServerPlayer player) {
        double dirtiness = Math.max(0.0, Math.min(1.0, DirtinessHandler.getDirtiness(player)));
        double max = SoundAttractConfig.COMMON.hotBathMaxDirtyScentMultiplier.get();
        return 1.0 + (Math.max(0.0, max) - 1.0) * dirtiness;
    }

    private static ResourceLocation currentHotBathFluid(ServerPlayer player) {
        ResourceLocation atFeet = fluidAt(player, player.blockPosition());
        if (atFeet != null) return atFeet;
        return fluidAt(player, BlockPos.containing(player.getEyePosition()));
    }

    private static ResourceLocation fluidAt(ServerPlayer player, BlockPos pos) {
        if (pos == null || !player.level().isLoaded(pos)) return null;
        if (player.level().getBlockEntity(pos) instanceof CustomFluidBlockEntity custom) {
            return custom.getFluidDefinition().map(CustomFluidDefinition::id).orElse(custom.getFluidId());
        }
        var fluidState = player.level().getFluidState(pos);
        if (fluidState.isEmpty()) return null;
        ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluidState.getType());
        return id != null && "hotbath".equals(id.getNamespace()) ? id : null;
    }

    public static ResourceLocation fluidIdFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null || !"hotbath".equals(itemId.getNamespace())) return null;
        return switch (itemId.getPath()) {
            case "splash_hot_water_bottle", "hot_water_bottle" -> ResourceLocation.tryParse("hotbath:hot_water_fluid");
            case "splash_milk_bath_bottle", "milk_bath_bottle" -> ResourceLocation.tryParse("hotbath:milk_bath_fluid");
            case "splash_herbal_bath_bottle", "herbal_bath_bottle" -> ResourceLocation.tryParse("hotbath:herbal_bath_fluid");
            case "splash_honey_bath_bottle", "honey_bath_bottle" -> ResourceLocation.tryParse("hotbath:honey_bath_fluid");
            case "splash_peony_bath_bottle", "peony_bath_bottle" -> ResourceLocation.tryParse("hotbath:peony_bath_fluid");
            case "splash_rose_bath_bottle", "rose_bath_bottle" -> ResourceLocation.tryParse("hotbath:rose_bath_fluid");
            default -> null;
        };
    }
}