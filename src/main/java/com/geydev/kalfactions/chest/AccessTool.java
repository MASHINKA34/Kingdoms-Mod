package com.geydev.kalfactions.chest;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.net.FactionServerHooks;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * "Management Key": right-clicking a container inside the player's own territory
 * opens the access-mode screen for that container.
 *
 * <p>The protection handler invokes {@link #handleProtectedUse} before vanilla opens
 * the container; {@link #useOn} is a fallback for when no other handler intercepts.</p>
 */
public class AccessTool extends Item {
    public AccessTool(Properties properties) {
        super(properties);
    }

    public static ToolResult handleProtectedUse(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos,
            InteractionHand hand
    ) {
        if (!isContainer(level, pos)) {
            return ToolResult.PASS;
        }
        Prepared prepared = prepare(player, level, pos);
        if (prepared.error != null) {
            feedback(player, prepared.error);
            return ToolResult.CONSUME;
        }
        FactionServerHooks.sendChestAccessState(player, level, pos, Component.empty(), true);
        return ToolResult.CONSUME;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)
                || !(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }
        return switch (handleProtectedUse(player, level, context.getClickedPos(), context.getHand())) {
            case PASS -> InteractionResult.PASS;
            case CONSUME -> InteractionResult.SUCCESS;
            case DENY -> InteractionResult.FAIL;
        };
    }

    public static WhitelistResult setMode(
            ServerPlayer actor,
            ServerLevel level,
            BlockPos pos,
            ChestAccessMode mode
    ) {
        Prepared prepared = prepare(actor, level, pos);
        if (prepared.error != null) {
            return WhitelistResult.failure(prepared.error);
        }
        ChestAccess existing = prepared.existing;
        ChestAccess updated = existing == null
                ? new ChestAccess(prepared.key, prepared.factionId, actor.getUUID(), mode)
                : existing.withMode(mode);
        if (!FactionManager.get(level).setChestAccess(updated).successful()) {
            return WhitelistResult.failure("kingdoms.chest.update_failed");
        }
        return WhitelistResult.success(
                Component.translatable("kingdoms.access_tool.set", Component.translatable(modeKey(mode)))
        );
    }

    public static AccessState stateFor(ServerPlayer actor, ServerLevel level, BlockPos pos) {
        Prepared prepared = prepare(actor, level, pos);
        if (prepared.error != null) {
            return null;
        }
        ChestAccess existing = prepared.existing;
        ChestAccessMode mode = existing == null ? ChestAccessMode.FACTION : existing.mode();
        List<StateEntry> whitelist = new ArrayList<>();
        if (existing != null) {
            for (UUID id : existing.whitelistedPlayers()) {
                whitelist.add(new StateEntry(id, resolveName(level, id)));
            }
        }
        return new AccessState(prepared.factionId, mode, List.copyOf(whitelist));
    }

    /**
     * Adds a specific player to the looked-at container's whitelist. If the container has no
     * access rule yet, one is created in {@link ChestAccessMode#WHITELIST}. Server-authoritative:
     * only members of the faction that owns the claim may edit it.
     */
    public static WhitelistResult addWhitelistPlayer(
            ServerPlayer actor,
            ServerLevel level,
            BlockPos pos,
            UUID targetId,
            String targetName
    ) {
        Prepared prepared = prepare(actor, level, pos);
        if (prepared.error != null) {
            return WhitelistResult.failure(prepared.error);
        }
        if (targetId.equals(actor.getUUID())) {
            return WhitelistResult.failure("kingdoms.chest.self");
        }

        ChestAccess existing = prepared.existing;
        ChestAccess updated = existing == null
                ? new ChestAccess(prepared.key, prepared.factionId, actor.getUUID(), ChestAccessMode.WHITELIST, Set.of(targetId))
                : existing.withWhitelistedPlayer(targetId);
        if (!FactionManager.get(level).setChestAccess(updated).successful()) {
            return WhitelistResult.failure("kingdoms.chest.update_failed");
        }
        if (updated.mode() == ChestAccessMode.WHITELIST) {
            return WhitelistResult.success(Component.translatable("kingdoms.chest.added", targetName));
        }
        return WhitelistResult.success(Component.translatable("kingdoms.chest.added_inactive", targetName));
    }

    /**
     * Removes a specific player from the looked-at container's whitelist.
     */
    public static WhitelistResult removeWhitelistPlayer(
            ServerPlayer actor,
            ServerLevel level,
            BlockPos pos,
            UUID targetId,
            String targetName
    ) {
        Prepared prepared = prepare(actor, level, pos);
        if (prepared.error != null) {
            return WhitelistResult.failure(prepared.error);
        }
        if (prepared.existing == null || !prepared.existing.whitelistedPlayers().contains(targetId)) {
            return WhitelistResult.failure("kingdoms.chest.not_listed");
        }
        ChestAccess updated = prepared.existing.withoutWhitelistedPlayer(targetId);
        if (!FactionManager.get(level).setChestAccess(updated).successful()) {
            return WhitelistResult.failure("kingdoms.chest.update_failed");
        }
        return WhitelistResult.success(Component.translatable("kingdoms.chest.removed", targetName));
    }

    /**
     * Clears every specific player from the looked-at container's whitelist (faction-wide
     * access under {@link ChestAccessMode#WHITELIST} is unaffected).
     */
    public static WhitelistResult clearWhitelist(ServerPlayer actor, ServerLevel level, BlockPos pos) {
        Prepared prepared = prepare(actor, level, pos);
        if (prepared.error != null) {
            return WhitelistResult.failure(prepared.error);
        }
        ChestAccess existing = prepared.existing;
        if (existing == null || existing.whitelistedPlayers().isEmpty()) {
            return WhitelistResult.failure("kingdoms.chest.nothing_to_clear");
        }
        int removed = existing.whitelistedPlayers().size();
        ChestAccess updated = new ChestAccess(
                existing.key(),
                existing.factionId(),
                existing.ownerId(),
                existing.mode(),
                Set.of()
        );
        if (!FactionManager.get(level).setChestAccess(updated).successful()) {
            return WhitelistResult.failure("kingdoms.chest.update_failed");
        }
        return WhitelistResult.success(Component.translatable("kingdoms.chest.cleared", removed));
    }

    /**
     * Describes the looked-at container's access mode and explicit whitelist for {@code /f chest}.
     */
    public static WhitelistResult describe(ServerPlayer actor, ServerLevel level, BlockPos pos) {
        Prepared prepared = prepare(actor, level, pos);
        if (prepared.error != null) {
            return WhitelistResult.failure(prepared.error);
        }
        ChestAccess existing = prepared.existing;
        ChestAccessMode mode = existing == null ? ChestAccessMode.FACTION : existing.mode();
        Component message = Component.translatable("kingdoms.chest.info_mode", Component.translatable(modeKey(mode)));
        if (existing == null || existing.whitelistedPlayers().isEmpty()) {
            return WhitelistResult.success(message.copy()
                    .append("\n")
                    .append(Component.translatable("kingdoms.chest.info_empty")));
        }
        List<String> names = new ArrayList<>();
        for (UUID id : existing.whitelistedPlayers()) {
            names.add(resolveName(level, id));
        }
        return WhitelistResult.success(message.copy()
                .append("\n")
                .append(Component.translatable("kingdoms.chest.info_list", String.join(", ", names))));
    }

    private static Prepared prepare(ServerPlayer actor, ServerLevel level, BlockPos pos) {
        FactionManager manager = FactionManager.get(level);
        UUID factionId = manager.getFactionIdForMember(actor.getUUID()).orElse(null);
        if (factionId == null) {
            return Prepared.error("kingdoms.access_tool.not_member");
        }
        if (!isContainer(level, pos)) {
            return Prepared.error("kingdoms.chest.not_container");
        }
        UUID claimFactionId = manager.getFactionIdAt(ClaimKey.of(level, pos)).orElse(null);
        if (!factionId.equals(claimFactionId)) {
            return Prepared.error("kingdoms.access_tool.not_owned");
        }
        ChestAccess.Key key = ChestAccess.Key.of(level, pos);
        ChestAccess existing = manager.getChestAccess(key).orElse(null);
        if (existing == null) {
            BlockPos linked = ChestLinks.linkedPosition(level, pos);
            if (linked != null) {
                ChestAccess.Key linkedKey = ChestAccess.Key.of(level, linked);
                ChestAccess linkedAccess = manager.getChestAccess(linkedKey).orElse(null);
                if (linkedAccess != null) {
                    return new Prepared(factionId, linkedKey, linkedAccess, null);
                }
            }
        }
        return new Prepared(factionId, key, existing, null);
    }

    private static String resolveName(ServerLevel level, UUID id) {
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return level.getServer().getProfileCache()
                .get(id)
                .map(GameProfile::getName)
                .orElse(id.toString().substring(0, 8));
    }

    private static String modeKey(ChestAccessMode mode) {
        return "kingdoms.access." + mode.name().toLowerCase(Locale.ROOT);
    }

    private static boolean isContainer(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof Container
                || level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null;
    }

    private static void feedback(ServerPlayer player, String translationKey) {
        player.displayClientMessage(Component.translatable(translationKey), true);
    }

    public enum ToolResult {
        PASS,
        CONSUME,
        DENY
    }

    public record StateEntry(UUID id, String name) {
    }

    public record AccessState(UUID factionId, ChestAccessMode mode, List<StateEntry> whitelist) {
    }

    /** Outcome of a {@code /f chest} whitelist edit: a success/failure flag and a chat message. */
    public record WhitelistResult(boolean success, Component message) {
        private static WhitelistResult success(Component message) {
            return new WhitelistResult(true, message);
        }

        private static WhitelistResult failure(String translationKey) {
            return new WhitelistResult(false, Component.translatable(translationKey));
        }
    }

    private record Prepared(UUID factionId, ChestAccess.Key key, ChestAccess existing, String error) {
        private static Prepared error(String translationKey) {
            return new Prepared(null, null, null, translationKey);
        }
    }
}
