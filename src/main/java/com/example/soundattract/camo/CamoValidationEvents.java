package com.example.soundattract.camo;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.network.PacketCamoRemoval;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

@EventBusSubscriber(modid = SoundAttractMod.MOD_ID, value = Dist.CLIENT)
public class CamoValidationEvents {

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?, ?> event) {
        if (!(CamoRenderStateEntities.get(event.getRenderState()) instanceof Player player)) return;
        
        if (player != Minecraft.getInstance().player) return;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;

            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;

            if (com.example.soundattract.util.CamoUtil.hasCustomTag(stack) && (com.example.soundattract.util.CamoUtil.getCustomTag(stack).contains("soundattract:CamoLayers") || com.example.soundattract.util.CamoUtil.getCustomTag(stack).contains("soundattract:CamoStrength"))) {
                
                if (!ArmorValidationTracker.wasSlotValidated(player, slot)) {
                    
                    ClientPacketDistributor.sendToServer(new PacketCamoRemoval(slot));
                    
                    com.example.soundattract.util.CamoUtil.getCustomTag(stack).remove("soundattract:CamoLayers");
                    com.example.soundattract.util.CamoUtil.getCustomTag(stack).remove("soundattract:CamoStrength");
                    com.example.soundattract.util.CamoUtil.getCustomTag(stack).remove("soundattract:CamoColor");
                    com.example.soundattract.util.CamoUtil.getCustomTag(stack).remove("soundattract:CamoSeed");
                }
            }
        }
    }
}
