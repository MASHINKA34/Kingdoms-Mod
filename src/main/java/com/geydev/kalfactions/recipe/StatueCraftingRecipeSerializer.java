package com.geydev.kalfactions.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

public final class StatueCraftingRecipeSerializer implements RecipeSerializer<StatueCraftingRecipe> {
    private static final Codec<Character> SYMBOL_CODEC = Codec.STRING.comapFlatMap(
            value -> value.length() == 1
                    ? DataResult.success(value.charAt(0))
                    : DataResult.error(() -> "Invalid key entry: '" + value + "' is not a single character"),
            String::valueOf);

    private static final StreamCodec<ByteBuf, Character> SYMBOL_STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(value -> value.charAt(0), String::valueOf);

    private static final StreamCodec<RegistryFriendlyByteBuf, Map<Character, SizedIngredient>> KEY_STREAM_CODEC =
            ByteBufCodecs.map(HashMap::new, SYMBOL_STREAM_CODEC, SizedIngredient.STREAM_CODEC);

    private static final MapCodec<StatueCraftingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CraftingBookCategory.CODEC
                    .optionalFieldOf("category", CraftingBookCategory.MISC)
                    .forGetter(StatueCraftingRecipe::category),
            Codec.STRING.listOf().fieldOf("pattern").forGetter(StatueCraftingRecipe::pattern),
            ExtraCodecs.strictUnboundedMap(SYMBOL_CODEC, SizedIngredient.FLAT_CODEC)
                    .fieldOf("key")
                    .forGetter(StatueCraftingRecipe::key),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(StatueCraftingRecipe::result)
    ).apply(instance, StatueCraftingRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, StatueCraftingRecipe> STREAM_CODEC = StreamCodec.composite(
            CraftingBookCategory.STREAM_CODEC,
            StatueCraftingRecipe::category,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
            StatueCraftingRecipe::pattern,
            KEY_STREAM_CODEC,
            StatueCraftingRecipe::key,
            ItemStack.STREAM_CODEC,
            StatueCraftingRecipe::result,
            StatueCraftingRecipe::new);

    @Override
    public MapCodec<StatueCraftingRecipe> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, StatueCraftingRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
