package com.example.soundattract.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import com.example.soundattract.integration.tacznpcs.TaczNpcDebug;

import java.util.List;
import java.util.Set;

public final class TaczNpcsMixinPlugin implements IMixinConfigPlugin {
    private static final String ABSTRACT_SCAV = "com.corrinedev.tacznpcs.common.entity.AbstractScavEntity";
    private static final String SHOOT_BEHAVIOUR = "com.corrinedev.tacznpcs.common.entity.behavior.TaczShootAttack";

    @Override
    public void onLoad(String mixinPackage) {
        TaczMixinBootstrapStatus.markNpcPluginLoaded();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Mixin prepares configs before Forge finishes loading mod containers, so
        // ModList.isLoaded("tacz_npc") can be false even when its classes are present.
        // Both mixins are @Pseudo, which keeps this optional when TACZ NPCs is absent.
        return (ABSTRACT_SCAV.equals(targetClassName)
                    && "com.example.soundattract.mixin.TaczNpcsAbstractScavEntityMixin".equals(mixinClassName))
                || (SHOOT_BEHAVIOUR.equals(targetClassName)
                    && "com.example.soundattract.mixin.tacznpcs.TaczShootAttackMixin".equals(mixinClassName));
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        TaczNpcDebug.markMixinApplied(mixinClassName);
    }
}
