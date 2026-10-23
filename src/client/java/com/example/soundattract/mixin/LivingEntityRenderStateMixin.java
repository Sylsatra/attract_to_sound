package com.example.soundattract.mixin;

import com.example.soundattract.camo.CamoRenderStateEntities;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRenderStateMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void soundattract$storeEntity(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        CamoRenderStateEntities.put(state, entity);
    }
}
