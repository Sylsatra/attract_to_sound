package com.example.soundattract.integration.hotbath;

import com.crabmod.hotbath.custom_fluid.CustomFluidBlockEntity;
import com.crabmod.hotbath.custom_fluid.CustomFluidDefinition;
import com.crabmod.hotbath.dirtiness.DirtinessHandler;
import com.example.soundattract.camo.CamoAttachments;
import com.example.soundattract.camo.CamouflageCapability;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Map;

public final class HotBathIntegration {
    private static final String HOTBATH = "hotbath";
    private static final Map<String, Identifier> BLOCK_TO_FLUID = Map.of(
            "hot_water_block", hotBathId("hot_water_fluid"),
            "milk_bath_block", hotBathId("milk_bath_fluid"),
            "herbal_bath_block", hotBathId("herbal_bath_fluid"),
            "honey_bath_block", hotBathId("honey_bath_fluid"),
            "peony_bath_block", hotBathId("peony_bath_fluid"),
            "rose_bath_block", hotBathId("rose_bath_fluid"));
    private static final Map<String, Identifier> ITEM_TO_FLUID = Map.ofEntries(
            Map.entry("hot_water_bucket", hotBathId("hot_water_fluid")),
            Map.entry("milk_bath_bucket", hotBathId("milk_bath_fluid")),
            Map.entry("herbal_bath_bucket", hotBathId("herbal_bath_fluid")),
            Map.entry("honey_bath_bucket", hotBathId("honey_bath_fluid")),
            Map.entry("peony_bath_bucket", hotBathId("peony_bath_fluid")),
            Map.entry("rose_bath_bucket", hotBathId("rose_bath_fluid")),
            Map.entry("hot_water_bottle", hotBathId("hot_water_fluid")),
            Map.entry("milk_bath_bottle", hotBathId("milk_bath_fluid")),
            Map.entry("herbal_bath_bottle", hotBathId("herbal_bath_fluid")),
            Map.entry("honey_bath_bottle", hotBathId("honey_bath_fluid")),
            Map.entry("peony_bath_bottle", hotBathId("peony_bath_fluid")),
            Map.entry("rose_bath_bottle", hotBathId("rose_bath_fluid")),
            Map.entry("splash_hot_water_bottle", hotBathId("hot_water_fluid")),
            Map.entry("splash_milk_bath_bottle", hotBathId("milk_bath_fluid")),
            Map.entry("splash_herbal_bath_bottle", hotBathId("herbal_bath_fluid")),
            Map.entry("splash_honey_bath_bottle", hotBathId("honey_bath_fluid")),
            Map.entry("splash_peony_bath_bottle", hotBathId("peony_bath_fluid")),
            Map.entry("splash_rose_bath_bottle", hotBathId("rose_bath_fluid")));
    private static int cursor;

    private HotBathIntegration() {
    }

    public static boolean isEnabledAndLoaded() {
        return ModList.get().isLoaded(HOTBATH)
                && SoundAttractConfig.COMMON != null
                && SoundAttractConfig.COMMON.enableHotBathIntegration.get();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!isEnabledAndLoaded()) return;
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
    public static void onServerStopped(ServerStoppedEvent event) {
        cursor = 0;
        HotBathScentStateCache.clear();
    }

    public static void applySplashWash(ThrowableItemProjectile projectile, Identifier fluidId) {
        if (projectile == null || projectile.level().isClientSide() || !isEnabledAndLoaded()) return;
        Identifier effectiveFluid = fluidId != null ? fluidId : fluidIdFromStack(projectile.getItem());
        if (effectiveFluid == null) return;
        HotBathConfigParser.CamoWashRate rate = HotBathScentRules.splashWashRate(effectiveFluid);
        if (rate == null) return;

        AABB aabb = projectile.getBoundingBox().inflate(4.0D, 2.0D, 4.0D);
        List<LivingEntity> entities = projectile.level().getEntitiesOfClass(LivingEntity.class, aabb);
        for (LivingEntity entity : entities) {
            if (!entity.isAffectedByPotions() || projectile.distanceToSqr(entity) >= 16.0D) continue;
            CamouflageCapability camo = entity.getData(CamoAttachments.CAMOUFLAGE);
            camo.applyCamoWash(entity, rate.skinRate(), rate.armorRate(), true);
            if (entity instanceof ServerPlayer player) {
                applyAroma(player, effectiveFluid, player.level().getGameTime());
            }
        }
    }

