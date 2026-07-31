package com.geydev.kalfactions.scorched;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.LootModifier;

public final class RemoveRaidFlaresLootModifier extends LootModifier {
    public static final MapCodec<RemoveRaidFlaresLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).apply(instance, RemoveRaidFlaresLootModifier::new));

    public RemoveRaidFlaresLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        generatedLoot.removeIf(DisabledRaidFlares::isDisabled);
        return generatedLoot;
    }

    @Override
    public MapCodec<RemoveRaidFlaresLootModifier> codec() {
        return CODEC;
    }
}
