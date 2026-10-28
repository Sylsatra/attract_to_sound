package com.example.soundattract.gametest;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;

/** Captures real outbound break-progress packets only while the focused GameTest is active. */
public final class BlockBreakProgressPacketProbe {
    public record Progress(int breakerId, BlockPos pos, int stage) {}

    private static final List<Progress> CAPTURED = new CopyOnWriteArrayList<>();
    private static volatile boolean enabled;

    private BlockBreakProgressPacketProbe() {}

    public static void begin() {
        CAPTURED.clear();
        enabled = true;
    }

    public static List<Progress> finish() {
        enabled = false;
        List<Progress> result = List.copyOf(CAPTURED);
        CAPTURED.clear();
        return result;
    }

    public static void capture(Packet<?> packet) {
        if (enabled && packet instanceof ClientboundBlockDestructionPacket progress) {
            CAPTURED.add(new Progress(progress.getId(), progress.getPos().immutable(), progress.getProgress()));
        }
    }
}
