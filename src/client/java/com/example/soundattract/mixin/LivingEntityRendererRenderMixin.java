package com.example.soundattract.mixin;

import com.example.soundattract.camo.ArmorValidationTracker;
import com.example.soundattract.camo.CamoRenderStateEntities;
import com.example.soundattract.network.PacketCamoRemoval;
import com.example.soundattract.network.SoundAttractClientNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererRenderMixin {
    @Inject(method = "submit", at = @At("HEAD"))
    private void soundattract$beforeSubmit(LivingEntityRenderState state, PoseStack poseStack,
                                           SubmitNodeCollector collector, CameraRenderState camera,
                                           CallbackInfo ci) {
        LivingEntity entity = CamoRenderStateEntities.get(state);
        if (entity != null) ArmorValidationTracker.clearForEntity(entity);
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void soundattract$afterSubmit(LivingEntityRenderState state, PoseStack poseStack,
                                          SubmitNodeCollector collector, CameraRenderState camera,
                                          CallbackInfo ci) {
        LivingEntity entity = CamoRenderStateEntities.get(state);
        if (!(entity instanceof Player player) || player != Minecraft.getInstance().player) return;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) continue;
            net.minecraft.nbt.CompoundTag tag = stack.getOrDefault(
                    net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
            if (!tag.contains("soundattract:CamoLayers") && !tag.contains("soundattract:CamoStrength")) continue;
            if (!ArmorValidationTracker.wasSlotValidated(player, slot)) {
                SoundAttractClientNetworking.sendCamoRemovalToServer(new PacketCamoRemoval(slot));
                tag.remove("soundattract:CamoLayers");
                tag.remove("soundattract:CamoStrength");
                tag.remove("soundattract:CamoColor");
                tag.remove("soundattract:CamoSeed");
                if (tag.isEmpty()) stack.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                else stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.of(tag));
            }
        }
    }
}
