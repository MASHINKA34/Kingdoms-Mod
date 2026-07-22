package com.geydev.kalfactions.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PendingFactionInvitesTest {
    @Test
    void repeatedInvitationReplacesExistingEntry() {
        PendingFactionInvites.InviteStore store = new PendingFactionInvites.InviteStore();
        UUID factionId = UUID.randomUUID();
        UUID invitedId = UUID.randomUUID();
        UUID firstInviter = UUID.randomUUID();
        UUID secondInviter = UUID.randomUUID();

        store.put(factionId, firstInviter, invitedId, 100L);
        store.put(factionId, secondInviter, invitedId, 200L);

        assertEquals(1, store.size());
        PendingFactionInvites.Invite invite = store.find(factionId, invitedId).orElseThrow();
        assertEquals(secondInviter, invite.inviterId());
        assertEquals(200L, invite.expiresAt());
    }
}
