package com.geydev.kalfactions.integration.curios;

import com.geydev.kalfactions.KalFactions;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Optional Curios bridge with no Curios types in fields or method signatures.
 */
public final class CuriosHookahIntegration {
    private static final String CURIOS_API = "top.theillusivec4.curios.api.CuriosApi";
    private static volatile Predicate<ItemStack> hookahMatcher = CuriosHookahIntegration::defaultHookahMatch;
    private static volatile Method inventoryGetter;
    private static volatile boolean apiResolved;
    private static volatile boolean resolutionFailureLogged;

    public static boolean isCuriosLoaded() {
        return ModList.get().isLoaded("curios");
    }

    public static boolean isHookahEquipped(ServerPlayer player) {
        return isEquipped(player, hookahMatcher);
    }

    public static boolean isEquipped(ServerPlayer player, Predicate<ItemStack> matcher) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(matcher, "matcher");
        if (!isCuriosLoaded()) {
            return false;
        }

        try {
            Method getter = resolveInventoryGetter();
            if (getter == null) {
                return false;
            }
            Optional<Object> inventory = unwrap(getter.invoke(null, player));
            if (inventory.isEmpty()) {
                return false;
            }

            for (Method method : inventory.get().getClass().getMethods()) {
                if (!method.getName().equals("findFirstCurio")
                        || method.getParameterCount() != 1
                        || !Predicate.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    continue;
                }
                return unwrap(method.invoke(inventory.get(), matcher)).isPresent();
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logResolutionFailure(exception);
        }
        return false;
    }

    public static void installHookahMatcher(Predicate<ItemStack> matcher) {
        hookahMatcher = Objects.requireNonNull(matcher, "matcher");
    }

    private static Method resolveInventoryGetter() {
        if (apiResolved) {
            return inventoryGetter;
        }
        synchronized (CuriosHookahIntegration.class) {
            if (apiResolved) {
                return inventoryGetter;
            }
            try {
                Class<?> apiClass = Class.forName(
                        CURIOS_API,
                        false,
                        CuriosHookahIntegration.class.getClassLoader()
                );
                for (Method method : apiClass.getMethods()) {
                    if (method.getName().equals("getCuriosInventory")
                            && Modifier.isStatic(method.getModifiers())
                            && method.getParameterCount() == 1
                            && method.getParameterTypes()[0].isAssignableFrom(ServerPlayer.class)) {
                        inventoryGetter = method;
                        break;
                    }
                }
            } catch (ClassNotFoundException exception) {
                logResolutionFailure(exception);
            }
            apiResolved = true;
            return inventoryGetter;
        }
    }

    private static boolean defaultHookahMatch(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getPath().contains("hookah") || id.getNamespace().contains("hookah");
    }

    private static Optional<Object> unwrap(Object value) {
        Object current = value;
        while (current instanceof Optional<?> optional) {
            if (optional.isEmpty()) {
                return Optional.empty();
            }
            current = optional.get();
        }
        return Optional.ofNullable(current);
    }

    private static void logResolutionFailure(Exception exception) {
        if (resolutionFailureLogged) {
            return;
        }
        resolutionFailureLogged = true;
        KalFactions.LOGGER.warn(
                "Curios is loaded, but its inventory API could not be accessed. Hookah integration is disabled.",
                exception
        );
    }

    private CuriosHookahIntegration() {
    }
}
