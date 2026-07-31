package com.geydev.kalfactions.scorched;

import com.geydev.kalfactions.KalFactions;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class DisabledRaidFlares {
    public static final ResourceLocation RAID_FLARE_ENTITY_ID = scguns("raid_flare");
    public static final List<ResourceLocation> ITEM_IDS = List.of(
            scguns("antique_flare"),
            scguns("frontier_flare"),
            scguns("copper_flare"),
            scguns("iron_flare"),
            scguns("wrecker_flare"),
            scguns("gold_flare"),
            scguns("diamond_steel_flare"),
            scguns("treated_brass_flare"),
            scguns("ocean_flare"),
            scguns("sculk_flare")
    );
    private static final Set<ResourceLocation> ITEM_ID_SET = Set.copyOf(ITEM_IDS);

    public static boolean isDisabled(ItemStack stack) {
        return !stack.isEmpty() && isDisabled(stack.getItem());
    }

    public static boolean isDisabled(Item item) {
        return ITEM_ID_SET.contains(BuiltInRegistries.ITEM.getKey(item));
    }

    public static boolean isDisabledRecipe(RecipeHolder<?> holder, HolderLookup.Provider registries) {
        return isDisabled(holder.value().getResultItem(registries));
    }

    public static List<RecipeHolder<?>> removeRecipes(
            Iterable<RecipeHolder<?>> recipes,
            HolderLookup.Provider registries
    ) {
        return java.util.stream.StreamSupport.stream(recipes.spliterator(), false)
                .filter(holder -> !isDisabledRecipe(holder, registries))
                .toList();
    }

    public static void removeFromCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        for (ResourceLocation id : ITEM_IDS) {
            BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> event.remove(
                    new ItemStack(item),
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
            ));
        }
    }

    @SubscribeEvent
    public static void onRaidFlareJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (RAID_FLARE_ENTITY_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (isDisabled(event.getItemStack())) {
            event.getToolTip().add(Component.translatable("kingdoms.scguns.raid_flare.disabled")
                    .withStyle(ChatFormatting.RED));
        }
    }

    private static ResourceLocation scguns(String path) {
        return ResourceLocation.fromNamespaceAndPath("scguns", path);
    }

    private DisabledRaidFlares() {
    }
}
