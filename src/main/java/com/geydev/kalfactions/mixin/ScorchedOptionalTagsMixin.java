package com.geydev.kalfactions.mixin;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TagLoader.class)
public abstract class ScorchedOptionalTagsMixin {
    @Shadow
    @Final
    private String directory;

    @Inject(method = "load", at = @At("RETURN"))
    private void kingdoms$makeMissingKnifeOptional(
            ResourceManager resources,
            CallbackInfoReturnable<Map<ResourceLocation, List<TagLoader.EntryWithSource>>> callback
    ) {
        if (!directory.equals("tags/item")) {
            return;
        }
        ResourceLocation knife = ResourceLocation.fromNamespaceAndPath("scguns", "anthralite_knife");
        for (List<TagLoader.EntryWithSource> entries : callback.getReturnValue().values()) {
            entries.replaceAll(entry -> entry.source().equals("mod/scguns")
                    && !entry.entry().isTag() && entry.entry().getId().equals(knife)
                    ? new TagLoader.EntryWithSource(TagEntry.optionalElement(knife), entry.source(), entry.remove())
                    : entry);
        }
    }
}
