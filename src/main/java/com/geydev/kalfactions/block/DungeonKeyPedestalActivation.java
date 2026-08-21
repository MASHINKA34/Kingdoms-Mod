package com.geydev.kalfactions.block;

import com.geydev.kalfactions.registry.ModItems;
import java.util.List;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

public enum DungeonKeyPedestalActivation implements StringRepresentable {
    NONE("none"),
    GHOST("ghost"),
    SCULK("sculk"),
    INFERNAL("infernal"),
    MOSSY("mossy");

    public static final List<DungeonKeyPedestalActivation> CONFIGURATION_VALUES =
            List.of(NONE, GHOST, SCULK, INFERNAL, MOSSY);

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

    public static DungeonKeyPedestalActivation fromSerializedName(String name) {
        for (DungeonKeyPedestalActivation activation : values()) {
            if (activation.serializedName.equals(name)) {
                return activation;
            }
        }
        return NONE;
    }

    public String displayNameKey() {
        return switch (this) {
            case NONE -> "screen.kingdoms.dungeon_key_pedestal.key_disabled";
            case GHOST -> "item.kingdoms.ghost_key";
            case SCULK -> "item.kingdoms.sculk_key";
            case INFERNAL -> "item.kingdoms.infernal_key";
            case MOSSY -> "item.kingdoms.mossy_key";
        };
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
