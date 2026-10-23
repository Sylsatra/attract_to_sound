package com.example.soundattract.integration.immersive_melodies;

import com.example.soundattract.tracking.SoundTracker;
import com.example.soundattract.config.separate.IntegrationConfig;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

/** Verifies the optional integration against the actual 1.21.1 Immersive Melodies artifact. */
public final class ImmersiveMelodiesGameTests {
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void playingComponentCreatesVirtualSoundFromEitherHandAndPausedInstrumentDoesNot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer mainHandPlayer = null;
        ServerPlayer offHandPlayer = null;
        ServerPlayer pausedPlayer = null;
        String dimension = level.dimension().identifier().toString();
        List<? extends String> previousOverrides = IntegrationConfig.IMMERSIVE_MELODIES_MELODY_OVERRIDES.get();
        try {
            mainHandPlayer = helper.makeMockServerPlayerInLevel();
            offHandPlayer = helper.makeMockServerPlayerInLevel();
            pausedPlayer = helper.makeMockServerPlayerInLevel();
            mainHandPlayer.setPos(helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2)).getCenter());
            offHandPlayer.setPos(helper.absolutePos(new net.minecraft.core.BlockPos(5, 2, 2)).getCenter());
            pausedPlayer.setPos(helper.absolutePos(new net.minecraft.core.BlockPos(8, 2, 2)).getCenter());
            mainHandPlayer.setItemInHand(InteractionHand.MAIN_HAND, instrumentStack(true));
            offHandPlayer.setItemInHand(InteractionHand.OFF_HAND, instrumentStack(true));
            pausedPlayer.setItemInHand(InteractionHand.MAIN_HAND, instrumentStack(false));

            String defaultMelody = ImmersiveMelodiesComponents.melodyId(mainHandPlayer.getItemInHand(InteractionHand.MAIN_HAND));
            if (!"immersive_melodies:default".equals(defaultMelody)) {
                helper.fail("Instrument default melody should resolve through Immersive Melodies, got " + defaultMelody);
                return;
            }
            IntegrationConfig.IMMERSIVE_MELODIES_MELODY_OVERRIDES.set(List.of("immersive_melodies:default:32.0:7.0"));

            String mainId = integrationId(mainHandPlayer);
            String offId = integrationId(offHandPlayer);
            String pausedId = integrationId(pausedPlayer);
            ImmersiveMelodiesEvents.checkAndAddMusicSound(mainHandPlayer);
            ImmersiveMelodiesEvents.checkAndAddMusicSound(offHandPlayer);
            ImmersiveMelodiesEvents.checkAndAddMusicSound(pausedPlayer);

            SoundTracker.SoundRecord mainRecord = findSound(mainId, dimension);
            boolean mainHandTracked = mainRecord != null;
            boolean offHandTracked = hasSound(offId, dimension);
            boolean pausedNotTracked = !hasSound(pausedId, dimension);
            boolean melodyOverrideApplied = mainRecord != null
                    && Math.abs(mainRecord.range - 32.0) < 0.0001
                    && Math.abs(mainRecord.weight - 7.0) < 0.0001;
            if (!mainHandTracked || !offHandTracked || !pausedNotTracked || !melodyOverrideApplied) {
                helper.fail("Immersive Melodies component integration failed: mainHand=" + mainHandTracked
                        + ", offHand=" + offHandTracked + ", pausedNotTracked=" + pausedNotTracked
                        + ", defaultMelodyOverrideApplied=" + melodyOverrideApplied);
                return;
            }
        } finally {
            IntegrationConfig.IMMERSIVE_MELODIES_MELODY_OVERRIDES.set(previousOverrides);
            removePlayer(level, mainHandPlayer, dimension);
            removePlayer(level, offHandPlayer, dimension);
            removePlayer(level, pausedPlayer, dimension);
        }
        helper.succeed();
    }

    private static ItemStack instrumentStack(boolean playing) {
        try {
            Class<?> instrumentClass = Class.forName("immersive_melodies.item.InstrumentItem", false,
                    ImmersiveMelodiesGameTests.class.getClassLoader());
            Item instrument = BuiltInRegistries.ITEM.stream().filter(instrumentClass::isInstance).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Immersive Melodies registered no instrument items"));
            ItemStack stack = new ItemStack(instrument);
            setComponent(stack, instrumentClass.getField("PLAYING"), playing);
            return stack;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unable to construct an Immersive Melodies instrument fixture", failure);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setComponent(ItemStack stack, Field field, Object value) throws ReflectiveOperationException {
        stack.set((DataComponentType) field.get(null), value);
    }

    private static String integrationId(ServerPlayer player) {
        return SoundTracker.buildIntegrationSoundId(Identifier.fromNamespaceAndPath("soundattract", "virtual"),
                player.getUUID() + "/ImmersiveMelody");
    }

    private static boolean hasSound(String id, String dimension) {
        return findSound(id, dimension) != null;
    }

    private static SoundTracker.SoundRecord findSound(String id, String dimension) {
        return SoundTracker.getTrackedSoundsSnapshot().stream()
                .filter(record -> id.equals(record.soundId) && dimension.equals(record.dimensionKey))
                .findFirst().orElse(null);
    }

    private static void removePlayer(ServerLevel level, ServerPlayer player, String dimension) {
        if (player == null) return;
        SoundTracker.removeSoundAt(player.blockPosition(), dimension);
        level.getServer().getPlayerList().remove(player);
    }
}
