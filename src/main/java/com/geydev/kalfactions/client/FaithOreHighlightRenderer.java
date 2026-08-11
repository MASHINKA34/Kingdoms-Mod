package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.faith.FaithTags;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class FaithOreHighlightRenderer {
    private static final float LINE_RED = 0.98F;
    private static final float LINE_GREEN = 0.82F;
    private static final float LINE_BLUE = 0.34F;
    private static final float LINE_ALPHA = 0.85F;
    private static final int MIN_SCAN_TICKS = 5;

    private static final List<BlockPos> CACHED = new ArrayList<>();
    private static int tickCounter;
    private static BlockPos lastScanOrigin;

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientFaithHighlightState.clear();
        CACHED.clear();
        lastScanOrigin = null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ClientFaithHighlightState.enabled()) {
            if (!CACHED.isEmpty()) {
                CACHED.clear();
                lastScanOrigin = null;
            }
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        BlockPos origin = player.blockPosition();
        int radius = ClientFaithHighlightState.radius();
        boolean walkedOut = lastScanOrigin == null
                || lastScanOrigin.distSqr(origin) > (double) radius * radius / 4.0D;
        tickCounter++;
        if (tickCounter < ClientFaithHighlightState.scanTicks()
                && !(walkedOut && tickCounter >= MIN_SCAN_TICKS)) {
            return;
        }
        tickCounter = 0;
        lastScanOrigin = origin;
        scan(level, origin);
    }

    private static void scan(ClientLevel level, BlockPos origin) {
        int radius = ClientFaithHighlightState.radius();
        int limit = ClientFaithHighlightState.maxBlocks();
        List<BlockPos> found = new ArrayList<>(Math.min(limit, 256));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minY = Math.max(level.getMinBuildHeight(), origin.getY() - radius);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + radius);
        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    if (!level.hasChunkAt(cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(FaithTags.HIGHLIGHTED_ORES)) {
                        found.add(cursor.immutable());
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        CACHED.clear();
        CACHED.addAll(found.subList(0, Math.min(limit, found.size())));
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || !ClientFaithHighlightState.enabled()
                || CACHED.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = bufferSource.getBuffer(FaithRenderTypes.SEE_THROUGH_LINES);
        for (BlockPos pos : CACHED) {
            LevelRenderer.renderLineBox(
                    poseStack,
                    lines,
                    pos.getX() - cameraPos.x,
                    pos.getY() - cameraPos.y,
                    pos.getZ() - cameraPos.z,
                    pos.getX() + 1.0D - cameraPos.x,
                    pos.getY() + 1.0D - cameraPos.y,
                    pos.getZ() + 1.0D - cameraPos.z,
                    LINE_RED,
                    LINE_GREEN,
                    LINE_BLUE,
                    LINE_ALPHA
            );
        }
        bufferSource.endBatch(FaithRenderTypes.SEE_THROUGH_LINES);
    }

    private FaithOreHighlightRenderer() {
    }
}
