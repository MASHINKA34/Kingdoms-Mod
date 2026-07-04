package com.geydev.kalfactions.market;

import com.geydev.kalfactions.KalFactions;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class MarketPayloads {
    public static final int MAX_PLOTS = 512;
    public static final int MAX_NAME_LENGTH = 48;
    public static final int MAX_TRUST_ENTRIES = 256;

    public record PlotEntry(
            int id,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            byte state,
            long price,
            Optional<UUID> owner,
            String ownerName,
            boolean access
    ) {
        static void encode(RegistryFriendlyByteBuf buffer, PlotEntry entry) {
            buffer.writeVarInt(entry.id);
            buffer.writeVarInt(entry.minX);
            buffer.writeVarInt(entry.minY);
            buffer.writeVarInt(entry.minZ);
            buffer.writeVarInt(entry.maxX);
            buffer.writeVarInt(entry.maxY);
            buffer.writeVarInt(entry.maxZ);
            buffer.writeByte(entry.state);
            buffer.writeVarLong(entry.price);
            buffer.writeBoolean(entry.owner.isPresent());
            entry.owner.ifPresent(buffer::writeUUID);
            buffer.writeUtf(entry.ownerName, MAX_NAME_LENGTH);
            buffer.writeBoolean(entry.access);
        }

        static PlotEntry decode(RegistryFriendlyByteBuf buffer) {
            return new PlotEntry(
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readByte(),
                    buffer.readVarLong(),
                    buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.empty(),
                    buffer.readUtf(MAX_NAME_LENGTH),
                    buffer.readBoolean()
            );
        }
    }

    public record TrustEntry(UUID id, String name, int color) {
        static void encode(RegistryFriendlyByteBuf buffer, TrustEntry entry) {
            buffer.writeUUID(entry.id);
            buffer.writeUtf(entry.name, MAX_NAME_LENGTH);
            buffer.writeVarInt(entry.color);
        }

        static TrustEntry decode(RegistryFriendlyByteBuf buffer) {
            return new TrustEntry(
                    buffer.readUUID(),
                    buffer.readUtf(MAX_NAME_LENGTH),
                    buffer.readVarInt()
            );
        }

        static void encodeList(RegistryFriendlyByteBuf buffer, List<TrustEntry> entries) {
            int size = Math.min(entries.size(), MAX_TRUST_ENTRIES);
            buffer.writeVarInt(size);
            for (int i = 0; i < size; i++) {
                encode(buffer, entries.get(i));
            }
        }

        static List<TrustEntry> decodeList(RegistryFriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            if (size < 0 || size > MAX_TRUST_ENTRIES) {
                throw new DecoderException("Trust entry count " + size + " exceeds " + MAX_TRUST_ENTRIES);
            }
            List<TrustEntry> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                entries.add(decode(buffer));
            }
            return List.copyOf(entries);
        }
    }

    public record PlayerCandidate(UUID id, String name, String factionName) {
        static void encode(RegistryFriendlyByteBuf buffer, PlayerCandidate candidate) {
            buffer.writeUUID(candidate.id);
            buffer.writeUtf(candidate.name, MAX_NAME_LENGTH);
            buffer.writeUtf(candidate.factionName, MAX_NAME_LENGTH);
        }

        static PlayerCandidate decode(RegistryFriendlyByteBuf buffer) {
            return new PlayerCandidate(
                    buffer.readUUID(),
                    buffer.readUtf(MAX_NAME_LENGTH),
                    buffer.readUtf(MAX_NAME_LENGTH)
            );
        }
    }

    public record S2CPlotTrustState(
            int plotId,
            List<TrustEntry> trustedPlayers,
            List<TrustEntry> trustedFactions,
            List<PlayerCandidate> playerCandidates,
            List<TrustEntry> factionCandidates
    ) implements CustomPacketPayload {
        public static final Type<S2CPlotTrustState> TYPE = payloadType("market_plot_trust_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CPlotTrustState> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.plotId);
                    TrustEntry.encodeList(buffer, payload.trustedPlayers);
                    TrustEntry.encodeList(buffer, payload.trustedFactions);
                    int candidateCount = Math.min(payload.playerCandidates.size(), MAX_TRUST_ENTRIES);
                    buffer.writeVarInt(candidateCount);
                    for (int i = 0; i < candidateCount; i++) {
                        PlayerCandidate.encode(buffer, payload.playerCandidates.get(i));
                    }
                    TrustEntry.encodeList(buffer, payload.factionCandidates);
                },
                buffer -> {
                    int plotId = buffer.readVarInt();
                    List<TrustEntry> trustedPlayers = TrustEntry.decodeList(buffer);
                    List<TrustEntry> trustedFactions = TrustEntry.decodeList(buffer);
                    int candidateCount = buffer.readVarInt();
                    if (candidateCount < 0 || candidateCount > MAX_TRUST_ENTRIES) {
                        throw new DecoderException("Trust candidate count " + candidateCount
                                + " exceeds " + MAX_TRUST_ENTRIES);
                    }
                    List<PlayerCandidate> playerCandidates = new ArrayList<>(candidateCount);
                    for (int i = 0; i < candidateCount; i++) {
                        playerCandidates.add(PlayerCandidate.decode(buffer));
                    }
                    return new S2CPlotTrustState(
                            plotId,
                            trustedPlayers,
                            trustedFactions,
                            List.copyOf(playerCandidates),
                            TrustEntry.decodeList(buffer)
                    );
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SRequestPlotTrust(int plotId) implements CustomPacketPayload {
        public static final Type<C2SRequestPlotTrust> TYPE = payloadType("market_request_plot_trust");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestPlotTrust> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeVarInt(payload.plotId),
                buffer -> new C2SRequestPlotTrust(buffer.readVarInt())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SEditPlotTrust(
            int plotId,
            boolean add,
            boolean faction,
            UUID targetId
    ) implements CustomPacketPayload {
        public static final Type<C2SEditPlotTrust> TYPE = payloadType("market_edit_plot_trust");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SEditPlotTrust> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.plotId);
                    buffer.writeBoolean(payload.add);
                    buffer.writeBoolean(payload.faction);
                    buffer.writeUUID(payload.targetId);
                },
                buffer -> new C2SEditPlotTrust(
                        buffer.readVarInt(),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readUUID()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SCreatePlot(long price) implements CustomPacketPayload {
        public static final Type<C2SCreatePlot> TYPE = payloadType("market_create_plot");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SCreatePlot> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeVarLong(payload.price),
                buffer -> new C2SCreatePlot(buffer.readVarLong())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SAdjustPlotSelection(byte face, byte delta) implements CustomPacketPayload {
        public static final Type<C2SAdjustPlotSelection> TYPE = payloadType("market_adjust_plot_selection");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SAdjustPlotSelection> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeByte(payload.face);
                    buffer.writeByte(payload.delta);
                },
                buffer -> new C2SAdjustPlotSelection(buffer.readByte(), buffer.readByte())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CSyncPlots(ResourceLocation dimension, List<PlotEntry> plots) implements CustomPacketPayload {
        public static final Type<S2CSyncPlots> TYPE = payloadType("market_sync_plots");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CSyncPlots> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeResourceLocation(payload.dimension);
                    int size = Math.min(payload.plots.size(), MAX_PLOTS);
                    buffer.writeVarInt(size);
                    for (int i = 0; i < size; i++) {
                        PlotEntry.encode(buffer, payload.plots.get(i));
                    }
                },
                buffer -> {
                    ResourceLocation dimension = buffer.readResourceLocation();
                    int size = buffer.readVarInt();
                    if (size < 0 || size > MAX_PLOTS) {
                        throw new DecoderException("Market plot count " + size + " exceeds " + MAX_PLOTS);
                    }
                    List<PlotEntry> plots = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        plots.add(PlotEntry.decode(buffer));
                    }
                    return new S2CSyncPlots(dimension, List.copyOf(plots));
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2COpenPlotScreen(
            int plotId,
            byte state,
            boolean isOwner,
            long askingPrice,
            long buybackAmount,
            String ownerName
    ) implements CustomPacketPayload {
        public static final Type<S2COpenPlotScreen> TYPE = payloadType("market_open_plot_screen");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2COpenPlotScreen> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.plotId);
                    buffer.writeByte(payload.state);
                    buffer.writeBoolean(payload.isOwner);
                    buffer.writeVarLong(payload.askingPrice);
                    buffer.writeVarLong(payload.buybackAmount);
                    buffer.writeUtf(payload.ownerName, MAX_NAME_LENGTH);
                },
                buffer -> new S2COpenPlotScreen(
                        buffer.readVarInt(),
                        buffer.readByte(),
                        buffer.readBoolean(),
                        buffer.readVarLong(),
                        buffer.readVarLong(),
                        buffer.readUtf(MAX_NAME_LENGTH)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SBuyPlot(int plotId) implements CustomPacketPayload {
        public static final Type<C2SBuyPlot> TYPE = payloadType("market_buy_plot");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SBuyPlot> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeVarInt(payload.plotId),
                buffer -> new C2SBuyPlot(buffer.readVarInt())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SManagePlot(int plotId, byte action, long price) implements CustomPacketPayload {
        public static final byte ACTION_LIST_RESALE = 0;
        public static final byte ACTION_CANCEL_RESALE = 1;
        public static final byte ACTION_SELL_TO_SERVER = 2;

        public static final Type<C2SManagePlot> TYPE = payloadType("market_manage_plot");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SManagePlot> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.plotId);
                    buffer.writeByte(payload.action);
                    buffer.writeVarLong(payload.price);
                },
                buffer -> new C2SManagePlot(
                        buffer.readVarInt(),
                        buffer.readByte(),
                        buffer.readVarLong()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, path));
    }

    private MarketPayloads() {
    }
}
