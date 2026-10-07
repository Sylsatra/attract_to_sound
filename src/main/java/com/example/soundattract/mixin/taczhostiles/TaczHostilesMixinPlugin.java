package com.example.soundattract.mixin.taczhostiles;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import com.example.soundattract.integration.taczhostiles.TaczHostilesDebug;
import java.util.List;
import java.util.Set;

public final class TaczHostilesMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String mixinPackage) { com.example.soundattract.mixin.TaczMixinBootstrapStatus.markHostilesPluginLoaded(); }
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!targetClassName.equals("com.corrinedev.tachostiles.common.entity.behavior.TaczShootAttack")) return false;
        try {
            return net.minecraftforge.fml.ModList.get() != null && net.minecraftforge.fml.ModList.get().isLoaded("tacz_hostiles");
        } catch (Throwable ignored) {
            return false;
        }
    }
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    public List<String> getMixins() { return null; }
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        TaczHostilesDebug.logMixinApplied(mixinClassName);
    }
}
