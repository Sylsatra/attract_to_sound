package com.example.soundattract.mixin;

import com.example.soundattract.Soundattract;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Set;

public class CustomNpcsMixinPlugin implements IMixinConfigPlugin {

    private static final String CUSTOMNPCS_MOD_ID = "customnpcs";
    private static final String[] CUSTOMNPCS_CORE_MIXINS = {
        "CustomNpcsEntityNPCInterfaceMixin",
        "CustomNpcsUpdateTasksMixin"
    };

    private boolean customNpcsPresent = false;

    @Override
    public void onLoad(String mixinPackage) {
        customNpcsPresent = FabricLoader.getInstance().isModLoaded(CUSTOMNPCS_MOD_ID);
        if (customNpcsPresent) {
            Soundattract.LOGGER.info("[CustomNPCs] Mod detected, enabling core CustomNPCs mixins");
        } else {
            Soundattract.LOGGER.info("[CustomNPCs] Mod not detected, disabling all CustomNPCs mixins");
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        for (String coreMixin : CUSTOMNPCS_CORE_MIXINS) {
            if (mixinClassName.contains(coreMixin)) {
                return customNpcsPresent;
            }
        }
        return true;
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
