package com.example.soundattract.integration.spore;

import com.Harbinger.Spore.Sentities.Organoids.Proto;
import com.example.soundattract.config.separate.IntegrationConfig;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class IntersectionUtils {
    
    /**
     * Gets the effective range for Proto to detect and react to sounds.
     * Uses config value if available, otherwise falls back to follow range attribute.
     */
    public static double getProtoDetectionRange(Proto proto) {
        if (proto == null) return 0;
        
        double configRange = IntegrationConfig.PROTO_SPREAD_MAX_DISTANCE.get();
        if (configRange > 0) {
            return configRange;
        }
        
        return proto.getAttributeValue(Attributes.FOLLOW_RANGE);
    }
}
