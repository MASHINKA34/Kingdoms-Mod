package com.geydev.kalfactions.faith;

import com.geydev.kalfactions.KalFactions;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class FaithPayloads {
    public static final int MAX_ENTRIES = 16;
    public static final int MAX_ID_LENGTH = 256;

    public static final byte ACTION_OFFER_QUEST = 0;
    public static final byte ACTION_LEVEL_UP = 1;
    public static final byte ACTION_ACTIVATE_BUFF = 2;

    public record QuestEntry(String iconItemId, String labelKey, int required, int delivered) {
        public QuestEntry {
            iconItemId = iconItemId == null ? "" : iconItemId;
            labelKey = labelKey == null ? "" : labelKey;
            required = Math.max(0, required);
            delivered = Math.max(0, delivered);
        }

        private static void encode(RegistryFriendlyByteBuf buffer, QuestEntry entry) {
            buffer.writeUtf(entry.iconItemId, MAX_ID_LENGTH);
            buffer.writeUtf(entry.labelKey, MAX_ID_LENGTH);
            buffer.writeVarInt(entry.required);
            buffer.writeVarInt(entry.delivered);
        }

        private static QuestEntry decode(RegistryFriendlyByteBuf buffer) {
            return new QuestEntry(
                    buffer.readUtf(MAX_ID_LENGTH),
                    buffer.readUtf(MAX_ID_LENGTH),
                    buffer.readVarInt(),
                    buffer.readVarInt()
            );
        }
    }

    public record S2CFaithState(
            BlockPos statuePos,
            boolean greatStatue,
            byte god,
            int level,
            List<QuestEntry> entries,
            long spursRequired,
            long spursDelivered,
            int killsRequired,
            int killsDone,
            boolean killsOrTrophy,
            boolean questComplete,
            int buffCrystalCost,
            int buffMinutes,
            long buffRemainingMillis,
            boolean buffOwnedByViewer,
            double effectPrimary,
            double effectSecondary,
            int effectExtra,
            Optional<Component> notice,
            boolean noticeSuccessful
    ) implements CustomPacketPayload {
        public static final Type<S2CFaithState> TYPE = payloadType("state");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CFaithState> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.statuePos);
                    buffer.writeBoolean(payload.greatStatue);
                    buffer.writeByte(payload.god);
                    buffer.writeVarInt(payload.level);
                    int count = Math.min(payload.entries.size(), MAX_ENTRIES);
                    buffer.writeVarInt(count);
                    for (int index = 0; index < count; index++) {
                        QuestEntry.encode(buffer, payload.entries.get(index));
                    }
                    buffer.writeVarLong(Math.max(0L, payload.spursRequired));
                    buffer.writeVarLong(Math.max(0L, payload.spursDelivered));
                    buffer.writeVarInt(Math.max(0, payload.killsRequired));
                    buffer.writeVarInt(Math.max(0, payload.killsDone));
                    buffer.writeBoolean(payload.killsOrTrophy);
                    buffer.writeBoolean(payload.questComplete);
                    buffer.writeVarInt(Math.max(0, payload.buffCrystalCost));
                    buffer.writeVarInt(Math.max(0, payload.buffMinutes));
                    buffer.writeVarLong(Math.max(0L, payload.buffRemainingMillis));
                    buffer.writeBoolean(payload.buffOwnedByViewer);
                    buffer.writeDouble(payload.effectPrimary);
                    buffer.writeDouble(payload.effectSecondary);
                    buffer.writeVarInt(Math.max(0, payload.effectExtra));
                    buffer.writeBoolean(payload.notice.isPresent());
                    payload.notice.ifPresent(notice ->
                            ComponentSerialization.STREAM_CODEC.encode(buffer, notice));
                    buffer.writeBoolean(payload.noticeSuccessful);
                },
                buffer -> {
                    BlockPos statuePos = buffer.readBlockPos();
                    boolean greatStatue = buffer.readBoolean();
                    byte god = buffer.readByte();
                    int level = buffer.readVarInt();
                    int count = buffer.readVarInt();
                    if (count < 0 || count > MAX_ENTRIES) {
                        throw new DecoderException("Faith quest entry count " + count + " exceeds " + MAX_ENTRIES);
                    }
                    List<QuestEntry> entries = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        entries.add(QuestEntry.decode(buffer));
                    }
                    return new S2CFaithState(
                            statuePos,
                            greatStatue,
                            god,
                            level,
                            List.copyOf(entries),
                            buffer.readVarLong(),
                            buffer.readVarLong(),
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readBoolean(),
                            buffer.readBoolean(),
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readVarLong(),
                            buffer.readBoolean(),
                            buffer.readDouble(),
                            buffer.readDouble(),
                            buffer.readVarInt(),
                            buffer.readBoolean()
                                    ? Optional.of(ComponentSerialization.STREAM_CODEC.decode(buffer))
                                    : Optional.empty(),
                            buffer.readBoolean()
                    );
                }
        );

        public S2CFaithState {
            entries = List.copyOf(entries);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SFaithAction(BlockPos statuePos, byte action) implements CustomPacketPayload {
        public static final Type<C2SFaithAction> TYPE = payloadType("action");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SFaithAction> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.statuePos);
                    buffer.writeByte(payload.action);
                },
                buffer -> new C2SFaithAction(buffer.readBlockPos(), buffer.readByte())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2COreHighlight(boolean enabled, int radius, int maxBlocks, int scanTicks)
            implements CustomPacketPayload {
        public static final Type<S2COreHighlight> TYPE = payloadType("ore_highlight");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2COreHighlight> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBoolean(payload.enabled);
                    buffer.writeVarInt(Math.max(0, payload.radius));
                    buffer.writeVarInt(Math.max(0, payload.maxBlocks));
                    buffer.writeVarInt(Math.max(0, payload.scanTicks));
                },
                buffer -> new S2COreHighlight(
                        buffer.readBoolean(),
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readVarInt()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "faith_" + path)
        );
    }

    private FaithPayloads() {
    }
}
