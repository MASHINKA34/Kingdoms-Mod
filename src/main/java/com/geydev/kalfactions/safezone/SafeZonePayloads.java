package com.geydev.kalfactions.safezone;

import com.geydev.kalfactions.KalFactions;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class SafeZonePayloads {
    public record ZoneEntry(
            String id,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ
    ) {
        static void encode(RegistryFriendlyByteBuf buffer, ZoneEntry entry) {
            buffer.writeUtf(entry.id, SafeZoneManager.MAX_ID_LENGTH);
            buffer.writeVarInt(entry.minX);
            buffer.writeVarInt(entry.minY);
            buffer.writeVarInt(entry.minZ);
            buffer.writeVarInt(entry.maxX);
            buffer.writeVarInt(entry.maxY);
            buffer.writeVarInt(entry.maxZ);
        }

        static ZoneEntry decode(RegistryFriendlyByteBuf buffer) {
            return new ZoneEntry(
                    buffer.readUtf(SafeZoneManager.MAX_ID_LENGTH),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt()
            );
        }

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    public record S2CSyncSafeZones(ResourceLocation dimension, List<ZoneEntry> zones)
            implements CustomPacketPayload {
        public static final Type<S2CSyncSafeZones> TYPE = payloadType("safezone_sync");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CSyncSafeZones> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeResourceLocation(payload.dimension);
                    int size = Math.min(payload.zones.size(), SafeZoneManager.MAX_ZONES);
                    buffer.writeVarInt(size);
                    for (int index = 0; index < size; index++) {
                        ZoneEntry.encode(buffer, payload.zones.get(index));
                    }
                },
                buffer -> {
                    ResourceLocation dimension = buffer.readResourceLocation();
                    int size = buffer.readVarInt();
                    if (size < 0 || size > SafeZoneManager.MAX_ZONES) {
                        throw new DecoderException(
                                "Safe zone count " + size + " exceeds " + SafeZoneManager.MAX_ZONES);
                    }
                    List<ZoneEntry> zones = new ArrayList<>(size);
                    for (int index = 0; index < size; index++) {
                        zones.add(ZoneEntry.decode(buffer));
                    }
                    return new S2CSyncSafeZones(dimension, List.copyOf(zones));
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SAdjustSelection(byte face, byte delta) implements CustomPacketPayload {
        public static final Type<C2SAdjustSelection> TYPE = payloadType("safezone_adjust_selection");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SAdjustSelection> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeByte(payload.face);
                    buffer.writeByte(payload.delta);
                },
                buffer -> new C2SAdjustSelection(buffer.readByte(), buffer.readByte())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, path));
    }

    private SafeZonePayloads() {
    }
}
