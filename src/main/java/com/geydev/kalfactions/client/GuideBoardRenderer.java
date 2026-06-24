package com.geydev.kalfactions.client;

import com.geydev.kalfactions.block.GuideBoardBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

public final class GuideBoardRenderer implements BlockEntityRenderer<GuideBoardBlockEntity> {
    private static final Component LABEL = Component.translatable("block.kingdoms.guide_board.label");
    private static final int LABEL_COLOR = 0xFFFFFFFF;
    private static final int LABEL_BACKGROUND = 0x60000000;
    private final Font font;

    public GuideBoardRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    @Override
    public boolean shouldRenderOffScreen(GuideBoardBlockEntity blockEntity) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(GuideBoardBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(2.0D, 3.0D, 2.0D);
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
        pose.pushPose();
        pose.translate(0.5D, 2.85D, 0.5D);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.03F, -0.03F, 0.03F);

        Matrix4f matrix = pose.last().pose();
        float x = -font.width(LABEL) / 2.0F;
        font.drawInBatch(
                LABEL,
                x,
                0.0F,
                LABEL_COLOR,
                true,
                matrix,
                buffer,
                Font.DisplayMode.NORMAL,
                LABEL_BACKGROUND,
                LightTexture.FULL_BRIGHT
        );
        pose.popPose();
    }
}
