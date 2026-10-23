package com.example.soundattract.event;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.ai.BlockBreakerManager;
import com.example.soundattract.ai.FollowLeaderGoal;
import com.example.soundattract.ai.FollowScentGoal;
import com.example.soundattract.ai.FollowerEdgeRelayGoal;
import com.example.soundattract.ai.LeaderAttractionGoal;
import com.example.soundattract.ai.PickUpAndThrowToSoundGoal;
import com.example.soundattract.ai.TeleportToSoundGoal;
import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.data.DataDrivenTags;
import com.example.soundattract.worker.WorkerScheduler.GroupComputeResult;
import com.example.soundattract.worker.WorkSchedulerManager;
import com.example.soundattract.integration.smartbrainlib.SmartBrainLibCompat;
import com.example.soundattract.tracking.SoundTracker;
import com.example.soundattract.integration.relentlessundead.RelentlessUndeadIntegration;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import com.example.soundattract.mixin.MobAccessor;
import net.fabricmc.loader.api.FabricLoader;
import java.time.Duration;

public class SoundAttractionEvents {

    private static final Cache<Mob, List<GoalDefinition>> PENDING_GOAL_ADDITIONS = Caffeine.newBuilder()
            .weakKeys()
            .maximumSize(8192)
            .expireAfterWrite(Duration.ofSeconds(5))
            .build();
    private static final int MAX_PENDING_GOALS_PER_MOB = 16;

    private static long serverTickCounter = 0;
    private static boolean initialDelayHasPassed = false;

    private static class GoalDefinition {
        final int priority;
        final Goal goalInstance;
        final Class<? extends Goal> goalClass;

        GoalDefinition(int priority, Goal goalInstance) {
            this.priority = priority;
            this.goalInstance = goalInstance;
            this.goalClass = goalInstance.getClass();
        }
    }

    public static boolean isCustomNpcsMob(Mob mob) {        
        if (mob == null) return false;
        if (SoundAttractConfig.COMMON == null || !SoundAttractConfig.COMMON.enableCustomNpcsIntegration.get()) {
            return false;
        }
        try {

            Class<?> c = mob.getClass();
            while (c != null) {
                String className = c.getName();
                if (className.contains("EntityNPCInterface") || 
                    className.contains("EntityCustomNpc") || 
                    className.contains("EntityNPCFlying") ||
                    className.contains("noppes.npcs.entity")) {
                    return true;
                }
                c = c.getSuperclass();
            }
            

            Identifier entityTypeKey = EntityType.getKey(mob.getType());
            if (entityTypeKey != null && "customnpcs".equals(entityTypeKey.getNamespace())) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[SoundAttractionEvents] Detected CustomNPCs by namespace: {}", entityTypeKey);
                }
                return true;
            }
            
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void scheduleAddGoal(Mob mob, int priority, Goal goal) {
        if (mob == null || goal == null || mob.isRemoved()) return;
        PENDING_GOAL_ADDITIONS.asMap().compute(mob, (key, pending) -> {
            List<GoalDefinition> goals = pending == null ? new ArrayList<>() : pending;
            Class<?> goalClass = goal.getClass();
            for (GoalDefinition existing : goals) {
                if (existing.goalClass == goalClass) return goals;
            }
            if (goals.size() >= MAX_PENDING_GOALS_PER_MOB) return goals;
            goals.add(new GoalDefinition(priority, goal));
            return goals;
        });
    }

    public static void onServerStopped() {
        PENDING_GOAL_ADDITIONS.invalidateAll();
        serverTickCounter = 0L;
        initialDelayHasPassed = false;
        invalidateCachedEntityTypes();
    }

    private static long lastMobCountUpdateTime_ServerTick = -1;
    private static int cachedAttractedMobCount_ServerTick = 0;
    private static Set<EntityType<?>> CACHED_ATTRACTED_ENTITY_TYPES = null;
    private static Set<EntityType<?>> CACHED_BLACKLISTED_ENTITY_TYPES = null;

    public static void invalidateCachedEntityTypes() {
        CACHED_ATTRACTED_ENTITY_TYPES = null;
        CACHED_BLACKLISTED_ENTITY_TYPES = null;
    }

