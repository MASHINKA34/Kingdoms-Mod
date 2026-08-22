package com.geydev.kalfactions.dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class NetherPortalRegistrationTest {
    private static final Instant LIT = Instant.parse("2026-08-22T10:00:00Z");

    @Test
    void portalChargeSurvivesRestartAndExpiresOnAbsoluteTime() throws IOException {
        Path file = stateFile();
        DimensionControlManager manager = DimensionControlManager.forTesting(file);
        assertFalse(manager.isNetherPortalCharged(LIT));
        manager.igniteNetherPortal(LIT, Duration.ofHours(48), "Operator", new BlockPos(4, 70, 8));

        DimensionControlManager restarted = DimensionControlManager.forTesting(file);
        assertTrue(restarted.isNetherPortalCharged(LIT.plus(Duration.ofHours(47))));
        assertFalse(restarted.isNetherPortalCharged(LIT.plus(Duration.ofHours(49))));
        assertEquals(new BlockPos(4, 70, 8), restarted.netherPortalAnchor().orElseThrow());
        assertEquals("Operator", restarted.netherPortalCharge().orElseThrow().ignitedBy());
    }

    @Test
    void extinguishedPortalStaysUnlitAcrossRestarts() throws IOException {
        Path file = stateFile();
        DimensionControlManager manager = DimensionControlManager.forTesting(file);
        manager.igniteNetherPortal(LIT, Duration.ofHours(48), "Operator", new BlockPos(4, 70, 8));
        assertTrue(manager.clearNetherPortalCharge());
        assertFalse(manager.clearNetherPortalCharge());

        DimensionControlManager restarted = DimensionControlManager.forTesting(file);
        assertFalse(restarted.isNetherPortalCharged(LIT));
        assertTrue(restarted.netherPortalCharge().isEmpty());
    }

    @Test
    void remainingLifetimeIsReportedWithDaysOnlyWhenItExceedsADay() {
        assertEquals("00:00:00", NetherSchedulePolicy.formatClock(Duration.ofHours(48)));
        assertEquals("01:01:01", NetherSchedulePolicy.formatClock(Duration.ofSeconds(3661)));
        assertEquals("00:00:00", NetherSchedulePolicy.formatClock(Duration.ofSeconds(-5)));
        assertEquals("02:03:04", NetherSchedulePolicy.formatClock(
                Duration.ofDays(1).plusHours(2).plusMinutes(3).plusSeconds(4)
        ));
    }

    private static Path stateFile() throws IOException {
        Path directory = Files.createTempDirectory("kingdoms-portal-charge-");
        directory.toFile().deleteOnExit();
        Path file = directory.resolve("kingdoms_dimension_control.json");
        file.toFile().deleteOnExit();
        return file;
    }
}
