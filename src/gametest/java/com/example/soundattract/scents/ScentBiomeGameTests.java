package com.example.soundattract.scents;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.ai.FollowScentGoal;
import com.example.soundattract.event.ScentEvents;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import com.example.soundattract.util.BiomeClimateAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** Verifies biome and weather inputs to scent duration without mutating shared world weather. */
public final class ScentBiomeGameTests {
    private static void setWeather(ServerLevel level, int clearTime, int rainTime,
                                   boolean raining, boolean thundering) {
        var weather = level.getWeatherData();
        weather.setClearWeatherTime(clearTime);
        weather.setRainTime(rainTime);
        weather.setRaining(raining);
        weather.setThunderTime(thundering ? 12000 : 0);
        weather.setThundering(thundering);
        level.setRainLevel(raining ? 1.0F : 0.0F);
        level.setThunderLevel(thundering ? 1.0F : 0.0F);
    }
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void scentDurationUsesBiomeDownfallAndRain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var biome = level.getBiome(pos).value();
        double downfall = BiomeClimateAccess.getDownfall(biome);
        if (Math.abs(downfall - 0.5) < 0.001) {
            helper.fail("Climate fixture downfall equals the former fixed value, so it cannot distinguish the regression");
            return;
        }

        double previousHotThreshold = SoundAttractConfig.COMMON.tempHeavyDecayThreshold.get();
        double previousColdThreshold = SoundAttractConfig.COMMON.tempFreezeThreshold.get();
        double previousHumidityThreshold = SoundAttractConfig.COMMON.humidityPreserveThreshold.get();
        double humidityThreshold = (downfall + 0.5) / 2.0;
        float temperature = biome.getBaseTemperature();
        float expectedBiomeModifier = downfall > humidityThreshold ? 1.3f
                : downfall < 0.2 && temperature > 0.5f ? 0.8f : 1.0f;
        float dryActual;
        float rainyActual;
        float environmentActual;
        try {
            SoundAttractConfig.COMMON.tempHeavyDecayThreshold.set(2.0);
            SoundAttractConfig.COMMON.tempFreezeThreshold.set(-1.0);
            SoundAttractConfig.COMMON.humidityPreserveThreshold.set(humidityThreshold);
            dryActual = ScentBiomeModifier.getDurationModifier(level, pos, false);
            rainyActual = ScentBiomeModifier.getDurationModifier(level, pos, true);
            environmentActual = ScentBiomeModifier.getDurationModifier(level, pos);
        } finally {
            SoundAttractConfig.COMMON.tempHeavyDecayThreshold.set(previousHotThreshold);
            SoundAttractConfig.COMMON.tempFreezeThreshold.set(previousColdThreshold);
            SoundAttractConfig.COMMON.humidityPreserveThreshold.set(previousHumidityThreshold);
        }

