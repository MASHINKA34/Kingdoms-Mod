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
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.Map;
import org.joml.Matrix4f;

public final class WorldMapRenderer implements BlockEntityRenderer<WorldMapBlockEntity> {
    private static final ResourceLocation PLACEHOLDER =
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

        float shift = (lx + lz < 0.0F) ? -1.0F : 0.0F;
        float leftMin = -WorldMapBlock.HALF_WIDTH + shift;
        float leftMax = WorldMapBlock.HALF_WIDTH + 1.0F + shift;
        float yMin = 0.0F;
        float yMax = WorldMapBlock.HEIGHT;

        float[] c00 = {baseX + lx * leftMin, yMin, baseZ + lz * leftMin};
        float[] c10 = {baseX + lx * leftMax, yMin, baseZ + lz * leftMax};
        float[] c11 = {baseX + lx * leftMax, yMax, baseZ + lz * leftMax};
        float[] c01 = {baseX + lx * leftMin, yMax, baseZ + lz * leftMin};

        ResourceLocation texture = ClientWorldMapStore.texture();
        if (texture == null) {
            ClientWorldMapStore.requestIfNeeded(blockEntity.getBlockPos());
            texture = PLACEHOLDER;
        }

        VertexConsumer vc = buffer.getBuffer(RenderType.entitySolid(texture));
        Matrix4f matrix = pose.last().pose();

        quad(vc, matrix, c00, c10, c11, c01, fx, fz, packedLight);
        quad(vc, matrix, c01, c11, c10, c00, -fx, -fz, packedLight);

