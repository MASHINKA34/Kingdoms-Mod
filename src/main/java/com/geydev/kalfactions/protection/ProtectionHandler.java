package com.geydev.kalfactions.protection;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.chest.AccessTool;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.war.WarManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class ProtectionHandler {
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        WarManager wars = WarManager.get(level);
        BlockPos breakPos = event.getPos();
        boolean canBuild = FactionAccess.canBuild(player, level, breakPos);
        boolean warBreak = !canBuild && wars.canBuildInWar(player, level, breakPos);
        // War override: a belligerent may break in the enemy faction's claims while the war is active.
        if (!canBuild && !warBreak) {
            event.setCanceled(true);
            deny(player, "kingdoms.protection.no_break");
            return;
        }
        if (warBreak) {
            // Storages are excluded from war-break so loot cannot be reached through claim walls.
            if (level.getBlockEntity(breakPos) instanceof net.minecraft.world.Container) {
                event.setCanceled(true);
                deny(player, "kingdoms.war.container_protected");
                return;
            }
            wars.recordWarBreak(player, level, level.getBlockState(breakPos));
        }
        // Copy-on-write the chunk before the break is applied (no-op outside a war).
        wars.onChunkModified(level, new ChunkPos(breakPos));
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        // The only war placement override is TNT on enemy claims; everything else needs canBuild.
        // We still snapshot, so a warring faction's own peaceful builds revert.
        WarManager wars = WarManager.get(level);
        Map<BlockPos, BlockState> placed = new HashMap<>();
        boolean denied = false;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiPlaceEvent) {
            for (BlockSnapshot snapshot : multiPlaceEvent.getReplacedBlockSnapshots()) {
                if (!FactionAccess.canBuild(player, level, snapshot.getPos())) {
                    denied = true;
                    break;
                }
                placed.put(snapshot.getPos().immutable(), snapshot.getState());
            }
        } else if (FactionAccess.canBuild(player, level, event.getPos())
                || (event.getPlacedBlock().is(Blocks.TNT)
                        && wars.canBuildInWar(player, level, event.getPos()))) {
            placed.put(event.getPos().immutable(), event.getBlockSnapshot().getState());
        } else {
            denied = true;
        }

        if (denied) {
            event.setCanceled(true);
            deny(player, "kingdoms.protection.no_place");
            return;
        }
        // The place event fires after the block is in the world, so onBlocksPlaced patches the
        // captured chunk back to each position's pre-placement state (no-op outside a war).
        wars.onBlocksPlaced(level, placed);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (isAlwaysAllowed(state)) {
            return;
        }

        boolean accessTool = player.getItemInHand(event.getHand()).getItem() instanceof AccessTool;
        if (isContainer(level, pos)) {
            if (!canAccessContainer(player, level, pos)) {
                cancelInteraction(event);
                deny(player, event.getHand(), "kingdoms.protection.no_container");
                return;
            }
            if (accessTool) {
                if (!FactionAccess.canBuild(player, level, pos)) {
                    cancelInteraction(event);
                    deny(player, event.getHand(), "kingdoms.protection.no_edit_container");
                    return;
                }
                handleAccessTool(event, player, level, pos);
            }
            return;
        }

        if (!FactionAccess.canBuild(player, level, pos)) {
            WarManager wars = WarManager.get(level);
            if (state.is(Blocks.TNT) && wars.canBuildInWar(player, level, pos)) {
                return;
            }
            if (player.getItemInHand(event.getHand()).is(Items.TNT)
                    && wars.canBuildInWar(player, level, pos)) {
                event.setUseBlock(TriState.FALSE);
                return;
            }
            cancelInteraction(event);
            deny(player, event.getHand(), "kingdoms.protection.no_interact");
            return;
        }

        if (accessTool) {
            handleAccessTool(event, player, level, pos);
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        for (Slot slot : event.getContainer().slots) {
            if (slot.container instanceof BlockEntity blockEntity
                    && !canAccessContainer(player, level, blockEntity.getBlockPos())) {
                player.closeContainer();
                deny(player, "kingdoms.protection.no_container");
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!ModConfigSpec.PROTECT_EXPLOSIONS.get()
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        FactionManager factions = FactionManager.get(level);
        WarManager wars = WarManager.get(level);
        UUID exploderFaction = resolveExploderFaction(factions, event.getExplosion());
        event.getAffectedBlocks().removeIf(pos -> {
            UUID owner = factions.getFactionIdAt(ClaimKey.of(level, pos)).orElse(null);
            if (owner == null) {
                return false; // unclaimed land: vanilla explosion
            }
            if (exploderFaction != null && wars.areAtWar(owner, exploderFaction)) {
                if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container) {
                    return true; // storages stay protected even from war explosions
                }
                wars.onChunkModified(level, new ChunkPos(pos)); // snapshot before the blast
                double surviveChance = factions.getFactionById(owner)
                        .map(faction -> Math.min(0.30D, 0.10D * faction.researchBonusCount("TNT_RESIST")))
                        .orElse(0.0D);
                if (surviveChance > 0.0D && level.getRandom().nextFloat() < surviveChance) {
                    return true; // research-hardened claim block survives the blast
                }
                return false; // a belligerent's explosion may damage the enemy claim
            }
            return true; // protected claim
        });
    }

    private static UUID resolveExploderFaction(FactionManager factions, Explosion explosion) {
        if (explosion.getIndirectSourceEntity() instanceof ServerPlayer player) {
            return factions.getFactionIdForMember(player.getUUID()).orElse(null);
        }
        return null;
    }

    private static void cancelInteraction(PlayerInteractEvent.RightClickBlock event) {
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    private static void handleAccessTool(
            PlayerInteractEvent.RightClickBlock event,
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos
    ) {
        AccessTool.ToolResult result = AccessTool.handleProtectedUse(player, level, pos, event.getHand());
        if (result == AccessTool.ToolResult.PASS) {
            return;
        }
        cancelInteraction(event);
        event.setCancellationResult(result == AccessTool.ToolResult.CONSUME
                ? InteractionResult.SUCCESS
                : InteractionResult.FAIL);
    }

    private static final TagKey<Block> INTERACTABLE =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "interactable"));

    private static boolean isAlwaysAllowed(BlockState state) {
        return state.is(INTERACTABLE)
                || state.is(BlockTags.DOORS)
                || state.is(BlockTags.TRAPDOORS)
                || state.is(BlockTags.BUTTONS)
                || state.is(BlockTags.PRESSURE_PLATES)
                || state.is(BlockTags.FENCE_GATES)
                || state.getBlock() instanceof LeverBlock;
    }

    private static boolean isContainer(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof Container
                || level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null;
    }

    private static boolean canAccessContainer(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return player.hasPermissions(2)
                || FactionManager.get(level).canAccessContainer(player.getUUID(), level, pos);
    }

    private static void deny(ServerPlayer player, InteractionHand hand, String translationKey) {
        if (hand == InteractionHand.MAIN_HAND) {
            deny(player, translationKey);
        }
    }

    private static void deny(ServerPlayer player, String translationKey) {
        player.displayClientMessage(Component.translatable(translationKey), true);
    }

    private ProtectionHandler() {
    }
}
