package com.geydev.kalfactions.net;

import com.geydev.kalfactions.block.FactionTableBlockEntity;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.command.PendingAllianceRequests;
import com.geydev.kalfactions.command.PendingFactionInvites;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionBonus;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.FactionMember;
import com.geydev.kalfactions.faction.FactionRole;
import com.geydev.kalfactions.faction.InfluenceType;
import com.geydev.kalfactions.faction.ResearchManager;
import com.geydev.kalfactions.faction.ResearchNode;
import com.geydev.kalfactions.faction.ResearchCrystalCosts;
import com.geydev.kalfactions.integration.IntegrationManager;
import com.geydev.kalfactions.registry.ModItems;
import com.geydev.kalfactions.sanctuary.SanctuaryManager;
import com.geydev.kalfactions.war.WarManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

public final class FactionManagerService implements FactionServerHooks.Service {
    private static final int MAP_RADIUS = 6;
    private static final int MAX_PIXEL_EMBLEM_REFS = 64;

    @Override
    public FactionSnapshot view(ServerPlayer player, BlockPos tablePos) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        ChunkPos center = new ChunkPos(tablePos);
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return FactionSnapshot.empty(
                    tablePos,
                    center.x,
                    center.z,
                    0L
            );
        }

        FactionRole role = faction.roleOf(player.getUUID()).orElse(FactionRole.MEMBER);
        int ownColor = faction.color();
        List<FactionSnapshot.FactionRef> allies = alliedFactionRefs(manager, faction);
        return new FactionSnapshot(
                tablePos,
                faction.id(),
                faction.name(),
                playerName(player, faction.ownerId()),
                ownColor,
                role == FactionRole.LEADER,
                role.canManageClaims(),
                center.x,
                center.z,
                MAP_RADIUS,
                members(player, faction),
                claims(player, manager, faction.id(), ownColor, center),
                faction.treasuryBalance(),
                faction.influence(),
                faction.influence(com.geydev.kalfactions.faction.InfluenceType.SCIENCE),
                faction.influence(com.geydev.kalfactions.faction.InfluenceType.ECONOMIC),
                faction.influence(com.geydev.kalfactions.faction.InfluenceType.MILITARY),
                faction.internalPvp(),
                0L,
                player.getUUID(),
                role.isAtLeast(FactionRole.OFFICER),
                activeWarName(player, manager, faction),
                WarManager.get(player.getServer()).declareCooldownRemainingSeconds(faction.id()),
                warTargetRefs(manager, faction),
                allianceCandidateRefs(player, manager, faction),
                allies,
                joinableAllyRefs(player, manager, faction),
                onlinePlayers(player, manager),
                bonusNames(faction),
                emblemPixels(faction),
                faction.emblemUrl(),
                researchNames(faction),
                faction.activeResearch().map(active -> active.node().name()).orElse(""),
                faction.activeResearch().map(faction::researchEndMillis).orElse(0L),
                ResearchCrystalCosts.configured(),
                countCrystals(player.getInventory(), InfluenceType.SCIENCE),
                countCrystals(player.getInventory(), InfluenceType.ECONOMIC),
                countCrystals(player.getInventory(), InfluenceType.MILITARY),
                pendingWarSpoils(player, faction),
                faction.claimCount(),
                faction.forceLoadedCount()
        );
    }

    private static FactionSnapshot.WarSpoils pendingWarSpoils(ServerPlayer player, Faction faction) {
        return WarManager.get(player.getServer())
                .pendingSpoilsForWinner(player.getServer(), faction.id())
                .map(spoils -> new FactionSnapshot.WarSpoils(
                        spoils.spoilsId(),
                        spoils.loserName(),
                        spoils.money(),
                        spoils.resourceOne(),
                        spoils.resourceOneItem(),
                        spoils.resourceTwo(),
                        spoils.resourceTwoItem(),
                        spoils.resourceThree(),
                        spoils.resourceThreeItem()
                ))
                .orElse(FactionSnapshot.WarSpoils.EMPTY);
    }

    private static List<String> researchNames(Faction faction) {
        return faction.completedResearch().stream()
                .map(Enum::name)
                .sorted()
                .toList();
    }

    private static int countCrystals(Inventory inventory, InfluenceType type) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.crystalFor(type))) {
                if (count > Integer.MAX_VALUE - stack.getCount()) {
                    return Integer.MAX_VALUE;
                }
                count += stack.getCount();
            }
        }
        return count;
    }

    static List<String> bonusNames(Faction faction) {
        return faction.bonuses().stream()
                .map(Enum::name)
                .sorted()
                .toList();
    }

    public static List<Integer> emblemPixels(Faction faction) {
        int[] pixels = faction.emblem();
        if (!Faction.isValidEmblemLength(pixels.length)) {
            return List.of();
        }
        List<Integer> boxed = new ArrayList<>(pixels.length);
        for (int pixel : pixels) {
            boxed.add(pixel);
        }
        return List.copyOf(boxed);
    }

    private static List<FactionSnapshot.OnlinePlayer> onlinePlayers(ServerPlayer viewer, FactionManager manager) {
        return viewer.getServer().getPlayerList().getPlayers().stream()
                .filter(online -> !online.getUUID().equals(viewer.getUUID()))
                .map(online -> new FactionSnapshot.OnlinePlayer(
                        online.getGameProfile().getName(),
                        manager.getFactionForMember(online.getUUID()).map(Faction::name).orElse("")
                ))
                .sorted(Comparator.comparing(FactionSnapshot.OnlinePlayer::inFaction)
                        .thenComparing(FactionSnapshot.OnlinePlayer::name, String.CASE_INSENSITIVE_ORDER))
                .limit(FactionSnapshot.MAX_ONLINE_PLAYERS)
                .toList();
    }

    private static List<FactionSnapshot.FactionRef> warTargetRefs(FactionManager manager, Faction ownFaction) {
        return factionRefs(manager.factions().stream()
                .filter(faction -> !faction.id().equals(ownFaction.id()))
                .filter(faction -> !manager.areAllied(ownFaction.id(), faction.id())));
    }

    private static List<FactionSnapshot.FactionRef> factionRefs(java.util.stream.Stream<Faction> factions) {
        List<Faction> sorted = factions
                .sorted(Comparator.comparing(Faction::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<FactionSnapshot.FactionRef> refs = new ArrayList<>(sorted.size());
        for (int index = 0; index < sorted.size(); index++) {
            Faction faction = sorted.get(index);
            refs.add(new FactionSnapshot.FactionRef(
                    faction.id(),
                    faction.name(),
                    faction.color(),
                    index < MAX_PIXEL_EMBLEM_REFS ? emblemPixels(faction) : List.of(),
                    faction.emblemUrl()
            ));
        }
        return List.copyOf(refs);
    }

    private static String activeWarName(ServerPlayer player, FactionManager manager, Faction faction) {
        return WarManager.get(player.getServer()).warForFaction(faction.id())
                .filter(war -> war.isActive())
                .map(war -> manager.getFactionById(war.opponentOf(faction.id()))
                        .map(Faction::name)
                        .orElse(""))
                .orElse("");
    }

    private static List<FactionSnapshot.FactionRef> allianceCandidateRefs(
            ServerPlayer player,
            FactionManager manager,
            Faction ownFaction
    ) {
        WarManager wars = WarManager.get(player.getServer());
        return factionRefs(manager.factions().stream()
                .filter(faction -> !faction.id().equals(ownFaction.id()))
                .filter(faction -> !manager.areAllied(ownFaction.id(), faction.id()))
                .filter(faction -> !wars.areAtWar(ownFaction.id(), faction.id()))
                .filter(faction -> PendingAllianceRequests
                        .find(player.getServer(), ownFaction.id(), faction.id())
                        .isEmpty()));
    }

    private static List<FactionSnapshot.FactionRef> alliedFactionRefs(FactionManager manager, Faction faction) {
        return factionRefs(faction.allies().stream()
                .map(manager::getFactionById)
                .flatMap(java.util.Optional::stream));
    }

    /**
     * Allies the viewing faction may join in war: an ally currently defending an active war it was
     * attacked in, provided the viewer is free (not already in a war) and not allied with the attacker.
     */
    private static List<FactionSnapshot.FactionRef> joinableAllyRefs(
            ServerPlayer player,
            FactionManager manager,
            Faction faction
    ) {
        WarManager wars = WarManager.get(player.getServer());
        if (wars.warForFaction(faction.id()).isPresent()) {
            return List.of();
        }
        return factionRefs(faction.allies().stream()
                .map(manager::getFactionById)
                .flatMap(java.util.Optional::stream)
                .filter(ally -> wars.canJoinDefense(faction.id(), ally.id())));
    }

    @Override
    public FactionServerHooks.Result create(
            ServerPlayer player,
            BlockPos tablePos,
            String name,
            int color,
            List<String> bonusNames,
            List<Integer> emblem,
            String emblemUrl
    ) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        UUID boundFactionId = boundFactionId(player, tablePos);
        if (boundFactionId != null && manager.getFaction(boundFactionId).isPresent()) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.table_already_bound"),
                    view(player, tablePos)
            );
        }
        Set<FactionBonus> bonuses = parseBonuses(bonusNames);
        if (bonuses == null) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.bonus_selection"),
                    view(player, tablePos)
            );
        }
        if (nearSanctuary(player, new ChunkPos(tablePos))) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.sanctuary.no_found_near"),
                    view(player, tablePos)
            );
        }
        FactionManager.OperationResult result = manager.createFaction(
                player.getUUID(),
                name,
                ClaimKey.of(player.serverLevel(), new ChunkPos(tablePos))
        );
        if (result.successful()) {
            manager.setFactionBonuses(result.factionId(), bonuses);
            manager.setFactionEmblem(result.factionId(), unboxEmblem(emblem), sanitizeEmblemUrl(emblemUrl));
            manager.setFactionColor(result.factionId(), color);
            updateTableMetadata(player, tablePos, result.factionId(), color);
            IntegrationManager.refreshFromServer(player.getServer());
        }
        return result(result, player, tablePos);
    }

    @Override
    public FactionServerHooks.Result setEmblem(
            ServerPlayer player,
            BlockPos tablePos,
            List<Integer> emblem,
            String emblemUrl
    ) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return notInFaction(player, tablePos);
        }
        if (!faction.ownerId().equals(player.getUUID())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.leader_settings_only"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return otherFactionTable(player, tablePos);
        }
        FactionManager.OperationResult result =
                manager.setFactionEmblem(faction.id(), unboxEmblem(emblem), sanitizeEmblemUrl(emblemUrl));
        if (!result.successful()) {
            return FactionServerHooks.Result.denied(message(result.status()), view(player, tablePos));
        }
        return new FactionServerHooks.Result(
                true,
                Component.translatable("kingdoms.command.faction.emblem.saved"),
                view(player, tablePos)
        );
    }

    private static Set<FactionBonus> parseBonuses(List<String> bonusNames) {
        if (bonusNames == null || bonusNames.size() != 2) {
            return null;
        }
        Set<FactionBonus> parsed = new LinkedHashSet<>();
        for (String bonusName : bonusNames) {
            FactionBonus bonus;
            try {
                bonus = FactionBonus.parse(bonusName);
            } catch (IllegalArgumentException exception) {
                return null;
            }
            if (!FactionBonus.SELECTABLE.contains(bonus) || !parsed.add(bonus)) {
                return null;
            }
        }
        return parsed;
    }

    private static int[] unboxEmblem(List<Integer> emblem) {
        if (emblem == null || !Faction.isValidEmblemLength(emblem.size())) {
            return null;
        }
        int[] pixels = new int[emblem.size()];
        for (int index = 0; index < pixels.length; index++) {
            Integer value = emblem.get(index);
            pixels[index] = value == null ? 0 : value;
        }
        return pixels;
    }

    private static String sanitizeEmblemUrl(String url) {
        if (url == null) {
            return "";
        }
        String cleaned = url.strip();
        if (cleaned.isEmpty()) {
            return "";
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            if (!cleaned.contains("://") && cleaned.contains(".")) {
                cleaned = "https://" + cleaned;
            } else {
                return "";
            }
        }
        return cleaned.length() > Faction.MAX_EMBLEM_URL_LENGTH
                ? cleaned.substring(0, Faction.MAX_EMBLEM_URL_LENGTH)
                : cleaned;
    }

    @Override
    public FactionServerHooks.Result update(
            ServerPlayer player,
            BlockPos tablePos,
            String name,
            int color
    ) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null || !faction.ownerId().equals(player.getUUID())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.leader_settings_only"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.table_other_faction"),
                    view(player, tablePos)
            );
        }

        FactionManager.OperationResult result = faction.name().equals(name)
                ? new FactionManager.OperationResult(FactionManager.Status.SUCCESS, faction.id(), 0L)
                : manager.renameFaction(faction.id(), name);
        if (result.successful()) {
            manager.setFactionColor(faction.id(), color);
            updateTableMetadata(player, tablePos, faction.id(), color);
            IntegrationManager.refreshFromServer(player.getServer());
        }
        return result(result, player, tablePos);
    }

    @Override
    public FactionServerHooks.Result setClaim(
            ServerPlayer player,
            BlockPos tablePos,
            ChunkPos chunkPos,
            boolean claimed
    ) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        FactionRole role = manager.getRole(player.getUUID()).orElse(FactionRole.MEMBER);
        if (faction == null || !role.canManageClaims()) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.role_cannot_change_claims"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.table_other_faction"),
                    view(player, tablePos)
            );
        }

        ClaimKey key = ClaimKey.of(player.serverLevel(), chunkPos);
        if (claimed && SanctuaryManager.get(player.serverLevel()).isSanctuary(key)) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.sanctuary.no_claim"),
                    view(player, tablePos)
            );
        }
        FactionManager.OperationResult result = claimed
                ? manager.claim(faction.id(), key, player.getUUID())
                : manager.unclaim(faction.id(), key);
        if (result.successful()) {
            updateTableMetadata(player, tablePos, faction.id(), faction.color());
        }
        return result(result, player, tablePos);
    }

    @Override
    public FactionServerHooks.Result deposit(ServerPlayer player, BlockPos tablePos, long amount) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.not_in_faction"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.table_other_faction"),
                    view(player, tablePos)
            );
        }
        if (amount <= 0L) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.deposit_amount"),
                    view(player, tablePos)
            );
        }
        if (Long.MAX_VALUE - faction.treasuryBalance() < amount) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.treasury_overflow"),
                    view(player, tablePos)
            );
        }

        NumismaticsEconomy.Payment payment = NumismaticsEconomy.preparePayment(player, amount);
        if (!payment.ready()) {
            return FactionServerHooks.Result.denied(
                    Component.translatable(
                            "kingdoms.error.available_funds",
                            NumismaticsEconomy.format(payment.available())
                    ),
                    view(player, tablePos)
            );
        }
        if (!NumismaticsEconomy.commitPayment(player, payment)) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.coin_inventory_changed"),
                    view(player, tablePos)
            );
        }

        FactionManager.OperationResult result = manager.deposit(faction.id(), amount);
        if (!result.successful()) {
            NumismaticsEconomy.give(player, amount);
            return FactionServerHooks.Result.denied(message(result.status()), view(player, tablePos));
        }
        Component message = payment.change() > 0L
                ? Component.translatable(
                        "kingdoms.command.faction.deposit.success_change",
                        NumismaticsEconomy.format(amount),
                        NumismaticsEconomy.format(payment.change())
                )
                : Component.translatable(
                        "kingdoms.command.faction.deposit.success",
                        NumismaticsEconomy.format(amount)
                );
        return new FactionServerHooks.Result(true, message, view(player, tablePos));
    }

    @Override
    public FactionServerHooks.Result turnInCrystals(ServerPlayer player, BlockPos tablePos) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.not_in_faction"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.table_other_faction"),
                    view(player, tablePos)
            );
        }
        long rate = ModConfigSpec.INFLUENCE_CRYSTAL_TO_INFLUENCE.getAsLong();
        int[] counts = new int[InfluenceType.VALUES.length];
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Optional<InfluenceType> type = ModItems.crystalType(stack.getItem());
            if (type.isEmpty()) {
                continue;
            }
            counts[type.get().index()] += stack.getCount();
            inventory.setItem(slot, ItemStack.EMPTY);
        }
        int totalCrystals = 0;
        long totalInfluence = 0L;
        for (InfluenceType type : InfluenceType.VALUES) {
            int count = counts[type.index()];
            if (count <= 0) {
                continue;
            }
            long gain = (long) count * rate;
            manager.grantInfluence(faction.id(), type, gain);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    player,
                    new com.geydev.kalfactions.net.FactionPayloads.S2CInfluenceGain(type.id(), gain)
            );
            totalCrystals += count;
            totalInfluence += gain;
        }
        if (totalCrystals <= 0) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.no_crystals"),
                    view(player, tablePos)
            );
        }
        Component message = Component.translatable(
                "kingdoms.influence.turned_in",
                totalCrystals,
                totalInfluence
        );
        return new FactionServerHooks.Result(true, message, view(player, tablePos));
    }

    @Override
    public FactionServerHooks.Result startResearch(ServerPlayer player, BlockPos tablePos, String nodeName) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.not_in_faction"),
                    view(player, tablePos)
            );
        }
        FactionRole role = manager.getRole(player.getUUID()).orElse(FactionRole.MEMBER);
        if (!role.isAtLeast(FactionRole.OFFICER)) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.research_officer_only"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.table_other_faction"),
                    view(player, tablePos)
            );
        }
        ResearchNode node = ResearchNode.parse(nodeName).orElse(null);
        if (node == null) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.research_invalid"),
                    view(player, tablePos)
            );
        }
        FactionManager.StartResearchResult result = ResearchManager.start(manager, faction.id(), node, player);
        return switch (result) {
            case STARTED -> new FactionServerHooks.Result(
                    true,
                    Component.translatable(
                            "kingdoms.research.started",
                            Component.translatable(node.translationKey())
                    ),
                    view(player, tablePos)
            );
            case ALREADY_ACTIVE -> FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.research_active"),
                    view(player, tablePos)
            );
            case UNAVAILABLE -> FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.research_unavailable"),
                    view(player, tablePos)
            );
            case INSUFFICIENT_INFLUENCE -> FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.research_influence"),
                    view(player, tablePos)
            );
            case INSUFFICIENT_CRYSTALS -> FactionServerHooks.Result.denied(
                    Component.translatable(
                            "kingdoms.error.research_crystals",
                            ResearchCrystalCosts.forTier(node.tier()),
                            Component.translatable(ModItems.crystalFor(node.type()).getDescriptionId())
                    ),
                    view(player, tablePos)
            );
            case CRYSTAL_PAYMENT_CHANGED -> FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.research_payment_changed"),
                    view(player, tablePos)
            );
            case NO_FACTION -> FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.not_in_faction"),
                    view(player, tablePos)
            );
        };
    }

    @Override
    public FactionServerHooks.Result withdraw(ServerPlayer player, BlockPos tablePos, long amount) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        FactionRole role = manager.getRole(player.getUUID()).orElse(FactionRole.MEMBER);
        if (faction == null) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.not_in_faction"),
                    view(player, tablePos)
            );
        }
        if (!role.isAtLeast(FactionRole.OFFICER)) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.withdraw_officer_only"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.table_other_faction"),
                    view(player, tablePos)
            );
        }
        if (amount <= 0L) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.withdraw_amount"),
                    view(player, tablePos)
            );
        }
        if (!NumismaticsEconomy.canGive(amount)) {
            return FactionServerHooks.Result.denied(
                    Component.translatable(
                            "kingdoms.command.faction.withdraw.max",
                            NumismaticsEconomy.format(NumismaticsEconomy.MAX_SINGLE_PAYOUT)
                    ),
                    view(player, tablePos)
            );
        }

        FactionManager.OperationResult result = manager.withdraw(faction.id(), amount);
        if (!result.successful()) {
            return FactionServerHooks.Result.denied(message(result.status()), view(player, tablePos));
        }
        NumismaticsEconomy.give(player, amount);
        return new FactionServerHooks.Result(
                true,
                Component.translatable(
                        "kingdoms.command.faction.withdraw.success",
                        NumismaticsEconomy.format(amount)
                ),
                view(player, tablePos)
        );
    }

    @Override
    public FactionServerHooks.Result kickMember(ServerPlayer player, BlockPos tablePos, UUID targetId) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.not_in_faction"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.table_other_faction"),
                    view(player, tablePos)
            );
        }
        Component rejection = validateMemberAction(faction, player.getUUID(), targetId, FactionRole.OFFICER);
        if (rejection != null) {
            return FactionServerHooks.Result.denied(rejection, view(player, tablePos));
        }

        FactionManager.OperationResult result = manager.removeMember(faction.id(), targetId);
        if (!result.successful()) {
            return FactionServerHooks.Result.denied(message(result.status()), view(player, tablePos));
        }
        ServerPlayer target = player.getServer().getPlayerList().getPlayer(targetId);
        if (target != null) {
            target.sendSystemMessage(Component.translatable(
                    "kingdoms.command.faction.member.removed_notice",
                    faction.name()
            ));
        }
        return new FactionServerHooks.Result(
                true,
                Component.translatable(
                        "kingdoms.command.faction.member.removed",
                        playerName(player, targetId)
                ),
                view(player, tablePos)
        );
    }

    @Override
    public FactionServerHooks.Result setMemberRole(
            ServerPlayer player,
            BlockPos tablePos,
            UUID targetId,
            String roleName
    ) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.not_in_faction"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.table_other_faction"),
                    view(player, tablePos)
            );
        }
        FactionRole role;
        try {
            role = FactionRole.valueOf(roleName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.invalid_role"),
                    view(player, tablePos)
            );
        }
        if (role == FactionRole.LEADER) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.command.faction.leadership.use_transfer"),
                    view(player, tablePos)
            );
        }
        Component rejection = validateMemberAction(faction, player.getUUID(), targetId, FactionRole.LEADER);
        if (rejection != null) {
            return FactionServerHooks.Result.denied(rejection, view(player, tablePos));
        }

        FactionManager.OperationResult result = manager.setMemberRole(faction.id(), targetId, role);
        if (!result.successful()) {
            return FactionServerHooks.Result.denied(message(result.status()), view(player, tablePos));
        }
        return new FactionServerHooks.Result(
                true,
                Component.translatable(
                        "kingdoms.command.faction.role.rank_changed",
                        playerName(player, targetId),
                        roleComponent(role)
                ),
                view(player, tablePos)
        );
    }

    @Override
    public FactionServerHooks.Result setPvp(ServerPlayer player, BlockPos tablePos, boolean enabled) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        FactionRole role = manager.getRole(player.getUUID()).orElse(FactionRole.MEMBER);
        if (faction == null) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.not_in_faction"),
                    view(player, tablePos)
            );
        }
        if (!role.isAtLeast(FactionRole.OFFICER)) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.pvp_officer_only"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.table_other_faction"),
                    view(player, tablePos)
            );
        }

        FactionManager.OperationResult result = manager.setInternalPvp(faction.id(), enabled);
        if (!result.successful()) {
            return FactionServerHooks.Result.denied(message(result.status()), view(player, tablePos));
        }
        Component memberNotice = Component.translatable("kingdoms.notice.pvp_changed", enabledState(enabled));
        for (UUID memberId : faction.members().keySet()) {
            if (memberId.equals(player.getUUID())) {
                continue;
            }
            ServerPlayer member = player.getServer().getPlayerList().getPlayer(memberId);
            if (member != null) {
                FactionServerHooks.sendNotice(member, memberNotice, true);
            }
        }
        return new FactionServerHooks.Result(
                true,
                Component.translatable(
                        "kingdoms.command.faction.pvp.status",
                        enabledState(enabled)
                ),
                view(player, tablePos)
        );
    }

    @Override
    public FactionServerHooks.Result leave(ServerPlayer player, BlockPos tablePos) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return notInFaction(player, tablePos);
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return otherFactionTable(player, tablePos);
        }
        if (faction.ownerId().equals(player.getUUID()) && faction.memberCount() == 1) {
            if (!NumismaticsEconomy.canGive(faction.treasuryBalance())) {
                return FactionServerHooks.Result.denied(
                        Component.translatable("kingdoms.command.faction.disband.withdraw_first"),
                        view(player, tablePos)
                );
            }
            FactionManager.OperationResult disbanded = manager.disbandFaction(faction.id());
            if (!disbanded.successful()) {
                return FactionServerHooks.Result.denied(message(disbanded.status()), view(player, tablePos));
            }
            PendingFactionInvites.removeForFaction(player.getServer(), faction.id());
            PendingAllianceRequests.removeForFaction(player.getServer(), faction.id());
            IntegrationManager.refreshFromServer(player.getServer());
            Component message;
            if (disbanded.amount() > 0L) {
                NumismaticsEconomy.give(player, disbanded.amount());
                message = Component.translatable(
                        "kingdoms.command.faction.leave.disbanded_refund",
                        faction.name(),
                        NumismaticsEconomy.format(disbanded.amount())
                );
            } else {
                message = Component.translatable("kingdoms.command.faction.leave.disbanded", faction.name());
            }
            return new FactionServerHooks.Result(true, message, view(player, tablePos));
        }
        FactionManager.OperationResult result = manager.removeMember(faction.id(), player.getUUID());
        if (!result.successful()) {
            return FactionServerHooks.Result.denied(message(result.status()), view(player, tablePos));
        }
        PendingFactionInvites.removeForPlayer(player.getServer(), player.getUUID());
        return new FactionServerHooks.Result(
                true,
                Component.translatable("kingdoms.command.faction.leave.success", faction.name()),
                view(player, tablePos)
        );
    }

    @Override
    public FactionServerHooks.Result disband(ServerPlayer player, BlockPos tablePos) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return notInFaction(player, tablePos);
        }
        if (!faction.ownerId().equals(player.getUUID())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.leader_settings_only"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return otherFactionTable(player, tablePos);
        }
        if (!NumismaticsEconomy.canGive(faction.treasuryBalance())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.command.faction.disband.withdraw_first"),
                    view(player, tablePos)
            );
        }

        FactionManager.OperationResult result = manager.disbandFaction(faction.id());
        if (!result.successful()) {
            return FactionServerHooks.Result.denied(message(result.status()), view(player, tablePos));
        }
        PendingFactionInvites.removeForFaction(player.getServer(), faction.id());
        PendingAllianceRequests.removeForFaction(player.getServer(), faction.id());
        Component message;
        if (result.amount() > 0L) {
            NumismaticsEconomy.give(player, result.amount());
            message = Component.translatable(
                    "kingdoms.command.faction.disband.success_refund",
                    NumismaticsEconomy.format(result.amount())
            );
        } else {
            message = Component.translatable("kingdoms.command.faction.disband.success");
        }
        return new FactionServerHooks.Result(true, message, view(player, tablePos));
    }

    @Override
    public FactionServerHooks.Result invite(ServerPlayer player, BlockPos tablePos, String targetName) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return notInFaction(player, tablePos);
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return otherFactionTable(player, tablePos);
        }
        FactionRole role = faction.roleOf(player.getUUID()).orElse(FactionRole.MEMBER);
        if (!role.isAtLeast(FactionRole.OFFICER)) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.role_cannot_manage_members"),
                    view(player, tablePos)
            );
        }
        ServerPlayer target = player.getServer().getPlayerList().getPlayerByName(targetName.trim());
        if (target == null) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.duel.error.player_offline"),
                    view(player, tablePos)
            );
        }
        if (target.getUUID().equals(player.getUUID())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.command.faction.invite.self"),
                    view(player, tablePos)
            );
        }
        if (manager.getFactionForMember(target.getUUID()).isPresent()) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.player_already_member"),
                    view(player, tablePos)
            );
        }
        if (faction.memberCount() >= FactionManager.MAX_FACTION_MEMBERS) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.faction_full"),
                    view(player, tablePos)
            );
        }

        PendingFactionInvites.PutResult inviteResult = PendingFactionInvites.put(
                player.getServer(),
                faction.id(),
                player.getUUID(),
                target.getUUID()
        );
        if (inviteResult != PendingFactionInvites.PutResult.CREATED) {
            Component error = inviteResult == PendingFactionInvites.PutResult.FACTION_FULL
                    ? Component.translatable("kingdoms.error.faction_full")
                    : Component.translatable("kingdoms.error.faction_data_not_found");
            return FactionServerHooks.Result.denied(error, view(player, tablePos));
        }
        FactionServerHooks.pushInviteBadge(target);
        FactionServerHooks.sendNotice(
                target,
                Component.translatable(
                        "kingdoms.command.faction.invite.received",
                        player.getGameProfile().getName(),
                        faction.name()
                ),
                true
        );
        return new FactionServerHooks.Result(
                true,
                Component.translatable(
                        "kingdoms.command.faction.invite.sent",
                        target.getGameProfile().getName(),
                        faction.name()
                ),
                view(player, tablePos)
        );
    }

    @Override
    public FactionServerHooks.Result transfer(ServerPlayer player, BlockPos tablePos, String targetName) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return notInFaction(player, tablePos);
        }
        if (!faction.ownerId().equals(player.getUUID())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.leader_settings_only"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return otherFactionTable(player, tablePos);
        }
        ServerPlayer target = player.getServer().getPlayerList().getPlayerByName(targetName.trim());
        if (target == null) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.duel.error.player_offline"),
                    view(player, tablePos)
            );
        }
        if (target.getUUID().equals(player.getUUID())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.self_action"),
                    view(player, tablePos)
            );
        }

        FactionManager.OperationResult result = manager.transferLeadership(faction.id(), target.getUUID());
        if (!result.successful()) {
            return FactionServerHooks.Result.denied(message(result.status()), view(player, tablePos));
        }
        target.sendSystemMessage(Component.translatable(
                "kingdoms.command.faction.leadership.received",
                faction.name()
        ));
        return new FactionServerHooks.Result(
                true,
                Component.translatable(
                        "kingdoms.command.faction.leadership.transferred",
                        target.getGameProfile().getName()
                ),
                view(player, tablePos)
        );
    }

    @Override
    public FactionServerHooks.Result requestAlliance(
            ServerPlayer player,
            BlockPos tablePos,
            String targetFactionName
    ) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return notInFaction(player, tablePos);
        }
        if (!faction.ownerId().equals(player.getUUID())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.leader_settings_only"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return otherFactionTable(player, tablePos);
        }
        Faction target = manager.getFactionByName(targetFactionName.trim()).orElse(null);
        if (target == null) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.faction_not_found"),
                    view(player, tablePos)
            );
        }
        if (target.id().equals(faction.id())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.invalid_alliance"),
                    view(player, tablePos)
            );
        }
        if (manager.areAllied(faction.id(), target.id())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.already_allied"),
                    view(player, tablePos)
            );
        }
        if (WarManager.get(player.getServer()).areAtWar(faction.id(), target.id())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.alliance_at_war"),
                    view(player, tablePos)
            );
        }
        if (PendingAllianceRequests.find(player.getServer(), faction.id(), target.id()).isPresent()) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.alliance_request_pending"),
                    view(player, tablePos)
            );
        }

        PendingAllianceRequests.put(player.getServer(), faction.id(), player.getUUID(), target.id());
        ServerPlayer targetLeader = player.getServer().getPlayerList().getPlayer(target.ownerId());
        if (targetLeader != null) {
            FactionServerHooks.pushInviteBadge(targetLeader);
        }
        FactionServerHooks.sendNoticeToFaction(
                player,
                faction,
                Component.translatable("kingdoms.alliance.request.sent", target.name()),
                true
        );
        FactionServerHooks.sendNoticeToFaction(
                player,
                target,
                Component.translatable("kingdoms.alliance.request.received", faction.name()),
                true
        );
        return new FactionServerHooks.Result(true, Component.empty(), view(player, tablePos));
    }

    @Override
    public FactionServerHooks.Result breakAlliance(
            ServerPlayer player,
            BlockPos tablePos,
            String targetFactionName
    ) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return notInFaction(player, tablePos);
        }
        if (!faction.ownerId().equals(player.getUUID())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.leader_settings_only"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return otherFactionTable(player, tablePos);
        }
        Faction target = manager.getFactionByName(targetFactionName.trim()).orElse(null);
        if (target == null) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.faction_not_found"),
                    view(player, tablePos)
            );
        }
        FactionManager.OperationResult result = manager.breakAlliance(faction.id(), target.id());
        if (!result.successful()) {
            return FactionServerHooks.Result.denied(message(result.status()), view(player, tablePos));
        }
        PendingAllianceRequests.removeBetween(player.getServer(), faction.id(), target.id());
        Component notice = Component.translatable("kingdoms.alliance.broken", faction.name(), target.name());
        FactionServerHooks.sendNoticeToFaction(player, faction, notice, true);
        FactionServerHooks.sendNoticeToFaction(player, target, notice, true);
        return new FactionServerHooks.Result(true, Component.empty(), view(player, tablePos));
    }

    @Override
    public FactionServerHooks.Result declareWar(
            ServerPlayer player,
            BlockPos tablePos,
            String targetFactionName,
            String warType,
            String reason
    ) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return notInFaction(player, tablePos);
        }
        if (!faction.ownerId().equals(player.getUUID())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.leader_settings_only"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return otherFactionTable(player, tablePos);
        }
        Faction target = manager.getFactionByName(targetFactionName.trim()).orElse(null);
        if (target == null) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.faction_not_found"),
                    view(player, tablePos)
            );
        }
        if (manager.areAllied(faction.id(), target.id())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.war_with_ally"),
                    view(player, tablePos)
            );
        }

        WarManager.DeclareResult result = WarManager.get(player.getServer()).declareWar(
                player.getServer(),
                faction.id(),
                target.id(),
                com.geydev.kalfactions.war.WarType.fromIdOrDefault(warType),
                reason,
                player.getServer().overworld().getGameTime()
        );
        Component message = switch (result) {
            case SUCCESS -> Component.translatable("kingdoms.command.faction.war.declared", target.name());
            case SAME_FACTION -> Component.translatable("kingdoms.command.faction.war.same_faction");
            case ATTACKER_BUSY -> Component.translatable("kingdoms.command.faction.war.attacker_busy");
            case ATTACKER_COOLDOWN -> Component.translatable(
                    "kingdoms.command.faction.war.cooldown",
                    WarManager.get(player.getServer()).declareCooldownRemainingHours(faction.id()));
            case DEFENDER_BUSY -> Component.translatable("kingdoms.command.faction.war.defender_busy", target.name());
            case DEFENDER_OFFLINE -> Component.translatable(
                    "kingdoms.command.faction.war.defender_offline", target.name());
        };
        return new FactionServerHooks.Result(
                result == WarManager.DeclareResult.SUCCESS,
                message,
                view(player, tablePos)
        );
    }

    @Override
    public FactionServerHooks.Result joinWar(ServerPlayer player, BlockPos tablePos, String allyFactionName) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return notInFaction(player, tablePos);
        }
        if (!faction.ownerId().equals(player.getUUID())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.leader_settings_only"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return otherFactionTable(player, tablePos);
        }
        Faction ally = manager.getFactionByName(allyFactionName.trim()).orElse(null);
        if (ally == null) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.faction_not_found"),
                    view(player, tablePos)
            );
        }
        WarManager.JoinResult result = WarManager.get(player.getServer()).joinWar(
                player.getServer(),
                faction.id(),
                ally.id()
        );
        Component message = switch (result) {
            case SUCCESS -> Component.translatable("kingdoms.command.faction.war.joined", ally.name());
            case SAME_FACTION -> Component.translatable("kingdoms.command.faction.war.same_faction");
            case NOT_ALLIED -> Component.translatable("kingdoms.error.war_join_not_allied", ally.name());
            case ALLY_NOT_DEFENDING -> Component.translatable("kingdoms.error.war_join_not_defending", ally.name());
            case ALLIED_WITH_ENEMY -> Component.translatable("kingdoms.error.war_join_allied_enemy");
            case JOINER_BUSY -> Component.translatable("kingdoms.error.war_join_busy");
        };
        return new FactionServerHooks.Result(
                result == WarManager.JoinResult.SUCCESS,
                message,
                view(player, tablePos)
        );
    }

    @Override
    public FactionServerHooks.Result endWar(ServerPlayer player, BlockPos tablePos) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return notInFaction(player, tablePos);
        }
        if (!faction.ownerId().equals(player.getUUID())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.leader_settings_only"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return otherFactionTable(player, tablePos);
        }
        if (WarManager.get(player.getServer()).endWarForFaction(player.getServer(), faction.id()).isEmpty()) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.command.faction.war.not_active"),
                    view(player, tablePos)
            );
        }
        return new FactionServerHooks.Result(
                true,
                Component.translatable("kingdoms.command.faction.war.ended"),
                view(player, tablePos)
        );
    }

    @Override
    public FactionServerHooks.Result surrenderWar(ServerPlayer player, BlockPos tablePos) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return notInFaction(player, tablePos);
        }
        if (!faction.ownerId().equals(player.getUUID())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.leader_settings_only"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return otherFactionTable(player, tablePos);
        }
        if (WarManager.get(player.getServer()).surrender(player.getServer(), faction.id()).isEmpty()) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.command.faction.war.not_active"),
                    view(player, tablePos)
            );
        }
        return new FactionServerHooks.Result(
                true,
                Component.translatable("kingdoms.command.faction.war.surrendered"),
                view(player, tablePos)
        );
    }

    @Override
    public FactionServerHooks.Result mapSetClaims(ServerPlayer player, boolean claimed, List<Long> packedChunks) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return notice(false, Component.translatable("kingdoms.error.not_in_faction"));
        }
        FactionRole role = faction.roleOf(player.getUUID()).orElse(FactionRole.MEMBER);
        if (!role.canManageClaims()) {
            return notice(false, Component.translatable("kingdoms.error.role_cannot_change_claims"));
        }

        int radius = ModConfigSpec.CLAIM_SYNC_RADIUS_CHUNKS.get();
        ChunkPos center = player.chunkPosition();
        SanctuaryManager sanctuary = SanctuaryManager.get(player.serverLevel());
        Set<ChunkPos> pending = new LinkedHashSet<>();
        for (Long packed : packedChunks) {
            ChunkPos pos = new ChunkPos(packed);
            if (claimed && sanctuary.isSanctuary(ClaimKey.of(player.serverLevel(), pos))) {
                continue;
            }
            if (Math.abs(pos.x - center.x) <= radius && Math.abs(pos.z - center.z) <= radius) {
                pending.add(pos);
            }
            if (pending.size() >= FactionPayloads.C2SMapSetClaims.MAX_CHUNKS) {
                break;
            }
        }
        if (pending.isEmpty()) {
            return notice(false, Component.translatable("kingdoms.error.claim_outside_map"));
        }

        int applied = 0;
        long moved = 0L;
        FactionManager.Status lastFailure = null;
        boolean progress = true;
        while (progress && !pending.isEmpty()) {
            progress = false;
            for (Iterator<ChunkPos> iterator = pending.iterator(); iterator.hasNext(); ) {
                ChunkPos pos = iterator.next();
                ClaimKey key = ClaimKey.of(player.serverLevel(), pos);
                FactionManager.OperationResult result = claimed
                        ? manager.claim(faction.id(), key, player.getUUID())
                        : manager.unclaim(faction.id(), key);
                if (result.successful()) {
                    applied++;
                    moved += result.amount();
                    iterator.remove();
                    progress = true;
                } else {
                    lastFailure = result.status();
                }
            }
        }

        if (applied == 0) {
            return notice(false, message(lastFailure == null ? FactionManager.Status.FACTION_NOT_FOUND : lastFailure));
        }

        IntegrationManager.refreshFromServer(player.getServer());
        ClaimSyncManager.resync(player);

        Component summary = Component.translatable(
                claimed ? "kingdoms.map_claim.claimed" : "kingdoms.map_claim.unclaimed",
                applied,
                NumismaticsEconomy.format(moved)
        );
        if (pending.isEmpty() || lastFailure == null) {
            return notice(true, summary);
        }
        return notice(true, Component.empty().append(summary).append(" ").append(message(lastFailure)));
    }

    private static FactionServerHooks.Result notice(boolean successful, Component message) {
        return new FactionServerHooks.Result(successful, message, null);
    }

    private FactionServerHooks.Result notInFaction(ServerPlayer player, BlockPos tablePos) {
        return FactionServerHooks.Result.denied(
                Component.translatable("kingdoms.error.not_in_faction"),
                view(player, tablePos)
        );
    }

    private FactionServerHooks.Result otherFactionTable(ServerPlayer player, BlockPos tablePos) {
        return FactionServerHooks.Result.denied(
                Component.translatable("kingdoms.error.table_other_faction"),
                view(player, tablePos)
        );
    }

    /**
     * Mirrors the {@code /f} role gating: the actor must hold at least {@code minimumActorRole},
     * cannot target themselves, the target must be a faction member, and only the leader may
     * manage officers. Returns {@code null} when the action is allowed, otherwise a reason.
     */
    private static Component validateMemberAction(
            Faction faction,
            UUID actorId,
            UUID targetId,
            FactionRole minimumActorRole
    ) {
        FactionRole actorRole = faction.roleOf(actorId).orElse(null);
        if (actorRole == null || !actorRole.isAtLeast(minimumActorRole)) {
            return Component.translatable("kingdoms.error.role_cannot_manage_members");
        }
        if (actorId.equals(targetId)) {
            return Component.translatable("kingdoms.error.self_action");
        }
        FactionRole targetRole = faction.roleOf(targetId).orElse(null);
        if (targetRole == null) {
            return Component.translatable("kingdoms.error.target_not_in_faction");
        }
        if (actorRole != FactionRole.LEADER && targetRole.isAtLeast(FactionRole.OFFICER)) {
            return Component.translatable("kingdoms.error.manage_officers");
        }
        return null;
    }

    private FactionServerHooks.Result result(
            FactionManager.OperationResult operation,
            ServerPlayer player,
            BlockPos tablePos
    ) {
        FactionSnapshot snapshot = view(player, tablePos);
        if (operation.successful()) {
            return FactionServerHooks.Result.success(snapshot);
        }
        return FactionServerHooks.Result.denied(message(operation.status()), snapshot);
    }

    private static List<FactionSnapshot.Member> members(ServerPlayer player, Faction faction) {
        return faction.members().values().stream()
                .sorted(Comparator
                        .comparing((FactionMember member) -> member.role().ordinal()).reversed()
                        .thenComparing(member -> playerName(player, member.playerId()), String.CASE_INSENSITIVE_ORDER))
                .map(member -> new FactionSnapshot.Member(
                        member.playerId(),
                        playerName(player, member.playerId()),
                        roleName(member.role())
                ))
                .toList();
    }

    private static List<FactionSnapshot.Claim> claims(
            ServerPlayer player,
            FactionManager manager,
            UUID ownFactionId,
            int ownColor,
            ChunkPos center
    ) {
        List<FactionSnapshot.Claim> claims = new ArrayList<>();
        for (int x = center.x - MAP_RADIUS; x <= center.x + MAP_RADIUS; x++) {
            for (int z = center.z - MAP_RADIUS; z <= center.z + MAP_RADIUS; z++) {
                Faction faction = manager
                        .getFactionAt(player.level().dimension(), new ChunkPos(x, z))
                        .orElse(null);
                if (faction == null) {
                    continue;
                }
                boolean own = faction.id().equals(ownFactionId);
                claims.add(new FactionSnapshot.Claim(
                        x,
                        z,
                        own ? ownColor : faction.color(),
                        faction.name(),
                        own
                ));
            }
        }
        return List.copyOf(claims);
    }

    private static boolean nearSanctuary(ServerPlayer player, ChunkPos center) {
        SanctuaryManager sanctuary = SanctuaryManager.get(player.serverLevel());
        var dimension = player.serverLevel().dimension();
        int radius = ModConfigSpec.STARTER_CLAIM_SIZE.get() / 2 + 3;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (sanctuary.isSanctuary(new ClaimKey(dimension, center.x + dx, center.z + dz))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean canUseBoundTable(ServerPlayer player, BlockPos tablePos, UUID factionId) {
        UUID boundFactionId = boundFactionId(player, tablePos);
        return boundFactionId == null || boundFactionId.equals(factionId);
    }

    private static UUID boundFactionId(ServerPlayer player, BlockPos tablePos) {
        if (player.level().getBlockEntity(tablePos) instanceof FactionTableBlockEntity table) {
            return table.getFactionId();
        }
        return null;
    }

    private static void updateTableMetadata(
            ServerPlayer player,
            BlockPos tablePos,
            UUID factionId,
            int color
    ) {
        if (player.level().getBlockEntity(tablePos) instanceof FactionTableBlockEntity table) {
            table.setFactionId(factionId);
            table.setFactionColor(color);
        }
    }

    private static String playerName(ServerPlayer player, UUID playerId) {
        ServerPlayer online = player.getServer().getPlayerList().getPlayer(playerId);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return player.getServer().getProfileCache()
                .get(playerId)
                .map(profile -> profile.getName())
                .orElse(playerId.toString().substring(0, 8));
    }

    private static String roleName(FactionRole role) {
        String lower = role.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static Component roleComponent(FactionRole role) {
        return Component.translatable("kingdoms.role." + role.name().toLowerCase(Locale.ROOT));
    }

    private static Component enabledState(boolean enabled) {
        return Component.translatable(enabled
                ? "kingdoms.state.enabled"
                : "kingdoms.state.disabled");
    }

    private static Component message(FactionManager.Status status) {
        String key = switch (status) {
            case SUCCESS -> null;
            case INVALID_NAME -> "kingdoms.error.invalid_name";
            case NAME_TAKEN -> "kingdoms.error.name_taken";
            case PLAYER_ALREADY_MEMBER -> "kingdoms.error.already_in_faction";
            case FACTION_FULL -> "kingdoms.error.faction_full";
            case CLAIM_ALREADY_OWNED -> "kingdoms.error.claim_already_owned";
            case CLAIM_NOT_OWNED -> "kingdoms.error.claim_not_owned";
            case CLAIM_NOT_ADJACENT -> "kingdoms.error.claim_not_adjacent";
            case CLAIM_WOULD_DISCONNECT -> "kingdoms.error.claim_would_disconnect";
            case CLAIM_PROTECTED -> "kingdoms.error.claim_protected";
            case INSUFFICIENT_FUNDS -> "kingdoms.error.insufficient_funds";
            case TREASURY_OVERFLOW -> "kingdoms.error.treasury_overflow";
            case PLAYER_NOT_MEMBER -> "kingdoms.error.not_member_of_faction";
            case FACTION_NOT_FOUND -> "kingdoms.error.faction_data_not_found";
            case OWNER_CANNOT_LEAVE -> "kingdoms.error.owner_cannot_leave";
            case INVALID_ALLIANCE -> "kingdoms.error.invalid_alliance";
            case NOT_ALLIED -> "kingdoms.error.not_allied";
            default -> "kingdoms.error.faction_action_rejected";
        };
        if (key == null) {
            return Component.empty();
        }
        return Component.translatable(key);
    }
}
