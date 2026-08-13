package com.geydev.kalfactions.client;

import com.geydev.kalfactions.client.screen.DungeonChestScreen;
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

    public static void acceptChest(DungeonPayloads.S2CDungeonChestState payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof DungeonChestScreen screen && screen.pos().equals(payload.pos())) {
                screen.acceptState(payload);
                return;
            }
            minecraft.setScreen(new DungeonChestScreen(payload));
        });
    }

    private ClientDungeonState() {
    }
}
