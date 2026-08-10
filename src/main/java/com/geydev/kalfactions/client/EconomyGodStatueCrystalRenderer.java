package com.geydev.kalfactions.client;

import com.geydev.kalfactions.block.EconomyGodStatueBlock;
import com.geydev.kalfactions.block.EconomyGodStatueBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class EconomyGodStatueCrystalRenderer
        implements BlockEntityRenderer<EconomyGodStatueBlockEntity> {
    private static final float PULSE_SPEED = 0.12F;
    private static final double PIVOT_X = 8.04D / 16.0D;
    private static final double PIVOT_Y = 3.51D / 16.0D;
    private static final double PIVOT_Z = 0.35D / 16.0D;

    private final BlockRenderDispatcher blockRenderer;

    public EconomyGodStatueCrystalRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public boolean shouldRenderOffScreen(EconomyGodStatueBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public AABB getRenderBoundingBox(EconomyGodStatueBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(0.5D);
    }

    @Override
    public void render(
            EconomyGodStatueBlockEntity blockEntity,
            float partialTick,
            PoseStack pose,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }

        float time = level.getGameTime() + partialTick;
        float pulse = 0.5F + 0.5F * Mth.sin(time * PULSE_SPEED);
        float brightness = 0.72F + 0.28F * pulse;
        float expansion = 1.01F + 0.008F * pulse;
        Direction facing = blockEntity.getBlockState().getValue(EconomyGodStatueBlock.FACING);
        float facingRotation = switch (facing) {
            case EAST -> 270.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
        BakedModel model = Minecraft.getInstance().getModelManager()
                .getModel(EconomyGodStatueModels.CRYSTAL_GLOW);

        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(facingRotation));
        pose.translate(-0.5D, 0.0D, -0.5D);
        pose.translate(PIVOT_X, PIVOT_Y, PIVOT_Z);
        pose.scale(expansion, expansion, expansion);
        pose.translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z - 0.0015D);

        VertexConsumer crystal = buffer.getBuffer(RenderType.cutout());
        blockRenderer.getModelRenderer().renderModel(
                pose.last(),
                crystal,
                null,
                model,
                brightness,
                brightness,
                brightness,
                LightTexture.FULL_BRIGHT,
                packedOverlay
        );
        renderGlow(pose, buffer, pulse);
        pose.popPose();
    }

    private static void renderGlow(PoseStack pose, MultiBufferSource buffer, float pulse) {
        VertexConsumer glow = buffer.getBuffer(RenderType.lightning());
        float centerX = (float)PIVOT_X;
        float centerY = (float)PIVOT_Y;
        float frontZ = -0.008F;
        float breathing = 0.94F + 0.08F * pulse;

        glowDiamond(
                glow,
                pose,
                centerX,
                centerY,
                frontZ,
                0.082F * breathing,
                255,
                178,
                18,
                Math.round(20.0F + 42.0F * pulse)
        );
        glowDiamond(
                glow,
                pose,
                centerX,
                centerY,
                frontZ - 0.002F,
                0.056F * breathing,
                255,
                234,
                102,
                Math.round(45.0F + 92.0F * pulse)
        );
    }

    private static void glowDiamond(
            VertexConsumer consumer,
            PoseStack pose,
            float centerX,
            float centerY,
            float z,
            float radius,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        var matrix = pose.last().pose();
        consumer.addVertex(matrix, centerX, centerY + radius, z).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, centerX + radius, centerY, z).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, centerX, centerY - radius, z).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, centerX - radius, centerY, z).setColor(red, green, blue, alpha);
    }
}
