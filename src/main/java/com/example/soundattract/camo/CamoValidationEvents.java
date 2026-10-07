package com.example.soundattract.camo;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.network.PacketCamoRemoval;
import com.example.soundattract.network.SoundAttractNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, value = Dist.CLIENT)
public class CamoValidationEvents {

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (player != Minecraft.getInstance().player) return;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) continue;

            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;

            if (stack.hasTag() && (stack.getTag().contains("soundattract:CamoLayers") || stack.getTag().contains("soundattract:CamoStrength"))) {

                if (!ArmorValidationTracker.wasSlotValidated(player, slot)) {

                    SoundAttractNetwork.INSTANCE.sendToServer(new PacketCamoRemoval(slot));

                    stack.getTag().remove("soundattract:CamoLayers");
                    stack.getTag().remove("soundattract:CamoStrength");
                    stack.getTag().remove("soundattract:CamoColor");
                    stack.getTag().remove("soundattract:CamoSeed");
                }
            }
        }
    }
}