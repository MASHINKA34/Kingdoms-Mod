package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.market.MarketPlot;
import com.geydev.kalfactions.market.MarketPlotManager;
import com.geydev.kalfactions.sanctuary.SanctuaryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {
    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void kingdoms$confinePlotFluids(
            LevelAccessor level,
            BlockPos pos,
            BlockState blockState,
            Direction direction,
            FluidState fluidState,
            CallbackInfo ci
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos source = pos.relative(direction.getOpposite());
        if (!SanctuaryManager.get(serverLevel).isSanctuary(serverLevel, source)) {
            return;
        }
        MarketPlotManager plots = MarketPlotManager.get(serverLevel);
        ResourceKey<Level> dimension = serverLevel.dimension();
        int sourcePlot = plots.plotAt(dimension, source).map(MarketPlot::id).orElse(-1);
        if (sourcePlot == -1) {
            return;
        }
        int targetPlot = plots.plotAt(dimension, pos).map(MarketPlot::id).orElse(-1);
        if (targetPlot != sourcePlot) {
            ci.cancel();
        }
    }
}
