package com.example.soundattract.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobAlertEntityBlacklistTest {
    @Test
    void armorStandIsBlacklistedByDefault() {
        var defaults = ClientConfig.MOB_ALERT_ENTITY_BLACKLIST.getDefault();
        assertTrue(defaults.contains("minecraft:armor_stand"));
        assertTrue(ClientConfig.isMobAlertEntityBlacklisted("minecraft:armor_stand", defaults));
        assertFalse(ClientConfig.isMobAlertEntityBlacklisted("minecraft:zombie", defaults));
        assertFalse(ClientConfig.isMobAlertEntityBlacklisted(null, defaults));
    }
}
