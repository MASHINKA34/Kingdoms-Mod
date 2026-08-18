package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.dungeon.DungeonManager;
import com.geydev.kalfactions.quarry.QuarryDistribution;
import com.geydev.kalfactions.quarry.QuarryManager;
import com.geydev.kalfactions.sanctuary.SanctuaryManager;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProtectedZoneToolGameTests {
    @GameTest(template = "empty", batch = "protected_zone_tools", timeoutTicks = 400)
    public static void toolsCannotReshapeDungeonBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DungeonManager manager = DungeonManager.get(level);
        BlockPos anchor = new BlockPos(
                level.getSharedSpawnPos().getX() + 180_000,
                level.getSeaLevel() + 24,
                level.getSharedSpawnPos().getZ() + 180_000
        );
        level.getChunk(new ChunkPos(anchor).x, new ChunkPos(anchor).z);
        DungeonManager.CreateResult created = manager.create(level, anchor, "Тест инструментов");
        helper.assertTrue(created.successful(), "the dungeon was created in the black zone");
        int dungeonId = created.dungeon().id();
        helper.assertTrue(
                manager.setClaims(level, dungeonId, List.of(ClaimKey.of(level, anchor)), true).changed() == 1,
                "the anchor chunk joined the dungeon"
        );
        try {
            assertZoneRejectsTools(helper, level, anchor, anchor.offset(64, 0, 0));
        } finally {
            manager.remove(dungeonId);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "protected_zone_tools", timeoutTicks = 400)
    public static void toolsCannotReshapeQuarryBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuarryManager manager = QuarryManager.get(level);
        ChunkPos core = findQuarryCandidate(level, manager);
        level.getChunk(core.x, core.z);
        QuarryManager.CreateResult created = manager.createAtChunk(level, core);
        helper.assertTrue(
                created == QuarryManager.CreateResult.CREATED || created == QuarryManager.CreateResult.OVERLAP,
                "the quarry was created, got " + created
        );
        BlockPos quarryCore = manager.all().stream()
                .filter(view -> new ChunkPos(view.core()).equals(core))
                .findFirst()
                .orElseThrow()
                .core();
        BlockPos inside = new BlockPos(core.getMiddleBlockX(), level.getSeaLevel() + 24, core.getMiddleBlockZ());
        try {
            assertZoneRejectsTools(helper, level, inside, inside.offset(128, 0, 0));
        } finally {
            manager.removeByCore(level, quarryCore);
            level.removeBlock(quarryCore, false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "protected_zone_tools", timeoutTicks = 400)
    public static void toolsCannotReshapeSpawnZoneBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SanctuaryManager manager = SanctuaryManager.get(level);
        BlockPos inside = new BlockPos(
                level.getSharedSpawnPos().getX() + 210_000,
                level.getSeaLevel() + 24,
                level.getSharedSpawnPos().getZ() + 210_000
        );
        ChunkPos chunk = new ChunkPos(inside);
        level.getChunk(chunk.x, chunk.z);
        ClaimKey key = new ClaimKey(level.dimension(), chunk);
        helper.assertTrue(manager.setClaim(key, true), "the chunk joined the spawn zone");
        try {
            assertZoneRejectsTools(helper, level, inside, inside.offset(64, 0, 0));
        } finally {
            manager.setClaim(key, false);
        }
        helper.succeed();
    }

    private static void assertZoneRejectsTools(
            GameTestHelper helper,
            ServerLevel level,
            BlockPos inside,
            BlockPos outside
    ) {
        level.getChunk(new ChunkPos(outside).x, new ChunkPos(outside).z);
        ServerPlayer visitor = mockPlayer(level, "kingdoms-tool-visitor", 0);
        ServerPlayer operator = mockPlayer(level, "kingdoms-tool-operator", 4);
        try {
            level.setBlock(inside, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            level.setBlock(outside, Blocks.GRASS_BLOCK.defaultBlockState(), 3);

            helper.assertTrue(
                    toolBlocked(visitor, level, inside, Items.IRON_HOE, ItemAbilities.HOE_TILL),
                    "a hoe may not till inside the zone"
            );
            helper.assertTrue(
                    toolBlocked(visitor, level, inside, Items.IRON_SHOVEL, ItemAbilities.SHOVEL_FLATTEN),
                    "a shovel may not make a path inside the zone"
            );
            helper.assertTrue(
                    toolBlocked(visitor, level, inside, Items.IRON_AXE, ItemAbilities.AXE_STRIP),
                    "an axe may not strip inside the zone"
            );
            helper.assertFalse(
                    toolBlocked(operator, level, inside, Items.IRON_HOE, ItemAbilities.HOE_TILL),
                    "operators still reshape blocks inside the zone"
            );
            helper.assertFalse(
                    toolBlocked(visitor, level, outside, Items.IRON_HOE, ItemAbilities.HOE_TILL),
                    "the wild chunk beside the zone stays tillable"
            );
        } finally {
            level.setBlock(inside, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(outside, Blocks.AIR.defaultBlockState(), 3);
            visitor.discard();
            operator.discard();
        }
    }

    private static boolean toolBlocked(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos,
            net.minecraft.world.item.Item tool,
            ItemAbility ability
    ) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        UseOnContext context =
                new UseOnContext(level, player, InteractionHand.MAIN_HAND, new ItemStack(tool), hit);
        BlockEvent.BlockToolModificationEvent event = new BlockEvent.BlockToolModificationEvent(
                level.getBlockState(pos),
                context,
                ability,
                false
        );
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    private static ChunkPos findQuarryCandidate(ServerLevel level, QuarryManager manager) {
        BlockPos spawn = level.getSharedSpawnPos();
        int red = ModConfigSpec.RESOURCE_RED_RADIUS.getAsInt();
        for (int distance = red + 64; distance <= red + 1_800; distance += 16) {
            for (int offset = -6_000; offset <= 6_000; offset += 16) {
                ChunkPos chunk = new ChunkPos(new BlockPos(spawn.getX() + distance, 0, spawn.getZ() + offset));
                if (!level.getWorldBorder().isWithinBounds(new BlockPos(
                        chunk.getMiddleBlockX(),
                        level.getSeaLevel(),
                        chunk.getMiddleBlockZ()
                ))) {
                    continue;
                }
                if (!QuarryDistribution.isCandidate(
                        level.getSeed() ^ 0x5155415252594C31L,
                        chunk.x,
                        chunk.z,
                        QuarryManager.MINIMUM_SPACING_CHUNKS
                )) {
                    continue;
                }
                boolean blocked = manager.all().stream().anyMatch(view -> {
                    ChunkPos existing = new ChunkPos(view.core());
                    return !existing.equals(chunk)
                            && Math.max(Math.abs(existing.x - chunk.x), Math.abs(existing.z - chunk.z))
                            <= QuarryManager.MINIMUM_SPACING_CHUNKS;
                });
                if (!blocked) {
                    return chunk;
                }
            }
        }
        throw new IllegalStateException("No valid quarry position found");
    }

    private static ServerPlayer mockPlayer(ServerLevel level, String name, int permissions) {
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        return new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            protected int getPermissionLevel() {
                return permissions;
            }

            @Override
            public void displayClientMessage(Component message, boolean actionBar) {
            }
        };
    }

    private ProtectedZoneToolGameTests() {
    }
}
