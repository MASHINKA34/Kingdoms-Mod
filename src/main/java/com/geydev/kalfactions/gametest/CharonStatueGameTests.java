package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.CharonStatueBlock;
import com.geydev.kalfactions.charon.CharonManager;
import com.geydev.kalfactions.charon.CharonPayloads;
import com.geydev.kalfactions.charon.CharonStatueShop;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.FactionRole;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.registry.ModCreativeTabs;
import com.geydev.kalfactions.registry.ModItems;
import dev.ithundxr.createnumismatics.Numismatics;
import dev.ithundxr.createnumismatics.content.backend.BankAccount;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CharonStatueGameTests {
    @GameTest(template = "empty", batch = "charon_statue")
    public static void everyDirectionAndEveryBrokenPartDropsExactlyOneStatue(GameTestHelper helper) {
        var level = helper.getLevel();
        var block = ModBlocks.CHARON_STATUE.get();
        BlockPos anchor = helper.absolutePos(new BlockPos(4, 1, 4));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int broken = 0; broken < 12; broken++) {
                var root = block.partState(facing, 0, 0, 0);
                level.setBlock(anchor, root, Block.UPDATE_ALL);
                block.setPlacedBy(level, anchor, root, null, ItemStack.EMPTY);
                for (int x = 0; x < 2; x++) {
                    for (int y = 0; y < 3; y++) {
                        for (int z = 0; z < 2; z++) {
                            var pos = CharonStatueBlock.partPos(anchor, facing, x, y, z);
                            var state = level.getBlockState(pos);
                            helper.assertTrue(state.equals(block.partState(facing, x, y, z)), "Missing or incorrect statue part");
                            helper.assertTrue(CharonStatueBlock.anchorPos(pos, state).equals(anchor), "Part resolved the wrong anchor");
                            var shape = state.getCollisionShape(level, pos, CollisionContext.empty());
                            helper.assertFalse(shape.isEmpty(), "Part has no collision");
                            var box = shape.bounds();
                            helper.assertTrue(box.minX >= 0 && box.minY >= 0 && box.minZ >= 0
                                    && box.maxX <= 1 && box.maxY <= 1 && box.maxZ <= 1, "Collision escapes its cell");
                            helper.assertTrue(shape == state.getShape(level, pos), "Selection and collision differ");
                        }
                    }
                }
                BlockPos hit = CharonStatueBlock.partPos(anchor, facing, broken / 6, broken / 2 % 3, broken % 2);
                level.destroyBlock(hit, true);
                for (int x = 0; x < 2; x++) {
                    for (int y = 0; y < 3; y++) {
                        for (int z = 0; z < 2; z++) {
                            helper.assertFalse(level.getBlockState(CharonStatueBlock.partPos(anchor, facing, x, y, z)).is(block),
                                    "A part survived destruction");
                        }
                    }
                }
                var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(anchor).inflate(4));
                int count = drops.stream().filter(e -> e.getItem().is(ModItems.CHARON_STATUE.get()))
                        .mapToInt(e -> e.getItem().getCount()).sum();
                helper.assertValueEqual(count, 1, "statues dropped by breaking " + broken + " facing " + facing);
                drops.forEach(ItemEntity::discard);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "charon_statue")
    public static void placementRequiresAllTwelveCellsAndFourSupports(GameTestHelper helper) {
        var level = helper.getLevel();
        var block = ModBlocks.CHARON_STATUE.get();
        BlockPos anchor = helper.absolutePos(new BlockPos(3, 1, 3));
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setYRot(0);
        player.setPos(Vec3.atCenterOf(anchor.offset(4, 0, 4)));
        var stack = new ItemStack(ModItems.CHARON_STATUE.get(), 2);
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                level.setBlock(anchor.offset(x, -1, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        var context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(anchor.below()).add(0, .5, 0), Direction.UP, anchor.below(), false));
        helper.assertTrue(block.getStateForPlacement(context) != null, "A clear supported footprint was rejected");
        BlockPos obstruction = anchor.offset(1, 2, 1);
        level.setBlock(obstruction, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        helper.assertTrue(block.getStateForPlacement(context) == null, "An obstructed upper corner was accepted");
        helper.assertFalse(ModItems.CHARON_STATUE.get().place(context).consumesAction(), "Obstructed placement succeeded");
        helper.assertTrue(level.getBlockState(obstruction).is(Blocks.STONE), "Placement overwrote the obstruction");
        helper.assertValueEqual(stack.getCount(), 2, "items after refused placement");
        level.removeBlock(obstruction, false);
        level.removeBlock(anchor.offset(1, -1, 1), false);
        helper.assertTrue(block.getStateForPlacement(context) == null, "An unsupported corner was accepted");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "charon_statue")
    public static void snapshotRollbackDoesNotEraseRestoredNeighbors(GameTestHelper helper) {
        var level = helper.getLevel();
        var block = ModBlocks.CHARON_STATUE.get();
        BlockPos anchor = helper.absolutePos(new BlockPos(3, 1, 3));
        var root = block.defaultBlockState();
        level.setBlock(anchor, root, Block.UPDATE_ALL);
        block.setPlacedBy(level, anchor, root, null, ItemStack.EMPTY);
        level.restoringBlockSnapshots = true;
        try {
            // Undo the root first. Its onRemove must not recursively erase other snapshots.
            level.setBlock(anchor, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(level.getBlockState(anchor.above()).is(block), "Rollback cascaded into other snapshots");
            for (int x = 0; x < 2; x++) {
                for (int y = 0; y < 3; y++) {
                    for (int z = 0; z < 2; z++) {
                        if (x != 0 || y != 0 || z != 0) {
                            level.setBlock(anchor.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        }
                    }
                }
            }
        } finally {
            level.restoringBlockSnapshots = false;
        }
        helper.assertTrue(level.getBlockState(anchor).is(Blocks.STONE), "Restored block was erased");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "charon_statue")
    public static void creativeTabAndPickBlockExposeTheStatueItem(GameTestHelper helper) {
        helper.assertTrue(ModCreativeTabs.creativeItems().contains(ModItems.CHARON_STATUE.get()), "Statue is absent from the creative tab");
        helper.assertTrue(ModBlocks.CHARON_STATUE.get().asItem() == ModItems.CHARON_STATUE.get(), "Pick block resolves the wrong item");
        helper.assertTrue(ModBlocks.CHARON_STATUE.get().defaultBlockState().getPistonPushReaction() == PushReaction.BLOCK,
                "A piston can split the structure");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "charon_statue_shop")
    public static void clickingTheStatueOffersTheConfiguredPrice(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));
        var fixture = RegressionPlayers.create(level, anchor.offset(0, 0, -2), 0);
        boolean sale = ModConfigSpec.CHARON_STATUE_SALE_ENABLED.get();
        long cost = ModConfigSpec.CHARON_STATUE_TOKEN_COST.getAsLong();
        place(level, anchor);
        try {
            ModConfigSpec.CHARON_STATUE_SALE_ENABLED.set(true);
            ModConfigSpec.CHARON_STATUE_TOKEN_COST.set(640L);

            click(level, CharonStatueBlock.partPos(anchor, Direction.NORTH, 1, 1, 0), fixture.player());

            var offers = offers(fixture);
            helper.assertValueEqual(offers.size(), 1, "offers sent by one click");
            helper.assertValueEqual(offers.get(0).price(), 640L, "offered price");
            helper.assertValueEqual(offers.get(0).anchor(), anchor, "offered anchor");

            ModConfigSpec.CHARON_STATUE_SALE_ENABLED.set(false);
            click(level, anchor, fixture.player());
            helper.assertValueEqual(offers(fixture).size(), 1, "offers while the sale is disabled");
        } finally {
            ModConfigSpec.CHARON_STATUE_SALE_ENABLED.set(sale);
            ModConfigSpec.CHARON_STATUE_TOKEN_COST.set(cost);
            clear(level, anchor);
            fixture.player().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "charon_statue_shop")
    public static void coinsAloneBuyTheTokenAtTheConfiguredPrice(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = RegressionPlayers.create(level, anchor.offset(0, 0, -2), 0).player();
        boolean sale = ModConfigSpec.CHARON_STATUE_SALE_ENABLED.get();
        long cost = ModConfigSpec.CHARON_STATUE_TOKEN_COST.getAsLong();
        place(level, anchor);
        try {
            ModConfigSpec.CHARON_STATUE_SALE_ENABLED.set(true);
            ModConfigSpec.CHARON_STATUE_TOKEN_COST.set(800L);
            NumismaticsEconomy.give(player, 803L);

            helper.assertTrue(CharonStatueShop.buy(player, anchor), "the coin purchase was accepted");

            helper.assertValueEqual(
                    player.getInventory().countItem(ModItems.CHARON_TOKEN.get()), 1, "tokens after the purchase");
            helper.assertValueEqual(NumismaticsEconomy.balance(player), 3L, "spurs left after the purchase");
        } finally {
            ModConfigSpec.CHARON_STATUE_SALE_ENABLED.set(sale);
            ModConfigSpec.CHARON_STATUE_TOKEN_COST.set(cost);
            clear(level, anchor);
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "charon_statue_shop")
    public static void anOfficerPaysWithCoinsThenBankThenTreasury(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = RegressionPlayers.create(level, anchor.offset(0, 0, -2), 0).player();
        Treasury treasury = Treasury.open(helper, level, player, anchor, FactionRole.OFFICER, 300L);
        BankAccount bank = fundBank(player, 200);
        place(level, anchor);
        try {
            NumismaticsEconomy.give(player, 300L);

            helper.assertTrue(CharonStatueShop.buy(player, anchor), "the split purchase was accepted");

            helper.assertValueEqual(
                    player.getInventory().countItem(ModItems.CHARON_TOKEN.get()), 1, "tokens after the purchase");
            helper.assertValueEqual(NumismaticsEconomy.balance(player), 0L, "coins after the purchase");
            helper.assertValueEqual(bank.getBalance(), 0, "bank balance after the purchase");
            helper.assertValueEqual(treasury.balance(), 0L, "treasury after the purchase");
        } finally {
            clear(level, anchor);
            treasury.close();
            clearBank(bank);
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "charon_statue_shop")
    public static void aPlainMemberCannotReachTheTreasury(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = RegressionPlayers.create(level, anchor.offset(0, 0, -2), 0).player();
        Treasury treasury = Treasury.open(helper, level, player, anchor, FactionRole.MEMBER, 300L);
        BankAccount bank = fundBank(player, 200);
        place(level, anchor);
        try {
            NumismaticsEconomy.give(player, 300L);

            helper.assertFalse(CharonStatueShop.buy(player, anchor), "a member cannot spend the treasury");

            helper.assertValueEqual(
                    player.getInventory().countItem(ModItems.CHARON_TOKEN.get()), 0, "tokens after the refusal");
            helper.assertValueEqual(NumismaticsEconomy.balance(player), 300L, "coins after the refusal");
            helper.assertValueEqual(bank.getBalance(), 200, "bank balance after the refusal");
            helper.assertValueEqual(treasury.balance(), 300L, "treasury after the refusal");
        } finally {
            clear(level, anchor);
            treasury.close();
            clearBank(bank);
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "charon_statue_shop")
    public static void allThreeSourcesTogetherCanStillFallShort(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = RegressionPlayers.create(level, anchor.offset(0, 0, -2), 0).player();
        Treasury treasury = Treasury.open(helper, level, player, anchor, FactionRole.OFFICER, 200L);
        BankAccount bank = fundBank(player, 200);
        place(level, anchor);
        try {
            NumismaticsEconomy.give(player, 300L);

            helper.assertFalse(CharonStatueShop.buy(player, anchor), "700 spurs cannot pay for 800");

            helper.assertValueEqual(
                    player.getInventory().countItem(ModItems.CHARON_TOKEN.get()), 0, "tokens after the refusal");
            helper.assertValueEqual(NumismaticsEconomy.balance(player), 300L, "coins after the refusal");
            helper.assertValueEqual(bank.getBalance(), 200, "bank balance after the refusal");
            helper.assertValueEqual(treasury.balance(), 200L, "treasury after the refusal");
        } finally {
            clear(level, anchor);
            treasury.close();
            clearBank(bank);
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "charon_statue_shop")
    public static void ghostsAndDistantBuyersAreRefused(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));
        var fixture = RegressionPlayers.create(level, anchor.offset(0, 0, -2), 0);
        ServerPlayer player = fixture.player();
        CharonManager manager = CharonManager.get(level.getServer());
        boolean sale = ModConfigSpec.CHARON_STATUE_SALE_ENABLED.get();
        long cost = ModConfigSpec.CHARON_STATUE_TOKEN_COST.getAsLong();
        place(level, anchor);
        try {
            ModConfigSpec.CHARON_STATUE_SALE_ENABLED.set(true);
            ModConfigSpec.CHARON_STATUE_TOKEN_COST.set(800L);
            NumismaticsEconomy.give(player, 1000L);

            manager.startGhost(player.getUUID(), new CharonManager.Ghost(
                    GlobalPos.of(level.dimension(), anchor), level.getGameTime() + 400L, 20.0F));
            helper.assertFalse(CharonStatueShop.buy(player, anchor), "a ghost cannot buy the token");
            click(level, anchor, player);
            helper.assertTrue(offers(fixture).isEmpty(), "a ghost gets no offer");
            manager.clearGhost(player.getUUID());

            player.setPos(anchor.getX() + 20.5D, anchor.getY() + 0.5D, anchor.getZ() + 0.5D);
            helper.assertFalse(CharonStatueShop.buy(player, anchor), "a distant player cannot buy the token");

            helper.assertValueEqual(
                    player.getInventory().countItem(ModItems.CHARON_TOKEN.get()), 0, "tokens after the refusals");
            helper.assertValueEqual(NumismaticsEconomy.balance(player), 1000L, "coins after the refusals");
        } finally {
            ModConfigSpec.CHARON_STATUE_SALE_ENABLED.set(sale);
            ModConfigSpec.CHARON_STATUE_TOKEN_COST.set(cost);
            manager.forget(player.getUUID());
            clear(level, anchor);
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "charon_statue_shop")
    public static void theStatueRecipeNeedsEveryIngredient(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "charon_statue");
        RecipeHolder<?> holder = helper.getLevel().getRecipeManager().byKey(id).orElseThrow();
        helper.assertTrue(
                holder.value().getResultItem(helper.getLevel().registryAccess()).is(ModItems.CHARON_STATUE.get()),
                "the recipe makes a statue"
        );
        ItemStack bone = new ItemStack(Items.BONE);
        ItemStack copper = new ItemStack(Items.COPPER_BLOCK);
        ItemStack apple = new ItemStack(Items.GOLDEN_APPLE);
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        ItemStack ingot = new ItemStack(Items.COPPER_INGOT);

        assertAssembles(helper, holder, List.of(
                bone, copper, bone,
                apple, copper, apple,
                diamond, copper, diamond
        ), true);
        assertAssembles(helper, holder, List.of(
                bone, copper, bone,
                apple, copper, apple,
                ItemStack.EMPTY, copper, ItemStack.EMPTY
        ), false);
        assertAssembles(helper, holder, List.of(
                bone, ingot, bone,
                apple, ingot, apple,
                diamond, ingot, diamond
        ), false);
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    private static void assertAssembles(
            GameTestHelper helper, RecipeHolder<?> holder, List<ItemStack> grid, boolean expected) {
        Recipe<CraftingInput> recipe = (Recipe<CraftingInput>) holder.value();
        helper.assertValueEqual(
                recipe.matches(CraftingInput.of(3, 3, grid), helper.getLevel()),
                expected,
                (expected ? "assembles " : "must not assemble: ") + holder.id()
        );
    }

    private static void place(ServerLevel level, BlockPos anchor) {
        var block = ModBlocks.CHARON_STATUE.get();
        var root = block.partState(Direction.NORTH, 0, 0, 0);
        level.setBlock(anchor, root, Block.UPDATE_ALL);
        block.setPlacedBy(level, anchor, root, null, ItemStack.EMPTY);
    }

    private static void clear(ServerLevel level, BlockPos anchor) {
        level.destroyBlock(anchor, false);
    }

    private static void click(ServerLevel level, BlockPos pos, ServerPlayer player) {
        level.getBlockState(pos).useWithoutItem(
                level, player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static List<CharonPayloads.S2CStatueOffer> offers(RegressionPlayers.Fixture fixture) {
        return fixture.packets().stream()
                .filter(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof CharonPayloads.S2CStatueOffer)
                .map(packet -> (CharonPayloads.S2CStatueOffer) ((ClientboundCustomPayloadPacket) packet).payload())
                .toList();
    }

    private static BankAccount fundBank(ServerPlayer player, int amount) {
        BankAccount account = Numismatics.BANK.getOrCreateAccount(player.getUUID(), BankAccount.Type.PLAYER);
        account.setBalance(amount);
        return account;
    }

    private static void clearBank(BankAccount account) {
        account.setBalance(0);
    }

    private record Treasury(ServerLevel level, FactionManager manager, FactionManager original, UUID factionId) {
        private static Treasury open(
                GameTestHelper helper,
                ServerLevel level,
                ServerPlayer player,
                BlockPos anchor,
                FactionRole role,
                long balance
        ) {
            var storage = level.getServer().overworld().getDataStorage();
            FactionManager original = FactionManager.get(level);
            FactionManager manager = new FactionManager();
            var created = manager.createFaction(UUID.randomUUID(), "Charon", ClaimKey.of(level, anchor), 1);
            helper.assertTrue(created.successful(), "the fixture faction was created");
            UUID factionId = created.factionId();
            helper.assertTrue(manager.addMember(factionId, player.getUUID()).successful(), "the buyer joined");
            if (role != FactionRole.MEMBER) {
                helper.assertTrue(
                        manager.setMemberRole(factionId, player.getUUID(), role).successful(), "the buyer got a role");
            }
            helper.assertTrue(manager.deposit(factionId, balance).successful(), "the treasury was filled");
            storage.set(FactionManager.DATA_NAME, manager);
            return new Treasury(level, manager, original, factionId);
        }

        private long balance() {
            return manager.getFactionById(factionId).map(Faction::treasuryBalance).orElse(0L);
        }

        private void close() {
            level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, original);
        }
    }

    private CharonStatueGameTests() {
    }
}
