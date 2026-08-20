package com.geydev.kalfactions.block;

import com.geydev.kalfactions.menu.KeyForgeMenu;
import com.geydev.kalfactions.registry.ModBlockEntities;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.registry.ModItems;
import com.geydev.kalfactions.registry.ModMenuTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public enum KeyForgeType {
    GHOST("ghost_key_forge"),
    SCULK("sculk_key_forge"),
    INFERNAL("infernal_key_forge"),
    MOSSY("mossy_key_forge");

    private final String serializedName;

    KeyForgeType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public String displayNameKey() {
        return "block.kingdoms." + serializedName;
    }

    public Block block() {
        return switch (this) {
            case GHOST -> ModBlocks.GHOST_KEY_FORGE.get();
            case SCULK -> ModBlocks.SCULK_KEY_FORGE.get();
            case INFERNAL -> ModBlocks.INFERNAL_KEY_FORGE.get();
            case MOSSY -> ModBlocks.MOSSY_KEY_FORGE.get();
        };
    }

    public Item bowFragment() {
        return switch (this) {
            case GHOST -> ModItems.GHOST_KEY_BOW_FRAGMENT.get();
            case SCULK -> ModItems.SCULK_KEY_BOW_FRAGMENT.get();
            case INFERNAL -> ModItems.INFERNAL_KEY_BOW_FRAGMENT.get();
            case MOSSY -> ModItems.MOSSY_KEY_BOW_FRAGMENT.get();
        };
    }

    public Item shaftFragment() {
        return switch (this) {
            case GHOST -> ModItems.GHOST_KEY_SHAFT_FRAGMENT.get();
            case SCULK -> ModItems.SCULK_KEY_SHAFT_FRAGMENT.get();
            case INFERNAL -> ModItems.INFERNAL_KEY_SHAFT_FRAGMENT.get();
            case MOSSY -> ModItems.MOSSY_KEY_SHAFT_FRAGMENT.get();
        };
    }

    public Item bitFragment() {
        return switch (this) {
            case GHOST -> ModItems.GHOST_KEY_BIT_FRAGMENT.get();
            case SCULK -> ModItems.SCULK_KEY_BIT_FRAGMENT.get();
            case INFERNAL -> ModItems.INFERNAL_KEY_BIT_FRAGMENT.get();
            case MOSSY -> ModItems.MOSSY_KEY_BIT_FRAGMENT.get();
        };
    }

    public Item result() {
        return switch (this) {
            case GHOST -> ModItems.GHOST_KEY.get();
            case SCULK -> ModItems.SCULK_KEY.get();
            case INFERNAL -> ModItems.INFERNAL_KEY.get();
            case MOSSY -> ModItems.MOSSY_KEY.get();
        };
    }

    public BlockEntityType<KeyForgeBlockEntity> blockEntityType() {
        return switch (this) {
            case GHOST -> ModBlockEntities.GHOST_KEY_FORGE.get();
            case SCULK -> ModBlockEntities.SCULK_KEY_FORGE.get();
            case INFERNAL -> ModBlockEntities.INFERNAL_KEY_FORGE.get();
            case MOSSY -> ModBlockEntities.MOSSY_KEY_FORGE.get();
        };
    }

    public MenuType<KeyForgeMenu> menuType() {
        return switch (this) {
            case GHOST -> ModMenuTypes.GHOST_KEY_FORGE.get();
            case SCULK -> ModMenuTypes.SCULK_KEY_FORGE.get();
            case INFERNAL -> ModMenuTypes.INFERNAL_KEY_FORGE.get();
            case MOSSY -> ModMenuTypes.MOSSY_KEY_FORGE.get();
        };
    }
}
