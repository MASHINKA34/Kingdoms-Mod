package com.geydev.kalfactions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws a real miniature of the statue in the creative menu and in hand. */
public final class WarGodStatueItemRenderer extends BlockEntityWithoutLevelRenderer {
    public WarGodStatueItemRenderer() {
        super(
                Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels()
        );
    }

    @Override
    public void renderByItem(
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack pose,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        float scale = switch (displayContext) {
            case GUI -> 0.072F;
            case GROUND -> 0.045F;
            case FIXED -> 0.065F;
            default -> 0.052F;
        };
        float time = Minecraft.getInstance().level == null
                ? 0.0F
                : Minecraft.getInstance().level.getGameTime();

        pose.pushPose();
        pose.translate(0.5F, 0.05F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(displayContext == ItemDisplayContext.GUI ? 205.0F : 180.0F));
        pose.scale(scale, scale, scale);
        WarGodStatueRenderer.renderGeometry(pose, buffer, packedLight, packedOverlay, time);
        pose.popPose();
    }
}
