package com.geydev.kalfactions.dimension;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.market.MarketPlot;
import com.geydev.kalfactions.market.MarketPlotManager;
import com.geydev.kalfactions.outpost.RogueOutpostManager;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class DimensionControlEvents {
    private static final String WIPE_GEN_KEY_PREFIX = "kingdoms_wipe_gen_";

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        DimensionControlManager.get(event.getServer()).runPendingWipes(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        for (ResourceKey<Level> dimension : DimensionControlManager.get(server).consumeWipedThisStartup()) {
            cleanupModDataFor(server, dimension);
        }
    }

    @SubscribeEvent
    public static void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        ResourceKey<Level> target = event.getDimension();
        if (!DimensionControlManager.isControlled(target)) {
            return;
        }
        MinecraftServer server = event.getEntity().getServer();
        if (server == null || !DimensionControlManager.get(server).isClosed(target)) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.hasPermissions(2)) {
                return;
            }
            player.displayClientMessage(Component.translatable("kingdoms.dimension.closed_notice"), true);
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ResourceKey<Level> dimension = player.level().dimension();
        if (!DimensionControlManager.isControlled(dimension)) {
            return;
        }
        DimensionControlManager control = DimensionControlManager.get(player.serverLevel().getServer());
        if (control.isClosed(dimension) && !player.hasPermissions(2)) {
            teleportToOverworldSpawn(player);
            player.displayClientMessage(Component.translatable("kingdoms.dimension.evicted"), false);
            return;
        }
        if (persistedWipeGen(player, dimension) < control.wipeGeneration(dimension)) {
            teleportToOverworldSpawn(player);
            player.displayClientMessage(Component.translatable("kingdoms.dimension.wiped_notice"), false);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ResourceKey<Level> target = event.getTo();
        if (!DimensionControlManager.isControlled(target)) {
            return;
        }
        DimensionControlManager control = DimensionControlManager.get(player.serverLevel().getServer());
        persistedData(player).putLong(wipeGenKey(target), control.wipeGeneration(target));
    }

    public static int evacuate(MinecraftServer server, ResourceKey<Level> dimension) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return 0;
        }
        List<ServerPlayer> players = List.copyOf(level.players());
        for (ServerPlayer player : players) {
            teleportToOverworldSpawn(player);
            player.displayClientMessage(Component.translatable("kingdoms.dimension.evicted"), false);
        }
        return players.size();
    }

    private static void teleportToOverworldSpawn(ServerPlayer player) {
        ServerLevel overworld = player.serverLevel().getServer().overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        int surfaceY = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        player.teleportTo(
                overworld,
                spawn.getX() + 0.5D,
                Math.max(spawn.getY(), surfaceY),
                spawn.getZ() + 0.5D,
                player.getYRot(),
                player.getXRot()
        );
    }

    private static void cleanupModDataFor(MinecraftServer server, ResourceKey<Level> dimension) {
        String dimensionId = dimension.location().toString();
        RogueOutpostManager rogueOutposts = RogueOutpostManager.get(server);
        for (RogueOutpostManager.RogueOutpost outpost : rogueOutposts.all()) {
            if (outpost.dimension().equals(dimensionId)) {
                rogueOutposts.remove(outpost.id());
            }
        }
        MarketPlotManager plots = MarketPlotManager.get(server);
        for (MarketPlot plot : plots.all()) {
            if (plot.dimension().equals(dimension)) {
                plots.remove(plot.id());
            }
        }
    }

    private static long persistedWipeGen(ServerPlayer player, ResourceKey<Level> dimension) {
        return persistedData(player).getLong(wipeGenKey(dimension));
    }

    private static CompoundTag persistedData(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static String wipeGenKey(ResourceKey<Level> dimension) {
        return WIPE_GEN_KEY_PREFIX + dimension.location().getPath();
    }

    private DimensionControlEvents() {
    }
}
