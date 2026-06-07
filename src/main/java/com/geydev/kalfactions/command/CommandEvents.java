package com.geydev.kalfactions.command;

import com.geydev.kalfactions.KalFactions;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class CommandEvents {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void registerCommands(RegisterCommandsEvent event) {
        FactionCommands.register(event.getDispatcher());
        DuelCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void registerPermissions(PermissionGatherEvent.Nodes event) {
        CommandPermissions.register(event);
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        PendingFactionInvites.clear(event.getServer());
    }

    private CommandEvents() {
    }
}
