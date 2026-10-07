package com.example.soundattract.mixin;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class GeckoMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
        org.apache.logging.log4j.LogManager.getLogger().info("[SoundAttract] GeckoMixinPlugin loaded! Package: " + mixinPackage);
        try {
            boolean geckolibPresent = LoadingModList.get().getModFileById("geckolib") != null;
            org.apache.logging.log4j.LogManager.getLogger().info("[SoundAttract] Checking for 'geckolib': " + geckolibPresent);

            if (!geckolibPresent) {
                 org.apache.logging.log4j.LogManager.getLogger().info("[SoundAttract] Available Mods: " + LoadingModList.get().getMods().stream().map(net.minecraftforge.forgespi.language.IModInfo::getModId).collect(java.util.stream.Collectors.joining(", ")));
            }
        } catch (Exception e) {
             org.apache.logging.log4j.LogManager.getLogger().error("[SoundAttract] Error checking mod list in mixin plugin", e);
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean geckoLoaded = LoadingModList.get().getModFileById("geckolib") != null;
        if (geckoLoaded) {
            org.apache.logging.log4j.LogManager.getLogger().info("[SoundAttract] GeckoLib detected! Enabling mixin: " + mixinClassName);
        }
        return geckoLoaded;
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