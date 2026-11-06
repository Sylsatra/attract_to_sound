package com.example.soundattract.camo;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

@EventBusSubscriber(modid = SoundAttractMod.MOD_ID, value = Dist.CLIENT)
public class CamoClientEvents {

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        event.getSkins().forEach(skin -> {
            AvatarRenderer<?> renderer = event.getPlayerRenderer(skin);
            if (renderer != null) {
                renderer.addLayer(new CamoRenderLayer(renderer));
            }
        });

        tryAddLayer(event, "zombie");
        tryAddLayer(event, "husk");
        tryAddLayer(event, "drowned");
        tryAddLayer(event, "skeleton");
        tryAddLayer(event, "stray");
        tryAddLayer(event, "wither_skeleton");
        tryAddLayer(event, "piglin");
        tryAddLayer(event, "piglin_brute");
        tryAddLayer(event, "zombified_piglin");
        tryAddLayer(event, "pillager");
        tryAddLayer(event, "vindicator");
        tryAddLayer(event, "evoker");
        tryAddLayer(event, "illusioner");
        tryAddLayer(event, "villager");
        tryAddLayer(event, "wandering_trader");
    }

    @SubscribeEvent
    public static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        CamoRenderState.register(event);
    }

    @SuppressWarnings("unchecked")
    private static <T extends LivingEntity> void tryAddLayer(EntityRenderersEvent.AddLayers event, String entityId) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(Identifier.withDefaultNamespace(entityId)).map(reference -> reference.value()).orElse(null);
        if (type == null) return;

        EntityRenderer<?, ?> renderer;
        try {
            renderer = event.getRenderer((EntityType<? extends T>) type);
        } catch (Exception e) {
            return;
        }

        if (renderer instanceof LivingEntityRenderer livingRenderer) {
            var model = livingRenderer.getModel();
            
            if (model instanceof IllagerModel illager) {
                livingRenderer.addLayer(new GenericMobCamoLayer.Illager(livingRenderer, state -> livingRenderer.getTextureLocation(state)));
            } else if (model instanceof VillagerModel villager) {
                livingRenderer.addLayer(new GenericMobCamoLayer.VillagerLayer(livingRenderer, state -> livingRenderer.getTextureLocation(state)));
            } else if (model instanceof HumanoidModel humanoid) {
                livingRenderer.addLayer(new GenericMobCamoLayer<HumanoidRenderState, HumanoidModel<HumanoidRenderState>>(livingRenderer, livingRenderer::getTextureLocation));
            }
        }
    }
}