        renderFactions(blockEntity, buffer, matrix, baseX, baseZ, lx, lz, leftMin, leftMax, yMin, yMax, fx, fz);
        renderTracks(blockEntity, buffer, matrix, baseX, baseZ, lx, lz, leftMin, leftMax, yMin, yMax, fx, fz);
    }

    private static final int TRACK_COLOR = 0xFF4FA8;

    private static void renderTracks(WorldMapBlockEntity blockEntity, MultiBufferSource buffer, Matrix4f matrix,
                                     float baseX, float baseZ, float lx, float lz,
                                     float leftMin, float leftMax, float yMin, float yMax, float fx, float fz) {
        int regionBlocks = ClientWorldMapStore.regionBlocks();
        if (regionBlocks <= 0 || blockEntity.getLevel() == null) {
            return;
        }
        float[] segments = ClientWorldMapTracks.segments(blockEntity.getLevel().dimension());
        if (segments.length < 4) {
            return;
        }
        double minX = ClientWorldMapStore.centerX() - regionBlocks / 2.0;
        double minZ = ClientWorldMapStore.centerZ() - regionBlocks / 2.0;
        float ox = baseX + fx * 0.02F;
        float oz = baseZ + fz * 0.02F;
        VertexConsumer vc = buffer.getBuffer(RenderType.debugQuads());
        for (int i = 0; i + 3 < segments.length; i += 4) {
            double uA = (segments[i] - minX) / regionBlocks;
            double vA = (segments[i + 1] - minZ) / regionBlocks;
            double uB = (segments[i + 2] - minX) / regionBlocks;
            double vB = (segments[i + 3] - minZ) / regionBlocks;
            if ((uA < 0.0 && uB < 0.0) || (uA > 1.0 && uB > 1.0)
                    || (vA < 0.0 && vB < 0.0) || (vA > 1.0 && vB > 1.0)) {
                continue;
            }
            lineQuad(vc, matrix, ox, oz, lx, lz, leftMin, leftMax, yMin, yMax, fx, fz, uA, vA, uB, vB, TRACK_COLOR);
        }
    }

    private static void lineQuad(VertexConsumer vc, Matrix4f matrix, float baseX, float baseZ, float lx, float lz,
                                 float leftMin, float leftMax, float yMin, float yMax, float fx, float fz,
                                 double uA, double vA, double uB, double vB, int rgb) {
        float[] a = facePoint(baseX, baseZ, lx, lz, leftMin, leftMax, yMin, yMax, clamp01(uA), clamp01(vA));
        float[] b = facePoint(baseX, baseZ, lx, lz, leftMin, leftMax, yMin, yMax, clamp01(uB), clamp01(vB));
        float dx = b[0] - a[0];
        float dy = b[1] - a[1];
        float dz = b[2] - a[2];
        float px = dy * fz;
        float py = dz * fx - dx * fz;
        float pz = -dy * fx;
        float len = (float) Math.sqrt(px * px + py * py + pz * pz);
        if (len < 1.0E-5F) {
            return;
        }
        float scale = 0.035F / len;
        px *= scale;
        py *= scale;
        pz *= scale;
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b2 = rgb & 255;
        int a2 = 0xE6;
        colorVertex(vc, matrix, new float[] {a[0] + px, a[1] + py, a[2] + pz}, r, g, b2, a2);
        colorVertex(vc, matrix, new float[] {b[0] + px, b[1] + py, b[2] + pz}, r, g, b2, a2);
        colorVertex(vc, matrix, new float[] {b[0] - px, b[1] - py, b[2] - pz}, r, g, b2, a2);
        colorVertex(vc, matrix, new float[] {a[0] - px, a[1] - py, a[2] - pz}, r, g, b2, a2);
    }

    private static void renderFactions(WorldMapBlockEntity blockEntity, MultiBufferSource buffer, Matrix4f matrix,
                                       float baseX, float baseZ, float lx, float lz,
                                       float leftMin, float leftMax, float yMin, float yMax, float fx, float fz) {
        int regionBlocks = ClientWorldMapStore.regionBlocks();
        if (regionBlocks <= 0 || blockEntity.getLevel() == null) {
            return;
        }
        ResourceKey<Level> dimension = blockEntity.getLevel().dimension();
        Map<Long, ClientClaimStore.ClaimInfo> claims = ClientClaimStore.claims(dimension);
        if (claims.isEmpty()) {
            return;
        }
        double minX = ClientWorldMapStore.centerX() - regionBlocks / 2.0;
        double minZ = ClientWorldMapStore.centerZ() - regionBlocks / 2.0;
        float ox = baseX + fx * 0.01F;
        float oz = baseZ + fz * 0.01F;
        VertexConsumer vc = buffer.getBuffer(RenderType.debugQuads());
        for (Map.Entry<Long, ClientClaimStore.ClaimInfo> entry : claims.entrySet()) {
            ChunkPos pos = new ChunkPos(entry.getKey());
            double uMin = (pos.x * 16 - minX) / regionBlocks;
            double uMax = (pos.x * 16 + 16 - minX) / regionBlocks;
            double vMin = (pos.z * 16 - minZ) / regionBlocks;
            double vMax = (pos.z * 16 + 16 - minZ) / regionBlocks;
            if (uMax <= 0.0 || uMin >= 1.0 || vMax <= 0.0 || vMin >= 1.0) {
                continue;
            }
            claimQuad(vc, matrix, ox, oz, lx, lz, leftMin, leftMax, yMin, yMax,
                    clamp01(uMin), clamp01(uMax), clamp01(vMin), clamp01(vMax), entry.getValue().color());
        }
    }

    private static void claimQuad(VertexConsumer vc, Matrix4f matrix, float baseX, float baseZ, float lx, float lz,
                                  float leftMin, float leftMax, float yMin, float yMax,
                                  double uMin, double uMax, double vMin, double vMax, int rgb) {
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;
        int a = 0x99;
        colorVertex(vc, matrix, facePoint(baseX, baseZ, lx, lz, leftMin, leftMax, yMin, yMax, uMin, vMin), r, g, b, a);
        colorVertex(vc, matrix, facePoint(baseX, baseZ, lx, lz, leftMin, leftMax, yMin, yMax, uMax, vMin), r, g, b, a);
        colorVertex(vc, matrix, facePoint(baseX, baseZ, lx, lz, leftMin, leftMax, yMin, yMax, uMax, vMax), r, g, b, a);
        colorVertex(vc, matrix, facePoint(baseX, baseZ, lx, lz, leftMin, leftMax, yMin, yMax, uMin, vMax), r, g, b, a);
    }

    private static float[] facePoint(float baseX, float baseZ, float lx, float lz,
                                     float leftMin, float leftMax, float yMin, float yMax, double u, double v) {
        float leftCoord = leftMin + (float) u * (leftMax - leftMin);
        float x = baseX + lx * leftCoord;
        float z = baseZ + lz * leftCoord;
        float y = yMax - (float) v * (yMax - yMin);
        return new float[] {x, y, z};
    }

    private static void colorVertex(VertexConsumer vc, Matrix4f matrix, float[] p, int r, int g, int b, int a) {
        vc.addVertex(matrix, p[0], p[1], p[2]).setColor(r, g, b, a);
    }

    private static double clamp01(double value) {
        return value < 0.0 ? 0.0 : (value > 1.0 ? 1.0 : value);
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
