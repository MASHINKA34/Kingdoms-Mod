package com.geydev.kalfactions.command;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.net.FactionServerHooks;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class InviteReminderEvents {
    private static final int REMINDER_INTERVAL_TICKS = 20 * 60 * 5;

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FactionServerHooks.pushInviteBadge(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() == 0 || server.getTickCount() % REMINDER_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            remind(server, player);
        }
    }

    private static void remind(MinecraftServer server, ServerPlayer player) {
        FactionServerHooks.pushInviteBadge(player);
        FactionManager manager = FactionManager.get(player.serverLevel());
        for (PendingFactionInvites.Invite invite : PendingFactionInvites.allFor(server, player.getUUID())) {
            Faction faction = manager.getFactionById(invite.factionId()).orElse(null);
            if (faction != null) {
                FactionServerHooks.sendNotice(
                        player,
                        Component.translatable("kingdoms.invite.reminder.faction", faction.name()),
                        true
                );
                return;
            }
        }
        Faction ownFaction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (ownFaction == null || !ownFaction.ownerId().equals(player.getUUID())) {
            return;
        }
        for (PendingAllianceRequests.Request request : PendingAllianceRequests.allFor(server, ownFaction.id())) {
            Faction from = manager.getFactionById(request.fromFactionId()).orElse(null);
            if (from != null) {
                FactionServerHooks.sendNotice(
                        player,
                        Component.translatable("kingdoms.invite.reminder.alliance", from.name()),
                        true
                );
                return;
            }
        }
    }

    private InviteReminderEvents() {
    }
}