    private static void updatePlayer(ServerPlayer player) {
        if (player == null || player.isRemoved()) return;
        long now = player.level().getGameTime();
        double dirtyMultiplier = dirtyMultiplier(player);
        Identifier fluidId = currentHotBathFluid(player);
        Identifier aromaFluid = null;
        double aromaMultiplier = 1.0;
        long aromaExpiresAt = 0L;

        if (fluidId != null) {
            HotBathConfigParser.FluidModifier modifier = HotBathScentRules.scentModifier(fluidId);
            if (modifier != null) {
                aromaFluid = fluidId;
                aromaMultiplier = modifier.scentMultiplier() * HotBathScentRules.biomeMultiplier(fluidId, player.level(), player.blockPosition());
                int duration = modifier.durationTicks();
                if (duration <= 0) {
                    duration = SoundAttractConfig.COMMON.hotBathDefaultBathAromaDurationTicks.get();
                }
                aromaExpiresAt = now + Math.max(0, duration);
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

    private static void applyAroma(ServerPlayer player, Identifier fluidId, long now) {
        HotBathConfigParser.FluidModifier modifier = HotBathScentRules.scentModifier(fluidId);
        if (modifier == null) return;
        int duration = modifier.durationTicks();
        if (duration <= 0) {
            duration = SoundAttractConfig.COMMON.hotBathDefaultBathAromaDurationTicks.get();
        }
        double aroma = modifier.scentMultiplier() * HotBathScentRules.biomeMultiplier(fluidId, player.level(), player.blockPosition());
        double dirty = dirtyMultiplier(player);
        HotBathScentStateCache.update(player.getUUID(), new HotBathScentStateCache.State(dirty, fluidId, aroma, now + Math.max(0, duration)));
    }

    private static void applyCamoWash(ServerPlayer player, Identifier fluidId) {
        HotBathConfigParser.CamoWashRate rate = HotBathScentRules.camoWashRate(fluidId);
        if (rate == null) return;
        CamouflageCapability camo = player.getData(CamoAttachments.CAMOUFLAGE);
        camo.applyCamoWash(player, rate.skinRate(), rate.armorRate(), true);
    }

    private static double dirtyMultiplier(ServerPlayer player) {
        double dirtiness = Math.max(0.0D, Math.min(1.0D, DirtinessHandler.getDirtiness(player)));
        double maxMultiplier = Math.max(0.0D, SoundAttractConfig.COMMON.hotBathMaxDirtyScentMultiplier.get());
        return 1.0D + (maxMultiplier - 1.0D) * dirtiness;
    }

    private static Identifier currentHotBathFluid(ServerPlayer player) {
        Identifier atFeet = fluidAt(player, player.blockPosition());
        if (atFeet != null) return atFeet;
        return fluidAt(player, BlockPos.containing(player.getEyePosition()));
    }

    private static Identifier fluidAt(ServerPlayer player, BlockPos pos) {
        if (pos == null || !player.level().isLoaded(pos)) return null;

        if (player.level().getBlockEntity(pos) instanceof CustomFluidBlockEntity customFluidBlock) {
            return customFluidBlock.getFluidDefinition()
                    .map(CustomFluidDefinition::id)
                    .orElseGet(customFluidBlock::getFluidId);
        }

        Block block = player.level().getBlockState(pos).getBlock();
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        if (blockId != null && HOTBATH.equals(blockId.getNamespace())) {
            Identifier fluidId = BLOCK_TO_FLUID.get(blockId.getPath());
            if (fluidId != null) {
                return fluidId;
            }
        }

        var fluidState = player.level().getFluidState(pos);
        if (fluidState.isEmpty()) return null;
        Fluid fluid = fluidState.getType();
        Identifier fluidId = BuiltInRegistries.FLUID.getKey(fluid);
        return fluidId != null && HOTBATH.equals(fluidId.getNamespace()) ? normalizeFluidId(fluidId) : null;
    }

    public static Identifier fluidIdFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || !HOTBATH.equals(itemId.getNamespace())) return null;
        return ITEM_TO_FLUID.get(itemId.getPath());
    }

    private static Identifier normalizeFluidId(Identifier id) {
        if (id == null || !HOTBATH.equals(id.getNamespace())) return null;
        String path = id.getPath();
        if (path.endsWith("_flowing")) {
            return hotBathId(path.substring(0, path.length() - "_flowing".length()) + "_fluid");
        }
        return id;
    }

    private static Identifier hotBathId(String path) {
        return Identifier.fromNamespaceAndPath(HOTBATH, path);
    }
}
