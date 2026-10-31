package com.example.soundattract.integration.spore;

import com.Harbinger.Spore.core.Seffects;
import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.Harbinger.Spore.Sentities.BaseEntities.Organoid;
import com.Harbinger.Spore.Sentities.Organoids.*;
import com.Harbinger.Spore.Sentities.Utility.ScentEntity;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.config.separate.IntegrationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

import com.example.soundattract.ai.*;
import com.example.soundattract.integration.spore.goals.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SporeGoalInjectorProxy {

    public static void tryInject(Entity entity) {
        if (entity instanceof Infected infected) {
            injectInfectedGoals(infected);
        }
        if (entity instanceof Proto proto) {
            injectProtoGoals(proto);
        }
        if (entity instanceof Vigil vigil) {
            injectVigilGoals(vigil);
        }
        if (entity instanceof HiveTumor tumor) {
            injectHiveTumorGoals(tumor);
        }
        if (entity instanceof Delusionare delusionare) {
            injectDelusionareGoals(delusionare);
        }
    }

    private static void injectInfectedGoals(Infected infected) {
        boolean hasAttractionGoal = findAttractionGoal(infected) != null;
        boolean hasFollowScentGoal = false;
        boolean hasFollowLeaderGoal = false;
        for (WrappedGoal wg : infected.goalSelector.getAvailableGoals()) {
            Goal goal = wg.getGoal();
            if (goal instanceof FollowScentGoal) {
                hasFollowScentGoal = true;
            }
            if (goal instanceof FollowLeaderGoal) {
                hasFollowLeaderGoal = true;
            }
        }

        double moveSpeed = getAttractionSpeed(infected);
        if (!hasAttractionGoal) {
            if (SoundAttractConfig.COMMON.edgeMobSmartBehavior.get()) {
                infected.goalSelector.addGoal(3, new LeaderAttractionGoal(infected, moveSpeed));
                infected.goalSelector.addGoal(1, new FollowerEdgeRelayGoal(infected, moveSpeed));
            } else {
                infected.goalSelector.addGoal(3, new AttractionGoal(infected, moveSpeed));
            }
        }

        if (!hasFollowLeaderGoal) {
            infected.goalSelector.addGoal(4, new FollowLeaderGoal(infected, moveSpeed));
        }

        if (!hasFollowScentGoal && IntegrationConfig.ENABLE_SPORE_SCENT_TRAIL.get()) {
            infected.goalSelector.addGoal(4, new FollowScentGoal(infected));
        }
        if (IntegrationConfig.ENABLE_SOUND_RELAY.get()) {
            infected.goalSelector.addGoal(3, new InfectedSoundRelayGoal(infected));
        }

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[SporeGoalInjector] Injected goals for Infected {} (alreadyHadAttraction={}, speed={})",
                    infected.getName().getString(), hasAttractionGoal, moveSpeed);
        }
    }

    private static void injectProtoGoals(Proto proto) {
        if (!IntegrationConfig.ENABLE_PROTO_SOUND_DEPLOYMENT.get()) return;
        
        if (findAttractionGoal(proto) == null) {
            AttractionGoal biomassGoal = new AttractionGoal(proto, 0.0) {
                @Override
                protected boolean isBiomassBypass() {
                    return true;
                }

                @Override
                protected Collection<ChunkPos> getExtendedListeningChunks() {
                    return BiomassHearingManager.getInfectedChunks(proto);
                }
            };
            proto.goalSelector.addGoal(3, biomassGoal);
        }

        if (proto != null && IntegrationConfig.ENABLE_PROTO_SOUND_DEPLOYMENT.get()) {
            proto.goalSelector.addGoal(4, new ProtoSoundDeploymentGoal(proto));
            if (IntegrationConfig.ENABLE_PROTO_NEURAL_INFLUENCE.get()) {
                proto.goalSelector.addGoal(5, new com.example.soundattract.integration.spore.goals.ProtoNeuralSoundInfluenceGoal(proto));
            }
        }
        
        if (IntegrationConfig.ENABLE_PROTO_PREDATORY_CREEP.get()) {
            proto.goalSelector.addGoal(6, new ProtoPredatoryCreepGoal(proto));
        }

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[SporeGoalInjector] Injected ProtoSoundDeploymentGoal and biomass-aware AttractionGoal for Proto {}",
                    proto.getName().getString());
        }
    }

    private static void injectVigilGoals(Vigil vigil) {
        if (!IntegrationConfig.ENABLE_PROTO_PREDATORY_CREEP.get()) return;
        
        vigil.goalSelector.addGoal(2, new VigilSightedReporterGoal(vigil));

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[SporeGoalInjector] Injected VigilSightedReporterGoal for Vigil {}",
                    vigil.getName().getString());
        }
    }

    /**
     * Finds the AttractionGoal or LeaderAttractionGoal instance on a mob.
     */
    public static void injectHiveTumorGoals(HiveTumor tumor) {
        if (tumor != null && IntegrationConfig.ENABLE_HARMONIC_FEAR.get()) {
            tumor.goalSelector.addGoal(4, new com.example.soundattract.integration.spore.goals.HiveTumorHarmonicFearGoal(tumor));
        }
    }

    public static void injectDelusionareGoals(Delusionare delusionare) {
        if (delusionare != null && IntegrationConfig.ENABLE_DELUGE_OF_SOUND.get()) {
            delusionare.goalSelector.addGoal(4, new DelusionareSoundAttackGoal(delusionare));
        }
    }

    /**
     * Finds the current sound target position for a mob by checking its sound-tracking goals.
     */
    public static BlockPos getActiveSoundTarget(Mob mob) {
        for (WrappedGoal wg : mob.goalSelector.getAvailableGoals()) {
            Goal goal = wg.getGoal();
            if (goal instanceof AttractionGoal ag && ag.isPursuingSound()) {
                return ag.getTargetSoundPos();
            }
            if (goal instanceof LeaderAttractionGoal lag && lag.getTargetSoundPos() != null) {
                return lag.getTargetSoundPos();
            }
        }
        return null;
    }

    /**
     * Finds the current sound weight for a mob by checking its sound-tracking goals.
     */
    public static double getActiveSoundWeight(Mob mob) {
        for (WrappedGoal wg : mob.goalSelector.getAvailableGoals()) {
            Goal goal = wg.getGoal();
            if (goal instanceof AttractionGoal ag && ag.isPursuingSound()) {
                return ag.getCurrentTargetWeight();
            }
            if (goal instanceof LeaderAttractionGoal lag && lag.getTargetSoundPos() != null) {
                return lag.getCurrentTargetWeight();
            }
        }
        return -1.0;
    }

    /**
     * Finds the AttractionGoal or LeaderAttractionGoal instance on a mob.
     */
    public static Goal findAttractionGoal(Mob mob) {
        for (WrappedGoal wg : mob.goalSelector.getAvailableGoals()) {
            Goal goal = wg.getGoal();
            if (goal instanceof AttractionGoal || goal instanceof LeaderAttractionGoal) {
                return goal;
            }
        }
        return null;
    }

    /**
     * Determines the appropriate attraction speed for a Spore mob.
     * Stationary mobs like Organoids (Proto, Mound) get 0.0 speed.
     */
    private static double getAttractionSpeed(Mob mob) {
        if (mob instanceof Organoid) {
            return 0.0;
        }
        AttributeInstance speedAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null && speedAttr.getBaseValue() <= 0.001) {
            return 0.0;
        }
        return SoundAttractConfig.COMMON.mobMoveSpeed.get();
    }

    /**
     * Checks if a target should have their stealth bypassed for a specific looker.
    */
    public static boolean shouldBypassStealth(Mob looker, LivingEntity target) {
        if (!IntegrationConfig.ENABLE_STEALTH_MARKER_BRIDGE.get()) return false;
        
        if (looker instanceof Infected) {
            return target.hasEffect(Seffects.MARKER);
        }
        return false;
    }

    /**
     * Handles visibility modification based on MARKER.
    */
    public static void handleVisibilityEvent(LivingEvent.LivingVisibilityEvent event) {
        if (!IntegrationConfig.ENABLE_STEALTH_MARKER_BRIDGE.get()) return;

        LivingEntity target = event.getEntity();
        Entity looker = event.getLookingEntity();

        if (looker instanceof Infected) {
            if (target.hasEffect(Seffects.MARKER)) {
                event.modifyVisibility(1.0 / event.getVisibilityModifier());
            }
        }
    }

    /**
     * Accelerates the dissipation of ScentEntities near a stealthy player.
     */
    public static void accelerateScentDissipation(LivingEntity entity, double radius, int acceleration) {
        if (!IntegrationConfig.ENABLE_SCENT_BLOCK_SUPPRESSION.get()) return;

        AABB area = entity.getBoundingBox().inflate(radius);
        List<ScentEntity> scents = entity.level().getEntitiesOfClass(ScentEntity.class, area);

        for (ScentEntity scent : scents) {            
            int current = scent.getDissipate();
            scent.setDissipate(current + acceleration);
        }
    }

    private static class BiomassHearingManager {
        private static final Map<Proto, ProtoTerritory> TERRITORIES = Collections.synchronizedMap(new WeakHashMap<>());
        private static final int UPDATE_INTERVAL = 12000;

        static Collection<ChunkPos> getInfectedChunks(Proto proto) {
            ProtoTerritory territory = TERRITORIES.computeIfAbsent(proto, p -> new ProtoTerritory());
            long now = proto.level().getGameTime();
            if (now - territory.lastUpdateTick > UPDATE_INTERVAL || territory.infectedChunks.isEmpty()) {
                updateInfectedChunks(proto, territory);
                territory.lastUpdateTick = now;
            }
            return territory.infectedChunks;
        }

        private static void updateInfectedChunks(Proto proto, ProtoTerritory territory) {
            territory.infectedChunks.clear();
            ChunkPos start = new ChunkPos(proto.blockPosition());
            
            Queue<ChunkPos> queue = new LinkedList<>();
            Set<ChunkPos> visited = new HashSet<>();
            Map<ChunkPos, Integer> distances = new HashMap<>();

            queue.add(start);
            visited.add(start);
            distances.put(start, 0);

            while (!queue.isEmpty()) {
                ChunkPos current = queue.poll();
                int dist = distances.get(current);

                if (isChunkInfected(proto.level(), current)) {
                    territory.infectedChunks.add(current);
                    
                    if (dist < 2) {
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                if (dx == 0 && dz == 0) continue;
                                ChunkPos next = new ChunkPos(current.x + dx, current.z + dz);
                                if (!visited.contains(next)) {
                                    visited.add(next);
                                    distances.put(next, dist + 1);
                                    queue.add(next);
                                }
                            }
                        }
                    }
                }
            }
        }

        private static boolean isChunkInfected(net.minecraft.world.level.Level level, ChunkPos chunk) {
            AABB chunkBox = new AABB(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(), 
                                     chunk.getMaxBlockX(), level.getMaxY(), chunk.getMaxBlockZ());
            List<Mound> mounds = level.getEntitiesOfClass(Mound.class, chunkBox);
            if (!mounds.isEmpty()) return true;

            int[] samples = {0, 8, 15};
            for (int xOff : samples) {
                for (int zOff : samples) {
                    int bx = chunk.getMinBlockX() + xOff;
                    int bz = chunk.getMinBlockZ() + zOff;
                    int by = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
                    
                    if (isBiomass(level, level.getBlockState(new BlockPos(bx, by, bz))) ||
                        isBiomass(level, level.getBlockState(new BlockPos(bx, by - 1, bz)))) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static boolean isBiomass(net.minecraft.world.level.Level level, net.minecraft.world.level.block.state.BlockState state) {
            Identifier id = level.registryAccess().lookupOrThrow(Registries.BLOCK).getKey(state.getBlock());
            return id != null && id.getNamespace().equals("spore");
        }

        private static class ProtoTerritory {
            final Set<ChunkPos> infectedChunks = new HashSet<>();
            long lastUpdateTick = -UPDATE_INTERVAL;
        }
    }
}