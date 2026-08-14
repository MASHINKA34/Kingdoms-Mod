package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.net.FactionPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class LegacyAbilityKeys {
    public static final KeyMapping MINER_VISION_KEY = new KeyMapping(
            "key.kingdoms.miner_vision",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.kingdoms"
    );

    public static void register(IEventBus modBus) {
        modBus.addListener(LegacyAbilityKeys::onRegisterKeys);
    }

    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(MINER_VISION_KEY);
    }

    public static Component minerVisionKeyName() {
        return MINER_VISION_KEY.getKey().getValue() == InputConstants.UNKNOWN.getValue()
                ? Component.translatable("key.kingdoms.unbound")
                : MINER_VISION_KEY.getTranslatedKeyMessage();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (MINER_VISION_KEY.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.getConnection() != null) {
                PacketDistributor.sendToServer(FactionPayloads.C2SToggleMinerVision.INSTANCE);
            }
        }
    }

    private LegacyAbilityKeys() {
    }
}
