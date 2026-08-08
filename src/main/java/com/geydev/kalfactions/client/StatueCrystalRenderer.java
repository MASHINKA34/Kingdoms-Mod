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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class StatueCrystalRenderer implements BlockEntityRenderer<StatueScienceBlockEntity> {
    private static final float SPIN_DEGREES_PER_TICK = 0.8F;

    private final BlockRenderDispatcher blockRenderer;

    public StatueCrystalRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public AABB getRenderBoundingBox(StatueScienceBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(0.5D, 1.0D, 0.5D);
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
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(StatueModels.CRYSTAL_SCIENCE);
        float time = level.getGameTime() % 24000L + partialTick;
        float bob = StatueScienceBlock.crystalBobOffset(level.getGameTime(), partialTick);
        float spin = time * SPIN_DEGREES_PER_TICK;

        pose.pushPose();
        pose.translate(0.5D, bob, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.translate(-0.5D, 0.0D, -0.5D);

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
