package com.geydev.kalfactions.bonus;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionBonus;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.protection.FactionAccess;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class BonusHandler {
    private static final Map<UUID, EnumMap<InteractionHand, PendingDurability>> PENDING_DURABILITY =
            new HashMap<>();
    private static volatile DurabilityPolicy durabilityPolicy = BonusHandler::defaultDurabilityChance;

    @SubscribeEvent
    public static void onBonusDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player) || event.getDrops().isEmpty()) {
            return;
        }
        boolean isOre = event.getState().is(Tags.Blocks.ORES);
        Faction faction = FactionManager.get(player.serverLevel())
                .getFactionForMember(player.getUUID())
                .orElse(null);
        if (isOre && faction != null && faction.researchBonusCount("AUTO_SMELT") > 0) {
            ServerLevel level = player.serverLevel();
            for (ItemEntity drop : event.getDrops()) {
                ItemStack smelted = smelt(drop.getItem(), level);
                if (!smelted.isEmpty()) {
                    drop.setItem(smelted);
                }
            }
        }
        double chance;
        if (isOre
                && (FactionAccess.hasAnyBonus(player, FactionBonus.MINERS)
                    || (faction != null && faction.researchBonusCount("ORE_DROP") > 0))) {
            chance = ModConfigSpec.ORE_BONUS_CHANCE.get();
        } else if (event.getState().is(BlockTags.CROPS)
                && FactionAccess.hasAnyBonus(player, FactionBonus.FARMERS)) {
            chance = ModConfigSpec.HARVEST_BONUS_CHANCE.get();
        } else {
            return;
        }
        if (player.getRandom().nextDouble() >= chance) {
            return;
        }

        List<ItemEntity> bonusDrops = new ArrayList<>(event.getDrops().size());
        for (ItemEntity drop : event.getDrops()) {
            ItemStack bonusStack = drop.getItem().copy();
            if (bonusStack.isEmpty()) {
                continue;
            }
            ItemEntity bonus = new ItemEntity(
                    event.getLevel(),
                    drop.getX(),
                    drop.getY(),
                    drop.getZ(),
                    bonusStack,
                    drop.getDeltaMovement().x,
                    drop.getDeltaMovement().y,
                    drop.getDeltaMovement().z
            );
            bonus.setDefaultPickUpDelay();
            bonusDrops.add(bonus);
        }
        event.getDrops().addAll(bonusDrops);
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        double multiplier = FactionManager.get(player.serverLevel())
                .getFactionForMember(player.getUUID())
                .map(com.geydev.kalfactions.faction.Faction::miningSpeedMultiplier)
                .orElse(1.0D);
        if (multiplier != 1.0D) {
            event.setNewSpeed((float) (event.getNewSpeed() * multiplier));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforeBlockBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getPlayer() instanceof ServerPlayer player) {
            remember(player, InteractionHand.MAIN_HAND);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforeAttack(AttackEntityEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            remember(player, InteractionHand.MAIN_HAND);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforeItemUseOnBlock(UseItemOnBlockEvent event) {
        if (!event.isCanceled()
                && event.getUsePhase() == UseItemOnBlockEvent.UsePhase.ITEM_BEFORE_BLOCK
                && event.getPlayer() instanceof ServerPlayer player) {
            remember(player, event.getHand());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforeEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            remember(player, event.getHand());
        }
    }

    @SubscribeEvent
    public static void afterPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        EnumMap<InteractionHand, PendingDurability> pendingByHand =
                PENDING_DURABILITY.get(player.getUUID());
        if (pendingByHand == null) {
            return;
        }

        long gameTime = player.serverLevel().getGameTime();
        pendingByHand.entrySet().removeIf(entry ->
                restoreIfDamaged(player, entry.getKey(), entry.getValue(), gameTime));
        if (pendingByHand.isEmpty()) {
            PENDING_DURABILITY.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_DURABILITY.remove(event.getEntity().getUUID());
    }

    private static ItemStack smelt(ItemStack input, ServerLevel level) {
        if (input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Optional<RecipeHolder<SmeltingRecipe>> recipe = level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), level);
        if (recipe.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = recipe.get().value().getResultItem(level.registryAccess());
        if (result.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack output = result.copy();
        output.setCount(result.getCount() * input.getCount());
        return output;
    }

    public static void installDurabilityPolicy(DurabilityPolicy policy) {
        durabilityPolicy = Objects.requireNonNull(policy, "policy");
    }

    private static void remember(ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.isDamageableItem() || held.isEmpty()) {
            return;
        }

        long gameTime = player.serverLevel().getGameTime();
        EnumMap<InteractionHand, PendingDurability> pendingByHand =
                PENDING_DURABILITY.computeIfAbsent(
                        player.getUUID(),
                        ignored -> new EnumMap<>(InteractionHand.class)
                );
        PendingDurability existing = pendingByHand.get(hand);
        if (existing != null
                && existing.createdGameTime() == gameTime
                && sameIgnoringDamage(existing.before(), held)) {
            return;
        }

        double chance = Math.clamp(durabilityPolicy.preservationChance(player, held), 0.0D, 1.0D);
        if (chance <= 0.0D || player.getRandom().nextDouble() >= chance) {
            return;
        }
        pendingByHand.put(hand, new PendingDurability(held.copy(), held.getDamageValue(), gameTime));
    }

    private static boolean restoreIfDamaged(
            ServerPlayer player,
            InteractionHand hand,
            PendingDurability pending,
            long gameTime
    ) {
        ItemStack current = player.getItemInHand(hand);
        if (current.isEmpty()) {
            player.setItemInHand(hand, pending.before().copy());
            player.inventoryMenu.broadcastChanges();
            return true;
        }
        if (!sameIgnoringDamage(pending.before(), current)) {
            return gameTime > pending.createdGameTime() + 2L;
        }
        if (current.getDamageValue() > pending.damageBefore()) {
            current.setDamageValue(pending.damageBefore());
            player.inventoryMenu.broadcastChanges();
            return true;
        }
        return gameTime > pending.createdGameTime() + 2L;
    }

    private static boolean sameIgnoringDamage(ItemStack first, ItemStack second) {
        if (!first.is(second.getItem())) {
            return false;
        }
        ItemStack firstCopy = first.copy();
        ItemStack secondCopy = second.copy();
        firstCopy.remove(DataComponents.DAMAGE);
        secondCopy.remove(DataComponents.DAMAGE);
        return ItemStack.isSameItemSameComponents(firstCopy, secondCopy);
    }

    private static double defaultDurabilityChance(ServerPlayer player, ItemStack stack) {
        return FactionAccess.hasAnyBonus(player, FactionBonus.CRAFTERS)
                ? ModConfigSpec.CRAFT_BONUS_CHANCE.get()
                : 0.0D;
    }

    private record PendingDurability(ItemStack before, int damageBefore, long createdGameTime) {
    }

    @FunctionalInterface
    public interface DurabilityPolicy {
        double preservationChance(ServerPlayer player, ItemStack stack);
    }

    private BonusHandler() {
    }
}
