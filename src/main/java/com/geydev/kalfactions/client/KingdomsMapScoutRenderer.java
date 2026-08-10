package com.geydev.kalfactions.client;

import com.geydev.kalfactions.ClientBridge;
import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.entity.MapScoutEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class KingdomsMapScoutRenderer extends MobRenderer<MapScoutEntity, PlayerModel<MapScoutEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/entity/map_scout.png");

    public KingdomsMapScoutRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(MapScoutEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(MapScoutEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return !ClientBridge.scoutBusy() && super.shouldRender(entity, frustum, camX, camY, camZ);
    }
}
