package com.geydev.kalfactions.integration.curios;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.faction.FactionBonus;
import com.geydev.kalfactions.protection.FactionAccess;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Optional Curios bridge with no Curios types in fields or method signatures.
 */
@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class CuriosHookahIntegration {
    private static final String CURIOS_API = "top.theillusivec4.curios.api.CuriosApi";
    public static final ResourceLocation HOOKAH_ARMOR_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "hookah_armor_bonus");
    private static volatile Predicate<ServerPlayer> bonusChecker =
            player -> FactionAccess.hasAnyBonus(player, FactionBonus.HOOKAH);
    private static volatile double armorBonus = 2.0D;
    private static volatile Method inventoryGetter;
    private static volatile boolean apiResolved;
    private static volatile boolean resolutionFailureLogged;

    public static boolean isCuriosLoaded() {
        return ModList.get().isLoaded("curios");
    }

    public static boolean isHookahEquipped(ServerPlayer player) {
        return player.getInventory().contains(stack -> stack.is(Items.BREWING_STAND))
                || isEquipped(player, stack -> stack.is(Items.BREWING_STAND));
    }

    public static boolean hasActiveHookahBonus(ServerPlayer player) {
        return bonusChecker.test(player) && isHookahEquipped(player);
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

    public static void installBonusChecker(Predicate<ServerPlayer> checker) {
        bonusChecker = Objects.requireNonNull(checker, "checker");
    }

    public static void setArmorBonus(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0D) {
            throw new IllegalArgumentException("Hookah armor bonus must be finite and non-negative");
        }
        armorBonus = amount;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (armor == null) {
            return;
        }
        if (!hasActiveHookahBonus(player) || armorBonus == 0.0D) {
            armor.removeModifier(HOOKAH_ARMOR_MODIFIER_ID);
            return;
        }

        AttributeModifier modifier = new AttributeModifier(
                HOOKAH_ARMOR_MODIFIER_ID,
                armorBonus,
                AttributeModifier.Operation.ADD_VALUE
        );
        armor.addOrUpdateTransientModifier(modifier);
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
