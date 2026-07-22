package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.entity.SellerTraderEntity;
import com.geydev.kalfactions.outpost.trader.SellerTraderRole;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class KingdomsSellerTraderRenderer extends MobRenderer<SellerTraderEntity, PlayerModel<SellerTraderEntity>> {
    private static final ResourceLocation PERMANENT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/entity/seller_trader.png");
    private static final ResourceLocation WANDERING_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/entity/seller_trader_wandering.png");
    private static final ResourceLocation CONTRABAND_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/entity/seller_trader_contraband.png");

    public KingdomsSellerTraderRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(SellerTraderEntity entity) {
        return textureFor(entity.traderRole());
    }

    static ResourceLocation textureFor(SellerTraderRole role) {
        return switch (role) {
            case PERMANENT -> PERMANENT_TEXTURE;
            case WANDERING -> WANDERING_TEXTURE;
            case CONTRABAND -> CONTRABAND_TEXTURE;
        };
    }
}
