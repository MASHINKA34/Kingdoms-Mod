package com.geydev.kalfactions.keyholder;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.registry.ModItems;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.LootModifier;

public final class BossKeyLootModifier extends LootModifier {
    public static final TagKey<EntityType<?>> BOSS_KEY_DROPPERS = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "boss_key_droppers")
    );
    public static final MapCodec<BossKeyLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance)
                    .and(Codec.FLOAT.optionalFieldOf("chance", 1.0F).forGetter(BossKeyLootModifier::chance))
                    .apply(instance, BossKeyLootModifier::new));

    private final float chance;

    public BossKeyLootModifier(LootItemCondition[] conditions, float chance) {
        super(conditions);
        this.chance = chance;
    }

    public float chance() {
        return chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (entity == null || !entity.getType().is(BOSS_KEY_DROPPERS)) {
            return generatedLoot;
        }
        if (chance < 1.0F && context.getRandom().nextFloat() >= chance) {
            return generatedLoot;
        }
        generatedLoot.add(new ItemStack(ModItems.BOSS_KEY.get()));
        return generatedLoot;
    }

    @Override
    public MapCodec<BossKeyLootModifier> codec() {
        return CODEC;
    }
}
