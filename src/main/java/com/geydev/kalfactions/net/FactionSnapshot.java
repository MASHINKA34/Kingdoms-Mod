package com.geydev.kalfactions.net;

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
        boolean internalPvp,
        UUID viewerId,
        boolean isOfficer,
        List<String> knownFactions,
        List<OnlinePlayer> onlinePlayers,
        List<String> bonuses,
        List<Integer> emblem,
        String emblemUrl
) {
    public static final UUID NO_FACTION = new UUID(0L, 0L);
    public static final int MAX_MEMBERS = 256;
    public static final int MAX_CLAIMS = 1024;
    public static final int MAX_KNOWN_FACTIONS = 512;
    public static final int MAX_ONLINE_PLAYERS = 128;
    public static final int MAX_BONUSES = 8;
    public static final int EMBLEM_PIXELS = 256;
    public static final int MAX_EMBLEM_URL = 256;
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
        viewerId = viewerId == null ? NO_FACTION : viewerId;
        knownFactions = knownFactions == null ? List.of() : List.copyOf(knownFactions);
        onlinePlayers = onlinePlayers == null ? List.of() : List.copyOf(onlinePlayers);
        bonuses = bonuses == null ? List.of() : List.copyOf(bonuses);
        emblem = emblem == null || emblem.size() != EMBLEM_PIXELS ? List.of() : List.copyOf(emblem);
        emblemUrl = limit(emblemUrl, MAX_EMBLEM_URL);
    }

    public static FactionSnapshot empty(BlockPos tablePos, int centerChunkX, int centerChunkZ) {
        return new FactionSnapshot(
                tablePos, NO_FACTION, "", "", 0x4E7A42, false, false,
                centerChunkX, centerChunkZ, 6, List.of(), List.of(),
                0L, 0L, false, NO_FACTION, false, List.of(), List.of(),
                List.of(), List.of(), ""
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
        buffer.writeBoolean(snapshot.internalPvp);
        buffer.writeUUID(snapshot.viewerId);
        buffer.writeBoolean(snapshot.isOfficer);
        writeBoundedList(
                buffer,
                snapshot.knownFactions,
                MAX_KNOWN_FACTIONS,
                (target, value) -> target.writeUtf(value, 32)
        );
        writeBoundedList(buffer, snapshot.onlinePlayers, MAX_ONLINE_PLAYERS, OnlinePlayer::encode);
        writeBoundedList(
                buffer,
                snapshot.bonuses,
                MAX_BONUSES,
                (target, value) -> target.writeUtf(value, 24)
        );
        writeBoundedList(buffer, snapshot.emblem, EMBLEM_PIXELS, (target, value) -> target.writeInt(value));
        buffer.writeUtf(snapshot.emblemUrl, MAX_EMBLEM_URL);
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
        boolean internalPvp = buffer.readBoolean();
        UUID viewerId = buffer.readUUID();
        boolean isOfficer = buffer.readBoolean();
        List<String> knownFactions = readBoundedList(
                buffer,
                MAX_KNOWN_FACTIONS,
                target -> target.readUtf(32)
        );
        List<OnlinePlayer> onlinePlayers = readBoundedList(buffer, MAX_ONLINE_PLAYERS, OnlinePlayer::decode);
        List<String> bonuses = readBoundedList(buffer, MAX_BONUSES, target -> target.readUtf(24));
        List<Integer> emblem = readBoundedList(buffer, EMBLEM_PIXELS, RegistryFriendlyByteBuf::readInt);
        String emblemUrl = buffer.readUtf(MAX_EMBLEM_URL);
        return new FactionSnapshot(
                tablePos, factionId, name, ownerName, color, canManage, canClaim,
                centerChunkX, centerChunkZ, mapRadius, members, claims,
                treasury, influence, internalPvp, viewerId, isOfficer, knownFactions, onlinePlayers,
                bonuses, emblem, emblemUrl
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
