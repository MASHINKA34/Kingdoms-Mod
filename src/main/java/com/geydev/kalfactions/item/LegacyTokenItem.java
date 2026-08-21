package com.geydev.kalfactions.item;

import net.minecraft.world.item.Item;

public final class LegacyTokenItem extends Item {
    private final String descriptionId;

    public LegacyTokenItem(Properties properties, String descriptionId) {
        super(properties);
        this.descriptionId = descriptionId;
    }

    @Override
    public String getDescriptionId() {
        return descriptionId;
    }
}
