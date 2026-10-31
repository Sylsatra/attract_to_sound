package com.example.soundattract.network;

import com.example.soundattract.client.AwarenessClientState;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AwarenessSnapshotMessage(float visibility, float noise, int scent, boolean windshielded, List<Alert> alerts) implements CustomPacketPayload {
    private static final int MAX_ALERTS = 128;
    public static final Type<AwarenessSnapshotMessage> TYPE = new Type<>(Identifier.fromNamespaceAndPath("soundattract", "awareness_snapshot"));
    public static final StreamCodec<ByteBuf, AwarenessSnapshotMessage> STREAM_CODEC = StreamCodec.of(AwarenessSnapshotMessage::encode, AwarenessSnapshotMessage::decode);

    public AwarenessSnapshotMessage {
        visibility = Float.isFinite(visibility) ? Math.max(0.0f, Math.min(1.0f, visibility)) : 0.0f;
        noise = Float.isFinite(noise) ? Math.max(0.0f, Math.min(1.0f, noise)) : 0.0f;
        scent = Math.max(0, Math.min(5, scent));
        if (alerts == null || alerts.isEmpty()) alerts = List.of();
        else {
            List<Alert> sanitized = new ArrayList<>(Math.min(MAX_ALERTS, alerts.size()));
            for (Alert alert : alerts) {
                if (alert != null && alert.mobId() != null) {
                    sanitized.add(alert);
                    if (sanitized.size() == MAX_ALERTS) break;
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

    private static void encode(ByteBuf buffer, AwarenessSnapshotMessage message) {
        buffer.writeFloat(message.visibility());
        buffer.writeFloat(message.noise());
        buffer.writeByte(message.scent());
        buffer.writeBoolean(message.windshielded());
        writeVarInt(buffer, message.alerts().size());
        for (Alert alert : message.alerts()) {
            buffer.writeLong(alert.mobId().getMostSignificantBits());
            buffer.writeLong(alert.mobId().getLeastSignificantBits());
            writeVarInt(buffer, alert.entityId());
            buffer.writeByte(alert.type());
            buffer.writeInt(alert.color());
            buffer.writeFloat(alert.progress());
        }

        writeVarInt(buffer, 1);
        for (Alert alert : message.alerts()) writeVarLong(buffer, alert.triggerId());
    }

    private static AwarenessSnapshotMessage decode(ByteBuf buffer) {
        float visibility = buffer.readFloat();
        float noise = buffer.readFloat();
        int scent = buffer.readUnsignedByte();
        boolean windshielded = buffer.readBoolean();
        int count = readVarInt(buffer);
        if (count < 0 || count > MAX_ALERTS) throw new DecoderException("Invalid awareness alert count: " + count);
        List<Alert> alerts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) alerts.add(new Alert(new UUID(buffer.readLong(), buffer.readLong()), readVarInt(buffer), buffer.readUnsignedByte(), buffer.readInt(), buffer.readFloat()));

        if (buffer.isReadable()) {
            int extensionVersion = readVarInt(buffer);
            if (extensionVersion != 1) throw new DecoderException("Unsupported awareness snapshot extension: " + extensionVersion);
            for (int i = 0; i < count; i++) {
                Alert alert = alerts.get(i);
                alerts.set(i, new Alert(alert.mobId(), alert.entityId(), alert.type(), alert.color(), alert.progress(), readVarLong(buffer)));
            }
            if (buffer.isReadable()) throw new DecoderException("Trailing data in awareness snapshot");
        }
        return new AwarenessSnapshotMessage(visibility, noise, scent, windshielded, alerts);
    }

    private static void writeVarInt(ByteBuf buffer, int value) {
        while ((value & -128) != 0) { buffer.writeByte(value & 127 | 128); value >>>= 7; }
        buffer.writeByte(value);
    }

    private static int readVarInt(ByteBuf buffer) {
        int value = 0, position = 0;
        byte current;
        do {
            current = buffer.readByte();
            value |= (current & 127) << position;
            position += 7;
            if (position >= 32) throw new DecoderException("VarInt too big");
        } while ((current & 128) != 0);
        return value;
    }

    private static void writeVarLong(ByteBuf buffer, long value) {
        while ((value & -128L) != 0L) {
            buffer.writeByte((int) (value & 127L) | 128);
            value >>>= 7;
        }
        buffer.writeByte((int) value);
    }

    private static long readVarLong(ByteBuf buffer) {
        long value = 0L;
        for (int shift = 0; shift < 64; shift += 7) {
            int current = buffer.readUnsignedByte();
            if (shift == 63 && (current & 0xFE) != 0) throw new DecoderException("VarLong too big");
            value |= (long) (current & 127) << shift;
            if ((current & 128) == 0) return value;
        }
        throw new DecoderException("VarLong too big");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(AwarenessSnapshotMessage message, IPayloadContext context) { context.enqueueWork(() -> AwarenessClientState.apply(message)); }
}
