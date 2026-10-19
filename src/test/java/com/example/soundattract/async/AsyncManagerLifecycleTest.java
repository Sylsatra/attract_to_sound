package com.example.soundattract.async;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AsyncManagerLifecycleTest {
    @Test
    void mainThreadCallFailsPromptlyWithoutAnActiveServer() {
        AsyncManager.onServerStopped();

        var future = AsyncManager.callOnMain(() -> "unreachable");

        assertTrue(future.isCompletedExceptionally());
    }
}
