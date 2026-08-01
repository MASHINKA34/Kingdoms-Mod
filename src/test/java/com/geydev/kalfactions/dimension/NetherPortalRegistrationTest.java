package com.geydev.kalfactions.dimension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class NetherPortalRegistrationTest {
    @Test
    void ordinaryPlayersCanNeverCreateNetherPortals() {
        assertFalse(NetherPortalRegistration.mayCreatePortal(false, true));
        assertFalse(NetherPortalRegistration.mayCreatePortal(false, false));
    }

    @Test
    void operatorPortalMayBeAnywhereButOnlyInOverworld() {
        assertTrue(NetherPortalRegistration.mayCreatePortal(true, true));
        assertFalse(NetherPortalRegistration.mayCreatePortal(true, false));
    }
}
