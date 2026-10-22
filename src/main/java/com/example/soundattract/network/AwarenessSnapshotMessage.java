package com.example.soundattract.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record AwarenessSnapshotMessage(float visibility, float noise, int scent, boolean windshielded, List<Alert> alerts) implements CustomPacketPayload {
    public static final Type<AwarenessSnapshotMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("soundattract", "awareness_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, AwarenessSnapshotMessage> STREAM_CODEC = new StreamCodec<>() {
        @Override public AwarenessSnapshotMessage decode(FriendlyByteBuf buffer) { return AwarenessSnapshotMessage.decode(buffer); }
        @Override public void encode(FriendlyByteBuf buffer, AwarenessSnapshotMessage message) { AwarenessSnapshotMessage.encode(message, buffer); }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
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

    public record Alert(UUID mobId, int entityId, int type, int color, float progress, long triggerId) {
        public Alert(UUID mobId, int entityId, int type, int color, float progress) {
            this(mobId, entityId, type, color, progress, 0L);
        }
    }

    public static void encode(AwarenessSnapshotMessage message, FriendlyByteBuf buffer) {
        buffer.writeFloat(message.visibility());
        buffer.writeFloat(message.noise());
        buffer.writeByte(message.scent());
        buffer.writeBoolean(message.windshielded());
        buffer.writeVarInt(message.alerts().size());
        for (Alert alert : message.alerts()) {
            buffer.writeUUID(alert.mobId());
            buffer.writeVarInt(alert.entityId());
            buffer.writeByte(alert.type());
            buffer.writeInt(alert.color());
            buffer.writeFloat(alert.progress());
        }


        buffer.writeVarInt(1);
        for (Alert alert : message.alerts()) {
            buffer.writeVarLong(alert.triggerId());
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
        if (buffer.isReadable()) {
            int extensionVersion = buffer.readVarInt();
            if (extensionVersion != 1) throw new DecoderException("Invalid awareness extension: " + extensionVersion);
            for (int i = 0; i < count; i++) {
                Alert alert = alerts.get(i);
                alerts.set(i, new Alert(alert.mobId(), alert.entityId(), alert.type(), alert.color(),
                        alert.progress(), buffer.readVarLong()));
            }
            if (buffer.isReadable()) throw new DecoderException("Unexpected awareness extension data");
        }
        return new AwarenessSnapshotMessage(visibility, noise, scent, windshielded, alerts);
    }
}
