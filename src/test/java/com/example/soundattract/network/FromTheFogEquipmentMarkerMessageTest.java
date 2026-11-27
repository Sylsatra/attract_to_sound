package com.example.soundattract.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FromTheFogEquipmentMarkerMessageTest {
    @Test
    void payloadRoundTripsEntityIdentityAndVisibilityState() {
        var expected = new FromTheFogEquipmentMarkerMessage(UUID.randomUUID(), true);
        ByteBuf buffer = Unpooled.buffer();
        try {
            FromTheFogEquipmentMarkerMessage.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, FromTheFogEquipmentMarkerMessage.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
