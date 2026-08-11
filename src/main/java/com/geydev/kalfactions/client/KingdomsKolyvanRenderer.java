package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.entity.KolyvanEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public final class KingdomsKolyvanRenderer extends EntityRenderer<KolyvanEntity> {
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/entity/kolyvan.png");

    private static final float HEIGHT = 2.6F;
    private static final float WIDTH = HEIGHT * 559.0F / 592.0F;

    public KingdomsKolyvanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(
            KolyvanEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack pose,
            MultiBufferSource buffer,
            int packedLight
    ) {
        Minecraft.getInstance().getTextureManager().getTexture(TEXTURE).setFilter(false, false);
        Camera camera = this.entityRenderDispatcher.camera;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        Matrix4f matrix = pose.last().pose();
        float half = WIDTH * 0.5F;
        vertex(matrix, consumer, -half, 0.0F, 0.0F, 1.0F);
        vertex(matrix, consumer, half, 0.0F, 1.0F, 1.0F);
        vertex(matrix, consumer, half, HEIGHT, 1.0F, 0.0F);
        vertex(matrix, consumer, -half, HEIGHT, 0.0F, 0.0F);
        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(KolyvanEntity entity) {
        return TEXTURE;
    }

    private static void vertex(Matrix4f matrix, VertexConsumer consumer, float x, float y, float u, float v) {
        consumer.addVertex(matrix, x, y, 0.0F)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }
}
