package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.screen.FactionListScreen;
import com.geydev.kalfactions.net.FactionPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class FactionListOpener {
    public static final KeyMapping OPEN_KEY = new KeyMapping(
            "key.kingdoms.open_factions",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.kingdoms"
    );

    public static void register(IEventBus modBus) {
        modBus.addListener(FactionListOpener::onRegisterKeys);
    }

    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_KEY);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN_KEY.consumeClick()) {
            open();
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen screen) {
            event.addListener(createInventoryButton(
                    Component.literal("K"),
                    button -> open(),
                    screen.getGuiLeft() + screen.getXSize() - 20,
                    screen.getGuiTop() - 22,
                    20,
                    20
            ));
        }
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        minecraft.setScreen(new FactionListScreen());
        PacketDistributor.sendToServer(FactionPayloads.C2SRequestFactionList.INSTANCE);
    }

    private static Button createInventoryButton(
            Component message,
            Button.OnPress onPress,
            int x,
            int y,
            int width,
            int height
    ) {
        try {
            Class<?> buttonClass = Class.forName("com.geydev.kalfactions.client.screen.KingdomsButton");
            Object button = buttonClass
                    .getMethod("create", Component.class, Button.OnPress.class, int.class, int.class, int.class, int.class)
                    .invoke(null, message, onPress, x, y, width, height);
            if (button instanceof Button widget) {
                return widget;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return Button.builder(message, onPress).bounds(x, y, width, height).build();
    }

    private FactionListOpener() {
    }
}
