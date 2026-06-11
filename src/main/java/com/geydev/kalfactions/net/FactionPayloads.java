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
    public record C2SOpenTable(BlockPos tablePos) implements CustomPacketPayload {
        public static final Type<C2SOpenTable> TYPE = FactionPayloads.payloadType("open_table");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SOpenTable> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeBlockPos(payload.tablePos),
                buffer -> new C2SOpenTable(buffer.readBlockPos())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SCreateFaction(BlockPos tablePos, String name, int color) implements CustomPacketPayload {
        public static final Type<C2SCreateFaction> TYPE = FactionPayloads.payloadType("create_faction");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SCreateFaction> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.tablePos);
                    buffer.writeUtf(payload.name, FactionServerHooks.MAX_NAME_LENGTH);
                    buffer.writeInt(payload.color);
                },
                buffer -> new C2SCreateFaction(
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

    public record ClaimEntry(int chunkX, int chunkZ, int color, String name, UUID factionId) {
        private static void encode(RegistryFriendlyByteBuf buffer, ClaimEntry entry) {
            buffer.writeVarInt(entry.chunkX);
            buffer.writeVarInt(entry.chunkZ);
            buffer.writeInt(entry.color);
            buffer.writeUtf(entry.name, 32);
            buffer.writeUUID(entry.factionId);
        }

        private static ClaimEntry decode(RegistryFriendlyByteBuf buffer) {
            return new ClaimEntry(
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readInt(),
                    buffer.readUtf(32),
                    buffer.readUUID()
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
