package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.dungeon.DungeonManager;
import com.geydev.kalfactions.item.WarpScrollItem;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.registry.ModDataComponents;
import com.geydev.kalfactions.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WarpGameTests {
    @GameTest(template = "empty", batch = "warp", timeoutTicks = 300)
    public static void clickingTheAnchorBindsTheScroll(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 1, 0));
        ServerPlayer player = mockPlayer(level, "kingdoms-warp-binder", null);
        try {
            level.setBlockAndUpdate(anchor, ModBlocks.WARP_ANCHOR.get().defaultBlockState());
            ItemStack scroll = new ItemStack(ModItems.WARP_SCROLL.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
            helper.assertTrue(
                    ModItems.WARP_SCROLL.get().useOn(useContext(player, anchor)).consumesAction(),
                    "clicking the anchor binds the scroll"
            );
            GlobalPos target = scroll.get(ModDataComponents.WARP_TARGET.get());
            helper.assertTrue(target != null, "the scroll stores a target");
            helper.assertValueEqual(target.dimension(), level.dimension(), "bound dimension");
            helper.assertValueEqual(target.pos(), anchor, "bound position");
            helper.assertValueEqual(scroll.getCount(), 1, "binding keeps the scroll");
        } finally {
            level.setBlockAndUpdate(anchor, Blocks.AIR.defaultBlockState());
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "warp", timeoutTicks = 300)
    public static void clickingAnythingElseLeavesTheScrollUnbound(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos stone = helper.absolutePos(new BlockPos(0, 1, 0));
        ServerPlayer player = mockPlayer(level, "kingdoms-warp-misclick", null);
        try {
            level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
            ItemStack scroll = new ItemStack(ModItems.WARP_SCROLL.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
            helper.assertFalse(
                    ModItems.WARP_SCROLL.get().useOn(useContext(player, stone)).consumesAction(),
                    "another block does not bind the scroll"
            );
            helper.assertTrue(
                    scroll.get(ModDataComponents.WARP_TARGET.get()) == null,
                    "the scroll stays unbound"
            );
        } finally {
            level.setBlockAndUpdate(stone, Blocks.AIR.defaultBlockState());
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "warp", timeoutTicks = 300)
    public static void usingOutsideADungeonKeepsTheScroll(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 1, 0));
        Teleports teleports = new Teleports();
        ServerPlayer player = mockPlayer(level, "kingdoms-warp-outsider", teleports);
        try {
            level.setBlockAndUpdate(anchor, ModBlocks.WARP_ANCHOR.get().defaultBlockState());
            ItemStack scroll = boundScroll(level, anchor);
            player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
            player.setPos(anchor.getX() + 0.5D, anchor.getY() + 1.0D, anchor.getZ() + 0.5D);
            helper.assertFalse(
                    DungeonManager.get(level).isDungeon(level, player.blockPosition()),
                    "the test area is not a dungeon"
            );

            InteractionResultHolder<ItemStack> result =
                    ModItems.WARP_SCROLL.get().use(level, player, InteractionHand.MAIN_HAND);

            helper.assertFalse(result.getResult().consumesAction(), "the scroll refuses to work outside a dungeon");
            helper.assertValueEqual(scroll.getCount(), 1, "the scroll is not consumed");
            helper.assertValueEqual(teleports.calls, 0, "no teleport happened");
        } finally {
            level.setBlockAndUpdate(anchor, Blocks.AIR.defaultBlockState());
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "warp", timeoutTicks = 600)
    public static void aBrokenAnchorKeepsTheScroll(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DungeonManager manager = DungeonManager.get(level);
        BlockPos zone = dungeonAnchor(level, 1);
        DungeonManager.DungeonView dungeon = createDungeon(helper, level, manager, zone, "Тест варпа без якоря");
        BlockPos anchor = zone.offset(2, 0, 2);
        Teleports teleports = new Teleports();
        ServerPlayer player = mockPlayer(level, "kingdoms-warp-lost", teleports);
        try {
            level.setBlockAndUpdate(anchor, Blocks.AIR.defaultBlockState());
            ItemStack scroll = boundScroll(level, anchor);
            player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
            player.setPos(zone.getX() + 0.5D, zone.getY(), zone.getZ() + 0.5D);
            helper.assertTrue(
                    manager.isDungeon(level, player.blockPosition()),
                    "the player stands inside the dungeon"
            );

            InteractionResultHolder<ItemStack> result =
                    ModItems.WARP_SCROLL.get().use(level, player, InteractionHand.MAIN_HAND);

            helper.assertFalse(result.getResult().consumesAction(), "a destroyed anchor refuses the warp");
            helper.assertValueEqual(scroll.getCount(), 1, "the scroll is not consumed");
            helper.assertValueEqual(teleports.calls, 0, "no teleport happened");
        } finally {
            player.discard();
            manager.remove(dungeon.id());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "warp", timeoutTicks = 600)
    public static void warpingConsumesTheScrollAndKeepsTheRespawn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DungeonManager manager = DungeonManager.get(level);
        BlockPos zone = dungeonAnchor(level, 2);
        DungeonManager.DungeonView dungeon = createDungeon(helper, level, manager, zone, "Тест варпа");
        BlockPos anchor = zone.offset(2, 0, 2);
        BlockPos respawn = level.getSharedSpawnPos();
        Teleports teleports = new Teleports();
        ServerPlayer player = mockPlayer(level, "kingdoms-warp-traveller", teleports);
        try {
            buildLandingPad(level, anchor);
            ItemStack scroll = boundScroll(level, anchor);
            player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
            player.setPos(zone.getX() + 0.5D, zone.getY(), zone.getZ() + 0.5D);
            player.setRespawnPosition(Level.OVERWORLD, respawn, 0.0F, true, false);

            InteractionResultHolder<ItemStack> result =
                    ModItems.WARP_SCROLL.get().use(level, player, InteractionHand.MAIN_HAND);

            helper.assertTrue(result.getResult().consumesAction(), "the warp succeeded");
            helper.assertValueEqual(scroll.getCount(), 0, "the scroll is consumed");
            helper.assertValueEqual(teleports.calls, 1, "the player was teleported once");
            helper.assertValueEqual(teleports.level, level, "teleport target level");
            BlockPos landing = BlockPos.containing(teleports.position);
            helper.assertValueEqual(landing.getY(), anchor.getY(), "the landing keeps the anchor height");
            helper.assertFalse(
                    landing.getX() == anchor.getX() && landing.getZ() == anchor.getZ(),
                    "the player does not land in the anchor column"
            );
            helper.assertTrue(
                    Math.abs(landing.getX() - anchor.getX()) <= 1
                            && Math.abs(landing.getZ() - anchor.getZ()) <= 1,
                    "the player lands right next to the anchor"
            );
            helper.assertValueEqual(player.getRespawnPosition(), respawn, "respawn position is untouched");
            helper.assertValueEqual(player.getRespawnDimension(), Level.OVERWORLD, "respawn dimension is untouched");
        } finally {
            clear(level, anchor.offset(-2, -1, -2), anchor.offset(2, 2, 2));
            player.discard();
            manager.remove(dungeon.id());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "warp", timeoutTicks = 300)
    public static void theLandingStandsBesideTheAnchor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(1, 8, 1));
        try {
            buildLandingPad(level, anchor);

            BlockPos landing = WarpScrollItem.findLanding(level, anchor);

            helper.assertValueEqual(landing.getY(), anchor.getY(), "the landing keeps the anchor height");
            helper.assertFalse(
                    landing.getX() == anchor.getX() && landing.getZ() == anchor.getZ(),
                    "the landing avoids the anchor column"
            );
            helper.assertTrue(
                    Math.abs(landing.getX() - anchor.getX()) <= 1
                            && Math.abs(landing.getZ() - anchor.getZ()) <= 1,
                    "the landing touches the anchor"
            );
        } finally {
            clear(level, anchor.offset(-2, -1, -2), anchor.offset(2, 2, 2));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "warp", timeoutTicks = 300)
    public static void aBoxedInAnchorSendsThePlayerAbove(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(1, 12, 1));
        try {
            fill(level, anchor.offset(-1, -4, -1), anchor.offset(1, 3, 1), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(anchor, ModBlocks.WARP_ANCHOR.get().defaultBlockState());
            clear(level, anchor.above(), anchor.above(2));

            helper.assertValueEqual(
                    WarpScrollItem.findLanding(level, anchor),
                    anchor.above(),
                    "a boxed in anchor still lands the player above itself"
            );
        } finally {
            clear(level, anchor.offset(-1, -4, -1), anchor.offset(1, 3, 1));
        }
        helper.succeed();
    }

    private static void buildLandingPad(ServerLevel level, BlockPos anchor) {
        fill(level, anchor.offset(-2, -1, -2), anchor.offset(2, -1, 2), Blocks.STONE.defaultBlockState());
        clear(level, anchor.offset(-2, 0, -2), anchor.offset(2, 2, 2));
        level.setBlockAndUpdate(anchor, ModBlocks.WARP_ANCHOR.get().defaultBlockState());
    }

    private static void fill(ServerLevel level, BlockPos from, BlockPos to, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            level.setBlockAndUpdate(pos, state);
        }
    }

    private static void clear(ServerLevel level, BlockPos from, BlockPos to) {
        fill(level, from, to, Blocks.AIR.defaultBlockState());
    }

    private static ItemStack boundScroll(ServerLevel level, BlockPos anchor) {
        ItemStack scroll = new ItemStack(ModItems.WARP_SCROLL.get());
        scroll.set(ModDataComponents.WARP_TARGET.get(), GlobalPos.of(level.dimension(), anchor));
        return scroll;
    }

    private static UseOnContext useContext(ServerPlayer player, BlockPos pos) {
        return new UseOnContext(
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
        );
    }

    private static DungeonManager.DungeonView createDungeon(
            GameTestHelper helper,
            ServerLevel level,
            DungeonManager manager,
            BlockPos anchor,
            String name
    ) {
        manager.byName(name).ifPresent(existing -> manager.remove(existing.id()));
        DungeonManager.CreateResult result = manager.create(level, anchor, name);
        helper.assertTrue(result.successful(), "the dungeon was created in the black zone");
        DungeonManager.DungeonView dungeon = result.dungeon();
        DungeonManager.MarkResult marked = manager.setClaims(
                level,
                dungeon.id(),
                List.of(ClaimKey.of(level, anchor)),
                true
        );
        helper.assertTrue(marked.changed() == 1, "the anchor chunk joined the dungeon");
        return manager.byId(dungeon.id()).orElseThrow();
    }

    private static BlockPos dungeonAnchor(ServerLevel level, int index) {
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos anchor = new BlockPos(
                spawn.getX() + 70_000 + index * 1_024,
                level.getSeaLevel() + 24,
                spawn.getZ() + 70_000
        );
        ChunkPos chunk = new ChunkPos(anchor);
        level.getChunk(chunk.x, chunk.z);
        return anchor;
    }

    private static ServerPlayer mockPlayer(ServerLevel level, String name, Teleports teleports) {
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        return new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public void displayClientMessage(Component message, boolean actionBar) {
            }

            @Override
            public void teleportTo(ServerLevel newLevel, double x, double y, double z, float yaw, float pitch) {
                if (teleports != null) {
                    teleports.level = newLevel;
                    teleports.position = new Vec3(x, y, z);
                    teleports.calls++;
                }
                setPos(x, y, z);
            }
        };
    }

    private static final class Teleports {
        private ServerLevel level;
        private Vec3 position;
        private int calls;
    }

    private WarpGameTests() {
    }
}
