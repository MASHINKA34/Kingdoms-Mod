package com.geydev.kalfactions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws the closed dungeon chest in the inventory and in hand. */
public final class DungeonChestItemRenderer extends BlockEntityWithoutLevelRenderer {
    private ModelPart lid;
    private ModelPart bottom;
    private ModelPart lock;

    public DungeonChestItemRenderer() {
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
        if (lid == null) {
            ModelPart root = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.CHEST);
            bottom = root.getChild("bottom");
            lid = root.getChild("lid");
            lock = root.getChild("lock");
        }
        DungeonChestRenderer.renderChest(
                pose,
                buffer,
                packedLight,
                packedOverlay,
                Direction.SOUTH,
                0.0F,
                lid,
                lock,
                bottom
        );
    }
}
