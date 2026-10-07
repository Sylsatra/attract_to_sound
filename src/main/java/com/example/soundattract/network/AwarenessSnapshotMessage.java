package com.example.soundattract.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.DistExecutor;

public record AwarenessSnapshotMessage(float visibility, float noise, int scent, boolean windshielded, List<Alert> alerts) {
    private static final int MAX_ALERTS = 128;

    public AwarenessSnapshotMessage {
        visibility = Float.isFinite(visibility) ? Math.max(0.0f, Math.min(1.0f, visibility)) : 0.0f;
        noise = Float.isFinite(noise) ? Math.max(0.0f, Math.min(1.0f, noise)) : 0.0f;
        scent = Math.max(0, Math.min(5, scent));
        if (alerts == null || alerts.isEmpty()) {
            alerts = List.of();
        } else {
            List<Alert> sanitized = new ArrayList<>(Math.min(MAX_ALERTS, alerts.size()));
            for (Alert alert : alerts) {
                if (alert != null && alert.mobId() != null) {
                    sanitized.add(alert);
                    if (sanitized.size() == MAX_ALERTS) {
                        break;
                    }
                }
            }
            alerts = sanitized.isEmpty() ? List.of() : Collections.unmodifiableList(sanitized);
        }
    }

    public record Alert(UUID mobId, int entityId, int type, int color, float progress) {
    }

    public static void encode(AwarenessSnapshotMessage message, FriendlyByteBuf buffer) {
        buffer.writeFloat(message.visibility);
        buffer.writeFloat(message.noise);
        buffer.writeByte(message.scent);
        buffer.writeBoolean(message.windshielded);
        buffer.writeVarInt(message.alerts.size());
        for (Alert alert : message.alerts) {
            buffer.writeUUID(alert.mobId);
            buffer.writeVarInt(alert.entityId);
            buffer.writeByte(alert.type);
            buffer.writeInt(alert.color);
            buffer.writeFloat(alert.progress);
        }
    }

    public static AwarenessSnapshotMessage decode(FriendlyByteBuf buffer) {
        float visibility = buffer.readFloat();
        float noise = buffer.readFloat();
        int scent = buffer.readUnsignedByte();
        boolean windshielded = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ALERTS) {
            throw new DecoderException("Invalid awareness alert count: " + count);
        }
        List<Alert> alerts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            alerts.add(new Alert(buffer.readUUID(), buffer.readVarInt(), buffer.readUnsignedByte(), buffer.readInt(), buffer.readFloat()));
        }
        return new AwarenessSnapshotMessage(visibility, noise, scent, windshielded, alerts);
    }

    public static void handle(AwarenessSnapshotMessage message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> com.example.soundattract.client.AwarenessClientState.apply(message)));
        context.setPacketHandled(true);
    }
}