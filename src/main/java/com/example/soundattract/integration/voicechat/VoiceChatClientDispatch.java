package com.example.soundattract.integration.voicechat;

import de.maxhenkel.voicechat.api.events.ClientSoundEvent;

public final class VoiceChatClientDispatch {
    private VoiceChatClientDispatch() {}

    public static void handle(ClientSoundEvent event) {
        try {
            Class<?> handler = Class.forName("com.example.soundattract.integration.voicechat.VoiceChatIntegrationClient");
            handler.getMethod("handleClientSound", ClientSoundEvent.class).invoke(null, event);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