    private static Set<EntityType<?>> getEntityTypesForTag(TagKey<EntityType<?>> tagKey) {
        Set<EntityType<?>> result = new HashSet<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type == null) {
                continue;
            }
            try {
                if (type.is(tagKey)) {
                    result.add(type);
                }
            } catch (Exception e) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.warn("[SoundAttractionEvents] Error checking tag {} for entity type {}", tagKey.location(), BuiltInRegistries.ENTITY_TYPE.getKey(type), e);
                }
            }
        }
        return result;
    }

    public static Set<EntityType<?>> getCachedAttractedEntityTypes() {
        if (CACHED_ATTRACTED_ENTITY_TYPES != null) {
            return CACHED_ATTRACTED_ENTITY_TYPES;
        }

        Set<EntityType<?>> configSet = new HashSet<>();
        boolean shouldRetryLater = false;
        for (String idStr : SoundAttractConfig.COMMON.attractedEntities.get()) {
            try {
                Identifier id = Identifier.tryParse(idStr);
                EntityType<?> type = id != null ? BuiltInRegistries.ENTITY_TYPE.getValue(id) : null;
                if (type != null) {
                    configSet.add(type);
                } else {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        Soundattract.LOGGER.warn("[SoundAttractionEvents] Unknown attracted entity type in config: {}", idStr);
                    }
                    if (id != null && !"minecraft".equals(id.getNamespace()) && FabricLoader.getInstance().isModLoaded(id.getNamespace())) {
                        shouldRetryLater = true;
                    }
                }
            } catch (Exception e) {
                Soundattract.LOGGER.warn("[SoundAttractionEvents] Invalid Identifier for attracted entity type in config: {}", idStr, e);
            }
        }

        boolean enableDataDriven = SoundAttractConfig.COMMON.enableDataDriven.get();
        Set<EntityType<?>> tagSet = enableDataDriven ? getEntityTypesForTag(DataDrivenTags.ATTRACTED_MOBS) : java.util.Collections.emptySet();

        Set<EntityType<?>> result = new HashSet<>();
        if (!enableDataDriven || tagSet.isEmpty()) {
            result.addAll(configSet);
        } else {
            String priority = SoundAttractConfig.COMMON.datapackPriority.get();
            boolean datapackOverConfig = "datapack_over_config".equalsIgnoreCase(priority);
            if (datapackOverConfig) {
                result.addAll(tagSet);
            } else {
                result.addAll(configSet);
                result.addAll(tagSet);
            }
        }

        if (shouldRetryLater) {
            return result;
        }

        CACHED_ATTRACTED_ENTITY_TYPES = result;

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            String resultingTypes = result.stream()
                    .map(et -> BuiltInRegistries.ENTITY_TYPE.getKey(et).toString())
                    .collect(Collectors.joining(", "));
            Soundattract.LOGGER.info("[DIAGNOSTIC] Final attracted EntityType cache contains: [{}]", resultingTypes);
        }

        return CACHED_ATTRACTED_ENTITY_TYPES;
    }

    public static Set<EntityType<?>> getCachedBlacklistedEntityTypes() {
        if (CACHED_BLACKLISTED_ENTITY_TYPES != null) {
            return CACHED_BLACKLISTED_ENTITY_TYPES;
        }

        Set<EntityType<?>> configSet = new HashSet<>();
        boolean shouldRetryLater = false;
        for (String idStr : SoundAttractConfig.COMMON.mobBlacklist.get()) {
            try {
                Identifier id = Identifier.tryParse(idStr);
                EntityType<?> type = id != null ? BuiltInRegistries.ENTITY_TYPE.getValue(id) : null;
                if (type != null) {
                    configSet.add(type);
                } else {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        Soundattract.LOGGER.warn("[SoundAttractionEvents] Unknown blacklisted entity type in config: {}", idStr);
                    }
                    if (id != null && !"minecraft".equals(id.getNamespace()) && FabricLoader.getInstance().isModLoaded(id.getNamespace())) {
                        shouldRetryLater = true;
                    }
                }
            } catch (Exception e) {
                Soundattract.LOGGER.warn("[SoundAttractionEvents] Invalid Identifier for blacklisted entity type in config: {}", idStr, e);
            }
        }

        boolean enableDataDriven = SoundAttractConfig.COMMON.enableDataDriven.get();
        Set<EntityType<?>> tagSet = enableDataDriven ? getEntityTypesForTag(DataDrivenTags.BLACKLISTED_MOBS) : java.util.Collections.emptySet();

        Set<EntityType<?>> result = new HashSet<>();
        if (!enableDataDriven || tagSet.isEmpty()) {
            result.addAll(configSet);
        } else {
            String priority = SoundAttractConfig.COMMON.datapackPriority.get();
            boolean datapackOverConfig = "datapack_over_config".equalsIgnoreCase(priority);
            if (datapackOverConfig) {
                result.addAll(tagSet);
            } else {
                result.addAll(configSet);
                result.addAll(tagSet);
            }
        }

        if (shouldRetryLater) {
            return result;
        }

        CACHED_BLACKLISTED_ENTITY_TYPES = result;

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            String resultingTypes = result.stream()
                    .map(et -> BuiltInRegistries.ENTITY_TYPE.getKey(et).toString())
                    .collect(Collectors.joining(", "));
            Soundattract.LOGGER.info("[DIAGNOSTIC] Final blacklisted EntityType cache contains: [{}]", resultingTypes);
        }

        return CACHED_BLACKLISTED_ENTITY_TYPES;
    }

    public static void onServerTick(MinecraftServer server) {
        serverTickCounter++;
        applyPendingGoalAdditions();
        if (!initialDelayHasPassed) {
            int delay = SoundAttractConfig.COMMON.initialGroupComputationDelay.get();
            if (serverTickCounter >= delay) {
                initialDelayHasPassed = true;
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[SoundAttractionEvents] Initial group computation delay of {} ticks has passed. Enabling group updates.", delay);
                }
            }
            return;
        }
        if (!initialDelayHasPassed) return;
        if (server == null || server.overworld() == null) {
            return;
        }
        ServerLevel serverLevel = server.overworld();
            long currentTime = serverLevel.getGameTime();
            int mobCountForCooldownManager;

            int mobCountUpdateInterval = SoundAttractConfig.COMMON.groupUpdateInterval.get();
            mobCountUpdateInterval = Math.max(20, mobCountUpdateInterval);

            if (currentTime - lastMobCountUpdateTime_ServerTick >= mobCountUpdateInterval || lastMobCountUpdateTime_ServerTick == -1) {
                int currentMobCount = 0;
                Set<Mob> countedMobsInTick = new HashSet<>();
                Set<EntityType<?>> attractedEntityTypes = getCachedAttractedEntityTypes();

                if (!attractedEntityTypes.isEmpty()) {
                    for (ServerPlayer player : serverLevel.players()) {
                        int simDistanceBlocks = player.level().getServer().getPlayerList().getViewDistance() * 16;
                        AABB playerSimArea = player.getBoundingBox().inflate(simDistanceBlocks);
                        List<Mob> mobsNearPlayer = serverLevel.getEntitiesOfClass(Mob.class, playerSimArea);

                        for (Mob mob : mobsNearPlayer) {
                            boolean eligible = attractedEntityTypes.contains(mob.getType())
                                    || SoundAttractConfig.getMatchingProfile(mob) != null
                                    || isCustomNpcsMob(mob);
                            if (mob.isAlive() && !mob.isRemoved() && eligible) {
                                if (countedMobsInTick.add(mob)) {
                                    currentMobCount++;
                                }
                            }
                        }
                    }
                }
                cachedAttractedMobCount_ServerTick = currentMobCount;
                lastMobCountUpdateTime_ServerTick = currentTime;
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[SoundAttractionEvents] Updated attracted mob count for DynamicScanCooldownManager: {}", cachedAttractedMobCount_ServerTick);
                }
            }
            mobCountForCooldownManager = cachedAttractedMobCount_ServerTick;

            com.example.soundattract.runtime.DynamicScanCooldownManager.update(currentTime, mobCountForCooldownManager);
            SoundTracker.tick();
            BlockBreakerManager.processPendingActions();
            try {
                List<GroupComputeResult> results = WorkSchedulerManager.get().drainGroupResults();
                if (!results.isEmpty()) {
                    for (GroupComputeResult r : results) {
                        if (r.dimension() == null) continue;
                        ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, r.dimension()));
                        if (level != null) {
                            com.example.soundattract.ai.MobGroupManager.applyGroupResult(level, r);
                        }
                    }
                }
            } catch (Throwable t) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.error("[SoundAttractionEvents] Applying group results failed", t);
                }
            }
            if (!PENDING_GOAL_ADDITIONS.asMap().isEmpty()) {
                Iterator<Map.Entry<Mob, List<GoalDefinition>>> iterator = PENDING_GOAL_ADDITIONS.asMap().entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Mob, List<GoalDefinition>> entry = iterator.next();
                    Mob mob = entry.getKey();
                    List<GoalDefinition> goalsToAdd = entry.getValue();

                    if (mob.isAlive() && mob.level() != null && !mob.isRemoved()) {
                        MobAccessor accessor = (MobAccessor) mob;
                        for (GoalDefinition def : goalsToAdd) {
                            boolean goalExists = accessor.getGoalSelector().getAvailableGoals().stream()
                                    .anyMatch(wrappedGoal -> def.goalClass.isInstance(wrappedGoal.getGoal()));
                            if (!goalExists) {
                                accessor.getGoalSelector().addGoal(def.priority, def.goalInstance);
                                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                                    Soundattract.LOGGER.info("[SoundAttractionEvents] Added goal {} to mob {}", def.goalClass.getSimpleName(), mob.getName().getString());
                                }
                            }
                        }
                    }
                    iterator.remove();
                }
            }
        }

    private static void applyPendingGoalAdditions() {
        if (PENDING_GOAL_ADDITIONS.asMap().isEmpty()) return;
        Iterator<Map.Entry<Mob, List<GoalDefinition>>> iterator =
                PENDING_GOAL_ADDITIONS.asMap().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Mob, List<GoalDefinition>> entry = iterator.next();
            Mob mob = entry.getKey();
            List<GoalDefinition> goalsToAdd = entry.getValue();
            if (mob.isAlive() && mob.level() != null && !mob.isRemoved()) {
                MobAccessor accessor = (MobAccessor) mob;
                for (GoalDefinition def : goalsToAdd) {
                    boolean goalExists = accessor.getGoalSelector().getAvailableGoals().stream()
                            .anyMatch(wrappedGoal -> def.goalClass.isInstance(wrappedGoal.getGoal()));
                    if (!goalExists) {
                        accessor.getGoalSelector().addGoal(def.priority, def.goalInstance);
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            Soundattract.LOGGER.info("[SoundAttractionEvents] Added goal {} to mob {}",
                                    def.goalClass.getSimpleName(), mob.getName().getString());
                        }
                    }
                }
            }
            iterator.remove();
        }
    }

    public static void onLevelTick(ServerLevel level) {
        if (!level.isClientSide()) {
            if (initialDelayHasPassed) {
                com.example.soundattract.ai.MobGroupManager.updateGroups(level);
                com.example.soundattract.ai.RaidManager.tick(level);
            }
        }
    }

    public static void onMobJoinLevel(Mob mob, ServerLevel level) {
        if (mob == null || level.isClientSide()) return;


        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info("[SoundAttractionEvents] CustomNPCs integration enabled: {}", 
                SoundAttractConfig.COMMON.enableCustomNpcsIntegration.get());
        }


        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info("[SoundAttractionEvents] Mob joined: {} of type {}, isCustomNpcs: {}", 
                mob.getName().getString(), EntityType.getKey(mob.getType()), isCustomNpcsMob(mob));
        }

        Set<EntityType<?>> blacklistedEntityTypes = getCachedBlacklistedEntityTypes();
        if (blacklistedEntityTypes.contains(mob.getType())) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info("[SoundAttractionEvents] Mob {} is on the blacklist, ignoring.", EntityType.getKey(mob.getType()));
            }
            return;
        }
        
        Set<EntityType<?>> attractedEntityTypes = getCachedAttractedEntityTypes();
        boolean isAttractedByType = attractedEntityTypes.contains(mob.getType());
        boolean hasMatchingprofile = SoundAttractConfig.getMatchingProfile(mob) != null;
        boolean isCustomNpcs = isCustomNpcsMob(mob);
        
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info("[SoundAttractionEvents] {} eligibility - type:{}, profile:{}, customNpcs:{}", 
                mob.getName().getString(), isAttractedByType, hasMatchingprofile, isCustomNpcs);
        }
        
        if (!isAttractedByType && !hasMatchingprofile && !isCustomNpcs) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info("[SoundAttractionEvents] Mob {} not eligible - type:{}, profile:{}, customNpcs:{}", 
                    mob.getName().getString(), isAttractedByType, hasMatchingprofile, isCustomNpcs);
            }
            return;
        }


        if (isCustomNpcs && SoundAttractConfig.COMMON.enableCustomNpcsIntegration.get()) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info("[SoundAttractionEvents] Adding CustomNPCs goals immediately for {}", mob.getName().getString());
            }
            addCustomNpcsGoals(mob);
            return;
        }

        if (SoundAttractConfig.COMMON.enableSmartBrainLibIntegration.get()) {
            if (SmartBrainLibCompat.tryAttachSoundAttractBrain(mob)) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[SoundAttractionEvents] Using SmartBrainLib integration for mob {} of type {}", mob.getName().getString(), EntityType.getKey(mob.getType()));
                }
                return;
            }
        }

        double moveSpeed = SoundAttractConfig.COMMON.mobMoveSpeed.get();

        if (SoundAttractConfig.COMMON.enableTeleportToSound.get()) {
            try {
                Identifier teleportTagLoc = Identifier.tryParse(SoundAttractConfig.COMMON.teleportCanTeleportTag.get());
                if (teleportTagLoc != null) {
                    TagKey<EntityType<?>> canTeleportTag = TagKey.create(Registries.ENTITY_TYPE, teleportTagLoc);
                    if (mob.getType().is(canTeleportTag)) {
                        scheduleAddGoal(mob, 2, new TeleportToSoundGoal(mob));
                    }
                }
            } catch (Exception e) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.warn("[SoundAttractionEvents] Failed to apply TeleportToSoundGoal tag check for mob {}: {}", EntityType.getKey(mob.getType()), e.getMessage());
                }
            }
        }

        if (SoundAttractConfig.COMMON.enablePickUpAndThrowToSound.get()) {
            try {
                Identifier pickUpTagLoc = Identifier.tryParse(SoundAttractConfig.COMMON.pickUpCanPickUpTag.get());
                if (pickUpTagLoc != null) {
                    TagKey<EntityType<?>> canPickUpTag = TagKey.create(Registries.ENTITY_TYPE, pickUpTagLoc);
                    if (mob.getType().is(canPickUpTag)) {
                        scheduleAddGoal(mob, 2, new PickUpAndThrowToSoundGoal(mob));
                    }
                }
            } catch (Exception e) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.warn("[SoundAttractionEvents] Failed to apply PickUpAndThrowToSoundGoal tag check for mob {}: {}", EntityType.getKey(mob.getType()), e.getMessage());
                }
            }
        }
        
        if (RelentlessUndeadIntegration.isMobEligibleForClimbing(mob)) {
             scheduleAddGoal(mob, 0, new com.example.soundattract.ai.SoundClimbGoal(mob));
             if (SoundAttractConfig.COMMON.debugLogging.get()) {
                  Soundattract.LOGGER.info("[SoundAttractionEvents] Scheduled SoundClimbGoal for mob {}", mob.getName().getString());
             }
        }

        if (SoundAttractConfig.COMMON.edgeMobSmartBehavior.get()) {
            scheduleAddGoal(mob, 3, new LeaderAttractionGoal(mob, moveSpeed));
            scheduleAddGoal(mob, 1, new FollowerEdgeRelayGoal(mob, moveSpeed));
        } else {
            scheduleAddGoal(mob, 3, new AttractionGoal(mob, moveSpeed));
        }
        scheduleAddGoal(mob, 4, new FollowLeaderGoal(mob, moveSpeed));

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info("[SoundAttractionEvents] Scheduled goals for mob {} of type {}", mob.getName().getString(), EntityType.getKey(mob.getType()));
        }

        if (initialDelayHasPassed) {
            com.example.soundattract.ai.MobGroupManager.updateGroups(level);
        }
    }

    public static void onWorldTick(ServerLevel world) {
    }

    private static void addCustomNpcsGoals(Mob mob) {
        MobAccessor accessor = (MobAccessor) mob;
        if (accessor.getGoalSelector() == null) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info("[SoundAttractionEvents] CustomNPCs {} has null goalSelector", mob.getName().getString());
            }
            return;
        }

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info("[SoundAttractionEvents] CustomNPCs {} existing goals:", mob.getName().getString());
            accessor.getGoalSelector().getAvailableGoals().forEach(wrapped -> {
                Soundattract.LOGGER.info("  - Priority {}: {}", wrapped.getPriority(), wrapped.getGoal().getClass().getSimpleName());
            });
        }

        double moveSpeed = SoundAttractConfig.COMMON.mobMoveSpeed.get();

        boolean hasAttractionGoal = accessor.getGoalSelector().getAvailableGoals().stream()
                .anyMatch(wrappedGoal -> wrappedGoal.getGoal() instanceof AttractionGoal);
        boolean hasLeaderAttractionGoal = accessor.getGoalSelector().getAvailableGoals().stream()
                .anyMatch(wrappedGoal -> wrappedGoal.getGoal() instanceof LeaderAttractionGoal);
        boolean hasFollowerEdgeRelayGoal = accessor.getGoalSelector().getAvailableGoals().stream()
                .anyMatch(wrappedGoal -> wrappedGoal.getGoal() instanceof FollowerEdgeRelayGoal);
        boolean hasFollowLeaderGoal = accessor.getGoalSelector().getAvailableGoals().stream()
                .anyMatch(wrappedGoal -> wrappedGoal.getGoal() instanceof FollowLeaderGoal);

        boolean smartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        int attractionPriority = 0;

        if (smartEdge) {
            if (!hasFollowerEdgeRelayGoal) {
                accessor.getGoalSelector().addGoal(attractionPriority, new FollowerEdgeRelayGoal(mob, moveSpeed));
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[SoundAttractionEvents] Added FollowerEdgeRelayGoal to CustomNPCs {}", mob.getName().getString());
                }
            }
            if (!hasLeaderAttractionGoal) {
                accessor.getGoalSelector().addGoal(attractionPriority + 1, new LeaderAttractionGoal(mob, moveSpeed));
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[SoundAttractionEvents] Added LeaderAttractionGoal to CustomNPCs {}", mob.getName().getString());
                }
            }
        } else {
            if (!hasAttractionGoal) {
                accessor.getGoalSelector().addGoal(attractionPriority, new AttractionGoal(mob, moveSpeed) {
                    @Override
                    public boolean canUse() {
                        boolean hasTarget = (mob.getTarget() != null && mob.getTarget().isAlive());
                        boolean result = (!hasTarget || isHighWeightOverrideActive()) && super.canUse();
                        if (SoundAttractConfig.COMMON.debugLogging.get() && result) {
                            Soundattract.LOGGER.info("[CustomNPCs] AttractionGoal.canUse() returning true for {}", mob.getName().getString());
                        }
                        return result;
                    }

                    @Override
                    public boolean canContinueToUse() {
                        boolean hasTarget = (mob.getTarget() != null && mob.getTarget().isAlive());
                        boolean result = (!hasTarget || isHighWeightOverrideActive()) && super.canContinueToUse();
                        if (SoundAttractConfig.COMMON.debugLogging.get() && result) {
                            Soundattract.LOGGER.info("[CustomNPCs] AttractionGoal.canContinueToUse() returning true for {}", mob.getName().getString());
                        }
                        return result;
                    }
                });
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[SoundAttractionEvents] Added AttractionGoal to CustomNPCs {} at priority {}", mob.getName().getString(), attractionPriority);
                }
            }
        }

        if (!hasFollowLeaderGoal) {
            accessor.getGoalSelector().addGoal(attractionPriority + 2, new FollowLeaderGoal(mob, moveSpeed));
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info("[SoundAttractionEvents] Added FollowLeaderGoal to CustomNPCs {}", mob.getName().getString());
            }
        }

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info("[SoundAttractionEvents] CustomNPCs {} goals after adding:", mob.getName().getString());
            accessor.getGoalSelector().getAvailableGoals().forEach(wrapped -> {
                Soundattract.LOGGER.info("  - Priority {}: {}", wrapped.getPriority(), wrapped.getGoal().getClass().getSimpleName());
            });
        }
    }
}
