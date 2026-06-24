package com.geydev.kalfactions.client;

import com.geydev.kalfactions.block.GuideBoardBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public final class GuideBoardRenderer implements BlockEntityRenderer<GuideBoardBlockEntity> {
    public GuideBoardRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public int getViewDistance() {
        return 24;
    }

    @Override
    public void render(
            GuideBoardBlockEntity blockEntity,
            float partialTick,
            PoseStack pose,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
    }
}
