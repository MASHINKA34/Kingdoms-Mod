package com.geydev.kalfactions.registry;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.chest.AccessTool;
import com.geydev.kalfactions.faction.InfluenceType;
import com.geydev.kalfactions.item.PlotWandItem;
import com.geydev.kalfactions.item.SellerSpawnEggItem;
import com.geydev.kalfactions.item.TraderSpawnEggItem;
import java.util.Optional;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(KalFactions.MOD_ID);

    public static final DeferredItem<BlockItem> FACTION_TABLE =
            ITEMS.registerSimpleBlockItem(ModBlocks.FACTION_TABLE);

    public static final DeferredItem<BlockItem> WAR_ARCHIVE =
            ITEMS.registerSimpleBlockItem(ModBlocks.WAR_ARCHIVE);

    public static final DeferredItem<BlockItem> GUIDE_BOARD =
            ITEMS.registerSimpleBlockItem(ModBlocks.GUIDE_BOARD);

    public static final DeferredItem<BlockItem> SANCTUARY_CORE =
            ITEMS.registerSimpleBlockItem(ModBlocks.SANCTUARY_CORE);

    public static final DeferredItem<BlockItem> WORLD_MAP =
            ITEMS.registerSimpleBlockItem(ModBlocks.WORLD_MAP);

    public static final DeferredItem<AccessTool> ACCESS_TOOL = ITEMS.register(
            "access_tool",
            () -> new AccessTool(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<Item> OUTPOST_CHARTER = ITEMS.register(
            "outpost_charter",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<BlockItem> DRILL =
            ITEMS.registerSimpleBlockItem(ModBlocks.DRILL);

    public static final DeferredItem<TraderSpawnEggItem> TRADER_SPAWN_EGG = ITEMS.register(
            "trader_spawn_egg",
            () -> new TraderSpawnEggItem(new Item.Properties())
    );

    public static final DeferredItem<SellerSpawnEggItem> SELLER_SPAWN_EGG = ITEMS.register(
            "seller_spawn_egg",
            () -> new SellerSpawnEggItem(new Item.Properties())
    );

    public static final DeferredItem<Item> TRADER_REMOVER = ITEMS.register(
            "trader_remover",
            () -> new Item(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<PlotWandItem> PLOT_WAND = ITEMS.register(
            "plot_wand",
            () -> new PlotWandItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<Item> CRYSTAL_SCIENCE = ITEMS.register(
            "crystal_science",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> CRYSTAL_ECONOMIC = ITEMS.register(
            "crystal_economic",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> CRYSTAL_MILITARY = ITEMS.register(
            "crystal_military",
            () -> new Item(new Item.Properties())
    );

    public static Item crystalFor(InfluenceType type) {
        return switch (type) {
            case SCIENCE -> CRYSTAL_SCIENCE.get();
            case ECONOMIC -> CRYSTAL_ECONOMIC.get();
            case MILITARY -> CRYSTAL_MILITARY.get();
        };
    }

    public static Optional<InfluenceType> crystalType(Item item) {
        if (item == CRYSTAL_SCIENCE.get()) {
            return Optional.of(InfluenceType.SCIENCE);
        }
        if (item == CRYSTAL_ECONOMIC.get()) {
            return Optional.of(InfluenceType.ECONOMIC);
        }
        if (item == CRYSTAL_MILITARY.get()) {
            return Optional.of(InfluenceType.MILITARY);
        }
        return Optional.empty();
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private ModItems() {
    }
}
