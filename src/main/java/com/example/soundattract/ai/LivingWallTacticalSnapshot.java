package com.example.soundattract.ai;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

final class LivingWallTacticalSnapshot {
    static final int TILE_SIZE = 4;
    static final int MAX_TILES = 32;
    static final int MAX_CELLS = 512;
    static final int MAX_REQUESTS = 128;
    static final int TTL_TICKS = 40;

    enum CellKind {
        UNKNOWN,
        UNLOADED_BOUNDARY,
        AIR,
        SUPPORTED_AIR,
        SOLID_FULL,
        SOLID_PARTIAL,
        THIN,
        FOLIAGE,
        WATER_OR_FLUID,
        LAVA,
        OPEN_TRAPDOOR_DROP,
        PORTAL,
        DROP_EDGE,
        VOID_EXPOSED
    }

    record Cell(BlockPos pos, CellKind kind, long revision, String liveFingerprint) {
        Cell {
            pos = pos == null ? null : pos.immutable();
            kind = kind == null ? CellKind.UNKNOWN : kind;
            liveFingerprint = liveFingerprint == null ? "" : liveFingerprint;
        }
    }

    record Tile(BlockPos origin, List<Cell> cells, long revision) {
        Tile {
            origin = origin == null ? null : origin.immutable();
            cells = cells == null ? List.of() : List.copyOf(cells);
        }
    }

    record Bounds(BlockPos min, BlockPos max, int horizontalRadius, int verticalRadius) {
        Bounds {
            min = min == null ? null : min.immutable();
            max = max == null ? null : max.immutable();
        }
    }

    record Identity(ResourceLocation dimension, UUID sourceEntity, UUID selectedMob, String rosterFingerprint,
                    String formationIdentity, long terrainRevision, long requestRevision) {
        Identity {
            rosterFingerprint = rosterFingerprint == null ? "" : rosterFingerprint;
            formationIdentity = formationIdentity == null ? "" : formationIdentity;
        }

        String key() {
            return Objects.toString(dimension) + ':' + Objects.toString(sourceEntity) + ':'
                    + Objects.toString(selectedMob) + ':' + rosterFingerprint + ':' + formationIdentity + ':'
                    + terrainRevision + ':' + requestRevision;
        }
    }

    record DestinationEgressWitness(BlockPos selectedCell, BlockPos exitCell, String exitMode,
                                    long revision, String fingerprint) {
        DestinationEgressWitness {
            selectedCell = selectedCell == null ? null : selectedCell.immutable();
            exitCell = exitCell == null ? null : exitCell.immutable();
            exitMode = exitMode == null ? "" : exitMode;
            fingerprint = fingerprint == null ? "" : fingerprint;
        }
    }

    record Snapshot(Bounds bounds, Identity identity, DestinationEgressWitness destinationEgressWitness,
                    List<Tile> tiles, long revision, long expiresAt) {
        Snapshot {
            destinationEgressWitness = destinationEgressWitness;
            tiles = tiles == null ? List.of() : List.copyOf(tiles);
        }

        boolean isExpired(long now) {
            return now >= expiresAt;
        }

        int tileCount() {
            return tiles.size();
        }

        int cellCount() {
            int count = 0;
            for (Tile tile : tiles) {
                count += tile == null ? 0 : tile.cells().size();
            }
            return count;
        }

        CellKind cellKind(BlockPos pos) {
            if (pos == null || tiles.isEmpty()) {
                return CellKind.UNKNOWN;
            }
            for (Tile tile : tiles) {
                if (tile == null || tile.cells().isEmpty()) {
                    continue;
                }
                for (Cell cell : tile.cells()) {
                    if (cell != null && pos.equals(cell.pos())) {
                        return cell.kind();
                    }
                }
            }
            return CellKind.UNKNOWN;
        }

        RouteDecision routeDecision(List<BlockPos> route, boolean allowUnknownExpansion) {
            if (route == null || route.isEmpty()) {
                return RouteDecision.UNKNOWN;
            }
            boolean sawUnknown = false;
            for (BlockPos step : route) {
                CellKind kind = cellKind(step);
                if (kind == CellKind.UNKNOWN || kind == CellKind.UNLOADED_BOUNDARY) {
                    sawUnknown = true;
                    if (!allowUnknownExpansion) {
                        return RouteDecision.UNKNOWN;
                    }
                    continue;
                }
                if (kind == CellKind.LAVA || kind == CellKind.PORTAL || kind == CellKind.OPEN_TRAPDOOR_DROP
                        || kind == CellKind.VOID_EXPOSED) {
                    return RouteDecision.BLOCKED;
                }
            }
            return sawUnknown ? RouteDecision.UNKNOWN : RouteDecision.REACHABLE;
        }

        ExpansionStatus expansionStatus(List<BlockPos> route, int tileBudget) {
            if (route == null || route.isEmpty()) {
                return ExpansionStatus.UNKNOWN;
            }
            boolean needsExpansion = false;
            for (BlockPos step : route) {
                if (!isWithinBounds(bounds, step)) {
                    return ExpansionStatus.BLOCKED;
                }
                CellKind kind = cellKind(step);
                if (kind == CellKind.LAVA || kind == CellKind.PORTAL || kind == CellKind.OPEN_TRAPDOOR_DROP
                        || kind == CellKind.VOID_EXPOSED) {
                    return ExpansionStatus.BLOCKED;
                }
                if (kind == CellKind.UNKNOWN || kind == CellKind.UNLOADED_BOUNDARY) {
                    needsExpansion = true;
                }
            }
            if (!needsExpansion) {
                return ExpansionStatus.COMPLETE;
            }
            return tileBudget > 0 ? ExpansionStatus.EXPANDING : ExpansionStatus.UNKNOWN;
        }
    }

