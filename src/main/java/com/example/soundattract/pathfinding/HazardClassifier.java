package com.example.soundattract.pathfinding;

import com.example.soundattract.ai.LivingWallCoordinator;
import com.example.soundattract.ai.RaidManager;
import com.github.benmanes.caffeine.cache.Ticker;
import it.unimi.dsi.fastutil.HashCommon;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class HazardClassifier {
    public enum HazardType {
        SAFE,
        LAVA,
        TRAPDOOR_DROP,
        DEEP_DROP
    }

    private record Key(ResourceLocation dimension, long position) {
    }

    private record PursuitStamp(ResourceLocation dimension, long untilTick) {
    }

    record CachedHazard(ResourceLocation dimension, long position, HazardType type, long writtenAt) {
    }

    static final class RecentHazards {
        private final CachedHazard[] entries = new CachedHazard[4096];
        private final Ticker ticker;

        RecentHazards(Ticker ticker) {
            this.ticker = ticker;
        }

        int index(long position) {
            return (int) HashCommon.mix(position) & (entries.length - 1);
        }

        HazardType get(int index, ResourceLocation dimension, long position) {
            CachedHazard entry = entries[index];
            if (entry != null && entry.position() == position && entry.dimension().equals(dimension)
                    && ticker.read() - entry.writtenAt() < HAZARD_TTL_NANOS) {
                return entry.type();
            }
            return null;
        }

        void put(int index, CachedHazard hazard) {
            entries[index] = hazard;
        }

        void clear() {
            Arrays.fill(entries, null);
        }
    }

    private static final long HAZARD_TTL_NANOS = TimeUnit.MILLISECONDS.toNanos(500);
    private static final Ticker HAZARD_TICKER = Ticker.systemTicker();
    private static final RecentHazards RECENT_HAZARDS = new RecentHazards(HAZARD_TICKER);

    private static final int SAFE_GROUND_SIZE = 2048;
    private static final int SAFE_GROUND_MASK = 2047;
    private static final long[] SG_KEYS = new long[SAFE_GROUND_SIZE];
    private static final boolean[] SG_VALUES = new boolean[SAFE_GROUND_SIZE];
    private static final ResourceLocation[] SG_DIMS = new ResourceLocation[SAFE_GROUND_SIZE];

    private static final com.github.benmanes.caffeine.cache.Cache<Key, CachedHazard> CACHE =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .maximumSize(2048)
                    .expireAfterWrite(HAZARD_TTL_NANOS, TimeUnit.NANOSECONDS)
                    .ticker(HAZARD_TICKER)
                    .build();
    private static final com.github.benmanes.caffeine.cache.Cache<java.util.UUID, PursuitStamp> ACTIVE_PURSUITS =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .maximumSize(32768)
                    .expireAfterWrite(2, TimeUnit.SECONDS)
                    .build();

    private HazardClassifier() {
    }

    public static void markPursuer(Mob mob) {
        if (mob != null && mob.level() != null) {
            ACTIVE_PURSUITS.put(mob.getUUID(), new PursuitStamp(mob.level().dimension().location(), mob.level().getGameTime() + 20L));
        }
    }

    public static void clear() {
        ACTIVE_PURSUITS.invalidateAll();
        CACHE.invalidateAll();
        RECENT_HAZARDS.clear();
        Arrays.fill(SG_DIMS, null);
    }

    public static boolean isSafeGround(Level level, BlockPos feet) {
        if (level == null || feet == null) {
            return false;
        }
        ResourceLocation dim = level.dimension().location();
        long pos = feet.asLong();
        int idx = (int) ((pos ^ (pos >>> 16) ^ (pos >>> 32)) & SAFE_GROUND_MASK);
        if (SG_DIMS[idx] == dim && SG_KEYS[idx] == pos) {
            return SG_VALUES[idx];
        }
        boolean safe = computeSafeGround(level, feet);
        SG_KEYS[idx] = pos;
        SG_DIMS[idx] = dim;
        SG_VALUES[idx] = safe;
        return safe;
    }

    public static boolean isSafeGroundUncached(Level level, BlockPos feet) {
        return level != null && feet != null && computeSafeGround(level, feet);
    }

    private static boolean computeSafeGround(Level level, BlockPos feet) {
        BlockPos support = feet.below();
        BlockState supportState = level.getBlockState(support);
        if (supportState.isAir() || !supportState.getFluidState().isEmpty()
                || supportState.getCollisionShape(level, support).isEmpty()
                || supportState.is(BlockTags.TRAPDOORS)
                || supportState.getBlock() instanceof TrapDoorBlock) {
            return false;
        }
        BlockState feetState = level.getBlockState(feet);
        if (feetState.is(BlockTags.TRAPDOORS) || feetState.getBlock() instanceof TrapDoorBlock) {
            return false;
        }
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos nSupport = support.offset(dx, 0, dz);
                BlockState nState = level.getBlockState(nSupport);
                if (nState.isAir() || !nState.getFluidState().isEmpty()
                        || nState.getCollisionShape(level, nSupport).isEmpty()
                        || nState.is(BlockTags.TRAPDOORS)
                        || nState.getBlock() instanceof TrapDoorBlock) {
                    return false;
                }
                BlockPos nFeet = feet.offset(dx, 0, dz);
                if (!level.getFluidState(nFeet).isEmpty()
                        || level.getBlockState(nFeet).is(BlockTags.TRAPDOORS)
                        || level.getBlockState(nFeet).getBlock() instanceof TrapDoorBlock) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isActivePursuer(Mob mob) {
        if (mob == null || mob.level() == null || LivingWallCoordinator.isControlled(mob)) {
            return false;
        }
        if (mob.getTarget() != null && mob.getTarget().isAlive()) {
            return true;
        }
        if (RaidManager.isRaidAdvancing(mob)) {
            return true;
        }
        PursuitStamp stamp = ACTIVE_PURSUITS.getIfPresent(mob.getUUID());
        if (stamp == null || !stamp.dimension().equals(mob.level().dimension().location())
                || stamp.untilTick() < mob.level().getGameTime()) {
            if (stamp != null) {
                ACTIVE_PURSUITS.invalidate(mob.getUUID());
            }
            return false;
        }
        return true;
    }

    public static boolean isDangerous(Level level, Mob mob, Vec3 position) {
        if (level == null || position == null) {
            return false;
        }
        AABB box = (mob != null)
                ? mob.getDimensions(mob.getPose()).makeBoundingBox(position)
                : new AABB(position.x - 0.3D, position.y, position.z - 0.3D, position.x + 0.3D, position.y + 1.8D, position.z + 0.3D);
        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ);
        int y = Mth.floor(position.y);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                HazardType type = classify(level, x, y, z);
                if (type == HazardType.LAVA) {
                    if (mob == null || !mob.fireImmune()) {
                        return true;
                    }
                } else if (type != HazardType.SAFE) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isDangerous(BlockGetter level, Mob mob, int x, int y, int z) {
        if (level == null) {
            return false;
        }

        Level realLevel = null;
        if (level instanceof Level lvl) {
            realLevel = lvl;
        } else if (mob != null && mob.level() != null) {
            realLevel = mob.level();
        }

        if (realLevel != null) {
            HazardType type = classify(realLevel, x, y, z);
            if (type == HazardType.LAVA) {
                return mob == null || !mob.fireImmune();
            }
            return type != HazardType.SAFE;
        }

        BlockPos pos = new BlockPos(x, y, z);
        HazardType type = classifyUncached(level, pos);
        if (type == HazardType.LAVA) {
            return mob == null || !mob.fireImmune();
        }
        return type != HazardType.SAFE;
    }

    public static boolean isDangerous(BlockGetter level, Mob mob, BlockPos position) {
        if (level == null || position == null) {
            return false;
        }

        Level realLevel = null;
        if (level instanceof Level lvl) {
            realLevel = lvl;
        } else if (mob != null && mob.level() != null) {
            realLevel = mob.level();
        }

        if (realLevel != null) {
            HazardType type = classify(realLevel, position);
            if (type == HazardType.LAVA) {
                return mob == null || !mob.fireImmune();
            }
            return type != HazardType.SAFE;
        }

        HazardType type = classifyUncached(level, position);
        if (type == HazardType.LAVA) {
            return mob == null || !mob.fireImmune();
        }
        return type != HazardType.SAFE;
    }

    public static HazardType classify(Level level, BlockPos feet) {
        if (level == null || feet == null) {
            return HazardType.DEEP_DROP;
        }
        return classify(level, feet.getX(), feet.getY(), feet.getZ());
    }

    public static HazardType classify(Level level, int x, int y, int z) {
        if (level == null) {
            return HazardType.DEEP_DROP;
        }
        ResourceLocation dim = level.dimension().location();
        long pos = BlockPos.asLong(x, y, z);
        int idx = RECENT_HAZARDS.index(pos);
        HazardType recent = RECENT_HAZARDS.get(idx, dim, pos);
        if (recent != null) {
            return recent;
        }
        Key key = new Key(dim, pos);
        CachedHazard cached = CACHE.getIfPresent(key);
        if (cached == null) {
            HazardType result = classifyUncached(level, new BlockPos(x, y, z));
            cached = new CachedHazard(dim, pos, result, HAZARD_TICKER.read());
            CACHE.put(key, cached);
        }
        RECENT_HAZARDS.put(idx, cached);
        return cached.type();
    }

    private static HazardType classifyUncached(BlockGetter level, BlockPos feet) {
        BlockPos support = feet.below();
        BlockPos head = feet.above();
        BlockPos lowerSupport = support.below();
        BlockPos deepSupport = support.below(2);
        BlockState feetState = level.getBlockState(feet);
        if (containsLava(feetState)) {
            return HazardType.LAVA;
        }
        BlockState supportState = level.getBlockState(support);
        if (containsLava(supportState)) {
            return HazardType.LAVA;
        }
        BlockState headState = level.getBlockState(head);
        if (containsLava(headState)) {
            return HazardType.LAVA;
        }
        BlockState lowerSupportState = level.getBlockState(lowerSupport);
        if (containsLava(lowerSupportState)) {
            return HazardType.LAVA;
        }
        BlockState deepSupportState = level.getBlockState(deepSupport);
        if (containsLava(deepSupportState)) {
            return HazardType.LAVA;
        }
        if (TrapdoorNavigationHelper.isTrapdoorDropHazard(level, feet.getX(), feet.getY(), feet.getZ(),
                    feetState, supportState)
                || TrapdoorNavigationHelper.isTrapdoorDropHazard(level, support.getX(), support.getY(), support.getZ(),
                    supportState, lowerSupportState)
                || TrapdoorNavigationHelper.isTrapdoorDropHazard(level, head.getX(), head.getY(), head.getZ(),
                    headState, feetState)) {
            return HazardType.TRAPDOOR_DROP;
        }
        if (supportState.getCollisionShape(level, support).isEmpty()
                && lowerSupportState.getCollisionShape(level, lowerSupport).isEmpty()
                && deepSupportState.getCollisionShape(level, deepSupport).isEmpty()) {
            return HazardType.DEEP_DROP;
        }
        return HazardType.SAFE;
    }

    private static boolean containsLava(BlockState state) {
        return state.getFluidState().is(FluidTags.LAVA);
    }
}
