package com.geydev.kalfactions.client;

import com.geydev.kalfactions.charon.CharonPayloads;
import com.geydev.kalfactions.client.screen.KingdomsConfirmScreen;
import com.geydev.kalfactions.command.NumismaticsEconomy;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientCharonStatueHandler {
    public static void handleOffer(CharonPayloads.S2CStatueOffer payload) {
        BlockPos anchor = payload.anchor().immutable();
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(new KingdomsConfirmScreen(
                null,
                Component.translatable("screen.kingdoms.charon_statue.offer_title"),
                Component.translatable(
                        "screen.kingdoms.charon_statue.offer_message",
                        NumismaticsEconomy.format(payload.price())
                ),
                () -> PacketDistributor.sendToServer(new CharonPayloads.C2SStatueBuy(anchor))
        )));
    }

    private ClientCharonStatueHandler() {
    }
}
