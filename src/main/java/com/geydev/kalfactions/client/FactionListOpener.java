package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.screen.FactionListScreen;
import com.geydev.kalfactions.client.screen.InviteBadgeButton;
import com.geydev.kalfactions.client.screen.NetherStatusScreen;
import com.geydev.kalfactions.client.screen.NewsScreen;
import com.geydev.kalfactions.net.FactionPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen screen) {
            for (GuiEventListener listener : List.copyOf(event.getListenersList())) {
                if (isCustomNpcInventoryTab(listener.getClass().getName())) {
                    event.removeListener(listener);
                }
            }
            event.addListener(InviteBadgeButton.create(
                    Component.literal("K"),
                    button -> open(),
                    screen.getGuiLeft() + screen.getXSize() - 20,
                    screen.getGuiTop() - 22,
                    20,
                    20
            ));
            event.addListener(InviteBadgeButton.create(
                    Component.literal("Н"),
                    button -> NewsScreen.open(),
                    screen.getGuiLeft() + screen.getXSize() - 42,
                    screen.getGuiTop() - 22,
                    20,
                    20,
                    ClientNewsState::unreadNews
            ));
            event.addListener(InviteBadgeButton.create(
                    Component.literal("А"),
                    button -> NetherStatusScreen.open(),
                    screen.getGuiLeft() + screen.getXSize() - 64,
                    screen.getGuiTop() - 22,
                    20,
                    20,
                    () -> 0
            ));
        }
    }

    static boolean isCustomNpcInventoryTab(String className) {
        return className.startsWith("noppes.npcs.client.gui.player.tabs.InventoryTab");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen)) {
            return;
        }
        for (GuiEventListener listener : event.getScreen().children()) {
            if (listener instanceof AbstractWidget widget
                    && isCustomNpcInventoryTab(listener.getClass().getName())) {
                widget.visible = false;
                widget.active = false;
            }
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

    private FactionListOpener() {
    }
}
