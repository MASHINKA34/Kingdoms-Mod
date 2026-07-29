package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.outpost.trader.TraderPayloads;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class TraderPointRenderer {
    private static final double RENDER_DISTANCE = 192.0D;
    private static final double LABEL_DISTANCE = 96.0D;
    private static final float LABEL_SCALE = 0.028F;
    private static final int LABEL_COLOR = 0xFFE8C46A;
    private static final int ACTIVE_COLOR = 0xFF7BE38F;

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientTraderPointStore.clear();
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
        List<TraderPayloads.PointEntry> entries =
                ClientTraderPointStore.pointsIn(minecraft.level.dimension().location());
        if (entries.isEmpty()) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        for (TraderPayloads.PointEntry entry : entries) {
            BlockPos pos = entry.pos();
            if (cameraPos.distanceToSqr(Vec3.atCenterOf(pos)) > RENDER_DISTANCE * RENDER_DISTANCE) {
                continue;
            }
            float red = entry.active() ? 0.45F : 0.95F;
            float green = entry.active() ? 0.90F : 0.75F;
            float blue = entry.active() ? 0.55F : 0.25F;
            LevelRenderer.renderLineBox(
                    poseStack,
                    lines,
                    pos.getX() - cameraPos.x,
                    pos.getY() - cameraPos.y,
                    pos.getZ() - cameraPos.z,
                    pos.getX() + 1 - cameraPos.x,
                    pos.getY() + 2 - cameraPos.y,
                    pos.getZ() + 1 - cameraPos.z,
                    red, green, blue, 1.0F
            );
        }
        bufferSource.endBatch(RenderType.lines());

        for (TraderPayloads.PointEntry entry : entries) {
            renderLabel(poseStack, bufferSource, camera, cameraPos, entry);
        }
        bufferSource.endBatch();
    }

    private static void renderLabel(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Camera camera,
            Vec3 cameraPos,
            TraderPayloads.PointEntry entry
    ) {
        BlockPos pos = entry.pos();
        double labelY = pos.getY() + 2.4D;
        if (cameraPos.distanceToSqr(pos.getX() + 0.5D, labelY, pos.getZ() + 0.5D)
                > LABEL_DISTANCE * LABEL_DISTANCE) {
            return;
        }
        Component title = Component.translatable("kingdoms.trader.point.marker", entry.index());
        Component detail = entry.active()
                ? Component.translatable("kingdoms.trader.point.marker.active")
                : Component.translatable(
                        "kingdoms.trader.point.marker.pos", pos.getX(), pos.getY(), pos.getZ());

        poseStack.pushPose();
        poseStack.translate(pos.getX() + 0.5D - cameraPos.x, labelY - cameraPos.y, pos.getZ() + 0.5D - cameraPos.z);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
        Font font = Minecraft.getInstance().font;
        Matrix4f matrix = poseStack.last().pose();
        int background = (int) (0.4F * 255.0F) << 24;
        int lineY = -20;
        for (Component line : List.of(title, detail)) {
            int color = line == title ? LABEL_COLOR : entry.active() ? ACTIVE_COLOR : 0xFFD5D5D5;
            float x = -font.width(line) / 2.0F;
            font.drawInBatch(line, x, lineY, 0x20000000 | (color & 0xFFFFFF), false, matrix, bufferSource,
                    Font.DisplayMode.SEE_THROUGH, background, LightTexture.FULL_BRIGHT);
            font.drawInBatch(line, x, lineY, color, false, matrix, bufferSource,
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            lineY += 10;
        }
        poseStack.popPose();
    }

    private TraderPointRenderer() {
    }
}
