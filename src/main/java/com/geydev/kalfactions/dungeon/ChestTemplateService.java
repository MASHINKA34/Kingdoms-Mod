package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.block.DungeonChestBlockEntity;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.menu.DungeonLootMenu;
import com.geydev.kalfactions.net.FactionServerHooks;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ChestTemplateService {
    private static final long ACTION_COOLDOWN_TICKS = 2L;
    private static final int MAX_HANDLED_REQUESTS = 256;
    private static final ConcurrentHashMap<UUID, Long> LAST_ACTION_TICK = new ConcurrentHashMap<>();
    private static final Set<UUID> HANDLED_REQUESTS = new LinkedHashSet<>();
    private static final NoticeSink CLIENT_NOTICES = FactionServerHooks::sendNotice;

    private static volatile NoticeSink notices = CLIENT_NOTICES;

    public static void clearRateLimit(UUID playerId) {
        LAST_ACTION_TICK.remove(playerId);
    }

    public static void overrideNotices(NoticeSink replacement) {
        notices = replacement == null ? CLIENT_NOTICES : replacement;
    }

    public static void resetNotices() {
        notices = CLIENT_NOTICES;
    }

    public static void reset() {
        LAST_ACTION_TICK.clear();
        synchronized (HANDLED_REQUESTS) {
            HANDLED_REQUESTS.clear();
        }
    }

    public static void handle(ServerPlayer player, DungeonPayloads.C2SChestTemplateAction payload) {
        if (!player.hasPermissions(2)) {
            notice(player, Component.translatable("kingdoms.dungeon.not_operator"), false);
            return;
        }
        if (!DungeonService.validateChest(player, payload.pos())) {
            return;
        }
        if (payload.action() == DungeonPayloads.C2SChestTemplateAction.SYNC) {
            sync(player, payload.pos());
            return;
        }
        if (!rateLimit(player) || !claimRequest(payload.requestId())) {
            return;
        }
        switch (payload.action()) {
            case DungeonPayloads.C2SChestTemplateAction.SAVE -> save(player, payload);
            case DungeonPayloads.C2SChestTemplateAction.APPLY -> apply(player, payload);
            case DungeonPayloads.C2SChestTemplateAction.APPLY_ALL -> applyAll(player, payload);
            case DungeonPayloads.C2SChestTemplateAction.RENAME -> rename(player, payload);
            case DungeonPayloads.C2SChestTemplateAction.DELETE -> delete(player, payload);
            default -> sync(player, payload.pos());
        }
    }

    private static void save(ServerPlayer player, DungeonPayloads.C2SChestTemplateAction payload) {
        DungeonChestBlockEntity chest = chestAt(player, payload.pos());
        if (chest == null) {
            return;
        }
        ChestTemplateManager.SaveResult result = store(
                player,
                ChestTemplate.capture(
                        UUID.randomUUID(),
                        payload.name(),
                        player.getGameProfile().getName(),
                        DungeonClock.now(),
                        chest
                ),
                payload.overwrite()
        );
        if (!result.successful()) {
            notice(player, reasonMessage(result.reason()), false);
            return;
        }
        notice(
                player,
                Component.translatable("kingdoms.dungeon.template_saved", result.template().name()),
                true
        );
        syncOpenScreens(player.getServer());
    }

    private static void apply(ServerPlayer player, DungeonPayloads.C2SChestTemplateAction payload) {
        DungeonChestBlockEntity chest = chestAt(player, payload.pos());
        ChestTemplate template = template(player, payload.templateId());
        if (chest == null || template == null) {
            return;
        }
        template.applyTo(chest, payload.applyCooldown());
        notice(
                player,
                Component.translatable("kingdoms.dungeon.template_applied", template.name()),
                true
        );
    }

    private static void applyAll(ServerPlayer player, DungeonPayloads.C2SChestTemplateAction payload) {
        ChestTemplate template = template(player, payload.templateId());
        if (template == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        DungeonManager.DungeonView dungeon = DungeonManager.get(level)
                .dungeonAt(ClaimKey.of(level, payload.pos()))
                .orElse(null);
        if (dungeon == null) {
            notice(player, Component.translatable("kingdoms.dungeon.template_no_dungeon"), false);
            return;
        }
        if (!ChestTemplateApplyTicker.enqueue(player, dungeon, template, payload.applyCooldown())) {
            notice(player, Component.translatable("kingdoms.dungeon.template_busy"), false);
            return;
        }
        notice(
                player,
                Component.translatable(
                        "kingdoms.dungeon.template_apply_all_started",
                        dungeon.name(),
                        dungeon.chunks().size()
                ),
                true
        );
    }

    private static void rename(ServerPlayer player, DungeonPayloads.C2SChestTemplateAction payload) {
        ChestTemplate template = template(player, payload.templateId());
        if (template == null) {
            return;
        }
        ChestTemplateManager manager = ChestTemplateManager.get(player.serverLevel());
        ChestTemplateManager.Reason reason = manager.rename(template.id(), payload.name());
        if (reason != ChestTemplateManager.Reason.OK) {
            notice(player, reasonMessage(reason), false);
            return;
        }
        notice(
                player,
                Component.translatable(
                        "kingdoms.dungeon.template_renamed",
                        template.name(),
                        manager.byId(template.id()).map(ChestTemplate::name).orElse(payload.name())
                ),
                true
        );
        syncOpenScreens(player.getServer());
    }

    private static void delete(ServerPlayer player, DungeonPayloads.C2SChestTemplateAction payload) {
        ChestTemplate template = template(player, payload.templateId());
        if (template == null) {
            return;
        }
        ChestTemplateManager.get(player.serverLevel()).delete(template.id());
        notice(
                player,
                Component.translatable("kingdoms.dungeon.template_deleted", template.name()),
                true
        );
        syncOpenScreens(player.getServer());
    }

    public static ChestTemplateManager.SaveResult store(
            ServerPlayer player,
            ChestTemplate template,
            boolean overwrite
    ) {
        return ChestTemplateManager.get(player.serverLevel()).put(
                template,
                overwrite,
                ChestTemplateManager.Limits.fromConfig(),
                player.server.registryAccess()
        );
    }

    public static void sync(ServerPlayer player, BlockPos pos) {
        List<DungeonPayloads.ChestTemplateView> views = views(ChestTemplateManager.get(player.serverLevel()).all());
        PacketDistributor.sendToPlayer(player, new DungeonPayloads.S2CChestTemplates(pos, views));
    }

    public static void syncOpenScreens(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer.containerMenu instanceof DungeonLootMenu menu) {
                sync(viewer, menu.pos());
            }
        }
    }

    private static List<DungeonPayloads.ChestTemplateView> views(List<ChestTemplate> templates) {
        List<DungeonPayloads.ChestTemplateView> views = new ArrayList<>(templates.size());
        for (ChestTemplate template : templates) {
            List<DungeonPayloads.ChestTemplateSlot> slots = new ArrayList<>(ChestTemplate.SIZE);
            for (int slot = 0; slot < ChestTemplate.SIZE; slot++) {
                ChestTemplate.Entry entry = template.entry(slot);
                if (entry.stack().isEmpty()) {
                    continue;
                }
                slots.add(new DungeonPayloads.ChestTemplateSlot(
                        slot,
                        entry.stack(),
                        entry.chance(),
                        entry.minCount(),
                        entry.maxCount()
                ));
            }
            views.add(new DungeonPayloads.ChestTemplateView(
                    template.id(),
                    template.name(),
                    template.author(),
                    template.createdAt(),
                    template.cooldownHours(),
                    slots
            ));
        }
        return views;
    }

    public static Component reasonMessage(ChestTemplateManager.Reason reason) {
        return switch (reason) {
            case OK -> Component.empty();
            case NOT_FOUND -> Component.translatable("kingdoms.dungeon.template_not_found");
            case NAME_EMPTY -> Component.translatable("kingdoms.dungeon.template_name_empty");
            case NAME_TAKEN -> Component.translatable("kingdoms.dungeon.template_name_taken");
            case TEMPLATE_EMPTY -> Component.translatable("kingdoms.dungeon.template_empty");
            case TOO_MANY -> Component.translatable("kingdoms.dungeon.template_too_many");
            case TOO_LARGE -> Component.translatable("kingdoms.dungeon.template_too_large");
        };
    }

    private static ChestTemplate template(ServerPlayer player, UUID templateId) {
        ChestTemplate template = ChestTemplateManager.get(player.serverLevel()).byId(templateId).orElse(null);
        if (template == null) {
            notice(player, Component.translatable("kingdoms.dungeon.template_not_found"), false);
        }
        return template;
    }

    private static DungeonChestBlockEntity chestAt(ServerPlayer player, BlockPos pos) {
        return player.serverLevel().getBlockEntity(pos) instanceof DungeonChestBlockEntity chest ? chest : null;
    }

    public static void notice(ServerPlayer player, Component message, boolean successful) {
        notices.accept(player, message, successful);
    }

    @FunctionalInterface
    public interface NoticeSink {
        void accept(ServerPlayer player, Component message, boolean successful);
    }

    private static boolean claimRequest(UUID requestId) {
        if (requestId == null || requestId.equals(Util.NIL_UUID)) {
            return true;
        }
        synchronized (HANDLED_REQUESTS) {
            if (!HANDLED_REQUESTS.add(requestId)) {
                return false;
            }
            Iterator<UUID> iterator = HANDLED_REQUESTS.iterator();
            while (HANDLED_REQUESTS.size() > MAX_HANDLED_REQUESTS && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        return true;
    }

    private static boolean rateLimit(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long previous = LAST_ACTION_TICK.put(player.getUUID(), now);
        if (previous != null && now - previous < ACTION_COOLDOWN_TICKS) {
            notice(player, Component.translatable("kingdoms.error.action_rate_limited"), false);
            return false;
        }
        return true;
    }

    private ChestTemplateService() {
    }
}
