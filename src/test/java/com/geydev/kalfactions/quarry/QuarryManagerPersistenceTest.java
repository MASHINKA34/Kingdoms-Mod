package com.geydev.kalfactions.quarry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geydev.kalfactions.claim.ClaimKey;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

final class QuarryManagerPersistenceTest {
    @Test
    void ownerLevelCaptureAndStateVersionSurviveRoundTrip() {
        UUID quarryId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        BlockPos core = new BlockPos(120, 80, -240);
        CompoundTag quarry = new CompoundTag();
        quarry.putUUID("id", quarryId);
        quarry.putLong("core", core.asLong());
        ListTag chunks = new ListTag();
        chunks.add(new ClaimKey(Level.OVERWORLD, 7, -15).save());
        quarry.put("chunks", chunks);
        quarry.putUUID("owner", ownerId);
        quarry.putInt("level", 4);
        quarry.putUUID("attacker", attackerId);
        quarry.putInt("captureTicks", 2_345);
        quarry.putBoolean("capturePaused", true);
        quarry.putLong("stateVersion", 19L);
        ListTag quarries = new ListTag();
        quarries.add(quarry);
        CompoundTag root = new CompoundTag();
        root.put("quarries", quarries);

        QuarryManager manager = QuarryManager.load(root, null);
        QuarryManager.QuarryView loaded = manager.byCore(core).orElseThrow();

        assertEquals(quarryId, loaded.id());
        assertEquals(ownerId, loaded.ownerFactionId());
        assertEquals(4, loaded.level());
        assertEquals(attackerId, loaded.attackerFactionId());
        assertEquals(2_345, loaded.captureTicksRemaining());
        assertTrue(loaded.capturePaused());
        assertEquals(19L, loaded.stateVersion());
        CompoundTag saved = manager.save(new CompoundTag(), null);
        QuarryManager reloaded = QuarryManager.load(saved, null);
        assertEquals(loaded, reloaded.byCore(core).orElseThrow());
    }
}
