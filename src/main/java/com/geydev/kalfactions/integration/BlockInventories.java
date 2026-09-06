package com.geydev.kalfactions.integration;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

public final class BlockInventories {
    public static IItemHandler localHandler(ServerLevel level, BlockEntity entity) {
        if (ModList.get().isLoaded("create")) {
            IItemHandler inventory = CreateOnly.vaultInventory(entity);
            if (inventory != null) {
                return inventory;
            }
        }
        return level.getCapability(Capabilities.ItemHandler.BLOCK, entity.getBlockPos(), null);
    }

    private static final class CreateOnly {
        private static IItemHandler vaultInventory(BlockEntity entity) {
            return entity instanceof com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity vault
                    ? vault.getInventoryOfBlock() : null;
        }
    }

    private BlockInventories() {
    }
}