        float expectedRainModifier = 1.0f / SoundAttractConfig.COMMON.rainDecayMultiplier.get().floatValue();
        float expectedRainy = expectedBiomeModifier * expectedRainModifier;
        float expectedEnvironment = level.isRainingAt(pos) ? expectedRainy : expectedBiomeModifier;
        if (Math.abs(dryActual - expectedBiomeModifier) > 0.001f
                || Math.abs(rainyActual - expectedRainy) > 0.001f
                || Math.abs(environmentActual - expectedEnvironment) > 0.001f
                || Math.abs(rainyActual - dryActual * expectedRainModifier) > 0.001f) {
            helper.fail("Scent duration failed biome/rain calculation: downfall=" + downfall
                    + ", threshold=" + humidityThreshold + ", expectedBiome=" + expectedBiomeModifier
                    + ", dry=" + dryActual + ", expectedRainy=" + expectedRainy
                    + ", rainy=" + rainyActual + ", environment=" + environmentActual
                    + ", rainingAtPosition=" + level.isRainingAt(pos) + ", biome=" + biome);
            return;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void rainyWalkingScentIsWeakerAndStillAcquiredByConsumer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 9; x++) {
            for (int z = 0; z <= 3; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 2);
            }
        }

        ScentManager manager = ScentManager.getForLevel(level);
        if (manager == null) {
            helper.fail("Production scent component is unavailable in the climate consumer fixture");
            return;
        }
        double previousRainMultiplier = SoundAttractConfig.COMMON.rainDecayMultiplier.get();
        double testRainMultiplier = 2.0;
        UUID owner = UUID.randomUUID();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new com.mojang.authlib.GameProfile(owner, "rainy-scent-gametest"), net.minecraft.server.level.ClientInformation.createDefault()) {
            @Override
            public boolean isCreative() {
                return false;
            }
        };
        ScentNode dryNode = null;
        ScentNode rainyNode = null;
        boolean rainyTargetSelected = false;
        boolean rainyOverrideApplied = false;
        net.minecraft.world.phys.Vec3 consumerTarget = null;
        try {
            SoundAttractConfig.COMMON.rainDecayMultiplier.set(testRainMultiplier);
            player.getAbilities().instabuild = false;
            com.example.soundattract.camo.CamouflageCapability.getCapability(player)
                    .ifPresent(capability -> capability.setLayers(List.of()));
            com.example.soundattract.integration.toughasnails.ToughAsNailsStateCache.remove(owner);
            ScentEvents.onPlayerLoggedOut(player);

            BlockPos dryStart = helper.absolutePos(new BlockPos(1, 1, 1));
            player.setPos(dryStart.getX() + 0.5, dryStart.getY(), dryStart.getZ() + 0.5);
            ScentEvents.onPlayerTickForTesting(player, 1.0, false);
            player.setPos(dryStart.getX() + 5.5, dryStart.getY(), dryStart.getZ() + 0.5);
            ScentEvents.onPlayerTickForTesting(player, 1.0, false);
            // Query stored nodes directly: the moved player may finish in an adjacent chunk,
            // while getNodesInArea also filters by currently loaded chunks in this test fixture.
            dryNode = manager.getAllNodes().stream()
                    .filter(node -> owner.equals(node.getOwnerUUID()))
                    .filter(node -> node.getSourceType() == ScentSourceType.PLAYER_WALK)
                    .findFirst().orElse(null);
            if (dryNode == null) {
                helper.fail("Dry production walking callback did not emit its owned scent node: pos="
                        + player.position() + ", gameMode=" + player.gameMode.getGameModeForPlayer()
                        + ", spectator=" + player.isSpectator() + ", creative=" + player.isCreative()
                        + ", inWater=" + player.isInWater() + ", systemEnabled="
                        + SoundAttractConfig.COMMON.enableScentSystem.get() + ", biomeModifier="
                        + ScentBiomeModifier.getDurationModifierForTesting(level, player.blockPosition(), false)
                        + ", camouflage=" + com.example.soundattract.camo.CamouflageCapability.getCapability(player)
                                .map(com.example.soundattract.camo.CamouflageCapability::getScentBlockFactor).orElse(-1.0)
                        + ", toughAsNails=" + com.example.soundattract.integration.toughasnails.ToughAsNailsStateCache
                                .scentMultiplier(player, level.getGameTime()) + ", totalNodes=" + manager.getAllNodes().size());
                return;
            }

            manager.removeOwnerNodes(owner);
            ScentEvents.onPlayerLoggedOut(player);
            BlockPos rainyStart = helper.absolutePos(new BlockPos(1, 1, 2));
            player.setPos(rainyStart.getX() + 0.5, rainyStart.getY(), rainyStart.getZ() + 0.5);
            ScentEvents.onPlayerTickForTesting(player, 1.0, true);
            player.setPos(rainyStart.getX() + 5.5, rainyStart.getY(), rainyStart.getZ() + 0.5);
            rainyOverrideApplied = true;
            ScentEvents.onPlayerTickForTesting(player, 1.0, true);
            rainyNode = manager.getAllNodes().stream()
                    .filter(node -> owner.equals(node.getOwnerUUID()))
                    .filter(node -> node.getSourceType() == ScentSourceType.PLAYER_WALK)
                    .findFirst().orElse(null);

            Zombie follower = helper.spawn(com.example.soundattract.gametest.GameTestEntityTypes.ZOMBIE, 2, 1, 1);
            // Keep unrelated GameTest scent producers from competing for the same shared level manager.
            ScentManager consumerManager = new ScentManager(level);
            if (rainyNode != null) consumerManager.addScentNode(rainyNode);
            FollowScentGoal goal = new FollowScentGoal(follower, consumerManager);
            rainyTargetSelected = goal.acquireScentForTesting()
                    && rainyNode != null
                    && goal.getTargetPosition() != null
                    && rainyNode.getBlockPos().distSqr(BlockPos.containing(goal.getTargetPosition())) <= 1.0D;
            consumerTarget = goal.getTargetPosition();
        } finally {
            manager.removeOwnerNodes(owner);
            ScentEvents.onPlayerLoggedOut(player);
            SoundAttractConfig.COMMON.rainDecayMultiplier.set(previousRainMultiplier);
        }

        float expectedRatio = 1.0f / (float) testRainMultiplier;
        boolean rainStrengthCorrect = dryNode != null && rainyNode != null
                && rainyOverrideApplied && rainyNode.getStrength() < dryNode.getStrength()
                && Math.abs(rainyNode.getStrength() - dryNode.getStrength() * expectedRatio) <= 0.01f;
        if (!rainStrengthCorrect || !rainyTargetSelected) {
            helper.fail("Rainy walking-scent production/consumption mismatch: dry=" + dryNode
                    + ", rainy=" + rainyNode + ", rainyOverrideApplied=" + rainyOverrideApplied
                    + ", expectedRatio=" + expectedRatio + ", consumerSelectedRainy=" + rainyTargetSelected
                    + ", consumerTarget=" + consumerTarget);
            return;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 120)
    public void rainInputWalkingScentReachesConsumer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean oldScentEnabled = SoundAttractConfig.COMMON.enableScentSystem.get();
        double oldRainMultiplier = SoundAttractConfig.COMMON.rainDecayMultiplier.get();
        UUID owner = UUID.randomUUID();
        ScentManager manager = ScentManager.getForLevel(level);
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new com.mojang.authlib.GameProfile(owner, "real-rain-scent-gametest"), net.minecraft.server.level.ClientInformation.createDefault()) {
            @Override
            public boolean isCreative() {
                return false;
            }
        };
        ScentNode dryNode;
        ScentNode rainyNode;
        boolean consumerAcquired = false;
        Vec3 consumerTarget = null;
        try {
            if (manager == null) {
                helper.fail("Production scent component is unavailable in the real-weather fixture");
                return;
            }
            SoundAttractConfig.COMMON.enableScentSystem.set(true);
            SoundAttractConfig.COMMON.rainDecayMultiplier.set(2.0);
            com.example.soundattract.camo.CamouflageCapability.getCapability(player)
                    .ifPresent(capability -> capability.setLayers(List.of()));
            com.example.soundattract.integration.toughasnails.ToughAsNailsStateCache.remove(owner);

            BlockPos dryStart = helper.absolutePos(new BlockPos(1, 150, 1));
            player.setPos(dryStart.getX() + 0.5, dryStart.getY(), dryStart.getZ() + 0.5);
            ScentEvents.onPlayerLoggedOut(player);
            ScentEvents.onPlayerTickForTesting(player, 1.0, false);
            player.setPos(dryStart.getX() + 5.5, dryStart.getY(), dryStart.getZ() + 0.5);
            ScentEvents.onPlayerTickForTesting(player, 1.0, false);
            dryNode = manager.getAllNodes().stream()
                    .filter(node -> owner.equals(node.getOwnerUUID()))
                    .filter(node -> node.getSourceType() == ScentSourceType.PLAYER_WALK)
                    .findFirst().orElse(null);
            if (dryNode == null) {
                helper.fail("Dry-condition production walking callback did not emit its owned scent node");
                return;
            }

            manager.removeOwnerNodes(owner);
            ScentEvents.onPlayerLoggedOut(player);
            BlockPos rainyStart = helper.absolutePos(new BlockPos(1, 150, 2));
            player.setPos(rainyStart.getX() + 0.5, rainyStart.getY(), rainyStart.getZ() + 0.5);
            ScentEvents.onPlayerLoggedOut(player);
            ScentEvents.onPlayerTickForTesting(player, 1.0, true);
            player.setPos(rainyStart.getX() + 5.5, rainyStart.getY(), rainyStart.getZ() + 0.5);
            ScentEvents.onPlayerTickForTesting(player, 1.0, true);
            rainyNode = manager.getAllNodes().stream()
                    .filter(node -> owner.equals(node.getOwnerUUID()))
                    .filter(node -> node.getSourceType() == ScentSourceType.PLAYER_WALK)
                    .findFirst().orElse(null);

            Zombie follower = helper.spawn(com.example.soundattract.gametest.GameTestEntityTypes.ZOMBIE, 2, 150, 1);
            ScentManager consumerManager = new ScentManager(level);
            if (rainyNode != null) consumerManager.addScentNode(rainyNode);
            FollowScentGoal goal = new FollowScentGoal(follower, consumerManager);
            consumerAcquired = goal.acquireScentForTesting() && goal.getTargetPosition() != null
                    && rainyNode != null
                    && rainyNode.getBlockPos().distSqr(BlockPos.containing(goal.getTargetPosition())) <= 1.0D;
            consumerTarget = goal.getTargetPosition();
        } finally {
            ScentEvents.onPlayerLoggedOut(player);
            if (manager != null) manager.removeOwnerNodes(owner);
            SoundAttractConfig.COMMON.enableScentSystem.set(oldScentEnabled);
            SoundAttractConfig.COMMON.rainDecayMultiplier.set(oldRainMultiplier);
            com.example.soundattract.integration.toughasnails.ToughAsNailsStateCache.remove(owner);
        }
        boolean strengthMatches = dryNode != null && rainyNode != null
                && Math.abs(rainyNode.getStrength() - dryNode.getStrength() * 0.5f) <= 0.01f;
        if (!strengthMatches || !consumerAcquired) {
            helper.fail("Rain input did not attenuate a production walking scent consumed by a follower: "
                    + "dry=" + dryNode + ", rainy=" + rainyNode
                    + ", strengthMatches=" + strengthMatches + ", consumerAcquired=" + consumerAcquired
                    + ", consumerTarget=" + consumerTarget + ", storedNodes="
                    + (manager == null ? -1 : manager.getAllNodes().size()));
            return;
        }
        helper.succeed();
    }

}



