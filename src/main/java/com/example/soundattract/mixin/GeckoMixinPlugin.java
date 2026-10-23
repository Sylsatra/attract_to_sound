package com.example.soundattract.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin plugin for GeckoLib integration.
 * Only applies mixins when GeckoLib is loaded.
 */
public class GeckoMixinPlugin implements IMixinConfigPlugin {

    private static boolean geckolibLoaded = false;

    @Override
    public void onLoad(String mixinPackage) {
        geckolibLoaded = FabricLoader.getInstance().isModLoaded("geckolib");
        org.apache.logging.log4j.LogManager.getLogger().info("[SoundAttract] GeckoMixinPlugin loaded! Package: " + mixinPackage);
        org.apache.logging.log4j.LogManager.getLogger().info("[SoundAttract] Checking for 'geckolib': " + geckolibLoaded);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (geckolibLoaded) {
            org.apache.logging.log4j.LogManager.getLogger().info("[SoundAttract] GeckoLib detected! Enabling mixin: " + mixinClassName);
        }
        return geckolibLoaded;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
