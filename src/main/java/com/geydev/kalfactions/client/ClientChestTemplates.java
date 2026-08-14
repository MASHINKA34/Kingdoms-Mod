package com.geydev.kalfactions.client;

import com.geydev.kalfactions.client.screen.DungeonLootScreen;
import com.geydev.kalfactions.dungeon.DungeonPayloads;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class ClientChestTemplates {
    private static List<DungeonPayloads.ChestTemplateView> templates = List.of();
    private static BlockPos pos;

    public static void accept(DungeonPayloads.S2CChestTemplates payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            templates = payload.templates();
            pos = payload.pos();
            if (minecraft.screen instanceof DungeonLootScreen screen) {
                screen.acceptTemplates();
            }
        });
    }

    public static List<DungeonPayloads.ChestTemplateView> templates(BlockPos requested) {
        return pos != null && pos.equals(requested) ? templates : List.of();
    }

    public static void clear() {
        templates = List.of();
        pos = null;
    }

    private ClientChestTemplates() {
    }
}
