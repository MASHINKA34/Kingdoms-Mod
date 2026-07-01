package com.geydev.kalfactions.client;

import java.util.UUID;

public final class XaeroPlayerVisibility {
    public static boolean shouldHide(UUID playerId) {
        return !ClientFactionMembership.isVisibleMember(playerId);
    }

    private XaeroPlayerVisibility() {
    }
}
