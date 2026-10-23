package com.example.soundattract.mixin;

import com.example.soundattract.event.StealthDetectionEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HurtByTargetGoal.class)
public abstract class HurtByTargetGoalMixin extends TargetGoal {
    public HurtByTargetGoalMixin(Mob pMob, boolean pMustSee) { super(pMob, pMustSee); }

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void soundattract_onCanUse(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity attacker = this.mob.getLastHurtByMob();
        if (attacker instanceof Player player) {
            if (!StealthDetectionEvents.canMobDetectPlayer(this.mob, player)) {
                cir.setReturnValue(false);
            }
        }
    }
}
