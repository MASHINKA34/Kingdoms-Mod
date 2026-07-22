package com.geydev.kalfactions.sculk;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SculkTomeGameTests {
    @GameTest(template = "empty")
    public static void regularGunBenchTakeConsumesOneTome(GameTestHelper helper) {
        BenchHarness harness = gunBench(helper);
        ItemStack tome = tome(1);
        helper.assertTrue(gunBenchBlueprint(helper, "echoes_2_from_gun_bench").test(tome), "Gun Bench recipe must display the consumable tome");
        harness.menu.getSlot(10).set(tome);
        harness.menu.getSlot(11).set(sculkGun());
        helper.assertTrue(harness.menu.getSlot(10).mayPlace(tome), "Gun Bench blueprint slot must accept a sculk tome");
        helper.assertTrue(harness.menu.getSlot(11).mayPickup(harness.player), "Gun result must be available with a tome");
        harness.menu.getSlot(11).onTake(harness.player, harness.menu.getSlot(11).getItem());
        helper.assertTrue(harness.menu.getSlot(10).getItem().isEmpty(), "One tome must be consumed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shiftTakeConsumesOneTome(GameTestHelper helper) {
        BenchHarness harness = gunBench(helper);
        helper.assertTrue(mechanicalRecipeHasTome(helper, "echoes_2"), "Mechanical recipe must contain the consumable tome");
        harness.menu.getSlot(10).set(tome(1));
        harness.menu.getSlot(11).set(sculkGun());
        ItemStack moved = harness.menu.quickMoveStack(harness.player, 11);
        helper.assertFalse(moved.isEmpty(), "Shift take must transfer the result");
        helper.assertTrue(harness.menu.getSlot(10).getItem().isEmpty(), "Shift take must consume exactly one tome");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gunBenchRejectsMissingTome(GameTestHelper helper) {
        SimpleContainer bench = benchWithTomes(0);
        ItemStack gun = sculkGun();
        helper.assertFalse(SculkTomePolicy.mayTakeGunBenchResult(bench, gun), "Result must not be available without a tome");
        helper.assertFalse(SculkTomePolicy.consumeAfterSuccessfulTake(bench, gun), "Missing tome must not create a successful transaction");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void twoGunBenchTakesConsumeTwoTomes(GameTestHelper helper) {
        SimpleContainer bench = benchWithTomes(2);
        ItemStack gun = sculkGun();
        helper.assertTrue(SculkTomePolicy.consumeAfterSuccessfulTake(bench, gun), "First result must consume a tome");
        helper.assertTrue(SculkTomePolicy.consumeAfterSuccessfulTake(bench, gun), "Second result must consume another tome");
        helper.assertTrue(bench.getItem(SculkTomePolicy.GUN_BENCH_BLUEPRINT_SLOT).isEmpty(), "Two results must consume both tomes");
        helper.assertFalse(SculkTomePolicy.consumeAfterSuccessfulTake(bench, gun), "A third result must be rejected");
        helper.succeed();
    }

    private static SimpleContainer benchWithTomes(int count) {
        SimpleContainer bench = new SimpleContainer(12);
        if (count > 0) {
            bench.setItem(SculkTomePolicy.GUN_BENCH_BLUEPRINT_SLOT, tome(count));
        }
        return bench;
    }

    private static ItemStack tome(int count) {
        return new ItemStack(BuiltInRegistries.ITEM.get(SculkTomePolicy.TOME_ID), count);
    }

    private static ItemStack sculkGun() {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("scguns", "echoes_2")));
    }

    private static BenchHarness gunBench(GameTestHelper helper) {
        try {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            Class<?> type = Class.forName("top.ribs.scguns.client.screen.GunBenchMenu");
            AbstractContainerMenu menu = (AbstractContainerMenu) type
                    .getConstructor(int.class, Inventory.class, ContainerLevelAccess.class)
                    .newInstance(1, player.getInventory(), ContainerLevelAccess.NULL);
            return new BenchHarness(player, menu);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Ingredient gunBenchBlueprint(GameTestHelper helper, String path) {
        try {
            RecipeHolder<?> holder = helper.getLevel().getRecipeManager()
                    .byKey(ResourceLocation.fromNamespaceAndPath("scguns", "guns/" + path))
                    .orElseThrow();
            return (Ingredient) holder.value().getClass().getMethod("getBlueprint").invoke(holder.value());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean mechanicalRecipeHasTome(GameTestHelper helper, String path) {
        RecipeHolder<?> holder = helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath("scguns", "create/mechanical_crafting/" + path))
                .orElseThrow();
        ItemStack tome = tome(1);
        return holder.value().getIngredients().stream().anyMatch(ingredient -> ingredient.test(tome));
    }

    private record BenchHarness(Player player, AbstractContainerMenu menu) {
    }

    private SculkTomeGameTests() {
    }
}
