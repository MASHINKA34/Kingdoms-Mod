package com.geydev.kalfactions.integration.curios;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.FactionBonus;
import com.geydev.kalfactions.protection.FactionAccess;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
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
    private static final String HOOKAH_MOD_ID = "hookahmod";
    /**
     * The hookahmod block-item ids, one per tier. We match these exact paths rather than
     * any {@code hookah*} prefix so accessory items (tobacco, charcoal, mouthpiece, hoses…)
     * never count as a worn hookah. Resolved through the item registry, so there is no
     * compile-time dependency on hookahmod.
     */
    private static final Set<String> HOOKAH_ITEM_PATHS = Set.of(
            "hookah",
            "hookah_leather",
            "hookah_gold",
            "hookah_iron",
            "hookah_diamond",
            "hookah_netherite"
    );
    public static final ResourceLocation HOOKAH_ARMOR_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "hookah_armor_bonus");
    public static final ResourceLocation HOOKAH_SPEED_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "hookah_speed_bonus");
    private static volatile Predicate<ServerPlayer> bonusChecker =
            player -> FactionAccess.hasAnyBonus(player, FactionBonus.HOOKAH);
    private static volatile Method inventoryGetter;
    private static volatile boolean apiResolved;
    private static volatile boolean resolutionFailureLogged;

    public static boolean isCuriosLoaded() {
        return ModList.get().isLoaded("curios");
    }

    public static boolean isHookahModLoaded() {
        return ModList.get().isLoaded(HOOKAH_MOD_ID);
    }

    /**
     * True when {@code stack} is one of the hookahmod hookah tiers. Detection is by registry
     * id so the bonus works whether the hookah sits in a Curios slot or the main inventory.
     */
    public static boolean isHookahStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null
                && id.getNamespace().equals(HOOKAH_MOD_ID)
                && HOOKAH_ITEM_PATHS.contains(id.getPath());
    }

    public static boolean isHookahEquipped(ServerPlayer player) {
        if (!isHookahModLoaded()) {
            return false;
        }
        return isHookahStack(player.getItemBySlot(EquipmentSlot.CHEST))
                || isEquipped(player, CuriosHookahIntegration::isHookahStack);
    }

    public static boolean hasActiveHookahBonus(ServerPlayer player) {
        return bonusChecker.test(player) && isHookahEquipped(player);
    }

    public static float combatMultiplier(ServerPlayer player) {
        return hasActiveHookahBonus(player)
                ? ModConfigSpec.HOOKAH_DAMAGE_MULTIPLIER.get().floatValue()
                : 1.0F;
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

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (!hasActiveHookahBonus(player)) {
            removeModifier(armor, HOOKAH_ARMOR_MODIFIER_ID);
            removeModifier(speed, HOOKAH_SPEED_MODIFIER_ID);
            return;
        }

        double armorAmount = Math.max(0.0D, ModConfigSpec.HOOKAH_ARMOR_BONUS.getAsDouble());
        if (armor != null && armorAmount > 0.0D) {
            armor.addOrUpdateTransientModifier(new AttributeModifier(
                    HOOKAH_ARMOR_MODIFIER_ID,
                    armorAmount,
                    AttributeModifier.Operation.ADD_VALUE
            ));
        } else {
            removeModifier(armor, HOOKAH_ARMOR_MODIFIER_ID);
        }

        double speedAmount = Math.max(0.0D, ModConfigSpec.HOOKAH_SPEED_BONUS.getAsDouble());
        if (speed != null && speedAmount > 0.0D) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(
                    HOOKAH_SPEED_MODIFIER_ID,
                    speedAmount,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
        } else {
            removeModifier(speed, HOOKAH_SPEED_MODIFIER_ID);
        }
    }

    private static void removeModifier(AttributeInstance attribute, ResourceLocation id) {
        if (attribute != null) {
            attribute.removeModifier(id);
        }
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
