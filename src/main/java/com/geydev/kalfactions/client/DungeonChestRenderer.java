package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.DungeonChestBlock;
import com.geydev.kalfactions.block.DungeonChestBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public final class DungeonChestRenderer implements BlockEntityRenderer<DungeonChestBlockEntity> {
    public static final Material MATERIAL = new Material(
            Sheets.CHEST_SHEET,
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "entity/chest/dungeon_chest")
    );

    private final ModelPart lid;
    private final ModelPart bottom;
    private final ModelPart lock;

    public DungeonChestRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(ModelLayers.CHEST);
        this.bottom = root.getChild("bottom");
        this.lid = root.getChild("lid");
        this.lock = root.getChild("lock");
    }

    @Override
    public void render(
            DungeonChestBlockEntity chest,
            float partialTick,
            PoseStack pose,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        BlockState state = chest.getBlockState();
        Direction facing = state.hasProperty(DungeonChestBlock.FACING)
                ? state.getValue(DungeonChestBlock.FACING)
                : Direction.NORTH;
        renderChest(
                pose,
                buffer,
                packedLight,
                packedOverlay,
                facing,
                chest.getOpenNess(partialTick),
                lid,
                lock,
                bottom
        );
    }

    public static void renderChest(
            PoseStack pose,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay,
            Direction facing,
            float openness,
            ModelPart lid,
            ModelPart lock,
            ModelPart bottom
    ) {
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5F, -0.5F, -0.5F);

        float eased = 1.0F - openness;
        eased = 1.0F - eased * eased * eased;
        lid.xRot = -(eased * ((float) Math.PI / 2.0F));
        lock.xRot = lid.xRot;

        VertexConsumer consumer = MATERIAL.buffer(buffer, RenderType::entityCutout);
        lid.render(pose, consumer, packedLight, packedOverlay);
        lock.render(pose, consumer, packedLight, packedOverlay);
        bottom.render(pose, consumer, packedLight, packedOverlay);
        pose.popPose();
    }
}
