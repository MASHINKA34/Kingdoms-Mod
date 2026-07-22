package com.geydev.kalfactions.outpost.trader;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public enum TraderPointToolMode {
    EDIT,
    SHOW;

    public static final Codec<TraderPointToolMode> CODEC = Codec.STRING.xmap(
            value -> {
                try {
                    return valueOf(value.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    return EDIT;
                }
            },
            value -> value.name().toLowerCase(Locale.ROOT)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, TraderPointToolMode> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeEnum(value),
            buffer -> buffer.readEnum(TraderPointToolMode.class)
    );

    public TraderPointToolMode next() {
        return this == EDIT ? SHOW : EDIT;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
