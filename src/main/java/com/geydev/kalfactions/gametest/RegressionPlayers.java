package com.geydev.kalfactions.gametest;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public final class RegressionPlayers {
    public static Fixture create(ServerLevel level, BlockPos pos, int permissions) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "Regression"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            protected int getPermissionLevel() {
                return permissions;
            }

            @Override
            public void displayClientMessage(Component message, boolean actionBar) {
            }

            @Override
            public void sendSystemMessage(Component message) {
            }
        };
        List<Packet<?>> packets = new ArrayList<>();
        player.connection = new ServerGamePacketListenerImpl(level.getServer(),
                new Connection(PacketFlow.SERVERBOUND), player, cookie) {
            @Override
            public void send(Packet<?> packet) {
                packets.add(packet);
            }
        };
        player.setPos(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        return new Fixture(player, packets);
    }

    public record Fixture(ServerPlayer player, List<Packet<?>> packets) {
    }

    private RegressionPlayers() {
    }
}
