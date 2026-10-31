package com.example.soundattract.camo;

import com.example.soundattract.SoundAttractMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
@EventBusSubscriber(modid = SoundAttractMod.MOD_ID, value = Dist.CLIENT)
public class CamoRuntimeEvents {

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?, ?> event) {
        var entity = CamoRenderStateEntities.get(event.getRenderState());
        if (entity != null) ArmorValidationTracker.clearForEntity(entity);
    }
}
