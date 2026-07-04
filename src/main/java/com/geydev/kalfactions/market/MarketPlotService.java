package com.geydev.kalfactions.market;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.registry.ModDataComponents;
import com.geydev.kalfactions.sanctuary.SanctuaryManager;
import dev.ithundxr.createnumismatics.Numismatics;
import dev.ithundxr.createnumismatics.content.backend.BankAccount;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MarketPlotService {
    public static final int MAX_SPAN_XZ = 64;
    public static final int MAX_SPAN_Y = 64;
    public static final int MAX_VOLUME = 65536;
    public static final long MAX_PRICE = 1_000_000_000L;

    public static boolean hasAccess(MarketPlot plot, ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (plot.isOwnedBy(playerId) || plot.isTrustedPlayer(playerId)) {
            return true;
        }
        if (plot.trustedFactions().isEmpty()) {
            return false;
        }
        return FactionManager.get(player.serverLevel())
                .getFactionIdForMember(playerId)
                .map(plot::isTrustedFaction)
                .orElse(false);
    }

    public static boolean canEdit(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return MarketPlotManager.get(level)
                .plotAt(level.dimension(), pos)
                .map(plot -> hasAccess(plot, player))
                .orElse(false);
    }

    public static Optional<Component> validateBox(ServerLevel level, BoundingBox box) {
        if (box.getXSpan() > MAX_SPAN_XZ || box.getZSpan() > MAX_SPAN_XZ || box.getYSpan() > MAX_SPAN_Y) {
            return Optional.of(Component.translatable(
                    "kingdoms.plot.create.too_large", MAX_SPAN_XZ, MAX_SPAN_Y));
        }
        long volume = (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
        if (volume > MAX_VOLUME) {
            return Optional.of(Component.translatable("kingdoms.plot.create.too_large_volume", MAX_VOLUME));
        }
        SanctuaryManager sanctuary = SanctuaryManager.get(level);
        for (int chunkX = box.minX() >> 4; chunkX <= box.maxX() >> 4; chunkX++) {
            for (int chunkZ = box.minZ() >> 4; chunkZ <= box.maxZ() >> 4; chunkZ++) {
                if (!sanctuary.isSanctuary(new ClaimKey(level.dimension(), chunkX, chunkZ))) {
                    return Optional.of(Component.translatable("kingdoms.plot.create.outside_sanctuary"));
                }
            }
        }
        if (MarketPlotManager.get(level).intersectsAny(level.dimension(), box)) {
            return Optional.of(Component.translatable("kingdoms.plot.create.overlaps"));
        }
        return Optional.empty();
    }

    public static MarketPlot create(ServerLevel level, BoundingBox box, long price) {
        MarketPlotManager manager = MarketPlotManager.get(level);
        MarketPlot plot = manager.create(level.dimension(), box, price);
        plot.setSnapshot(PlotSnapshots.capture(level, box));
        manager.markChanged();
        syncAll(level.getServer());
        return plot;
    }

    public static void openScreen(ServerPlayer player, MarketPlot plot) {
        long buyback = buybackAmount(plot);
        PacketDistributor.sendToPlayer(player, new MarketPayloads.S2COpenPlotScreen(
                plot.id(),
                (byte) plot.state().ordinal(),
                plot.isOwnedBy(player.getUUID()),
                plot.askingPrice(),
                buyback,
                plot.ownerName()
        ));
    }

    public static void buy(ServerPlayer player, int plotId) {
        ServerLevel level = player.serverLevel();
        MarketPlotManager manager = MarketPlotManager.get(level);
        MarketPlot plot = manager.byId(plotId).orElse(null);
        if (plot == null || !plot.dimension().equals(level.dimension()) || !isNear(player, plot)) {
            return;
        }
        MarketPlot.State state = plot.state();
        if (state == MarketPlot.State.OWNED || plot.isOwnedBy(player.getUUID())) {
            return;
        }
        long price = plot.askingPrice();
        if (price <= 0L || price > MAX_PRICE) {
            return;
        }
        if (!charge(player, price)) {
            player.displayClientMessage(Component.translatable(
                    "kingdoms.plot.buy.insufficient", NumismaticsEconomy.format(price)), false);
            return;
        }
        UUID previousOwner = plot.owner();
        String previousOwnerName = plot.ownerName();
        if (state == MarketPlot.State.RESALE && previousOwner != null) {
            BankAccount account = Numismatics.BANK.getOrCreateAccount(previousOwner, BankAccount.Type.PLAYER);
            account.deposit((int) price);
            ServerPlayer seller = level.getServer().getPlayerList().getPlayer(previousOwner);
            if (seller != null) {
                seller.displayClientMessage(Component.translatable(
                        "kingdoms.plot.resale.sold",
                        plot.id(),
                        player.getGameProfile().getName(),
                        NumismaticsEconomy.format(price)), false);
            }
        }
        plot.setOwner(player.getUUID(), player.getGameProfile().getName());
        manager.markChanged();
        syncAll(level.getServer());
        player.displayClientMessage(Component.translatable(
                "kingdoms.plot.buy.success", plot.id(), NumismaticsEconomy.format(price)), false);
        if (state == MarketPlot.State.RESALE && !previousOwnerName.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "kingdoms.plot.buy.from_player", previousOwnerName), false);
        }
    }

    public static void manage(ServerPlayer player, int plotId, byte action, long price) {
        ServerLevel level = player.serverLevel();
        MarketPlotManager manager = MarketPlotManager.get(level);
        MarketPlot plot = manager.byId(plotId).orElse(null);
        if (plot == null || !plot.isOwnedBy(player.getUUID()) || !isNear(player, plot)) {
            return;
        }
        switch (action) {
            case MarketPayloads.C2SManagePlot.ACTION_LIST_RESALE -> {
                if (price <= 0L || price > MAX_PRICE) {
                    return;
                }
                plot.setResalePrice(price);
                manager.markChanged();
                syncAll(level.getServer());
                player.displayClientMessage(Component.translatable(
                        "kingdoms.plot.resale.listed", plot.id(), NumismaticsEconomy.format(price)), false);
            }
            case MarketPayloads.C2SManagePlot.ACTION_CANCEL_RESALE -> {
                plot.setResalePrice(0L);
                manager.markChanged();
                syncAll(level.getServer());
                player.displayClientMessage(Component.translatable(
                        "kingdoms.plot.resale.cancelled", plot.id()), false);
            }
            case MarketPayloads.C2SManagePlot.ACTION_SELL_TO_SERVER -> {
                long refund = buybackAmount(plot);
                if (!NumismaticsEconomy.canGive(refund)) {
                    return;
                }
                release(level, plot);
                NumismaticsEconomy.give(player, refund);
                player.displayClientMessage(Component.translatable(
                        "kingdoms.plot.buyback.success", plot.id(), NumismaticsEconomy.format(refund)), false);
            }
            default -> {
            }
        }
    }

    public static void release(ServerLevel level, MarketPlot plot) {
        ServerLevel plotLevel = level.getServer().getLevel(plot.dimension());
        if (plotLevel != null) {
            PlotSnapshots.restore(plotLevel, plot.box(), plot.snapshot());
        }
        plot.setOwner(null, null);
        MarketPlotManager.get(level).markChanged();
        syncAll(level.getServer());
    }

    public static long buybackAmount(MarketPlot plot) {
        return (long) Math.floor(plot.basePrice() * ModConfigSpec.MARKET_BUYBACK_PERCENT.get());
    }

    public static boolean charge(ServerPlayer player, long amount) {
        long inventoryTotal = NumismaticsEconomy.balance(player);
        if (inventoryTotal >= amount) {
            NumismaticsEconomy.Payment payment = NumismaticsEconomy.preparePayment(player, amount);
            return payment.ready() && NumismaticsEconomy.commitPayment(player, payment);
        }
        long fromBank = amount - inventoryTotal;
        if (NumismaticsEconomy.bankBalance(player) < fromBank) {
            return false;
        }
        if (inventoryTotal > 0L) {
            NumismaticsEconomy.Payment payment = NumismaticsEconomy.preparePayment(player, inventoryTotal);
            if (!payment.ready() || !NumismaticsEconomy.commitPayment(player, payment)) {
                return false;
            }
        }
        return NumismaticsEconomy.deductBank(player, fromBank) == fromBank;
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncTo(player);
        }
    }

    public static void syncTo(ServerPlayer player) {
        ResourceKey<Level> dimension = player.level().dimension();
        List<MarketPayloads.PlotEntry> entries = new ArrayList<>();
        for (MarketPlot plot : MarketPlotManager.get(player.serverLevel()).plotsIn(dimension)) {
            if (entries.size() >= MarketPayloads.MAX_PLOTS) {
                break;
            }
            BoundingBox box = plot.box();
            entries.add(new MarketPayloads.PlotEntry(
                    plot.id(),
                    box.minX(), box.minY(), box.minZ(),
                    box.maxX(), box.maxY(), box.maxZ(),
                    (byte) plot.state().ordinal(),
                    plot.askingPrice(),
                    Optional.ofNullable(plot.owner()),
                    plot.ownerName(),
                    plot.owner() != null && hasAccess(plot, player)
            ));
        }
        PacketDistributor.sendToPlayer(player, new MarketPayloads.S2CSyncPlots(dimension.location(), entries));
    }

    public static void requestTrust(ServerPlayer player, int plotId) {
        MarketPlot plot = ownedPlot(player, plotId);
        if (plot != null) {
            sendTrustState(player, plot);
        }
    }

    public static void editTrust(ServerPlayer player, int plotId, boolean add, boolean faction, UUID targetId) {
        MarketPlot plot = ownedPlot(player, plotId);
        if (plot == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        boolean changed;
        if (faction) {
            if (add) {
                changed = FactionManager.get(level).getFactionById(targetId).isPresent()
                        && plot.addTrustedFaction(targetId);
            } else {
                changed = plot.removeTrustedFaction(targetId);
            }
        } else {
            if (add) {
                ServerPlayer target = level.getServer().getPlayerList().getPlayer(targetId);
                changed = target != null
                        && !target.getUUID().equals(plot.owner())
                        && plot.addTrustedPlayer(target.getUUID(), target.getGameProfile().getName());
            } else {
                changed = plot.removeTrustedPlayer(targetId);
            }
        }
        if (changed) {
            MarketPlotManager.get(level).markChanged();
            syncAll(level.getServer());
        }
        sendTrustState(player, plot);
    }

    private static MarketPlot ownedPlot(ServerPlayer player, int plotId) {
        MarketPlot plot = MarketPlotManager.get(player.serverLevel()).byId(plotId).orElse(null);
        if (plot == null || !plot.isOwnedBy(player.getUUID()) || !isNear(player, plot)) {
            return null;
        }
        return plot;
    }

    private static void sendTrustState(ServerPlayer player, MarketPlot plot) {
        ServerLevel level = player.serverLevel();
        FactionManager factions = FactionManager.get(level);

        List<MarketPayloads.TrustEntry> trustedPlayers = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : plot.trustedPlayers().entrySet()) {
            trustedPlayers.add(new MarketPayloads.TrustEntry(entry.getKey(), entry.getValue(), 0));
        }

        List<MarketPayloads.TrustEntry> trustedFactions = new ArrayList<>();
        for (UUID factionId : plot.trustedFactions()) {
            Faction resolved = factions.getFactionById(factionId).orElse(null);
            trustedFactions.add(new MarketPayloads.TrustEntry(
                    factionId,
                    resolved == null ? "???" : resolved.name(),
                    resolved == null ? 0x777777 : resolved.color()
            ));
        }

        List<MarketPayloads.PlayerCandidate> playerCandidates = new ArrayList<>();
        for (ServerPlayer candidate : level.getServer().getPlayerList().getPlayers()) {
            UUID candidateId = candidate.getUUID();
            if (candidateId.equals(plot.owner()) || plot.isTrustedPlayer(candidateId)) {
                continue;
            }
            Faction candidateFaction = factions.getFactionForMember(candidateId).orElse(null);
            if (candidateFaction != null && plot.isTrustedFaction(candidateFaction.id())) {
                continue;
            }
            playerCandidates.add(new MarketPayloads.PlayerCandidate(
                    candidateId,
                    candidate.getGameProfile().getName(),
                    candidateFaction == null ? "" : candidateFaction.name()
            ));
        }

        List<MarketPayloads.TrustEntry> factionCandidates = new ArrayList<>();
        for (Faction candidate : factions.factions()) {
            if (!plot.isTrustedFaction(candidate.id())) {
                factionCandidates.add(new MarketPayloads.TrustEntry(
                        candidate.id(), candidate.name(), candidate.color()));
            }
        }

        PacketDistributor.sendToPlayer(player, new MarketPayloads.S2CPlotTrustState(
                plot.id(), trustedPlayers, trustedFactions, playerCandidates, factionCandidates));
    }

    public static void adjustSelection(ServerPlayer player, byte faceIndex, byte delta) {
        if (!player.hasPermissions(2) || delta == 0 || faceIndex < 0 || faceIndex >= Direction.values().length) {
            return;
        }
        ItemStack wand = ItemStack.EMPTY;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof com.geydev.kalfactions.item.PlotWandItem) {
                wand = held;
                break;
            }
        }
        PlotSelection selection = wand.isEmpty() ? null : wand.get(ModDataComponents.PLOT_SELECTION);
        if (selection == null || !selection.isComplete() || !selection.matchesDimension(player.level())) {
            return;
        }
        BlockPos first = selection.first();
        BlockPos second = selection.second().orElseThrow();
        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxX = Math.max(first.getX(), second.getX());
        int maxY = Math.max(first.getY(), second.getY());
        int maxZ = Math.max(first.getZ(), second.getZ());
        Direction face = Direction.values()[faceIndex];
        int step = delta > 0 ? 1 : -1;
        switch (face) {
            case EAST -> maxX = Math.max(minX, Math.min(minX + MAX_SPAN_XZ - 1, maxX + step));
            case WEST -> minX = Math.min(maxX, Math.max(maxX - MAX_SPAN_XZ + 1, minX - step));
            case UP -> maxY = Math.max(minY, Math.min(minY + MAX_SPAN_Y - 1, maxY + step));
            case DOWN -> minY = Math.min(maxY, Math.max(maxY - MAX_SPAN_Y + 1, minY - step));
            case SOUTH -> maxZ = Math.max(minZ, Math.min(minZ + MAX_SPAN_XZ - 1, maxZ + step));
            case NORTH -> minZ = Math.min(maxZ, Math.max(maxZ - MAX_SPAN_XZ + 1, minZ - step));
        }
        int minBuild = player.level().getMinBuildHeight();
        int maxBuild = player.level().getMaxBuildHeight() - 1;
        minY = Math.max(minBuild, minY);
        maxY = Math.min(maxBuild, maxY);
        wand.set(ModDataComponents.PLOT_SELECTION, new PlotSelection(
                selection.dimension(),
                new BlockPos(minX, minY, minZ),
                Optional.of(new BlockPos(maxX, maxY, maxZ))
        ));
        player.displayClientMessage(Component.translatable(
                "kingdoms.plot.wand.size",
                maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1), true);
    }

    private static boolean isNear(ServerPlayer player, MarketPlot plot) {
        BoundingBox box = plot.box();
        double centerX = (box.minX() + box.maxX() + 1) / 2.0D;
        double centerY = (box.minY() + box.maxY() + 1) / 2.0D;
        double centerZ = (box.minZ() + box.maxZ() + 1) / 2.0D;
        double reach = Math.max(box.getXSpan(), Math.max(box.getYSpan(), box.getZSpan())) / 2.0D + 16.0D;
        return player.distanceToSqr(centerX, centerY, centerZ) <= reach * reach;
    }

    private MarketPlotService() {
    }
}
