package com.example.soundattract.scents;

public enum ScentSourceType {
    PLAYER_WALK,
    ARROW_ORIGIN,
    ARROW_PATH,
    MOB_PROJECTILE_ORIGIN,
    MOB_PROJECTILE_PATH,
    PLAYER_WIND;

    public static ScentSourceType fromNameOrDefault(String name) {
        if (name == null || name.isEmpty()) return PLAYER_WALK;
        try {
            return ScentSourceType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return PLAYER_WALK;
        }
    }
}
