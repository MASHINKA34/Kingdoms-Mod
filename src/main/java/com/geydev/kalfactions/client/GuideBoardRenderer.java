package com.geydev.kalfactions.client;

import com.geydev.kalfactions.block.GuideBoardBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

public final class GuideBoardRenderer implements BlockEntityRenderer<GuideBoardBlockEntity> {
    private static final Component LABEL = Component.translatable("block.kingdoms.guide_board");
    private static final int LABEL_COLOR = 0xFFF3D58B;

    private final Font font;

    public GuideBoardRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
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
        Minecraft minecraft = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(0.5D, 2.35D, 0.5D);
        pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f matrix = pose.last().pose();
        float x = -font.width(LABEL) / 2.0F;
        int background = (int) (minecraft.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        font.drawInBatch(LABEL, x, 0.0F, 0x20FFFFFF, false, matrix, buffer,
                Font.DisplayMode.SEE_THROUGH, background, packedLight);
        font.drawInBatch(LABEL, x, 0.0F, LABEL_COLOR, false, matrix, buffer,
                Font.DisplayMode.NORMAL, 0, packedLight);
        pose.popPose();
    }
}
