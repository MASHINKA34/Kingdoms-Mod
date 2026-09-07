package com.geydev.kalfactions.net;

import com.geydev.kalfactions.command.PendingAllianceRequests;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.FactionMember;
import com.geydev.kalfactions.faction.FactionRole;
import com.geydev.kalfactions.faction.InfluenceType;
import com.geydev.kalfactions.faction.ResearchCrystalCosts;
import com.geydev.kalfactions.registry.ModItems;
import com.geydev.kalfactions.war.WarManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

/**
 * Builds the read-only view of a faction that the table screen renders. Kept apart from
 * {@link FactionManagerService}, which only runs the operations that change that state.
 */
public final class FactionSnapshotFactory {
    private static final int MAP_RADIUS = 6;
    private static final int MAX_PIXEL_EMBLEM_REFS = 64;

    static FactionSnapshot view(ServerPlayer player, BlockPos tablePos) {
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
                PlayerNames.resolve(player, faction.ownerId()),
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
                faction.extraBonus() == null ? "" : faction.extraBonus().name(),
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
                faction.forceLoadedCount(),
                com.geydev.kalfactions.faction.ScienceLedger.get(player.serverLevel())
                        .grantedToday(faction.id(), System.currentTimeMillis()),
                ModConfigSpec.SCIENCE_DAILY_CAP.getAsLong()
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

    public static List<String> bonusNames(Faction faction) {
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

    private static List<FactionSnapshot.Member> members(ServerPlayer player, Faction faction) {
        return faction.members().values().stream()
                .sorted(Comparator
                        .comparing((FactionMember member) -> member.role().ordinal()).reversed()
                        .thenComparing(member -> PlayerNames.resolve(player, member.playerId()), String.CASE_INSENSITIVE_ORDER))
                .map(member -> new FactionSnapshot.Member(
                        member.playerId(),
                        PlayerNames.resolve(player, member.playerId()),
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

    private static String roleName(FactionRole role) {
        String lower = role.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private FactionSnapshotFactory() {
    }
}
