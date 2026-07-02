package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.market.MarketPayloads;
import com.geydev.kalfactions.market.PlotSelection;
import com.geydev.kalfactions.registry.ModDataComponents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class PlotRenderer {
    private static final double RENDER_DISTANCE = 128.0D;
    private static final byte STATE_FOR_SALE = 0;
    private static final byte STATE_RESALE = 2;

    private static int lastActionBarPlot = -1;

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPlotStore.clear();
        lastActionBarPlot = -1;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());

        for (MarketPayloads.PlotEntry entry
                : ClientPlotStore.plotsIn(minecraft.level.dimension().location())) {
            boolean mine = entry.owner().map(owner -> owner.equals(player.getUUID())).orElse(false);
            float red;
            float green;
            float blue;
            if (mine) {
                red = 0.30F;
                green = 0.85F;
                blue = 1.00F;
            } else if (entry.state() == STATE_FOR_SALE) {
                red = 0.25F;
                green = 1.00F;
                blue = 0.40F;
            } else if (entry.state() == STATE_RESALE) {
                red = 1.00F;
                green = 0.75F;
                blue = 0.20F;
            } else {
                continue;
            }
            renderBox(poseStack, lines, camera,
                    entry.minX(), entry.minY(), entry.minZ(),
                    entry.maxX() + 1, entry.maxY() + 1, entry.maxZ() + 1,
                    red, green, blue);
        }

        PlotSelection selection = heldSelection(player);
        if (selection != null && selection.dimension().equals(minecraft.level.dimension().location())) {
            BlockPos first = selection.first();
            BlockPos second = selection.second().orElse(first);
            renderBox(poseStack, lines, camera,
                    Math.min(first.getX(), second.getX()),
                    Math.min(first.getY(), second.getY()),
                    Math.min(first.getZ(), second.getZ()),
                    Math.max(first.getX(), second.getX()) + 1,
                    Math.max(first.getY(), second.getY()) + 1,
                    Math.max(first.getZ(), second.getZ()) + 1,
                    1.00F, 1.00F, 1.00F);
        }

        bufferSource.endBatch(RenderType.lines());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        BlockPos pos = player.blockPosition();
        MarketPayloads.PlotEntry inside = null;
        for (MarketPayloads.PlotEntry entry
                : ClientPlotStore.plotsIn(minecraft.level.dimension().location())) {
            if (pos.getX() >= entry.minX() && pos.getX() <= entry.maxX()
                    && pos.getY() >= entry.minY() && pos.getY() <= entry.maxY()
                    && pos.getZ() >= entry.minZ() && pos.getZ() <= entry.maxZ()) {
                inside = entry;
                break;
            }
        }
        if (inside == null) {
            lastActionBarPlot = -1;
            return;
        }
        if (inside.id() == lastActionBarPlot) {
            return;
        }
        lastActionBarPlot = inside.id();
        boolean mine = inside.owner().map(owner -> owner.equals(player.getUUID())).orElse(false);
        if (mine) {
            player.displayClientMessage(
                    Component.translatable("kingdoms.plot.enter.yours", inside.id()), true);
        } else if (inside.state() == STATE_FOR_SALE) {
            player.displayClientMessage(Component.translatable(
                    "kingdoms.plot.enter.for_sale",
                    inside.id(),
                    NumismaticsEconomy.format(inside.price())), true);
        } else if (inside.state() == STATE_RESALE) {
            player.displayClientMessage(Component.translatable(
                    "kingdoms.plot.enter.resale",
                    inside.id(),
                    inside.ownerName(),
                    NumismaticsEconomy.format(inside.price())), true);
        }
    }

    private static PlotSelection heldSelection(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            PlotSelection selection = stack.get(ModDataComponents.PLOT_SELECTION);
            if (selection != null) {
                return selection;
            }
        }
        return null;
    }

    private static void renderBox(
            PoseStack poseStack,
            VertexConsumer lines,
            Vec3 camera,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float red, float green, float blue
    ) {
        double centerX = (minX + maxX) / 2.0D;
        double centerZ = (minZ + maxZ) / 2.0D;
        if (camera.distanceToSqr(centerX, camera.y, centerZ) > RENDER_DISTANCE * RENDER_DISTANCE) {
            return;
        }
        LevelRenderer.renderLineBox(
                poseStack,
                lines,
                minX - camera.x, minY - camera.y, minZ - camera.z,
                maxX - camera.x, maxY - camera.y, maxZ - camera.z,
                red, green, blue, 1.0F
        );
    }

    private PlotRenderer() {
    }
}
