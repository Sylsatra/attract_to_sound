package com.example.soundattract.ai;

import com.example.soundattract.config.separate.LivingWallConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder("soundattract_bridgefix")
public class LivingBridgeTraversalGameTests {
    @GameTest(template = "empty", timeoutTicks = 4000)
    public void diagonalWallBridgeFormsAndTraverses(GameTestHelper helper) {
        LivingWallConfig.ENABLE_LIVING_WALL.set(true);
        helper.getLevel().setDayTime(18000L);
        for (int x = 1; x <= 40; x++) {
            for (int z = 1; z <= 40; z++) {
                for (int y = 1; y <= 30; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 31, z), Blocks.STONE);
                if (x < 23 || x > 26) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                if (x >= 30 && z >= 20) {
                    for (int y = 2; y <= 7; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        for (int edge = 0; edge <= 41; edge += 41) {
            for (int along = 0; along <= 41; along++) {
                for (int y = 0; y <= 31; y++) {
                    helper.setBlock(new BlockPos(edge, y, along), Blocks.STONE);
                    helper.setBlock(new BlockPos(along, y, edge), Blocks.STONE);
                }
            }
        }
        List<Mob> workers = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            Mob worker = helper.spawn(EntityType.ZOMBIE, 18.5F + i % 4, 2.0F, 12.5F + i / 4);
            worker.setPersistenceRequired();
            ((net.minecraft.world.entity.monster.Zombie) worker).setBaby(false);
            worker.setNoAi(true);
            worker.setOnGround(true);
            workers.add(worker);
        }
        BlockPos threat = helper.absolutePos(new BlockPos(34, 8, 28));
        helper.runAfterDelay(60, () -> admit(helper, workers, threat, 0));
    }

    private static void admit(GameTestHelper helper, List<Mob> workers, BlockPos threat, int attempts) {
        try {
            ServerLevel level = helper.getLevel();
            Method targetsMethod = LivingWallCoordinator.class.getDeclaredMethod("findClusterTargets",
                    ServerLevel.class, List.class, BlockPos.class);
            targetsMethod.setAccessible(true);
            List<?> targets = (List<?>) targetsMethod.invoke(null, level, workers, threat);
            Method requestMethod = LivingWallCoordinator.class.getDeclaredMethod("findConstructionRequest",
                    ServerLevel.class, List.class, BlockPos.class, BlockPos.class);
            requestMethod.setAccessible(true);
            Method prepare = java.util.Arrays.stream(LivingWallCoordinator.class.getDeclaredMethods())
                    .filter(method -> method.getName().equals("prepareFormation") && method.getParameterCount() == 5)
                    .findFirst().orElseThrow();
            prepare.setAccessible(true);
            for (Object target : targets) {
                Object request = requestMethod.invoke(null, level, workers, threat, target);
                if (request == null) {
                    continue;
                }
                workers.forEach(worker -> worker.setNoAi(false));
                boolean prepared = (boolean) prepare.invoke(null, workers.get(0), target, level.getGameTime(), workers, request);
                if (!prepared) continue;
                Method stateMethod = LivingWallCoordinator.class.getDeclaredMethod("state", ServerLevel.class);
                stateMethod.setAccessible(true);
                Object state = stateMethod.invoke(null, level);
                for (Object formation : ((Map<?, ?>) field(state, "formations")).values()) {
                    if (!workers.get(0).getUUID().equals(field(formation, "plannerId"))) continue;
                    LivingWallCoordinator.SitePlan plan = (LivingWallCoordinator.SitePlan) field(formation, "plan");
                    var execution = (LivingWallCoordinator.CommittedBridgeExecution) field(formation, "committedExecution");
                    helper.assertTrue(execution.admission().roster().size() == workers.size(),
                            "admission must commit the discovered roster before assigning workers");
                    helper.assertTrue(Math.abs(plan.forward().x) > 0.05D && Math.abs(plan.forward().z) > 0.05D,
                            "formation must use a diagonal corridor");
                    helper.assertTrue(plan.forward().distanceToSqr(plan.corridor().traversalForward()) < 1.0E-9D,
                            "formation must preserve resolved traversal bearing");
                    observe(helper, formation, 0);
                    return;
                }
            }
            if (attempts >= 8) {
                helper.fail("no executable diagonal formation; candidates=" + targets.size());
                return;
            }
            helper.runAfterDelay(45, () -> admit(helper, workers, threat, attempts + 1));
        } catch (ReflectiveOperationException exception) {
            helper.fail("admission failed: " + exception.getCause());
        }
    }

    private static void observe(GameTestHelper helper, Object formation, int tick) {
        try {
            int placed = ((Map<?, ?>) field(formation, "successfulPlacementProvenance")).size();
            int crossed = ((Set<?>) field(formation, "completedCrossers")).size();
            if (placed > 0 && crossed > 0) {
                helper.succeed();
                return;
            }
            if ((boolean) field(formation, "collapsed") || tick >= 3200) {
                Object witness = field(formation, "traversalWitness");
                for (String cells : List.of("observedCenterline", "observedEnvelope")) {
                    for (Object cell : ((Map<?, ?>) field(witness, cells)).values()) {
                        BlockPos position = (BlockPos) field(cell, "pos");
                        String actual = WallProfileScanner.stateFingerprint(helper.getLevel(), position);
                        if (!actual.equals(field(cell, "fingerprint"))) {
                            helper.fail("traversal witness changed at " + position + ": before="
                                    + field(cell, "fingerprint") + " after=" + actual);
                            return;
                        }
                    }
                }
                helper.fail("bridge traversal failed: phase=" + field(formation, "phase")
                        + " collapse=" + field(formation, "collapseReason")
                        + " detail=" + field(formation, "collapseDetail")
                        + " assembled=" + field(formation, "assembledSlots")
                        + " members=" + ((List<?>) field(formation, "members")).size()
                        + " placed=" + placed + " crossed=" + crossed);
                return;
            }
            helper.runAfterDelay(1, () -> observe(helper, formation, tick + 1));
        } catch (ReflectiveOperationException exception) {
            helper.fail("formation observation failed: " + exception);
        }
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
}
