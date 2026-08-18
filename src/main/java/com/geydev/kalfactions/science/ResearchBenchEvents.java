package com.geydev.kalfactions.science;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.net.FactionServerHooks;
import com.geydev.kalfactions.registry.ModBlocks;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class ResearchBenchEvents {
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)
                || !event.getPlacedBlock().is(ModBlocks.RESEARCH_BENCH.get())
                || player.hasPermissions(2)) {
            return;
        }
        FactionManager manager = FactionManager.get(level);
        UUID owner = manager.getFactionIdAt(ClaimKey.of(level, event.getPos())).orElse(null);
        if (owner != null && owner.equals(manager.getFactionIdForMember(player.getUUID()).orElse(null))) {
            return;
        }
        event.setCanceled(true);
        FactionServerHooks.sendNotice(
                player,
                Component.translatable("kingdoms.research_bench.not_own_territory"),
                false
        );
    }

    private ResearchBenchEvents() {
    }
}
