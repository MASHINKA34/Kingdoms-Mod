package com.geydev.kalfactions.bonus;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.FactionBonus;
import com.geydev.kalfactions.protection.FactionAccess;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class EnchanterBonusHandler {
    private record AnvilOverride(ItemStack output, int cost, int materialCost, boolean cancelVanilla) {
        static AnvilOverride pass() {
            return new AnvilOverride(ItemStack.EMPTY, 0, 0, false);
        }

        static AnvilOverride empty() {
            return new AnvilOverride(ItemStack.EMPTY, 0, 0, true);
        }
    }

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && FactionAccess.hasAnyBonus(player, FactionBonus.ENCHANTERS)) {
            ItemStack output = createOutput(event);
            if (!output.isEmpty()) {
                event.setOutput(output);
            }
            return;
        }

        AnvilOverride override = createRegularPlayerOutput(event);
        if (override.cancelVanilla()) {
            event.setCanceled(true);
        } else if (!override.output().isEmpty()) {
            event.setOutput(override.output());
            event.setCost(override.cost());
            event.setMaterialCost(override.materialCost());
        }
    }

    private static ItemStack createOutput(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || !EnchantmentHelper.canStoreEnchantments(left)) {
            return ItemStack.EMPTY;
        }

        ItemStack result = left.copy();
        long baseCost = priorWorkCost(left) + priorWorkCost(right);
        long operationCost = 0L;
        int materialCost = 0;

        if (!right.isEmpty()) {
            if (result.isDamageableItem() && result.getItem().isValidRepairItem(left, right)) {
                int repaired = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
                if (repaired <= 0) {
                    return ItemStack.EMPTY;
                }
                while (repaired > 0 && materialCost < right.getCount()) {
                    result.setDamageValue(result.getDamageValue() - repaired);
                    operationCost++;
                    materialCost++;
                    repaired = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
                }
            } else {
                boolean enchantedBook = right.has(DataComponents.STORED_ENCHANTMENTS);
                if (!enchantedBook && (!result.is(right.getItem()) || !result.isDamageableItem())) {
                    return ItemStack.EMPTY;
                }
                if (result.isDamageableItem() && !enchantedBook) {
                    int leftRemaining = left.getMaxDamage() - left.getDamageValue();
                    int rightRemaining = right.getMaxDamage() - right.getDamageValue();
                    int combinedRemaining = leftRemaining + rightRemaining + result.getMaxDamage() * 12 / 100;
                    int combinedDamage = Math.max(0, result.getMaxDamage() - combinedRemaining);
                    if (combinedDamage < result.getDamageValue()) {
                        result.setDamageValue(combinedDamage);
                        operationCost += 2L;
                    }
                }
                ItemEnchantments.Mutable mutable =
                        new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(result));
                boolean changedEnchantments = false;
                ItemEnchantments rightEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(right);
                for (Object2IntMap.Entry<Holder<Enchantment>> entry : rightEnchantments.entrySet()) {
                    Holder<Enchantment> holder = entry.getKey();
                    boolean supported = left.supportsEnchantment(holder) || event.getPlayer().getAbilities().instabuild;
                    if (!supported) {
                        continue;
                    }
                    int currentLevel = mutable.getLevel(holder);
                    int rightLevel = entry.getIntValue();
                    int targetLevel = currentLevel == rightLevel ? rightLevel + 1 : Math.max(currentLevel, rightLevel);
                    mutable.set(holder, targetLevel);
                    int anvilCost = holder.value().getAnvilCost();
                    if (enchantedBook) {
                        anvilCost = Math.max(1, anvilCost / 2);
                    }
                    operationCost = Mth.clamp(
                            operationCost + (long) anvilCost * enchantLevelCost(targetLevel),
                            0L,
                            Integer.MAX_VALUE
                    );
                    changedEnchantments = true;
                }
                if (!rightEnchantments.isEmpty() && !changedEnchantments) {
                    return ItemStack.EMPTY;
                }
                EnchantmentHelper.setEnchantments(result, mutable.toImmutable());
            }
        }

        String name = event.getName();
        boolean renamed = false;
        if (name != null && !StringUtil.isBlank(name)) {
            if (!name.equals(left.getHoverName().getString())) {
                result.set(DataComponents.CUSTOM_NAME, Component.literal(name));
                operationCost++;
                renamed = true;
            }
        } else if (left.has(DataComponents.CUSTOM_NAME)) {
            result.remove(DataComponents.CUSTOM_NAME);
            operationCost++;
            renamed = true;
        }

        if (operationCost <= 0L) {
            return ItemStack.EMPTY;
        }

        if (!renamed || operationCost > 1L) {
            int repairCost = Math.max(
                    left.getOrDefault(DataComponents.REPAIR_COST, 0),
                    right.getOrDefault(DataComponents.REPAIR_COST, 0)
            );
            result.set(DataComponents.REPAIR_COST, AnvilMenu.calculateIncreasedRepairCost(repairCost));
        }

        event.setCost(Mth.clamp(baseCost + operationCost, 1L, ModConfigSpec.ENCHANTER_ANVIL_MAX_COST.getAsInt()));
        event.setMaterialCost(materialCost);
        return result;
    }

    private static AnvilOverride createRegularPlayerOutput(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty() || !right.has(DataComponents.STORED_ENCHANTMENTS)
                || !EnchantmentHelper.canStoreEnchantments(left)) {
            return AnvilOverride.pass();
        }

        ItemEnchantments leftEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(left);
        ItemEnchantments rightEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(right);
        if (rightEnchantments.isEmpty() || !hasOverleveled(leftEnchantments) && !hasOverleveled(rightEnchantments)) {
            return AnvilOverride.pass();
        }

        boolean storedBook = left.has(DataComponents.STORED_ENCHANTMENTS);
        if (!storedBook && !left.isBookEnchantable(right)) {
            return AnvilOverride.pass();
        }

        ItemStack result = left.copy();
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(leftEnchantments);
        long operationCost = 0L;
        boolean changed = false;

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : rightEnchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (!supportsRegularApplication(event, left, storedBook, holder) || !compatibleWithExisting(mutable, holder)) {
                continue;
            }

            int currentLevel = mutable.getLevel(holder);
            int rightLevel = entry.getIntValue();
            int targetLevel = regularTargetLevel(storedBook, currentLevel, rightLevel, holder.value().getMaxLevel());
            if (targetLevel != currentLevel) {
                mutable.set(holder, targetLevel);
                operationCost = Mth.clamp(
                        operationCost + (long) Math.max(1, holder.value().getAnvilCost() / 2) * enchantLevelCost(targetLevel),
                        0L,
                        Integer.MAX_VALUE
                );
                changed = true;
            }
        }

        if (storedBook) {
            changed |= capStoredBookEnchantments(mutable);
        }
        if (!changed) {
            return AnvilOverride.empty();
        }

        EnchantmentHelper.setEnchantments(result, mutable.toImmutable());
        String name = event.getName();
        if (name != null && !StringUtil.isBlank(name) && !name.equals(left.getHoverName().getString())) {
            result.set(DataComponents.CUSTOM_NAME, Component.literal(name));
            operationCost++;
        } else if ((name == null || StringUtil.isBlank(name)) && left.has(DataComponents.CUSTOM_NAME)) {
            result.remove(DataComponents.CUSTOM_NAME);
            operationCost++;
        }

        int repairCost = Math.max(
                left.getOrDefault(DataComponents.REPAIR_COST, 0),
                right.getOrDefault(DataComponents.REPAIR_COST, 0)
        );
        result.set(DataComponents.REPAIR_COST, AnvilMenu.calculateIncreasedRepairCost(repairCost));
        int cost = (int) Mth.clamp(
                priorWorkCost(left) + priorWorkCost(right) + operationCost,
                1L,
                ModConfigSpec.ENCHANTER_ANVIL_MAX_COST.getAsInt()
        );
        return new AnvilOverride(result, cost, 1, false);
    }

    private static int priorWorkCost(ItemStack stack) {
        int repairCost = Math.max(0, stack.getOrDefault(DataComponents.REPAIR_COST, 0));
        if (repairCost <= 0) {
            return 0;
        }
        int cost = Integer.SIZE - Integer.numberOfLeadingZeros(repairCost);
        return Math.min(cost, ModConfigSpec.ENCHANTER_PRIOR_WORK_MAX_COST.getAsInt());
    }

    private static int enchantLevelCost(int level) {
        return Math.min(Math.max(1, level), ModConfigSpec.ENCHANTER_LEVEL_COST_CAP.getAsInt());
    }

    private static boolean hasOverleveled(ItemEnchantments enchantments) {
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            if (entry.getIntValue() > entry.getKey().value().getMaxLevel()) {
                return true;
            }
        }
        return false;
    }

    private static boolean supportsRegularApplication(
            AnvilUpdateEvent event,
            ItemStack left,
            boolean storedBook,
            Holder<Enchantment> holder
    ) {
        return storedBook || left.supportsEnchantment(holder) || event.getPlayer().getAbilities().instabuild;
    }

    private static boolean compatibleWithExisting(ItemEnchantments.Mutable mutable, Holder<Enchantment> holder) {
        for (Holder<Enchantment> current : mutable.keySet()) {
            if (!current.equals(holder) && !Enchantment.areCompatible(holder, current)) {
                return false;
            }
        }
        return true;
    }

    private static int regularTargetLevel(boolean storedBook, int currentLevel, int rightLevel, int maxLevel) {
        int combinedLevel = currentLevel == rightLevel ? rightLevel + 1 : Math.max(currentLevel, rightLevel);
        if (storedBook) {
            return Math.min(combinedLevel, maxLevel);
        }
        return currentLevel > maxLevel || rightLevel > maxLevel
                ? Math.max(currentLevel, rightLevel)
                : Math.min(combinedLevel, maxLevel);
    }

    private static boolean capStoredBookEnchantments(ItemEnchantments.Mutable mutable) {
        boolean changed = false;
        for (Holder<Enchantment> holder : mutable.keySet()) {
            int level = mutable.getLevel(holder);
            int maxLevel = holder.value().getMaxLevel();
            if (level > maxLevel) {
                mutable.set(holder, maxLevel);
                changed = true;
            }
        }
        return changed;
    }

    private EnchanterBonusHandler() {
    }
}
