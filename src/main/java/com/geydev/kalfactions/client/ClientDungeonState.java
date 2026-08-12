package com.geydev.kalfactions.client;

import com.geydev.kalfactions.client.screen.DungeonMapScreen;
import com.geydev.kalfactions.client.screen.DungeonScreen;
import com.geydev.kalfactions.dungeon.DungeonPayloads;
import net.minecraft.client.Minecraft;

public final class ClientDungeonState {
    public static void accept(DungeonPayloads.S2COpenDungeon payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof DungeonScreen screen && screen.dungeonId() == payload.dungeonId()) {
                screen.acceptState(payload);
                return;
            }
            if (minecraft.screen instanceof DungeonMapScreen map && map.dungeonId() == payload.dungeonId()) {
                map.acceptState(payload);
                return;
            }
            minecraft.setScreen(new DungeonScreen(payload));
        });
    }

    private ClientDungeonState() {
    }
}
