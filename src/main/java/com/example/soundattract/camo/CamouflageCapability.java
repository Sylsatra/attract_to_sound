package com.example.soundattract.camo;

import com.example.soundattract.config.separate.StealthConfig;
import com.example.soundattract.util.CamoUtil;
import com.example.soundattract.event.StealthDetectionEvents;
import com.example.soundattract.network.CamoSyncMessage;
import com.example.soundattract.network.SoundAttractNetwork;
import org.ladysnake.cca.api.v3.component.Component;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class CamouflageCapability implements Component {

    private final List<CamoLayer> layers = new CopyOnWriteArrayList<>();

    public void addLayer(LivingEntity entity, CamoLayer layer) {
        if (!StealthConfig.ENABLE_LAYERED_CAMOUFLAGE.get()) {
            layers.clear();
        }
        layers.add(layer);
        int max = StealthConfig.MAX_CAMO_LAYERS.get();
        while (layers.size() > max) {
            layers.remove(0);
        }
        sync(entity);
    }

    public static Optional<CamouflageCapability> getCapability(LivingEntity entity) {
        if (entity == null) return Optional.empty();
        return com.example.soundattract.component.ModComponents.CAMOUFLAGE.maybeGet(entity);
    }

    public static void removeCapability(LivingEntity entity) {
        getCapability(entity).ifPresent(cap -> cap.setLayers(java.util.Collections.emptyList()));
    }

    public static void removeCamouflage(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof LivingEntity living) {
            removeCapability(living);
        }
    }

    public void sync(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        CamoSyncMessage msg = new CamoSyncMessage(entity.getId(), new ArrayList<>(layers));
        SoundAttractNetwork.sendToTrackingAndSelf(entity, msg);
    }

    /**
     * Returns an unmodifiable view of the layers.
     * Use {@link #setLayers(List)} for bulk mutation.
     */
    public List<CamoLayer> getLayers() {
        return Collections.unmodifiableList(layers);
    }

    /**
     * Replaces all layers atomically. Used by network sync.
     */
    public void setLayers(List<CamoLayer> newLayers) {
        layers.clear();
        layers.addAll(newLayers);
    }

    public Optional<Integer> getBlendedColor() {
        if (layers.isEmpty()) return Optional.empty();

        float totalR = 0, totalG = 0, totalB = 0, totalWeight = 0;
        for (CamoLayer layer : layers) {
            float weight = (layer.upperDurability() + layer.lowerDurability()) / 2.0f;
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

    public float getVisualCamoStrength() {
        if (layers.isEmpty()) return 0.0f;
        
        float totalDurability = 0;
        for (CamoLayer layer : layers) {
            totalDurability += (layer.upperDurability() + layer.lowerDurability()) / 2.0f;
        }
        return Math.min(1.0f, totalDurability);
    }

    public double getScentBlockFactor() {
        if (layers.isEmpty()) return 0.0;
        
        List<CamoLayer> scentLayers = layers.stream().filter(CamoLayer::blocksScent).toList();
        if (scentLayers.isEmpty()) return 0.0;
        
        double sum = 0;
        for (CamoLayer layer : scentLayers) {
            sum += (layer.upperDurability() + layer.lowerDurability()) / 2.0f;
        }

        double strength = StealthConfig.CAMO_APPLIED_SCENT_BLOCK_STRENGTH.get();
        return Math.min(1.0, sum * strength);
    }

    public void applyMaterial(LivingEntity entity, CamoMaterialRegistry.CamoMaterialEntry entry, boolean isCreative) {
        long currentTick = entity.level().getGameTime();
        long seed = entity.getRandom().nextLong();
        
        CamoUtil.ClimateData climate = CamoUtil.sampleClimate(entity);
        CamoLayer layer = entry.getLayer(currentTick, seed, climate);
        

        boolean headEmpty = entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        boolean chestEmpty = entity.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        boolean legsEmpty = entity.getItemBySlot(EquipmentSlot.LEGS).isEmpty();
        boolean feetEmpty = entity.getItemBySlot(EquipmentSlot.FEET).isEmpty();
        
        boolean hitUpper = headEmpty || chestEmpty;
        boolean hitLower = legsEmpty || feetEmpty;
        
        if (hitUpper || hitLower) {

            CamoLayer skinLayer = new CamoLayer(layer.color(), layer.category(), hitUpper ? layer.upperDurability() : 0f, hitLower ? layer.lowerDurability() : 0f, layer.blocksScent(), layer.appliedTick(), layer.seed(), climate.erosion(), climate.humidity(), climate.temperature());
            addLayer(entity, skinLayer);
        }


        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                ItemStack armorStack = entity.getItemBySlot(slot);
                if (!armorStack.isEmpty()) {
                    CamoUtil.addLayerToStack(armorStack, layer, slot);

                    entity.setItemSlot(slot, armorStack);
                }
            }
        }

        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), 
            SoundEvents.MUD_PLACE, SoundSource.PLAYERS, 1.0f, 1.0f);
        
        sync(entity);
    }

    public void onSnowballed(LivingEntity entity) {
        float gain = 0.1f;
        long seed = entity.level().random.nextLong();
        long time = entity.level().getGameTime();
        
        boolean headEmpty = entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        boolean chestEmpty = entity.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        boolean legsEmpty = entity.getItemBySlot(EquipmentSlot.LEGS).isEmpty();
        boolean feetEmpty = entity.getItemBySlot(EquipmentSlot.FEET).isEmpty();
        
        boolean hitUpper = headEmpty || chestEmpty;
        boolean hitLower = legsEmpty || feetEmpty;
        
        if (hitUpper || hitLower) {
            CamoUtil.ClimateData climate = CamoUtil.sampleClimate(entity);
            addLayer(entity, new CamoLayer(0xCACACA, "snow", hitUpper ? gain : 0f, hitLower ? gain : 0f, true, time, seed, climate.erosion(), climate.humidity(), climate.temperature()));
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                ItemStack stack = entity.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    CamoUtil.ClimateData climate = CamoUtil.sampleClimate(entity);
                    CamoUtil.addLayerToStack(stack, new CamoLayer(0xCACACA, "snow", gain, gain, true, time, seed, climate.erosion(), climate.humidity(), climate.temperature()), slot);
                }
            }
        }
        sync(entity);
    }

    public void onWaterSplashed(LivingEntity entity, boolean totalSplash) {
        boolean headEmpty = entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        boolean chestEmpty = entity.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        boolean legsEmpty = entity.getItemBySlot(EquipmentSlot.LEGS).isEmpty();
        boolean feetEmpty = entity.getItemBySlot(EquipmentSlot.FEET).isEmpty();

        boolean washUpper = totalSplash || ((headEmpty || chestEmpty) && (CamoUtil.isSlotSubmerged(entity, EquipmentSlot.HEAD) || CamoUtil.isSlotSubmerged(entity, EquipmentSlot.CHEST)));
        boolean washLower = totalSplash || ((legsEmpty || feetEmpty) && (CamoUtil.isSlotSubmerged(entity, EquipmentSlot.LEGS) || CamoUtil.isSlotSubmerged(entity, EquipmentSlot.FEET)));


        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                if (totalSplash || CamoUtil.isSlotSubmerged(entity, slot)) {
                    CamoUtil.washStack(entity.getItemBySlot(slot), 0.5f);
                }
            }
        }


        if (washUpper || washLower) {
            List<CamoLayer> next = new ArrayList<>();
            boolean skinDirty = false;
            for (CamoLayer l : layers) {
                float u = washUpper ? l.upperDurability() * 0.5f : l.upperDurability();
                float v = washLower ? l.lowerDurability() * 0.5f : l.lowerDurability();
                if (u > 0.01f || v > 0.01f) {
                    next.add(new CamoLayer(l.color(), l.category(), u, v, l.blocksScent(), l.appliedTick(), l.seed(), l.erosion(), l.humidity(), l.temperature()));
                }
                if (u != l.upperDurability() || v != l.lowerDurability()) skinDirty = true;
            }
            if (skinDirty) {
                layers.clear();
                layers.addAll(next);
            }
        }
        
        sync(entity);
    }

    public void tickDegradation(LivingEntity entity, Level level, StealthDetectionEvents.StealthPerfTier tier) {
        int interval = getCamoTickInterval(tier);
        
        double tps = StealthDetectionEvents.getLastEstimatedTps();
        if (tps > 0 && tps < 18.0) {
            interval *= 2;
        }

        if (level.getGameTime() % interval != 0) return;

        float decayScale = 1.0f / Math.max(1, StealthConfig.MAX_CAMO_LAYERS.get());
        float baseDecay = ((float) interval / StealthConfig.BASE_CAMO_DURATION_TICKS.get()) * decayScale;
        
        net.minecraft.core.BlockPos pos = entity.blockPosition();
        boolean inRain = level.isRainingAt(pos) && level.canSeeSky(pos);
        

        boolean lowerInWater = level.getFluidState(pos.offset(0, 0, 0)).is(net.minecraft.tags.FluidTags.WATER) 
                            || level.getFluidState(pos.offset(0, 1, 0)).is(net.minecraft.tags.FluidTags.WATER);
        boolean upperInWater = level.getFluidState(pos.offset(0, 2, 0)).is(net.minecraft.tags.FluidTags.WATER);

        boolean sprinting = entity.isSprinting();
        float temp = level.getBiome(pos).value().getBaseTemperature();
        float waterRate = StealthConfig.WATER_DEGRADATION_RATE.get().floatValue() * decayScale;

        boolean dirty = false;


        if (!layers.isEmpty()) {
            List<CamoLayer> nextLayers = new ArrayList<>();
            for (CamoLayer layer : layers) {
                float upperDecay = baseDecay;
                float lowerDecay = baseDecay;
                CamoMaterialRegistry.CategoryRules rules = CamoMaterialRegistry.getCategoryRules(layer.category());

                if (upperInWater) upperDecay += waterRate;
                if (lowerInWater) lowerDecay += waterRate;

                if (inRain && rules != null && rules.waterSensitive()) {
                    upperDecay += waterRate;
                    lowerDecay += waterRate;
                }

                if (rules != null && (temp < rules.minTemp() || temp > rules.maxTemp())) {
                    float mismatch = StealthConfig.BIOME_MISMATCH_DEGRADATION_RATE.get().floatValue() * decayScale;
                    upperDecay += mismatch;
                    lowerDecay += mismatch;
                }

                if (sprinting) {
                    float sprint = StealthConfig.SPRINT_DEGRADATION_RATE.get().floatValue() * decayScale;
                    upperDecay += sprint;
                    lowerDecay += sprint;
                }

                float newUpper = Math.max(0, layer.upperDurability() - upperDecay);
                float newLower = Math.max(0, layer.lowerDurability() - lowerDecay);
                
                if (newUpper > 0 || newLower > 0) {
                    nextLayers.add(new CamoLayer(layer.color(), layer.category(), newUpper, newLower, layer.blocksScent(), layer.appliedTick(), layer.seed(), layer.erosion(), layer.humidity(), layer.temperature()));
                }
                
                if (newUpper != layer.upperDurability() || newLower != layer.lowerDurability()) {
                    dirty = true;
                }
            }
            
            if (dirty) {
                layers.clear();
                layers.addAll(nextLayers);
            }
        }
        

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                ItemStack armorStack = entity.getItemBySlot(slot);
                if (!armorStack.isEmpty() && armorStack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
                    net.minecraft.nbt.CompoundTag tag = armorStack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
                    boolean armorChanged = false;

                    boolean slotSubmerged = CamoUtil.isSlotSubmerged(entity, slot);

                    if (tag.contains("soundattract:CamoLayers", net.minecraft.nbt.Tag.TAG_LIST)) {
                        net.minecraft.nbt.ListTag list = tag.getList("soundattract:CamoLayers", net.minecraft.nbt.Tag.TAG_COMPOUND);
                        net.minecraft.nbt.ListTag nextList = new net.minecraft.nbt.ListTag();
                        for (int i = 0; i < list.size(); i++) {
                            CamoLayer layer = CamoLayer.load(list.getCompound(i));
                            float decay = baseDecay;
                            CamoMaterialRegistry.CategoryRules rules = CamoMaterialRegistry.getCategoryRules(layer.category());

                            if (slotSubmerged) {
                                decay += waterRate;
                            } else if (inRain && rules != null && rules.waterSensitive()) {
                                decay += waterRate;
                            }

                            if (sprinting) {
                                decay += StealthConfig.SPRINT_DEGRADATION_RATE.get().floatValue() * decayScale;
                            }


                            float currentDur = (layer.upperDurability() + layer.lowerDurability()) / 2.0f;
                            float newDur = Math.max(0, currentDur - decay);
                            
                            if (newDur > 0) {
                                nextList.add(new CamoLayer(layer.color(), layer.category(), newDur, newDur, layer.blocksScent(), layer.appliedTick(), layer.seed(), layer.erosion(), layer.humidity(), layer.temperature()).save());
                            }
                            
                            if (newDur != currentDur) {
                                armorChanged = true;
                            }
                        }
                        
                        if (armorChanged) {
                            if (nextList.isEmpty()) {
                                tag.remove("soundattract:CamoLayers");
                            } else {
                                tag.put("soundattract:CamoLayers", nextList);
                            }
                        }
                    } 
                    else if (tag.contains("soundattract:CamoStrength")) {
                        float current = tag.getFloat("soundattract:CamoStrength");
                        float decay = slotSubmerged ? (waterRate + baseDecay) : baseDecay;
                        float next = Math.max(0, current - decay);
                        if (next != current) {
                            if (next <= 0) {
                                tag.remove("soundattract:CamoStrength");
                                tag.remove("soundattract:CamoColor");
                                tag.remove("soundattract:CamoSeed");
                            } else {
                                tag.putFloat("soundattract:CamoStrength", next);
                            }
                            armorChanged = true;
                        }
                    }

                    if (armorChanged) {
                        armorStack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                                net.minecraft.world.item.component.CustomData.of(tag));
                        dirty = true;
                        entity.setItemSlot(slot, armorStack);
                    }
                }
            }
        }

        if (dirty) {
            sync(entity);
        }
    }

    public void onDamage(LivingEntity entity, float amount) {
        float decayScale = 1.0f / Math.max(1, StealthConfig.MAX_CAMO_LAYERS.get());
        float damageDecay = StealthConfig.DAMAGE_DEGRADATION_RATE.get().floatValue() * (amount / 10.0f) * decayScale;
        List<CamoLayer> nextLayers = new ArrayList<>();
        for (CamoLayer layer : layers) {
            float newUpper = Math.max(0, layer.upperDurability() - damageDecay);
            float newLower = Math.max(0, layer.lowerDurability() - damageDecay);
            if (newUpper > 0 || newLower > 0) {
                nextLayers.add(new CamoLayer(layer.color(), layer.category(), newUpper, newLower, layer.blocksScent(), layer.appliedTick(), layer.seed(), layer.erosion(), layer.humidity(), layer.temperature()));
            }
        }
        layers.clear();
        layers.addAll(nextLayers);


        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                ItemStack armorStack = entity.getItemBySlot(slot);
                if (!armorStack.isEmpty() && armorStack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
                    net.minecraft.nbt.CompoundTag tag = armorStack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
                    boolean changed = false;


                    if (tag.contains("soundattract:CamoLayers", net.minecraft.nbt.Tag.TAG_LIST)) {
                        net.minecraft.nbt.ListTag list = tag.getList("soundattract:CamoLayers", net.minecraft.nbt.Tag.TAG_COMPOUND);
                        net.minecraft.nbt.ListTag nextList = new net.minecraft.nbt.ListTag();
                        for (int i = 0; i < list.size(); i++) {
                            CamoLayer layer = CamoLayer.load(list.getCompound(i));
                            float newDur = layer.durability() - damageDecay;
                            if (newDur > 0) {
                                nextList.add(layer.withDurability(newDur, newDur).save());
                            }
                        }
                        if (!nextList.equals(list)) {
                            changed = true;
                        }
                        
                        if (nextList.isEmpty()) {
                            tag.remove("soundattract:CamoLayers");
                        } else {
                            tag.put("soundattract:CamoLayers", nextList);
                        }
                    } 

                    else if (tag.contains("soundattract:CamoStrength")) {
                        float current = tag.getFloat("soundattract:CamoStrength");
                        float next = current - damageDecay;
                        if (next <= 0) {
                            tag.remove("soundattract:CamoStrength");
                            tag.remove("soundattract:CamoColor");
                            tag.remove("soundattract:CamoSeed");
                        } else {
                            tag.putFloat("soundattract:CamoStrength", next);
                        }
                        changed = true;
                    }

                    if (changed) {
                        armorStack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                                net.minecraft.world.item.component.CustomData.of(tag));
                        entity.setItemSlot(slot, armorStack);
                    }
                }
            }
        }

        sync(entity);
    }

    private int getCamoTickInterval(StealthDetectionEvents.StealthPerfTier tier) {
        return switch (tier) {
            case FULL -> 20;
            case SHARE_NEARBY -> 40;
            case SKIP_EXPENSIVE -> 80;
            default -> 160;
        };
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (CamoLayer layer : layers) {
            list.add(layer.save());
        }
        tag.put("Layers", list);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        layers.clear();
        ListTag list = tag.getList("Layers", 10);
        for (int i = 0; i < list.size(); i++) {
            layers.add(CamoLayer.load(list.getCompound(i)));
        }
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        deserializeNBT(tag);
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.put("Layers", serializeNBT().get("Layers"));
    }


    public long getDisplaySeed() {
        if (layers.isEmpty()) return 0;
        return layers.get(layers.size() - 1).seed();
    }

    public float getDisplayErosion() {
        if (layers.isEmpty()) return 0;
        return layers.get(layers.size() - 1).erosion();
    }

    public float getDisplayHumidity() {
        if (layers.isEmpty()) return 0;
        return layers.get(layers.size() - 1).humidity();
    }

    public float getDisplayTemperature() {
        if (layers.isEmpty()) return 0;
        return layers.get(layers.size() - 1).temperature();
    }
}
