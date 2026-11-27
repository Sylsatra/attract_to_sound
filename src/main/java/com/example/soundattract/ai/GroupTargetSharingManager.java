package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.event.SoundAttractionEvents;
import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.pathfinding.PathTaskScheduler;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;

@EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class GroupTargetSharingManager {
    private static final int MAX_ADMISSIONS_PER_TICK = 32;
    private static final int MAX_PENDING_CHECKS_PER_TICK = 64;
    private static final int MAX_LEASE_CHECKS_PER_TICK = 32;
    private static final int MAX_REPORTS = 2048;
    private static final int MAX_PENDING = 1024;
    private static final int MAX_LEASES = 4096;
    private static final int REPORT_TICKS = 40;
    private static final int OBSERVATION_INTERVAL = 20;
    private static final int RESCAN_INTERVAL = 10;
    private static final int COALESCE_TICKS = 10;

    private static final Map<MemberKey, PendingSighting> pending = new LinkedHashMap<>();
    private static final Map<MemberKey, Report> reports = new LinkedHashMap<>();
    private static final Object2ObjectLinkedOpenHashMap<MemberKey, Lease> leases = new Object2ObjectLinkedOpenHashMap<>();
    private static final Set<Mob> leaseMembers = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<MemberKey, SpotterCheck> spotters = new LinkedHashMap<>();
    private static final ThreadLocal<ShareAssignment> assignment = new ThreadLocal<>();
    private static volatile Consumer<ConfirmedSight> sightListener;
    private static volatile long configRevision;
    private static long nextReportRevision;

    private GroupTargetSharingManager() {}

    public record ConfirmedSight(ServerLevel level, BlockPos position, long seenTick, UUID sourceId, UUID targetId) {}

    private record MemberKey(ServerLevel level, UUID uuid) {}

    private record PendingSighting(
            long serverTick, int sourceEntityId, int targetEntityId, UUID targetId,
            BlockPos position, long seenTick, boolean scout, LivingChangeTargetEvent event, long configRevision
    ) {}

    private record ShareAssignment(Mob recipient, LivingEntity target) {}

    private record SpotterCheck(UUID lastSpotter, long nextCheckTick, long allowSameSpotterTick) {}

    private static final class Report {
        final long revision;
        final long rosterEpoch;
        final long configRevision;
        final int leaderEntityId;
        final UUID targetId;
        final int targetEntityId;
        UUID sourceId;
        int sourceEntityId;
        boolean scout;
        BlockPos position;
        long observedTick;
        long nextObservationTick;
        long nextRescanTick;
        int cursor;
        Set<UUID> vetoedMembers;

        Report(long revision, long rosterEpoch, int leaderEntityId, LivingEntity target) {
            this.revision = revision;
            this.rosterEpoch = rosterEpoch;
            this.configRevision = GroupTargetSharingManager.configRevision;
            this.leaderEntityId = leaderEntityId;
            this.targetId = target.getUUID();
            this.targetEntityId = target.getId();
        }

        void observe(Mob source, BlockPos position, long seenTick, boolean scout) {
            sourceId = source.getUUID();
            sourceEntityId = source.getId();
            this.scout = scout;
            this.position = position;
            observedTick = seenTick;
            nextObservationTick = seenTick + OBSERVATION_INTERVAL;
        }

        boolean active(long now) {
            return now - observedTick < REPORT_TICKS;
        }
    }

    private static final class Lease {
        final MemberKey group;
        final long rosterEpoch;
        final long reportRevision;
        final UUID targetId;
        final int targetEntityId;
        final int memberEntityId;
        final int lastHurtTimestamp;
        long validatedTick = Long.MIN_VALUE;
        long validatedConfigRevision = Long.MIN_VALUE;
        double memberX;
        double memberY;
        double memberZ;
        double targetX;
        double targetY;
        double targetZ;
        boolean allowed;
        long nextDirectAttempt;
        long nextClearAttempt;

        Lease(MemberKey group, Report report, Mob member) {
            this.group = group;
            rosterEpoch = report.rosterEpoch;
            reportRevision = report.revision;
            targetId = report.targetId;
            targetEntityId = report.targetEntityId;
            memberEntityId = member.getId();
            lastHurtTimestamp = member.getLastHurtByMobTimestamp();
        }
    }

    public static void setConfirmedSightListener(Consumer<ConfirmedSight> listener) {
        sightListener = listener;
    }

    public static void invalidatePolicy() {
        configRevision++;
    }

    public static boolean isShareAssignment(Mob mob, LivingEntity target) {
        ShareAssignment current = assignment.get();
        return current != null && current.recipient() == mob && current.target() == target;
    }

    public static void noteDirectTargetAttempt(Mob source, LivingEntity target, LivingChangeTargetEvent event) {
        if (event.getTargetType() != LivingChangeTargetEvent.LivingTargetType.MOB_TARGET
                || event.getOriginalAboutToBeSetTarget() != target || isShareAssignment(source, target)) return;
        queueSighting(source, target, false, event);
    }

    public static void noteScoutSighting(Mob source, LivingEntity target) {
        queueSighting(source, target, true, null);
    }

    private static void queueSighting(Mob source, LivingEntity target, boolean scout, LivingChangeTargetEvent event) {
        if (!(source.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !validTarget(source, target) || !isAttracted(source)) return;
        Mob leader = MobGroupManager.getLeader(source);
        if (scout && ownsTarget(source, target)) return;
        if (!scout && source.getTarget() == target && !ownsTarget(source, target)) return;
        if (sightListener == null && MobGroupManager.getGroupRoster(leader) == null) return;
        MemberKey key = new MemberKey(level, source.getUUID());
        PendingSighting previous = pending.get(key);
        if (previous != null && previous.targetEntityId() == target.getId()
                && previous.sourceEntityId() == source.getId() && previous.scout() == scout) return;
        if (previous != null) pending.remove(key);
        else if (pending.size() >= MAX_PENDING) return;
        pending.put(key, new PendingSighting(level.getServer().getTickCount(), source.getId(), target.getId(),
                target.getUUID(), target.blockPosition(), level.getGameTime(), scout, event, configRevision));
    }

    public static boolean trySupplementalSpotter(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level)) return false;
        Mob leader = MobGroupManager.getLeader(mob);
        if (leader == null || leader == mob || !leader.isAlive() || !isAttracted(mob)) return false;
        MemberKey group = new MemberKey(level, leader.getUUID());
        long now = level.getGameTime();
        Report active = reports.get(group);
        if (active != null && active.active(now)) return false;
        SpotterCheck previous = spotters.get(group);
        if (previous != null && (now < previous.nextCheckTick()
                || (previous.lastSpotter().equals(mob.getUUID()) && now < previous.allowSameSpotterTick()))) return false;
        if (spotters.size() >= MAX_REPORTS && previous == null) {
            spotters.remove(spotters.keySet().iterator().next());
        }
        spotters.put(group, new SpotterCheck(mob.getUUID(), now + 4, now + 16));
        return true;
    }

    public static boolean ownsTarget(Mob mob, LivingEntity target) {
        return findLease(mob, target) != null;
    }

    public static boolean tryDirectReacquisition(Mob mob, LivingEntity target) {
        Lease lease = findLease(mob, target);
        if (lease == null) return false;
        long now = mob.level().getGameTime();
        if (now < lease.nextDirectAttempt) return false;
        lease.nextDirectAttempt = now + COALESCE_TICKS;
        return true;
    }

    public static BlockPos getFollowPosition(Mob mob, Mob leader) {
        if (leader == null || leader == mob || !(mob.level() instanceof ServerLevel level)) return null;
        MobGroupManager.GroupRoster roster = MobGroupManager.getGroupRoster(leader);
        if (roster == null || !roster.members().contains(mob.getUUID())) return null;
        Report report = reports.get(new MemberKey(level, leader.getUUID()));
        if (report != null && report.active(level.getGameTime()) && report.leaderEntityId == leader.getId()
                && roster.epoch() == report.rosterEpoch) return report.position;
        return null;
    }

    public static BlockPos getClueTrailPosition(Mob mob, Mob leader) {
        if (leader == null || leader == mob || !(mob.level() instanceof ServerLevel level)
                || leader.level() != level) return null;
        MobGroupManager.GroupRoster roster = MobGroupManager.getGroupRoster(leader);
        if (roster == null || !roster.members().contains(mob.getUUID())) return null;
        ClueDirector.Hint hint = ClueDirector.getActiveHint(leader);
        if (hint == null || !hint.dimension().equals(level.dimension())
                || !(ClueInvestigationGoal.ownsActiveHintPath(leader, hint)
                || LeaderAttractionGoal.ownsActiveHintPath(leader, hint))) return null;
        Vec3 away = mob.position().subtract(leader.position());
        if (away.lengthSqr() < 0.01) away = leader.getLookAngle().reverse();
        Vec3 trail = leader.position().add(away.normalize().scale(3.5));
        BlockPos position = BlockPos.containing(trail.x, leader.getY(), trail.z);
        return level.hasChunkAt(position) ? position : null;
    }

    public static boolean allowSharedMelee(Mob mob, LivingEntity target) {
        if (mob == null || target == null || !leaseMembers.contains(mob)) return true;
        Lease lease = findLease(mob, target);
        return lease == null || retaliationOwnsTarget(mob, target, lease)
                || validateLease(lease.group.level(), mob, target, lease);
    }

    public static boolean allowSharedPath(Mob mob, LivingEntity target) {
        Lease lease = findLease(mob, target);
        if (lease == null || retaliationOwnsTarget(mob, target, lease)) return true;
        if (!validateLease(lease.group.level(), mob, target, lease)) return false;
        PathNavigation navigation = mob.getNavigation();
        Path current = navigation.getPath();
        return (current != null && !current.isDone() && target.blockPosition().equals(navigation.getTargetPos()))
                || PathTaskScheduler.tryConsumePathAttempt(mob.level(), PathTaskScheduler.Priority.COMBAT);
    }

    private static Lease findLease(Mob mob, LivingEntity target) {
        if (target == null || mob == null || leases.isEmpty() || !leaseMembers.contains(mob)
                || !(mob.level() instanceof ServerLevel level)) return null;
        Lease lease = leases.get(new MemberKey(level, mob.getUUID()));
        return lease != null && matchesLease(lease, mob, target) ? lease : null;
    }

    private static void putLease(MemberKey key, Lease lease) {
        leases.put(key, lease);
        if (key.level().getEntity(key.uuid()) instanceof Mob member) leaseMembers.add(member);
    }

    private static Lease removeLease(MemberKey key) {
        Lease lease = leases.remove(key);
        if (lease != null && key.level().getEntity(key.uuid()) instanceof Mob member) leaseMembers.remove(member);
        return lease;
    }

    private static void removeLease(MemberKey key, Lease expected) {
        if (leases.remove(key, expected) && key.level().getEntity(key.uuid()) instanceof Mob member) {
            leaseMembers.remove(member);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        confirmSightings(event.getServer());
        checkLeases(event.getServer());
        advanceReports(event.getServer());
    }

    private static void confirmSightings(MinecraftServer server) {
        for (int i = 0; i < MAX_PENDING_CHECKS_PER_TICK && !pending.isEmpty(); i++) {
            Iterator<Map.Entry<MemberKey, PendingSighting>> iterator = pending.entrySet().iterator();
            Map.Entry<MemberKey, PendingSighting> entry = iterator.next();
            MemberKey key = entry.getKey();
            PendingSighting sight = entry.getValue();
            if (sight.serverTick() == server.getTickCount()) return;
            iterator.remove();
            ServerLevel level = key.level();
            if (level.getServer() != server || sight.configRevision() != configRevision
                    || level.getGameTime() - sight.seenTick() >= REPORT_TICKS) continue;
            if (!(level.getEntity(key.uuid()) instanceof Mob source) || source.getId() != sight.sourceEntityId()
                    || !(level.getEntity(sight.targetId()) instanceof LivingEntity target)
                    || target.getId() != sight.targetEntityId() || !validTarget(source, target)
                    || LivingWallCoordinator.isControlled(source)) continue;
            if (!sight.scout()) {
                LivingChangeTargetEvent event = sight.event();
                if (event.isCanceled() || event.getNewAboutToBeSetTarget() != target || source.getTarget() != target) continue;
                Lease previous = removeLease(key);
                if (previous != null && !matchesLease(previous, source, target)) putLease(key, previous);
            }
            recordConfirmedSight(source, target, sight.position(), sight.seenTick(), sight.scout());
        }
    }

    private static void recordConfirmedSight(Mob source, LivingEntity target, BlockPos position, long seenTick, boolean scout) {
        ServerLevel level = (ServerLevel) source.level();
        Mob leader = MobGroupManager.getLeader(source);
        MobGroupManager.GroupRoster roster = MobGroupManager.getGroupRoster(leader);
        if (roster != null && leader.isAlive() && leader.level() == level && withinGroup(source, leader)) {
            MemberKey key = new MemberKey(level, leader.getUUID());
            Report report = reports.get(key);
            if (report != null && report.active(seenTick) && report.rosterEpoch == roster.epoch()
                    && report.leaderEntityId == leader.getId()) {
                if (report.targetEntityId == target.getId() && report.targetId.equals(target.getUUID())) {
                    if (seenTick - report.observedTick < COALESCE_TICKS) return;
                    report.observe(source, position, seenTick, scout);
                    publishSight(level, position, seenTick, source, target);
                    return;
                }
                if (source != leader && level.getEntity(report.targetId) instanceof LivingEntity previous
                        && previous.getId() == report.targetEntityId && isLiveTarget(level, previous)) return;
            }
            if (report == null && reports.size() >= MAX_REPORTS) return;
            Report replacement = new Report(++nextReportRevision, roster.epoch(), leader.getId(), target);
            replacement.observe(source, position, seenTick, scout);
            reports.put(key, replacement);
            if (scout && RaidManager.isRaidAdvancing(leader)
                    && "redirect".equalsIgnoreCase(SoundAttractConfig.COMMON.raidEdgeDetectionAction.get())) {
                RaidManager.updateRaidTarget(leader, position);
            }
        }
        publishSight(level, position, seenTick, source, target);
    }

    private static void publishSight(ServerLevel level, BlockPos position, long seenTick, Mob source, LivingEntity target) {
        Consumer<ConfirmedSight> listener = sightListener;
        if (listener != null) listener.accept(new ConfirmedSight(level, position, seenTick, source.getUUID(), target.getUUID()));
    }

    private static void advanceReports(MinecraftServer server) {
        for (int i = 0, waiting = 0; i < MAX_ADMISSIONS_PER_TICK && !reports.isEmpty(); i++) {
            Iterator<Map.Entry<MemberKey, Report>> iterator = reports.entrySet().iterator();
            Map.Entry<MemberKey, Report> entry = iterator.next();
            MemberKey key = entry.getKey();
            Report report = entry.getValue();
            iterator.remove();
            ServerLevel level = key.level();
            long now = level.getGameTime();
            if (level.getServer() != server || !report.active(now)
                    || !(level.getEntity(key.uuid()) instanceof Mob leader) || !leader.isAlive()
                    || leader.getId() != report.leaderEntityId
                    || !(level.getEntity(report.targetId) instanceof LivingEntity target)
                    || target.getId() != report.targetEntityId || !isLiveTarget(level, target)) continue;
            MobGroupManager.GroupRoster roster = MobGroupManager.getGroupRoster(leader);
            if (roster == null || roster.epoch() != report.rosterEpoch) continue;
            reports.put(key, report);
            if (now >= report.nextObservationTick) {
                report.nextObservationTick = now + OBSERVATION_INTERVAL / 2;
                refreshReport(level, now, report, leader, target);
                waiting = 0;
                continue;
            }
            if (report.cursor > roster.followers().size()) {
                if (now < report.nextRescanTick) {
                    if (++waiting >= reports.size()) return;
                    continue;
                }
                report.cursor = 0;
            }
            waiting = 0;
            int index = report.cursor++;
            UUID memberId = index == 0 ? key.uuid() : roster.followers().get(index - 1);
            if (report.cursor > roster.followers().size()) report.nextRescanTick = now + RESCAN_INTERVAL;
            if (memberId.equals(report.sourceId)
                    || report.vetoedMembers != null && report.vetoedMembers.contains(memberId)) continue;
            if (level.getEntity(memberId) instanceof Mob member) tryAdmit(key, report, leader, member, target);
        }
    }

    private static void refreshReport(ServerLevel level, long now, Report report, Mob leader, LivingEntity target) {
        if (!(level.getEntity(report.sourceId) instanceof Mob source) || source.getId() != report.sourceEntityId
                || MobGroupManager.getLeader(source) != leader || !withinGroup(source, leader)
                || LivingWallCoordinator.isControlled(source) || ownsTarget(source, target)
                || (report.scout ? source.getTarget() != null : source.getTarget() != target)
                || !validTarget(source, target)
                || !StealthDetectionEvents.hasDirectGroupSight(source, target)) return;
        BlockPos position = target.blockPosition();
        report.observe(source, position, now, report.scout);
        publishSight(level, position, now, source, target);
        if (report.scout && RaidManager.isRaidAdvancing(leader)
                && "redirect".equalsIgnoreCase(SoundAttractConfig.COMMON.raidEdgeDetectionAction.get())
                && !position.equals(RaidManager.getRaidTarget(leader))) {
            RaidManager.updateRaidTarget(leader, position);
        }
    }

    private static void tryAdmit(MemberKey group, Report report, Mob leader, Mob member, LivingEntity target) {
        if (leases.size() >= MAX_LEASES || member.getTarget() != null || !validTarget(member, target)
                || MobGroupManager.getLeader(member) != leader || !withinGroup(member, leader)
                || member.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                || hasActiveRetaliation(member) || !supportsSharedMelee(member)
                || !canShareDuringRaid(leader) || LivingWallCoordinator.isControlled(member)
                || !StealthDetectionEvents.canReceiveSharedTarget(member, target)) return;
        ShareAssignment previous = assignment.get();
        assignment.set(new ShareAssignment(member, target));
        try {
            member.setTarget(target);
        } finally {
            if (previous == null) assignment.remove();
            else assignment.set(previous);
        }
        if (member.getTarget() == target) {
            putLease(new MemberKey(group.level(), member.getUUID()), new Lease(group, report, member));
        } else {
            if (report.vetoedMembers == null) report.vetoedMembers = new HashSet<>();
            report.vetoedMembers.add(member.getUUID());
        }
    }

    private static void checkLeases(MinecraftServer server) {
        int checks = Math.min(MAX_LEASE_CHECKS_PER_TICK, leases.size());
        for (int i = 0; i < checks && !leases.isEmpty(); i++) {
            MemberKey key = leases.firstKey();
            Lease lease = leases.getAndMoveToLast(key);
            ServerLevel level = key.level();
            if (level.getServer() != server || !(level.getEntity(key.uuid()) instanceof Mob member)) {
                removeLease(key, lease);
                continue;
            }
            LivingEntity target = member.getTarget();
            if (target == null || !matchesLease(lease, member, target) || retaliationOwnsTarget(member, target, lease)) {
                removeLease(key, lease);
                continue;
            }
            if (!validateLease(level, member, target, lease) && level.getGameTime() >= lease.nextClearAttempt) {
                lease.nextClearAttempt = level.getGameTime() + 5;
                member.setTarget(null);
                if (member.getTarget() != target) removeLease(key, lease);
            }
        }
    }

    private static boolean validateLease(ServerLevel level, Mob member, LivingEntity target, Lease lease) {
        long now = level.getGameTime();
        Report report = reports.get(lease.group);
        if (report == null || !report.active(now) || report.revision != lease.reportRevision
                || !validTarget(member, target) || level.getEntity(lease.targetId) != target
                || hasNewRetaliation(member, lease) || LivingWallCoordinator.isControlled(member)) return false;
        Mob leader = MobGroupManager.getLeader(member);
        MobGroupManager.GroupRoster roster = MobGroupManager.getGroupRoster(leader);
        if (leader == null || leader.getId() != report.leaderEntityId || !leader.isAlive()
                || roster == null || roster.epoch() != lease.rosterEpoch || !withinGroup(member, leader)
                || !canShareDuringRaid(leader)) return false;
        if (lease.validatedTick == now && lease.validatedConfigRevision == configRevision
                && lease.memberX == member.getX() && lease.memberY == member.getY()
                && lease.memberZ == member.getZ() && lease.targetX == target.getX() && lease.targetY == target.getY()
                && lease.targetZ == target.getZ()) return lease.allowed;
        lease.validatedTick = now;
        lease.validatedConfigRevision = configRevision;
        lease.memberX = member.getX();
        lease.memberY = member.getY();
        lease.memberZ = member.getZ();
        lease.targetX = target.getX();
        lease.targetY = target.getY();
        lease.targetZ = target.getZ();
        lease.allowed = StealthDetectionEvents.canReceiveSharedTarget(member, target);
        return lease.allowed;
    }

    private static boolean matchesLease(Lease lease, Mob member, LivingEntity target) {
        return lease.memberEntityId == member.getId() && lease.targetEntityId == target.getId()
                && lease.targetId.equals(target.getUUID());
    }

    private static boolean validTarget(Mob mob, LivingEntity target) {
        return target != mob && mob.isAlive() && mob.level() instanceof ServerLevel level && isLiveTarget(level, target)
                && !mob.isAlliedTo(target) && mob.canAttack(target);
    }

    private static boolean isLiveTarget(ServerLevel level, LivingEntity target) {
        return target.isAlive() && !target.isRemoved() && target.level() == level
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    private static boolean supportsSharedMelee(Mob mob) {
        if (mob instanceof RangedAttackMob || !(mob.getNavigation() instanceof GroundPathNavigation)
                || !EntityType.getKey(mob.getType()).getNamespace().equals("minecraft")
                || !isAttracted(mob) || SoundAttractConfig.isStealthBypassed(mob)) return false;
        return mob.goalSelector.getAvailableGoals().stream().anyMatch(goal -> goal.getGoal() instanceof MeleeAttackGoal);
    }

    private static boolean isAttracted(Mob mob) {
        return !SoundAttractionEvents.getCachedBlacklistedEntityTypes().contains(mob.getType())
                && (SoundAttractionEvents.getCachedAttractedEntityTypes().contains(mob.getType())
                    || SoundAttractConfig.getMatchingProfile(mob) != null || SoundAttractionEvents.isCustomNpcsMob(mob));
    }

    private static boolean withinGroup(Mob member, Mob leader) {
        double range = SoundAttractConfig.COMMON.groupDistance.get();
        return member.distanceToSqr(leader) <= range * range;
    }

    private static boolean canShareDuringRaid(Mob leader) {
        return !RaidManager.isRaidTicking(leader) && (!RaidManager.isRaidAdvancing(leader)
                || SoundAttractConfig.COMMON.raidFollowerInheritTarget.get());
    }

    private static boolean hasActiveRetaliation(Mob mob) {
        LivingEntity attacker = mob.getLastHurtByMob();
        return attacker != null && attacker.isAlive() && mob.tickCount - mob.getLastHurtByMobTimestamp() < 200;
    }

    private static boolean hasNewRetaliation(Mob mob, Lease lease) {
        return mob.getLastHurtByMobTimestamp() != lease.lastHurtTimestamp && hasActiveRetaliation(mob);
    }

    private static boolean retaliationOwnsTarget(Mob mob, LivingEntity target, Lease lease) {
        return mob.getLastHurtByMob() == target && hasNewRetaliation(mob, lease);
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob)) return;
        Entity entity = event.getEntity();
        MemberKey key = new MemberKey(level, entity.getUUID());
        pending.remove(key);
        removeLease(key);
        leaseMembers.remove((Mob) entity);
        reports.remove(key);
        spotters.remove(key);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        pending.keySet().removeIf(key -> key.level() == level);
        reports.keySet().removeIf(key -> key.level() == level);
        leases.keySet().removeIf(key -> {
            if (key.level() != level) return false;
            if (level.getEntity(key.uuid()) instanceof Mob member) leaseMembers.remove(member);
            return true;
        });
        spotters.keySet().removeIf(key -> key.level() == level);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        pending.clear();
        reports.clear();
        leases.clear();
        leaseMembers.clear();
        spotters.clear();
        assignment.remove();
    }
}
