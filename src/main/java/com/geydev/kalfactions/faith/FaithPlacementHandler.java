package com.geydev.kalfactions.faith;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.net.FactionServerHooks;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class FaithPlacementHandler {
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Block block = event.getPlacedBlock().getBlock();
        if (player.hasPermissions(2)) {
            return;
        }
        if (FaithGod.isGreatStatue(block)) {
            event.setCanceled(true);
            FactionServerHooks.sendNotice(
                    player, Component.translatable("kingdoms.faith.notice.great_statue_operator_only"), false);
            return;
        }
        if (!FaithGod.isSmallStatue(block)) {
            return;
        }
        FactionManager manager = FactionManager.get(level);
        UUID own = manager.getFactionIdForMember(player.getUUID()).orElse(null);
        UUID owner = manager.getFactionIdAt(ClaimKey.of(level, event.getPos())).orElse(null);
        if (own == null || !own.equals(owner)) {
            event.setCanceled(true);
            FactionServerHooks.sendNotice(
                    player, Component.translatable("kingdoms.faith.notice.small_statue_own_claim"), false);
        }
    }

    private FaithPlacementHandler() {
    }
}
