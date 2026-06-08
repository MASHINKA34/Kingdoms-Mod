package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.net.FactionSnapshot;
import net.minecraft.client.Minecraft;

public final class FactionScreens {
    public static void openRoot(FactionSnapshot snapshot, boolean successful, String message) {
        Minecraft.getInstance().setScreen(
                snapshot.hasFaction()
                        ? new FactionManageScreen(snapshot, successful, message)
                        : new FactionCreateScreen(snapshot, successful, message)
        );
    }

    public static void openClaims(FactionSnapshot snapshot, boolean successful, String message) {
        Minecraft.getInstance().setScreen(new FactionClaimMapScreen(snapshot, successful, message));
    }

    public static void openMembers(FactionSnapshot snapshot, boolean successful, String message) {
        Minecraft.getInstance().setScreen(new FactionMembersScreen(snapshot, successful, message));
    }

    public static void openTreasury(FactionSnapshot snapshot, boolean successful, String message) {
        Minecraft.getInstance().setScreen(new FactionTreasuryScreen(snapshot, successful, message));
    }

    private FactionScreens() {
    }
}
