package com.geydev.kalfactions.dimension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class NetherPortalRegistrationTest {
    @Test
    void ordinaryPlayersCanNeverCreateNetherPortals() {
        assertFalse(NetherPortalRegistration.mayCreatePortal(false, true, true));
        assertFalse(NetherPortalRegistration.mayCreatePortal(false, false, true));
    }

    @Test
    void operatorPortalMustBeNearSharedSpawnInOverworld() {
        assertTrue(NetherPortalRegistration.mayCreatePortal(true, true, true));
        assertFalse(NetherPortalRegistration.mayCreatePortal(true, true, false));
        assertTrue(NetherPortalRegistration.isNear(BlockPos.ZERO, new BlockPos(64, 0, 0)));
        assertFalse(NetherPortalRegistration.isNear(BlockPos.ZERO, new BlockPos(65, 0, 0)));
    }
}
