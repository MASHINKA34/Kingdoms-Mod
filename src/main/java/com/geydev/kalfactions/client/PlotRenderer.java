package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.screen.PlotCreateScreen;
import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.item.PlotWandItem;
import com.geydev.kalfactions.market.MarketPayloads;
import com.geydev.kalfactions.market.PlotSelection;
import com.geydev.kalfactions.registry.ModDataComponents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class PlotRenderer {
    private static final double RENDER_DISTANCE = 128.0D;
    private static final double LABEL_DISTANCE = 48.0D;
    private static final float LABEL_SCALE = 0.032F;
    private static final byte STATE_FOR_SALE = 0;
    private static final byte STATE_RESALE = 2;
    private static final int LABEL_TITLE_COLOR = 0xFFFFFFFF;
    private static final int LABEL_FOR_SALE_COLOR = 0xFF6BF08A;
    private static final int LABEL_RESALE_COLOR = 0xFFF5C542;
    private static final int LABEL_OWNED_COLOR = 0xFF9AD8FF;

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

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

        List<MarketPayloads.PlotEntry> entries =
                ClientPlotStore.plotsIn(minecraft.level.dimension().location());
        PlotSelection selection = heldSelection(player);
        AABB selectionBox = selection != null
                && selection.dimension().equals(minecraft.level.dimension().location())
                ? selectionBounds(selection)
                : null;

        // BufferSource keeps one builder at a time: requesting a different RenderType
        // ends the previous batch, so each phase below fully finishes before the next.
        if (selectionBox != null) {
            VertexConsumer fill = bufferSource.getBuffer(RenderType.debugFilledBox());
            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack,
                    fill,
                    selectionBox.minX - cameraPos.x,
                    selectionBox.minY - cameraPos.y,
                    selectionBox.minZ - cameraPos.z,
                    selectionBox.maxX - cameraPos.x,
                    selectionBox.maxY - cameraPos.y,
                    selectionBox.maxZ - cameraPos.z,
                    0.55F, 0.72F, 1.00F, 0.16F
            );
            if (selection.isComplete() && Screen.hasControlDown()) {
                Direction face = targetedFace(player, selectionBox);
                if (face != null) {
                    AABB slab = faceSlab(selectionBox, face);
                    LevelRenderer.addChainedFilledBoxVertices(
                            poseStack,
                            fill,
                            slab.minX - cameraPos.x, slab.minY - cameraPos.y, slab.minZ - cameraPos.z,
                            slab.maxX - cameraPos.x, slab.maxY - cameraPos.y, slab.maxZ - cameraPos.z,
                            0.75F, 0.88F, 1.00F, 0.42F
                    );
                }
            }
            bufferSource.endBatch(RenderType.debugFilledBox());
        }

        boolean holdingWand = isHoldingWand(player);
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        for (MarketPayloads.PlotEntry entry : entries) {
            boolean mine = entry.access()
                    || entry.owner().map(owner -> owner.equals(player.getUUID())).orElse(false);
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
            } else if (holdingWand) {
                red = 0.95F;
                green = 0.30F;
                blue = 0.30F;
            } else {
                continue;
            }
            renderBox(poseStack, lines, cameraPos,
                    entry.minX(), entry.minY(), entry.minZ(),
                    entry.maxX() + 1, entry.maxY() + 1, entry.maxZ() + 1,
                    red, green, blue);
        }
        if (selectionBox != null) {
            renderBox(poseStack, lines, cameraPos,
                    selectionBox.minX, selectionBox.minY, selectionBox.minZ,
                    selectionBox.maxX, selectionBox.maxY, selectionBox.maxZ,
                    1.00F, 1.00F, 1.00F);
        }
        bufferSource.endBatch(RenderType.lines());

        for (MarketPayloads.PlotEntry entry : entries) {
            boolean mine = entry.access()
                    || entry.owner().map(owner -> owner.equals(player.getUUID())).orElse(false);
            renderPlotLabel(poseStack, bufferSource, camera, cameraPos, entry, mine);
        }
        bufferSource.endBatch();
    }

    private static void renderPlotLabel(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Camera camera,
            Vec3 cameraPos,
            MarketPayloads.PlotEntry entry,
            boolean mine
    ) {
        double centerX = (entry.minX() + entry.maxX() + 1) / 2.0D;
        double centerZ = (entry.minZ() + entry.maxZ() + 1) / 2.0D;
        double labelY = entry.maxY() + 1.9D;
        double distanceSq = cameraPos.distanceToSqr(centerX, labelY, centerZ);
        if (distanceSq > LABEL_DISTANCE * LABEL_DISTANCE) {
            return;
        }

        List<Line> linesToDraw = new ArrayList<>(2);
        if (entry.state() == STATE_FOR_SALE) {
            linesToDraw.add(new Line(
                    Component.translatable("kingdoms.plot.label.title", entry.id()), LABEL_TITLE_COLOR));
            linesToDraw.add(new Line(
                    Component.translatable("kingdoms.plot.label.for_sale",
                            NumismaticsEconomy.format(entry.price())),
                    LABEL_FOR_SALE_COLOR));
        } else if (entry.state() == STATE_RESALE) {
            linesToDraw.add(new Line(
                    Component.translatable("kingdoms.plot.label.title", entry.id()), LABEL_TITLE_COLOR));
            linesToDraw.add(new Line(
                    Component.translatable("kingdoms.plot.label.resale",
                            entry.ownerName(),
                            NumismaticsEconomy.format(entry.price())),
                    LABEL_RESALE_COLOR));
        } else if (!entry.ownerName().isEmpty()) {
            linesToDraw.add(new Line(
                    Component.translatable("kingdoms.plot.label.owned", entry.ownerName()),
                    mine ? LABEL_OWNED_COLOR : LABEL_TITLE_COLOR));
        } else {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(centerX - cameraPos.x, labelY - cameraPos.y, centerZ - cameraPos.z);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
        Font font = Minecraft.getInstance().font;
        Matrix4f matrix = poseStack.last().pose();
        int background = (int) (0.4F * 255.0F) << 24;
        int lineY = -linesToDraw.size() * 10;
        for (Line line : linesToDraw) {
            float x = -font.width(line.text()) / 2.0F;
            // Nametag-style two passes: a dim see-through layer for occlusion and a
            // depth-tested full-color layer on top, so the text is only grey when
            // actually hidden behind blocks.
            font.drawInBatch(line.text(), x, lineY, 0x20000000 | (line.color() & 0xFFFFFF),
                    false, matrix, bufferSource,
                    Font.DisplayMode.SEE_THROUGH, background, LightTexture.FULL_BRIGHT);
            font.drawInBatch(line.text(), x, lineY, line.color(), false, matrix, bufferSource,
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            lineY += 10;
        }
        poseStack.popPose();
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null
                || !Screen.hasControlDown() || !player.hasPermissions(2)) {
            return;
        }
        PlotSelection selection = heldSelection(player);
        if (selection == null || !selection.isComplete()
                || !selection.dimension().equals(minecraft.level.dimension().location())) {
            return;
        }
        AABB box = selectionBounds(selection);
        Direction face = targetedFace(player, box);
        if (face == null) {
            return;
        }
        // Scroll direction is relative to the player: wheel-down pulls the face
        // toward them, wheel-up pushes it away, no matter which side they stand on.
        boolean towardPlayer = event.getScrollDeltaY() < 0.0D;
        boolean outside = isOutsideFacePlane(player, box, face);
        byte delta = (byte) (towardPlayer == outside ? 1 : -1);
        PacketDistributor.sendToServer(
                new MarketPayloads.C2SAdjustPlotSelection((byte) face.ordinal(), delta));
        event.setCanceled(true);
    }

    private static boolean isOutsideFacePlane(LocalPlayer player, AABB box, Direction face) {
        Vec3 eye = player.getEyePosition();
        return switch (face) {
            case EAST -> eye.x >= box.maxX;
            case WEST -> eye.x <= box.minX;
            case UP -> eye.y >= box.maxY;
            case DOWN -> eye.y <= box.minY;
            case SOUTH -> eye.z >= box.maxZ;
            case NORTH -> eye.z <= box.minZ;
        };
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() || !(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        AABB box = completeSelectionBox(player, event.getHand());
        if (box == null || player.isShiftKeyDown()
                || !box.contains(Vec3.atCenterOf(event.getPos()))) {
            return;
        }
        openCreateScreen(box);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide() || !(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        AABB box = completeSelectionBox(player, event.getHand());
        if (box == null || player.isShiftKeyDown() || targetedFace(player, box) == null) {
            return;
        }
        openCreateScreen(box);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static AABB completeSelectionBox(LocalPlayer player, InteractionHand hand) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !player.hasPermissions(2)) {
            return null;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof PlotWandItem)) {
            return null;
        }
        PlotSelection selection = stack.get(ModDataComponents.PLOT_SELECTION);
        if (selection == null || !selection.isComplete()
                || !selection.dimension().equals(minecraft.level.dimension().location())) {
            return null;
        }
        return selectionBounds(selection);
    }

    private static void openCreateScreen(AABB box) {
        Minecraft.getInstance().setScreen(new PlotCreateScreen(
                (int) Math.round(box.maxX - box.minX),
                (int) Math.round(box.maxY - box.minY),
                (int) Math.round(box.maxZ - box.minZ)
        ));
    }

    private static boolean isHoldingWand(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof PlotWandItem) {
                return true;
            }
        }
        return false;
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
        boolean mine = inside.access()
                || inside.owner().map(owner -> owner.equals(player.getUUID())).orElse(false);
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

    private static AABB selectionBounds(PlotSelection selection) {
        BlockPos first = selection.first();
        BlockPos second = selection.second().orElse(first);
        return new AABB(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()),
                Math.max(first.getX(), second.getX()) + 1,
                Math.max(first.getY(), second.getY()) + 1,
                Math.max(first.getZ(), second.getZ()) + 1
        );
    }

    private static Direction targetedFace(LocalPlayer player, AABB box) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        if (box.contains(eye)) {
            return Direction.getNearest(look.x, look.y, look.z);
        }
        Optional<Vec3> hit = box.clip(eye, eye.add(look.scale(96.0D)));
        if (hit.isEmpty()) {
            return null;
        }
        Vec3 point = hit.get();
        double epsilon = 1.0E-4D;
        if (Math.abs(point.x - box.minX) < epsilon) {
            return Direction.WEST;
        }
        if (Math.abs(point.x - box.maxX) < epsilon) {
            return Direction.EAST;
        }
        if (Math.abs(point.y - box.minY) < epsilon) {
            return Direction.DOWN;
        }
        if (Math.abs(point.y - box.maxY) < epsilon) {
            return Direction.UP;
        }
        if (Math.abs(point.z - box.minZ) < epsilon) {
            return Direction.NORTH;
        }
        return Direction.SOUTH;
    }

    private static AABB faceSlab(AABB box, Direction face) {
        double thickness = 0.03D;
        return switch (face) {
            case WEST -> new AABB(box.minX - thickness, box.minY, box.minZ,
                    box.minX + thickness, box.maxY, box.maxZ);
            case EAST -> new AABB(box.maxX - thickness, box.minY, box.minZ,
                    box.maxX + thickness, box.maxY, box.maxZ);
            case DOWN -> new AABB(box.minX, box.minY - thickness, box.minZ,
                    box.maxX, box.minY + thickness, box.maxZ);
            case UP -> new AABB(box.minX, box.maxY - thickness, box.minZ,
                    box.maxX, box.maxY + thickness, box.maxZ);
            case NORTH -> new AABB(box.minX, box.minY, box.minZ - thickness,
                    box.maxX, box.maxY, box.minZ + thickness);
            case SOUTH -> new AABB(box.minX, box.minY, box.maxZ - thickness,
                    box.maxX, box.maxY, box.maxZ + thickness);
        };
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

    private record Line(Component text, int color) {
    }

    private PlotRenderer() {
    }
}
