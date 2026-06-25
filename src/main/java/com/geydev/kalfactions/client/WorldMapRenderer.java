package com.geydev.kalfactions.client;

import com.geydev.kalfactions.block.WorldMapBlock;
import com.geydev.kalfactions.block.WorldMapBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

public final class WorldMapRenderer implements BlockEntityRenderer<WorldMapBlockEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/map/map_background.png");

    public WorldMapRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(WorldMapBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(WorldMapBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos())
                .inflate(WorldMapBlock.WIDTH, WorldMapBlock.HEIGHT, WorldMapBlock.WIDTH);
    }

    @Override
    public void render(WorldMapBlockEntity blockEntity, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = blockEntity.getBlockState();
        if (!state.hasProperty(WorldMapBlock.FACING)) {
            return;
        }
        Direction facing = state.getValue(WorldMapBlock.FACING);
        Direction left = facing.getCounterClockWise();

        float fx = facing.getStepX();
        float fz = facing.getStepZ();
        float lx = left.getStepX();
        float lz = left.getStepZ();

        float eps = 0.02F;
        float baseX = (fx > 0 ? 1.0F : 0.0F) + fx * eps;
        float baseZ = (fz > 0 ? 1.0F : 0.0F) + fz * eps;

        float leftMin = -WorldMapBlock.HALF_WIDTH;
        float leftMax = WorldMapBlock.HALF_WIDTH + 1.0F;
        float yMin = 0.0F;
        float yMax = WorldMapBlock.HEIGHT;

        float[] c00 = {baseX + lx * leftMin, yMin, baseZ + lz * leftMin};
        float[] c10 = {baseX + lx * leftMax, yMin, baseZ + lz * leftMax};
        float[] c11 = {baseX + lx * leftMax, yMax, baseZ + lz * leftMax};
        float[] c01 = {baseX + lx * leftMin, yMax, baseZ + lz * leftMin};

        VertexConsumer vc = buffer.getBuffer(RenderType.entitySolid(TEXTURE));
        Matrix4f matrix = pose.last().pose();

        quad(vc, matrix, c00, c10, c11, c01, fx, fz, packedLight);
        quad(vc, matrix, c01, c11, c10, c00, -fx, -fz, packedLight);
    }

    private static void quad(VertexConsumer vc, Matrix4f matrix,
                             float[] a, float[] b, float[] c, float[] d,
                             float nx, float nz, int light) {
        vertex(vc, matrix, a, 0.0F, 1.0F, nx, nz, light);
        vertex(vc, matrix, b, 1.0F, 1.0F, nx, nz, light);
        vertex(vc, matrix, c, 1.0F, 0.0F, nx, nz, light);
        vertex(vc, matrix, d, 0.0F, 0.0F, nx, nz, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f matrix,
                               float[] p, float u, float v, float nx, float nz, int light) {
        vc.addVertex(matrix, p[0], p[1], p[2])
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(nx, 0.0F, nz);
    }
}
