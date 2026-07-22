package com.geydev.kalfactions.dimension;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record ReturnBinding(UUID playerId, UUID sessionId, UUID token, BlockPos returnPos) {
    public ReturnBinding {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(returnPos, "returnPos");
    }

    public ReturnBinding(UUID playerId, UUID sessionId, UUID token) {
        this(playerId, sessionId, token, BlockPos.ZERO);
    }

    public static final Codec<ReturnBinding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("player").forGetter(ReturnBinding::playerId),
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("session").forGetter(ReturnBinding::sessionId),
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("token").forGetter(ReturnBinding::token),
            BlockPos.CODEC.optionalFieldOf("return_pos", BlockPos.ZERO).forGetter(ReturnBinding::returnPos)
    ).apply(instance, ReturnBinding::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReturnBinding> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.core.UUIDUtil.STREAM_CODEC,
            ReturnBinding::playerId,
            net.minecraft.core.UUIDUtil.STREAM_CODEC,
            ReturnBinding::sessionId,
            net.minecraft.core.UUIDUtil.STREAM_CODEC,
            ReturnBinding::token,
            BlockPos.STREAM_CODEC,
            ReturnBinding::returnPos,
            ReturnBinding::new
    );
}
