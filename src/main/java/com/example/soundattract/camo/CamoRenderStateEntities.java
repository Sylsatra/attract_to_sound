package com.example.soundattract.camo;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

public final class CamoRenderStateEntities {
    private static final Map<LivingEntityRenderState, LivingEntity> ENTITIES = Collections.synchronizedMap(new WeakHashMap<>());
    private CamoRenderStateEntities() {}
    public static void put(LivingEntityRenderState state, LivingEntity entity) { ENTITIES.put(state, entity); }
    public static LivingEntity get(LivingEntityRenderState state) { return ENTITIES.get(state); }
}
