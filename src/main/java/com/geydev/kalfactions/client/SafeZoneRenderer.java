package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.item.SafeZoneWandItem;
import com.geydev.kalfactions.market.PlotSelection;
import com.geydev.kalfactions.safezone.SafeZonePayloads;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
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
public final class SafeZoneRenderer {
    private static final double RENDER_DISTANCE = 128.0D;
    private static final float ZONE_RED = 0.20F;
    private static final float ZONE_GREEN = 0.84F;
    private static final float ZONE_BLUE = 0.78F;

    private static String lastActionBarZone;

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSafeZoneStore.clear();
        lastActionBarZone = null;
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
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());

        for (SafeZonePayloads.ZoneEntry zone
                : ClientSafeZoneStore.zonesIn(minecraft.level.dimension().location())) {
            renderBox(poseStack, lines, cameraPos,
                    zone.minX(), zone.minY(), zone.minZ(),
                    zone.maxX() + 1, zone.maxY() + 1, zone.maxZ() + 1,
                    ZONE_RED, ZONE_GREEN, ZONE_BLUE);
        }

        PlotSelection selection = heldSelection(player);
        if (selection != null && selection.matchesDimension(minecraft.level)) {
            BlockPos first = selection.first();
            BlockPos second = selection.second().orElse(first);
            renderBox(poseStack, lines, cameraPos,
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
        SafeZonePayloads.ZoneEntry inside = null;
        for (SafeZonePayloads.ZoneEntry zone
                : ClientSafeZoneStore.zonesIn(minecraft.level.dimension().location())) {
            if (zone.contains(pos.getX(), pos.getY(), pos.getZ())) {
                inside = zone;
                break;
            }
        }
        if (inside == null) {
            if (lastActionBarZone != null) {
                lastActionBarZone = null;
                player.displayClientMessage(Component.translatable("kingdoms.safezone.left"), true);
            }
            return;
        }
        if (!inside.id().equals(lastActionBarZone)) {
            lastActionBarZone = inside.id();
            player.displayClientMessage(Component.translatable("kingdoms.safezone.entered"), true);
        }
    }

    private static PlotSelection heldSelection(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            PlotSelection selection = SafeZoneWandItem.selectionOf(stack);
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

    private SafeZoneRenderer() {
    }
}
