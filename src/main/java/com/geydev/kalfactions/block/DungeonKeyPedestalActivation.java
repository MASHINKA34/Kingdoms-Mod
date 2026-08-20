package com.geydev.kalfactions.block;

import com.geydev.kalfactions.registry.ModItems;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

public enum DungeonKeyPedestalActivation implements StringRepresentable {
    NONE("none"),
    GHOST("ghost"),
    SCULK("sculk"),
    INFERNAL("infernal"),
    MOSSY("mossy");

    private final String serializedName;

    DungeonKeyPedestalActivation(String serializedName) {
        this.serializedName = serializedName;
    }

    public static DungeonKeyPedestalActivation fromKey(ItemStack stack) {
        if (stack.is(ModItems.GHOST_KEY.get())) {
            return GHOST;
        }
        if (stack.is(ModItems.SCULK_KEY.get())) {
            return SCULK;
        }
        if (stack.is(ModItems.INFERNAL_KEY.get())) {
            return INFERNAL;
        }
        if (stack.is(ModItems.MOSSY_KEY.get())) {
            return MOSSY;
        }
        return NONE;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
