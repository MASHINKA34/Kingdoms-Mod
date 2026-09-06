package com.geydev.kalfactions.raid;

import static org.junit.jupiter.api.Assertions.*;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class RaidTargetTest {
    @Test
    void outpostRemainsRaidTargetAfterWarningUntilDetached() {
        FactionManager manager = new FactionManager();
        var created = manager.createFaction(UUID.randomUUID(), "Raid Target", new ClaimKey(Level.OVERWORLD, 0, 0), 1);
        assertTrue(created.successful());
        Faction faction = manager.getFactionById(created.factionId()).orElseThrow();
        ClaimKey target = new ClaimKey(Level.OVERWORLD, 100, 100);
        BlockPos core = new BlockPos(1608, 70, 1608);
        assertTrue(manager.attachOutpost(faction.id(), core, Set.of(target)).successful());
        var outpost = faction.outposts().iterator().next();
        Raid raid = Raid.warning(UUID.randomUUID(), faction.id(), Raid.TargetType.OUTPOST,
                target, core, outpost.id(), 1);
        assertFalse(faction.hasClaim(target));
        assertTrue(RaidManager.ownsTarget(faction, raid));
        manager.detachOutpost(faction.id(), outpost.id());
        assertFalse(RaidManager.ownsTarget(faction, raid));
        assertTrue(manager.attachOutpost(faction.id(), core, Set.of(target)).successful());
        assertFalse(RaidManager.ownsTarget(faction, raid));
    }
}
