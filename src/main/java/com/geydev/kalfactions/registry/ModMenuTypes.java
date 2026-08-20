package com.geydev.kalfactions.registry;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.menu.DrillMenu;
import com.geydev.kalfactions.menu.DungeonLootMenu;
import com.geydev.kalfactions.block.KeyForgeType;
import com.geydev.kalfactions.menu.KeyForgeMenu;
import com.geydev.kalfactions.menu.QuarryMenu;
import com.geydev.kalfactions.menu.ResearchBenchMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class ModMenuTypes {
    public static final ResourceLocation DRILL_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "drill");
    public static final DeferredHolder<MenuType<?>, MenuType<DrillMenu>> DRILL =
            DeferredHolder.create(Registries.MENU, DRILL_ID);
    public static final ResourceLocation QUARRY_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "quarry");
    public static final DeferredHolder<MenuType<?>, MenuType<QuarryMenu>> QUARRY =
            DeferredHolder.create(Registries.MENU, QUARRY_ID);

    public static final ResourceLocation DUNGEON_LOOT_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "dungeon_loot");
    public static final DeferredHolder<MenuType<?>, MenuType<DungeonLootMenu>> DUNGEON_LOOT =
            DeferredHolder.create(Registries.MENU, DUNGEON_LOOT_ID);

    public static final ResourceLocation RESEARCH_BENCH_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "research_bench");
    public static final DeferredHolder<MenuType<?>, MenuType<ResearchBenchMenu>> RESEARCH_BENCH =
            DeferredHolder.create(Registries.MENU, RESEARCH_BENCH_ID);
    public static final ResourceLocation GHOST_KEY_FORGE_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "ghost_key_forge");
    public static final DeferredHolder<MenuType<?>, MenuType<KeyForgeMenu>> GHOST_KEY_FORGE =
            DeferredHolder.create(Registries.MENU, GHOST_KEY_FORGE_ID);
    public static final ResourceLocation SCULK_KEY_FORGE_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "sculk_key_forge");
    public static final DeferredHolder<MenuType<?>, MenuType<KeyForgeMenu>> SCULK_KEY_FORGE =
            DeferredHolder.create(Registries.MENU, SCULK_KEY_FORGE_ID);
    public static final ResourceLocation INFERNAL_KEY_FORGE_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "infernal_key_forge");
    public static final DeferredHolder<MenuType<?>, MenuType<KeyForgeMenu>> INFERNAL_KEY_FORGE =
            DeferredHolder.create(Registries.MENU, INFERNAL_KEY_FORGE_ID);

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.MENU, DUNGEON_LOOT_ID, () -> IMenuTypeExtension.create(
                (containerId, playerInventory, extraData) ->
                        new DungeonLootMenu(containerId, playerInventory, extraData.readBlockPos())
        ));
        event.register(Registries.MENU, DRILL_ID, () -> IMenuTypeExtension.create(
                (containerId, playerInventory, extraData) -> new DrillMenu(containerId, playerInventory)
        ));
        event.register(Registries.MENU, QUARRY_ID, () -> IMenuTypeExtension.create(
                (containerId, playerInventory, extraData) ->
                        new QuarryMenu(containerId, playerInventory, extraData.readBlockPos())
        ));
        event.register(Registries.MENU, RESEARCH_BENCH_ID, () -> IMenuTypeExtension.create(
                (containerId, playerInventory, extraData) -> new ResearchBenchMenu(containerId, playerInventory)
        ));
        event.register(Registries.MENU, GHOST_KEY_FORGE_ID, () -> IMenuTypeExtension.create(
                (containerId, playerInventory, extraData) ->
                        new KeyForgeMenu(
                                containerId,
                                playerInventory,
                                extraData.readBlockPos(),
                                KeyForgeType.GHOST
                        )
        ));
        event.register(Registries.MENU, SCULK_KEY_FORGE_ID, () -> IMenuTypeExtension.create(
                (containerId, playerInventory, extraData) ->
                        new KeyForgeMenu(
                                containerId,
                                playerInventory,
                                extraData.readBlockPos(),
                                KeyForgeType.SCULK
                        )
        ));
        event.register(Registries.MENU, INFERNAL_KEY_FORGE_ID, () -> IMenuTypeExtension.create(
                (containerId, playerInventory, extraData) ->
                        new KeyForgeMenu(
                                containerId,
                                playerInventory,
                                extraData.readBlockPos(),
                                KeyForgeType.INFERNAL
                        )
        ));
    }

    private ModMenuTypes() {
    }
}
