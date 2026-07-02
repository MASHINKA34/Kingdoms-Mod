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

    public record PlotEntry(
            int id,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            byte state,
            long price,
            Optional<UUID> owner,
            String ownerName
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
                    buffer.readUtf(MAX_NAME_LENGTH)
            );
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
