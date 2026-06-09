package com.geydev.kalfactions.command;

import com.geydev.kalfactions.KalFactions;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

public final class CommandPermissions {
    public static final PermissionNode<Boolean> FACTION =
        node("command.faction", "kingdoms.permission.command.faction");
    public static final PermissionNode<Boolean> CREATE =
        node("command.faction.create", "kingdoms.permission.command.faction.create");
    public static final PermissionNode<Boolean> MEMBERS =
        node("command.faction.members", "kingdoms.permission.command.faction.members");
    public static final PermissionNode<Boolean> CLAIMS =
        node("command.faction.claims", "kingdoms.permission.command.faction.claims");
    public static final PermissionNode<Boolean> TREASURY =
        node("command.faction.treasury", "kingdoms.permission.command.faction.treasury");
    public static final PermissionNode<Boolean> WITHDRAW =
        node("command.faction.withdraw", "kingdoms.permission.command.faction.withdraw");
    public static final PermissionNode<Boolean> SETTINGS =
        node("command.faction.settings", "kingdoms.permission.command.faction.settings");
    public static final PermissionNode<Boolean> INFO =
        node("command.faction.info", "kingdoms.permission.command.faction.info");
    public static final PermissionNode<Boolean> DUEL =
        node("command.duel", "kingdoms.permission.command.duel");

    private static final List<PermissionNode<?>> ALL = List.of(
        FACTION,
        CREATE,
        MEMBERS,
        CLAIMS,
        TREASURY,
        WITHDRAW,
        SETTINGS,
        INFO,
        DUEL
    );

    public static void register(PermissionGatherEvent.Nodes event) {
        event.addNodes(ALL);
    }

    public static boolean has(CommandSourceStack source, PermissionNode<Boolean> node) {
        ServerPlayer player = source.getPlayer();
        return player != null && PermissionAPI.getPermission(player, node);
    }

    private static PermissionNode<Boolean> node(String path, String descriptionKey) {
        PermissionNode<Boolean> node = new PermissionNode<>(
            KalFactions.MOD_ID,
            path,
            PermissionTypes.BOOLEAN,
            (player, playerId, contexts) -> true
        );
        node.setInformation(
            Component.translatable("kingdoms.permission.title", path),
            Component.translatable(descriptionKey)
        );
        return node;
    }

    private CommandPermissions() {
    }
}
