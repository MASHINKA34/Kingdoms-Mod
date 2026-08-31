package com.geydev.kalfactions.safezone;

import com.geydev.kalfactions.item.SafeZoneWandItem;
import com.geydev.kalfactions.market.PlotSelection;
import com.geydev.kalfactions.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SafeZoneService {
    public static List<SafeZonePayloads.ZoneEntry> entriesFor(ServerLevel level, ResourceKey<Level> dimension) {
        List<SafeZonePayloads.ZoneEntry> entries = new ArrayList<>();
        for (SafeZone zone : SafeZoneManager.get(level).all()) {
            if (!zone.dimension().equals(dimension)) {
                continue;
            }
            BlockPos min = zone.min();
            BlockPos max = zone.max();
            entries.add(new SafeZonePayloads.ZoneEntry(
                    zone.id(),
                    min.getX(), min.getY(), min.getZ(),
                    max.getX(), max.getY(), max.getZ()
            ));
        }
        return List.copyOf(entries);
    }

    public static void syncTo(ServerPlayer player) {
        ResourceKey<Level> dimension = player.level().dimension();
        PacketDistributor.sendToPlayer(player, new SafeZonePayloads.S2CSyncSafeZones(
                dimension.location(),
                entriesFor(player.serverLevel(), dimension)
        ));
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncTo(player);
        }
    }

    public static Component failureMessage(SafeZoneManager.Reason reason, String id) {
        return switch (reason) {
            case DUPLICATE -> Component.translatable("kingdoms.safezone.duplicate", id);
            case TOO_MANY -> Component.translatable("kingdoms.safezone.too_many", SafeZoneManager.MAX_ZONES);
            case TOO_LARGE -> Component.translatable("kingdoms.safezone.too_large", SafeZoneManager.MAX_SIDE);
            default -> Component.translatable("kingdoms.safezone.invalid_id", SafeZoneManager.MAX_ID_LENGTH);
        };
    }

    public static boolean createFromSelection(ServerPlayer player, ItemStack wand, PlotSelection selection) {
        ServerLevel level = player.serverLevel();
        SafeZoneManager manager = SafeZoneManager.get(level);
        String id = manager.suggestId();
        SafeZoneManager.Reason reason = manager.add(
                id,
                level.dimension(),
                selection.first(),
                selection.second().orElseThrow()
        );
        if (reason != SafeZoneManager.Reason.OK) {
            player.displayClientMessage(failureMessage(reason, id), true);
            return false;
        }
        wand.remove(ModDataComponents.SAFE_ZONE_SELECTION.get());
        syncAll(level.getServer());
        SafeZone zone = manager.byId(id).orElseThrow();
        BlockPos min = zone.min();
        BlockPos max = zone.max();
        player.displayClientMessage(Component.translatable(
                "kingdoms.safezone.wand.created",
                zone.id(),
                max.getX() - min.getX() + 1,
                max.getY() - min.getY() + 1,
                max.getZ() - min.getZ() + 1
        ), true);
        return true;
    }

    public static boolean removeAt(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        SafeZoneManager manager = SafeZoneManager.get(level);
        SafeZone zone = manager.zoneAt(level.dimension(), pos).orElse(null);
        if (zone == null) {
            player.displayClientMessage(Component.translatable("kingdoms.safezone.wand.no_zone"), true);
            return false;
        }
        manager.remove(zone.id());
        syncAll(level.getServer());
        player.displayClientMessage(
                Component.translatable("kingdoms.safezone.wand.removed", zone.id()), true);
        return true;
    }

    public static ItemStack heldWand(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof SafeZoneWandItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static boolean adjustSelection(ServerPlayer player, byte faceIndex, byte delta) {
        if (!player.hasPermissions(2) || delta == 0 || faceIndex < 0 || faceIndex >= Direction.values().length) {
            return false;
        }
        ItemStack wand = heldWand(player);
        PlotSelection selection = SafeZoneWandItem.selectionOf(wand);
        if (selection == null || !selection.isComplete() || !selection.matchesDimension(player.level())) {
            return false;
        }
        BlockPos first = selection.first();
        BlockPos second = selection.second().orElseThrow();
        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxX = Math.max(first.getX(), second.getX());
        int maxY = Math.max(first.getY(), second.getY());
        int maxZ = Math.max(first.getZ(), second.getZ());
        int span = SafeZoneManager.MAX_SIDE;
        int step = delta > 0 ? 1 : -1;
        switch (Direction.values()[faceIndex]) {
            case EAST -> maxX = Math.max(minX, Math.min(minX + span - 1, maxX + step));
            case WEST -> minX = Math.min(maxX, Math.max(maxX - span + 1, minX - step));
            case UP -> maxY = Math.max(minY, Math.min(minY + span - 1, maxY + step));
            case DOWN -> minY = Math.min(maxY, Math.max(maxY - span + 1, minY - step));
            case SOUTH -> maxZ = Math.max(minZ, Math.min(minZ + span - 1, maxZ + step));
            case NORTH -> minZ = Math.min(maxZ, Math.max(maxZ - span + 1, minZ - step));
        }
        minY = Math.max(player.level().getMinBuildHeight(), minY);
        maxY = Math.min(player.level().getMaxBuildHeight() - 1, maxY);
        wand.set(ModDataComponents.SAFE_ZONE_SELECTION.get(), new PlotSelection(
                selection.dimension(),
                new BlockPos(minX, minY, minZ),
                Optional.of(new BlockPos(maxX, maxY, maxZ))
        ));
        player.displayClientMessage(Component.translatable(
                "kingdoms.safezone.wand.size",
                maxX - minX + 1,
                maxY - minY + 1,
                maxZ - minZ + 1
        ), true);
        return true;
    }

    private SafeZoneService() {
    }
}
