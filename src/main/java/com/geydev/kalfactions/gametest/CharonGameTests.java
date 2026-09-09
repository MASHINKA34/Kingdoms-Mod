package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.charon.CharonGhosts;
import com.geydev.kalfactions.charon.CharonManager;
import com.geydev.kalfactions.charon.CharonService;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.entity.SellerTraderEntity;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.InfluenceSourceHandler;
import com.geydev.kalfactions.faction.InfluenceType;
import com.geydev.kalfactions.faction.KillRewardLedger;
import com.geydev.kalfactions.outpost.trader.SellerTraderRole;
import com.geydev.kalfactions.outpost.trader.TradeSessionManager;
import com.geydev.kalfactions.outpost.trader.TraderService;
import com.geydev.kalfactions.outpost.trader.TraderWorldData;
import com.geydev.kalfactions.pvp.WarTrophyDrops;
import com.geydev.kalfactions.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CharonGameTests {
    private static final long LONG_AGO_MILLIS = 600_000L;
    private static final BlockPos START = new BlockPos(1, 2, 1);
    private static final BlockPos DEATH = new BlockPos(5, 2, 5);

    @GameTest(template = "empty", batch = "charon")
    public static void theTokenRefusesBeforeTheDeathDelay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(START);
        BlockPos death = buildFloor(level, helper.absolutePos(DEATH));
        ServerPlayer player = RegressionPlayers.create(level, start, 0).player();
        CharonManager manager = CharonManager.get(level.getServer());
        try {
            manager.recordDeath(player.getUUID(), GlobalPos.of(level.dimension(), death), System.currentTimeMillis());

            InteractionResultHolder<ItemStack> result = useToken(level, player);

            helper.assertFalse(result.getResult().consumesAction(), "the token refuses right after death");
            helper.assertFalse(CharonService.isGhost(player), "no ghost form before the delay");
            helper.assertValueEqual(cooldownUntil(manager, player), 0L, "cooldown after a refusal");
            helper.assertValueEqual(player.blockPosition(), start, "the player stays in place");
        } finally {
            cleanup(manager, player);
            clearFloor(level, death);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "charon")
    public static void usingTheTokenStartsTheCooldownAndLeadsToTheDeath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(START);
        BlockPos death = buildFloor(level, helper.absolutePos(DEATH));
        ServerPlayer player = RegressionPlayers.create(level, start, 0).player();
        CharonManager manager = CharonManager.get(level.getServer());
        long before = System.currentTimeMillis();
        try {
            player.setHealth(14.0F);
            manager.recordDeath(player.getUUID(), GlobalPos.of(level.dimension(), death), before - LONG_AGO_MILLIS);

            InteractionResultHolder<ItemStack> result = useToken(level, player);

            helper.assertTrue(result.getResult().consumesAction(), "the token works after the delay");
            helper.assertTrue(CharonService.isGhost(player), "the player became a ghost");
            helper.assertTrue(CharonGhosts.isGhost(player.getUUID()), "the ghost set knows the player");
            long cooldown = cooldownUntil(manager, player) - before;
            long expected = ModConfigSpec.CHARON_COOLDOWN_MINUTES.getAsInt() * 60_000L;
            helper.assertTrue(
                    cooldown >= expected && cooldown <= expected + 60_000L,
                    "the cooldown was recorded, got " + cooldown
            );
            helper.assertTrue(
                    player.blockPosition().closerThan(death, 4.0D),
                    "the ghost stands at the death position, was " + player.blockPosition()
            );
            int ghostHealth = ModConfigSpec.CHARON_GHOST_HEALTH.getAsInt();
            helper.assertValueEqual((int) player.getMaxHealth(), ghostHealth, "ghost max health");
            helper.assertValueEqual((int) player.getHealth(), ghostHealth, "ghost health");
            helper.assertTrue(player.isInvisible(), "the ghost is invisible");
            CharonManager.Ghost ghost = manager.ghost(player.getUUID()).orElseThrow();
            helper.assertValueEqual(ghost.returnPos().pos(), start, "return position");
            helper.assertValueEqual((int) ghost.savedHealth(), 14, "saved health");
            helper.assertValueEqual(
                    player.getInventory().countItem(ModItems.CHARON_TOKEN.get()), 1, "the token is kept"
            );
        } finally {
            cleanup(manager, player);
            clearFloor(level, death);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "charon")
    public static void aGhostIgnoresMobsAndFallsButNotPlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos start = helper.absolutePos(START);
        BlockPos death = buildFloor(level, helper.absolutePos(DEATH));
        ServerPlayer ghost = RegressionPlayers.create(level, start, 0).player();
        ServerPlayer attacker = RegressionPlayers.create(level, start, 0).player();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        CharonManager manager = CharonManager.get(server);
        boolean pvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(true);
            becomeGhost(level, manager, ghost, death);
            passSpawnProtection(ghost);
            float ghostHealth = ghost.getHealth();

            zombie.setTarget(ghost);
            helper.assertTrue(zombie.getTarget() == null, "a mob cannot target the ghost");

            helper.assertFalse(ghost.hurt(level.damageSources().mobAttack(zombie), 2.0F), "a mob attack is ignored");
            helper.assertFalse(ghost.hurt(level.damageSources().fall(), 2.0F), "fall damage is ignored");
            helper.assertValueEqual(ghost.getHealth(), ghostHealth, "health after ignored damage");

            helper.assertTrue(ghost.hurt(level.damageSources().playerAttack(attacker), 2.0F), "a player attack lands");
            helper.assertTrue(ghost.getHealth() < ghostHealth, "health after a player attack");
        } finally {
            server.setPvpAllowed(pvp);
            cleanup(manager, ghost);
            cleanup(manager, attacker);
            zombie.discard();
            clearFloor(level, death);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "charon", timeoutTicks = 200)
    public static void theTimerReturnsTheGhostAndRestoresHealth(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(START);
        BlockPos death = buildFloor(level, helper.absolutePos(DEATH));
        ServerPlayer player = RegressionPlayers.create(level, start, 0).player();
        CharonManager manager = CharonManager.get(level.getServer());
        int originalSeconds = ModConfigSpec.CHARON_GHOST_SECONDS.getAsInt();
        ModConfigSpec.CHARON_GHOST_SECONDS.set(1);
        try {
            player.setHealth(14.0F);
            becomeGhost(level, manager, player, death);
        } catch (RuntimeException exception) {
            ModConfigSpec.CHARON_GHOST_SECONDS.set(originalSeconds);
            cleanup(manager, player);
            clearFloor(level, death);
            throw exception;
        }
        helper.runAfterDelay(30, () -> {
            try {
                CharonService.tick(player);

                helper.assertFalse(CharonService.isGhost(player), "the ghost form ended");
                helper.assertFalse(CharonGhosts.isGhost(player.getUUID()), "the ghost set forgot the player");
                helper.assertTrue(
                        player.blockPosition().closerThan(start, 3.0D),
                        "the player returned, was " + player.blockPosition()
                );
                helper.assertValueEqual((int) player.getMaxHealth(), 20, "max health after the return");
                helper.assertValueEqual((int) player.getHealth(), 14, "health after the return");
                helper.assertTrue(
                        player.getAttribute(Attributes.MAX_HEALTH).getModifier(CharonService.GHOST_HEALTH_MODIFIER) == null,
                        "the health modifier is gone"
                );
                helper.assertFalse(player.isInvisible(), "the player is visible again");
                helper.assertTrue(
                        player.getCooldowns().isOnCooldown(ModItems.CHARON_TOKEN.get()),
                        "the visual cooldown is shown"
                );
                helper.succeed();
            } finally {
                ModConfigSpec.CHARON_GHOST_SECONDS.set(originalSeconds);
                cleanup(manager, player);
                clearFloor(level, death);
            }
        });
    }

    @GameTest(template = "empty", batch = "charon")
    public static void usingTheTokenAgainReturnsEarly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(START);
        BlockPos death = buildFloor(level, helper.absolutePos(DEATH));
        ServerPlayer player = RegressionPlayers.create(level, start, 0).player();
        CharonManager manager = CharonManager.get(level.getServer());
        try {
            player.setHealth(9.0F);
            becomeGhost(level, manager, player, death);

            InteractionResultHolder<ItemStack> result = useToken(level, player);

            helper.assertTrue(result.getResult().consumesAction(), "the second use is accepted");
            helper.assertFalse(CharonService.isGhost(player), "the ghost form ended early");
            helper.assertTrue(
                    player.blockPosition().closerThan(start, 3.0D),
                    "the player returned early, was " + player.blockPosition()
            );
            helper.assertValueEqual((int) player.getMaxHealth(), 20, "max health after the early return");
            helper.assertValueEqual((int) player.getHealth(), 9, "health after the early return");
            helper.assertTrue(
                    cooldownUntil(manager, player) > System.currentTimeMillis(),
                    "the cooldown stays after an early return"
            );
        } finally {
            cleanup(manager, player);
            clearFloor(level, death);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "charon")
    public static void killingAGhostGivesNoReward(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        var storage = server.overworld().getDataStorage();
        BlockPos start = helper.absolutePos(START);
        BlockPos death = buildFloor(level, helper.absolutePos(DEATH));
        ServerPlayer killer = RegressionPlayers.create(level, start, 0).player();
        ServerPlayer victim = RegressionPlayers.create(level, start, 0).player();
        FactionManager originalFactions = FactionManager.get(level);
        KillRewardLedger originalLedger = KillRewardLedger.get(server);
        FactionManager factions = new FactionManager();
        helper.assertTrue(
                factions.createFaction(killer.getUUID(), "Charon", ClaimKey.of(level, start), 1).successful(),
                "the killer has a faction"
        );
        storage.set(FactionManager.DATA_NAME, factions);
        KillRewardLedger ledger = new KillRewardLedger();
        storage.set(KillRewardLedger.DATA_NAME, ledger);
        CharonManager manager = CharonManager.get(server);
        long originalReward = ModConfigSpec.INFLUENCE_KILL_INFLUENCE.getAsLong();
        int originalCap = ModConfigSpec.INFLUENCE_KILL_CAP_PER_VICTIM.getAsInt();
        try {
            ModConfigSpec.INFLUENCE_KILL_INFLUENCE.set(15L);
            ModConfigSpec.INFLUENCE_KILL_CAP_PER_VICTIM.set(2);
            becomeGhost(level, manager, victim, death);
            var faction = factions.getFactionForMember(killer.getUUID()).orElseThrow();
            long before = faction.influence(InfluenceType.MILITARY);

            InfluenceSourceHandler.onDeath(new LivingDeathEvent(victim, level.damageSources().playerAttack(killer)));

            helper.assertValueEqual(
                    faction.influence(InfluenceType.MILITARY), before, "military influence after a ghost kill"
            );
            CompoundTag saved = ledger.save(new CompoundTag(), level.registryAccess());
            helper.assertTrue(
                    saved.getList("playerAwards", Tag.TAG_COMPOUND).isEmpty(),
                    "no kill reward was written to the ledger"
            );
            List<ItemEntity> drops = new ArrayList<>();
            WarTrophyDrops.onDrops(new LivingDropsEvent(victim, level.damageSources().playerAttack(killer), drops, true));
            helper.assertTrue(drops.isEmpty(), "no war trophy for a ghost kill");
        } finally {
            ModConfigSpec.INFLUENCE_KILL_INFLUENCE.set(originalReward);
            ModConfigSpec.INFLUENCE_KILL_CAP_PER_VICTIM.set(originalCap);
            storage.set(FactionManager.DATA_NAME, originalFactions);
            storage.set(KillRewardLedger.DATA_NAME, originalLedger);
            cleanup(manager, victim);
            cleanup(manager, killer);
            clearFloor(level, death);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "charon")
    public static void theContrabandBuyerSellsTheToken(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos pos = helper.absolutePos(START);
        ServerPlayer buyer = RegressionPlayers.create(level, pos, 0).player();
        TraderWorldData data = TraderWorldData.get(server);
        TraderWorldData.ActiveContraband previous = data.contraband().orElse(null);
        if (previous != null) {
            data.cancelContraband(previous.eventId());
        }
        SellerTraderEntity trader = TraderService.createSellerEntity(
                level,
                pos.getX() + 1.5D,
                pos.getY(),
                pos.getZ() + 0.5D,
                0.0F,
                null,
                SellerTraderRole.CONTRABAND,
                UUID.randomUUID(),
                null,
                System.currentTimeMillis() + 600_000L
        );
        helper.assertTrue(trader != null, "the contraband buyer could be created");
        TraderWorldData.ActiveContraband active = new TraderWorldData.ActiveContraband(
                trader.eventId().orElseThrow(),
                trader.getUUID(),
                UUID.randomUUID(),
                level.dimension(),
                pos,
                trader.expiresAtMillis(),
                List.of()
        );
        helper.assertTrue(data.beginContraband(active), "the contraband event was reserved");
        helper.assertTrue(level.addFreshEntity(trader), "the contraband buyer joined the level");
        long price = ModConfigSpec.CHARON_TOKEN_COST.getAsLong();
        try {
            NumismaticsEconomy.give(buyer, price + 3L);
            UUID sessionId = TradeSessionManager.open(buyer, trader.getUUID());

            TraderService.buy(buyer, trader.getUUID(), sessionId, 1L, "charon_token");

            helper.assertValueEqual(
                    buyer.getInventory().countItem(ModItems.CHARON_TOKEN.get()), 1, "the buyer received the token"
            );
            helper.assertValueEqual(NumismaticsEconomy.balance(buyer), 3L, "spurs left after the purchase");
        } finally {
            trader.discard();
            if (previous != null) {
                data.beginContraband(previous);
            }
            buyer.discard();
        }
        helper.succeed();
    }

    private static void becomeGhost(ServerLevel level, CharonManager manager, ServerPlayer player, BlockPos death) {
        manager.recordDeath(
                player.getUUID(), GlobalPos.of(level.dimension(), death), System.currentTimeMillis() - LONG_AGO_MILLIS
        );
        InteractionResultHolder<ItemStack> result = useToken(level, player);
        if (!result.getResult().consumesAction() || !CharonService.isGhost(player)) {
            throw new IllegalStateException("The player did not become a ghost");
        }
    }

    private static InteractionResultHolder<ItemStack> useToken(ServerLevel level, ServerPlayer player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CHARON_TOKEN.get()));
        return ModItems.CHARON_TOKEN.get().use(level, player, InteractionHand.MAIN_HAND);
    }

    private static void passSpawnProtection(ServerPlayer player) {
        for (int tick = 0; tick < 70; tick++) {
            player.tick();
        }
    }

    private static long cooldownUntil(CharonManager manager, ServerPlayer player) {
        return manager.entry(player.getUUID()).map(CharonManager.Entry::cooldownUntilMillis).orElse(0L);
    }

    private static BlockPos buildFloor(ServerLevel level, BlockPos death) {
        for (BlockPos pos : BlockPos.betweenClosed(death.offset(-1, -1, -1), death.offset(1, -1, 1))) {
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(death.offset(-1, 0, -1), death.offset(1, 2, 1))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        return death;
    }

    private static void clearFloor(ServerLevel level, BlockPos death) {
        for (BlockPos pos : BlockPos.betweenClosed(death.offset(-1, -1, -1), death.offset(1, 2, 1))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    private static void cleanup(CharonManager manager, ServerPlayer player) {
        CharonService.onLogout(player);
        manager.forget(player.getUUID());
        player.discard();
    }

    private CharonGameTests() {
    }
}
