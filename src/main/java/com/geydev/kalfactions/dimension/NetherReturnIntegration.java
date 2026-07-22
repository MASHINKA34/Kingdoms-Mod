package com.geydev.kalfactions.dimension;

import com.geydev.kalfactions.KalFactions;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

public final class NetherReturnIntegration {
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
        if (component == null || item.get() == Items.AIR) {
            return false;
        }
        remove(player, candidate -> candidate.playerId().equals(player.getUUID()));
        ItemStack stack = new ItemStack(item.get());
        stack.set(component.get(), binding);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    public static void removeForPlayer(ServerPlayer player) {
        remove(player, binding -> binding.playerId().equals(player.getUUID()));
    }

    public static void removeForSession(ServerPlayer player, java.util.UUID sessionId) {
        remove(player, binding -> binding.sessionId().equals(sessionId));
    }

    private static void remove(ServerPlayer player, java.util.function.Predicate<ReturnBinding> predicate) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (binding(stack).filter(predicate).isPresent()) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
        removeCurios(player, predicate);
        player.inventoryMenu.broadcastChanges();
    }

    private static void removeCurios(ServerPlayer player, java.util.function.Predicate<ReturnBinding> predicate) {
        if (!ModList.get().isLoaded("curios")) {
            return;
        }
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Method getter = api.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
            Object optional = getter.invoke(null, player);
            if (!(optional instanceof Optional<?> value) || value.isEmpty()) {
                return;
            }
            Object handler = value.get();
            Object equipped = handler.getClass().getMethod("getEquippedCurios").invoke(handler);
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

    private NetherReturnIntegration() {
    }
}