    enum RouteDecision {
        REACHABLE,
        UNKNOWN,
        BLOCKED
    }

    enum ExpansionStatus {
        COMPLETE,
        EXPANDING,
        UNKNOWN,
        BLOCKED
    }

    record CacheKey(Identity identity, long snapshotRevision, int tileCount, int cellCount) {
        CacheKey {
            if (tileCount < 0 || cellCount < 0) {
                throw new IllegalArgumentException("negative cache dimensions");
            }
        }
    }

    static boolean isWithinBounds(Bounds bounds, BlockPos pos) {
        if (bounds == null || pos == null || bounds.min() == null || bounds.max() == null) {
            return false;
        }
        return pos.getX() >= bounds.min().getX() && pos.getX() <= bounds.max().getX()
                && pos.getY() >= bounds.min().getY() && pos.getY() <= bounds.max().getY()
                && pos.getZ() >= bounds.min().getZ() && pos.getZ() <= bounds.max().getZ();
    }

    static CellKind projectLoadedState(boolean loaded, boolean empty, boolean supported, boolean fluid, boolean lava,
                                       boolean portal, boolean trapdoor, boolean foliage, boolean thin, boolean solid,
                                       boolean dropEdge, boolean voidExposed) {
        if (!loaded) return CellKind.UNLOADED_BOUNDARY;
        if (voidExposed) return CellKind.VOID_EXPOSED;
        if (lava) return CellKind.LAVA;
        if (portal) return CellKind.PORTAL;
        if (trapdoor) return CellKind.OPEN_TRAPDOOR_DROP;
        if (fluid) return CellKind.WATER_OR_FLUID;
        if (dropEdge) return CellKind.DROP_EDGE;
        if (solid) return CellKind.SOLID_FULL;
        if (thin) return CellKind.THIN;
        if (foliage) return CellKind.FOLIAGE;
        if (supported) return CellKind.SUPPORTED_AIR;
        return empty ? CellKind.AIR : CellKind.UNKNOWN;
    }

    static boolean invalidates(Cell previous, Cell current) {
        if (previous == null || current == null) {
            return true;
        }
        return previous.kind() != current.kind()
                || previous.revision() != current.revision()
                || !Objects.equals(previous.liveFingerprint(), current.liveFingerprint());
    }

    static boolean witnessInvalidates(DestinationEgressWitness previous, DestinationEgressWitness current) {
        if (previous == null || current == null) {
            return true;
        }
        return !Objects.equals(previous.selectedCell(), current.selectedCell())
                || !Objects.equals(previous.exitCell(), current.exitCell())
                || !Objects.equals(previous.exitMode(), current.exitMode())
                || previous.revision() != current.revision()
                || !Objects.equals(previous.fingerprint(), current.fingerprint());
    }

    private LivingWallTacticalSnapshot() {
    }
}