package com.example.soundattract.pathfinding;

public final class PFStats {
    private PFStats() {}

    public static volatile long pathAttemptsConsumed = 0;
    public static volatile long pathAttemptsRejected = 0;

    public static volatile long moveToIssued = 0;
    public static volatile long moveToDroppedCooldown = 0;
    public static volatile long moveToDroppedDelta = 0;
    public static volatile long moveToFallbackAttempted = 0;
    public static volatile long moveToFallbackSucceeded = 0;

    public static String snapshotAndReset() {
        long a = pathAttemptsConsumed;
        long r = pathAttemptsRejected;
        long i = moveToIssued;
        long c = moveToDroppedCooldown;
        long d = moveToDroppedDelta;
        long fa = moveToFallbackAttempted;
        long fs = moveToFallbackSucceeded;
        pathAttemptsConsumed = 0;
        pathAttemptsRejected = 0;
        moveToIssued = 0;
        moveToDroppedCooldown = 0;
        moveToDroppedDelta = 0;
        moveToFallbackAttempted = 0;
        moveToFallbackSucceeded = 0;
        return "PFStats: path(consumed=" + a + ", rejected=" + r + ") moveTo(issued=" + i + ", droppedCooldown=" + c + ", droppedDelta=" + d + ", fallback=" + fs + "/" + fa + ")";
    }
}
