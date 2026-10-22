package com.example.soundattract.spatial;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class SpatialContracts {
    private SpatialContracts() {
    }

    public enum RoleState {
        UNKNOWN,
        LEADER,
        FOLLOWER,
        DESERTER,
        ORPHAN_PENDING
    }

    public record TileKey(ResourceLocation dimension, int x, int z, long epoch) {
        public TileKey {
            if (dimension == null) {
                throw new IllegalArgumentException("dimension");
            }
        }
    }

    public record InterestTicket(
            UUID ticketId,
            String ownerKey,
            String interestType,
            TileKey tileKey,
            long createdTick,
            long expiresTick,
            long epoch) {
        public InterestTicket {
            if (ticketId == null) {
                throw new IllegalArgumentException("ticketId");
            }
            if (tileKey == null) {
                throw new IllegalArgumentException("tileKey");
            }
        }
    }

    public record IndexedEntitySnapshot(
            UUID entityUuid,
            long incarnation,
            ResourceLocation dimension,
            TileKey tileKey,
            Vec3 position,
            boolean eligible,
            boolean alive,
            double health,
            UUID leaderUuid,
            UUID groupOwnerUuid,
            long entityEpoch) {
        public IndexedEntitySnapshot {
            if (entityUuid == null) {
                throw new IllegalArgumentException("entityUuid");
            }
            if (dimension == null) {
                throw new IllegalArgumentException("dimension");
            }
            if (tileKey == null) {
                throw new IllegalArgumentException("tileKey");
            }
            position = position == null ? Vec3.ZERO : new Vec3(position.x, position.y, position.z);
        }
    }

    public record SpatialQuerySnapshot(
            ResourceLocation dimension,
            long queryEpoch,
            boolean complete,
            Set<TileKey> queriedTiles,
            List<IndexedEntitySnapshot> entities,
            List<Long> tileVersions) {
        public SpatialQuerySnapshot {
            if (dimension == null) {
                throw new IllegalArgumentException("dimension");
            }
            queriedTiles = queriedTiles == null ? Set.of() : Set.copyOf(queriedTiles);
            entities = entities == null ? List.of() : List.copyOf(entities);
            tileVersions = tileVersions == null ? List.of() : List.copyOf(tileVersions);
        }
    }

    public record GroupPlanningSnapshot(
            ResourceLocation dimension,
            UUID ownerUuid,
            TileKey homeTile,
            long queryEpoch,
            boolean complete,
            List<IndexedEntitySnapshot> candidates,
            Set<TileKey> coveredTiles) {
        public GroupPlanningSnapshot {
            if (dimension == null) {
                throw new IllegalArgumentException("dimension");
            }
            if (ownerUuid == null) {
                throw new IllegalArgumentException("ownerUuid");
            }
            if (homeTile == null) {
                throw new IllegalArgumentException("homeTile");
            }
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            coveredTiles = coveredTiles == null ? Set.of() : Set.copyOf(coveredTiles);
        }
    }

    public record GroupPatch(
            ResourceLocation dimension,
            long queryEpoch,
            boolean complete,
            UUID ownerUuid,
            UUID leaderUuid,
            List<UUID> followers,
            Set<UUID> edgeMembers,
            Set<UUID> deserters,
            RoleState roleState,
            long inputHash,
            long publicationEpoch) {
        public GroupPatch {
            if (dimension == null) {
                throw new IllegalArgumentException("dimension");
            }
            followers = followers == null ? List.of() : List.copyOf(followers);
            edgeMembers = edgeMembers == null ? Set.of() : Set.copyOf(edgeMembers);
            deserters = deserters == null ? Set.of() : Set.copyOf(deserters);
            roleState = roleState == null ? RoleState.UNKNOWN : roleState;
        }
    }

    public record PublishedGroupView(
            ResourceLocation dimension,
            long version,
            boolean complete,
            List<GroupPatch> patches) {
        public PublishedGroupView {
            if (dimension == null) {
                throw new IllegalArgumentException("dimension");
            }
            patches = patches == null ? List.of() : List.copyOf(patches);
        }
    }

    public record TerrainSnapshot(
            ResourceLocation dimension,
            long terrainEpoch,
            long signature,
            boolean complete,
            List<Long> sampleKeys) {
        public TerrainSnapshot {
            if (dimension == null) {
                throw new IllegalArgumentException("dimension");
            }
            sampleKeys = sampleKeys == null ? List.of() : List.copyOf(sampleKeys);
        }
    }

    public record PathfindingSnapshot(
            ResourceLocation dimension,
            long pathfindingEpoch,
            boolean complete,
            List<Long> nodeKeys,
            List<Integer> costs) {
        public PathfindingSnapshot {
            if (dimension == null) {
                throw new IllegalArgumentException("dimension");
            }
            nodeKeys = nodeKeys == null ? List.of() : List.copyOf(nodeKeys);
            costs = costs == null ? List.of() : List.copyOf(costs);
        }
    }
}