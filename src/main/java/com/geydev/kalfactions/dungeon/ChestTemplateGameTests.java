package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.DungeonChestBlockEntity;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChestTemplateGameTests {
    @GameTest(template = "empty", batch = "dungeon_templates", timeoutTicks = 600)
    public static void templatesCarryThePlanBetweenChests(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DungeonManager dungeons = DungeonManager.get(level);
        ChestTemplateManager templates = ChestTemplateManager.get(level);
        BlockPos anchor = blackZoneAnchor(level, 11);
        DungeonManager.DungeonView dungeon = createDungeon(helper, level, dungeons, anchor, "Тест шаблонов");
        BlockPos sourcePos = anchor.above();
        BlockPos targetPos = anchor.above(3);
        ServerPlayer operator = join(level, sourcePos, "kingdoms-template-operator", 4);
        ServerPlayer visitor = join(level, sourcePos, "kingdoms-template-visitor", 0);
        AtomicLong clock = new AtomicLong(7_000_000L);
        DungeonClock.override(clock::get);
        ChestTemplateService.overrideNotices((player, message, successful) -> {
        });
        try {
            DungeonChestBlockEntity source = placeChest(helper, level, sourcePos);
            DungeonChestBlockEntity target = placeChest(helper, level, targetPos);
            ItemStack sword = namedSword(level);
            source.planContainer().setItem(0, sword);
            source.setEntry(0, 50, 2, 4);
            source.planContainer().setItem(5, new ItemStack(Items.DIRT));
            source.setEntry(5, 100, 3, 3);
            source.setCooldownHours(12);

            UUID replayed = UUID.randomUUID();
            send(operator, DungeonPayloads.C2SChestTemplateAction.SAVE, sourcePos, replayed, null, "Клад", true);
            ChestTemplate stored = templates.byName("Клад").orElse(null);
            helper.assertTrue(stored != null, "the operator saved the plan as a template");
            helper.assertTrue(stored.filledSlots() == 2, "the template kept both plan entries");

            templates.delete(stored.id());
            send(operator, DungeonPayloads.C2SChestTemplateAction.SAVE, sourcePos, replayed, null, "Клад", true);
            helper.assertTrue(templates.byName("Клад").isEmpty(), "a replayed packet never saves the template twice");

            send(operator, DungeonPayloads.C2SChestTemplateAction.SAVE, sourcePos,
                    UUID.randomUUID(), null, "Клад", true);
            stored = templates.byName("Клад").orElseThrow();

            send(visitor, DungeonPayloads.C2SChestTemplateAction.SAVE, sourcePos,
                    UUID.randomUUID(), null, "Чужой", true);
            helper.assertTrue(templates.byName("Чужой").isEmpty(), "a non-operator cannot save a template");

            send(operator, DungeonPayloads.C2SChestTemplateAction.APPLY, targetPos,
                    UUID.randomUUID(), stored.id(), "", true);
            helper.assertTrue(
                    ItemStack.matches(target.planItem(0), source.planItem(0)),
                    "the applied plan carries the enchanted item with its components"
            );
            helper.assertTrue(target.planItem(5).is(Items.DIRT), "the applied plan carries every slot");
            helper.assertTrue(
                    target.chanceAt(0) == 50 && target.minAt(0) == 2 && target.maxAt(0) == 4,
                    "the applied plan carries chances and count ranges"
            );
            helper.assertTrue(target.configuredCooldownHours() == 12, "the template interval was applied");
            helper.assertTrue(countOf(target, Items.DIRT) == 3, "applying refills the chest at once");
            helper.assertTrue(target.remainingMillis() > 0L, "the cooldown restarted after the refill");

            send(visitor, DungeonPayloads.C2SChestTemplateAction.DELETE, sourcePos,
                    UUID.randomUUID(), stored.id(), "", true);
            helper.assertTrue(templates.byId(stored.id()).isPresent(), "a non-operator cannot delete a template");

            ChestTemplateManager reloaded = ChestTemplateManager.load(
                    templates.save(new CompoundTag(), level.registryAccess()),
                    level.registryAccess()
            );
            ChestTemplate survived = reloaded.byName("Клад").orElse(null);
            helper.assertTrue(survived != null, "templates survive a server restart");
            helper.assertTrue(
                    ItemStack.matches(survived.entry(0).stack(), source.planItem(0)),
                    "a restarted server still holds the item components"
            );
            helper.assertTrue(survived.cooldownHours() == 12, "a restarted server still holds the interval");
        } finally {
            DungeonClock.reset();
            ChestTemplateService.resetNotices();
            clearTemplates(templates);
            level.setBlockAndUpdate(sourcePos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(targetPos, Blocks.AIR.defaultBlockState());
            leave(operator);
            leave(visitor);
            dungeons.remove(dungeon.id());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dungeon_templates", timeoutTicks = 600)
    public static void templateReachesEveryChestOfTheDungeon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DungeonManager dungeons = DungeonManager.get(level);
        ChestTemplateManager templates = ChestTemplateManager.get(level);
        BlockPos anchor = blackZoneAnchor(level, 12);
        clearTemplates(templates);
        DungeonManager.DungeonView dungeon = createDungeon(helper, level, dungeons, anchor, "Тест массового");
        BlockPos firstPos = anchor.above();
        BlockPos secondPos = anchor.above(4);
        ServerPlayer operator = join(level, firstPos, "kingdoms-template-mass-operator", 4);
        ChestTemplateService.overrideNotices((player, message, successful) -> {
        });
        DungeonChestBlockEntity first = placeChest(helper, level, firstPos);
        DungeonChestBlockEntity second = placeChest(helper, level, secondPos);
        ChestTemplate template = new ChestTemplate(
                UUID.randomUUID(),
                "Массовый",
                "Оператор",
                DungeonClock.now(),
                6,
                List.of(new ChestTemplate.Entry(new ItemStack(Items.GOLD_INGOT), 100, 2, 2))
        );
        helper.assertTrue(
                templates.put(
                        template,
                        true,
                        ChestTemplateManager.Limits.fromConfig(),
                        level.registryAccess()
                ).successful(),
                "the template was stored"
        );
        helper.startSequence()
                .thenExecute(() -> send(
                        operator,
                        DungeonPayloads.C2SChestTemplateAction.APPLY_ALL,
                        firstPos,
                        UUID.randomUUID(),
                        template.id(),
                        "",
                        true
                ))
                .thenIdle(10)
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(
                                first.planItem(0).is(Items.GOLD_INGOT) && second.planItem(0).is(Items.GOLD_INGOT),
                                "every chest of the dungeon took the template plan"
                        );
                        helper.assertTrue(
                                first.configuredCooldownHours() == 6 && second.configuredCooldownHours() == 6,
                                "every chest of the dungeon took the template interval"
                        );
                        helper.assertTrue(
                                countOf(first, Items.GOLD_INGOT) == 2 && countOf(second, Items.GOLD_INGOT) == 2,
                                "every chest of the dungeon was refilled"
                        );
                    } finally {
                        ChestTemplateApplyTicker.clear();
                        ChestTemplateService.resetNotices();
                        clearTemplates(templates);
                        level.setBlockAndUpdate(firstPos, Blocks.AIR.defaultBlockState());
                        level.setBlockAndUpdate(secondPos, Blocks.AIR.defaultBlockState());
                        leave(operator);
                        dungeons.remove(dungeon.id());
                    }
                })
                .thenSucceed();
    }

    private static void send(
            ServerPlayer player,
            int action,
            BlockPos pos,
            UUID requestId,
            UUID templateId,
            String name,
            boolean applyCooldown
    ) {
        ChestTemplateService.clearRateLimit(player.getUUID());
        ChestTemplateService.handle(player, new DungeonPayloads.C2SChestTemplateAction(
                pos,
                requestId,
                action,
                templateId,
                name,
                applyCooldown,
                true
        ));
    }

    private static DungeonChestBlockEntity placeChest(GameTestHelper helper, ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, ModBlocks.DUNGEON_CHEST.get().defaultBlockState());
        helper.assertTrue(
                level.getBlockEntity(pos) instanceof DungeonChestBlockEntity,
                "the dungeon chest has its block entity"
        );
        return (DungeonChestBlockEntity) level.getBlockEntity(pos);
    }

    private static ItemStack namedSword(ServerLevel level) {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Меч короля"));
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS),
                4
        );
        stack.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        return stack;
    }

    private static ServerPlayer join(ServerLevel level, BlockPos pos, String name, int permissions) {
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
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        return player;
    }

    private static void leave(ServerPlayer player) {
        if (player != null) {
            player.discard();
        }
    }

    private static int countOf(DungeonChestBlockEntity chest, Item item) {
        int total = 0;
        for (int slot = 0; slot < DungeonChestBlockEntity.SIZE; slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void clearTemplates(ChestTemplateManager manager) {
        for (ChestTemplate template : manager.all()) {
            manager.delete(template.id());
        }
    }

    private static DungeonManager.DungeonView createDungeon(
            GameTestHelper helper,
            ServerLevel level,
            DungeonManager manager,
            BlockPos anchor,
            String name
    ) {
        manager.byName(name).ifPresent(existing -> manager.remove(existing.id()));
        manager.dungeonAt(ClaimKey.of(level, anchor)).ifPresent(existing -> manager.remove(existing.id()));
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

    private ChestTemplateGameTests() {
    }
}
