package com.example.soundattract.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityInvoker {
    @Invoker("actuallyHurt")
    void soundattract$actuallyHurt(DamageSource source, float amount);

    @Invoker("getHurtSound")
    SoundEvent soundattract$getHurtSound(DamageSource source);
}

