package com.geydev.kalfactions.music;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MusicCommands {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private static final SuggestionProvider<CommandSourceStack> TRACK_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    MusicManager.get(context.getSource().getServer()).tracks().stream()
                            .map(track -> track.name().isEmpty() ? track.hash() : track.name()),
                    builder
            );

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("music")
                .then(Commands.literal("list").executes(MusicCommands::list))
                .then(Commands.literal("delete")
                        .then(Commands.argument("track", StringArgumentType.greedyString())
                                .suggests(TRACK_SUGGESTIONS)
                                .executes(MusicCommands::delete)))
                .then(Commands.literal("blocks").executes(MusicCommands::blocks))
                .then(Commands.literal("stopall").executes(MusicCommands::stopAll))
                .then(muteBranch());
    }

    public static LiteralArgumentBuilder<CommandSourceStack> playerBranch() {
        return Commands.literal("music").then(muteBranch());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> muteBranch() {
        return Commands.literal("mute")
                .then(Commands.literal("on").executes(context -> mute(context, true)))
                .then(Commands.literal("off").executes(context -> mute(context, false)));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        List<MusicTrack> tracks = MusicManager.get(server).tracks();
        if (tracks.isEmpty()) {
            context.getSource().sendSuccess(
                    () -> Component.translatable("kingdoms.command.music.list.empty"), false);
            return Command.SINGLE_SUCCESS;
        }
        context.getSource().sendSuccess(() -> Component.translatable(
                "kingdoms.command.music.list.header",
                tracks.size(),
                MusicManager.get(server).totalBytes() / 1024L / 1024L,
                MusicLimits.maxStorageBytes() / 1024L / 1024L
        ), false);
        for (MusicTrack track : tracks) {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "kingdoms.command.music.list.entry",
                    track.name(),
                    track.hash().substring(0, 8),
                    track.size() / 1024L,
                    track.uploaderName().isEmpty() ? "-" : track.uploaderName(),
                    DATE_FORMAT.format(Instant.ofEpochMilli(track.uploadedAt()))
            ), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int delete(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        String query = StringArgumentType.getString(context, "track");
        MusicManager manager = MusicManager.get(server);
        MusicTrack target = manager.track(query)
                .or(() -> manager.trackByName(query))
                .or(() -> manager.tracks().stream()
                        .filter(track -> track.hash().startsWith(query.toLowerCase(java.util.Locale.ROOT)))
                        .findFirst())
                .orElse(null);
        if (target == null) {
            context.getSource().sendFailure(Component.translatable("kingdoms.command.music.delete.missing", query));
            return 0;
        }
        MusicService.deleteTrack(server, target.hash());
        context.getSource().sendSuccess(
                () -> Component.translatable("kingdoms.command.music.delete.done", target.name()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int blocks(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        List<MusicSpeaker> speakers = MusicManager.get(server).speakers();
        if (speakers.isEmpty()) {
            context.getSource().sendSuccess(
                    () -> Component.translatable("kingdoms.command.music.blocks.empty"), false);
            return Command.SINGLE_SUCCESS;
        }
        for (MusicSpeaker speaker : speakers) {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "kingdoms.command.music.blocks.entry",
                    speaker.dimension().location().toString(),
                    speaker.pos().getX(),
                    speaker.pos().getY(),
                    speaker.pos().getZ(),
                    speaker.trackName().isEmpty()
                            ? Component.translatable("kingdoms.command.music.blocks.no_track")
                            : Component.literal(speaker.trackName()),
                    speaker.radius(),
                    Component.translatable(speaker.playing()
                            ? "kingdoms.command.music.blocks.playing"
                            : "kingdoms.command.music.blocks.stopped")
            ), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int stopAll(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        int stopped = MusicManager.get(server).stopAll();
        MusicRadius.stopEverywhere(server);
        MusicRadius.refreshAll(server);
        context.getSource().sendSuccess(
                () -> Component.translatable("kingdoms.command.music.stopall", stopped), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int mute(CommandContext<CommandSourceStack> context, boolean muted)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PacketDistributor.sendToPlayer(player, new MusicPayloads.S2CMusicMute(muted));
        context.getSource().sendSuccess(() -> Component.translatable(
                muted ? "kingdoms.command.music.mute.on" : "kingdoms.command.music.mute.off"), false);
        return Command.SINGLE_SUCCESS;
    }

    private MusicCommands() {
    }
}
