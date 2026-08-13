package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.DungeonChestBlockEntity;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.protection.MachineProtection;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.sanctuary.SanctuaryManager;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DungeonGameTests {
    private static final ResourceKey<LootTable> TEST_LOOT_TABLE = ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.withDefaultNamespace("chests/simple_dungeon")
    );

    @GameTest(template = "empty", batch = "dungeon_break", timeoutTicks = 600)
    public static void playersCannotBreakBlocksInsideDungeon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DungeonManager manager = DungeonManager.get(level);
        BlockPos anchor = blackZoneAnchor(level, 1);
        DungeonManager.DungeonView dungeon = createDungeon(helper, level, manager, anchor, "Тест ломки");
        ServerPlayer visitor = mockPlayer(level, "kingdoms-dungeon-visitor", 0);
        ServerPlayer operator = mockPlayer(level, "kingdoms-dungeon-operator", 4);
        BlockPos target = anchor.above();
        try {
            level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
            helper.assertTrue(breakEvent(visitor, level, target).isCanceled(), "visitor cannot break in a dungeon");
            helper.assertFalse(breakEvent(operator, level, target).isCanceled(), "operators still break in a dungeon");

            BlockPos outside = anchor.offset(64, 1, 0);
            level.getChunk(new ChunkPos(outside).x, new ChunkPos(outside).z);
            level.setBlockAndUpdate(outside, Blocks.STONE.defaultBlockState());
            helper.assertFalse(
                    breakEvent(visitor, level, outside).isCanceled(),
                    "the neighbouring wild chunk stays breakable"
            );
            level.setBlockAndUpdate(outside, Blocks.AIR.defaultBlockState());
        } finally {
            level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
            visitor.discard();
            operator.discard();
            manager.remove(dungeon.id());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dungeon_break", timeoutTicks = 600)
    public static void explosionsNeverDamageDungeonBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DungeonManager manager = DungeonManager.get(level);
        BlockPos anchor = blackZoneAnchor(level, 2);
        DungeonManager.DungeonView dungeon = createDungeon(helper, level, manager, anchor, "Тест ТНТ");
        BlockPos target = anchor.above();
        try {
            level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
            level.explode(
                    null,
                    target.getX() + 0.5D,
                    target.getY() + 1.5D,
                    target.getZ() + 0.5D,
                    6.0F,
                    Level.ExplosionInteraction.TNT
            );
            helper.assertTrue(
                    level.getBlockState(target).is(Blocks.STONE),
                    "TNT never removes a dungeon block"
            );
        } finally {
            level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
            manager.remove(dungeon.id());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dungeon_break", timeoutTicks = 600)
    public static void machinesAndProjectilesCannotBreakDungeonBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DungeonManager manager = DungeonManager.get(level);
        BlockPos anchor = blackZoneAnchor(level, 3);
        DungeonManager.DungeonView dungeon = createDungeon(helper, level, manager, anchor, "Тест машин");
        ServerPlayer shooter = mockPlayer(level, "kingdoms-dungeon-shooter", 0);
        BlockPos target = anchor.above();
        BlockPos outside = anchor.offset(64, 1, 0);
        try {
            level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
            helper.assertFalse(
                    MachineProtection.canContraptionBreak(level, target),
                    "a Create contraption cannot break or assemble a dungeon block"
            );
            helper.assertFalse(
                    MachineProtection.canContraptionBreak(level, target, target),
                    "an anchored contraption cannot break a dungeon block either"
            );
            helper.assertFalse(
                    MachineProtection.canProjectileBreak(level, target, shooter),
                    "a bullet cannot break a dungeon block"
            );
            helper.assertFalse(
                    MachineProtection.canPlayerMine(level, target, shooter),
                    "beam mining cannot break a dungeon block"
            );

            MachineProtection.beginProjectileContext(shooter);
            try {
                helper.assertTrue(
                        MachineProtection.blocksProjectileGrief(level, target),
                        "projectile grief is blocked inside a dungeon"
                );
                helper.assertFalse(level.removeBlock(target, false), "the level guard keeps the block in place");
            } finally {
                MachineProtection.endProjectileContext();
            }
            helper.assertTrue(level.getBlockState(target).is(Blocks.STONE), "the dungeon block survived");

            level.getChunk(new ChunkPos(outside).x, new ChunkPos(outside).z);
            helper.assertTrue(
                    MachineProtection.canContraptionBreak(level, outside),
                    "contraptions still work outside the dungeon"
            );
        } finally {
            level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
            shooter.discard();
            manager.remove(dungeon.id());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dungeon_claims", timeoutTicks = 600)
    public static void dungeonChunksRejectFactionsAndOverlaps(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DungeonManager manager = DungeonManager.get(level);
        SanctuaryManager sanctuary = SanctuaryManager.get(level);
        BlockPos anchor = blackZoneAnchor(level, 4);
        DungeonManager.DungeonView dungeon = createDungeon(helper, level, manager, anchor, "Тест клеймов");
        DungeonManager.DungeonView second = manager
                .create(level, anchor.offset(512, 0, 0), "Тест клеймов 2")
                .dungeon();
        ClaimKey dungeonKey = ClaimKey.of(level, anchor);
        ClaimKey sanctuaryKey = new ClaimKey(level.dimension(), new ChunkPos(anchor).x + 4, new ChunkPos(anchor).z);
        UUID ownerId = UUID.randomUUID();
        try {
            helper.assertTrue(second != null, "a second dungeon can be created");
            helper.assertFalse(
                    FactionManager.get(level)
                            .createFaction(ownerId, "ДанжТест", dungeonKey)
                            .successful(),
                    "a faction cannot be founded on dungeon chunks"
            );
            helper.assertTrue(
                    manager.canMark(level, second.id(), dungeonKey) == DungeonManager.Reason.OTHER_DUNGEON,
                    "another dungeon cannot take a marked chunk"
            );

            sanctuary.setClaim(sanctuaryKey, true);
            helper.assertTrue(
                    manager.canMark(level, dungeon.id(), sanctuaryKey) == DungeonManager.Reason.SANCTUARY,
                    "spawn chunks cannot become a dungeon"
            );
            sanctuary.setClaim(sanctuaryKey, false);

            ClaimKey redZoneKey = ClaimKey.of(level, level.getSharedSpawnPos());
            helper.assertTrue(
                    manager.canMark(level, dungeon.id(), redZoneKey) == DungeonManager.Reason.NOT_BLACK,
                    "only black zone chunks can become a dungeon"
            );
        } finally {
            sanctuary.setClaim(sanctuaryKey, false);
            manager.remove(dungeon.id());
            if (second != null) {
                manager.remove(second.id());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dungeon_loot", timeoutTicks = 600)
    public static void lootRefillsOnlyAfterTheCooldown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DungeonManager manager = DungeonManager.get(level);
        BlockPos anchor = blackZoneAnchor(level, 5);
        DungeonManager.DungeonView dungeon = createDungeon(helper, level, manager, anchor, "Тест лута");
        BlockPos chestPos = anchor.above();
        AtomicLong clock = new AtomicLong(1_000_000L);
        DungeonClock.override(clock::get);
        try {
            level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
            BlockEntity blockEntity = level.getBlockEntity(chestPos);
            helper.assertTrue(blockEntity instanceof RandomizableContainer, "the chest carries a loot table");
            RandomizableContainer container = (RandomizableContainer) blockEntity;
            container.setLootTable(TEST_LOOT_TABLE, 1L);

            helper.assertTrue(
                    DungeonLoot.registerAutomatically(level, chestPos),
                    "a structure chest inside a dungeon registers itself"
            );
            helper.assertTrue(
                    manager.lootAt(level, chestPos).isPresent(),
                    "the container is in the dungeon loot registry"
            );

            container.setLootTable(null);
            ((net.minecraft.world.Container) container).clearContent();
            ((net.minecraft.world.Container) container)
                    .setItem(0, new ItemStack(Items.DIRT, 3));

            helper.assertFalse(
                    DungeonLoot.refreshIfDue(level, chestPos),
                    "loot does not refill while the cooldown runs"
            );
            helper.assertTrue(
                    ((net.minecraft.world.Container) container).getItem(0).is(Items.DIRT),
                    "player items survive while the cooldown runs"
            );

            clock.addAndGet(DungeonLoot.cooldownMillis());
            helper.assertTrue(
                    DungeonLoot.refreshIfDue(level, chestPos),
                    "loot refills once the cooldown expired"
            );
            helper.assertTrue(container.getLootTable() != null, "a fresh loot table is armed");
            helper.assertTrue(
                    manager.lootAt(level, chestPos).orElseThrow().lastFilled() == clock.get(),
                    "the refill timestamp advanced"
            );

            container.setLootTable(null);
            helper.assertFalse(
                    DungeonLoot.refreshIfDue(level, chestPos),
                    "the cooldown restarts after a refill"
            );
        } finally {
            DungeonClock.reset();
            level.setBlockAndUpdate(chestPos, Blocks.AIR.defaultBlockState());
            manager.remove(dungeon.id());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dungeon_loot", timeoutTicks = 600)
    public static void dungeonChestRollsItsLootPlan(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DungeonManager manager = DungeonManager.get(level);
        BlockPos anchor = blackZoneAnchor(level, 6);
        DungeonManager.DungeonView dungeon = createDungeon(helper, level, manager, anchor, "Тест сундука");
        BlockPos chestPos = anchor.above();
        AtomicLong clock = new AtomicLong(5_000_000L);
        DungeonClock.override(clock::get);
        try {
            level.setBlockAndUpdate(chestPos, ModBlocks.DUNGEON_CHEST.get().defaultBlockState());
            helper.assertTrue(
                    level.getBlockEntity(chestPos) instanceof DungeonChestBlockEntity,
                    "the dungeon chest has its block entity"
            );
            DungeonChestBlockEntity chest = (DungeonChestBlockEntity) level.getBlockEntity(chestPos);

            helper.assertFalse(chest.configured(), "a fresh dungeon chest is unconfigured");
            helper.assertFalse(chest.refillIfDue(), "an unconfigured chest never refills");

            chest.planContainer().setItem(0, new ItemStack(Items.DIAMOND));
            chest.setEntry(0, 100, 3, 3);
            chest.planContainer().setItem(1, new ItemStack(Items.DIRT));
            chest.setEntry(1, 0, 1, 1);
            helper.assertTrue(chest.configured(), "a chest with a plan entry counts as configured");
            helper.assertTrue(chest.planCount() == 2, "the plan kept both entries");

            chest.setItem(0, new ItemStack(Items.STONE, 1));
            helper.assertFalse(chest.refillIfDue(), "the chest keeps its contents during the cooldown");
            helper.assertTrue(chest.getItem(0).is(Items.STONE), "player items survive during the cooldown");

            clock.addAndGet(chest.cooldownMillis());
            helper.assertTrue(chest.refillIfDue(), "the chest refills once the cooldown expired");
            helper.assertTrue(countOf(chest, Items.DIAMOND) == 3, "a 100% entry always rolls its fixed count");
            helper.assertTrue(countOf(chest, Items.DIRT) == 0, "a 0% entry never rolls");
            helper.assertTrue(countOf(chest, Items.STONE) == 0, "the previous contents were wiped");
            helper.assertFalse(chest.refillIfDue(), "the cooldown restarts after a refill");

            chest.setEntry(0, 100, 2, 5);
            chest.resetCooldown();
            helper.assertTrue(chest.refillIfDue(), "resetCooldown makes the chest refill at once");
            int rolled = countOf(chest, Items.DIAMOND);
            helper.assertTrue(rolled >= 2 && rolled <= 5, "the rolled count stays inside the range, got " + rolled);

            helper.assertTrue(
                    DungeonLoot.containerAt(level, chestPos) == null,
                    "the structure-chest registry ignores dungeon chests"
            );
        } finally {
            DungeonClock.reset();
            level.setBlockAndUpdate(chestPos, Blocks.AIR.defaultBlockState());
            manager.remove(dungeon.id());
        }
        helper.succeed();
    }

    private static int countOf(DungeonChestBlockEntity chest, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < DungeonChestBlockEntity.SIZE; slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    @GameTest(template = "empty", batch = "dungeon_claims", timeoutTicks = 600)
    public static void naturalSpawnsAreBlockedButCommandSpawnsAreNot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DungeonManager manager = DungeonManager.get(level);
        BlockPos anchor = blackZoneAnchor(level, 7);
        DungeonManager.DungeonView dungeon = createDungeon(helper, level, manager, anchor, "Тест спавна");
        BlockPos inside = anchor.above();
        BlockPos outside = anchor.offset(64, 1, 0);
        try {
            level.getChunk(new ChunkPos(outside).x, new ChunkPos(outside).z);
            helper.assertTrue(spawnCancelled(level, inside, MobSpawnType.NATURAL), "natural spawns are blocked");
            helper.assertTrue(
                    spawnCancelled(level, inside, MobSpawnType.CHUNK_GENERATION),
                    "passive chunk-generation spawns are blocked too"
            );
            helper.assertFalse(
                    spawnCancelled(level, inside, MobSpawnType.COMMAND),
                    "command blocks keep spawning mobs"
            );
            helper.assertFalse(
                    spawnCancelled(level, inside, MobSpawnType.SPAWN_EGG),
                    "spawn eggs keep working"
            );
            helper.assertFalse(
                    spawnCancelled(level, outside, MobSpawnType.NATURAL),
                    "mobs still spawn outside the dungeon"
            );
        } finally {
            manager.remove(dungeon.id());
        }
        helper.succeed();
    }

    private static boolean spawnCancelled(ServerLevel level, BlockPos pos, MobSpawnType type) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            throw new IllegalStateException("Zombie could not be created");
        }
        zombie.moveTo(Vec3.atBottomCenterOf(pos));
        FinalizeSpawnEvent event = new FinalizeSpawnEvent(
                zombie,
                level,
                pos.getX() + 0.5D,
                pos.getY(),
                pos.getZ() + 0.5D,
                level.getCurrentDifficultyAt(pos),
                type,
                null,
                null
        );
        NeoForge.EVENT_BUS.post(event);
        zombie.discard();
        return event.isSpawnCancelled();
    }

    private static DungeonManager.DungeonView createDungeon(
            GameTestHelper helper,
            ServerLevel level,
            DungeonManager manager,
            BlockPos anchor,
            String name
    ) {
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

    private static BlockPos blackZoneAnchor(ServerLevel level, int index) {
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos anchor = new BlockPos(
                spawn.getX() + 60_000 + index * 1_024,
                level.getSeaLevel() + 24,
                spawn.getZ() + 60_000
        );
        ChunkPos chunk = new ChunkPos(anchor);
        level.getChunk(chunk.x, chunk.z);
        return anchor;
    }

    private static BlockEvent.BreakEvent breakEvent(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockEvent.BreakEvent event =
                new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player);
        NeoForge.EVENT_BUS.post(event);
        return event;
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

    private DungeonGameTests() {
    }
}
