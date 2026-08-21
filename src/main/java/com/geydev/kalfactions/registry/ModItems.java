package com.geydev.kalfactions.registry;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.chest.AccessTool;
import com.geydev.kalfactions.faction.InfluenceType;
import com.geydev.kalfactions.item.AdminAnalyzerItem;
import com.geydev.kalfactions.item.BankerSpawnEggItem;
import com.geydev.kalfactions.item.BlackZoneAntidoteItem;
import com.geydev.kalfactions.item.DimensionKeyItem;
import com.geydev.kalfactions.item.FactionMeterItem;
import com.geydev.kalfactions.item.LegacyTokenItem;
import com.geydev.kalfactions.item.NetherReturnItem;
import com.geydev.kalfactions.item.PlotWandItem;
import com.geydev.kalfactions.item.SellerCatalogItem;
import com.geydev.kalfactions.item.MapScoutSpawnEggItem;
import com.geydev.kalfactions.item.SellerSpawnEggItem;
import com.geydev.kalfactions.item.TraderRemoverItem;
import com.geydev.kalfactions.item.TraderSpawnEggItem;
import com.geydev.kalfactions.item.TraderPointToolItem;
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

    public static final DeferredItem<BlockItem> OUTPOST_CORE =
            ITEMS.registerSimpleBlockItem(ModBlocks.OUTPOST_CORE);

    public static final DeferredItem<BlockItem> RESOURCE_CLUSTER_SCIENCE =
            ITEMS.registerSimpleBlockItem(ModBlocks.RESOURCE_CLUSTER_SCIENCE);

    public static final DeferredItem<BlockItem> RESOURCE_CLUSTER_ECONOMIC =
            ITEMS.registerSimpleBlockItem(ModBlocks.RESOURCE_CLUSTER_ECONOMIC);

    public static final DeferredItem<BlockItem> RESOURCE_CLUSTER_MILITARY =
            ITEMS.registerSimpleBlockItem(ModBlocks.RESOURCE_CLUSTER_MILITARY);

    public static final DeferredItem<BlockItem> RESOURCE_CLUSTER_DIAMOND =
            ITEMS.registerSimpleBlockItem(ModBlocks.RESOURCE_CLUSTER_DIAMOND);

    public static final DeferredItem<BlockItem> GUIDE_BOARD =
            ITEMS.registerSimpleBlockItem(ModBlocks.GUIDE_BOARD);


    public static final DeferredItem<BlockItem> SANCTUARY_CORE =
            ITEMS.registerSimpleBlockItem(ModBlocks.SANCTUARY_CORE);


    public static final DeferredItem<BlockItem> XAERO_MAP_ARCHIVE =
            ITEMS.registerSimpleBlockItem(ModBlocks.XAERO_MAP_ARCHIVE);

    public static final DeferredItem<BlockItem> STATUE_SCIENCE =
            ITEMS.registerSimpleBlockItem(ModBlocks.STATUE_SCIENCE);

    public static final DeferredItem<BlockItem> WAR_GOD_STATUE =
            ITEMS.registerSimpleBlockItem(ModBlocks.WAR_GOD_STATUE);

    public static final DeferredItem<BlockItem> ECONOMY_GOD_STATUE =
            ITEMS.registerSimpleBlockItem(ModBlocks.ECONOMY_GOD_STATUE);

    public static final DeferredItem<BlockItem> RESEARCH_GOD_STONE_8BLOCKS =
            ITEMS.registerSimpleBlockItem(ModBlocks.RESEARCH_GOD_STONE_8BLOCKS);

    public static final DeferredItem<BlockItem> WAR_GOD_STONE_8BLOCKS =
            ITEMS.registerSimpleBlockItem(ModBlocks.WAR_GOD_STONE_8BLOCKS);

    public static final DeferredItem<BlockItem> ECONOMY_GOD_STONE_8BLOCKS =
            ITEMS.registerSimpleBlockItem(ModBlocks.ECONOMY_GOD_STONE_8BLOCKS);

    public static final DeferredItem<AccessTool> ACCESS_TOOL = ITEMS.register(
            "access_tool",
            () -> new AccessTool(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<Item> OUTPOST_CHARTER = ITEMS.register(
            "outpost_charter",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<BlockItem> QUARRY_CORE =
            ITEMS.registerSimpleBlockItem(ModBlocks.QUARRY_CORE);

    public static final DeferredItem<Item> QUARRY_ACTIVATOR = ITEMS.register(
            "quarry_activator",
            () -> new Item(new Item.Properties().stacksTo(1))
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

    public static final DeferredItem<BankerSpawnEggItem> BANKER_SPAWN_EGG = ITEMS.register(
            "banker_spawn_egg",
            () -> new BankerSpawnEggItem(new Item.Properties())
    );

    public static final DeferredItem<MapScoutSpawnEggItem> MAP_SCOUT_SPAWN_EGG = ITEMS.register(
            "map_scout_spawn_egg",
            () -> new MapScoutSpawnEggItem(new Item.Properties())
    );

    public static final DeferredItem<TraderRemoverItem> TRADER_REMOVER = ITEMS.register(
            "trader_remover",
            () -> new TraderRemoverItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<TraderPointToolItem> TRADER_POINT_TOOL = ITEMS.register(
            "trader_point_tool",
            () -> new TraderPointToolItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<SellerCatalogItem> SELLER_CATALOG = ITEMS.register(
            "seller_catalog",
            () -> new SellerCatalogItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<DimensionKeyItem> DIMENSION_KEY = ITEMS.register(
            "dimension_key",
            () -> new DimensionKeyItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<NetherReturnItem> NETHER_RETURN = ITEMS.register(
            "nether_return",
            () -> new NetherReturnItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<PlotWandItem> PLOT_WAND = ITEMS.register(
            "plot_wand",
            () -> new PlotWandItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<AdminAnalyzerItem> ADMIN_ANALYZER = ITEMS.register(
            "admin_analyzer",
            () -> new AdminAnalyzerItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<FactionMeterItem> FACTION_METER = ITEMS.register(
            "faction_meter",
            () -> new FactionMeterItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<Item> WAR_TROPHY = ITEMS.register(
            "war_trophy",
            () -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE))
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

    public static final DeferredItem<Item> MINIBOSS_TOKEN_GHOST = ITEMS.register(
            "miniboss_token_ghost",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> MINIBOSS_TOKEN_SCULK = ITEMS.register(
            "miniboss_token_sculk",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> MINIBOSS_TOKEN_NETHER = ITEMS.register(
            "miniboss_token_nether",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> MINIBOSS_TOKEN_LUSH_CAVES = ITEMS.register(
            "miniboss_token_lush_caves",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> MINIBOSS_TOKEN_END = ITEMS.register(
            "miniboss_token_end",
            () -> new LegacyTokenItem(new Item.Properties(), "item.kingdoms.miniboss_token")
    );

    public static final DeferredItem<Item> MINIBOSS_TOKEN = ITEMS.register(
            "miniboss_token",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> BOSS_TROPHY_LESSER = ITEMS.register(
            "boss_trophy_lesser",
            () -> new com.geydev.kalfactions.item.BossTrophyItem(
                    new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE),
                    "item.kingdoms.boss_trophy_lesser.tooltip"
            )
    );

    public static final DeferredItem<Item> BOSS_TROPHY_GREATER = ITEMS.register(
            "boss_trophy_greater",
            () -> new com.geydev.kalfactions.item.BossTrophyItem(
                    new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE),
                    "item.kingdoms.boss_trophy_greater.tooltip"
            )
    );

    public static final DeferredItem<Item> BOSS_TROPHY_LEGENDARY = ITEMS.register(
            "boss_trophy_legendary",
            () -> new com.geydev.kalfactions.item.BossTrophyItem(
                    new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC),
                    "item.kingdoms.boss_trophy_legendary.tooltip"
            )
    );

    public static final DeferredItem<BlockItem> DUNGEON_CORE =
            ITEMS.registerSimpleBlockItem(ModBlocks.DUNGEON_CORE);

    public static final DeferredItem<BlockItem> DUNGEON_CHEST =
            ITEMS.registerSimpleBlockItem(ModBlocks.DUNGEON_CHEST);

    public static final DeferredItem<BlockItem> GHOST_KEY_FORGE =
            ITEMS.registerSimpleBlockItem(ModBlocks.GHOST_KEY_FORGE);

    public static final DeferredItem<BlockItem> INFERNAL_KEY_FORGE =
            ITEMS.registerSimpleBlockItem(ModBlocks.INFERNAL_KEY_FORGE);

    public static final DeferredItem<BlackZoneAntidoteItem> BLACKZONE_ANTIDOTE = ITEMS.register(
            "blackzone_antidote",
            () -> new BlackZoneAntidoteItem(
                    new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE)
            )
    );

    public static final DeferredItem<BlockItem> MUSIC_BLOCK =
            ITEMS.registerSimpleBlockItem(ModBlocks.MUSIC_BLOCK);

    public static final DeferredItem<BlockItem> RESEARCH_BENCH =
            ITEMS.registerSimpleBlockItem(ModBlocks.RESEARCH_BENCH);

    public static final DeferredItem<BlockItem> SCULK_KEY_FORGE =
            ITEMS.registerSimpleBlockItem(ModBlocks.SCULK_KEY_FORGE);

    public static final DeferredItem<BlockItem> MOSSY_KEY_FORGE =
            ITEMS.registerSimpleBlockItem(ModBlocks.MOSSY_KEY_FORGE);

    public static final DeferredItem<Item> GHOST_KEY_BOW_FRAGMENT = ITEMS.register(
            "ghost_key_bow_fragment",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> GHOST_KEY_SHAFT_FRAGMENT = ITEMS.register(
            "ghost_key_shaft_fragment",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> GHOST_KEY_BIT_FRAGMENT = ITEMS.register(
            "ghost_key_bit_fragment",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> GHOST_KEY = ITEMS.register(
            "ghost_key",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> INFERNAL_KEY_BOW_FRAGMENT = ITEMS.register(
            "infernal_key_bow_fragment",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> INFERNAL_KEY_SHAFT_FRAGMENT = ITEMS.register(
            "infernal_key_shaft_fragment",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> INFERNAL_KEY_BIT_FRAGMENT = ITEMS.register(
            "infernal_key_bit_fragment",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> INFERNAL_KEY = ITEMS.register(
            "infernal_key",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> SCULK_KEY_BOW_FRAGMENT = ITEMS.register(
            "sculk_key_bow_fragment",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> SCULK_KEY_SHAFT_FRAGMENT = ITEMS.register(
            "sculk_key_shaft_fragment",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> SCULK_KEY_BIT_FRAGMENT = ITEMS.register(
            "sculk_key_bit_fragment",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> SCULK_KEY = ITEMS.register(
            "sculk_key",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> MOSSY_KEY_BOW_FRAGMENT = ITEMS.register(
            "mossy_key_bow_fragment",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> MOSSY_KEY_SHAFT_FRAGMENT = ITEMS.register(
            "mossy_key_shaft_fragment",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> MOSSY_KEY_BIT_FRAGMENT = ITEMS.register(
            "mossy_key_bit_fragment",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> MOSSY_KEY = ITEMS.register(
            "mossy_key",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<BlockItem> DUNGEON_KEY_PEDESTAL =
            ITEMS.registerSimpleBlockItem(ModBlocks.DUNGEON_KEY_PEDESTAL);

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
