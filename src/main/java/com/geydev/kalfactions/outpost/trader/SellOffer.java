package com.geydev.kalfactions.outpost.trader;

import java.util.Arrays;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum SellOffer {
    COPPER_INGOT("copper_ingot", Items.COPPER_INGOT, 1L),
    GOLD_INGOT("gold_ingot", Items.GOLD_INGOT, 1L),
    WHEAT("wheat", Items.WHEAT, 1L),
    BONE("bone", Items.BONE, 1L),
    BONE_MEAL("bone_meal", Items.BONE_MEAL, 1L),
    STRING("string", Items.STRING, 1L),
    FEATHER("feather", Items.FEATHER, 1L),
    SNOWBALL("snowball", Items.SNOWBALL, 1L),
    EGG("egg", Items.EGG, 1L),
    CLAY_BALL("clay_ball", Items.CLAY_BALL, 1L),
    SLIME_BALL("slime_ball", Items.SLIME_BALL, 1L),
    RAW_IRON("raw_iron", Items.RAW_IRON, 1L),
    GREEN_DYE("green_dye", Items.GREEN_DYE, 1L),
    PHANTOM_MEMBRANE("phantom_membrane", Items.PHANTOM_MEMBRANE, 1L),
    HONEYCOMB("honeycomb", Items.HONEYCOMB, 1L),
    RABBIT_HIDE("rabbit_hide", Items.RABBIT_HIDE, 1L),
    LEATHER("leather", Items.LEATHER, 1L),
    NAUTILUS_SHELL("nautilus_shell", Items.NAUTILUS_SHELL, 1L),
    FIRE_CHARGE("fire_charge", Items.FIRE_CHARGE, 1L),
    BLAZE_ROD("blaze_rod", Items.BLAZE_ROD, 1L),
    ENDER_PEARL("ender_pearl", Items.ENDER_PEARL, 1L),
    ENDER_EYE("ender_eye", Items.ENDER_EYE, 1L),
    CHORUS_FRUIT("chorus_fruit", Items.CHORUS_FRUIT, 1L),
    ECHO_SHARD("echo_shard", Items.ECHO_SHARD, 1L),
    PRISMARINE_SHARD("prismarine_shard", Items.PRISMARINE_SHARD, 1L),
    PRISMARINE_CRYSTALS("prismarine_crystals", Items.PRISMARINE_CRYSTALS, 1L),
    IRON_INGOT("iron_ingot", Items.IRON_INGOT, 1L),
    QUARTZ("quartz", Items.QUARTZ, 1L),
    DIAMOND("diamond", Items.DIAMOND, 1L),
    LAPIS_LAZULI("lapis_lazuli", Items.LAPIS_LAZULI, 1L),
    EMERALD("emerald", Items.EMERALD, 1L),
    BREEZE_ROD("breeze_rod", Items.BREEZE_ROD, 1L),
    RAW_GOLD("raw_gold", Items.RAW_GOLD, 1L),
    RAW_COPPER("raw_copper", Items.RAW_COPPER, 1L),
    CHARCOAL("charcoal", Items.CHARCOAL, 1L),
    COAL("coal", Items.COAL, 1L),
    GLISTERING_MELON_SLICE("glistering_melon_slice", Items.GLISTERING_MELON_SLICE, 1L),
    HEART_OF_THE_SEA("heart_of_the_sea", Items.HEART_OF_THE_SEA, 1L),
    NETHER_WART("nether_wart", Items.NETHER_WART, 1L),
    MAGMA_CREAM("magma_cream", Items.MAGMA_CREAM, 1L),
    RABBIT_FOOT("rabbit_foot", Items.RABBIT_FOOT, 1L),
    SUGAR("sugar", Items.SUGAR, 1L),
    BEETROOT("beetroot", Items.BEETROOT, 1L),
    BLAZE_POWDER("blaze_powder", Items.BLAZE_POWDER, 1L),
    BRICK("brick", Items.BRICK, 1L),
    BOWL("bowl", Items.BOWL, 1L),
    NETHER_BRICK("nether_brick", Items.NETHER_BRICK, 1L),
    BOOK("book", Items.BOOK, 1L),
    GLASS_BOTTLE("glass_bottle", Items.GLASS_BOTTLE, 1L),
    REDSTONE_TORCH("redstone_torch", Items.REDSTONE_TORCH, 1L),
    REDSTONE("redstone", Items.REDSTONE, 1L),
    GUNPOWDER("gunpowder", Items.GUNPOWDER, 1L),
    GLOWSTONE_DUST("glowstone_dust", Items.GLOWSTONE_DUST, 1L),
    AMETHYST_SHARD("amethyst_shard", Items.AMETHYST_SHARD, 1L),
    HONEY_BOTTLE("honey_bottle", Items.HONEY_BOTTLE, 1L),
    COD("cod", Items.COD, 1L),
    BREAD("bread", Items.BREAD, 1L),
    BEEF("beef", Items.BEEF, 1L),
    COOKED_BEEF("cooked_beef", Items.COOKED_BEEF, 1L),
    SALMON("salmon", Items.SALMON, 1L),
    COOKED_SALMON("cooked_salmon", Items.COOKED_SALMON, 1L),
    PORKCHOP("porkchop", Items.PORKCHOP, 1L),
    COOKED_PORKCHOP("cooked_porkchop", Items.COOKED_PORKCHOP, 1L),
    COOKED_MUTTON("cooked_mutton", Items.COOKED_MUTTON, 1L),
    COOKED_RABBIT("cooked_rabbit", Items.COOKED_RABBIT, 1L),
    MUTTON("mutton", Items.MUTTON, 1L),
    DRIED_KELP("dried_kelp", Items.DRIED_KELP, 1L),
    RABBIT("rabbit", Items.RABBIT, 1L),
    CHICKEN("chicken", Items.CHICKEN, 1L),
    PUFFERFISH("pufferfish", Items.PUFFERFISH, 1L),
    TURTLE_SCUTE("turtle_scute", Items.TURTLE_SCUTE, 1L),
    COOKED_CHICKEN("cooked_chicken", Items.COOKED_CHICKEN, 1L),
    BAKED_POTATO("baked_potato", Items.BAKED_POTATO, 1L),
    COOKIE("cookie", Items.COOKIE, 1L),
    ROTTEN_FLESH("rotten_flesh", Items.ROTTEN_FLESH, 1L),
    TROPICAL_FISH("tropical_fish", Items.TROPICAL_FISH, 1L),
    PUMPKIN_PIE("pumpkin_pie", Items.PUMPKIN_PIE, 1L),
    GOLDEN_APPLE("golden_apple", Items.GOLDEN_APPLE, 1L),
    APPLE("apple", Items.APPLE, 1L),
    SWEET_BERRIES("sweet_berries", Items.SWEET_BERRIES, 1L),
    GLOW_BERRIES("glow_berries", Items.GLOW_BERRIES, 1L),
    MELON_SLICE("melon_slice", Items.MELON_SLICE, 1L),
    CARROT("carrot", Items.CARROT, 1L),
    POTATO("potato", Items.POTATO, 1L),
    POISONOUS_POTATO("poisonous_potato", Items.POISONOUS_POTATO, 1L),
    RAIL("rail", Items.RAIL, 1L),
    SOUL_TORCH("soul_torch", Items.SOUL_TORCH, 1L),
    MINECART("minecart", Items.MINECART, 1L),
    AMETHYST_BLOCK("amethyst_block", Items.AMETHYST_BLOCK, 1L),
    WHITE_WOOL("white_wool", Items.WHITE_WOOL, 1L),
    LIGHTNING_ROD("lightning_rod", Items.LIGHTNING_ROD, 1L),
    REPEATER("repeater", Items.REPEATER, 1L),
    COMPARATOR("comparator", Items.COMPARATOR, 1L),
    HOPPER("hopper", Items.HOPPER, 1L),
    PISTON("piston", Items.PISTON, 1L),
    STICKY_PISTON("sticky_piston", Items.STICKY_PISTON, 1L),
    HONEYCOMB_BLOCK("honeycomb_block", Items.HONEYCOMB_BLOCK, 1L),
    POPPY("poppy", Items.POPPY, 1L),
    DANDELION("dandelion", Items.DANDELION, 1L),
    OAK_LOG("oak_log", Items.OAK_LOG, 1L),
    ENCHANTING_TABLE("enchanting_table", Items.ENCHANTING_TABLE, 1L),
    RED_MUSHROOM("red_mushroom", Items.RED_MUSHROOM, 1L),
    BROWN_MUSHROOM("brown_mushroom", Items.BROWN_MUSHROOM, 1L),
    KELP("kelp", Items.KELP, 1L),
    SPONGE("sponge", Items.SPONGE, 1L),
    MELON("melon", Items.MELON, 1L),
    HAY_BLOCK("hay_block", Items.HAY_BLOCK, 1L),
    MAGMA_BLOCK("magma_block", Items.MAGMA_BLOCK, 1L),
    OBSIDIAN("obsidian", Items.OBSIDIAN, 1L),
    TORCH("torch", Items.TORCH, 1L),
    FLETCHING_TABLE("fletching_table", Items.FLETCHING_TABLE, 1L),
    SMITHING_TABLE("smithing_table", Items.SMITHING_TABLE, 1L),
    CAMPFIRE("campfire", Items.CAMPFIRE, 1L),
    CARTOGRAPHY_TABLE("cartography_table", Items.CARTOGRAPHY_TABLE, 1L),
    CRAFTING_TABLE("crafting_table", Items.CRAFTING_TABLE, 1L),
    STONECUTTER("stonecutter", Items.STONECUTTER, 1L),
    GRINDSTONE("grindstone", Items.GRINDSTONE, 1L),
    ANVIL("anvil", Items.ANVIL, 1L),
    OAK_PLANKS("oak_planks", Items.OAK_PLANKS, 1L),
    BREWING_STAND("brewing_stand", Items.BREWING_STAND, 1L),
    OAK_SIGN("oak_sign", Items.OAK_SIGN, 1L),
    BOOKSHELF("bookshelf", Items.BOOKSHELF, 1L),
    SCAFFOLDING("scaffolding", Items.SCAFFOLDING, 1L),
    WHITE_BED("white_bed", Items.WHITE_BED, 1L),
    LEAD("lead", Items.LEAD, 1L),
    MELON_SEEDS("melon_seeds", Items.MELON_SEEDS, 1L),
    COCOA_BEANS("cocoa_beans", Items.COCOA_BEANS, 1L),
    WHEAT_SEEDS("wheat_seeds", Items.WHEAT_SEEDS, 1L),
    PUMPKIN_SEEDS("pumpkin_seeds", Items.PUMPKIN_SEEDS, 1L),
    ARROW("arrow", Items.ARROW, 1L),
    GLOWSTONE("glowstone", Items.GLOWSTONE, 1L),
    CHERRY_SAPLING("cherry_sapling", Items.CHERRY_SAPLING, 1L),
    OAK_SAPLING("oak_sapling", Items.OAK_SAPLING, 1L),
    SPRUCE_SAPLING("spruce_sapling", Items.SPRUCE_SAPLING, 1L),
    MANGROVE_PROPAGULE("mangrove_propagule", Items.MANGROVE_PROPAGULE, 1L),
    BAMBOO("bamboo", Items.BAMBOO, 1L),
    AZALEA("azalea", Items.AZALEA, 1L),
    VINE("vine", Items.VINE, 1L),
    IRON_BARS("iron_bars", Items.IRON_BARS, 1L),
    GLASS("glass", Items.GLASS, 1L),
    TNT("tnt", Items.TNT, 1L);

    private final String id;
    private final Item item;
    private final long price;

    SellOffer(String id, Item item, long price) {
        this.id = id;
        this.item = item;
        this.price = price;
    }

    public String id() {
        return id;
    }

    public Item item() {
        return item;
    }

    public long price() {
        return price;
    }

    public static Optional<SellOffer> byId(String id) {
        return Arrays.stream(values()).filter(offer -> offer.id.equals(id)).findFirst();
    }

    public static Optional<SellOffer> byItem(Item item) {
        return Arrays.stream(values()).filter(offer -> offer.item == item).findFirst();
    }
}
