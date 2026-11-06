package com.example.soundattract.pathfinding;

import com.example.soundattract.ai.LivingWallCoordinator;
import com.example.soundattract.ai.RaidManager;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.resources.Identifier;
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

    private record Key(Identifier dimension, long position) {
    }

    private record PursuitStamp(Identifier dimension, long untilTick) {
    }

    private static final int L1_SIZE = 4096;
    private static final int L1_MASK = 4095;
    private static final long[] L1_KEYS = new long[L1_SIZE];
    private static final byte[] L1_TYPES = new byte[L1_SIZE];
    private static final Identifier[] L1_DIMS = new Identifier[L1_SIZE];

    private static final int SAFE_GROUND_SIZE = 2048;
    private static final int SAFE_GROUND_MASK = 2047;
    private static final long[] SG_KEYS = new long[SAFE_GROUND_SIZE];
    private static final boolean[] SG_VALUES = new boolean[SAFE_GROUND_SIZE];
    private static final Identifier[] SG_DIMS = new Identifier[SAFE_GROUND_SIZE];

    private static final com.github.benmanes.caffeine.cache.Cache<Key, HazardType> CACHE =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .maximumSize(2048)
                    .expireAfterWrite(500, TimeUnit.MILLISECONDS)
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
            ACTIVE_PURSUITS.put(mob.getUUID(), new PursuitStamp(mob.level().dimension().identifier(), mob.level().getGameTime() + 20L));
        }
    }

    public static void clear() {
        ACTIVE_PURSUITS.invalidateAll();
        CACHE.invalidateAll();
        java.util.Arrays.fill(L1_DIMS, null);
        java.util.Arrays.fill(SG_DIMS, null);
    }

    public static void invalidateTerrain(Level level, BlockPos changed) {
        if (level == null || changed == null) return;
        Identifier dimension = level.dimension().identifier();

        for (int y = changed.getY() - 1; y <= changed.getY() + 3; y++) {
            long position = BlockPos.asLong(changed.getX(), y, changed.getZ());
            int index = (int) ((position ^ (position >>> 16) ^ (position >>> 32)) & L1_MASK);
            if (dimension.equals(L1_DIMS[index]) && L1_KEYS[index] == position) L1_DIMS[index] = null;
            CACHE.invalidate(new Key(dimension, position));

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long neighbor = BlockPos.asLong(changed.getX() + dx, y, changed.getZ() + dz);
                    int safeIndex = (int) ((neighbor ^ (neighbor >>> 16) ^ (neighbor >>> 32)) & SAFE_GROUND_MASK);
                    if (dimension.equals(SG_DIMS[safeIndex]) && SG_KEYS[safeIndex] == neighbor) SG_DIMS[safeIndex] = null;
                }
            }
        }
    }

    public static boolean isSafeGround(Level level, BlockPos feet) {
        if (level == null || feet == null) {
            return false;
        }
        Identifier dim = level.dimension().identifier();
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
        if (stamp == null || !stamp.dimension().equals(mob.level().dimension().identifier())
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

    private static byte encodeType(HazardType type) {
        if (type == null) return (byte) 0;
        return switch (type) {
            case SAFE -> (byte) 1;
            case LAVA -> (byte) 2;
            case TRAPDOOR_DROP -> (byte) 3;
            case DEEP_DROP -> (byte) 4;
        };
    }

    private static HazardType decodeType(byte b) {
        return switch (b) {
            case 1 -> HazardType.SAFE;
            case 2 -> HazardType.LAVA;
            case 3 -> HazardType.TRAPDOOR_DROP;
            case 4 -> HazardType.DEEP_DROP;
            default -> null;
        };
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
        Identifier dim = level.dimension().identifier();
        long pos = BlockPos.asLong(x, y, z);
        int idx = (int) ((pos ^ (pos >>> 16) ^ (pos >>> 32)) & L1_MASK);
        if (L1_DIMS[idx] == dim && L1_KEYS[idx] == pos) {
            HazardType decoded = decodeType(L1_TYPES[idx]);
            if (decoded != null) {
                return decoded;
            }
        }
        Key key = new Key(dim, pos);
        HazardType cached = CACHE.getIfPresent(key);
        if (cached != null) {
            L1_KEYS[idx] = pos;
            L1_DIMS[idx] = dim;
            L1_TYPES[idx] = encodeType(cached);
            return cached;
        }
        BlockPos feet = new BlockPos(x, y, z);
        HazardType result = classifyUncached(level, feet);
        CACHE.put(key, result);
        L1_KEYS[idx] = pos;
        L1_DIMS[idx] = dim;
        L1_TYPES[idx] = encodeType(result);
        return result;
    }

    private static HazardType classifyUncached(BlockGetter level, BlockPos feet) {
        BlockPos support = feet.below();
        if (containsLava(level, feet) || containsLava(level, support) || containsLava(level, feet.above())
                || containsLava(level, support.below()) || containsLava(level, support.below(2))) {
            return HazardType.LAVA;
        }
        if (containsTrapdoorDrop(level, feet) || containsTrapdoorDrop(level, support) || containsTrapdoorDrop(level, feet.above())) {
            return HazardType.TRAPDOOR_DROP;
        }
        if (level.getBlockState(support).getCollisionShape(level, support).isEmpty()
                && level.getBlockState(support.below()).getCollisionShape(level, support.below()).isEmpty()
                && level.getBlockState(support.below(2)).getCollisionShape(level, support.below(2)).isEmpty()) {
            return HazardType.DEEP_DROP;
        }
        return HazardType.SAFE;
    }

    private static boolean containsLava(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getFluidState().is(FluidTags.LAVA);
    }

    private static boolean containsTrapdoorDrop(BlockGetter level, BlockPos pos) {
        return TrapdoorNavigationHelper.isTrapdoorDropHazard(level, pos);
    }
}
