package com.geydev.kalfactions.market;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.sanctuary.SanctuaryManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class MarketPlotEvents {
    private static final String NUMISMATICS_NAMESPACE = "numismatics";

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MarketPlotService.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MarketPlotService.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MarketPlotService.syncTo(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || !player.getMainHandItem().isEmpty()) {
            return;
        }
        MarketPlot plot = MarketPlotManager.get(level)
                .plotAt(level.dimension(), event.getPos())
                .orElse(null);
        if (plot == null || isNumismaticsBlock(level, event.getPos())) {
            return;
        }
        if (plot.isOwnedBy(player.getUUID())) {
            if (!player.isShiftKeyDown()) {
                return;
            }
        } else if (plot.state() == MarketPlot.State.OWNED) {
            return;
        }
        MarketPlotService.openScreen(player, plot);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos base = event.getPos();
        if (!SanctuaryManager.get(level).isSanctuary(level, base)) {
            return;
        }
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) {
            return;
        }
        MarketPlotManager plots = MarketPlotManager.get(level);
        ResourceKey<Level> dimension = level.dimension();
        int basePlot = plotIdAt(plots, dimension, base);
        Direction moveDirection = event.getPistonMoveType() == PistonEvent.PistonMoveType.EXTEND
                ? event.getDirection()
                : event.getDirection().getOpposite();
        List<BlockPos> affected = new ArrayList<>();
        affected.add(base.relative(event.getDirection()));
        for (BlockPos pos : resolver.getToPush()) {
            affected.add(pos);
            affected.add(pos.relative(moveDirection));
        }
        affected.addAll(resolver.getToDestroy());
        for (BlockPos pos : affected) {
            if (plotIdAt(plots, dimension, pos) != basePlot) {
                event.setCanceled(true);
                return;
            }
        }
    }

    private static int plotIdAt(MarketPlotManager plots, ResourceKey<Level> dimension, BlockPos pos) {
        return plots.plotAt(dimension, pos).map(MarketPlot::id).orElse(-1);
    }

    public static boolean isNumismaticsBlock(ServerLevel level, BlockPos pos) {
        return NUMISMATICS_NAMESPACE.equals(
                BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getNamespace());
    }

    private MarketPlotEvents() {
    }
}
