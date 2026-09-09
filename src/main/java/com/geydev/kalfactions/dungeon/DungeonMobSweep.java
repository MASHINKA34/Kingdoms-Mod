package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.protection.BannedMobs;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class DungeonMobSweep {
    private static final int SWEEP_INTERVAL_TICKS = 20;
    private static int ticksUntilSweep;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ModConfigSpec.DUNGEON_BANNED_MOBS_SWEEP.get() || --ticksUntilSweep > 0) {
            return;
        }
        ticksUntilSweep = SWEEP_INTERVAL_TICKS;
        MinecraftServer server = event.getServer();
        if (DungeonManager.get(server).isEmpty()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            sweep(level);
        }
    }

    public static int sweep(ServerLevel level) {
        List<? extends Mob> banned = level.getEntities(
                EntityTypeTest.forClass(Mob.class),
                mob -> BannedMobs.isBanned(mob.getType())
                        && DungeonProtection.isDungeon(level, mob.blockPosition())
        );
        for (Mob mob : banned) {
            mob.discard();
        }
        return banned.size();
    }

    private DungeonMobSweep() {
    }
}
