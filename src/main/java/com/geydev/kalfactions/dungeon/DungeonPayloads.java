package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.DungeonChestBlockEntity;
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
import net.minecraft.world.item.ItemStack;

public final class DungeonPayloads {
    public static final int MAP_RADIUS = 8;
    public static final int MAX_BATCH_CHUNKS = 512;
    public static final int MAX_STATE_CHUNKS = 1024;

    public record C2SRenameDungeon(int dungeonId, String name) implements CustomPacketPayload {
        public static final Type<C2SRenameDungeon> TYPE = payloadType("dungeon_rename");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SRenameDungeon> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.dungeonId);
                    buffer.writeUtf(payload.name, DungeonManager.MAX_NAME_CHARS);
                },
                buffer -> new C2SRenameDungeon(
                        buffer.readVarInt(),
                        buffer.readUtf(DungeonManager.MAX_NAME_CHARS)
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
                    buffer.writeUtf(payload.name, DungeonManager.MAX_NAME_CHARS);
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
                    String name = buffer.readUtf(DungeonManager.MAX_NAME_CHARS);
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

    public record C2SDungeonChestEntry(
            BlockPos pos,
            int slot,
            int chance,
            int min,
            int max,
            int cooldownHours,
            boolean announce
    ) implements CustomPacketPayload {
        public static final int NO_SLOT = -1;
        public static final Type<C2SDungeonChestEntry> TYPE = payloadType("dungeon_chest_entry");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SDungeonChestEntry> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {
                            buffer.writeBlockPos(payload.pos);
                            buffer.writeVarInt(payload.slot + 1);
                            buffer.writeVarInt(payload.chance);
                            buffer.writeVarInt(payload.min);
                            buffer.writeVarInt(payload.max);
                            buffer.writeVarInt(payload.cooldownHours + 1);
                            buffer.writeBoolean(payload.announce);
                        },
                        buffer -> new C2SDungeonChestEntry(
                                buffer.readBlockPos(),
                                Math.clamp(buffer.readVarInt() - 1, NO_SLOT, DungeonChestBlockEntity.SIZE - 1),
                                Math.clamp(buffer.readVarInt(), 0, 100),
                                Math.clamp(buffer.readVarInt(), 1, DungeonChestBlockEntity.MAX_COUNT),
                                Math.clamp(buffer.readVarInt(), 1, DungeonChestBlockEntity.MAX_COUNT),
                                Math.clamp(
                                        buffer.readVarInt() - 1,
                                        -1,
                                        DungeonChestBlockEntity.MAX_COOLDOWN_HOURS
                                ),
                                buffer.readBoolean()
                        )
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SDungeonChestRefill(BlockPos pos) implements CustomPacketPayload {
        public static final Type<C2SDungeonChestRefill> TYPE = payloadType("dungeon_chest_refill");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SDungeonChestRefill> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> buffer.writeBlockPos(payload.pos),
                        buffer -> new C2SDungeonChestRefill(buffer.readBlockPos())
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SChestTemplateAction(
            BlockPos pos,
            UUID requestId,
            int action,
            UUID templateId,
            String name,
            boolean applyCooldown,
            boolean overwrite
    ) implements CustomPacketPayload {
        public static final int SYNC = 0;
        public static final int SAVE = 1;
        public static final int APPLY = 2;
        public static final int APPLY_ALL = 3;
        public static final int RENAME = 4;
        public static final int DELETE = 5;
        public static final int LAST_ACTION = DELETE;

        public static final Type<C2SChestTemplateAction> TYPE = payloadType("chest_template_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SChestTemplateAction> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {
                            buffer.writeBlockPos(payload.pos);
                            buffer.writeUUID(payload.requestId);
                            buffer.writeVarInt(payload.action);
                            buffer.writeUUID(payload.templateId);
                            buffer.writeUtf(payload.name, ChestTemplate.MAX_NAME_CHARS);
                            buffer.writeBoolean(payload.applyCooldown);
                            buffer.writeBoolean(payload.overwrite);
                        },
                        buffer -> new C2SChestTemplateAction(
                                buffer.readBlockPos(),
                                buffer.readUUID(),
                                Math.clamp(buffer.readVarInt(), SYNC, LAST_ACTION),
                                buffer.readUUID(),
                                buffer.readUtf(ChestTemplate.MAX_NAME_CHARS),
                                buffer.readBoolean(),
                                buffer.readBoolean()
                        )
                );

        public C2SChestTemplateAction {
            requestId = requestId == null ? Util.NIL_UUID : requestId;
            templateId = templateId == null ? Util.NIL_UUID : templateId;
            name = ChestTemplate.normalizeName(name);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ChestTemplateSlot(int slot, ItemStack stack, int chance, int min, int max) {
        private static void encode(RegistryFriendlyByteBuf buffer, ChestTemplateSlot entry) {
            buffer.writeVarInt(entry.slot);
            ItemStack.STREAM_CODEC.encode(buffer, entry.stack);
            buffer.writeVarInt(entry.chance);
            buffer.writeVarInt(entry.min);
            buffer.writeVarInt(entry.max);
        }

        private static ChestTemplateSlot decode(RegistryFriendlyByteBuf buffer) {
            return new ChestTemplateSlot(
                    Math.clamp(buffer.readVarInt(), 0, DungeonChestBlockEntity.SIZE - 1),
                    ItemStack.STREAM_CODEC.decode(buffer),
                    Math.clamp(buffer.readVarInt(), 0, 100),
                    Math.clamp(buffer.readVarInt(), 1, DungeonChestBlockEntity.MAX_COUNT),
                    Math.clamp(buffer.readVarInt(), 1, DungeonChestBlockEntity.MAX_COUNT)
            );
        }
    }

    public record ChestTemplateView(
            UUID id,
            String name,
            String author,
            long createdAt,
            int cooldownHours,
            List<ChestTemplateSlot> slots
    ) {
        public ChestTemplateView {
            slots = slots == null ? List.of() : List.copyOf(slots);
        }

        private static void encode(RegistryFriendlyByteBuf buffer, ChestTemplateView view) {
            buffer.writeUUID(view.id);
            buffer.writeUtf(view.name, ChestTemplate.MAX_NAME_CHARS);
            buffer.writeUtf(view.author, ChestTemplate.MAX_AUTHOR_CHARS);
            buffer.writeLong(view.createdAt);
            buffer.writeVarInt(view.cooldownHours + 1);
            int size = Math.min(view.slots.size(), DungeonChestBlockEntity.SIZE);
            buffer.writeVarInt(size);
            for (int index = 0; index < size; index++) {
                ChestTemplateSlot.encode(buffer, view.slots.get(index));
            }
        }

        private static ChestTemplateView decode(RegistryFriendlyByteBuf buffer) {
            UUID id = buffer.readUUID();
            String name = buffer.readUtf(ChestTemplate.MAX_NAME_CHARS);
            String author = buffer.readUtf(ChestTemplate.MAX_AUTHOR_CHARS);
            long createdAt = buffer.readLong();
            int cooldownHours = Math.clamp(
                    buffer.readVarInt() - 1,
                    -1,
                    DungeonChestBlockEntity.MAX_COOLDOWN_HOURS
            );
            int size = buffer.readVarInt();
            if (size < 0 || size > DungeonChestBlockEntity.SIZE) {
                throw new DecoderException("Chest template slot count " + size + " exceeds "
                        + DungeonChestBlockEntity.SIZE);
            }
            List<ChestTemplateSlot> slots = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                slots.add(ChestTemplateSlot.decode(buffer));
            }
            return new ChestTemplateView(id, name, author, createdAt, cooldownHours, slots);
        }
    }

    public record S2CChestTemplates(BlockPos pos, List<ChestTemplateView> templates)
            implements CustomPacketPayload {
        public static final int MAX_TEMPLATES = 512;
        public static final Type<S2CChestTemplates> TYPE = payloadType("chest_templates");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CChestTemplates> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {
                            buffer.writeBlockPos(payload.pos);
                            int size = Math.min(payload.templates.size(), MAX_TEMPLATES);
                            buffer.writeVarInt(size);
                            for (int index = 0; index < size; index++) {
                                ChestTemplateView.encode(buffer, payload.templates.get(index));
                            }
                        },
                        buffer -> {
                            BlockPos pos = buffer.readBlockPos();
                            int size = buffer.readVarInt();
                            if (size < 0 || size > MAX_TEMPLATES) {
                                throw new DecoderException(
                                        "Chest template count " + size + " exceeds " + MAX_TEMPLATES);
                            }
                            List<ChestTemplateView> templates = new ArrayList<>(size);
                            for (int index = 0; index < size; index++) {
                                templates.add(ChestTemplateView.decode(buffer));
                            }
                            return new S2CChestTemplates(pos, templates);
                        }
                );

        public S2CChestTemplates {
            templates = templates == null ? List.of() : List.copyOf(templates);
        }

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
