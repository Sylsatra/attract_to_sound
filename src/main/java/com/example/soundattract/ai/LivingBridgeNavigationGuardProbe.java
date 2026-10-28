package com.example.soundattract.ai;

import java.util.concurrent.atomic.AtomicInteger;

public final class LivingBridgeNavigationGuardProbe {
    private static final AtomicInteger INVOCATIONS = new AtomicInteger();

    private LivingBridgeNavigationGuardProbe() {
    }

    public static void recordInvocation() {
        INVOCATIONS.incrementAndGet();
    }

    public static int invocationCount() {
        return INVOCATIONS.get();
    }

    public static void reset() {
        INVOCATIONS.set(0);
    }
}