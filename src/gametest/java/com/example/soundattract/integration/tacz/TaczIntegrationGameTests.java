package com.example.soundattract.integration.tacz;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.tracking.SoundTracker;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

public final class TaczIntegrationGameTests {
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, required = false)
    public void registeredTaczShootAndReloadCallbacksCreateConfiguredSounds(GameTestHelper helper) {
        if (!FabricLoader.getInstance().isModLoaded("tacz")) {
            helper.succeed();
            return;
        }
        if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enableTaczIntegration.get()) {
            helper.fail("TaCZ integration must be enabled with server config loaded for this runtime test");
            return;
        }

        ServerPlayer shooter = helper.makeMockServerPlayerInLevel();
        String dimension = helper.getLevel().dimension().identifier().toString();
        Identifier gunSoundId = Identifier.fromNamespaceAndPath("tacz", "gun");
        String shotId = SoundTracker.buildIntegrationSoundId(gunSoundId, shooter.getUUID() + "/shoot");
        String reloadId = SoundTracker.buildIntegrationSoundId(gunSoundId, shooter.getUUID() + "/reload");
        try {
            postTaczEvent("com.tacz.guns.api.event.common.GunShootEvent", shooter, "SERVER");
            postTaczEvent("com.tacz.guns.api.event.common.GunReloadEvent", shooter, "SERVER");

            List<SoundTracker.SoundRecord> tracked = SoundTracker.getTrackedSoundsSnapshot().stream()
                    .filter(record -> dimension.equals(record.dimensionKey)
                            && record.pos.equals(shooter.blockPosition())
                            && (shotId.equals(record.soundId) || reloadId.equals(record.soundId)))
                    .toList();
            SoundTracker.SoundRecord shot = tracked.stream()
                    .filter(record -> shotId.equals(record.soundId)).findFirst().orElse(null);
            SoundTracker.SoundRecord reload = tracked.stream()
                    .filter(record -> reloadId.equals(record.soundId)).findFirst().orElse(null);
            double expectedShotRange = SoundAttractConfig.TACZ_SHOOT_RANGE_CACHE;
            double expectedReloadRange = SoundAttractConfig.TACZ_RELOAD_RANGE_CACHE;
            double expectedReloadWeight = SoundAttractConfig.TACZ_RELOAD_WEIGHT_CACHE;
            if (shot == null || reload == null
                    || Math.abs(shot.range - expectedShotRange) > 0.001
                    || Math.abs(shot.weight - expectedShotRange / 10.0) > 0.001
                    || Math.abs(reload.range - expectedReloadRange) > 0.001
                    || Math.abs(reload.weight - expectedReloadWeight) > 0.001) {
                helper.fail("TaCZ production callbacks did not create configured shoot/reload sounds: shot="
                        + shot + ", reload=" + reload + ", tracked=" + tracked);
                return;
            }
            helper.succeed();
        } catch (ReflectiveOperationException exception) {
            helper.fail("TaCZ 26.2 event API could not deliver the production callback: " + exception);
        } finally {
            SoundTracker.removeSoundAt(shooter.blockPosition(), dimension);
            helper.getLevel().getServer().getPlayerList().remove(shooter);
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, required = false)
    public void taczGunAndMuzzleDataDriveSoundRangeAndGunshotFlash(GameTestHelper helper) {
        if (!FabricLoader.getInstance().isModLoaded("tacz")) {
            helper.succeed();
            return;
        }
        if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enableTaczIntegration.get()) {
            helper.fail("TaCZ integration must be enabled with server config loaded for this runtime test");
            return;
        }

        Identifier gunId = Identifier.fromNamespaceAndPath("tacz", "m1911");
        Identifier muzzleId = Identifier.fromNamespaceAndPath("tacz", "muzzle_silencer_ptilopsis");
        org.apache.commons.lang3.tuple.Pair<Double, Double> gunStats =
                SoundAttractConfig.TACZ_GUN_SHOOT_DB_CACHE.get(gunId);
        if (gunStats == null) {
            helper.fail("TaCZ test gun is missing its configured decibel entry: " + gunId);
            return;
        }

        ServerPlayer shooter = helper.makeMockServerPlayerInLevel();
        String dimension = helper.getLevel().dimension().identifier().toString();
        BlockPos unsuppressedPos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos suppressedPos = helper.absolutePos(new BlockPos(5, 1, 1));
        String shotId = SoundTracker.buildIntegrationSoundId(
                Identifier.fromNamespaceAndPath("tacz", "gun"), shooter.getUUID() + "/shoot");
        double gunRangeReduction = SoundAttractConfig.TACZ_ATTACHMENT_REDUCTION_DB_CACHE
                .getOrDefault(muzzleId.toString(),
                        SoundAttractConfig.TACZ_ATTACHMENT_REDUCTION_DEFAULT_CACHE);
        double expectedGunRange = Math.max(0.0, gunStats.getLeft() - gunRangeReduction);
        double flashRange = SoundAttractConfig.COMMON.gunshotBaseDetectionRange.get();
        double flashReduction = SoundAttractConfig.TACZ_MUZZLE_FLASH_REDUCTION_CACHE
                .getOrDefault(muzzleId, SoundAttractConfig.TACZ_ATTACHMENT_FLASH_REDUCTION_DEFAULT_CACHE);
        double expectedFlashRange = Math.max(0.0, flashRange - flashReduction);
        try {
            shooter.setPos(unsuppressedPos.getX() + 0.5, unsuppressedPos.getY(), unsuppressedPos.getZ() + 0.5);
            ItemStack unsuppressedGun = buildGun(gunId, null, null);
            if (!isTaczGun(unsuppressedGun, gunId)) {
                helper.fail("TaCZ test API did not build the unsuppressed M1911 stack");
                return;
            }
            postTaczEvent("com.tacz.guns.api.event.common.GunShootEvent", shooter, unsuppressedGun, "SERVER");
            SoundTracker.SoundRecord unsuppressedSound = findSound(shotId, unsuppressedPos, dimension);
            double expectedUnsuppressedFlash = flashRange;
            Double observedUnsuppressedFlash = activeGunshotRange(shooter);

            shooter.setPos(suppressedPos.getX() + 0.5, suppressedPos.getY(), suppressedPos.getZ() + 0.5);
            ItemStack suppressedGun = buildGun(gunId, muzzleId, attachmentType("MUZZLE"));
            if (!isTaczGun(suppressedGun, gunId)
                    || !muzzleId.equals(gunAttachmentId(suppressedGun, "MUZZLE"))) {
                helper.fail("TaCZ test API did not install the compatible M1911 muzzle attachment");
                return;
            }
            postTaczEvent("com.tacz.guns.api.event.common.GunShootEvent", shooter, suppressedGun, "SERVER");
            SoundTracker.SoundRecord suppressedSound = findSound(shotId, suppressedPos, dimension);
            Double observedSuppressedFlash = activeGunshotRange(shooter);
            if (unsuppressedSound == null || suppressedSound == null
                    || Math.abs(unsuppressedSound.range - gunStats.getLeft()) > 0.001
                    || Math.abs(unsuppressedSound.weight - gunStats.getLeft() / 10.0) > 0.001
                    || Math.abs(suppressedSound.range - expectedGunRange) > 0.001
                    || Math.abs(suppressedSound.weight - expectedGunRange / 10.0) > 0.001
                    || observedUnsuppressedFlash == null
                    || Math.abs(observedUnsuppressedFlash - expectedUnsuppressedFlash) > 0.001
                    || observedSuppressedFlash == null
                    || Math.abs(observedSuppressedFlash - expectedFlashRange) > 0.001) {
                helper.fail("TaCZ gun or muzzle data was not applied to tracked sound/flash ranges: unsuppressedSound="
                        + unsuppressedSound + ", suppressedSound=" + suppressedSound
                        + ", expectedGunRange=" + expectedGunRange
                        + ", unsuppressedFlash=" + observedUnsuppressedFlash
                        + ", suppressedFlash=" + observedSuppressedFlash
                        + ", expectedFlashRange=" + expectedFlashRange);
                return;
            }
            helper.succeed();
        } catch (ReflectiveOperationException exception) {
            helper.fail("TaCZ 26.2 gun or attachment API could not exercise the production path: " + exception);
        } finally {
            SoundTracker.removeSoundAt(unsuppressedPos, dimension);
            SoundTracker.removeSoundAt(suppressedPos, dimension);
            helper.getLevel().getServer().getPlayerList().remove(shooter);
        }
    }

    private static void postTaczEvent(String eventClassName, ServerPlayer player, String logicalSide)
            throws ReflectiveOperationException {
        postTaczEvent(eventClassName, player, ItemStack.EMPTY, logicalSide);
    }

    private static void postTaczEvent(String eventClassName, ServerPlayer player, ItemStack gun, String logicalSide)
            throws ReflectiveOperationException {
        Class<?> eventClass = Class.forName(eventClassName);
        Class<?> sideClass = Class.forName("cn.sh1rocu.tacz.api.LogicalSide");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object side = Enum.valueOf((Class<? extends Enum>) sideClass.asSubclass(Enum.class), logicalSide);
        Object event = eventClass.getConstructor(net.minecraft.world.entity.LivingEntity.class,
                        ItemStack.class, sideClass)
                .newInstance(player, gun, side);
        Field callbackField = eventClass.getField("CALLBACK");
        Object callback = callbackField.get(null);
        Object invoker = Event.class.getMethod("invoker").invoke(callback);
        Class<?> callbackClass = java.util.Arrays.stream(eventClass.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("Callback"))
                .findFirst().orElseThrow(() -> new NoSuchMethodException("TaCZ event Callback interface"));
        callbackClass.getMethod("post", eventClass).invoke(invoker, event);
    }

    private static ItemStack buildGun(Identifier gunId, Identifier muzzleId, Object muzzleType)
            throws ReflectiveOperationException {
        Class<?> builderClass = Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder");
        Object builder = builderClass.getMethod("create").invoke(null);
        builderClass.getMethod("setId", Identifier.class).invoke(builder, gunId);
        if (muzzleId != null) {
            Class<?> attachmentType = Class.forName("com.tacz.guns.api.item.attachment.AttachmentType");
            builderClass.getMethod("putAttachment", attachmentType, Identifier.class)
                    .invoke(builder, muzzleType, muzzleId);
        }
        return (ItemStack) builderClass.getMethod("forceBuild").invoke(builder);
    }

    private static Object attachmentType(String name) throws ReflectiveOperationException {
        Class<?> type = Class.forName("com.tacz.guns.api.item.attachment.AttachmentType");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object value = Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), name);
        return value;
    }

    private static boolean isTaczGun(ItemStack stack, Identifier expectedGunId) throws ReflectiveOperationException {
        Class<?> gunType = Class.forName("com.tacz.guns.api.item.IGun");
        Object gun = gunType.getMethod("getIGunOrNull", ItemStack.class).invoke(null, stack);
        return gun != null && expectedGunId.equals(gunType.getMethod("getGunId", ItemStack.class).invoke(gun, stack));
    }

    private static Identifier gunAttachmentId(ItemStack stack, String attachmentType)
            throws ReflectiveOperationException {
        Class<?> gunType = Class.forName("com.tacz.guns.api.item.IGun");
        Object gun = gunType.getMethod("getIGunOrNull", ItemStack.class).invoke(null, stack);
        return (Identifier) gunType.getMethod("getAttachmentId", ItemStack.class,
                        Class.forName("com.tacz.guns.api.item.attachment.AttachmentType"))
                .invoke(gun, stack, attachmentType(attachmentType));
    }

    private static Double activeGunshotRange(ServerPlayer player) throws ReflectiveOperationException {
        Class<?> events = Class.forName("com.example.soundattract.event.StealthDetectionEvents");
        Method method = events.getDeclaredMethod("getActiveGunshotRange", net.minecraft.world.entity.player.Player.class);
        method.setAccessible(true);
        Optional<?> range = (Optional<?>) method.invoke(null, player);
        return range.map(value -> ((Number) value).doubleValue()).orElse(null);
    }

    private static SoundTracker.SoundRecord findSound(String soundId, BlockPos pos, String dimension) {
        return SoundTracker.getTrackedSoundsSnapshot().stream()
                .filter(record -> dimension.equals(record.dimensionKey)
                        && record.pos.equals(pos) && soundId.equals(record.soundId))
                .findFirst().orElse(null);
    }
}
