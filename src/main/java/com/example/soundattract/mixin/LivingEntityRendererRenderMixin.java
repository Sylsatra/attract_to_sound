package com.example.soundattract.mixin;

import com.example.soundattract.camo.ArmorValidationTracker;
import com.example.soundattract.camo.GenericMobCamoLayer;
import com.example.soundattract.network.PacketCamoRemoval;
import com.example.soundattract.network.SoundAttractNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererRenderMixin<T extends LivingEntity, M extends net.minecraft.client.model.EntityModel<T>> {

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderPre(T entity, float entityYaw, float partialTicks, com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        ArmorValidationTracker.clearForEntity(entity);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderPost(T entity, float entityYaw, float partialTicks, com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (entity instanceof Player player) {
            if (player != Minecraft.getInstance().player) return;

            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;

                ItemStack stack = player.getItemBySlot(slot);
                if (stack.isEmpty()) continue;

                if (!stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) continue;

                net.minecraft.nbt.CompoundTag tag = stack.getOrDefault(
                        net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
                if (!tag.contains("soundattract:CamoLayers") && !tag.contains("soundattract:CamoStrength")) continue;

                if (!ArmorValidationTracker.wasSlotValidated(player, slot)) {
                    SoundAttractNetwork.sendCamoRemovalToServer(new PacketCamoRemoval(slot));

                    tag.remove("soundattract:CamoLayers");
                    tag.remove("soundattract:CamoStrength");
                    tag.remove("soundattract:CamoColor");
                    tag.remove("soundattract:CamoSeed");
                    if (tag.isEmpty()) {
                        stack.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                    } else {
                        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                                net.minecraft.world.item.component.CustomData.of(tag));
                    }
                }
            }
        }
    }
}
