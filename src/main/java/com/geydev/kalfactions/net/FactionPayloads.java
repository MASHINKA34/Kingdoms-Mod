package com.geydev.kalfactions.net;

import com.geydev.kalfactions.KalFactions;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.Util;
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

    public record C2SOpenWarArchive(BlockPos archivePos) implements CustomPacketPayload {
        public static final Type<C2SOpenWarArchive> TYPE = FactionPayloads.payloadType("open_war_archive");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SOpenWarArchive> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeBlockPos(payload.archivePos),
                buffer -> new C2SOpenWarArchive(buffer.readBlockPos())
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

    public record C2STurnInCrystals(BlockPos tablePos) implements CustomPacketPayload {
        public static final Type<C2STurnInCrystals> TYPE = FactionPayloads.payloadType("turn_in_crystals");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2STurnInCrystals> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeBlockPos(payload.tablePos),
                buffer -> new C2STurnInCrystals(buffer.readBlockPos())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SStartResearch(BlockPos tablePos, String nodeName) implements CustomPacketPayload {
        public static final Type<C2SStartResearch> TYPE = FactionPayloads.payloadType("start_research");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SStartResearch> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.tablePos);
                    buffer.writeUtf(payload.nodeName, 32);
                },
                buffer -> new C2SStartResearch(buffer.readBlockPos(), buffer.readUtf(32))
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

    public record C2SDeclareWar(
            BlockPos tablePos,
            String targetFactionName,
            String warType,
            String reason
    ) implements CustomPacketPayload {
        public static final Type<C2SDeclareWar> TYPE = FactionPayloads.payloadType("declare_war");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SDeclareWar> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.tablePos);
                    buffer.writeUtf(payload.targetFactionName, FactionServerHooks.MAX_NAME_LENGTH);
                    buffer.writeUtf(payload.warType, 32);
                    buffer.writeUtf(payload.reason, com.geydev.kalfactions.war.War.MAX_REASON_LENGTH);
                },
                buffer -> new C2SDeclareWar(
                        buffer.readBlockPos(),
                        buffer.readUtf(FactionServerHooks.MAX_NAME_LENGTH),
                        buffer.readUtf(32),
                        buffer.readUtf(com.geydev.kalfactions.war.War.MAX_REASON_LENGTH)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SJoinWar(BlockPos tablePos, String allyFactionName) implements CustomPacketPayload {
        public static final Type<C2SJoinWar> TYPE = FactionPayloads.payloadType("join_war");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SJoinWar> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.tablePos);
                    buffer.writeUtf(payload.allyFactionName, FactionServerHooks.MAX_NAME_LENGTH);
                },
                buffer -> new C2SJoinWar(
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

    public record C2SSurrenderWar(BlockPos tablePos) implements CustomPacketPayload {
        public static final Type<C2SSurrenderWar> TYPE = FactionPayloads.payloadType("surrender_war");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SSurrenderWar> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeBlockPos(payload.tablePos),
                buffer -> new C2SSurrenderWar(buffer.readBlockPos())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SClaimWarSpoils(UUID spoilsId, String choice) implements CustomPacketPayload {
        public static final Type<C2SClaimWarSpoils> TYPE = FactionPayloads.payloadType("claim_war_spoils");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SClaimWarSpoils> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUUID(payload.spoilsId);
                    buffer.writeUtf(payload.choice, 16);
                },
                buffer -> new C2SClaimWarSpoils(buffer.readUUID(), buffer.readUtf(16))
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

    public record C2SToggleForceLoad(ResourceLocation dimension, long packedChunk) implements CustomPacketPayload {
        public static final Type<C2SToggleForceLoad> TYPE = FactionPayloads.payloadType("toggle_force_load");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SToggleForceLoad> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeResourceLocation(payload.dimension);
                    buffer.writeLong(payload.packedChunk);
                },
                buffer -> new C2SToggleForceLoad(buffer.readResourceLocation(), buffer.readLong())
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

    public record S2CWarArchive(List<WarRecordView> records) implements CustomPacketPayload {
        public static final int MAX_RECORDS = 200;
        public static final Type<S2CWarArchive> TYPE = FactionPayloads.payloadType("war_archive");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CWarArchive> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    int size = Math.min(payload.records.size(), MAX_RECORDS);
                    buffer.writeVarInt(size);
                    for (int i = 0; i < size; i++) {
                        WarRecordView.encode(buffer, payload.records.get(i));
                    }
                },
                buffer -> {
                    int size = buffer.readVarInt();
                    if (size < 0 || size > MAX_RECORDS) {
                        throw new DecoderException("War archive size " + size + " exceeds " + MAX_RECORDS);
                    }
                    List<WarRecordView> records = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        records.add(WarRecordView.decode(buffer));
                    }
                    return new S2CWarArchive(List.copyOf(records));
                }
        );

        public S2CWarArchive {
            records = records == null ? List.of() : List.copyOf(records);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record WarRecordView(
            UUID id,
            String typeId,
            String reason,
            String attackerLead,
            List<String> attackerAllies,
            String defenderLead,
            List<String> defenderAllies,
            String outcome,
            String winnerName,
            String loserName,
            long attackerPoints,
            long defenderPoints,
            long militaryInfluenceReward,
            boolean lootSpoilsAvailable,
            long startedAtMillis,
            long endedAtMillis
    ) {
        public static final int MAX_SIDE_NAMES = 64;

        public WarRecordView {
            id = id == null ? Util.NIL_UUID : id;
            typeId = typeId == null ? "" : typeId;
            reason = reason == null ? "" : reason;
            attackerLead = attackerLead == null ? "" : attackerLead;
            attackerAllies = attackerAllies == null ? List.of() : List.copyOf(attackerAllies);
            defenderLead = defenderLead == null ? "" : defenderLead;
            defenderAllies = defenderAllies == null ? List.of() : List.copyOf(defenderAllies);
            outcome = outcome == null ? "" : outcome;
            winnerName = winnerName == null ? "" : winnerName;
            loserName = loserName == null ? "" : loserName;
        }

        private static void encode(RegistryFriendlyByteBuf buffer, WarRecordView view) {
            buffer.writeUUID(view.id);
            buffer.writeUtf(view.typeId, 32);
            buffer.writeUtf(view.reason, com.geydev.kalfactions.war.War.MAX_REASON_LENGTH);
            buffer.writeUtf(view.attackerLead, FactionServerHooks.MAX_NAME_LENGTH);
            writeNames(buffer, view.attackerAllies, MAX_SIDE_NAMES);
            buffer.writeUtf(view.defenderLead, FactionServerHooks.MAX_NAME_LENGTH);
            writeNames(buffer, view.defenderAllies, MAX_SIDE_NAMES);
            buffer.writeUtf(view.outcome, 16);
            buffer.writeUtf(view.winnerName, FactionServerHooks.MAX_NAME_LENGTH);
            buffer.writeUtf(view.loserName, FactionServerHooks.MAX_NAME_LENGTH);
            buffer.writeLong(view.attackerPoints);
            buffer.writeLong(view.defenderPoints);
            buffer.writeLong(view.militaryInfluenceReward);
            buffer.writeBoolean(view.lootSpoilsAvailable);
            buffer.writeLong(view.startedAtMillis);
            buffer.writeLong(view.endedAtMillis);
        }

        private static WarRecordView decode(RegistryFriendlyByteBuf buffer) {
            return new WarRecordView(
                    buffer.readUUID(),
                    buffer.readUtf(32),
                    buffer.readUtf(com.geydev.kalfactions.war.War.MAX_REASON_LENGTH),
                    buffer.readUtf(FactionServerHooks.MAX_NAME_LENGTH),
                    readNames(buffer, MAX_SIDE_NAMES),
                    buffer.readUtf(FactionServerHooks.MAX_NAME_LENGTH),
                    readNames(buffer, MAX_SIDE_NAMES),
                    buffer.readUtf(16),
                    buffer.readUtf(FactionServerHooks.MAX_NAME_LENGTH),
                    buffer.readUtf(FactionServerHooks.MAX_NAME_LENGTH),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readBoolean(),
                    buffer.readLong(),
                    buffer.readLong()
            );
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

    public record S2CInfluenceGain(String influenceType, long amount) implements CustomPacketPayload {
        public static final Type<S2CInfluenceGain> TYPE = FactionPayloads.payloadType("influence_gain");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CInfluenceGain> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeUtf(payload.influenceType, 16);
                    buffer.writeLong(payload.amount);
                },
                buffer -> new S2CInfluenceGain(buffer.readUtf(16), buffer.readLong())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CMiningBonus(float multiplier) implements CustomPacketPayload {
        public static final Type<S2CMiningBonus> TYPE = FactionPayloads.payloadType("mining_bonus");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CMiningBonus> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeFloat(payload.multiplier),
                buffer -> new S2CMiningBonus(buffer.readFloat())
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

    public record ClaimEntry(
            int chunkX,
            int chunkZ,
            int color,
            String name,
            UUID factionId,
            boolean outpost,
            boolean forceLoaded,
            boolean sanctuary
    ) {
        private static void encode(RegistryFriendlyByteBuf buffer, ClaimEntry entry) {
            buffer.writeVarInt(entry.chunkX);
            buffer.writeVarInt(entry.chunkZ);
            buffer.writeInt(entry.color);
            buffer.writeUtf(entry.name, 32);
            buffer.writeUUID(entry.factionId);
            buffer.writeBoolean(entry.outpost);
            buffer.writeBoolean(entry.forceLoaded);
            buffer.writeBoolean(entry.sanctuary);
        }

        private static ClaimEntry decode(RegistryFriendlyByteBuf buffer) {
            return new ClaimEntry(
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readInt(),
                    buffer.readUtf(32),
                    buffer.readUUID(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean()
            );
        }
    }

    public record S2COpenGuide() implements CustomPacketPayload {
        public static final S2COpenGuide INSTANCE = new S2COpenGuide();
        public static final Type<S2COpenGuide> TYPE = FactionPayloads.payloadType("open_guide_screen");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2COpenGuide> STREAM_CODEC =
                StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SSanctuarySetClaim(
            BlockPos corePos,
            int chunkX,
            int chunkZ,
            boolean claimed
    ) implements CustomPacketPayload {
        public static final Type<C2SSanctuarySetClaim> TYPE = FactionPayloads.payloadType("sanctuary_set_claim");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SSanctuarySetClaim> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.corePos);
                    buffer.writeInt(payload.chunkX);
                    buffer.writeInt(payload.chunkZ);
                    buffer.writeBoolean(payload.claimed);
                },
                buffer -> new C2SSanctuarySetClaim(
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

    public record C2SSanctuaryMapSet(boolean claimed, List<Long> chunks) implements CustomPacketPayload {
        public static final int MAX_CHUNKS = 512;
        public static final Type<C2SSanctuaryMapSet> TYPE = FactionPayloads.payloadType("sanctuary_map_set");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SSanctuaryMapSet> STREAM_CODEC = StreamCodec.of(
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
                        throw new DecoderException("Sanctuary map batch size " + size + " exceeds " + MAX_CHUNKS);
                    }
                    List<Long> chunks = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        chunks.add(buffer.readLong());
                    }
                    return new C2SSanctuaryMapSet(claimed, List.copyOf(chunks));
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2COpenSanctuary(
            BlockPos corePos,
            int centerChunkX,
            int centerChunkZ,
            int radius,
            List<Long> chunks,
            Component message,
            boolean successful
    ) implements CustomPacketPayload {
        public static final int MAX_CHUNKS = 16384;
        public static final Type<S2COpenSanctuary> TYPE = FactionPayloads.payloadType("sanctuary_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2COpenSanctuary> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.corePos);
                    buffer.writeInt(payload.centerChunkX);
                    buffer.writeInt(payload.centerChunkZ);
                    buffer.writeVarInt(payload.radius);
                    int size = Math.min(payload.chunks.size(), MAX_CHUNKS);
                    buffer.writeVarInt(size);
                    for (int i = 0; i < size; i++) {
                        buffer.writeLong(payload.chunks.get(i));
                    }
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, payload.message);
                    buffer.writeBoolean(payload.successful);
                },
                buffer -> {
                    BlockPos corePos = buffer.readBlockPos();
                    int centerChunkX = buffer.readInt();
                    int centerChunkZ = buffer.readInt();
                    int radius = buffer.readVarInt();
                    int size = buffer.readVarInt();
                    if (size < 0 || size > MAX_CHUNKS) {
                        throw new DecoderException("Sanctuary chunk batch size " + size + " exceeds " + MAX_CHUNKS);
                    }
                    List<Long> chunks = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        chunks.add(buffer.readLong());
                    }
                    return new S2COpenSanctuary(
                            corePos,
                            centerChunkX,
                            centerChunkZ,
                            radius,
                            List.copyOf(chunks),
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

    public record C2SRequestWorldMap(BlockPos pos) implements CustomPacketPayload {
        public static final Type<C2SRequestWorldMap> TYPE = FactionPayloads.payloadType("request_world_map");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestWorldMap> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeBlockPos(payload.pos),
                buffer -> new C2SRequestWorldMap(buffer.readBlockPos())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CWorldMapBegin(
            int width,
            int height,
            int centerX,
            int centerZ,
            int regionBlocks,
            int totalParts,
            int totalBytes,
            long version
    ) implements CustomPacketPayload {
        public static final Type<S2CWorldMapBegin> TYPE = FactionPayloads.payloadType("world_map_begin");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CWorldMapBegin> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.width);
                    buffer.writeVarInt(payload.height);
                    buffer.writeInt(payload.centerX);
                    buffer.writeInt(payload.centerZ);
                    buffer.writeVarInt(payload.regionBlocks);
                    buffer.writeVarInt(payload.totalParts);
                    buffer.writeVarInt(payload.totalBytes);
                    buffer.writeLong(payload.version);
                },
                buffer -> new S2CWorldMapBegin(
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readInt(),
                        buffer.readInt(),
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readLong()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CWorldMapPart(long version, int index, byte[] data) implements CustomPacketPayload {
        public static final int MAX_PART = 32 * 1024;
        public static final Type<S2CWorldMapPart> TYPE = FactionPayloads.payloadType("world_map_part");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CWorldMapPart> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeLong(payload.version);
                    buffer.writeVarInt(payload.index);
                    buffer.writeByteArray(payload.data);
                },
                buffer -> new S2CWorldMapPart(
                        buffer.readLong(),
                        buffer.readVarInt(),
                        buffer.readByteArray(MAX_PART)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CWorldMapTracks(ResourceLocation dimension, float[] segments) implements CustomPacketPayload {
        public static final int MAX_FLOATS = 16_384 * 4;
        public static final Type<S2CWorldMapTracks> TYPE = FactionPayloads.payloadType("world_map_tracks");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CWorldMapTracks> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeResourceLocation(payload.dimension);
                    int count = Math.min(payload.segments.length, MAX_FLOATS);
                    buffer.writeVarInt(count);
                    for (int i = 0; i < count; i++) {
                        buffer.writeFloat(payload.segments[i]);
                    }
                },
                buffer -> {
                    ResourceLocation dimension = buffer.readResourceLocation();
                    int count = buffer.readVarInt();
                    if (count < 0 || count > MAX_FLOATS) {
                        throw new DecoderException("World map track float count " + count + " exceeds " + MAX_FLOATS);
                    }
                    float[] segments = new float[count];
                    for (int i = 0; i < count; i++) {
                        segments[i] = buffer.readFloat();
                    }
                    return new S2CWorldMapTracks(dimension, segments);
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
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
