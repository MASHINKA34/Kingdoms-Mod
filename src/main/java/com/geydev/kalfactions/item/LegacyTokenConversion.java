package com.geydev.kalfactions.item;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.registry.ModItems;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class LegacyTokenConversion {
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            convertInventory(player);
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean changed = convertInventory(player);
        AbstractContainerMenu menu = event.getContainer();
        for (Slot slot : menu.slots) {
            ItemStack replacement = replacementFor(slot.getItem());
            if (!replacement.isEmpty()) {
                slot.set(replacement);
                changed = true;
            }
        }
        if (changed) {
            menu.broadcastChanges();
        }
    }

    public static boolean convertInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        boolean changed = false;
        for (NonNullList<ItemStack> compartment : List.of(inventory.items, inventory.armor, inventory.offhand)) {
            for (int index = 0; index < compartment.size(); index++) {
                ItemStack replacement = replacementFor(compartment.get(index));
                if (!replacement.isEmpty()) {
                    compartment.set(index, replacement);
                    changed = true;
                }
            }
        }
        ItemStack carried = replacementFor(player.containerMenu.getCarried());
        if (!carried.isEmpty()) {
            player.containerMenu.setCarried(carried);
            changed = true;
        }
        if (changed) {
            player.inventoryMenu.broadcastChanges();
        }
        return changed;
    }

    public static ItemStack replacementFor(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ModItems.MINIBOSS_TOKEN_END.get())) {
            return ItemStack.EMPTY;
        }
        ItemStack replacement = new ItemStack(ModItems.MINIBOSS_TOKEN.get(), stack.getCount());
        replacement.applyComponents(stack.getComponentsPatch());
        return replacement;
    }

    private LegacyTokenConversion() {
    }
}
