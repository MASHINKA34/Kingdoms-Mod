package com.geydev.kalfactions.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class KingdomsMixinPlugin implements IMixinConfigPlugin {
    public static final List<String> OPTIONAL_MOD_PACKAGES = List.of(
            "com.simibubi.create.",
            "net.createmod.",
            "top.ribs.scguns.",
            "net.mcreator.protectionpixel.",
            "xaero."
    );

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !isOptionalTarget(targetClassName) || classExists(targetClassName);
    }

    public static boolean isOptionalTarget(String targetClassName) {
        for (String prefix : OPTIONAL_MOD_PACKAGES) {
            if (targetClassName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
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

    private static boolean classExists(String className) {
        String classFile = className.replace('.', '/') + ".class";
        return KingdomsMixinPlugin.class.getClassLoader().getResource(classFile) != null;
    }
}
