package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class PlayerResearchCrystalPayment implements ResearchCrystalPayment {
    private final Inventory inventory;

    public PlayerResearchCrystalPayment(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public int available(InfluenceType type) {
        Item item = ModItems.crystalFor(type);
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                if (total > Integer.MAX_VALUE - stack.getCount()) {
                    return Integer.MAX_VALUE;
                }
                total += stack.getCount();
            }
        }
        return total;
    }

    @Override
    public boolean consumeExact(InfluenceType type, int amount) {
        if (amount < 0) {
            return false;
        }
        if (amount == 0) {
            return true;
        }
        Item item = ModItems.crystalFor(type);
        List<SlotCharge> charges = new ArrayList<>();
        int remaining = amount;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            int charge = Math.min(stack.getCount(), remaining);
            charges.add(new SlotCharge(slot, charge));
            remaining -= charge;
        }
        if (remaining > 0) {
            return false;
        }
        for (SlotCharge charge : charges) {
            ItemStack stack = inventory.getItem(charge.slot());
            if (!stack.is(item) || stack.getCount() < charge.amount()) {
                return false;
            }
        }
        for (SlotCharge charge : charges) {
            inventory.getItem(charge.slot()).shrink(charge.amount());
        }
        inventory.setChanged();
        return true;
    }

    private record SlotCharge(int slot, int amount) {
    }
}
