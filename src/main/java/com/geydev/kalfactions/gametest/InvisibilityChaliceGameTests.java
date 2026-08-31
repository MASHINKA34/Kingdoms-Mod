package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.InvisibilityChaliceBlockEntity;
import com.geydev.kalfactions.invisibility.InvisibilityNetwork;
import com.geydev.kalfactions.invisibility.TrueInvisibility;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.registry.ModEffects;
import com.geydev.kalfactions.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InvisibilityChaliceGameTests {
    @GameTest(template = "empty", batch = "invisibility_chalice")
    public static void drinkingGrantsBothEffectsForTheConfiguredDuration(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        placeChalice(level, pos, 30);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.assertFalse(TrueInvisibility.isActive(player), "no invisibility before the click");
        use(level, pos, player);

        MobEffectInstance hidden = player.getEffect(ModEffects.TRUE_INVISIBILITY);
        MobEffectInstance vanilla = player.getEffect(MobEffects.INVISIBILITY);
        helper.assertTrue(hidden != null, "true invisibility was granted");
        helper.assertTrue(vanilla != null, "vanilla invisibility was granted");
        helper.assertValueEqual(hidden.getDuration(), 30 * 20, "true invisibility duration");
        helper.assertValueEqual(vanilla.getDuration(), 30 * 20, "vanilla invisibility duration");
        for (MobEffectInstance instance : List.of(hidden, vanilla)) {
            helper.assertFalse(instance.isAmbient(), "ambient is off on " + instance.getDescriptionId());
            helper.assertFalse(instance.isVisible(), "particles are off on " + instance.getDescriptionId());
            helper.assertTrue(instance.showIcon(), "the hud icon is on for " + instance.getDescriptionId());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "invisibility_chalice")
    public static void breakingABlockEndsTheInvisibility(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos chalicePos = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos stonePos = helper.absolutePos(new BlockPos(2, 1, 0));
        placeChalice(level, chalicePos, 60);
        level.setBlock(stonePos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        use(level, chalicePos, player);
        helper.assertTrue(TrueInvisibility.isActive(player), "the buff is active before the break");

        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(
                level,
                stonePos,
                level.getBlockState(stonePos),
                player
        );
        NeoForge.EVENT_BUS.post(event);

        helper.assertFalse(event.isCanceled(), "the break itself is not cancelled");
        assertNoInvisibility(helper, player, "after breaking a block");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "invisibility_chalice")
    public static void attackingOrPlacingEndsTheInvisibility(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos chalicePos = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos placedPos = helper.absolutePos(new BlockPos(2, 1, 0));
        placeChalice(level, chalicePos, 60);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));

        use(level, chalicePos, player);
        helper.assertTrue(TrueInvisibility.isActive(player), "the buff is active before the attack");
        NeoForge.EVENT_BUS.post(new AttackEntityEvent(player, target));
        assertNoInvisibility(helper, player, "after attacking an entity");

        use(level, chalicePos, player);
        helper.assertTrue(TrueInvisibility.isActive(player), "the buff is active before the placement");
        level.setBlock(placedPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(
                BlockSnapshot.create(level.dimension(), level, placedPos),
                Blocks.STONE.defaultBlockState(),
                player
        ));
        assertNoInvisibility(helper, player, "after placing a block");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "invisibility_chalice")
    public static void aPotionOfInvisibilityIsNotStrippedByTheseActions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos stonePos = helper.absolutePos(new BlockPos(2, 1, 0));
        level.setBlock(stonePos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 400, 0, false, true, true));

        NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(
                level,
                stonePos,
                level.getBlockState(stonePos),
                player
        ));

        helper.assertTrue(
                player.hasEffect(MobEffects.INVISIBILITY),
                "a plain invisibility potion survives breaking a block"
        );
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "invisibility_chalice")
    public static void settingsPacketsNeedAnOperatorAValidRangeAndACooldown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        placeChalice(level, pos, TrueInvisibility.DEFAULT_SECONDS);
        ServerPlayer visitor = mockPlayer(level, "kingdoms-chalice-visitor", 0, pos);
        ServerPlayer operator = mockPlayer(level, "kingdoms-chalice-operator", 4, pos);
        try {
            helper.assertFalse(
                    InvisibilityNetwork.applySettings(visitor, pos, 120),
                    "a player without permissions is refused"
            );
            helper.assertFalse(
                    InvisibilityNetwork.applySettings(operator, pos, TrueInvisibility.MIN_SECONDS - 1),
                    "a duration below the range is refused"
            );
            helper.assertFalse(
                    InvisibilityNetwork.applySettings(operator, pos, TrueInvisibility.MAX_SECONDS + 1),
                    "a duration above the range is refused"
            );
            helper.assertValueEqual(
                    durationOf(level, pos),
                    TrueInvisibility.DEFAULT_SECONDS,
                    "the duration after the refused packets"
            );

            helper.assertTrue(
                    InvisibilityNetwork.applySettings(operator, pos, 120),
                    "the operator saves a duration inside the range"
            );
            helper.assertValueEqual(durationOf(level, pos), 120, "the saved duration");

            helper.assertFalse(
                    InvisibilityNetwork.applySettings(operator, pos, 300),
                    "the immediate second packet is rate limited"
            );
            helper.assertValueEqual(durationOf(level, pos), 120, "the duration after the rate limited packet");

            ServerPlayer distant = mockPlayer(level, "kingdoms-chalice-sniper", 4, pos.offset(0, 0, 64));
            try {
                helper.assertFalse(
                        InvisibilityNetwork.applySettings(distant, pos, 300),
                        "an operator too far away is refused"
                );
            } finally {
                InvisibilityNetwork.clearRateLimit(distant.getUUID());
                distant.discard();
            }
        } finally {
            InvisibilityNetwork.clearRateLimit(visitor.getUUID());
            InvisibilityNetwork.clearRateLimit(operator.getUUID());
            visitor.discard();
            operator.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "invisibility_chalice")
    public static void theChaliceKeepsItsDurationAndDropsItself(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        placeChalice(level, pos, 240);
        HolderLookup.Provider registries = level.registryAccess();

        InvisibilityChaliceBlockEntity chalice = chaliceAt(level, pos);
        CompoundTag saved = chalice.saveWithFullMetadata(registries);
        InvisibilityChaliceBlockEntity restored = new InvisibilityChaliceBlockEntity(pos, level.getBlockState(pos));
        restored.loadWithComponents(saved, registries);
        helper.assertValueEqual(restored.durationSeconds(), 240, "the duration survives a save and load");

        chalice.configure(TrueInvisibility.MAX_SECONDS + 500);
        helper.assertValueEqual(
                chalice.durationSeconds(),
                TrueInvisibility.MAX_SECONDS,
                "an out of range duration is clamped"
        );

        BlockState state = level.getBlockState(pos);
        helper.assertFalse(state.isCollisionShapeFullBlock(level, pos), "the chalice is not a full cube");
        helper.assertTrue(
                Shapes.joinIsNotEmpty(Shapes.block(), state.getShape(level, pos), BooleanOp.ONLY_FIRST),
                "the chalice shape leaves part of the block cube open"
        );

        List<ItemStack> drops = Block.getDrops(state, level, pos, chalice);
        helper.assertValueEqual(drops.size(), 1, "loot stack count");
        helper.assertTrue(drops.getFirst().is(ModItems.INVISIBILITY_CHALICE.get()), "loot item was " + drops);
        helper.succeed();
    }

    private static void assertNoInvisibility(GameTestHelper helper, Player player, String what) {
        helper.assertFalse(player.hasEffect(ModEffects.TRUE_INVISIBILITY), "true invisibility " + what);
        helper.assertFalse(player.hasEffect(MobEffects.INVISIBILITY), "vanilla invisibility " + what);
    }

    private static void placeChalice(ServerLevel level, BlockPos pos, int durationSeconds) {
        level.setBlock(pos, ModBlocks.INVISIBILITY_CHALICE.get().defaultBlockState(), Block.UPDATE_ALL);
        chaliceAt(level, pos).configure(durationSeconds);
    }

    private static InvisibilityChaliceBlockEntity chaliceAt(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof InvisibilityChaliceBlockEntity chalice) {
            return chalice;
        }
        throw new IllegalStateException("Missing invisibility chalice block entity at " + pos);
    }

    private static int durationOf(ServerLevel level, BlockPos pos) {
        return chaliceAt(level, pos).durationSeconds();
    }

    private static void use(ServerLevel level, BlockPos pos, Player player) {
        level.getBlockState(pos).useWithoutItem(
                level,
                player,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
        );
    }

    private static ServerPlayer mockPlayer(ServerLevel level, String name, int permissions, BlockPos pos) {
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player =
                new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
                    @Override
                    protected int getPermissionLevel() {
                        return permissions;
                    }

                    @Override
                    public void displayClientMessage(Component message, boolean actionBar) {
                    }
                };
        player.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        return player;
    }

    private InvisibilityChaliceGameTests() {
    }
}
