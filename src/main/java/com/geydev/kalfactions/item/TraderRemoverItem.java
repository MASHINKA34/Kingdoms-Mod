package com.geydev.kalfactions.item;

import com.geydev.kalfactions.market.MarketPlot;
import com.geydev.kalfactions.market.MarketPlotManager;
import com.geydev.kalfactions.market.MarketPlotService;
import com.geydev.kalfactions.outpost.cluster.ResourceClusterManager;
import com.geydev.kalfactions.outpost.cluster.ResourceClusterType;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

public final class TraderRemoverItem extends Item {
    public TraderRemoverItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        BlockPos pos = context.getClickedPos();
        ResourceClusterManager clusters = ResourceClusterManager.get(level);
        Optional<ResourceClusterType> cluster = clusters.clusterBlockAt(pos);
        MarketPlotManager plots = MarketPlotManager.get(level);
        Optional<MarketPlot> plot = cluster.isEmpty() && player.isShiftKeyDown()
                ? plots.plotAt(level.dimension(), pos)
                : Optional.empty();
        if (cluster.isEmpty() && plot.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("kingdoms.remover.no_permission"), true);
            return InteractionResult.FAIL;
        }
        if (cluster.isPresent()) {
            clusters.removeCluster(level, pos);
            player.displayClientMessage(
                    Component.translatable("kingdoms.remover.cluster_removed", cluster.get().displayName()),
                    false
            );
            return InteractionResult.SUCCESS;
        }
        MarketPlot target = plot.get();
        plots.remove(target.id());
        MarketPlotService.syncAll(level.getServer());
        player.displayClientMessage(
                Component.translatable("kingdoms.remover.plot_removed", target.id()),
                false
        );
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.kingdoms.trader_remover.tooltip.trader")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.kingdoms.trader_remover.tooltip.cluster")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.kingdoms.trader_remover.tooltip.plot")
                .withStyle(ChatFormatting.GRAY));
    }
}
