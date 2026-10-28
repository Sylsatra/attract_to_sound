package com.example.soundattract.gecko;

import com.example.soundattract.camo.CamouflageCapability;
import com.example.soundattract.camo.CamoLODUtil;
import com.example.soundattract.util.CamoUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderState;

import java.util.Optional;

/** Immutable camouflage inputs captured before GeckoLib defers its render submission. */
public record GeckoCamoData(int color, float strength, long seed, boolean masked, int resolution,
                            float erosion, float humidity, float temperature) {
    public static final DataTicket<GeckoCamoData> TICKET =
            DataTicket.create("soundattract:camo", GeckoCamoData.class);

    public static GeckoCamoData fromEntity(LivingEntity living) {
        if (living.isInvisible()) {
            return null;
        }

        Optional<CamouflageCapability> capability = CamouflageCapability.getCapability(living);
        if (capability.isEmpty() || capability.get().getLayers().isEmpty()) {
            return null;
        }

        CamouflageCapability camo = capability.get();
        Optional<Integer> color = camo.getBlendedColor();
        float strength = camo.getVisualCamoStrength();
        if (color.isEmpty() || strength <= 0.05f) {
            return null;
        }

        return new GeckoCamoData(color.get(), strength, camo.getDisplaySeed(), true,
                CamoLODUtil.getLOD(living).resolution(), camo.getDisplayErosion(),
                camo.getDisplayHumidity(), camo.getDisplayTemperature());
    }

    public static GeckoCamoData fromArmor(LivingEntity living, ItemStack stack) {
        if (living.isInvisible() || stack.isEmpty()) {
            return null;
        }

        CompoundTag tag = CamoUtil.getCustomTagReadOnly(stack);
        if (tag == null || tag.isEmpty() || !tag.contains("soundattract:CamoLayers")) {
            return null;
        }

        var layers = tag.getListOrEmpty("soundattract:CamoLayers");
        CamoUtil.BlendedCamoData blended = CamoUtil.getBlendedDataFromTag(layers);
        if (blended.color().isEmpty()) {
            return null;
        }

        float strength = blended.strength() * 2.0f;
        if (strength <= 0.05f) {
            return null;
        }

        return new GeckoCamoData(blended.color().get(), strength, blended.seed(), false,
                CamoLODUtil.getLOD(living).resolution(), blended.erosion(), blended.humidity(), blended.temp());
    }

    public static void put(GeoRenderState state, GeckoCamoData data) {
        if (data == null) {
            state.getDataMap().remove(TICKET);
        } else {
            state.addGeckolibData(TICKET, data);
        }
    }
}
