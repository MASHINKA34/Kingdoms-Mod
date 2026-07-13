package com.geydev.kalfactions.item;

import com.geydev.kalfactions.tax.LagTaxService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class FactionMeterItem extends Item {
    public FactionMeterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            LagTaxService.sendMeterData(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
