package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.KeyHolderBlock;
import com.geydev.kalfactions.block.KeyHolderBlockEntity;
import com.geydev.kalfactions.block.KeyHolderMode;
import com.geydev.kalfactions.keyholder.BossKeyLootModifier;
import com.geydev.kalfactions.keyholder.KeyHolderNetwork;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.registry.ModItems;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KeyHolderGameTests {
    @GameTest(template = "empty", batch = "key_holder", timeoutTicks = 120)
    public static void bossKeyPowersTheHolderForTheConfiguredPulse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        placeHolder(level, pos);
        configure(level, pos, KeyHolderMode.PULSE, 40, true);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack key = new ItemStack(ModItems.BOSS_KEY.get(), 2);
        use(level, pos, player, key);

        BlockState state = level.getBlockState(pos);
        helper.assertTrue(state.getValue(KeyHolderBlock.POWERED), "the holder powers up");
        helper.assertValueEqual(state.getSignal(level, pos, Direction.NORTH), 15, "weak signal");
        helper.assertValueEqual(state.getDirectSignal(level, pos, Direction.UP), 15, "direct signal");
        helper.assertValueEqual(key.getCount(), 1, "key was consumed");

        helper.runAfterDelay(39, () -> helper.assertValueEqual(
                level.getBlockState(pos).getSignal(level, pos, Direction.NORTH),
                15,
                "signal at tick 39"
        ));
        helper.runAfterDelay(40, () -> {
            BlockState ended = level.getBlockState(pos);
            helper.assertFalse(ended.getValue(KeyHolderBlock.POWERED), "the pulse ended");
            helper.assertValueEqual(ended.getSignal(level, pos, Direction.NORTH), 0, "signal at tick 40");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "key_holder", timeoutTicks = 60)
    public static void shortPulseUsesTheConfiguredDuration(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        placeHolder(level, pos);
        configure(level, pos, KeyHolderMode.PULSE, KeyHolderBlockEntity.MIN_PULSE_TICKS, true);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        use(level, pos, player, new ItemStack(ModItems.BOSS_KEY.get()));

        helper.assertTrue(level.getBlockState(pos).getValue(KeyHolderBlock.POWERED), "short pulse started");
        helper.runAfterDelay(1, () -> helper.assertTrue(
                level.getBlockState(pos).getValue(KeyHolderBlock.POWERED),
                "short pulse at tick 1"
        ));
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(
                    level.getBlockState(pos).getValue(KeyHolderBlock.POWERED),
                    "short pulse at tick 2"
            );
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "key_holder", timeoutTicks = 120)
    public static void toggleModeStaysPoweredUntilTheNextKeyClick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        placeHolder(level, pos);
        configure(level, pos, KeyHolderMode.TOGGLE, 40, true);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack key = new ItemStack(ModItems.BOSS_KEY.get(), 4);
        use(level, pos, player, key);
        helper.assertTrue(level.getBlockState(pos).getValue(KeyHolderBlock.POWERED), "toggle turned on");
        helper.assertValueEqual(key.getCount(), 3, "toggle consumed one key");

        helper.runAfterDelay(60, () -> {
            helper.assertTrue(
                    level.getBlockState(pos).getValue(KeyHolderBlock.POWERED),
                    "toggle ignores the pulse duration"
            );
            use(level, pos, player, key);
            helper.assertFalse(
                    level.getBlockState(pos).getValue(KeyHolderBlock.POWERED),
                    "the second click turned it off"
            );
            helper.assertValueEqual(key.getCount(), 3, "turning off keeps the key");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "key_holder")
    public static void holdersWithoutKeyConsumptionKeepTheKey(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        placeHolder(level, pos);
        configure(level, pos, KeyHolderMode.PULSE, 40, false);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack key = new ItemStack(ModItems.BOSS_KEY.get(), 2);
        use(level, pos, player, key);
        helper.assertTrue(level.getBlockState(pos).getValue(KeyHolderBlock.POWERED), "the holder powers up");
        helper.assertValueEqual(key.getCount(), 2, "key was kept");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "key_holder")
    public static void withoutABossKeyTheHolderStaysUnpowered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        placeHolder(level, pos);
        configure(level, pos, KeyHolderMode.PULSE, 40, true);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        ItemStack wrongItem = new ItemStack(Items.DIAMOND, 3);
        use(level, pos, player, wrongItem);
        helper.assertValueEqual(wrongItem.getCount(), 3, "wrong item count");
        helper.assertFalse(
                level.getBlockState(pos).getValue(KeyHolderBlock.POWERED),
                "a diamond does not power the holder"
        );

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        level.getBlockState(pos).useWithoutItem(
                level,
                player,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
        );
        helper.assertFalse(
                level.getBlockState(pos).getValue(KeyHolderBlock.POWERED),
                "an empty hand does not power the holder"
        );
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "key_holder")
    public static void settingsPacketsRequirePermissionsAndValidRanges(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        placeHolder(level, pos);
        configure(level, pos, KeyHolderMode.PULSE, 40, true);
        ServerPlayer visitor = mockPlayer(level, "kingdoms-holder-visitor", 0, pos);
        ServerPlayer operator = mockPlayer(level, "kingdoms-holder-operator", 4, pos);
        ServerPlayer ranger = mockPlayer(level, "kingdoms-holder-ranger", 4, pos);
        try {
            helper.assertFalse(
                    KeyHolderNetwork.applySettings(visitor, pos, "toggle", 200, false),
                    "a player without permissions may not edit the holder"
            );
            assertSettings(helper, level, pos, KeyHolderMode.PULSE, 40, true, "after the visitor packet");

            helper.assertFalse(
                    KeyHolderNetwork.applySettings(operator, pos, "toggle", KeyHolderBlockEntity.MIN_PULSE_TICKS - 1,
                            false),
                    "pulse ticks below the minimum are rejected"
            );
            helper.assertFalse(
                    KeyHolderNetwork.applySettings(operator, pos, "toggle", KeyHolderBlockEntity.MAX_PULSE_TICKS + 1,
                            false),
                    "pulse ticks above the maximum are rejected"
            );
            helper.assertFalse(
                    KeyHolderNetwork.applySettings(operator, pos, "sabotage", 200, false),
                    "an unknown mode is rejected"
            );
            assertSettings(helper, level, pos, KeyHolderMode.PULSE, 40, true, "after the invalid packets");

            ranger.setPos(pos.getX() + 40.5D, pos.getY(), pos.getZ() + 0.5D);
            helper.assertFalse(
                    KeyHolderNetwork.applySettings(ranger, pos, "toggle", 200, false),
                    "a distant operator may not edit the holder"
            );
            assertSettings(helper, level, pos, KeyHolderMode.PULSE, 40, true, "after the distant packet");

            helper.assertTrue(
                    KeyHolderNetwork.applySettings(
                            operator, pos, "toggle", KeyHolderBlockEntity.MAX_PULSE_TICKS, false),
                    "a nearby operator saves a duration up to the maximum"
            );
            assertSettings(
                    helper,
                    level,
                    pos,
                    KeyHolderMode.TOGGLE,
                    KeyHolderBlockEntity.MAX_PULSE_TICKS,
                    false,
                    "after the valid packet"
            );
        } finally {
            visitor.discard();
            operator.discard();
            ranger.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "key_holder")
    public static void settingsPacketsAreRateLimited(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        placeHolder(level, pos);
        configure(level, pos, KeyHolderMode.PULSE, 40, true);
        ServerPlayer operator = mockPlayer(level, "kingdoms-holder-spammer", 4, pos);
        try {
            helper.assertTrue(
                    KeyHolderNetwork.applySettings(operator, pos, "toggle", 200, false),
                    "the first packet is accepted"
            );
            helper.assertFalse(
                    KeyHolderNetwork.applySettings(operator, pos, "pulse", 100, true),
                    "the immediate second packet is rate limited"
            );
            assertSettings(helper, level, pos, KeyHolderMode.TOGGLE, 200, false, "after the rate limited packet");
        } finally {
            KeyHolderNetwork.clearRateLimit(operator.getUUID());
            operator.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "key_holder")
    public static void savingSettingsClearsAnActiveSignal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        placeHolder(level, pos);
        configure(level, pos, KeyHolderMode.TOGGLE, 40, true);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        use(level, pos, player, new ItemStack(ModItems.BOSS_KEY.get()));
        helper.assertTrue(level.getBlockState(pos).getValue(KeyHolderBlock.POWERED), "toggle turned on");

        ServerPlayer operator = mockPlayer(level, "kingdoms-holder-editor", 4, pos);
        try {
            helper.assertTrue(
                    KeyHolderNetwork.applySettings(operator, pos, "pulse", 40, true),
                    "the operator saves settings"
            );
        } finally {
            KeyHolderNetwork.clearRateLimit(operator.getUUID());
            operator.discard();
        }
        helper.assertFalse(
                level.getBlockState(pos).getValue(KeyHolderBlock.POWERED),
                "saving settings cleared the signal"
        );
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "key_holder")
    public static void redstoneWireFollowsTheHolderAndBreakingStopsPower(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos holderPos = helper.absolutePos(new BlockPos(0, 2, 0));
        BlockPos wirePos = helper.absolutePos(new BlockPos(1, 2, 0));
        level.setBlock(holderPos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(wirePos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        placeHolder(level, holderPos);
        configure(level, holderPos, KeyHolderMode.TOGGLE, 40, true);
        level.setBlock(wirePos, Blocks.REDSTONE_WIRE.defaultBlockState(), Block.UPDATE_ALL);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        use(level, holderPos, player, new ItemStack(ModItems.BOSS_KEY.get()));

        helper.runAfterDelay(2, () -> {
            helper.assertValueEqual(
                    level.getBlockState(wirePos).getValue(RedStoneWireBlock.POWER),
                    15,
                    "powered wire"
            );
            level.destroyBlock(holderPos, false);
        });
        helper.runAfterDelay(4, () -> {
            helper.assertValueEqual(
                    level.getBlockState(wirePos).getValue(RedStoneWireBlock.POWER),
                    0,
                    "wire after breaking"
            );
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "key_holder")
    public static void facingRotatesAndLootDropsTheHolder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockState state = ModBlocks.KEY_HOLDER.get().defaultBlockState();
        helper.assertValueEqual(state.getValue(KeyHolderBlock.FACING), Direction.NORTH, "default facing");
        helper.assertFalse(state.getValue(KeyHolderBlock.POWERED), "default powered");
        helper.assertValueEqual(
                state.rotate(Rotation.CLOCKWISE_90).getValue(KeyHolderBlock.FACING),
                Direction.EAST,
                "rotated facing"
        );
        level.setBlock(pos, state, Block.UPDATE_ALL);
        List<ItemStack> drops = Block.getDrops(state, level, pos, null);
        helper.assertValueEqual(drops.size(), 1, "loot stack count");
        helper.assertTrue(drops.getFirst().is(ModItems.KEY_HOLDER.get()), "loot item was " + drops);
        helper.assertValueEqual(drops.getFirst().getCount(), 1, "loot item count");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "key_holder")
    public static void bossKeyLootModifierIsRegisteredAndOptIn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RegistryOps<JsonElement> ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);

        IGlobalLootModifier defaulted = IGlobalLootModifier.DIRECT_CODEC
                .parse(ops, JsonParser.parseString("{\"type\":\"kingdoms:boss_key_drop\",\"conditions\":[]}"))
                .getOrThrow();
        helper.assertTrue(defaulted instanceof BossKeyLootModifier, "the modifier type is registered");
        helper.assertValueEqual(((BossKeyLootModifier) defaulted).chance(), 1.0F, "default chance");

        IGlobalLootModifier configured = IGlobalLootModifier.DIRECT_CODEC
                .parse(ops, JsonParser.parseString(
                        "{\"type\":\"kingdoms:boss_key_drop\",\"chance\":0.25,\"conditions\":[]}"))
                .getOrThrow();
        helper.assertValueEqual(((BossKeyLootModifier) configured).chance(), 0.25F, "configured chance");

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 1, 0));
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, zombie)
                .withParameter(LootContextParams.ORIGIN, zombie.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                .create(LootContextParamSets.ENTITY);
        LootContext context = new LootContext.Builder(params).create(Optional.empty());
        helper.assertTrue(
                defaulted.apply(new ObjectArrayList<>(), context).isEmpty(),
                "entity types outside kingdoms:boss_key_droppers drop no boss key"
        );
        helper.succeed();
    }

    private static void placeHolder(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, ModBlocks.KEY_HOLDER.get().defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void use(ServerLevel level, BlockPos pos, Player player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        level.getBlockState(pos).useItemOn(
                stack,
                level,
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
        );
    }

    private static void configure(
            ServerLevel level,
            BlockPos pos,
            KeyHolderMode mode,
            int pulseTicks,
            boolean consumeKey
    ) {
        if (!(level.getBlockEntity(pos) instanceof KeyHolderBlockEntity holder)) {
            throw new IllegalStateException("Missing key holder block entity at " + pos);
        }
        holder.configure(mode, pulseTicks, consumeKey);
    }

    private static void assertSettings(
            GameTestHelper helper,
            ServerLevel level,
            BlockPos pos,
            KeyHolderMode mode,
            int pulseTicks,
            boolean consumeKey,
            String what
    ) {
        if (!(level.getBlockEntity(pos) instanceof KeyHolderBlockEntity holder)) {
            throw new IllegalStateException("Missing key holder block entity at " + pos);
        }
        helper.assertValueEqual(holder.mode(), mode, "mode " + what);
        helper.assertValueEqual(holder.pulseTicks(), pulseTicks, "pulse ticks " + what);
        helper.assertValueEqual(holder.consumeKey(), consumeKey, "key consumption " + what);
    }

    private static ServerPlayer mockPlayer(ServerLevel level, String name, int permissions, BlockPos pos) {
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(
                level.getServer(),
                level,
                cookie.gameProfile(),
                cookie.clientInformation()
        ) {
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

    private KeyHolderGameTests() {
    }
}
