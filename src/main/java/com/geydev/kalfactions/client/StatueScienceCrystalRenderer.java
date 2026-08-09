package com.geydev.kalfactions.client;

import com.geydev.kalfactions.block.StatueScienceBlock;
import com.geydev.kalfactions.block.StatueScienceBlockEntity;
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

public final class StatueScienceCrystalRenderer implements BlockEntityRenderer<StatueScienceBlockEntity> {
    private static final float BOB_AMPLITUDE = 0.06F;
    private static final float BOB_SPEED = 0.10F;
    private static final float SPIN_DEGREES_PER_TICK = 0.65F;
    private static final double CRYSTAL_PIVOT_X = 0.5D;
    private static final double CRYSTAL_PIVOT_Z = 1.40D / 16.0D;

    private final BlockRenderDispatcher blockRenderer;

    public StatueScienceCrystalRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public AABB getRenderBoundingBox(StatueScienceBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(0.75D);
    }

    @Override
    public void render(
            StatueScienceBlockEntity blockEntity,
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

        float time = level.getGameTime() % 24000L + partialTick;
        float bob = (Mth.sin(time * BOB_SPEED) * 0.5F + 0.5F) * BOB_AMPLITUDE;
        float spin = time * SPIN_DEGREES_PER_TICK;
        Direction facing = blockEntity.getBlockState().getValue(StatueScienceBlock.FACING);
        float facingRotation = switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
        BakedModel model = Minecraft.getInstance().getModelManager()
                .getModel(StatueScienceModels.FLOATING_CRYSTAL);

        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(facingRotation));
        pose.translate(-0.5D, 0.0D, -0.5D);
        pose.translate(CRYSTAL_PIVOT_X, bob, CRYSTAL_PIVOT_Z);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.translate(-CRYSTAL_PIVOT_X, 0.0D, -CRYSTAL_PIVOT_Z);

        VertexConsumer consumer = buffer.getBuffer(RenderType.cutout());
        blockRenderer.getModelRenderer().renderModel(
                pose.last(),
                consumer,
                null,
                model,
                1.0F,
                1.0F,
                1.0F,
                LightTexture.FULL_BRIGHT,
                packedOverlay
        );
        pose.popPose();
    }
}
