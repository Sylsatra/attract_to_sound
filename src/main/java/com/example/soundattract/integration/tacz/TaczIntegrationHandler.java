package com.example.soundattract.integration.tacz;

import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;

public class TaczIntegrationHandler {
    
    public static void register() {
        GunShootEvent.CALLBACK.register(TaczIntegration::onGunShoot);
        GunReloadEvent.CALLBACK.register(TaczIntegration::onGunReload);
    }
}
