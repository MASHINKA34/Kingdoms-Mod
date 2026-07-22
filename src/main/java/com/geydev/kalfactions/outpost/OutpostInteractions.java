package com.geydev.kalfactions.outpost;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.FactionRole;
import com.geydev.kalfactions.integration.IntegrationManager;
import com.geydev.kalfactions.net.ClaimSyncManager;
import com.geydev.kalfactions.net.FactionServerHooks;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.registry.ModItems;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class OutpostInteractions {
    @SubscribeEvent
    public static void onCharterUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (!held.is(ModItems.OUTPOST_CHARTER.get())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        FactionManager manager = FactionManager.get(level);
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.outpost.not_in_faction"), false);
            return;
        }
        BlockPos corePos = event.getPos().relative(event.getFace());
        if (!level.getBlockState(corePos).canBeReplaced()) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.outpost.blocked"), false);
            return;
        }
        ChunkPos baseChunk = new ChunkPos(corePos);
        FactionManager.OperationResult result =
                manager.claimOutpost(faction.id(), level.dimension(), corePos, baseChunk);
        if (!result.successful()) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.outpost.overlap"), false);
            return;
        }
        level.setBlockAndUpdate(corePos, ModBlocks.OUTPOST_CORE.get().defaultBlockState());
        if (!player.isCreative()) {
            held.shrink(1);
        }
        IntegrationManager.refreshFromServer(player.getServer());
        ClaimSyncManager.resync(player);
        FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.outpost.founded"), true);
    }

    @SubscribeEvent
    public static void onDrillPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState placed = event.getPlacedBlock();
        if (!placed.is(ModBlocks.DRILL.get())) {
            return;
        }
        ChunkPos chunk = new ChunkPos(event.getPos());
        ClaimKey key = ClaimKey.of(level, chunk);
        FactionManager manager = FactionManager.get(level);
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        boolean ownClaim = faction != null
                && manager.getFactionIdAt(key).map(id -> id.equals(faction.id())).orElse(false);
        if (!ownClaim) {
            event.setCanceled(true);
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.drill.not_outpost"), false);
            return;
        }
        com.geydev.kalfactions.outpost.cluster.ResourceClusterManager clusters =
                com.geydev.kalfactions.outpost.cluster.ResourceClusterManager.get(level);
        if (clusters.clusterAt(chunk).isEmpty() && clusters.oreDepositAt(chunk).isEmpty()) {
            event.setCanceled(true);
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.drill.no_cluster"), false);
            return;
        }
        if (!clusters.bindDrill(chunk, event.getPos())) {
            event.setCanceled(true);
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.drill.cluster_taken"), false);
        }
    }

    public static InteractionResult onCoreRightClick(ServerPlayer player, BlockPos corePos) {
        ServerLevel level = player.serverLevel();
        RogueOutpostManager rogue = RogueOutpostManager.get(level);
        RogueOutpostManager.RogueOutpost outpost = rogue.byCore(level, corePos).orElse(null);
        if (outpost == null) {
            return handleOwnedCore(player, level, corePos);
        }
        if (outpost.garrisonRemaining() > 0) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.outpost.garrison_alive"), false);
            return InteractionResult.SUCCESS;
        }
        FactionManager manager = FactionManager.get(level);
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.outpost.not_in_faction"), false);
            return InteractionResult.SUCCESS;
        }
        FactionManager.OperationResult result = manager.attachOutpost(faction.id(), corePos, outpost.chunks());
        if (!result.successful()) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.outpost.overlap"), false);
            return InteractionResult.SUCCESS;
        }
        rogue.remove(outpost.id());
        IntegrationManager.refreshFromServer(player.getServer());
        ClaimSyncManager.resync(player);
        FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.outpost.recaptured"), true);
        UUID previousOwner = outpost.previousOwnerId();
        if (previousOwner != null && !previousOwner.equals(player.getUUID())) {
            ServerPlayer previous = player.getServer().getPlayerList().getPlayer(previousOwner);
            if (previous != null) {
                FactionServerHooks.sendNotice(
                        previous,
                        Component.translatable("kingdoms.outpost.taken_by", faction.name()),
                        false
                );
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult handleOwnedCore(ServerPlayer player, ServerLevel level, BlockPos corePos) {
        FactionManager manager = FactionManager.get(level);
        ClaimKey key = ClaimKey.of(level, new ChunkPos(corePos));
        Faction owner = manager.getFactionAt(key).orElse(null);
        if (owner == null || owner.outpostByCore(level.dimension(), corePos).isEmpty()) {
            return InteractionResult.PASS;
        }
        Faction playerFaction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (playerFaction == null || !playerFaction.id().equals(owner.id())) {
            FactionServerHooks.sendNotice(
                    player,
                    Component.translatable("kingdoms.outpost.owned_by", owner.name()),
                    true
            );
            return InteractionResult.SUCCESS;
        }
        boolean canManage = owner.roleOf(player.getUUID()).map(FactionRole::canManageClaims).orElse(false);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                player,
                new OutpostPayloads.S2COutpostState(corePos, owner.name(), canManage)
        );
        return InteractionResult.SUCCESS;
    }

    public static void dismantleFromUi(ServerPlayer player, BlockPos corePos) {
        ServerLevel level = player.serverLevel();
        if (!player.isAlive() || player.isSpectator() || !level.isLoaded(corePos)
                || player.distanceToSqr(corePos.getX() + 0.5D, corePos.getY() + 0.5D, corePos.getZ() + 0.5D) > 64.0D) {
            return;
        }
        FactionManager manager = FactionManager.get(level);
        ClaimKey key = ClaimKey.of(level, new ChunkPos(corePos));
        Faction owner = manager.getFactionAt(key).orElse(null);
        if (owner == null) {
            return;
        }
        Faction.Outpost outpost = owner.outpostByCore(level.dimension(), corePos).orElse(null);
        Faction playerFaction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (outpost == null || playerFaction == null || !playerFaction.id().equals(owner.id())
                || !owner.roleOf(player.getUUID()).map(FactionRole::canManageClaims).orElse(false)) {
            FactionServerHooks.sendNotice(
                    player,
                    Component.translatable("kingdoms.error.role_cannot_change_claims"),
                    false
            );
            return;
        }
        manager.detachOutpost(owner.id(), outpost.id());
        level.removeBlock(corePos, false);
        ItemStack charter = new ItemStack(ModItems.OUTPOST_CHARTER.get());
        if (!player.getInventory().add(charter)) {
            player.drop(charter, false);
        }
        IntegrationManager.refreshFromServer(player.getServer());
        ClaimSyncManager.resync(player);
        FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.outpost.dismantled"), true);
    }

    private OutpostInteractions() {
    }
}
