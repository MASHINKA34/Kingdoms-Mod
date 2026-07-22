package com.geydev.kalfactions.net;

import com.geydev.kalfactions.faction.FactionManager;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record FactionSnapshot(
        BlockPos tablePos,
        UUID factionId,
        String name,
        String ownerName,
        int color,
        boolean canManage,
        boolean canClaim,
        int centerChunkX,
        int centerChunkZ,
        int mapRadius,
        List<Member> members,
        List<Claim> claims,
        long treasury,
        long influence,
        long influenceScience,
        long influenceEconomic,
        long influenceMilitary,
        boolean internalPvp,
        long creationCost,
        UUID viewerId,
        boolean isOfficer,
        String warWith,
        long warDeclareCooldownSeconds,
        List<FactionRef> knownFactions,
        List<FactionRef> allianceCandidates,
        List<FactionRef> allies,
        List<FactionRef> joinableAllies,
        List<OnlinePlayer> onlinePlayers,
        List<String> bonuses,
        List<Integer> emblem,
        String emblemUrl,
        List<String> completedResearch,
        String activeResearchNode,
        long activeResearchEndMillis,
        WarSpoils pendingWarSpoils,
        int claimCount,
        int forceLoadUsed
) {
    public static final UUID NO_FACTION = new UUID(0L, 0L);
    public static final int MAX_RESEARCH_NODES = 64;
    public static final int MAX_MEMBERS = FactionManager.MAX_FACTION_MEMBERS;
    public static final int MAX_CLAIMS = 1024;
    public static final int MAX_KNOWN_FACTIONS = 512;
    public static final int MAX_ONLINE_PLAYERS = 128;
    public static final int MAX_BONUSES = 8;
    public static final int EMBLEM_PIXELS = 256;
    public static final int MAX_EMBLEM_PIXELS = 1024;
    public static final int MAX_EMBLEM_URL = 256;

    public static boolean isValidEmblemSize(int size) {
        return size == EMBLEM_PIXELS || size == MAX_EMBLEM_PIXELS;
    }
    public static final StreamCodec<RegistryFriendlyByteBuf, FactionSnapshot> STREAM_CODEC = StreamCodec.of(
            FactionSnapshot::encode,
            FactionSnapshot::decode
    );

    public FactionSnapshot {
        tablePos = tablePos.immutable();
        factionId = factionId == null ? NO_FACTION : factionId;
        name = limit(name, 32);
        ownerName = limit(ownerName, 32);
        color &= 0xFFFFFF;
        mapRadius = Math.clamp(mapRadius, 2, 8);
        members = List.copyOf(members);
        claims = List.copyOf(claims);
        treasury = Math.max(0L, treasury);
        influence = Math.max(0L, influence);
        influenceScience = Math.max(0L, influenceScience);
        influenceEconomic = Math.max(0L, influenceEconomic);
        influenceMilitary = Math.max(0L, influenceMilitary);
        creationCost = Math.max(0L, creationCost);
        viewerId = viewerId == null ? NO_FACTION : viewerId;
        warWith = limit(warWith, 32);
        warDeclareCooldownSeconds = Math.max(0L, warDeclareCooldownSeconds);
        knownFactions = knownFactions == null ? List.of() : List.copyOf(knownFactions);
        allianceCandidates = allianceCandidates == null ? List.of() : List.copyOf(allianceCandidates);
        allies = allies == null ? List.of() : List.copyOf(allies);
        joinableAllies = joinableAllies == null ? List.of() : List.copyOf(joinableAllies);
        onlinePlayers = onlinePlayers == null ? List.of() : List.copyOf(onlinePlayers);
        bonuses = bonuses == null ? List.of() : List.copyOf(bonuses);
        emblem = emblem == null || !isValidEmblemSize(emblem.size()) ? List.of() : List.copyOf(emblem);
        emblemUrl = limit(emblemUrl, MAX_EMBLEM_URL);
        completedResearch = completedResearch == null ? List.of() : List.copyOf(completedResearch);
        activeResearchNode = activeResearchNode == null ? "" : limit(activeResearchNode, 32);
        activeResearchEndMillis = Math.max(0L, activeResearchEndMillis);
        pendingWarSpoils = pendingWarSpoils == null ? WarSpoils.EMPTY : pendingWarSpoils;
        claimCount = Math.max(0, claimCount);
        forceLoadUsed = Math.max(0, forceLoadUsed);
    }

    public static FactionSnapshot empty(BlockPos tablePos, int centerChunkX, int centerChunkZ, long creationCost) {
        return new FactionSnapshot(
                tablePos, NO_FACTION, "", "", 0x4E7A42, false, false,
                centerChunkX, centerChunkZ, 6, List.of(), List.of(),
                0L, 0L, 0L, 0L, 0L, false, creationCost, NO_FACTION, false,
                "", 0L, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "",
                List.of(), "", 0L, WarSpoils.EMPTY, 0, 0
        );
    }

    public boolean hasFaction() {
        return !NO_FACTION.equals(factionId);
    }

    public boolean isSelf(UUID playerId) {
        return viewerId != null && viewerId.equals(playerId);
    }

    public boolean isClaimedByCurrentFaction(int chunkX, int chunkZ) {
        return claims.stream().anyMatch(claim -> claim.chunkX == chunkX && claim.chunkZ == chunkZ && claim.own);
    }

    public boolean hasPendingWarSpoils() {
        return pendingWarSpoils.hasSpoils();
    }

    public Claim claimAt(int chunkX, int chunkZ) {
        return claims.stream()
                .filter(claim -> claim.chunkX == chunkX && claim.chunkZ == chunkZ)
                .findFirst()
                .orElse(null);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, FactionSnapshot snapshot) {
        buffer.writeBlockPos(snapshot.tablePos);
        buffer.writeUUID(snapshot.factionId);
        buffer.writeUtf(snapshot.name, 32);
        buffer.writeUtf(snapshot.ownerName, 32);
        buffer.writeInt(snapshot.color);
        buffer.writeBoolean(snapshot.canManage);
        buffer.writeBoolean(snapshot.canClaim);
        buffer.writeInt(snapshot.centerChunkX);
        buffer.writeInt(snapshot.centerChunkZ);
        buffer.writeByte(snapshot.mapRadius);
        writeBoundedList(buffer, snapshot.members, MAX_MEMBERS, Member::encode);
        writeBoundedList(buffer, snapshot.claims, MAX_CLAIMS, Claim::encode);
        buffer.writeLong(snapshot.treasury);
        buffer.writeLong(snapshot.influence);
        buffer.writeLong(snapshot.influenceScience);
        buffer.writeLong(snapshot.influenceEconomic);
        buffer.writeLong(snapshot.influenceMilitary);
        buffer.writeBoolean(snapshot.internalPvp);
        buffer.writeLong(snapshot.creationCost);
        buffer.writeUUID(snapshot.viewerId);
        buffer.writeBoolean(snapshot.isOfficer);
        buffer.writeUtf(snapshot.warWith, 32);
        buffer.writeLong(snapshot.warDeclareCooldownSeconds);
        writeBoundedList(buffer, snapshot.knownFactions, MAX_KNOWN_FACTIONS, FactionRef::encode);
        writeBoundedList(buffer, snapshot.allianceCandidates, MAX_KNOWN_FACTIONS, FactionRef::encode);
        writeBoundedList(buffer, snapshot.allies, MAX_KNOWN_FACTIONS, FactionRef::encode);
        writeBoundedList(buffer, snapshot.joinableAllies, MAX_KNOWN_FACTIONS, FactionRef::encode);
        writeBoundedList(buffer, snapshot.onlinePlayers, MAX_ONLINE_PLAYERS, OnlinePlayer::encode);
        writeBoundedList(
                buffer,
                snapshot.bonuses,
                MAX_BONUSES,
                (target, value) -> target.writeUtf(value, 24)
        );
        writeBoundedList(buffer, snapshot.emblem, MAX_EMBLEM_PIXELS, (target, value) -> target.writeInt(value));
        buffer.writeUtf(snapshot.emblemUrl, MAX_EMBLEM_URL);
        writeBoundedList(
                buffer,
                snapshot.completedResearch,
                MAX_RESEARCH_NODES,
                (target, value) -> target.writeUtf(value, 32)
        );
        buffer.writeUtf(snapshot.activeResearchNode, 32);
        buffer.writeLong(snapshot.activeResearchEndMillis);
        snapshot.pendingWarSpoils.encode(buffer);
        buffer.writeVarInt(snapshot.claimCount);
        buffer.writeVarInt(snapshot.forceLoadUsed);
    }

    private static FactionSnapshot decode(RegistryFriendlyByteBuf buffer) {
        BlockPos tablePos = buffer.readBlockPos();
        UUID factionId = buffer.readUUID();
        String name = buffer.readUtf(32);
        String ownerName = buffer.readUtf(32);
        int color = buffer.readInt();
        boolean canManage = buffer.readBoolean();
        boolean canClaim = buffer.readBoolean();
        int centerChunkX = buffer.readInt();
        int centerChunkZ = buffer.readInt();
        int mapRadius = buffer.readUnsignedByte();
        List<Member> members = readBoundedList(buffer, MAX_MEMBERS, Member::decode);
        List<Claim> claims = readBoundedList(buffer, MAX_CLAIMS, Claim::decode);
        long treasury = buffer.readLong();
        long influence = buffer.readLong();
        long influenceScience = buffer.readLong();
        long influenceEconomic = buffer.readLong();
        long influenceMilitary = buffer.readLong();
        boolean internalPvp = buffer.readBoolean();
        long creationCost = buffer.readLong();
        UUID viewerId = buffer.readUUID();
        boolean isOfficer = buffer.readBoolean();
        String warWith = buffer.readUtf(32);
        long warDeclareCooldownSeconds = buffer.readLong();
        List<FactionRef> knownFactions = readBoundedList(buffer, MAX_KNOWN_FACTIONS, FactionRef::decode);
        List<FactionRef> allianceCandidates = readBoundedList(buffer, MAX_KNOWN_FACTIONS, FactionRef::decode);
        List<FactionRef> allies = readBoundedList(buffer, MAX_KNOWN_FACTIONS, FactionRef::decode);
        List<FactionRef> joinableAllies = readBoundedList(buffer, MAX_KNOWN_FACTIONS, FactionRef::decode);
        List<OnlinePlayer> onlinePlayers = readBoundedList(buffer, MAX_ONLINE_PLAYERS, OnlinePlayer::decode);
        List<String> bonuses = readBoundedList(buffer, MAX_BONUSES, target -> target.readUtf(24));
        List<Integer> emblem = readBoundedList(buffer, MAX_EMBLEM_PIXELS, RegistryFriendlyByteBuf::readInt);
        String emblemUrl = buffer.readUtf(MAX_EMBLEM_URL);
        List<String> completedResearch = readBoundedList(buffer, MAX_RESEARCH_NODES, target -> target.readUtf(32));
        String activeResearchNode = buffer.readUtf(32);
        long activeResearchEndMillis = buffer.readLong();
        WarSpoils pendingWarSpoils = WarSpoils.decode(buffer);
        int claimCount = buffer.readVarInt();
        int forceLoadUsed = buffer.readVarInt();
        return new FactionSnapshot(
                tablePos, factionId, name, ownerName, color, canManage, canClaim,
                centerChunkX, centerChunkZ, mapRadius, members, claims,
                treasury, influence, influenceScience, influenceEconomic, influenceMilitary,
                internalPvp, creationCost, viewerId, isOfficer,
                warWith, warDeclareCooldownSeconds, knownFactions, allianceCandidates, allies, joinableAllies,
                onlinePlayers, bonuses, emblem, emblemUrl,
                completedResearch, activeResearchNode, activeResearchEndMillis, pendingWarSpoils,
                claimCount, forceLoadUsed
        );
    }

    private static <T> void writeBoundedList(
            RegistryFriendlyByteBuf buffer,
            List<T> values,
            int maxSize,
            ElementWriter<T> writer
    ) {
        int size = Math.min(values.size(), maxSize);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            writer.write(buffer, values.get(i));
        }
    }

    private static <T> List<T> readBoundedList(
            RegistryFriendlyByteBuf buffer,
            int maxSize,
            ElementReader<T> reader
    ) {
        int size = buffer.readVarInt();
        if (size < 0 || size > maxSize) {
            throw new DecoderException("Faction payload list size " + size + " exceeds " + maxSize);
        }
        java.util.ArrayList<T> values = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(reader.read(buffer));
        }
        return List.copyOf(values);
    }

    private static String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record FactionRef(UUID id, String name, int color, List<Integer> emblem, String emblemUrl) {
        public FactionRef {
            id = id == null ? NO_FACTION : id;
            name = limit(name, 32);
            color &= 0xFFFFFF;
            emblem = emblem == null || !isValidEmblemSize(emblem.size()) ? List.of() : List.copyOf(emblem);
            emblemUrl = limit(emblemUrl, MAX_EMBLEM_URL);
        }

        private static void encode(RegistryFriendlyByteBuf buffer, FactionRef ref) {
            buffer.writeUUID(ref.id);
            buffer.writeUtf(ref.name, 32);
            buffer.writeInt(ref.color);
            writeBoundedList(buffer, ref.emblem, MAX_EMBLEM_PIXELS, (target, value) -> target.writeInt(value));
            buffer.writeUtf(ref.emblemUrl, MAX_EMBLEM_URL);
        }

        private static FactionRef decode(RegistryFriendlyByteBuf buffer) {
            return new FactionRef(
                    buffer.readUUID(),
                    buffer.readUtf(32),
                    buffer.readInt(),
                    readBoundedList(buffer, MAX_EMBLEM_PIXELS, RegistryFriendlyByteBuf::readInt),
                    buffer.readUtf(MAX_EMBLEM_URL)
            );
        }
    }

    public record Member(UUID playerId, String name, String role) {
        public Member {
            name = limit(name, 32);
            role = limit(role, 24);
        }

        private static void encode(RegistryFriendlyByteBuf buffer, Member member) {
            buffer.writeUUID(member.playerId);
            buffer.writeUtf(member.name, 32);
            buffer.writeUtf(member.role, 24);
        }

        private static Member decode(RegistryFriendlyByteBuf buffer) {
            return new Member(buffer.readUUID(), buffer.readUtf(32), buffer.readUtf(24));
        }
    }

    public record WarSpoils(
            UUID spoilsId,
            String loserName,
            long money,
            long resourceOne,
            String resourceOneItem,
            long resourceTwo,
            String resourceTwoItem,
            long resourceThree,
            String resourceThreeItem
    ) {
        public static final WarSpoils EMPTY = new WarSpoils(NO_FACTION, "", 0L, 0L, "", 0L, "", 0L, "");

        public WarSpoils {
            spoilsId = spoilsId == null ? NO_FACTION : spoilsId;
            loserName = limit(loserName, 32);
            money = Math.max(0L, money);
            resourceOne = Math.max(0L, resourceOne);
            resourceTwo = Math.max(0L, resourceTwo);
            resourceThree = Math.max(0L, resourceThree);
            resourceOneItem = limit(resourceOneItem, 128);
            resourceTwoItem = limit(resourceTwoItem, 128);
            resourceThreeItem = limit(resourceThreeItem, 128);
        }

        public boolean hasSpoils() {
            return !NO_FACTION.equals(spoilsId);
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(spoilsId);
            buffer.writeUtf(loserName, 32);
            buffer.writeLong(money);
            buffer.writeLong(resourceOne);
            buffer.writeUtf(resourceOneItem, 128);
            buffer.writeLong(resourceTwo);
            buffer.writeUtf(resourceTwoItem, 128);
            buffer.writeLong(resourceThree);
            buffer.writeUtf(resourceThreeItem, 128);
        }

        private static WarSpoils decode(RegistryFriendlyByteBuf buffer) {
            return new WarSpoils(
                    buffer.readUUID(),
                    buffer.readUtf(32),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readUtf(128),
                    buffer.readLong(),
                    buffer.readUtf(128),
                    buffer.readLong(),
                    buffer.readUtf(128)
            );
        }
    }

    public record OnlinePlayer(String name, String factionName) {
        public OnlinePlayer {
            name = limit(name, 16);
            factionName = limit(factionName, 32);
        }

        public boolean inFaction() {
            return !factionName.isEmpty();
        }

        private static void encode(RegistryFriendlyByteBuf buffer, OnlinePlayer player) {
            buffer.writeUtf(player.name, 16);
            buffer.writeUtf(player.factionName, 32);
        }

        private static OnlinePlayer decode(RegistryFriendlyByteBuf buffer) {
            return new OnlinePlayer(buffer.readUtf(16), buffer.readUtf(32));
        }
    }

    public record Claim(int chunkX, int chunkZ, int color, String label, boolean own) {
        public Claim {
            color &= 0xFFFFFF;
            label = limit(label, 32);
        }

        private static void encode(RegistryFriendlyByteBuf buffer, Claim claim) {
            buffer.writeInt(claim.chunkX);
            buffer.writeInt(claim.chunkZ);
            buffer.writeInt(claim.color);
            buffer.writeUtf(claim.label, 32);
            buffer.writeBoolean(claim.own);
        }

        private static Claim decode(RegistryFriendlyByteBuf buffer) {
            return new Claim(
                    buffer.readInt(), buffer.readInt(), buffer.readInt(),
                    buffer.readUtf(32), buffer.readBoolean()
            );
        }
    }

    @FunctionalInterface
    private interface ElementWriter<T> {
        void write(RegistryFriendlyByteBuf buffer, T value);
    }

    @FunctionalInterface
    private interface ElementReader<T> {
        T read(RegistryFriendlyByteBuf buffer);
    }
}
