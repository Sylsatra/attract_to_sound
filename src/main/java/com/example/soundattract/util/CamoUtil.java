package com.example.soundattract.util;

import com.example.soundattract.Soundattract;
import com.example.soundattract.camo.CamoLayer;
import com.example.soundattract.camo.CamouflageCapability;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.config.separate.StealthConfig;
import com.example.soundattract.data.DataDrivenTags;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CamoUtil {

    private CamoUtil() {}

    public static boolean hasCustomTag(ItemStack stack) {
        return stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
    }

    public static CompoundTag getCustomTag(ItemStack stack) {
        return stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /** Returns the component's backing tag for read-only access. Never mutate this tag. */
    public static CompoundTag getCustomTagReadOnly(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static CompoundTag getOrCreateCustomTag(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        if (tag == null || tag.isEmpty()) {
            tag = new CompoundTag();
        }
        return tag;
    }

    public static void setCustomTag(ItemStack stack, CompoundTag tag) {
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static boolean isCamouflageArmorItem(Item item) {
        if (item == null) {
            return false;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) {
            return false;
        }

        boolean inConfig = SoundAttractConfig.COMMON.camouflageArmorItems.get().contains(id.toString());

        boolean enableDataDriven = SoundAttractConfig.COMMON.enableDataDriven.get();
        if (!enableDataDriven) {
            return inConfig;
        }

        boolean inTag = false;
        try {
            inTag = new net.minecraft.world.item.ItemStack(item).is(DataDrivenTags.CAMOUFLAGE_ARMOR);
        } catch (Exception e) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.warn("[CamoUtil] Failed to check CAMOUFLAGE_ARMOR tag for {}: {}", id, e.getMessage());
            }
        }

        if (!inTag) {
            return inConfig;
        }

        String priority = SoundAttractConfig.COMMON.datapackPriority.get();
        boolean datapackOverConfig = "datapack_over_config".equalsIgnoreCase(priority);
        if (datapackOverConfig) {
            return inTag;
        } else {
            return inConfig || inTag;
        }
    }

    /**
     * Aggregates all camouflage layers from both the skin capability and armor NBT.
     */
    public static List<CamoLayer> getTotalCamoLayers(LivingEntity entity) {
        List<CamoLayer> total = new ArrayList<>();
        

        CamouflageCapability.getCapability(entity).ifPresent(cap -> {
            total.addAll(cap.getLayers());
        });


        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                ItemStack stack = entity.getItemBySlot(slot);
                if (!stack.isEmpty() && hasCustomTag(stack)) {
                    CompoundTag tag = getCustomTagReadOnly(stack);
                    if (tag.get("soundattract:CamoLayers") instanceof net.minecraft.nbt.ListTag) {
                        ListTag list = tag.getListOrEmpty("soundattract:CamoLayers");
                        for (int i = 0; i < list.size(); i++) {
                            total.add(CamoLayer.load(list.getCompoundOrEmpty(i)));
                        }
                    } else if (tag.contains("soundattract:CamoStrength")) {
                        int color = tag.getIntOr("soundattract:CamoColor", 0);
                        float strength = tag.getFloatOr("soundattract:CamoStrength", 0.0F);
                        long seed = tag.getLongOr("soundattract:CamoSeed", 0L);
                        total.add(new CamoLayer(color, "legacy", strength, strength, false, entity.level().getGameTime(), seed, 0, 0, 0));
                    }
                }
            }
        }
        return total;
    }

    /**
     * Calculates the blended color of all active camouflage smudges.
     */
    public static Optional<Integer> getBlendedCamoColor(LivingEntity entity) {
        List<CamoLayer> layers = getTotalCamoLayers(entity);
        if (layers.isEmpty()) return Optional.empty();

        float totalR = 0, totalG = 0, totalB = 0, totalWeight = 0;
        for (CamoLayer layer : layers) {
            float weight = layer.durability();
            totalR += ((layer.color() >> 16) & 0xFF) * weight;
            totalG += ((layer.color() >> 8) & 0xFF) * weight;
            totalB += (layer.color() & 0xFF) * weight;
            totalWeight += weight;
        }

        if (totalWeight <= 0) return Optional.empty();

        int r = (int) (totalR / totalWeight);
        int g = (int) (totalG / totalWeight);
        int b = (int) (totalB / totalWeight);
        return Optional.of((r << 16) | (g << 8) | b);
    }

    /**
     * Calculates the "base" color of the entity (skin + armor dyes/config colors).
     */
    public static int getEffectiveBaseColor(LivingEntity entity) {
        List<Integer> colors = new ArrayList<>();
        boolean onlyDyedLeather = SoundAttractConfig.COMMON.environmentalCamouflageOnlyDyedLeather.get();

        for (ItemStack itemStack : com.example.soundattract.util.ArmorSlots.get(entity)) {
            if (itemStack.isEmpty()) continue;
            Item item = itemStack.getItem();
            boolean colorAdded = false;

            if (itemStack.get(DataComponents.EQUIPPABLE) != null && itemStack.get(DataComponents.EQUIPPABLE).assetId().map(key -> key.equals(net.minecraft.world.item.equipment.EquipmentAssets.LEATHER)).orElse(false)) {
                net.minecraft.world.item.component.DyedItemColor color = itemStack.get(DataComponents.DYED_COLOR);
                if (color != null) {
                    colors.add(color.rgb());
                    colorAdded = true;
                }
            }

            if (!colorAdded && !onlyDyedLeather) {
                Identifier itemIdRL = BuiltInRegistries.ITEM.getKey(item);
                if (itemIdRL != null) {
                    Integer mapped = SoundAttractConfig.customArmorColors.get(itemIdRL);
                    if (mapped != null) {
                        colors.add(mapped);
                    }
                }
            }
        }


        if (colors.isEmpty()) {
            return 0x8D5932;
        }

        long totalR = 0, totalG = 0, totalB = 0;
        for (int color : colors) {
            totalR += (color >> 16) & 0xFF;
            totalG += (color >> 8) & 0xFF;
            totalB += color & 0xFF;
        }
        int n = colors.size();
        return ((int)(totalR / n) << 16) | ((int)(totalG / n) << 8) | (int)(totalB / n);
    }

    /**
     * Calculates the combined strength (coverage) of all layers.
     */
    public static float getCombinedCamoStrength(LivingEntity entity) {
        List<CamoLayer> layers = getTotalCamoLayers(entity);
        if (layers.isEmpty()) return 0.0f;

        float totalDurability = 0;
        for (CamoLayer layer : layers) {
            totalDurability += layer.durability();
        }



        return Math.min(1.0f, totalDurability / 5.0f);
    }

    public static void addLayerToStack(ItemStack stack, CamoLayer layer) {
        addLayerToStack(stack, layer, null);
    }

    public static void addLayerToStack(ItemStack stack, CamoLayer layer, net.minecraft.world.entity.EquipmentSlot slot) {
        if (stack.isEmpty()) return;
        
        float upper = layer.upperDurability();
        float lower = layer.lowerDurability();
        
        if (slot != null) {
            switch (slot) {
                case HEAD:
                case CHEST:
                    lower = 0;
                    break;
                case LEGS:
                case FEET:
                    upper = 0;
                    break;
                default:
                    break;
            }
        }
        
        CamoLayer splitLayer = new CamoLayer(layer.color(), layer.category(), upper, lower, layer.blocksScent(), layer.appliedTick(), layer.seed(), layer.erosion(), layer.humidity(), layer.temperature());

        CompoundTag tag = getOrCreateCustomTag(stack);
        net.minecraft.nbt.ListTag list;
        if (tag.get("soundattract:CamoLayers") instanceof net.minecraft.nbt.ListTag) {
            list = tag.getListOrEmpty("soundattract:CamoLayers");
        } else {
            list = new net.minecraft.nbt.ListTag();
            tag.put("soundattract:CamoLayers", list);
            
            if (tag.contains("soundattract:CamoStrength")) {
                float s = tag.getFloatOr("soundattract:CamoStrength", 0.0F);
                int c = tag.getIntOr("soundattract:CamoColor", 0);
                long sd = tag.getLongOr("soundattract:CamoSeed", 0L);
                list.add(new CamoLayer(c, "legacy", s, s, false, 0, sd, 0, 0, 0).save());
                tag.remove("soundattract:CamoStrength");
                tag.remove("soundattract:CamoColor");
                tag.remove("soundattract:CamoSeed");
            }
        }
        
        list.add(splitLayer.save());
        int max = com.example.soundattract.config.separate.StealthConfig.MAX_CAMO_LAYERS.get();
        while (list.size() > max) {
            list.remove(0);
        }
        setCustomTag(stack, tag);
    }

    public static boolean washStack(ItemStack stack, float factor) {
        if (stack.isEmpty() || !hasCustomTag(stack)) return false;
        CompoundTag tag = getCustomTag(stack);
        boolean changed = false;

        if (tag.get("soundattract:CamoLayers") instanceof net.minecraft.nbt.ListTag) {
            net.minecraft.nbt.ListTag list = tag.getListOrEmpty("soundattract:CamoLayers");
            net.minecraft.nbt.ListTag next = new net.minecraft.nbt.ListTag();
            for (int i = 0; i < list.size(); i++) {
                CamoLayer l = CamoLayer.load(list.getCompoundOrEmpty(i));
                float u = l.upperDurability() * factor;
                float v = l.lowerDurability() * factor;
                if (u > 0.01f || v > 0.01f) {
                    next.add(new CamoLayer(l.color(), l.category(), u, v, l.blocksScent(), l.appliedTick(), l.seed(), l.erosion(), l.humidity(), l.temperature()).save());
                }
                changed = true;
            }
            if (next.isEmpty()) tag.remove("soundattract:CamoLayers");
            else tag.put("soundattract:CamoLayers", next);
        } else if (tag.contains("soundattract:CamoStrength")) {
            float s = tag.getFloatOr("soundattract:CamoStrength", 0.0F) * factor;
            if (s < 0.01f) {
                tag.remove("soundattract:CamoStrength");
                tag.remove("soundattract:CamoColor");
                tag.remove("soundattract:CamoSeed");
            } else {
                tag.putFloat("soundattract:CamoStrength", s);
            }
            changed = true;
        }
        if (changed) {
            setCustomTag(stack, tag);
        }
        return changed;
    }

    /**
     * Final color for perception based on BaseColor and CamoSmudgeColor.
     */
    public static int getFinalPerceptionColor(LivingEntity entity) {
        int baseColor = getEffectiveBaseColor(entity);
        Optional<Integer> camoColorOpt = getBlendedCamoColor(entity);
        
        if (camoColorOpt.isEmpty()) return baseColor;

        float strength = getCombinedCamoStrength(entity);
        int camoColor = camoColorOpt.get();

        int r1 = (baseColor >> 16) & 0xFF;
        int g1 = (baseColor >> 8) & 0xFF;
        int b1 = baseColor & 0xFF;

        int r2 = (camoColor >> 16) & 0xFF;
        int g2 = (camoColor >> 8) & 0xFF;
        int b2 = camoColor & 0xFF;

        int r = (int) (r1 + (r2 - r1) * strength);
        int g = (int) (g1 + (g2 - g1) * strength);
        int b = (int) (b1 + (b2 - b1) * strength);

        return (r << 16) | (g << 8) | b;
    }
    public record BlendedCamoData(Optional<Integer> color, float strength, long seed, float erosion, float humidity, float temp) {}

    public static BlendedCamoData getBlendedDataFromTag(net.minecraft.nbt.ListTag list) {
        if (list == null || list.isEmpty()) return new BlendedCamoData(Optional.empty(), 0, 0, 0, 0, 0);

        float totalR = 0, totalG = 0, totalB = 0, totalWeight = 0;
        long lastSeed = 0;
        float lastErosion = 0, lastHumidity = 0, lastTemp = 0;

        for (int i = 0; i < list.size(); i++) {
            CamoLayer layer = CamoLayer.load(list.getCompoundOrEmpty(i));
            float weight = layer.durability();
            totalR += ((layer.color() >> 16) & 0xFF) * weight;
            totalG += ((layer.color() >> 8) & 0xFF) * weight;
            totalB += (layer.color() & 0xFF) * weight;
            totalWeight += weight;
            lastSeed = layer.seed();
            lastErosion = layer.erosion();
            lastHumidity = layer.humidity();
            lastTemp = layer.temperature();
        }

        if (totalWeight <= 0) return new BlendedCamoData(Optional.empty(), 0, 0, 0, 0, 0);

        int r = (int) (totalR / totalWeight);
        int g = (int) (totalG / totalWeight);
        int b = (int) (totalB / totalWeight);
        int color = (r << 16) | (g << 8) | b;
        
        return new BlendedCamoData(Optional.of(color), Math.min(1.0f, totalWeight), lastSeed, lastErosion, lastHumidity, lastTemp);
    }

    /**
     * Helper to check if a specific armor slot's position is submerged in water for an entity.
     */
    public static boolean isSlotSubmerged(LivingEntity entity, EquipmentSlot slot) {
        if (entity == null || entity.level() == null) return false;
        
        net.minecraft.core.BlockPos pos = entity.blockPosition();
        net.minecraft.world.level.Level level = entity.level();
        
        if (!level.isLoaded(pos)) return false;

        return switch (slot) {
            case FEET -> level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER);
            case LEGS -> level.getFluidState(pos.offset(0, 1, 0)).is(net.minecraft.tags.FluidTags.WATER);
            case CHEST -> level.getFluidState(pos.offset(0, 1, 0)).is(net.minecraft.tags.FluidTags.WATER) 
                       || level.getFluidState(pos.offset(0, 2, 0)).is(net.minecraft.tags.FluidTags.WATER);
            case HEAD -> level.getFluidState(pos.offset(0, 2, 0)).is(net.minecraft.tags.FluidTags.WATER);
            default -> false;
        };
    }

    public record ClimateData(float erosion, float humidity, float temperature) {
        public static final ClimateData DEFAULT = new ClimateData(0, 0, 0);
    }

    /**
     * Samples climate data (Erosion, Humidity, Temperature) at the given entity's position.
     * Guarded against NPEs and server/client discrepancies.
     */
    public static ClimateData sampleClimate(LivingEntity entity) {
        if (entity == null || entity.level() == null || entity.level().isClientSide()) return ClimateData.DEFAULT;
        
        try {
            if (entity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                net.minecraft.world.level.biome.Climate.Sampler sampler = serverLevel.getChunkSource().randomState().sampler();
                net.minecraft.core.BlockPos pos = entity.blockPosition();
                
                net.minecraft.world.level.biome.Climate.TargetPoint target = sampler.sample(pos.getX(), pos.getY(), pos.getZ());
                
                return new ClimateData(
                    target.erosion() / 10000.0f, 
                    target.humidity() / 10000.0f, 
                    target.temperature() / 10000.0f
                );
            }
        } catch (Exception e) {
        }
        return ClimateData.DEFAULT;
    }
}

