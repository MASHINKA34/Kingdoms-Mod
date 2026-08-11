package com.geydev.kalfactions.sanctuary;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.market.MarketPlot;
import com.geydev.kalfactions.market.MarketPlotManager;
import com.mojang.authlib.GameProfile;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SanctuaryGameTests {
    private static final TagKey<Block> BLOCKED_INTERACTION = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "sanctuary_blocked_interaction")
    );
    private static final AtomicReference<Consumer<Component>> NOTICE_SINK = new AtomicReference<>();

    @GameTest(template = "empty")
    public static void remoteControlPointDoesNotMoveAutomaticSquare(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SanctuaryManager manager = SanctuaryManager.get(level);
        BlockPos worldSpawn = level.getSharedSpawnPos();
        BlockPos remoteControl = worldSpawn.offset(1_024, 0, -1_024);
        manager.initializeAutomaticSpawn(level);
        Set<ClaimKey> spawnClaims = SanctuaryManager.calculateAutomaticClaims(
                Level.OVERWORLD,
                worldSpawn,
                200
        );

        manager.initializeAutomaticSpawn(Level.OVERWORLD, remoteControl, 200);
        helper.assertTrue(manager.claims().containsAll(spawnClaims), "spawn square stays in place");

        ClaimKey extension = ClaimKey.of(level, remoteControl);
        manager.setClaim(extension, true);
        helper.assertTrue(manager.isSanctuary(extension), "remote control can extend sanctuary");
        helper.assertTrue(manager.claims().containsAll(spawnClaims), "extension does not move spawn square");
        manager.setClaim(extension, false);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "sanctuary_fire", timeoutTicks = 600)
    public static void fireIsExtinguishedAndCannotSpreadIntoSanctuary(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SanctuaryManager manager = SanctuaryManager.get(level);
        BlockPos anchor = level.getSharedSpawnPos().offset(4_096, 0, 4_096);
        ChunkPos sanctuaryChunk = new ChunkPos(anchor);
        ChunkPos wildChunk = new ChunkPos(sanctuaryChunk.x + 1, sanctuaryChunk.z);
        level.getChunk(sanctuaryChunk.x, sanctuaryChunk.z);
        level.getChunk(wildChunk.x, wildChunk.z);

        int y = level.getSeaLevel() + 24;
        BlockPos insideFloor = new BlockPos(sanctuaryChunk.getMaxBlockX(), y, sanctuaryChunk.getMinBlockZ() + 8);
        BlockPos outsideFloor = insideFloor.east();
        BlockPos insideFuel = insideFloor.above();
        BlockPos outsideFire = outsideFloor.above();

        ClaimKey sanctuaryKey = ClaimKey.of(level, insideFloor);
        boolean fireTick = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
        manager.setClaim(sanctuaryKey, true);
        level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true, level.getServer());
        try {
            helper.assertTrue(manager.isSanctuary(sanctuaryKey), "test chunk is a sanctuary");
            helper.assertFalse(
                    manager.isSanctuary(ClaimKey.of(level, outsideFloor)),
                    "neighbour chunk stays wild"
            );

            level.setBlockAndUpdate(insideFloor, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(outsideFloor, Blocks.OAK_PLANKS.defaultBlockState());
            level.setBlockAndUpdate(insideFuel, Blocks.OAK_PLANKS.defaultBlockState());

            BlockState fire = Blocks.FIRE.defaultBlockState();
            helper.assertFalse(
                    fire.canSurvive(level, insideFuel.above()),
                    "fire cannot survive inside the sanctuary"
            );
            helper.assertTrue(
                    fire.canSurvive(level, outsideFire),
                    "fire still survives outside the sanctuary"
            );

            level.setBlockAndUpdate(insideFuel.above(), fire);
            helper.assertTrue(
                    level.getBlockState(insideFuel.above()).isAir(),
                    "fire placed inside the sanctuary is removed at once"
            );

            for (int attempt = 0; attempt < 400; attempt++) {
                level.setBlock(outsideFloor, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                if (!level.getBlockState(outsideFire).is(Blocks.FIRE)) {
                    level.setBlock(outsideFire, fire, 3);
                }
                level.getBlockState(outsideFire).tick(level, outsideFire, level.getRandom());
            }

            helper.assertTrue(
                    level.getBlockState(insideFuel).is(Blocks.OAK_PLANKS),
                    "sanctuary fuel next to burning wild fire never burns out"
            );
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 4; dy++) {
                        BlockPos probe = outsideFire.offset(dx, dy, dz);
                        if (!manager.isSanctuary(ClaimKey.of(level, probe))) {
                            continue;
                        }
                        helper.assertFalse(
                                level.getBlockState(probe).is(Blocks.FIRE),
                                "fire never spreads into the sanctuary at " + probe
                        );
                    }
                }
            }
        } finally {
            level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(fireTick, level.getServer());
            manager.setClaim(sanctuaryKey, false);
            for (BlockPos pos : new BlockPos[] {insideFloor, outsideFloor, insideFuel, outsideFire}) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "sanctuary_lightning", timeoutTicks = 600)
    public static void lightningNeverStrikesInsideSanctuary(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SanctuaryManager manager = SanctuaryManager.get(level);
        BlockPos anchor = level.getSharedSpawnPos().offset(-4_096, 0, 4_096);
        ChunkPos sanctuaryChunk = new ChunkPos(anchor);
        ChunkPos wildChunk = new ChunkPos(sanctuaryChunk.x + 1, sanctuaryChunk.z);
        level.getChunk(sanctuaryChunk.x, sanctuaryChunk.z);
        level.getChunk(wildChunk.x, wildChunk.z);

        int y = level.getSeaLevel() + 24;
        BlockPos inside = new BlockPos(sanctuaryChunk.getMaxBlockX(), y, sanctuaryChunk.getMinBlockZ() + 8);
        BlockPos outside = inside.east();

        ClaimKey sanctuaryKey = ClaimKey.of(level, inside);
        manager.setClaim(sanctuaryKey, true);
        try {
            helper.assertTrue(manager.isSanctuary(sanctuaryKey), "test chunk is a sanctuary");
            helper.assertFalse(
                    manager.isSanctuary(ClaimKey.of(level, outside)),
                    "neighbour chunk stays wild"
            );

            LightningBolt blocked = spawnBolt(level, inside);
            helper.assertFalse(level.addFreshEntity(blocked), "sanctuary rejects the lightning bolt");
            helper.assertFalse(blocked.isAddedToLevel(), "rejected bolt never joins the level");

            LightningBolt allowed = spawnBolt(level, outside);
            helper.assertTrue(level.addFreshEntity(allowed), "lightning still strikes outside the sanctuary");
            allowed.discard();
        } finally {
            manager.setClaim(sanctuaryKey, false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "sanctuary_interaction", timeoutTicks = 600)
    public static void taggedBlocksAreLockedForSanctuaryVisitors(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SanctuaryManager manager = SanctuaryManager.get(level);
        Block locked = BuiltInRegistries.BLOCK.getTag(BLOCKED_INTERACTION)
                .filter(holders -> holders.size() > 0)
                .map(holders -> holders.get(0).value())
                .orElse(null);
        helper.assertTrue(locked != null, "kingdoms:sanctuary_blocked_interaction resolves to at least one block");

        BlockPos anchor = level.getSharedSpawnPos().offset(4_096, 0, -4_096);
        ChunkPos sanctuaryChunk = new ChunkPos(anchor);
        level.getChunk(sanctuaryChunk.x, sanctuaryChunk.z);

        int y = level.getSeaLevel() + 24;
        BlockPos openGround = new BlockPos(sanctuaryChunk.getMinBlockX() + 2, y, sanctuaryChunk.getMinBlockZ() + 2);
        BlockPos insidePlot = openGround.offset(4, 0, 0);
        BlockPos plainTrapdoor = new BlockPos(sanctuaryChunk.getMinBlockX() + 8, y, sanctuaryChunk.getMinBlockZ() + 2);
        BlockPos plainDoor = plainTrapdoor.offset(2, 0, 0);

        ClaimKey sanctuaryKey = ClaimKey.of(level, openGround);
        MarketPlotManager plots = MarketPlotManager.get(level);
        ServerPlayer visitor = mockPlayer(level, "kingdoms-visitor", 0);
        ServerPlayer operator = mockPlayer(level, "kingdoms-operator", 4);
        MarketPlot plot = plots.create(level.dimension(), BoundingBox.fromCorners(insidePlot, insidePlot), 1L);
        manager.setClaim(sanctuaryKey, true);
        try {
            plot.setOwner(visitor.getUUID(), visitor.getGameProfile().getName());
            level.setBlockAndUpdate(openGround, locked.defaultBlockState());
            level.setBlockAndUpdate(insidePlot, locked.defaultBlockState());
            level.setBlockAndUpdate(plainTrapdoor, Blocks.OAK_TRAPDOOR.defaultBlockState());
            level.setBlockAndUpdate(plainDoor, Blocks.OAK_DOOR.defaultBlockState());
            level.setBlockAndUpdate(
                    plainDoor.above(),
                    Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
            );
            helper.assertTrue(
                    level.getBlockState(openGround).is(BLOCKED_INTERACTION),
                    "the locked block really carries the tag"
            );
            helper.assertTrue(level.getBlockState(plainTrapdoor).is(BlockTags.TRAPDOORS), "plain trapdoor stands");
            helper.assertTrue(level.getBlockState(plainDoor).is(BlockTags.DOORS), "plain door stands");

            AtomicReference<Component> notice = new AtomicReference<>();
            NOTICE_SINK.set(notice::set);
            helper.assertTrue(rightClick(visitor, openGround).isCanceled(), "visitor cannot use the locked block");
            helper.assertTrue(
                    notice.get() != null
                            && notice.get().getContents() instanceof TranslatableContents contents
                            && "kingdoms.protection.no_sanctuary_interact".equals(contents.getKey()),
                    "visitor is told why the block is locked"
            );

            helper.assertFalse(rightClick(operator, openGround).isCanceled(), "operators keep using the locked block");
            helper.assertFalse(
                    rightClick(visitor, insidePlot).isCanceled(),
                    "the plot owner keeps using the locked block inside the plot"
            );
            helper.assertFalse(
                    rightClick(visitor, plainTrapdoor).isCanceled(),
                    "plain trapdoors stay usable at spawn"
            );
            helper.assertFalse(rightClick(visitor, plainDoor).isCanceled(), "plain doors stay usable at spawn");
        } finally {
            NOTICE_SINK.set(null);
            manager.setClaim(sanctuaryKey, false);
            plots.remove(plot.id());
            for (BlockPos pos : new BlockPos[] {openGround, insidePlot, plainTrapdoor, plainDoor, plainDoor.above()}) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            visitor.discard();
            operator.discard();
        }
        helper.succeed();
    }

    private static PlayerInteractEvent.RightClickBlock rightClick(ServerPlayer player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        PlayerInteractEvent.RightClickBlock event =
                new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
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
                Consumer<Component> sink = NOTICE_SINK.get();
                if (sink != null) {
                    sink.accept(message);
                }
            }
        };
    }

    private static LightningBolt spawnBolt(ServerLevel level, BlockPos pos) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            throw new IllegalStateException("Lightning bolt could not be created");
        }
        bolt.moveTo(Vec3.atBottomCenterOf(pos));
        bolt.setVisualOnly(true);
        return bolt;
    }

    private SanctuaryGameTests() {
    }
}
