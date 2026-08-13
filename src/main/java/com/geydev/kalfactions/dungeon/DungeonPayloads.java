package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.KalFactions;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class DungeonPayloads {
    public static final int MAP_RADIUS = 8;
    public static final int MAX_BATCH_CHUNKS = 512;
    public static final int MAX_STATE_CHUNKS = 1024;

    public record C2SRenameDungeon(int dungeonId, String name) implements CustomPacketPayload {
        public static final Type<C2SRenameDungeon> TYPE = payloadType("dungeon_rename");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SRenameDungeon> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.dungeonId);
                    buffer.writeUtf(payload.name, DungeonManager.MAX_NAME_LENGTH * 4);
                },
                buffer -> new C2SRenameDungeon(
                        buffer.readVarInt(),
                        buffer.readUtf(DungeonManager.MAX_NAME_LENGTH * 4)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SRemoveDungeon(int dungeonId) implements CustomPacketPayload {
        public static final Type<C2SRemoveDungeon> TYPE = payloadType("dungeon_remove");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SRemoveDungeon> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeVarInt(payload.dungeonId),
                buffer -> new C2SRemoveDungeon(buffer.readVarInt())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SDungeonSetClaim(
            int dungeonId,
            BlockPos corePos,
            int chunkX,
            int chunkZ,
            boolean marked
    ) implements CustomPacketPayload {
        public static final Type<C2SDungeonSetClaim> TYPE = payloadType("dungeon_set_claim");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SDungeonSetClaim> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.dungeonId);
                    buffer.writeBlockPos(payload.corePos);
                    buffer.writeInt(payload.chunkX);
                    buffer.writeInt(payload.chunkZ);
                    buffer.writeBoolean(payload.marked);
                },
                buffer -> new C2SDungeonSetClaim(
                        buffer.readVarInt(),
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

    public record C2SDungeonMapSet(
            int dungeonId,
            boolean marked,
            List<Long> chunks
    ) implements CustomPacketPayload {
        public static final Type<C2SDungeonMapSet> TYPE = payloadType("dungeon_map_set");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SDungeonMapSet> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.dungeonId);
                    buffer.writeBoolean(payload.marked);
                    int size = Math.min(payload.chunks.size(), MAX_BATCH_CHUNKS);
                    buffer.writeVarInt(size);
                    for (int index = 0; index < size; index++) {
                        buffer.writeLong(payload.chunks.get(index));
                    }
                },
                buffer -> {
                    int dungeonId = buffer.readVarInt();
                    boolean marked = buffer.readBoolean();
                    int size = buffer.readVarInt();
                    if (size < 0 || size > MAX_BATCH_CHUNKS) {
                        throw new DecoderException("Dungeon map batch size " + size + " exceeds " + MAX_BATCH_CHUNKS);
                    }
                    List<Long> chunks = new ArrayList<>(size);
                    for (int index = 0; index < size; index++) {
                        chunks.add(buffer.readLong());
                    }
                    return new C2SDungeonMapSet(dungeonId, marked, List.copyOf(chunks));
                }
        );

        public C2SDungeonMapSet {
            chunks = chunks == null ? List.of() : List.copyOf(chunks);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2COpenDungeon(
            int dungeonId,
            String name,
            BlockPos corePos,
            ResourceLocation dimension,
            int chunkCount,
            int containerCount,
            int centerChunkX,
            int centerChunkZ,
            int radius,
            List<Long> chunks,
            Component message,
            boolean successful
    ) implements CustomPacketPayload {
        public static final Type<S2COpenDungeon> TYPE = payloadType("dungeon_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2COpenDungeon> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.dungeonId);
                    buffer.writeUtf(payload.name, DungeonManager.MAX_NAME_LENGTH * 4);
                    buffer.writeBlockPos(payload.corePos);
                    buffer.writeResourceLocation(payload.dimension);
                    buffer.writeVarInt(payload.chunkCount);
                    buffer.writeVarInt(payload.containerCount);
                    buffer.writeInt(payload.centerChunkX);
                    buffer.writeInt(payload.centerChunkZ);
                    buffer.writeVarInt(payload.radius);
                    int size = Math.min(payload.chunks.size(), MAX_STATE_CHUNKS);
                    buffer.writeVarInt(size);
                    for (int index = 0; index < size; index++) {
                        buffer.writeLong(payload.chunks.get(index));
                    }
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, payload.message);
                    buffer.writeBoolean(payload.successful);
                },
                buffer -> {
                    int dungeonId = buffer.readVarInt();
                    String name = buffer.readUtf(DungeonManager.MAX_NAME_LENGTH * 4);
                    BlockPos corePos = buffer.readBlockPos();
                    ResourceLocation dimension = buffer.readResourceLocation();
                    int chunkCount = buffer.readVarInt();
                    int containerCount = buffer.readVarInt();
                    int centerChunkX = buffer.readInt();
                    int centerChunkZ = buffer.readInt();
                    int radius = Math.clamp(buffer.readVarInt(), 0, MAP_RADIUS);
                    int size = buffer.readVarInt();
                    if (size < 0 || size > MAX_STATE_CHUNKS) {
                        throw new DecoderException("Dungeon chunk batch size " + size + " exceeds " + MAX_STATE_CHUNKS);
                    }
                    List<Long> chunks = new ArrayList<>(size);
                    for (int index = 0; index < size; index++) {
                        chunks.add(buffer.readLong());
                    }
                    return new S2COpenDungeon(
                            dungeonId,
                            name,
                            corePos,
                            dimension,
                            chunkCount,
                            containerCount,
                            centerChunkX,
                            centerChunkZ,
                            radius,
                            List.copyOf(chunks),
                            ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer),
                            buffer.readBoolean()
                    );
                }
        );

        public S2COpenDungeon {
            name = DungeonManager.normalizeName(name);
            chunks = chunks == null ? List.of() : List.copyOf(chunks);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SDungeonChestAction(
            BlockPos pos,
            int action,
            int mode,
            String lootTable,
            int cooldownHours
    ) implements CustomPacketPayload {
        public static final int ACTION_APPLY = 0;
        public static final int ACTION_SAVE_TEMPLATE = 1;
        public static final int ACTION_REFRESH = 2;
        public static final int MAX_TABLE_LENGTH = 128;
        public static final Type<C2SDungeonChestAction> TYPE = payloadType("dungeon_chest_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SDungeonChestAction> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {
                            buffer.writeBlockPos(payload.pos);
                            buffer.writeVarInt(payload.action);
                            buffer.writeVarInt(payload.mode);
                            buffer.writeUtf(payload.lootTable, MAX_TABLE_LENGTH);
                            buffer.writeVarInt(payload.cooldownHours + 1);
                        },
                        buffer -> new C2SDungeonChestAction(
                                buffer.readBlockPos(),
                                Math.clamp(buffer.readVarInt(), ACTION_APPLY, ACTION_REFRESH),
                                Math.clamp(buffer.readVarInt(), 0, 1),
                                buffer.readUtf(MAX_TABLE_LENGTH),
                                Math.clamp(buffer.readVarInt() - 1, -1, 8760)
                        )
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CDungeonChestState(
            BlockPos pos,
            int mode,
            String lootTable,
            int cooldownHours,
            int effectiveCooldownHours,
            int templateCount,
            long remainingMillis,
            boolean configured,
            Component message,
            boolean successful
    ) implements CustomPacketPayload {
        public static final Type<S2CDungeonChestState> TYPE = payloadType("dungeon_chest_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CDungeonChestState> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {
                            buffer.writeBlockPos(payload.pos);
                            buffer.writeVarInt(payload.mode);
                            buffer.writeUtf(payload.lootTable, C2SDungeonChestAction.MAX_TABLE_LENGTH);
                            buffer.writeVarInt(payload.cooldownHours + 1);
                            buffer.writeVarInt(payload.effectiveCooldownHours);
                            buffer.writeVarInt(payload.templateCount);
                            buffer.writeLong(payload.remainingMillis);
                            buffer.writeBoolean(payload.configured);
                            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, payload.message);
                            buffer.writeBoolean(payload.successful);
                        },
                        buffer -> new S2CDungeonChestState(
                                buffer.readBlockPos(),
                                Math.clamp(buffer.readVarInt(), 0, 1),
                                buffer.readUtf(C2SDungeonChestAction.MAX_TABLE_LENGTH),
                                Math.clamp(buffer.readVarInt() - 1, -1, 8760),
                                Math.clamp(buffer.readVarInt(), 0, 8760),
                                Math.max(0, buffer.readVarInt()),
                                Math.max(0L, buffer.readLong()),
                                buffer.readBoolean(),
                                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer),
                                buffer.readBoolean()
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

    private DungeonPayloads() {
    }
}
