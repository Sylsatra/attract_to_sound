package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import com.mojang.serialization.Codec;
import net.minecraft.util.datafix.DataFixTypes;
public final class LivingBridgeRouteData extends SavedData {
    static final String DATA_ID = "living_bridge_routes";
    static final int CURRENT_SCHEMA = 2;
    private static final Codec<LivingBridgeRouteData> CODEC = CompoundTag.CODEC.xmap(
            LivingBridgeRouteData::load, data -> data.save(new CompoundTag(), null));

    private final Map<UUID, LivingBridgeRouteSnapshot> reservations = new HashMap<>();
    private final Map<UUID, CompoundTag> transitionJournal = new HashMap<>();
    private final List<CompoundTag> quarantineLedger = new ArrayList<>();
    private final List<String> dimensionBarriers = new ArrayList<>();
    private CompoundTag preservedRoot = new CompoundTag();
    private boolean opaqueFutureSchema;

    enum CopyAgreement {
        EXACT,
        JOURNALED_ADJACENT,
        MISSING_SAVED_COPY,
        DIVERGENCE
    }

    static LivingBridgeRouteData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedDataType<>(net.minecraft.resources.Identifier.fromNamespaceAndPath(SoundAttractMod.MOD_ID, DATA_ID), LivingBridgeRouteData::new, CODEC, null));
    }

    static LivingBridgeRouteData load(CompoundTag root) {
        LivingBridgeRouteData data = new LivingBridgeRouteData();
        if (root.isEmpty()) return data;
        if (!(root.get("schema") instanceof net.minecraft.nbt.IntTag)) {
            data.quarantineRoot("OPAQUE_AMBIGUOUS_ROOT", root);
            return data;
        }
        int schema = root.getIntOr("schema", 0);
        if (schema > CURRENT_SCHEMA) {
            data.opaqueFutureSchema = true;
            data.preservedRoot = root.copy();
            data.dimensionBarriers.add("*");
            return data;
        }
        if (schema == 0 && root.size() == 1) return data;
        if (schema == 1) {
            data.quarantineRoot("OPAQUE_V1_SAVED_DATA", root);
            return data;
        }
        if (schema != CURRENT_SCHEMA) {
            data.quarantineRoot("OPAQUE_AMBIGUOUS_ROOT", root);
            return data;
        }
        data.preservedRoot = root.copy();
        ListTag rows = root.getListOrEmpty("reservations");
        for (int index = 0; index < rows.size(); index++) {
            CompoundTag raw = rows.getCompoundOrEmpty(index);
            try {
                LivingBridgeRouteSnapshot snapshot = LivingBridgeRouteSnapshot.fromTag(raw);
                if (data.reservations.putIfAbsent(snapshot.reservationId(), snapshot) != null) {
                    data.quarantineLedger.add(rawQuarantine("duplicate_reservation_id", raw));
                    data.dimensionBarriers.add("*");
                }
            } catch (RuntimeException malformed) {
                data.quarantineLedger.add(rawQuarantine("malformed_saved_snapshot", raw));
                data.dimensionBarriers.add("*");
            }
        }
        ListTag quarantined = root.getListOrEmpty("quarantineLedger");
        for (int index = 0; index < quarantined.size(); index++) {
            data.quarantineLedger.add(quarantined.getCompoundOrEmpty(index).copy());
        }
        ListTag journal = root.getListOrEmpty("transitionJournal");
        for (int index = 0; index < journal.size(); index++) {
            CompoundTag row = journal.getCompoundOrEmpty(index);
            if (!com.example.soundattract.NbtCompat.hasUuid(row, "reservationId") || data.transitionJournal.size() >= 512) {
                data.quarantineLedger.add(rawQuarantine("malformed_transition_journal", row));
                data.dimensionBarriers.add("*");
                continue;
            }
            data.transitionJournal.putIfAbsent(com.example.soundattract.NbtCompat.getUuid(row, "reservationId"), row.copy());
        }
        ListTag barriers = root.getListOrEmpty("dimensionBarriers");
        for (int index = 0; index < barriers.size(); index++) {
            String dimension = barriers.getCompoundOrEmpty(index).getStringOr("dimension", "");
            if (!dimension.isBlank() && !data.dimensionBarriers.contains(dimension)) data.dimensionBarriers.add(dimension);
        }
        ListTag globalBarriers = root.getListOrEmpty("globalQuarantine");
        for (int index = 0; index < globalBarriers.size(); index++) {
            String dimension = globalBarriers.getCompoundOrEmpty(index).getStringOr("dimension", "");
            if (!dimension.isBlank() && !data.dimensionBarriers.contains(dimension)) data.dimensionBarriers.add(dimension);
        }
        return data;
    }

    private void quarantineRoot(String classification, CompoundTag root) {
        preservedRoot = new CompoundTag();
        CompoundTag entry = rawQuarantine(classification, root);
        quarantineLedger.add(entry);
        dimensionBarriers.clear();
        dimensionBarriers.add("*");
        setDirty();
        SoundAttractMod.LOGGER.warn("[LivingBridgeRouteData] persistence_quarantine classification={} conflictId={} scope=global resolution=operator_review_or_verified_safe_recovery",
                classification, entry.getStringOr("conflictId", ""));
    }

    LivingBridgeRouteSnapshot snapshot(UUID reservationId) {
        return reservations.get(reservationId);
    }

    static LivingBridgeRouteSnapshot dockedSnapshot(LivingBridgeRouteSnapshot current,
                                                      LivingBridgeRouteSnapshot.Point safeEndpoint,
                                                      int lastChunkX, int lastChunkZ) {
        if (current == null || safeEndpoint == null) {
            throw new IllegalArgumentException("a terminal dock requires a verified safe endpoint");
        }
        long generation = Math.addExact(current.commitGeneration(), 1L);
        UUID transactionId = UUID.nameUUIDFromBytes((current.reservationId() + ":docked:" + generation)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new LivingBridgeRouteSnapshot(current.reservationId(), current.ownerId(), current.formationId(),
                current.dimension(), generation, transactionId, current.routeRevision(), "DOCKED", "DOCKED",
                current.originalNoPhysics(), current.originalNoGravity(), false, current.corridor(),
                current.halfWidth(), current.recoveryRoute(), current.recoveryIndex(), safeEndpoint,
                lastChunkX, lastChunkZ);
    }

    static LivingBridgeRouteSnapshot safeRecoverySnapshot(LivingBridgeRouteSnapshot current,
                                                           int lastChunkX, int lastChunkZ) {
        if (current == null || current.safeEndpoint() == null) {
            throw new IllegalArgumentException("safe recovery requires a persisted endpoint");
        }
        long generation = Math.addExact(current.commitGeneration(), 1L);
        UUID transactionId = UUID.nameUUIDFromBytes((current.reservationId() + ":safe-recovery:" + generation)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new LivingBridgeRouteSnapshot(current.reservationId(), current.ownerId(), current.formationId(),
                current.dimension(), generation, transactionId, current.routeRevision(),
                "SAFE_RECOVERY_COMPLETE", "SAFE_RECOVERY_COMPLETE", current.originalNoPhysics(),
                current.originalNoGravity(), false, current.corridor(), current.halfWidth(),
                current.recoveryRoute(), current.recoveryIndex(), current.safeEndpoint(), lastChunkX, lastChunkZ);
    }

    static LivingBridgeRouteSnapshot deadSnapshot(LivingBridgeRouteSnapshot current,
                                                   int lastChunkX, int lastChunkZ) {
        if (current == null) throw new IllegalArgumentException("death receipt requires an owned route snapshot");
        long generation = Math.addExact(current.commitGeneration(), 1L);
        UUID transactionId = UUID.nameUUIDFromBytes((current.reservationId() + ":dead:" + generation)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new LivingBridgeRouteSnapshot(current.reservationId(), current.ownerId(), current.formationId(),
                current.dimension(), generation, transactionId, current.routeRevision(), "DEAD", "DEAD",
                current.originalNoPhysics(), current.originalNoGravity(), false, current.corridor(),
                current.halfWidth(), current.recoveryRoute(), current.recoveryIndex(), current.safeEndpoint(),
                lastChunkX, lastChunkZ);
    }

    static LivingBridgeRouteSnapshot removedSnapshot(LivingBridgeRouteSnapshot current,
                                                      int lastChunkX, int lastChunkZ) {
        if (current == null) throw new IllegalArgumentException("removal receipt requires an owned route snapshot");
        long generation = Math.addExact(current.commitGeneration(), 1L);
        UUID transactionId = UUID.nameUUIDFromBytes((current.reservationId() + ":removed:" + generation)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new LivingBridgeRouteSnapshot(current.reservationId(), current.ownerId(), current.formationId(),
                current.dimension(), generation, transactionId, current.routeRevision(), "REMOVED", "REMOVED",
                current.originalNoPhysics(), current.originalNoGravity(), false, current.corridor(),
                current.halfWidth(), current.recoveryRoute(), current.recoveryIndex(), current.safeEndpoint(),
                lastChunkX, lastChunkZ);
    }

    static LivingBridgeRouteSnapshot recoveringSnapshot(LivingBridgeRouteSnapshot current,
                                                         List<LivingBridgeRouteSnapshot.Point> route,
                                                         int nextIndex, int lastChunkX, int lastChunkZ) {
        if (current == null || current.safeEndpoint() == null || route == null || route.size() < 2
                || route.size() > 65 || nextIndex < 1 || nextIndex > route.size()) {
            throw new IllegalArgumentException("recovery route is absent or exceeds its bounded contract");
        }
        long generation = Math.addExact(current.commitGeneration(), 1L);
        UUID transactionId = UUID.nameUUIDFromBytes((current.reservationId() + ":recovering:" + generation)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new LivingBridgeRouteSnapshot(current.reservationId(), current.ownerId(), current.formationId(),
                current.dimension(), generation, transactionId, current.routeRevision(),
                "RECOVERING_TO_SAFE_ENDPOINT", "RECOVERING_TO_SAFE_ENDPOINT", current.originalNoPhysics(),
                current.originalNoGravity(), true, route, current.halfWidth(), route, nextIndex,
                current.safeEndpoint(), lastChunkX, lastChunkZ);
    }

    boolean hasConflictingLane(String dimension, UUID ownerId, UUID reservationId,
                               List<net.minecraft.core.BlockPos> corridor,
                               double bodyWidth, double spacing) {
        if (dimension == null || dimension.isBlank() || ownerId == null || reservationId == null || corridor == null
                || corridor.isEmpty() || !Double.isFinite(bodyWidth) || bodyWidth <= 0.0D
                || !Double.isFinite(spacing) || spacing < 0.0D || opaqueFutureSchema
                || admissionBlocked(dimension)) return true;
        return hasConflictingKnownReservation(dimension, ownerId, reservationId, corridor, bodyWidth, spacing);
    }


    boolean hasConflictingRecoveryLane(LivingBridgeRouteSnapshot owner,
                                       List<net.minecraft.core.BlockPos> corridor,
                                       double bodyWidth, double spacing) {
        if (!canRecoverOwner(owner) || corridor == null
                || corridor.isEmpty() || !Double.isFinite(bodyWidth) || bodyWidth <= 0.0D
                || !Double.isFinite(spacing) || spacing < 0.0D) return true;
        return hasConflictingKnownReservation(owner.dimension(), owner.ownerId(), owner.reservationId(),
                corridor, bodyWidth, spacing);
    }

    boolean canRecoverOwner(LivingBridgeRouteSnapshot owner) {
        if (owner == null || opaqueFutureSchema || dimensionBarriers.contains("*")) return false;
        LivingBridgeRouteSnapshot saved = reservations.get(owner.reservationId());
        return saved != null && saved.exactlyEquals(owner) && saved.ownerId().equals(owner.ownerId())
                && saved.dimension().equals(owner.dimension()) && !isTerminalLaneState(saved.lifecycleState());
    }

    private boolean hasConflictingKnownReservation(String dimension, UUID ownerId, UUID reservationId,
                                                    List<net.minecraft.core.BlockPos> corridor,
                                                    double bodyWidth, double spacing) {
        for (LivingBridgeRouteSnapshot reservation : reservations.values()) {
            if (!dimension.equalsIgnoreCase(reservation.dimension())
                    || reservationId.equals(reservation.reservationId())
                    || isTerminalLaneState(reservation.lifecycleState())) continue;
            List<net.minecraft.core.BlockPos> durableCorridor = reservation.corridor().stream()
                    .map(point -> net.minecraft.core.BlockPos.containing(
                            point.x() / 4096.0D, point.y() / 4096.0D, point.z() / 4096.0D))
                    .toList();
            double durableBodyWidth = reservation.halfWidth() * 2.0D / 4096.0D;
            if (LivingBridgeSafeBaseRoute.laneCorridorsConflict(corridor, bodyWidth,
                    durableCorridor, durableBodyWidth, spacing)) return true;
        }
        return false;
    }

    static boolean isTerminalLaneState(String lifecycleState) {
        return switch (lifecycleState) {
            case "DOCKED", "SAFE_RECOVERY_COMPLETE", "CANCELLED", "DEAD", "REMOVED",
                    "FORMATION_CLEANUP" -> true;
            default -> false;
        };
    }

    void putSnapshot(LivingBridgeRouteSnapshot snapshot) {
        if (opaqueFutureSchema) throw new IllegalStateException("future SavedData is read-only");
        reservations.put(snapshot.reservationId(), snapshot);
        setDirty();
    }

    boolean beginInitialClaim(LivingBridgeRouteSnapshot snapshot, Mob mob) {
        if (opaqueFutureSchema || admissionBlocked(snapshot.dimension())) return false;
        LivingBridgeRouteSnapshot existing = reservations.get(snapshot.reservationId());
        if (existing != null) {
            CompoundTag existingCapsule = com.example.soundattract.component.LivingBridgeEntityData.get(mob).getCompoundOrEmpty("livingBridgeControl");
            try {
                LivingBridgeRouteSnapshot capsuleSnapshot = LivingBridgeRouteSnapshot.fromTag(
                        existingCapsule.getCompoundOrEmpty("snapshot"));
                return existing.exactlyEquals(snapshot) && capsuleSnapshot.exactlyEquals(snapshot);
            } catch (RuntimeException malformed) {
                quarantine("REUSED_RESERVATION_CONFLICT", existing.toTag(),
                        existingCapsule, snapshot.dimension());
                return false;
            }
        }
        if (transitionJournal.containsKey(snapshot.reservationId())) return false;
        return commitTransition(null, snapshot, mob);
    }


    boolean commitTransition(LivingBridgeRouteSnapshot from, LivingBridgeRouteSnapshot to, Mob mob) {
        return commitTransition(from, to, mob, false);
    }

    boolean commitDockedTransition(LivingBridgeRouteSnapshot from, LivingBridgeRouteSnapshot to, Mob mob) {
        if (from == null || to == null || !"DOCKED".equals(to.lifecycleState())
                || !"DOCKED".equals(to.movementMode()) || to.recoveryRequired() || to.safeEndpoint() == null
                || mob == null || !(com.example.soundattract.component.LivingBridgeEntityData.get(mob).get("livingBridgeControl") instanceof net.minecraft.nbt.CompoundTag)) {
            return false;
        }
        CompoundTag capsule = com.example.soundattract.component.LivingBridgeEntityData.get(mob).getCompoundOrEmpty("livingBridgeControl");
        try {
            LivingBridgeRouteSnapshot entity = LivingBridgeRouteSnapshot.fromTag(capsule.getCompoundOrEmpty("snapshot"));
            if (capsule.getIntOr("schema", 0) != CURRENT_SCHEMA || !from.exactlyEquals(entity)
                    || !entity.digest().equals(capsule.getStringOr("snapshotDigest", ""))) return false;
        } catch (RuntimeException malformed) {
            return false;
        }
        return commitTransition(from, to, mob, true);
    }

    boolean commitSafeRecoveryTransition(LivingBridgeRouteSnapshot from, LivingBridgeRouteSnapshot to, Mob mob) {
        if (from == null || to == null || !"SAFE_RECOVERY_COMPLETE".equals(to.lifecycleState())
                || !"SAFE_RECOVERY_COMPLETE".equals(to.movementMode()) || to.recoveryRequired()
                || to.safeEndpoint() == null || mob == null
                || !(com.example.soundattract.component.LivingBridgeEntityData.get(mob).get("livingBridgeControl") instanceof net.minecraft.nbt.CompoundTag)) return false;
        CompoundTag capsule = com.example.soundattract.component.LivingBridgeEntityData.get(mob).getCompoundOrEmpty("livingBridgeControl");
        try {
            LivingBridgeRouteSnapshot entity = LivingBridgeRouteSnapshot.fromTag(capsule.getCompoundOrEmpty("snapshot"));
            if (capsule.getIntOr("schema", 0) != CURRENT_SCHEMA || !from.exactlyEquals(entity)
                    || !entity.digest().equals(capsule.getStringOr("snapshotDigest", ""))) return false;
        } catch (RuntimeException malformed) {
            return false;
        }
        return commitTransition(from, to, mob, true);
    }

    boolean commitDeathTransition(LivingBridgeRouteSnapshot from, LivingBridgeRouteSnapshot to, Mob mob) {
        if (from == null || to == null || !"DEAD".equals(to.lifecycleState())
                || !"DEAD".equals(to.movementMode()) || to.recoveryRequired() || mob == null
                || !(com.example.soundattract.component.LivingBridgeEntityData.get(mob).get("livingBridgeControl") instanceof net.minecraft.nbt.CompoundTag)) return false;
        CompoundTag capsule = com.example.soundattract.component.LivingBridgeEntityData.get(mob).getCompoundOrEmpty("livingBridgeControl");
        try {
            LivingBridgeRouteSnapshot entity = LivingBridgeRouteSnapshot.fromTag(capsule.getCompoundOrEmpty("snapshot"));
            if (!(capsule.get("schema") instanceof net.minecraft.nbt.IntTag) || capsule.getIntOr("schema", 0) != CURRENT_SCHEMA
                    || !from.exactlyEquals(entity) || !entity.digest().equals(capsule.getStringOr("snapshotDigest", ""))) {
                return false;
            }
        } catch (RuntimeException malformed) {
            return false;
        }
        return commitTransition(from, to, mob, true);
    }

    boolean commitRemovedTransition(LivingBridgeRouteSnapshot from, LivingBridgeRouteSnapshot to, Mob mob) {
        if (from == null || to == null || !"REMOVED".equals(to.lifecycleState())
                || !"REMOVED".equals(to.movementMode()) || to.recoveryRequired() || mob == null
                || mob.getRemovalReason() != Entity.RemovalReason.DISCARDED
                || !(com.example.soundattract.component.LivingBridgeEntityData.get(mob).get("livingBridgeControl") instanceof net.minecraft.nbt.CompoundTag)) return false;
        CompoundTag capsule = com.example.soundattract.component.LivingBridgeEntityData.get(mob).getCompoundOrEmpty("livingBridgeControl");
        try {
            LivingBridgeRouteSnapshot entity = LivingBridgeRouteSnapshot.fromTag(capsule.getCompoundOrEmpty("snapshot"));
            if (!(capsule.get("schema") instanceof net.minecraft.nbt.IntTag) || capsule.getIntOr("schema", 0) != CURRENT_SCHEMA
                    || !from.exactlyEquals(entity) || !entity.digest().equals(capsule.getStringOr("snapshotDigest", ""))) {
                return false;
            }
        } catch (RuntimeException malformed) {
            return false;
        }
        return commitTransition(from, to, mob, true);
    }

    boolean retireDeadOwner(Mob mob) {
        if (mob == null || !(mob.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || opaqueFutureSchema) return false;
        CompoundTag entityData = com.example.soundattract.component.LivingBridgeEntityData.get(mob);
        if (!(entityData.get("livingBridgeControl") instanceof net.minecraft.nbt.CompoundTag)) return false;
        CompoundTag capsule = entityData.getCompoundOrEmpty("livingBridgeControl");
        if (!(capsule.get("schema") instanceof net.minecraft.nbt.IntTag) || capsule.getIntOr("schema", 0) != CURRENT_SCHEMA) return false;
        try {
            LivingBridgeRouteSnapshot current = LivingBridgeRouteSnapshot.fromTag(capsule.getCompoundOrEmpty("snapshot"));
            LivingBridgeRouteSnapshot saved = reservations.get(current.reservationId());
            if (saved == null || !saved.exactlyEquals(current) || !current.ownerId().equals(mob.getUUID())
                    || !current.dimension().equals(level.dimension().identifier().toString())) return false;
            if (isTerminalLaneState(current.lifecycleState())) return true;
            LivingBridgeRouteSnapshot terminal = deadSnapshot(current, mob.chunkPosition().x(), mob.chunkPosition().z());
            return commitDeathTransition(current, terminal, mob);
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    boolean retireRemovedOwner(Mob mob) {
        if (mob == null || mob.getRemovalReason() != Entity.RemovalReason.DISCARDED
                || !(mob.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || opaqueFutureSchema) return false;
        CompoundTag entityData = com.example.soundattract.component.LivingBridgeEntityData.get(mob);
        if (!(entityData.get("livingBridgeControl") instanceof net.minecraft.nbt.CompoundTag)) return false;
        CompoundTag capsule = entityData.getCompoundOrEmpty("livingBridgeControl");
        if (!(capsule.get("schema") instanceof net.minecraft.nbt.IntTag) || capsule.getIntOr("schema", 0) != CURRENT_SCHEMA) return false;
        try {
            LivingBridgeRouteSnapshot current = LivingBridgeRouteSnapshot.fromTag(capsule.getCompoundOrEmpty("snapshot"));
            LivingBridgeRouteSnapshot saved = reservations.get(current.reservationId());
            if (saved == null || !saved.exactlyEquals(current) || !current.ownerId().equals(mob.getUUID())
                    || !current.dimension().equals(level.dimension().identifier().toString())) return false;
            if (isTerminalLaneState(current.lifecycleState())) return true;
            LivingBridgeRouteSnapshot terminal = removedSnapshot(current, mob.chunkPosition().x(), mob.chunkPosition().z());
            return commitRemovedTransition(current, terminal, mob);
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    boolean commitRecoveryTransition(LivingBridgeRouteSnapshot from, LivingBridgeRouteSnapshot to, Mob mob) {
        if (from == null || to == null || !"RECOVERING_TO_SAFE_ENDPOINT".equals(to.lifecycleState())
                || !"RECOVERING_TO_SAFE_ENDPOINT".equals(to.movementMode()) || !to.recoveryRequired()
                || to.safeEndpoint() == null || to.recoveryRoute().size() < 2
                || mob == null || !(com.example.soundattract.component.LivingBridgeEntityData.get(mob).get("livingBridgeControl") instanceof net.minecraft.nbt.CompoundTag)) {
            return false;
        }
        CompoundTag capsule = com.example.soundattract.component.LivingBridgeEntityData.get(mob).getCompoundOrEmpty("livingBridgeControl");
        try {
            LivingBridgeRouteSnapshot entity = LivingBridgeRouteSnapshot.fromTag(capsule.getCompoundOrEmpty("snapshot"));
            if (capsule.getIntOr("schema", 0) != CURRENT_SCHEMA || !from.exactlyEquals(entity)
                    || !entity.digest().equals(capsule.getStringOr("snapshotDigest", ""))) return false;
        } catch (RuntimeException malformed) {
            return false;
        }
        return commitTransition(from, to, mob, true);
    }

    private boolean commitTransition(LivingBridgeRouteSnapshot from, LivingBridgeRouteSnapshot to, Mob mob,
                                     boolean safeDockTerminal) {
        if (opaqueFutureSchema || to == null || mob == null
                || !(mob.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread()
                || (!safeDockTerminal && admissionBlocked(to.dimension())) || transitionJournal.size() >= 512
                || !to.ownerId().equals(mob.getUUID())
                || (from == null ? reservations.containsKey(to.reservationId())
                : reservations.get(to.reservationId()) == null
                || !reservations.get(to.reservationId()).exactlyEquals(from)
                || !from.reservationId().equals(to.reservationId())
                || !from.ownerId().equals(to.ownerId())
                || to.commitGeneration() <= from.commitGeneration())) return false;
        CompoundTag journal = new CompoundTag();
        com.example.soundattract.NbtCompat.putUuid(journal, "reservationId", to.reservationId());
        com.example.soundattract.NbtCompat.putUuid(journal, "transactionId", to.transactionId());
        journal.putLong("commitGeneration", to.commitGeneration());
        journal.putBoolean("hasFrom", from != null);
        if (from != null) {
            journal.put("from", from.toTag());
            journal.putString("fromDigest", from.digest());
        }
        journal.put("to", to.toTag());
        journal.putString("toDigest", to.digest());
        transitionJournal.put(to.reservationId(), journal);
        setDirty();

        CompoundTag capsule = new CompoundTag();
        capsule.putInt("schema", CURRENT_SCHEMA);
        capsule.put("snapshot", to.toTag());
        capsule.putString("snapshotDigest", to.digest());
        com.example.soundattract.component.LivingBridgeEntityData.get(mob).put("livingBridgeControl", capsule);
        reservations.put(to.reservationId(), to);
        setDirty();
        return true;
    }

    static CopyAgreement compareCopies(LivingBridgeRouteSnapshot saved,
                                       LivingBridgeRouteSnapshot entity, CompoundTag journal) {
        if (saved != null && saved.exactlyEquals(entity)) return CopyAgreement.EXACT;
        if (saved == null || entity == null || journal == null || !com.example.soundattract.NbtCompat.hasUuid(journal, "reservationId")
                || !com.example.soundattract.NbtCompat.getUuid(journal, "reservationId").equals(saved == null
                ? entity.reservationId() : saved.reservationId()) || !com.example.soundattract.NbtCompat.hasUuid(journal, "transactionId")) {
            return saved == null ? CopyAgreement.MISSING_SAVED_COPY : CopyAgreement.DIVERGENCE;
        }
        try {
            LivingBridgeRouteSnapshot from = journal.getBooleanOr("hasFrom", false)
                    ? LivingBridgeRouteSnapshot.fromTag(journal.getCompoundOrEmpty("from")) : null;
            LivingBridgeRouteSnapshot to = LivingBridgeRouteSnapshot.fromTag(journal.getCompoundOrEmpty("to"));
            if (!to.transactionId().equals(com.example.soundattract.NbtCompat.getUuid(journal, "transactionId"))
                    || to.commitGeneration() != journal.getLongOr("commitGeneration", 0L)
                    || !to.digest().equals(journal.getStringOr("toDigest", ""))
                    || (from != null && !from.digest().equals(journal.getStringOr("fromDigest", "")))) {
                return CopyAgreement.DIVERGENCE;
            }
            boolean adjacent = from != null && ((saved.exactlyEquals(from) && entity.exactlyEquals(to))
                    || (entity.exactlyEquals(from) && saved.exactlyEquals(to)));
            return adjacent ? CopyAgreement.JOURNALED_ADJACENT : CopyAgreement.DIVERGENCE;
        } catch (RuntimeException malformed) {
            return CopyAgreement.DIVERGENCE;
        }
    }

    void quarantine(String classification, CompoundTag firstRaw, CompoundTag secondRaw,
                    String dimension) {
        if (opaqueFutureSchema) {
            if (!dimensionBarriers.contains("*")) dimensionBarriers.add("*");
            return;
        }
        CompoundTag entry = new CompoundTag();
        entry.putString("classification", classification);
        if (firstRaw != null) entry.put("firstRaw", firstRaw.copy());
        if (secondRaw != null) entry.put("secondRaw", secondRaw.copy());
        entry.putString("conflictId", conflictId(classification, firstRaw, secondRaw));
        if (quarantineLedger.stream().noneMatch(existing -> existing.getStringOr("conflictId", "")
                .equals(entry.getStringOr("conflictId", "")))) {
            quarantineLedger.add(entry);
            SoundAttractMod.LOGGER.warn("[LivingBridgeRouteData] persistence_quarantine classification={} conflictId={} scope={} resolution=verified_safe_recovery_or_operator_review",
                    classification, entry.getStringOr("conflictId", ""), dimension == null || dimension.isBlank()
                            ? "global" : dimension);
        }
        if (dimension == null || dimension.isBlank()) {
            if (!dimensionBarriers.contains("*")) dimensionBarriers.add("*");
        } else if (!dimensionBarriers.contains(dimension)) {
            dimensionBarriers.add(dimension);
        }
        setDirty();
    }

    boolean admissionBlocked(String dimension) {
        return dimensionBarriers.contains("*") || dimensionBarriers.contains(dimension);
    }

    boolean opaqueFutureSchema() {
        return opaqueFutureSchema;
    }

    int reservationCount() {
        return reservations.size();
    }

    int quarantineCount() {
        return quarantineLedger.size();
    }

    static CopyAgreement compareCopies(LivingBridgeRouteSnapshot saved,
                                       LivingBridgeRouteSnapshot entity) {
        if (saved == null) return CopyAgreement.MISSING_SAVED_COPY;
        return saved.exactlyEquals(entity) ? CopyAgreement.EXACT : CopyAgreement.DIVERGENCE;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (opaqueFutureSchema) return preservedRoot.copy();
        CompoundTag root = preservedRoot.copy();
        root.remove("schema");
        root.remove("reservations");
        root.remove("quarantineLedger");
        root.remove("dimensionBarriers");
        root.remove("globalQuarantine");
        root.remove("transitionJournal");
        root.putInt("schema", CURRENT_SCHEMA);
        ListTag rows = new ListTag();
        reservations.values().stream()
                .sorted(Comparator.comparing(LivingBridgeRouteSnapshot::reservationId)
                        .thenComparing(LivingBridgeRouteSnapshot::ownerId))
                .forEach(snapshot -> rows.add(snapshot.toTag()));
        root.put("reservations", rows);
        ListTag journal = new ListTag();
        transitionJournal.values().stream()
                .sorted(Comparator.comparing(row -> com.example.soundattract.NbtCompat.getUuid(row, "reservationId")))
                .forEach(row -> journal.add(row.copy()));
        root.put("transitionJournal", journal);
        ListTag quarantined = new ListTag();
        quarantineLedger.stream().sorted(Comparator.comparing(row -> row.getStringOr("conflictId", "")))
                .forEach(raw -> quarantined.add(raw.copy()));
        root.put("quarantineLedger", quarantined);
        ListTag barriers = new ListTag();
        dimensionBarriers.stream().distinct().sorted().forEach(dimension -> {
            CompoundTag barrier = new CompoundTag();
            barrier.putString("dimension", dimension);
            barriers.add(barrier);
        });
        root.put("globalQuarantine", barriers);
        return root;
    }

    private static CompoundTag rawQuarantine(String classification, CompoundTag raw) {
        CompoundTag entry = new CompoundTag();
        entry.putString("classification", classification);
        entry.put("raw", raw.copy());
        entry.putString("conflictId", conflictId(classification, raw, null));
        return entry;
    }

    static String conflictId(String classification, CompoundTag firstRaw, CompoundTag secondRaw) {
        String first = rawDigest(firstRaw);
        String second = rawDigest(secondRaw);
        if (first.compareTo(second) > 0) {
            String swap = first;
            first = second;
            second = swap;
        }
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(
                    (first + "\n" + second + "\n" + classification)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().withUpperCase().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String rawDigest(CompoundTag raw) {
        if (raw == null) return "";
        try {
            return LivingBridgeRouteSnapshot.fromTag(raw).digest();
        } catch (RuntimeException notCanonicalSnapshot) {
            return java.util.HexFormat.of().withUpperCase().formatHex(
                    canonicalTag(raw).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static String canonicalTag(Tag tag) {
        String type = Byte.toString(tag.getId()) + ":";
        if (tag instanceof CompoundTag compound) {
            StringBuilder canonical = new StringBuilder(type).append('{');
            compound.keySet().stream().sorted().forEach(key -> {
                canonical.append(key.length()).append(':').append(key);
                canonical.append(canonicalTag(compound.get(key)));
            });
            return canonical.append('}').toString();
        }
        if (tag instanceof ListTag list) {
            StringBuilder canonical = new StringBuilder(type).append('[');
            for (Tag child : list) canonical.append(canonicalTag(child));
            return canonical.append(']').toString();
        }
        String value = tag.toString();
        return type + value.length() + ':' + value;
    }

    public static void onEntityJoinLevel(ServerLevel level, Entity entity) {
        if (!(entity instanceof Mob mob) || level.getServer() == null
                || !level.getServer().isSameThread()) return;
        CompoundTag entityData = com.example.soundattract.component.LivingBridgeEntityData.get(mob);
        if (!entityData.contains("livingBridgeControl")) return;
        if (!(entityData.get("livingBridgeControl") instanceof net.minecraft.nbt.CompoundTag)) {
            CompoundTag raw = new CompoundTag();
            raw.put("rawCapsule", entityData.get("livingBridgeControl").copy());
            get(level).quarantine("MALFORMED_ENTITY_CAPSULE", raw, null,
                    level.dimension().identifier().toString());
            protectBeforeJoinTick(mob);
            return;
        }
        CompoundTag capsule = entityData.getCompoundOrEmpty("livingBridgeControl");
        LivingBridgeRouteData data = get(level);
        if (!(capsule.get("schema") instanceof net.minecraft.nbt.IntTag)) {
            data.quarantine("MALFORMED_ENTITY_CAPSULE", capsule, null,
                    level.dimension().identifier().toString());
            protectBeforeJoinTick(mob);
            return;
        }
        int schema = capsule.getIntOr("schema", 0);
        if (schema > CURRENT_SCHEMA) {
            data.quarantine("UNKNOWN_FUTURE_ENTITY_SCHEMA", capsule, null,
                    level.dimension().identifier().toString());
            protectBeforeJoinTick(mob);
            return;
        }
        if (schema != CURRENT_SCHEMA) {
            quarantineEntityCapsule(data, capsule, level.dimension().identifier().toString(), schema == 1);
            protectBeforeJoinTick(mob);
            return;
        }
        try {
            LivingBridgeRouteSnapshot entitySnapshot = LivingBridgeRouteSnapshot.fromTag(
                    capsule.getCompoundOrEmpty("snapshot"));
            if (!entitySnapshot.digest().equals(capsule.getStringOr("snapshotDigest", ""))) {
                throw new IllegalArgumentException("entity capsule digest mismatch");
            }
            LivingBridgeRouteSnapshot savedSnapshot = data.snapshot(entitySnapshot.reservationId());
            CompoundTag journal = data.transitionJournal.get(entitySnapshot.reservationId());
            CopyAgreement agreement = compareCopies(savedSnapshot, entitySnapshot, journal);
            if (agreement != CopyAgreement.EXACT) {
                CompoundTag savedRaw = savedSnapshot == null ? new CompoundTag() : savedSnapshot.toTag();
                data.quarantine(agreement == CopyAgreement.JOURNALED_ADJACENT
                                ? "JOURNALED_ADJACENT_UNION_LANE" : "VALID_DIVERGENCE",
                        savedRaw, capsule,
                        entitySnapshot.dimension());
                protectBeforeJoinTick(mob);
                return;
            }
            if (!isTerminalLaneState(entitySnapshot.lifecycleState())) {
                protectBeforeJoinTick(mob);
            }
        } catch (RuntimeException malformed) {
            data.quarantine("MALFORMED_ENTITY_CAPSULE", capsule, null,
                    level.dimension().identifier().toString());
            protectBeforeJoinTick(mob);
        }
    }

    public static void onLivingDeath(Entity entity) {
        if (!(entity instanceof Mob mob)
                || !(mob.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread()) return;
        get(level).retireDeadOwner(mob);
    }

    public static void onEntityLeaveLevel(ServerLevel level, Entity entity) {
        if (!(entity instanceof Mob mob)
                || mob.getRemovalReason() != Entity.RemovalReason.DISCARDED
                || level.getServer() == null
                || !level.getServer().isSameThread()) return;
        get(level).retireRemovedOwner(mob);
    }

    static void quarantineEntityCapsule(LivingBridgeRouteData data, CompoundTag capsule,
                                        String dimension, boolean schemaOne) {
        data.quarantine(schemaOne ? "OPAQUE_V1_ENTITY" : "MALFORMED_ENTITY_CAPSULE",
                capsule, null, dimension);
    }

    private static void protectBeforeJoinTick(Mob mob) {
        mob.setNoGravity(true);
        mob.noPhysics = true;
        mob.getNavigation().stop();
        mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.0D);
        net.minecraft.world.phys.Vec3 velocity = mob.getDeltaMovement();
        mob.setDeltaMovement(0.0D, velocity.y, 0.0D);
    }
}
