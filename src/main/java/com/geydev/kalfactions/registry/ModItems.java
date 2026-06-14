package com.geydev.kalfactions.registry;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.chest.AccessTool;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(KalFactions.MOD_ID);

    public static final DeferredItem<BlockItem> FACTION_TABLE =
            ITEMS.registerSimpleBlockItem(ModBlocks.FACTION_TABLE);

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

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private ModItems() {
    }
}
