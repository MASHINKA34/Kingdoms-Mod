package com.geydev.kalfactions.faith;

import com.geydev.kalfactions.block.SmallStatueBlockEntity;
import com.geydev.kalfactions.block.StoneGodStatueBlock;
import com.geydev.kalfactions.block.StoneGodStatueCollisionBlock;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.FactionMember;
import com.geydev.kalfactions.net.FactionServerHooks;
import com.geydev.kalfactions.tax.OfflineNoticeQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FaithService {
    private static final double MAX_STATUE_DISTANCE_SQR = 144.0D;
    private static final long ACTION_COOLDOWN_TICKS = 5L;
    private static final ConcurrentHashMap<UUID, Long> LAST_ACTION_TICK = new ConcurrentHashMap<>();

    public record StatueRef(BlockPos anchor, FaithGod god, boolean great) {
    }

    public static Optional<StatueRef> resolveStatue(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof StoneGodStatueCollisionBlock) {
            BlockPos anchor = StoneGodStatueCollisionBlock.anchorOf(pos, state);
            BlockState anchorState = level.getBlockState(anchor);
            return FaithGod.ofStatue(anchorState).map(god -> new StatueRef(anchor, god, true));
        }
        if (state.getBlock() instanceof StoneGodStatueBlock) {
            BlockPos anchor = pos.below(state.getValue(StoneGodStatueBlock.SEGMENT));
            return FaithGod.ofStatue(state).map(god -> new StatueRef(anchor, god, true));
        }
        if (FaithGod.isSmallStatue(state.getBlock())) {
            BlockPos anchor = state.hasProperty(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF
            ) && state.getValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF
            ) == DoubleBlockHalf.UPPER ? pos.below() : pos;
            return FaithGod.ofStatue(state).map(god -> new StatueRef(anchor.immutable(), god, false));
        }
        return Optional.empty();
    }

    public static void stampStatueOwner(
            net.minecraft.world.level.Level level,
            BlockPos anchor,
            @javax.annotation.Nullable net.minecraft.world.entity.LivingEntity placer
    ) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        UUID factionId = placer instanceof ServerPlayer player
                ? FactionManager.get(serverLevel).getFactionIdForMember(player.getUUID()).orElse(null)
                : null;
        smallStatueEntity(serverLevel, anchor).ifPresent(entity -> entity.setOwnerFactionId(factionId));
    }

    public static Optional<SmallStatueBlockEntity> smallStatueEntity(ServerLevel level, BlockPos anchor) {
        for (BlockPos candidate : new BlockPos[] {anchor, anchor.above()}) {
            if (level.getBlockEntity(candidate) instanceof SmallStatueBlockEntity entity) {
                return Optional.of(entity);
            }
        }
        return Optional.empty();
    }

    public static Optional<UUID> statueOwner(ServerLevel level, StatueRef statue) {
        if (statue.great()) {
            return Optional.empty();
        }
        return smallStatueEntity(level, statue.anchor()).flatMap(SmallStatueBlockEntity::ownerFactionId);
    }

    private static boolean canUseStatue(ServerLevel level, StatueRef statue, Faction faction, ServerPlayer player) {
        if (statue.great() || player.hasPermissions(2)) {
            return true;
        }
        UUID owner = statueOwner(level, statue).orElse(null);
        return owner == null || owner.equals(faction.id());
    }

    public static net.minecraft.world.InteractionResult openFromBlock(
            net.minecraft.world.level.Level level,
            BlockPos pos,
            net.minecraft.world.entity.player.Player player
    ) {
        if (level.isClientSide()) {
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            open(serverPlayer, pos);
        }
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    public static void open(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        StatueRef statue = resolveStatue(level, pos).orElse(null);
        if (statue == null) {
            return;
        }
        Faction faction = factionOf(player);
        if (faction == null) {
            FactionServerHooks.sendNotice(
                    player, Component.translatable("kingdoms.faith.notice.no_faction"), false);
            return;
        }
        if (!canUseStatue(level, statue, faction, player)) {
            FactionServerHooks.sendNotice(
                    player, Component.translatable("kingdoms.faith.notice.not_your_statue"), false);
            return;
        }
        sendState(player, statue, pos, Optional.empty(), true);
    }

    public static void handleAction(ServerPlayer player, BlockPos pos, byte action) {
        ServerLevel level = player.serverLevel();
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        long now = level.getGameTime();
        Long previous = LAST_ACTION_TICK.put(player.getUUID(), now);
        if (previous != null && now - previous < ACTION_COOLDOWN_TICKS) {
            FactionServerHooks.sendNotice(
                    player, Component.translatable("kingdoms.error.action_rate_limited"), false);
            return;
        }
        StatueRef statue = resolveStatue(level, pos).orElse(null);
        if (statue == null || player.distanceToSqr(
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > MAX_STATUE_DISTANCE_SQR) {
            FactionServerHooks.sendNotice(
                    player, Component.translatable("kingdoms.faith.notice.too_far"), false);
            return;
        }
        Faction faction = factionOf(player);
        if (faction == null) {
            FactionServerHooks.sendNotice(
                    player, Component.translatable("kingdoms.faith.notice.no_faction"), false);
            return;
        }
        if (!canUseStatue(level, statue, faction, player)) {
            FactionServerHooks.sendNotice(
                    player, Component.translatable("kingdoms.faith.notice.not_your_statue"), false);
            return;
        }
        switch (action) {
            case FaithPayloads.ACTION_OFFER_QUEST -> {
                if (statue.great()) {
                    offerQuest(player, faction, statue, pos);
                }
            }
            case FaithPayloads.ACTION_LEVEL_UP -> {
                if (statue.great()) {
                    levelUp(player, faction, statue, pos);
                }
            }
            case FaithPayloads.ACTION_ACTIVATE_BUFF -> {
                if (!statue.great()) {
                    activateBuff(player, faction, statue, pos);
                }
            }
            default -> {
            }
        }
    }

    private static void offerQuest(ServerPlayer player, Faction faction, StatueRef statue, BlockPos clicked) {
        FaithManager manager = FaithManager.get(player.serverLevel());
        int level = manager.level(faction.id(), statue.god());
        if (level >= FaithGod.MAX_LEVEL) {
            sendState(player, statue, clicked,
                    Optional.of(Component.translatable("kingdoms.faith.notice.max_level")), false);
            return;
        }
        FaithQuest quest = quest(manager, faction.id(), statue.god(), level);
        int taken = applyOffering(manager, faction.id(), statue.god(), quest, player.getInventory());
        if (taken > 0) {
            player.inventoryMenu.broadcastChanges();
        }
        long spursTaken = 0L;
        long missingSpurs = quest.spurs() - manager.spursDelivered(faction.id(), statue.god());
        if (missingSpurs > 0L) {
            long available = Math.min(missingSpurs, faction.treasuryBalance());
            if (available > 0L
                    && FactionManager.get(player.serverLevel()).withdraw(faction.id(), available).successful()) {
                manager.addSpurs(faction.id(), statue.god(), available);
                spursTaken = available;
            }
        }
        Component notice;
        boolean successful = taken > 0 || spursTaken > 0L;
        if (successful) {
            notice = Component.translatable("kingdoms.faith.notice.offered", taken, spursTaken);
        } else {
            notice = Component.translatable("kingdoms.faith.notice.nothing_to_offer");
        }
        sendState(player, statue, clicked, Optional.of(notice), successful);
    }

    private static void levelUp(ServerPlayer player, Faction faction, StatueRef statue, BlockPos clicked) {
        FaithManager manager = FaithManager.get(player.serverLevel());
        FaithGod god = statue.god();
        int level = manager.level(faction.id(), god);
        if (level >= FaithGod.MAX_LEVEL) {
            sendState(player, statue, clicked,
                    Optional.of(Component.translatable("kingdoms.faith.notice.max_level")), false);
            return;
        }
        FaithQuest quest = quest(manager, faction.id(), god, level);
        if (!quest.isComplete(
                manager.delivered(faction.id(), god),
                manager.spursDelivered(faction.id(), god),
                manager.kills(faction.id(), god))) {
            sendState(player, statue, clicked,
                    Optional.of(Component.translatable("kingdoms.faith.notice.quest_incomplete")), false);
            return;
        }
        if (!manager.advanceLevel(faction.id(), god)) {
            sendState(player, statue, clicked,
                    Optional.of(Component.translatable("kingdoms.faith.notice.max_level")), false);
            return;
        }
        int newLevel = manager.level(faction.id(), god);
        MinecraftServer server = player.getServer();
        notifyFaction(
                server,
                faction,
                Component.translatable(
                        "kingdoms.faith.notice.level_up",
                        Component.translatable(god.translationKey()),
                        newLevel
                ),
                true
        );
        if (newLevel >= FaithGod.MAX_LEVEL && server != null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable(
                            "kingdoms.faith.broadcast.max_level",
                            faction.name(),
                            Component.translatable(god.translationKey())
                    ).withStyle(ChatFormatting.GOLD),
                    false
            );
        }
        sendState(player, statue, clicked, Optional.empty(), true);
    }

    private static void activateBuff(ServerPlayer player, Faction faction, StatueRef statue, BlockPos clicked) {
        FaithManager manager = FaithManager.get(player.serverLevel());
        FaithGod god = statue.god();
        long now = System.currentTimeMillis();
        if (manager.buffActive(faction.id(), god, now)) {
            sendState(player, statue, clicked,
                    Optional.of(Component.translatable("kingdoms.faith.notice.buff_active")), false);
            return;
        }
        int cost = Math.max(0, ModConfigSpec.FAITH_BUFF_CRYSTAL_COST.getAsInt());
        FaithRequirement crystals = FaithRequirement.ofItem(god.crystal(), cost);
        if (cost > 0 && countInInventory(player, crystals) < cost) {
            sendState(player, statue, clicked,
                    Optional.of(Component.translatable(
                            "kingdoms.faith.notice.missing_crystals",
                            cost,
                            god.crystal().getDescription()
                    )), false);
            return;
        }
        if (cost > 0) {
            removeFromInventory(player, crystals, cost);
        }
        long duration = Math.max(1, ModConfigSpec.FAITH_BUFF_DURATION_MINUTES.getAsInt()) * 60_000L;
        manager.activateBuff(faction.id(), god, now + duration);
        MinecraftServer server = player.getServer();
        notifyFaction(
                server,
                faction,
                Component.translatable(
                        "kingdoms.faith.notice.buff_started",
                        Component.translatable(god.translationKey()),
                        ModConfigSpec.FAITH_BUFF_DURATION_MINUTES.getAsInt()
                ),
                true
        );
        refreshMembers(server, faction);
        sendState(player, statue, clicked, Optional.empty(), true);
    }

    public static void sendState(
            ServerPlayer player,
            StatueRef statue,
            BlockPos clicked,
            Optional<Component> notice,
            boolean successful
    ) {
        Faction faction = factionOf(player);
        if (faction == null) {
            return;
        }
        FaithManager manager = FaithManager.get(player.serverLevel());
        FaithGod god = statue.god();
        int level = manager.level(faction.id(), god);
        FaithQuest quest = quest(manager, faction.id(), god, level);
        int[] delivered = manager.delivered(faction.id(), god);
        List<FaithPayloads.QuestEntry> entries = new ArrayList<>(quest.requirements().size());
        for (int index = 0; index < quest.requirements().size(); index++) {
            FaithRequirement requirement = quest.requirements().get(index);
            entries.add(new FaithPayloads.QuestEntry(
                    BuiltInRegistries.ITEM.getKey(requirement.icon()).toString(),
                    requirement.labelKey(),
                    requirement.count(),
                    Math.min(requirement.count(), FaithQuest.deliveredAt(delivered, index))
            ));
        }
        long now = System.currentTimeMillis();
        long buffEnd = manager.buffEndMillis(faction.id(), god);
        boolean complete = level < FaithGod.MAX_LEVEL && quest.isComplete(
                delivered, manager.spursDelivered(faction.id(), god), manager.kills(faction.id(), god));
        PacketDistributor.sendToPlayer(player, new FaithPayloads.S2CFaithState(
                clicked,
                statue.great(),
                (byte) god.index(),
                level,
                entries,
                quest.spurs(),
                Math.min(quest.spurs(), manager.spursDelivered(faction.id(), god)),
                quest.kills(),
                Math.min(quest.kills(), manager.kills(faction.id(), god)),
                quest.killsOrTrophy(),
                complete,
                Math.max(0, ModConfigSpec.FAITH_BUFF_CRYSTAL_COST.getAsInt()),
                Math.max(1, ModConfigSpec.FAITH_BUFF_DURATION_MINUTES.getAsInt()),
                Math.max(0L, buffEnd - now),
                buffEnd > now && !manager.hasForfeited(player.getUUID(), god, buffEnd),
                primaryEffect(god, level),
                secondaryEffect(god, level),
                god == FaithGod.ECONOMY && FaithBonuses.economyHighlight(level)
                        ? Math.max(1, ModConfigSpec.FAITH_ECONOMY_HIGHLIGHT_RADIUS.getAsInt())
                        : 0,
                notice,
                successful
        ));
    }

    private static double primaryEffect(FaithGod god, int level) {
        return switch (god) {
            case SCIENCE -> FaithBonuses.scienceCraftChance(level);
            case WAR -> FaithBonuses.warBonusDamage(level);
            case ECONOMY -> FaithBonuses.economySellPercent(level);
        };
    }

    private static double secondaryEffect(FaithGod god, int level) {
        return switch (god) {
            case SCIENCE -> FaithBonuses.scienceExperienceBonus(level);
            case WAR -> FaithBonuses.warBonusHealth(level);
            case ECONOMY -> FaithBonuses.economyDropChance(level);
        };
    }

    public static FaithQuest quest(FaithManager manager, UUID factionId, FaithGod god, int level) {
        return FaithQuests.build(factionId, god, Math.min(level + 1, FaithGod.MAX_LEVEL),
                manager.nonce(factionId, god));
    }

    public static void recordPlayerKill(ServerPlayer killer, ServerPlayer victim) {
        MinecraftServer server = killer.getServer();
        if (server == null || killer.getUUID().equals(victim.getUUID())) {
            return;
        }
        FactionManager factions = FactionManager.get(killer.serverLevel());
        UUID killerFaction = factions.getFactionIdForMember(killer.getUUID()).orElse(null);
        if (killerFaction == null) {
            return;
        }
        UUID victimFaction = factions.getFactionIdForMember(victim.getUUID()).orElse(null);
        if (killerFaction.equals(victimFaction)) {
            return;
        }
        FaithManager.get(killer.serverLevel()).addKills(killerFaction, FaithGod.WAR, 1);
    }

    public static void forgetPlayer(UUID playerId) {
        LAST_ACTION_TICK.remove(playerId);
    }

    public static void forfeitBuffs(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        UUID factionId = FactionManager.get(player.serverLevel())
                .getFactionIdForMember(player.getUUID())
                .orElse(null);
        if (factionId == null) {
            return;
        }
        FaithManager manager = FaithManager.get(player.serverLevel());
        long now = System.currentTimeMillis();
        for (FaithGod god : FaithGod.VALUES) {
            long buffEnd = manager.buffEndMillis(factionId, god);
            if (buffEnd > now) {
                manager.forfeit(player.getUUID(), god, buffEnd);
            }
        }
    }

    public static void refreshMembers(MinecraftServer server, Faction faction) {
        if (server == null) {
            return;
        }
        for (UUID memberId : faction.members().keySet()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member != null) {
                FaithEffects.refresh(member);
            }
        }
    }

    public static void notifyFaction(
            MinecraftServer server,
            Faction faction,
            Component message,
            boolean successful
    ) {
        if (server == null) {
            return;
        }
        OfflineNoticeQueue queue = OfflineNoticeQueue.get(server);
        for (Map.Entry<UUID, FactionMember> entry : faction.members().entrySet()) {
            ServerPlayer member = server.getPlayerList().getPlayer(entry.getKey());
            if (member != null) {
                member.sendSystemMessage(message);
                FactionServerHooks.sendNotice(member, message, successful);
            } else {
                queue.enqueue(server, entry.getKey(), message, successful);
            }
        }
    }

    public static int applyOffering(
            FaithManager manager,
            UUID factionId,
            FaithGod god,
            FaithQuest quest,
            Inventory inventory
    ) {
        int taken = 0;
        for (int index = 0; index < quest.requirements().size(); index++) {
            FaithRequirement requirement = quest.requirements().get(index);
            int missing = requirement.count() - manager.deliveredAt(factionId, god, index);
            if (missing <= 0) {
                continue;
            }
            int removed = takeFromInventory(inventory, requirement, missing);
            if (removed > 0) {
                manager.addDelivered(factionId, god, index, removed);
                taken += removed;
            }
        }
        return taken;
    }

    public static int countInInventory(ServerPlayer player, FaithRequirement requirement) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (requirement.matches(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static int takeFromInventory(Inventory inventory, FaithRequirement requirement, int wanted) {
        int remaining = wanted;
        for (ItemStack stack : inventory.items) {
            if (remaining <= 0) {
                break;
            }
            if (!requirement.matches(stack)) {
                continue;
            }
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
        }
        if (remaining != wanted) {
            inventory.setChanged();
        }
        return wanted - remaining;
    }

    private static int removeFromInventory(ServerPlayer player, FaithRequirement requirement, int wanted) {
        int removed = takeFromInventory(player.getInventory(), requirement, wanted);
        if (removed > 0) {
            player.inventoryMenu.broadcastChanges();
        }
        return removed;
    }

    private static Faction factionOf(ServerPlayer player) {
        return FactionManager.get(player.serverLevel())
                .getFactionForMember(player.getUUID())
                .orElse(null);
    }

    private FaithService() {
    }
}
