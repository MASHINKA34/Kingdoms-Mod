package com.geydev.kalfactions.net;

import com.geydev.kalfactions.KalFactions;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
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

    public record S2CFactionState(
            FactionSnapshot snapshot,
            boolean successful,
            boolean openScreen,
            String message
    ) implements CustomPacketPayload {
        public static final Type<S2CFactionState> TYPE = FactionPayloads.payloadType("faction_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CFactionState> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    FactionSnapshot.STREAM_CODEC.encode(buffer, payload.snapshot);
                    buffer.writeBoolean(payload.successful);
                    buffer.writeBoolean(payload.openScreen);
                    buffer.writeUtf(payload.message, 256);
                },
                buffer -> new S2CFactionState(
                        FactionSnapshot.STREAM_CODEC.decode(buffer),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readUtf(256)
                )
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
