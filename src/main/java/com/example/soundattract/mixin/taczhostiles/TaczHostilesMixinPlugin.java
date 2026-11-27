package com.example.soundattract.mixin.taczhostiles;

import com.example.soundattract.integration.taczhostiles.TaczHostilesDebug;
import com.example.soundattract.mixin.TaczMixinBootstrapStatus;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class TaczHostilesMixinPlugin implements IMixinConfigPlugin {
    private static final String SHOOT_BEHAVIOR = "com.corrinedev.tachostiles.common.entity.behavior.TaczShootAttack";

    @Override
    public void onLoad(String mixinPackage) {
        TaczMixinBootstrapStatus.markHostilesPluginLoaded();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return SHOOT_BEHAVIOR.equals(targetClassName) && isHostilesDiscovered();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return isHostilesDiscovered() ? List.of("TaczShootAttackMixin") : List.of();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        TaczHostilesDebug.logMixinApplied(mixinClassName);
    }

    private static boolean isHostilesDiscovered() {
        LoadingModList loadingMods = LoadingModList.get();
        return loadingMods != null && loadingMods.getModFileById("tacz_hostiles") != null;
    }
}
