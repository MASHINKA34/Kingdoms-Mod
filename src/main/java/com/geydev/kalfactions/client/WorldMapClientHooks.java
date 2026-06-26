package com.geydev.kalfactions.client;

import com.geydev.kalfactions.client.screen.WorldMapScreen;
import net.minecraft.client.Minecraft;

public final class WorldMapClientHooks {
    private WorldMapClientHooks() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new WorldMapScreen());
    }
}
