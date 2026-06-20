package com.geydev.kalfactions.outpost.trader;

import java.util.Arrays;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum SellOffer {
    COPPER_INGOT("copper_ingot", Items.COPPER_INGOT, 10L, 128),
    GOLD_INGOT("gold_ingot", Items.GOLD_INGOT, 12L, 128),
    WHEAT("wheat", Items.WHEAT, 5L, 128),
    BONE("bone", Items.BONE, 8L, 64),
    BONE_MEAL("bone_meal", Items.BONE_MEAL, 4L, 128),
    STRING("string", Items.STRING, 9L, 64),
    FEATHER("feather", Items.FEATHER, 6L, 128),
    SNOWBALL("snowball", Items.SNOWBALL, 2L, 128),
    EGG("egg", Items.EGG, 9L, 64),
    CLAY_BALL("clay_ball", Items.CLAY_BALL, 4L, 128),
    SLIME_BALL("slime_ball", Items.SLIME_BALL, 5L, 128),
    RAW_IRON("raw_iron", Items.RAW_IRON, 4L, 64),
    GREEN_DYE("green_dye", Items.GREEN_DYE, 3L, 128),
    PHANTOM_MEMBRANE("phantom_membrane", Items.PHANTOM_MEMBRANE, 15L, 64),
    HONEYCOMB("honeycomb", Items.HONEYCOMB, 4L, 64),
    RABBIT_HIDE("rabbit_hide", Items.RABBIT_HIDE, 14L, 64),
    LEATHER("leather", Items.LEATHER, 12L, 64),
    NAUTILUS_SHELL("nautilus_shell", Items.NAUTILUS_SHELL, 25L, 32),
    FIRE_CHARGE("fire_charge", Items.FIRE_CHARGE, 27L, 32),
    BLAZE_ROD("blaze_rod", Items.BLAZE_ROD, 32L, 32),
    ENDER_PEARL("ender_pearl", Items.ENDER_PEARL, 32L, 32),
    ENDER_EYE("ender_eye", Items.ENDER_EYE, 40L, 32),
    CHORUS_FRUIT("chorus_fruit", Items.CHORUS_FRUIT, 13L, 64),
    ECHO_SHARD("echo_shard", Items.ECHO_SHARD, 50L, 32),
    PRISMARINE_SHARD("prismarine_shard", Items.PRISMARINE_SHARD, 16L, 64),
    PRISMARINE_CRYSTALS("prismarine_crystals", Items.PRISMARINE_CRYSTALS, 16L, 64),
    IRON_INGOT("iron_ingot", Items.IRON_INGOT, 11L, 128),
    QUARTZ("quartz", Items.QUARTZ, 7L, 128),
    DIAMOND("diamond", Items.DIAMOND, 25L, 64),
    LAPIS_LAZULI("lapis_lazuli", Items.LAPIS_LAZULI, 4L, 128),
    EMERALD("emerald", Items.EMERALD, 21L, 64),
    BREEZE_ROD("breeze_rod", Items.BREEZE_ROD, 30L, 32),
    RAW_GOLD("raw_gold", Items.RAW_GOLD, 7L, 128),
    RAW_COPPER("raw_copper", Items.RAW_COPPER, 5L, 128),
    CHARCOAL("charcoal", Items.CHARCOAL, 4L, 128),
    COAL("coal", Items.COAL, 5L, 128),
    GLISTERING_MELON_SLICE("glistering_melon_slice", Items.GLISTERING_MELON_SLICE, 20L, 32),
    HEART_OF_THE_SEA("heart_of_the_sea", Items.HEART_OF_THE_SEA, 700L, 4),
    NETHER_WART("nether_wart", Items.NETHER_WART, 8L, 128),
    MAGMA_CREAM("magma_cream", Items.MAGMA_CREAM, 12L, 64),
    RABBIT_FOOT("rabbit_foot", Items.RABBIT_FOOT, 32L, 32),
    SUGAR("sugar", Items.SUGAR, 6L, 128),
    BEETROOT("beetroot", Items.BEETROOT, 4L, 128),
    BLAZE_POWDER("blaze_powder", Items.BLAZE_POWDER, 16L, 32),
    BRICK("brick", Items.BRICK, 4L, 128),
    BOWL("bowl", Items.BOWL, 5L, 64),
    NETHER_BRICK("nether_brick", Items.NETHER_BRICK, 3L, 128),
    BOOK("book", Items.BOOK, 11L, 64),
    GLASS_BOTTLE("glass_bottle", Items.GLASS_BOTTLE, 5L, 64),
    REDSTONE_TORCH("redstone_torch", Items.REDSTONE_TORCH, 8L, 64),
    REDSTONE("redstone", Items.REDSTONE, 8L, 128),
    GUNPOWDER("gunpowder", Items.GUNPOWDER, 15L, 64),
    GLOWSTONE_DUST("glowstone_dust", Items.GLOWSTONE_DUST, 12L, 64),
    AMETHYST_SHARD("amethyst_shard", Items.AMETHYST_SHARD, 11L, 64),
    HONEY_BOTTLE("honey_bottle", Items.HONEY_BOTTLE, 15L, 64),
    COD("cod", Items.COD, 5L, 64),
    BREAD("bread", Items.BREAD, 15L, 64),
    BEEF("beef", Items.BEEF, 8L, 64),
    COOKED_BEEF("cooked_beef", Items.COOKED_BEEF, 16L, 64),
    SALMON("salmon", Items.SALMON, 4L, 64),
    COOKED_SALMON("cooked_salmon", Items.COOKED_SALMON, 8L, 64),
    PORKCHOP("porkchop", Items.PORKCHOP, 8L, 64),
    COOKED_PORKCHOP("cooked_porkchop", Items.COOKED_PORKCHOP, 16L, 64),
    COOKED_MUTTON("cooked_mutton", Items.COOKED_MUTTON, 16L, 64),
    COOKED_RABBIT("cooked_rabbit", Items.COOKED_RABBIT, 16L, 64),
    MUTTON("mutton", Items.MUTTON, 8L, 64),
    DRIED_KELP("dried_kelp", Items.DRIED_KELP, 4L, 128),
    RABBIT("rabbit", Items.RABBIT, 8L, 64),
    CHICKEN("chicken", Items.CHICKEN, 8L, 64),
    PUFFERFISH("pufferfish", Items.PUFFERFISH, 22L, 32),
    TURTLE_SCUTE("turtle_scute", Items.TURTLE_SCUTE, 24L, 64),
    COOKED_CHICKEN("cooked_chicken", Items.COOKED_CHICKEN, 16L, 64),
    BAKED_POTATO("baked_potato", Items.BAKED_POTATO, 14L, 64),
    COOKIE("cookie", Items.COOKIE, 11L, 128),
    ROTTEN_FLESH("rotten_flesh", Items.ROTTEN_FLESH, 4L, 128),
    TROPICAL_FISH("tropical_fish", Items.TROPICAL_FISH, 15L, 64),
    PUMPKIN_PIE("pumpkin_pie", Items.PUMPKIN_PIE, 17L, 64),
    GOLDEN_APPLE("golden_apple", Items.GOLDEN_APPLE, 70L, 16),
    APPLE("apple", Items.APPLE, 12L, 64),
    SWEET_BERRIES("sweet_berries", Items.SWEET_BERRIES, 4L, 128),
    GLOW_BERRIES("glow_berries", Items.GLOW_BERRIES, 8L, 128),
    MELON_SLICE("melon_slice", Items.MELON_SLICE, 4L, 128),
    CARROT("carrot", Items.CARROT, 4L, 128),
    POTATO("potato", Items.POTATO, 4L, 128),
    POISONOUS_POTATO("poisonous_potato", Items.POISONOUS_POTATO, 2L, 128),
    RAIL("rail", Items.RAIL, 8L, 64),
    SOUL_TORCH("soul_torch", Items.SOUL_TORCH, 6L, 64),
    MINECART("minecart", Items.MINECART, 34L, 32),
    AMETHYST_BLOCK("amethyst_block", Items.AMETHYST_BLOCK, 24L, 32),
    WHITE_WOOL("white_wool", Items.WHITE_WOOL, 6L, 128),
    LIGHTNING_ROD("lightning_rod", Items.LIGHTNING_ROD, 8L, 64),
    REPEATER("repeater", Items.REPEATER, 12L, 32),
    COMPARATOR("comparator", Items.COMPARATOR, 16L, 32),
    HOPPER("hopper", Items.HOPPER, 17L, 32),
    PISTON("piston", Items.PISTON, 18L, 32),
    STICKY_PISTON("sticky_piston", Items.STICKY_PISTON, 22L, 32),
    HONEYCOMB_BLOCK("honeycomb_block", Items.HONEYCOMB_BLOCK, 16L, 32),
    POPPY("poppy", Items.POPPY, 4L, 64),
    DANDELION("dandelion", Items.DANDELION, 4L, 64),
    OAK_LOG("oak_log", Items.OAK_LOG, 5L, 128),
    ENCHANTING_TABLE("enchanting_table", Items.ENCHANTING_TABLE, 250L, 4),
    RED_MUSHROOM("red_mushroom", Items.RED_MUSHROOM, 5L, 128),
    BROWN_MUSHROOM("brown_mushroom", Items.BROWN_MUSHROOM, 5L, 128),
    KELP("kelp", Items.KELP, 4L, 128),
    SPONGE("sponge", Items.SPONGE, 15L, 64),
    MELON("melon", Items.MELON, 8L, 64),
    HAY_BLOCK("hay_block", Items.HAY_BLOCK, 12L, 64),
    MAGMA_BLOCK("magma_block", Items.MAGMA_BLOCK, 12L, 64),
    OBSIDIAN("obsidian", Items.OBSIDIAN, 16L, 64),
    TORCH("torch", Items.TORCH, 4L, 128),
    FLETCHING_TABLE("fletching_table", Items.FLETCHING_TABLE, 32L, 16),
    SMITHING_TABLE("smithing_table", Items.SMITHING_TABLE, 32L, 16),
    CAMPFIRE("campfire", Items.CAMPFIRE, 24L, 32),
    CARTOGRAPHY_TABLE("cartography_table", Items.CARTOGRAPHY_TABLE, 32L, 16),
    CRAFTING_TABLE("crafting_table", Items.CRAFTING_TABLE, 16L, 32),
    STONECUTTER("stonecutter", Items.STONECUTTER, 15L, 32),
    GRINDSTONE("grindstone", Items.GRINDSTONE, 12L, 32),
    ANVIL("anvil", Items.ANVIL, 80L, 16),
    OAK_PLANKS("oak_planks", Items.OAK_PLANKS, 2L, 128),
    BREWING_STAND("brewing_stand", Items.BREWING_STAND, 67L, 12),
    OAK_SIGN("oak_sign", Items.OAK_SIGN, 8L, 64),
    BOOKSHELF("bookshelf", Items.BOOKSHELF, 26L, 32),
    SCAFFOLDING("scaffolding", Items.SCAFFOLDING, 2L, 128),
    WHITE_BED("white_bed", Items.WHITE_BED, 27L, 32),
    LEAD("lead", Items.LEAD, 16L, 32),
    MELON_SEEDS("melon_seeds", Items.MELON_SEEDS, 4L, 128),
    COCOA_BEANS("cocoa_beans", Items.COCOA_BEANS, 4L, 128),
    WHEAT_SEEDS("wheat_seeds", Items.WHEAT_SEEDS, 4L, 128),
    PUMPKIN_SEEDS("pumpkin_seeds", Items.PUMPKIN_SEEDS, 4L, 128),
    ARROW("arrow", Items.ARROW, 7L, 128),
    GLOWSTONE("glowstone", Items.GLOWSTONE, 12L, 64),
    CHERRY_SAPLING("cherry_sapling", Items.CHERRY_SAPLING, 4L, 128),
    OAK_SAPLING("oak_sapling", Items.OAK_SAPLING, 4L, 128),
    SPRUCE_SAPLING("spruce_sapling", Items.SPRUCE_SAPLING, 4L, 128),
    MANGROVE_PROPAGULE("mangrove_propagule", Items.MANGROVE_PROPAGULE, 4L, 128),
    BAMBOO("bamboo", Items.BAMBOO, 4L, 128),
    AZALEA("azalea", Items.AZALEA, 4L, 128),
    VINE("vine", Items.VINE, 4L, 128),
    IRON_BARS("iron_bars", Items.IRON_BARS, 8L, 128),
    GLASS("glass", Items.GLASS, 7L, 128),
    TNT("tnt", Items.TNT, 16L, 64);

    private final String id;
    private final Item item;
    private final long price;
    private final int dailyLimit;

    SellOffer(String id, Item item, long price, int dailyLimit) {
        this.id = id;
        this.item = item;
        this.price = price;
        this.dailyLimit = dailyLimit;
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

    public int dailyLimit() {
        return dailyLimit;
    }

    public static Optional<SellOffer> byId(String id) {
        return Arrays.stream(values()).filter(offer -> offer.id.equals(id)).findFirst();
    }

    public static Optional<SellOffer> byItem(Item item) {
        return Arrays.stream(values()).filter(offer -> offer.item == item).findFirst();
    }
}
