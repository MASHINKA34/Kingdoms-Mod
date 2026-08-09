package com.geydev.kalfactions.client;

import com.geydev.kalfactions.scout.ScoutPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ScoutAreaSelection {
    private static volatile ScoutPayloads.PackageEntry pending;

    public static void begin(ScoutPayloads.PackageEntry entry) {
        pending = entry;
    }

    public static void clear() {
        pending = null;
    }

    public static ScoutPayloads.PackageEntry pending() {
        return pending;
    }

    public static boolean active() {
        return pending != null;
    }

    public static void confirm(int centerChunkX, int centerChunkZ) {
        ScoutPayloads.PackageEntry entry = pending;
        if (entry == null) {
            return;
        }
        pending = null;
        ResourceKey<Level> dimension = Minecraft.getInstance().level == null
                ? Level.OVERWORLD
                : Minecraft.getInstance().level.dimension();
        PacketDistributor.sendToServer(new ScoutPayloads.C2SScoutOrder(
                entry.ordinal(),
                dimension.location(),
                centerChunkX,
                centerChunkZ
        ));
    }

    private ScoutAreaSelection() {
    }
}
