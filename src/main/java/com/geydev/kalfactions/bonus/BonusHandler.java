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
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
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
    private static final ResourceLocation NOMAD_MOUNT_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "nomad_mount_speed");
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

    @SubscribeEvent
    public static void onBabySpawn(BabyEntitySpawnEvent event) {
        if (event.isCanceled()
                || !(event.getCausedByPlayer() instanceof ServerPlayer player)
                || !(event.getParentA() instanceof AgeableMob parentA)
                || !(event.getParentB() instanceof AgeableMob parentB)
                || event.getChild() == null
                || !FactionAccess.hasAnyBonus(player, FactionBonus.FARMERS)
                || player.getRandom().nextDouble() >= ModConfigSpec.FARMER_BREEDING_TWIN_CHANCE.getAsDouble()) {
            return;
        }
        AgeableMob extraChild = parentA.getBreedOffspring(player.serverLevel(), parentB);
        if (extraChild == null) {
            return;
        }
        AgeableMob child = event.getChild();
        extraChild.setBaby(true);
        extraChild.moveTo(child.getX(), child.getY(), child.getZ(), child.getYRot(), child.getXRot());
        player.serverLevel().addFreshEntity(extraChild);
    }

    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        Entity entity = event.getEntityMounting();
        Entity mount = event.getEntityBeingMounted();
        if (event.isDismounting() && entity instanceof ServerPlayer && mount instanceof LivingEntity living) {
            removeNomadMountSpeed(living);
            return;
        }
        if (event.isMounting() && entity instanceof ServerPlayer player) {
            updateNomadMountSpeed(player);
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack crafted = event.getCrafting();
        if (crafted.isEmpty()) {
            return;
        }
        int levels = FactionManager.get(player.serverLevel())
                .getFactionForMember(player.getUUID())
                .map(faction -> faction.researchBonusCount("CRAFT_EXTRA"))
                .orElse(0);
        if (levels <= 0) {
            return;
        }
        double chance = Math.min(0.5D, 0.1D * levels);
        if (player.serverLevel().getRandom().nextDouble() >= chance) {
            return;
        }
        ItemStack bonus = crafted.copy();
        bonus.setCount(1);
        if (!player.getInventory().add(bonus)) {
            player.drop(bonus, false);
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
        updateNomadMountSpeed(player);
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
        if (event.getEntity() instanceof ServerPlayer player && player.getVehicle() instanceof LivingEntity living) {
            removeNomadMountSpeed(living);
        }
        PENDING_DURABILITY.remove(event.getEntity().getUUID());
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
        return 0.0D;
    }

    private static void updateNomadMountSpeed(ServerPlayer player) {
        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof LivingEntity living)) {
            return;
        }
        AttributeInstance speed = living.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        if (!FactionAccess.hasAnyBonus(player, FactionBonus.NOMADS)) {
            speed.removeModifier(NOMAD_MOUNT_SPEED_ID);
            return;
        }
        speed.addOrUpdateTransientModifier(new AttributeModifier(
                NOMAD_MOUNT_SPEED_ID,
                ModConfigSpec.NOMAD_MOUNT_SPEED_BONUS.getAsDouble(),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        ));
    }

    private static void removeNomadMountSpeed(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(NOMAD_MOUNT_SPEED_ID);
        }
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
