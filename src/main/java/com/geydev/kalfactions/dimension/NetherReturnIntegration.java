package com.geydev.kalfactions.dimension;

import com.geydev.kalfactions.KalFactions;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

public final class NetherReturnIntegration {
    public static final int CENTRAL_HOTBAR_SLOT = 4;
    private static final int MAIN_INVENTORY_START = 9;
    private static final int MAIN_INVENTORY_END = 35;
    private static Supplier<Item> item = () -> Items.AIR;
    private static Supplier<DataComponentType<ReturnBinding>> component;

    public static void install(
            Supplier<Item> itemSupplier,
            Supplier<DataComponentType<ReturnBinding>> componentSupplier
    ) {
        item = itemSupplier;
        component = componentSupplier;
    }

    public static Optional<ReturnBinding> binding(ItemStack stack) {
        if (component == null || stack.isEmpty() || stack.getItem() != item.get()) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.get(component.get()));
    }

    public static boolean give(ServerPlayer player, ReturnBinding binding) {
        return ensureInCentralSlot(player, binding);
    }

    public static boolean hasFreeInventorySlot(ServerPlayer player) {
        return canPrepareCentralSlot(player);
    }

    public static boolean canPrepareCentralSlot(ServerPlayer player) {
        return player.getInventory().getItem(CENTRAL_HOTBAR_SLOT).isEmpty() || findFreeMainSlot(player) >= 0;
    }

    public static boolean prepareCentralSlot(ServerPlayer player) {
        ItemStack center = player.getInventory().getItem(CENTRAL_HOTBAR_SLOT);
        if (center.isEmpty()) {
            return true;
        }
        int free = findFreeMainSlot(player);
        if (free < 0) {
            return false;
        }
        player.getInventory().setItem(free, center);
        player.getInventory().setItem(CENTRAL_HOTBAR_SLOT, ItemStack.EMPTY);
        broadcast(player);
        return true;
    }

    public static boolean ensureInInventory(ServerPlayer player, ReturnBinding required) {
        return ensureInCentralSlot(player, required);
    }

    public static boolean ensureInCentralSlot(ServerPlayer player, ReturnBinding required) {
        if (component == null || item.get() == Items.AIR) {
            return false;
        }
        Predicate<ReturnBinding> correct = required::equals;
        ItemStack extracted = extractFromInventory(player, correct);
        if (extracted.isEmpty()) {
            extracted = extractFromOpenContainer(player, correct);
        }
        if (extracted.isEmpty()) {
            extracted = extractFromCursor(player, correct);
        }
        if (extracted.isEmpty()) {
            extracted = swapFromCurios(player, correct, player.getInventory().getItem(CENTRAL_HOTBAR_SLOT));
        }
        ItemStack center = player.getInventory().getItem(CENTRAL_HOTBAR_SLOT);
        if (extracted.isEmpty()) {
            if (!center.isEmpty()) {
                if (binding(center).filter(candidate -> candidate.playerId().equals(player.getUUID())).isPresent()) {
                    center = ItemStack.EMPTY;
                    player.getInventory().setItem(CENTRAL_HOTBAR_SLOT, ItemStack.EMPTY);
                } else {
                    int free = findFreeMainSlot(player);
                    if (free < 0) {
                        KalFactions.LOGGER.error(
                                "Could not restore the Nether return item to central slot for {}",
                                player.getGameProfile().getName()
                        );
                        return false;
                    }
                    player.getInventory().setItem(free, center);
                    player.getInventory().setItem(CENTRAL_HOTBAR_SLOT, ItemStack.EMPTY);
                }
            }
            extracted = new ItemStack(item.get());
            extracted.set(component.get(), required);
        }
        player.getInventory().setItem(CENTRAL_HOTBAR_SLOT, extracted);
        Predicate<ReturnBinding> own = candidate -> candidate.playerId().equals(player.getUUID());
        removeDuplicates(player, own);
        broadcast(player);
        return true;
    }

    public static void removeForPlayer(ServerPlayer player) {
        remove(player, binding -> binding.playerId().equals(player.getUUID()));
    }

    public static void removeForSession(ServerPlayer player, java.util.UUID sessionId) {
        remove(player, binding -> binding.sessionId().equals(sessionId));
    }

    private static ItemStack extractFromInventory(ServerPlayer player, Predicate<ReturnBinding> predicate) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (binding(stack).filter(predicate).isEmpty()) {
                continue;
            }
            if (slot == CENTRAL_HOTBAR_SLOT) {
                return stack;
            }
            ItemStack center = player.getInventory().getItem(CENTRAL_HOTBAR_SLOT);
            player.getInventory().setItem(slot, center);
            player.getInventory().setItem(CENTRAL_HOTBAR_SLOT, ItemStack.EMPTY);
            return stack;
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack extractFromOpenContainer(ServerPlayer player, Predicate<ReturnBinding> predicate) {
        for (Slot slot : player.containerMenu.slots) {
            if (slot.container == player.getInventory()) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (binding(stack).filter(predicate).isPresent()) {
                slot.set(player.getInventory().getItem(CENTRAL_HOTBAR_SLOT));
                player.getInventory().setItem(CENTRAL_HOTBAR_SLOT, ItemStack.EMPTY);
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack extractFromCursor(ServerPlayer player, Predicate<ReturnBinding> predicate) {
        ItemStack carried = player.containerMenu.getCarried();
        if (binding(carried).filter(predicate).isEmpty()) {
            return ItemStack.EMPTY;
        }
        player.containerMenu.setCarried(player.getInventory().getItem(CENTRAL_HOTBAR_SLOT));
        player.getInventory().setItem(CENTRAL_HOTBAR_SLOT, ItemStack.EMPTY);
        return carried;
    }

    private static void removeDuplicates(ServerPlayer player, Predicate<ReturnBinding> predicate) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (slot == CENTRAL_HOTBAR_SLOT) {
                continue;
            }
            if (binding(player.getInventory().getItem(slot)).filter(predicate).isPresent()) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
        for (Slot slot : player.containerMenu.slots) {
            if (slot.container != player.getInventory() && binding(slot.getItem()).filter(predicate).isPresent()) {
                slot.set(ItemStack.EMPTY);
            }
        }
        if (binding(player.containerMenu.getCarried()).filter(predicate).isPresent()) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        removeCurios(player, predicate);
    }

    private static void remove(ServerPlayer player, Predicate<ReturnBinding> predicate) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (binding(player.getInventory().getItem(slot)).filter(predicate).isPresent()) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
        for (Slot slot : player.containerMenu.slots) {
            if (slot.container != player.getInventory() && binding(slot.getItem()).filter(predicate).isPresent()) {
                slot.set(ItemStack.EMPTY);
            }
        }
        if (binding(player.containerMenu.getCarried()).filter(predicate).isPresent()) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        removeCurios(player, predicate);
        broadcast(player);
    }

    private static int findFreeMainSlot(ServerPlayer player) {
        for (int slot = MAIN_INVENTORY_START; slot <= MAIN_INVENTORY_END; slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private static ItemStack swapFromCurios(
            ServerPlayer player,
            Predicate<ReturnBinding> predicate,
            ItemStack replacement
    ) {
        if (!ModList.get().isLoaded("curios")) {
            return ItemStack.EMPTY;
        }
        try {
            Object equipped = equippedCurios(player);
            if (equipped == null) {
                return ItemStack.EMPTY;
            }
            Method slotsMethod = equipped.getClass().getMethod("getSlots");
            Method getStack = equipped.getClass().getMethod("getStackInSlot", int.class);
            Method setStack = equipped.getClass().getMethod("setStackInSlot", int.class, ItemStack.class);
            int slots = (int) slotsMethod.invoke(equipped);
            for (int slot = 0; slot < slots; slot++) {
                ItemStack stack = (ItemStack) getStack.invoke(equipped, slot);
                if (binding(stack).filter(predicate).isPresent()) {
                    setStack.invoke(equipped, slot, replacement);
                    player.getInventory().setItem(CENTRAL_HOTBAR_SLOT, ItemStack.EMPTY);
                    return stack;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            KalFactions.LOGGER.warn("Could not restore a Nether return item from Curios", exception);
        }
        return ItemStack.EMPTY;
    }

    private static void removeCurios(ServerPlayer player, Predicate<ReturnBinding> predicate) {
        if (!ModList.get().isLoaded("curios")) {
            return;
        }
        try {
            Object equipped = equippedCurios(player);
            if (equipped == null) {
                return;
            }
            Method slotsMethod = equipped.getClass().getMethod("getSlots");
            Method getStack = equipped.getClass().getMethod("getStackInSlot", int.class);
            Method setStack = equipped.getClass().getMethod("setStackInSlot", int.class, ItemStack.class);
            int slots = (int) slotsMethod.invoke(equipped);
            for (int slot = 0; slot < slots; slot++) {
                ItemStack stack = (ItemStack) getStack.invoke(equipped, slot);
                if (binding(stack).filter(predicate).isPresent()) {
                    setStack.invoke(equipped, slot, ItemStack.EMPTY);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            KalFactions.LOGGER.warn("Could not remove a Nether return item from Curios", exception);
        }
    }

    private static Object equippedCurios(ServerPlayer player) throws ReflectiveOperationException {
        Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
        Method getter = api.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
        Object optional = getter.invoke(null, player);
        if (!(optional instanceof Optional<?> value) || value.isEmpty()) {
            return null;
        }
        return value.get().getClass().getMethod("getEquippedCurios").invoke(value.get());
    }

    private static void broadcast(ServerPlayer player) {
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }

    private NetherReturnIntegration() {
    }
}
