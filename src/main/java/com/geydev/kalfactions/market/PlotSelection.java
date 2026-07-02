package com.geydev.kalfactions.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record PlotSelection(ResourceLocation dimension, BlockPos first, Optional<BlockPos> second) {
    public static final Codec<PlotSelection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(PlotSelection::dimension),
            BlockPos.CODEC.fieldOf("first").forGetter(PlotSelection::first),
            BlockPos.CODEC.optionalFieldOf("second").forGetter(PlotSelection::second)
    ).apply(instance, PlotSelection::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlotSelection> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, PlotSelection::dimension,
            BlockPos.STREAM_CODEC, PlotSelection::first,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), PlotSelection::second,
            PlotSelection::new
    );

    public static PlotSelection start(Level level, BlockPos first) {
        return new PlotSelection(level.dimension().location(), first.immutable(), Optional.empty());
    }

    public PlotSelection withSecond(BlockPos second) {
        return new PlotSelection(dimension, first, Optional.of(second.immutable()));
    }

    public boolean isComplete() {
        return second.isPresent();
    }

    public boolean matchesDimension(Level level) {
        return level.dimension().location().equals(dimension);
    }

    public Optional<BoundingBox> box() {
        return second.map(pos -> BoundingBox.fromCorners(first, pos));
    }
}
