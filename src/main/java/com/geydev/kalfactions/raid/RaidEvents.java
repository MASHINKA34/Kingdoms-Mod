package com.geydev.kalfactions.raid;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.outpost.RogueOutpostManager;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class RaidEvents {
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || !isRaider(event.getEntity())) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker != null && isRaider(attacker)) {
            event.setCanceled(true);
        }
    }

    private static boolean isRaider(Entity entity) {
        return entity != null
            && (entity.getTags().contains(RaidManager.RAIDER_TAG)
                || entity.getTags().contains(RogueOutpostManager.GARRISON_TAG));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (event.isCanceled() || entity.getServer() == null) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        if (entity.getTags().contains(RogueOutpostManager.GARRISON_TAG)
            && data.hasUUID(RogueOutpostManager.OUTPOST_ID_DATA)) {
            RogueOutpostManager.get(entity.getServer()).onGarrisonKilled(entity.getUUID());
            return;
        }
        if (entity.getTags().contains(RaidManager.RAIDER_TAG)
            && data.hasUUID(RaidManager.RAID_ID_DATA)) {
            UUID raidId = data.getUUID(RaidManager.RAID_ID_DATA);
            RaidManager.get(entity.getServer()).onRaiderDeath(entity.getServer(), raidId, entity.getUUID());
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!event.loadedFromDisk()
            || !entity.getTags().contains(RaidManager.RAIDER_TAG)
            || !entity.getPersistentData().hasUUID(RaidManager.RAID_ID_DATA)
            || entity.getServer() == null) {
            return;
        }
        UUID raidId = entity.getPersistentData().getUUID(RaidManager.RAID_ID_DATA);
        entity.getServer().execute(() -> {
            if (entity.isAddedToLevel()
                && !RaidManager.get(entity.getServer()).ownsRaider(raidId, entity.getUUID())) {
                entity.discard();
            }
        });
    }

    private RaidEvents() {
    }
}
