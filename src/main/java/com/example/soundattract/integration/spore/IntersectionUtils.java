package com.example.soundattract.integration.spore;

import com.Harbinger.Spore.Sentities.Organoids.Proto;
import com.example.soundattract.config.separate.IntegrationConfig;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class IntersectionUtils {


    public static double getProtoDetectionRange(Proto proto) {
        if (proto == null) return 0;

        double configRange = IntegrationConfig.PROTO_SPREAD_MAX_DISTANCE.get();
        if (configRange > 0) {
            return configRange;
        }

        return proto.getAttributeValue(Attributes.FOLLOW_RANGE);
    }
}