package com.geydev.kalfactions.net;

import com.geydev.kalfactions.KalFactions;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class FactionPayloads {
    public record C2SOpenTable(BlockPos tablePos, boolean silent) implements CustomPacketPayload {
        public static final Type<C2SOpenTable> TYPE = FactionPayloads.payloadType("open_table");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SOpenTable> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.tablePos);
                    buffer.writeBoolean(payload.silent);
                },
                buffer -> new C2SOpenTable(buffer.readBlockPos(), buffer.readBoolean())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SCreateFaction(
            BlockPos tablePos,
            String name,
            int color,
            List<String> bonuses,
            List<Integer> emblem,
            String emblemUrl
    ) implements CustomPacketPayload {
        public static final Type<C2SCreateFaction> TYPE = FactionPayloads.payloadType("create_faction");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SCreateFaction> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.tablePos);
                    buffer.writeUtf(payload.name, FactionServerHooks.MAX_NAME_LENGTH);
                    buffer.writeInt(payload.color);
                    writeBonuses(buffer, payload.bonuses);
                    writeEmblem(buffer, payload.emblem);
                    buffer.writeUtf(payload.emblemUrl, FactionSnapshot.MAX_EMBLEM_URL);
                },
                buffer -> new C2SCreateFaction(
                        buffer.readBlockPos(),
                        buffer.readUtf(FactionServerHooks.MAX_NAME_LENGTH),
                        buffer.readInt(),
                        readBonuses(buffer),
                        readEmblem(buffer),
                        buffer.readUtf(FactionSnapshot.MAX_EMBLEM_URL)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SSetEmblem(
            BlockPos tablePos,
            List<Integer> emblem,
            String emblemUrl
    ) implements CustomPacketPayload {
        public static final Type<C2SSetEmblem> TYPE = FactionPayloads.payloadType("set_emblem");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SSetEmblem> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.tablePos);
                    writeEmblem(buffer, payload.emblem);
                    buffer.writeUtf(payload.emblemUrl, FactionSnapshot.MAX_EMBLEM_URL);
                },
                buffer -> new C2SSetEmblem(
                        buffer.readBlockPos(),
                        readEmblem(buffer),
                        buffer.readUtf(FactionSnapshot.MAX_EMBLEM_URL)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void writeBonuses(RegistryFriendlyByteBuf buffer, List<String> bonuses) {
        int size = Math.min(bonuses.size(), FactionSnapshot.MAX_BONUSES);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            buffer.writeUtf(bonuses.get(i), 24);
        }
    }

    private static List<String> readBonuses(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > FactionSnapshot.MAX_BONUSES) {
            throw new DecoderException("Bonus list size " + size + " exceeds " + FactionSnapshot.MAX_BONUSES);
        }
        List<String> bonuses = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            bonuses.add(buffer.readUtf(24));
        }
        return List.copyOf(bonuses);
    }

    private static void writeEmblem(RegistryFriendlyByteBuf buffer, List<Integer> emblem) {
        int size = FactionSnapshot.isValidEmblemSize(emblem.size()) ? emblem.size() : 0;
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            Integer pixel = emblem.get(i);
            buffer.writeInt(pixel == null ? 0 : pixel);
        }
    }

    private static List<Integer> readEmblem(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > FactionSnapshot.MAX_EMBLEM_PIXELS) {
            throw new DecoderException("Emblem size " + size + " exceeds " + FactionSnapshot.MAX_EMBLEM_PIXELS);
        }
        List<Integer> emblem = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            emblem.add(buffer.readInt());
        }
        return List.copyOf(emblem);
    }

    private static void writeNames(RegistryFriendlyByteBuf buffer, List<String> names, int maxSize) {
        int size = Math.min(names.size(), maxSize);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            buffer.writeUtf(names.get(i), FactionServerHooks.MAX_NAME_LENGTH);
        }
    }

    private static List<String> readNames(RegistryFriendlyByteBuf buffer, int maxSize) {
        int size = buffer.readVarInt();
        if (size < 0 || size > maxSize) {
            throw new DecoderException("Name list size " + size + " exceeds " + maxSize);
        }
        List<String> names = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            names.add(buffer.readUtf(FactionServerHooks.MAX_NAME_LENGTH));
        }
        return List.copyOf(names);
    }

    public record C2SUpdateFaction(BlockPos tablePos, String name, int color) implements CustomPacketPayload {
        public static final Type<C2SUpdateFaction> TYPE = FactionPayloads.payloadType("update_faction");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateFaction> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.tablePos);
                    buffer.writeUtf(payload.name, FactionServerHooks.MAX_NAME_LENGTH);
                    buffer.writeInt(payload.color);
                },
                buffer -> new C2SUpdateFaction(
                        buffer.readBlockPos(),
                        buffer.readUtf(FactionServerHooks.MAX_NAME_LENGTH),
                        buffer.readInt()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SSetClaim(
            BlockPos tablePos,
            int chunkX,
            int chunkZ,
            boolean claimed
    ) implements CustomPacketPayload {
        public static final Type<C2SSetClaim> TYPE = FactionPayloads.payloadType("set_claim");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SSetClaim> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.tablePos);
                    buffer.writeInt(payload.chunkX);
                    buffer.writeInt(payload.chunkZ);
                    buffer.writeBoolean(payload.claimed);
                },
                buffer -> new C2SSetClaim(
                        buffer.readBlockPos(),
                        buffer.readInt(),
                        buffer.readInt(),
                        buffer.readBoolean()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SDepositTreasury(BlockPos tablePos, long amount) implements CustomPacketPayload {
        public static final Type<C2SDepositTreasury> TYPE = FactionPayloads.payloadType("deposit_treasury");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SDepositTreasury> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.tablePos);
                    buffer.writeLong(payload.amount);
                },
                buffer -> new C2SDepositTreasury(buffer.readBlockPos(), buffer.readLong())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SWithdrawTreasury(BlockPos tablePos, long amount) implements CustomPacketPayload {
        public static final Type<C2SWithdrawTreasury> TYPE = FactionPayloads.payloadType("withdraw_treasury");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SWithdrawTreasury> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.tablePos);
                    buffer.writeLong(payload.amount);
                },
                buffer -> new C2SWithdrawTreasury(buffer.readBlockPos(), buffer.readLong())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SKickMember(BlockPos tablePos, UUID playerId) implements CustomPacketPayload {
        public static final Type<C2SKickMember> TYPE = FactionPayloads.payloadType("kick_member");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SKickMember> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.tablePos);
                    buffer.writeUUID(payload.playerId);
                },
                buffer -> new C2SKickMember(buffer.readBlockPos(), buffer.readUUID())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SSetMemberRole(BlockPos tablePos, UUID playerId, String role) implements CustomPacketPayload {
        public static final Type<C2SSetMemberRole> TYPE = FactionPayloads.payloadType("set_member_role");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SSetMemberRole> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.tablePos);
                    buffer.writeUUID(payload.playerId);
                    buffer.writeUtf(payload.role, 24);
                },
                buffer -> new C2SSetMemberRole(
                        buffer.readBlockPos(),
                        buffer.readUUID(),
                        buffer.readUtf(24)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SSetPvp(BlockPos tablePos, boolean enabled) implements CustomPacketPayload {
        public static final Type<C2SSetPvp> TYPE = FactionPayloads.payloadType("set_pvp");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SSetPvp> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.tablePos);
                    buffer.writeBoolean(payload.enabled);
                },
                buffer -> new C2SSetPvp(buffer.readBlockPos(), buffer.readBoolean())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SLeaveFaction(BlockPos tablePos) implements CustomPacketPayload {
        public static final Type<C2SLeaveFaction> TYPE = FactionPayloads.payloadType("leave_faction");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SLeaveFaction> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeBlockPos(payload.tablePos),
                buffer -> new C2SLeaveFaction(buffer.readBlockPos())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SDisbandFaction(BlockPos tablePos) implements CustomPacketPayload {
        public static final Type<C2SDisbandFaction> TYPE = FactionPayloads.payloadType("disband_faction");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SDisbandFaction> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeBlockPos(payload.tablePos),
                buffer -> new C2SDisbandFaction(buffer.readBlockPos())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SInvitePlayer(BlockPos tablePos, String targetName) implements CustomPacketPayload {
        public static final Type<C2SInvitePlayer> TYPE = FactionPayloads.payloadType("invite_player");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SInvitePlayer> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.tablePos);
                    buffer.writeUtf(payload.targetName, 16);
                },
                buffer -> new C2SInvitePlayer(buffer.readBlockPos(), buffer.readUtf(16))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2STransferLeadership(BlockPos tablePos, String targetName) implements CustomPacketPayload {
        public static final Type<C2STransferLeadership> TYPE = FactionPayloads.payloadType("transfer_leadership");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2STransferLeadership> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.tablePos);
                    buffer.writeUtf(payload.targetName, 16);
                },
                buffer -> new C2STransferLeadership(buffer.readBlockPos(), buffer.readUtf(16))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SRequestAlliance(BlockPos tablePos, String targetFactionName) implements CustomPacketPayload {
        public static final Type<C2SRequestAlliance> TYPE = FactionPayloads.payloadType("request_alliance");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestAlliance> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.tablePos);
                    buffer.writeUtf(payload.targetFactionName, FactionServerHooks.MAX_NAME_LENGTH);
                },
                buffer -> new C2SRequestAlliance(
                        buffer.readBlockPos(),
                        buffer.readUtf(FactionServerHooks.MAX_NAME_LENGTH)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SBreakAlliance(BlockPos tablePos, String targetFactionName) implements CustomPacketPayload {
        public static final Type<C2SBreakAlliance> TYPE = FactionPayloads.payloadType("break_alliance");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SBreakAlliance> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.tablePos);
                    buffer.writeUtf(payload.targetFactionName, FactionServerHooks.MAX_NAME_LENGTH);
                },
                buffer -> new C2SBreakAlliance(
                        buffer.readBlockPos(),
                        buffer.readUtf(FactionServerHooks.MAX_NAME_LENGTH)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SDeclareWar(BlockPos tablePos, String targetFactionName) implements CustomPacketPayload {
        public static final Type<C2SDeclareWar> TYPE = FactionPayloads.payloadType("declare_war");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SDeclareWar> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.tablePos);
                    buffer.writeUtf(payload.targetFactionName, FactionServerHooks.MAX_NAME_LENGTH);
                },
                buffer -> new C2SDeclareWar(
                        buffer.readBlockPos(),
                        buffer.readUtf(FactionServerHooks.MAX_NAME_LENGTH)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SEndWar(BlockPos tablePos) implements CustomPacketPayload {
        public static final Type<C2SEndWar> TYPE = FactionPayloads.payloadType("end_war");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SEndWar> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeBlockPos(payload.tablePos),
                buffer -> new C2SEndWar(buffer.readBlockPos())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SMapSetClaims(boolean claimed, List<Long> chunks) implements CustomPacketPayload {
        public static final int MAX_CHUNKS = 512;
        public static final Type<C2SMapSetClaims> TYPE = FactionPayloads.payloadType("map_set_claims");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SMapSetClaims> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBoolean(payload.claimed);
                    int size = Math.min(payload.chunks.size(), MAX_CHUNKS);
                    buffer.writeVarInt(size);
                    for (int i = 0; i < size; i++) {
                        buffer.writeLong(payload.chunks.get(i));
                    }
                },
                buffer -> {
                    boolean claimed = buffer.readBoolean();
                    int size = buffer.readVarInt();
                    if (size < 0 || size > MAX_CHUNKS) {
                        throw new DecoderException("Map claim batch size " + size + " exceeds " + MAX_CHUNKS);
                    }
                    List<Long> chunks = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        chunks.add(buffer.readLong());
                    }
                    return new C2SMapSetClaims(claimed, List.copyOf(chunks));
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SSetChestMode(BlockPos pos, String mode) implements CustomPacketPayload {
        public static final Type<C2SSetChestMode> TYPE = FactionPayloads.payloadType("set_chest_mode");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SSetChestMode> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.pos);
                    buffer.writeUtf(payload.mode, 16);
                },
                buffer -> new C2SSetChestMode(buffer.readBlockPos(), buffer.readUtf(16))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SEditChestWhitelist(
            BlockPos pos,
            boolean add,
            UUID targetId,
            String targetName
    ) implements CustomPacketPayload {
        public static final Type<C2SEditChestWhitelist> TYPE = FactionPayloads.payloadType("edit_chest_whitelist");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SEditChestWhitelist> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.pos);
                    buffer.writeBoolean(payload.add);
                    buffer.writeUUID(payload.targetId);
                    buffer.writeUtf(payload.targetName, 16);
                },
                buffer -> new C2SEditChestWhitelist(
                        buffer.readBlockPos(),
                        buffer.readBoolean(),
                        buffer.readUUID(),
                        buffer.readUtf(16)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CChestAccessState(
            BlockPos pos,
            String mode,
            List<ChestWhitelistEntry> whitelist,
            List<String> candidates,
            Component notice,
            boolean successful
    ) implements CustomPacketPayload {
        public static final int MAX_WHITELIST = 64;
        public static final int MAX_CANDIDATES = 128;
        public static final Type<S2CChestAccessState> TYPE = FactionPayloads.payloadType("chest_access_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CChestAccessState> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.pos);
                    buffer.writeUtf(payload.mode, 16);
                    int whitelistSize = Math.min(payload.whitelist.size(), MAX_WHITELIST);
                    buffer.writeVarInt(whitelistSize);
                    for (int i = 0; i < whitelistSize; i++) {
                        ChestWhitelistEntry.encode(buffer, payload.whitelist.get(i));
                    }
                    int candidateSize = Math.min(payload.candidates.size(), MAX_CANDIDATES);
                    buffer.writeVarInt(candidateSize);
                    for (int i = 0; i < candidateSize; i++) {
                        buffer.writeUtf(payload.candidates.get(i), 16);
                    }
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, payload.notice);
                    buffer.writeBoolean(payload.successful);
                },
                buffer -> {
                    BlockPos pos = buffer.readBlockPos();
                    String mode = buffer.readUtf(16);
                    int whitelistSize = buffer.readVarInt();
                    if (whitelistSize < 0 || whitelistSize > MAX_WHITELIST) {
                        throw new DecoderException("Chest whitelist size " + whitelistSize + " exceeds " + MAX_WHITELIST);
                    }
                    List<ChestWhitelistEntry> whitelist = new ArrayList<>(whitelistSize);
                    for (int i = 0; i < whitelistSize; i++) {
                        whitelist.add(ChestWhitelistEntry.decode(buffer));
                    }
                    int candidateSize = buffer.readVarInt();
                    if (candidateSize < 0 || candidateSize > MAX_CANDIDATES) {
                        throw new DecoderException("Chest candidate size " + candidateSize + " exceeds " + MAX_CANDIDATES);
                    }
                    List<String> candidates = new ArrayList<>(candidateSize);
                    for (int i = 0; i < candidateSize; i++) {
                        candidates.add(buffer.readUtf(16));
                    }
                    return new S2CChestAccessState(
                            pos,
                            mode,
                            List.copyOf(whitelist),
                            List.copyOf(candidates),
                            ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer),
                            buffer.readBoolean()
                    );
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ChestWhitelistEntry(UUID id, String name) {
        private static void encode(RegistryFriendlyByteBuf buffer, ChestWhitelistEntry entry) {
            buffer.writeUUID(entry.id);
            buffer.writeUtf(entry.name, 32);
        }

        private static ChestWhitelistEntry decode(RegistryFriendlyByteBuf buffer) {
            return new ChestWhitelistEntry(buffer.readUUID(), buffer.readUtf(32));
        }
    }

    public record C2SRequestFactionList() implements CustomPacketPayload {
        public static final Type<C2SRequestFactionList> TYPE = FactionPayloads.payloadType("request_faction_list");
        public static final C2SRequestFactionList INSTANCE = new C2SRequestFactionList();
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestFactionList> STREAM_CODEC =
                StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SRespondInvite(UUID factionId, boolean accept) implements CustomPacketPayload {
        public static final Type<C2SRespondInvite> TYPE = FactionPayloads.payloadType("respond_invite");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SRespondInvite> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUUID(payload.factionId);
                    buffer.writeBoolean(payload.accept);
                },
                buffer -> new C2SRespondInvite(buffer.readUUID(), buffer.readBoolean())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SRespondAlliance(UUID factionId, boolean accept) implements CustomPacketPayload {
        public static final Type<C2SRespondAlliance> TYPE = FactionPayloads.payloadType("respond_alliance");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SRespondAlliance> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUUID(payload.factionId);
                    buffer.writeBoolean(payload.accept);
                },
                buffer -> new C2SRespondAlliance(buffer.readUUID(), buffer.readBoolean())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CFactionList(
            List<FactionInfo> factions,
            List<InviteInfo> invites,
            List<AllianceInviteInfo> allianceInvites
    ) implements CustomPacketPayload {
        public static final int MAX_FACTIONS = 256;
        public static final int MAX_INVITES = 32;
        public static final int MAX_ALLIANCE_INVITES = 32;
        public static final Type<S2CFactionList> TYPE = FactionPayloads.payloadType("faction_list");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CFactionList> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    int factionCount = Math.min(payload.factions.size(), MAX_FACTIONS);
                    buffer.writeVarInt(factionCount);
                    for (int i = 0; i < factionCount; i++) {
                        FactionInfo.encode(buffer, payload.factions.get(i));
                    }
                    int inviteCount = Math.min(payload.invites.size(), MAX_INVITES);
                    buffer.writeVarInt(inviteCount);
                    for (int i = 0; i < inviteCount; i++) {
                        InviteInfo.encode(buffer, payload.invites.get(i));
                    }
                    int allianceInviteCount = Math.min(payload.allianceInvites.size(), MAX_ALLIANCE_INVITES);
                    buffer.writeVarInt(allianceInviteCount);
                    for (int i = 0; i < allianceInviteCount; i++) {
                        AllianceInviteInfo.encode(buffer, payload.allianceInvites.get(i));
                    }
                },
                buffer -> {
                    int factionCount = buffer.readVarInt();
                    if (factionCount < 0 || factionCount > MAX_FACTIONS) {
                        throw new DecoderException("Faction list size " + factionCount + " exceeds " + MAX_FACTIONS);
                    }
                    List<FactionInfo> factions = new ArrayList<>(factionCount);
                    for (int i = 0; i < factionCount; i++) {
                        factions.add(FactionInfo.decode(buffer));
                    }
                    int inviteCount = buffer.readVarInt();
                    if (inviteCount < 0 || inviteCount > MAX_INVITES) {
                        throw new DecoderException("Invite list size " + inviteCount + " exceeds " + MAX_INVITES);
                    }
                    List<InviteInfo> invites = new ArrayList<>(inviteCount);
                    for (int i = 0; i < inviteCount; i++) {
                        invites.add(InviteInfo.decode(buffer));
                    }
                    int allianceInviteCount = buffer.readVarInt();
                    if (allianceInviteCount < 0 || allianceInviteCount > MAX_ALLIANCE_INVITES) {
                        throw new DecoderException(
                                "Alliance invite list size " + allianceInviteCount + " exceeds " + MAX_ALLIANCE_INVITES);
                    }
                    List<AllianceInviteInfo> allianceInvites = new ArrayList<>(allianceInviteCount);
                    for (int i = 0; i < allianceInviteCount; i++) {
                        allianceInvites.add(AllianceInviteInfo.decode(buffer));
                    }
                    return new S2CFactionList(
                            List.copyOf(factions),
                            List.copyOf(invites),
                            List.copyOf(allianceInvites)
                    );
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record FactionInfo(
            UUID id,
            String name,
            int color,
            int memberCount,
            long influence,
            String warWith,
            List<String> allies,
            List<String> bonuses,
            List<Integer> emblem,
            String emblemUrl,
            List<MemberInfo> members
    ) {
        public static final int MAX_LIST_MEMBERS = 64;
        public static final int MAX_ALLIES = 64;

        private static void encode(RegistryFriendlyByteBuf buffer, FactionInfo info) {
            buffer.writeUUID(info.id);
            buffer.writeUtf(info.name, 32);
            buffer.writeInt(info.color);
            buffer.writeVarInt(info.memberCount);
            buffer.writeLong(info.influence);
            buffer.writeUtf(info.warWith, 32);
            writeNames(buffer, info.allies, MAX_ALLIES);
            writeBonuses(buffer, info.bonuses);
            writeEmblem(buffer, info.emblem);
            buffer.writeUtf(info.emblemUrl, FactionSnapshot.MAX_EMBLEM_URL);
            int memberSize = Math.min(info.members.size(), MAX_LIST_MEMBERS);
            buffer.writeVarInt(memberSize);
            for (int i = 0; i < memberSize; i++) {
                MemberInfo.encode(buffer, info.members.get(i));
            }
        }

        private static FactionInfo decode(RegistryFriendlyByteBuf buffer) {
            UUID id = buffer.readUUID();
            String name = buffer.readUtf(32);
            int color = buffer.readInt();
            int memberCount = buffer.readVarInt();
            long influence = buffer.readLong();
            String warWith = buffer.readUtf(32);
            List<String> allies = readNames(buffer, MAX_ALLIES);
            List<String> bonuses = readBonuses(buffer);
            List<Integer> emblem = readEmblem(buffer);
            String emblemUrl = buffer.readUtf(FactionSnapshot.MAX_EMBLEM_URL);
            int memberSize = buffer.readVarInt();
            if (memberSize < 0 || memberSize > MAX_LIST_MEMBERS) {
                throw new DecoderException("Member list size " + memberSize + " exceeds " + MAX_LIST_MEMBERS);
            }
            List<MemberInfo> members = new ArrayList<>(memberSize);
            for (int i = 0; i < memberSize; i++) {
                members.add(MemberInfo.decode(buffer));
            }
            return new FactionInfo(
                    id, name, color, memberCount, influence, warWith,
                    allies, bonuses, emblem, emblemUrl, List.copyOf(members)
            );
        }
    }

    public record MemberInfo(String name, String role) {
        private static void encode(RegistryFriendlyByteBuf buffer, MemberInfo info) {
            buffer.writeUtf(info.name, 32);
            buffer.writeUtf(info.role, 24);
        }

        private static MemberInfo decode(RegistryFriendlyByteBuf buffer) {
            return new MemberInfo(buffer.readUtf(32), buffer.readUtf(24));
        }
    }

    public record InviteInfo(
            UUID factionId,
            String factionName,
            int color,
            int memberCount,
            String inviterName,
            List<String> bonuses,
            List<Integer> emblem,
            String emblemUrl
    ) {
        private static void encode(RegistryFriendlyByteBuf buffer, InviteInfo info) {
            buffer.writeUUID(info.factionId);
            buffer.writeUtf(info.factionName, 32);
            buffer.writeInt(info.color);
            buffer.writeVarInt(info.memberCount);
            buffer.writeUtf(info.inviterName, 32);
            writeBonuses(buffer, info.bonuses);
            writeEmblem(buffer, info.emblem);
            buffer.writeUtf(info.emblemUrl, FactionSnapshot.MAX_EMBLEM_URL);
        }

        private static InviteInfo decode(RegistryFriendlyByteBuf buffer) {
            return new InviteInfo(
                    buffer.readUUID(),
                    buffer.readUtf(32),
                    buffer.readInt(),
                    buffer.readVarInt(),
                    buffer.readUtf(32),
                    readBonuses(buffer),
                    readEmblem(buffer),
                    buffer.readUtf(FactionSnapshot.MAX_EMBLEM_URL)
            );
        }
    }

    public record AllianceInviteInfo(
            UUID factionId,
            String factionName,
            int color,
            int memberCount,
            String requesterName,
            List<Integer> emblem,
            String emblemUrl
    ) {
        private static void encode(RegistryFriendlyByteBuf buffer, AllianceInviteInfo info) {
            buffer.writeUUID(info.factionId);
            buffer.writeUtf(info.factionName, 32);
            buffer.writeInt(info.color);
            buffer.writeVarInt(info.memberCount);
            buffer.writeUtf(info.requesterName, 32);
            writeEmblem(buffer, info.emblem);
            buffer.writeUtf(info.emblemUrl, FactionSnapshot.MAX_EMBLEM_URL);
        }

        private static AllianceInviteInfo decode(RegistryFriendlyByteBuf buffer) {
            return new AllianceInviteInfo(
                    buffer.readUUID(),
                    buffer.readUtf(32),
                    buffer.readInt(),
                    buffer.readVarInt(),
                    buffer.readUtf(32),
                    readEmblem(buffer),
                    buffer.readUtf(FactionSnapshot.MAX_EMBLEM_URL)
            );
        }
    }

    public record S2CFactionState(
            FactionSnapshot snapshot,
            boolean successful,
            boolean openScreen,
            Component message
    ) implements CustomPacketPayload {
        public static final Type<S2CFactionState> TYPE = FactionPayloads.payloadType("faction_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CFactionState> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    FactionSnapshot.STREAM_CODEC.encode(buffer, payload.snapshot);
                    buffer.writeBoolean(payload.successful);
                    buffer.writeBoolean(payload.openScreen);
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, payload.message);
                },
                buffer -> new S2CFactionState(
                        FactionSnapshot.STREAM_CODEC.decode(buffer),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CFactionNotice(Component message, boolean successful) implements CustomPacketPayload {
        public static final Type<S2CFactionNotice> TYPE = FactionPayloads.payloadType("faction_notice");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CFactionNotice> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, payload.message);
                    buffer.writeBoolean(payload.successful);
                },
                buffer -> new S2CFactionNotice(
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer),
                        buffer.readBoolean()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CSyncClaims(
            ResourceLocation dimension,
            List<ClaimEntry> claims,
            UUID viewerFactionId,
            int viewerClaimCount,
            double viewerClaimDiscount
    ) implements CustomPacketPayload {
        public static final int MAX_ENTRIES = 16384;
        public static final Type<S2CSyncClaims> TYPE = FactionPayloads.payloadType("sync_claims");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CSyncClaims> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeResourceLocation(payload.dimension);
                    int size = Math.min(payload.claims.size(), MAX_ENTRIES);
                    buffer.writeVarInt(size);
                    for (int i = 0; i < size; i++) {
                        ClaimEntry.encode(buffer, payload.claims.get(i));
                    }
                    buffer.writeUUID(payload.viewerFactionId);
                    buffer.writeVarInt(payload.viewerClaimCount);
                    buffer.writeDouble(payload.viewerClaimDiscount);
                },
                buffer -> {
                    ResourceLocation dimension = buffer.readResourceLocation();
                    int size = buffer.readVarInt();
                    if (size < 0 || size > MAX_ENTRIES) {
                        throw new DecoderException("Claim sync size " + size + " exceeds " + MAX_ENTRIES);
                    }
                    List<ClaimEntry> claims = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        claims.add(ClaimEntry.decode(buffer));
                    }
                    return new S2CSyncClaims(
                            dimension,
                            List.copyOf(claims),
                            buffer.readUUID(),
                            buffer.readVarInt(),
                            buffer.readDouble()
                    );
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ClaimEntry(int chunkX, int chunkZ, int color, String name, UUID factionId, boolean outpost) {
        private static void encode(RegistryFriendlyByteBuf buffer, ClaimEntry entry) {
            buffer.writeVarInt(entry.chunkX);
            buffer.writeVarInt(entry.chunkZ);
            buffer.writeInt(entry.color);
            buffer.writeUtf(entry.name, 32);
            buffer.writeUUID(entry.factionId);
            buffer.writeBoolean(entry.outpost);
        }

        private static ClaimEntry decode(RegistryFriendlyByteBuf buffer) {
            return new ClaimEntry(
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readInt(),
                    buffer.readUtf(32),
                    buffer.readUUID(),
                    buffer.readBoolean()
            );
        }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, path)
        );
    }

    private FactionPayloads() {
    }
}
